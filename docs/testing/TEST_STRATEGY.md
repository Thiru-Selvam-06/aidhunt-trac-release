# AIDHUNT Trac Testing Strategy

This document outlines the testing philosophy, methodology, and pipeline architecture used to secure AIDHUNT Trac against regressions, financial anomalies, and role-privilege escalations.

## 1. JVM-Based Simulation Model

Because direct physical device/emulator automated testing can introduce flakiness, latency, and environment configuration overhead, AIDHUNT Trac utilizes **JVM-based Robolectric unit tests** to simulate multi-user, multi-device, offline/online synchronization behavior.

### Architectural Core Under Test
*   **Database Schema (Room):** Local persistence layer serving as the primary source of truth on devices.
*   **Financial Engine (`FinancialCalculationEngine`):** Central calculation authority for investor attribution, earnings, and summaries.
*   **Auth Manager (`AuthorizationManager`):** Rule engine executing 24-hour limits and role permissions.

---

## 2. Intended vs. Built Verification Paradigm

We explicitly distinguish between:
1.  **Intended Goal (Business Requirements):** The absolute, invariant logic mandated by the business models.
2.  **Built Behavior (Current Implementation):** What the compiled Kotlin/Compose codebase actually supports and validates.

This framework actively tracks where the built system satisfies the intended rules, and explicitly flags any operational limitations.

---

## 3. Continuous Integration Pipeline (GitHub Actions)

Our automated CI pipeline compiles and runs the complete verification suite on every change, outputting real-time scorecard reports:

```
[ Push / PR / Dispatch ]
          │
          ▼
   [ Checkout Code ]
          │
          ▼
   [ Run ./gradlew testDebugUnitTest --info ]
          │
          ▼
   [ Parse JUnit XML results ]
          │
          ▼
   [ Generate Scorecard, Matrices & Diagrams ]
          │
          ▼
[ Upload Artifact: aidhunt-trac-test-report-${RUN_ID} ]
```

Every execution yields machine-readable JSON data detailing exact preconditions, expected vs. actual outcomes, and verification evidence.
