package dev.dyclaim.visualizer;

import dev.dyclaim.DyClaim;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkVisualizer {

    private final DyClaim plugin;
    private final Set<UUID> activeVisualizations = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, List<Location>> bedrockBlocks = new ConcurrentHashMap<>();

    private final boolean floodgateAvailable;
    private Object floodgateInstance;
    private java.lang.reflect.Method floodgateCheckMethod;

    public ChunkVisualizer(DyClaim plugin) {
        this.plugin = plugin;

        boolean available = false;
        try {
            Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateInstance = floodgateApi.getMethod("getInstance").invoke(null);
            floodgateCheckMethod = floodgateApi.getMethod("isFloodgatePlayer", UUID.class);
            available = true;
        } catch (Exception ignored) {
        }
        this.floodgateAvailable = available;
    }

    public boolean isViewing(UUID playerUUID) {
        return activeVisualizations.contains(playerUUID);
    }

    public void showChunkBorders(Player player) {
        UUID uuid = player.getUniqueId();

        if (activeVisualizations.contains(uuid)) {
            return;
        }

        activeVisualizations.add(uuid);

        Chunk chunk = player.getLocation().getChunk();
        int duration = plugin.getConfigManager().getVisualizationDuration();

        if (isBedrockPlayer(player)) {
            showBedrockVisualization(player, chunk, duration);
        } else {
            showJavaVisualization(player, chunk, duration);
        }
    }

    private void showJavaVisualization(Player player, Chunk chunk, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        World world = chunk.getWorld();

        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 16;
        int maxZ = minZ + 16;

        Particle particleType;
        try {
            particleType = Particle.valueOf(plugin.getConfigManager().getParticleType());
        } catch (IllegalArgumentException e) {
            particleType = Particle.FLAME;
        }

        final Particle finalParticle = particleType;

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= durationSeconds * 4) {
                    cleanup(uuid);
                    cancel();
                    return;
                }

                int y = player.getLocation().getBlockY();

                for (int i = 0; i <= 16; i++) {
                    spawnParticle(player, finalParticle, minX + i, y, minZ, world);
                    spawnParticle(player, finalParticle, minX + i, y + 1, minZ, world);

                    spawnParticle(player, finalParticle, minX + i, y, maxZ, world);
                    spawnParticle(player, finalParticle, minX + i, y + 1, maxZ, world);

                    spawnParticle(player, finalParticle, minX, y, minZ + i, world);
                    spawnParticle(player, finalParticle, minX, y + 1, minZ + i, world);

                    spawnParticle(player, finalParticle, maxX, y, minZ + i, world);
                    spawnParticle(player, finalParticle, maxX, y + 1, minZ + i, world);
                }

                for (int dy = -1; dy <= 3; dy++) {
                    spawnParticle(player, finalParticle, minX, y + dy, minZ, world);
                    spawnParticle(player, finalParticle, maxX, y + dy, minZ, world);
                    spawnParticle(player, finalParticle, minX, y + dy, maxZ, world);
                    spawnParticle(player, finalParticle, maxX, y + dy, maxZ, world);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 5L);

        activeTasks.put(uuid, task);
    }

    private void spawnParticle(Player player, Particle particle, double x, double y, double z, World world) {
        Location loc = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        if (particle == Particle.DUST) {
            player.spawnParticle(particle, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 165, 0), 1.0f));
        } else {
            player.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
        }
    }

    private void showBedrockVisualization(Player player, Chunk chunk, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        World world = chunk.getWorld();

        int minX = chunk.getX() << 4;
        int minZ = chunk.getZ() << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        int y = player.getLocation().getBlockY();

        Material blockMaterial;
        try {
            blockMaterial = Material.valueOf(plugin.getConfigManager().getBedrockBlock());
        } catch (IllegalArgumentException e) {
            blockMaterial = Material.ORANGE_STAINED_GLASS;
        }

        List<Location> placedBlocks = new ArrayList<>();
        BlockData blockData = blockMaterial.createBlockData();

        for (int i = 0; i <= 15; i++) {
            sendFakeBlock(player, world, minX + i, y, minZ, blockData, placedBlocks);
            sendFakeBlock(player, world, minX + i, y, maxZ, blockData, placedBlocks);

            sendFakeBlock(player, world, minX, y, minZ + i, blockData, placedBlocks);
            sendFakeBlock(player, world, maxX, y, minZ + i, blockData, placedBlocks);
        }

        bedrockBlocks.put(uuid, placedBlocks);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupBedrockBlocks(player, uuid);
                cleanup(uuid);
            }
        }.runTaskLater(plugin, durationSeconds * 20L);

        activeTasks.put(uuid, task);
    }

    private void sendFakeBlock(Player player, World world, int x, int y, int z,
            BlockData blockData, List<Location> placedBlocks) {
        Location loc = new Location(world, x, y, z);
        Block block = world.getBlockAt(loc);

        if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
            player.sendBlockChange(loc, blockData);
            placedBlocks.add(loc);
        }
    }

    private void cleanupBedrockBlocks(Player player, UUID uuid) {
        List<Location> blocks = bedrockBlocks.remove(uuid);
        if (blocks != null && player.isOnline()) {
            for (Location loc : blocks) {
                player.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }
    }

    private void cleanup(UUID uuid) {
        activeVisualizations.remove(uuid);
        BukkitTask task = activeTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public void cleanupAll() {
        for (UUID uuid : new HashSet<>(activeVisualizations)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                cleanupBedrockBlocks(player, uuid);
            }
            cleanup(uuid);
        }
    }

    private boolean isBedrockPlayer(Player player) {
        if (floodgateAvailable) {
            try {
                Boolean isBedrock = (Boolean) floodgateCheckMethod.invoke(floodgateInstance, player.getUniqueId());
                return isBedrock != null && isBedrock;
            } catch (Exception ignored) {
            }
        }

        String name = player.getName();
        if (name != null && name.startsWith(".")) {
            return true;
        }

        return false;
    }
}
