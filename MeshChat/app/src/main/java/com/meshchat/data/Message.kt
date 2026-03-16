package com.meshchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Статус сообщения в mesh-сети.
 */
enum class MessageStatus {
    /** Сообщение создано и ожидает отправки */
    PENDING,
    /** Сообщение отправлено в mesh-сеть и хранится в банке */
    STORED_IN_BANK,
    /** Сообщение доставлено получателю */
    DELIVERED,
    /** Сообщение прочитано получателем */
    READ
}

/**
 * Сущность сообщения в mesh-сети.
 *
 * Каждое сообщение шифруется с использованием AES-256-GCM,
 * а ключ AES шифруется публичным ключом RSA получателя.
 * Все узлы хранят зашифрованные сообщения в «банке» до тех пор,
 * пока адресат не прочтёт сообщение.
 */
@Entity(tableName = "messages")
data class Message(
    /** Уникальный идентификатор сообщения */
    @PrimaryKey
    val id: String,

    /** ID отправителя (публичный ключ или его хеш) */
    val senderId: String,

    /** ID получателя (публичный ключ или его хеш) */
    val recipientId: String,

    /** Зашифрованное содержимое сообщения (Base64) */
    val encryptedContent: String,

    /** Зашифрованный AES-ключ (RSA, Base64) — только получатель может расшифровать */
    val encryptedAesKey: String,

    /** Вектор инициализации для AES-GCM (Base64) */
    val iv: String,

    /** Временная метка создания сообщения */
    val timestamp: Long,

    /** Текущий статус сообщения */
    val status: MessageStatus = MessageStatus.PENDING,

    /** Является ли текущее устройство отправителем */
    val isMine: Boolean = false,

    /** Расшифрованное содержимое (хранится только локально у отправителя/получателя) */
    val decryptedContent: String? = null,

    /** Нужно ли хранить это сообщение в банке (для пересылки) */
    val isInBank: Boolean = true
)
