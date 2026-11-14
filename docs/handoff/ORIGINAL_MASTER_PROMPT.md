# AIDHUNT TRAC — MASTER PROFESSIONAL ENGINEERING INVESTIGATION, IMPLEMENTATION, GIT VALIDATION & REGRESSION TESTING PROMPT

## CUSTOMER IDENTITY • GLOBAL PROFILE EDITING • JOB REASSIGNMENT • PARTNER PERMISSIONS • BALANCE SHEET • EXPENSES • INVESTMENTS • EXTERNAL LOANS • BORROWING • SETTLEMENTS • NOTIFICATIONS • OFFLINE SYNCHRONIZATION

---

# YOUR ROLE — ACT AS A COMPLETE SENIOR SOFTWARE ENGINEERING TEAM

Act as an experienced, multidisciplinary software engineering organization responsible for investigating, implementing, validating, and safely delivering the requested enhancements to my existing **AIDHUNT TRAC Kotlin Android application**.

Assume responsibility for the following professional roles:

1. **Senior Product Manager:** Understand the real-world tractor business workflows, distinguish confirmed requirements from unresolved decisions, prevent unnecessary scope changes, and maintain a complete requirements-to-tests traceability matrix.

2. **Principal Android Software Architect:** Investigate the existing architecture, identify the root causes of bugs, evaluate dependencies, and propose minimal, backward-compatible changes.

3. **Senior Kotlin Android Developer:** Implement approved changes using the existing Kotlin, Jetpack Compose, ViewModel, navigation, repository, and application architecture.

4. **Room/SQLite Database Architect:** Protect local customer identities, job relationships, transaction references, schema migrations, and persistent offline records.

5. **Firebase and Backend Architect:** Investigate cloud records, authorization, cross-device synchronization, Firebase Security Rules, and backend integration without unnecessarily modifying working Firebase infrastructure.

6. **Offline-First Synchronization Engineer:** Protect local-first workflows, pending-operation queues, WorkManager, acknowledgment handling, retries, conflict resolution, and multi-device consistency.

7. **Senior Financial Systems Engineer:** Validate customer dues, business cash, expenses, investments, profit distributions, partner withdrawals, borrowings, settlement liabilities, external loans, and repayment calculations.

8. **Senior UI/UX Designer:** Make compact, minimal, rural-user-friendly interface improvements while preserving the application's established visual design.

9. **Localization Engineer:** Ensure consistent English and Tamil wording, date/time formatting, notifications, search, and accessibility.

10. **Senior QA and Test Automation Engineer:** Preserve all existing tests, create new automated tests for changed behavior, and verify integration, synchronization, financial correctness, and regressions.

11. **Git and GitHub CI/CD Engineer:** Protect the active repository, inspect working changes, verify Git diffs, run actual project test commands, check GitHub Actions, and maintain a traceable implementation history.

12. **Security and Code Review Engineer:** Verify role authorization, workspace isolation, transaction integrity, auditability, and prevention of unauthorized or duplicate changes.

13. **Senior Engineering Manager and Release Manager:** Coordinate phased implementation, enforce approval gates, review test evidence, and prepare controlled APK release recommendations.

Do not act merely as a code generator.

**Act as an accountable engineering team that investigates, proves, implements, tests, reviews, and reports.**

---

# 1. PROJECT CONTEXT

AIDHUNT TRAC is an existing Kotlin Android application for managing tractor-related agricultural business activities.

The application includes:

- Business workspaces.
- Original workspace owner.
- Financial partners.
- Operators.
- New Entry.
- Old Entry / historical job recording.
- Customer creation.
- Customer profile editing.
- Individual job editing.
- Customer Dues.
- Payment Collection.
- Collection History.
- Customer transaction history.
- Job-level payment records.
- Tractor and operator attribution.
- Expenses.
- Business Paid and I Paid behavior.
- Partner investment.
- Investment recovery.
- Partner profit entitlement.
- Partner withdrawals.
- Balance Sheet and reports.
- Offline Room/SQLite persistence.
- Firebase synchronization.
- Multiple devices accessing one business workspace.
- English and Tamil application experience.

The application is already in active use and has undergone previous development and bug-fixing work.

## 1.1 Current repository

I am continuing work in the **current collaborative GitHub repository already connected to this ChatGPT Work conversation**.

Use that current repository only.

Do not switch to an older AIDHUNT TRAC repository.

Do not create a replacement application.

Do not rebuild the entire project.

## 1.2 Important: Work is already in progress

Another ChatGPT Work implementation task concerning customer editing, customer identity, individual-job reassignment, and edit synchronization may already be underway.

Before starting:

1. Inspect the current repository state.
2. Identify the current branch and latest commit.
3. Identify uncommitted changes.
4. Identify changes already made for the customer-editing task.
5. Identify ongoing or partially completed feature work.
6. Determine whether another active Work process could be modifying the same branch or files.
7. Do not overwrite, reset, duplicate, or conflict with that existing implementation.

**Do not run a second concurrent implementation against the same mutable working tree.**

If the active task is still modifying the repository, complete a read-only assessment and report the safe integration options rather than starting conflicting edits.

Features that are already correctly implemented should be verified and retained, not implemented again.

## 1.3 Previously fixed functionality

A previously reported standalone expense synchronization problem has already been corrected.

Do not reopen or rewrite that successful expense synchronization path without evidence of a necessary dependency or a regression.

Existing functional behavior should be considered protected until the investigation proves a change is required.

---

# 2. NON-NEGOTIABLE ENGINEERING SAFETY RULES

## 2.1 Preserve working functionality

The current application already supports important functioning behavior, including:

- New job creation.
- Historical job creation by authorized users.
- Customer creation.
- Job recording.
- Customer dues.
- Collections.
- Payment attribution.
- Expenses.
- I Paid.
- Business Paid.
- Investment.
- Partner accounting.
- Offline entry creation.
- Local persistence.
- Offline-to-online synchronization for successful operations.
- Online-to-local synchronization.
- Cross-device sharing of confirmed records.
- Financial reporting.

Do not disturb these systems unnecessarily.

## 2.2 Protect production Firebase infrastructure

Do not unnecessarily modify:

- Firebase project configuration.
- Firebase Authentication.
- Firebase Security Rules.
- Cloud Functions.
- Cloudflare Workers.
- Firestore collections.
- Firestore document identifiers.
- Existing financial records.
- Existing cloud indexes.
- Existing synchronization endpoints.
- Existing authorization logic.

Read-only investigation is permitted.

If a backend modification is genuinely essential:

1. Demonstrate the root cause.
2. Explain why a local-only fix is insufficient.
3. Identify the exact minimal proposed change.
4. Explain backward compatibility.
5. Identify affected users and data.
6. Provide tests and a rollback plan.
7. **Request my explicit approval before changing protected production infrastructure.**

Do not perform destructive migrations.

Do not edit live financial records as a testing shortcut.

## 2.3 Protect offline-first architecture

Preserve existing:

- Room/SQLite architecture.
- WorkManager configuration.
- Offline outbox.
- Pending-operation tracking.
- Sync acknowledgments.
- Retry behavior.
- Local transaction IDs.
- Firestore synchronization protocols.

Do not replace the offline architecture with a different framework.

If the current protocol cannot support an approved operation safely, document the limitation and propose the smallest compatible extension.

## 2.4 Financial rules must not be invented

Never guess the meaning of:

- Business cash.
- Partner investment.
- Partner expense.
- Profit allocation.
- Available withdrawal entitlement.
- Borrowing from business.
- Borrowing from another partner.
- Repayment through business.
- Repayment directly to partner.
- External loan.
- Loan principal.
- Loan interest.
- Negative partner borrowing balance.

If implementation requires an unresolved financial decision, stop that affected change, show a numerical example, and request approval.

## 2.5 No unnecessary redesign

Retain:

- Existing screen structure.
- Dark-green visual identity.
- Compact cards.
- Existing navigation.
- Familiar button positioning.
- Existing form conventions.
- Existing user terminology unless specifically changed.
- Simple flows appropriate for rural business users.

Do not introduce unrelated modules, excessive settings, or complicated accounting terminology.

---

# 3. MANDATORY GIT BASELINE, VERSION CONTROL AND WORKTREE PROTECTION

Before editing code, investigate the Git repository.

## 3.1 Record the baseline

Run the appropriate read-only commands, where available:

