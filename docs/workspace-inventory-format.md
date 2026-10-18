# Local workspace inventory format v1

Approved PMS reference policy: [Option A — existing ledger, no new financial baseline](existing-ledger-reference-policy.md). Snapshot observation time never introduces a financial cutoff or opening transaction.

This tool has no network client, Firebase dependency, database connection or provisioning behavior. It compares two explicitly provided JSON files. It does not extract production records, grant authorization, compute balances, establish an opening date, or claim that a source is trustworthy.

Run from the implementation worktree:

```powershell
python scripts/reconcile_workspace_inventory.py --expected approved-source.json --observed local-snapshot.json --output new-result.json
```

Exit 0: `MATCH_WITHIN_DECLARED_SCOPE`. Exit 1: `FAIL` (differences) or `INCOMPLETE` (metadata/completeness gaps). Exit 2: malformed/unreadable input or CLI safety error. Existing output files and input files are never overwritten. Output contains one-way identity fingerprints, field names and category counts, not original IDs, amounts, customer data or raw command payloads. Input content is never echoed on validation error. Input byte hashes are recorded for valid comparisons. An existing destination/output-access error is not proof of successful verification.

## Input contract

See `test-artifacts/pms-workflow-gate-2026-10-18/synthetic-final-expected.json` for an executable example. Every field shown is required; unknown fields are rejected to prevent accidental customer/payload dumps. Missing historical metadata uses explicit null, never the current time. Null businessTime or unknown state prevents a complete match. A missing operationTime is allowed for records with no known associated operation; it must not be invented. Times are positive signed-64-bit integer milliseconds. Record IDs and operation targets are strings even above JavaScript's exact integer range.

Top-level fields:

- `schemaVersion`: 1.
- `workspaceId`: exact workspace scope (or consistent approved opaque alias).
- `identityScheme`: `opaque-v1` (exact IDs or approved stable aliases) or `sha256-v1` (64 lowercase hex ID digests). Both inventories must use the same scheme and transformation. Never use display names or phone matching as identities. For privacy-sensitive exports, use an approved keyed/opaque mapping; raw predictable-ID hashes do not by themselves anonymize data.
- `source`: `label`, `environment` (`SYNTHETIC`, `EMULATOR`, or `AUTHORIZED_SANITIZED_EXPORT`), `projectId`, `artifactSha256`. This is provider-declared provenance, not verified authority. Point artifactSha256 to the originating approved export/fixture definition and retain its provenance separately.
- `snapshotTime`: ISO8601 including timezone. This is observation time, never transaction or authorization time. Deliberately different observation times are not automatically a mismatch: the provider must select comparable expected/observed states and explain intervening changes.
- `scope`: unique nonempty `categories`, exact `queryScope` string describing category/status/date filters, booleans `complete`, `paginationComplete`, `operationsComplete`. Use false when pagination/export coverage is unknown. Scope flags are assertions by the provider; the tool cannot prove that a supplied export includes every remote row. Partial category scopes cannot prove an entire workspace's count.
- `records`: metadata only, as below.
- `operations`: durable command metadata, as below. This separate ledger is not a set of extra business records.

Supported categories: ENTRY, CUSTOMER, COLLECTION, EXPENSE, WITHDRAWAL, TRACTOR, EXTENSION. Map existing schema collections explicitly (for example PaymentEntity/Firestore collections to COLLECTION); retain the mapping and query definitions in the export procedure. No schema migration or synthetic PMS population is implied.

Each record requires `workspaceId`, `category`, `id`, `status`, `businessTime`, `operationTime`, `deleted`, `archived`, `syncState`, `attributionSha256`.

- status: ACTIVE, REVERSED (COLLECTION only), DELETED, ARCHIVED, UNKNOWN. Preserve raw-state interpretation in approved export metadata. Do not manufacture ACTIVE for unknown legacy records.
- deleted/archived: explicit booleans. Retained rows are compared, not removed for display convenience.
- syncState: ACKNOWLEDGED, PENDING, IN_FLIGHT, REJECTED, INVALIDATED, UNKNOWN. Do not infer command acknowledgement from a disappearing UI row or upload attempt.
- attributionSha256: digest of a documented, consistently ordered canonical representation of stored creator, payer, beneficiary, collector and financial-attribution identities, with explicit missing markers. Never substitute the logged-in viewer. The tool checks equality of digests; it cannot reconstruct attribution or authenticate its source.

Each operation requires `operationId`, `workspaceId`, `actorSha256`, `category`, `targetId`, `mutationType`, `protocolVersion`, `actionTime`, `payloadSha256`, `dependencies`, `state`.

- mutationType uses normalized CREATE/UPDATE/DELETE/REVERSE labels; document mapping of actual UPSERT payloads rather than changing the stored command.
- actorSha256/payloadSha256: approved stable actor digest and exact durable payload-byte digest. Do not reserialize the original payload before hashing. Original payload remains in its authorized source, never in this inventory.
- dependencies: original command IDs, order-independent for comparison; duplicates are invalid.
- protocolVersion 1 or null actionTime records a legacy unresolved command and prevents a complete result. Never invent protocol-v2/actionTime for an old command. Unsupported versions are invalid input, not accepted upgrades.
- Nonacknowledged local rows marked PENDING/IN_FLIGHT/REJECTED require a matching same-workspace/category/target/state durable operation. State disagreements remain failures. A record restored to confirmed state may coexist with a separate rejected command; compare both layers.

## Interpretation

Identity is `(workspaceId, category, recordId)`; command identity is `(workspaceId, operationId)`. Missing, unexpected and duplicate identities are separate findings. Category changes appear as missing/unexpected records, so equal counters cannot cancel them. Matching identities compare every supplied state/time/attribution field; commands compare actor, target, payload hash, original action time, protocol, dependencies and durable state. Cross-workspace rows fail.

Counts are per category: all rows, ACTIVE + not deleted/archived + ACKNOWLEDGED, pending/in-flight, rejected, reversed, deleted and archived. Diagnostic buckets can overlap; do not add them as distinct records. No total across heterogeneous categories is defined. Pending/rejected rows are excluded from activeAcknowledged, but preserved and compared. These counts do not set the financial policy for optimistic local displays or historical accounting.

A match proves only equality within declared scope. It is not PMS login, financial baseline, live authorization, 183-record reconciliation or end-to-end acceptance. Before real reconciliation, supply separately authorized sanitized authoritative and Room inventories, category definitions, complete pagination evidence, capture/build/UID-to-workspace provenance, relevant filters and approved historical-state interpretation. No such PMS inventory currently exists in the reviewed local evidence.

## Required category coverage for an approved reference

Repeat `--required-category` for every category in the independently approved reference scope. For example, a scope requiring Entry, Collection, Expense and Withdrawal needs:

```powershell
python scripts/reconcile_workspace_inventory.py --expected approved-source.json --observed local-snapshot.json --required-category ENTRY --required-category COLLECTION --required-category EXPENSE --required-category WITHDRAWAL --output new-result.json
```

This example is not a claim that PMS has exactly those categories or counts. Set the actual scope from the authorized inventory specification. A required category omitted from both inventories yields `INCOMPLETE` with `REQUIRED_CATEGORY_MISSING` gaps and exit1, even when their partial records match. Explicitly declared empty categories satisfy coverage only under the provider's completeness assertion; the tool does not verify remote absence. Missing coverage remains listed when additional mismatches make the overall result FAIL. Unsupported required categories are rejected. Output records `requiredCategories` for reproducibility. Existing usage without this option retains only a match-within-declared-scope meaning.
