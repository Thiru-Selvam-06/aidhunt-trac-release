import copy
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("inventory", Path(__file__).with_name("reconcile_workspace_inventory.py"))
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)

def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()

def fixture():
    return {"schemaVersion": 1, "workspaceId": "synthetic-pms", "identityScheme": "opaque-v1",
        "source": {"label": "synthetic-fixture", "environment": "SYNTHETIC", "projectId": "demo-aidhunt-v16", "artifactSha256": digest("synthetic-source")},
        "snapshotTime": "2026-10-18T12:00:00+05:30",
        "scope": {"categories": ["ENTRY", "COLLECTION"], "queryScope": "all rows including retained audit and local states; no date filter", "complete": True, "paginationComplete": True, "operationsComplete": True},
        "records": [{"workspaceId": "synthetic-pms", "category": "ENTRY", "id": "9007199254740995", "status": "ACTIVE", "businessTime": 1700000000000, "operationTime": 1700000000001, "deleted": False, "archived": False, "syncState": "ACKNOWLEDGED", "attributionSha256": digest("creator/payer/beneficiary") }],
        "operations": [{"operationId": "operation-original", "workspaceId": "synthetic-pms", "actorSha256": digest("actor"), "category": "ENTRY", "targetId": "9007199254740995", "mutationType": "CREATE", "protocolVersion": 2, "actionTime": 1700000000001, "payloadSha256": digest("original payload"), "dependencies": [], "state": "ACKNOWLEDGED"}]}

