# Expense phase — physical verification checklist

All cases below are **NOT TESTED**. JVM tests and Android-emulator runs must not
be relabelled as physical evidence. Use synthetic records in a genuinely isolated
demo project/workspace, never the live customer business. Record APK/Rules source
revisions, business ID, actor UID and device model/Android version before testing.
Do not clear either phone's local data or retry an existing rejected action.

## Manual two-phone verification — no USB required

1. **Offline category retention — NOT TESTED.** Partner A disconnects both Wi-Fi
   and mobile data, adds `QA Engine Oil`, selects it and creates a synthetic expense
   for 1234.56. Close/reopen the app while still offline. Expected: category and
   original expense remain available, pending is visible and no Synced claim is
   made. Evidence: category selection, exact amount, queue operation ID/actor/time
   and pending status before/after reopening.
2. **Reconnect and second phone — NOT TESTED.** Reconnect A; Partner B opens the
   same isolated business. Expected: one confirmed category and one expense with
   the original payer/creator and amount. Both phones show identical confirmed
   records. Evidence: matching IDs/amount, ACK state and server readback in the
   demo console; do not record authentication tokens.
3. **Equivalent concurrent names — NOT TESTED.** Both phones add `QA Welding`
   and `  qa   WELDING  ` while offline, then reconnect. Expected: one category;
   the first confirmed display/creator stays intact and both new commands resolve
   without duplicate money. Evidence: both operation IDs and exactly one matching
   normalized category document. Verify no extra expense was created by adding a
   category alone.
4. **English/Tamil and entered text — NOT TESTED.** Switch languages on both
   expense forms, filters and breakdown. Expected: built-in app information changes
   language; custom names, descriptions, customer/business names and amounts do
   not change. Evidence: paired screenshots with the same record ID and raw text.
5. **Paise and refund edits — NOT TESTED.** Reopen an eligible 1234.56 expense and
   a synthetic -25.75 refund; edit only description and save. Expected: both exact
   signed amounts, original creator/time/payer and linked job remain unchanged.
   Evidence: before/after server fields and local/other-phone display. A rejected
   edit must retain its action without being silently retried.
6. **Charts and dates — NOT TESTED.** Add 100.25 Business Paid, -20.10 Business
   refund, 50.50 personally paid and -3.30 with a legacy unavailable funding marker
   through an approved synthetic fixture. Expected: charges 150.75, refunds 23.40,
   net 127.35; funding nets 80.15, 50.50 and -3.30. Pie slices are positive charges
   before refunds. Change Today/Month/Year/Custom range and a phone's timezone;
   lists and charts must use the same IST business-day boundaries. Evidence:
   selected filters, same record IDs and reconciled amounts.
7. **Authorization — NOT TESTED.** Use an active Operator and an unrelated business
   account. Expected: Operator may use authorized category choices but cannot add
   or acknowledge categories; another business's choices/records remain hidden.
   Evidence: actual denial and unchanged server records, not just a hidden button.

## Automated physical Android instrumentation

**NOT TESTED.** No connected phone instrumentation run is claimed. After explicit
device access is available, use the existing isolated `.devicetest` application and
mandatory instrumentation plan on actual Android hardware. Confirm the demo
project/package guards pass before running anything. Collect actual JUnit XML,
instrumentation log, operation IDs/ACKs, server records, device model/API and exact
APK/Rules revisions. The category offline/reconnect/empty-peer-Room journey and
Tamil dialog journey must execute; compilation alone does not satisfy them.

Keep these results separate from the manual two-phone evidence above and from
GitHub Android-emulator evidence. Installation on a production app ID, live-data
recovery, production Rules deployment and customer rollout require separate approval.
