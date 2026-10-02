# Integration test tools

These scripts modify claims, blocks, configuration and test-player permissions. Run them only against disposable test servers.

## Requirements

- Node.js and dependencies installed with `npm ci` in this directory.
- A prepared Minecraft server with the DyClaim JAR installed.
- A localhost-only, offline-mode test instance.
- A supported Java protocol client version.

Server files, downloaded JARs, worlds, generated reports and `node_modules` are not included in the repository.

## Configuration

| Variable | Purpose | Default |
|---|---|---|
| `DYCLAIM_TEST_SERVER` | Test-server directory | Repository `.local/server` |
| `DYCLAIM_TEST_JAVA` | Java executable | `java` |
| `DYCLAIM_TEST_JAR` | Server JAR filename | `purpur.jar` |
| `DYCLAIM_TEST_PORT` | Client connection port | `25575` |
| `DYCLAIM_TEST_CLIENT_VERSION` | Java client protocol version | `1.20.4` |

Bind the test server to `127.0.0.1` and use the configured port. Server setup, EULA handling and server properties are not automated by these scripts.

Start the console bridge in a separate terminal:

```text
node run-server.js
```

It forwards lines appended to the server directory's `commands.txt` to the server console. Wait until the server is ready before running clients.

## Scenarios

Start with empty DyClaim claim/state data, then run these scenarios in order against the same test world:

```text
node scenario.js current
node features.js
node boundaries.js
```

`scenario.js` checks that a sale confirmation applies to the originally selected claim after the player changes location. `features.js` covers the core feature flow. `boundaries.js` uses the preceding ownership fixture to check piston, hopper and fluid behavior.

For historical regression comparisons, `scenario.js baseline` expects the original Beta sale bug. The `rc` argument remains accepted as a legacy name for the current-behavior check.

Scripts write `*-result.json` on success and, where supported, `*-failed.json` with intermediate results on failure. Generated reports are ignored by Git.

## Protocol coverage

The 26.2 server checks used ViaVersion/ViaBackwards 5.12.0 with 1.20.4 Java protocol clients. They exercise the actual server API and mechanics but do not substitute for direct 26.2 or Bedrock client tests. See the [server compatibility matrix](../../docs/compatibility-test.md).