- `git status --short`
- `git branch --show-current`
- `git rev-parse HEAD`
- `git log -1 --oneline`
- `git diff --stat`
- `git diff --name-status`
- `git diff --cached --stat`

Also inspect whether the connected repository has relevant open or in-progress work that is visible to you.

Record:

- Repository identity.
- Working branch.
- Base commit hash.
- Dirty/clean working tree status.
- Existing uncommitted files.
- Existing staged files.
- Relevant previous commits.
- Relevant GitHub Actions workflows.
- Existing test framework and Gradle modules.

## 3.2 Never destroy user work

Do not run destructive operations such as:

- `git reset --hard`
- `git clean -fd`
- Force push.
- Deleting branches.
- Rewriting published history.
- Discarding uncommitted files.
- Overwriting active work.
- Reverting unrelated changes without authorization.

Do not switch branches if doing so would interfere with an active Work session.

If a working branch is needed, propose the branch strategy and obtain authorization where required.

## 3.3 Git checkpoints

For every approved implementation phase:

1. Record the starting Git state.
2. Modify only necessary files.
3. Run relevant tests.
4. Inspect `git diff`.
5. Run `git diff --check`.
6. Review unexpected file modifications.
7. Report phase completion.
8. Create a phase-specific commit only when permitted by the agreed workflow.

Do not commit temporary logs, credentials, debug data, private configuration, or generated secrets.

## 3.4 GitHub Actions

Inspect existing `.github/workflows` definitions.

Determine whether the repository already runs:

- Android compilation.
- Unit tests.
- Static checks.
- Lint.
- Instrumentation tests.
- APK build checks.
- Other project-specific verification.

Use existing workflows where appropriate.

Do not replace or weaken existing workflows merely to make CI green.

If automated test coverage is missing, propose a minimal GitHub Actions improvement, but request approval before modifying protected CI configuration.

**Every applicable GitHub Actions check should pass before release readiness is claimed.**

If a GitHub Actions run cannot be triggered or inspected, report that limitation explicitly.

---

# 4. PHASE ZERO — COMPLETE READ-ONLY ARCHITECTURE AND BUSINESS-LOGIC INVESTIGATION

**NO IMPLEMENTATION DURING PHASE ZERO.**

This phase must establish the actual state of the application rather than assume the implementation matches previous conversations.

## 4.1 Inspect application architecture

Identify:

- Android modules.
- Kotlin source structure.
- Jetpack Compose screens.
- ViewModels.
- Repositories.
- Domain/use-case layer.
- Room entities and DAOs.
- Firestore synchronization adapters.
- WorkManager jobs.
- Offline queues.
- Navigation.
- Current design system.
- Localization resources.
- Test modules.

## 4.2 Inspect current customer-editing implementation

The customer-editing work may already be partly implemented.

Identify whether the following are already fixed, partially fixed, or missing:

- Customer ID consistency.
- Capitalization mismatches.
- Global profile name editing.
- Phone editing.
- Individual-job customer reassignment.
- Job creation against existing customer IDs.
- Cross-device edit synchronization.
- Need Review rejection handling.
- Room/Firestore identity relationships.
- Search result ordering.

Do not duplicate completed work.

## 4.3 Inspect job role permissions

Determine:

- Original owner identity.
- Financial partner identity.
- Operator identity.
- Who may create current jobs.
- Who may create historical jobs.
- Who may edit current jobs.
- Who may edit historical jobs.
- Existing same-day editing restrictions.
- Whether permission checks are client-only or backend-enforced.

## 4.4 Inspect financial logic

Trace:

- Job amount.
- Initial payment.
- Later collections.
- Job dues.
- Customer dues.
- Daily/monthly/yearly Balance Sheet.
- Sales field.
- Expense field.
- Net Margin field.
- I Paid.
- Business Paid.
- Investments.
- Partner capital recovery.
- Partner profit entitlement.
- Withdrawals.
- Existing available cash calculation.
- Partner settlement behavior, if implemented.
- Negative balance behavior.

Do not assume cash available equals profit entitlement.

## 4.5 Inspect existing expense and loan support

Determine whether TRAC already has:

- Expense category definitions.
- Custom categories.
- Partner-funded expense attribution.
- Split Between.
- Loan-funded expense classification.
- External loan records.
- Loan repayment records.
- Expense category charts.
- Loan repayment charts.

## 4.6 Inspect notifications

Determine:

- Whether activity notifications exist.
- Whether in-app notifications exist.
- Whether push notifications exist.
- Whether original owner identity is stable.
- Whether recipient language is available.
- Whether notifications are connected to successful sync events.
- Whether approval actions are supported.
- Whether duplicate delivery is prevented.

## 4.7 Required Phase Zero output

Produce a professional investigation report with:

1. Git baseline and current work status.
2. Existing repository architecture.
3. Current feature status matrix.
4. Root causes of confirmed bugs.
5. Exact impacted files and functions.
6. Existing financial calculations.
7. Current Firestore and Room data relationships.
8. Permissions matrix.
9. Proposed minimal modifications.
10. Backend changes, if any.
11. New tests needed.
12. Existing baseline test results.
13. Risk assessment.
14. Phase dependencies.
15. Unresolved product decisions.
16. Estimated regression areas.
17. Recommended implementation sequence.

**STOP after Phase Zero and ask for explicit approval.**

Do not automatically proceed to code edits.

---

# 5. PHASE ONE — CUSTOMER EDITING BUGS AND CROSS-DEVICE CONSISTENCY

The following real-world problems were observed in previously tested APKs.

## 5.1 Scenario A — Edit fails on one phone

Steps:

1. Open AIDHUNT TRAC.
2. Navigate to Reports.
3. Open Customer Dues.
4. Select a customer.
5. Open details or transaction history.
6. Edit the customer or an existing job.
7. Save.

Reported behavior:

- The edit sometimes fails on one Android device.
- Another device may complete the same operation.
- A Need Review synchronization warning may appear.
- Some New Entry or Old Entry records are not updated consistently.

Investigate:

- Installed APK version.
- Application build identity.
- Local schema version.
- Authenticated user.
- Workspace membership.
- Authorization.
- Customer ID.
- Job ID.
- Edit operation payload.
- Original entity version.
- Room cached record.
- Firestore document.
- Offline operation state.
- Outbox dependency.
- Sync acknowledgment.
- Need Review rejection reason.
- Conflict resolution.
- Stale ViewModel or UI state.

Do not assume this is merely an internet problem.

Find the root cause using code and reproducible evidence.

## 5.2 Scenario B — Same customer shows different names

Example:

Original name: `saga jothi`

Updated name: `Saga Jothi`

Reported inconsistency:

- Customer Dues displays the original capitalization.
- New Entry may display updated capitalization.
- Recent Jobs may display another value.
- Editing dialogs may display the new name.

Trace name rendering across:

- Customer Dues.
- Customer Profile.
- Job Details.
- Recent Job Entries.
- Transaction History.
- Collection History.
- New Entry.
- Old Entry.
- Customer Search.
- Reports.
- Newly generated PDF statements.
- Other customer-facing views.

Determine whether screens use:

- Stable customer ID.
- Canonical customer record.
- Job-level customer-name snapshot.
- Payment-level customer-name snapshot.
- Room relation.
- Firestore customer document.
- Stale UI cache.
- Case-sensitive lookup.

Correct the underlying identity/data-flow problem, not only the appearance.

## 5.3 Acceptance criteria

After a successful global customer name update:

- All current customer-facing views resolve the same canonical display name.
- The stable customer ID remains unchanged.
- No second customer is created.
- Existing jobs remain linked.
- Existing collections remain linked.
- Customer dues remain correct.
- The edit synchronizes correctly across authorized devices.
- Offline editing follows existing supported queue behavior.
- Need Review failures have actionable, accurate causes.

---

# 6. PHASE TWO — STABLE CUSTOMER IDENTITY

## 6.1 Identity principles

Separate:

1. Stable workspace-scoped Customer ID.
2. Editable Customer Display Name.
3. Normalized Search Name.

Capitalization must not define customer identity.

Examples:

- `saga jothi`
- `Saga Jothi`
- `SAGA JOTHI`

These may represent the same customer when their stable customer ID is the same.

Do not automatically merge unrelated customers with matching names.

## 6.2 Preserve relationships

Investigate and preserve:

- Workspace ID.
- Customer ID.
- Job/customer relationship.
- Payment/job relationship.
- Collection allocations.
- Customer dues.
- Customer history.
- Partner attribution.

