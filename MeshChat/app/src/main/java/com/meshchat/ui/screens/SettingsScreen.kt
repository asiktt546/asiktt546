package com.meshchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meshchat.ui.theme.*

/**
 * Экран настроек и информации.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nodeId: String,
    deviceName: String,
    activePeers: Int,
    bankMessageCount: Int,
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
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MeshGreenAccent,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Настройки",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Карточка профиля
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Аватар
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MeshGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MeshGreenAccent,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Text(
                        text = deviceName.ifEmpty { "Устройство" },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )

                    // ID узла
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = null,
                                tint = MeshGreenAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = nodeId.ifEmpty { "Не определён" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MeshGreenAccent
                            )
                        }
                    }
                }
            }

            // Статистика сети
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Статистика сети",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            icon = Icons.Filled.Devices,
                            value = activePeers.toString(),
                            label = "Активных узлов"
                        )
                        StatItem(
                            icon = Icons.Filled.Inventory2,
                            value = bankMessageCount.toString(),
                            label = "В банке"
                        )
                    }
                }
            }

            // Информация о протоколе
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Протокол",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    ProtocolInfoRow(
                        icon = Icons.Filled.Wifi,
                        label = "Транспорт",
                        value = "Wi-Fi Direct (P2P)"
                    )
                    ProtocolInfoRow(
                        icon = Icons.Filled.SignalCellularAlt,
                        label = "Частота",
                        value = "2.4 ГГц"
                    )
                    ProtocolInfoRow(
                        icon = Icons.Filled.Lock,
                        label = "Шифрование",
                        value = "RSA-2048 + AES-256-GCM"
                    )
                    ProtocolInfoRow(
                        icon = Icons.Filled.VpnKey,
                        label = "Обмен ключами",
                        value = "RSA-OAEP (SHA-256)"
                    )
                    ProtocolInfoRow(
                        icon = Icons.Filled.Storage,
                        label = "Хранение",
                        value = "Распределённый банк"
                    )
                }
            }

            // О приложении
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "О приложении",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "MeshChat v1.0",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Мессенджер на основе mesh-сети Wi-Fi Direct. " +
                                "Сообщения шифруются сквозным шифрованием и хранятся " +
                                "у всех участников сети до прочтения адресатом. " +
                                "После прочтения сообщение удаляется у промежуточных узлов, " +
                                "но сохраняется у отправителя и получателя.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MeshGreenAccent,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ProtocolInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MeshTeal,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}
