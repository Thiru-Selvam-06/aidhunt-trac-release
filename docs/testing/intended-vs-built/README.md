# Intended vs. Built Specifications

This folder provides individual comparative reports tracking the gap between **Intended Goals (the formal business/architecture specs)** and the **Built Behavior (what our automated JVM/Robolectric test suite compiles and asserts)**.

## Comparison Domain Categories

1.  [**Workspace Isolation** (workspace-isolation.md)](workspace-isolation.md): Securing boundaries between workspaces.
2.  [**Role Authorization** (authorization.md)](authorization.md): Restricting Owner, Partner, and Operator privileges.
3.  [**Payments & Reversals** (payments.md)](payments.md): Non-destructive collection reversals and oldest-job allocations.
4.  [**Immutable Identity** (identity.md)](identity.md): System logging invariants (e.g. `createdByUid`, creation windows).
5.  [**Investment Attribution** (financial-integrity.md)](financial-integrity.md): Viewer-independent investor shares based on stable UIDs.
6.  [**Offline Synchronization** (offline-sync.md)](offline-sync.md): Local queue storage and dual-device convergence matrices.
7.  [**Sessions** (session.md)](session.md): Keeping cached token credentials secure across process restarts.
8.  [**Withdrawals** (withdrawals.md)](withdrawals.md): Withdrawal management limits.
9.  [**Assets (Tractors/Extensions)** (assets.md)](assets.md): Assets and extensions rules.
