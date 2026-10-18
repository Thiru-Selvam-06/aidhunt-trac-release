# AIDHUNT Trac v1.6 — architecture compliance audit

Audit: 2026-10-09. Current local working tree, modified/untracked code included. Audit/report only: no application implementation, Git mutations, deploys or production data operations.

## Part 1 — Executive summary

**Not ready for integration.** Substantial authorization, composite Room keys, atomic queue, collection reversal and Deleted Users work is worth retaining. Passing builds do not establish release safety. Two added Firebase Emulator denial tests FAILED: Operators can read historical Entries, and an ACTIVE member with an unrecognized role can read ordinary Entries. Home job-popup PDF actions bypass the role-aware ViewModel export capability. Local/release v14 schemas and migration paths conflict, so direct source replacement is unsafe for deployed data.

**Single most important next task: Q01 — close server and cached/UI read/export authorization gaps**, keeping locked permissions and existing design. Q02 data-preserving migration reconciliation is also release-blocking. Neither fix was implemented in this audit.

Authoritative source: C:/Users/user/Downloads/AIDHUNT_Trac_Stable_Authoritative_Architecture_v1.6.docx, read through complete paragraph/table extraction (793 paragraphs, 27 tables). Older documents and current behavior do not override it. Its accepted practical offline action time is tamperable; no cryptographic clock/attestation mechanism is required by v1.6.

Conflict: pasted checklist requests an immediate KEEP RECORDS / DELETE RECORDS TOO choice. v1.6 §4.2/§17 requires Stage1 keep-record removal, then later Stage2 purge. D-CONFLICT records the disagreement; Remove-only Stage1 is not a defect solely for lacking immediate purge.

Existing emulator tests: Rules55/55, trusted-service integration5/5, invitation policy10/10 pass. Added audit read tests:3 pass,2 fail. Android debug build/lint succeed; unit tests were explicitly rerun after the first check reused cached results. No physical OTP, device cold-start/reboot, live Firebase backup/restore, full purge recovery or performance certification is claimed.

## Part 2 — Full architecture checklist

Every entry has exactly one selected status; other status boxes remain unchecked. Verified means the specific named fixture/direct check, not production or device certification. Invariants/threats/milestones repeat underlying requirements for traceability, so these counts are **not a completion percentage**. §13 fields have individual entries. A reference to the 55-case suite describes representative coverage, not a claim that each permission cell has a passing dedicated test.

Checklist entries: 327.

- Implemented and verified: 12
- Implemented, verification incomplete: 257
- Partially implemented: 43
- Not implemented: 0
- Incorrect or unsafe: 11
- Blocked: 4

### I-01 — v1.6 §2

**Requirement:** One immutable Firebase UID per person, independent of viewer.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### I-02 — v1.6 §2

**Requirement:** Role is (memberUid,workspaceId), not global.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:782) — `cacheVerifiedMembership`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### I-03 — v1.6 §2

**Requirement:** Exactly OWNER/PARTNER/OPERATOR; no CO-OWNER.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:5) — `function member`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** FAILED read tests: Operator historical and unknown ACTIVE role reads succeed.

**Next action:** Q01. **Dependencies:** Q01, Q02.

### I-04 — v1.6 §2

**Requirement:** Membership authorizes Partner/Operator; root ownerUid identifies Owner.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:15) — `function owner`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### I-05 — v1.6 §2

**Requirement:** Every business record stays in one authorized workspace.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [Entities.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/entity/Entities.kt:6) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### I-06 — v1.6 §2

**Requirement:** Viewer never rewrites stored membership role assignments.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [RoleUtils.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/RoleUtils.kt:121) — `getEntryForDisplay`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### I-07 — v1.6 §2

**Requirement:** Viewing never grants edit/delete/reverse authority.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### I-08 — v1.6 §2

**Requirement:** Creator/beneficiary/collector/financial attribution stay distinct.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:380) — `financialAttributionUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### I-09 — v1.6 §2

**Requirement:** Original createdAt controls 24h; updatedAt never extends it.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:85) — `within24Hours`.

**Test evidence:** EntryCreationTimestampPersistenceTest and original-createdAt-reset denial cases in architecture.test.cjs — passed/inspected this audit; device and production coverage is separate.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### I-10 — v1.6 §2

**Requirement:** UI hiding is not security; Rules reject forged requests.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** FAILED read tests: Operator historical and unknown ACTIVE role reads succeed.

**Next action:** Q01. **Dependencies:** Q01, Q02.

### I-11 — v1.6 §2

**Requirement:** Queue retains original workspace/actor; never re-homes.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** V16ArchitectureContractTest.queuedActionCannotCrossWorkspaceOrSurviveMembershipRemoval — passed/inspected this audit; device and production coverage is separate.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### I-12 — v1.6 §2

**Requirement:** Financial engine uses only authorized workspace data.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:24) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### A-01 — v1.6 §3 / 10

**Requirement:** Fresh phone/OTP sign-in requires connectivity; no bypass.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-02 — v1.6 §3 / 10

**Requirement:** Fresh independent UID creates own workspace and becomes its one OWNER.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-03 — v1.6 §3 / 10

**Requirement:** Existing invitee joins without changing other workspaces.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-04 — v1.6 §3 / 10

**Requirement:** Brand-new invitee can later create their own Owner workspace.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-05 — v1.6 §3 / 10

**Requirement:** Closing/reopening app preserves real Firebase authenticated session.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-06 — v1.6 §3 / 10

**Requirement:** Device reboot does not unnecessarily require OTP.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-07 — v1.6 §3 / 10

**Requirement:** Network loss alone never logs authenticated user out.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-08 — v1.6 §3 / 10

**Requirement:** Explicit logout signs Firebase out and returns to login.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:145) — `signOut`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-09 — v1.6 §3 / 10

**Requirement:** Genuine invalid/revoked/signed-out session handled honestly; cache is not Auth.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-10 — v1.6 §3 / 10

**Requirement:** Restore verified workspace/role only for matching authenticated UID.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-11 — v1.6 §3 / 10

**Requirement:** Missing identity/role/workspace never invents OWNER or workspace.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No physical OTP, cold-start/reboot, revoked-token or logout instrumentation executed.

**Next action:** Q03. **Dependencies:** Q01.

### A-12 — v1.6 §3 / 10

**Requirement:** Fence delayed auth/profile callbacks after logout/account switch.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Async auth launch publishes profile/state without current-UID/generation recheck.

**Next action:** Q03. **Dependencies:** Q01.

### A-OWNER — v1.6 §3 / 10

**Requirement:** OWNER persists login through app/device restarts until explicit logout, subject to genuine invalidation.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:92) — `cachedVerifiedProfile`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:424) — `restoreVerifiedMembership`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Shared cached-startup service exists; separate role-specific device scenario untested.

**Next action:** Q03. **Dependencies:** Q01.

### A-PARTNER — v1.6 §3 / 10

**Requirement:** PARTNER persists login through app/device restarts until explicit logout, subject to genuine invalidation.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:92) — `cachedVerifiedProfile`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:424) — `restoreVerifiedMembership`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Shared cached-startup service exists; separate role-specific device scenario untested.

**Next action:** Q03. **Dependencies:** Q01.

### A-OPERATOR — v1.6 §3 / 10

**Requirement:** OPERATOR persists login through app/device restarts until explicit logout, subject to genuine invalidation.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:92) — `cachedVerifiedProfile`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:424) — `restoreVerifiedMembership`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Shared cached-startup service exists; separate role-specific device scenario untested.

**Next action:** Q03. **Dependencies:** Q01.

### B-01 — v1.6 §3 / 4 / 17

**Requirement:** Owner one target-phone OTP; shared code is consent.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Instant verification without SMS code lacks complete confirm path.

**Next action:** Q04. **Dependencies:** Q03.

### B-02 — v1.6 §3 / 4 / 17

**Requirement:** Trusted phone proof binds immutable target UID.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Live consent/OTP/session/multi-workspace lifecycle untested.

**Next action:** Q04. **Dependencies:** Q03.

### B-03 — v1.6 §3 / 4 / 17

**Requirement:** Requested invitation role restricted to Partner/Operator.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Live consent/OTP/session/multi-workspace lifecycle untested.

**Next action:** Q04. **Dependencies:** Q03.

### B-04 — v1.6 §3 / 4 / 17

**Requirement:** Normal OTP expires after 5 minutes.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Live consent/OTP/session/multi-workspace lifecycle untested.

**Next action:** Q04. **Dependencies:** Q03.

### B-05 — v1.6 §3 / 4 / 17

**Requirement:** Normal OTP single-use across repeated redemption.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Live consent/OTP/session/multi-workspace lifecycle untested.

**Next action:** Q04. **Dependencies:** Q03.

### B-06 — v1.6 §3 / 4 / 17

**Requirement:** Current test-number OTP flow has no expiry.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Expiry exemption is emulator-bound; configured test-number non-emulator flow not demonstrated.

**Next action:** Q04. **Dependencies:** Q03.

### B-07 — v1.6 §3 / 4 / 17

**Requirement:** Secondary Auth verification preserves Owner session.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Live consent/OTP/session/multi-workspace lifecycle untested.

**Next action:** Q04. **Dependencies:** Q03.

### B-08 — v1.6 §3 / 4 / 17

**Requirement:** Exactly one Owner; no Partner↔Operator changes.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Live consent/OTP/session/multi-workspace lifecycle untested.

**Next action:** Q04. **Dependencies:** Q03.

### B-09 — v1.6 §3 / 4 / 17

**Requirement:** No suspend/reactivate/ownership-transfer ability.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Live consent/OTP/session/multi-workspace lifecycle untested.

**Next action:** Q04. **Dependencies:** Q03.

### B-10 — v1.6 §3 / 4 / 17

**Requirement:** Partner/Operator cannot manage members; Partner can view permitted membership data.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Live consent/OTP/session/multi-workspace lifecycle untested.

**Next action:** Q04. **Dependencies:** Q03.

### RB-ENTRY-OWNER-1 — v1.6 §6 / 14

**Requirement:** ENTRY: OWNER — view all ordinary.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-OWNER-2 — v1.6 §6 / 14

**Requirement:** ENTRY: OWNER — create self/eligible member.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-OWNER-3 — v1.6 §6 / 14

**Requirement:** ENTRY: OWNER — edit any.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-OWNER-4 — v1.6 §6 / 14

**Requirement:** ENTRY: OWNER — delete any.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-PARTNER-1 — v1.6 §6 / 14

**Requirement:** ENTRY: PARTNER — view all ordinary.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-PARTNER-2 — v1.6 §6 / 14

**Requirement:** ENTRY: PARTNER — create self only.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-PARTNER-3 — v1.6 §6 / 14

**Requirement:** ENTRY: PARTNER — edit own-created <24h.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-PARTNER-4 — v1.6 §6 / 14

**Requirement:** ENTRY: PARTNER — delete own-created <24h.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-OPERATOR-1 — v1.6 §6 / 14

**Requirement:** ENTRY: OPERATOR — view all ordinary.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-OPERATOR-2 — v1.6 §6 / 14

**Requirement:** ENTRY: OPERATOR — create self only.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-OPERATOR-3 — v1.6 §6 / 14

**Requirement:** ENTRY: OPERATOR — edit own-created <24h.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-ENTRY-OPERATOR-4 — v1.6 §6 / 14

**Requirement:** ENTRY: OPERATOR — delete own-created <24h.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-OWNER-1 — v1.6 §6 / 14

**Requirement:** HISTORICAL: OWNER — view.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-OWNER-2 — v1.6 §6 / 14

**Requirement:** HISTORICAL: OWNER — create dedicated workflow.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-OWNER-3 — v1.6 §6 / 14

**Requirement:** HISTORICAL: OWNER — edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-OWNER-4 — v1.6 §6 / 14

**Requirement:** HISTORICAL: OWNER — delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-PARTNER-1 — v1.6 §6 / 14

**Requirement:** HISTORICAL: PARTNER — view through Business Overview only.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Home recent filter can surface historical Entries created today instead of restricting history to Business Overview.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-PARTNER-2 — v1.6 §6 / 14

**Requirement:** HISTORICAL: PARTNER — deny create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-PARTNER-3 — v1.6 §6 / 14

**Requirement:** HISTORICAL: PARTNER — deny edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-PARTNER-4 — v1.6 §6 / 14

**Requirement:** HISTORICAL: PARTNER — deny delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-OPERATOR-1 — v1.6 §6 / 14

**Requirement:** HISTORICAL: OPERATOR — deny read.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** FAILED audit test: Operator historical Entry read must be denied by v1.6 — expected failure, request succeeded.

**Gap:** Actual Rules read succeeded. Raw jobs StateFlow/recent filter also lack historical exclusion.

**Next action:** Q01. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-OPERATOR-2 — v1.6 §6 / 14

**Requirement:** HISTORICAL: OPERATOR — deny create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-OPERATOR-3 — v1.6 §6 / 14

**Requirement:** HISTORICAL: OPERATOR — deny edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-HISTORICAL-OPERATOR-4 — v1.6 §6 / 14

**Requirement:** HISTORICAL: OPERATOR — deny delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:61) — `canCreateOldEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-OWNER-1 — v1.6 §6 / 14

