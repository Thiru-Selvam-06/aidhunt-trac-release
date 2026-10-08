import re

with open('app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt', 'r') as f:
    text = f.read()

replacement = """LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 100.dp
        ),"""

text = text.replace("""LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 16.dp + WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        ),""", replacement)

with open('app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt', 'w') as f:
    f.write(text)

