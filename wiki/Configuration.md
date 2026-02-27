# Configuration

All settings are in `plugins/DyClaim/config.yml`. Changes take effect after `/claim admin reload`.

---

## General

```yaml
prefix: "&7[&dDyClaim&7]"
```
Chat prefix shown before all messages. Use `&` color codes.

```yaml
lang: auto
```
Language setting. Options:
- `auto` — Detects each player's Minecraft client language
- `en` — Force English for everyone
- `tr` — Force Turkish for everyone

---

## Economy

```yaml
economy:
  enabled: true
  claim-price: 1000.0
  sell-refund-percentage: 75.0
```

| Setting | Description |
|---------|-------------|
| `enabled` | If `false`, claiming is free (no Vault needed) |
| `claim-price` | Cost to claim a chunk |
| `sell-refund-percentage` | % of claim price returned when selling (0-100) |

Requires **Vault** + an economy plugin (EssentialsX, CMI, etc.) when enabled.

---

## Claiming

```yaml
claiming:
  max-claims-per-player: 10
  claim-cooldown-seconds: 30
```

| Setting | Description |
|---------|-------------|
| `max-claims-per-player` | Maximum chunks a player can claim |
| `claim-cooldown-seconds` | Seconds between claims (anti-spam) |

---

## Protection Defaults

```yaml
protection:
  pvp-disabled: true
  explosion-disabled: true
  mob-griefing-disabled: true
```

Default protection settings for **newly created** claims. Players can toggle these per-claim with `/claim pvp`, `/claim explosion`, `/claim mob`.

---

## Notifications

```yaml
notification:
  use-actionbar: true
  show-enter: true
  show-leave: true
```

| Setting | Description |
|---------|-------------|
| `use-actionbar` | `true` = action bar (Towny-style), `false` = chat message |
| `show-enter` | Show notification when entering a claim |
| `show-leave` | Show notification when leaving a claim |

The action bar only appears when claim **settings change** between chunks (different owner or different PvP status). Moving between your own claims with the same settings won't spam notifications.

---

## Visualization

```yaml
visualization:
  particle: FLAME
  duration-seconds: 10
  bedrock-block: GLOWSTONE
```

| Setting | Description |
|---------|-------------|
| `particle` | Particle type for Java players (e.g., `FLAME`, `HEART`, `REDSTONE`) |
| `duration-seconds` | How long borders are shown |
| `bedrock-block` | Block type shown to Bedrock players via Floodgate |

---

## World Blacklist

```yaml
blacklisted-worlds:
  - world_the_end
  - world_nether
```

Worlds where claiming is disabled. Managed with `/claim admin blacklist add/remove <world>`.

---

## Update Checker

```yaml
update-checker:
  enabled: true
  modrinth-id: "dyclaim"
```

Checks Modrinth for new versions matching your server's Minecraft version. OP players receive a notification on join.
