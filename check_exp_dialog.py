import re
with open('app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt', 'r') as f:
    text = f.read()

for match in re.finditer(r'text = \{\s*(Column|LazyColumn)\(\s*modifier = ([^\,]+),', text):
    print("MATCH:", match.group(0))

