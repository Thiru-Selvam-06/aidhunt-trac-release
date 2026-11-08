package com.example

import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.WorkspaceMember
import com.example.data.sync.CustomerProfileEdit
import com.example.data.sync.OperationPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=android.app.Application::class)
class BasicCustomerOperationPolicyTest {
    private val member=WorkspaceMember(uid="operator",workspaceId="w",role="Operator",status="ACTIVE")
    private fun operation()=PendingOperationEntity("original-profile","w","operator","CUSTOMER",7,"UPSERT",1,1000,
        JSONObject().put("operationProtocolVersion",2).put("workspaceId","w").put("id",7)
            .put("createdByUid","operator").put("createdAt",1000).put("updatedAt",1000)
            .put("basicProfileCreate",true).put("totalBilled",0).put("totalPaid",0).put("balanceDue",0).toString())
    @Test fun activeOperatorMayCreateOnlyTheBoundBasicProfile() {
        OperationPolicy.requireAuthorized(operation(),"Operator",listOf(member))
        for(changes in listOf(JSONObject().put("basicProfileCreate",false),JSONObject().put("totalPaid",1),
            JSONObject().put("createdByUid","owner"),JSONObject().put("id",8),JSONObject().put("updatedAt",1001),
            JSONObject().put(CustomerProfileEdit.PAYLOAD_KEY,"{}"))) {
            val op=operation();val json=JSONObject(op.payloadJson);changes.keys().forEach{json.put(it,changes.get(it))}
            assertThrows(SecurityException::class.java){OperationPolicy.requireAuthorized(op.copy(payloadJson=json.toString()),"Operator",listOf(member))}
        }
        assertThrows(SecurityException::class.java){OperationPolicy.requireAuthorized(operation().copy(mutationType="DELETE"),"Operator",listOf(member))}
    }
    @Test fun removalForeignWorkspaceOrRoleChangeCannotAuthorizeRetainedBasicCreate() {
        for(person in listOf(member.copy(status="REMOVED"),member.copy(workspaceId="foreign"),member.copy(role="Partner")))
            assertThrows(SecurityException::class.java){OperationPolicy.requireAuthorized(operation(),"Operator",listOf(person))}
    }
}
