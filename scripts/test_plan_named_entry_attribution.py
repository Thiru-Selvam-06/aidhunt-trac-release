import copy
import unittest
from plan_named_entry_attribution import plan

class NamedAttributionTest(unittest.TestCase):
    def fixture(self):
        base = "projects/trac-aidhunt/databases/(default)/documents/workspaces/test"
        def fields(**kw): return {k: {"stringValue": v} for k,v in kw.items()}
        return {"projectId":"trac-aidhunt", "workspaceId":"test",
          "members":[{"name":base+"/members/partner","fields":fields(displayName="Partner Name",role="PARTNER",status="ACTIVE")}],
          "entries":[{"name":base+"/entries/9007199254740995","updateTime":"version",
              "fields":dict(fields(workspaceId="test",operatorName="Partner Name",createdByUid="owner"),
                            id={"integerValue":"9007199254740995"},actionTime={"integerValue":"123"})}]}
    def test_named_beneficiary_not_creator_and_exact_id(self):
        s=self.fixture(); original=copy.deepcopy(s); r=plan(s)[0]
        self.assertEqual(r["patch"],{"entryForUid":"partner","financialAttributionUid":"partner"})
        self.assertEqual(r["id"],"9007199254740995"); self.assertEqual(s,original)
    def test_normalized_whitespace_and_case(self):
        s=self.fixture(); s["entries"][0]["fields"]["operatorName"]["stringValue"]=" PARTNER  name "
        self.assertEqual(plan(s)[0]["decision"],"MATCHED_ENTRY_FOR")
    def test_ambiguous_name_is_not_assigned(self):
        s=self.fixture(); m=copy.deepcopy(s["members"][0]); m["name"]+="2"; s["members"].append(m)
        self.assertEqual(plan(s)[0]["patch"],{})
    def test_unknown_name_is_not_owner_fallback(self):
        s=self.fixture(); s["entries"][0]["fields"]["operatorName"]["stringValue"]="unknown"
        self.assertEqual(plan(s)[0]["decision"],"UNRESOLVED_NAME")
    def test_removed_and_operator_members_not_assigned(self):
        for key,val in [("status","REMOVED"),("role","OPERATOR")]:
            s=self.fixture(); s["members"][0]["fields"][key]["stringValue"]=val
            self.assertEqual(plan(s)[0]["patch"],{})
    def test_existing_or_partial_assignment_preserved(self):
        for key in ["entryForUid","financialAttributionUid"]:
            s=self.fixture(); s["entries"][0]["fields"][key]={"stringValue":"existing"}
            self.assertEqual(plan(s)[0]["decision"],"PRESERVE_EXISTING")
    def test_deleted_preserved(self):
        s=self.fixture(); s["entries"][0]["fields"]["isDeleted"]={"booleanValue":True}
        self.assertEqual(plan(s)[0]["decision"],"PRESERVE_NONACTIVE")
    def test_workspace_and_id_and_version_rejected(self):
        for field,val in [("workspaceId",{"stringValue":"other"}),("id",{"integerValue":"12"})]:
            s=self.fixture(); s["entries"][0]["fields"][field]=val
            with self.assertRaises(ValueError): plan(s)
        s=self.fixture(); del s["entries"][0]["updateTime"]
        with self.assertRaises(ValueError): plan(s)
    def test_duplicate_records_rejected(self):
        s=self.fixture(); s["entries"]*=2
        with self.assertRaises(ValueError): plan(s)
    def test_repeat_plan_preserves_repaired_assignment(self):
        s=self.fixture(); first=plan(s)[0]
        for k,v in first["patch"].items(): s["entries"][0]["fields"][k]={"stringValue":v}
        before=copy.deepcopy(s)
        self.assertEqual(plan(s)[0]["decision"],"PRESERVE_EXISTING")
        self.assertEqual(plan(s)[0]["patch"],{})
        self.assertEqual(s,before)
if __name__ == "__main__": unittest.main()
