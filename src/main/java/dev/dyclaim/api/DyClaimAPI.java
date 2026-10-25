package dev.dyclaim.api;

import dev.dyclaim.DyClaim;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;

/**
 * Stable entry point for other plugins. Add {@code softdepend: [DyClaim]} and check {@link #isAvailable()} first.
 * All methods are read-only; claim changes are announced through the events in {@code dev.dyclaim.api.event}.
 */
public final class DyClaimAPI {
    private DyClaimAPI() {}
    private static DyClaim plugin(){
        DyClaim plugin=DyClaim.getInstance();
        if(plugin==null||!plugin.isEnabled()||plugin.getClaimManager()==null)throw new IllegalStateException("DyClaim is not enabled");
        return plugin;
    }
    public static boolean isAvailable(){DyClaim plugin=DyClaim.getInstance();return plugin!=null&&plugin.isEnabled()&&plugin.getClaimManager()!=null;}
    public static Optional<Claim> getClaimAt(Location location){
        Objects.requireNonNull(location,"location");Objects.requireNonNull(location.getWorld(),"world");
        return getClaimAt(location.getWorld().getName(),location.getBlockX()>>4,location.getBlockZ()>>4);
    }
    public static Optional<Claim> getClaimAt(String world,int chunkX,int chunkZ){
        return Optional.ofNullable(ClaimView.of(plugin().getClaimManager().getClaimAt(world,chunkX,chunkZ)));
    }
    /** Claims the player owns or coowns, in the same order as {@code /claim list}. */
    public static List<Claim> getClaims(UUID player){
        return plugin().getClaimManager().getPlayerClaims(player).stream().map(ClaimView::of).toList();
    }
    public static int getClaimCount(UUID player){return plugin().getClaimManager().getPlayerClaimCount(player);}
    /** The player's effective total limit, including dyclaim.limit.&lt;n&gt; permissions. */
    public static int getClaimLimit(Player player){return plugin().getAcquisitionRules().limit(player);}
    /** Same decision as block placement: owners, coowners, trusted builders, clan access and active admin bypass. */
    public static boolean canBuild(Player player,Location location){
        DyClaim plugin=plugin();Objects.requireNonNull(location.getWorld(),"world");
        return plugin.getAccessManager().allows(player,plugin.getClaimManager().getClaimAt(location.getWorld().getName(),location.getBlockX()>>4,location.getBlockZ()>>4),"build");
    }
    public static Collection<Claim> getAllClaims(){
        return plugin().getClaimManager().getAllClaims().values().stream().map(ClaimView::of).toList();
    }
}
