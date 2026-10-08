# Intended vs. Built: Payments & Reversals

This document covers collection payments, FIFO allocations, and non-destructive reversals.

---

## Comparative Domain Matrix

### 1. Payment Deletion
*   **Intended Goal (PAY-001 to PAY-005):** Deleting collections must act as an authoritative reversal instead of raw record destruction.
*   **Built Behavior under Test:** Asserted in `testMultipleIndependentPaymentEvents` where deleting partial payment A preserves payment B, restores outstanding dues, and updates linked job values.

### 2. Allocation Engine
*   **Intended Goal (PAY-013, PAY-015):** Payments are applied following oldest-job-first order (FIFO). Reversals undo allocations newest-job-first (LIFO).
*   **Built Behavior under Test:** Validated in `PaymentReversalAndCollectionHistoryTest.kt` asserting that job pending balances adjust exactly according to chronological FIFO/LIFO rules.
