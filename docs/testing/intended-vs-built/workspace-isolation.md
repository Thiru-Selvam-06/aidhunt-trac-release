# Intended vs. Built: Workspace Isolation

This document outlines how AIDHUNT Trac isolates multi-tenant workspaces to prevent cross-workspace data leakage.

---

## Comparative Domain Matrix

### 1. Read Isolation
*   **Intended Goal (WS-001):** Workspace A data can never be visible or queryable from Workspace B.
*   **Built Behavior under Test:** Handled via room DAO queries. `testWorkspaceIsolation` inserts distinct records in `workspace_alpha_101` and `workspace_beta_202`, and verifies that querying Workspace A never returns Workspace B records.

### 2. Multi-Tenant Identifiers
*   **Intended Goal (WS-002 to WS-005):** Tenancy must rely on stable, generated `workspaceId` values, and never on dynamic human names, phone numbers, or display names which are subject to collision.
*   **Built Behavior under Test:** Entities map a specific `workspaceId: String` foreign key. Querying is strictly filtered via SQLite query parameters on this key.

### 3. Synchronization Safety
*   **Intended Goal (WS-008 to WS-010):** Synchronizing offline records must not leak data into a different workspace or resurrect deleted records.
*   **Built Behavior under Test:** Synced queries run per `workspaceId`. Tombstone checks verify deleted items are permanently removed from sync streams.