Use stable identifiers for financial relationships.

Avoid relying on customer-name strings as database relationship keys.

## 6.3 Duplicate detection

When selecting or creating customers:

- Show suitable existing matches.
- Prioritize prefix matches.
- Include distinguishing phone or village information where available.
- Permit genuinely different customers with identical names.
- Do not silently merge records.
- Do not create duplicates after the user explicitly selects an existing customer.

## 6.4 Legacy duplicates

If duplicate historical customer identities exist:

- Detect and report them.
- Identify affected jobs and dues.
- Do not automatically merge production records.
- Propose a separate reviewed repair workflow if necessary.

---

# 7. PHASE THREE — GLOBAL CUSTOMER PROFILE EDITING

## 7.1 Existing UI

The Customer Details screen currently has an edit interaction near the customer's name and phone information.

That interaction may currently open an Edit Phone Number dialog.

Extend the existing interaction to edit:

- Customer Name.
- Mobile Number.

Reuse the current dialog styling.

Do not introduce a large replacement screen.

## 7.2 Global edit behavior

Example:

Name before: `saga jothi`

Name after: `Saga Jothi`

Expected:

- Same Customer ID.
- Same Workspace ID.
- Same linked jobs.
- Same linked payments.
- Same dues.
- Same partner attribution.
- Updated name displayed consistently.
- No new financial transaction.
- No job reassignment.

## 7.3 Phone editing

Preserve existing phone validation rules.

Test:

- Valid number.
- Invalid characters.
- Invalid length.
- Unchanged number.
- Optional blank number, if supported.
- Repeated saves.
- Offline update.
- Cross-device synchronization.
- Concurrent edits.

Do not overwrite a newer value with stale data silently.

## 7.4 Historical records

Preserve immutable audit evidence.

If the app stores historical name snapshots for evidence, do not destructively rewrite them merely to fix current profile display.

Use canonical customer data for appropriate current-name presentation.

---

# 8. PHASE FOUR — INDIVIDUAL JOB CUSTOMER REASSIGNMENT

This is separate from a global customer profile edit.

## 8.1 Global edit versus job reassignment

**Global Customer Edit:**

Changes the name or phone number of the same customer.

**Individual Job Reassignment:**

Changes the customer ID associated with one selected job.

Do not confuse these operations.

## 8.2 Reassign job to existing customer

Customer A has:

- Job 1: ₹800.
- Job 2: ₹1,000.
- Job 3: ₹500.

Move Job 2 to Customer B.

Expected:

- Job 2 belongs to Customer B.
- Jobs 1 and 3 remain with Customer A.
- Original Job 2 ID remains unchanged.
- Linked job payments remain valid.
- Original creator remains unchanged.
- Original operator and partner attribution remain unchanged.
- Customer-level dues recalculate.
- Workspace-wide dues remain unchanged by reassignment alone.

## 8.3 Create new customer from job edit

Allow:

1. Search existing customers.
2. Select a matching customer.
3. Or explicitly choose Create New Customer.
4. Create the new customer safely.
5. Reassign only the selected job.
6. Preserve its original Job ID.
7. Preserve all linked financial records.

Do not silently create customers while the user types.

## 8.4 Same name, different people

Two people named Raman may be separate customers.

Do not merge them.

Require correct customer selection using stable IDs.

## 8.5 Financial reconciliation

Preserve:

- Job amount.
- Initial payment.
- Additional payments.
- Payment IDs.
- Collection amounts.
- Outstanding job balance.
- Collected By.
- Recorded By.
- Work date.
- Tractor.
- Operator.
- Partner attribution.
- Workspace.
- Audit history.

If a payment spans multiple jobs or customers, inspect the allocation model.

Do not blindly transfer or duplicate shared payment transactions.

If a complex historical reassignment cannot be completed safely using the current architecture, stop and report the exact limitation.

## 8.6 Acceptance example

Before reassignment:

Customer A dues: ₹1,800.  
Customer B dues: ₹500.  
Workspace dues: ₹2,300.

Move a ₹1,000-due job from A to B.

After reassignment:

Customer A dues: ₹800.  
Customer B dues: ₹1,500.  
Workspace dues: ₹2,300.

No duplicate jobs or payments.

---

# 9. PHASE FIVE — OWNER, PARTNER AND OPERATOR PERMISSIONS

## 9.1 Original owner identity

The person who originally created the workspace remains the original business owner.

If that person becomes a financial partner, they retain original-owner privileges.

Example:

- Muniyappan: Original owner and financial partner.
- Perumal: Financial partner.
- Sriram: Financial partner.
- Murugan: Operator.

Do not erase the original-owner identity when updating partnership roles.

## 9.2 Historical job creation

Only the original owner can create a new historical job entry.

Other partners cannot create new historical entries.

Operators cannot create historical entries.

## 9.3 Editing any existing job

Both the original owner and authorized partners may edit:

- Their own current job entries.
- Other partners' current job entries.
- Their own old job entries.
- Other partners' old job entries.
- Existing historical entries created by the original owner.
- Eligible jobs created months earlier.

The previous same-day-only partner editing restriction must be removed for authorized partner job edits.

Examples:

- Muniyappan creates a job today; Perumal edits it next month.
- Perumal creates a job; Sriram edits it next month.
- Muniyappan creates a historical job; Perumal edits it later.

## 9.4 Operator permissions

Do not grant operators unrestricted job editing.

Operators cannot edit jobs created by the original owner or partners merely because partner permissions are expanded.

Preserve their existing entry and self-edit permissions unless separately approved.

## 9.5 Security enforcement

Verify role authorization in:

- UI.
- ViewModel.
- Repository.
- Offline operation validation.
- Cloud/backend authorization.

Do not rely only on hiding buttons.

Prevent a partner from bypassing historical-entry creation restrictions through an alternate API or unsupported workflow.

If changing an existing job date could effectively create an unauthorized historical entry, identify and handle that case explicitly.

---

# 10. PHASE SIX — JOB CREATION TIME, EDITOR DETAILS AND AUDIT HISTORY

## 10.1 Current job creation

Enhance current job cards or details to show:

- Entry by.
- Created date.
- Created time.
- Entry for Operator.
- Last edited by, if edited.
- Last edited date and time.

Use Indian Standard Time for display.

Readable example:

Entry by: Muniyappan  
Created: 07 Nov 2026, 10:30 AM IST  
Entry for Operator: Murugan  
Last edited by: Perumal  
Last edited: 08 Nov 2026, 02:15 PM IST

Use valid time notation.

Do not combine a 24-hour clock value with AM/PM.

Store exact timestamps separately from display formatting.

## 10.2 Historical job

The historical **work date** should be date-only.

Do not invent an unknown historical work time.

Preserve separately:

- Historical job date.
- Actual TRAC record creation timestamp.
- Actual modification timestamp.

Example:

Job Date: 10 Oct 2026.  
Entry by: Muniyappan.  
Recorded in TRAC: 07 Nov 2026.  
Last edited by: Perumal.  
Last edited: 09 Nov 2026, 11:20 AM IST.

Keep the main card compact.

## 10.3 Audit behavior

Preserve:

- Original creator.
- Creation timestamp.
- Last editor.
- Last modification timestamp.
- Relevant edit history.

For records lacking reliable historic timestamps, do not fabricate exact times.

## 10.4 Financial edits

When changing a job amount or payment-related field:

- Preserve real collection history.
- Recalculate dues correctly.
- Do not create duplicate receipts.
- Prevent invalid negative dues or other inconsistent states.
- Preserve actor attribution.
- Record the edit separately from any new collection.

---

# 11. PHASE SEVEN — JOB TERMINOLOGY CORRECTION

Change only the intended job-entry labels.

Current label:

**Entry for Partner**

Required label:

**Entry for Operator**

Creator label:

**Entry by**

Example:

Entry by: Muniyappan.  
Entry for Operator: Perumal.

Perumal may be both a financial partner and the actual tractor operator for this job.

His financial role remains Partner.

Do not globally rename Partner to Operator.

Keep Partner in:

- Partner Management.
- Investments.
- Profit sharing.
- Withdrawals.
- Borrowing.
- Settlement.
- Repayment.

**Critical architecture check:** If the current `Entry for Partner` field is also used to calculate financial ownership or partner profit allocation, do not simply reinterpret that underlying field as an operator ID.

Investigate the current data model.

Preserve financial attribution and, if necessary, propose a separate operator association rather than silently changing financial meaning.

