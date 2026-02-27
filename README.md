# DyClaim — Chunk Claiming Made Simple

Protect your builds. Claim your land. Control your world.

DyClaim is a lightweight yet powerful chunk claiming plugin that gives players full control over their land. Whether you run a survival, SMP, or economy server — DyClaim has you covered.

---

## Why DyClaim?

**Zero bloat.** No complex GUIs, no unnecessary features. Just clean, fast chunk protection that works.

**Per-claim settings.** Each claim has its own PvP, explosion, and mob spawning toggles. Your players decide how their land works.

**Automatic language.** DyClaim detects each player's Minecraft language and shows commands and messages in their language. Currently supports **English** and **Turkish** out of the box.

**Works with everything.** Hooks into WorldGuard, GriefPrevention, Towny, Lands, Residence, GriefDefender, Vault, and Floodgate — all optional, all automatic.

---

## Core Features

**Claiming & Economy**
- Claim chunks with `/claim` — simple as that
- Vault economy integration with configurable prices
- Sell claims back for a refund percentage
- Automatic price difference refunds when admins adjust prices
- Configurable claim limits and cooldowns
- Works without Vault — economy features disabled automatically

**Protection**
- Full block protection (break, place, interact, pistons, fire, water/lava flow)
- Entity protection (item frames, armor stands, paintings, vehicles)
- PvP toggle per claim
- Explosion toggle per claim
- Hostile mob spawning toggle per claim

**Trust System**
- `/claim trust <player>` — let friends build in your claim
- Per-claim trust management
- Easy untrust and trust list commands

**Teleportation**
- `/claim tp <number>` — teleport to any of your claims
- Configurable warmup timer (default: 3 seconds)
- Blindness effect during warmup
- Cancelled on movement — prevents abuse
- Separate permission for fine-grained control

**Smart Action Bar**
- Towny-style action bar when entering claims
- Shows owner name and PvP status
- Only appears when settings actually change — no spam
- Fully configurable (action bar or chat, enable/disable)

**Chunk Visualization**
- Particle borders for Java players
- Block borders for Bedrock players (via Floodgate)
- Configurable particle type and display duration

**Admin Toolkit**
- Enable/disable claiming server-wide
- Delete, give, and manage claims for any player
- Set prices with automatic refunds to existing claim owners
- Toggle settings for single claims or all claims at once
- Bulk sell with owner refunds
- World blacklist
- Hot reload — no restart needed

---

## Commands

| Command | What it does |
|---------|-------------|
| `/claim` | Claim the chunk you're in |
| `/claim sell` | Sell your claim back |
| `/unclaim` | Remove your claim |
| `/claim see` | Visualize chunk borders |
| `/claim info` | View claim details |
| `/claim list` | See all your claims with coordinates |
| `/claim tp <number>` | Teleport to a claim |
| `/claim pvp` | Toggle PvP |
| `/claim explosion` | Toggle explosions |
| `/claim mob` | Toggle mob spawning |
| `/claim trust <player>` | Trust a player |
| `/claim untrust <player>` | Untrust a player |
| `/confirm` | Confirm an action |
| `/cancel` | Cancel an action |

Admin commands available under `/claim admin` — run `/claim admin help` for the full list.

---

## Permissions

| Permission | Default | Description |
|---|---|---|
| `dyclaim.player` | Everyone | All player commands |
| `dyclaim.teleport` | Everyone | Teleport to claims |
| `dyclaim.admin` | OP | All admin commands + protection bypass |

Individual permissions (`dyclaim.claim`, `dyclaim.sell`, `dyclaim.see`, `dyclaim.info`, `dyclaim.list`, `dyclaim.trust`, `dyclaim.teleport`) are also available for fine-grained control.

---

## Compatibility

| | |
|--------|------------|
| **Server** | Paper, Purpur, Spigot, Bukkit **1.20.x — 1.21.x** |
| **Java** | Java 17+ |

| Plugin | Integration |
|--------|------------|
| Vault | Economy (prices, refunds) |
| WorldGuard | Prevents claiming in WG regions |
| GriefPrevention | Prevents overlap with GP claims |
| Towny | Prevents claiming in towns |
| Lands | Prevents claiming in Lands areas |
| Residence | Prevents claiming in residences |
| GriefDefender | Prevents claiming in GD claims |
| Floodgate | Bedrock player visualization |

All integrations are **optional** and detected automatically. The plugin works perfectly without any of them.

---

## Setup

1. Drop the `.jar` into your `plugins/` folder
2. Start the server
3. Edit `plugins/DyClaim/config.yml` to your liking
4. `/claim admin reload` — done!

---

## Open Source

DyClaim is fully open source. Contributions, issues, and feature requests are welcome on GitHub.