**Requirement:** EXPENSE: OWNER — view all.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-OWNER-2 — v1.6 §6 / 14

**Requirement:** EXPENSE: OWNER — create self/eligible target.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-OWNER-3 — v1.6 §6 / 14

**Requirement:** EXPENSE: OWNER — edit any.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-OWNER-4 — v1.6 §6 / 14

**Requirement:** EXPENSE: OWNER — delete any.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-PARTNER-1 — v1.6 §6 / 14

**Requirement:** EXPENSE: PARTNER — view all.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-PARTNER-2 — v1.6 §6 / 14

**Requirement:** EXPENSE: PARTNER — create self only.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-PARTNER-3 — v1.6 §6 / 14

**Requirement:** EXPENSE: PARTNER — edit own-created <24h.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-PARTNER-4 — v1.6 §6 / 14

**Requirement:** EXPENSE: PARTNER — delete own-created <24h.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-OPERATOR-1 — v1.6 §6 / 14

**Requirement:** EXPENSE: OPERATOR — deny read.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-OPERATOR-2 — v1.6 §6 / 14

**Requirement:** EXPENSE: OPERATOR — deny create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-OPERATOR-3 — v1.6 §6 / 14

**Requirement:** EXPENSE: OPERATOR — deny edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXPENSE-OPERATOR-4 — v1.6 §6 / 14

**Requirement:** EXPENSE: OPERATOR — deny delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-OWNER-1 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: OWNER — view all.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-OWNER-2 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: OWNER — create self/eligible Partner.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-OWNER-3 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: OWNER — deny ordinary edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-OWNER-4 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: OWNER — delete any.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-PARTNER-1 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: PARTNER — view all.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-PARTNER-2 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: PARTNER — create self only.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-PARTNER-3 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: PARTNER — deny ordinary edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-PARTNER-4 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: PARTNER — delete own-created <24h.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-OPERATOR-1 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: OPERATOR — deny read.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-OPERATOR-2 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: OPERATOR — deny create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-OPERATOR-3 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: OPERATOR — deny edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-WITHDRAWAL-OPERATOR-4 — v1.6 §6 / 14

**Requirement:** WITHDRAWAL: OPERATOR — deny delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-OWNER-1 — v1.6 §6 / 14

**Requirement:** COLLECTION: OWNER — view all.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-OWNER-2 — v1.6 §6 / 14

**Requirement:** COLLECTION: OWNER — collect for self against permitted due.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-OWNER-3 — v1.6 §6 / 14

**Requirement:** COLLECTION: OWNER — reverse any retaining history.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-OWNER-4 — v1.6 §6 / 14

**Requirement:** COLLECTION: OWNER — deny normal physical deletion.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-PARTNER-1 — v1.6 §6 / 14

**Requirement:** COLLECTION: PARTNER — view all.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-PARTNER-2 — v1.6 §6 / 14

**Requirement:** COLLECTION: PARTNER — collect for self including Owner-created due.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-PARTNER-3 — v1.6 §6 / 14

**Requirement:** COLLECTION: PARTNER — reverse own <24h retaining history.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-PARTNER-4 — v1.6 §6 / 14

**Requirement:** COLLECTION: PARTNER — deny normal physical deletion.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-OPERATOR-1 — v1.6 §6 / 14

**Requirement:** COLLECTION: OPERATOR — deny read.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-OPERATOR-2 — v1.6 §6 / 14

**Requirement:** COLLECTION: OPERATOR — deny create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-OPERATOR-3 — v1.6 §6 / 14

**Requirement:** COLLECTION: OPERATOR — deny reversal.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-COLLECTION-OPERATOR-4 — v1.6 §6 / 14

**Requirement:** COLLECTION: OPERATOR — deny physical deletion.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-OWNER-1 — v1.6 §6 / 14

**Requirement:** CUSTOMER: OWNER — view.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-OWNER-2 — v1.6 §6 / 14

**Requirement:** CUSTOMER: OWNER — create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-OWNER-3 — v1.6 §6 / 14

**Requirement:** CUSTOMER: OWNER — edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-OWNER-4 — v1.6 §6 / 14

**Requirement:** CUSTOMER: OWNER — delete with dependency protection.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-PARTNER-1 — v1.6 §6 / 14

**Requirement:** CUSTOMER: PARTNER — view.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-PARTNER-2 — v1.6 §6 / 14

**Requirement:** CUSTOMER: PARTNER — create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-PARTNER-3 — v1.6 §6 / 14

**Requirement:** CUSTOMER: PARTNER — edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-PARTNER-4 — v1.6 §6 / 14

**Requirement:** CUSTOMER: PARTNER — deny delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-OPERATOR-1 — v1.6 §6 / 14

**Requirement:** CUSTOMER: OPERATOR — deny read.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Rules finance-only read is correct; cached MainViewModel.customers/customersWithDue are role-unfiltered.

**Next action:** Q01. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-OPERATOR-2 — v1.6 §6 / 14

**Requirement:** CUSTOMER: OPERATOR — deny create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-OPERATOR-3 — v1.6 §6 / 14

**Requirement:** CUSTOMER: OPERATOR — deny edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-CUSTOMER-OPERATOR-4 — v1.6 §6 / 14

**Requirement:** CUSTOMER: OPERATOR — deny delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1311) — `updateCustomer`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:191) — `match /customers`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-OWNER-1 — v1.6 §6 / 14

**Requirement:** TRACTOR: OWNER — view/use.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-OWNER-2 — v1.6 §6 / 14

**Requirement:** TRACTOR: OWNER — create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-OWNER-3 — v1.6 §6 / 14

**Requirement:** TRACTOR: OWNER — edit any.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-OWNER-4 — v1.6 §6 / 14

**Requirement:** TRACTOR: OWNER — delete any with references protected.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-PARTNER-1 — v1.6 §6 / 14

**Requirement:** TRACTOR: PARTNER — view/use shared.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-PARTNER-2 — v1.6 §6 / 14

**Requirement:** TRACTOR: PARTNER — create own.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-PARTNER-3 — v1.6 §6 / 14

**Requirement:** TRACTOR: PARTNER — edit own anytime.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-PARTNER-4 — v1.6 §6 / 14

**Requirement:** TRACTOR: PARTNER — delete own anytime.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-OPERATOR-1 — v1.6 §6 / 14

**Requirement:** TRACTOR: OPERATOR — view/use shared.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-OPERATOR-2 — v1.6 §6 / 14

**Requirement:** TRACTOR: OPERATOR — deny create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-OPERATOR-3 — v1.6 §6 / 14

