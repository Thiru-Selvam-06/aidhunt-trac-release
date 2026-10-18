# 7. Backend and security

Standard Android trusted commands still resolve to Firebase Functions. The latest authorized inspection found none deployed; a credential-free invitation endpoint request returned 404. This blocks live invitations and deletion independently of test numbers.

The Cloudflare Worker is staging-only with explicit workspace restrictions. A credential-free health probe returned 403; its cause/business behavior was not established. Never weaken staging restrictions or redirect production there. Deployment and production routing need separate authorization.

The four operations are beginWorkspaceInvitation, redeemWorkspaceInvitation, deleteWorkspaceCustomer and deleteWorkspaceEntry. Worker operations share policy with functions. Firestore REST preserves exact IDs and transactions. Firebase token verification establishes actor UID; current root ownership/membership, not client roles or paths, grants authority.

The chosen staging architecture stores GOOGLE_SERVICE_ACCOUNT_JSON only as an encrypted Worker Secret. A binding name in code is not the secret. Provision a dedicated least-privilege service account with required Firestore and Auth-lookup permissions, never Owner/Editor. Credentials do not belong in Android, Git, logs or artifacts.

Local Firebase client configuration, keys and private captures are excluded from publication. Synthetic deviceTest config is versioned. Historical signing exposure needs separate review/rotation; current-tree cleanup does not erase history. CI never deploys, upgrades billing or modifies business records.
