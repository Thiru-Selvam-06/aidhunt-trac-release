import re

files = [
    'app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt',
    'app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt',
    'app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt'
]

for filepath in files:
    with open(filepath, 'r') as f:
        text = f.read()
    
    dialogs = re.finditer(r'AlertDialog\([^\)]+\)', text)
    print(f"File: {filepath}")
    for match in re.finditer(r'text = \{\s*(Column|LazyColumn)\(\s*modifier = ([^\,]+),', text):
        print("MATCH:", match.group(0))

