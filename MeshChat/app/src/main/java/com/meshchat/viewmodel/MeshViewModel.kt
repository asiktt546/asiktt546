package com.meshchat.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshchat.CrashReporter
import com.meshchat.crypto.CryptoManager
import com.meshchat.data.*
import com.meshchat.mesh.MeshBackgroundService
import com.meshchat.mesh.BackgroundRuntimeStatus
import com.meshchat.mesh.MeshManager
import com.meshchat.mesh.MessageRouter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel для управления mesh-сетью и сообщениями.
 */
class MeshViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "meshchat_prefs"
        private const val KEY_BROADCAST_ENABLED = "broadcast_enabled"
        private const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
        private const val KEY_USER_NICKNAME = "user_nickname"
        private const val KEY_USER_AVATAR_PATH = "user_avatar_path"
        private const val DEFAULT_BROADCAST_ENABLED = true
        private const val DEFAULT_BACKGROUND_SYNC_ENABLED = true
    }

    private val cryptoManager = CryptoManager()
    private val meshManager = MeshManager(application)
    private val database = MessageDatabase.getInstance(application)
    private val repository = MessageRepository(database.messageDao())
    private val messageRouter = MessageRouter(cryptoManager, meshManager, repository)

    // Состояния UI
    val peers: StateFlow<List<WifiP2pDevice>> = meshManager.peers
    val isDiscovering: StateFlow<Boolean> = meshManager.isDiscovering
    val connectionInfo: StateFlow<WifiP2pInfo?> = meshManager.connectionInfo
    val isWifiP2pEnabled: StateFlow<Boolean> = meshManager.isWifiP2pEnabled
    val thisDevice: StateFlow<WifiP2pDevice?> = meshManager.thisDevice
    val serviceMeshDeviceAddresses: StateFlow<Set<String>> = meshManager.meshServiceDeviceAddresses

    private val _verifiedMeshDeviceNames = MutableStateFlow<Set<String>>(emptySet())
    val verifiedMeshDeviceNames: StateFlow<Set<String>> = _verifiedMeshDeviceNames.asStateFlow()

    private val _verifiedMeshDeviceAddresses = MutableStateFlow<Set<String>>(emptySet())
    val verifiedMeshDeviceAddresses: StateFlow<Set<String>> = _verifiedMeshDeviceAddresses.asStateFlow()

    private val _showOnlyMeshDevices = MutableStateFlow(false)
    val showOnlyMeshDevices: StateFlow<Boolean> = _showOnlyMeshDevices.asStateFlow()

    val visiblePeers: StateFlow<List<WifiP2pDevice>> = combine(
        peers,
        showOnlyMeshDevices,
        verifiedMeshDeviceNames,
        verifiedMeshDeviceAddresses,
        serviceMeshDeviceAddresses
    ) { allPeers, onlyMesh, verifiedNames, verifiedAddresses, serviceAddresses ->
        if (!onlyMesh) return@combine allPeers
        allPeers.filter { peer ->
            isMeshChatPeer(peer, verifiedNames, verifiedAddresses, serviceAddresses)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _peersError = MutableStateFlow<String?>(null)
    val peersError: StateFlow<String?> = _peersError.asStateFlow()

    private val _readinessMessage = MutableStateFlow("Проверка состояния Wi-Fi Direct…")
    val readinessMessage: StateFlow<String> = _readinessMessage.asStateFlow()

    private val _isReadyForDiscovery = MutableStateFlow(false)
    val isReadyForDiscovery: StateFlow<Boolean> = _isReadyForDiscovery.asStateFlow()

    private val _broadcastStatusMessage = MutableStateFlow("Статус трансляции: неизвестно")
    val broadcastStatusMessage: StateFlow<String> = _broadcastStatusMessage.asStateFlow()

    private val _isBroadcastAvailable = MutableStateFlow(false)
    val isBroadcastAvailable: StateFlow<Boolean> = _isBroadcastAvailable.asStateFlow()

    private val _crashLog = MutableStateFlow("")
    val crashLog: StateFlow<String> = _crashLog.asStateFlow()

    private val _backgroundSyncEnabled = MutableStateFlow(false)
    val backgroundSyncEnabled: StateFlow<Boolean> = _backgroundSyncEnabled.asStateFlow()

    private val _broadcastEnabled = MutableStateFlow(false)
    val broadcastEnabled: StateFlow<Boolean> = _broadcastEnabled.asStateFlow()

    private val _userNickname = MutableStateFlow("")
    val userNickname: StateFlow<String> = _userNickname.asStateFlow()

    private val _avatarPath = MutableStateFlow<String?>(null)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    val wifiLockHeld: StateFlow<Boolean> = BackgroundRuntimeStatus.wifiLockHeld
    val wakeLockHeld: StateFlow<Boolean> = BackgroundRuntimeStatus.wakeLockHeld
    val backgroundServiceRunning: StateFlow<Boolean> = BackgroundRuntimeStatus.serviceRunning

    private val prefs by lazy {
        application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
    }

    private val _currentChatMessages = MutableStateFlow<List<Message>>(emptyList())
    val currentChatMessages: StateFlow<List<Message>> = _currentChatMessages.asStateFlow()

    val recentChats: Flow<List<Message>> = repository.getRecentChats()
    val bankMessageCount: Flow<Int> = repository.getBankMessageCount()

    private val _nodeId = MutableStateFlow("")
    val nodeId: StateFlow<String> = _nodeId.asStateFlow()

    private var lastKeyExchangeHostAddress: String? = null
    private var handshakeTimeoutJob: Job? = null
    private var lastKnownNodeUpdateMs: Long = 0L
    private var backgroundPausedForInteractiveOps: Boolean = false
    private var resumeBackgroundJob: Job? = null

    init {
        initialize()
    }

    /**
     * Инициализация компонентов.
     */
    private fun initialize() {
        cryptoManager.initialize()
        meshManager.initialize()
        meshManager.onError = { message ->
            _peersError.value = message
        }
        messageRouter.initialize()
        messageRouter.setLocalDeviceNameProvider {
            _userNickname.value.ifBlank { Build.MODEL }
        }
        messageRouter.onKnownNodeUpdated = { node ->
            val name = node.deviceName.trim()
            if (name.isNotEmpty()) {
                val key = name.lowercase()
                _verifiedMeshDeviceNames.value = _verifiedMeshDeviceNames.value + key
            }
            val connectedPeerAddress = peers.value
                .firstOrNull { it.status == WifiP2pDevice.CONNECTED }
                ?.deviceAddress
                ?.lowercase()
            if (!connectedPeerAddress.isNullOrBlank()) {
                _verifiedMeshDeviceAddresses.value = _verifiedMeshDeviceAddresses.value + connectedPeerAddress
            }
            lastKnownNodeUpdateMs = System.currentTimeMillis()
            handshakeTimeoutJob?.cancel()
        }
        _nodeId.value = cryptoManager.getNodeId()
        refreshCrashLog()
        _userNickname.value = prefs.getString(KEY_USER_NICKNAME, "")?.take(32).orEmpty()
        _avatarPath.value = prefs.getString(KEY_USER_AVATAR_PATH, null)
        _broadcastEnabled.value = prefs.getBoolean(
            KEY_BROADCAST_ENABLED,
            DEFAULT_BROADCAST_ENABLED
        )
        _backgroundSyncEnabled.value = prefs.getBoolean(
            KEY_BACKGROUND_SYNC_ENABLED,
            DEFAULT_BACKGROUND_SYNC_ENABLED
        ) && _broadcastEnabled.value

        if (_backgroundSyncEnabled.value && _broadcastEnabled.value) {
            val started = MeshBackgroundService.start(getApplication())
            if (!started) {
                _peersError.value = "Не удалось запустить фоновый сервис (ограничение Android). Откройте приложение и включите снова"
            }
        } else {
            MeshBackgroundService.stop(getApplication())
        }

        observeConnectionForKeyExchange()
        observeEnvironmentReadiness()
        updateReadinessState()
    }

    private fun observeEnvironmentReadiness() {
        viewModelScope.launch {
            isWifiP2pEnabled.collect {
                updateReadinessState()
                updateBroadcastStatus()
            }
        }

        viewModelScope.launch {
            thisDevice.collect {
                updateBroadcastStatus()
            }
        }

        viewModelScope.launch {
            isDiscovering.collect {
                updateBroadcastStatus()
            }
        }

        viewModelScope.launch {
            backgroundSyncEnabled.collect {
                updateBroadcastStatus()
            }
        }
    }

    private fun observeConnectionForKeyExchange() {
        viewModelScope.launch {
            connectionInfo.collect { info ->
                val hostAddress = info?.groupOwnerAddress?.hostAddress
                val shouldInitiate = info?.groupFormed == true && info.isGroupOwner == false && !hostAddress.isNullOrBlank()
                if (shouldInitiate) {
                    val targetHost = hostAddress ?: return@collect
                    if (lastKeyExchangeHostAddress != hostAddress) {
                        val handshakeStartMs = System.currentTimeMillis()
                        messageRouter.initiateKeyExchange(targetHost)
                        lastKeyExchangeHostAddress = targetHost
                        startHandshakeWatchdog(targetHost, handshakeStartMs)
                    }
                } else {
                    lastKeyExchangeHostAddress = null
                    handshakeTimeoutJob?.cancel()
                }
            }
        }
    }

    private fun startHandshakeWatchdog(hostAddress: String, handshakeStartMs: Long) {
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = viewModelScope.launch {
            delay(15_000)

            val currentInfo = connectionInfo.value
            val currentHost = currentInfo?.groupOwnerAddress?.hostAddress
            val stillConnectedToSameHost = currentInfo?.groupFormed == true && currentHost == hostAddress
            val handshakeMissing = lastKnownNodeUpdateMs < handshakeStartMs

            if (stillConnectedToSameHost && handshakeMissing) {
                _peersError.value = "Подключились к устройству без MeshChat. Соединение сброшено, поиск возобновлен"
                meshManager.disconnect()
                delay(1200)
                meshManager.discoverPeers()
            }
        }
    }

    /**
     * Поиск устройств в mesh-сети.
     */
    fun discoverPeers() {
        pauseBackgroundForInteractiveP2pOps()
        updateReadinessState()
        if (!_isReadyForDiscovery.value) {
            _peersError.value = readinessMessage.value
            scheduleBackgroundResumeIfNeeded()
            return
        }
        meshManager.discoverPeers()
        scheduleBackgroundResumeIfNeeded()
    }

    /**
     * Подключение к устройству.
     */
    fun connectToDevice(device: WifiP2pDevice) {
        pauseBackgroundForInteractiveP2pOps()
        meshManager.connectToDevice(device)
        scheduleBackgroundResumeIfNeeded()
    }

    /**
     * Подключение к устройству по имени из обнаруженного списка.
     */
    fun connectToDeviceByName(deviceName: String) {
        pauseBackgroundForInteractiveP2pOps()
        meshManager.connectToDeviceByName(deviceName)
        scheduleBackgroundResumeIfNeeded()
    }

    fun setShowOnlyMeshDevices(enabled: Boolean) {
        _showOnlyMeshDevices.value = enabled
    }

    private fun isMeshChatPeer(
        peer: WifiP2pDevice,
        verifiedNames: Set<String>,
        verifiedAddresses: Set<String>,
        serviceAddresses: Set<String>
    ): Boolean {
        val name = peer.deviceName?.trim().orEmpty()
        val address = peer.deviceAddress?.lowercase().orEmpty()
        if (address.isNotEmpty() && serviceAddresses.contains(address)) return true
        if (address.isNotEmpty() && verifiedAddresses.contains(address)) return true
        if (name.isNotEmpty() && verifiedNames.contains(name.lowercase())) return true
        return false
    }

    fun consumePeersError() {
        _peersError.value = null
    }

    fun refreshCrashLog() {
        _crashLog.value = CrashReporter.readLog(getApplication())
    }

    fun clearCrashLog() {
        CrashReporter.clearLog(getApplication())
        refreshCrashLog()
    }

    fun startBackgroundSync() {
        if (!_broadcastEnabled.value) {
            _peersError.value = "Сначала включите трансляцию MeshChat"
            return
        }
        val started = MeshBackgroundService.start(getApplication())
        if (started) {
            backgroundPausedForInteractiveOps = false
            _backgroundSyncEnabled.value = true
            prefs.edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, true).apply()
        } else {
            _backgroundSyncEnabled.value = false
            prefs.edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, false).apply()
            _peersError.value = "Android запретил запуск фонового сервиса из фона. Включите его при открытом приложении"
        }
    }

    fun stopBackgroundSync() {
        MeshBackgroundService.stop(getApplication())
        backgroundPausedForInteractiveOps = false
        resumeBackgroundJob?.cancel()
        _backgroundSyncEnabled.value = false
        prefs.edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, false).apply()
    }

    fun setBroadcastEnabled(enabled: Boolean) {
        _broadcastEnabled.value = enabled
        prefs.edit().putBoolean(KEY_BROADCAST_ENABLED, enabled).apply()

        if (!enabled) {
            stopBackgroundSync()
            meshManager.disconnect()
            _peersError.value = "Трансляция MeshChat отключена"
        } else {
            _peersError.value = "Трансляция MeshChat включена"
            val shouldRunBackground = prefs.getBoolean(
                KEY_BACKGROUND_SYNC_ENABLED,
                DEFAULT_BACKGROUND_SYNC_ENABLED
            )
            if (shouldRunBackground) {
                startBackgroundSync()
            }
        }
        updateReadinessState()
        updateBroadcastStatus()
    }

    fun setBackgroundWorkEnabled(enabled: Boolean) {
        if (enabled) startBackgroundSync() else stopBackgroundSync()
    }

    private fun pauseBackgroundForInteractiveP2pOps() {
        if (_backgroundSyncEnabled.value) {
            MeshBackgroundService.stop(getApplication())
            backgroundPausedForInteractiveOps = true
            resumeBackgroundJob?.cancel()
        }
    }

    private fun scheduleBackgroundResumeIfNeeded() {
        if (!_backgroundSyncEnabled.value || !backgroundPausedForInteractiveOps) return

        resumeBackgroundJob?.cancel()
        resumeBackgroundJob = viewModelScope.launch {
            delay(35_000)
            if (_backgroundSyncEnabled.value && backgroundPausedForInteractiveOps) {
                val started = MeshBackgroundService.start(getApplication())
                if (started) {
                    backgroundPausedForInteractiveOps = false
                } else {
                    _peersError.value = "Фоновый сервис не возобновлен (ограничение Android). Откройте приложение для ручного запуска"
                }
            }
        }
    }

    fun updateUserNickname(newNickname: String) {
        val normalized = newNickname.trim().take(32)
        _userNickname.value = normalized
        prefs.edit().putString(KEY_USER_NICKNAME, normalized).apply()
        _peersError.value = if (normalized.isBlank()) {
            "Ник сброшен. Используется имя устройства"
        } else {
            "Ник сохранен: $normalized"
        }
    }

    fun updateAvatarFromUri(uriString: String) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val uri = Uri.parse(uriString)
                val input = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Не удалось открыть изображение")

                val avatarsDir = File(app.filesDir, "profile")
                if (!avatarsDir.exists()) {
                    avatarsDir.mkdirs()
                }

                val avatarFile = File(avatarsDir, "avatar.jpg")
                input.use { inputStream ->
                    avatarFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                }

                _avatarPath.value = avatarFile.absolutePath
                prefs.edit().putString(KEY_USER_AVATAR_PATH, avatarFile.absolutePath).apply()
                _peersError.value = "Аватар обновлен"
            } catch (e: Exception) {
                _peersError.value = "Не удалось установить аватар"
                CrashReporter.recordHandledError(app, "MeshViewModel", "Ошибка установки аватара", e)
            }
        }
    }

    fun clearAvatar() {
        val currentPath = _avatarPath.value
        if (!currentPath.isNullOrBlank()) {
            runCatching { File(currentPath).delete() }
        }
        _avatarPath.value = null
        prefs.edit().remove(KEY_USER_AVATAR_PATH).apply()
        _peersError.value = "Аватар удален"
    }

    /**
     * Отключение от сети.
     */
    fun disconnect() {
        meshManager.disconnect()
    }

    /**
     * Загрузить сообщения для чата с конкретным пользователем.
     */
    fun loadChatMessages(peerId: String) {
        viewModelScope.launch {
            repository.getMessagesForChat(peerId, cryptoManager.getNodeId())
                .collect { messages ->
                    _currentChatMessages.value = messages
                }
        }
    }

    /**
     * Отправить сообщение.
     */
    fun sendMessage(text: String, recipientId: String) {
        val info = connectionInfo.value
        val hostAddress = info?.groupOwnerAddress?.hostAddress ?: return

        messageRouter.sendMessage(text, recipientId, hostAddress)
    }

    /**
     * Отправить уведомление о прочтении.
     */
    fun sendReadReceipt(messageId: String) {
        val info = connectionInfo.value
        val hostAddress = info?.groupOwnerAddress?.hostAddress ?: return

        messageRouter.sendReadReceipt(messageId, hostAddress)
    }

    /**
     * Инициировать обмен ключами.
     */
    fun initiateKeyExchange() {
        val info = connectionInfo.value
        val hostAddress = info?.groupOwnerAddress?.hostAddress ?: return

        messageRouter.initiateKeyExchange(hostAddress)
    }

    /**
     * Получить количество активных узлов.
     */
    fun getActivePeersCount(): Int = peers.value.size

    /**
     * Получить имя устройства.
     */
    fun getDeviceName(): String = _userNickname.value.ifBlank { Build.MODEL }

    private fun updateReadinessState() {
        when {
            !_broadcastEnabled.value -> {
                _isReadyForDiscovery.value = false
                _readinessMessage.value = "Трансляция MeshChat отключена в настройках"
            }
            !hasRequiredPermissions() -> {
                _isReadyForDiscovery.value = false
                _readinessMessage.value = "Нет нужных разрешений для Wi-Fi Direct"
            }
            !isWifiP2pEnabled.value -> {
                _isReadyForDiscovery.value = false
                _readinessMessage.value = "Wi-Fi Direct выключен в системе"
            }
            !isLocationReadyForDiscovery() -> {
                _isReadyForDiscovery.value = false
                _readinessMessage.value = "Для поиска включите геолокацию на устройстве"
            }
            else -> {
                _isReadyForDiscovery.value = true
                _readinessMessage.value = "Готово к поиску устройств"
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val app = getApplication<Application>()
        val nearbyGranted = ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nearbyGranted || fineLocationGranted
        } else {
            fineLocationGranted
        }
    }

    private fun isLocationReadyForDiscovery(): Boolean {
        val app = getApplication<Application>()
        val locationManager = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun updateBroadcastStatus() {
        if (!_broadcastEnabled.value) {
            _isBroadcastAvailable.value = false
            _broadcastStatusMessage.value = "Трансляция отключена пользователем"
            return
        }

        if (!isWifiP2pEnabled.value) {
            _isBroadcastAvailable.value = false
            _broadcastStatusMessage.value = "Трансляция недоступна: Wi-Fi Direct выключен"
            return
        }

        if (!hasRequiredPermissions()) {
            _isBroadcastAvailable.value = false
            _broadcastStatusMessage.value = "Трансляция недоступна: нет разрешений Wi-Fi Direct"
            return
        }

        if (!isLocationReadyForDiscovery()) {
            _isBroadcastAvailable.value = false
            _broadcastStatusMessage.value = "Трансляция недоступна: включите геолокацию"
            return
        }

        when (thisDevice.value?.status) {
            WifiP2pDevice.AVAILABLE -> {
                _isBroadcastAvailable.value = true
                _broadcastStatusMessage.value = "Трансляция доступна: устройство видно для соседей"
            }
            WifiP2pDevice.INVITED -> {
                _isBroadcastAvailable.value = true
                _broadcastStatusMessage.value = "Трансляция доступна: ожидается подтверждение подключения"
            }
            WifiP2pDevice.CONNECTED -> {
                _isBroadcastAvailable.value = true
                _broadcastStatusMessage.value = "Трансляция активна: устройство подключено"
            }
            WifiP2pDevice.FAILED -> {
                _isBroadcastAvailable.value = false
                _broadcastStatusMessage.value = "Трансляция недоступна: ошибка Wi-Fi Direct"
            }
            WifiP2pDevice.UNAVAILABLE -> {
                // На ряде устройств этот статус может держаться долго даже при рабочем P2P,
                // поэтому учитываем общий контекст работы вместо жёсткой ошибки.
                if (isDiscovering.value || backgroundSyncEnabled.value) {
                    _isBroadcastAvailable.value = true
                    _broadcastStatusMessage.value = "Трансляция активируется: выполняется поиск/фоновая работа"
                } else {
                    _isBroadcastAvailable.value = true
                    _broadcastStatusMessage.value = "Трансляция готова: ожидание поиска или подключений"
                }
            }
            else -> {
                _isBroadcastAvailable.value = true
                _broadcastStatusMessage.value = if (isDiscovering.value || backgroundSyncEnabled.value) {
                    "Трансляция активна: идет обнаружение соседей"
                } else {
                    "Трансляция готова: запустите поиск для обнаружения соседей"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        handshakeTimeoutJob?.cancel()
        resumeBackgroundJob?.cancel()
        meshManager.cleanup()
    }
}
