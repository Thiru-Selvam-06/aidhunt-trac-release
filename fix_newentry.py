import re

with open('app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt', 'r') as f:
    text = f.read()

# Add .imePadding() to the main LazyColumn
# we find `modifier = Modifier\n                .fillMaxSize()\n                .background(AppTheme.colors.background),`
replacement = """modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background)
                .imePadding(),"""
text = text.replace("""modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),""", replacement)

# We should also adjust the bottom padding of the LazyColumn so the submit button is reachable when the keyboard is open.
# WindowInsets.ime.asPaddingValues().calculateBottomPadding() isn't really needed if we use .imePadding(), we can just add a fixed padding like 120.dp
# wait, actually, imePadding() is applied to the modifier, so it will shift the whole LazyColumn up. 
# But wait, if it's a Scaffold, Scaffold doesn't automatically consume insets.
# The contentPadding bottom can just be `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp`. Let's just put `120.dp`.

text = re.sub(r'bottom = WindowInsets\.ime\.asPaddingValues\(\)\.calculateBottomPadding\(\)', 'bottom = 120.dp', text)

with open('app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt', 'w') as f:
    f.write(text)
