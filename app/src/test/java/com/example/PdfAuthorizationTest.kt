package com.example

import com.example.data.auth.PdfExportAuthorization
import org.junit.Assert.*
import org.junit.Test

class PdfAuthorizationTest {
    @Test fun ownerAndPartnerCanExportOnlyTheirVerifiedWorkspace() {
        for (role in listOf("OWNER", "PARTNER")) {
            assertTrue(PdfExportAuthorization.permits("user", "user", "a", "a", role, "ACTIVE", listOf("a", "a")))
            assertFalse(PdfExportAuthorization.permits("user", "user", "a", "b", role, "ACTIVE", listOf("b")))
            assertFalse(PdfExportAuthorization.permits("user", "user", "a", "a", role, "ACTIVE", listOf("a", "b")))
        }
    }
    @Test fun staleAccountRemovedMemberAndOperatorCannotExport() {
        assertFalse(PdfExportAuthorization.permits("next-user", "old-user", "a", "a", "OWNER", "ACTIVE"))
        assertFalse(PdfExportAuthorization.permits("user", "user", "a", "a", "OPERATOR", "ACTIVE"))
        assertFalse(PdfExportAuthorization.permits("user", "user", "a", "a", "PARTNER", "REMOVED"))
        assertFalse(PdfExportAuthorization.permits("", "", "a", "a", "OWNER", "ACTIVE"))
        assertFalse(PdfExportAuthorization.permits("user", "user", "a", "a", "Co-Owner", "ACTIVE"))
    }
}
