package dev.dyclaim.api.event;

import dev.dyclaim.api.Claim;
import org.bukkit.event.Event;

/** Base for DyClaim notifications. They are fired after a change is committed and cannot be cancelled. */
public abstract class ClaimEvent extends Event {
    private final Claim claim;
    protected ClaimEvent(Claim claim){this.claim=claim;}
    public Claim getClaim(){return claim;}
}