---

# 12. PHASE EIGHT — CUSTOMER SEARCH AND ALPHABETICAL PRIORITY

The current search behavior does not reliably prioritize names from the first typed letters.

Required:

- Case-insensitive matching.
- Prefix matches first.
- Alphabetical ordering.
- Immediate filtering.
- Stable customer ID selection.
- Tamil Unicode support.
- Optional substring matches after prefix matches.

Examples:

Typing `R`:

- Raja.
- Rajesh.
- Ram.
- Ravi.

Typing `Pa`:

- Palani.
- Palaniappan.

Typing `saga` must find `Saga Jothi`.

Apply consistently to:

- New Entry.
- Old Entry.
- Customer Dues.
- Customer Profile.
- Customer reassignment.
- Collection screens.
- Relevant customer selectors.

Do not automatically merge customers with similar names.

Do not create duplicates solely because of capitalization.

---

# 13. PHASE NINE — BALANCE SHEET CORRECTION

## 13.1 Preserve the existing three main fields

The current Balance Sheet has three summary fields.

Previously:

1. Sales.
2. Expenses.
3. Net Margin / Net Profit.

Required user-friendly labels:

1. **Amount Received**
2. **Expenses**
3. **Net Balance**

The application performs tractor work and services rather than retail product sales.

Therefore do not use Sales as the primary label.

## 13.2 Existing simplified formula

The intended simplified calculation is:

**Net Balance = Amount Received − Expenses**

Example:

Amount Received: ₹7,000.  
Expenses: ₹2,000.  
Net Balance: ₹5,000.

Do not reverse the subtraction.

Do not call this number accounting Net Profit unless the actual accounting definition has been implemented.

## 13.3 Late customer collections

Reported bug:

A job recorded with pending dues may not contribute correctly to the Balance Sheet when the money is collected on a later date.

Correct behavior:

The payment belongs to the date it was actually collected.

Example:

07 Nov:
- Job amount: ₹5,000.
- Initial receipt: ₹500.
- Due: ₹4,500.

08 Nov:
- Customer pays ₹4,500.

Expected:

07 Nov Amount Received: ₹500.  
08 Nov Amount Received: ₹4,500.

The original job value must not be counted as money received twice.

## 13.4 Another example

07 Nov:
- Job amount: ₹7,000.
- Initial receipt: ₹0.

08 Nov:
- Customer pays ₹7,000.

Expected:

07 Nov Amount Received: ₹0.  
08 Nov Amount Received: ₹7,000.

## 13.5 Reporting periods

Validate:

- Daily.
- Monthly.
- Yearly.
- Different-month collections.
- Different-year collections.
- IST midnight boundaries.
- Historical job dates.
- Actual receipt timestamps.
- Corrections to previous payments.

Do not classify a newly created historical job as cash received on its historical work date unless there is an actual payment transaction supporting that date.

## 13.6 Expense-definition safety

Inspect the existing Expenses field.

Determine whether it currently includes:

- Business Paid.
- I Paid.
- Partner-funded expenses.
- Other sources.

Do not silently redefine the expense field while fixing late collections.

A business expense and a business cash outflow are not always identical.

If existing reports treat them differently, preserve the approved business meaning and report any inconsistency.

## 13.7 Financing exclusions

Do not classify these as Amount Received:

- Partner investment.
- Business borrowing.
- Partner borrowing.
- External loan proceeds.
- Settlement repayment.
- External loan principal repayment.

These are financing movements, not customer work payments.

---

# 14. PHASE TEN — EXPENSE CATEGORY ENHANCEMENTS

## 14.1 Preserve the expense form

Reuse the existing form and controls.

Maintain:

- Expense date.
- Description.
- Category.
- Amount.
- Paid By.
- Split Between.
- Save.
- Existing business versus individual payment treatment.

## 14.2 Expand category selection

Preserve existing useful categories, and support categories such as:

- General.
- Seeds.
- Pesticides.
- Labour.
- Furniture.
- Tyre / Tire.
- Petrol.
- Diesel.
- Repairs.
- Maintenance.
- Rental.
- Other already-supported categories.

## 14.3 Add custom expense category

Replace the final generic `Others` selection action with:

**+ Add Category**

Workflow:

1. Open expense category dropdown.
2. Select + Add Category.
3. Enter custom name.
4. Validate.
5. Save.
6. Automatically select the new category.

Examples:

- Engine Oil.
- Tractor Tyre.
- Welding.
- Furniture.
- Machine Rental.

The newly added category must persist and synchronize appropriately.

Avoid duplicate categories created by letter capitalization or extra spaces.

## 14.4 Preserve historical Other records

Do not delete or invalidate existing expenses already categorized as `Other` or `Others`.

Ensure previous records remain readable and properly categorized in reports.

## 14.5 Preserve negative expense/refund behavior

If the current application supports negative expense amounts for corrections or refunds, do not remove that capability without approval.

Investigate how such adjustments should appear in category analytics.

Avoid invalid negative pie-chart slices or misleading totals.

---

# 15. PHASE ELEVEN — EXPENSE FUNDING SOURCE AND INVESTMENTS

The expense type and the source of funds are separate concepts.

Example:

Expense type: Diesel.  
Amount: ₹2,000.  
Funding source: Partner Paid.

The category remains Diesel.

## 15.1 Business Paid

Expense paid using business funds.

Preserve the current accounting and synchronization behavior.

## 15.2 I Paid / Partner Paid / Investment

Expense paid personally by a partner for business purposes.

Example:

Muniyappan pays ₹3,000 personally for Diesel.

Expected:

- Diesel expense: ₹3,000.
- Paid By: Muniyappan.
- Correct partner investment or reimbursement attribution.
- Business-held cash must not also be reduced as if the same ₹3,000 came from the business.
- No duplicate expense.
- No duplicate investment.

Inspect the existing treatment before changing it.

## 15.3 Shared expense

Example:

Muniyappan pays ₹1,000 personally.

Expense shared equally between Muniyappan and Perumal.

Expected financial interpretation, subject to existing verified rules:

- Total business expense: ₹1,000.
- Muniyappan's allocation: ₹500.
- Perumal's allocation: ₹500.
- Relevant settlement obligation recorded.
- No duplicated expense or capital contribution.

## 15.4 External-loan-funded expense

An expense may be funded from a loan obtained from an outside lender.

Examples:

- Vehicle repair financed by a loan.
- Equipment purchased with borrowed funds.
- Rental cost paid using external borrowing.

Expected:

- Expense category remains correct.
- Loan funding source is recorded.
- External liability is tracked.
- The same expenditure is recorded only once.
- Loan proceeds are not customer revenue.
- Principal repayment is not a duplicate expense.

## 15.5 Existing Business Paid negative-balance behavior

Previously reported behavior:

Business Paid expenses may be allowed even when business cash is zero, creating a negative business balance.

The user previously instructed that this flexibility must remain for now.

**Do not automatically block Business Paid expenses merely because business cash is insufficient.**

This is a deferred change.

However, **partner withdrawals must still respect available business funds**.

Do not conflate these rules.

---

# 16. PHASE TWELVE — EXTERNAL LOAN MANAGEMENT

Introduce or extend a simple external-loan management feature.

This must be distinct from partner borrowing and partner settlement.

## 16.1 External loan types

Support relevant types such as:

- Vehicle Loan.
- Tractor Loan.
- Equipment Loan.
- Business Loan.
- Rental Loan.
- Bank Loan.
- Finance Company Loan.
- Other/custom loan category.

Preserve existing supported loan types if already present.

## 16.2 Add Loan

Suggested fields:

- Loan name.
- Loan type.
- Lender.
- Principal amount.
- Loan date.
- Amount received, if different from principal.
- Where the funds were received.
- Whether funds were paid directly to a supplier.
- Interest or charges, if applicable.
- Optional due date.
- Notes.

Do not require unnecessary banking complexity for informal loans.

## 16.3 Loan summary

Display:

- Original principal.
- Principal repaid.
- Outstanding principal.
- Interest/fees, if tracked.
- Total repayments.
- Lender.
- Loan type.
- Current status.

## 16.4 Repay Loan

Allow the user to:

1. Select the loan.
2. Enter repayment amount.
3. Choose date.
4. Specify payment source.
5. Record principal and interest portions when applicable.
6. Save repayment.
7. Update outstanding loan balance.
8. View repayment in history.

