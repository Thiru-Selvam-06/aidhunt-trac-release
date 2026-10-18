# One-time Entry attribution repair — 2026-10-18

Status: PREPARED / LIVE REPAIR NOT VERIFIED. Release gate remains NOT READY.

## User-approved policy

For this repair only, preserve every existing beneficiary/financial assignment. For an Entry with BOTH `entryForUid` and `financialAttributionUid` missing/empty, use a separately verified creator bound to the same workspace membership; otherwise use the verified active Owner matching workspace ownership. This is an explicit Owner attribution decision, not evidence that the Owner originally created the record. Preserve `createdByUid`, audit roles, payer/collector identities, amounts, original timestamps, operation IDs and queued payloads. Do not change normal runtime attribution rules.

## Implemented safely offline

`scripts/plan_entry_attribution_repair.py` builds proposals only from a supplied capture. It reuses validation helpers from `reconcile_workspace_inventory.py`; it is not another inventory fetcher. There is no network dependency, Firebase SDK or apply mode. Output patches contain only Entry beneficiary fields; source fingerprints/update-time preconditions support later conflict review. Exact string IDs include values above JavaScript's safe integer range. Existing partial/conflicting assignments remain unchanged. Nonactive/unknown rows remain unchanged. Unresolved outbox rows block proposed repair of matching targets.

Required input: project/workspace/owner metadata, source SHA256, complete pagination and operation-inventory assertions, same-workspace membership records, exact Entry IDs/update times and original metadata, and durable operation states. `creatorIdentityVerified` must come from independent authorized provenance review, never from merely matching a name or a legacy deserialized `createdBy` field. Membership alone does not prove creation. Completeness/source assertions are supplied evidence, not authentication performed by the planner. Proposal files may contain UIDs; keep them within the authorized private evidence boundary.

## Exact blockers

No complete authorized PMS Entry/membership/outbox snapshot was available to this pass; no actual record count or patch manifest is claimed. Historical owner UID snapshots do not establish current ownership.

Local `firestore.rules` Entry update contract freezes `entryForUid` and `financialAttributionUid`; expense update similarly freezes its attribution. The existing Worker/Functions operations contain no attribution-repair endpoint. Ordinary client edits would therefore be rejected under this contract. Deployed Rules were not fetched or assumed identical. The timestamp-migration utility uses elevated Firestore access and addresses different fields; it was not executed or repurposed.

Applying a repair requires a separately authorized trusted migration path, complete reviewed snapshot/backup, per-record fingerprint/update-time checks, preservation of unresolved outbox operations, an audit receipt distinguishing verified-creator versus approved-Owner attribution, and persisted before/after verification. Do not bypass Rules, enable elevated credentials, deploy code, or retry old commands to get around this blocker.

## Fresh validation

- Python `-m unittest discover -s scripts -p test_plan_entry_attribution_repair.py -v`: exit 0, 11 tests passed.
- Python `-m unittest discover -s scripts -p test_reconcile_workspace_inventory.py -q`: exit 0, 37 tests passed.
- `git diff --check`: exit 0.

Tests assert Owner fallback, verified Partner creator, rejection of display-name/unknown-UID proof, existing assignments retained, pending/rejected commands blocked, nonactive rows retained, cross-workspace rejection, owner validation, incomplete capture rejection, duplicate/numeric IDs rejection, deterministic nonmutating proposals and metadata fingerprints. These are synthetic/offline tests, not PMS or physical-device evidence. No Android changes in this repair preparation; earlier APK/test results are not fresh repair validation.

## Preservation

Actual branch: `codex/comprehensive-regression-2026-10-17`.
HEAD: `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`.
Existing tracked/untracked changes were retained. This pass adds only the planner, its test, and this report. No production requests, data mutations, credential retrieval, deployment, app installation/launch, queue retry, commits, pushes or branch changes occurred in this repair preparation.
