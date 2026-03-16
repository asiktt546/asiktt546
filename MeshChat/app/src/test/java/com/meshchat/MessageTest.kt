package com.meshchat

import com.meshchat.data.Message
import com.meshchat.data.MessageStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * Тесты логики сообщений в mesh-сети.
 */
class MessageTest {

    @Test
    fun `message created with correct default values`() {
        val message = Message(
            id = "test-id",
            senderId = "sender-123",
            recipientId = "recipient-456",
            encryptedContent = "encrypted-data",
            encryptedAesKey = "encrypted-key",
            iv = "iv-data",
            timestamp = System.currentTimeMillis()
        )

        assertEquals(MessageStatus.PENDING, message.status)
        assertFalse(message.isMine)
        assertNull(message.decryptedContent)
        assertTrue(message.isInBank)
    }

    @Test
    fun `message for sender has decrypted content`() {
        val message = Message(
            id = "test-id",
            senderId = "sender-123",
            recipientId = "recipient-456",
            encryptedContent = "encrypted-data",
            encryptedAesKey = "encrypted-key",
            iv = "iv-data",
            timestamp = System.currentTimeMillis(),
            isMine = true,
            decryptedContent = "Привет!"
        )

        assertTrue(message.isMine)
        assertNotNull(message.decryptedContent)
        assertEquals("Привет!", message.decryptedContent)
    }

    @Test
    fun `bank message for third party has no decrypted content`() {
        val message = Message(
            id = "test-id",
            senderId = "sender-123",
            recipientId = "recipient-456",
            encryptedContent = "encrypted-data",
            encryptedAesKey = "encrypted-key",
            iv = "iv-data",
            timestamp = System.currentTimeMillis(),
            isMine = false,
            isInBank = true,
            decryptedContent = null
        )

        assertFalse(message.isMine)
        assertTrue(message.isInBank)
        assertNull(message.decryptedContent)
    }

    @Test
    fun `message status transitions are valid`() {
        val statuses = MessageStatus.values()
        assertEquals(4, statuses.size)
        assertTrue(statuses.contains(MessageStatus.PENDING))
        assertTrue(statuses.contains(MessageStatus.STORED_IN_BANK))
        assertTrue(statuses.contains(MessageStatus.DELIVERED))
        assertTrue(statuses.contains(MessageStatus.READ))
    }

    @Test
    fun `message copy preserves fields correctly`() {
        val original = Message(
            id = "test-id",
            senderId = "sender-123",
            recipientId = "recipient-456",
            encryptedContent = "encrypted-data",
            encryptedAesKey = "encrypted-key",
            iv = "iv-data",
            timestamp = 1000L,
            status = MessageStatus.STORED_IN_BANK,
            isMine = true,
            decryptedContent = "Original text",
            isInBank = true
        )

        // Симулируем отправку в mesh (убираем расшифрованное содержимое)
        val forMesh = original.copy(
            decryptedContent = null,
            isMine = false
        )

        assertEquals(original.id, forMesh.id)
        assertEquals(original.senderId, forMesh.senderId)
        assertEquals(original.recipientId, forMesh.recipientId)
        assertEquals(original.encryptedContent, forMesh.encryptedContent)
        assertNull(forMesh.decryptedContent)
        assertFalse(forMesh.isMine)
    }
}
