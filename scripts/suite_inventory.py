"""Fail when a test suite is silently omitted; generate a reproducible source inventory."""
import argparse, json, re, subprocess, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def instrumentation(root):
    found = []
    for p in sorted((root/"app/src/androidTest").rglob("*Test.kt")):
        body = p.read_text(encoding="utf-8")
        package = re.search(r"(?m)^\s*package\s+([\w.]+)", body)
        if not package: raise ValueError("Missing package: "+str(p))
        found.append(package[1]+"."+p.stem)
    return found
def inspect(root=ROOT):
    plan = json.loads((root/"scripts/instrumentation-plan.json").read_text())
    selected, blocked = plan["runnable"], plan["blocked"]
    actual = set(instrumentation(root))
    declared = set(selected) | set(blocked)
    errors = []
    if actual != declared: errors.append("Instrumentation plan drift: missing="+str(sorted(actual-declared))+" stale="+str(sorted(declared-actual)))
    if len(selected)!=len(set(selected)) or set(selected)&set(blocked): errors.append("Duplicate instrumentation declarations")
    if any(not str(reason).strip() for reason in blocked.values()): errors.append("Blocked tests need a specific reason")
    # These tests require an emulator and are deliberately assigned to the Rules partition.
    expected = set()
    for folder, pattern in [("cloudflare","*.test.mjs"),("functions","*.test.js"),("security-tests","*.test.cjs")]:
        expected.update(p.relative_to(root).as_posix() for p in (root/folder).rglob(pattern) if "node_modules" not in p.parts)
    runner = (root/"scripts/run_backend_suite.mjs").read_text()
    declared_backend = re.findall(r"['\"]((?:cloudflare|functions|security-tests)/[^'\"]+\.test\.(?:mjs|js|cjs))['\"]", runner)
    if set(declared_backend)!=expected: errors.append("Backend plan drift: missing="+str(sorted(expected-set(declared_backend)))+" stale="+str(sorted(set(declared_backend)-expected)))
    if len(declared_backend)!=len(set(declared_backend)): errors.append("Backend suites overlap")
    unit = []
    for p in sorted((root/"app/src/test").rglob("*Test.kt")):
        body=p.read_text(encoding="utf-8")
        package=re.search(r"(?m)^\s*package\s+([\w.]+)",body)
        unit.append({"source":p.relative_to(root).as_posix(),"class":package[1]+"."+p.stem if package else None,
                     "partition":"compose" if "createComposeRule" in body else "database" if "Room." in body or "SupportSQLite" in body else "outbox" if re.match(r".*(Command|Offline|Acknowledgement|Deletion|Operation|Sync|Dependencies|Routing|Recovery|Protocol).*Test.kt",p.name) else "unit",
                     "assertionStatus":"SOURCE INVENTORY ONLY; consult executed JUnit"})
    return {"unitClasses":unit,"instrumentation":plan,"backendFiles":sorted(expected),"errors":errors,
            "allControlsVerified":False,"liveServicesVerified":False}
def main():
    parser=argparse.ArgumentParser();parser.add_argument("--check",action="store_true");parser.add_argument("--output",type=Path)
    args=parser.parse_args();result=inspect()
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"unitClasses":len(result["unitClasses"]),"backendFiles":len(result["backendFiles"]),
                      "instrumentationRunnable":len(result["instrumentation"]["runnable"]),"instrumentationBlocked":len(result["instrumentation"]["blocked"]),"errors":result["errors"]}))
    return int(bool(result["errors"]))
if __name__=="__main__": sys.exit(main())
