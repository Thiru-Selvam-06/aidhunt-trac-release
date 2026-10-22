# Tamil display and language-switching repair

This change starts from development commit `8fe06c0` in the current repository, preserving the earlier offline-sync corrections. It changes app presentation, not customer records, financial calculations, authorization or synchronization.

## Confirmed causes

- `BusinessOverviewScreen.kt` contained UTF-8 Tamil and symbols previously decoded as Windows-1252 and then saved as source. The affected text included report headings, chart metrics, calendars, buttons and rupee symbols. The baseline has 101 affected lines, including comments. The damaged sequences were reversibly restored in source; no runtime text conversion was added.
- `CustomerCreditDueTab.kt` contained literal question-mark strings for its three Tamil customer status labels. These are now `செலுத்தப்பட்டது`, `நிலுவை` and `நிலுவை இல்லை`.
- The overview date label and three expense date formatters were remembered without the selected language as a key. They could keep the previous language until the component reopened. Those caches now update when the language changes. An English app selection uses English expense dates even on a Tamil-configured device.
- Some overview/person-filter and role labels bypassed localization. They now use app translations while displaying actual member names unchanged.
- The shared work-type display helper replaced any custom label containing words such as “Rotavator,” “Loader” or “Payment” with a built-in translation. It now translates exact recognized app options and preserves custom work-type names.

## Data boundaries

Customer and business names, partner names, phone numbers, locations, descriptions, notes and custom work-type names are entered data. They are not passed through a general translator or character-repair routine. Only app-owned text is localized. The person-filter's “All” option is localized by its filter identifier; a real member named “All” keeps that name.

No database migration, record update, historical-data rewrite, Firebase Rules change, live-data recovery or device reset is involved. No new dependency, font download, translation service or monitoring service is added.

## Changed files

| File | Purpose |
| --- | --- |
| `app/src/main/java/com/example/ui/screens/report/BusinessOverviewScreen.kt` | Restore damaged Unicode; refresh date labels; localize filter and role labels. |
| `app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt` | Replace lost Tamil status text and use the same case-insensitive language selection as the other reports. |
| `app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt` | Refresh list, form and detail date formatters when the app language changes. |
| `app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt` | Localize dropdown role labels while preserving member names. |
| `app/src/main/java/com/example/ui/util/Localization.kt` | Preserve custom work-type names. |
| `app/src/test/java/com/example/TamilLocalizationUiTest.kt` | Exercise language switches, report controls, customer statuses, dates and unchanged entered values. |
| `app/src/test/java/com/example/LocalizationDataPreservationTest.kt` | Check custom names remain exact while recognized built-in labels still translate. |
| `scripts/test_localization_integrity.py` | Reject damaged Unicode and question-mark-only app labels in the existing Python CI gate. |

## Verification record

The new source checks scan all 112 main Kotlin/Java/XML files. They detected the baseline overview corruption and all three lost Customer Dues labels; the corrected sources pass. All 110 Python tests passed using a workspace-local temporary directory.

The local Compose regression partition executed 57 tests: 56 passed and one new person-filter dialog test failed with `AppNotIdleException`. Eight of the nine new Tamil UI tests passed; the two plain JVM data-preservation tests also passed. The failing test and assertions remain enabled. A matching mandatory Android instrumentation journey has been added to distinguish Android behavior from the local Windows Robolectric environment. Final CI results are pending; this is not a passing release gate.

Native-rendered images of Tamil Customer Dues and Business Overview were inspected. They show readable Tamil, correct rupee symbols and unchanged entered names/contact details. The overview's expense toggle now wraps below its legend when space is limited instead of overlapping it.

The added Android instrumentation class is registered in `scripts/instrumentation-plan.json` and requires the isolated `.devicetest` application with Firebase project `demo-aidhunt-v16`. Version 1.0.9 (code 10) is reserved for this development update; no production APK has been distributed for this change.

Automated physical-device testing: NOT TESTED. Manual physical-device testing: NOT TESTED. Neither emulator results nor native JVM rendering are physical-device evidence. No phone or USB connection is required for the automated CI work.
