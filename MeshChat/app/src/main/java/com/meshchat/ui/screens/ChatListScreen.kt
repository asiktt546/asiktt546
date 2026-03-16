package com.meshchat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meshchat.data.Message
import com.meshchat.ui.components.ChatListItem
import com.meshchat.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Экран списка чатов.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<Message>,
    bankMessageCount: Int,
    onChatClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Заголовок
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Hub,
                        contentDescription = null,
                        tint = MeshGreenAccent,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "MeshChat",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }
            },
            actions = {
                // Индикатор банка сообщений
                if (bankMessageCount > 0) {
                    Badge(
                        containerColor = MeshTeal,
                        contentColor = TextOnPrimary
                    ) {
                        Text("📦 $bankMessageCount")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface
            )
        )

        // Информационная полоса
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MeshGreenAccent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Сквозное шифрование • Wi-Fi Direct 2.4 ГГц",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeshGreenAccent
                )
            }
        }

        // Список чатов
        if (chats.isEmpty()) {
            // Пустое состояние
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(80.dp)
                    )
                    Text(
                        text = "Нет сообщений",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Найдите устройства во вкладке «Узлы»\nи начните общаться через mesh-сеть",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(chats) { message ->
                    val peerId = if (message.isMine) message.recipientId else message.senderId
                    val peerName = peerId.take(8)
                    ChatListItem(
                        peerName = peerName,
                        lastMessage = message.decryptedContent ?: "🔒 Зашифровано",
                        timestamp = formatDate(message.timestamp),
                        onClick = { onChatClick(peerId) }
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "сейчас"
        diff < 3_600_000 -> "${diff / 60_000} мин"
        diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(timestamp))
    }
}
