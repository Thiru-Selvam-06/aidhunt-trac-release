import importlib.util,json,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("suite_inventory",Path(__file__).with_name("suite_inventory.py"))
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
class SuiteInventoryTest(unittest.TestCase):
    def fixture(self,root):
        for folder in ["scripts","app/src/androidTest","app/src/test","cloudflare","functions","security-tests"]:
            (root/folder).mkdir(parents=True,exist_ok=True)
        (root/"app/src/androidTest/OneTest.kt").write_text("package synthetic\nclass OneTest")
        (root/"scripts/instrumentation-plan.json").write_text(json.dumps({"runnable":["synthetic.OneTest"],"blocked":{}}))
        (root/"scripts/run_backend_suite.mjs").write_text("")
    def test_new_device_test_cannot_be_silently_omitted(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root)
            (root/"app/src/androidTest/NewTest.kt").write_text("package synthetic\nclass NewTest")
            self.assertIn("synthetic.NewTest",str(v.inspect(root)["errors"]))
    def test_new_backend_suite_requires_assignment(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root)
            (root/"cloudflare/new.test.mjs").write_text("")
            self.assertIn("cloudflare/new.test.mjs",str(v.inspect(root)["errors"]))
    def test_explicit_blocker_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);self.fixture(root)
            (root/"scripts/instrumentation-plan.json").write_text(json.dumps({"runnable":[],"blocked":{"synthetic.OneTest":"Needs phone OTP fixture"}}))
            result=v.inspect(root);self.assertEqual([],result["errors"]);self.assertFalse(result["allControlsVerified"])
    def test_actual_source_plan_is_complete(self):
        self.assertEqual([],v.inspect()["errors"])
if __name__=="__main__":unittest.main()
