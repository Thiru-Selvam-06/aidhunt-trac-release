# Intended vs. Built: Offline Synchronization

This document tracks dual-device synchronization convergence across all network states.

---

## Comparative Domain Matrix

### 1. Offline Queues
*   **Intended Goal (SYNC-002, SYNC-003):** When a device goes offline, mutations are held in local SQLite queue with `isSynced = false`.
*   **Built Behavior under Test:** Asserted in `testOfflineSyncQueueAndNoResurrection` where offline inserted items are marked unsynced.

### 2. Dual-Device Convergence (Cases A, B, C, D)
*   **Intended Goal (SYNC-001 to SYNC-004):** Offline/Online states across Owner and Partner must seamlessly merge without duplicating records, dropping entries, or corrupting viewer totals.
*   **Built Behavior under Test:** Tested end-to-end in `testCaseA`, `testCaseB`, `testCaseC`, and `testCaseD` inside `CompleteRegressionPipelineTest`. Reconnect sequences trigger 2-way propagation, converging to consistent sets of 2 jobs and 2 expenses across both isolated database stores.