**Requirement:** TRACTOR: OPERATOR — deny edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-TRACTOR-OPERATOR-4 — v1.6 §6 / 14

**Requirement:** TRACTOR: OPERATOR — deny delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-OWNER-1 — v1.6 §6 / 14

**Requirement:** EXTENSION: OWNER — view/use.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-OWNER-2 — v1.6 §6 / 14

**Requirement:** EXTENSION: OWNER — create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-OWNER-3 — v1.6 §6 / 14

**Requirement:** EXTENSION: OWNER — edit any.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-OWNER-4 — v1.6 §6 / 14

**Requirement:** EXTENSION: OWNER — delete any with references protected.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-PARTNER-1 — v1.6 §6 / 14

**Requirement:** EXTENSION: PARTNER — view/use shared.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-PARTNER-2 — v1.6 §6 / 14

**Requirement:** EXTENSION: PARTNER — create own.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-PARTNER-3 — v1.6 §6 / 14

**Requirement:** EXTENSION: PARTNER — edit own anytime.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-PARTNER-4 — v1.6 §6 / 14

**Requirement:** EXTENSION: PARTNER — delete own anytime.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-OPERATOR-1 — v1.6 §6 / 14

**Requirement:** EXTENSION: OPERATOR — view/use shared.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-OPERATOR-2 — v1.6 §6 / 14

**Requirement:** EXTENSION: OPERATOR — deny create.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-OPERATOR-3 — v1.6 §6 / 14

**Requirement:** EXTENSION: OPERATOR — deny edit.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### RB-EXTENSION-OPERATOR-4 — v1.6 §6 / 14

**Requirement:** EXTENSION: OPERATOR — deny delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2453) — `addExtension`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### F-01 — v1.6 §7 / 8

**Requirement:** Dedicated Historical Entry marker/workflow; ordinary age never reclassifies it.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Passed this audit: ordinaryEntryDoesNotBecomeHistoricalFromWorkDateOrAge

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-02 — v1.6 §7 / 8

**Requirement:** Owner Entry for Partner + I Paid attributes investment to Partner.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-03 — v1.6 §7 / 8

**Requirement:** Expense attribution follows target participant separately from creator.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-04 — v1.6 §7 / 8

**Requirement:** Split collections share original customer and Job/credit identity.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-05 — v1.6 §7 / 8

**Requirement:** Owner 20k stays unchanged when Partner reverses 30k; due restored 30k.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Passed this audit: Owner 20k collection unchanged when Partner reverses own 30k

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-06 — v1.6 §7 / 8

**Requirement:** Reversal keeps status=REVERSED history; no physical deletion.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Passed this audit: Owner 20k collection unchanged when Partner reverses own 30k

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-07 — v1.6 §7 / 8

**Requirement:** Delete Due hard-deletes source Entry subject to collection/reference reconciliation.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-08 — v1.6 §7 / 8

**Requirement:** Authorized normal entity delete retains queued tombstone until acknowledged physical deletion.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-09 — v1.6 §7 / 8

**Requirement:** Shared Customers/historical asset references protected on delete.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-10 — v1.6 §7 / 8

**Requirement:** Unmapped legacy beneficiary never guessed; totals preserved for migration.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Passed this audit: unmappedBeneficiaryIsNotGuessedFromCreatorAndTotalsRemainVisible

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### F-11 — v1.6 §7 / 8

**Requirement:** Pending/synced/deleted/reversed records produce consistent financial summaries.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### D-01 — v1.6 §4.2 / 10 / 17

**Requirement:** Owner-only Stage1 removal immediately revokes workspace access.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2574) — `removePartnerFromWorkspace`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1426) — `removePartnerFromWorkspace`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-02 — v1.6 §4.2 / 10 / 17

**Requirement:** Stage1 archives in Owner-only Deleted Users, keeps business data.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2574) — `removePartnerFromWorkspace`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1426) — `removePartnerFromWorkspace`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-03 — v1.6 §4.2 / 10 / 17

**Requirement:** Retained records greyed/read-only with original attribution.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2574) — `removePartnerFromWorkspace`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1426) — `removePartnerFromWorkspace`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-04 — v1.6 §4.2 / 10 / 17

**Requirement:** Retained dues remain collectible by Owner/Partner.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2574) — `removePartnerFromWorkspace`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1426) — `removePartnerFromWorkspace`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-05 — v1.6 §4.2 / 10 / 17

**Requirement:** No new record attribution to removed UID.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2574) — `removePartnerFromWorkspace`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1426) — `removePartnerFromWorkspace`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-06 — v1.6 §4.2 / 10 / 17

**Requirement:** Stage2 later explicit permanent-delete confirmation from Deleted Users.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-07 — v1.6 §4.2 / 10 / 17

**Requirement:** Purge creator OR beneficiary/target UID in current workspace.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Passed planner fixture: V16ArchitectureContractTest.purgeIncludesOwnerCreatedBeneficiaryAndProtectsSharedCustomerAndOtherWorkspace; full purge recovery not tested.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-08 — v1.6 §4.2 / 10 / 17

**Requirement:** Include member-created assets and linked collection/source dependencies.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-09 — v1.6 §4.2 / 10 / 17

**Requirement:** Reconcile dues/collection effects before physical purge.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-10 — v1.6 §4.2 / 10 / 17

**Requirement:** Protect shared Customers referenced by retained records.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Passed planner fixture: V16ArchitectureContractTest.purgeIncludesOwnerCreatedBeneficiaryAndProtectsSharedCustomerAndOtherWorkspace; full purge recovery not tested.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-11 — v1.6 §4.2 / 10 / 17

**Requirement:** Protect historical asset snapshots/references for survivors.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-12 — v1.6 §4.2 / 10 / 17

**Requirement:** Never delete global Firebase Auth UID or affect other workspaces.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Passed planner fixture: V16ArchitectureContractTest.purgeIncludesOwnerCreatedBeneficiaryAndProtectsSharedCustomerAndOtherWorkspace; full purge recovery not tested.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-13 — v1.6 §4.2 / 10 / 17

**Requirement:** Invalidate actor/entity pending operations to prevent resurrection.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-14 — v1.6 §4.2 / 10 / 17

**Requirement:** Keep PURGE_PENDING until both cloud and local cleanup complete.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-15 — v1.6 §4.2 / 10 / 17

**Requirement:** Idempotent recovery under retry/partial failure/concurrent collection.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-16 — v1.6 §4.2 / 10 / 17

**Requirement:** Verify actual data restorable backup before destructive migration/purge testing.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [x] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No actual Firebase backup/restore evidence.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### D-CONFLICT — v1.6 §4.2 vs audit request

**Requirement:** Resolve requested immediate KEEP RECORDS/DELETE RECORDS TOO choice against locked two-stage lifecycle.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [x] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2574) — `removePartnerFromWorkspace`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** v1.6 explicitly forbids immediate Stage1 purge. Remove-only followed by later purge is consistent; not an architecture defect solely for lacking immediate delete choice.

**Next action:** Q00. **Dependencies:** Explicit architecture revision if different lifecycle intended..

### O-01 — v1.6 §9 / 10

**Requirement:** Cached role authorization before every local mutation.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1498) — `requireWorkspaceActor`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-02 — v1.6 §9 / 10

**Requirement:** Room business mutation and queue insert atomic.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1540) — `enqueue`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** V16ArchitectureContractTest.roomMutationAndQueueRollbackTogether — passed/inspected this audit; device and production coverage is separate.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-03 — v1.6 §9 / 10

**Requirement:** Stable operationId survives retry/process recreation.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1540) — `enqueue`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-04 — v1.6 §9 / 10

**Requirement:** Command retains originating workspace/actor.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1540) — `enqueue`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** V16ArchitectureContractTest.queuedActionCannotCrossWorkspaceOrSurviveMembershipRemoval — passed/inspected this audit; device and production coverage is separate.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-05 — v1.6 §9 / 10

**Requirement:** Persist type/entity/order/actionTime/dependency references.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1540) — `enqueue`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Dependency/backoff/process-death/performance proof missing; first failure stops loop.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-06 — v1.6 §9 / 10

**Requirement:** Before-deadline offline action syncs later; after deadline denies.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** V16ArchitectureContractTest.mondayOfflineActionPreservesAuthorizationAtTuesdaySync and emulator offline action before/after deadline cases — passed/inspected this audit; device and production coverage is separate.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-07 — v1.6 §9 / 10

**Requirement:** Accepted practical local clock; no attestation added.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Direct OperationPolicy/Rules inspection: local actionTime used, no clock attestation service — passed/inspected this audit; device and production coverage is separate.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-08 — v1.6 §9 / 10

**Requirement:** Local hard delete retains durable command until cloud ack.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1540) — `enqueue`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-09 — v1.6 §9 / 10

**Requirement:** Bounded sync around 10–20 commands.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [PendingOperationDao.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/dao/PendingOperationDao.kt:14) — `nextBatch`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Direct query inspection: LIMIT 20.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-10 — v1.6 §9 / 10

**Requirement:** Prerequisites/dependencies ordered across retries.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:879) — `pushUnsyncedToCloud`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Dependency/backoff/process-death/performance proof missing; first failure stops loop.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-11 — v1.6 §9 / 10

**Requirement:** Individual cloud acknowledgement precedes synced mark.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1549) — `acknowledge`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-12 — v1.6 §9 / 10

**Requirement:** Transient failures pending with bounded backoff.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:879) — `pushUnsyncedToCloud`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Dependency/backoff/process-death/performance proof missing; first failure stops loop.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-13 — v1.6 §9 / 10

**Requirement:** Permanent rejection visible, never shown synced.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:879) — `pushUnsyncedToCloud`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-14 — v1.6 §9 / 10

**Requirement:** Continue independent work after unrelated rejection.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:879) — `pushUnsyncedToCloud`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Query excludes only INVALIDATED, selects REJECTED; non-PENDING stops whole queue including independent later work.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-15 — v1.6 §9 / 10

