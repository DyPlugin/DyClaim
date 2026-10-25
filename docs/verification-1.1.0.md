# 1.1.0 verification

Checks performed on 2026-10-23 and 2026-10-24. These results apply to the 1.1.0 artifacts, separately from historical release tests.

## Automated checks

- 328 JUnit tests passed with zero failures, errors or skips. The 1.0.4 suite (299 tests) still passes unchanged; 29 tests are new.
- The new tests cover:
  - permission limits, including the switch and the highest-permission rule;
  - the API snapshot, its behaviour while DyClaim is disabled, event order, and that no event objects are built without listeners;
  - claim messages: set, clear, invalid input, the switch, and the greeting on entry;
  - visitor bans: ban, unban, ban list, entry blocking, exemptions, durations, the per-claim maximum, the switch, and clearing on transfer;
  - claim storage round-trip with messages and bans;
  - the import plan, a real GriefPrevention API mock, a reflective ClaimChunk stand-in and the import command;
  - map area merging, idle integrations, map refreshes that skip unchanged maps and send only differences, and PlaceholderAPI values;
  - background saving, including that an older copy never replaces a newer one;
  - MiniMessage tags next to `&` codes, inserted values shown as typed, and claim-message tags applied only when colours are allowed;
  - key and placeholder parity across all five message files, client-locale selection, configuration validation, plugin.yml, the relocated bStats package and the help-row switches.
- `gradlew.bat clean test foliaJar verifyPlatformArtifacts --offline` passed. Both JARs contain plugin version `1.1.0` and the correct backend marker. Only the Folia JAR declares `folia-supported: true`.
- SHA-256 of the final JARs:
  - `DyClaim-1.1.0.jar`: `a2b62a2908aa1ec75ae23e1cdf7f203a45a42327bf882609df760d65929edcfe`
  - `DyClaim-1.1.0-Folia.jar`: `320c6c4069ed18748fada3fa235899ca383cc0e6fcb38c7a09592914f149a972`

The final JARs differ from the first test build in four ways. Three were found by the tests below: clearer German trust messages and claim-message error text, map refreshes that run in the background and send only differences, and background saving of claim changes that do not involve money. The fourth is MiniMessage support. Checks marked "first build" ran before these changes. After them, the following were repeated with the final JARs, and all passed: the economy, market, clan and API scenario (32/32), both upgrade tests (12/12 each), the map test on Purpur (12/12) and Folia (6/6), PlaceholderAPI on Folia (4/4), the saving test (6/6), the MiniMessage test (6/6) and the version matrix on Paper 1.21.11, Paper 26.3, Spigot 26.3 and Folia 26.3 (10/10 each).

## Isolated real-server checks

Only loopback-bound disposable servers were used, with Minecraft protocol clients rather than human visual clients. bStats was disabled on every test server except the one bStats check below.

### Purpur 1.20.4 with maps, placeholders and permissions (first build)

In-place upgrade of the 1.0.4 test server with PlaceholderAPI 2.12.3, LuckPerms 5.5.71, BlueMap 5.16, Dynmap 3.7-beta-8 and squaremap 1.2.3 installed. 18 of 18 checks passed:

- 1.0.4 claims survived the upgrade.
- Diagnostics listed the PlaceholderAPI, BlueMap, Dynmap and squaremap hooks.
- PlaceholderAPI returned `2/10`, the owner, the claim name, `owner` and `true` for a player standing in their claim.
- With LuckPerms, `dyclaim.limit.1` stopped a second claim and `/claim list` showed `1/1`. With limits 1 and 3, the higher one won.
- A visitor walking in saw the claim message, and `/claim info` showed it.
- A banned visitor could not walk into the claim, the ban list showed them, and a banned player who was inside was moved out to the neighbouring unclaimed chunk.
- German, Russian and Spanish help appeared after `/claim lang de|ru|es`.
- Import explained that a missing source plugin is not installed.
- After `/claim admin reload` the hooks were active again.
- squaremap's `markers.json` and Dynmap's marker file contained the claims.

### BlueMap (first build)

On 2026-10-24 the server owner accepted BlueMap's Mojang download terms for the test servers. BlueMap's web server was bound to 127.0.0.1.

