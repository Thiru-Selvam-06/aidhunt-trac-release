package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.AccessControlDocument
import com.example.data.auth.AccountStatus
import com.example.data.entity.AppSettingsEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AccountSuspendedOrExpiredScreen(
    accessControl: AccessControlDocument,
    settings: AppSettingsEntity,
    isTamil: Boolean,
    isSyncing: Boolean,
    onRefreshStatus: () -> Unit,
    onLogout: () -> Unit
) {
    val status = accessControl.getResolvedStatus()
    val isExpired = status == AccountStatus.EXPIRED

    val statusBadgeBg = if (isExpired) Color(0xFFFEF3C7) else Color(0xFFFEE2E2)
    val statusBadgeText = if (isExpired) Color(0xFFB45309) else Color(0xFFB91C1C)
    val iconBg = if (isExpired) Color(0xFFFFFBEB) else Color(0xFFFEF2F2)
    val iconColor = if (isExpired) Color(0xFFD97706) else Color(0xFFDC2626)

    val title = if (isExpired) {
        if (isTamil) "பணிமனை சந்தா காலாவதியானது" else "Workspace Subscription Expired"
    } else {
        if (isTamil) "பணிமனை கணக்கு இடைநிறுத்தப்பட்டது" else "Workspace Account Suspended"
    }

    val description = if (isExpired) {
        if (isTamil) {
            "உங்கள் பணிமனைக்கான பயன்பாட்டு சந்தா காலம் முடிவடைந்துவிட்டது. தரவு பாதுகாப்பாக உள்ளது. செயல்பாடுகளைத் தொடர நிர்வாகத்தை அல்லது உரிமையாளரைத் தொடர்பு கொள்ளவும்."
        } else {
            "Your workspace subscription period has expired. All business records remain completely safe. Please contact the administrator or business owner to renew access."
        }
    } else {
        if (isTamil) {
            "நிர்வாகத்தால் உங்கள் பணிமனை கணக்கு தற்காலிகமாக இடைநிறுத்தப்பட்டுள்ளது. அணுகலை மீட்டமைக்க ஆதரவுக் குழுவை அல்லது உரிமையாளரைத் தொடர்பு கொள்ளவும்."
        } else {
            "Your workspace account has been suspended by administration. Operational access is currently locked. Please contact support or your business owner to restore access."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpired) Icons.Default.Warning else Icons.Default.Lock,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBadgeBg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isExpired) {
                            if (isTamil) "காலாவதி (EXPIRED)" else "EXPIRED"
                        } else {
                            if (isTamil) "இடைநிறுத்தப்பட்டது (SUSPENDED)" else "SUSPENDED"
                        },
                        color = statusBadgeText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // Title
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                // Description
                Text(
                    text = description,
                    fontSize = 13.5.sp,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                // Metadata Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (settings.businessName.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isTamil) "வணிகம்:" else "Business:",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = settings.businessName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                        if (settings.workspaceId.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isTamil) "பணிமனை ID:" else "Workspace ID:",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = settings.workspaceId.take(16),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                        if (accessControl.validUntil > 0L) {
                            val expDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                .format(Date(accessControl.validUntil))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isTamil) "காலாவதி தேதி:" else "Valid Until:",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = expDateStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Refresh Status Button
                Button(
                    onClick = onRefreshStatus,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5132)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isTamil) "நிலையை மீண்டும் சரிபார்" else "Check Status / Refresh",
                        fontWeight = FontWeight.Bold
                    )
                }

                // Log out Button
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTamil) "வெளியேறு (Log Out)" else "Log Out",
                        color = Color(0xFF64748B)
                    )
                }

                // Safety Assurance
                Text(
                    text = if (isTamil) {
                        "✓ உங்கள் உள்ளூர் பதிவுகள் மற்றும் வாடிக்கையாளர் தரவு பாதுகாப்பாக சேமிக்கப்பட்டுள்ளன."
                    } else {
                        "✓ Your local records and customer data remain safely preserved on this device."
                    },
                    fontSize = 11.5.sp,
                    color = Color(0xFF16A34A),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