**Requirement:** Process genuinely PENDING, not REJECTED commands.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [PendingOperationDao.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/dao/PendingOperationDao.kt:14) — `nextBatch`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Query excludes only INVALIDATED, selects REJECTED; non-PENDING stops whole queue including independent later work.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-16 — v1.6 §9 / 10

**Requirement:** Stable retries never duplicate financial effects.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-17 — v1.6 §9 / 10

**Requirement:** Account/workspace changes during batch cannot leak/reassign.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:879) — `pushUnsyncedToCloud`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-18 — v1.6 §9 / 10

**Requirement:** Responsive background sync survives process death.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:879) — `pushUnsyncedToCloud`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Dependency/backoff/process-death/performance proof missing; first failure stops loop.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-19 — v1.6 §9 / 10

**Requirement:** Measure hundreds-of-commands memory/locks/convergence.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [x] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:879) — `pushUnsyncedToCloud`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Dependency/backoff/process-death/performance proof missing; first failure stops loop.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-20 — v1.6 §9 / 10

**Requirement:** Role-permitted workflows after authenticated offline cold start.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:424) — `restoreVerifiedMembership`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-21 — v1.6 §9 / 10

**Requirement:** Pending/recoverable errors visible in existing UI.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1538) — `pendingOperations`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### O-22 — v1.6 §9 / 10

**Requirement:** Pending local results immediate; other devices only after sync.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1540) — `enqueue`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device/network/crash/convergence verification incomplete.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### W-01 — v1.6 §2 / 10 / 12

**Requirement:** Composite (workspaceId,id) keys isolate same-numbered records.

- [x] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AppDatabase.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/database/AppDatabase.kt:48) — `version = 17`.

**Test evidence:** Passed RoomScopeTest.sameIdsRemainIndependentAcrossWorkspaces.

**Gap:** Legacy global DAO/TractorRepository routes and complete same-UID-two-role account/listener/export tests need proof.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### W-02 — v1.6 §2 / 10 / 12

**Requirement:** Every local query/search/filter uses authorized active workspace.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:484) — `val jobs`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy global DAO/TractorRepository routes and complete same-UID-two-role account/listener/export tests need proof.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### W-03 — v1.6 §2 / 10 / 12

**Requirement:** Firestore queries/listeners/references bind explicit workspace.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:246) — `startRealtimeListeners`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy global DAO/TractorRepository routes and complete same-UID-two-role account/listener/export tests need proof.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### W-04 — v1.6 §2 / 10 / 12

**Requirement:** Stale callbacks cannot expose old account/workspace data.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:736) — `stopWorkspaceListeners`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy global DAO/TractorRepository routes and complete same-UID-two-role account/listener/export tests need proof.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### W-05 — v1.6 §2 / 10 / 12

**Requirement:** Reports/calculations/PDF never combine workspaces.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:24) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy global DAO/TractorRepository routes and complete same-UID-two-role account/listener/export tests need proof.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### W-06 — v1.6 §2 / 10 / 12

**Requirement:** Bidirectional forged cross-workspace requests/references deny.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy global DAO/TractorRepository routes and complete same-UID-two-role account/listener/export tests need proof.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### UI-OWNER-BusinessOverview — v1.6 §6.7 / 11 / 14

**Requirement:** OWNER: allow Business Overview.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OWNER-CustomerCreditDue — v1.6 §6.7 / 11 / 14

**Requirement:** OWNER: allow Customer Credit Due.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OWNER-CollectionHistory — v1.6 §6.7 / 11 / 14

**Requirement:** OWNER: allow Collection History.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OWNER-Expenses — v1.6 §6.7 / 11 / 14

**Requirement:** OWNER: allow Expenses.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OWNER-BalanceSheet — v1.6 §6.7 / 11 / 14

**Requirement:** OWNER: allow Balance Sheet.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OWNER-Withdrawal — v1.6 §6.7 / 11 / 14

**Requirement:** OWNER: allow Withdrawal.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OWNER-OldEntryCreation — v1.6 §6.7 / 11 / 14

**Requirement:** OWNER: allow Old Entry Creation.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-PARTNER-BusinessOverview — v1.6 §6.7 / 11 / 14

**Requirement:** PARTNER: allow Business Overview.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-PARTNER-CustomerCreditDue — v1.6 §6.7 / 11 / 14

**Requirement:** PARTNER: allow Customer Credit Due.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-PARTNER-CollectionHistory — v1.6 §6.7 / 11 / 14

**Requirement:** PARTNER: allow Collection History.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-PARTNER-Expenses — v1.6 §6.7 / 11 / 14

**Requirement:** PARTNER: allow Expenses.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-PARTNER-BalanceSheet — v1.6 §6.7 / 11 / 14

**Requirement:** PARTNER: allow Balance Sheet.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-PARTNER-Withdrawal — v1.6 §6.7 / 11 / 14

**Requirement:** PARTNER: allow Withdrawal.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-PARTNER-OldEntryCreation — v1.6 §6.7 / 11 / 14

**Requirement:** PARTNER: deny Old Entry Creation.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OPERATOR-BusinessOverview — v1.6 §6.7 / 11 / 14

**Requirement:** OPERATOR: deny Business Overview.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OPERATOR-CustomerCreditDue — v1.6 §6.7 / 11 / 14

**Requirement:** OPERATOR: deny Customer Credit Due.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OPERATOR-CollectionHistory — v1.6 §6.7 / 11 / 14

**Requirement:** OPERATOR: deny Collection History.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OPERATOR-Expenses — v1.6 §6.7 / 11 / 14

**Requirement:** OPERATOR: deny Expenses.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OPERATOR-BalanceSheet — v1.6 §6.7 / 11 / 14

**Requirement:** OPERATOR: deny Balance Sheet.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OPERATOR-Withdrawal — v1.6 §6.7 / 11 / 14

**Requirement:** OPERATOR: deny Withdrawal.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-OPERATOR-OldEntryCreation — v1.6 §6.7 / 11 / 14

**Requirement:** OPERATOR: deny Old Entry Creation.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:154) — `canAccessPage`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:259) — `canExportPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Role section selection exists; individual direct navigation, rendering and export/device tests missing.

**Next action:** Q09. **Dependencies:** Q01, Q06.

### UI-X-01 — v1.6 §6 / 11

**Requirement:** Operator PDF export denied from every surface.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [HomeScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/home/HomeScreen.kt:549) — `onDownloadPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Home popup Share/Save PDF callbacks unconditional; VM capability bypassed.

**Next action:** Q01. **Dependencies:** Q01, Q03, Q07.

### UI-X-02 — v1.6 §6 / 11

**Requirement:** Owner/Partner PDF contains only authorized workspace data.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [PdfGeneratorHelper.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/pdf/PdfGeneratorHelper.kt:66) — `generateJobReceiptPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Generic helper lacks auth context; every caller/dataset needs proof.

**Next action:** Q09. **Dependencies:** Q01, Q03, Q07.

### UI-X-03 — v1.6 §6 / 11

**Requirement:** Partner Expense For selector hidden; target self.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:943) — `addExpense`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Client/repository self rules exist; screenshot/device proof incomplete.

**Next action:** Q09. **Dependencies:** Q01, Q03, Q07.

### UI-X-04 — v1.6 §6 / 11

**Requirement:** Partner Withdrawal member dropdown hidden; self target.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:319) — `canTakeWithdrawalFor`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** UI rendering/device proof incomplete.

**Next action:** Q09. **Dependencies:** Q01, Q03, Q07.

### UI-X-05 — v1.6 §6 / 11

**Requirement:** Edit/delete/reverse controls mirror UID/time policy.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** All popup/navigation/direct callbacks require review.

**Next action:** Q09. **Dependencies:** Q01, Q03, Q07.

### UI-X-06 — v1.6 §6 / 11

**Requirement:** Operator asset controls view/use only.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:441) — `canMutateAsset`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device visual/navigation test missing.

**Next action:** Q09. **Dependencies:** Q01, Q03, Q07.

### UI-X-07 — v1.6 §6 / 11

**Requirement:** Business name/settings Owner-only mutation.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:220) — `match /settings`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Direct client mutation matrix incomplete.

**Next action:** Q09. **Dependencies:** Q01, Q03, Q07.

### UI-X-08 — v1.6 §6 / 11

**Requirement:** Existing theme/spacing/typography/navigation preserved.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [ReportScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/report/ReportScreen.kt:89) — `fun ReportScreen`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No complete screenshot comparison with supplied ZIP executed in this audit.

**Next action:** Q09. **Dependencies:** Q01, Q03, Q07.

### UI-X-09 — v1.6 §6 / 11

**Requirement:** Deleted Users confirmation follows existing visual conventions.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Dark/light/neighboring screen/recovery state visual validation missing.

**Next action:** Q09. **Dependencies:** Q01, Q03, Q07.

### C-Workspace-workspaceId — v1.6 §13

**Requirement:** Workspace: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Workspace-ownerUid — v1.6 §13

**Requirement:** Workspace: canonical ownerUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:40) — `ownerUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Workspace-name — v1.6 §13

**Requirement:** Workspace: canonical name preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:171) — `name`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Workspace-timestamps — v1.6 §13

**Requirement:** Workspace: canonical timestamps preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:42) — `createdAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Storage alias createdAt requires documented/tested mapping. Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Member-uid — v1.6 §13

**Requirement:** Member: canonical uid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:68) — `uid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Member-workspaceId — v1.6 §13

**Requirement:** Member: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Member-role — v1.6 §13

**Requirement:** Member: canonical role preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:70) — `role`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Member-status — v1.6 §13

**Requirement:** Member: canonical status preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:71) — `status`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Member-createdAt — v1.6 §13

