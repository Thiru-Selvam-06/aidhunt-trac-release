package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Presentation-only formatter for Expense IDs.
 * Long generated IDs (> 13 chars, e.g. 19-digit snowflake IDs) render as "376594...79155";
 * shorter IDs render in full. Never alters stored or callback IDs.
 */
fun formatShortExpenseId(id: Long): String {
    val raw = id.toString()
    return if (raw.length > 13) "${raw.take(6)}...${raw.takeLast(5)}" else raw
}

@Composable
fun ExpenseBottomRightWaveAccent(
    modifier: Modifier = Modifier,
    waveColor: Color = Color(0xFFDCFCE7).copy(alpha = 0.72f)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.34f, h)
            cubicTo(
                w * 0.50f, h * 0.96f,
                w * 0.60f, h * 0.74f,
                w * 0.73f, h * 0.78f
            )
            cubicTo(
                w * 0.85f, h * 0.81f,
                w * 0.92f, h * 0.52f,
                w, h * 0.56f
            )
            lineTo(w, h)
            close()
        }
        drawPath(path = path, color = waveColor)
    }
}

@Composable
fun ExpenseSummaryBottomWaveAccent(
    modifier: Modifier = Modifier,
    waveColor: Color = Color(0xFFDCFCE7).copy(alpha = 0.75f)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h * 0.86f)
            cubicTo(
                w * 0.16f, h * 0.68f,
                w * 0.30f, h * 0.94f,
                w * 0.46f, h * 0.80f
            )
            cubicTo(
                w * 0.62f, h * 0.66f,
                w * 0.78f, h * 0.92f,
                w, h * 0.72f
            )
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path = path, color = waveColor)
    }
}

@Composable
fun ExpenseAmountPill(
    amountText: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 15f
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFFFEE2E2),
        modifier = modifier
    ) {
        Text(
            text = amountText,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFDC2626),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpensePaymentSourceBadges(
    paidBy: String,
    paidByPartner: String,
    fundingSource: String,
    isTamil: Boolean,
    modifier: Modifier = Modifier
) {
    val isLoanFunded = fundingSource.equals("EXTERNAL_LOAN", ignoreCase = true) ||
        paidBy.trim().equals("Loan Funded", ignoreCase = true)
    val isBusinessPaid = paidBy.trim().equals("Business Paid", ignoreCase = true)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when {
            isLoanFunded -> {
                ExpenseBadgePill(
                    icon = Icons.Default.Payments,
                    text = if (isTamil) "கடன் நிதி" else "Loan Funded",
                    containerColor = Color(0xFFDCFCE7),
                    contentColor = Color(0xFF166534),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                ExpenseBadgePill(
                    icon = Icons.Default.Groups,
                    text = if (isTamil) "நேரடி விநியோகஸ்தர்" else "Direct to Supplier",
                    containerColor = Color(0xFFEDE9FE),
                    contentColor = Color(0xFF4338CA),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            isBusinessPaid -> {
                ExpenseBadgePill(
                    icon = Icons.Default.CreditCard,
                    text = if (isTamil) "வணிகம் செலுத்தியது" else "Business Paid",
                    containerColor = Color(0xFFDCFCE7),
                    contentColor = Color(0xFF166534),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                ExpenseBadgePill(
                    icon = Icons.Default.Groups,
                    text = if (isTamil) "பகிரப்பட்ட செலவு" else "Shared expense",
                    containerColor = Color(0xFFEDE9FE),
                    contentColor = Color(0xFF4338CA),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            else -> {
                val payerLabel = paidByPartner.ifBlank {
                    if (isTamil) "செலுத்தியவர் விவரம் இல்லை" else "Payer unavailable"
                }
                ExpenseBadgePill(
                    icon = Icons.Default.Person,
                    text = payerLabel,
                    containerColor = Color(0xFFECFDF5),
                    contentColor = Color(0xFF15803D),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
fun ExpenseBadgePill(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExpenseReferenceCard(
    expenseId: Long,
    isTamil: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onCopy)
            .testTag("expense_reference_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFECFDF5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isTamil) "செலவு குறிப்பு" else "Expense Reference",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = "ID",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatShortExpenseId(expenseId),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCopy
                        )
                        .testTag("expense_record_identity")
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = if (isTamil) "முழு எண்ணையும் நகலெடுக்க தட்டவும்" else "Tap to copy full ID",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFECFDF5))
                    .testTag("btn_copy_expense_id")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = if (isTamil) "முழு எண்ணையும் நகலெடு" else "Copy full ID",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ExpenseDetailFieldRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFECFDF5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                lineHeight = 18.sp
            )
        }
    }
}
