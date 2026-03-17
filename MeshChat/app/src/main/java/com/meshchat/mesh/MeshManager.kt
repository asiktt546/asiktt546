package com.meshchat.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.meshchat.CrashReporter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

/**
 * Менеджер mesh-сети на основе Wi-Fi Direct.
 *
 * Wi-Fi Direct работает на частоте 2.4 ГГц (и 5 ГГц, если поддерживается),
 * обеспечивая прямое соединение между устройствами без точки доступа.
 *
 * Основные функции:
 * - Обнаружение устройств в радиусе действия
 * - Установление P2P-соединений
 * - Обмен данными через TCP-сокеты
 * - Маршрутизация сообщений в mesh-сети
 */
class MeshManager(private val context: Context) {

    companion object {
        private const val TAG = "MeshManager"
        private const val SERVER_PORT = 8765
        private const val SOCKET_TIMEOUT = 10000
        private const val SERVICE_INSTANCE = "meshchat"
        private const val SERVICE_TYPE = "_meshchat._tcp"
        private const val SERVICE_CODE_KEY = "app_code"
        private const val SERVICE_CODE_VALUE = "MESHCHAT_V1"
    }

    private val gson = Gson()
    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null
    private var localServiceInfo: WifiP2pDnsSdServiceInfo? = null
    private var serviceDiscoveryConfigured: Boolean = false
    @Volatile
    private var isConnectInProgress: Boolean = false
    @Volatile
    private var connectAttemptCounter: Long = 0L
    private val recentP2pEvents = ArrayDeque<String>()
    @Volatile
    private var lastPeersUpdateMs: Long = 0L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Состояния
    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectedNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val connectedNodes: StateFlow<List<MeshNode>> = _connectedNodes.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _thisDevice = MutableStateFlow<WifiP2pDevice?>(null)
    val thisDevice: StateFlow<WifiP2pDevice?> = _thisDevice.asStateFlow()

    private val _meshServiceDeviceAddresses = MutableStateFlow<Set<String>>(emptySet())
    val meshServiceDeviceAddresses: StateFlow<Set<String>> = _meshServiceDeviceAddresses.asStateFlow()

    // Callback для входящих пакетов
    var onPacketReceived: ((MeshPacket) -> Unit)? = null
    var onPacketReceivedWithSender: ((MeshPacket, String?) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private fun reportError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
        CrashReporter.recordHandledError(context, TAG, message, throwable)
        onError?.invoke(message)
    }

    private fun formatP2pFailureReason(reason: Int): String {
        return when (reason) {
            WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct не поддерживается на этом устройстве"
            WifiP2pManager.BUSY -> "Wi-Fi Direct занят другой операцией (например, фоновым поиском). Повторяем попытку"
            WifiP2pManager.ERROR -> "Системная ошибка Wi-Fi Direct (часто: конфликт группы, отклонение приглашения, сбой стека P2P)"
            else -> "Не удалось выполнить операцию Wi-Fi Direct (код: $reason)"
        }
    }

    private fun wifiP2pStatusName(status: Int?): String {
        return when (status) {
            WifiP2pDevice.AVAILABLE -> "AVAILABLE"
            WifiP2pDevice.INVITED -> "INVITED"
            WifiP2pDevice.CONNECTED -> "CONNECTED"
            WifiP2pDevice.FAILED -> "FAILED"
            WifiP2pDevice.UNAVAILABLE -> "UNAVAILABLE"
            null -> "UNKNOWN"
            else -> "CODE_$status"
        }
    }

    private fun safeText(value: String?): String {
        return value?.ifBlank { "-" } ?: "-"
    }

    private fun traceEvent(event: String) {
        val timestamp = System.currentTimeMillis()
        val entry = "$timestamp:$event"
        synchronized(recentP2pEvents) {
            recentP2pEvents.addLast(entry)
            while (recentP2pEvents.size > 30) {
                recentP2pEvents.removeFirst()
            }
        }
        Log.d(TAG, "TRACE $event")
    }

    private fun recentEventsSummary(max: Int = 8): String {
        val items = synchronized(recentP2pEvents) { recentP2pEvents.toList() }
        if (items.isEmpty()) return "none"
        return items.takeLast(max).joinToString(" -> ")
    }

    private fun peersFreshnessSec(): String {
        if (lastPeersUpdateMs <= 0L) return "unknown"
        val ageSec = (System.currentTimeMillis() - lastPeersUpdateMs) / 1000.0
        return String.format(Locale.US, "%.1f", ageSec)
    }

