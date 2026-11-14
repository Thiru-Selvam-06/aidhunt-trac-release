package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppNotificationEntity
import com.example.ui.util.NotificationPresenter

private enum class NotificationFilter { ALL, UNREAD, APPROVALS_AND_SETTLEMENTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPanelSheet(
    notifications: List<AppNotificationEntity>,
    isOwner: Boolean,
    isTamil: Boolean,
    currency: String = "₹",
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf(NotificationFilter.ALL) }

    val visibleItems = remember(notifications, filter) {
        when (filter) {
            NotificationFilter.ALL -> notifications
            NotificationFilter.UNREAD -> notifications.filter { !it.isRead }
            NotificationFilter.APPROVALS_AND_SETTLEMENTS -> notifications.filter {
                it.eventType.startsWith("BORROWING_") || it.eventType.startsWith("SETTLEMENT_")
            }
        }
    }
    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFF8FAF8)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .testTag("notification_panel_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTamil) "அறிவிப்புகள்" else "Notifications",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = if (isOwner) {
                            if (isTamil) "உரிமையாளர் செயல்பாடு & கடன் கோரிக்கைகள்" else "Owner activity monitor & requests"
                        } else {
                            if (isTamil) "உங்கள் கடன் & தீர்வு அறிவிப்புகள்" else "Your borrowing & settlement alerts"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
                if (unreadCount > 0) {
                    TextButton(
                        onClick = onMarkAllRead,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_mark_all_notifications_read")
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = if (isTamil) "அனைத்தும் படித்தவை" else "Mark all read",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == NotificationFilter.ALL,
                    onClick = { filter = NotificationFilter.ALL },
                    label = { Text(if (isTamil) "அனைத்தும் (${notifications.size})" else "All (${notifications.size})", fontSize = 12.sp) },
                    modifier = Modifier.testTag("notif_filter_all")
                )
                FilterChip(
                    selected = filter == NotificationFilter.UNREAD,
                    onClick = { filter = NotificationFilter.UNREAD },
                    label = { Text(if (isTamil) "படிக்காதவை ($unreadCount)" else "Unread ($unreadCount)", fontSize = 12.sp) },
                    modifier = Modifier.testTag("notif_filter_unread")
                )
                if (isOwner) {
                    FilterChip(
                        selected = filter == NotificationFilter.APPROVALS_AND_SETTLEMENTS,
                        onClick = { filter = NotificationFilter.APPROVALS_AND_SETTLEMENTS },
                        label = { Text(if (isTamil) "கடன்/தீர்வு" else "Loans/Settle", fontSize = 12.sp) },
                        modifier = Modifier.testTag("notif_filter_approvals")
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (visibleItems.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .testTag("notification_empty_state")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = if (isTamil) "அறிவிப்புகள் எதுவும் இல்லை" else "No notifications yet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = if (isTamil) "கிளவுட்டில் உறுதிசெய்யப்பட்ட செயல்பாடுகள் இங்கே தோன்றும்"
                            else "Cloud-confirmed workspace activity and approval alerts appear here",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleItems, key = { it.id }) { item ->
                        val presented = remember(item, isTamil, currency) {
                            NotificationPresenter.present(item, isTamil, currency)
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (item.isRead) Color.White else Color(0xFFECFDF5),
                            border = BorderStroke(
                                1.dp,
                                if (item.isRead) Color(0xFFE2E8F0) else Color(0xFFA7F3D0)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { if (!item.isRead) onMarkRead(item.id) }
                                .testTag("notification_item_${item.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (!item.isRead) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF059669))
                                            )
                                        }
                                        Text(
                                            text = presented.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFE2E8F0)
                                    ) {
                                        Text(
                                            text = presented.categoryLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF334155),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = presented.body,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = presented.timestampLabel,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
