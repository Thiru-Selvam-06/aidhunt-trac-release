package com.example

import com.example.data.auth.ArchivedRecordAuthorization
import com.example.data.firebase.WorkspaceMember
import org.junit.Assert.*
import org.junit.Test

class ArchivedRecordAuthorizationTest {
    @Test fun archiveFreezesCreatorOrBeneficiaryOnlyInsideItsWorkspace() {
        val removed = WorkspaceMember(workspaceId = "a", uid = "removed", role = "PARTNER", status = "REMOVED")
        assertTrue(ArchivedRecordAuthorization.isReadOnly("a", "removed", "owner", listOf(removed)))
        assertTrue(ArchivedRecordAuthorization.isReadOnly("a", "owner", "removed", listOf(removed)))
        assertFalse(ArchivedRecordAuthorization.isReadOnly("b", "owner", "removed", listOf(removed)))
        assertFalse(ArchivedRecordAuthorization.isReadOnly("a", "owner", "other", listOf(removed)))
        assertFalse(ArchivedRecordAuthorization.isReadOnly("a", "owner", "removed", listOf(removed.copy(status = "ACTIVE"))))
        assertFalse(ArchivedRecordAuthorization.isReadOnly("a", "", "", listOf(removed.copy(uid = ""))))
    }
}
