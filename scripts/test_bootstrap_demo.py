import importlib.util,json,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("bootstrap_demo",Path(__file__).with_name("bootstrap_demo.py"))
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
class BootstrapTest(unittest.TestCase):
    def test_idempotent_and_refuses_to_overwrite_other_config(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);folder=root/"app/src/deviceTest";folder.mkdir(parents=True)
            template={"project_info":{"project_id":"demo-aidhunt-v16"},"client":[{"client_info":{"android_client_info":{"package_name":"demo.devicetest"}}}]}
            (folder/"google-services.json").write_text(json.dumps(template))
            self.assertTrue(v.prepare(root));before=(root/"app/google-services.json").read_bytes()
            self.assertFalse(v.prepare(root));self.assertEqual(before,(root/"app/google-services.json").read_bytes())
            target=root/"app/google-services.json";target.write_text('{"project_info":{"project_id":"existing-project"}}')
            before=target.read_bytes()
            with self.assertRaises(ValueError):v.prepare(root)
            self.assertEqual(before,target.read_bytes())
if __name__=="__main__":unittest.main()
