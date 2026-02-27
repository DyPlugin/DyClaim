package dev.dyclaim.hook;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;

import java.lang.reflect.Method;

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
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wg = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wg.getClass().getMethod("getPlatform").invoke(wg);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);

            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adaptWorld = adapterClass.getMethod("adapt", org.bukkit.World.class);
            Object weWorld = adaptWorld.invoke(null, chunk.getWorld());

            Method getMethod = container.getClass().getMethod("get", Class.forName("com.sk89q.worldedit.world.World"));
            Object regionManager = getMethod.invoke(container, weWorld);

            if (regionManager == null)
                return false;

            int minX = chunk.getX() << 4;
            int minZ = chunk.getZ() << 4;
            int maxX = minX + 15;
            int maxZ = minZ + 15;

            Class<?> bv3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Method atMethod = bv3Class.getMethod("at", int.class, int.class, int.class);
            Object min = atMethod.invoke(null, minX, chunk.getWorld().getMinHeight(), minZ);
            Object max = atMethod.invoke(null, maxX, chunk.getWorld().getMaxHeight(), maxZ);

            Class<?> cuboidClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion");
            Object testRegion = cuboidClass.getConstructor(String.class, bv3Class, bv3Class)
                    .newInstance("_dyclaim_test_", min, max);

            Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            Method getApplicable = regionManager.getClass().getMethod("getApplicableRegions", protectedRegionClass);
            Object result = getApplicable.invoke(regionManager, testRegion);

            Method getRegions = result.getClass().getMethod("getRegions");
            java.util.Collection<?> regions = (java.util.Collection<?>) getRegions.invoke(result);

            return !regions.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
