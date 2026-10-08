#!/bin/bash
# Adding trackFocusedField to PartnerFormDialog
sed -i 's/var hasAttemptedSubmit by remember { mutableStateOf(false) }/var hasAttemptedSubmit by remember { mutableStateOf(false) }\n    val coroutineScope = rememberCoroutineScope()\n    val nameRequester = remember { BringIntoViewRequester() }\n    val phoneRequester = remember { BringIntoViewRequester() }/g' app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
