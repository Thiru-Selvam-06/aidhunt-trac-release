# Customer editing — verified development results

Source revision: `6211caaca53de449b2fbc0a132de7ecd2a6bde24`.
Draft PR: https://github.com/Thiru-Selvam-06/aidhunt-trac-release/pull/5
Base: `codex/tamil-localization-fix`, not `main`.

## Executed GitHub Actions suites

Both independent runs completed the full required gate successfully:

- PR run: https://github.com/Thiru-Selvam-06/aidhunt-trac-release/actions/runs/40084761502
- Push run: https://github.com/Thiru-Selvam-06/aidhunt-trac-release/actions/runs/40084758392

The PR tested merge revision `d1bf4b2dea144ec2584246d9f2620d12badbd044`.
Its tree and the source-head tree are identical:
`6b2457fe6cdd8ecfeacd86affc5ef3f8542ade53`.
The results below describe executed tests at that revision; subsequent evidence-only
documentation does not imply that a different application revision was tested.

| Suite | Tests in EACH run | Failures / errors / skipped |
| --- | --- | --- |
| Kotlin/unit | 321 | 0 / 0 / 0 |
| Room/database | 151 | 0 / 0 / 0 |
| Outbox | 139 | 0 / 0 / 0 |
| Compose/Robolectric | 57 | 0 / 0 / 0 |
| Backend | 131 | 0 / 0 / 0 |
| Firestore Rules | 307 | 0 / 0 / 0 |
| Actual Android API 35 emulator | 30 | 0 / 0 / 0 |
| **Total** | **1,136** | **0 / 0 / 0** |

Compilation/lint, source/test inventories, publication scanner and Linux Worker
build-only validation also passed. The separate performance-baseline workflow is
manual-dispatch only; its non-dispatch job is skipped by its existing event condition,
not counted as an executed test. No assertions or tests were removed to pass.

The downloaded aggregate archives' SHA-256 values are:

- PR: `ba832405ce1dd4b61c8f6d220ae44c19c3e51c94a45898ff6a76aca263a24311`
- Push: `f1746951e88d20923fccd5ec823ac596830629c3d23b38c81263d62170e47287`

## Synchronization evidence

All 30 actual Android-emulator cases passed in each run. This includes the real
WorkManager automatic reconnect upload (case l), offline profile merge/Room reload
(p), paid reassignment and authorized receipt reversal (q), future-clock pending
preservation and blocked dependent payment (r), and small-skew exact-once ACK (s).
Case l took 2.602 s in the PR run and 2.900 s in the push run. The active network's
actual VALIDATED capability was asserted, using a guarded local CI connectivity
endpoint. This does not claim real mobile internet validation or physical testing.

Each retained demo Rules-coverage artifact contains 105 requests. Inspection found
no future-action requests and no request actor/Auth UID mismatches. The fresh Owner
job retained its original action time, 207 ms before the PR request and 96 ms before
the push request. Exact IDs, amounts, history, ACK and replica equality remain
assertions in the executed journeys; success is not inferred only from a toast.

Earlier failures remain evidence: ACK mapping and audit number-type defects were
corrected in 7c12a1f; five 31–101 ms future requests in run 40054806456 confirmed the
clock condition; a non-void JUnit method in run 40057574452 was fixed in 048c78e;
run 40058675979 retained four operations with zero attempts because external network
validation failed. The CI-only connectivity fixture addresses that latter case.
None of these results proves which predicate rejected an unavailable historical
phone payload. Original Muniyappan/payment/expense failure details remain unverified.

## Separate release limits

- Hosted Cloudflare preview build: **FAILED**, due to the existing root/Wrangler
  trigger versus the cf/Vite project in `cloudflare/`. The build-only job passes;
  persistent trigger, isolated preview bindings and deployment need explicit approval.
- Production Rules/deployment, main merge and production APK distribution:
  **NOT PERFORMED**. The local single-job Rules prototype requires approval.
- Live rejected-operation recovery and production customer inventory/cleanup:
  **NOT PERFORMED**. No UUID, original time, amount or historical actor was rewritten.
- Automated physical-device instrumentation: **NOT TESTED**.
- Manual multi-phone acceptance: **NOT TESTED**; USB is not required. Use the customer
  checklist and `OCT22_PHYSICAL_DEVICE_CHECKLIST.md` with isolated synthetic data.
- The three pre-existing blocked instrumentation classes retain their explicit
  fixture requirements in `scripts/instrumentation-plan.json`; they are not included
  in the 30 executed emulator cases or silently counted as passing.

No production records, phone storage or pending operations were modified. Keep the
PR in draft until the remaining authorized staging/release checks are satisfied.

## October 24 staging follow-up and candidate identity

After explicit approval, only the development trigger's root directory, build command
and preview command were corrected to `cloudflare`, `npm run build` and
`npx cf previews deploy`. Hosted build `3aff5db2-1be7-4e71-a308-37a1a075abd8` at
evidence commit `3f0ba42` passed and deployed branch preview
`916dc385-16f7-4336-b371-6942b7a90101`. This supersedes the earlier hosted-build failure
above; the original failure is retained as historical evidence.

Preview URL: https://codex-customer-identity-editing-aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev

Actual HTTP checks passed: health 200 with the expected project and credential
presence true, unknown endpoint 404, unauthenticated operation 401. Runtime metadata
confirms dedicated staging workspace `ws_cf_staging_20261015_login_a62e71`. No secret
value was read or copied. The existing Worker's October 15 version
`919ce27d-6562-449d-9392-a6d1eea5f0b0` remains deployed at 100%; production routing,
Firestore Rules and real customer records are unchanged.

Authenticated hosted financial journeys remain NOT TESTED because no current
synthetic phone-OTP client session was available. Emulator results are separate.
No live or historical rejected financial action was replayed.

The next candidate is identified as **1.0.10 / versionCode 11** instead of reusing
1.0.9 / 10. This changes release identity only, with no financial or authorization
logic change. Its fresh build/signature and mandatory CI results must be recorded
before distribution. Production Rules, customer APK distribution and main merge
remain subject to explicit approval and the physical/staging acceptance gate.
