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
    
    text = text.replace('import ', 'import ')
    
    # We have things like `accountimport` and `Apiimport` and `Contextimport`
    # Let's just find any lower case or letter followed by `import `
    text = re.sub(r'([a-zA-Z])import ', r'\1\nimport ', text)
    text = re.sub(r'([a-zA-Z])package ', r'\1\npackage ', text)
    text = re.sub(r'(@file:OptIn\([^)]+\))(package )', r'\1\n\2', text)
    
    with open(filepath, 'w') as f:
        f.write(text.strip() + '\n')

