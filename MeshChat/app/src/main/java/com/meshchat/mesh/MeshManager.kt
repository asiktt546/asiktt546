package com.meshchat.mesh

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

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
    }

    private val gson = Gson()
    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

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

    // Callback для входящих пакетов
    var onPacketReceived: ((MeshPacket) -> Unit)? = null

    /**
     * Инициализация Wi-Fi Direct.
     */
    fun initialize() {
        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)

        registerReceiver()
        Log.d(TAG, "MeshManager инициализирован")
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
                        Log.d(TAG, "Wi-Fi P2P состояние: ${if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) "включено" else "выключено"}")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        wifiP2pManager?.requestPeers(channel) { peerList ->
                            _peers.value = peerList.deviceList.toList()
                            Log.d(TAG, "Обнаружено устройств: ${peerList.deviceList.size}")
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        wifiP2pManager?.requestConnectionInfo(channel) { info ->
                            _connectionInfo.value = info
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
        _isDiscovering.value = true
        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Поиск устройств начат")
            }

            override fun onFailure(reason: Int) {
                _isDiscovering.value = false
                Log.e(TAG, "Ошибка поиска устройств: $reason")
            }
        })

        // Остановить поиск через 30 секунд
        scope.launch {
            delay(30000)
            _isDiscovering.value = false
        }
    }

    /**
     * Подключиться к устройству Wi-Fi Direct.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Подключение к ${device.deviceName} инициировано")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Ошибка подключения к ${device.deviceName}: $reason")
            }
        })
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
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val json = reader.readLine()
            if (json != null) {
                val packet = gson.fromJson(json, MeshPacket::class.java)
                Log.d(TAG, "Получен пакет: ${packet.type}")
                onPacketReceived?.invoke(packet)
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
            receiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при unregisterReceiver", e)
        }
        scope.cancel()
    }
}
