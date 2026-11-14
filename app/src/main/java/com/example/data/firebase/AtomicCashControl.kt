package com.example.data.firebase

import com.example.data.entity.PendingOperationEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Date
import kotlin.math.abs
import kotlin.math.round

/** Permanent domain rejection when shared confirmed available cash is insufficient. */
class InsufficientSharedFundsException(message: String) : IllegalArgumentException(message)

/** Transient hold when atomic cash control is active without a reviewed baseline snapshot. */
class FinancialBaselinePendingException : java.io.IOException(
    "Workspace financial baseline is not initialized yet; original action remains pending without fabricating an opening balance"
)

internal object AtomicCashControl {
    val CANONICAL_PAYMENT_METHODS = setOf("Cash", "UPI / GPay", "Bank Transfer", "UPI", "Bank", "Cheque")
    val CANONICAL_EXPENSE_PAID_BY = setOf("Business Paid", "I Paid", "Loan Funded")

    fun sameMoney(actual: Double, expected: Double): Boolean = abs(actual - expected) < 0.000001

    fun addMoney(a: Double, b: Double): Double {
        val sum = a + b
        val rounded = round(sum * 100.0) / 100.0
        return if (abs(sum - rounded) < 0.000001) rounded else sum
    }

    fun isCashMethod(method: String?): Boolean = method?.trim().equals("Cash", ignoreCase = true)

    fun isBusinessPaid(paidBy: String?): Boolean = paidBy?.trim().equals("Business Paid", ignoreCase = true)

    internal fun fsRef(firestore: FirebaseFirestore, ws: String): DocumentReference =
        firestore.collection("workspaces").document(ws).collection("financialState").document("current")

    internal fun cashOpRef(firestore: FirebaseFirestore, ws: String, opId: String): DocumentReference =
        firestore.collection("workspaces").document(ws).collection("cashOperations").document(opId)

    internal fun deleteProofRef(firestore: FirebaseFirestore, ws: String, kind: String, id: Long): DocumentReference =
        firestore.collection("workspaces").document(ws).collection("cashDeleteProofs").document("${kind}_$id")

    internal fun verifyMatchingReceipt(
        receipt: DocumentSnapshot,
        op: PendingOperationEntity,
        expectedMutation: String,
        expectedDelta: Double? = null,
        expectedReservedDelta: Double? = null,
        expectedBankDelta: Double? = null
    ) {
        val data = receipt.data ?: error("Missing cash operation receipt")
        require(data["workspaceId"] == op.workspaceId) { "Cash receipt workspace mismatch" }
        require(data["operationId"] == op.operationId) { "Cash receipt operation mismatch" }
        require(data["entityType"] == op.entityType) { "Cash receipt entity type mismatch" }
        require(data["entityId"] == op.entityId.toString()) { "Cash receipt entity ID mismatch" }
        require(data["mutationType"] == expectedMutation) { "Cash receipt mutation mismatch" }
        require(data["actorUid"] == op.actorUid) { "Cash receipt actor mismatch" }
        if (expectedDelta != null) {
            val recorded = (data["cashDelta"] as? Number)?.toDouble() ?: Double.NaN
            require(sameMoney(recorded, expectedDelta)) { "Cash receipt delta mismatch" }
        }
        if (expectedReservedDelta != null) {
            val recorded = (data["reservedDelta"] as? Number)?.toDouble() ?: Double.NaN
            require(sameMoney(recorded, expectedReservedDelta)) { "Reserved receipt delta mismatch" }
        }
        if (expectedBankDelta != null) {
            val recorded = (data["bankDelta"] as? Number)?.toDouble() ?: 0.0
            require(sameMoney(recorded, expectedBankDelta)) { "Bank receipt delta mismatch" }
        }
    }

