import importlib.util, tempfile, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("inventory",Path(__file__).with_name("interaction_inventory.py"))
inventory=importlib.util.module_from_spec(spec);spec.loader.exec_module(inventory)
class InventoryTest(unittest.TestCase):
    def test_callbacks_and_modifier_actions_remain_explicitly_unverified(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            path=root/"app/src/main/java/com/example/ui/screens/Fixture.kt"
            path.parent.mkdir(parents=True)
            path.write_text('fun Example() { Button(\n onClick = { save() })\n Box(Modifier.clickable { open() })\n TextField(onValueChange = { v -> change(v) }) }',encoding="utf-8")
            rows=inventory.discover(root)
            self.assertEqual(["onClick","clickable","onValueChange"],[r["callback"] for r in rows])
            self.assertEqual(3,len({r["control_id"] for r in rows}))
            self.assertTrue(all(r["device_status"]=="NOT VERIFIED" for r in rows))
    def test_test_reference_does_not_imply_a_control_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            path=root/"app/src/main/java/com/example/ui/screens/Fixture.kt"
            path.parent.mkdir(parents=True)
            path.write_text("fun Example() { Button(onClick = {}) }",encoding="utf-8")
            test=root/"app/src/test/ExampleTest.kt";test.parent.mkdir(parents=True);test.write_text("Example()",encoding="utf-8")
            row=inventory.discover(root)[0]
            self.assertIn("ExampleTest.kt",row["related_tests"])
            self.assertTrue(row["automated_status"].startswith("NOT VERIFIED"))
if __name__=="__main__": unittest.main()
