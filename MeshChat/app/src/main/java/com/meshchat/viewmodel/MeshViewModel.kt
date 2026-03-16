package com.meshchat.viewmodel

import android.app.Application
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshchat.crypto.CryptoManager
import com.meshchat.data.*
import com.meshchat.mesh.MeshManager
import com.meshchat.mesh.MessageRouter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel для управления mesh-сетью и сообщениями.
 */
class MeshViewModel(application: Application) : AndroidViewModel(application) {

    private val cryptoManager = CryptoManager()
    private val meshManager = MeshManager(application)
    private val database = MessageDatabase.getInstance(application)
    private val repository = MessageRepository(database.messageDao())
    private val messageRouter = MessageRouter(cryptoManager, meshManager, repository)

    // Состояния UI
    val peers: StateFlow<List<WifiP2pDevice>> = meshManager.peers
    val isDiscovering: StateFlow<Boolean> = meshManager.isDiscovering
    val connectionInfo: StateFlow<WifiP2pInfo?> = meshManager.connectionInfo

    private val _currentChatMessages = MutableStateFlow<List<Message>>(emptyList())
    val currentChatMessages: StateFlow<List<Message>> = _currentChatMessages.asStateFlow()

    val recentChats: Flow<List<Message>> = repository.getRecentChats()
    val bankMessageCount: Flow<Int> = repository.getBankMessageCount()

    private val _nodeId = MutableStateFlow("")
    val nodeId: StateFlow<String> = _nodeId.asStateFlow()

    init {
        initialize()
    }

    /**
     * Инициализация компонентов.
     */
    private fun initialize() {
        cryptoManager.initialize()
        meshManager.initialize()
        messageRouter.initialize()
        _nodeId.value = cryptoManager.getNodeId()
    }

    /**
     * Поиск устройств в mesh-сети.
     */
    fun discoverPeers() {
        meshManager.discoverPeers()
    }

    /**
     * Подключение к устройству.
     */
    fun connectToDevice(device: WifiP2pDevice) {
        meshManager.connectToDevice(device)
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
    fun initiateKeyExchange(deviceName: String) {
        val info = connectionInfo.value
        val hostAddress = info?.groupOwnerAddress?.hostAddress ?: return

        messageRouter.initiateKeyExchange(hostAddress, deviceName)
    }

    /**
     * Получить количество активных узлов.
     */
    fun getActivePeersCount(): Int = peers.value.size

    /**
     * Получить имя устройства.
     */
    fun getDeviceName(): String = android.os.Build.MODEL

    override fun onCleared() {
        super.onCleared()
        meshManager.cleanup()
    }
}