**Requirement:** Member: canonical createdAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:42) — `createdAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Pendinginvitation-workspaceId — v1.6 §13

**Requirement:** Pending invitation: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:13) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Pendinginvitation-targetPhoneE164 — v1.6 §13

**Requirement:** Pending invitation: canonical targetPhoneE164 preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:25) — `phone` (document field `targetPhoneE164` maps to storage `phone`).

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Pendinginvitation-inviterUid — v1.6 §13

**Requirement:** Pending invitation: canonical inviterUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `ownerUid` (document field `inviterUid` maps to storage `ownerUid`).

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Pendinginvitation-requestedRole — v1.6 §13

**Requirement:** Pending invitation: canonical requestedRole preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`).

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Pendinginvitation-status — v1.6 §13

**Requirement:** Pending invitation: canonical status preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `status`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Pendinginvitation-expiresAt — v1.6 §13

**Requirement:** Pending invitation: canonical expiresAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:32) — `expiresAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Pendinginvitation-trusted_verification_binding — v1.6 §13

**Requirement:** Pending invitation: canonical trusted verification binding preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:54) — `auth_time`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Storage alias auth_time requires documented/tested mapping. Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Entry-workspaceId — v1.6 §13

**Requirement:** Entry: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Entry-createdByUid — v1.6 §13

**Requirement:** Entry: canonical createdByUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:375) — `createdByUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Entry-createdAt — v1.6 §13

**Requirement:** Entry: canonical createdAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:42) — `createdAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Entry-entryForUid — v1.6 §13

**Requirement:** Entry: canonical entryForUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:379) — `entryForUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Entry-financialAttributionUid — v1.6 §13

**Requirement:** Entry: canonical financialAttributionUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:380) — `financialAttributionUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Expense-workspaceId — v1.6 §13

**Requirement:** Expense: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Expense-createdByUid — v1.6 §13

**Requirement:** Expense: canonical createdByUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:375) — `createdByUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Expense-createdAt — v1.6 §13

**Requirement:** Expense: canonical createdAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:42) — `createdAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Expense-expenseForUid — v1.6 §13

**Requirement:** Expense: canonical expenseForUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:500) — `expenseForUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Expense-financialAttributionUid — v1.6 §13

**Requirement:** Expense: canonical financialAttributionUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:380) — `financialAttributionUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Tractor-workspaceId — v1.6 §13

**Requirement:** Tractor: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Tractor-createdByUid — v1.6 §13

**Requirement:** Tractor: canonical createdByUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:375) — `createdByUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Tractor-createdAt — v1.6 §13

**Requirement:** Tractor: canonical createdAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:42) — `createdAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Extension-workspaceId — v1.6 §13

**Requirement:** Extension: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Extension-createdByUid — v1.6 §13

**Requirement:** Extension: canonical createdByUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:375) — `createdByUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Extension-createdAt — v1.6 §13

**Requirement:** Extension: canonical createdAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:42) — `createdAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Withdrawal-workspaceId — v1.6 §13

**Requirement:** Withdrawal: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Withdrawal-createdByUid — v1.6 §13

**Requirement:** Withdrawal: canonical createdByUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:375) — `createdByUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Withdrawal-createdAt — v1.6 §13

**Requirement:** Withdrawal: canonical createdAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:42) — `createdAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Withdrawal-withdrawalForUid — v1.6 §13

**Requirement:** Withdrawal: canonical withdrawalForUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:664) — `targetPartnerUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Storage alias targetPartnerUid requires documented/tested mapping. Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Collection-workspaceId — v1.6 §13

**Requirement:** Collection: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Collection-customerId — v1.6 §13

**Requirement:** Collection: canonical customerId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:382) — `customerId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Collection-jobId_creditId — v1.6 §13

**Requirement:** Collection: canonical jobId/creditId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:457) — `jobEntryId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Storage alias jobEntryId requires documented/tested mapping. Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Collection-amount — v1.6 §13

**Requirement:** Collection: canonical amount preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:395) — `amount`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Collection-collectedByUid — v1.6 §13

**Requirement:** Collection: canonical collectedByUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:465) — `collectedByUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Collection-status — v1.6 §13

**Requirement:** Collection: canonical status preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:71) — `status`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Collection-createdAt — v1.6 §13

**Requirement:** Collection: canonical createdAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:42) — `createdAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Customer-workspaceId — v1.6 §13

**Requirement:** Customer: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Customer-createdByUid — v1.6 §13

**Requirement:** Customer: canonical createdByUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:375) — `createdByUid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-Customer-createdAt — v1.6 §13

**Requirement:** Customer: canonical createdAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:42) — `createdAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-DeletedUserarchive-workspaceId — v1.6 §13

**Requirement:** Deleted User archive: canonical workspaceId preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:69) — `workspaceId`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-DeletedUserarchive-memberUid — v1.6 §13

**Requirement:** Deleted User archive: canonical memberUid preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:68) — `uid`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Storage alias uid requires documented/tested mapping. Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-DeletedUserarchive-originalRole — v1.6 §13

**Requirement:** Deleted User archive: canonical originalRole preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirestoreModels.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt:70) — `role`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Storage alias role requires documented/tested mapping. Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-DeletedUserarchive-removedAt — v1.6 §13

**Requirement:** Deleted User archive: canonical removedAt preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [DeletedMemberEntity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/entity/DeletedMemberEntity.kt:11) — `removedAt`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### C-DeletedUserarchive-purgeStatus — v1.6 §13

**Requirement:** Deleted User archive: canonical purgeStatus preserves specified meaning.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [DeletedMemberEntity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/entity/DeletedMemberEntity.kt:13) — `purgeState`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Storage alias purgeState requires documented/tested mapping. Legacy backfill, missing-field validation and complete round-trip contract tests incomplete.

**Next action:** Q08. **Dependencies:** Q02, Q06.

### S-01 — v1.6 §9 / 13 / 18

**Requirement:** No subscription/account-validity gate.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:34) — `isAccountBlocked`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:48) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No active policy gating found in changed helpers; complete call-path/device regression missing.

**Next action:** Q06. **Dependencies:** Do not implement future subscription scope..

### S-02 — v1.6 §9 / 13 / 18

**Requirement:** No plan-expiry/member-count restriction.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:34) — `isAccountBlocked`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:48) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No active policy gating found in changed helpers; complete call-path/device regression missing.

**Next action:** Q06. **Dependencies:** Do not implement future subscription scope..

### S-03 — v1.6 §9 / 13 / 18

**Requirement:** No workspace/invitation/record-creation quota.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:34) — `isAccountBlocked`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:48) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No active policy gating found in changed helpers; complete call-path/device regression missing.

**Next action:** Q06. **Dependencies:** Do not implement future subscription scope..

### S-04 — v1.6 §9 / 13 / 18

**Requirement:** No personal-plan fallback/FREE downgrade.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:34) — `isAccountBlocked`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:48) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No active policy gating found in changed helpers; complete call-path/device regression missing.

**Next action:** Q06. **Dependencies:** Do not implement future subscription scope..

### S-05 — v1.6 §9 / 13 / 18

**Requirement:** Future subscription schema has no current access effect.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:34) — `isAccountBlocked`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:48) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No active policy gating found in changed helpers; complete call-path/device regression missing.

**Next action:** Q06. **Dependencies:** Do not implement future subscription scope..

### SEC-01 — v1.6 §12.1 / 12.3

**Requirement:** Authenticated UID required.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### SEC-02 — v1.6 §12.1 / 12.3

**Requirement:** Exact workspace membership required.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### SEC-03 — v1.6 §12.1 / 12.3

**Requirement:** ACTIVE status required.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### SEC-04 — v1.6 §12.1 / 12.3

**Requirement:** Only recognized workspace role grants access.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:5) — `function member`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** FAILED unknown-role/historical read tests demonstrate collection read gaps.

**Next action:** Q01. **Dependencies:** None.

### SEC-05 — v1.6 §12.1 / 12.3

**Requirement:** Collection-specific role permission required.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** FAILED unknown-role/historical read tests demonstrate collection read gaps.

**Next action:** Q01. **Dependencies:** None.

### SEC-06 — v1.6 §12.1 / 12.3

**Requirement:** Record workspace matches path.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### SEC-07 — v1.6 §12.1 / 12.3

**Requirement:** Creator ownership enforced.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### SEC-08 — v1.6 §12.1 / 12.3

**Requirement:** Original creation/action window enforced.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### SEC-09 — v1.6 §12.1 / 12.3

**Requirement:** Target/collector attribution enforced.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### SEC-10 — v1.6 §12.1 / 12.3

**Requirement:** Identity/workspace/createdAt immutable.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### SEC-11 — v1.6 §12.1 / 12.3

**Requirement:** Unmatched paths default deny.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### SEC-12 — v1.6 §12.1 / 12.3

**Requirement:** Legacy collaborationGroups/alternate path denied.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Representative fixture passes; exhaustive entity/role/immutable-field matrix incomplete.

**Next action:** Q01. **Dependencies:** None.

### T-01 — v1.6 §15

**Requirement:** Partner cannot edit Owner Entry

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-02 — v1.6 §15

**Requirement:** Partner own Entry after24h denies

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-03 — v1.6 §15

**Requirement:** Operator own Entry delete <24h allows

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-04 — v1.6 §15

**Requirement:** Partner withdrawal for other denies

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-05 — v1.6 §15

**Requirement:** Partner withdrawal target dropdown absent

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:319) — `canTakeWithdrawalFor`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** UI rendering/device proof incomplete.

**Next action:** Q09. **Dependencies:** Q01, Q03, Q07.

### T-06 — v1.6 §15

**Requirement:** Partner edit other Expense denies

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-07 — v1.6 §15

**Requirement:** Partner delete other Tractor denies

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-08 — v1.6 §15

**Requirement:** Partner reverse Owner collection denies

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:176) — `match /collections`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Repository/Rules path exists; full positive/negative UI→Room→queue→Firestore coverage for this permission cell incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-09 — v1.6 §15

**Requirement:** Owner Entry for Partner + I Paid attribution

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Named fixture passes where cited; all choices, allocation/crash/retry/source-delete and real legacy mapping combinations incomplete.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### T-10 — v1.6 §15

