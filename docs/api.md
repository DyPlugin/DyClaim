# DyClaim developer API

DyClaim 1.1.0 adds a small, read-only API and four notification events for other plugins. The API lives in the `dev.dyclaim.api` package of the normal DyClaim JAR. There is no separate API artifact.

## Setup

1. Add DyClaim as a soft dependency in your `plugin.yml`:

   ```yaml
   softdepend: [DyClaim]
   ```

2. Compile against the DyClaim JAR (for example as a local `compileOnly` file dependency). Do not shade it.
3. Call `DyClaimAPI.isAvailable()` before anything else. It is false when DyClaim is missing, disabled or still starting.

The API works the same on the standard and the Folia build.

## Reading claims

```java
import dev.dyclaim.api.Claim;
import dev.dyclaim.api.DyClaimAPI;

if (DyClaimAPI.isAvailable()) {
    Optional<Claim> claim = DyClaimAPI.getClaimAt(player.getLocation());
    claim.ifPresent(c -> player.sendMessage("Owned by " + c.getOwnerName()));

    boolean canBuild = DyClaimAPI.canBuild(player, block.getLocation());
    int used = DyClaimAPI.getClaimCount(player.getUniqueId());
    int limit = DyClaimAPI.getClaimLimit(player); // Integer.MAX_VALUE means unlimited
}
```

| Method | Returns |
|---|---|
| `isAvailable()` | Whether DyClaim is enabled and ready |
| `getClaimAt(Location)` | The claim covering that block column, if any |
| `getClaimAt(String world, int chunkX, int chunkZ)` | The claim of one chunk, if any |
| `getClaims(UUID player)` | Claims the player owns or coowns, in `/claim list` order |
| `getClaimCount(UUID player)` | Number of owned and coowned claims |
| `getClaimLimit(Player)` | Effective total limit, including `dyclaim.limit.<n>` permissions |
| `canBuild(Player, Location)` | Same decision as block placement: owner, coowner, trusted builder, clan access or active admin bypass |
| `getAllClaims()` | Every claim on the server |

Every method except `isAvailable()` throws `IllegalStateException` while DyClaim is not available.

### The `Claim` view

`Claim` is a read-only snapshot taken when you asked for it. It does not change afterwards; ask again for current values. It provides:

- `getId()`, `getWorld()`, `getChunkX()`, `getChunkZ()`
- `getOwner()`, `getOwnerName()`, `getCoowner()`, `getName()`, `getClaimedAt()`
- `getTrustedPlayers()`, which returns active trust including unexpired temporary trust
- `allows(UUID, right)`, with the rights `build`, `containers`, `doors`, `trapdoors`, `redstone`, `entities` and `teleport`

Claims cannot be changed through the API. Players and administrators change them with DyClaim's own commands, so prices, confirmations, limits and the economy journal always apply.

## Events

All events are in `dev.dyclaim.api.event`. They are fired after the change has been saved in memory, so the claim state already matches the event. They cannot be cancelled. Delivery happens on a server thread: the main thread on Bukkit/Paper, or the thread that made the change on Folia.

| Event | When |
|---|---|
| `ClaimCreateEvent` | A chunk became claimed. `getCause()` is `PLAYER`, `ADMIN` (admin give) or `IMPORT`. `getPlayer()` is null for imports. |
| `ClaimRemoveEvent` | A claim was sold, unclaimed, deleted or purged. `getClaim()` shows its last state. |
| `ClaimTransferEvent` | Ownership moved through a transfer, a market purchase or an admin give. Provides the previous and the new owner. |
| `ClaimTrustChangeEvent` | A player gained or lost trust. A visitor ban also removes trust and fires this event. |

```java
@EventHandler
public void onClaim(ClaimCreateEvent event) {
    Claim claim = event.getClaim();
    getLogger().info(claim.getOwnerName() + " claimed " + claim.getChunkX() + ", " + claim.getChunkZ());
}
```

DyClaim builds event objects only when at least one listener is registered, so unused events cost nothing.

## Compatibility promise

The types in `dev.dyclaim.api` and `dev.dyclaim.api.event` are the supported surface. They are kept compatible within the 1.x line. Everything else is internal and may change in any release, including `dev.dyclaim.api.ApiEvents`.
