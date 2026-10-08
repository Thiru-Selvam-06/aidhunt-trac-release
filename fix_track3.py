import re

with open('app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt', 'r') as f:
    text = f.read()

pattern = r'\.trackFocusedField\(\s*field\s*=\s*[a-zA-Z0-9_\.]+\s*,\s*requester\s*=\s*([a-zA-Z]+)\s*,\s*coroutineScope\s*=\s*coroutineScope\s*,\s*onFocused\s*=\s*\{\s*currentlyFocusedField\s*=\s*[a-zA-Z0-9_\.]+\s*\}\s*,\s*onFocusLost\s*=\s*\{\s*if\s*\(\s*currentlyFocusedField\s*==\s*[a-zA-Z0-9_\.]+\s*\)\s*\{\s*currentlyFocusedField\s*=\s*[a-zA-Z0-9_\.]+\s*\}\s*\}\s*\)'

text = re.sub(pattern, r'.trackFocusedField(\1, coroutineScope)', text, flags=re.MULTILINE|re.DOTALL)

with open('app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt', 'w') as f:
    f.write(text)
