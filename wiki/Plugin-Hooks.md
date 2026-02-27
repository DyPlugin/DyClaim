# Plugin Hooks

DyClaim automatically detects and integrates with the following plugins. No configuration needed — if the plugin is installed, DyClaim hooks into it.

---

## Economy

### Vault
- **What it does:** Enables economy features (claim prices, sell refunds)
- **Required?** Only if `economy.enabled: true` in config
- **Note:** You also need an economy provider (EssentialsX, CMI, etc.)

---

## Claim Overlap Prevention

DyClaim prevents players from claiming chunks that are already protected by other plugins:

### WorldGuard
- Checks for overlapping regions
- If a chunk intersects with any WorldGuard region, claiming is denied

### GriefPrevention
- Checks for existing GP claims
- Prevents claiming in already-protected areas

### Towny
- Checks for town blocks
- Uses reflection (no compile dependency)

### Lands
- Checks for Lands-protected areas
- Uses reflection (no compile dependency)

### Residence
- Checks for existing residences
- Uses reflection (no compile dependency)

### GriefDefender
- Checks for GD claims
- Uses reflection (no compile dependency)

---

## Bedrock Support

### Floodgate
- **What it does:** Detects Bedrock players for visualization
- **Effect:** Bedrock players see block-based borders instead of particle-based borders (since Bedrock doesn't render custom particles the same way)

---

## Technical Notes

- Towny, Lands, Residence, and GriefDefender hooks use **reflection**, meaning DyClaim doesn't need these plugins at compile time. This prevents version conflicts.
- WorldGuard and GriefPrevention use **direct API** (compile-time dependency).
- All hooks are checked during `/claim`. If the chunk is protected by any hook, the player sees an error message.
- Players with `dyclaim.admin.bypass` can override all hook checks.
