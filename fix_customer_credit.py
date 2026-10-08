import re

with open('app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt', 'r') as f:
    text = f.read()

# Add missing imports if needed
if 'import androidx.compose.foundation.rememberScrollState' not in text:
    text = text.replace('import androidx.compose.foundation.layout.fillMaxSize', 'import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.layout.fillMaxSize')

replacement = """text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),"""

text = text.replace("""text = {
            Column(
                modifier = Modifier.fillMaxWidth(),""", replacement)

with open('app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt', 'w') as f:
    f.write(text)
