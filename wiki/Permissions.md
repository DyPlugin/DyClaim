# Permissions

## Parent Permissions

| Permission | Default | Description |
|---|---|---|
| `dyclaim.player` | `true` (everyone) | Grants all player permissions listed below |
| `dyclaim.admin` | `op` | Grants all admin permissions + player permissions |

## Individual Permissions

### Player Permissions

| Permission | Default | What it allows |
|---|---|---|
| `dyclaim.claim` | `true` | Claim chunks |
| `dyclaim.sell` | `true` | Sell owned claims |
| `dyclaim.see` | `true` | Visualize chunk borders |
| `dyclaim.info` | `true` | View claim information |
| `dyclaim.list` | `true` | List owned claims |
| `dyclaim.trust` | `true` | Trust/untrust players |

### Admin Permissions

| Permission | Default | What it allows |
|---|---|---|
| `dyclaim.admin` | `op` | Access to all `/claim admin` commands |
| `dyclaim.admin.bypass` | `op` | Bypass all claim protections (break, place, interact, PvP) |

## Notes

- `dyclaim.player` includes all individual player permissions. If you give a player `dyclaim.player`, they get `claim`, `sell`, `see`, `info`, `list`, and `trust`.
- `dyclaim.admin` includes `dyclaim.player` + `dyclaim.admin.bypass`.
- Players with `dyclaim.admin.bypass` can interact with any claim regardless of trust. Keep this in mind when testing protection features.
