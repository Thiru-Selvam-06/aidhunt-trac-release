package com.example.data.firebase

import com.example.data.entity.PendingOperationEntity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

internal object Stage3AtomicControl {

    suspend fun applyExternalLoan(
        firestore: FirebaseFirestore,
        op: PendingOperationEntity,
        basePayload: Map<String, Any?>,
        atomicCashEnabled: Boolean
    ) {
        val ws = firestore.collection("workspaces").document(op.workspaceId)
        val loanRef = ws.collection("externalLoans").document(op.entityId.toString())
        val fsDocRef = AtomicCashControl.fsRef(firestore, op.workspaceId)
        val opRef = AtomicCashControl.cashOpRef(firestore, op.workspaceId, op.operationId)

        firestore.runTransaction { tx ->
            val loanSnap = tx.get(loanRef)
            val fsSnap = if (atomicCashEnabled) tx.get(fsDocRef) else null
            val receiptSnap = if (atomicCashEnabled) tx.get(opRef) else null

            if (loanSnap.getString("operationId") == op.operationId) return@runTransaction null
            val principal = (basePayload["principalAmount"] as? Number)?.toDouble() ?: 0.0
            val interest = (basePayload["interestAmount"] as? Number)?.toDouble() ?: 0.0
            val destination = (basePayload["destination"] as? String).orEmpty()
            require(principal > 0.0 && principal.isFinite()) { "Loan principal must be positive" }
            require(interest >= 0.0 && interest.isFinite()) { "Loan interest must be non-negative" }
            require(destination in setOf("BUSINESS_CASH", "BUSINESS_BANK", "DIRECT_TO_SUPPLIER")) {
                "Unsupported external loan destination '$destination'"
            }
            if (receiptSnap?.exists() == true) {
                AtomicCashControl.verifyMatchingReceipt(receiptSnap, op, "UPSERT")
                return@runTransaction null
            }
            require(!loanSnap.exists()) { "External loan record already exists under another operation" }

            if (atomicCashEnabled && destination in setOf("BUSINESS_CASH", "BUSINESS_BANK")) {
                val cashDelta = if (destination == "BUSINESS_CASH") principal else 0.0
                val bankDelta = if (destination == "BUSINESS_BANK") principal else 0.0
                AtomicCashControl.writeCashStateAndReceipt(
                    tx, requireNotNull(fsSnap), fsDocRef, opRef,
                    op.workspaceId, op.operationId, "EXTERNAL_LOAN", op.entityId.toString(),
                    "UPSERT", op.actorUid, op.actionTime,
                    cashDelta = cashDelta, bankDelta = bankDelta
                )
            }
            val doc = basePayload.toMutableMap().apply {
                remove("operationProtocolVersion")
                remove("stage3Action")
                put("serverReceivedAt", FieldValue.serverTimestamp())
            }
            tx.set(loanRef, doc)
            null
        }.await()
    }