**Requirement:** Same UID workspace A/B isolated

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy global DAO/TractorRepository routes and complete same-UID-two-role account/listener/export tests need proof.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-11 — v1.6 §15

**Requirement:** Phone/name impersonation denies

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Narrow fixtures do not certify account/listener/export transitions or actual legacy backfill.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-12 — v1.6 §15

**Requirement:** Plan/expiry/quota must not block

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:34) — `isAccountBlocked`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:48) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No active policy gating found in changed helpers; complete call-path/device regression missing.

**Next action:** Q06. **Dependencies:** Do not implement future subscription scope..

### T-13 — v1.6 §15

**Requirement:** A records from B context deny

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Legacy global DAO/TractorRepository routes and complete same-UID-two-role account/listener/export tests need proof.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### T-14 — v1.6 §15

**Requirement:** Stage1 archive/read-only/collectible dues

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2574) — `removePartnerFromWorkspace`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1426) — `removePartnerFromWorkspace`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### T-15 — v1.6 §15

**Requirement:** Purge creator OR target current-workspace only

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Passed planner fixture: V16ArchitectureContractTest.purgeIncludesOwnerCreatedBeneficiaryAndProtectsSharedCustomerAndOtherWorkspace; full purge recovery not tested.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### T-16 — v1.6 §15

**Requirement:** Queued action cannot resurrect purged entity

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Full cloud/Room failure and concurrent collection proof missing. Backup path is device-local; cloud completion can precede local cleanup.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### J-01 — v1.6 §12.2 / 16

**Requirement:** Preserve release database through correct data-preserving migrations.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [x] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AppDatabase.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/database/AppDatabase.kt:209) — `MIGRATION_12_13`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Release v14/local v14 have different schemas. Release has Payment.createdAt already; local15→16 adds it again. Release14→local15 also lacks queue/identity/history additions. Synthetic16→17 test does not prove upgrade safety.

**Next action:** Q02. **Dependencies:** Q01, Q02.

### J-02 — v1.6 §12.2 / 16

**Requirement:** Inspect actual Firebase data and verify restorable export before destructive migration/purge.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [x] Blocked

**Code evidence:** [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** No production inspection/export/restore evidence; no production destructive action performed.

**Next action:** Q08. **Dependencies:** Q01, Q02.

### J-03 — v1.6 §12.2 / 16

**Requirement:** Incremental legacy mapping never guesses UID/history/time or loses financial totals.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AppDatabase.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/database/AppDatabase.kt:209) — `MIGRATION_12_13`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Empty defaults preserve rows but identities unresolved; actual validated backfill/cutover absent.

**Next action:** Q08. **Dependencies:** Q01, Q02.

### J-04 — v1.6 §12.2 / 16

**Requirement:** Preserve existing release Android CI build/lint/unit/Robolectric/artifacts/scorecard.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AppDatabase.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/database/AppDatabase.kt:48) — `version = 17`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Release android-ci.yml intact remotely, absent in local tree. architecture-v16.yml additive intent, not reconciled CI.

**Next action:** Q10. **Dependencies:** Q01, Q02.

### J-05 — v1.6 §12.2 / 16

**Requirement:** Every role/operation positive+negative creator/workspace/24h/immutable tests before exit.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** 55 existing pass but omit key read negatives; new2 fail. No complete device/production grid.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### J-06 — v1.6 §12.2 / 16

**Requirement:** Signing/config material reviewed without exposing values or accidental publication.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AppDatabase.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/database/AppDatabase.kt:48) — `version = 17`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Tracked release.jks on both sides, remote debug.keystore, local.properties/client Firebase config and Gradle signing literals need controlled security review.

**Next action:** Q10. **Dependencies:** Q01, Q02.

### M1 — v1.6 §16

**Requirement:** Identity/membership: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FirebaseAuthService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt:84) — `startAuthStateListener`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:726) — `initializeForUser`; [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:266) — `val isAuthenticated`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q03. **Dependencies:** Q01.

### M2 — v1.6 §16

**Requirement:** Invitation: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceInvitationService.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt:19) — `class WorkspaceInvitationService`; [index.js](C:/Users/user/Desktop/aidhunt-trac/functions/index.js:19) — `role` (document field `requestedRole` maps to storage `role`); [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:111) — `match /members`.

**Test evidence:** This audit: service integration 5 pass, invitation policy 10 pass; verification-token integration stubbed, not live SMS.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q04. **Dependencies:** Q03.

### M3 — v1.6 §16

**Requirement:** Workspace isolation: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:22) — `function envelope`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### M4 — v1.6 §16

**Requirement:** RBAC: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q01. **Dependencies:** None.

### M5 — v1.6 §16

**Requirement:** Entries: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1573) — `saveJob`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:104) — `canEditEntry`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### M6 — v1.6 §16

**Requirement:** Expenses: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:1935) — `addExpense`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:150) — `match /expenses`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### M7 — v1.6 §16

**Requirement:** Assets: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2429) — `addTractor`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:199) — `match /{asset}`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### M8 — v1.6 §16

**Requirement:** Withdrawals: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2096) — `addWithdrawal`; [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:162) — `match /withdrawals`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:122) — `canCollectPayment`.

**Test evidence:** Existing Firebase Emulator suite architecture.test.cjs: 55 passed this audit. Representative role mutations covered; not a dedicated proof of every UI/Room/sync permission cell.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### M9 — v1.6 §16

**Requirement:** Collections: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Passed this audit: Owner 20k collection unchanged when Partner reverses own 30k

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### M10 — v1.6 §16

**Requirement:** Financial attribution: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [FinancialCalculationEngine.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt:446) — `financialAttributionUid`; [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2284) — `recordCustomerPayment`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:660) — `applyPendingOperation`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q06. **Dependencies:** Q01, Q02, Q05.

### M11 — v1.6 §16

**Requirement:** Room/offline/sync: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:879) — `pushUnsyncedToCloud`; [OperationPolicy.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/OperationPolicy.kt:11) — `requireAuthorized`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q05. **Dependencies:** Q01, Q02, Q03.

### M12 — v1.6 §16

**Requirement:** No subscription enforcement: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:34) — `isAccountBlocked`; [AuthorizationManager.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/auth/AuthorizationManager.kt:48) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q06. **Dependencies:** Do not implement future subscription scope..

### M13 — v1.6 §16

**Requirement:** UI capabilities: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [HomeScreen.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/home/HomeScreen.kt:549) — `onDownloadPdf`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q01. **Dependencies:** Q01, Q03, Q07.

### M14 — v1.6 §16

**Requirement:** Security verification: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [firestore.rules](C:/Users/user/Desktop/aidhunt-trac/firestore.rules:127) — `match /entries`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q06. **Dependencies:** Q01, Q02.

### M15 — v1.6 §16

**Requirement:** Deleted Users/purge: all positive and negative exit gates before moving on.

- [ ] Implemented and verified
- [ ] Implemented, verification incomplete
- [x] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [WorkspaceRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/repository/WorkspaceRepository.kt:2791) — `permanentlyDeleteMember`; [FirestoreRepository.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreRepository.kt:1528) — `permanentlyDeleteMember`; [MemberPurgePlan.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt:15) — `fun build`; [DeletedUsersPage.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt:13) — `fun DeletedUsersPage`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Related checklist rows remain open; representative passing tests do not satisfy entire milestone exit.

**Next action:** Q07. **Dependencies:** Q02, Q05, Q06, Q08.

### HOME-OWNER — v1.6 §6

**Requirement:** OWNER can view Home and create permitted ordinary Entry.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:125) — `BottomTab.HOME`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:205) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device navigation/role rendering not executed.

**Next action:** Q09. **Dependencies:** Q01, Q03.

### HOME-PARTNER — v1.6 §6

**Requirement:** PARTNER can view Home and create permitted ordinary Entry.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:125) — `BottomTab.HOME`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:205) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device navigation/role rendering not executed.

**Next action:** Q09. **Dependencies:** Q01, Q03.

### HOME-OPERATOR — v1.6 §6

**Requirement:** OPERATOR can view Home and create permitted ordinary Entry.

- [ ] Implemented and verified
- [x] Implemented, verification incomplete
- [ ] Partially implemented
- [ ] Not implemented
- [ ] Incorrect or unsafe
- [ ] Blocked

**Code evidence:** [MainActivity.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/MainActivity.kt:125) — `BottomTab.HOME`; [MainViewModel.kt](C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt:205) — `canCreateNewEntry`.

**Test evidence:** Static inspection only; no targeted executed end-to-end test of this complete requirement.

**Gap:** Device navigation/role rendering not executed.

**Next action:** Q09. **Dependencies:** Q01, Q03.

## Part 3 — Persistent-login verification

| Scenario | Current conclusion | Code and actual test evidence |
|---|---|---|
| Owner app restart / device reboot | Implemented, verification incomplete | FirebaseAuthService.startAuthStateListener, cachedVerifiedProfile; WorkspaceRepository.restoreVerifiedMembership. No role-specific device run. |
| Partner app restart / device reboot | Implemented, verification incomplete | Same actual Firebase currentUser requirement and matching-UID cache guard. No role-specific device run. |
| Operator app restart / device reboot | Implemented, verification incomplete | Same service; no OTP required in cached startup branch. No role-specific device run. |
| Explicit logout | Implemented, verification incomplete | FirebaseAuthService.signOut clears verified workspace session and calls auth.signOut; MainViewModel logout routes via AuthState. Not exercised on device. Verified-auth profile cache survives but cannot authenticate without currentUser and matching verified workspace context. |
| Network loss | Cached restoration exists, device verification incomplete | Source.SERVER profile lookup times out/fails and can reuse matched cached verified profile. Network exception does not call signOut. This is not proof of every error/revocation scenario. |
| Invalid/revoked session | Not certified | Null/anonymous currentUser routes unauthenticated; live token expiry/revocation, permission denial and reconnect recovery untested. A cached profile alone cannot count as authentication. |
| Workspace/role restoration | Partially verified | Cached membership keyed by UID/workspace; per-workspace roles. Policy fixtures do not prove process restart/account change. |
| Delayed previous-account callback | Incorrect/unsafe race by inspection | Auth listener scope.launch captures old user then publishes fetched profile without rechecking current Firebase UID/generation; logout/switch may be followed by an obsolete Authenticated state. No runtime race test yet. |

