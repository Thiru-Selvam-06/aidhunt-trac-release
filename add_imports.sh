#!/bin/bash
add_imports() {
    file=$1
    # Check if we need to add trackFocusedField import
    if ! grep -q "import com.example.ui.utils.trackFocusedField" "$file"; then
        sed -i '/import androidx.compose/i import com.example.ui.utils.trackFocusedField' "$file"
    fi
    # Check BringIntoViewRequester
    if ! grep -q "import androidx.compose.foundation.relocation.BringIntoViewRequester" "$file"; then
        sed -i '/import androidx.compose/i import androidx.compose.foundation.relocation.BringIntoViewRequester' "$file"
    fi
    # Check rememberCoroutineScope
    if ! grep -q "import androidx.compose.runtime.rememberCoroutineScope" "$file"; then
        sed -i '/import androidx.compose/i import androidx.compose.runtime.rememberCoroutineScope' "$file"
    fi
}

add_imports app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
add_imports app/src/main/java/com/example/ui/screens/auth/FirstAccountSetupDialog.kt
add_imports app/src/main/java/com/example/ui/screens/auth/LoginScreen.kt
add_imports app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt
add_imports app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt
add_imports app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt

# specific window insets for expenses tab
if ! grep -q "import androidx.compose.foundation.layout.WindowInsets" app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt; then
    sed -i '/import androidx.compose/i import androidx.compose.foundation.layout.WindowInsets\nimport androidx.compose.foundation.layout.asPaddingValues\nimport androidx.compose.foundation.layout.ime\nimport androidx.compose.foundation.layout.imePadding' app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt
fi

