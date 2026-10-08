import re

files = [
    'app/src/main/java/com/example/ui/screens/account/AccountScreen.kt',
    'app/src/main/java/com/example/ui/screens/auth/FirstAccountSetupDialog.kt',
    'app/src/main/java/com/example/ui/screens/auth/LoginScreen.kt',
    'app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt',
    'app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt',
    'app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt'
]

for filepath in files:
    with open(filepath, 'r') as f:
        text = f.read()
    
    # replace `@file:OptIn(...)package` with `@file:OptIn(...)\npackage`
    text = re.sub(r'(@file:OptIn\([^)]+\))package ', r'\1\npackage ', text)
    
    # Also if there's `package ...import` fix it
    text = re.sub(r'(package [a-zA-Z0-9_\.]+)(import )', r'\1\n\n\2', text)
    
    with open(filepath, 'w') as f:
        f.write(text)
