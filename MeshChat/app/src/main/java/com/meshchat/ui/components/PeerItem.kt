package com.meshchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshchat.ui.theme.*

/**
 * Компонент элемента узла mesh-сети.
 */
@Composable
fun PeerItem(
    deviceName: String,
    deviceAddress: String,
    isMeshChatCompatible: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAvailableForMeshChat = isConnected || isMeshChatCompatible

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Иконка статуса
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isAvailableForMeshChat) MeshGreen else DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAvailableForMeshChat) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = if (isAvailableForMeshChat) "Доступно" else "Недоступно",
                    tint = if (isAvailableForMeshChat) MeshGreenAccent else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Информация об устройстве
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = deviceName.ifEmpty { "Неизвестное устройство" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isMeshChatCompatible) {
                        Surface(
                            color = MeshGreen,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "MeshChat",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextOnPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = deviceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Индикатор статуса
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) StatusOnline else StatusOffline)
            )
        }
    }
}

/**
 * Компонент элемента чата в списке.
 */
@Composable
fun ChatListItem(
    peerName: String,
    lastMessage: String,
    timestamp: String,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = DarkCard
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Аватар
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MeshGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peerName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextOnPrimary
                )
            }

            // Содержимое
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MeshGreenAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkBackground
                            )
                        }
                    }
                }
            }
        }
    }
}
