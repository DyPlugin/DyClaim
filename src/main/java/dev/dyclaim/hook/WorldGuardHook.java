package dev.dyclaim.hook;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

public class WorldGuardHook {

    private static boolean available = false;

    public static void init() {
        available = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        if (available) {
            Bukkit.getLogger().info("[DyClaim] WorldGuard detected, region protection enabled.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isRegionProtected(Chunk chunk) {
        if (!available)
            return false;
        try {
            return checkWorldGuard(chunk);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkWorldGuard(Chunk chunk) {
        try {
            com.sk89q.worldedit.bukkit.BukkitAdapter adapter = null;
            com.sk89q.worldguard.WorldGuard wg = com.sk89q.worldguard.WorldGuard.getInstance();
            com.sk89q.worldguard.protection.regions.RegionContainer container = wg.getPlatform().getRegionContainer();

            World world = chunk.getWorld();
            com.sk89q.worldedit.world.World weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world);
            com.sk89q.worldguard.protection.managers.RegionManager regionManager = container.get(weWorld);

            if (regionManager == null)
                return false;

            int minX = chunk.getX() << 4;
            int minZ = chunk.getZ() << 4;
            int maxX = minX + 15;
            int maxZ = minZ + 15;

            com.sk89q.worldedit.math.BlockVector3 min = com.sk89q.worldedit.math.BlockVector3.at(minX,
                    world.getMinHeight(), minZ);
            com.sk89q.worldedit.math.BlockVector3 max = com.sk89q.worldedit.math.BlockVector3.at(maxX,
                    world.getMaxHeight(), maxZ);
            com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion testRegion = new com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion(
                    "_dyclaim_test_", min, max);

            return !regionManager.getApplicableRegions(testRegion).getRegions().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
