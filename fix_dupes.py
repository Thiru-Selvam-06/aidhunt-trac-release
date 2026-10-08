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
        lines = f.readlines()
    
    unique_imports = set()
    new_lines = []
    
    for line in lines:
        if line.startswith('import '):
            if line not in unique_imports:
                unique_imports.add(line)
                new_lines.append(line)
        else:
            new_lines.append(line)
            
    with open(filepath, 'w') as f:
        f.writelines(new_lines)
