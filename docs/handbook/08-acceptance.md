# 8. Manual acceptance

Use an isolated authorized package/workspace. Record source commit, APK hash, backend revision, role, workspace and queue counts. Preserve all valuable queues.

For each inventory control: record starting state; tap/cancel/repeat; validation; Room transaction; command identity/dependencies; server decision and persisted state; UI after acknowledgement and restart. Mark only exercised scenarios PASS.

Critical journeys:
1. OTP, no/one/multiple workspaces, stale default, offline session and revoked membership.
2. Owner invites another workspace's Owner as Partner; verify target OTP, role/index, retained personal Owner/default and duplicate redemption.
3. Owner creates Partner job with personal Expense; independently verify creator, beneficiary, payer, investment and cash.
4. Home/Recent Jobs/Business Overview deletion with linked Expenses, partial/full Collections, reversed audit and shared allocation protections. Exercise rejection, lost acknowledgement, duplicate delivery, offline/reconnect/restart.
5. Cash/noncash partial collections, grouped timelines, India dates, reversal exactly once and exact due/cash.
6. Standalone Expense deletion, Partner withdrawals and denied role/unsupported edits.
7. Account/workspace switch with colliding IDs, distinguishable totals and pending work; no redirected uploads.
8. Actor-scoped pending versus synced status, report/PDF permissions, empty/loading/error states and small screens.
9. Member removal retains history; purge requires separate authorized disposable fixtures.

Immediate invitation failure: inspect begin endpoint/current root Owner/status before blaming OTP. Owner-elsewhere role: inspect selected-workspace membership versus stale mirror. Reappearing deletion: inspect server rejection, queue, acknowledgement and listener ordering. Different pending badges can reflect different actors. Missing attribution is not permission to invent identities.

Decode screenshots, establish scenario provenance and redact logs. Screenshots alone do not prove transactions. Never clear storage, manually edit Room, retry old rejected financial commands or alter roles merely to pass tests.
