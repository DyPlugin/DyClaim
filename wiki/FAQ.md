# FAQ

## General

**Q: Does DyClaim require Vault?**
No. Vault is optional. If `economy.enabled` is set to `false` in config, claiming is free and Vault is not needed.

**Q: Which Minecraft versions are supported?**
1.20.x through 1.21.x on Paper, Purpur, Spigot, and Bukkit.

**Q: Does it work with Bedrock players?**
Yes. If Floodgate is installed, Bedrock players get block-based chunk visualization instead of particles.

---

## Claims

**Q: How many chunks can a player claim?**
Configurable via `claiming.max-claims-per-player` in config.yml. Default is 10.

**Q: Can I claim in the Nether or End?**
By default, yes. To disable, add the world to the blacklist:
`/claim admin blacklist add world_nether`

**Q: Can trusted players toggle PvP/explosions?**
No. Only the claim owner can toggle claim settings. Trusted players can only build, break, and interact.

---

## Protection

**Q: Does PvP protection work for admin/OP players?**
Players with `dyclaim.admin.bypass` permission bypass all claim protections, including PvP. Test with a non-OP player.

**Q: What does claim protection cover?**
- Block breaking and placing
- Interacting with blocks (chests, doors, buttons, etc.)
- Entity damage (item frames, armor stands, paintings)
- Explosions (creeper, TNT, etc.)
- Fire spread and lava/water flow
- Piston movement into claims
- PvP (when disabled)

---

## Language

**Q: How does auto language work?**
When `lang: auto`, DyClaim reads each player's Minecraft client language. Turkish clients see Turkish, everyone else sees English. Tab completion also adapts.

**Q: Can I add more languages?**
Create a new `messages_xx.yml` file (e.g., `messages_de.yml`) in the plugin folder and add the language code to the plugin's language resolver. Pull requests welcome!

---

## Troubleshooting

**Q: "Message not found: xyz" appears in chat**
Your server has old cached message files. DyClaim automatically loads missing keys from the jar, but if the key is still not found, delete `messages_en.yml` and `messages_tr.yml` from the plugin folder and restart. They will be regenerated.

**Q: Action bar doesn't show when entering claims**
Check that `notification.use-actionbar` and `notification.show-enter` are both `true` in config.yml. Also, action bar only appears when claim settings **change** between chunks.

**Q: Plugin says "Unknown command" for Turkish commands**
Make sure you're using the correct Turkish characters (ğ, ü, ş, ö, ç, ı, İ). Both Turkish and English commands work regardless of language setting — they just don't appear in tab completion if it's not your active language.
