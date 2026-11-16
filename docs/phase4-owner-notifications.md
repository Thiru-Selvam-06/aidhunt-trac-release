# Phase 4 Owner notifications

The application receives a data-only FCM wakeup. The signed-in device fetches the activity from the authenticated server before displaying it. Only the workspace's original Owner can register, fetch or reconcile the activity. The acting Partner is authenticated by the confirmed write and workspace membership. The launcher and WorkManager never use customer data or amounts from the push payload. Firestore Rules, sync, Room and the in-app bell history keep their existing behavior.

The backend creates an immutable logical activity identity from the workspace, confirmed operation and activity family. Firestore records the event before attempting FCM. Per-token attempts record FCM acceptance separately from Android display. If a function crashes after sending and before recording FCM acceptance, another attempt can occur. The device deduplicates the event ID. Each installation keeps its own registration and history position. Registrations unrefreshed for 45 days are excluded from sends. Reopening with an authenticated Owner renews registration and reconciles history. The first registration can reconcile seven recent days, and a known installation resumes from its last stable event cursor. Older history remains in Firestore. FCM can queue delivery while offline, and opening the app online reconciles missed events. Force-stop, permission, channel settings, device power policy, and network availability can affect actual display.

## Install variants

- Release has the feature enabled only for the expected production package and Firebase project.
- Live Test requires explicit opt-in with gradle property **ownerActivityLiveTest=true** and the expected **.livetest** Firebase Android app configuration. Use controlled accounts and an authorized isolated scenario.
- Debug, emulator Device Test and Physical Test keep Owner push disabled. No fake build configuration grants a real backend session.
- The installed Owner APK is still unknown. Check Settings → Apps → AIDHUNT Trac and record the shown version suffix. When a device is available, confirm the package and variant through the APK metadata or ADB. Keep the current installed app and its local records intact.

## Verified infrastructure gap

A read-only check on trac-aidhunt found disabled billing, no first- or second-generation Cloud Functions, and zero Owner registrations or Owner activity documents in the inspected workspace. The active Firestore Rules revision lacks direct Android access to both notification collections. The authenticated server endpoint is used because it can enforce the original Owner account with the current live Rules.

## Deployment proposal requiring approval

1. Link a billing account to trac-aidhunt only after approval. Establish an approved budget and alerts, confirm the expected Cloud Functions, Cloud Run, Eventarc, Artifact Registry, Cloud Build and Messaging APIs, and review Cloud Firestore and build usage. FCM delivery has no direct charge, while associated infrastructure and storage usage can incur charges. [Firebase pricing plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans), [Cloud Functions billing](https://firebase.google.com/docs/functions/faq-and-troubleshooting).
2. Verify the notification runtime service identity. Grant the minimum Firestore database permissions needed by the notification functions and FCM send permission to the sender. Admin SDK calls bypass Firestore Rules, so review the notification-only write paths before granting database access. Confirm the role used by Eventarc delivery separately. Use managed service identity without a downloaded key. [FCM permissions](https://docs.cloud.google.com/iam/docs/roles-permissions/firebasecloudmessaging), [Firestore Eventarc setup](https://docs.cloud.google.com/firestore/native/docs/extend-with-cloud-run-functions).
3. Confirm the intended Firebase Android registration for the controlled **.livetest** package. The Firebase project currently lists only the release package. The controlled .livetest package is absent. Registering another Firebase Android client is a separate production configuration action if the controlled package is unavailable.
4. Deploy only **ownerActivitySession**, **ownerActivity_entries**, **ownerActivity_entryDeletionProofs**, **ownerActivity_collections**, **ownerActivity_expenses**, **ownerActivity_cashDeleteProofs**, and **ownerActivity_withdrawals** in **asia-south1**, using reviewed deployment identities. Preserve all currently deployed Rules and indexes. Confirm the endpoint only accepts the authenticated original Owner and the sender can reach FCM. Keep financial transactions in approved isolated accounts and workspace.
5. Rollback by removing only the six notification triggers to halt new sends. Disable or remove the notification callable if necessary and retain event history for review. Confirm the active Rules version and normal sync behavior after rollback. Revisiting the billing link is a separate project-wide decision.

## Physical-device test checklist

Both automated physical-device testing and manual physical-device testing are **NOT TESTED**. Emulator results are tracked separately. Manual checks use the two approved Owner/Partner phones without USB.

1. Record the build variant, notification permission and language on both phones. Authenticate as original Owner and an active Partner in the approved isolated workspace. Verify the server reports an Owner device registration.
2. Create a documented synthetic job while the Partner device is offline. Capture Pending Sync and verify there is no new confirmed activity. Reconnect and capture the cloud-confirmed operation, matching event ID and the notification in the Owner's shade. Open it and verify the authorized activity appears.
3. Repeat for historical entry, edit, collection and reversal, expense create/edit/delete, authorized job deletion and ordinary withdrawal. Use a controlled operation that fails authorization and verify no success event. Record operation IDs and event metadata without customer details.
4. Disconnect the Owner device, confirm an action on the Partner device, then reconnect. Verify one retained activity, one device notification when permitted and no repeated display after app reopen. Repeat with both Owner devices and with one Owner device signed out. Verify the signed-out device shows no private activity and the other remains eligible.
5. Repeat with Tamil and English, background/foreground, and Android 13+ notification permission denied then granted. Record event persisted, FCM acceptance and actual shade display as separate evidence. A force-stopped app may need reopening before reconciliation.
