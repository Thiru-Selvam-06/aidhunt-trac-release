import re

with open('app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt', 'r') as f:
    text = f.read()

replacement = """LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .imePadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 100.dp
        ),"""

text = text.replace("""LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB)),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 100.dp + WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        ),""", replacement)

with open('app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt', 'w') as f:
    f.write(text)

