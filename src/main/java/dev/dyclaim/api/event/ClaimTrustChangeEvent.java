package dev.dyclaim.api.event;

import dev.dyclaim.api.Claim;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** A player gained or lost trust in a claim. */
public class ClaimTrustChangeEvent extends ClaimEvent {
    private static final HandlerList HANDLERS=new HandlerList();
    private final UUID player;
    private final boolean trusted;
    public ClaimTrustChangeEvent(Claim claim,UUID player,boolean trusted){super(claim);this.player=player;this.trusted=trusted;}
    public UUID getPlayer(){return player;}
    public boolean isTrusted(){return trusted;}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