    suspend fun applyExternalLoanRepayment(
        firestore: FirebaseFirestore,
        op: PendingOperationEntity,
        basePayload: Map<String, Any?>,
        atomicCashEnabled: Boolean
    ) {
        val ws = firestore.collection("workspaces").document(op.workspaceId)
        val repRef = ws.collection("externalLoanRepayments").document(op.entityId.toString())
        val loanId = (basePayload["loanId"] as? Number)?.toLong() ?: 0L
        require(loanId > 0L) { "Missing external loan reference" }
        val loanRef = ws.collection("externalLoans").document(loanId.toString())
        val fsDocRef = AtomicCashControl.fsRef(firestore, op.workspaceId)
        val opRef = AtomicCashControl.cashOpRef(firestore, op.workspaceId, op.operationId)

        firestore.runTransaction { tx ->
            val repSnap = tx.get(repRef)
            val loanSnap = tx.get(loanRef)
            val fsSnap = if (atomicCashEnabled) tx.get(fsDocRef) else null
            val receiptSnap = if (atomicCashEnabled) tx.get(opRef) else null

            if (repSnap.getString("operationId") == op.operationId) return@runTransaction null
            if (receiptSnap?.exists() == true) {
                AtomicCashControl.verifyMatchingReceipt(receiptSnap, op, "UPSERT")
                return@runTransaction null
            }
            require(!repSnap.exists()) { "Repayment already recorded under another operation" }
            require(loanSnap.exists() && loanSnap.getString("workspaceId") == op.workspaceId) {
                "Source external loan not found"
            }

            val principalPaid = (basePayload["principalPaid"] as? Number)?.toDouble() ?: 0.0
            val interestPaid = (basePayload["interestPaid"] as? Number)?.toDouble() ?: 0.0
            val totalPaid = AtomicCashControl.addMoney(principalPaid, interestPaid)
            val source = (basePayload["source"] as? String).orEmpty()
            require(principalPaid >= 0.0 && interestPaid >= 0.0 && totalPaid > 0.0) {
                "Repayment principal or interest must be positive"
            }
            require(source in setOf("BUSINESS_CASH", "BUSINESS_BANK")) {
                "Unsupported loan repayment source '$source'"
            }

            val loanPrincipal = loanSnap.getDouble("principalAmount") ?: 0.0
            val loanInterest = loanSnap.getDouble("interestAmount") ?: 0.0
            val prevRepaidPrincipal = loanSnap.getDouble("totalRepaidPrincipal") ?: 0.0
            val prevRepaidInterest = loanSnap.getDouble("totalRepaidInterest") ?: 0.0
            val nextRepaidPrincipal = AtomicCashControl.addMoney(prevRepaidPrincipal, principalPaid)
            val nextRepaidInterest = AtomicCashControl.addMoney(prevRepaidInterest, interestPaid)
            require(nextRepaidPrincipal <= loanPrincipal + 0.000001) {
                "Principal repayment exceeds remaining loan principal"
            }
            require(nextRepaidInterest <= loanInterest + 0.000001) {
                "Interest repayment exceeds remaining loan interest"
            }
            val nextStatus = if (nextRepaidPrincipal >= loanPrincipal - 0.000001 && nextRepaidInterest >= loanInterest - 0.000001) {
                "SETTLED"
            } else "ACTIVE"

            if (atomicCashEnabled) {
                val cashDelta = if (source == "BUSINESS_CASH") -totalPaid else 0.0
                val bankDelta = if (source == "BUSINESS_BANK") -totalPaid else 0.0
                AtomicCashControl.writeCashStateAndReceipt(
                    tx, requireNotNull(fsSnap), fsDocRef, opRef,
                    op.workspaceId, op.operationId, "EXTERNAL_LOAN_REPAYMENT", op.entityId.toString(),
                    "UPSERT", op.actorUid, op.actionTime,
                    cashDelta = cashDelta,
                    bankDelta = bankDelta,
                    enforceNonNegativeAvailable = source == "BUSINESS_CASH",
                    enforceNonNegativeBank = source == "BUSINESS_BANK"
                )
            }

            tx.update(
                loanRef,
                mapOf(
                    "totalRepaidPrincipal" to nextRepaidPrincipal,
                    "totalRepaidInterest" to nextRepaidInterest,
                    "status" to nextStatus,
                    "updatedAt" to op.actionTime,
                    "operationId" to op.operationId,
                    "actorUid" to op.actorUid,
                    "actionTime" to op.actionTime
                )
            )
            val doc = basePayload.toMutableMap().apply {
                remove("operationProtocolVersion")
                remove("stage3Action")
                put("serverReceivedAt", FieldValue.serverTimestamp())
            }
            tx.set(repRef, doc)
            null
        }.await()
    }

