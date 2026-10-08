#!/bin/bash
fix_file() {
    file=$1
    # Remove all `@file:OptIn(ExperimentalFoundationApi::class)` and `import androidx.compose.foundation.ExperimentalFoundationApi` that we added at the top
    sed -i '/@file:OptIn(ExperimentalFoundationApi::class)/d' "$file"
    
    # Add it at the very top of the file
    sed -i '1i@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi)' "$file"
}

fix_file app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
fix_file app/src/main/java/com/example/ui/screens/auth/FirstAccountSetupDialog.kt
fix_file app/src/main/java/com/example/ui/screens/auth/LoginScreen.kt
fix_file app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt
fix_file app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt
fix_file app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt

