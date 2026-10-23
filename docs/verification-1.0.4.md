# 1.0.4 verification

Checks performed on 2026-10-22. These results apply to the 1.0.4 artifacts, separately from historical release tests.

## Automated checks

- 299 JUnit tests passed with zero failures, errors or skips. Before any change, the 1.0.3 suite passed 284/284 on the same machine.
- New tests cover paged player and admin help, Turkish next-page commands, `/dyca`, `/cltp` and `/dyc`, TAB for the shortcuts, plugin.yml permissions, both message catalogs, the message upgrade, the permission-message fix and the cancellation message.
- The permission-message regression test failed on the unchanged 1.0.3 code (both "no permission" and "feature disabled" were sent) and passed after the fix.
- Two existing help tests were changed because help is now paged. The rows they check are the same; they are now looked up on the page where they appear.
- `gradlew.bat clean test verifyPlatformArtifacts --no-daemon --offline` passed. Both JARs contain plugin version `1.0.4` and the correct backend marker. Only the Folia JAR declares `folia-supported: true`.

## Isolated real-server checks

Only loopback-bound disposable servers were used, with Minecraft protocol clients rather than human visual clients.

**Purpur 1.20.4 build 2176, Java 21.** This was an in-place upgrade of the 1.0.3 test server, keeping its claims and its 1.0.3 message files. 24 of 24 checks passed:

- Untouched 1.0.3 message texts appeared with the 1.0.4 wording.
- Claims and claim names were kept.
- Help showed eight commands per page with the next-page command. The market rows were on page 3. The admin entry was shown only to admins.
- Players saw `/dyc` and `/cltp` in help but never saw `/dyca`.
- `/dyc list` worked. `/dyca` opened paged admin help, and `/dyca diagnose` worked.
- `/dyca` was missing from TAB for players and present for admins. Players could not run it and got no argument suggestions.
- `/cltp` completed for players. It answered a player without claims, suggested claim names and teleported to a named claim.
- Turkish paged help used `/claim yardım 2`.
- `/cancel` reported the cancellation.

**Folia 1.20.4 build 31, Java 21.** The 1.0.4 Folia JAR started with existing claims. The console ran `dyca diagnose`, `dyc help`, `dyca help` and `dyca reload` successfully.

On this host, the JVM sometimes crashed in the console library (`jansi.dll`) or stayed running after the server had saved all worlds. This happened with and without DyClaim, including during the comparison runs with only other claim plugins. After the Purpur run, the claim data was checked again: it was intact, with schema 2 and both claims present. DyClaim does not appear in the crash reports. An earlier normal stop of the same Purpur server with 1.0.4 ended with exit code 0.

## Comparison benchmark

The four-plugin, 50-claim comparison was repeated with the 1.0.4 JAR. See [the benchmark report](benchmark-50-claims.md).

## Limits

The following were not exercised for 1.0.4:

- a live Vault economy provider
- SimpleClans
- a Floodgate/Bedrock client
- a permissions plugin (permission denials were covered by automated tests only)
- Paper or Spigot 26.x
- Folia 26.3
- every intermediate Minecraft patch release
