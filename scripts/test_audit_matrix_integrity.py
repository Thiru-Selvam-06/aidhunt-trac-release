import base64
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("matrix_validator", Path(__file__).with_name("audit_matrix_integrity.py"))
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)

class IntegrityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "docs").mkdir()
        self.audit = self.root / "docs/architecture-compliance-audit.md"
        self.matrix = self.root / "docs/architecture-v16-requirement-matrix.md"
        self.audit.write_text("### R-01 — Real requirement\n\n**Requirement:** Real behavior.\n", encoding="utf-8")
        self.rows = ["| R-01 | ref | Real behavior | source | evidence | NOT VERIFIED |"]
        self.write()

    def write(self, summary_count=1):
        summary = "\n".join(f"- **{s}:** {summary_count if s == 'NOT VERIFIED' else 0} items" for s in sorted(v.ALLOWED_STATUSES))
        self.matrix.write_text(summary + f"\nTotal Requirements: **{summary_count} items**\n## 2. Register\n| ID | Reference | Requirement | Anchor | Evidence | Status |\n|---|---|---|---|---|---|\n" + "\n".join(self.rows), encoding="utf-8")

    def errors(self): return v.inspect(self.root)["errors"]
    def rejects(self, fragment): self.assertTrue(any(fragment in x for x in self.errors()), self.errors())
    def test_valid_fixture(self): self.assertEqual([], self.errors())
    def test_omitted_requirement(self):
        self.rows = []; self.write(); self.rejects("Missing matrix IDs: ['R-01']")
    def test_missing_middle_id_reports_exact_set_not_assumed_tail(self):
        self.audit.write_text("".join(f"### R-0{i} — Requirement\n\n**Requirement:** Behavior {i}.\n" for i in range(1,4)),encoding="utf-8")
        self.rows.append(self.rows[0].replace('R-01','R-03'));self.write(2)
        self.assertEqual(['R-02'],v.inspect(self.root)['missing_ids'])
        self.rejects("Missing matrix IDs: ['R-02']")

    def test_invented_requirement(self):
        self.rows[0] = self.rows[0].replace('R-01','R-02'); self.write(); self.rejects("Extra matrix IDs: ['R-02']")
    def test_duplicate_matrix(self):
        self.rows *= 2; self.write(); self.rejects('Duplicate matrix IDs')
    def test_duplicate_source_not_hidden_by_dictionary(self):
        self.audit.write_text(self.audit.read_text(encoding="utf-8") * 2, encoding="utf-8"); self.rejects('Duplicate source IDs')
    def test_missing_source_requirement_field(self):
        self.audit.write_text('### R-01 — Requirement\nno field\n',encoding="utf-8"); self.rejects('missing Requirement field')
    def test_indented_rows_are_parsed(self):
        self.rows[0]='   '+self.rows[0];self.write();self.assertEqual([],self.errors())
    def test_malformed_row_cannot_silently_disappear(self):
        self.rows[0]=self.rows[0][2:];self.write();self.rejects('missing leading pipe')
    def test_extra_columns_rejected(self):
        self.rows[0]+=' extra |';self.write();self.rejects('expected six fields')
    def test_invalid_status(self):
        self.rows[0]=self.rows[0].replace('NOT VERIFIED','PASS');self.write();self.rejects('invalid status')
    def test_summary_mismatch(self): self.write(2);self.rejects('Summary mismatch')
    def test_zero_count_summary_must_exist(self):
        s=self.matrix.read_text(encoding='utf-8');self.matrix.write_text(s.replace('- **FAILED:** 0 items',''),encoding='utf-8');self.rejects("Summary must declare 'FAILED'")
    def test_missing_file(self):
        self.rows[0]=self.rows[0].replace('source','[source](../missing.kt:1)');self.write();self.rejects('missing or outside-repository')
    def test_invalid_target_line(self):
        (self.root/'code.kt').write_text('one\n',encoding='utf-8');self.rows[0]=self.rows[0].replace('source','[source](../code.kt:9)');self.write();self.rejects('line out of range')
    def test_missing_assertion(self):
        self.rows[0]=self.rows[0].replace('evidence','`MissingTest.invented`').replace('NOT VERIFIED','VERIFIED — Automated Test');self.write();self.rejects('nonexistent test assertion')
    def test_corrupt_image(self):
        images=self.root/'docs/test-artifacts/emulator-acceptance-2026-10-17';images.mkdir(parents=True)
        (images/'fake.png').write_text('PNG text corruption');self.rows[0]=self.rows[0].replace('evidence','`fake.png`');self.write();self.rejects('invalid PNG')
    def test_valid_png_structure(self):
        p=self.root/'one.png';p.write_bytes(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='));self.assertTrue(v.valid_png(p))
    def test_cli_invalid_fixture_returns_nonzero(self):
        self.rows=[];self.write();r=subprocess.run([sys.executable,str(Path(v.__file__)), '--root',str(self.root)],capture_output=True);self.assertEqual(1,r.returncode)
    def test_cli_valid_fixture_returns_zero(self):
        r=subprocess.run([sys.executable,str(Path(v.__file__)), '--root',str(self.root)],capture_output=True);self.assertEqual(0,r.returncode)

if __name__ == '__main__': unittest.main()
