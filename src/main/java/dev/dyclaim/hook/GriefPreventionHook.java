package dev.dyclaim.hook;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
            Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Field instanceField = gpClass.getField("instance");
            Object gp = instanceField.get(null);
            if (gp == null) return true;

            Field dataStoreField = gpClass.getField("dataStore");
            Object dataStore = dataStoreField.get(gp);

            int minX = chunk.getX() << 4;
            int minZ = chunk.getZ() << 4;

            java.util.List<Location> corners = new java.util.ArrayList<>();
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) corners.add(new Location(chunk.getWorld(),minX+x,64,minZ+z));

            Method getClaimAt = null;
            for (Method m : dataStore.getClass().getMethods()) {
                if (m.getName().equals("getClaimAt") && m.getParameterCount() == 3) {
                    getClaimAt = m;
                    break;
                }
            }

            if (getClaimAt == null) return true;

            for (Location loc : corners) {
                Object claim = getClaimAt.invoke(dataStore, loc, true, null);
                if (claim != null)
                    return true;
            }

            return false;
        } catch (Exception e) { return true;
        }
    }
}