Do not invent unknown interest or amortization values.

## 16.5 Example

Loan principal: ₹50,000.

Principal repaid: ₹10,000.

Outstanding principal: ₹40,000.

The ₹50,000 does not count as customer Amount Received.

The ₹10,000 reduces loan liability.

If paid from business cash, it also reduces business cash.

Do not count the same ₹10,000 as a second purchase or operating expense.

Interest, if paid, should be treated separately under the approved expense rules.

## 16.6 Loan record integrity

Prevent:

- Duplicate loan creation during sync retry.
- Duplicate repayments.
- Negative outstanding principal from an invalid repayment.
- Repayment attached to the wrong lender.
- Confusion with internal partner borrowing.
- Incorrect cash movement attribution.

Do not alter the existing partner investment engine to represent bank borrowing.

---

# 17. PHASE THIRTEEN — EXPENSE AND LOAN ANALYTICS

Create compact, understandable charts within the existing reporting experience.

## 17.1 Expense category pie chart

Example:

Total expenses: ₹10,000.

- Diesel: ₹4,000.
- Labour: ₹3,000.
- Tyre: ₹2,000.
- Furniture: ₹1,000.

Show distribution by category with readable amounts and percentages.

## 17.2 Funding-source breakdown

Group relevant expenditure by source:

- Business Paid.
- Partner Paid / Investment.
- External Loan Funded.

Example:

Total expense: ₹15,000.

- Business Paid: ₹7,000.
- Partner Paid: ₹5,000.
- Loan Funded: ₹3,000.

The total of funding allocations must reconcile to the actual recorded expenses.

Do not double-count partially funded expenses.

## 17.3 Loan repayment chart

Show repayments by external-loan type or individual loan.

Example:

Total repayment: ₹10,000.

- Vehicle Loan: ₹6,000.
- Equipment Loan: ₹3,000.
- Rental Loan: ₹1,000.

Clearly distinguish:

- Loan Repayments Made.
- Loan Principal Outstanding.
- Loan Funding.

These must not be combined under an ambiguous chart title.

## 17.4 Reporting filters

Reuse existing:

- Daily.
- Monthly.
- Yearly.

Add only necessary controls.

## 17.5 UI requirements

- Compact cards.
- Readable pie/doughnut charts.
- Clearly labeled legend.
- Indian rupee formatting.
- English/Tamil support.
- Useful no-data states.
- Good accessibility.
- Low device resource usage.
- No excessive animation.
- No unnecessary heavy charting dependency.

---

# 18. PHASE FOURTEEN — PARTNER WITHDRAWALS AND BORROWING

## 18.1 Preserve existing calculations

The application already has investment and profit-sharing rules.

Do not replace these rules without proving the exact impact.

Investigate separately:

- Partner investment.
- Investment recovery.
- Profit entitlement.
- Amount already withdrawn.
- Available entitlement.
- Business cash.
- Partner receivables.
- Partner debts.

## 18.2 Business cash withdrawal limit

Example:

Business cash available: ₹5,000.

Requested withdrawal: ₹10,000.

Expected:

**Block the withdrawal.**

A partner must not receive money that the business does not actually have available.

## 18.3 Excess withdrawal example

Three partners:

- Muniyappan: ₹5,000 entitlement.
- Perumal: ₹5,000 entitlement.
- Sriram: ₹5,000 entitlement.

Total entitlement: ₹15,000.

Perumal requests ₹10,000.

Own entitlement: ₹5,000.

Extra needed: ₹5,000.

That additional amount is borrowing, not new profit.

## 18.4 Borrow from Business — no partner approval

**Confirmed requirement: Borrow from Business must not require a partner approval popup.**

The borrowing may proceed directly when authorized and financially valid.

The application must:

- Check available business cash.
- Determine the applicable business borrowing limit.
- Record the actual withdrawal.
- Record outstanding debt to the business.
- Show the borrower's negative borrowing position.
- Record the borrowing in Settlement History.
- Support repayment into business cash.
- Prevent duplicate financial transactions.

**Important unresolved accounting distinction:** Borrowing from Business must not automatically create personal debts to other named partners unless the current approved business model explicitly allocates those funds to them.

If business borrowing and partner entitlements overlap, propose a transparent accounting treatment instead of counting the same cash twice.

## 18.5 Borrow from Partner — approval required

If Perumal borrows ₹5,000 from Muniyappan:

- Perumal requests ₹5,000.
- Muniyappan receives the request.
- Muniyappan approves or rejects.
- The money is not disbursed before approval.
- Business cash is checked again when executing.
- Muniyappan's available entitlement is reduced or reserved appropriately.
- Perumal's debt is recorded.
- Settlement History is updated.

Example:

Perumal owes Muniyappan ₹5,000.

## 18.6 Lender entitlement protection

Example:

Original total business cash: ₹15,000.

Each partner initially has ₹5,000 entitlement.

Perumal withdraws ₹10,000:

- ₹5,000 from own entitlement.
- ₹5,000 borrowed from Muniyappan.

Business cash remaining: ₹5,000.

Muniyappan cannot withdraw the same ₹5,000 already used to fund Perumal's borrowing.

Sriram's entitlement remains separately accounted for.

The loan receivable and available business-held cash must not be treated as the same immediately withdrawable amount.

## 18.7 Split borrowing

If Perumal needs ₹5,000 extra and borrows:

- ₹2,500 from Muniyappan.
- ₹2,500 from Sriram.

Both lenders must approve their respective shares before the complete withdrawal is executed, unless a separately approved partial-execution policy exists.

## 18.8 Negative partner borrowing balance

Example:

Perumal borrows ₹5,000.

Show outstanding borrowing as:

Amount to Repay: ₹5,000.

Internal balance may represent it as:

`−₹5,000`.

After repayment of ₹2,000:

Remaining borrowing: ₹3,000.

After full repayment:

Remaining borrowing: ₹0.

Do not treat settlement as new profit.

Do not grant a second withdrawal merely because an old debt was repaid.

---

# 19. PHASE FIFTEEN — PARTNER BORROWING APPROVAL WORKFLOW

## 19.1 Approval request

When a partner requests borrowing from another partner:

- Show borrower name.
- Show lender name.
- Show amount.
- Show available lender entitlement.
- Show request date/time.
- Show Approve and Reject.

Example:

"Perumal requests ₹5,000 from your available share."

## 19.2 Approval outcome

If approved:

- Revalidate funds.
- Create the authorized withdrawal.
- Create settlement obligation.
- Notify affected parties.
- Record audit event.

If rejected:

- Do not withdraw money.
- Do not create active debt.
- Keep rejection history.

## 19.3 Offline requests

If the borrower is offline:

- Preserve the request using the existing local-first mechanism where supported.
- Synchronize when connectivity returns.
- Notify the lender after synchronization.
- Require explicit approval.
- Never mark pending borrowing as completed.

## 19.4 Concurrency

Prevent:

- Two simultaneous requests consuming one entitlement.
- Double approval processing.
- Multiple withdrawals from a repeated sync event.
- Borrowing beyond actual business cash.
- Approvals being applied against outdated available amounts.

Use an appropriate atomic or idempotent approach compatible with current infrastructure.

---

# 20. PHASE SIXTEEN — PARTNER SETTLEMENT SYSTEM

## 20.1 Settlement sources

Settlement should support obligations arising from:

- Borrow from Partner.
- Approved multi-partner borrowing.
- Borrow from Business.
- Shared expenses.
- Partner-funded business obligations.
- Other already-supported partner obligations.

Do not mix external bank/vehicle loans with internal partner settlement.

## 20.2 Settlement overview

Display:

- Borrower.
- Lender or creditor.
- Original amount.
- Amount repaid.
- Outstanding amount.
- Reason.
- Transaction date.
- Status.

## 20.3 Settlement History

Record:

- Request.
- Approval.
- Rejection.
- Borrowed withdrawal.
- Shared-expense obligation.
- Partial repayment.
- Full repayment.
- Reversal/correction.
- Settlement completion.

History must remain visible after repayment.

## 20.4 Repayment directly to partner

Example:

Perumal owes Muniyappan ₹5,000.

Perumal pays ₹2,000 personally to Muniyappan.

Expected:

- Perumal's debt decreases to ₹3,000.
- Muniyappan's receivable decreases to ₹3,000.
- Business cash does not increase.
- Payment history is recorded.
- No duplicate business withdrawal is created.

## 20.5 Repayment into business for partner

