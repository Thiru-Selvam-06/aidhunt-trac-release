package com.example

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36], application=android.app.Application::class)
class UnusedCustomerPolicyTest {
    @Test fun unusedProfileSelectionExcludesReversedReceiptsAndRejectedOfflineActionsWithoutCrossWorkspaceMixing() {
        val customers=(1L..3L).map { com.example.data.entity.CustomerEntity(id=it,workspaceId="ws_test",name="Synthetic $it",phone="") }
        val reversed=com.example.data.entity.PaymentEntity(id=1,workspaceId="ws_test",customerId=1,customerName="Synthetic 1",amount=10.0,status="REVERSED")
        val retained=com.example.data.entity.PendingOperationEntity("original-job","ws_test","partner","JOB",9,"UPSERT",1,123,
            """{"customerId":2}""",state="REJECTED")
        assertEquals(listOf(3L),com.example.ui.components.UnusedCustomerPolicy.candidates("ws_test",customers,emptyList(),
            listOf(reversed,reversed.copy(workspaceId="foreign",customerId=3)),listOf(retained)).map { it.id })
        assertTrue(com.example.ui.components.UnusedCustomerPolicy.candidates("ws_test",customers,emptyList(),emptyList(),
            listOf(retained.copy(payloadJson="malformed"))).isEmpty())
    }
}
