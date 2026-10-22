# Physical-device verification — NOT TESTED

No scenario in this document has been executed on physical hardware for this change. Android CI emulator results are separate evidence. A successful screen message alone is insufficient evidence of a financial write.

## Prerequisites and isolation

Use an explicitly approved staging build and isolated Firebase staging project or reachable Auth/Firestore emulators with synthetic accounts and data. The `demo-*` project identifier is an emulator safety boundary, not a hosted Firebase service. Do not use real customers, alter rejected live operations, uninstall the live application, or clear its storage. Record commit, package, version, Android version, device model and backend/rules revision first. Keep production and staging application storage separate.

Manual two-phone testing does not require USB or ADB. Both phones need access to the same approved staging backend over their network. Preparing that reachable staging configuration and distributing its APK remains a release prerequisite. A USB connection is required only for the separately listed automated ADB procedure.

## Manual physical-device tests

All rows: **NOT TESTED**. Before each scenario capture synthetic job/payment/expense IDs, original actor UID, beneficiary UID, workspace, operation UUID, action time, amounts and original outbox state. After confirmation capture both devices' records and authoritative staging documents. Store evidence privately; exclude tokens, real contacts and customer data from Git.

| ID | Steps on two actual phones | Expected result and evidence |
| --- | --- | --- |
| M1 | Partner A disables internet, records a new job billed 1100 with 900 received for B; B remains online. Inspect A before reconnecting. | Original job and receipt are durably present. Pending/review state is explicit. Recorder=A, beneficiary/initial collector=B, due=200. B cannot see an unconfirmed cloud write. |
| M2 | Close A from recents while offline, reopen, then reconnect. Repeat with an actual device restart. | Original UUIDs, payload, timestamps, attribution and amounts survive; customer then job then receipt acknowledge in order. Both phones eventually display identical confirmed amounts; no duplicate receipt. |
| M3 | While A is offline, B creates a different online job. Reconnect A. | Both distinct jobs appear on both phones, correct creators retained. Compare server documents, local rows and confirmed totals. |
| M4 | Add three standalone expenses 1, 1 and 2, using personal and business funding, first as original Owner account and then Partner. Restart while offline and reconnect. | Every original command survives. Standalone Room source=null; new wire source=0. Three distinct records, total=4 per run. Personal payer is actual selected eligible UID; Business Paid has no personal UID. No extra withdrawal. |
| M5 | Create a job with a 900 Business Paid expense and then repeat with I Paid. | Business cash decreases once by 900; equal shared costs total 900. I Paid leaves business cash unchanged and adds exactly 900 to the correct personal investment. Compare all reports, not only one card. |
| M6 | Arrange an intentionally denied synthetic payment through a controlled staging test fixture, not by changing production roles. Inspect normal screens and sync diagnostics. | Original payment remains retained; failure/review accurately shown. Confirmed job balance does not falsely include denied money. No automatic replay or actor reassignment. Retained action remains discoverable for support. |
| M7 | Try deleting the job with that unresolved related payment. | Financial dependency protection explains refusal; no partial job/receipt/expense removal. After separately approved, verified staging resolution, the checked deletion protocol remains atomic and audited. |
| M8 | Interrupt network during acknowledgement; restore it twice and restart. | Same operation UUID is reconciled against the server. Exactly one financial event; local Synced only after authoritative acknowledgement. |
| M9 | Both Partners edit the same synthetic job from the same starting revision. | Conflict is detected or explicitly reviewed; no silent overwrite, merged amounts or changed historical attribution. Preserve both attempted payloads and server result. |
| M10 | On each phone switch EN→TA→EN in Home, Overview, Customer Dues, History, Expenses, Withdrawals, entry forms and account dialogs. Use mixed-language names, notes, parentheses and duplicate member names. | App labels remain readable and switch immediately. Entered names, business names, phone numbers, notes, locations and custom work types stay byte-for-byte unchanged. Capture screenshots in each language. |
| M11 | Create Raman jobs for A (due 300) and B (due 700). Select each Customer Dues chip and All. Open the existing collection dialog from a filtered view. | Display totals 300/700/1000. Filter uses beneficiary UID before aggregation. Existing customer collection dialog clearly uses the full account due 1000, without silently collecting against only a displayed slice. |
| M12 | Verify initial Owner display alone, after first active Partner joins, and after last Partner leaves on each installation that observed partnership. | Owner→Partner→Partner display only; actual Owner permissions retained; Operators stay Operators. Fresh installation after all Partners left is a documented unresolved history limitation, not a pass. |
| M13 | Test small/typical screens, landscape, largest practical font scale, Tamil person dialog and multiple job expenses. | Controls remain reachable; delete buttons and dialog close button visible or scrollable; labels do not overlap. Record any device-specific font/rendering issues. |
| M14 | Test long background delay, battery restrictions, airplane mode, Wi-Fi/mobile handover and force-stop/reopen. | Pending data survives. Android force-stop may defer workers until reopening; UI must not claim server confirmation before it arrives. Record timestamps, WorkManager state and both phones' eventual totals. |

## Automated physical-device tests — NOT TESTED

These are distinct from M1–M14. Do not mark them passed based on a manual observation or Android emulator run.

1. Connect an actual isolated test phone to Android Studio/ADB; record the returned hardware model, serial (private evidence), OS and package. Do not uninstall/clear the live package.
2. Start the existing synthetic emulator fixtures and the mandatory device-test runner documented in `scripts/run_device_ci.sh`. Use the isolated `.devicetest` application ID. Confirm each required emulator port is reachable by that test device before running.
3. Execute the current mandatory instrumentation plan against that physical serial. Preserve test XML, logs and screenshots and verify all listed classes actually executed. Do not run destructive fixture cleanup against a production backend.
4. Inspect Room rows and outbox payloads using Android Studio/diagnostic tests, and WorkManager state using its inspector. Compare operation UUIDs, actor/workspace, acknowledgements and financial totals with synthetic server state.
5. Record per-test pass/fail/skip, hardware details and commit. Any missing test, fixture or diagnostic is NOT TESTED, with its reason. This procedure has not been run for this change.

## Evidence record template

Record `scenario`, `manual physical / automated physical / Android emulator`, `commit`, `rules hash`, `device model/OS`, `actor UID/workspace`, `operation UUID`, `before/after Room rows`, `before/after server rows`, `amounts`, `acknowledgement`, `screenshots/log references`, `actual outcome`, and `tester/time`. Never infer evidence for one category from another.
