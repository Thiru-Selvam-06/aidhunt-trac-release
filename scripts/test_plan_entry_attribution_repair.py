import copy
import unittest
from plan_entry_attribution_repair import plan


class EntryAttributionRepairTest(unittest.TestCase):
    def fixture(self):
        return {"projectId": "trac-aidhunt", "workspaceId": "synthetic-workspace",
                "ownerUid": "owner", "ownerIdentityVerified": True, "sourceSha256": "a" * 64, "complete": True,
                "paginationComplete": True, "operationsComplete": True,
                "members": [{"workspaceId": "synthetic-workspace", "uid": uid,
                             "role": role, "status": "ACTIVE", "identityVerified": True}
                            for uid, role in [("owner", "OWNER"), ("partner", "PARTNER")]],
                "operations": [], "entries": [{"workspaceId": "synthetic-workspace",
                    "id": "9007199254740995", "updateTime": "2026-10-18T00:00:00Z",
                    "isDeleted": False, "createdByUid": "partner", "entryForUid": "",
                    "financialAttributionUid": "", "actionTime": 1000, "createdAt": 500,
                    "operationId": "original-operation", "totalAmount": 120,
                    "amountReceived": 20, "pendingAmount": 100}]}

    def run_plan(self, data):
        return plan(data, workspace_id="synthetic-workspace", owner_uid="owner")

    def test_uncertain_creator_uses_approved_owner_fallback_without_mutation(self):
        data = self.fixture()
        original = copy.deepcopy(data)
        item = self.run_plan(data)["items"][0]
        self.assertEqual(item["decision"], "APPROVED_OWNER_FALLBACK")
        self.assertEqual(item["patch"], {"entryForUid": "owner", "financialAttributionUid": "owner"})
        self.assertEqual(item["id"], "9007199254740995")
        self.assertEqual(data, original)

    def test_verified_partner_creator_does_not_override_owner_policy(self):
        data = self.fixture()
        data["entries"][0]["creatorIdentityVerified"] = True
        item = self.run_plan(data)["items"][0]
        self.assertEqual(item["decision"], "APPROVED_OWNER_FALLBACK")
        self.assertEqual(set(item["patch"].values()), {"owner"})

    def test_display_name_or_unknown_uid_never_proves_creator(self):
        for creator in ["Partner Name", "unknown"]:
            data = self.fixture()
            data["entries"][0].update(createdByUid=creator, creatorIdentityVerified=True)
            self.assertEqual(self.run_plan(data)["items"][0]["decision"], "APPROVED_OWNER_FALLBACK")

    def test_existing_partial_and_conflicting_assignments_are_retained(self):
        for target, attribution in [("partner", "partner"), ("", "unknown"),
                                    ("partner", ""), ("partner", "owner")]:
            data = self.fixture()
            data["entries"][0].update(entryForUid=target, financialAttributionUid=attribution)
            self.assertEqual(self.run_plan(data)["items"][0]["patch"], {})

    def test_03_existing_entry_for_uid_preserved_unchanged(self):
        data = self.fixture()
        data["entries"][0].update(entryForUid="partner", financialAttributionUid="")
        item = self.run_plan(data)["items"][0]
        self.assertEqual(item["decision"], "RETAIN_EXISTING_ASSIGNMENT")
        self.assertEqual(item["existingAttribution"], {"entryForUid": "partner", "financialAttributionUid": ""})
        self.assertEqual(item["patch"], {})

    def test_04_existing_financial_attribution_uid_preserved_unchanged(self):
        data = self.fixture()
        data["entries"][0].update(entryForUid="", financialAttributionUid="partner")
        item = self.run_plan(data)["items"][0]
        self.assertEqual(item["decision"], "RETAIN_EXISTING_ASSIGNMENT")
        self.assertEqual(item["existingAttribution"], {"entryForUid": "", "financialAttributionUid": "partner"})
        self.assertEqual(item["patch"], {})

    def test_05_either_field_populated_not_treated_as_genuinely_unattributed(self):
        for entry_for, fin_attr in [("partner", ""), ("", "owner"), ("partner", "owner")]:
            data = self.fixture()
            data["entries"][0].update(entryForUid=entry_for, financialAttributionUid=fin_attr)
            item = self.run_plan(data)["items"][0]
            self.assertEqual(item["decision"], "RETAIN_EXISTING_ASSIGNMENT")
            self.assertEqual(item["patch"], {})

    def test_pending_rejected_invalidated_commands_block_repair(self):
        for state in ["PENDING", "IN_FLIGHT", "REJECTED", "INVALIDATED"]:
            data = self.fixture()
            data["operations"] = [{"workspaceId": "synthetic-workspace",
                "targetId": "9007199254740995", "state": state}]
            self.assertEqual(self.run_plan(data)["items"][0]["decision"], "BLOCKED_OUTBOX")

    def test_deleted_archived_unknown_records_retained(self):
        for changes in [{"isDeleted": True}, {"isDeleted": None}, {"archived": True}]:
            data = self.fixture()
            data["entries"][0].update(changes)
            self.assertEqual(self.run_plan(data)["items"][0]["patch"], {})

    def test_foreign_workspace_rejected_at_each_layer(self):
        for group in ["entries", "members", "operations"]:
            data = self.fixture()
            if group == "operations":
                data[group] = [{"state": "PENDING", "targetId": "9007199254740995"}]
            data[group][0]["workspaceId"] = "foreign"
            with self.assertRaises(ValueError):
                self.run_plan(data)

    def test_invalid_owner_evidence_is_unresolved_and_project_mismatch_rejected(self):
        for change in ["ownerUid", "inactive", "missing", "wrongRole"]:
            data = self.fixture()
            if change == "ownerUid": data["ownerUid"] = "other"
            if change == "inactive": data["members"][0]["status"] = "REMOVED"
            if change == "wrongRole": data["members"][0]["role"] = "PARTNER"
            if change == "missing": data["members"] = data["members"][1:]
            item = self.run_plan(data)["items"][0]
            self.assertEqual(item["decision"], "UNRESOLVED_OWNER")
            self.assertEqual(item["patch"], {})
        data = self.fixture()
        data["projectId"] = "other"
        with self.assertRaises(ValueError): self.run_plan(data)

    def test_unverified_owner_produces_no_proposals(self):
        for field in ["ownerIdentityVerified", "membership"]:
            data = self.fixture()
            if field == "membership":
                data["members"][0]["identityVerified"] = False
            else:
                data[field] = False
            result = self.run_plan(data)
            self.assertEqual(result["items"][0]["decision"], "UNRESOLVED_OWNER")
            self.assertEqual(result["items"][0]["patch"], {})

    def test_malformed_financial_metadata_is_excluded(self):
        for changes in [{"totalAmount": -1}, {"amountReceived": 999},
                        {"pendingAmount": "unknown"}, {"createdAt": None}]:
            data = self.fixture()
            data["entries"][0].update(changes)
            self.assertEqual(self.run_plan(data)["items"][0]["decision"], "EXCLUDED_MALFORMED")
            self.assertEqual(self.run_plan(data)["items"][0]["patch"], {})

    def test_review_proposal_includes_scope_originals_and_queue_evidence(self):
        data = self.fixture()
        item = self.run_plan(data)["items"][0]
        self.assertEqual(item["workspaceId"], "synthetic-workspace")
        self.assertEqual(item["existingAttribution"], {"entryForUid": "", "financialAttributionUid": ""})
        self.assertEqual(item["reviewStatus"], "AWAITING_INDEPENDENT_REVIEW")
        self.assertEqual(item["operationStatus"], "NO_UNRESOLVED_TARGET_OPERATION")
        self.assertEqual(item["ownerEvidence"]["sourceSha256"], data["sourceSha256"])

    def test_incomplete_capture_is_rejected(self):
        for field in ["complete", "paginationComplete", "operationsComplete"]:
            data = self.fixture()
            data[field] = False
            with self.assertRaises(ValueError):
                self.run_plan(data)

    def test_duplicate_and_numeric_ids_rejected(self):
        data = self.fixture()
        data["entries"].append(copy.deepcopy(data["entries"][0]))
        with self.assertRaises(ValueError):
            self.run_plan(data)
        for invalid in [9007199254740995, "9223372036854775808", "0"]:
            data = self.fixture()
            data["entries"][0]["id"] = invalid
            with self.assertRaises(ValueError):
                self.run_plan(data)

    def test_repeat_proposal_is_identical_and_patch_cannot_rewrite_ledger(self):
        data = self.fixture()
        first = self.run_plan(data)
        self.assertEqual(first, self.run_plan(data))
        self.assertEqual(set(first["items"][0]["patch"]), {"entryForUid", "financialAttributionUid"})
        self.assertTrue(first["requiresTrustedRepair"])
        self.assertEqual(first["mode"], "PROPOSAL_ONLY")
        changed = copy.deepcopy(data)
        changed["entries"][0]["pendingAmount"] = 99
        self.assertNotEqual(first["items"][0]["beforeSha256"],
                            self.run_plan(changed)["items"][0]["beforeSha256"])


if __name__ == "__main__":
    unittest.main()
