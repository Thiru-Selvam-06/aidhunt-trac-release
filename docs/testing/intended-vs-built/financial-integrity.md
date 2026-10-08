# Intended vs. Built: Financial Integrity & Investment Attribution

This document describes financial invariants and stable investor attribution.

---

## Comparative Domain Matrix

### 1. Financial Invariants
*   **Intended Goal:** Total Recorded - Total Outstanding Due == Total Collected.
*   **Built Behavior under Test:** Tested in `testFinancialInvariantTotals` asserting that totals from multiple active job sheets strictly agree with the basic algebraic constraint under overall scope.

### 2. Investment Attribution
*   **Intended Goal (FIN-001 to FIN-008):** Share totals must strictly associate with the investor's stable `paidByUid`, guaranteeing viewer independence on both Owner and Partner overview panels.
*   **Built Behavior under Test:** Validated in `testOwnerPartnerFinancialAttribution` (in `CompleteRegressionPipelineTest`) and `PartnerWithdrawalsProfitShareTest.kt` ensuring that the same ₹14,000 / ₹4,000 / ₹18,000 amounts are computed on both Owner and Partner overview screens without viewer-bound bias.
