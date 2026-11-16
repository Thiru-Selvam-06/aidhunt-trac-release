package com.example

import com.example.data.firebase.OwnerActivityItem
import com.example.data.firebase.OwnerActivityPolicy
import org.junit.Assert.*
import org.junit.Test

class OwnerActivityPushTest {
    private val app = "com.aistudio.aidhunttrac.cedlir"
    private val event = "a".repeat(64)

    @Test fun push018EnglishAndTamilUseTheVerifiedActorOrAPlainFallback() {
        assertEquals("Perumal added a new job.", OwnerActivityItem(event, "JOB_CREATED", "Perumal").label(false))
        assertEquals("பெருமாள் புதிய வேலைப் பதிவைச் சேர்த்தார்.",
            OwnerActivityItem(event, "JOB_CREATED", "பெருமாள்").label(true))
        assertEquals("A partner reversed a collection.", OwnerActivityItem(event, "COLLECTION_REVERSED", "").label(false))
        assertNull(OwnerActivityItem(event, "BORROWING", "Partner").label(true))
        val supported = listOf("JOB_CREATED", "OLD_ENTRY_CREATED", "JOB_EDITED", "JOB_DELETED", "COLLECTION",
            "COLLECTION_REVERSED", "EXPENSE", "EXPENSE_EDITED", "EXPENSE_DELETED", "WITHDRAWAL")
        for (type in supported) {
            assertTrue(OwnerActivityItem(event, type, "").label(true)!!.startsWith("பங்குதாரர் "))
            assertTrue(OwnerActivityItem(event, type, "").label(false)!!.startsWith("A partner "))
        }
    }

    @Test fun push019And020OnlyExplicitControlledLiveTestAndRealReleasePackagesEnablePush() {
        assertTrue(OwnerActivityPolicy.enabled(true, "trac-aidhunt", app))
        assertTrue(OwnerActivityPolicy.enabled(true, "trac-aidhunt", "$app.livetest"))
        assertFalse(OwnerActivityPolicy.enabled(false, "trac-aidhunt", "$app.livetest"))
        assertFalse(OwnerActivityPolicy.enabled(false, "trac-aidhunt", app))
        assertFalse(OwnerActivityPolicy.enabled(true, "trac-aidhunt", "$app.physicaltest"))
        assertFalse(OwnerActivityPolicy.enabled(true, "demo-aidhunt-v16", "$app.devicetest"))
        assertFalse(OwnerActivityPolicy.enabled(true, "demo-aidhunt-v16", app))
        assertFalse(OwnerActivityPolicy.enabled(true, null, app))
    }

    @Test fun push016And021AuthorizedDestinationRequiresUidWorkspaceAndOriginalOwnerRole() {
        val destination = requireNotNull(OwnerActivityPolicy.destination("owner", "workspace", event))
        assertTrue(destination.matches("owner", "workspace", true))
        assertFalse(destination.matches("partner", "workspace", true))
        assertFalse(destination.matches("owner", "foreign", true))
        assertFalse(destination.matches("owner", "workspace", false))
        assertFalse(destination.matches(null, "workspace", true))
        assertNull(OwnerActivityPolicy.destination("owner", "other/workspace", event))
        assertNull(OwnerActivityPolicy.destination("owner", "workspace", "forged"))
    }
}
