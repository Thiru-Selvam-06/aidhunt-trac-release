# Intended vs. Built: Offline Sessions

This document describes how user sessions and authentication are maintained across states.

---

## Comparative Domain Matrix

### 1. Persistence
*   **Intended Goal (SESSION-001, SESSION-002):** User sessions remain authenticated after process death or removal from recents.
*   **Built Behavior under Test:** Preferences keep cached state valid. Asserted in unit test flows.

### 2. Startup Cache
*   **Intended Goal (SESSION-003):** Startup offline must load cached profile details smoothly, without default/blank fallbacks.
*   **Built Behavior under Test:** Session states load synchronously on startup to prevent flickering auth screens.
