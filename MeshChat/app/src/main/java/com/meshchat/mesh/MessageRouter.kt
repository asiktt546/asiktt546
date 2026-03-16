package com.meshchat.mesh

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.meshchat.crypto.CryptoManager
import com.meshchat.crypto.EncryptedPayload
import com.meshchat.data.Message
import com.meshchat.data.MessageRepository
import com.meshchat.data.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.PublicKey
import java.util.UUID

/**
 * Маршрутизатор сообщений в mesh-сети.
 *
 * Отвечает за:
 * - Шифрование и отправку сообщений
 * - Приём и расшифровку сообщений
 * - Хранение сообщений в банке до прочтения адресатом
 * - Рассылку уведомлений о прочтении для очистки банка
 * - Синхронизацию банка сообщений между узлами
 */
class MessageRouter(
    private val cryptoManager: CryptoManager,
    private val meshManager: MeshManager,
    private val repository: MessageRepository
) {

    companion object {
        private const val TAG = "MessageRouter"
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Карта известных узлов: nodeId -> MeshNode */
    private val knownNodes = mutableMapOf<String, MeshNode>()

    /** Карта публичных ключей: nodeId -> PublicKey */
    private val publicKeys = mutableMapOf<String, PublicKey>()

    /**
     * Инициализация роутера.
     */
    fun initialize() {
        meshManager.onPacketReceived = { packet ->
            handleIncomingPacket(packet)
        }
    }

    /**
     * Получить ID текущего узла.
     */
    fun getMyNodeId(): String = cryptoManager.getNodeId()

    /**
     * Получить список известных узлов.
     */
    fun getKnownNodes(): Map<String, MeshNode> = knownNodes.toMap()

    /**
     * Отправить зашифрованное сообщение.
     *
     * @param text текст сообщения
     * @param recipientNodeId ID узла-получателя
     * @param hostAddress адрес для отправки
     */
    fun sendMessage(text: String, recipientNodeId: String, hostAddress: String) {
        scope.launch {
            try {
                val recipientKey = publicKeys[recipientNodeId]
                    ?: throw IllegalStateException("Публичный ключ получателя $recipientNodeId не найден")

                // 1. Шифруем сообщение
                val payload = cryptoManager.encryptMessage(text, recipientKey)

                // 2. Создаём объект сообщения
                val messageId = UUID.randomUUID().toString()
                val message = Message(
                    id = messageId,
                    senderId = getMyNodeId(),
                    recipientId = recipientNodeId,
                    encryptedContent = payload.encryptedContent,
                    encryptedAesKey = payload.encryptedAesKey,
                    iv = payload.iv,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.STORED_IN_BANK,
                    isMine = true,
                    decryptedContent = text, // Отправитель хранит расшифрованную копию
                    isInBank = true
                )

                // 3. Сохраняем локально
                repository.saveMessage(message)

                // 4. Отправляем в mesh-сеть
                val meshPacket = MeshPacket(
                    type = MeshPacketType.ENCRYPTED_MESSAGE,
                    senderId = getMyNodeId(),
                    payload = gson.toJson(message.copy(decryptedContent = null, isMine = false))
                )
                meshManager.sendPacket(meshPacket, hostAddress)

                Log.d(TAG, "Сообщение отправлено: $messageId -> $recipientNodeId")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка отправки сообщения", e)
            }
        }
    }

    /**
     * Обработка входящих пакетов.
     */
    private fun handleIncomingPacket(packet: MeshPacket) {
        scope.launch {
            when (packet.type) {
                MeshPacketType.KEY_EXCHANGE -> handleKeyExchange(packet)
                MeshPacketType.ENCRYPTED_MESSAGE -> handleEncryptedMessage(packet)
                MeshPacketType.READ_RECEIPT -> handleReadReceipt(packet)
                MeshPacketType.BANK_SYNC_REQUEST -> handleBankSyncRequest(packet)
                MeshPacketType.BANK_SYNC_RESPONSE -> handleBankSyncResponse(packet)
                MeshPacketType.PING -> { /* Ответить PONG */ }
                MeshPacketType.PONG -> { /* Обновить lastSeen */ }
            }
        }
    }

    /**
     * Обработка обмена ключами.
     */
    private fun handleKeyExchange(packet: MeshPacket) {
        try {
            val data = gson.fromJson(packet.payload, KeyExchangeData::class.java)
            val publicKey = cryptoManager.publicKeyFromBase64(data.publicKeyBase64)
            publicKeys[data.nodeId] = publicKey

            val node = MeshNode(
                nodeId = data.nodeId,
                deviceName = data.deviceName,
                deviceAddress = "",
                publicKeyBase64 = data.publicKeyBase64,
                isConnected = true
            )
            knownNodes[data.nodeId] = node

            Log.d(TAG, "Получен ключ от узла: ${data.nodeId} (${data.deviceName})")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки обмена ключами", e)
        }
    }

    /**
     * Обработка зашифрованного сообщения.
     */
    private fun handleEncryptedMessage(packet: MeshPacket) {
        try {
            val message = gson.fromJson(packet.payload, Message::class.java)

            if (message.recipientId == getMyNodeId()) {
                // Сообщение предназначено нам — расшифровываем
                val payload = EncryptedPayload(
                    encryptedContent = message.encryptedContent,
                    encryptedAesKey = message.encryptedAesKey,
                    iv = message.iv
                )
                val decryptedText = cryptoManager.decryptMessage(payload)

                val decryptedMessage = message.copy(
                    status = MessageStatus.DELIVERED,
                    decryptedContent = decryptedText,
                    isInBank = false
                )

                scope.launch {
                    repository.saveMessage(decryptedMessage)
                }

                Log.d(TAG, "Сообщение расшифровано: ${message.id}")
            } else {
                // Сообщение не для нас — сохраняем в банк для пересылки
                val bankMessage = message.copy(
                    isInBank = true,
                    isMine = false,
                    decryptedContent = null
                )

                scope.launch {
                    repository.saveMessage(bankMessage)
                }

                Log.d(TAG, "Сообщение сохранено в банк: ${message.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки сообщения", e)
        }
    }

    /**
     * Обработка уведомления о прочтении.
     * Удаляет сообщение из банка у узлов, которым оно не предназначалось.
     */
    private fun handleReadReceipt(packet: MeshPacket) {
        try {
            val receipt = gson.fromJson(packet.payload, ReadReceipt::class.java)

            scope.launch {
                // Удаляем сообщение из банка, если мы не отправитель и не получатель
                repository.removeFromBankIfNotMine(receipt.messageId)
                Log.d(TAG, "Сообщение удалено из банка по уведомлению: ${receipt.messageId}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки уведомления о прочтении", e)
        }
    }

    /**
     * Отправить уведомление о прочтении сообщения.
     * Вызывается, когда получатель открывает и читает сообщение.
     */
    fun sendReadReceipt(messageId: String, hostAddress: String) {
        scope.launch {
            val receipt = ReadReceipt(
                messageId = messageId,
                readerId = getMyNodeId()
            )

            val packet = MeshPacket(
                type = MeshPacketType.READ_RECEIPT,
                senderId = getMyNodeId(),
                payload = gson.toJson(receipt)
            )

            meshManager.sendPacket(packet, hostAddress)

            // Обновляем статус локально
            repository.updateMessageStatus(messageId, MessageStatus.READ)
            Log.d(TAG, "Уведомление о прочтении отправлено: $messageId")
        }
    }

    /**
     * Обработка запроса синхронизации банка.
     */
    private fun handleBankSyncRequest(packet: MeshPacket) {
        scope.launch {
            val bankMessages = repository.getBankMessages()
            val response = MeshPacket(
                type = MeshPacketType.BANK_SYNC_RESPONSE,
                senderId = getMyNodeId(),
                payload = gson.toJson(bankMessages)
            )
            // Отправляем банк сообщений запросившему узлу
            val info = meshManager.connectionInfo.value
            info?.groupOwnerAddress?.hostAddress?.let { address ->
                meshManager.sendPacket(response, address)
            }
        }
    }

    /**
     * Обработка ответа синхронизации банка.
     */
    private fun handleBankSyncResponse(packet: MeshPacket) {
        try {
            val type = object : TypeToken<List<Message>>() {}.type
            val messages: List<Message> = gson.fromJson(packet.payload, type)

            scope.launch {
                for (message in messages) {
                    val existing = repository.getMessageById(message.id)
                    if (existing == null) {
                        if (message.recipientId == getMyNodeId()) {
                            // Сообщение для нас — расшифровываем
                            val payload = EncryptedPayload(
                                encryptedContent = message.encryptedContent,
                                encryptedAesKey = message.encryptedAesKey,
                                iv = message.iv
                            )
                            val decryptedText = cryptoManager.decryptMessage(payload)
                            repository.saveMessage(
                                message.copy(
                                    decryptedContent = decryptedText,
                                    status = MessageStatus.DELIVERED,
                                    isInBank = false
                                )
                            )
                        } else {
                            // Сохраняем в банк
                            repository.saveMessage(message.copy(isInBank = true))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка синхронизации банка", e)
        }
    }

    /**
     * Инициировать обмен ключами с подключённым узлом.
     */
    fun initiateKeyExchange(hostAddress: String, deviceName: String) {
        val data = KeyExchangeData(
            nodeId = getMyNodeId(),
            deviceName = deviceName,
            publicKeyBase64 = cryptoManager.getPublicKeyBase64()
        )

        val packet = MeshPacket(
            type = MeshPacketType.KEY_EXCHANGE,
            senderId = getMyNodeId(),
            payload = gson.toJson(data)
        )

        meshManager.sendPacket(packet, hostAddress)
    }
}
