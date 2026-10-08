#!/bin/bash
# name text field
sed -i 's/modifier = Modifier.fillMaxWidth(),/modifier = Modifier.fillMaxWidth().trackFocusedField(nameRequester, coroutineScope),/g' app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
