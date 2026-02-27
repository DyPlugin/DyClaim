# Commands

All DyClaim commands support both English and Turkish. Players see tab completion in their game language.

---

## Player Commands

| Command | Aliases (TR) | Description |
|---------|-------------|-------------|
| `/claim` | — | Claim the chunk you're standing on |
| `/claim sell` | `/claim sat` | Sell your current claim |
| `/unclaim` | — | Remove your claim (with confirmation) |
| `/claim see` | `/claim gör` | Show chunk borders with particles |
| `/claim info` | `/claim bilgi` | View detailed claim information |
| `/claim list` | `/claim liste` | List all your claims |
| `/claim pvp` | — | Toggle PvP in your claim |
| `/claim explosion` | `/claim patlama` | Toggle explosions in your claim |
| `/claim mob` | — | Toggle mob spawning in your claim |
| `/claim trust <player>` | `/claim güven <player>` | Trust a player in your claim |
| `/claim untrust <player>` | `/claim güvensil <player>` | Remove trust from a player |
| `/claim trustlist` | `/claim güvenliste` | View trusted players in your claim |
| `/claim help` | `/claim yardım` | Show help menu |
| `/onayla` or `/confirm` | — | Confirm a pending action |
| `/reddet` or `/cancel` | — | Cancel a pending action |

---

## Admin Commands

All admin commands require `dyclaim.admin` permission (default: OP).

| Command | Aliases (TR) | Description |
|---------|-------------|-------------|
| `/claim admin enable` | `/claim admin aç` | Enable claiming server-wide |
| `/claim admin disable` | `/claim admin kapat` | Disable claiming server-wide |
| `/claim admin delete <player> [all]` | `/claim admin sil <player> [tümü]` | Delete a player's claim (or all) |
| `/claim admin give <player>` | `/claim admin ver <player>` | Give your current chunk to a player |
| `/claim admin price <amount>` | `/claim admin fiyat <amount>` | Set claim price |
| `/claim admin cooldown <seconds>` | — | Set claim cooldown |
| `/claim admin prefix <prefix>` | — | Set chat prefix (empty = reset) |
| `/claim admin economy enable/disable` | `/claim admin ekonomi aç/kapat` | Toggle economy requirement |
| `/claim admin pvp enable/disable [all]` | `/claim admin pvp aç/kapat [tümü]` | Toggle PvP for claim or all |
| `/claim admin explosion enable/disable [all]` | `/claim admin patlama aç/kapat [tümü]` | Toggle explosions for claim or all |
| `/claim admin mob enable/disable [all]` | `/claim admin mob aç/kapat [tümü]` | Toggle mob spawning for claim or all |
| `/claim admin bulksell <player>` | `/claim admin toplusat <player>` | Sell all claims of a player with refund |
| `/claim admin pricediff [old_price]` | `/claim admin farkver [eski_fiyat]` | Refund price difference to all owners |
| `/claim admin lang <auto/en/tr>` | `/claim admin dil <auto/en/tr>` | Set server language |
| `/claim admin blacklist add/remove <world>` | `/claim admin karaliste ekle/çıkar <dünya>` | Manage world blacklist |
| `/claim admin reload` | `/claim admin yenile` | Reload configuration |
