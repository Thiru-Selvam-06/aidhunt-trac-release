"""Pure, one-time maintenance planner. Never changes authentication or ledger amounts."""
import re
import unicodedata

def value(doc, key):
    return doc.get("fields", {}).get(key, {}).get("stringValue", "")

def normalized(name):
    return " ".join(unicodedata.normalize("NFC", name).casefold().split())

def plan(snapshot):
    workspace = snapshot["workspaceId"]
    prefix = "projects/trac-aidhunt/databases/(default)/documents/workspaces/" + workspace
    if snapshot["projectId"] != "trac-aidhunt":
        raise ValueError("Wrong project")
    members = {}
    for member in snapshot["members"]:
        if not member["name"].startswith(prefix + "/members/"):
            raise ValueError("Foreign member")
        if value(member, "status").upper() != "ACTIVE" or value(member, "role").upper() not in {"OWNER", "PARTNER"}:
            continue
        uid = member["name"].rsplit("/", 1)[1]
        name = normalized(value(member, "displayName"))
        if name:
            members.setdefault(name, []).append(uid)
    result, seen = [], set()
    for doc in snapshot["entries"]:
        record_id = doc["name"].rsplit("/", 1)[1]
        if not doc["name"].startswith(prefix + "/entries/") or value(doc, "workspaceId") != workspace:
            raise ValueError("Foreign Entry")
        if not re.fullmatch(r"[1-9][0-9]*", record_id) or int(record_id) > 2**63-1 or record_id in seen:
            raise ValueError("Invalid or duplicate exact Entry ID")
        seen.add(record_id)
        typed_id = doc["fields"].get("id", {})
        if str(typed_id.get("integerValue", typed_id.get("stringValue", ""))) != record_id:
            raise ValueError("Document/record ID mismatch")
        if not doc.get("updateTime"):
            raise ValueError("Version precondition required")
        item = {"id": record_id, "name": doc["name"], "updateTime": doc["updateTime"], "patch": {}}
        if value(doc, "entryForUid") or value(doc, "financialAttributionUid"):
            item["decision"] = "PRESERVE_EXISTING"
        elif any(doc["fields"].get(k, {}).get("booleanValue") is True for k in ("isDeleted", "archived")):
            item["decision"] = "PRESERVE_NONACTIVE"
        else:
            matches = members.get(normalized(value(doc, "operatorName")), [])
            if len(matches) == 1:
                item["decision"] = "MATCHED_ENTRY_FOR"
                item["patch"] = {"entryForUid": matches[0], "financialAttributionUid": matches[0]}
            else:
                item["decision"] = "UNRESOLVED_NAME"
        result.append(item)
    return result
