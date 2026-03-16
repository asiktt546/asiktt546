package com.meshchat.data

import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для работы с сообщениями.
 * Обеспечивает единую точку доступа к данным сообщений.
 */
class MessageRepository(private val messageDao: MessageDao) {

    /** Получить сообщения для чата с конкретным пользователем */
    fun getMessagesForChat(peerId: String, currentUserId: String): Flow<List<Message>> =
        messageDao.getMessagesForChat(peerId, currentUserId)

    /** Получить список последних чатов */
    fun getRecentChats(): Flow<List<Message>> =
        messageDao.getRecentChats()

    /** Получить количество сообщений в банке */
    fun getBankMessageCount(): Flow<Int> =
        messageDao.getBankMessageCount()

    /** Сохранить сообщение */
    suspend fun saveMessage(message: Message) =
        messageDao.insertMessage(message)

    /** Сохранить список сообщений */
    suspend fun saveMessages(messages: List<Message>) =
        messageDao.insertMessages(messages)

    /** Получить все сообщения из банка */
    suspend fun getBankMessages(): List<Message> =
        messageDao.getBankMessages()

    /** Обновить статус сообщения */
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) =
        messageDao.updateStatus(messageId, status)

    /** Сохранить расшифрованное содержимое */
    suspend fun setDecryptedContent(messageId: String, content: String) =
        messageDao.setDecryptedContent(messageId, content)

    /** Удалить сообщения из банка после прочтения адресатом */
    suspend fun cleanupBankMessages(messageIds: List<String>) =
        messageDao.removeBankMessages(messageIds)

    /** Удалить сообщение из банка если не принадлежит текущему пользователю */
    suspend fun removeFromBankIfNotMine(messageId: String) =
        messageDao.removeFromBankIfNotMine(messageId)

    /** Убрать флаг банка у сообщения */
    suspend fun removeFromBank(messageId: String) =
        messageDao.removeFromBank(messageId)

    /** Получить сообщение по ID */
    suspend fun getMessageById(messageId: String): Message? =
        messageDao.getMessageById(messageId)
}
