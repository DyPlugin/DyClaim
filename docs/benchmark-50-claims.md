# Four-plugin, 50-claim comparison

Measured on 12 November 2026 with DyClaim 1.3.1. The same procedure was run on 10 October 2026 (DyClaim 1.0.2), 22 October (1.0.4), 24 October (1.1.0) and 7 November (1.3.0; not published because GriefPrevention had the lower mixed-workload median). The graphic compares a specific synthetic block-break workload. It does not measure overall CPU usage, plugin-owned RAM, full gameplay throughput or every protection feature.

## Results

All values below are milliseconds of measured processing time per 1,000 dispatched events. Numerically these equal the raw microseconds-per-event values. Values are medians of six 10-second window means; ranges are the observed min–max window means, not confidence intervals.

| Plugin | Owner allowed | Visitor blocked | 50% / 50% mixed workload |
|---|---:|---:|---:|
| DyClaim 1.3.1 | 0.364 (0.257–0.399) | 2.642 (2.250–3.110) | 1.505 (1.253–1.755) |
| GriefPrevention | 0.586 (0.496–0.652) | 14.659 (12.178–16.361) | 7.604 (6.375–8.507) |
| ClaimChunk | 1.647 (1.371–1.820) | 71.378 (50.032–84.875) | 36.521 (25.702–43.327) |
| Towny | 4.425 (4.090–6.076) | 48.583 (43.983–71.649) | 26.504 (24.037–38.863) |

The lowest observed medians in all three columns were **DyClaim**'s. In this run the DyClaim and GriefPrevention ranges do not overlap in any column; the ClaimChunk and Towny visitor and mixed ranges overlap. This does not establish superiority for all workloads or statistical significance. All windows are retained, including outliers and warmup/JIT variation; no result was removed to favor DyClaim.

## What changed in DyClaim since 7 November

On 7 November, DyClaim 1.3.0 measured 0.840 (owner), 11.862 (visitor) and 6.351 (mixed); GriefPrevention measured 0.408, 12.005 and 6.235. DyClaim 1.3.1 changes how the same checks are done, not what they decide:

- The claim is found from block coordinates instead of a chunk object, and the claim's own answer (owner, coowner, trust) comes before the bypass permission check. Owner checks no longer look up any permission. Claim owners and trust are read without a lock.
- The finished denial text is built once per language instead of on every event. On this Purpur server, DyClaim sends it to the player as a ready Adventure component instead of legacy text, so the server does not parse the text into a chat component again inside the timed event. This is most of the visitor-blocked difference. Spigot servers keep the text path, so this part of the result does not carry over to Spigot.
- Feedback still reaches the player. In a separate check with the same runner and clients (not part of this dataset), BenchVisitor received 40,151 chat lines with DyClaim 1.3.1 and 40,151 with GriefPrevention 16.18.3 over the same sequence of events.
- Two quick runs before the dataset were used to check the build. The analyzer reads only the eight runs below; the quick runs and the feedback check are not mixed in.

## Shared procedure

- Intel Core i5-1240P, Windows desktop host; Purpur git-Purpur-2176 (Minecraft 1.20.4), Java 21.0.12.1; `-Xms512M -Xmx1G`. The host was not a dedicated benchmark machine. No CPU pinning or controlled power profile.
- Only one of the four claiming plugins was enabled per JVM start. The same ClaimBench helper and bundled spark were present; spark background profiling was disabled. No Vault/economy provider, permissions plugin or region protection plugin.
- Same isolated loopback server (port 25622), flat-world seed 424242, view/simulation distance 3, natural mobs disabled. No production data was used.
- Fifty equal 16×16 horizontal claim areas, arranged in a 10×5 grid at chunk X=10–19, Z=10–14, with force-loaded chunks. Stone test blocks were at Y=64 and offsets 0, 8, 15: two corners and one interior point. All points were verified protected by every provider. Providers have different vertical semantics; results apply to the tested Y=64 locations.
- Real online, non-OP Mineflayer clients BenchOwner and BenchVisitor, with identical offline UUIDs and survival mode across starts. Owner was allowed; untrusted visitor was blocked. Both clients consumed normal plugin feedback without per-message console logging.
- Before timing, all 150 positions were checked with both clients. Every measured window had to yield zero owner cancellations and exactly 10,000 visitor cancellations. All 24 windows passed these assertions.
- Every tick dispatched 50 fresh owner BlockBreakEvents and 50 fresh visitor BlockBreakEvents. All claims were visited once per role per tick, with a claim permutation, rotating corner/interior locations and alternating role order.
- Events were synchronously dispatched through Bukkit PluginManager.callEvent. Blocks were not actually mined; no client mining packets, block drops or tool durability changes were generated. Measured duration includes event construction, dispatch, cancellation counting and each plugin's default denial feedback. Feedback formats and internal processing differ between plugins.
- Ten-second warmup, followed by three 10-second measurement windows per start. Two starts per plugin; six windows, 60,000 allowed + 60,000 blocked events = 120,000 measured events per plugin, 480,000 across all four.
- Balanced process order: ClaimChunk, Towny, DyClaim, GriefPrevention, GriefPrevention, DyClaim, Towny, ClaimChunk. All eight measurement starts completed. As on 22 and 24 October, starts left the JVM running after all worlds had been saved; the runner waited 90 seconds and, only when the "All dimensions are saved" log line was present, ended those processes, and each forced exit is recorded in the logs. This happened after the timed windows and did not change any measurement.
- System.nanoTime bracketed each 50-event batch on the main server thread. Summed durations were divided by event counts. Startup, claim creation, preparation, teleports and a five-second settling interval were outside timed batches. No subtraction of a baseline and no selective pruning of windows.
- Results can be affected by JIT, GC, host scheduling and different default feedback. They do not predict a normal server's owner/visitor traffic proportions. A denial-heavy synthetic workload emphasizes denial processing.

