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
    
    # Remove any @file:OptIn at the start
    text = re.sub(r'^@file:OptIn[^\n]*\n', '', text)
    
    # Prepend it properly
    new_top = "@file:OptIn(ExperimentalFoundationApi::class)\n\n"
    
    # Find package and insert after it the import
    # But wait, it's easier to just do:
    text = new_top + text
    
    # Ensure import is present
    if "import androidx.compose.foundation.ExperimentalFoundationApi" not in text:
        text = text.replace('package com.example.ui.screens', 'package com.example.ui.screens\n\nimport androidx.compose.foundation.ExperimentalFoundationApi')
        
    with open(filepath, 'w') as f:
        f.write(text)

