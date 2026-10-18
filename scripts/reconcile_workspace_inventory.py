"""Offline-only comparison of provided sanitized inventories; never fetches data.

A match proves only equality within the declared complete scope, not authenticity,
authorization, numerical financial correctness or the state of a live workspace.
"""
import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import sys

CATEGORIES = {"ENTRY", "CUSTOMER", "COLLECTION", "EXPENSE", "WITHDRAWAL", "TRACTOR", "EXTENSION"}
STATES = {"ACTIVE", "REVERSED", "DELETED", "ARCHIVED", "UNKNOWN"}
SYNC = {"ACKNOWLEDGED", "PENDING", "IN_FLIGHT", "REJECTED", "INVALIDATED", "UNKNOWN"}
HASH = re.compile(r"[0-9a-f]{64}\Z")
ID = re.compile(r"[A-Za-z0-9_-]{1,160}\Z")

class InvalidInventory(ValueError):
    pass

def require(condition, message):
    if not condition:
        raise InvalidInventory(message)

def shape(value, keys, location):
    require(isinstance(value, dict) and set(value) == set(keys.split()), location + ": unexpected or missing fields")

def identifier(value, location):
    # Never round numeric IDs through floating point, including JavaScript-sized IDs.
    require(isinstance(value, str) and ID.fullmatch(value) is not None, location + ": ID must be an exact string")

def timestamp(value, location, nullable=False):
    require((nullable and value is None) or (type(value) is int and 0 < value <= 2**63-1), location + ": expected positive signed-64-bit milliseconds or explicit null")

def instant(value, location):
    require(isinstance(value, str), location + ": expected ISO8601 timestamp with offset")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        require(parsed.tzinfo is not None and parsed.utcoffset() is not None, location + ": timezone required")
    except ValueError:
        raise InvalidInventory(location + ": invalid timestamp") from None

def validate(data):
    shape(data, "schemaVersion workspaceId identityScheme source snapshotTime scope records operations", "inventory")
    require(type(data["schemaVersion"]) is int and data["schemaVersion"] == 1, "schemaVersion: unsupported")
    identifier(data["workspaceId"], "workspaceId")
    require(data["identityScheme"] in {"opaque-v1", "sha256-v1"}, "identityScheme: unsupported")
    shape(data["source"], "label environment projectId artifactSha256", "source")
    source = data["source"]
    for key in ("label", "projectId"):
        require(isinstance(source[key], str) and 0 < len(source[key]) <= 160, "source: invalid metadata")
    require(source["environment"] in {"SYNTHETIC", "EMULATOR", "AUTHORIZED_SANITIZED_EXPORT"}, "source: unsupported environment")
    require(isinstance(source["artifactSha256"], str) and HASH.fullmatch(source["artifactSha256"]), "source: artifact digest required")
    instant(data["snapshotTime"], "snapshotTime")
    shape(data["scope"], "categories queryScope complete paginationComplete operationsComplete", "scope")
    scope = data["scope"]
    require(isinstance(scope["categories"], list) and scope["categories"] and all(isinstance(x, str) and x in CATEGORIES for x in scope["categories"]), "scope: invalid categories")
    require(len(scope["categories"]) == len(set(scope["categories"])), "scope: duplicate categories")
    require(isinstance(scope["queryScope"], str) and bool(scope["queryScope"].strip()), "scope: query/filter definition required")
    for key in ("complete", "paginationComplete", "operationsComplete"):
        require(type(scope[key]) is bool, "scope: completeness flags must be boolean")
    require(isinstance(data["records"], list) and isinstance(data["operations"], list), "records/operations: arrays required")
    for i, row in enumerate(data["records"]):
        at = f"records[{i}]"
        shape(row, "workspaceId category id status businessTime operationTime deleted archived syncState attributionSha256", at)
        identifier(row["workspaceId"], at)
        identifier(row["id"], at)
        require(isinstance(row["attributionSha256"], str) and HASH.fullmatch(row["attributionSha256"]), at + ": attribution digest required")
        if data["identityScheme"] == "sha256-v1":
            require(HASH.fullmatch(row["id"]) is not None, at + ": hashed identity required")
        require(row["category"] in scope["categories"], at + ": category outside declared scope")
        require(row["status"] in STATES and row["syncState"] in SYNC, at + ": unsupported state")
        require(type(row["deleted"]) is bool and type(row["archived"]) is bool, at + ": explicit boolean flags required")
        require(row["status"] != "REVERSED" or row["category"] == "COLLECTION", at + ": reversal requires collection")
        timestamp(row["businessTime"], at + ".businessTime", True)
        timestamp(row["operationTime"], at + ".operationTime", True)
    for i, op in enumerate(data["operations"]):
        at = f"operations[{i}]"
        shape(op, "operationId workspaceId actorSha256 category targetId mutationType protocolVersion actionTime payloadSha256 dependencies state", at)
        for key in ("operationId", "workspaceId", "targetId"):
            identifier(op[key], at + "." + key)
        if data["identityScheme"] == "sha256-v1":
            require(HASH.fullmatch(op["targetId"]) is not None, at + ": hashed target required")
        require(op["category"] in scope["categories"], at + ": category outside scope")
        require(op["mutationType"] in {"CREATE", "UPDATE", "DELETE", "REVERSE"}, at + ": unsupported mutation")
        require(type(op["protocolVersion"]) is int and op["protocolVersion"] in {1, 2}, at + ": unsupported protocol marker")
        timestamp(op["actionTime"], at + ".actionTime", True)
        require(op["state"] in SYNC, at + ": unsupported sync state")
        for key in ("actorSha256", "payloadSha256"):
            require(isinstance(op[key], str) and HASH.fullmatch(op[key]), at + ": digest required")
        require(isinstance(op["dependencies"], list), at + ": dependencies must be an array")
        for dep in op["dependencies"]:
            identifier(dep, at + ".dependency")
        require(len(set(op["dependencies"])) == len(op["dependencies"]), at + ": duplicate dependency")
    return data