    internal fun writeCashStateAndReceipt(
        transaction: Transaction,
        fsDoc: DocumentSnapshot,
        fsDocRef: DocumentReference,
        cashReceiptRef: DocumentReference,
        workspaceId: String,
        operationId: String,
        entityType: String,
        entityId: String,
        mutationType: String,
        actorUid: String,
        actionTime: Long,
        cashDelta: Double,
        reservedDelta: Double = 0.0,
        bankDelta: Double = 0.0,
        enforceNonNegativeAvailable: Boolean = false,
        enforceNonNegativeBank: Boolean = false,
        extraReceiptFields: Map<String, Any?> = emptyMap()
    ) {
        if (!fsDoc.exists()) throw FinancialBaselinePendingException()
        val data = fsDoc.data ?: throw FinancialBaselinePendingException()
        require(data["workspaceId"] == workspaceId) { "Financial state workspace mismatch" }
        val expectedVersion = (data["version"] as? Number)?.toLong()
            ?: throw FinancialBaselinePendingException()
        require(expectedVersion >= 1L) { "Invalid financial state version" }
        val prevCash = (data["confirmedCash"] as? Number)?.toDouble()
            ?: throw FinancialBaselinePendingException()
        val prevReserved = (data["reservedCash"] as? Number)?.toDouble() ?: 0.0
        val prevBank = (data["confirmedBank"] as? Number)?.toDouble() ?: 0.0
        val nextCash = addMoney(prevCash, cashDelta)
        val nextReserved = addMoney(prevReserved, reservedDelta)
        val nextBank = addMoney(prevBank, bankDelta)
        require(nextReserved >= -0.000001) { "Invalid negative reserved cash" }
        val availableAfter = addMoney(nextCash, -nextReserved)
        if (enforceNonNegativeAvailable && availableAfter < -0.000001) {
            val availableNow = addMoney(prevCash, -prevReserved)
            throw InsufficientSharedFundsException(
                "Insufficient confirmed shared cash (available ₹%.2f, requested ₹%.2f)".format(
                    java.util.Locale.US, availableNow, -cashDelta + maxOf(0.0, reservedDelta)
                )
            )
        }
        if (enforceNonNegativeBank && nextBank < -0.000001) {
            throw InsufficientSharedFundsException(
                "Insufficient confirmed business bank funds (available ₹%.2f, requested ₹%.2f)".format(
                    java.util.Locale.US, prevBank, -bankDelta
                )
            )
        }
        val newVersion = expectedVersion + 1L
        transaction.update(
            fsDocRef,
            mapOf(
                "version" to newVersion,
                "confirmedCash" to nextCash,
                "reservedCash" to nextReserved,
                "confirmedBank" to nextBank,
                "lastOperationId" to operationId,
                "lastEntityType" to entityType,
                "lastEntityId" to entityId,
                "lastActorUid" to actorUid,
                "actionTime" to actionTime,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val receipt = linkedMapOf<String, Any?>(
            "workspaceId" to workspaceId,
            "operationId" to operationId,
            "entityType" to entityType,
            "entityId" to entityId,
            "mutationType" to mutationType,
            "actorUid" to actorUid,
            "actionTime" to actionTime,
            "cashDelta" to cashDelta,
            "reservedDelta" to reservedDelta,
            "expectedVersion" to expectedVersion,
            "newVersion" to newVersion,
            "confirmedCashAfter" to nextCash,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!sameMoney(reservedDelta, 0.0)) {
            receipt["reservedCashAfter"] = nextReserved
        }
        if (!sameMoney(bankDelta, 0.0)) {
            receipt["bankDelta"] = bankDelta
            receipt["confirmedBankAfter"] = nextBank
        }
        receipt.putAll(extraReceiptFields)
        transaction.set(cashReceiptRef, receipt)
    }

    suspend fun applyWithdrawal(
        firestore: FirebaseFirestore,
        op: PendingOperationEntity,
        payload: Map<String, Any?>
    ) {
        val wRef = firestore.collection("workspaces").document(op.workspaceId)
            .collection("withdrawals").document(op.entityId.toString())
        val fsDocRef = fsRef(firestore, op.workspaceId)
        val opRef = cashOpRef(firestore, op.workspaceId, op.operationId)
        val dpRef = deleteProofRef(firestore, op.workspaceId, "WITHDRAWAL", op.entityId)

        firestore.runTransaction { tx ->
            val receiptSnap = tx.get(opRef)
            val fsSnap = tx.get(fsDocRef)
            val wSnap = tx.get(wRef)
            if (op.mutationType == "DELETE") {
                val dpSnap = tx.get(dpRef)
                if (receiptSnap.exists()) {
                    verifyMatchingReceipt(receiptSnap, op, "DELETE")
                    return@runTransaction null
                }
                if (dpSnap.exists()) {
                    require(dpSnap.getString("operationId") == op.operationId && dpSnap.getString("actorUid") == op.actorUid) {
                        "Withdrawal was already deleted by another action"
                    }
                    return@runTransaction null
                }
                require(wSnap.exists()) { "Withdrawal no longer exists; refresh shared records" }
                val amount = (wSnap.getDouble("amount") ?: 0.0)
                require(amount > 0.0 && amount.isFinite()) { "Invalid withdrawal amount" }
                writeCashStateAndReceipt(
                    tx, fsSnap, fsDocRef, opRef,
                    op.workspaceId, op.operationId, "WITHDRAWAL", op.entityId.toString(),
                    "DELETE", op.actorUid, op.actionTime, cashDelta = amount
                )
                tx.delete(wRef)
                tx.set(
                    dpRef,
                    mapOf(
                        "workspaceId" to op.workspaceId,
                        "kind" to "WITHDRAWAL",
                        "recordId" to op.entityId.toString(),
                        "actorUid" to op.actorUid,
                        "operationId" to op.operationId,
                        "actionTime" to op.actionTime,
                        "requiresCashOp" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val amount = (payload["amount"] as? Number)?.toDouble() ?: 0.0
                require(amount > 0.0 && amount.isFinite()) { "Withdrawal amount must be positive" }
                if (receiptSnap.exists()) {
                    verifyMatchingReceipt(receiptSnap, op, "UPSERT", -amount)
                    return@runTransaction null
                }
                if (wSnap.exists()) {
                    if (wSnap.getString("operationId") == op.operationId) return@runTransaction null
                    throw IllegalArgumentException("Withdrawal record already exists under another operation")
                }
                val doc = payload.toMutableMap()
                doc.remove("operationProtocolVersion")
                doc["authorizationTimeStatus"] = "CLIENT_ORIGINAL_V2"
                doc["authorizationCreatedAt"] = Timestamp(Date((doc["createdAt"] as Number).toLong()))
                doc["serverReceivedAt"] = FieldValue.serverTimestamp()

                writeCashStateAndReceipt(
                    tx, fsSnap, fsDocRef, opRef,
                    op.workspaceId, op.operationId, "WITHDRAWAL", op.entityId.toString(),
                    "UPSERT", op.actorUid, op.actionTime, cashDelta = -amount,
                    enforceNonNegativeAvailable = true
                )
                tx.set(wRef, doc)
            }
            null
        }.await()
    }

    suspend fun applyPayment(
        firestore: FirebaseFirestore,
        op: PendingOperationEntity,
        json: JSONObject,
        basePayload: MutableMap<String, Any?>,
        atomicCashEnabled: Boolean
    ) {
        val collectionRef = firestore.collection("workspaces").document(op.workspaceId)
            .collection("collections").document(op.entityId.toString())
        val jobId = json.getLong("jobEntryId")
        val jobRef = firestore.collection("workspaces").document(op.workspaceId)
            .collection("entries").document(jobId.toString())
        val fsDocRef = fsRef(firestore, op.workspaceId)
        val opRef = cashOpRef(firestore, op.workspaceId, op.operationId)

        firestore.runTransaction { tx ->
            val existing = tx.get(collectionRef)
            val currentJob = tx.get(jobRef)
            val receiptSnap = if (atomicCashEnabled) tx.get(opRef) else null
            val fsSnap = if (atomicCashEnabled) tx.get(fsDocRef) else null

            if (existing.getString("operationId") == op.operationId) return@runTransaction null
            if (receiptSnap?.exists() == true) {
                val expectedMut = if (existing.exists()) "REVERSE" else "UPSERT"
                verifyMatchingReceipt(receiptSnap, op, expectedMut)
                return@runTransaction null
            }

            if (!currentJob.exists() || currentJob.getString("workspaceId") != op.workspaceId) {
                throw IllegalArgumentException("Collection source entry is missing")
            }
            require(
                abs((currentJob.getDouble("amountReceived") ?: 0.0) - json.getDouble("baseAmountReceived")) < 0.001 &&
                    abs((currentJob.getDouble("pendingAmount") ?: 0.0) - json.getDouble("basePendingAmount")) < 0.001
            ) { "Collection conflict: refresh the source due before retrying" }

            val payload = basePayload.toMutableMap()
            val method = (payload["paymentMethod"] as? String).orEmpty()
            if (atomicCashEnabled) {
                require(method in CANONICAL_PAYMENT_METHODS) {
                    "Unsupported payment method '$method'; refresh or update app"
                }
            }

            val jobJson = JSONObject(json.getString("entrySnapshotJson"))
            val entry = FirestoreQueuedJson.read(jobJson)
            entry["collectionId"] = op.entityId.toString()
            entry["operationId"] = op.operationId
            entry["actorUid"] = op.actorUid
            entry["actionTime"] = op.actionTime
            entry["isDeleted"] = false
            payload.remove("authorizationCreatedAt")
            if (!existing.exists()) {
                com.example.data.auth.CollectionPolicy.requireDate(json.getLong("collectedAt"), currentJob.getLong("createdAt") ?: 0L)
                payload["authorizationTimeStatus"] = "CLIENT_ORIGINAL_V2"
                payload["authorizationCreatedAt"] = Timestamp(Date(json.getLong("createdAt")))
                payload["serverReceivedAt"] = FieldValue.serverTimestamp()
            } else {
                existing.getTimestamp("authorizationCreatedAt")?.let { payload["authorizationCreatedAt"] = it }
                existing.getTimestamp("serverReceivedAt")?.let { payload["serverReceivedAt"] = it }
                payload.remove("authorizationTimeStatus")
                existing.getString("authorizationTimeStatus")?.let { payload["authorizationTimeStatus"] = it }
            }

            if (atomicCashEnabled) {
                if (!existing.exists() && method == "Cash") {
                    val amount = (payload["amount"] as? Number)?.toDouble() ?: 0.0
                    require(amount > 0.0 && amount.isFinite()) { "Invalid Cash collection amount" }
                    writeCashStateAndReceipt(
                        tx, requireNotNull(fsSnap), fsDocRef, opRef,
                        op.workspaceId, op.operationId, "PAYMENT", op.entityId.toString(),
                        "UPSERT", op.actorUid, op.actionTime, cashDelta = amount
                    )
                } else if (existing.exists() && existing.getString("status") == "ACTIVE" && payload["status"] == "REVERSED") {
                    val existingMethod = existing.getString("paymentMethod").orEmpty()
                    require(existingMethod in CANONICAL_PAYMENT_METHODS) { "Unsupported stored payment method '$existingMethod'" }
                    if (existingMethod == "Cash") {
                        val existingAmount = existing.getDouble("amount") ?: 0.0
                        writeCashStateAndReceipt(
                            tx, requireNotNull(fsSnap), fsDocRef, opRef,
                            op.workspaceId, op.operationId, "PAYMENT", op.entityId.toString(),
                            "REVERSE", op.actorUid, op.actionTime, cashDelta = -existingAmount
                        )
                    }
                }
            }

            tx.set(collectionRef, payload)
            tx.update(
                jobRef,
                mapOf(
                    "amountReceived" to entry["amountReceived"],
                    "pendingAmount" to entry["pendingAmount"],
                    "protocolVersion" to 2,
                    "operationType" to op.mutationType,
                    "dependencies" to payload["dependencies"],
                    "collectionId" to op.entityId.toString(),
                    "operationId" to op.operationId,
                    "actorUid" to op.actorUid,
                    "actionTime" to op.actionTime,
                    "isDeleted" to false
                )
            )
            null
        }.await()
    }

    suspend fun applyExpense(
        firestore: FirebaseFirestore,
        op: PendingOperationEntity,
        json: JSONObject,
        basePayload: Map<String, Any?>
    ) {
        val eRef = firestore.collection("workspaces").document(op.workspaceId)
            .collection("expenses").document(op.entityId.toString())
        val fsDocRef = fsRef(firestore, op.workspaceId)
        val opRef = cashOpRef(firestore, op.workspaceId, op.operationId)
        val dpRef = deleteProofRef(firestore, op.workspaceId, "EXPENSE", op.entityId)

        val paidByInput = (basePayload["paidBy"] as? String).orEmpty()
        val externalLoanId = if (paidByInput == "Loan Funded") {
            (basePayload["externalLoanId"] as? Number)?.toLong() ?: 0L
        } else 0L
        val loanRef = if (externalLoanId > 0L) {
            firestore.collection("workspaces").document(op.workspaceId)
                .collection("externalLoans").document(externalLoanId.toString())
        } else null

        firestore.runTransaction { tx ->
            val eSnap = tx.get(eRef)
            val fsSnap = tx.get(fsDocRef)
            val receiptSnap = tx.get(opRef)
            val loanSnap = loanRef?.let { tx.get(it) }
            if (op.mutationType == "DELETE") {
                val dpSnap = tx.get(dpRef)
                if (receiptSnap.exists()) {
                    verifyMatchingReceipt(receiptSnap, op, "DELETE")
                    return@runTransaction null
                }
                if (dpSnap.exists()) {
                    require(dpSnap.getString("operationId") == op.operationId && dpSnap.getString("actorUid") == op.actorUid) {
                        "Expense was already deleted by another action"
                    }
                    return@runTransaction null
                }
                require(eSnap.exists()) { "Expense no longer exists; refresh shared records" }
                val paidBy = eSnap.getString("paidBy").orEmpty()
                val amount = eSnap.getDouble("amount") ?: 0.0
                val requiresCash = paidBy == "Business Paid"
                if (requiresCash) {
                    writeCashStateAndReceipt(
                        tx, fsSnap, fsDocRef, opRef,
                        op.workspaceId, op.operationId, "EXPENSE", op.entityId.toString(),
                        "DELETE", op.actorUid, op.actionTime, cashDelta = amount
                    )
                }
                tx.delete(eRef)
                tx.set(
                    dpRef,
                    mapOf(
                        "workspaceId" to op.workspaceId,
                        "kind" to "EXPENSE",
                        "recordId" to op.entityId.toString(),
                        "actorUid" to op.actorUid,
                        "operationId" to op.operationId,
                        "actionTime" to op.actionTime,
                        "requiresCashOp" to requiresCash,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                if (eSnap.getString("operationId") == op.operationId) return@runTransaction null
                if (receiptSnap.exists()) {
                    verifyMatchingReceipt(receiptSnap, op, "UPSERT")
                    return@runTransaction null
                }
                com.example.data.sync.QueuedWriteConflict.requireUnchanged(
                    "EXPENSE",
                    json.optString("expectedBaseJson").takeIf { it.isNotBlank() },
                    eSnap.data,
                    op.entityId,
                    op.workspaceId
                )
                val paidBy = (basePayload["paidBy"] as? String).orEmpty()
                require(paidBy in CANONICAL_EXPENSE_PAID_BY) {
                    "Unsupported expense payer '$paidBy'; refresh or update app"
                }
                val prevOut = if (eSnap.exists() && eSnap.getString("paidBy") == "Business Paid") {
                    eSnap.getDouble("amount") ?: 0.0
                } else 0.0
                val nextAmount = (basePayload["amount"] as? Number)?.toDouble() ?: 0.0
                val nextOut = if (paidBy == "Business Paid") nextAmount else 0.0
                val cashDelta = addMoney(prevOut, -nextOut)
                if (paidBy == "Loan Funded") {
                    require(externalLoanId > 0L && loanRef != null && loanSnap != null && loanSnap.exists()) {
                        "Linked Direct-to-Supplier external loan does not exist"
                    }
                    require(loanSnap.getString("workspaceId") == op.workspaceId) { "External loan workspace mismatch" }
                    require(loanSnap.getString("destination") == "DIRECT_TO_SUPPLIER") {
                        "Only Direct-to-Supplier external loans can directly fund an expense"
                    }
                    require(loanSnap.getString("status") == "ACTIVE") {
                        "Linked external loan is not active"
                    }
                    val loanPrincipal = loanSnap.getDouble("principalAmount") ?: 0.0
                    require(nextAmount > 0.0 && nextAmount <= loanPrincipal + 0.000001) {
                        "Loan-funded expense amount cannot exceed loan principal"
                    }
                    val currentLinked = loanSnap.getLong("linkedExpenseId") ?: 0L
                    require(currentLinked == 0L || currentLinked == op.entityId) {
                        "This external loan is already linked to another expense"
                    }
                    if (currentLinked == 0L) {
                        tx.update(
                            loanRef,
                            mapOf(
                                "linkedExpenseId" to op.entityId,
                                "updatedAt" to op.actionTime
                            )
                        )
                    }
                }
                if (!sameMoney(prevOut, nextOut)) {
                    writeCashStateAndReceipt(
                        tx, fsSnap, fsDocRef, opRef,
                        op.workspaceId, op.operationId, "EXPENSE", op.entityId.toString(),
                        "UPSERT", op.actorUid, op.actionTime, cashDelta = cashDelta
                    )
                }
                val payload = basePayload.toMutableMap()
                payload.remove("operationProtocolVersion")
                payload.remove("authorizationCreatedAt")
                if (!eSnap.exists()) {
                    payload["authorizationTimeStatus"] = "CLIENT_ORIGINAL_V2"
                    payload["authorizationCreatedAt"] = Timestamp(Date((basePayload["createdAt"] as Number).toLong()))
                    payload["serverReceivedAt"] = FieldValue.serverTimestamp()
                } else {
                    payload.remove("createdByRole")
                    eSnap.data?.get("createdByRole")?.let { payload["createdByRole"] = it }
                    eSnap.getTimestamp("authorizationCreatedAt")?.let { payload["authorizationCreatedAt"] = it }
                    eSnap.getString("authorizationTimeStatus")?.let { payload["authorizationTimeStatus"] = it }
                    eSnap.getTimestamp("serverReceivedAt")?.let { payload["serverReceivedAt"] = it }
                }
                tx.set(eRef, payload)
            }
            null
        }.await()
    }

    fun applyPurgeChunkCash(
        tx: Transaction,
        firestore: FirebaseFirestore,
        workspaceId: String,
        fsSnap: DocumentSnapshot?,
        existingDocs: List<DocumentSnapshot>,
        chunkOpId: String,
        targetUid: String,
        actorUid: String,
        actionTime: Long,
        cashOpSnap: DocumentSnapshot?
    ) {
        if (fsSnap == null) return
        val delta = existingDocs.sumOf { doc ->
            val seg = doc.reference.parent.id
            val data = doc.data ?: return@sumOf 0.0
            when (seg) {
                "collections" -> if (data["status"] == "ACTIVE" && isCashMethod(data["paymentMethod"] as? String)) {
                    -((data["amount"] as? Number)?.toDouble() ?: 0.0)
                } else 0.0
                "expenses" -> if (isBusinessPaid(data["paidBy"] as? String)) {
                    (data["amount"] as? Number)?.toDouble() ?: 0.0
                } else 0.0
                "withdrawals" -> (data["amount"] as? Number)?.toDouble() ?: 0.0
                else -> 0.0
            }
        }
        if (!sameMoney(delta, 0.0) && cashOpSnap?.exists() != true) {
            writeCashStateAndReceipt(
                tx, fsSnap, fsRef(firestore, workspaceId), cashOpRef(firestore, workspaceId, chunkOpId),
                workspaceId, chunkOpId, "PURGE", targetUid, "PURGE", actorUid, actionTime,
                cashDelta = delta
            )
        }
    }
}
