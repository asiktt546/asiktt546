package com.meshchat.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с сообщениями в локальной базе данных.
 */
@Dao
interface MessageDao {

    /**
     * Вставить сообщение (или заменить, если ID совпадает).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    /**
     * Вставить список сообщений.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    /**
     * Обновить статус сообщения.
     */
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: MessageStatus)

    /**
     * Сохранить расшифрованное содержимое для отправителя/получателя.
     */
    @Query("UPDATE messages SET decryptedContent = :content WHERE id = :messageId")
    suspend fun setDecryptedContent(messageId: String, content: String)

    /**
     * Получить все сообщения для конкретного чата (между текущим пользователем и собеседником).
     */
    @Query("""
        SELECT * FROM messages 
        WHERE (senderId = :peerId OR recipientId = :peerId)
        AND (isMine = 1 OR recipientId = :currentUserId OR senderId = :currentUserId)
        ORDER BY timestamp ASC
    """)
    fun getMessagesForChat(peerId: String, currentUserId: String): Flow<List<Message>>

    /**
     * Получить все чаты (уникальные собеседники).
     */
    @Query("""
        SELECT * FROM messages 
        WHERE isMine = 1 OR decryptedContent IS NOT NULL
        GROUP BY CASE WHEN isMine = 1 THEN recipientId ELSE senderId END
        ORDER BY timestamp DESC
    """)
    fun getRecentChats(): Flow<List<Message>>

    /**
     * Получить все сообщения в банке (для пересылки другим узлам).
     */
    @Query("SELECT * FROM messages WHERE isInBank = 1")
    suspend fun getBankMessages(): List<Message>

    /**
     * Получить сообщение по ID.
     */
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): Message?

    /**
     * Удалить сообщение из банка (после того как получатель прочитал).
     */
    @Query("DELETE FROM messages WHERE id = :messageId AND isMine = 0 AND decryptedContent IS NULL")
    suspend fun removeFromBankIfNotMine(messageId: String)

    /**
     * Пометить сообщение как удалённое из банка.
     */
    @Query("UPDATE messages SET isInBank = 0 WHERE id = :messageId")
    suspend fun removeFromBank(messageId: String)

    /**
     * Удалить все сообщения из банка, которые не принадлежат текущему пользователю.
     */
    @Query("DELETE FROM messages WHERE isInBank = 1 AND isMine = 0 AND decryptedContent IS NULL AND id IN (:messageIds)")
    suspend fun removeBankMessages(messageIds: List<String>)

    /**
     * Получить количество сообщений в банке.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE isInBank = 1")
    fun getBankMessageCount(): Flow<Int>

    /**
     * Получить все сообщения.
     */
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<Message>>
}