def fingerprint(key):
    return hashlib.sha256(json.dumps(key, separators=(",", ":")).encode()).hexdigest()

def reconcile(expected, observed, required_categories=()):
    require(isinstance(required_categories, (list, tuple, set, frozenset)) and
            all(isinstance(c, str) and c in CATEGORIES for c in required_categories),
            "required categories: unsupported scope")
    required_categories = set(required_categories)
    validate(expected)
    validate(observed)
    issues, gaps = [], []
    def problem(code, side, key=None, fields=None):
        item = {"code": code, "side": side}
        if key is not None:
            item["identitySha256"] = fingerprint(key)
        if fields:
            item["fields"] = sorted(fields)
        issues.append(item)
    for key in ("workspaceId", "identityScheme"):
        if expected[key] != observed[key]:
            problem(key.upper() + "_MISMATCH", "both")
    if expected["source"]["projectId"] != observed["source"]["projectId"]:
        problem("PROJECT_MISMATCH", "both")
    if (set(expected["scope"]["categories"]) != set(observed["scope"]["categories"]) or
            expected["scope"]["queryScope"] != observed["scope"]["queryScope"]):
        problem("SCOPE_MISMATCH", "both")
    maps = []
    summaries = {}
    for side, data in (("expected", expected), ("observed", observed)):
        for category in sorted(required_categories - set(data["scope"]["categories"])):
            gaps.append({"side": side, "code": "REQUIRED_CATEGORY_MISSING", "category": category})
        for flag in ("complete", "paginationComplete", "operationsComplete"):
            if not data["scope"][flag]:
                gaps.append({"side": side, "code": flag.upper() + "_INCOMPLETE"})
        record_map, op_map = {}, {}
        category_counts = {c: {"all": 0, "activeAcknowledged": 0, "pending": 0, "rejected": 0, "reversed": 0, "deleted": 0, "archived": 0} for c in sorted(data["scope"]["categories"])}
        for row in data["records"]:
            key = (row["workspaceId"], row["category"], row["id"])
            if row["workspaceId"] != data["workspaceId"]:
                problem("ROW_WORKSPACE_MISMATCH", side, key)
            if key in record_map:
                problem("DUPLICATE_RECORD", side, key)
            record_map[key] = row
            counts = category_counts[row["category"]]
            counts["all"] += 1
            if row["status"] == "ACTIVE" and not row["deleted"] and not row["archived"] and row["syncState"] == "ACKNOWLEDGED":
                counts["activeAcknowledged"] += 1
            counts["pending"] += row["syncState"] in {"PENDING", "IN_FLIGHT"}
            counts["rejected"] += row["syncState"] == "REJECTED"
            counts["reversed"] += row["status"] == "REVERSED"
            counts["deleted"] += row["deleted"] or row["status"] == "DELETED"
            counts["archived"] += row["archived"] or row["status"] == "ARCHIVED"
            if row["businessTime"] is None or row["status"] == "UNKNOWN" or row["syncState"] == "UNKNOWN":
                gaps.append({"side": side, "code": "UNRESOLVED_RECORD_METADATA", "identitySha256": fingerprint(key)})
        for op in data["operations"]:
            key = (op["workspaceId"], op["operationId"])
            if op["workspaceId"] != data["workspaceId"]:
                problem("OPERATION_WORKSPACE_MISMATCH", side, key)
            if key in op_map:
                problem("DUPLICATE_OPERATION", side, key)
            op_map[key] = dict(op, dependencies=sorted(op["dependencies"]))
            if op["protocolVersion"] != 2 or op["actionTime"] is None or op["state"] == "UNKNOWN":
                gaps.append({"side": side, "code": "LEGACY_OR_UNRESOLVED_OPERATION", "identitySha256": fingerprint(key)})
        for key, row in record_map.items():
            if row["syncState"] in {"PENDING", "IN_FLIGHT", "REJECTED"} and not any(
                op["workspaceId"] == row["workspaceId"] and op["category"] == row["category"] and
                op["targetId"] == row["id"] and op["state"] == row["syncState"] for op in op_map.values()
            ):
                problem("MISSING_DURABLE_OPERATION", side, key)
        maps.append((record_map, op_map))
        summaries[side] = {"categories": category_counts, "operationStates": dict(sorted(Counter(o["state"] for o in data["operations"]).items()))}
    for index, kind in ((0, "RECORD"), (1, "OPERATION")):
        left, right = maps[0][index], maps[1][index]
        for key in sorted(left.keys() - right.keys()):
            problem("MISSING_" + kind, "observed", key)
        for key in sorted(right.keys() - left.keys()):
            problem("UNEXPECTED_" + kind, "observed", key)
        for key in sorted(left.keys() & right.keys()):
            fields = [f for f in left[key] if left[key][f] != right[key][f]]
            if fields:
                problem(kind + "_STATE_MISMATCH", "both", key, fields)
    return {"schemaVersion": 1, "result": "FAIL" if issues else "INCOMPLETE" if gaps else "MATCH_WITHIN_DECLARED_SCOPE",
            "issues": issues, "gaps": gaps, "counts": summaries, "requiredCategories": sorted(required_categories),
            "meaning": "Local comparison only; does not verify source authenticity, production state, authorization, or financial amounts. Counts are overlapping diagnostics, not a financial ledger."}

