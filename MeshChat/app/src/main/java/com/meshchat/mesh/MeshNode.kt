package com.meshchat.mesh

import com.meshchat.data.Message

/**
 * Узел mesh-сети.
 * Представляет обнаруженное устройство в сети Wi-Fi Direct.
 */
data class MeshNode(
    /** Уникальный идентификатор узла (хеш публичного ключа) */
    val nodeId: String,

    /** Имя устройства */
    val deviceName: String,

    /** MAC-адрес устройства Wi-Fi Direct */
    val deviceAddress: String,

    /** Публичный ключ RSA в формате Base64 */
    val publicKeyBase64: String,

    /** Подключён ли узел в данный момент */
    val isConnected: Boolean = false,

    /** Последнее время активности */
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Типы пакетов, передаваемых между узлами mesh-сети.
 */
enum class MeshPacketType {
    /** Обмен публичными ключами при обнаружении */
    KEY_EXCHANGE,
    /** Зашифрованное сообщение */
    ENCRYPTED_MESSAGE,
    /** Уведомление о прочтении сообщения (для очистки банка) */
    READ_RECEIPT,
    /** Запрос сообщений из банка */
    BANK_SYNC_REQUEST,
    /** Ответ с сообщениями из банка */
    BANK_SYNC_RESPONSE,
    /** Пинг для поддержания связи */
    PING,
    /** Ответ на пинг */
    PONG
}

/**
 * Пакет данных для передачи между узлами mesh-сети.
 */
data class MeshPacket(
    /** Тип пакета */
    val type: MeshPacketType,
    /** ID отправителя пакета */
    val senderId: String,
    /** Данные пакета (JSON) */
    val payload: String,
    /** Временная метка */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Данные для обмена ключами.
 */
data class KeyExchangeData(
    val nodeId: String,
    val deviceName: String,
    val publicKeyBase64: String
)

/**
 * Уведомление о прочтении сообщения.
 * Распространяется по mesh-сети, чтобы узлы удалили сообщение из банка.
 */
data class ReadReceipt(
    /** ID прочитанного сообщения */
    val messageId: String,
    /** ID получателя, подтвердившего прочтение */
    val readerId: String,
    /** Временная метка прочтения */
    val readTimestamp: Long = System.currentTimeMillis()
)
