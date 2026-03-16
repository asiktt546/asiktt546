package com.meshchat.ui.screens

import android.net.wifi.p2p.WifiP2pDevice
import androidx.compose.animation.animateContentSize
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
import com.meshchat.ui.components.PeerItem
import com.meshchat.ui.theme.*

/**
 * Экран обнаружения и управления узлами mesh-сети.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersScreen(
    peers: List<WifiP2pDevice>,
    isDiscovering: Boolean,
    isConnected: Boolean,
    onDiscoverClick: () -> Unit,
    onConnectClick: (WifiP2pDevice) -> Unit,
    onDisconnectClick: () -> Unit,
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
                        imageVector = Icons.Filled.Sensors,
                        contentDescription = null,
                        tint = MeshGreenAccent,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Узлы сети",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkSurface
            )
        )

        // Информационная карточка о протоколе
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = null,
                        tint = MeshGreenAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Wi-Fi Direct • 2.4 ГГц",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "Прямое соединение между устройствами без точки доступа. " +
                            "Все данные шифруются сквозным шифрованием RSA + AES-256-GCM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        // Кнопки управления
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDiscoverClick,
                modifier = Modifier.weight(1f),
                enabled = !isDiscovering,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshGreen,
                    contentColor = TextOnPrimary
                )
            ) {
                if (isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MeshGreenAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Поиск…")
                } else {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Найти устройства")
                }
            }

            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnectClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ErrorColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Отключить")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Количество найденных устройств
        if (peers.isNotEmpty()) {
            Text(
                text = "Найдено устройств: ${peers.size}",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Список устройств
        if (peers.isEmpty()) {
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
                        imageVector = Icons.Filled.WifiFind,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(80.dp)
                    )
                    Text(
                        text = "Нет доступных устройств",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Нажмите «Найти устройства»,\nчтобы обнаружить узлы mesh-сети\nв радиусе действия Wi-Fi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(peers) { device ->
                    PeerItem(
                        deviceName = device.deviceName ?: "Неизвестно",
                        deviceAddress = device.deviceAddress ?: "",
                        isConnected = device.status == WifiP2pDevice.CONNECTED,
                        onClick = { onConnectClick(device) }
                    )
                }
            }
        }
    }
}
