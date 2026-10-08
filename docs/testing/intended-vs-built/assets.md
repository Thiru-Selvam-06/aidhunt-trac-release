# Intended vs. Built: Asset Management (Tractors/Extensions)

This document tracks asset management expectations for Tractors and Extensions across roles.

---

## Comparative Domain Matrix

### 1. Asset Ownership and Access
*   **Intended Goal (ASSET-001 to ASSET-005):** Owners manage all assets. Partners can add allowed assets and delete their own within 24 hours, but cannot touch Owner-created assets.
*   **Built Behavior under Test:** Asset tables isolate rows by `workspaceId` and enforce creation UID checks upon deletion requests.

### 2. Operator Restrictions
*   **Intended Goal (ASSET-006):** Operators cannot perform any asset management or configuration changes.
*   **Built Behavior under Test:** Asset configuration screens hide actions for Operator profiles.
