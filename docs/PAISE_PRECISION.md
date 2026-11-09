# Paise precision for jobs, dues and collections

The previous app rounded billed and paid amounts to whole rupees when it built the
customer ledger. A stored ₹850.25 job could therefore appear as ₹850 in Customer
Dues. New Entry, payment prefills, sharing and some rate fields had their own
whole-rupee conversions. This change keeps two decimals through the customer and
business projections and the new job and collection workflows. Whole amounts can
still display without a trailing `.00`.

## Money and synchronization

- New time-based quotes use the decimal hourly rate multiplied by minutes and
  divided by 60, rounded once to the nearest paisa. Existing jobs and their original
  values stay intact when an unrelated field is edited.
- A collection is planned against its ordered, scoped source jobs before writing
  any Room rows. Integer paise stop a tiny binary remainder from creating an extra
  receipt. Reversals keep the original receipt amount and record the actual original
  job balances in the queued command. No existing queued operation is rewritten or
  automatically retried by this change.
- The local Firestore emulator reproduced an authorization failure for a ₹0.30 job
  with a ₹0.10 collection and a ₹0.20 remaining balance. Exact binary sums and
  differences do not always match the stored decimal numbers. The Rules retain the
  exact historical comparison and accept only floating-point noise around the same
  paisa when it differs. They still require a matching balance change and linked
  receipt, keep role/workspace checks, and deny forged amounts and no-op receipts.
- If a historical source has genuine sub-paisa or inconsistent amounts, the app
  leaves the original records in place and requires review before collecting or
  reversing against that source. Legacy multi-job receipt reversals stay blocked.
- Equal thirds still display 33.33% each. Actual money is allocated in whole paise,
  with a stable tie order for any remaining paisa. The same remainder approach
  applies to complete custom percentages, without guessing missing members.
- English and Tamil app labels use the same amounts. Entered names, locations and
  notes are preserved exactly.

## Files changed

| File or group | Purpose |
| --- | --- |
| `FinancialCalculationEngine.kt`, `EqualShareAllocation.kt`, `WorkBillingCalculator.kt` | Paise in customer and aggregate totals, exact distribution of complete percentage shares, decimal time-based quotes. |
| `CollectionAmounts.kt`, `CollectionPolicy.kt`, `WorkspaceRepository.kt` | Exact collection and reversal plans, correct date preview for the jobs actually allocated, original Room/outbox transaction boundaries and source balances. |
| `MoneyInputText.kt`, `NewEntryDraft.kt`, `NewEntryScreen.kt` | Preserve saved money during editing, keep fractional rate/amount prefills, and use the shared ledger for existing customer suggestions. |
| `CustomerCreditDueTab.kt`, `WithdrawalTab.kt`, `AccountScreen.kt` | Keep small dues visible and fractional amounts/rates in forms. |
| `CommonComponents.kt`, `PdfGeneratorHelper.kt`, `MainViewModel.kt` | Preserve paise in displayed, shared and PDF amounts; surface a localized review message for unrepresentable saved amounts. |
| `firestore.rules`, `security-tests/architecture.test.cjs` | Validate exact decimal receipt and reversal outcomes without changing role, workspace or dependency checks. Test legitimate Owner/Partner writes and altered/no-op denials. |
| `PaiseConsistencyTest.kt`, `ExampleUnitTest.kt`, `PartnerLedgerFollowupTest.kt` | Add independent money, historical-edit and allocation checks; update the two old whole-rupee expectations to the approved precision without dropping their other assertions. |
| `DeviceEmulatorIntegrationTest.kt`, `CollectionFormDeviceTest.kt` | Check offline persistence, exact ACK/replay and partner records; exercise a Tamil quarter-rupee due and its payment prefill. Update only the Operator summary expectation that deliberately changes on this branch. |
| `docs/regression/interaction-inventory.*`, `docs/regression/asset-inventory.json` | Regenerate the source inventory. An inventory reference alone does not prove runtime coverage. |

## Release and rollback

This development change does not migrate Room, change backend services, rewrite
historical records or activate any workspace capability. The decimal Rules must
pass the full emulator suite and be reviewed before any separate production Rules
deployment. Older clients can still round displayed totals, so upgrade the partner
devices before comparing reports. Keep the existing APK and Rules release identity
available for rollback, and preserve Room and the queue on every device. A rollback
cannot reverse a confirmed receipt. If a production mismatch occurs, pause new
fractional collections and compare the original operation and authoritative source
before any approved recovery; do not clear or replay rejected operations in bulk.

## Physical verification (separate from emulator tests)

Use a compatible isolated test workspace and synthetic records. **Manual physical
devices: NOT TESTED. Automated physical instrumentation: NOT TESTED.** USB is
unnecessary for the manual workflow. An emulator run is not physical evidence.

1. On phone A, sign in as an Owner, go offline and create a ₹0.30 job with an
   initial ₹0.10 collection. Close and reopen the app while offline. Expect the
   original customer and ₹0.20 due to remain visible with pending sync. Record the
   original job and receipt operation IDs from diagnostics, where available.
2. Reconnect A. Expect both operations to confirm once and the job to appear on
   the authorized Partner's phone B with the same actor, customer, paid and due.
3. Save the remaining ₹0.20 offline on A, reconnect, and compare both devices:
   exactly two receipts for ₹0.10 and ₹0.20, ₹0.30 paid, ₹0 due. Check Customer
   Dues, Home, Collection History and the shared statement for matching values.
4. In the synthetic workspace only, reverse the ₹0.20 receipt. Both devices should
   show ₹0.10 paid and ₹0.20 due. Record the reversal operation ID and verify that
   the original receipt amount, original actor and creation time did not change.
5. Switch English/Tamil in Customer Dues. An unpaid ₹0.25 remains listed and its
   payment prefill is `0.25` in both languages; the customer name stays as entered.
   Record app version, device models, role, confirmed state and observed outcomes.
6. For automated physical verification, run the existing demo instrumentation on
   an explicitly selected physical device and retain its actual JUnit and sync
   evidence. Record separately from the two-phone manual observations. Do not
   install demo credentials into the live app or clear the live app's data.
