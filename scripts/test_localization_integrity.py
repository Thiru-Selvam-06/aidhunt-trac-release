"""Reject damaged app labels before packaging; never inspect or rewrite customer data."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
# UTF-8 Tamil decoded as Windows-1252, damaged rupees/punctuation, and replacement glyphs.
MOJIBAKE = re.compile(
    r"\u00e0[\u00ae\u00af]|\u00e2[\u201a\u20ac\u201d]|"
    r"[\ufffd\u0080-\u009f]|\\u[fF]{3}[dD]"
)
QUESTION_LABEL = re.compile(r'"\?{2,}(?:\s+\?+)*"')


def app_sources():
    return sorted(p for p in (ROOT / "app/src/main").rglob("*")
                  if p.suffix in {".kt", ".java", ".xml"})


class LocalizationIntegrityTest(unittest.TestCase):
    def test_app_sources_are_utf8_without_damaged_characters(self):
        errors = []
        for path in app_sources():
            for number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
                if MOJIBAKE.search(line):
                    errors.append(f"{path.relative_to(ROOT)}:{number}")
        self.assertEqual([], errors, "Damaged Unicode in app source: " + ", ".join(errors))

    def test_labels_are_not_question_mark_placeholders(self):
        errors = []
        for path in app_sources():
            for number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
                if QUESTION_LABEL.search(line):
                    errors.append(f"{path.relative_to(ROOT)}:{number}")
        self.assertEqual([], errors, "Lost label text: " + ", ".join(errors))


if __name__ == "__main__":
    unittest.main()
