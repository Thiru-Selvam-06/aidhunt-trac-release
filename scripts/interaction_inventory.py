"""Source-indexed interaction candidates; never converts source presence to a passing UI test."""
import argparse, csv, hashlib, json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CALLBACK = re.compile(r"\b(on[A-Z]\w*)\s*=|\.(clickable|combinedClickable|toggleable|selectable)\s*(?:\(|\{)")
FUNCTION = re.compile(r"\bfun\s+(\w+)\s*\(")
# A callback is a candidate, including callbacks forwarded to child components.
# Runtime-generated controls need a manual destination/role traversal as well.
def discover(root=ROOT):
    rows=[]
    sources=[root/"app/src/main/java/com/example/ui", root/"app/src/main/java/com/example/MainActivity.kt"]
    files=sorted({p for source in sources for p in ([source] if source.is_file() else source.rglob("*.kt"))})
    tests=list((root/"app/src/test").rglob("*.kt"))+list((root/"app/src/androidTest").rglob("*.kt"))
    test_text={str(p.relative_to(root)):p.read_text(encoding="utf-8") for p in tests}
    for path in files:
        text=path.read_text(encoding="utf-8")
        functions=list(FUNCTION.finditer(text))
        occurrences={}
        for match in CALLBACK.finditer(text):
            enclosing=next((f.group(1) for f in reversed(functions) if f.start()<match.start()),path.stem)
            callback=match.group(1) or match.group(2)
            key=(enclosing,callback)
            occurrences[key]=occurrences.get(key,0)+1
            source=path.relative_to(root).as_posix()
            identifier=f"{source}::{enclosing}::{callback}::{occurrences[key]}"
            related=[name for name,body in test_text.items() if re.search(r"\b"+re.escape(enclosing)+r"\s*\(",body)]
            rows.append(dict(control_id=identifier,source=source,line=text.count("\n",0,match.start())+1,
                component=enclosing,callback=callback,
                expected_result="Follow callback and verify validation, persistence, acknowledgement and reopened UI",
                role_workspace="NOT VERIFIED: traverse OWNER/PARTNER/OPERATOR in authorized and foreign workspaces",
                room_outbox="NOT VERIFIED: record exact DAO transaction, envelope and dependencies for this action",
                backend="NOT VERIFIED: identify endpoint/Firestore path; navigation-only actions may be N/A after review",
                authorization="NOT VERIFIED: UI visibility plus direct repository and backend denial",
                states="NOT VERIFIED: online/offline/loading/empty/error/retry/cancel/restart",
                related_tests=";".join(related),
                automated_status="NOT VERIFIED (test reference is not proof of control coverage)",
                manual_procedure=f"Open {path.stem}; exercise {enclosing}.{callback}, cancel, repeat and reopen for each role",
                manual_blocker="Runtime device traversal and persistence evidence not collected for this control",
                device_status="NOT VERIFIED"))
    return rows

def assets(root=ROOT):
    groups={
      "screens": "app/src/main/java/com/example/ui/screens/**/*.kt",
      "viewmodels": "app/src/main/java/com/example/ui/viewmodel/*.kt",
      "repositories": "app/src/main/java/com/example/data/repository/*.kt",
      "daos": "app/src/main/java/com/example/data/dao/*.kt",
      "entities": "app/src/main/java/com/example/data/entity/*.kt",
      "unit_tests": "app/src/test/**/*.kt",
      "instrumentation_tests": "app/src/androidTest/**/*.kt",
      "backend_tests": "functions/**/*.test.js",
      "worker_tests": "cloudflare/*.test.mjs",
      "rules_tests": "security-tests/*.test.cjs",
      "workflows": ".github/workflows/*",
    }
    return {key:sorted(p.relative_to(root).as_posix() for p in root.glob(glob)) for key,glob in groups.items()}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--check",action="store_true")
    args=parser.parse_args()
    output=ROOT/"docs/regression"
    rows=discover()
    manifest=output/"interaction-inventory.json"
    if args.check:
        saved=json.loads(manifest.read_text(encoding="utf-8"))
        # Only control identity is checked; evidence must be reviewed independently.
        if [r["control_id"] for r in saved] != [r["control_id"] for r in rows]:
            sys.exit("Interaction inventory drift: regenerate and review every added/removed callback")
        print(f"Inventory identities match: {len(rows)} candidates; this is not a UI pass result")
        return
    output.mkdir(parents=True,exist_ok=True)
    manifest.write_text(json.dumps(rows,indent=2)+"\n",encoding="utf-8")
    with (output/"interaction-inventory.csv").open("w",encoding="utf-8",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]))
        writer.writeheader();writer.writerows(rows)
    (output/"asset-inventory.json").write_text(json.dumps(assets(),indent=2)+"\n",encoding="utf-8")
    print(f"Indexed {len(rows)} callback/control candidates; runtime verification remains explicit")
if __name__=="__main__": main()
