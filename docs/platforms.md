# Platform builds

DyClaim keeps claim protection, commands, permissions, money and storage on each game server. Proxy companions provide an optional connection check; they do not replace backend protection or share claims between servers.

## Choose one backend JAR

| File | Install on | Purpose |
| --- | --- | --- |
| `DyClaim-v1.0.2.jar` | Bukkit, Spigot, Paper or Purpur | Existing standard backend |
| `DyClaim-v1.0.2-Folia.jar` | Folia | Backend with player, entity and region scheduling |
| `DyClaim-v1.0.2-Velocity.jar` | Velocity | Optional backend diagnostics |
| `DyClaim-v1.0.2-BungeeCord.jar` | BungeeCord | Optional backend diagnostics |

Install one backend build per game server. Keep its existing `plugins/DyClaim` directory; the claim schema remains version 2. Back up the directory before replacing a JAR, stop the server, replace the old backend JAR, then start the server. Do not install both backend JARs together. The new proxy protocol needs the matching backend source/build; the original public Bukkit JAR predates that bridge.

The Java compilation target remains 17. Run the Java required by your server. The current Velocity module targets the Velocity 4.2 API, which requires Java 25 for compilation and the tested runtime. BungeeCord API compilation uses the 1.21-R0.3 baseline.

## Proxy setup

Install only the matching companion in the proxy's `plugins` directory. Install DyClaim on every backend where protection is needed. No proxy companion configuration or shared database is required.

Grant `dyclaim.proxy.status` through your proxy permission manager. Run `/dyclaimproxy status` while connected to a backend; the same player also needs `dyclaim.admin` on that backend to receive its capabilities. `/dyclaimproxy version` reports the companion version. Turkish clients receive localized help and may use `durum` and `surum`/`sürüm` aliases.

Replies are correlated with the requesting player and current backend and expire after five seconds. Switching servers, disconnecting or losing authorization invalidates the check. This channel accepts no claim mutations, money transfers or console commands. The normal `/claim` command remains owned by the backend.

## Folia behavior

The Folia build uses the same claim data, configuration, English/Turkish messages and gameplay commands. Target terrain reads, border packets, mob removal, teleportation, confirmations and ownership operations are routed to their appropriate owners. Reload invalidates queued operations and cleans up active views and teleport warmups.

Synchronous ranged PvP decisions use player-owner snapshots instead of reading another region's player state or loading its chunks. These snapshots refresh each player tick and on movement/teleportation. If a remote actor has no valid snapshot, damage is conservatively blocked until owner data is available. Bukkit-family builds retain their direct synchronous behavior.

Optional integrations must themselves support Folia and their public APIs must permit the calling context. Vault economy/provider combinations, SimpleClans and Floodgate have not been verified on a live Folia server in this work. Normal server shutdown has ownership-aware cleanup; externally disabling a plugin outside its owning context can prevent restoration of existing client-only blocks or effects. Use normal stop/start for backend JAR replacement.

## Verification evidence

The platform expansion passed 290 automated tests (279 backend, 4 BungeeCord, 7 Velocity) plus the distribution descriptor/isolation check. All four distributable JARs use Java 17 bytecode and plugin version 1.0.2.

| Runtime | Scenarios actually exercised |
| --- | --- |
| Folia 1.20.4 build 31, Java 21 | Distant-region claim; denied visitor block break with block retained; allowed trusted break; coowner consent/index across regions; claim teleport warmup and native transfer; ownership transfer; reload; ownership persistence after normal shutdown/restart |
| Folia 26.3 build 3 (server ALPHA), Java 25 | Full backend startup, console reload, normal shutdown |
| Velocity 4.2.0 build 30, Java 25 with isolated Purpur 1.20.4 backend | Player/backend status, companion version, denied proxy user, backend `/claim help`, backend permission revocation, normal shutdown |
| BungeeCord build 2104, Java 21 with isolated Purpur 1.20.4 backend | The same player/backend diagnostic and permission scenarios |

These are separate from automated API-fixture tests. Intermediate Minecraft patches, real economy providers, native modern-protocol players and Bedrock rendering were not individually exercised here. Startup success alone does not establish gameplay compatibility.

## Build

Use the bundled Gradle wrapper with Java 21. For Velocity, provide a Java 25 JDK path:

```text
gradlew.bat clean :test :jar :foliaJar :bungeecord:test :bungeecord:jar :velocity:test :velocity:jar :verifyPlatformArtifacts -PplatformJava25=C:/path/to/jdk-25
```

On Linux/macOS use `./gradlew` and the corresponding JDK path. Backend artifacts are in `build/libs`; proxy artifacts are in `platforms/velocity/build/libs` and `platforms/bungeecord/build/libs`. All builds embed plugin version `1.0.2`.
