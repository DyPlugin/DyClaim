package dev.dyclaim.hook;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;

public class GriefPreventionHook {

    private static boolean available = false;

    public static void init() {
        available = Bukkit.getPluginManager().getPlugin("GriefPrevention") != null;
        if (available) {
            Bukkit.getLogger().info("[DyClaim] GriefPrevention detected, claim overlap protection enabled.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isRegionProtected(Chunk chunk) {
        if (!available)
            return false;
        try {
            return checkGriefPrevention(chunk);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkGriefPrevention(Chunk chunk) {
        try {
            me.ryanhamshire.GriefPrevention.GriefPrevention gp = me.ryanhamshire.GriefPrevention.GriefPrevention.instance;
            if (gp == null)
                return false;

            int minX = chunk.getX() << 4;
            int minZ = chunk.getZ() << 4;

            Location[] corners = {
                    new Location(chunk.getWorld(), minX, 64, minZ),
                    new Location(chunk.getWorld(), minX + 15, 64, minZ),
                    new Location(chunk.getWorld(), minX, 64, minZ + 15),
                    new Location(chunk.getWorld(), minX + 15, 64, minZ + 15),
                    new Location(chunk.getWorld(), minX + 8, 64, minZ + 8)
            };

            for (Location loc : corners) {
                me.ryanhamshire.GriefPrevention.Claim claim = gp.dataStore.getClaimAt(loc, false, null);
                if (claim != null)
                    return true;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