Example:

Perumal owes Muniyappan ₹5,000.

Perumal pays ₹2,000 into business cash to settle part of that obligation.

Expected:

- Debt becomes ₹3,000.
- Business cash increases by ₹2,000.
- Appropriate Muniyappan entitlement is restored according to verified rules.
- Repayment is linked to the original borrowing.
- No new profit is created.

## 20.6 Repayment to the business

If Perumal borrowed directly from the business:

- Repayment returns money into business cash.
- Outstanding business borrowing decreases.
- No artificial partner-to-partner loan is created.
- Settlement History reflects repayment.

## 20.7 Shared-expense example

Muniyappan pays ₹1,000 for a business expense split equally with Perumal.

Expected, according to verified accounting rules:

- One ₹1,000 expense.
- Correct funding/investment attribution.
- One ₹500 obligation from Perumal to Muniyappan.
- No duplicated expense.
- No duplicated investment credit.

## 20.8 Settlement states

Use suitable states:

- Pending.
- Partially Repaid.
- Settled.
- Rejected.
- Cancelled/Reversed.

Use a stable original obligation ID.

Partial repayments must reduce the same underlying obligation.

## 20.9 New-page UX

Recommend a compact Partner Settlement page with:

1. Pending Settlements.
2. Borrowing Requests.
3. Settlement History.

A selected settlement should open details and offer Record Repayment.

Do not require users to complete an excessively long financial workflow.

---

# 21. PHASE SEVENTEEN — ORIGINAL OWNER AND PARTNER NOTIFICATIONS

## 21.1 Original owner activity monitoring

The original workspace owner should be notified when other partners perform important actions.

Examples:

- Create New Entry.
- Create or edit relevant jobs.
- Edit Old Entry.
- Delete job.
- Receive customer payment.
- Correct payment.
- Add expense.
- Edit expense.
- Record withdrawal.
- Borrow money.
- Create settlement.
- Record repayment.
- Modify important business records.

Do not send duplicate self-notifications for the original owner's own activity.

## 21.2 Partner-specific notifications

All relevant partners require notification access for:

- Borrowing requests involving them.
- Approval/rejection results.
- Repayments involving them.
- Relevant settlement changes.

Other partners do not automatically receive every general activity notification.

## 21.3 English/Tamil localization

Use the recipient's selected language.

English example:

"Perumal added a new job for Ravi — ₹5,000."

Tamil example:

"பெருமாள் ரவி என்பவருக்கு ₹5,000 மதிப்புள்ள புதிய வேலையைப் பதிவு செய்தார்."

Avoid relying on the sender's language.

## 21.4 Offline notification behavior

Preserve the existing entry synchronization behavior.

Expected:

- Sender online, recipient online: event synchronizes and notification is available promptly.
- Sender offline: pending operation stays local.
- Sender reconnects: successful synchronization creates the corresponding cloud-visible event.
- Recipient offline: notification remains available for retrieval upon reconnecting.
- Multiple retries: no duplicate notifications.
- Rejected operation: do not create a misleading successful-action notification.

Use stable event identity or another proven idempotent method.

## 21.5 Notification screen

Preserve existing Home design.

Offer a compact notification icon/panel.

All partners may see their relevant notification panel, but original-owner activity monitoring remains restricted to the original owner.

Do not introduce unnecessary popups for every background event if doing so would interfere with basic entry workflows.

---

# 22. PHASE EIGHTEEN — END-TO-END FINANCIAL INTEGRITY

Before implementing financial changes, establish an explicit transaction model or mapping against the existing model.

The following must remain separate:

| Transaction | Changes customer receipts? | Changes expense? | Changes business cash? | Changes liability/entitlement? |
|---|---|---|---|---|
| Customer payment | Yes | No | Usually increases cash | Reduces customer due |
| Business-paid expense | No | Yes | Usually decreases cash | Depends on existing rules |
| Partner-paid expense | No | Yes | Not necessarily | May create investment/reimbursement |
| Partner investment | No | Not automatically | May increase cash | Increases capital claim |
| Partner withdrawal | No | No | Decreases cash | Reduces entitlement |
| Partner borrowing | No | No | Depends on disbursement | Creates debt |
| Partner settlement repayment | No | No | Depends on repayment destination | Reduces debt |
| External-loan proceeds | No | No | Depends on disbursement | Creates lender liability |
| External-loan principal repayment | No | No | Usually decreases cash | Reduces loan principal |
| Loan interest payment | No | May be expense | Usually decreases cash | Reduces accrued interest/charges |

This table is a conceptual safety guide.

**Verify all actual posting rules against the current TRAC financial model before implementation.**

Do not force this table into the database as a new architecture.

## 22.1 Financial invariants

Enforce:

1. A customer payment is counted once.
2. A job remains linked to one valid customer identity.
3. Reassigning a job does not change workspace total dues by itself.
4. An operating expense is not counted twice.
5. A partner investment is not automatically an operating expense.
6. Loan proceeds are not customer sales.
7. Loan principal repayment is not a duplicate purchase expense.
8. A partner loan creates a corresponding debt.
9. A borrower cannot repay more than the valid outstanding debt without an approved policy.
10. Money cannot be withdrawn twice from one entitlement.
11. Business cash withdrawal cannot exceed available funds.
12. Settlement repayment cannot create new profit.
13. Offline retries cannot duplicate financial postings.
14. User permissions apply consistently across devices.
15. Historical audit records remain traceable.

---

# 23. PHASE NINETEEN — UI/UX AND LOCALIZATION REVIEW

Review affected screens together so they form one coherent experience.

## 23.1 Customer screens

- Global profile editing.
- Stable customer selection.
- Customer search.
- Individual job reassignment.
- Customer Dues consistency.

## 23.2 Job screens

- Entry by.
- Entry for Operator.
- Creation date and time.
- Last editor and edit time.
- Authorized old/new entry editing.

## 23.3 Balance Sheet

Retain only the three primary summary metrics:

- Amount Received.
- Expenses.
- Net Balance.

## 23.4 Expense screen

- Existing expense form.
- Useful categories.
- + Add Category.
- Existing payment/funding attribution.
- Simple analytics entry point.

## 23.5 Loan screen

- Loan List.
- Add Loan.
- Loan Details.
- Repay Loan.
- History.

## 23.6 Withdrawal screen

- Available entitlement.
- Business cash availability.
- Own withdrawal.
- Borrow from Business.
- Borrow from Partner.
- Outstanding borrowed amount.

## 23.7 Settlement screen

- Pending.
- Requests.
- History.
- Repayment.

## 23.8 Notification panel

- Original owner activity.
- Partner approval requests.
- Relevant settlement notifications.
- English/Tamil messages.

No unrelated screen redesign.

No unnecessary navigation restructuring.

Preserve the application identity and rural-user simplicity.

---

# 24. PHASE TWENTY — AUTOMATED TEST STRATEGY

**All currently passing tests must continue to pass.**

Write new tests for every changed financial, identity, permission, and synchronization behavior.

Do not weaken existing tests.

Do not delete failing tests to get a green build.

Do not alter expected results merely to match incorrect implementation.

## 24.1 Baseline tests

Before changes:

1. Discover the actual project test tasks.
2. Run existing unit tests.
3. Run relevant build checks.
4. Record failures.
5. Identify environmental limitations.
6. Distinguish pre-existing failures from newly introduced failures.

Do not claim a clean baseline if tests were not run.

## 24.2 Customer identity tests

- Case-only rename does not create customer.
- Customer ID remains stable.
- Canonical name appears across screens.
- Two same-name customers remain distinct.
- Existing jobs remain associated.
- Existing payments remain associated.
- Offline rename synchronizes.
- Concurrent edits do not silently discard newer information.
- Phone changes do not modify customer names.

## 24.3 Job reassignment tests

- Move one job to existing customer.
- Create new customer and reassign one job.
- Preserve Job ID.
- Preserve payment IDs.
- Preserve original creator.
- Recalculate customer dues.
- Preserve workspace total dues.
- Preserve partner attribution.
- Handle partially paid jobs.
- Handle linked collections.
- Reject unsafe complex reassignment without corrupting data.

## 24.4 Job permission tests

- Original owner creates historical entry.
- Added partner cannot create historical entry.
- Added partner edits historical entry.
- Partner edits another partner's old entry.
- Partner edits original owner's old entry.
- Original owner retains owner permission after becoming a partner.
- Operator cannot edit owner/partner job.
- Unauthorized edit is blocked beyond the UI.

