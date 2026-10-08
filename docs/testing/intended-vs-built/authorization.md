# Intended vs. Built: Role Authorization

This document contrasts the expected security permissions across the Owner, Partner, and Operator roles with their actual validation assertions.

---

## Comparative Domain Matrix

### 1. Owner Privileges
*   **Intended Goal (AUTH-001, AUTH-002):** The Owner possesses total control of the workspace. They are not constrained by the 24-hour self-mutation edit/delete limit.
*   **Built Behavior under Test:** `AuthorizationManager.canDeleteCollection` checks if `isOwner == true` or `role == ROLE_OWNER` and returns `true` immediately regardless of record age.

### 2. Partner Limitations
*   **Intended Goal (AUTH-004, AUTH-005, AUTH-006, AUTH-007):** Partners have shared reading rights but can only edit/delete their own entries within a strict 24-hour timeframe. They cannot manage the Owner.
*   **Built Behavior under Test:** Validated in `testRoleAuthorizationRules` where Partner returns `true` for a record created 2 hours ago with matching UID, but returns `false` for records created 25 hours ago or belonging to another UID.

### 3. Operator Constraints
*   **Intended Goal (AUTH-008 to AUTH-013):** Operators are self-only. They cannot manage members, tractors, extensions, or perform payment/collection deletions.
*   **Built Behavior under Test:** Checked in `testRoleAuthorizationRules` where Operator has no deletion rights under any circumstance.
