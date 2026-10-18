# Developer handbook

This describes the checked-in implementation and safe reproducible test environment. Read it alongside Architecture v1.6 and accepted amendments; it is not complete runtime acceptance.

1. [Setup and build variants](01-setup.md)
2. [Architecture and source map](02-architecture.md)
3. [Identity, authorization and invitations](03-identity-and-roles.md)
4. [Ledger calculations and attribution](04-ledger.md)
5. [Offline commands and recovery](05-offline-and-recovery.md)
6. [Tests, CI and coverage](06-verification.md)
7. [Backend configuration and security](07-backend-and-security.md)
8. [Acceptance and troubleshooting](08-acceptance.md)
9. [Known limitations](09-limitations.md)

References: [control candidates](../regression/interaction-inventory.json), [assets/endpoints](../regression/asset-inventory.json), [role/action matrix](../regression/role-action-matrix.csv), [suite inventory](test-suite-inventory.json), [instrumentation plan](../../scripts/instrumentation-plan.json), [architecture source](../specification/README.md).

Unit, SQLite/Robolectric, Android emulator, physical-device and live-service evidence are distinct. Consult actual run artifacts.
