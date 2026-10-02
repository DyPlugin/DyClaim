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
            landsGetAreaByLoc = landsApiInstance.getClass().getMethod("getLandByChunk", org.bukkit.World.class, int.class, int.class);
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
            griefDefenderGetClaimAt = griefDefenderCore.getClass().getMethod("getClaimManager", java.util.UUID.class);
            griefDefenderAvailable = true;
            Bukkit.getLogger().info("[DyClaim] GriefDefender detected, overlap protection enabled.");
        } catch (Exception ignored) {
        }
    }

    public static boolean isRegionProtected(Chunk chunk) {
        if ((Bukkit.getPluginManager().getPlugin("Towny") != null && !townyAvailable) || (Bukkit.getPluginManager().getPlugin("Lands") != null && !landsAvailable) || (Bukkit.getPluginManager().getPlugin("Residence") != null && !residenceAvailable) || (Bukkit.getPluginManager().getPlugin("GriefDefender") != null && !griefDefenderAvailable)) return true;
        Location center = new Location(chunk.getWorld(), (chunk.getX() << 4) + 8, 64, (chunk.getZ() << 4) + 8);

        if (townyAvailable && checkTowny(center))
            return true;
        if (landsAvailable && checkLands(center))
            return true;
        if (residenceAvailable && checkResidence(chunk))
            return true;
        if (griefDefenderAvailable && checkGriefDefender(center))
            return true;

        return false;
    }

    private static boolean checkTowny(Location loc) {
        try {
            Object townBlock = townyGetTownBlock.invoke(townyApiInstance, loc);
            return townBlock != null;
        } catch (Exception e) { return true;
        }
    }

    private static boolean checkLands(Location loc) {
        try {
            Object area = landsGetAreaByLoc.invoke(landsApiInstance, loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            return area != null;
        } catch (Exception e) { return true;
        }
    }

    private static boolean checkResidence(Chunk chunk) {
        try {
            java.util.Map<?,?> residences=(java.util.Map<?,?>)residenceManager.getClass().getMethod("getResidences").invoke(residenceManager);
            long x=(long)chunk.getX()*16,z=(long)chunk.getZ()*16;
            for(Object residence:residences.values()) {
                Object[] areas=(Object[])residence.getClass().getMethod("getAreaArray").invoke(residence);
                for(Object area:areas) {
                    Location low=(Location)area.getClass().getMethod("getLowLocation").invoke(area);
                    Location high=(Location)area.getClass().getMethod("getHighLocation").invoke(area);
                    if(low.getWorld()!=null&&low.getWorld().getUID().equals(chunk.getWorld().getUID())
                        &&low.getBlockX()<=x+15&&high.getBlockX()>=x&&low.getBlockZ()<=z+15&&high.getBlockZ()>=z)return true;
                }
            }
            return false;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[DyClaim] Residence overlap query failed; acquisition blocked: "+e.getClass().getSimpleName());
            return true;
        }
    }
    private static boolean checkGriefDefender(Location loc) {
        try {
            Object manager=griefDefenderGetClaimAt.invoke(griefDefenderCore,loc.getWorld().getUID());
            if(manager==null)return false;
            java.util.Map<?,?> chunks=(java.util.Map<?,?>)manager.getClass().getMethod("getChunksToClaimsMap").invoke(manager);
            java.util.Set<Object> unique=new java.util.HashSet<>();
            for(Object value:chunks.values())unique.addAll((java.util.Collection<?>)value);
            int x=(loc.getBlockX()>>4)*16,z=(loc.getBlockZ()>>4)*16;
            for(Object claim:unique) {
                if((boolean)claim.getClass().getMethod("isWilderness").invoke(claim))continue;
                Object min=claim.getClass().getMethod("getLesserBoundaryCorner").invoke(claim),max=claim.getClass().getMethod("getGreaterBoundaryCorner").invoke(claim);
                int minX=(int)min.getClass().getMethod("getX").invoke(min),minZ=(int)min.getClass().getMethod("getZ").invoke(min);
                int maxX=(int)max.getClass().getMethod("getX").invoke(max),maxZ=(int)max.getClass().getMethod("getZ").invoke(max);
                if(minX<=x+15&&maxX>=x&&minZ<=z+15&&maxZ>=z)return true;
            }
            return false;
        } catch (Exception e) { return true;
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


