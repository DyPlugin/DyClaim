# Four-plugin, 50-claim comparison

Measured on 24 October 2026 with DyClaim 1.1.0; the same procedure was first run on 10 October 2026 with DyClaim 1.0.2 and repeated on 22 October 2026 with DyClaim 1.0.4. The graphic compares a specific synthetic block-break workload. It does not measure overall CPU usage, plugin-owned RAM, full gameplay throughput or every protection feature.

## Results

All values below are milliseconds of measured processing time per 1,000 dispatched events. Numerically these equal the raw microseconds-per-event values. Values are medians of six 10-second window means; ranges are the observed min–max window means, not confidence intervals.

| Plugin | Owner allowed | Visitor blocked | 50% / 50% mixed workload |
|---|---:|---:|---:|
| DyClaim 1.1.0 | 0.677 (0.595–0.841) | 10.194 (6.647–12.506) | 5.430 (3.621–6.674) |
| GriefPrevention | 0.479 (0.369–0.696) | 12.409 (8.943–17.313) | 6.443 (4.674–9.005) |
| ClaimChunk | 1.032 (0.921–1.150) | 39.302 (28.809–49.809) | 20.167 (14.872–25.453) |
| Towny | 3.720 (3.228–4.639) | 37.772 (31.086–43.227) | 20.752 (17.169–23.641) |

The lowest observed mixed-workload median was **DyClaim**. The lowest owner-only median was **GriefPrevention**. In this run the DyClaim and GriefPrevention mixed-workload ranges overlap, and so do the ClaimChunk and Towny ranges; their order changed compared with 22 October. This does not establish superiority for all workloads or statistical significance. All windows are retained, including outliers and warmup/JIT variation; no result was removed to favor DyClaim.

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
- Balanced process order: ClaimChunk, Towny, DyClaim, GriefPrevention, GriefPrevention, DyClaim, Towny, ClaimChunk. All eight measurement starts completed. On 22 and 24 October every start, including those without DyClaim, left the JVM running after all worlds had been saved; the console library (jansi) on this host also crashed after saving in separate DyClaim-only checks. The runner waited 90 seconds and, only when the "All dimensions are saved" log line was present, ended those processes, and each forced exit is recorded in the logs. This happened after the timed windows and did not change any measurement.
- System.nanoTime bracketed each 50-event batch on the main server thread. Summed durations were divided by event counts. Startup, claim creation, preparation, teleports and a five-second settling interval were outside timed batches. No subtraction of a baseline and no selective pruning of windows.
- Results can be affected by JIT, GC, host scheduling and different default feedback. They do not predict a normal server's owner/visitor traffic proportions. A denial-heavy synthetic workload emphasizes denial processing.

## Dataset adapters and settings

- DyClaim 1.1.0: existing ClaimManager API created 50 chunk claims. Ownership limit 100, update checker disabled; other shipped protection/message defaults retained, including the new claim messages and visitor bans (no message or ban was set). bStats was disabled for every plugin on the test server so test runs do not appear in public statistics. The tested JAR is the 1.1.0 build.
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

All 24 window values, raw reports, configurations and the test helper are kept with the local benchmark files. The 10 October 1.0.2 package remains available as [DyClaim-4-plugins-comparison.zip](https://github.com/DyPlugin/DyClaim/releases/download/v1.0.2/DyClaim-4-plugins-comparison.zip). Results from the three runs are not mixed.
