# Server compatibility

DyClaim 1.0.0 is compiled against Spigot API 1.20.4 with a Java 17 bytecode target. The server's own Java requirements still apply.

## Tested environments

| Server | Build | Runtime | Live checks |
|---|---|---|---|
| Purpur 1.20.4 | 2176 | Temurin 21.0.12.1 | Sale confirmation, 11 feature scenarios and 8 boundary scenarios |
| Paper 26.2 | 129 | Temurin 25.0.4.1 | Sale confirmation, 11 feature scenarios, 8 boundary scenarios, 5 additional checks and restart persistence |
| Purpur 26.2 | 2633 | Temurin 25.0.4.1 | Sale confirmation, 11 feature scenarios and 8 boundary scenarios |
| Spigot 26.2 | BuildTools revision 4648 | Temurin 25.0.4.1 | Sale confirmation, 11 feature scenarios, 8 boundary scenarios and 5 additional checks |

The runtime matrix above passed with 76 automated tests on the preceding artifact. Its SHA-256 is:

```text
A8E009D35E3981AB9E7C87F1044A682C113C3513A8D301C05812086197C5D56A
```

## Presentation revision

The published 1.0.0 artifact passes 83 automated tests. Its SHA-256 is:

```text
507E3207EA4D61DE9B094747E3AF227A96B18545D420AC42F1CEE8EAC1932439
```

Focused live checks on Purpur 1.20.4 verify English/Turkish player and administrator help, legacy message-file fallback, named/unnamed claim list rows and claim information. The complete runtime matrix was not repeated after the presentation revision.

## Version 1.0.1 validation

Version 1.0.1 passes 108 automated tests, including new update transport, release filtering, scheduling, permission, login timing and protection regression checks. The preparation artifact SHA-256 is:

```text
5CE2FE6E4FB7D2519C7EDEFBE3E4ED6DAFA6E3572DDFA45A9A17B0FD97D9F0E5
```

Real Java HTTPS requests to Modrinth returned HTTP 200 for Minecraft 1.20.4, 26.2 and 26.3. The fixed code selected public v1.0.0 for a simulated Beta installation and correctly found no update for the unpublished 1.0.1 installation. This verifies API transport and metadata selection, not native Minecraft 26.3 gameplay.

Live notification checks on Purpur 1.20.4 (Java 21) and Paper 26.2 (Java 25) used a test-only copy with its descriptor changed to `1.0.0-Beta` to exercise an actual newer public release. Only the descriptor was edited in that copy; the updater code matches the final preparation. Console discovery, administrator login messages, English/Turkish messages, direct version links, deduplication, non-admin exclusion, claim saving and new configuration defaults passed. The test copy preceded the final dispenser-projectile protection regression and is excluded from publication.

The unmodified final 1.0.1 JAR was also started on both servers with existing schema-2 data to check version identity, claim persistence, configuration reload and suppression of downgrade notifications.

The earlier complete gameplay matrix has not been repeated for 1.0.1. New protection edge cases are covered by regression tests with simulated Bukkit events. Native Bedrock clients and actual external economy/claim/clan providers remain outside this validation scope.

## Scenario coverage

The feature scenarios cover confirmation without buttons, temporary granular trust, claim names and safe teleportation, coowner acceptance and sale restrictions, ownership transfer, villager protection, saved language selection, visitor warnings, acquisition bans, invalid configuration reloads and explicit free-auto-claim configuration.

Boundary scenarios check incoming/outgoing piston movement, hopper transfers and fluid flow, plus permitted piston/hopper automation between claims with the same primary owner.

Additional Paper and Spigot checks cover global language overrides, language-specific TAB suggestions, translated marketplace arguments, cross-boundary double-chest access and profile-based server bans. Paper restart checks restored four schema-2 claims.

## Client and integration scope

The 1.20.4 tests used direct Java protocol clients. On 26.2, Java 1.20.4 protocol clients connected through ViaVersion and ViaBackwards 5.12.0. The plugin and game mechanics ran on actual 26.2 servers; direct 26.2 clients and Bedrock/Geyser clients were not tested.

Economy failure handling is covered by automated tests using mocked providers. Actual Vault economy providers, external protection plugins and SimpleClans were not installed in the completed server matrix. Validate the versions you intend to deploy.

Intermediate Minecraft patch releases were not individually tested. The table establishes the tested environments rather than guaranteeing every version between them. External redstone checks do not isolate every circuit, and optional mob cleanup may remove eligible mobs already inside a claim.

## Reproducing checks

Use the [integration test tools](../scripts/integration/README.md) with disposable, localhost-only test servers. Server downloads, generated worlds, runtime data and logs are excluded from the repository.

## GitHub release build

On 2026-10-07, the plugin author was updated to Claww and the release documentation was refreshed. The rebuilt artifact passed all 108 automated tests. Gameplay code is unchanged from the validated preparation; the live matrix was not repeated for this metadata change.

Release JAR SHA-256:

FC565D37B7BDCDD6DEC0FB5FF87BC80AC5422C46356F95A09C9CC7E4910F42F6

