package dev.dyclaim.api;

import dev.dyclaim.DyClaim;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.*;

/**
 * Stable entry point for other plugins. Add {@code softdepend: [DyClaim]} and check {@link #isAvailable()} first.
 * <p>
 * Lookups are read-only and return detached snapshots. Since 1.3.1 a small write API is available for estate and
 * economy plugins: {@link #transferClaim}, {@link #setTrust} and {@link #removeTrust}. Every claim change, made here or
 * by players, is announced through the events in {@code dev.dyclaim.api.event} after it is committed.
 * <p>
 * Thread safety: all methods may be called from any thread; writes take the same lock as DyClaim's own claim changes
 * and are saved in the background. On Folia, call the write methods on the region thread that owns the claim's chunk
 * (or schedule them there) so they stay ordered with players' own changes to that claim. Events are delivered on the
 * calling thread when it is a server thread, otherwise on the next global tick.
 * <p>
 * The write methods do not check DyClaim's player-facing rules (claim limits, prices, {@code trust.maximum-duration-days},
 * feature toggles); the calling plugin decides who may do what.
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
    /** The claim with this id ({@link Claim#getId()}), or empty when it does not exist (any more). */
    public static Optional<Claim> getClaimById(UUID id){
        Objects.requireNonNull(id,"id");
        return Optional.ofNullable(ClaimView.of(plugin().getClaimManager().getById(id)));
    }
    /**
     * Makes {@code newOwner} the owner of the claim. The new owner may be offline. Like {@code /claim transfer}, the
     * coowner, every trust grant, the market price, the claim message and visitor bans are cleared, and a duplicate claim
     * name of the new owner is dropped. Fires {@link dev.dyclaim.api.event.ClaimTransferEvent}.
     *
     * @param newOwnerName stored as the owner name; the UUID text is used when null or blank
     * @return false when the claim does not exist, is a server claim, already belongs to {@code newOwner}, is locked by a
     *         running DyClaim payment, or claim writes are disabled
     */
    public static boolean transferClaim(UUID claimId,UUID newOwner,String newOwnerName){
        Objects.requireNonNull(claimId,"claimId");Objects.requireNonNull(newOwner,"newOwner");
        dev.dyclaim.manager.ClaimManager manager=plugin().getClaimManager();
        synchronized(manager){
            dev.dyclaim.model.ClaimData claim=manager.getById(claimId);
            if(claim==null||!manager.isWritable()||claim.isServerClaim()||dev.dyclaim.model.ClaimData.SERVER.equals(newOwner)||claim.getOwnerUUID().equals(newOwner))return false;
            try{manager.transfer(claim,newOwner,newOwnerName);return true;}
            catch(IllegalStateException changedOrLocked){return false;}
        }
    }
    /**
     * Grants {@code player} trust with exactly {@code rights}, replacing any grant the player already has (rights are
     * not merged). Right names are those of {@code /claim trustperm}: build, containers, doors, trapdoors, redstone,
     * entities, teleport. A visitor ban of the player on this claim is lifted. Fires
     * {@link dev.dyclaim.api.event.ClaimTrustChangeEvent} with {@code trusted = true}.
     *
     * @param expiresAt epoch milliseconds when the grant ends, or 0 for permanent
     * @return false when the claim does not exist, the player is its owner or coowner, {@code rights} is empty or has an
     *         unknown name, {@code expiresAt} is negative or already passed, or claim writes are disabled
     */
    public static boolean setTrust(UUID claimId,UUID player,Set<String> rights,long expiresAt){
        Objects.requireNonNull(claimId,"claimId");Objects.requireNonNull(player,"player");Objects.requireNonNull(rights,"rights");
        dev.dyclaim.manager.ClaimManager manager=plugin().getClaimManager();
        synchronized(manager){return manager.setTrust(manager.getById(claimId),player,rights,expiresAt);}
    }
    /**
     * Removes the player's trust grant, timed or permanent. Fires {@link dev.dyclaim.api.event.ClaimTrustChangeEvent}
     * with {@code trusted = false}.
     *
     * @return false when the claim does not exist, the player had no grant, or claim writes are disabled
     */
    public static boolean removeTrust(UUID claimId,UUID player){
        Objects.requireNonNull(claimId,"claimId");Objects.requireNonNull(player,"player");
        dev.dyclaim.manager.ClaimManager manager=plugin().getClaimManager();
        synchronized(manager){return manager.removeTrust(manager.getById(claimId),player);}
    }
    /**
     * Rights of the player's own active trust grant on the claim (unmodifiable). Empty when the claim does not exist, the
     * player has no grant or it has expired. Owners, coowners, clan and group trust are not included; use
     * {@link Claim#allows(UUID, String)} for the full decision.
     */
    public static Set<String> getTrustRights(UUID claimId,UUID player){
        Objects.requireNonNull(claimId,"claimId");Objects.requireNonNull(player,"player");
        dev.dyclaim.model.ClaimData claim=plugin().getClaimManager().getById(claimId);
        return claim==null?Set.of():claim.activeTrustRights(player);
    }
    public static Collection<Claim> getAllClaims(){
        return plugin().getClaimManager().getAllClaims().values().stream().map(ClaimView::of).toList();
    }
}
