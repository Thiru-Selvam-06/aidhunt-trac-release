import re

with open('app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt', 'r') as f:
    text = f.read()

# We replace:
# .trackFocusedField(customerNameRequester, coroutineScope) {
#    currentlyFocusedField = FocusedField.NONE
# }
# }
# )
# With just: .trackFocusedField(customerNameRequester, coroutineScope)

def replace_track(match):
    req = match.group(1)
    return f'.trackFocusedField({req}, coroutineScope)'

pattern1 = r'\.trackFocusedField\(([a-zA-Z]+),\scoroutineScope\)\s*\{[^}]+\}\s*\}\s*\)'
text = re.sub(pattern1, replace_track, text)

# For the others that are like:
# .trackFocusedField(
#     field = FocusedField.HOURLY_RATE,
#     requester = hourlyRateRequester,
#     coroutineScope = coroutineScope,
#     onFocused = { currentlyFocusedField = FocusedField.HOURLY_RATE },
#     onFocusLost = {
#         if (currentlyFocusedField == FocusedField.HOURLY_RATE) {
#             currentlyFocusedField = FocusedField.NONE
#         }
#     }
# )
pattern2 = r'\.trackFocusedField\(\s*field\s*=\s*[a-zA-Z\.]+\s*,\s*requester\s*=\s*([a-zA-Z]+)\s*,\s*coroutineScope\s*=\s*coroutineScope\s*,\s*onFocused\s*=[^}]+\}\s*,\s*onFocusLost\s*=\s*\{\s*if[^}]+\}\s*[^}]+\}\s*\)'
text = re.sub(pattern2, replace_track, text)

pattern3 = r'\.trackFocusedField\(([a-zA-Z]+),\scoroutineScope\)\s*\{[^}]+\}\s*\}\s*,\s*shape'
def replace_track_shape(match):
    req = match.group(1)
    return f'.trackFocusedField({req}, coroutineScope),\n                                shape'
text = re.sub(pattern3, replace_track_shape, text)

# Just in case, replace:
# .trackFocusedField(notesRequester, coroutineScope) {
#    currentlyFocusedField = FocusedField.NONE
# }
# }
# ),
pattern4 = r'\.trackFocusedField\(([a-zA-Z]+),\scoroutineScope\)\s*\{[^}]+\}\s*\}\s*\),'
def replace_track_comma(match):
    req = match.group(1)
    return f'.trackFocusedField({req}, coroutineScope),'
text = re.sub(pattern4, replace_track_comma, text)

with open('app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt', 'w') as f:
    f.write(text)

