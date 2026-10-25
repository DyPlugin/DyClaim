package dev.dyclaim.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Read-only view of one claimed chunk. Values reflect the claim when the view was created. */
public interface Claim {
    UUID getId();
    String getWorld();
    int getChunkX();
    int getChunkZ();
    UUID getOwner();
    String getOwnerName();
    Optional<UUID> getCoowner();
    Optional<String> getName();
    long getClaimedAt();
    /** Players with active trust, including temporary trust that has not expired. */
    Set<UUID> getTrustedPlayers();
    /** True for the owner, the coowner and players with the given trust right (build, containers, doors, trapdoors, redstone, entities, teleport). */
    boolean allows(UUID player, String right);
}
