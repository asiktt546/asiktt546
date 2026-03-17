package com.meshchat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.meshchat.ui.navigation.Screen
import com.meshchat.ui.screens.*
import com.meshchat.ui.theme.*
import com.meshchat.viewmodel.MeshViewModel

/**
 * Главная активность MeshChat.
 *
 * Обеспечивает:
 * - Запрос разрешений для Wi-Fi Direct
 * - Навигацию между экранами
 * - Нижнюю панель навигации
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Разрешения обработаны
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        setContent {
            MeshChatTheme {
                MeshChatApp()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshChatApp(
    meshViewModel: MeshViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Состояния
    val peers by meshViewModel.peers.collectAsState()
    val visiblePeers by meshViewModel.visiblePeers.collectAsState()
    val verifiedMeshDeviceNames by meshViewModel.verifiedMeshDeviceNames.collectAsState()
    val verifiedMeshDeviceAddresses by meshViewModel.verifiedMeshDeviceAddresses.collectAsState()
    val serviceMeshDeviceAddresses by meshViewModel.serviceMeshDeviceAddresses.collectAsState()
    val showOnlyMeshDevices by meshViewModel.showOnlyMeshDevices.collectAsState()
    val readinessMessage by meshViewModel.readinessMessage.collectAsState()
    val isReadyForDiscovery by meshViewModel.isReadyForDiscovery.collectAsState()
    val broadcastStatusMessage by meshViewModel.broadcastStatusMessage.collectAsState()
    val isBroadcastAvailable by meshViewModel.isBroadcastAvailable.collectAsState()
    val isDiscovering by meshViewModel.isDiscovering.collectAsState()
    val connectionInfo by meshViewModel.connectionInfo.collectAsState()
    val peersError by meshViewModel.peersError.collectAsState()
    val chatMessages by meshViewModel.currentChatMessages.collectAsState()
    val nodeId by meshViewModel.nodeId.collectAsState()
    val crashLog by meshViewModel.crashLog.collectAsState()
    val backgroundSyncEnabled by meshViewModel.backgroundSyncEnabled.collectAsState()
    val broadcastEnabled by meshViewModel.broadcastEnabled.collectAsState()
    val backgroundServiceRunning by meshViewModel.backgroundServiceRunning.collectAsState()
    val wifiLockHeld by meshViewModel.wifiLockHeld.collectAsState()
    val wakeLockHeld by meshViewModel.wakeLockHeld.collectAsState()
    val recentChats by meshViewModel.recentChats.collectAsState(initial = emptyList())
    val bankMessageCount by meshViewModel.bankMessageCount.collectAsState(initial = 0)
    val userNickname by meshViewModel.userNickname.collectAsState()
    val avatarPath by meshViewModel.avatarPath.collectAsState()

    val isConnected = connectionInfo?.groupFormed == true

    // Показывать нижнюю навигацию только на главных экранах
    val showBottomBar = currentRoute in listOf(
        Screen.ChatList.route,
        Screen.Peers.route,
        Screen.Settings.route
    )

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = TextPrimary
                ) {
                    NavigationBarItem(
                        selected = currentRoute == Screen.ChatList.route,
                        onClick = {
                            navController.navigate(Screen.ChatList.route) {
                                popUpTo(Screen.ChatList.route) { inclusive = true }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Forum,
                                contentDescription = "Чаты"
                            )
                        },
                        label = { Text("Чаты") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MeshGreenAccent,
                            selectedTextColor = MeshGreenAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = MeshGreen
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == Screen.Peers.route,
                        onClick = {
                            navController.navigate(Screen.Peers.route) {
                                popUpTo(Screen.ChatList.route)
                            }
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (peers.isNotEmpty()) {
                                        Badge(
                                            containerColor = MeshGreenAccent,
                                            contentColor = DarkBackground
                                        ) {
                                            Text(peers.size.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sensors,
                                    contentDescription = "Узлы"
                                )
                            }
                        },
                        label = { Text("Узлы") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MeshGreenAccent,
                            selectedTextColor = MeshGreenAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = MeshGreen
                        )
                    )

                    NavigationBarItem(
                        selected = currentRoute == Screen.Settings.route,
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(Screen.ChatList.route)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Настройки"
                            )
                        },
                        label = { Text("Настройки") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MeshGreenAccent,
                            selectedTextColor = MeshGreenAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = MeshGreen
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.ChatList.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.ChatList.route) {
                ChatListScreen(
                    chats = recentChats,
                    bankMessageCount = bankMessageCount,
                    onChatClick = { peerId ->
                        navController.navigate(Screen.Chat.createRoute(peerId))
                    }
                )
            }

            composable(Screen.Peers.route) {
                PeersScreen(
                    peers = visiblePeers,
                    verifiedMeshDeviceNames = verifiedMeshDeviceNames,
                    verifiedMeshDeviceAddresses = verifiedMeshDeviceAddresses + serviceMeshDeviceAddresses,
                    showOnlyMeshDevices = showOnlyMeshDevices,
                    readinessMessage = readinessMessage,
                    isReadyForDiscovery = isReadyForDiscovery,
                    broadcastStatusMessage = broadcastStatusMessage,
                    isBroadcastAvailable = isBroadcastAvailable,
                    isDiscovering = isDiscovering,
                    isConnected = isConnected,
                    errorMessage = peersError,
                    onShowOnlyMeshDevicesChanged = { meshViewModel.setShowOnlyMeshDevices(it) },
                    onDiscoverClick = { meshViewModel.discoverPeers() },
                    onConnectByNameClick = { name -> meshViewModel.connectToDeviceByName(name) },
                    onConnectClick = { device -> meshViewModel.connectToDevice(device) },
                    onDisconnectClick = { meshViewModel.disconnect() },
                    onErrorShown = { meshViewModel.consumePeersError() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    nodeId = nodeId,
                    deviceName = meshViewModel.getDeviceName(),
                    userNickname = userNickname,
                    avatarPath = avatarPath,
                    activePeers = peers.size,
                    bankMessageCount = bankMessageCount,
                    broadcastEnabled = broadcastEnabled,
                    backgroundSyncEnabled = backgroundSyncEnabled,
                    backgroundServiceRunning = backgroundServiceRunning,
                    wifiLockHeld = wifiLockHeld,
                    wakeLockHeld = wakeLockHeld,
                    onBroadcastToggle = { enabled -> meshViewModel.setBroadcastEnabled(enabled) },
                    onBackgroundWorkToggle = { enabled -> meshViewModel.setBackgroundWorkEnabled(enabled) },
                    onSaveNickname = { nickname -> meshViewModel.updateUserNickname(nickname) },
                    onPickAvatar = { uri -> meshViewModel.updateAvatarFromUri(uri) },
                    onClearAvatar = { meshViewModel.clearAvatar() },
                    crashLog = crashLog,
                    onRefreshCrashLog = { meshViewModel.refreshCrashLog() },
                    onClearCrashLog = { meshViewModel.clearCrashLog() }
                )
            }

            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("peerId") { type = NavType.StringType })
            ) { backStackEntry ->
                val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
                LaunchedEffect(peerId) {
                    meshViewModel.loadChatMessages(peerId)
                }

                ChatScreen(
                    peerId = peerId,
                    peerName = peerId.take(8),
                    messages = chatMessages,
                    onSendMessage = { text -> meshViewModel.sendMessage(text, peerId) },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
