package dev.dyclaim.hook;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.lang.reflect.Method;

public class ClaimPluginHooks {

    private static boolean townyAvailable = false;
    private static boolean landsAvailable = false;
    private static boolean residenceAvailable = false;
    private static boolean griefDefenderAvailable = false;

    private static Object townyApiInstance;
    private static Method townyGetTownBlock;

    private static Object landsApiInstance;
    private static Method landsGetAreaByLoc;

    private static Object residenceManager;
    private static Method residenceGetByLoc;

    private static Object griefDefenderCore;
    private static Method griefDefenderGetClaimAt;

    public static void init(org.bukkit.plugin.Plugin plugin) {
        initTowny();
        initLands(plugin);
        initResidence();
        initGriefDefender();
    }

    private static void initTowny() {
        if (Bukkit.getPluginManager().getPlugin("Towny") == null)
            return;
        try {
            Class<?> townyApi = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            townyApiInstance = townyApi.getMethod("getInstance").invoke(null);
            townyGetTownBlock = townyApi.getMethod("getTownBlock", Location.class);
            townyAvailable = true;
            Bukkit.getLogger().info("[DyClaim] Towny detected, overlap protection enabled.");
        } catch (Exception ignored) {
        }
    }

    private static void initLands(org.bukkit.plugin.Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("Lands") == null)
            return;
        try {
            Class<?> landsIntegration = Class.forName("me.angeschossen.lands.api.LandsIntegration");
            landsApiInstance = landsIntegration.getMethod("of", org.bukkit.plugin.Plugin.class).invoke(null, plugin);
            landsGetAreaByLoc = landsApiInstance.getClass().getMethod("getArea", Location.class);
            landsAvailable = true;
            Bukkit.getLogger().info("[DyClaim] Lands detected, overlap protection enabled.");
        } catch (Exception ignored) {
        }
    }

    private static void initResidence() {
        if (Bukkit.getPluginManager().getPlugin("Residence") == null)
            return;
        try {
            Class<?> residenceApi = Class.forName("com.bekvon.bukkit.residence.Residence");
            Object instance = residenceApi.getMethod("getInstance").invoke(null);
            residenceManager = instance.getClass().getMethod("getResidenceManager").invoke(instance);
            residenceGetByLoc = residenceManager.getClass().getMethod("getByLoc", Location.class);
            residenceAvailable = true;
            Bukkit.getLogger().info("[DyClaim] Residence detected, overlap protection enabled.");
        } catch (Exception ignored) {
        }
    }

    private static void initGriefDefender() {
        if (Bukkit.getPluginManager().getPlugin("GriefDefender") == null)
            return;
        try {
            Class<?> gdApi = Class.forName("com.griefdefender.api.GriefDefender");
            griefDefenderCore = gdApi.getMethod("getCore").invoke(null);
            griefDefenderGetClaimAt = griefDefenderCore.getClass().getMethod("getClaimAt",
                    Class.forName("com.griefdefender.api.claim.ClaimTypes"), Location.class);
            griefDefenderAvailable = true;
            Bukkit.getLogger().info("[DyClaim] GriefDefender detected, overlap protection enabled.");
        } catch (Exception ignored) {
        }
    }

    public static boolean isRegionProtected(Chunk chunk) {
        Location center = new Location(chunk.getWorld(), (chunk.getX() << 4) + 8, 64, (chunk.getZ() << 4) + 8);

        if (townyAvailable && checkTowny(center))
            return true;
        if (landsAvailable && checkLands(center))
            return true;
        if (residenceAvailable && checkResidence(center))
            return true;
        if (griefDefenderAvailable && checkGriefDefender(center))
            return true;

        return false;
    }

    private static boolean checkTowny(Location loc) {
        try {
            Object townBlock = townyGetTownBlock.invoke(townyApiInstance, loc);
            return townBlock != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkLands(Location loc) {
        try {
            Object area = landsGetAreaByLoc.invoke(landsApiInstance, loc);
            return area != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkResidence(Location loc) {
        try {
            Object residence = residenceGetByLoc.invoke(residenceManager, loc);
            return residence != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkGriefDefender(Location loc) {
        try {
            Object claim = griefDefenderGetClaimAt.invoke(griefDefenderCore, null, loc);
            if (claim == null)
                return false;
            Method isWilderness = claim.getClass().getMethod("isWilderness");
            Boolean wild = (Boolean) isWilderness.invoke(claim);
            return wild == null || !wild;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getDetectedPlugins() {
        StringBuilder sb = new StringBuilder();
        if (townyAvailable)
            sb.append("Towny ");
        if (landsAvailable)
            sb.append("Lands ");
        if (residenceAvailable)
            sb.append("Residence ");
        if (griefDefenderAvailable)
            sb.append("GriefDefender ");
        return sb.toString().trim();
    }
}
