"""Prevent backend-only template values becoming generated Android constants."""
import re
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

class AndroidEnvironmentBoundaryTest(unittest.TestCase):
    def test_backend_settings_are_ignored_by_secrets_plugin(self):
        source = (ROOT / "app/build.gradle.kts").read_text()
        settings = ("GCLOUD_PROJECT", "FIRESTORE_EMULATOR_HOST",
                    "FIREBASE_AUTH_EMULATOR_HOST", "FUNCTIONS_EMULATOR",
                    "INVITATION_SMS_ENABLED", "INVITATION_TEST_PHONES")
        block = re.search(r"listOf\((.*?)\)\.forEach \{ ignoreList.add\(it\) \}", source, re.S)
        self.assertIsNotNone(block)
        for setting in settings:
            self.assertIn('"' + setting + '"', block.group(1))
        for path in (ROOT / "app/src").rglob("*.kt"):
            for setting in settings:
                self.assertNotIn("BuildConfig." + setting, path.read_text(encoding="utf-8-sig"),
                                 str(path))
if __name__ == "__main__":
    unittest.main()