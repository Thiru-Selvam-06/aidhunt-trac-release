# Technical Critical Test Case Specifications

Each specification document here conforms to a unified testing schema.

## Core Schema Requirements

*   **id:** Stable identifier (e.g. `WS-001`).
*   **domain:** Technical domain (e.g. `workspace-isolation`).
*   **title:** Short, descriptive title.
*   **intended_goal:** Business requirement.
*   **built_behavior_under_test:** Actual verified compiled behavior.
*   **actor:** Active role under test (OWNER/PARTNER/OPERATOR).
*   **actor_uid_requirement:** Associated test profile UID.
*   **workspace:** Active test workspaces.
*   **preconditions:** Expected initial database/app state.
*   **setup:** Scripted setup steps.
*   **action:** Method execution or simulated operation.
*   **expected_result:** Positive outcomes.
*   **forbidden_result:** Negative leakage or side effects.
*   **verification_method:** Unit test assertions or DAO queries.
*   **source_test_file:** File containing the test.
*   **test_function:** Method name inside file.
*   **priority:** Level of importance.
*   **offline_state:** Connected/disconnected.
*   **security_boundary:** Security scope (e.g. workspace, user role).
*   **financial_impact:** Effects on balances or ledgers.
*   **evidence:** Evidence references.
*   **status:** PASS/FAIL/BLOCKED.

---

## Detailed Test Case Files

We provide explicit specifications for primary critical test cases inside this folder:

*   [**WS-001.md** (Workspace Isolation Test)](WS-001.md)
*   [**AUTH-001.md** (Owner Privilege Elevation Test)](AUTH-001.md)
*   [**PAY-008.md** (Reversal Balance Reconciliation Test)](PAY-008.md)
*   [**FIN-005.md** (Stable Investment Attribution Test)](FIN-005.md)
