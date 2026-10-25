package dev.dyclaim.manager;

import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Claim/index changes and file writes are coordinated independently of external payments. */
public class ClaimManager {
    private final DyClaim plugin;
    private final Map<String, ClaimData> claims = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerIndex = new HashMap<>();
    private final ClaimStore store;
    private volatile boolean writable;
    private BukkitTask pendingSave;
    private final Object writeLock = new Object();
    private long saveNumber, writtenNumber;
    public ClaimManager(DyClaim plugin) {
        this.plugin = plugin;
        store = new ClaimStore(plugin.getDataFolder().toPath().resolve("claims.json"));
        loadAll();
    }
    public synchronized void loadAll() {
        writable = false;
        try {
            Map<String, ClaimData> loaded = store.load(plugin.getConfigManager().getClaimPrice());
            claims.clear(); claims.putAll(loaded); rebuildPlayerIndex(); writable = true;
            plugin.getLogger().info("Loaded " + claims.size() + " claims (schema 2).");
        } catch (IOException ex) {
            throw new IllegalStateException("Claim data cannot be loaded. Writes disabled; restore a verified backup. " + ex.getMessage(), ex);
        }
    }
    public synchronized void rebuildPlayerIndex() {
        playerIndex.clear();
        claims.forEach((key, claim) -> {
            playerIndex.computeIfAbsent(claim.getOwnerUUID(), uuid -> new HashSet<>()).add(key);
            if (claim.getCoowner() != null) playerIndex.computeIfAbsent(claim.getCoowner(), uuid -> new HashSet<>()).add(key);
        });
    }
    public synchronized void saveAll() {
        if (!writable) throw new IllegalStateException("Claim writes disabled");
        if (pendingSave == null) pendingSave = plugin.getPlatformTasks().globalLater(this::saveInBackground, 20L);
    }
    public boolean isWritable() { return writable; }
    /**
     * Copies the claims on the server thread, then writes the file in the background, so large claim files
     * do not stall the server. Writes are numbered and an older copy never replaces a newer one.
     */
    private void saveInBackground() {
        Map<String, ClaimData> snapshot;long number;
        synchronized (this) {
            pendingSave = null;
            if (!writable) return;
            try { snapshot = store.prepare(claims); } catch (IOException ex) { writeFailed(ex); return; }
            number = ++saveNumber;
        }
        try { plugin.getPlatformTasks().async(() -> write(snapshot, number)); }
        catch (RuntimeException ex) { write(snapshot, number); }
    }
    private void write(Map<String, ClaimData> snapshot,long number) {
        synchronized (writeLock) {
            if (number <= writtenNumber) return;
            try { store.write(snapshot); writtenNumber = number; }
            catch (IOException ex) {
                Runnable fail = () -> { synchronized (ClaimManager.this) { writeFailed(ex); } };
                try { plugin.getPlatformTasks().globalNow(fail); } catch (RuntimeException closed) { fail.run(); }
            }
        }
    }
    public synchronized void saveAllSync() {
        if (!writable) return;
        if (pendingSave != null) { pendingSave.cancel(); pendingSave = null; }
        try {
            Map<String, ClaimData> snapshot = store.prepare(claims);long number = ++saveNumber;
            // Waits for a background write that is still running, then writes this newer copy.
            synchronized (writeLock) { store.write(snapshot); writtenNumber = number; }
        } catch (IOException ex) {
            writeFailed(ex);
            throw new IllegalStateException("Claim write failed", ex);
        }
    }
    private void writeFailed(IOException ex) {
        if (!writable) return;
        writable = false;
        plugin.getLogger().severe("Claim write failed; plugin disabled to prevent further mutations: " + ex.getMessage());
        plugin.getServer().getPluginManager().disablePlugin(plugin);
    }
    public synchronized void backup(String reason) {
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
    public boolean claimChunk(Player player, Chunk chunk) { return claimChunk(player,chunk,dev.dyclaim.api.event.ClaimCreateEvent.Cause.PLAYER); }
    public boolean claimChunk(Player player, Chunk chunk, dev.dyclaim.api.event.ClaimCreateEvent.Cause cause) {
        String key=getChunkKey(chunk); if (claims.containsKey(key)) return false;
        ClaimData claim=new ClaimData(player.getUniqueId(),player.getName(),chunk.getWorld().getName(),chunk.getX(),chunk.getZ());
        claim.inheritChoices();
        claim.recordPrice(plugin.getEconomyManager().isEnabled() ? plugin.getAcquisitionRules().price(chunk.getWorld().getName()) : 0);
        synchronized(this){
            // The provider lookup above does not hold the state lock; recheck before committing.
            if(claims.containsKey(key)||mutationBlocked(key))return false;
            claims.put(key,claim);playerIndex.computeIfAbsent(claim.getOwnerUUID(),uuid->new HashSet<>()).add(key);saveAll();
        }
        dev.dyclaim.api.ApiEvents.created(plugin,claim,player,cause);return true;
    }
    /** Imported claims carry no DyClaim purchase price, so selling them refunds nothing. */
    public synchronized ClaimData importClaim(UUID owner,String ownerName,String world,int x,int z) {
        String key=getChunkKey(world,x,z);if(claims.containsKey(key)||mutationBlocked(key))return null;
        ClaimData claim=new ClaimData(owner,ownerName==null?owner.toString():ownerName,world,x,z);
        claim.inheritChoices();claim.recordPrice(0);
        claims.put(key,claim);playerIndex.computeIfAbsent(owner,uuid->new HashSet<>()).add(key);saveAll();
        dev.dyclaim.api.ApiEvents.created(plugin,claim,null,dev.dyclaim.api.event.ClaimCreateEvent.Cause.IMPORT);
        return claim;
    }
    public boolean unclaimChunk(Chunk chunk) { return remove(getChunkKey(chunk)); }
    public boolean unclaimChunk(String world,int x,int z) { return remove(getChunkKey(world,x,z)); }
    public synchronized boolean remove(String key) {
        if(mutationBlocked(key))return false;
        ClaimData removed=claims.remove(key); if (removed==null) return false;
        removeIndex(removed.getOwnerUUID(),key);if(removed.getCoowner()!=null)removeIndex(removed.getCoowner(),key);
        if (plugin.getFeatureManager()!=null) plugin.getFeatureManager().invalidate(removed);
        saveAll(); dev.dyclaim.api.ApiEvents.removed(plugin,removed); return true;
    }
    public synchronized List<ClaimData> getPlayerClaims(UUID uuid) {
        Set<String> keys=playerIndex.getOrDefault(uuid,Set.of());
        return keys.stream().map(claims::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(ClaimData::getWorld).thenComparingInt(ClaimData::getChunkX).thenComparingInt(ClaimData::getChunkZ)).toList();
    }
    public synchronized int getPlayerClaimCount(UUID uuid) { return playerIndex.getOrDefault(uuid,Set.of()).size(); }
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
        try(var reservation=plugin.getTransactionManager().reserve(claim.getChunkKey())){
            if(reservation==null)return false;
            synchronized(this){if(claims.get(claim.getChunkKey())!=claim)return false;}
            return plugin.getTransactionManager().execute(reservation,"refund",claim.getChunkKey(),null,claim.getOwnerUUID(),0,refund(claim),()->{
                if(!remove(claim.getChunkKey()))throw new IllegalStateException("Refund claim changed before removal");
            });
        }
    }
    private boolean mutationBlocked(String key){var transactions=plugin.getTransactionManager();return transactions!=null&&transactions.isLocked(key)&&!transactions.ownsMutation(key);}
    public synchronized void transfer(ClaimData c,Player target) {
        if(claims.get(c.getChunkKey())!=c||mutationBlocked(c.getChunkKey()))throw new IllegalStateException("Claim changed or is locked");
        UUID previous=c.getOwnerUUID();
        removeIndex(c.getOwnerUUID(),c.getChunkKey());if(c.getCoowner()!=null)removeIndex(c.getCoowner(),c.getChunkKey());
        c.transferOwnership(target.getUniqueId(),target.getName());
        if(c.getName()!=null && getPlayerClaims(target.getUniqueId()).stream().anyMatch(other->other!=c && c.getName().equalsIgnoreCase(other.getName()))) c.setName(null);
        playerIndex.computeIfAbsent(target.getUniqueId(),uuid->new HashSet<>()).add(c.getChunkKey());plugin.getFeatureManager().invalidate(c); saveAll();
        dev.dyclaim.api.ApiEvents.transferred(plugin,c,previous,target.getUniqueId());
    }
    public synchronized boolean changeCoowner(ClaimData claim,UUID target,UUID expectedOwner,long expectedRevision){
        if(claims.get(claim.getChunkKey())!=claim||mutationBlocked(claim.getChunkKey())||!claim.getOwnerUUID().equals(expectedOwner)||claim.getRevision()!=expectedRevision)return false;
        UUID previous=claim.getCoowner();if(previous!=null)removeIndex(previous,claim.getChunkKey());
        claim.setCoowner(target);if(target!=null)playerIndex.computeIfAbsent(target,uuid->new HashSet<>()).add(claim.getChunkKey());return true;
    }
    public void setAllClaimsPvp(boolean disabled) { claims.values().forEach(c->c.setChoice("pvp",!disabled)); saveAll(); }
    public void setAllClaimsExplosion(boolean disabled) { claims.values().forEach(c->c.setChoice("explosions",!disabled)); saveAll(); }
    public void setAllClaimsMobSpawn(boolean disabled) { claims.values().forEach(c->c.setChoice("mob-spawning",!disabled)); saveAll(); }
    public void giveChunk(Player player,Chunk chunk) { ClaimData old=getClaimAt(chunk); if(old!=null)remove(old.getChunkKey()); claimChunk(player,chunk); }
    public int refundPriceDifference(double difference) {
        if(!Double.isFinite(difference)||difference<=0||!plugin.getEconomyManager().isEnabled())return 0;
        double current=plugin.getConfigManager().getClaimPrice(); Set<UUID> owners=new HashSet<>();
        for(ClaimData c:new ArrayList<>(claims.values())) {
            try(var reservation=plugin.getTransactionManager().reserve(c.getChunkKey())){
                if(reservation==null)continue;
                synchronized(this){if(claims.get(c.getChunkKey())!=c)continue;}
                double basis=c.getRefundBasis(current), amount=dev.dyclaim.util.Rules.money(Math.min(difference,Math.max(0,basis-current)));
                if(amount>0 && plugin.getTransactionManager().execute(reservation,"price-difference",c.getChunkKey(),null,c.getOwnerUUID(),0,amount,()->{c.setRefundBasis(basis-amount);saveAll();})) owners.add(c.getOwnerUUID());
            }
        }
        return owners.size();
    }
    public synchronized Map<String,ClaimData> getAllClaims() { return Map.copyOf(claims); }
    private void removeIndex(UUID uuid,String key){Set<String> keys=playerIndex.get(uuid);if(keys!=null){keys.remove(key);if(keys.isEmpty())playerIndex.remove(uuid);}}
}
