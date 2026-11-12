# DyClaim developer API

DyClaim 1.1.0 added a small, read-only API and four notification events for other plugins. DyClaim 1.3.1 adds a write API for estate and economy plugins that sell, rent or lease claims. The API lives in the `dev.dyclaim.api` package of the normal DyClaim JAR. There is no separate API artifact.

## Setup

1. Add DyClaim as a soft dependency in your `plugin.yml`:

   ```yaml
   softdepend: [DyClaim]
   ```

2. Compile against the DyClaim JAR (for example as a local `compileOnly` file dependency). Do not shade it.
3. Call `DyClaimAPI.isAvailable()` before anything else. It is false when DyClaim is missing, disabled or still starting.

The API works the same on every supported server, including Folia.

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

Lookups never change a claim. Players and administrators change claims with DyClaim's own commands, so prices, confirmations, limits and the economy journal apply to them. The write methods below are for plugins that run their own sales or leases.

## Changing claims (1.3.1)

| Method | Effect |
|---|---|
| `getClaimById(UUID claimId)` | The claim with this id (`Claim.getId()`), or empty when it does not exist any more |
| `transferClaim(UUID claimId, UUID newOwner, String newOwnerName)` | Makes `newOwner` (who may be offline) the owner. As with `/claim transfer`, the coowner, all trust, the market price, the claim message and visitor bans are cleared, and a duplicate claim name of the new owner is dropped. `newOwnerName` is stored as the owner name; the UUID text is used when it is null or blank. Fires `ClaimTransferEvent`. |
| `setTrust(UUID claimId, UUID player, Set<String> rights, long expiresAt)` | Grants the player trust with exactly these rights and replaces any grant the player already had (rights are not merged). Right names are those of `/claim trustperm`: `build`, `containers`, `doors`, `trapdoors`, `redstone`, `entities`, `teleport`. `expiresAt` is epoch milliseconds; `0` is permanent. A visitor ban of the player on this claim is lifted. Fires `ClaimTrustChangeEvent` with `trusted = true`. |
| `removeTrust(UUID claimId, UUID player)` | Ends the player's trust, timed or permanent. Fires `ClaimTrustChangeEvent` with `trusted = false`. |
| `getTrustRights(UUID claimId, UUID player)` | The rights of the player's own active trust, unmodifiable. Empty when the claim does not exist, there is no grant or it has expired. Owners, coowners, clan and group trust are not included; use `Claim.allows(UUID, right)` for the full decision. |

The three write methods return `true` when the change was made and `false` when it was refused. They throw `IllegalStateException` while DyClaim is not available and `NullPointerException` for null arguments, like the lookups.

```java
Optional<Claim> claim = DyClaimAPI.getClaimById(claimId);
if (claim.isPresent()) {
    // sell the claim
    DyClaimAPI.transferClaim(claimId, buyer.getUniqueId(), buyer.getName());
    // lease it for seven days
    long end = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000;
    DyClaimAPI.setTrust(claimId, tenant.getUniqueId(), Set.of("build", "containers", "doors"), end);
    // lease over
    DyClaimAPI.removeTrust(claimId, tenant.getUniqueId());
}
```

### Refusals

- `transferClaim` returns `false` when the claim does not exist, is a server claim, already belongs to `newOwner`, `newOwner` is the server owner id, the claim is locked by a running DyClaim payment (market purchase, refund or similar), or claim writes are disabled.
- `setTrust` returns `false` when the claim does not exist, the player is its owner or coowner, `rights` is empty or contains an unknown name, `expiresAt` is negative or already passed, or claim writes are disabled.
- `removeTrust` returns `false` when the claim does not exist, the player had no grant, or claim writes are disabled.

The write methods do not apply DyClaim's player-facing rules: claim limits, prices, `trust.maximum-duration-days` and feature toggles are not checked. The calling plugin decides who may do what, and handles payment itself.

### Threads

All methods can be called from any thread. Writes take the same lock as DyClaim's own claim changes and are saved in the background. On Folia, call the write methods on the region thread that owns the claim's chunk (or schedule them there) so they stay in order with players' own changes to that claim.

Events are delivered on the calling thread when it is a server thread, and on the next global tick otherwise.

## Events

All events are in `dev.dyclaim.api.event`. They are fired after the change has been saved in memory, so the claim state already matches the event. They cannot be cancelled. Delivery happens on a server thread: the main thread on Bukkit/Paper, or the thread that made the change on Folia.

| Event | When |
|---|---|
| `ClaimCreateEvent` | A chunk became claimed. `getCause()` is `PLAYER`, `ADMIN` (admin give) or `IMPORT`. `getPlayer()` is null for imports. |
| `ClaimRemoveEvent` | A claim was sold, unclaimed, deleted or purged. `getClaim()` shows its last state. |
| `ClaimTransferEvent` | Ownership moved through a transfer, a market purchase, an admin give or `transferClaim`. Provides the previous and the new owner. |
| `ClaimTrustChangeEvent` | A player gained or lost trust, including through `setTrust` and `removeTrust`. A visitor ban also removes trust and fires this event. |

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
