package dev.dyclaim.model;

import java.util.*;

public class TrustGrant {
    public long expiresAt;
    public Set<String> permissions = new HashSet<>();
    public static final Set<String> RIGHTS = Set.of("build", "containers", "doors", "trapdoors", "redstone", "entities", "teleport");
    public TrustGrant() {}
    public TrustGrant(long expiresAt) { this.expiresAt = expiresAt; permissions.addAll(RIGHTS); }
    public boolean allows(String permission, long now) {
        return (expiresAt == 0 || expiresAt > now) && permissions != null && permissions.contains(permission);
    }
}
