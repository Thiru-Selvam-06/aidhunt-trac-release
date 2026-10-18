# 9. Known limitations

**Release gate: NOT READY.**

Recent stabilization changes are preserved, including workspace initialization, picker, identity checks, member/circle display, collection history, financial/sync and acknowledgement diagnostics. Historical data repairs are not replayed by clone or CI.

Open gates:
- Missing production trusted backend; no deployment/routing change included.
- Every discovered control lacks complete role/state-specific executed end-to-end evidence.
- Three instrumentation classes need additional fixture/auth setup and are explicitly unexecuted.
- Real phone OTP/SMS, deployed services and comprehensive physical-device acceptance remain separate.
- Room 19 synthetic migrations do not prove every historical deployed database; authentic sanitized schemas remain needed.
- Original client actionTime retains the approved clock-manipulation limitation.
- Fixtures and old evidence do not prove current business counts, memberships or balances.
- Historical signing exposure needs review. Secrets remain local; Git history is not rewritten.
- Cloning reproduces source and synthetic tests, not private accounts/data/keys/console settings.

Read actual GitHub commit/run artifacts. A green workflow is not all 327 requirements verified, nor production rollout approval. No completion percentage is assigned.