    private fun peersCompactSummary(limit: Int = 6): String {
        val peers = _peers.value
        if (peers.isEmpty()) return "none"

        return peers.take(limit).joinToString(" | ") { peer ->
            val name = safeText(peer.deviceName)
            val addr = safeText(peer.deviceAddress)
            val status = wifiP2pStatusName(peer.status)
            "$name@$addr:$status"
        }
    }

    private fun connectionCompactSummary(): String {
        val info = _connectionInfo.value
        if (info == null) return "none"
        return "groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}, owner=${safeText(info.groupOwnerAddress?.hostAddress)}"
    }

    private fun thisDeviceStatusSummary(): String {
        val device = _thisDevice.value
        return if (device == null) {
            "unknown"
        } else {
            "${safeText(device.deviceName)}@${safeText(device.deviceAddress)}:${wifiP2pStatusName(device.status)}"
        }
    }

    private fun p2pDebugSnapshot(): String {
        return "debug={isDiscovering=${_isDiscovering.value}, connectInProgress=$isConnectInProgress, serviceDiscoveryConfigured=$serviceDiscoveryConfigured, meshServiceSeen=${_meshServiceDeviceAddresses.value.size}, peersAgeSec=${peersFreshnessSec()}, conn=${connectionCompactSummary()}, thisDevice=${thisDeviceStatusSummary()}, peers=${peersCompactSummary()}, recent=${recentEventsSummary()}}"
    }

    private fun buildConnectDiagnostics(device: WifiP2pDevice, reason: Int): String {
        val p2pReady = isWifiP2pEnabled.value
        val hasPerms = hasP2pPermissions()
        val channelReady = channel != null
        val reasonText = formatP2pFailureReason(reason)
        val statusName = wifiP2pStatusName(device.status)
        val attemptId = connectAttemptCounter

        return "Не удалось подключиться к ${device.deviceName.ifEmpty { "устройству" }}: $reasonText | attempt=$attemptId | reasonCode=$reason | peerStatus=$statusName | peerAddress=${safeText(device.deviceAddress)} | p2pEnabled=$p2pReady | channelReady=$channelReady | permissions=$hasPerms | ${p2pDebugSnapshot()}"
    }