def read_json(path, raw=None):
    def no_duplicates(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "JSON: duplicate object key")
            result[key] = value
        return result
    return json.loads((path.read_bytes() if raw is None else raw).decode("utf-8"), object_pairs_hook=no_duplicates)

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected", type=Path, required=True)
    parser.add_argument("--observed", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--required-category", action="append", choices=sorted(CATEGORIES), default=[],
                        help="Repeat for every category required by the approved reference scope")
    args = parser.parse_args(argv)
    if args.output and args.output.resolve() in {args.expected.resolve(), args.observed.resolve()}:
        parser.error("output must not overwrite an inventory")
    if args.output and args.output.exists():
        parser.error("output already exists; preserve prior evidence")
    try:
        expected_bytes, observed_bytes = args.expected.read_bytes(), args.observed.read_bytes()
        result = reconcile(read_json(args.expected, expected_bytes), read_json(args.observed, observed_bytes), args.required_category)
        result["inputSha256"] = {"expected": hashlib.sha256(expected_bytes).hexdigest(),
                                 "observed": hashlib.sha256(observed_bytes).hexdigest()}
        code = 0 if result["result"] == "MATCH_WITHIN_DECLARED_SCOPE" else 1
    except (InvalidInventory, ValueError, TypeError, KeyError, OSError):
        # Never echo untrusted file contents, customer names, paths or credentials.
        result, code = {"result": "INVALID_INPUT", "meaning": "Schema or file validation failed. Inspect the sanitized input offline; no source data was changed."}, 2
    output = json.dumps(result, sort_keys=True, indent=2) + "\n"
    if args.output:
        try:
            with args.output.open("x", encoding="utf-8") as stream:
                stream.write(output)
        except OSError:
            print("Output could not be created exclusively; existing evidence was not overwritten.", file=sys.stderr)
            return 2
    print(output, end="")
    return code

if __name__ == "__main__":
    sys.exit(main())
