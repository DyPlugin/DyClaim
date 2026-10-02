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

The current 1.0.0 artifact passes 83 automated tests. Its SHA-256 is:

```text
507E3207EA4D61DE9B094747E3AF227A96B18545D420AC42F1CEE8EAC1932439
```

Focused live checks on Purpur 1.20.4 verify English/Turkish player and administrator help, legacy message-file fallback, named/unnamed claim list rows and claim information. The complete runtime matrix was not repeated after the presentation revision.

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
