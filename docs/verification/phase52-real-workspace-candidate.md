# Phase 5.2 normal TRAC update candidate

This candidate builds the actual TRAC product from Phase 5 commit e47430594ee5bf482d0cb96e3232b05c42924b14. It retains package com.aistudio.aidhunttrac.cedlir, uses the active trac-aidhunt registration, and identifies itself as AIDHUNT TRAC, 1.0.13 / Android version code 14. Phase 5.1's real-account testing is covered by this one product build. The separate synthetic pilot is retained with its existing account and workspace restrictions.

## Workspace access

The previous sign-in warning came from the synthetic APK's explicit pilot account allowlist. The normal release leaves OWNER_VISIT_PILOT disabled. Native background push is disabled in this release while the in-app bell, Tamil and IST remain enabled.

Discovery uses the signed-in Firebase UID, its existing profile and membership index as hints, then verifies the actual workspace document and that user's active membership. An Owner role also requires the original workspace owner UID to match. Discovery opens the only authorized workspace, restores a previously selected workspace while it remains authorized, or uses the existing selector when there is more than one choice and none is remembered. A failed read cannot authorize a new account or grant access from another account's cache. Only transient connectivity failures can restore a same-account cached workspace. An empty successful discovery gives a distinct message. Ordinary user-initiated personal onboarding remains available through the existing screen after a fresh server check confirms that the account has no authorized workspace.

A read-only production metadata inspection during preparation confirmed the normal app registration, the established SHA-1 and SHA-256 signing fingerprints, and discoverable same-workspace memberships for Muniyappan (original Owner), Perumal (active Partner), and Sriram (active Partner). It did not sign into those three accounts on a device or read their financial records. Live billing was disabled and Rules revision e7981704-cf72-453b-a867-ad3faa10c75f remained active.

## In-place update and backup

The normal package uses the same Room filename aidhunt_trac_v4.db and schema version 24 as the prior signed code-13 product artifact. This branch does not change the Room schema, migration chain, cache or queue layout, financial calculations, sync protocol, authentication configuration, or backend. Both the published 1.0.8 reference APK and the last known normal-package 1.0.12 artifact carry the established release certificate ae954fdb33bb43c6781a6e91f615ea8c7861e4a8c5d65a5c0ea878c693db20a5. A matching certificate and higher version code allow Android to retain the current app data on a compatible update. The currently installed handset package was not available to inspect. [Android update acceptance rules](https://developer.android.com/google/play/app-updates).

1. Before accepting the update, check the installed app version in Android App info. The newest known delivered APK is 1.0.12/code 13. A package read through Android debugging can show the installed versionCode, and Android Build Tools apksigner can verify the installed base APK against the certificate above. If Android reports a conflict, a downgrade, or asks to uninstall the existing app, cancel.
2. On each device, review Account → SQLite & Cloud Sync and record the workspace plus any pending or Needs Review operation IDs. Keep the prior known signed APK and current device backup. The app allows Android backup, but availability, age, and inclusion of unsynced data depend on the device. Treat exported reports as evidence about balances and records. They are not a restorable outbox backup. If there are irreplaceable unsynced changes and no verified recoverable copy, defer installation on that device. [Android Auto Backup](https://developer.android.com/identity/data/autobackup).
3. Approve only an in-place update. Keeping a copy of the older APK does not guarantee Android will accept a downgrade or restore local data. If a problem appears, keep the current installation and its data intact, disconnect that phone from the network if needed, capture the error and operation IDs, and use a separately verified forward update for recovery.

## Read-only physical acceptance

Physical testing starts after the owner approves installation. Use one Owner phone and the existing Partner phones in turn. Installations and handset checks are **NOT TESTED** by preparation.

| Step | Action and expected evidence |
| --- | --- |
| 1. Inspect existing data offline | Put the first phone offline before launching the updated app. Use its retained login if available. Check the last verified workspace, Home counts, and queue diagnostics. Compare pending operation IDs and states with the pre-update screenshots. A new sign-in needs internet and should report that clearly. |
| 2. Verify the original Owner | Before an online read-only visit, confirm that the device has no queued business writes. Normal TRAC automatically resumes eligible sync on reconnection. If the device has pending operations, stop this read-only sequence and preserve the queue for separately approved handling. Reconnect and verify Muniyappan opens the existing business, with the original Owner controls and no workspace-creation prompt. |
| 3. Verify both Partners | On Perumal's and Sriram's phones repeat the backup and offline checks, then reconnect only when appropriate. Each active Partner should open the authorized business and keep Partner permissions. When there are multiple authorized choices, verify the selector and reopening of the chosen workspace after restart. |
| 4. Verify the bell | On the Owner device open Since Your Last Visit and wait for Last Updated. Opening the bell should retain unread state. Read an available verified activity, close and reopen the app, then confirm only that activity stays read. Repeat with Tamil and later without connectivity. A first visit establishes the baseline before claiming anything is new. |
| 5. Verify reports without changing data | Compare Home, existing job details, Customer Dues, Collection History, Expenses and Balance Sheet with the pre-update screenshots. Check the established ₹16,900 receipt and ₹4,500 payment-method classifications. Browsing in this step uses existing records. |
| 6. Record boundaries | Existing live synchronization is active. Creating, editing, deleting or collecting in this business changes real accounts. Leave those actions for a separately approved test. Missing historical actions whose Firestore evidence was overwritten remain a known Phase 5 limitation. |

The read-only procedure means no intentional business changes. An online session can still update routine account or sync metadata and publish pre-existing queued records. Keep a device with pending writes offline when gathering read-only evidence.

The Phase 5 in-app feature recovers supported jobs, Old Entries, verified edits and deletion proofs, collections, expenses, and ordinary withdrawals when authoritative evidence is retained. It does not infer earlier expense edits or deletion events from a removed current record.

## Local automated verification

The focused Android run passed 216 tests with zero failures and zero skips under the isolated Firestore Emulator. It covered Owner and Partner role selection, active membership and startup, same-account offline recovery, the populated Room 23-to-24 migration, retention of both pending and rejected operations, Phase 5 notification refresh, unread persistence, Tamil and IST, and Home, job editing, collection, expense and balance-sheet regressions. No live login, financial write, handset installation or physical run was part of that automated result.
