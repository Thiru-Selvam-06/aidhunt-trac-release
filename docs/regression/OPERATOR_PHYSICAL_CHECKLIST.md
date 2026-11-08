# Physical-device verification — NOT TESTED

Android-emulator results do not establish physical-device results. All scenarios
below remain **NOT TESTED** until evidence is recorded. Use synthetic customer names
and an explicitly approved staging workspace; leave live rejected operations intact.
USB is not required for manual testing on two phones.

| Scenario | Manual steps | Expected result and required evidence | Status |
|---|---|---|---|
| Version/workspace | Record both APK versions, account names/UIDs, workspace and verified feature state. | Both clients use the reviewed protocol in the same authorized staging workspace; record versions without tokens. | NOT TESTED |
| Offline Operator create | On phone A, sign in as Operator, verify activation online, disconnect, create a new basic customer and own ordinary job. | Name/phone/location stay raw, job and profile remain visible pending; capture operation IDs, actor, amount and dependency order. No payment/financial read appears. | NOT TESTED |
| Restart durability | Close A, restart phone, reopen while offline. | Exact original profile/job, actor, timestamps and IDs remain pending; screenshot and retained queue diagnostics. | NOT TESTED |
| Reconnect and peer | Restore A's network; phone B remains online in another authorized account. | CUSTOMER then JOB confirmed once; B receives matching name, job ID, actor and amount; finance-authorized B sees correct derived dues, Operator B sees no financial customer profile. | NOT TESTED |
| Interrupted ACK | On staging only, interrupt network during upload, then reconnect. | One source/projection and one job, original creation receipt and immutable actor/time; capture cloud records and local ACK IDs. Never retry a previously rejected live financial action. | NOT TESTED |
| Disabled workspace | Use a synthetic workspace without capability flags, with new collections denied. Create and edit an ordinary customer/job, then explicitly delete that synthetic job. | Existing paths succeed without directory access; deletion proof matches original ID/amount/actor and unused customer disappears. New feature controls are unavailable. | NOT TESTED |
| Unauthorized identity | Sign out/switch to an unrelated account/workspace without changing retained operations. | No cached flag or metadata leak, no cross-workspace upload or reattribution; original actions preserved. | NOT TESTED |
| Checked deletion | In active staging workspace, create then delete an unused synthetic customer/job; separately try a referenced customer. | Unused source and directory disappear atomically with audit proof; referenced customer and unresolved financial dependencies remain protected. | NOT TESTED |
| Language | Switch English/Tamil on both phones. | App labels switch; entered customer/business text, amounts, UID attribution and custom names do not change. | NOT TESTED |

For **automated physical instrumentation**, separately record actual connected phone
model/Android version, exact reviewed SHA/APK, executed test classes, JUnit counts,
operation/ACK database evidence and logcat. No such execution has occurred here.
For **manual physical tests**, record who ran each scenario, date, both phone
versions, actual result and redacted evidence. Do not label manual evidence as
automated instrumentation or mark a scenario passed based only on a toast.