    @SuppressLint("MissingPermission")
    private fun stopPeerDiscoverySafely(onComplete: () -> Unit) {
        val manager = wifiP2pManager
        val currentChannel = channel
        if (manager == null || currentChannel == null || !hasP2pPermissions()) {
            onComplete()
            return
        }

        try {
            manager.stopPeerDiscovery(currentChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _isDiscovering.value = false
                    onComplete()
                }
                override fun onFailure(reason: Int) {
                    _isDiscovering.value = false
                    onComplete()
                }
            })
        } catch (_: Exception) {
            _isDiscovering.value = false
            onComplete()
        }
    }

    @SuppressLint("MissingPermission")
    private fun resetP2pSessionSafely(onComplete: () -> Unit) {
        val manager = wifiP2pManager
        val currentChannel = channel
        if (manager == null || currentChannel == null || !hasP2pPermissions()) {
            onComplete()
            return
        }

        try {
            manager.cancelConnect(currentChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    removeGroupSafelyThen(onComplete)
                }

                override fun onFailure(reason: Int) {
                    removeGroupSafelyThen(onComplete)
                }
            })
        } catch (_: Exception) {
            removeGroupSafelyThen(onComplete)
        }
    }

    @SuppressLint("MissingPermission")
    private fun removeGroupSafelyThen(onComplete: () -> Unit) {
        val manager = wifiP2pManager
        val currentChannel = channel
        if (manager == null || currentChannel == null || !hasP2pPermissions()) {
            onComplete()
            return
        }

        try {
            manager.removeGroup(currentChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = onComplete()
                override fun onFailure(reason: Int) = onComplete()
            })
        } catch (_: Exception) {
            onComplete()
        }
    }

    private fun hasP2pPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Инициализация Wi-Fi Direct.
     */
    fun initialize() {
        traceEvent("initialize")
        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
        _isWifiP2pEnabled.value = channel != null

        registerReceiver()
        setupMeshServiceDiscovery()
        Log.d(TAG, "MeshManager инициализирован")
    }

    @SuppressLint("MissingPermission")
    private fun setupMeshServiceDiscovery() {
        val manager = wifiP2pManager
        val currentChannel = channel
        if (manager == null || currentChannel == null || !hasP2pPermissions()) {
            traceEvent("setupMeshServiceDiscovery.skipped")
            serviceDiscoveryConfigured = false
            return
        }

        if (serviceDiscoveryConfigured) return

        try {
            manager.setDnsSdResponseListeners(
                currentChannel,
                { _, _, _ ->
                    // instance response not used currently
                },
                { _, txtRecordMap, device ->
                    val code = txtRecordMap[SERVICE_CODE_KEY]
                    if (code == SERVICE_CODE_VALUE) {
                        val address = device.deviceAddress?.lowercase()
                        if (!address.isNullOrBlank()) {
                            _meshServiceDeviceAddresses.value = _meshServiceDeviceAddresses.value + address
                        }
                    }
                }
            )

            localServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE,
                SERVICE_TYPE,
                mapOf(SERVICE_CODE_KEY to SERVICE_CODE_VALUE)
            )

            localServiceInfo?.let { info ->
                manager.addLocalService(currentChannel, info, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        traceEvent("addLocalService.ok")
                        Log.d(TAG, "MeshChat local service added")
                        serviceDiscoveryConfigured = true
                    }

                    override fun onFailure(reason: Int) {
                        traceEvent("addLocalService.fail.$reason")
                        serviceDiscoveryConfigured = false
                        reportError("Не удалось зарегистрировать сервис MeshChat: ${formatP2pFailureReason(reason)}")
                    }
                })
            }

            serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
            serviceRequest?.let { request ->
                manager.addServiceRequest(currentChannel, request, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        traceEvent("addServiceRequest.ok")
                        Log.d(TAG, "MeshChat service request added")
                        serviceDiscoveryConfigured = true
                    }

                    override fun onFailure(reason: Int) {
                        traceEvent("addServiceRequest.fail.$reason")
                        serviceDiscoveryConfigured = false
                        reportError("Не удалось добавить запрос сервиса MeshChat: ${formatP2pFailureReason(reason)}")
                    }
                })
            }
        } catch (e: Exception) {
            traceEvent("setupMeshServiceDiscovery.exception")
            serviceDiscoveryConfigured = false
            reportError("Ошибка инициализации Service Discovery", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverMeshServices() {
        val manager = wifiP2pManager
        val currentChannel = channel
        if (manager == null || currentChannel == null || !hasP2pPermissions()) return

        // Если сервис discovery не поднялся при старте (например, не было runtime permission),
        // пытаемся переинициализировать перед очередным поиском.
        setupMeshServiceDiscovery()

        try {
            manager.discoverServices(currentChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    traceEvent("discoverServices.ok")
                    Log.d(TAG, "MeshChat service discovery started")
                }

                override fun onFailure(reason: Int) {
                    traceEvent("discoverServices.fail.$reason")
                    Log.w(TAG, "Service discovery error: $reason")
                }
            })
        } catch (e: Exception) {
            traceEvent("discoverServices.exception")
            Log.w(TAG, "discoverMeshServices exception", e)
        }
    }

    /**
     * Регистрация BroadcastReceiver для событий Wi-Fi Direct.
     */
    @SuppressLint("MissingPermission")
    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED
                        )
                        _isWifiP2pEnabled.value = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        traceEvent("broadcast.P2P_STATE.$state")
                        Log.d(TAG, "Wi-Fi P2P состояние: ${if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) "включено" else "выключено"}")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        traceEvent("broadcast.PEERS_CHANGED")
                        val manager = wifiP2pManager
                        val currentChannel = channel
                        if (manager == null || currentChannel == null) {
                            reportError("Wi-Fi Direct не инициализирован")
                            return
                        }
                        if (!hasP2pPermissions()) {
                            reportError("Нет разрешения для получения списка устройств")
                            return
                        }
                        try {
                            manager.requestPeers(currentChannel) { peerList ->
                                _peers.value = peerList.deviceList.toList()
                                lastPeersUpdateMs = System.currentTimeMillis()
                                val activeAddresses = peerList.deviceList
                                    .mapNotNull { it.deviceAddress?.lowercase() }
                                    .toSet()
                                _meshServiceDeviceAddresses.value =
                                    _meshServiceDeviceAddresses.value.intersect(activeAddresses)
                                Log.d(TAG, "Обнаружено устройств: ${peerList.deviceList.size}")
                            }
                        } catch (se: SecurityException) {
                            reportError("Нет разрешения для получения списка устройств", se)
                        } catch (e: Exception) {
                            reportError("Ошибка чтения списка устройств", e)
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        traceEvent("broadcast.CONNECTION_CHANGED")
                        val manager = wifiP2pManager
                        val currentChannel = channel
                        if (manager == null || currentChannel == null) {
                            reportError("Wi-Fi Direct не инициализирован")
                            return
                        }
                        if (!hasP2pPermissions()) {
                            reportError("Нет разрешения для получения информации о соединении")
                            return
                        }
                        try {
                            manager.requestConnectionInfo(currentChannel) { info ->
                                _connectionInfo.value = info
                                traceEvent("requestConnectionInfo.groupFormed=${info?.groupFormed},owner=${info?.isGroupOwner}")
                                if (info?.groupFormed == true) {
                                    Log.d(TAG, "Группа создана. Владелец: ${info.isGroupOwner}")
                                    if (info.isGroupOwner) {
                                        startServer()
                                    } else {
                                        info.groupOwnerAddress?.let { address ->
                                            connectToServer(address.hostAddress ?: "")
                                        }
                                    }
                                }
                            }
                        } catch (se: SecurityException) {
                            reportError("Нет разрешения для получения информации о соединении", se)
                        } catch (e: Exception) {
                            reportError("Ошибка получения информации о соединении", e)
                        }
                    }
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        traceEvent("broadcast.THIS_DEVICE_CHANGED")
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                WifiP2pManager.EXTRA_WIFI_P2P_DEVICE,
                                WifiP2pDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        }
                        _thisDevice.value = device
                    }
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, intentFilter)
        }
    }

    /**
     * Начать поиск устройств Wi-Fi Direct.
     */
    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        if (!hasP2pPermissions()) {
            _isDiscovering.value = false
            reportError("Разрешение на поиск устройств не выдано")
            return
        }

        traceEvent("discoverPeers.request")
        setupMeshServiceDiscovery()

        val manager = wifiP2pManager
        val currentChannel = channel
        if (manager == null || currentChannel == null) {
            _isDiscovering.value = false
            reportError("Wi-Fi Direct не инициализирован")
            return
        }

        _isDiscovering.value = true
        discoverPeersWithRetry(manager, currentChannel, attempt = 0)

        // Остановить поиск через 30 секунд
        scope.launch {
            delay(30000)
            _isDiscovering.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverPeersWithRetry(
        manager: WifiP2pManager,
        currentChannel: WifiP2pManager.Channel,
        attempt: Int
    ) {
        try {
            manager.discoverPeers(currentChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    traceEvent("discoverPeers.ok")
                    Log.d(TAG, "Поиск устройств начат")
                    discoverMeshServices()
                }

                override fun onFailure(reason: Int) {
                    traceEvent("discoverPeers.fail.$reason")
                    if ((reason == WifiP2pManager.BUSY || reason == WifiP2pManager.ERROR) && attempt < 2) {
                        scope.launch {
                            delay(1200)
                            discoverPeersWithRetry(manager, currentChannel, attempt + 1)
                        }
                        return
                    }
                    _isDiscovering.value = false
                    reportError(formatP2pFailureReason(reason))
                }
            })
        } catch (se: SecurityException) {
            _isDiscovering.value = false
            reportError("Нет разрешения на поиск устройств Wi-Fi Direct", se)
        } catch (e: Exception) {
            _isDiscovering.value = false
            reportError("Ошибка запуска поиска устройств", e)
        }
    }

    /**
     * Подключиться к устройству Wi-Fi Direct.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice) {
        if (isConnectInProgress) {
            reportError("Подключение уже выполняется, дождитесь завершения")
            return
        }

        if (!hasP2pPermissions()) {
            reportError("Разрешение на подключение к устройствам не выдано")
            return
        }

        val manager = wifiP2pManager
        val currentChannel = channel
        if (manager == null || currentChannel == null) {
            reportError("Wi-Fi Direct не инициализирован")
            return
        }

        traceEvent("connect.request.${safeText(device.deviceName)}@${safeText(device.deviceAddress)}")
        val deviceAddress = device.deviceAddress?.lowercase().orEmpty()
        val isDeviceCurrentlyVisible = _peers.value.any {
            it.deviceAddress?.lowercase() == deviceAddress
        }
        if (!isDeviceCurrentlyVisible) {
            traceEvent("connect.targetNotVisible")
            reportError("Устройство ${safeText(device.deviceName)} сейчас не видно в списке peers. Обновите поиск и попробуйте снова | ${p2pDebugSnapshot()}")
            discoverPeers()
            return
        }

        isConnectInProgress = true
        connectAttemptCounter += 1
        Log.d(TAG, "Connect attempt=$connectAttemptCounter to ${safeText(device.deviceName)}@${safeText(device.deviceAddress)} | ${p2pDebugSnapshot()}")

        stopPeerDiscoverySafely {
            resetP2pSessionSafely {
                try {
                    manager.requestPeers(currentChannel) { peerList ->
                        traceEvent("connect.requestPeers.beforeConnect")
                        val currentPeers = peerList.deviceList.toList()
                        _peers.value = currentPeers

                        val targetAddress = device.deviceAddress?.lowercase().orEmpty()
                        val liveTarget = currentPeers.firstOrNull {
                            it.deviceAddress?.lowercase() == targetAddress
                        }

                        if (liveTarget == null) {
                            traceEvent("connect.liveTargetMissing")
                            isConnectInProgress = false
                            reportError("Целевое устройство исчезло из актуального peer-list перед подключением. Повторите поиск | ${p2pDebugSnapshot()}")
                            discoverPeers()
                            return@requestPeers
                        }

                        val config = buildConnectConfig(liveTarget, attempt = 0)
                        traceEvent("connect.invoke.attempt0")
                        connectWithRetry(manager, currentChannel, config, liveTarget, attempt = 0)
                    }
                } catch (e: Exception) {
                    traceEvent("connect.requestPeers.exception")
                    isConnectInProgress = false
                    reportError("Ошибка получения актуального списка peers перед подключением", e)
                    discoverPeers()
                }
            }
        }
    }

    private fun buildConnectConfig(device: WifiP2pDevice, attempt: Int): WifiP2pConfig {
        return WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            if (attempt >= 1) {
                wps.setup = WpsInfo.PBC
            }
            if (attempt >= 2) {
                groupOwnerIntent = 0
            }
        }
    }

    private fun reinitializeChannel() {
        try {
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
            _isWifiP2pEnabled.value = channel != null
            serviceDiscoveryConfigured = false
            setupMeshServiceDiscovery()
        } catch (e: Exception) {
            reportError("Не удалось переинициализировать канал Wi-Fi Direct", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectWithRetry(
        manager: WifiP2pManager,
        currentChannel: WifiP2pManager.Channel,
        config: WifiP2pConfig,
        device: WifiP2pDevice,
        attempt: Int
    ) {
        try {
            manager.connect(currentChannel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    traceEvent("connect.success.attempt$attempt")
                    Log.d(TAG, "Подключение к ${device.deviceName} инициировано")
                    isConnectInProgress = false
                }

                override fun onFailure(reason: Int) {
                    traceEvent("connect.fail.attempt$attempt.reason$reason")
                    if (reason == WifiP2pManager.BUSY && attempt < 2) {
                        scope.launch {
                            delay(1200)
                            connectWithRetry(manager, currentChannel, config, device, attempt + 1)
                        }
                        return
                    }

                    if (reason == WifiP2pManager.ERROR && attempt < 2) {
                        resetP2pSessionSafely {
                            val retryConfig = buildConnectConfig(device, attempt + 1)
                            reinitializeChannel()
                            val refreshedManager = wifiP2pManager
                            val refreshedChannel = channel
                            scope.launch {
                                delay(1800)
                                if (refreshedManager != null && refreshedChannel != null) {
                                    connectWithRetry(refreshedManager, refreshedChannel, retryConfig, device, attempt + 1)
                                } else {
                                    isConnectInProgress = false
                                    reportError("Не удалось переподключить канал Wi-Fi Direct для повторной попытки")
                                    restartDiscoveryAfterConnectFailure()
                                }
                            }
                        }
                        return
                    }

                    isConnectInProgress = false
                    reportError(buildConnectDiagnostics(device, reason))
                    restartDiscoveryAfterConnectFailure()
                }
            })
        } catch (se: SecurityException) {
            traceEvent("connect.securityException")
            isConnectInProgress = false
            reportError("Нет разрешения на подключение к устройству", se)
            restartDiscoveryAfterConnectFailure()
        } catch (e: Exception) {
            traceEvent("connect.exception")
            isConnectInProgress = false
            reportError("Ошибка подключения к ${device.deviceName.ifEmpty { "устройству" }}", e)
            restartDiscoveryAfterConnectFailure()
        }
    }

    private fun restartDiscoveryAfterConnectFailure() {
        scope.launch {
            delay(900)
            discoverPeers()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDeviceByName(deviceQuery: String) {
        if (isConnectInProgress) {
            reportError("Подключение уже выполняется, дождитесь завершения")
            return
        }

        val normalized = deviceQuery.trim()
        if (normalized.isEmpty()) {
            reportError("Введите имя устройства или MAC-адрес")
            return
        }

        traceEvent("connectByName.request.$normalized")
        if (_peers.value.isEmpty()) {
            traceEvent("connectByName.peersEmpty")
            reportError("Список устройств пуст. Сначала запустите поиск и дождитесь обнаружения peers | ${p2pDebugSnapshot()}")
            discoverPeers()
            return
        }

        fun normalizeName(value: String?): String {
            return value
                ?.lowercase()
                ?.replace("[^a-z0-9а-яё]".toRegex(), "")
                .orEmpty()
        }

        val normalizedQueryName = normalizeName(normalized)

        val candidate = _peers.value.firstOrNull {
            it.deviceName?.equals(normalized, ignoreCase = true) == true
        } ?: _peers.value.firstOrNull {
            it.deviceName?.contains(normalized, ignoreCase = true) == true
        } ?: _peers.value.firstOrNull {
            it.deviceAddress?.equals(normalized, ignoreCase = true) == true
        } ?: _peers.value.firstOrNull {
            val peerName = normalizeName(it.deviceName)
            peerName.isNotBlank() && (peerName == normalizedQueryName || peerName.contains(normalizedQueryName) || normalizedQueryName.contains(peerName))
        }

        if (candidate == null) {
            traceEvent("connectByName.notFound")
            reportError("Устройство \"$normalized\" не найдено рядом. Проверьте имя/адрес, затем запустите поиск и попробуйте снова | ${p2pDebugSnapshot()}")
            discoverPeers()
            return
        }

        connectToDevice(candidate)
    }

    /**
     * Отключиться от текущей группы.
     */
    fun disconnect() {
        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Отключено от группы")
                _connectionInfo.value = null
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Ошибка отключения: $reason")
            }
        })
    }

    /**
     * Отправить пакет подключённому узлу.
     */
    fun sendPacket(packet: MeshPacket, hostAddress: String) {
        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(hostAddress, SERVER_PORT), SOCKET_TIMEOUT)
                val outputStream = socket.getOutputStream()
                val json = gson.toJson(packet)
                val writer = BufferedWriter(OutputStreamWriter(outputStream))
                writer.write(json)
                writer.newLine()
                writer.flush()
                socket.close()
                Log.d(TAG, "Пакет отправлен: ${packet.type}")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отправки пакета", e)
            }
        }
    }

    /**
     * Запустить TCP-сервер для приёма данных (для владельца группы).
     */
    private fun startServer() {
        scope.launch {
            try {
                val serverSocket = ServerSocket(SERVER_PORT)
                Log.d(TAG, "Сервер запущен на порту $SERVER_PORT")

                while (isActive) {
                    try {
                        val client = serverSocket.accept()
                        launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Ошибка приёма соединения", e)
                        }
                    }
                }

                serverSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка запуска сервера", e)
            }
        }
    }

    /**
     * Обработка подключённого клиента.
     */
    private fun handleClient(socket: Socket) {
        try {
            val senderHost = socket.inetAddress?.hostAddress
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val json = reader.readLine()
            if (json != null) {
                val packet = gson.fromJson(json, MeshPacket::class.java)
                Log.d(TAG, "Получен пакет: ${packet.type}")
                onPacketReceived?.invoke(packet)
                onPacketReceivedWithSender?.invoke(packet, senderHost)
            }
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки клиента", e)
        }
    }

    /**
     * Подключиться к серверу (для клиента группы).
     */
    private fun connectToServer(hostAddress: String) {
        scope.launch {
            try {
                delay(1000) // Даём время серверу запуститься
                Log.d(TAG, "Подключение к серверу: $hostAddress")
                // Начинаем слушать входящие данные на клиентской стороне тоже
                startServer()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка подключения к серверу", e)
            }
        }
    }

    /**
     * Освобождение ресурсов.
     */
    fun cleanup() {
        try {
            val manager = wifiP2pManager
            val currentChannel = channel
            if (manager != null && currentChannel != null) {
                serviceRequest?.let {
                    manager.removeServiceRequest(currentChannel, it, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {}
                        override fun onFailure(reason: Int) {}
                    })
                }
                localServiceInfo?.let {
                    manager.removeLocalService(currentChannel, it, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {}
                        override fun onFailure(reason: Int) {}
                    })
                }
                serviceDiscoveryConfigured = false
            }
            receiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при unregisterReceiver", e)
        }
        scope.cancel()
    }
}
