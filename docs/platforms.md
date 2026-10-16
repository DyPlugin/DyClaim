# Standard and Folia builds

Use `DyClaim-1.0.3.jar` on Bukkit, Spigot, Paper or Purpur. Use `DyClaim-1.0.3-Folia.jar` on Folia. Install exactly one build on each gameplay server. Each contains the full backend claim system; neither needs the other JAR.

Both target Java 17 bytecode and Spigot API 1.20.4. Run the Java required by your actual server. Folia uses native entity, region, global and async schedulers; the standard distribution keeps Bukkit scheduling. English/Turkish commands and schema-2 data are shared. Back up the JAR and entire data folder, stop the server, replace the JAR and restart. Normal stop/start is required when changing builds.

Proxy companion experiments are archived privately and excluded from this release and its artifacts. Sponge and NeoForge are not supported by this release.

Optional integrations must themselves support your platform and permit their API to be called from the relevant context. Live Folia economy providers, Floodgate/Bedrock rendering and SimpleClans require separate integration acceptance testing. A remote PvP actor without a valid owner snapshot is conservatively blocked until current data is available. Disabling the plugin outside a region/entity owner can prevent restoration of client-only visuals; use normal server shutdown.

For the scenarios actually exercised with the 1.0.3 artifacts, see [verification](verification-1.0.3.md). Historical tests from earlier artifacts do not establish testing of this release or every intermediate Minecraft patch.
