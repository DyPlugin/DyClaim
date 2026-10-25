package dev.dyclaim.api.event;

import dev.dyclaim.api.Claim;
import org.bukkit.event.HandlerList;

/** A claim was sold, unclaimed, deleted or purged. {@link #getClaim()} shows its last state. */
public class ClaimRemoveEvent extends ClaimEvent {
    private static final HandlerList HANDLERS=new HandlerList();
    public ClaimRemoveEvent(Claim claim){super(claim);}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
