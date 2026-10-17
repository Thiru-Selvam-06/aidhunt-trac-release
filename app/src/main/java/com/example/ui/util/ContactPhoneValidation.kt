package com.example.ui.util

import com.example.ui.components.isValidPhoneNumber
import com.example.ui.components.sanitizePhoneNumberForStorage

/** Contact data is optional; authentication and phone invitations are separate operations. */
fun isOptionalContactPhoneValid(phone: String?): Boolean =
    phone.isNullOrBlank() || isValidPhoneNumber(phone)

fun isInvitationPhoneValid(phone: String): Boolean =
    phone.isNotBlank() && sanitizePhoneNumberForStorage(phone).length == 10

fun requireOptionalContactPhone(phone: String): String {
    require(isOptionalContactPhoneValid(phone)) { "Please enter a complete phone number" }
    return sanitizePhoneNumberForStorage(phone)
}
