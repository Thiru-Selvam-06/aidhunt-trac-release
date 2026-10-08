# AIDHUNT Trac Testing Framework

Welcome to the official, permanent testing documentation and verification specification for AIDHUNT Trac.

This area holds our comprehensive test strategies, matrices, case specifications, and diagrammatic models that bridge the gap between **Intended Goals (business/architecture rules)** and the **Built Goals (actual implementation under test)**.

## Directory Structure

*   [**TEST_STRATEGY.md**](TEST_STRATEGY.md): Overall testing philosophy, execution models, and automation pipeline details.
*   [**TEST_MATRIX.md**](TEST_MATRIX.md): Detailed inventory of all critical test cases across all domains, complete with stable IDs.
*   [**intended-vs-built/**](intended-vs-built/README.md): Focus area detailing differences between business rules and actual compiled behaviors.
    *   [Authorization Model](intended-vs-built/authorization.md)
    *   [Workspace Isolation](intended-vs-built/workspace-isolation.md)
    *   [Identity Invariants](intended-vs-built/identity.md)
    *   [Offline & Multi-User Sync](intended-vs-built/offline-sync.md)
    *   [Payments & Reversals](intended-vs-built/payments.md)
    *   [Financial Integrity & Invariants](intended-vs-built/financial-integrity.md)
    *   [Offline Sessions](intended-vs-built/session.md)
    *   [Withdrawals Management](intended-vs-built/withdrawals.md)
    *   [Asset Management (Tractors/Extensions)](intended-vs-built/assets.md)
*   [**cases/**](cases/README.md): Individual deep-dive technical specs of critical test cases with full preconditions, action steps, verification methods, and stable schema identifiers.
*   [**matrices/**](matrices/): Machine-readable JSON specifications of authorization grids, offline convergence, and isolation properties.
*   [**diagrams/**](diagrams/README.md): Machine-readable schema JSON files for flowcharts, authorization graphs, and dependency mapping.

---

## Technical Verification Policy

Every critical test documented in this framework corresponds to an active, automated Robolectric or unit test in our regression test suite. Gaps between business requirements (Intended) and current code capabilities (Built) are explicitly analyzed and documented, ensuring full transparency.
