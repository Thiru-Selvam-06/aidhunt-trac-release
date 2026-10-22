# October 22 maintenance: frozen acceptance expectations

Baseline: repository `Thiru-Selvam-06/aidhunt-trac-release`, development parent `8fe06c02983369d23a69fa3fa0e6afdd9b173b5c`. Tamil-only change set `0f6c529` is published on `codex/tamil-localization-fix`; main is unchanged. Existing tests and release gates remain mandatory.

These expectations come from the user's two October 22 attachments and clarification. They must not be changed merely to match application output.

| ID | Synthetic input | Independent expected outcome | Primary verification |
| --- | --- | --- | --- |
| ROLE | Owner alone / with Operators / with an active Partner | Owner / Owner / Partner display; Operators remain Operators; authorization roles unchanged | Pure role tests + Compose |
| ROLE-REMOVAL | Last Partner removed after partnership existed | Creator stays visibly Partner (user clarification); original Owner authority unchanged | Presentation policy and persisted nonfinancial preference |
| INITIAL | Owner records job for Partner, billed 1100, received 900 | Creator/recorder Owner; beneficiary/collector selected Partner; due 200 | Repository Room/outbox + Emulator |
| OLD | Owner records historical job for Partner, billed 2000, received 1200 | Historical dates unchanged; collector Partner; due 800 | Repository + Emulator |
| ZERO | Initial receipt zero | No payment row or payment operation | Repository |
| HISTORY | Job beneficiary A, later collection by B | Entry For A; Collected By B; recorder unchanged | Compose + existing grouping tests |
| DUES | Customer Raman with A due 300, B due 700 | A filter 300; B filter 700; All 1000; filter jobs by UID before grouping | Pure aggregation + Compose |
| EXP-WIRE | Standalone expense has no related job, either payer source | Existing Rules contract uses numeric zero; linked positive job ID unchanged; original amount/actor/payer/timestamps unchanged | Serialization + actual Firestore Rules |
| EXP-UPGRADE | Retained old expense-edit base has null source; unchanged server uses missing/null/zero | No false conflict and no mutation to retained original JSON; real source/money/payer/workspace/identity changes still rejected | Original compiled-code reproduction + conflict compatibility regression |
| EXP-AUTH | Active Owner/Partner, correct workspace, amount 1/1/2 | Valid standalone expense commits once; outsider/Operator/foreign actor denied | Emulator |
| EXP-DURABLE | Offline standalone expense and original outbox command, then restart | IDs, amounts, actor, workspace, timestamps and payload survive; only server ACK permits Synced | Room + outbox integration |
| PERSONAL | Cash 10000, member personally funds 900 | Cash 10000; member investment 900; zero artificial withdrawal | Independent financial assertions |
| BUSINESS | Cash 10000, business funds 900, three equal Partners | Cash 9100; allocated cost 300 each; zero personal investment/withdrawal | Independent financial assertions |
| PAISA | Business funds 1 or 3, three equal Partners | Allocation totals exactly 1 or 3; valid 0.34/0.33/0.33 and 1/1/1; deterministic across devices | Fixed and randomized conservation tests |
| ROUNDING | Repeated tiny expenses | No permanent creator priority; preserve explicit unequal settings; unresolved policy requires clarification before changing existing economics | Policy audit + allocation tests |
| LOCALIZATION | EN → TA → EN, mixed-language entered values | Only app labels change; names, notes, contact details, custom work types and money stay exact | UI + source integrity + screenshots |
| SECURITY | Removed/unknown member or workspace B | Denied; no privilege change from display labels; no Rules weakening | Existing + added Rules cases |

Initial known failure: the Compose partition at the Tamil change ran 57 tests, 56 passed and the new person-filter dialog test timed out on Windows and Linux. CI run 39755775953 failed its aggregate correctly. All 19 Android emulator tests passed, including the complete person-dialog journey. The user explicitly approved keeping that journey and its assertions mandatory on Android and retaining metric/language checks in Robolectric. Original logs are retained outside Git; do not replace them with a green-only history.

Approved funding hardening: new expenses and changed funding fields must have a recognized source. I Paid requires an eligible, authorized active payer UID in the exact workspace; Business Paid requires an empty personal payer UID. The user explicitly confirmed retaining an Owner's existing ability to record an active Operator as payer; Operators gain no financial write authority. Unchanged historical fields remain untouched. Existing access-test outcomes and amounts stay fixed; only two new-create fixtures now explicitly include valid payer fields.

The three real rejected expenses have not been inspected: the user no longer has their diagnostic text available. Their individual actor/payer/amount/timestamp/server state must therefore be reported UNVERIFIED. They are not duplicates by assumption and are not replayed by these tests.

Production records, pending actions and backend deployments are outside this change's execution scope. Automated physical-device testing and manual physical-device testing both remain NOT TESTED. No USB device is needed for CI/emulator testing or the later manual two-phone checklist.
