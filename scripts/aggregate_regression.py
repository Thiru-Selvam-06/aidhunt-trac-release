import hashlib,json,os,sys,xml.etree.ElementTree as ET
from pathlib import Path
out=Path("ci-reports");out.mkdir(exist_ok=True)
jobs=json.loads(os.environ.get("JOB_RESULTS","{}"))
results=[]
seen=set()
artifacts_with_tests=set()
required=set(json.loads(os.environ.get("REQUIRED_JUNIT_ARTIFACTS","[]")))
for p in Path("collected-reports").rglob("*.xml"):
    try:
        root=ET.parse(p).getroot()
        cases=list(root.iter("testcase"))
        if cases:
            counts={"tests":len(cases),
                    "failures":sum(c.find("failure") is not None for c in cases),
                    "errors":sum(c.find("error") is not None for c in cases),
                    "skipped":sum(c.find("skipped") is not None for c in cases)}
        else:
            suites=[s for s in root.iter("testsuite") if not s.findall(".//testsuite")]
            counts={k:sum(int(float(s.get(k,0))) for s in suites) for k in ("tests","failures","errors","skipped")}
        if counts["tests"]>0:
            artifacts_with_tests.add(p.relative_to("collected-reports").parts[0])
        digest=hashlib.sha256(p.read_bytes()).hexdigest()
        if digest in seen: continue
        seen.add(digest)
        results.append({"artifact":str(p),"suite":root.get("name"),**counts})
    except (ET.ParseError,ValueError):
        results.append({"artifact":str(p),"unreadable":True})
summary={"sha":os.environ.get("GITHUB_SHA"),"runId":os.environ.get("GITHUB_RUN_ID"),
 "workflow":os.environ.get("GITHUB_WORKFLOW"),"jobs":jobs,"junit":results,
 "missingJunitArtifacts":sorted(required-artifacts_with_tests),
 "totals":{k:sum(r.get(k,0) for r in results) for k in ("tests","failures","errors","skipped")},
 "physicalDevice":"NOT VERIFIED","liveServices":"NOT VERIFIED",
 "coverage":"Per-partition reports are separate artifacts; no combined coverage percentage is claimed"}
(out/"regression-summary.json").write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
text="# Regression gate\n\nCommit: "+str(summary["sha"])+"\n\n"+json.dumps(summary["totals"])+"\n"
text+="\nPhysical-device and live-service verification: NOT VERIFIED. Code coverage: see separate per-partition artifacts; no combined percentage.\n"
(out/"summary.md").write_text(text,encoding="utf-8")
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"],"a",encoding="utf-8") as f: f.write(text)
if required-artifacts_with_tests or not jobs or any(v.get("result")!="success" for v in jobs.values()) or not results or any(r.get("unreadable") or r.get("failures") or r.get("errors") for r in results):
    sys.exit("Regression gate failed or required test evidence is missing")
