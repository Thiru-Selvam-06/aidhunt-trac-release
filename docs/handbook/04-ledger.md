# 4. Financial identity and calculations

Keep these distinct:
- createdByUid: authenticated recorder/audit creator.
- entryForUid and financialAttributionUid: job beneficiary.
- expenseForUid: expense beneficiary.
- paidByUid: actual personal funder; paidBy distinguishes I Paid/Business Paid.
- collectedByUid: selected collector; recorder remains audit identity.

Owner-entered Partner jobs do not automatically make Owner the payer or beneficiary. Viewer identity never rewrites financial attribution.

## Current engine behavior

FinancialCalculationEngine scopes jobs by start time then createdAt; expenses by expense date then createdAt; withdrawals by timestamp then createdAt. Multiple workspaces without explicit scope are rejected.

Recorded amount excludes synthetic payment jobs and uses totalAmount, falling back to received+pending. Received uses job amountReceived. Due excludes synthetic jobs and uses pendingAmount, with billed-minus-received fallback. Test legacy imports before changing these rules.

With canonical payments supplied:
**Available cash = ACTIVE Cash collections − Business Paid expenses − withdrawals**, in selected workspace/date scope, using existing whole-rupee rounding. Personally funded I Paid expenses are investment, not cash-drawer outflow. Negative cash is retained.

Without payments input, calculateSummary retains its legacy received-minus-expenses-minus-withdrawals fallback. Check the actual caller rather than assuming identical semantics.

Investment uses personally funded Expenses matched by paidByUid. Profit-share uses active eligible Owner/Partner identities, valid configured allocations summing to 100 and rounding reconciliation. Operators are not financial participants. Idle eligible people have zero values. Unknown legacy identity is not permission to invent attribution.

## History and reference policy

Each collection retains its own record ID/audit identity. Collection History groups by workspace+job; two jobs for one customer remain separate. Timeline amounts/collector/date/method are individual events. Reversed/deleted states follow active-total and audit policy.

Reversal restores due once and retains REVERSED history. Compound job deletion follows transaction/dependency policy, including shared allocation protection. Do not sum cached summaries again with underlying events.

Collection calendar dates use Asia/Kolkata by approved amendment and cannot precede job creation; test UI/repository/backend enforcement separately.

Option A preserves the existing ledger as a reference snapshot: no new cutoff, opening amount/transaction, timestamp rewrite or replayed financial baseline. Observation time is not an effective financial date.

Tests include CashBalanceRegressionTest, PartnerWithdrawalsProfitShareTest, BusinessOverviewAttributionTest, CollectionHistoryGroupingTest, PaymentReversalAndCollectionHistoryTest and DriverEarningsConsistencyTest. Read exact assertions and results; names alone are not scenario proof.