- **Purpur 1.20.4, BlueMap 5.16: 10 of 10.**
  - BlueMap showed a toggleable "Claims" layer.
  - Four claims became three areas: the neighbouring Home and Farm chunks were joined into one area covering blocks 0-32 x 0-16, labelled "BridgeAdmin - Farm, Home".
  - The Nether map had an empty Claims layer, and the web server listened only on 127.0.0.1.
  - Diagnostics listed BlueMap. A claim made by a player appeared within one refresh.
  - After reload, `hidden-worlds: [world]` emptied the overworld layer, `integrations.maps.bluemap: false` removed the layer, and turning it back on restored all four areas.
  - In a browser, the BlueMap page showed the four areas with one colour per owner.
- **Folia 1.20.4, BlueMap 5.5 (its Folia build): 6 of 6.** The claims appeared as one area with the right bounds and label, diagnostics listed BlueMap, the layer came back after reload, and the log had no DyClaim errors.

### Economy, market, clans and API (first build)

Purpur 1.20.4 with Vault 1.7.3, EssentialsX 2.21.2, SimpleClans 2.19.2, LuckPerms, PlaceholderAPI and a small test plugin that listens to the DyClaim API. 32 of 32 checks passed:

- **Economy.** DyClaim found the economy although EssentialsX loads after DyClaim. A claim cost $1,500, selling it refunded $900 (60%), and a second claim cost $1,500 again.
- **Market.** A claim listed for $1,000 cost the buyer $1,000 and paid the seller $950 (5% tax). The sale cleared the old owner's claim message and visitor bans, the banned player could walk in afterwards, and the transaction journal recorded the purchase.
- **Coloured claim message.** With `claim-messages.allow-colors: true`, `&aGreen &lShop` reached the visitor in colour.
- **Timed bans.** During a 1-minute ban, both a `/tp` into the claim and walking in were refused. After one minute the player could walk in and the ban was no longer listed.
- **SimpleClans.** Without clan trust a clan member could not break blocks. `/claim trust clan:tst` found the clan, `/claim trustlist` showed it, the member could then build, and `/claim untrust clan:tst` blocked them again.
- **API.** The test plugin saw `isAvailable()`, `ClaimCreateEvent` (cause PLAYER), `ClaimRemoveEvent` after a sale, `ClaimTrustChangeEvent` for trust and for a ban removing trust, and `ClaimTransferEvent` after the market sale. A query returned the right count, limit, claim and `canBuild`. All events arrived on the main thread.

### Import with trusted players (first build)

50 real claims were created in the source plugin, trust was given with the source plugin's own commands, then DyClaim imported them.

- **GriefPrevention 16.18.3:** 50 chunks and 4 trusted players imported.
  - `/trust` and `/permissiontrust` became full trust.
  - `/containertrust` became containers, doors, entities, redstone and trapdoors.
  - `/accesstrust` became doors, redstone and trapdoors.
  - Trust stayed on the claim where it was given.
- **ClaimChunk 0.0.25-FIX3:** 50 chunks and 2 trusted players.
  - Full access became build, containers, doors, entities, redstone and trapdoors.
  - Doors-only access became doors and trapdoors.

The plain 50-claim imports from both plugins also passed earlier: the first run imported all 50, and a second run imported nothing new.

### Upgrading from older versions

Real data was made with the old JAR (claims, trust and, for 1.0.3, a claim name). One message was then edited by hand, and the same server was started with 1.1.0.

- **Beta: 12 of 12.**
  - Claims and trust were kept.
  - The Beta claim file was backed up before migration.
  - The untouched Beta message showed the 1.1.0 wording; the edited message was kept.
  - Claim messages, bans and German worked on the old claims.
  - `config.yml` gained the new settings and the new language files were created.
- **1.0.3: 12 of 12.** The same checks passed, and the claim name was kept.

### Version matrix

A fresh flat server per version, with only DyClaim (and ViaVersion/ViaBackwards 5.12.2 where the test client is older than the server). On each server, 10 of 10 checks passed:

- DyClaim enabled and diagnostics ran.
- A player claimed a chunk. A visitor could not break a block, and the owner could.
- The visitor saw the claim message when walking in. After a ban, the visitor could not walk in.
- German help worked, and console reload worked.
- The log had no DyClaim errors.

| Server | Java |
|---|---|
| Paper 1.20.1, 1.20.6, 1.21.1, 1.21.4, 1.21.8, 1.21.11 | 21 |
| Paper 26.1.2, 26.2, 26.3 | 25 |
| Purpur 26.2 | 25 |
| Spigot 26.2, Spigot 26.3 (built with BuildTools) | 25 |
| Folia 26.2, Folia 26.3 (Folia JAR) | 25 |

