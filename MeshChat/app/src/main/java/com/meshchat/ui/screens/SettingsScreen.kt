package com.meshchat.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meshchat.ui.theme.*
import java.io.File

/**
 * Экран настроек и информации.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    nodeId: String,
    deviceName: String,
    userNickname: String,
    avatarPath: String?,
    activePeers: Int,
    bankMessageCount: Int,
    broadcastEnabled: Boolean,
    backgroundSyncEnabled: Boolean,
    backgroundServiceRunning: Boolean,
    wifiLockHeld: Boolean,
    wakeLockHeld: Boolean,
    onBroadcastToggle: (Boolean) -> Unit,
    onBackgroundWorkToggle: (Boolean) -> Unit,
    onSaveNickname: (String) -> Unit,
    onPickAvatar: (String) -> Unit,
    onClearAvatar: () -> Unit,
    crashLog: String,
    onRefreshCrashLog: () -> Unit,
    onClearCrashLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nicknameInput by remember(userNickname) { mutableStateOf(userNickname) }
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onPickAvatar(it.toString()) }
    }

    val avatarBitmap = remember(avatarPath) {
        avatarPath
            ?.takeIf { File(it).exists() }
            ?.let { BitmapFactory.decodeFile(it) }
    }

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
                            .clip(CircleShape)
                            .background(MeshGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                bitmap = avatarBitmap.asImageBitmap(),
                                contentDescription = "Аватар",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MeshGreenAccent,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { avatarPicker.launch("image/*") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshGreenAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Выбрать аватар")
                        }

                        if (avatarBitmap != null) {
                            OutlinedButton(
                                onClick = onClearAvatar,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Удалить")
                            }
                        }
                    }

                    Text(
                        text = deviceName.ifEmpty { "Устройство" },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )

                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it.take(32) },
                        singleLine = true,
                        label = { Text("Ник пользователя") },
                        placeholder = { Text("Введите ник") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeshGreenAccent,
                            unfocusedBorderColor = DividerColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedButton(
                        onClick = { onSaveNickname(nicknameInput) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshGreenAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Сохранить ник")
                    }

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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Трансляция MeshChat",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (backgroundSyncEnabled) {
                            "Включена: по умолчанию активна при работе приложения. Выполняется периодический поиск узлов и синхронизация банка сообщений."
                        } else {
                            "Выключена: устройство не будет поддерживать фоновую трансляцию MeshChat."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Включить трансляцию",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Switch(
                            checked = broadcastEnabled,
                            onCheckedChange = onBroadcastToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MeshGreenAccent,
                                checkedTrackColor = MeshGreen
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Активация фоновой работы приложения",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Switch(
                            checked = backgroundSyncEnabled,
                            onCheckedChange = onBackgroundWorkToggle,
                            enabled = broadcastEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MeshGreenAccent,
                                checkedTrackColor = MeshGreen
                            )
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Статус фоновой активности",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Сервис: ${if (backgroundServiceRunning) "ON" else "OFF"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (backgroundServiceRunning) MeshGreenAccent else TextSecondary
                    )
                    Text(
                        text = "WiFiLock: ${if (wifiLockHeld) "ON" else "OFF"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (wifiLockHeld) MeshGreenAccent else TextSecondary
                    )
                    Text(
                        text = "WakeLock: ${if (wakeLockHeld) "ON" else "OFF"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (wakeLockHeld) MeshGreenAccent else TextSecondary
                    )
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Диагностика вылетов",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Text(
                        text = "После вылета откройте этот блок и отправьте лог.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRefreshCrashLog,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshGreenAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Обновить")
                        }

                        OutlinedButton(
                            onClick = onClearCrashLog,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Очистить")
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkSurfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        SelectionContainer {
                            Text(
                                text = crashLog,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }
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
