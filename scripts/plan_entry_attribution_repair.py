"""Offline proposal only. No Firebase imports, network, mutation or apply entry point.

Input identity/membership provenance must be independently verified by an authorized
capture process. This planner checks consistency; it cannot authenticate an export.
Only Entry beneficiary fields are in scope, never creator/payer/collector audit data.
"""
from copy import deepcopy
import hashlib
import json
import math
from reconcile_workspace_inventory import identifier, require


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"),
                                     ensure_ascii=True, allow_nan=False).encode()).hexdigest()


def plan(snapshot, *, workspace_id, owner_uid):
    identifier(workspace_id, "workspace")
    identifier(owner_uid, "owner")
    require(snapshot.get("workspaceId") == workspace_id, "Snapshot workspace mismatch")
    require(snapshot.get("projectId") == "trac-aidhunt", "Snapshot project mismatch")
    require(snapshot.get("complete") is True and snapshot.get("paginationComplete") is True,
            "Complete snapshot required")
    require(snapshot.get("operationsComplete") is True, "Complete outbox inventory required")
    require(isinstance(snapshot.get("sourceSha256"), str) and
            len(snapshot["sourceSha256"]) == 64 and
            all(c in "0123456789abcdef" for c in snapshot["sourceSha256"]),
            "Source digest required")
    members = snapshot.get("members", [])
    require(isinstance(members, list), "Member list required")
    member_by_uid = {}
    for member in members:
        identifier(member.get("uid"), "member")
        require(member.get("workspaceId") == workspace_id, "Member workspace mismatch")
        require(member["uid"] not in member_by_uid, "Duplicate member")
        member_by_uid[member["uid"]] = member
    owner = member_by_uid.get(owner_uid, {})
    owner_verified = (owner.get("role") == "OWNER" and owner.get("status") == "ACTIVE" and
                      snapshot.get("ownerUid") == owner_uid and
                      snapshot.get("ownerIdentityVerified") is True and owner.get("identityVerified") is True)
    rows = snapshot.get("entries")
    require(isinstance(rows, list), "Entry list required")
    operations = snapshot.get("operations")
    require(isinstance(operations, list), "Outbox list required")
    blocked = set()
    for op in operations:
        require(op.get("workspaceId") == workspace_id, "Outbox workspace mismatch")
        require(op.get("state") in {"ACKNOWLEDGED", "PENDING", "IN_FLIGHT", "REJECTED", "INVALIDATED"},
                "Unknown outbox state")
        if op["state"] != "ACKNOWLEDGED":
            # Conservative: any unresolved command targeting the same exact ID blocks repair.
            identifier(op.get("targetId"), "outbox target")
            blocked.add(op["targetId"])
    items, seen = [], set()
    for row in rows:
        record_id = row.get("id")
        require(isinstance(record_id, str) and record_id.isascii() and record_id.isdigit() and
                0 < int(record_id) <= 2**63 - 1 and str(int(record_id)) == record_id, "Exact signed-64-bit string Entry ID required")
        require(record_id not in seen, "Duplicate Entry ID")
        seen.add(record_id)
        require(row.get("workspaceId") == workspace_id, "Entry workspace mismatch")
        require(isinstance(row.get("updateTime"), str) and bool(row["updateTime"]),
                "Document update precondition required")
        target, attribution = row.get("entryForUid"), row.get("financialAttributionUid")
        item = {"workspaceId": workspace_id, "id": record_id, "beforeSha256": digest(row),
                "updateTime": row["updateTime"], "patch": {},
                "existingAttribution": {"entryForUid": target, "financialAttributionUid": attribution},
                "reviewStatus": "AWAITING_INDEPENDENT_REVIEW", "warnings": [],
                "operationStatus": "UNRESOLVED_TARGET_OPERATION" if record_id in blocked else "NO_UNRESOLVED_TARGET_OPERATION",
                "ownerEvidence": {"uid": owner_uid, "workspaceId": workspace_id,
                    "role": owner.get("role"), "status": owner.get("status"),
                    "independentlyVerified": owner_verified, "sourceSha256": snapshot["sourceSha256"]}}
        amounts = [row.get(key) for key in ("totalAmount", "amountReceived", "pendingAmount")]
        financial_valid = all(type(v) in (int, float) and math.isfinite(v) and v >= 0 for v in amounts)
        financial_valid = financial_valid and abs(amounts[0] - amounts[1] - amounts[2]) < 0.001
        creation_valid = type(row.get("createdAt")) is int and 0 < row["createdAt"] <= 2**63 - 1
        if record_id in blocked:
            item["decision"] = "BLOCKED_OUTBOX"
        elif row.get("isDeleted") is not False or row.get("archived", False) is not False:
            item["decision"] = "RETAIN_NONACTIVE_OR_UNKNOWN"
        elif target not in (None, "") or attribution not in (None, ""):
            # Even a partial, unknown or conflicting assignment must not be overwritten.
            item["decision"] = "RETAIN_EXISTING_ASSIGNMENT"
        elif not financial_valid or not creation_valid:
            item["decision"] = "EXCLUDED_MALFORMED"
            item["warnings"] = ["Original financial or creation metadata needs independent review"]
        elif not owner_verified:
            item["decision"] = "UNRESOLVED_OWNER"
            item["warnings"] = ["Current ownership and active membership lack independent verification"]
        else:
            # This approved one-time destination is independent of creator identity.
            # Never rewrite creator audit fields or infer authorship from this decision.
            item["decision"] = "APPROVED_OWNER_FALLBACK"
            item["rationale"] = "Explicit one-time Owner attribution; not a claim of original creation"
            item["patch"] = {"entryForUid": owner_uid, "financialAttributionUid": owner_uid}
        items.append(item)
    return {"mode": "PROPOSAL_ONLY", "projectId": "trac-aidhunt", "workspaceId": workspace_id,
            "sourceSha256": snapshot["sourceSha256"], "snapshotSha256": digest(snapshot),
            "requiresTrustedRepair": True, "items": deepcopy(items)}
