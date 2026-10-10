# Upgrade and recovery

## Before upgrading

Stop the server and back up the existing plugin JAR together with the entire `plugins/DyClaim/` directory. Keep the backup outside the running server directory. Test the upgrade on a copy of your server before replacing production files.

## Upgrading from Beta

Install the chosen stable JAR (1.0.0 or later) and start the server. Beta's flat `claims.json` map migrates to schema 2. The first migration preserves the old format in `claims.beta-backup.json`; subsequent starts do not overwrite that migration backup.

Existing ownership, coordinates and trust remain available. Legacy trusted players receive permanent access to the supported player rights. Claims receive stable IDs, and new fields receive defaults. `state.json` stores language preferences, acquisition bans, activity and warning state.

Version 1.0.1 retains schema 2. Existing 1.0.0 claim, trust, transaction and state files do not require a new format migration. Missing update-check intervals and boundary protection options are merged into the existing configuration. See [update checking](update-checking.md) for the notification fix and older-JAR limitations.

Invalid claim data prevents startup and writes instead of being replaced with an empty claim collection. Restore a verified backup before restarting. Invalid configuration reloads retain the previous valid settings. A successful reload clears pending confirmations and stops temporary automatic-claim sessions.

Version 1.0.2 also retains schema 2 and existing configuration keys. A successful reload invalidates active inactive-claim purge batches so they cannot continue using old rules. Shutdown logs component failures and continues independent claim saving and visual cleanup; this does not make a failed disk write successful. If a transaction journal update fails, the previous stage remains visible for reconciliation and the claim remains locked.

The second 1.0.2 review extends reload/shutdown invalidation to administrative deletion and bulk sales. Remaining bulk-sale claims are checked against the confirmed refund, and claim write failure blocks subsequent financial transactions. Payments already attempted before a storage failure still require reconciliation; the plugin does not replay them automatically. Comments added to bundled YAML defaults do not rewrite existing custom settings or translations.

The 1.0.2 command follow-up allows console reload and global configuration commands. Help groups granular rights under `trust` and mob explosions under `explosion mob`; old `trustperm` / `güvenizin` and `mobexplosion` / `mobpatlama` aliases remain usable. Known shipped help text from older message files is adapted in memory without replacing custom text or rewriting those files. Restart the server after replacing the JAR; configuration reload alone does not load new plugin classes.

## Economy reconciliation

`transactions.json` records payment intent and progress. Definite failures are rejected or compensated when the result is known. An uncertain provider response locks the affected claim; restarting does not automatically repeat the payment.

Use `/claim admin transactions` to inspect unresolved records. Compare server logs, the economy provider's transaction history and the players' balances before reconciling the record. Do not remove a journal entry without establishing what was paid and what ownership change occurred. Archived transaction records are retained in `transactions/`.

Marketplace prices use two decimal places. Tax is deducted from seller proceeds and removed from circulation. A market listing price does not become the claim's server-refund basis.

If a warning sanction was interrupted in the `applying` stage, compare it with the server's profile-ban records before changing the state. Uncertain sanctions are not blindly repeated.

## Rolling back

Stop the server and restore the old JAR and its matching complete data/configuration backup together. Beta cannot read schema 2; replacing only the JAR is insufficient.

The preserved Beta claim backup represents the migration point. Restoring it loses claim changes made after the upgrade. Restoring plugin files does not reverse external economy payments, so reconcile the provider's records before rolling back.

## Deployment checks

Use the [compatibility matrix](compatibility-test.md) to select a tested server/runtime combination. Test your chosen economy, protection and clan providers separately, and verify Bedrock behavior if your server uses Floodgate/Geyser.
