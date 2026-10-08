#!/bin/bash
fix_file() {
    file=$1
    sed -i 's/@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi)/@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)/g' "$file"
}

fix_file app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
fix_file app/src/main/java/com/example/ui/screens/auth/FirstAccountSetupDialog.kt
fix_file app/src/main/java/com/example/ui/screens/auth/LoginScreen.kt
fix_file app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt
fix_file app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt
fix_file app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt

