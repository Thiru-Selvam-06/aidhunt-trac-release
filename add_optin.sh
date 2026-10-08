#!/bin/bash
add_optin() {
    file=$1
    if ! grep -q "@file:OptIn(ExperimentalFoundationApi::class)" "$file"; then
        sed -i 's/package com.example.ui.*/&\n\n@file:OptIn(ExperimentalFoundationApi::class)\nimport androidx.compose.foundation.ExperimentalFoundationApi/g' "$file"
    fi
}

add_optin app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
add_optin app/src/main/java/com/example/ui/screens/auth/FirstAccountSetupDialog.kt
add_optin app/src/main/java/com/example/ui/screens/auth/LoginScreen.kt
add_optin app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt
add_optin app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt
add_optin app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt

