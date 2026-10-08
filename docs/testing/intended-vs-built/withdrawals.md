# Intended vs. Built: Withdrawals

This document covers expected withdrawal management permissions with actual code validations.

---

## Comparative Domain Matrix

### 1. Owner Delete
*   **Intended Goal (WITH-001):** Owners can delete/reconcile eligible withdrawal entries freely.
*   **Built Behavior under Test:** Checked in `CompleteRegressionPipelineTest` where Owner can perform mutations on withdrawals without timeframe limits.

### 2. Partner Own Mutation
*   **Intended Goal (WITH-002, WITH-003):** Partners can manage only their own withdrawals within 24 hours of creation.
*   **Built Behavior under Test:** `createdByUid` checks and age constraints are validated in the service layer under simulated environments.
