# Upgrade and recovery

## Before upgrading

Stop the server and back up the existing plugin JAR together with the entire `plugins/DyClaim/` directory. Keep the backup outside the running server directory. Test the upgrade on a copy of your server before replacing production files.

## Upgrading from Beta

Install the 1.0.0 JAR and start the server. Beta's flat `claims.json` map migrates to schema 2. The first migration preserves the old format in `claims.beta-backup.json`; subsequent starts do not overwrite that migration backup.

Existing ownership, coordinates and trust remain available. Legacy trusted players receive permanent access to the supported player rights. Claims receive stable IDs, and new fields receive defaults. `state.json` stores language preferences, acquisition bans, activity and warning state.

Invalid claim data prevents startup and writes instead of being replaced with an empty claim collection. Restore a verified backup before restarting. Invalid configuration reloads retain the previous valid settings. A successful reload clears pending confirmations and stops temporary automatic-claim sessions.

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