Exact files/symbols and line links: A-01…A-12, A-OWNER/A-PARTNER/A-OPERATOR. Persistent authentication is separate from Room data availability: cached records do not prove a valid Firebase session, and a persisted Firebase user does not prove current online membership. While offline the locked architecture permits last-verified membership; removal is rechecked on reconnect. Never invent an offline OTP bypass.

Required device run per role: online OTP/session establishment → cached authorized workspace → force-stop → airplane mode → cold launch → check UID/workspace/role/Home → allowed and denied operations → force-stop with queue pending → reopen → reconnect/ack convergence. Separately: reboot, explicit logout, null session, server revocation, role removal, two-workspace switch, and another UID login while old queue exists. These remain NOT YET TESTED.

## Part 4 — Critical blockers

1. **P0 read/export authorization — Q01.** firestore.rules member() checks ACTIVE without recognized-role validation; entries read permits every member including Operator historical reads. Added negative tests actually fail. MainViewModel jobs/customers streams and Home recent filtering bypass equivalent cached-data restrictions. Home popup PDF share/save invokes the helper without canExportPdf. Consequence: restricted data/export accessible despite other correct mutation guards. Static PDF path finding has no device execution yet.
2. **P0 deployed database compatibility — Q02.** Release AppDatabase version14 and local version14 have different meanings. Release12→13 adds Payment.createdAt and asset creator-role fields; local12→13 adds attribution/history, and local13→14 creates queue. Starting on release14 skips those local additions, then local15→16 attempts duplicate Payment.createdAt. Synthetic local16→17 Room test does not prove this upgrade. Consequence: migration/open failure and risk of data loss if a destructive fallback were reinstated. Do not reinstate it.
3. **P1 auth callback/session boundaries — Q03.** Unfenced delayed auth/profile callback can publish obsolete Authenticated state after account change. Device cold start/reboot/logout/revocation not tested for any role. Consequence: stale identity/navigation and mismatched cached context risk; direct server requests still require actual Auth UID.
4. **P1 queue convergence/performance — Q05.** nextBatch includes REJECTED because only INVALIDATED is excluded. Sync stops at the first non-PENDING or failed command, so an unrelated rejected record blocks later independent work. Dependency references are implicit in payload/order rather than a demonstrated graph; bounded backoff/process-death scheduling and large-queue performance not proved. Consequence: permanent pending backlog and incomplete offline acceptance criteria.
5. **P1 purge/backfill/backup — Q07/Q08.** Planner fixtures exist, but full cloud/local partial-failure, concurrent collection, resumed purge on another device and queued-resurrection proof is missing. Backup manifest stores a device-local path; cloud purge completion can precede Room cleanup. Actual Firebase data/export/restore inspection not done. Consequence: unsafe cutover/purge claims would be unsupported.
6. **Integration and signing-material review — Q10.** Histories unrelated, release regression tests/CI are not present in this working tree. Both histories contain tracked signing material; config/signing literals need controlled review. No values disclosed and no claim of a confirmed compromised server credential. A client google-services.json is not automatically a server secret. Do not bulk-push the working tree.

## Part 5 — Prioritized implementation roadmap

Tasks below are proposed only. Approval to implement has not been assumed. Exact affected files are the absolute linked paths in referenced checklist rows; paths below supplement them.

| Task | Requirements | Smallest implementation scope / files | Dependencies | Tests and acceptance |
|---|---|---|---|---|
| Q00 — resolve specification conflict | D-CONFLICT | Product/spec decision only; keep v1.6 Stage1 remove/keep then later Stage2 purge unless explicitly revised | None; does not block other v1.6 fixes | No immediate Stage1 purge; revised document only if policy changes |
| Q01 — authorization read/export boundary | I-03/I-10, SEC-04/05, RB-HISTORICAL-OPERATOR-1, RB-HISTORICAL-PARTNER-1, RB-CUSTOMER-OPERATOR-1, UI-X-01 | C:/Users/user/Desktop/aidhunt-trac/firestore.rules; app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt; ui/screens/home/HomeScreen.kt; ui/util/RecentJobFilter.kt; data/auth/AuthorizationManager.kt; data/firebase/FirestoreRepository.kt read queries; security-tests/architecture.test.cjs | Preserve ordinary Operator view/use permissions and Partner Business Overview history visibility; no new roles/permissions | Added2 denial tests pass; normal Entry positive and Owner/Partner historical reads pass; Operator query uses appropriate ordinary-only filter because Rules are not result filters; cached historical/customer and all PDF paths deny; device direct navigation/export tests |
| Q02 — deployed migration convergence | J-01, W-01, C-*, M11 | C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/database/AppDatabase.kt; data/entity/Entities.kt; DAO files; migration tests and schema fixtures from release versions12/13/14 plus current local versions | Inspect both real schema lineages; restorable fixture backup. No destructive fallback | Actual release14 fixture opens target DB preserving IDs/UIDs/createdAt/amounts; local12–17 upgrades; duplicate IDs in A/B; no duplicate-column failure or missing-table/column validation; queue preserved |
| Q03 — auth/session fencing and cold start | A-*, W-04 | C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt; repository/WorkspaceRepository.kt; ui/viewmodel/MainViewModel.kt; MainActivity.kt | Q01; actual test device/Auth environment | Delayed old-UID callbacks ignored; three-role restart/reboot/offline/login/logout/revocation matrix; no invented Owner/workspace; cached session separate from Auth validity |
| Q04 — invitation exact lifecycle | B-* | C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt; functions/index.js; functions/invitation-policy.js | Q03; document exact test-number environment semantics | One OTP/consent, instant verification/no SMS-code handling, 5-minute real expiry, permitted test-number exemption, replay deny, target binding, Owner session unchanged, unrelated memberships intact |
| Q05 — durable queue recovery | O-*, T-16 | C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/dao/PendingOperationDao.kt; data/repository/WorkspaceRepository.kt; data/sync/OperationPolicy.kt; data/firebase/FirestoreRepository.kt | Q01–03; reuse one existing queue | Rejected command remains visible but does not block independent commands; dependents wait; transient bounded retry; per-op ack; double retry no duplicated financial effects; process kill/reconnect/switch at each boundary; measured hundreds-of-items queue and UI responsiveness |
| Q06 — full role/isolation/financial regression grid | RB-*, W-*, F-*, T-*, J-05 | C:/Users/user/Desktop/aidhunt-trac/security-tests/architecture.test.cjs; functions/security.integration.test.js; app/src/test and app/src/androidTest; ui/util/FinancialCalculationEngine.kt only if failing behavior needs fix | Q01/02/05 | Every role/action positive/negative, wrong creator/target/ws, expired window, immutable fields. Same UID A Owner/B Operator and reverse setup, all entities and exports; exact Owner-for-Partner I Paid Entry/Expense; partial/split/reversed collections; zero cross-workspace totals |
| Q07 — two-stage purge recovery | D-*, M15 | C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/sync/MemberPurgePlan.kt; data/repository/WorkspaceRepository.kt; data/firebase/FirestoreRepository.kt; ui/screens/account/DeletedUsersPage.kt | Q02/05/06/08 | Stage1 access gone/data retained/dues collectible; Stage2 creator OR target purge; shared Customer retained; concurrent collection handled; all local/cloud crash points; resumable backup/state, queued resurrection denied; global Auth/other workspaces untouched |
| Q08 — controlled data mapping/backup | J-02/03, C-*, D-16 | Current Firebase data inspection/export tooling; C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/data/firebase/FirestoreModels.kt; FirestoreBackupCodec.kt; AppDatabase.kt migrations | Authorized actual test/data access and verified backup; no guessed identity mapping | Export restored into isolated environment and checksum/type/row totals verified; ambiguous records reported; validated incremental mapping/backfill; no production wipe/deploy during audit |
| Q09 — preserve design and validate navigation | UI-*, HOME-*, D-03 | Existing theme/composables, C:/Users/user/Desktop/aidhunt-trac/app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt; AccountScreen.kt; report/ReportScreen.kt; home/HomeScreen.kt | Q01/03/07; supplied ZIP + device screenshots | Owner7/Partner6/Operator0 restricted reports; correct controls; existing design matches references; no redesign; pending/rejected/removed/purge confirmations understandable in dark/light |
| Q10 — isolated release reconciliation and CI | J-04/06; all unresolved release differences | Future isolated branch codex/… based on release origin/main; C:/Users/user/Desktop/aidhunt-trac/.github/workflows/android-ci.yml remote version; architecture-v16.yml; Gradle/wrapper; release-only tests | Security/data gates, controlled signing review; do not modify current checkout during audit | Preserve release CI jobs and four release-only regression test files; compatible Gradle/Room schema; Android build/lint/tests + emulator security jobs + artifacts; inspect no generated files/secrets; remote CI actually green before reviewed PR |

Q01 is the next implementation task, not a deploy instruction. Q02 must be completed before any release schema cutover. Cosmetic checks follow security/data fixes; existing design remains authoritative.

## Part 6 — Local integration and push readiness

**Not ready for integration.** A future isolated integration branch/worktree based on release origin/main is the safe approach; no branch/worktree was created and no history was combined here. Never use --allow-unrelated-histories or a blanket working-tree replacement.

