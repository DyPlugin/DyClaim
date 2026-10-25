package dev.dyclaim.api.event;

import dev.dyclaim.api.Claim;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** Ownership moved through a transfer, a market purchase or an admin give. */
public class ClaimTransferEvent extends ClaimEvent {
    private static final HandlerList HANDLERS=new HandlerList();
    private final UUID previousOwner,newOwner;
    public ClaimTransferEvent(Claim claim,UUID previousOwner,UUID newOwner){super(claim);this.previousOwner=previousOwner;this.newOwner=newOwner;}
    public UUID getPreviousOwner(){return previousOwner;}
    public UUID getNewOwner(){return newOwner;}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
