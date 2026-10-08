# Intended vs. Built: Immutable Record Identity

This document defines identity preservation invariants for job entries, expenses, and payments.

---

## Comparative Domain Matrix

### 1. Creator Invariants
*   **Intended Goal (ID-001 to ID-004):** Once a record is created, its original `createdByUid`, `createdByRole`, `createdAt`, and associated `entryForUid` must remain strictly immutable after updates.
*   **Built Behavior under Test:** Handled in the repository layer using copy constructors that carry over original fields on edits.

### 2. Mutation Tracking
*   **Intended Goal (ID-005 to ID-007):** Updates to a record must cleanly capture `editedByUid`, `editedByRole`, and a fresh `updatedAt` timestamp.
*   **Built Behavior under Test:** These fields are updated dynamically in the repository prior to committing changes to SQLite/Firestore.

### 3. Edit Limit Safety
*   **Intended Goal (ID-008, ID-009):** The 24-hour edit limit window must be calculated from the original `createdAt` timestamp, and editing a record must not reset this window.
*   **Built Behavior under Test:** Checked in `CompleteRegressionPipelineTest` where original creation epoch is strictly referenced for eligibility checks.