## Dataset adapters and settings

- DyClaim 1.3.1: existing ClaimManager API created 50 chunk claims. Ownership limit 100, update checker disabled; other shipped protection/message defaults retained (no claim message, visitor ban or trust was set). bStats was disabled for every plugin on the test server so test runs do not appear in public statistics. The tested JAR is the 1.3.1 build, SHA-256 `16539006f375a8c190f152046806ef6185f7e116573f3fc01fab41b981eb2d88`.
- GriefPrevention 16.18.3: DataStore.createClaim created 50 inclusive 16×16 rectangular player claims. Existing generated defaults retained.
- ClaimChunk 0.0.25-FIX3: ChunkHandler.claimChunk created 50 owned chunks; getClaimed verified count 50. Generated default config/profile/feedback retained, including enabled permission checking. Version-update network requests were not inside the timed callEvent batches; default asynchronous update/metrics tasks were not disabled.
- Towny 0.103.2.0: 50 owned residential TownBlocks inside one town, BenchOwner as resident/mayor and plot owner; visitor remained outside the town. TownyUniverse, Town, Resident and TownBlock APIs created/registered/saved the dataset. Default 16-block town block size and permission/feedback settings retained. World use of Towny was enabled; permissions were verified at all test points.
- Claim acquisition, economy and ownership-count checks were bypassed through each provider's real data API only during dataset preparation. No measured protection listener was replaced or mocked. Equivalent allow/deny outcomes do not imply equivalent overall plugin feature sets.

## Native Timings and untested plugins

This runtime disables native Timings: Timings.setTimingsEnabled always sets false. The bytecode evidence is in the original results/timings-bytecode.txt. Measurements here are direct durations, not an Aikar Timings export or CPU percentages. Lands was not measured because no Lands JAR was available. No CPU/RAM numbers were invented.

## Versions, origin and artifacts

The tested versions were obtained from the official Modrinth project version API filtered for Minecraft 1.20.4. They are the tested compatible versions, not a claim that all are the newest releases for every Minecraft version. ClaimChunk's release identifier includes FIX3; it is not relabeled as a different stable release.

- [GriefPrevention official repository](https://github.com/GriefPrevention/GriefPrevention)
- [ClaimChunk official repository](https://github.com/cjburkey01/ClaimChunk)
- [Towny official repository](https://github.com/TownyAdvanced/Towny)
- [ClaimChunk download used](https://cdn.modrinth.com/data/6Y4cwBlA/versions/ed1J9M5o/claimchunk-0.0.25-FIX3-plugin.jar)
- [Towny download used](https://cdn.modrinth.com/data/Vs77PB2W/versions/pra46LOM/Towny-0.103.2.0.jar)

## Reproduction data

All 24 window values, raw reports, configurations, the test helper and the run order with JAR hashes are kept with the local benchmark files. Complete unfiltered window values are in measurements.csv and the chart data in summary.json. The 10 October 1.0.2 package remains available as [DyClaim-4-plugins-comparison.zip](https://github.com/DyPlugin/DyClaim/releases/download/v1.0.2/DyClaim-4-plugins-comparison.zip). Results from earlier runs are kept in their own folders and are not mixed.

Tested JAR SHA-256: DyClaim-1.3.1.jar `16539006f375a8c190f152046806ef6185f7e116573f3fc01fab41b981eb2d88`, GriefPrevention-16.18.3.jar `7c6d07a2fb7a90c29742c38b9af15846e9aa667407c265b968cfe23356b18140`, ClaimChunk-0.0.25-FIX3.jar `8419796c8d0e8924cf4c0f3e44a893194f1922326cef9fd4900aa74a15cf7086`, Towny-0.103.2.0.jar `c8d7d59984eaf2606fce4192e860f7f969f3384e5a51b26b97225efbbe747c70`, ClaimBench-expanded.jar `5624999badf5600129e10a5252bff07df0ff059793027b046cf0477b63c3a69f`.
