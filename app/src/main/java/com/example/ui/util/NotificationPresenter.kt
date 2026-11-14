package com.example.ui.util

import com.example.data.entity.AppNotificationEntity
import com.example.ui.components.formatInr
import org.json.JSONObject

data class PresentedNotification(
    val title: String,
    val body: String,
    val categoryLabel: String,
    val timestampLabel: String
)

/**
 * Renders structured notification records strictly using the recipient's active language (EN or TA)
 * and Asia/Kolkata (IST) timestamp formatting.
 */
object NotificationPresenter {

    fun present(
        notification: AppNotificationEntity,
        isTamil: Boolean,
        currency: String = "₹"
    ): PresentedNotification {
        val actor = notification.actorName.ifBlank { if (isTamil) "பங்குதாரர்" else "Partner" }
        val target = notification.counterpartyName.ifBlank { if (isTamil) "வணிகம்" else "Business" }
        val amt = formatInr(notification.amount, currency)
        val meta = runCatching { JSONObject(notification.metadataJson) }.getOrElse { JSONObject() }

        val title: String
        val body: String
        when (notification.eventType) {
            AppNotificationEntity.EVENT_JOB_CREATED -> {
                title = if (isTamil) "புதிய வேலைப் பதிவு" else "New Job Entry"
                body = if (isTamil) {
                    "$actor $target என்பவருக்கு $amt மதிப்புள்ள புதிய வேலையைப் பதிவு செய்தார்."
                } else {
                    "$actor added a new job for $target — $amt."
                }
            }
            AppNotificationEntity.EVENT_JOB_EDITED -> {
                title = if (isTamil) "வேலைப் பதிவு திருத்தப்பட்டது" else "Job Entry Edited"
                body = if (isTamil) {
                    "$actor $target வேலைப் பதிவைத் திருத்தினார் ($amt)."
                } else {
                    "$actor edited the job for $target ($amt)."
                }
            }
            AppNotificationEntity.EVENT_JOB_DELETED -> {
                title = if (isTamil) "வேலைப் பதிவு நீக்கப்பட்டது" else "Job Entry Deleted"
                body = if (isTamil) {
                    "$actor $target வேலைப் பதிவை நீக்கினார் ($amt)."
                } else {
                    "$actor deleted the job for $target ($amt)."
                }
            }
            AppNotificationEntity.EVENT_PAYMENT_RECEIVED -> {
                title = if (isTamil) "கட்டணம் வசூலிக்கப்பட்டது" else "Payment Received"
                body = if (isTamil) {
                    "$actor $target என்பவரிடமிருந்து $amt வசூலித்தார்."
                } else {
                    "$actor collected $amt from $target."
                }
            }
            AppNotificationEntity.EVENT_PAYMENT_REVERSED -> {
                title = if (isTamil) "கட்டணம் திரும்பப் பெறப்பட்டது" else "Payment Reversed"
                body = if (isTamil) {
                    "$actor $target வசூல் பதிவை ($amt) திரும்பப் பெற்றார்."
                } else {
                    "$actor reversed a collection of $amt for $target."
                }
            }
            AppNotificationEntity.EVENT_EXPENSE_ADDED -> {
                title = if (isTamil) "புதிய செலவு" else "Expense Added"
                body = if (isTamil) {
                    "$actor $target செலவைப் பதிவு செய்தார் — $amt."
                } else {
                    "$actor recorded a $target expense — $amt."
                }
            }
            AppNotificationEntity.EVENT_EXPENSE_EDITED -> {
                title = if (isTamil) "செலவு திருத்தப்பட்டது" else "Expense Edited"
                body = if (isTamil) {
                    "$actor $target செலவைத் திருத்தினார் ($amt)."
                } else {
                    "$actor updated a $target expense ($amt)."
                }
            }
            AppNotificationEntity.EVENT_EXPENSE_DELETED -> {
                title = if (isTamil) "செலவு நீக்கப்பட்டது" else "Expense Deleted"
                body = if (isTamil) {
                    "$actor $target செலவை நீக்கினார் ($amt)."
                } else {
                    "$actor deleted a $target expense ($amt)."
                }
            }
            AppNotificationEntity.EVENT_WITHDRAWAL_RECORDED -> {
                title = if (isTamil) "பங்குதாரர் எடுப்பு" else "Withdrawal Recorded"
                body = if (isTamil) {
                    "$actor $target பெயரில் $amt எடுப்பைப் பதிவு செய்தார்."
                } else {
                    "$actor recorded a withdrawal of $amt for $target."
                }
            }
            AppNotificationEntity.EVENT_EXTERNAL_LOAN_CREATED -> {
                title = if (isTamil) "புதிய வெளிப்புற கடன்" else "External Loan Recorded"
                body = if (isTamil) {
                    "$actor $target இடமிருந்து $amt வெளிப்புற கடன் பதிவு செய்தார்."
                } else {
                    "$actor recorded an external loan of $amt from $target."
                }
            }
            AppNotificationEntity.EVENT_EXTERNAL_LOAN_REPAID -> {
                title = if (isTamil) "வெளிப்புற கடன் திருப்பிச் செலுத்தல்" else "External Loan Repaid"
                body = if (isTamil) {
                    "$actor கடன் #$target இல் $amt திருப்பிச் செலுத்தினார்."
                } else {
                    "$actor recorded a $amt loan repayment ($target)."
                }
            }
            AppNotificationEntity.EVENT_BORROWING_REQUESTED -> {
                val borrower = meta.optString("borrowerName").ifBlank { actor }
                title = if (isTamil) "கடன் அனுமதி கோரிக்கை" else "Borrowing Approval Request"
                body = if (isTamil) {
                    "$borrower உங்கள் பங்கிலிருந்து $amt கடன் கோரியுள்ளார்."
                } else {
                    "$borrower requested $amt from your available share."
                }
            }
            AppNotificationEntity.EVENT_BORROWING_APPROVED -> {
                val lender = meta.optString("lendingPartnerName").ifBlank { actor }
                title = if (isTamil) "கடன் கோரிக்கை ஏற்கப்பட்டது" else "Borrowing Approved"
                body = if (isTamil) {
                    "$lender உங்கள் $amt கடன் கோரிக்கையை ஏற்று முன்பதிவு செய்தார்."
                } else {
                    "$lender approved and reserved your $amt borrowing request."
                }
            }
            AppNotificationEntity.EVENT_BORROWING_REJECTED -> {
                val reason = meta.optString("rejectionReason")
                title = if (isTamil) "கடன் கோரிக்கை நிராகரிக்கப்பட்டது" else "Borrowing Rejected"
                body = if (isTamil) {
                    if (reason.isNotBlank()) "$actor உங்கள் $amt கடன் கோரிக்கையை நிராகரித்தார் ($reason)."
                    else "$actor உங்கள் $amt கடன் கோரிக்கையை நிராகரித்தார்."
                } else {
                    if (reason.isNotBlank()) "$actor rejected your $amt borrowing request ($reason)."
                    else "$actor rejected your $amt borrowing request."
                }
            }
            AppNotificationEntity.EVENT_BORROWING_CANCELLED -> {
                title = if (isTamil) "கடன் கோரிக்கை ரத்து" else "Borrowing Request Cancelled"
                body = if (isTamil) {
                    "$actor $amt கடன் கோரிக்கையை ரத்து செய்தார்."
                } else {
                    "$actor cancelled the $amt borrowing request."
                }
            }
            AppNotificationEntity.EVENT_BORROWING_EXECUTED -> {
                title = if (isTamil) "கடன் எடுப்பு வழங்கப்பட்டது" else "Borrowing Disbursed"
                body = if (isTamil) {
                    "$actor $amt கடன் எடுப்பைப் பதிவு செய்தார் ($target)."
                } else {
                    "$actor completed a $amt borrowed withdrawal ($target)."
                }
            }
            AppNotificationEntity.EVENT_SETTLEMENT_REPAYMENT_RECORDED -> {
                title = if (isTamil) "தீர்வு திருப்பிச் செலுத்தல் பதிவு" else "Settlement Repayment Recorded"
                body = if (isTamil) {
                    "$actor $amt திருப்பிச் செலுத்தியதை உறுதிப்படுத்த வேண்டும் ($target)."
                } else {
                    "$actor recorded a $amt settlement repayment awaiting confirmation."
                }
            }
            AppNotificationEntity.EVENT_SETTLEMENT_REPAYMENT_CONFIRMED -> {
                title = if (isTamil) "தீர்வு திருப்பிச் செலுத்தல் உறுதி" else "Settlement Repayment Confirmed"
                body = if (isTamil) {
                    "$actor உங்கள் $amt திருப்பிச் செலுத்தலை உறுதிப்படுத்தினார்."
                } else {
                    "$actor confirmed receipt of your $amt settlement repayment."
                }
            }
            AppNotificationEntity.EVENT_SETTLEMENT_REPAYMENT_REJECTED -> {
                val reason = meta.optString("rejectionReason")
                title = if (isTamil) "திருப்பிச் செலுத்தல் நிராகரிக்கப்பட்டது" else "Settlement Repayment Rejected"
                body = if (isTamil) {
                    if (reason.isNotBlank()) "$actor உங்கள் $amt திருப்பிச் செலுத்தலை நிராகரித்தார் ($reason)."
                    else "$actor உங்கள் $amt திருப்பிச் செலுத்தலை நிராகரித்தார்."
                } else {
                    if (reason.isNotBlank()) "$actor rejected your $amt settlement repayment ($reason)."
                    else "$actor rejected your $amt settlement repayment."
                }
            }
            else -> {
                title = if (isTamil) "வணிக அறிவிப்பு" else "Workspace Update"
                body = "$actor · $amt ($target)"
            }
        }

        val categoryLabel = when {
            notification.eventType.startsWith("BORROWING_") ->
                if (isTamil) "கடன் அனுமதி" else "Borrowing"
            notification.eventType.startsWith("SETTLEMENT_") ->
                if (isTamil) "தீர்வு" else "Settlement"
            notification.targetAudience == AppNotificationEntity.AUDIENCE_OWNER_ONLY ->
                if (isTamil) "உரிமையாளர் கண்காணிப்பு" else "Owner Monitor"
            else -> if (isTamil) "செயல்பாடு" else "Activity"
        }

        return PresentedNotification(
            title = title,
            body = body,
            categoryLabel = categoryLabel,
            timestampLabel = JobAuditPresentation.instant(notification.createdAt, isTamil)
        )
    }
}