## 24.5 Timestamp tests

- Current job creation time.
- Latest editor name.
- Latest modification time.
- Original creator retention.
- Historical job date-only display.
- Real record creation timestamp retained.
- IST display formatting.
- Midnight boundary.
- Offline timestamp handling.

## 24.6 Balance Sheet tests

Example one:

Job ₹5,000.  
Initial receipt ₹500 on 07 Nov.  
Later receipt ₹4,500 on 08 Nov.

Assert:

07 Nov Amount Received = ₹500.  
08 Nov Amount Received = ₹4,500.

Example two:

Job ₹7,000.  
Initial payment ₹0.  
Full collection next day.

Assert correct reporting date.

Also test:

- Month-end.
- Year-end.
- Multiple collections.
- Partial collections.
- Corrected payments.
- Existing expense categories.
- Reports aggregation.
- No duplicate amounts after synchronization.

## 24.7 Expense tests

- Existing categories preserved.
- + Add Category saves.
- Custom category synchronizes.
- Case-insensitive duplicate prevention.
- Historical Others records remain readable.
- Business Paid remains functional.
- I Paid remains functional.
- Partner-funded expense recorded correctly.
- Shared expense does not duplicate cost.
- Loan-funded expense recorded once.
- Negative expense adjustments do not break charts.

## 24.8 External loan tests

- Create loan.
- Record disbursement.
- Record direct supplier funding.
- Link an expense to loan funding.
- Partial principal repayment.
- Full principal repayment.
- Interest handling.
- Outstanding amount.
- No loan proceeds classified as customer receipts.
- No duplicate principal expense.
- Retry does not duplicate repayment.

## 24.9 Chart tests

- Expense category totals.
- Expense funding-source totals.
- Loan repayment totals.
- Percentage accuracy.
- Empty data.
- One category.
- Large number of categories.
- Correct date filters.
- English/Tamil labels.
- Zero or reversal-adjusted values.

## 24.10 Borrowing tests

- Own-entitlement withdrawal.
- Direct Business borrowing without approval.
- Partner borrowing requiring approval.
- Approval acceptance.
- Approval rejection.
- Multiple lenders.
- Insufficient business cash.
- Insufficient lender entitlement.
- Concurrent borrowing requests.
- No double spending.
- Correct negative borrower position.

## 24.11 Settlement tests

- Create obligation.
- Partial repayment.
- Full repayment.
- Direct personal repayment.
- Business-mediated repayment.
- Business loan repayment.
- Shared-expense obligation.
- History retention.
- Reversal.
- Idempotency.
- Correct outstanding amounts.

## 24.12 Notification tests

- Partner creates job; owner notified.
- Partner edits job; owner notified.
- Partner records collection; owner notified.
- Original owner performs own action; no duplicate self-notification.
- Borrowing request reaches lender.
- Approval/rejection response reaches borrower.
- English message.
- Tamil message.
- Sender offline.
- Recipient offline.
- Both reconnect.
- Duplicate event retry.
- No success notification for rejected transaction.

## 24.13 Cross-device tests

Simulate or test multiple authorized accounts and devices:

- Device A online, Device B online.
- A offline, B online.
- A online, B offline.
- Both offline.
- Staggered reconnection.
- Simultaneous edits.
- Stale local records.
- Pending Need Review operations.
- Rejected authorization.
- App restart during pending sync.
- Duplicate outbox replay.

Use appropriate local test infrastructure and emulators.

**Never use production customer data as a destructive test fixture.**

---

# 25. PHASE TWENTY-ONE — GRADLE, GIT TEST ACTIONS AND GITHUB ACTIONS VERIFICATION

This section is mandatory.

## 25.1 Discover actual commands

Inspect:

- Gradle wrapper.
- Settings and build files.
- Android modules.
- Product flavors.
- Build variants.
- Existing test tasks.
- Current CI workflows.

Do not invent a Gradle task and claim that it ran.

## 25.2 Run project tests

Where supported by the actual repository, execute the applicable checks, for example:

