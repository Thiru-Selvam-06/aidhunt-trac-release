package com.example.ui.util

/** Editable stored values retain their exact decimal text; opening a form is not a financial edit. */
fun storedMoneyInput(value: Double): String = if (value.isFinite())
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else ""
