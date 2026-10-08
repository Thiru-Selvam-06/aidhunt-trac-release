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
    
    # Replace any missing newlines before 'import ' or 'package '
    text = re.sub(r'([^\n])(import )', r'\1\n\2', text)
    text = re.sub(r'([^\n])(package )', r'\1\n\2', text)
    
    # Also fix @file:OptIn which is all messed up
    text = re.sub(r'@file:OptIn\([^)]+\)', '@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)\n', text)
    text = text.replace('::class::class', '::class')
    
    with open(filepath, 'w') as f:
        f.write(text)