Paper 1.20.1 and 1.20.6 hung after saving all worlds, like the console-library issue below, and were ended by the runner. All other matrix servers stopped with exit code 0. Paper 26.3 writes `white-list=true` into a new `server.properties`, so the test sets it to `false`.

### PlaceholderAPI on Folia 1.20.4

A player on the Folia server read `2/10|BridgeAdmin|Home|owner|true|true|2` inside their claim and `[false][][]` outside it. 4 of 4 checks passed.

### bStats

One test server sent real data with logging turned on. 6 of 6 checks passed:

- The submission used plugin ID 34849 and plugin version 1.1.0.
- It contained the charts: claims, economy, language_mode, map_plugins, placeholderapi and platform.
- It contained no player UUIDs, names or claim locations.
- bStats accepted it.

This test server appears once in the public DyClaim statistics.

### Large servers

Purpur 1.20.4 with BlueMap, Dynmap and squaremap, `refresh-seconds: 10`, and generated claim files. The table shows the longest server tick reported by `/mspt` over about one minute.

| Claims | Map areas | Map layers off | Map layers on, first test build | Map layers on, final build |
|---|---|---|---|---|
| 10,000 in blocks | 400 | 5.3 ms | 33.9 ms | 5.8 ms |
| 49,807 scattered (worst case) | 49,586 | 4.8 ms | 685 ms | 19.8 ms |

- **What changed.** The first test build redrew every marker on the main thread at every refresh. Measured alone, Dynmap took 492 ms, squaremap 183 ms and BlueMap 114 ms for the worst case. The final build:
  - works out areas, labels and differences in the background;
  - skips refreshes in which nothing changed;
  - sends the map plugins only added or removed areas.
- **After one new claim** on the 50,000-claim server, each map received a single area. The live map test confirmed that new claims appear and sold claims disappear on all three maps.
- **At start-up**, drawing all 49,586 areas once still took about 0.6 s in Dynmap and 0.2 s in squaremap.
- **Saving.** Buying a claim saves the claim file immediately, so a payment can never be recorded without its claim. With 50,000 claims (a 24 MB file) one purchase took between 17 and 650 ms, depending on how quickly the disk confirmed the write. This was already the case in 1.0.4. Other claim changes are now written in the background.

### Background saving

- A claim message was on disk 3 seconds after it was set, even though the server process was then killed.
- A claim made right before `/stop` was saved.
- Both loaded after a restart, with no write errors.

6 of 6 checks passed. A unit test also checks that an older copy finishing later never replaces a newer one.

### MiniMessage on Purpur 1.20.4 (final JAR)

Purpur 1.20.4 build 2176 on Java 21, with EssentialsX, Vault and LuckPerms, and two protocol clients. **6/6 checks passed:**

- a MiniMessage gradient in `messages_en.yml` arrived as hex colours;
- with `claim-messages.allow-colors: false`, a claim message `<red>Hi there` was shown as typed;
- with `allow-colors: true`, `<red>Red &aGreen` was shown in red and green;
- a claim named `<bold>Base` was shown as typed in `/claim list`;
- no DyClaim or Adventure errors were logged.

On Spigot 26.3 the version matrix above passed with the same JAR; Spigot ships no Adventure, so only `&` codes apply there.

### Folia 1.20.4 and shutdown

The 1.1.0 Folia JAR started with existing claims and PlaceholderAPI, and ran diagnostics, German help and reload from the console.

On this host the JVM sometimes stays running after all worlds were saved, as already seen for 1.0.4 with and without DyClaim. A thread dump from one such hang (Folia with BlueMap) shows the Log4j console appender stuck in `jansi` `isatty`, with no DyClaim or BlueMap code in any thread. The test runners end such a process only after the world-save log lines.

## Language review

German, Spanish and Russian messages were reviewed for meaning, placeholders and tone.

- The German trust messages were reworded from "ist vertraut" to "hat Zugriff auf diesen Claim".
- The claim-message error now reads correctly in all five languages, whether colour codes are allowed or not.

This was a careful review, not a review by native speakers.

## Comparison benchmark

The four-plugin, 50-claim comparison was repeated with the 1.1.0 JAR. See [the benchmark report](benchmark-50-claims.md).

## Limits

The following were not exercised for 1.1.0:

- a Floodgate/Bedrock client;
- Minecraft versions not listed in the version matrix above.
