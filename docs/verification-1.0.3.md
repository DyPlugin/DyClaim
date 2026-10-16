# 1.0.3 verification

Checks performed on 2026-10-16. These results apply to the 1.0.3 artifacts, separately from historical release tests.

## Automated checks

- 284 JUnit tests passed, zero failures/errors/skips, including the reused backend scheduling/concurrency regressions and new command/outline tests.
- New query/geometry tests were observed failing before implementation. Command workflow tests exposed missing filtered rows/access/diagnostics/TAB, then passed after implementation and complete fixture setup.
- The joined Bedrock test initially received 60 single-chunk packets instead of the expected 92 joined-rectangle packets. The new rendering flow passed the test and verifies each terrain read occurs on that chunk's region.
- Independent read-only review found one Important issue: joined Bedrock borders could exceed the configured display radius. A current-player queued-callback regression failed before the fix and passed after distance filtering; the complete suite then passed.
- `gradlew.bat test verifyPlatformArtifacts --no-daemon` passed after the final code change. A preceding clean build also completed compilation and packaging; its single obsolete `v` display expectation was updated to the newly requested label format.
- Both JARs contain plugin version `1.0.3`, Java 17 class files and the correct backend marker. Only the Folia JAR declares `folia-supported: true`. The artifact check rejects archived proxy classes/resources.

## Isolated real-server checks

Only loopback-bound disposable environments were used. Minecraft protocol clients were used, not human visual clients.

| Runtime | Scenarios actually exercised |
|---|---|
| Purpur 1.20.4 build 2176, Java 21 | Free claim acquisition; names; name/sale filters preserving teleport index; authorized/denied player diagnostics; temporary personal trust with denied container access; joined Java particle packet coordinates; English and Turkish/ASCII TAB; global language; reload; normal stop/start and ownership/name/index persistence |
| Folia 1.20.4 build 31, Java 21 | The same new command/particle/language/reload scenarios; normal shutdown/start with ownership/name/index persistence; console diagnostics and reload via loopback RCON |
| Folia 26.3 build 3 ALPHA, Java 25 | Full plugin startup; console diagnostics; reload; repeated diagnostics; normal shutdown. No modern-client gameplay test |

The first Purpur script attempted `/claim see` while claim acquisition's automatic ten-second display was still active. This was a test sequencing failure, not a plugin fix: the remaining particle/TAB/language/reload checks were run after that display expired. Earlier passed scenarios were retained separately. Folia's persistence check was repeated after an actual normal stop/start; connecting twice to a running server was not treated as restart evidence.

The host produced pre-existing Windows OSHI/performance-counter warnings during modern-server startup. These were separate from DyClaim startup and console results.

## Limits

No live Vault economy provider, SimpleClans integration, Floodgate/Bedrock client, complete piston/fluid/entity matrix or every intermediate Minecraft patch was exercised for 1.0.3. Bedrock rendering and cross-region ownership constraints have automated API-fixture coverage, not live Bedrock acceptance. Prior Folia prototype gameplay checks are historical evidence and are not represented as a complete new 1.0.3 matrix.

No claim schema or economy-provider dependency was upgraded. Existing custom message/configuration preservation and persistence regressions remain in the automated suite. Source tags and release files must be matched before publication; see the release's SHA-256 checksums.
