import re
with open('app/src/main/java/com/example/ui/screens/auth/FirstAccountSetupDialog.kt', 'r') as f:
    text = f.read()

for match in re.finditer(r'AlertDialog\([^\)]+\)', text):
    pass
print("Done")
