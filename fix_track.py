import re

with open('app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt', 'r') as f:
    text = f.read()

# We need to replace .trackFocusedField(...) and any block { ... } that follows it up to the end parenthesis, but it's easier to just match from ".trackFocusedField" to ".testTag" or "," or ")" depending on context.

lines = text.split('\n')
new_lines = []
skip = False
for line in lines:
    if '.trackFocusedField' in line and not 'import' in line:
        # Check what requester it has
        req = ''
        if 'customerNameRequester' in line or 'FocusedField.NAME' in line: req = 'customerNameRequester'
        elif 'customerPhoneRequester' in line or 'FocusedField.PHONE' in line: req = 'customerPhoneRequester'
        elif 'customerLocationRequester' in line or 'FocusedField.LOCATION' in line: req = 'customerLocationRequester'
        elif 'hoursRequester' in line or 'FocusedField.HOURS' in line: req = 'hoursRequester'
        elif 'minutesRequester' in line or 'FocusedField.MINUTES' in line: req = 'minutesRequester'
        elif 'hourlyRateRequester' in line or 'FocusedField.HOURLY_RATE' in line: req = 'hourlyRateRequester'
        elif 'workAmountRequester' in line or 'FocusedField.WORK_AMOUNT' in line: req = 'workAmountRequester'
        elif 'extraChargesRequester' in line or 'FocusedField.EXTRA_CHARGES' in line: req = 'extraChargesRequester'
        elif 'amountReceivedRequester' in line or 'FocusedField.AMOUNT_RECEIVED' in line: req = 'amountReceivedRequester'
        elif 'expenseAmountRequester' in line or 'FocusedField.EXPENSE_AMOUNT' in line: req = 'expenseAmountRequester'
        elif 'expenseDescRequester' in line or 'FocusedField.EXPENSE_DESC' in line: req = 'expenseDescRequester'
        elif 'notesRequester' in line or 'FocusedField.NOTES' in line: req = 'notesRequester'
        
        # We need to look ahead to see what the next lines are, because it spans multiple lines.
        # Actually, let's just do a regex replace on the entire text block.
        pass

