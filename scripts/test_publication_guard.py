import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("publication_guard",Path(__file__).with_name("publication_guard.py"))
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
class PublicationGuardTest(unittest.TestCase):
    def test_credentials_and_private_snapshots_are_not_publishable(self):
        for path in [".env","release.jks","debug.keystore.base64","docs/test-artifacts/private.json","qodex/README.md","app/google-services.json"]:
            self.assertTrue(v.issues(path,b"{}"),path)
    def test_long_key_and_tokens_fail_without_echoing_their_values(self):
        key=b"-----BEGIN PRIVATE KEY-----\n"+b"A"*140+b"\n-----END PRIVATE KEY-----"
        for value in [key,b"cfat_"+b"x"*40,b"ghp_"+b"x"*40,b"1//"+b"x"*50]:
            reasons=v.issues("fixture.txt",value);self.assertTrue(reasons);self.assertNotIn(value.decode(),str(reasons))
    def test_templates_source_and_synthetic_config_are_allowed(self):
        for path in [".env.example","firestore.rules","docs/guide.txt","app/src/deviceTest/google-services.json"]:
            self.assertEqual([],v.issues(path,b'{"placeholder":"not-a-secret"}'))
if __name__=="__main__":unittest.main()