- `./gradlew test`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`
- `./gradlew :app:lintDebug`
- `./gradlew :app:connectedDebugAndroidTest`

These are examples, not an instruction to run nonexistent tasks blindly.

Use correct commands for the discovered project configuration.

Run:

1. Relevant unit tests during each phase.
2. Financial regression tests after financial changes.
3. Room/SQLite integration tests after identity or persistence changes.
4. Synchronization tests after outbox or cloud changes.
5. Full applicable test suite before final release readiness.
6. Android APK assembly validation.
7. Relevant lint/static checks.
8. Instrumentation tests where an emulator/device is available.

If a test cannot run, state why.

Do not present an unexecuted test as passed.

## 25.3 Git diff checks

After each implementation phase, perform:

- `git status --short`
- `git diff --check`
- `git diff --stat`
- `git diff --name-status`
- Detailed review of relevant changes.

Verify:

- No accidental unrelated changes.
- No credentials or secrets.
- No destructive migration.
- No unwanted generated files.
- No unauthorized Firebase modification.
- No weakened tests.
- No accidental permission expansion.

## 25.4 GitHub Actions checks

Inspect whether relevant GitHub Actions workflows already exist.

If they do:

- Confirm their intended checks.
- Run or request the appropriate checks using the permitted repository workflow.
- Inspect actual results.
- Investigate failures.
- Do not bypass required checks.
- Do not modify workflow conditions to skip failing tests.

If GitHub Actions cannot be accessed, state that honestly.

## 25.5 Every phase must have a test checkpoint

For each completed phase, report:

**Phase:**  
**Files changed:**  
**New tests added:**  
**Existing tests executed:**  
**Tests passed:**  
**Tests failed:**  
**Tests blocked:**  
**Android build status:**  
**Git diff check:**  
**Firebase changes:**  
**Remaining risks:**  
**Git commit, if authorized:**

Do not proceed from a failed critical financial test to another financial feature without addressing or explicitly escalating the failure.

## 25.6 Pre-existing failures

If baseline tests were failing before implementation:

- Record the failure.
- Explain whether it is related to the requested changes.
- Do not silently modify unrelated production code to make the suite pass.
- Do not suppress the test.
- Obtain approval for additional out-of-scope fixes where necessary.

The goal is a genuinely passing test suite, not a misleading green report.

## 25.7 Git commits

If authorized:

- Use small, descriptive phase-level commits.
- Include implementation and relevant tests.
- Keep unrelated files out of commits.
- Provide commit hashes.
- Maintain an understandable change history.

Do not push or merge automatically if approval has not been granted.

---

# 26. PHASE TWENTY-TWO — SECURITY, INTEGRITY AND RELEASE REVIEW

## 26.1 Authorization review

Verify:

- Original owner privileges.
- Added partner privileges.
- Operator restrictions.
- Historical-entry creation.
- Old/new job editing.
- Customer reassignment permissions.
- Borrowing approval identity.
- Settlement repayment authorization.
- Workspace isolation.

## 26.2 Financial review

Validate numerical examples for:

- Customer dues.
- Late collections.
- Investments.
- Partner-paid expenses.
- External-loan principal.
- Partner withdrawals.
- Borrowed amounts.
- Repayments.
- Negative borrowing balances.
- Business cash limits.

## 26.3 Offline review

Verify the new operations do not break existing:

- Local persistence.
- Outbox.
- Sync retry.
- Conflict handling.
- Need Review state.
- Cross-device update propagation.

## 26.4 UI review

Check:

- Small Android screens.
- English/Tamil labels.
- Tap target sizes.
- Compact layout.
- Readable currency.
- Proper loading and error states.
- Clear repayment and borrowing explanations.
- No unnecessary redesign.

## 26.5 Controlled APK preparation

Only after approved implementation and successful validation:

- Confirm build variant.
- Confirm correct versioning.
- Confirm APK builds.
- Document test coverage.
- Document known issues.
- Recommend staged device testing.
- Provide release readiness assessment.

Do not deploy production Firebase changes automatically.

Do not publish or distribute an APK without authorization.

---

# 27. REQUIRED PROFESSIONAL DELIVERABLES

At the end of the approved implementation, provide:

## A. Requirements completion matrix

For every requirement, identify:

- Implemented.
- Already implemented and verified.
- Partially implemented.
- Blocked.
- Deferred.
- Not approved.

## B. Root-cause report

Explain the actual cause of confirmed bugs, particularly:

- Customer name inconsistencies.
- Cross-device edit failures.
- Need Review warnings.
- Customer identity problems.
- Late collection Balance Sheet omissions.
- Any confirmed partner financial inconsistency.

Do not invent root causes without evidence.

## C. Implementation report

List:

- Screens changed.
- Kotlin files changed.
- ViewModels changed.
- Repositories changed.
- Room changes.
- Sync changes.
- Firebase changes.
- Tests added.

## D. Financial validation report

Provide before/after numerical scenarios proving correct totals.

## E. Git report

Provide:

- Starting branch.
- Starting commit.
- Relevant worktree status.
- Final branch.
- Final commit, where applicable.
- Phase commits, if created.
- Changed-file summary.
- Remaining uncommitted changes.

## F. Test execution report

Include:

- Exact commands executed.
- Test count, where available.
- Passed tests.
- Failed tests.
- Skipped tests.
- Blocked tests.
- Build status.
- Lint result.
- Git diff check.
- GitHub Actions status.

## G. Firebase safety report

Explicitly state:

- Firebase project changed: Yes/No.
- Firestore schema changed: Yes/No.
- Security Rules changed: Yes/No.
- Cloud Functions changed: Yes/No.
- Cloudflare Workers changed: Yes/No.
- Production records changed: Yes/No.
- Data migration required: Yes/No.
- Existing sync protocol changed: Yes/No.

Provide justification for each Yes.

## H. Remaining risks

Identify incomplete or unresolved items without claiming they were implemented.

## I. Release recommendation

State whether the result is:

- Ready for additional development.
- Ready for QA.
- Ready for limited device testing.
- Ready for release review.
- Not ready.

Do not claim production readiness without supporting evidence.

---

# 28. REQUIREMENT PRIORITIES AND DEPENDENCY ORDER

Because implementation is already underway, avoid restarting completed work.

A safe proposed order is:

## Priority 1 — Protect and verify current customer-editing work

- Git baseline.
- Existing Work changes.
- Customer identity.
- Global customer editing.
- Individual job reassignment.
- Need Review debugging.
- Cross-device edit synchronization.
- Relevant automated tests.

## Priority 2 — Correct job permissions and records

- Original owner identity.
- Partner editing of old/new jobs.
- Historical job creation restriction.
- Creator and editor timestamps.
- Audit history.
- Entry for Operator terminology.
- Prefix-first customer search.

## Priority 3 — Correct the existing financial report

- Amount Received naming.
- Late collection reporting.
- Net Balance terminology.
- Existing expense treatment verification.
- Daily/monthly/yearly regression tests.

## Priority 4 — Enhance expenses and external loans

- Expense categories.
- + Add Category.
- Partner-funded/investment-funded expenses.
- Loan-funded expenses.
- External loan tracking.
- External loan repayments.
- Expense and loan pie charts.

## Priority 5 — Partner borrowing and settlement

- Business cash withdrawal restrictions.
- Borrow from Business without partner approval.
- Borrow from Partner with approval.
- Partner entitlement protection.
- Negative borrowing balances.
- Repayment.
- Settlement History.

## Priority 6 — Notifications and final integration

- Original owner activity notifications.
- Partner-specific approval notifications.
- English/Tamil.
- Offline event synchronization.
- Complete regression suite.
- Git/GitHub Actions validation.
- Release review.

Modify this sequence only when technical dependencies justify doing so.

Explain any proposed change of order.

---

# 29. UNRESOLVED RULES — DO NOT SILENTLY INVENT THE ANSWER

The investigation must explicitly flag any remaining ambiguity.

## A. Business borrowing allocation

Borrow from Business requires no partner approval.

However, determine whether its debt is owed solely to the business or whether the current approved model allocates that cash against partners' financial entitlements.

Do not silently create partner-to-partner debts.

## B. Expense inclusion in Net Balance

The user wants the existing simple three-field Balance Sheet retained.

Verify whether Expenses includes both Business Paid and I Paid and whether the field describes business operating expenses or cash paid by business.

Do not silently redefine historical report totals.

## C. Repayment destination

Distinguish repayments made:

- Directly to another partner.
- Into the business for another partner.
- Into the business to repay a business borrowing.

Each may have a different cash effect.

## D. Future profits and existing debt

Do not automatically offset future partner profit against old borrowing unless this rule is confirmed.

## E. Original owner versus operator

Preserve existing operator actions and permissions.

Do not accidentally extend partner-only permissions to operators.

## F. Existing job operator versus financial attribution

Do not equate a tractor operator field with ownership of partner profits unless the existing business model explicitly requires it.

For any unresolved high-impact issue:

1. Explain the conflict.
2. Show a small numerical example.
3. State the safest proposed treatment.
4. Identify the affected implementation phase.
5. Request approval before proceeding.

Continue only with unrelated approved work that can safely proceed.

---

# 30. FINAL NON-NEGOTIABLE INSTRUCTIONS

1. Use only the current collaborative GitHub repository.
2. Respect any ChatGPT Work implementation already in progress.
3. Do not overwrite uncommitted changes.
4. Do not rebuild AIDHUNT TRAC from scratch.
5. Preserve Kotlin/Compose/ViewModel/repository architecture.
6. Preserve Room/SQLite and offline-first synchronization.
7. Preserve Firebase infrastructure unless a necessary change is approved.
8. Preserve previously corrected expense synchronization.
9. Fix actual root causes instead of merely refreshing UI.
10. Use stable customer IDs.
11. Preserve global customer editing and individual-job reassignment as separate operations.
12. Do not silently merge customers.
13. Allow original owner and partners to edit old/new jobs as specified.
14. Keep historical job creation restricted to the original owner.
15. Preserve operator restrictions.
16. Show current job creation time and latest edit details.
17. Keep historical work date date-only.
18. Use Entry by and Entry for Operator only in their correct contexts.
19. Correct customer search using prefix-first alphabetical matching.
20. Rename Sales to Amount Received.
21. Retain the three-field Balance Sheet.
22. Record later due collections on their actual collection dates.
23. Preserve existing expense business rules.
24. Add custom expense categories without deleting historical categories.
25. Track investment-funded and loan-funded expenses accurately.
26. Keep external loans separate from partner borrowing.
27. Provide external-loan repayment tracking.
28. Provide accurate expense and loan pie charts.
29. Do not allow withdrawals exceeding available business cash.
30. Borrow from Business must not require a partner approval.
31. Borrow from Partner must require the affected lender's approval.
32. Preserve separate business, partner, entitlement, and debt balances.
33. Record borrowing and repayment in Settlement History.
34. Protect repayment cash from double counting.
35. Notify the original owner about relevant partner activities.
36. Provide relevant partner borrowing/settlement notifications.
37. Respect English/Tamil notification language.
38. Preserve synchronization and prevent duplicate notifications.
39. Run existing tests and add new regression tests.
40. Do not delete, skip, weaken, or manipulate tests to conceal failures.
41. Run the applicable Gradle build and test checks.
42. Run Git diff validation.
43. Inspect existing GitHub Actions and verify applicable checks.
44. Do not force-push, reset, delete, or rewrite work without authorization.
45. Record exact test results and Git evidence.
46. Never claim tests passed if they were not executed.
47. Do not deploy Firebase changes without approval.
48. Do not alter production financial data without authorization.
49. Preserve the compact, rural-friendly TRAC user experience.
50. Treat financial accuracy, data integrity, and compatibility as higher priority than adding visual features quickly.

---

# FINAL EXECUTION COMMAND

**START WITH PHASE ZERO ONLY.**

Inspect the actual current repository, the current ChatGPT Work implementation status, existing Git changes, the code architecture, current database and synchronization behavior, and the existing test suite.

Run safe baseline checks where possible without modifying or conflicting with active work.

Produce a complete professional investigation report that includes:

- Current implementation status.
- Confirmed root causes.
- Existing versus requested behavior.
- Existing work that should not be repeated.
- Financial and security dependencies.
- Minimal proposed changes.
- Git baseline.
- Baseline test results.
- New regression test plan.
- Phase-wise implementation order.
- Unresolved decisions.
- Approval requirements.

**THEN STOP AND WAIT FOR MY EXPLICIT APPROVAL.**

After approval, implement only the authorized phases, verify each phase with real Git and test evidence, preserve existing functionality, and report the actual outcomes.

The goal is a stable, professionally engineered AIDHUNT TRAC application—not simply a completed list of code changes.