    suspend fun applyBorrowingRequest(
        firestore: FirebaseFirestore,
        op: PendingOperationEntity,
        json: JSONObject,
        basePayload: Map<String, Any?>,
        atomicCashEnabled: Boolean
    ) {
        val action = json.optString("stage3Action").ifBlank { "CREATE" }
        val ws = firestore.collection("workspaces").document(op.workspaceId)
        val reqRef = ws.collection("borrowingRequests").document(op.entityId.toString())
        val withdrawalId = (basePayload["withdrawalId"] as? Number)?.toLong() ?: 0L
        val obligationId = (basePayload["obligationId"] as? Number)?.toLong() ?: 0L
        val wRef = if (withdrawalId > 0L && action in setOf("EXECUTE_BUSINESS", "EXECUTE")) {
            ws.collection("withdrawals").document(withdrawalId.toString())
        } else null
        val obRef = if (obligationId > 0L && action in setOf("EXECUTE_BUSINESS", "EXECUTE")) {
            ws.collection("settlementObligations").document(obligationId.toString())
        } else null
        val fsDocRef = AtomicCashControl.fsRef(firestore, op.workspaceId)
        val opRef = AtomicCashControl.cashOpRef(firestore, op.workspaceId, op.operationId)

        firestore.runTransaction { tx ->
            val reqSnap = tx.get(reqRef)
            val wSnap = wRef?.let { tx.get(it) }
            val obSnap = obRef?.let { tx.get(it) }
            val fsSnap = if (atomicCashEnabled) tx.get(fsDocRef) else null
            val receiptSnap = if (atomicCashEnabled) tx.get(opRef) else null

            if (reqSnap.getString("operationId") == op.operationId) return@runTransaction null
            val mutationName = when (action) {
                "APPROVE_RESERVE" -> "APPROVE_RESERVE"
                "EXECUTE" -> "EXECUTE"
                "CANCEL" -> "CANCEL"
                "REJECT" -> "REJECT"
                else -> "UPSERT"
            }
            if (receiptSnap?.exists() == true) {
                AtomicCashControl.verifyMatchingReceipt(receiptSnap, op, mutationName)
                return@runTransaction null
            }

            val reqAmount = (basePayload["amount"] as? Number)?.toDouble() ?: (reqSnap.getDouble("amount") ?: 0.0)
            require(reqAmount > 0.0 && reqAmount.isFinite()) { "Borrowing amount must be positive" }

            when (action) {
                "EXECUTE_BUSINESS" -> {
                    require(!reqSnap.exists()) { "Borrowing request already exists" }
                    require(basePayload["sourceType"] == "BUSINESS_ONLY") { "Invalid sourceType for immediate business borrow" }
                    require(wRef != null && obRef != null && wSnap != null && !wSnap.exists() && obSnap != null && !obSnap.exists()) {
                        "Withdrawal or obligation target already exists"
                    }
                    if (atomicCashEnabled) {
                        AtomicCashControl.writeCashStateAndReceipt(
                            tx, requireNotNull(fsSnap), fsDocRef, opRef,
                            op.workspaceId, op.operationId, "BORROWING_REQUEST", op.entityId.toString(),
                            "UPSERT", op.actorUid, op.actionTime,
                            cashDelta = -reqAmount,
                            enforceNonNegativeAvailable = true
                        )
                    }
                    val reqDoc = basePayload.toMutableMap().apply {
                        remove("operationProtocolVersion")
                        remove("stage3Action")
                        remove("withdrawalSnapshotMap")
                        remove("obligationSnapshotMap")
                        put("status", "EXECUTED")
                        put("reservedAmount", 0.0)
                        put("executedAt", op.actionTime)
                        put("updatedAt", op.actionTime)
                        put("serverReceivedAt", FieldValue.serverTimestamp())
                    }
                    tx.set(reqRef, reqDoc)
                    val wDoc = FirestoreQueuedJson.read(json.getJSONObject("withdrawalSnapshotMap")).apply {
                        put("protocolVersion", 2)
                        put("operationType", "UPSERT")
                        put("dependencies", emptyList<Any>())
                        put("operationId", op.operationId)
                        put("actorUid", op.actorUid)
                        put("actionTime", op.actionTime)
                        put("createdAt", op.actionTime)
                        put("isDeleted", false)
                        put("borrowingRequestId", op.entityId)
                        put("authorizationTimeStatus", "CLIENT_ORIGINAL_V2")
                        put("authorizationCreatedAt", com.google.firebase.Timestamp(java.util.Date(op.actionTime)))
                        put("serverReceivedAt", FieldValue.serverTimestamp())
                    }
                    tx.set(wRef, wDoc)
                    val obDoc = FirestoreQueuedJson.read(json.getJSONObject("obligationSnapshotMap")).apply {
                        put("protocolVersion", 2)
                        put("operationType", "UPSERT")
                        put("dependencies", emptyList<Any>())
                        put("operationId", op.operationId)
                        put("actorUid", op.actorUid)
                        put("actionTime", op.actionTime)
                        put("isDeleted", false)
                    }
                    tx.set(obRef, obDoc)
                }

                "CREATE" -> {
                    require(!reqSnap.exists()) { "Borrowing request already exists" }
                    require(basePayload["sourceType"] == "NAMED_PARTNER") { "Partner approval request requires NAMED_PARTNER" }
                    val reqDoc = basePayload.toMutableMap().apply {
                        remove("operationProtocolVersion")
                        remove("stage3Action")
                        put("status", "PENDING_APPROVAL")
                        put("reservedAmount", 0.0)
                        put("serverReceivedAt", FieldValue.serverTimestamp())
                    }
                    tx.set(reqRef, reqDoc)
                }

                "APPROVE_RESERVE" -> {
                    require(reqSnap.exists()) { "Borrowing request no longer exists" }
                    require(reqSnap.getString("status") == "PENDING_APPROVAL") { "Request is no longer awaiting approval" }
                    require(reqSnap.getString("lendingPartnerUid") == op.actorUid) { "Only named lending partner can approve & reserve" }
                    val amt = reqSnap.getDouble("amount") ?: 0.0
                    if (atomicCashEnabled) {
                        AtomicCashControl.writeCashStateAndReceipt(
                            tx, requireNotNull(fsSnap), fsDocRef, opRef,
                            op.workspaceId, op.operationId, "BORROWING_REQUEST", op.entityId.toString(),
                            "APPROVE_RESERVE", op.actorUid, op.actionTime,
                            cashDelta = 0.0,
                            reservedDelta = amt,
                            enforceNonNegativeAvailable = true
                        )
                    }
                    tx.update(
                        reqRef,
                        mapOf(
                            "status" to "APPROVED_RESERVED",
                            "reservedAmount" to amt,
                            "approvedByUid" to op.actorUid,
                            "approvedAt" to op.actionTime,
                            "updatedAt" to op.actionTime,
                            "operationId" to op.operationId,
                            "actorUid" to op.actorUid,
                            "actionTime" to op.actionTime,
                            "operationType" to "UPSERT",
                            "protocolVersion" to 2,
                            "dependencies" to (basePayload["dependencies"] ?: emptyList<Any>()),
                            "isDeleted" to false
                        )
                    )
                }

                "EXECUTE" -> {
                    require(reqSnap.exists()) { "Borrowing request no longer exists" }
                    require(reqSnap.getString("status") == "APPROVED_RESERVED") { "Request must be approved and reserved before payout" }
                    val amt = reqSnap.getDouble("amount") ?: 0.0
                    val reserved = reqSnap.getDouble("reservedAmount") ?: 0.0
                    require(wRef != null && obRef != null && wSnap != null && !wSnap.exists() && obSnap != null && !obSnap.exists()) {
                        "Withdrawal or obligation already exists"
                    }
                    if (atomicCashEnabled) {
                        AtomicCashControl.writeCashStateAndReceipt(
                            tx, requireNotNull(fsSnap), fsDocRef, opRef,
                            op.workspaceId, op.operationId, "BORROWING_REQUEST", op.entityId.toString(),
                            "EXECUTE", op.actorUid, op.actionTime,
                            cashDelta = -amt,
                            reservedDelta = -reserved,
                            enforceNonNegativeAvailable = true
                        )
                    }
                    tx.update(
                        reqRef,
                        mapOf(
                            "status" to "EXECUTED",
                            "reservedAmount" to 0.0,
                            "withdrawalId" to withdrawalId,
                            "obligationId" to obligationId,
                            "executedAt" to op.actionTime,
                            "updatedAt" to op.actionTime,
                            "operationId" to op.operationId,
                            "actorUid" to op.actorUid,
                            "actionTime" to op.actionTime,
                            "operationType" to "UPSERT",
                            "protocolVersion" to 2,
                            "dependencies" to (basePayload["dependencies"] ?: emptyList<Any>()),
                            "isDeleted" to false
                        )
                    )
                    val wDoc = FirestoreQueuedJson.read(json.getJSONObject("withdrawalSnapshotMap")).apply {
                        put("protocolVersion", 2)
                        put("operationType", "UPSERT")
                        put("dependencies", emptyList<Any>())
                        put("operationId", op.operationId)
                        put("actorUid", op.actorUid)
                        put("actionTime", op.actionTime)
                        put("createdAt", op.actionTime)
                        put("isDeleted", false)
                        put("borrowingRequestId", op.entityId)
                        put("authorizationTimeStatus", "CLIENT_ORIGINAL_V2")
                        put("authorizationCreatedAt", com.google.firebase.Timestamp(java.util.Date(op.actionTime)))
                        put("serverReceivedAt", FieldValue.serverTimestamp())
                    }
                    tx.set(wRef, wDoc)
                    val obDoc = FirestoreQueuedJson.read(json.getJSONObject("obligationSnapshotMap")).apply {
                        put("protocolVersion", 2)
                        put("operationType", "UPSERT")
                        put("dependencies", emptyList<Any>())
                        put("operationId", op.operationId)
                        put("actorUid", op.actorUid)
                        put("actionTime", op.actionTime)
                        put("isDeleted", false)
                    }
                    tx.set(obRef, obDoc)
                }

                "CANCEL", "REJECT" -> {
                    require(reqSnap.exists()) { "Borrowing request no longer exists" }
                    val currentStatus = reqSnap.getString("status").orEmpty()
                    require(currentStatus in setOf("PENDING_APPROVAL", "APPROVED_RESERVED")) {
                        "Borrowing request can no longer be $action"
                    }
                    val reserved = reqSnap.getDouble("reservedAmount") ?: 0.0
                    if (atomicCashEnabled && currentStatus == "APPROVED_RESERVED" && reserved > 0.0) {
                        AtomicCashControl.writeCashStateAndReceipt(
                            tx, requireNotNull(fsSnap), fsDocRef, opRef,
                            op.workspaceId, op.operationId, "BORROWING_REQUEST", op.entityId.toString(),
                            action, op.actorUid, op.actionTime,
                            cashDelta = 0.0,
                            reservedDelta = -reserved
                        )
                    }
                    val nextStatus = if (action == "CANCEL") "CANCELLED" else "REJECTED"
                    tx.update(
                        reqRef,
                        mapOf(
                            "status" to nextStatus,
                            "reservedAmount" to 0.0,
                            "rejectedByUid" to op.actorUid,
                            "rejectedAt" to op.actionTime,
                            "rejectionReason" to json.optString("rejectionReason"),
                            "updatedAt" to op.actionTime,
                            "operationId" to op.operationId,
                            "actorUid" to op.actorUid,
                            "actionTime" to op.actionTime,
                            "operationType" to "UPSERT",
                            "protocolVersion" to 2,
                            "dependencies" to (basePayload["dependencies"] ?: emptyList<Any>()),
                            "isDeleted" to false
                        )
                    )
                }
            }
            null
        }.await()
    }

