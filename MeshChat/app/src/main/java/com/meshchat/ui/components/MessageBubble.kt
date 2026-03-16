package com.meshchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.meshchat.data.Message
import com.meshchat.data.MessageStatus
import com.meshchat.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Компонент «пузырь» сообщения в чате.
 */
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isMine = message.isMine
    val bubbleColor = if (isMine) BubbleSent else BubbleReceived
    val textColor = if (isMine) BubbleSentText else BubbleReceivedText
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = if (isMine) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            // Текст сообщения
            Text(
                text = message.decryptedContent ?: "🔒 Зашифровано",
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.decryptedContent != null) textColor else TextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Время и статус
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                if (isMine) {
                    val (icon, tint) = when (message.status) {
                        MessageStatus.PENDING -> Icons.Filled.Schedule to StatusPending
                        MessageStatus.STORED_IN_BANK -> Icons.Filled.Done to TextSecondary
                        MessageStatus.DELIVERED -> Icons.Filled.DoneAll to TextSecondary
                        MessageStatus.READ -> Icons.Filled.DoneAll to MeshGreenAccent
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = message.status.name,
                        modifier = Modifier.size(14.dp),
                        tint = tint
                    )
                }
            }
        }
    }
}

/**
 * Форматирование временной метки.
 */
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