class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.a = fixture()
        self.b = copy.deepcopy(self.a)
    def compare(self):
        return v.reconcile(self.a, self.b)
    def codes(self):
        return {x["code"] for x in self.compare()["issues"]}
    def test_identical_large_exact_ids_match_without_mutation(self):
        original = copy.deepcopy(self.a)
        self.assertEqual("MATCH_WITHIN_DECLARED_SCOPE", self.compare()["result"])
        self.assertEqual(original, self.a)
        self.assertEqual(original, self.b)
    def test_equal_counts_do_not_hide_different_ids(self):
        self.b["records"][0]["id"] = "9007199254740994"
        self.assertEqual({"MISSING_RECORD", "UNEXPECTED_RECORD"}, self.codes())
    def test_missing_and_unexpected_categories_cannot_cancel(self):
        self.b["records"][0]["category"] = "COLLECTION"
        self.assertIn("MISSING_RECORD", self.codes())
        self.assertIn("UNEXPECTED_RECORD", self.codes())
    def test_duplicate_records_are_not_deduplicated_into_pass(self):
        self.b["records"].append(copy.deepcopy(self.b["records"][0]))
        self.assertIn("DUPLICATE_RECORD", self.codes())
    def test_foreign_workspace_row_rejected(self):
        self.b["records"][0]["workspaceId"] = "another-workspace"
        self.assertIn("ROW_WORKSPACE_MISMATCH", self.codes())
    def test_workspace_header_mismatch(self):
        self.b["workspaceId"] = "another-workspace"
        self.assertIn("WORKSPACEID_MISMATCH", self.codes())
    def test_project_mismatch(self):
        self.b["source"]["projectId"] = "another-demo"
        self.assertIn("PROJECT_MISMATCH", self.codes())
    def test_query_scope_mismatch(self):
        self.b["scope"]["queryScope"] = "today only"
        self.assertIn("SCOPE_MISMATCH", self.codes())
    def test_category_scope_mismatch(self):
        self.b["scope"]["categories"].append("EXPENSE")
        self.assertIn("SCOPE_MISMATCH", self.codes())
    def test_status_and_original_times_are_compared(self):
        for field, value in [("status", "DELETED"), ("businessTime", 1700000000100), ("operationTime", 1700000000200), ("attributionSha256", digest("wrong-payer")), ("deleted", True), ("archived", True), ("syncState", "REJECTED")]:
            with self.subTest(field=field):
                self.b = copy.deepcopy(self.a)
                self.b["records"][0][field] = value
                issues=[x for x in self.compare()["issues"] if x["code"] == "RECORD_STATE_MISMATCH"]
                self.assertEqual(1, len(issues))
                self.assertEqual("RECORD_STATE_MISMATCH", issues[0]["code"])
                self.assertEqual([field], issues[0]["fields"])
    def test_incomplete_inventory_never_passes(self):
        for flag in ("complete", "paginationComplete", "operationsComplete"):
            with self.subTest(flag=flag):
                self.b = copy.deepcopy(self.a)
                self.b["scope"][flag] = False
                self.assertEqual("INCOMPLETE", self.compare()["result"])
    def test_unknown_metadata_retained_not_fabricated(self):
        self.a["records"][0]["businessTime"] = None
        self.b = copy.deepcopy(self.a)
        self.assertEqual("INCOMPLETE", self.compare()["result"])
        self.assertIsNone(self.b["records"][0]["businessTime"])
    def test_numeric_id_float_id_and_boolean_time_rejected(self):
        for field,value in [("id",9007199254740995),("id",9007199254740995.0),("businessTime",True),("businessTime",2**63)]:
            with self.subTest(field=field,value=value):
                self.b=copy.deepcopy(self.a); self.b["records"][0][field]=value
                with self.assertRaises(v.InvalidInventory): self.compare()
    def test_pending_rejected_reversed_deleted_and_archived_are_separate(self):
        for i,state in enumerate(["PENDING","IN_FLIGHT","REJECTED"]):
            row=copy.deepcopy(self.a["records"][0]);row.update(id=str(i),syncState=state)
            self.a["records"].append(row)
            op=copy.deepcopy(self.a["operations"][0]);op.update(operationId="operation-"+str(i),targetId=str(i),state=state)
            self.a["operations"].append(op)
        for i,state in enumerate(["REVERSED","DELETED","ARCHIVED"],10):
            row=copy.deepcopy(self.a["records"][0]);row.update(id=str(i),category="COLLECTION",status=state)
            self.a["records"].append(row)
        self.b=copy.deepcopy(self.a)
        counts=self.compare()["counts"]["observed"]["categories"]
        self.assertEqual(1,counts["ENTRY"]["activeAcknowledged"])
        self.assertEqual(2,counts["ENTRY"]["pending"])
        self.assertEqual(1,counts["ENTRY"]["rejected"])
        self.assertEqual(0,counts["COLLECTION"]["activeAcknowledged"])
        for key in ("reversed","deleted","archived"):self.assertEqual(1,counts["COLLECTION"][key])
    def test_pending_row_without_outbox_cannot_be_a_complete_match(self):
        self.a["records"][0]["syncState"]="PENDING"
        self.a["operations"]=[]
        self.b=copy.deepcopy(self.a)
        self.assertEqual("FAIL",self.compare()["result"])
        self.assertIn("MISSING_DURABLE_OPERATION",self.codes())
    def test_missing_operation_retains_failure(self):
        self.b["operations"]=[]
        self.assertIn("MISSING_OPERATION",self.codes())
    def test_operation_identity_timestamp_actor_payload_and_state_compared(self):
        for field,value in [("actionTime",1700000000010),("actorSha256",digest("other")),("payloadSha256",digest("rewritten")),("targetId","9007199254740994"),("state","REJECTED")]:
            with self.subTest(field=field):
                self.b=copy.deepcopy(self.a);self.b["operations"][0][field]=value
                issues=self.compare()["issues"]
                self.assertEqual("OPERATION_STATE_MISMATCH",issues[0]["code"])
                self.assertEqual([field],issues[0]["fields"])
    def test_duplicate_retry_record_is_detected_not_silently_counted(self):
        self.b["operations"].append(copy.deepcopy(self.b["operations"][0]))
        self.assertIn("DUPLICATE_OPERATION",self.codes())
    def test_operation_cannot_redirect_to_another_workspace(self):
        self.b["operations"][0]["workspaceId"]="other"
        self.assertIn("OPERATION_WORKSPACE_MISMATCH",self.codes())
    def test_legacy_operations_stay_unresolved(self):
        self.a["operations"][0].update(actionTime=None,protocolVersion=1)
        self.b=copy.deepcopy(self.a)
        self.assertEqual("INCOMPLETE",self.compare()["result"])
        self.assertIsNone(self.b["operations"][0]["actionTime"])
    def test_dependency_order_does_not_create_false_mismatch(self):
        self.a["operations"][0]["dependencies"]=["op-a","op-b"]
        self.b["operations"][0]["dependencies"]=["op-b","op-a"]
        self.assertEqual("MATCH_WITHIN_DECLARED_SCOPE",self.compare()["result"])
    def test_changed_dependency_is_detected(self):
        self.b["operations"][0]["dependencies"]=["different-op"]
        self.assertIn("OPERATION_STATE_MISMATCH",self.codes())
    def test_reports_contain_only_fingerprints_not_raw_identifiers(self):
        self.b["records"][0]["id"]="private-record-name"
        report=json.dumps(self.compare())
        for secret in ["private-record-name","9007199254740995","operation-original","synthetic-pms"]:
            self.assertNotIn(secret,report)
    def test_input_field_allowlist_rejects_customer_details(self):
        self.b["records"][0]["customerPhone"]="secret"
        with self.assertRaises(v.InvalidInventory):self.compare()
    def test_timezone_required(self):
        self.b["snapshotTime"]="2026-10-18T12:00:00"
        with self.assertRaises(v.InvalidInventory):self.compare()
    def test_hashed_scheme_requires_consistent_digest_id(self):
        self.a["identityScheme"]="sha256-v1"
        self.a["records"][0]["id"]=digest("entry")
        self.a["operations"][0]["targetId"]=digest("entry")
        self.b=copy.deepcopy(self.a)
        self.assertEqual("MATCH_WITHIN_DECLARED_SCOPE",self.compare()["result"])
    def test_cli_exit_codes_and_read_only_inputs(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);a=root/"a.json";b=root/"b.json"
            a.write_text(json.dumps(self.a),encoding="utf-8");b.write_text(json.dumps(self.b),encoding="utf-8")
            before=a.read_bytes()
            for expected in (0,1,2):
                if expected==1:self.b["records"]=[]
                if expected==2:self.b["records"]=[{"secret":"DO_NOT_LOG"}]
                b.write_text(json.dumps(self.b),encoding="utf-8");buf=io.StringIO()
                with contextlib.redirect_stdout(buf):code=v.main(["--expected",str(a),"--observed",str(b)])
                self.assertEqual(expected,code);self.assertNotIn("DO_NOT_LOG",buf.getvalue())
            self.assertEqual(before,a.read_bytes())
    def test_cli_does_not_overwrite_inputs_or_prior_results(self):
        with tempfile.TemporaryDirectory() as temp:
            a=Path(temp)/"a.json";a.write_text(json.dumps(self.a),encoding="utf-8");before=a.read_bytes()
            with contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):
                v.main(["--expected",str(a),"--observed",str(a),"--output",str(a)])
            out=Path(temp)/"previous.json";out.write_text("preserve",encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):
                v.main(["--expected",str(a),"--observed",str(a),"--output",str(out)])
            self.assertEqual(before,a.read_bytes());self.assertEqual("preserve",out.read_text(encoding="utf-8"))
    def test_provenance_hash_uses_exact_bytes_parsed_without_rereading(self):
        raw=json.dumps(self.a).encode()
        buf=io.StringIO()
        with patch.object(v.Path,"read_bytes",side_effect=[raw,raw]) as read,contextlib.redirect_stdout(buf):
            self.assertEqual(0,v.main(["--expected","fixture-a","--observed","fixture-b"]))
        self.assertEqual(2,read.call_count)
        self.assertEqual(hashlib.sha256(raw).hexdigest(),json.loads(buf.getvalue())["inputSha256"]["expected"])
    def test_output_uses_exclusive_creation_even_if_another_writer_races(self):
        raw=json.dumps(self.a).encode()
        with patch.object(v.Path,"read_bytes",side_effect=[raw,raw]),patch.object(v.Path,"exists",return_value=False),patch.object(v.Path,"open",side_effect=FileExistsError) as opened,contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2,v.main(["--expected","fixture-a","--observed","fixture-b","--output","new-result"]))
        self.assertEqual("x",opened.call_args.args[0])
    def test_required_missing_category_cannot_be_hidden_by_two_partial_scopes(self):
        result=v.reconcile(self.a,self.b,required_categories=["ENTRY","COLLECTION","EXPENSE"])
        self.assertEqual("INCOMPLETE",result["result"])
        self.assertEqual([("expected","EXPENSE"),("observed","EXPENSE")],[(x["side"],x["category"]) for x in result["gaps"] if x["code"]=="REQUIRED_CATEGORY_MISSING"])
    def test_explicit_empty_category_can_satisfy_provided_complete_scope(self):
        for data in (self.a,self.b):data["scope"]["categories"].append("EXPENSE")
        result=v.reconcile(self.a,self.b,required_categories=["EXPENSE"])
        self.assertEqual("MATCH_WITHIN_DECLARED_SCOPE",result["result"])
        self.assertEqual(0,result["counts"]["observed"]["categories"]["EXPENSE"]["all"])
    def test_invalid_required_category_is_not_ignored(self):
        with self.assertRaises(v.InvalidInventory):v.reconcile(self.a,self.b,required_categories=["UNKNOWN"])
    def test_later_observation_is_not_a_financial_cutoff(self):
        before=copy.deepcopy(self.a)
        self.b["snapshotTime"]="2026-10-19T12:00:00+05:30"
        self.assertEqual("MATCH_WITHIN_DECLARED_SCOPE",self.compare()["result"])
        self.assertEqual(before["records"],self.b["records"])
        self.assertEqual(before["operations"],self.b["operations"])
    def test_repeated_reference_cli_keeps_every_input_byte_and_no_new_rows(self):
        with tempfile.TemporaryDirectory() as temp:
            a,b=Path(temp)/"source.json",Path(temp)/"local.json"
            a.write_text(json.dumps(self.a),encoding="utf-8");b.write_text(json.dumps(self.b),encoding="utf-8")
            before=(a.read_bytes(),b.read_bytes())
            for i in range(2):
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(0,v.main(["--expected",str(a),"--observed",str(b),"--output",str(Path(temp)/f"result-{i}.json")]))
                self.assertEqual(before,(a.read_bytes(),b.read_bytes()))
            self.assertEqual(1,len(v.read_json(a)["records"]))
            self.assertEqual(1,len(v.read_json(a)["operations"]))
    def test_required_category_cli_is_explicit_and_incomplete(self):
        with tempfile.TemporaryDirectory() as temp:
            a=Path(temp)/"source.json";a.write_text(json.dumps(self.a),encoding="utf-8");buffer=io.StringIO()
            with contextlib.redirect_stdout(buffer):
                self.assertEqual(1,v.main(["--expected",str(a),"--observed",str(a),"--required-category","EXPENSE"]))
            self.assertEqual("INCOMPLETE",json.loads(buffer.getvalue())["result"])
    def test_duplicate_json_object_key_is_invalid(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/"a.json";p.write_text('{"schemaVersion":1,"schemaVersion":1}',encoding="utf-8")
            with self.assertRaises(v.InvalidInventory):v.read_json(p)

if __name__ == "__main__":unittest.main()