Verified checkout: C:/Users/user/Desktop/aidhunt-trac. Branch main tracks previous-origin/main. Correct release origin is https://github.com/Thiru-Selvam-06/aidhunt-trac-release.git. previous-origin is retained metadata for the old repository, not the release target. Local HEAD 374d6ac6f6922418c4e9dee1e373d1fee1fae317; release origin/main 0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141. merge-base exits1: histories unrelated (5 local-exclusive / 8 release-exclusive commits in previous full-history comparison), not merely ahead/behind. No fetch, pull, switch, merge, rebase, reset, stash, commit, push or remote/tracking change performed this audit. Remote tracking reference was verified; live remote CI was not executed.

The previous full file-level comparison remains the reconciliation inventory:
[C:/Users/user/.codex/visualizations/2026/10/08/01a11c81-8450-7d90-8b9e-c65fb0ed95ed/reconciliation-audit/file-level-comparison.csv](C:/Users/user/.codex/visualizations/2026/10/08/01a11c81-8450-7d90-8b9e-c65fb0ed95ed/reconciliation-audit/file-level-comparison.csv), 24,417 union paths. It contains 23,866 generated/build/cache paths, 377 local-only, 37 release-only/local-deleted, 35 both-different, 9 sensitive/config review, 93 identical source/auxiliary paths. These are category counts, not feature counts. 193 local-only paths are agent skills; do not call all 377 architecture implementations. Source-only comparison is 551 rows. Current audit preserves source content underlying that inventory; test regeneration changed generated artifacts, so their hashes are historical rather than a fresh generated-artifact comparison.

Carry forward selectively: current policy/repositories, Firestore Models/Rules, composite Room/DAO work after migration correction, durable queue/purge classes after recovery corrections, financial identity separation, dedicated Historical Entry integration, focused local tests, and minimal existing-theme UI additions. New implementation exists mainly as uncommitted changes/untracked source, not the HEAD commit.

Retain release-only CI, Unix gradlew, scripts/generate_ci_report.py, testing docs, and regression files CompleteRegressionPipelineTest, PartnerEarningsChartTest, PaymentReversalAndCollectionHistoryTest, and ui/screens/report/DriverEarningsConsistencyTest. Manually reconcile overlapping AppDatabase/Entities/DAOs, repositories/ViewModel/auth, Rules, tests, Gradle wrapper/build and FileProvider paths. Release wrapper9.3.1 vs local9.7.1 is a compatibility decision; do not silently copy one. Existing resource/theme design mostly matches in the file comparison; file_paths.xml differs (release broad roots vs local reports-only roots), requiring PDF regression tests.

Do not publish generated app/build/.gradle caches, APKs/intermediates, crash/startup logs, unrelated agent-skill assets, local.properties, signing keys or unchecked configuration. release.jks is tracked on both sides; debug.keystore exists remotely. Review Gradle signing literal references and both client Firebase configurations without revealing values; determine ownership/rotation/removal handling separately. No signing material was printed or changed.

Preserve release .github/workflows/android-ci.yml exactly in purpose: JDK21 debug build/lint, unit/Robolectric suite, structured report script, APK/lint/test/scorecard artifacts. Local architecture-v16.yml adds emulator/service intent but does not replace the release jobs and has not run on GitHub. Extend with the missing read-denial, deployed-schema migration, isolation and queue/purge fixtures after reconciliation. Fresh remote CI and instrumentation are mandatory before declaring reviewed-PR readiness.

## Part 7 — Verification log and boundaries

### Actually executed in this audit

| Command / inspection | Actual result | Scope |
|---|---|---|
| git status --short --untracked-files=all; git branch -vv; git remote -v; git log -1 --oneline; git diff --stat; git diff --cached --stat; git rev-parse HEAD/origin/main | Initial main→previous-origin/main, release origin correct, 862 tracked changes /34 untracked, no staged diff; commits above | Local checkout and fetched ref; source includes dirty/untracked work |
| git merge-base HEAD origin/main; history/revision inspection | Exit1, no common ancestor; incompatible lineages | No history mutation |
| git show origin/main:.github/workflows/android-ci.yml and release AppDatabase | Success; workflow jobs intact in release ref; migration12→13 and version14 differ from local | Static fetched-release evidence; no remote CI run |
| gradlew.bat testDebugUnitTest assembleDebug lintDebug --project-cache-dir .codex/build-cache-v16 -I .codex/isolated-build.init.gradle --no-daemon --no-configuration-cache | BUILD SUCCESSFUL in3m33s;58 tasks,9 executed/49 up-to-date | Debug build/lint successful; initial unit task reused existing results. This combined call's process exit metadata was not retained; success is from final Gradle log |
| gradlew.bat testDebugUnitTest --rerun --project-cache-dir .codex/build-cache-v16 -I .codex/isolated-build.init.gradle --no-daemon --no-configuration-cache | Exit0, BUILD SUCCESSFUL in50s;164 tests,0 failure/error/skipped; test task actually executed | Fresh Android unit/Robolectric run, not physical device |
| node --test security-tests/architecture.test.cjs | Exit0;55 pass/0 fail;13.60s | Actual Firestore Rules against localhost18180 demo-aidhunt-v16 emulator |
| node --test functions/security.integration.test.js | Exit0;5 pass/0 fail;5.70s | Trusted-service integration, emulator DB, Auth verification stub; no live SMS |
| node --test functions/invitation-policy.test.js | Exit0;10 pass/0 fail;85ms | Pure invitation expiry/replay/identity policy fixtures |
| node --test [external audit directory]/read-authorization.audit.cjs | Exit1;3 pass/2 fail | Operator ordinary and Owner/Partner historical reads pass; Operator historical-read denial and unknown-role-read denial FAIL because requests succeeded |
| Full v1.6 OOXML extraction and source tracing | Complete document paragraphs/tables read; policy/auth/ViewModel/Room/queue/Rules/Functions/financial/report/PDF/member lifecycle traced | Static inspection; individual evidence links above |

Security commands used FIRESTORE_EMULATOR_HOST=127.0.0.1:18180 and GCLOUD_PROJECT=demo-aidhunt-v16. Existing suites clear **demo fixture data only**, ran sequentially, and never addressed production. Added audit test and logs are outside the source checkout. No Firebase rules/functions deployment occurred. The direct read tests deliberately preserve failing assertions; they were not weakened to make the suite green.

Specific fresh unit evidence: V16ArchitectureContractTest (10) includes ordinaryEntryDoesNotBecomeHistoricalFromWorkDateOrAge, mondayOfflineActionPreservesAuthorizationAtTuesdaySync, queuedActionCannotCrossWorkspaceOrSurviveMembershipRemoval, noNameOrLegacyRoleCanGrantPermission, partnerExpenseIsOwnAndTimelyOperatorHasNoExpensePermission, collectionReversalUsesCreationTimeAndKeepsOtherCollectorUnchanged, purgeIncludesOwnerCreatedBeneficiaryAndProtectsSharedCustomerAndOtherWorkspace, roomMutationAndQueueRollbackTogether, financialScopeExcludesSameNumberedRecordInAnotherWorkspace, unmappedBeneficiaryIsNotGuessedFromCreatorAndTotalsRemainVisible. RoomScopeTest (4): sameIdsRemainIndependentAcrossWorkspaces, rejectedAndInvalidatedCommandsNeverBecomeAcknowledged, backupRoundTripPreservesFirestoreValueTypes, keyMigrationPreservesExistingRecordsAndCreationTimes. The rejection test checks acknowledgement, not independent queue convergence. The key migration test uses a synthetic local16 fixture, not actual release14. EntryCreationTimestampPersistenceTest reopens creation-time fields; it is not Firebase Auth startup.

### Not yet tested / blocked

Physical/device Owner/Partner/Operator cold-start/reboot/offline/logout; live OTP/consent; genuine token revocation; process kill while syncing; background process-death recovery; all same-UID/two-workspace flows in both directions; released DB upgrade fixtures; full permanent-purge failure/concurrency recovery; actual backup/export/restore; hundreds-of-command latency/memory/frame responsiveness; every screen compared against supplied ZIP; release compilation/signing; instrumentation/UI tests; live production Firebase; remote GitHub CI. Existing startup/crash logs are historical diagnostics, not current passing acceptance evidence. No unsupported optimization or release-readiness claim is made.

### Evidence artifacts

Audit logs, external failing tests, checklist JSON, baseline and preservation verification are in:
[C:/Users/user/.codex/visualizations/2026/10/08/01a11c81-8450-7d90-8b9e-c65fb0ed95ed/compliance-audit](C:/Users/user/.codex/visualizations/2026/10/08/01a11c81-8450-7d90-8b9e-c65fb0ed95ed/compliance-audit).

No fixes were made. Stop after this report; the next authorized implementation should begin with Q01 after user approval.

### Final preservation check

Initial baseline:862 tracked differences,34 untracked. Final:1107 tracked differences,35 untracked. The only new non-generated source/document path is this report, docs/architecture-compliance-audit.md. All 67 baseline non-generated dirty/untracked files are byte-identical; all34 pre-existing untracked paths remain present and unchanged. No application source or Firebase configuration was edited. HEAD, origin/main ref, index and Git config hashes are unchanged.

**Generated-output exception:** verification compared all896 original dirty/untracked file hashes.740 pre-existing generated app/build artifacts changed through the Android validation run;245 previously-clean tracked generated paths also became dirty. The requested isolated Gradle build/cache setup did not isolate every Kotlin intermediate. No generated file was reset/restored/deleted to conceal this. These regenerated files are excluded from source integration and must not be committed. Original source/user modifications remain intact; a claim that the entire working tree stayed byte-identical would be false.

Exact preservation procedure: before.json captured git status plus SHA-256 of every dirty/untracked file and .git/index/.git/config, HEAD and origin/main. After checks, verify-preservation.py/final-qa.py repeated hashes and git status --porcelain=v1 -z --untracked-files=all with GIT_OPTIONAL_LOCKS=0. preservation-verification.json records the complete changed-generated path lists and zero preexisting_non_generated_changed. Report itself is the permitted new artifact; tests/logs/helpers outside source and ignored .codex outputs are not application edits.
