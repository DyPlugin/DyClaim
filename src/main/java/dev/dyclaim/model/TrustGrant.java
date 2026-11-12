package dev.dyclaim.model;

import java.util.*;

public class TrustGrant {
    /** Volatile: protection checks read grants without the claim's lock; {@link ClaimData} publishes a new rights set to change them. */
    public volatile long expiresAt;
    public volatile Set<String> permissions = new HashSet<>();
    public static final Set<String> RIGHTS = Set.of("build", "containers", "doors", "trapdoors", "redstone", "entities", "teleport");
    public TrustGrant() {}
    public TrustGrant(long expiresAt) { this.expiresAt = expiresAt; permissions.addAll(RIGHTS); }
    public boolean allowsExpiry(long now){return expiresAt==0||expiresAt>now;}
    public boolean allows(String permission, long now) {
        return (expiresAt == 0 || expiresAt > now) && permissions != null && permissions.contains(permission);
    }
}
