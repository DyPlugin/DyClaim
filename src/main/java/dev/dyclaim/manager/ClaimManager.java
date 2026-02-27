package dev.dyclaim.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.dyclaim.DyClaim;
import dev.dyclaim.model.ClaimData;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager {

    private final DyClaim plugin;
    private final Map<String, ClaimData> claims = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerIndex = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File dataFile;
    private final File backupFile;

    private volatile boolean dirty = false;

    public ClaimManager(DyClaim plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "claims.json");
        this.backupFile = new File(plugin.getDataFolder(), "claims.json.bak");
        loadAll();
    }

    public void loadAll() {
        claims.clear();
        playerIndex.clear();
        if (!dataFile.exists()) {
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, ClaimData>>() {
            }.getType();
            Map<String, ClaimData> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                claims.putAll(loaded);
                rebuildPlayerIndex();
            }
            plugin.getLogger().info("Loaded " + claims.size() + " claims.");
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading claim data: " + e.getMessage());
            if (backupFile.exists()) {
                plugin.getLogger().info("Attempting to load from backup...");
                try (Reader reader = new InputStreamReader(new FileInputStream(backupFile), StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<Map<String, ClaimData>>() {
                    }.getType();
                    Map<String, ClaimData> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        claims.putAll(loaded);
                        rebuildPlayerIndex();
                        plugin.getLogger().info("Loaded " + claims.size() + " claims from backup.");
                    }
                } catch (Exception ex) {
                    plugin.getLogger().severe("Backup also failed: " + ex.getMessage());
                }
            }
        }
    }

    private void rebuildPlayerIndex() {
        playerIndex.clear();
        for (Map.Entry<String, ClaimData> entry : claims.entrySet()) {
            playerIndex.computeIfAbsent(entry.getValue().getOwnerUUID(), k -> new HashSet<>())
                    .add(entry.getKey());
        }
    }

    /**
     * Marks data as dirty and schedules an async save after 1 second (20 ticks).
     * Multiple rapid mutations coalesce into a single write.
     */
    public void saveAll() {
        if (!dirty) {
            dirty = true;
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                if (dirty) {
                    saveAllInternal();
                    dirty = false;
                }
            }, 20L);
        }
    }

    /**
     * Synchronous save for use during server shutdown (onDisable).
     * Ensures all pending changes are flushed before the plugin is disabled.
     */
    public void saveAllSync() {
        dirty = false;
        saveAllInternal();
    }

    private synchronized void saveAllInternal() {
        try {
            if (!dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }

            if (dataFile.exists()) {
                Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            Map<String, ClaimData> snapshot = new HashMap<>(claims);

            File tempFile = new File(plugin.getDataFolder(), "claims.json.tmp");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                gson.toJson(snapshot, writer);
            }

            Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().severe("Error saving claim data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getChunkKey(String world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    public String getChunkKey(Chunk chunk) {
        return getChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public boolean isChunkClaimed(Chunk chunk) {
        return claims.containsKey(getChunkKey(chunk));
    }

    public boolean isChunkClaimed(String world, int chunkX, int chunkZ) {
        return claims.containsKey(getChunkKey(world, chunkX, chunkZ));
    }

    public ClaimData getClaimAt(Chunk chunk) {
        return claims.get(getChunkKey(chunk));
    }

    public ClaimData getClaimAt(String world, int chunkX, int chunkZ) {
        return claims.get(getChunkKey(world, chunkX, chunkZ));
    }

    public boolean isOwner(Player player, Chunk chunk) {
        ClaimData claim = getClaimAt(chunk);
        return claim != null && claim.getOwnerUUID().equals(player.getUniqueId());
    }

    public boolean isAllowed(Player player, Chunk chunk) {
        ClaimData claim = getClaimAt(chunk);
        if (claim == null)
            return true;
        return claim.isAllowed(player.getUniqueId()) || player.hasPermission("dyclaim.admin.bypass");
    }

    public boolean claimChunk(Player player, Chunk chunk) {
        String key = getChunkKey(chunk);
        if (claims.containsKey(key)) {
            return false;
        }

        ClaimData claim = new ClaimData(
                player.getUniqueId(),
                player.getName(),
                chunk.getWorld().getName(),
                chunk.getX(),
                chunk.getZ());

        claim.setPvpDisabled(plugin.getConfigManager().isPvpDisabled());
        claim.setExplosionDisabled(plugin.getConfigManager().isExplosionDisabled());
        claim.setMobSpawnDisabled(plugin.getConfigManager().isMobGriefingDisabled());

        claims.put(key, claim);
        playerIndex.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(key);
        saveAll();
        return true;
    }

    public boolean unclaimChunk(Chunk chunk) {
        String key = getChunkKey(chunk);
        ClaimData removed = claims.remove(key);
        if (removed == null) {
            return false;
        }
        removeFromIndex(removed.getOwnerUUID(), key);
        saveAll();
        return true;
    }

    public boolean unclaimChunk(String world, int chunkX, int chunkZ) {
        String key = getChunkKey(world, chunkX, chunkZ);
        ClaimData removed = claims.remove(key);
        if (removed == null) {
            return false;
        }
        removeFromIndex(removed.getOwnerUUID(), key);
        saveAll();
        return true;
    }

    public List<ClaimData> getPlayerClaims(UUID playerUUID) {
        Set<String> keys = playerIndex.get(playerUUID);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClaimData> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            ClaimData claim = claims.get(key);
            if (claim != null) {
                result.add(claim);
            }
        }
        return result;
    }

    public int getPlayerClaimCount(UUID playerUUID) {
        Set<String> keys = playerIndex.get(playerUUID);
        return keys != null ? keys.size() : 0;
    }

    public int removeAllPlayerClaims(UUID playerUUID) {
        Set<String> keys = playerIndex.remove(playerUUID);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        for (String key : keys) {
            claims.remove(key);
        }
        saveAll();
        return keys.size();
    }

    public int removeAllPlayerClaimsWithRefund(UUID playerUUID, double refundPerClaim) {
        Set<String> keys = playerIndex.remove(playerUUID);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }

        int count = keys.size();
        for (String key : keys) {
            claims.remove(key);
        }

        if (count > 0 && refundPerClaim > 0 && plugin.getEconomyManager().isAvailable()) {
            Player target = Bukkit.getPlayer(playerUUID);
            if (target != null && target.isOnline()) {
                plugin.getEconomyManager().deposit(target, refundPerClaim * count);
            } else {
                depositOffline(playerUUID, refundPerClaim * count);
            }
        }

        saveAll();
        return count;
    }

    public int removeAllClaimsWithRefund(double refundPerClaim) {
        Map<UUID, Integer> playerClaimCounts = new HashMap<>();
        for (ClaimData claim : claims.values()) {
            playerClaimCounts.merge(claim.getOwnerUUID(), 1, Integer::sum);
        }

        int totalRemoved = claims.size();
        claims.clear();
        playerIndex.clear();

        if (refundPerClaim > 0 && plugin.getEconomyManager().isAvailable()) {
            for (Map.Entry<UUID, Integer> entry : playerClaimCounts.entrySet()) {
                UUID uuid = entry.getKey();
                int count = entry.getValue();
                double totalRefund = refundPerClaim * count;

                Player target = Bukkit.getPlayer(uuid);
                if (target != null && target.isOnline()) {
                    plugin.getEconomyManager().deposit(target, totalRefund);
                    target.sendMessage(plugin.getMessageManager().getPrefixed(target, "admin-refund-received",
                            Map.of("{refund}", plugin.getEconomyManager().formatMoney(totalRefund),
                                    "{count}", String.valueOf(count))));
                } else {
                    depositOffline(uuid, totalRefund);
                }
            }
        }

        saveAll();
        return totalRemoved;
    }

    public void setAllClaimsPvp(boolean disabled) {
        for (ClaimData claim : claims.values())
            claim.setPvpDisabled(disabled);
        saveAll();
    }

    public void setAllClaimsExplosion(boolean disabled) {
        for (ClaimData claim : claims.values())
            claim.setExplosionDisabled(disabled);
        saveAll();
    }

    public void setAllClaimsMobSpawn(boolean disabled) {
        for (ClaimData claim : claims.values())
            claim.setMobSpawnDisabled(disabled);
        saveAll();
    }

    public void giveChunk(Player target, Chunk chunk) {
        String key = getChunkKey(chunk);
        ClaimData oldClaim = claims.remove(key);
        if (oldClaim != null) {
            removeFromIndex(oldClaim.getOwnerUUID(), key);
        }
        ClaimData claim = new ClaimData(
                target.getUniqueId(),
                target.getName(),
                chunk.getWorld().getName(),
                chunk.getX(),
                chunk.getZ());

        claim.setPvpDisabled(plugin.getConfigManager().isPvpDisabled());
        claim.setExplosionDisabled(plugin.getConfigManager().isExplosionDisabled());
        claim.setMobSpawnDisabled(plugin.getConfigManager().isMobGriefingDisabled());

        claims.put(key, claim);
        playerIndex.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>()).add(key);
        saveAll();
    }

    public int refundPriceDifference(double diffPerClaim) {
        if (diffPerClaim <= 0 || !plugin.getEconomyManager().isAvailable())
            return 0;

        Map<UUID, Integer> playerClaimCounts = new HashMap<>();
        for (ClaimData claim : claims.values()) {
            playerClaimCounts.merge(claim.getOwnerUUID(), 1, Integer::sum);
        }

        int ownersRefunded = 0;
        for (Map.Entry<UUID, Integer> entry : playerClaimCounts.entrySet()) {
            UUID uuid = entry.getKey();
            int count = entry.getValue();
            double totalRefund = diffPerClaim * count;

            Player target = Bukkit.getPlayer(uuid);
            if (target != null && target.isOnline()) {
                plugin.getEconomyManager().deposit(target, totalRefund);
                target.sendMessage(plugin.getMessageManager().getPrefixed(target, "admin-price-diff-received",
                        Map.of("{refund}", plugin.getEconomyManager().formatMoney(totalRefund),
                                "{count}", String.valueOf(count))));
            } else {
                depositOffline(uuid, totalRefund);
            }
            ownersRefunded++;
        }

        return ownersRefunded;
    }

    public Map<String, ClaimData> getAllClaims() {
        return Collections.unmodifiableMap(claims);
    }

    private void removeFromIndex(UUID playerUUID, String key) {
        Set<String> keys = playerIndex.get(playerUUID);
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) {
                playerIndex.remove(playerUUID);
            }
        }
    }

    private void depositOffline(UUID playerUUID, double amount) {
        try {
            var offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            net.milkbowl.vault.economy.Economy econ = getEconomy();
            if (econ != null) {
                econ.depositPlayer(offlinePlayer, amount);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refund offline player: " + e.getMessage());
        }
    }

    private net.milkbowl.vault.economy.Economy getEconomy() {
        var rsp = plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }
}
