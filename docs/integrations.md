# Optional integrations and server tools

Everything on this page is optional. Each part has its own switch in `config.yml`. A switch only takes effect when the related plugin is installed. Core claiming needs none of these plugins.

## Claim limits by permission

`claim.max-claims-per-player` is the default total limit. Give players or groups a `dyclaim.limit.<number>` permission to use a different limit:

| Permission | Effect |
|---|---|
| `dyclaim.limit.5` | Total limit 5 |
| `dyclaim.limit.50` | Total limit 50 |
| `dyclaim.limit.unlimited` | No total limit |

- If a player has several, the highest one wins.
- The permission replaces the default, so it can also lower the limit for a group.
- Per-world `claim-limit` values in `claim-rules.worlds` still apply. A world without its own value uses the player's effective total.
- Coowned claims count toward the limit, as before.
- Switch: `claim.permission-limits.enabled`.

These permissions are read from your permission plugin (for example LuckPerms). Being OP does not grant them.

## Claim messages

`/claim message <text>` sets a short line that visitors see when they walk into the claim. `/claim message clear` removes it.

- Owners and coowners can set the message. They do not see it themselves.
- `claim-messages.maximum-length` sets the longest allowed message (1-128 characters, default 64).
- `claim-messages.allow-colors: false` removes `&` colour codes. Set it to `true` to allow them.
- Permission `dyclaim.message`; switch `claim-messages.enabled`.

## Visitor bans

`/claim ban <player> [30m|2h|7d|permanent]` keeps a player out of one claim. `/claim unban <player>` lifts the ban, and `/claim banlist` shows active bans.

- A banned player cannot walk or teleport into the claim. A player who is inside when banned is moved to a safe unclaimed spot nearby, using the same exit search as visitor warnings.
- Banning removes the player's trust in that claim. Trusting them again lifts the ban.
- Owners, coowners, administrators, players with active bypass and players with `dyclaim.ban.exempt` cannot be kept out.
- `claim-entry-bans.maximum-per-claim` limits bans per claim (default 50).
- Ownership transfers and market sales clear the old owner's bans and claim message.
- Permission `dyclaim.ban`; switch `claim-entry-bans.enabled`.

## PlaceholderAPI

With PlaceholderAPI installed, DyClaim registers the `dyclaim` expansion. No eCloud download is needed.

| Placeholder | Value |
|---|---|
| `%dyclaim_claims%` | Claims the player owns or coowns |
| `%dyclaim_claims_owned%` | Claims the player owns |
| `%dyclaim_limit%` | Effective total limit (`∞` when unlimited) |
| `%dyclaim_remaining%` | Claims the player can still get |
| `%dyclaim_in_claim%` | `true` / `false` for the player's current chunk |
| `%dyclaim_owner%` | Owner of the current chunk, empty when unclaimed |
| `%dyclaim_name%` | Name of the current claim, empty when none |
| `%dyclaim_pvp%` | `true` / `false` for PvP in the current claim |
| `%dyclaim_can_build%` | Whether the player may build in the current chunk |
| `%dyclaim_role%` | `owner`, `coowner`, `trusted` or `visitor` |
| `%dyclaim_total%` | Number of claims on the server |

Location-based placeholders are empty for offline players. Switch: `integrations.placeholderapi.enabled`.

## Web maps: BlueMap, Dynmap and squaremap

DyClaim draws claims as coloured areas on each installed web map.

- Each owner's neighbouring chunks are joined into a few rectangles.
- Every owner keeps the same colour.
- The hover text shows the owner and any claim names.
- Maps are refreshed every `integrations.maps.refresh-seconds` (10-3600 seconds, default 60). They are also refreshed after `/claim admin reload`.
- A refresh works out the claim areas off the main server thread and sends the map plugins only the areas that were added, removed or changed. When nothing changed, the map plugins are not touched at all. The first refresh after start-up draws every area once.
- `layer-name`, `fill-opacity`, `line-opacity` and `hidden-worlds` control how the layer looks and which worlds it covers.
- Each map plugin has its own switch: `integrations.maps.bluemap`, `.dynmap` and `.squaremap`. The master switch is `integrations.maps.enabled`.
- On the Folia build only BlueMap is used, because Dynmap and squaremap do not support Folia.

## Importing from GriefPrevention or ClaimChunk

Moving to DyClaim no longer means asking every player to claim again. Install DyClaim next to the old plugin and run:

```text
/dyclaim admin import griefprevention
/dyclaim admin import claimchunk
```

This shows a preview only: the number of source claims, the new chunks and what will be skipped. Nothing changes until you add `confirm`:

```text
/dyclaim admin import griefprevention confirm
```

Use `/dyclaim` (or `/dyca import ...`) rather than `/claim`, because GriefPrevention also registers `/claim`. The command works in the console without the leading slash.

How claims are converted:

- **GriefPrevention:** every chunk that a top-level player claim touches becomes a DyClaim chunk claim for the same owner.
  - Builder and manager trust become full trust.
  - Container trust becomes containers, entities, doors, trapdoors and redstone.
  - Access trust becomes doors, trapdoors and redstone.
  - Admin claims, subdivisions, public trust and permission-group trust are skipped.
- **ClaimChunk:** every claimed chunk is copied with its owner. Per-player flags map to the closest DyClaim rights.
- **Skipped chunks:**
  - chunks that DyClaim has already claimed;
  - chunks in blacklisted worlds;
  - chunks claimed by two different owners in the source (the first owner is kept).
- **Price and limits:**
  - Imported claims have no DyClaim purchase price, so `/claim sell` refunds nothing for them.
  - Claim limits and prices are not checked during import.
- **Safety:**
  - A backup of `claims.json` is written before the import.
  - The source plugin's data is never changed.
  - The command is safe to run again: chunks that are already claimed are skipped.

Import requires `dyclaim.admin`. Remove or disable the old plugin afterwards. While GriefPrevention stays installed, its claims still block new DyClaim claims in the same area.

## bStats

DyClaim reports anonymous usage statistics to [bStats](https://bstats.org/plugin/bukkit/DyClaim/34849): server count, player count, Minecraft and plugin versions, and a few DyClaim charts. The charts are language mode, platform, economy on/off, claim count range, PlaceholderAPI use and active map plugins.

- No player names, claim locations or chat are sent.
- Turn it off with `metrics.enabled: false`, or for every plugin in `plugins/bStats/config.yml`.

## Languages

Messages ship in English, Turkish, German, Spanish and Russian.

- `lang: auto` picks the player's saved choice (`/claim lang`) or the Minecraft client language, and falls back to English.
- An administrator can force one language with `/claim admin lang en|tr|de|es|ru`.
- Command words and TAB suggestions are translated only for Turkish. In the other languages the commands stay English.
- Message files are created in the plugin folder on first start and can be edited like the existing EN/TR files.
