# Phase 15 live-test verification — 2026-10-13

This report covers the retained collection action and dependent reversal on the separate `com.aistudio.aidhunttrac.cedlir.livetest` installation. It does not certify the whole v1.6 application. Firebase project: `trac-aidhunt`; workspace: `ws_QlVAEPVTtYSxLeWC`; role: Partner. The original production-configured package remained stopped and its data was not changed.

| Workflow | Physical-device result | Independent state | Status |
| --- | --- | --- | --- |
| Open blocked status from Home | `1 Blocked` badge opens SQLite & Cloud Sync and lists both retained actions | Screenshot `retry-build-diagnostics.png` in local temporary evidence directory | PASS |
| Retry while deployed Rules still deny Collections | Shows “Collection access is still denied by deployed Firebase Rules. Original action retained.” | Room original collection operation remains `REJECTED`, attempts 1, original ID and action time; reversal remains `PENDING`, attempts 0 | PASS for safe failure; cloud upload BLOCKED |
| Push all with rejected predecessor | Dependent reversal displays “Blocked — earlier action rejected” | Both queue rows retained; no false acknowledgement | PASS for safe failure |
| Actual collection synchronization | Deployed Rules deny collection access; original cloud collection is absent | Live backend permission denial | NOT VERIFIED / BLOCKED |
| Owner queued Entry deletion | Trusted service unavailable because Cloud Functions API is disabled | Original Owner deletion remains `PENDING` | NOT VERIFIED / BLOCKED |

Evidence files are under `%TEMP%\aidhunt-purge-v2-fix-20261013`: `retry-build-home.png`, `retry-build-diagnostics.png`, `retry-result-scrolled.png`, and corresponding UI hierarchies. The Room check read only operation ID, type, state, attempts, and original action time; it did not modify rows. Firebase credentials and operation payloads are excluded.

Changes in this phase: actor/workspace-scoped blocked predecessor reporting; actionable blocked badge and diagnostics; guarded user-triggered collection retry that checks current membership and server state before requeueing the original operation; queue identity and timestamp preservation regression test. No automatic retry of rejected financial actions was added.

Validation: `:app:assembleLiveTest` passed and the APK was installed with `adb install -r`; focused tests 20/20 passed; full `:app:testDebugUnitTest` 288/288 passed (zero failures, zero skips); `git diff --check` reported no whitespace errors. No commit, push, Rules/Functions deployment, or production data write was performed.

Release blockers: the deployed Collections and Extensions Rules still differ from the locally tested v1.6 candidate; Cloud Functions are unavailable for trusted deletion. Retest affected workflows only after separately approved backend rollout and verification of its active Ruleset and Functions. Do not treat the retained actions as synchronized before backend acknowledgement.
