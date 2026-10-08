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
    
    text = text.replace('package ', '\npackage ')
    text = text.replace('import ', '\nimport ')
    
    # fix the @file:OptIn block
    text = re.sub(r'@file:OptIn\([^\n]*\)', '@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)', text)
    
    # collapse multiple newlines at top
    text = re.sub(r'\n{3,}', '\n\n', text)
    
    with open(filepath, 'w') as f:
        f.write(text.strip() + '\n')

