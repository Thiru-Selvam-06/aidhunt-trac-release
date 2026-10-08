# Test Case: AUTH-001 (Role Authorization)

*   **id:** AUTH-001
*   **domain:** role-authorization
*   **title:** Owner Privilege Elevation
*   **intended_goal:** Owners must have administrative access to delete collections and bypass time limit constraints.
*   **built_behavior_under_test:** `AuthorizationManager.canDeleteCollection` returns `true` if role is OWNER.
*   **actor:** OWNER
*   **actor_uid_requirement:** `owner_uid`
*   **workspace:** `workspace_alpha_101`
*   **preconditions:** A payment record exists in the database.
*   **setup:** Mock active user session as `owner_uid` with role `ROLE_OWNER`.
*   **action:** Check delete rights on a collection created by a different member 25 hours ago.
*   **expected_result:** Action is permitted immediately.
*   **forbidden_result:** Action is blocked by time limit constraints or role errors.
*   **verification_method:** `assertTrue(AuthorizationManager.canDeleteCollection(...))` is asserted successfully.
*   **source_test_file:** `CompleteRegressionPipelineTest.kt`
*   **test_function:** `testRoleAuthorizationRules`
*   **priority:** Critical
*   **offline_state:** Connected
*   **security_boundary:** User Role
*   **financial_impact:** Administrative ledger editing capability.
*   **evidence:** `testRoleAuthorizationRules()` success.
*   **status:** PASS
