package com.meshchat

import com.meshchat.crypto.CryptoManager
import com.meshchat.crypto.EncryptedPayload
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom

/**
 * Тесты шифрования для mesh-сети.
 *
 * Проверяет:
 * - Корректность шифрования/расшифровки сообщений (RSA + AES-256-GCM)
 * - Невозможность расшифровки посторонним узлом
 * - Уникальность зашифрованных данных (каждое шифрование использует новый AES-ключ и IV)
 * - Работу с различными типами текста (Unicode, длинные сообщения)
 */
class CryptoManagerTest {

    private lateinit var senderCrypto: CryptoManager
    private lateinit var recipientCrypto: CryptoManager
    private lateinit var thirdPartyCrypto: CryptoManager

    @Before
    fun setup() {
        // Генерируем ключи для трёх узлов
        val senderKeys = generateTestKeyPair()
        val recipientKeys = generateTestKeyPair()
        val thirdPartyKeys = generateTestKeyPair()

        senderCrypto = CryptoManager().apply { initializeWithKeyPair(senderKeys) }
        recipientCrypto = CryptoManager().apply { initializeWithKeyPair(recipientKeys) }
        thirdPartyCrypto = CryptoManager().apply { initializeWithKeyPair(thirdPartyKeys) }
    }

    @Test
    fun `encryption and decryption work correctly`() {
        val originalText = "Привет, mesh-сеть!"
        val recipientPublicKey = recipientCrypto.publicKeyFromBase64(recipientCrypto.getPublicKeyBase64())

        // Отправитель шифрует
        val payload = senderCrypto.encryptMessage(originalText, recipientPublicKey)

        // Получатель расшифровывает
        val decryptedText = recipientCrypto.decryptMessage(payload)

        assertEquals(originalText, decryptedText)
    }

    @Test
    fun `third party cannot decrypt message`() {
        val originalText = "Секретное сообщение"
        val recipientPublicKey = recipientCrypto.publicKeyFromBase64(recipientCrypto.getPublicKeyBase64())

        // Отправитель шифрует для получателя
        val payload = senderCrypto.encryptMessage(originalText, recipientPublicKey)

        // Третья сторона пытается расшифровать — должна получить ошибку
        try {
            thirdPartyCrypto.decryptMessage(payload)
            fail("Третья сторона не должна расшифровать сообщение")
        } catch (e: Exception) {
            // Ожидаемо: расшифровка невозможна без приватного ключа получателя
        }
    }

    @Test
    fun `each encryption produces different ciphertext`() {
        val text = "Одинаковый текст"
        val recipientPublicKey = recipientCrypto.publicKeyFromBase64(recipientCrypto.getPublicKeyBase64())

        val payload1 = senderCrypto.encryptMessage(text, recipientPublicKey)
        val payload2 = senderCrypto.encryptMessage(text, recipientPublicKey)

        // Зашифрованные данные должны отличаться (разные AES-ключи и IV)
        assertNotEquals(payload1.encryptedContent, payload2.encryptedContent)
        assertNotEquals(payload1.encryptedAesKey, payload2.encryptedAesKey)
        assertNotEquals(payload1.iv, payload2.iv)

        // Но оба должны расшифровываться в один и тот же текст
        assertEquals(text, recipientCrypto.decryptMessage(payload1))
        assertEquals(text, recipientCrypto.decryptMessage(payload2))
    }

    @Test
    fun `unicode messages are handled correctly`() {
        val unicodeText = "🔒 Шифрование 中文 العربية 日本語 한국어"
        val recipientPublicKey = recipientCrypto.publicKeyFromBase64(recipientCrypto.getPublicKeyBase64())

        val payload = senderCrypto.encryptMessage(unicodeText, recipientPublicKey)
        val decrypted = recipientCrypto.decryptMessage(payload)

        assertEquals(unicodeText, decrypted)
    }

    @Test
    fun `long messages are handled correctly`() {
        val longText = "A".repeat(10000)
        val recipientPublicKey = recipientCrypto.publicKeyFromBase64(recipientCrypto.getPublicKeyBase64())

        val payload = senderCrypto.encryptMessage(longText, recipientPublicKey)
        val decrypted = recipientCrypto.decryptMessage(payload)

        assertEquals(longText, decrypted)
    }

    @Test
    fun `node IDs are unique for different keys`() {
        val id1 = senderCrypto.getNodeId()
        val id2 = recipientCrypto.getNodeId()
        val id3 = thirdPartyCrypto.getNodeId()

        assertNotEquals(id1, id2)
        assertNotEquals(id2, id3)
        assertNotEquals(id1, id3)
    }

    @Test
    fun `node ID is consistent`() {
        val id1 = senderCrypto.getNodeId()
        val id2 = senderCrypto.getNodeId()

        assertEquals(id1, id2)
    }

    @Test
    fun `public key round-trip works correctly`() {
        val base64Key = senderCrypto.getPublicKeyBase64()
        val restoredKey = senderCrypto.publicKeyFromBase64(base64Key)

        // Зашифровать с восстановленным ключом должно работать
        val payload = recipientCrypto.encryptMessage("тест", restoredKey)
        val decrypted = senderCrypto.decryptMessage(payload)

        assertEquals("тест", decrypted)
    }

    @Test
    fun `tampered ciphertext fails decryption`() {
        val text = "Целостность данных"
        val recipientPublicKey = recipientCrypto.publicKeyFromBase64(recipientCrypto.getPublicKeyBase64())

        val payload = senderCrypto.encryptMessage(text, recipientPublicKey)

        // Модифицируем зашифрованное содержимое
        val tamperedPayload = EncryptedPayload(
            encryptedContent = payload.encryptedContent.reversed(),
            encryptedAesKey = payload.encryptedAesKey,
            iv = payload.iv
        )

        try {
            recipientCrypto.decryptMessage(tamperedPayload)
            fail("Изменённые данные не должны расшифровываться (GCM обеспечивает аутентификацию)")
        } catch (e: Exception) {
            // Ожидаемо: AES-GCM обнаруживает модификацию
        }
    }

    private fun generateTestKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }
}
