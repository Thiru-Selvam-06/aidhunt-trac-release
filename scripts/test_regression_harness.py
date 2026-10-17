import json, os, shutil, subprocess, sys, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class EvidenceHarnessTest(unittest.TestCase):
    def aggregate(self,temp,jobs):
        return subprocess.run([sys.executable,str(ROOT/"scripts/aggregate_regression.py")],cwd=temp,
            env={**os.environ,"JOB_RESULTS":json.dumps(jobs)},capture_output=True)
    def test_aggregation_fails_when_required_junit_is_missing(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertNotEqual(0,self.aggregate(temp,{"tests":{"result":"success"}}).returncode)
    def test_job_failure_is_not_hidden_by_passing_junit(self):
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp)/"collected-reports";folder.mkdir()
            (folder/"results.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
            self.assertNotEqual(0,self.aggregate(temp,{"tests":{"result":"failure"}}).returncode)
    def test_aggregation_records_counts_without_claiming_device_or_live_verification(self):
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp)/"collected-reports";folder.mkdir()
            (folder/"results.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="1"/>')
            self.assertEqual(0,self.aggregate(temp,{"tests":{"result":"success"}}).returncode)
            data=json.loads((Path(temp)/"ci-reports/regression-summary.json").read_text())
            self.assertEqual(2,data["totals"]["tests"])
            self.assertEqual(1,data["totals"]["skipped"])
            self.assertEqual("NOT VERIFIED",data["physicalDevice"])
    def test_ci_rules_configuration_never_escapes_project_root(self):
        config=json.loads((ROOT/"firebase.ci.json").read_text())
        path=(ROOT/config["firestore"]["rules"]).resolve()
        self.assertTrue(path.is_relative_to(ROOT))
        self.assertTrue(path.is_file())
        self.assertEqual("127.0.0.1",config["emulators"]["firestore"]["host"])
    def test_new_operation_entities_require_explicit_role_matrix_expectations(self):
        import re
        source=(ROOT/"app/src/main/java/com/example/data/sync/OperationPolicy.kt").read_text(encoding="utf-8")
        matrix=(ROOT/"app/src/test/java/com/example/OperationRoleMatrixTest.kt").read_text(encoding="utf-8")
        cases=re.findall(r'^\s*((?:"[A-Z_]+"\s*,?\s*)+)->',source,re.M)
        entities={entity for case in cases for entity in re.findall(r'"([A-Z_]+)"',case)}
        expected=set(re.findall(r'"([A-Z_]+)" to setOf',matrix))
        self.assertTrue(entities)
        self.assertEqual(expected,entities)
    def test_backend_runner_refuses_live_project_and_credentials(self):
        node=shutil.which("node") or shutil.which("node.exe")
        self.assertIsNotNone(node,"Node required to verify the fail-closed runner")
        base={**os.environ,"GCLOUD_PROJECT":"demo-aidhunt-v16","FIRESTORE_EMULATOR_HOST":"127.0.0.1:18180"}
        for overrides in ({"GCLOUD_PROJECT":"trac-aidhunt"},{"FIRESTORE_EMULATOR_HOST":"firestore.googleapis.com:443"},{"GOOGLE_APPLICATION_CREDENTIALS":"do-not-read.json"}):
            result=subprocess.run([node,str(ROOT/"scripts/run_backend_suite.mjs"),"backend"],env={**base,**overrides},capture_output=True)
            self.assertEqual(2,result.returncode)
            self.assertIn(b"REFUSED",result.stderr)
if __name__=="__main__": unittest.main()
