import json,os,re,subprocess,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class ScorecardIntegrityTest(unittest.TestCase):
    def run_report(self,junit=None):
        temp=tempfile.TemporaryDirectory();self.addCleanup(temp.cleanup)
        root=Path(temp.name)
        script=ROOT/"scripts/generate_ci_report.py"
        for source in re.findall(r'shutil.copy\("([^"]+)"',script.read_text(encoding="utf-8")):
            target=root/source;target.parent.mkdir(parents=True,exist_ok=True);target.write_text("{}")
        if junit:
            folder=root/"app/build/test-results/testDebugUnitTest";folder.mkdir(parents=True)
            (folder/"TEST-fixture.xml").write_text(junit)
        result=subprocess.run([sys.executable,str(script)],cwd=root,env={**os.environ,"GITHUB_RUN_ID":"fixture","PYTHONUTF8":"1"},
            stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        self.assertEqual(0,result.returncode,result.stderr.decode(errors="replace"))
        report=root/"ci-reports/fixture"
        return json.loads((report/"run-metadata.json").read_text()),json.loads((report/"summary.json").read_text()),(report/"README.md").read_text()
    def test_missing_junit_cannot_claim_critical_cases_passed(self):
        metadata,cases,readme=self.run_report()
        self.assertEqual("NOT_VERIFIED",metadata["overall_result"])
        self.assertEqual(0,metadata["critical_tests_passed"])
        self.assertTrue(all(c["result"]=="NOT_VERIFIED" for c in cases))
        self.assertNotIn("No gaps were detected",readme)
    def test_similarly_named_unrelated_test_is_not_critical_case_evidence(self):
        metadata,cases,_=self.run_report('<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="com.example.CompleteRegressionPipelineTest" name="testWorkspaceIsolationUnrelated"/></testsuite>')
        self.assertEqual(1,metadata["passed"])
        self.assertEqual(0,metadata["critical_tests_passed"])
        self.assertTrue(all(c["result"]=="NOT_VERIFIED" for c in cases))
    def test_exact_failure_is_reported_as_failure_with_remaining_gaps(self):
        metadata,cases,_=self.run_report('<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase classname="com.example.CompleteRegressionPipelineTest" name="testWorkspaceIsolation"><failure message="Synthetic failure"/></testcase></testsuite>')
        self.assertEqual("FAIL",metadata["overall_result"])
        self.assertGreater(metadata["critical_tests_failed"],0)
        self.assertTrue(any(c["result"]=="NOT_VERIFIED" for c in cases))
    def test_skipped_critical_case_is_neither_pass_nor_failure(self):
        metadata,cases,_=self.run_report('<testsuite tests="1" failures="0" errors="0" skipped="1"><testcase classname="com.example.CompleteRegressionPipelineTest" name="testWorkspaceIsolation"><skipped/></testcase></testsuite>')
        self.assertEqual("NOT_VERIFIED",metadata["overall_result"])
        self.assertEqual(0,metadata["critical_tests_passed"])
        self.assertEqual(0,metadata["critical_tests_failed"])
        self.assertGreater(metadata["critical_tests_skipped"],0)
if __name__=="__main__":unittest.main()

