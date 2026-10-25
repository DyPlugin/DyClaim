package dev.dyclaim.api.event;

import dev.dyclaim.api.Claim;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** A chunk became claimed by a player, an administrator or an import. */
public class ClaimCreateEvent extends ClaimEvent {
    public enum Cause { PLAYER, ADMIN, IMPORT }
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player player;
    private final Cause cause;
    public ClaimCreateEvent(Claim claim,Player player,Cause cause){super(claim);this.player=player;this.cause=cause;}
    /** The acquiring player, or null for imports. */
    public Player getPlayer(){return player;}
    public Cause getCause(){return cause;}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
