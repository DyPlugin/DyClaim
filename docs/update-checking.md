# Update checking

DyClaim 1.0.1 and later query the public Modrinth version API asynchronously. Query filters are URL-encoded JSON arrays. Bukkit strings such as `1.20.4-R0.1-SNAPSHOT` and `26.2.build.2633-stable` are normalized before querying.

Only listed, stable releases compatible with the server's exact Minecraft version and a supported Bukkit-family loader are considered. The highest semantic version is selected rather than trusting publication order. Drafts, scheduled versions, prereleases, incompatible releases and unknown version tags are ignored. The download message links to the selected version page.

Checks run at startup, after a configuration reload, and every six hours by default. HTTP failures and malformed responses produce a console warning and retry after ten minutes. Both intervals are configurable:

```yaml
update-checker:
  enabled: true
  check-interval-hours: 6
  retry-minutes: 10
```

Admins already online when a check completes receive the notification. Admins joining later receive it after three seconds. Each detected release is announced once per login session; discovering a newer release permits another notification. Non-admin players are not notified. A failed request preserves the last successfully detected update. Disabling the checker through a reload clears its cached notification state and stops further checks.

## Release checklist

Publish the intended stable version with a numeric version number (for example `1.0.1` or `v1.0.1`), release channel, public listed status, the correct server loaders and all Minecraft versions actually supported. If a Minecraft version is not listed in that release's metadata, servers running it will not receive that update notification. A local development JAR never registers a remote release.

## Older installations

The Beta and original 1.0.0 JARs contain an HTTP request bug. A new release cannot replace the code already executing on those servers. Modrinth project metadata changes cannot repair their malformed request. Those users must install a fixed JAR manually before reliable future notifications become available. Use the existing project pages and update announcements to communicate the fix; no retroactive notification is claimed.

The plugin does not automatically download or replace installed JARs.
