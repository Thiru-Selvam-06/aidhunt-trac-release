# 3. Identity, roles and invitation protocol

Firebase UID identifies a person. Role belongs to one workspace membership. A UID can own A and be Partner in B. Phone, display name, profile defaults and a local partner mirror are not permission proof.

Authentication and workspace discovery are separate. Discovery indexes nominate candidates; current root ownership and active membership validate access. A stale default must not prevent another valid workspace opening or redirect queued work. Multiple valid choices require a selector or validated selection.

WorkspaceRequestFence binds initialization to UID/generation. Superseded requests are silent; actual coroutine cancellation propagates. A cached offline membership is not fresh online verification; reconnect must revalidate it.

| Role | Boundary |
|---|---|
| Owner | Current root ownerUid plus Owner membership; permitted workspace administration/financial actions |
| Partner | Granted workspace scope; own-record and original 24-hour restrictions where applicable; cannot invite as Owner |
| Operator | Operational scope; financial and administrative denials enforced independently |
| Removed/unknown/unauthenticated | No implicit Owner or access grant |

Use Architecture v1.6, AuthorizationManager, OperationPolicy, backend policies and firestore.rules. The role-action CSV is an outbox matrix, not comprehensive UI/backend proof.

## Invitation flow

```mermaid
sequenceDiagram
 participant O as Workspace Owner
 participant B as Trusted backend
 participant S as Secondary Firebase Auth
 participant F as Firestore
 O->>B: begin(workspace, phone, allowed role, retained operation ID)
 B->>F: Validate current Owner; persist pending invitation
 B-->>O: Invitation ID and expiry
 O->>S: Verify target phone OTP
 S-->>O: Target ID token
 O->>B: redeem(invitation ID, target token)
 B->>F: Transaction: proof, identity, role, membership, index, used state
 B-->>O: Confirmed UID, workspace and role
```

Primary Owner auth must not become target auth. Secondary Firebase auth proves the phone identity. Missing begin backend fails before OTP. HTTP 401 means authentication; 403/explicit PERMISSION_DENIED authorization; 400 validation; 5xx service. Message words Owner/permission do not classify errors.

An Owner elsewhere can join as Partner without changing the personal workspace, its Owner membership, default workspace or global directory metadata. Roles cannot be silently changed; removed-member and one-time proof protections remain. Test numbers get no special access bypass.

Member-list display now resolves current selected-workspace membership before filtering/displaying a stale local mirror role. This corrects presentation; it does not create or repair membership. A Partner's own personal workspace continues to show them as Owner there.