    suspend fun applySettlementRepayment(
        firestore: FirebaseFirestore,
        op: PendingOperationEntity,
        json: JSONObject,
        basePayload: Map<String, Any?>,
        atomicCashEnabled: Boolean
    ) {
        val action = json.optString("stage3Action").ifBlank { "CREATE" }
        val ws = firestore.collection("workspaces").document(op.workspaceId)
        val repRef = ws.collection("settlementRepayments").document(op.entityId.toString())
        val obligationId = (basePayload["obligationId"] as? Number)?.toLong()
            ?: json.optLong("obligationId")
        require(obligationId > 0L) { "Missing settlement obligation ID" }
        val obRef = ws.collection("settlementObligations").document(obligationId.toString())
        val fsDocRef = AtomicCashControl.fsRef(firestore, op.workspaceId)
        val opRef = AtomicCashControl.cashOpRef(firestore, op.workspaceId, op.operationId)

        firestore.runTransaction { tx ->
            val repSnap = tx.get(repRef)
            val obSnap = tx.get(obRef)
            val fsSnap = if (atomicCashEnabled) tx.get(fsDocRef) else null
            val receiptSnap = if (atomicCashEnabled) tx.get(opRef) else null

            if (repSnap.getString("operationId") == op.operationId) return@runTransaction null
            val mutationName = when (action) {
                "CONFIRM" -> "CONFIRM"
                "REJECT" -> "REJECT"
                else -> "UPSERT"
            }
            if (receiptSnap?.exists() == true) {
                AtomicCashControl.verifyMatchingReceipt(receiptSnap, op, mutationName)
                return@runTransaction null
            }

            require(obSnap.exists() && obSnap.getString("workspaceId") == op.workspaceId) {
                "Settlement obligation not found"
            }

            when (action) {
                "CREATE" -> {
                    require(!repSnap.exists()) { "Repayment entry already exists" }
                    val amount = (basePayload["amount"] as? Number)?.toDouble() ?: 0.0
                    val mode = (basePayload["mode"] as? String).orEmpty()
                    val principal = obSnap.getDouble("principalAmount") ?: 0.0
                    val alreadyRepaid = obSnap.getDouble("repaidAmount") ?: 0.0
                    val remaining = AtomicCashControl.addMoney(principal, -alreadyRepaid)
                    require(amount > 0.0 && amount <= remaining + 0.000001) {
                        "Repayment exceeds remaining obligation balance (₹%.2f)".format(java.util.Locale.US, remaining)
                    }
                    require(mode in setOf("INTO_BUSINESS", "INTO_BUSINESS_FOR_PARTNER", "DIRECT_TO_PARTNER")) {
                        "Unsupported repayment mode '$mode'"
                    }
                    val doc = basePayload.toMutableMap().apply {
                        remove("operationProtocolVersion")
                        remove("stage3Action")
                        put("status", "PENDING_CONFIRMATION")
                        put("serverReceivedAt", FieldValue.serverTimestamp())
                    }
                    tx.set(repRef, doc)
                }

                "CONFIRM" -> {
                    require(repSnap.exists()) { "Settlement repayment no longer exists" }
                    require(repSnap.getString("status") == "PENDING_CONFIRMATION") {
                        "Repayment was already ${repSnap.getString("status")}"
                    }
                    val mode = repSnap.getString("mode").orEmpty()
                    val creditorUid = repSnap.getString("creditorUid").orEmpty()
                    if (mode == "DIRECT_TO_PARTNER") {
                        require(creditorUid == op.actorUid) {
                            "Only the named lending partner may confirm direct-to-partner repayment"
                        }
                    }
                    val amount = repSnap.getDouble("amount") ?: 0.0
                    val principal = obSnap.getDouble("principalAmount") ?: 0.0
                    val alreadyRepaid = obSnap.getDouble("repaidAmount") ?: 0.0
                    val nextRepaid = AtomicCashControl.addMoney(alreadyRepaid, amount)
                    require(nextRepaid <= principal + 0.000001) {
                        "Repayment exceeds remaining obligation balance"
                    }
                    val prevDirect = obSnap.getDouble("directSettledAmount") ?: 0.0
                    val nextDirect = if (mode == "DIRECT_TO_PARTNER") {
                        AtomicCashControl.addMoney(prevDirect, amount)
                    } else prevDirect
                    val nextObStatus = if (nextRepaid >= principal - 0.000001) "SETTLED" else "OPEN"

                    if (atomicCashEnabled && mode in setOf("INTO_BUSINESS", "INTO_BUSINESS_FOR_PARTNER")) {
                        AtomicCashControl.writeCashStateAndReceipt(
                            tx, requireNotNull(fsSnap), fsDocRef, opRef,
                            op.workspaceId, op.operationId, "SETTLEMENT_REPAYMENT", op.entityId.toString(),
                            "CONFIRM", op.actorUid, op.actionTime,
                            cashDelta = amount
                        )
                    }

                    tx.update(
                        obRef,
                        mapOf(
                            "repaidAmount" to nextRepaid,
                            "directSettledAmount" to nextDirect,
                            "status" to nextObStatus,
                            "updatedAt" to op.actionTime,
                            "operationId" to op.operationId,
                            "actorUid" to op.actorUid,
                            "actionTime" to op.actionTime,
                            "operationType" to "UPSERT",
                            "protocolVersion" to 2,
                            "dependencies" to (basePayload["dependencies"] ?: emptyList<Any>()),
                            "isDeleted" to false
                        )
                    )
                    tx.update(
                        repRef,
                        mapOf(
                            "status" to "CONFIRMED",
                            "confirmedByUid" to op.actorUid,
                            "confirmedAt" to op.actionTime,
                            "operationId" to op.operationId,
                            "actorUid" to op.actorUid,
                            "actionTime" to op.actionTime,
                            "operationType" to "UPSERT",
                            "protocolVersion" to 2,
                            "dependencies" to (basePayload["dependencies"] ?: emptyList<Any>()),
                            "isDeleted" to false
                        )
                    )
                }

                "REJECT" -> {
                    require(repSnap.exists()) { "Settlement repayment no longer exists" }
                    require(repSnap.getString("status") == "PENDING_CONFIRMATION") {
                        "Repayment is no longer pending confirmation"
                    }
                    val mode = repSnap.getString("mode").orEmpty()
                    val creditorUid = repSnap.getString("creditorUid").orEmpty()
                    if (mode == "DIRECT_TO_PARTNER") {
                        require(creditorUid == op.actorUid) {
                            "Only the named lending partner may reject direct-to-partner repayment"
                        }
                    }
                    tx.update(
                        repRef,
                        mapOf(
                            "status" to "REJECTED",
                            "rejectedByUid" to op.actorUid,
                            "rejectedAt" to op.actionTime,
                            "rejectionReason" to json.optString("rejectionReason"),
                            "operationId" to op.operationId,
                            "actorUid" to op.actorUid,
                            "actionTime" to op.actionTime,
                            "operationType" to "UPSERT",
                            "protocolVersion" to 2,
                            "dependencies" to (basePayload["dependencies"] ?: emptyList<Any>()),
                            "isDeleted" to false
                        )
                    )
                }
            }
            null
        }.await()
    }
}
