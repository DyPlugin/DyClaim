package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** All mutations and snapshots occur on the server thread. */
public class ClaimManager {
    private final DyClaim plugin;
    private final Map<String, ClaimData> claims = new HashMap<>();
    private final Map<UUID, Set<String>> playerIndex = new HashMap<>();
    private final ClaimStore store;
    private boolean writable;
    private BukkitTask pendingSave;
    public ClaimManager(DyClaim plugin) {
        this.plugin = plugin;
        store = new ClaimStore(plugin.getDataFolder().toPath().resolve("claims.json"));
        loadAll();
    }
    public void loadAll() {
        writable = false;
        try {
            Map<String, ClaimData> loaded = store.load(plugin.getConfigManager().getClaimPrice());
            claims.clear(); claims.putAll(loaded); rebuildPlayerIndex(); writable = true;
            plugin.getLogger().info("Loaded " + claims.size() + " claims (schema 2).");
        } catch (IOException ex) {
            throw new IllegalStateException("Claim data cannot be loaded. Writes disabled; restore a verified backup. " + ex.getMessage(), ex);
        }
    }
    public void rebuildPlayerIndex() {
        playerIndex.clear();
        claims.forEach((key, claim) -> {
            playerIndex.computeIfAbsent(claim.getOwnerUUID(), uuid -> new HashSet<>()).add(key);
            if (claim.getCoowner() != null) playerIndex.computeIfAbsent(claim.getCoowner(), uuid -> new HashSet<>()).add(key);
        });
    }
    public void saveAll() {
        if (!writable) throw new IllegalStateException("Claim writes disabled");
        if (pendingSave == null) pendingSave = plugin.getServer().getScheduler().runTaskLater(plugin, () -> { pendingSave = null; saveAllSync(); }, 20L);
    }
    public void saveAllSync() {
        if (!writable) return;
        if (pendingSave != null) { pendingSave.cancel(); pendingSave = null; }
        try { store.save(claims); }
        catch (IOException ex) {
            writable = false;
            plugin.getLogger().severe("Claim write failed; plugin disabled to prevent further mutations: " + ex.getMessage());
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            throw new IllegalStateException("Claim write failed", ex);
        }
    }
    public void backup(String reason) {
        saveAllSync();
        try {
            Path dir = plugin.getDataFolder().toPath().resolve("backups"); Files.createDirectories(dir);
            Files.copy(plugin.getDataFolder().toPath().resolve("claims.json"), dir.resolve(reason + "-" + UUID.randomUUID() + ".json"));
        } catch (IOException ex) { throw new IllegalStateException("Backup failed", ex); }
    }
    public String getChunkKey(String world, int x, int z) { return world + ":" + x + ":" + z; }
    public String getChunkKey(Chunk chunk) { return getChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()); }
    public boolean isChunkClaimed(Chunk chunk) { return claims.containsKey(getChunkKey(chunk)); }
    public boolean isChunkClaimed(String world, int x, int z) { return claims.containsKey(getChunkKey(world,x,z)); }
    public ClaimData getClaimAt(Chunk chunk) { return claims.get(getChunkKey(chunk)); }
    public ClaimData getClaimAt(String world, int x, int z) { return claims.get(getChunkKey(world,x,z)); }
    public ClaimData getByKey(String key) { return claims.get(key); }
    public boolean isOwner(Player player, Chunk chunk) { ClaimData c=getClaimAt(chunk); return c != null && c.getOwnerUUID().equals(player.getUniqueId()); }
    public boolean isAllowed(Player player, Chunk chunk) { return plugin.getAccessManager().allows(player,getClaimAt(chunk),"build"); }
    public boolean claimChunk(Player player, Chunk chunk) {
        String key=getChunkKey(chunk); if (claims.containsKey(key)) return false;
        ClaimData claim=new ClaimData(player.getUniqueId(),player.getName(),chunk.getWorld().getName(),chunk.getX(),chunk.getZ());
        claim.inheritChoices();
        claim.recordPrice(plugin.getEconomyManager().isEnabled() ? plugin.getAcquisitionRules().price(chunk.getWorld().getName()) : 0);
        claims.put(key,claim); playerIndex.computeIfAbsent(player.getUniqueId(),uuid->new HashSet<>()).add(key); saveAll(); return true;
    }
    public boolean unclaimChunk(Chunk chunk) { return remove(getChunkKey(chunk)); }
    public boolean unclaimChunk(String world,int x,int z) { return remove(getChunkKey(world,x,z)); }
    public boolean remove(String key) {
        ClaimData removed=claims.remove(key); if (removed==null) return false;
        removeIndex(removed.getOwnerUUID(),key);if(removed.getCoowner()!=null)removeIndex(removed.getCoowner(),key);
        if (plugin.getFeatureManager()!=null) plugin.getFeatureManager().invalidate(removed);
        saveAll(); return true;
    }
    public List<ClaimData> getPlayerClaims(UUID uuid) {
        Set<String> keys=playerIndex.getOrDefault(uuid,Set.of());
        return keys.stream().map(claims::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(ClaimData::getWorld).thenComparingInt(ClaimData::getChunkX).thenComparingInt(ClaimData::getChunkZ)).toList();
    }
    public int getPlayerClaimCount(UUID uuid) { return playerIndex.getOrDefault(uuid,Set.of()).size(); }
    public int removeAllPlayerClaims(UUID uuid) {
        List<ClaimData> owned=getPlayerClaims(uuid).stream().filter(c->c.getOwnerUUID().equals(uuid)).toList();
        int count=0; for(ClaimData c:owned) if(!plugin.getTransactionManager().isLocked(c.getChunkKey()) && remove(c.getChunkKey())) count++;
        return count;
    }
    public int removeAllPlayerClaimsWithRefund(UUID uuid,double ignoredLegacyRefund) {
        int count=0;
        for(ClaimData c:new ArrayList<>(getPlayerClaims(uuid))) if(c.getOwnerUUID().equals(uuid) && refundAndRemove(c)) count++;
        return count;
    }
    public int removeAllClaimsWithRefund(double ignoredLegacyRefund) { int count=0; for(ClaimData c:new ArrayList<>(claims.values())) if(refundAndRemove(c))count++; return count; }
    public double refund(ClaimData claim) {
        if(!plugin.getEconomyManager().isEnabled())return 0;
        return dev.dyclaim.util.Rules.money(claim.getRefundBasis(plugin.getConfigManager().getClaimPrice()) * plugin.getConfigManager().getSellRefundPercent()/100.0);
    }
    public boolean refundAndRemove(ClaimData claim) {
        return plugin.getTransactionManager().execute("refund",claim.getChunkKey(),null,claim.getOwnerUUID(),0,refund(claim),()->remove(claim.getChunkKey()));
    }
    public void transfer(ClaimData c,Player target) {
        removeIndex(c.getOwnerUUID(),c.getChunkKey());if(c.getCoowner()!=null)removeIndex(c.getCoowner(),c.getChunkKey());
        c.setOwnerUUID(target.getUniqueId()); c.setOwnerName(target.getName()); c.clearAccess();
        if(c.getName()!=null && getPlayerClaims(target.getUniqueId()).stream().anyMatch(other->other!=c && c.getName().equalsIgnoreCase(other.getName()))) c.setName(null);
        playerIndex.computeIfAbsent(target.getUniqueId(),uuid->new HashSet<>()).add(c.getChunkKey());plugin.getFeatureManager().invalidate(c); saveAll();
    }
    public void setAllClaimsPvp(boolean disabled) { claims.values().forEach(c->c.setChoice("pvp",!disabled)); saveAll(); }
    public void setAllClaimsExplosion(boolean disabled) { claims.values().forEach(c->c.setChoice("explosions",!disabled)); saveAll(); }
    public void setAllClaimsMobSpawn(boolean disabled) { claims.values().forEach(c->c.setChoice("mob-spawning",!disabled)); saveAll(); }
    public void giveChunk(Player player,Chunk chunk) { ClaimData old=getClaimAt(chunk); if(old!=null)remove(old.getChunkKey()); claimChunk(player,chunk); }
    public int refundPriceDifference(double difference) {
        if(!Double.isFinite(difference)||difference<=0||!plugin.getEconomyManager().isEnabled())return 0;
        double current=plugin.getConfigManager().getClaimPrice(); Set<UUID> owners=new HashSet<>();
        for(ClaimData c:new ArrayList<>(claims.values())) {
            double basis=c.getRefundBasis(current), amount=dev.dyclaim.util.Rules.money(Math.min(difference,Math.max(0,basis-current)));
            if(amount>0 && plugin.getTransactionManager().execute("price-difference",c.getChunkKey(),null,c.getOwnerUUID(),0,amount,()->{c.setRefundBasis(basis-amount);saveAll();})) owners.add(c.getOwnerUUID());
        }
        return owners.size();
    }
    public Map<String,ClaimData> getAllClaims() { return Collections.unmodifiableMap(claims); }
    private void removeIndex(UUID uuid,String key){Set<String> keys=playerIndex.get(uuid);if(keys!=null){keys.remove(key);if(keys.isEmpty())playerIndex.remove(uuid);}}
}