## Local version 1.0.2 validation, first review — 2026-10-09

The baseline suite was run again before changes: 108 tests passed. The first review's 1.0.2 suite passed 122 tests, with zero failures and zero skipped tests. New regressions reproduced ASCII Turkish TAB prefixes, administrative leaf suggestion filtering, non-Monster hostile spawning/cleanup, stale purge batches after reload, independent shutdown persistence and failed journal completion visibility. Existing update, language, permission, protection, economy, storage and hook tests remained enabled.

The existing Gradle wrapper is used for targeted tests and a full clean build. The Spigot API 1.20.4 dependency and Java 17 bytecode target are unchanged. This local build has not been committed, pushed, published or deployed.

No actual Minecraft server or external-provider integration tests were run during the first 1.0.2 review. Earlier server results above are historical and are not claimed as validation of this version. Existing deprecated API and Gradle warnings remain.

First-review 1.0.2 JAR SHA-256: 426C48AC953AF42FD1269769D50D8015535A55ED88AE8DFA1FA3D0B19A1A2967. Packaged version: 1.0.2. All 46 class files verified as Java 17 bytecode (major 61).

## Local version 1.0.2 validation, second review — 2026-10-09

The full `gradlew.bat clean build` completed with 135 tests, zero failures, zero errors and zero skips. Four financial regression cases failed before the fixes: shutdown during a bulk refund, reload between batches, changed economy availability and continuing price-difference payments after claim write failure. Seven protection cases failed before the fixes: ghast projectile policy in both directions for blocks, protected entities and hanging entities, plus a spawning-policy change during cached mob cleanup. Player-fired projectile and unrestricted-cleanup controls also passed. No tests were disabled and no dependency versions changed.

The default `config.yml`, `messages_en.yml` and `messages_tr.yml` changes were verified as comments only, both by comparing non-comment text and parsed YAML data. Existing resource-contract tests also passed.

An isolated, localhost-only Purpur 1.20.4 build 2176 server used Java 21 and two Mineflayer 1.20.4 protocol clients. Completed scenarios:

- Startup without optional integration plugins; free claim acquisition and confirmation.
- Untrusted visitor block breaking denied and trusted visitor block breaking allowed.
- Turkish ASCII TAB prefix returning Turkish suggestions on a real protocol client.
- Actual ghast-owned fireballs preserving claim blocks when mob explosions are disabled, then destroying blocks when mob explosions are enabled and ordinary explosions are disabled. Server-side block queries verified both outcomes.
- Plugin reload preserving a custom English translation and the claim.
- Normal shutdown and restart preserving the claim's identity, owner, trust, name and protection choices; the restarted claim remained accessible through commands.

After a comment-only clarification, the exact final clean-build JAR also passed startup with existing data, persisted-claim comparison, claim-info retrieval and normal shutdown. Its runtime SHA-256 matched the artifact below.

The client library emitted partial explosion-packet decoding warnings. The server-side block checks passed; this does not certify client rendering. Financial fault injection and mob-cleanup batching were tested automatically with real temporary files and mocked server/economy boundaries, not through a live Vault provider. Actual protection/clan/economy integrations, live hanging/entity explosion damage, Bedrock clients and other server versions were not tested in this round.

Final JAR SHA-256: 74CFE87FCE2289E7AEAAE4AE1B4D5142F6A3914FAEFEEB57A53A66B9713DDC94. Packaged version: 1.0.2; all 46 class files are Java 17 bytecode (major 61). API baseline remains Spigot 1.20.4. This work is local and unpublished.

## Local version 1.0.2 command follow-up — 2026-10-09

The command follow-up supersedes the previous local artifact. The wrapper clean build passed 148 tests with zero failures, errors or skips. The first ten command regression cases failed before implementation; the final suite includes thirteen new command cases covering console routing/permissions, grouped trust and explosion commands, invalid input, help/TAB consistency, alias compatibility, idempotent normalization and preservation of custom messages.

The exact final JAR was tested on the same isolated Purpur 1.20.4 build 2176 instance with Java 21 and an actual Mineflayer 1.20.4 protocol client. English/Turkish console reload, console language overrides, grouped Turkish help loaded from existing message files, ASCII TAB prefixes, grouped EN/TR trust-right changes, legacy trust aliases, grouped mob-explosion toggles, the old mob alias and invalid explosion input all passed. The claim identity, existing grant expiry and custom message file were preserved. The server stopped normally.

An initial harness attempt checked the console reply too early; the log showed the successful reload reply after that check. The harness was changed to wait for the reply and the complete scenario passed. No plugin change was needed for that timing issue. This follow-up did not repeat the wider gameplay matrix or test real optional providers; previous sections retain their original scope. Existing compiler/Gradle warnings remain.

Current local JAR SHA-256: 9698222EF7127E01BCFDDDB01EC6E762F07B8473943E341BE3BDA0DAC6CC1E93. Packaged version: 1.0.2; all 46 classes target Java 17 (major 61). No dependencies, claim schema, commit, publication or production deployment changed.
