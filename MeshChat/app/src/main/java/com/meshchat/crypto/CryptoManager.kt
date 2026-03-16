package com.meshchat.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Менеджер шифрования для mesh-сети.
 *
 * Схема шифрования:
 * 1. Каждое устройство генерирует пару ключей RSA-2048 при первом запуске.
 * 2. Публичные ключи обмениваются при обнаружении устройств в mesh-сети.
 * 3. Для каждого сообщения:
 *    - Генерируется случайный AES-256 ключ
 *    - Сообщение шифруется AES-256-GCM (аутентифицированное шифрование)
 *    - AES-ключ шифруется публичным RSA-ключом получателя
 *    - В mesh-сеть отправляется: зашифрованное сообщение + зашифрованный AES-ключ + IV
 * 4. Только получатель может расшифровать AES-ключ своим приватным RSA-ключом,
 *    а затем расшифровать само сообщение.
 * 5. Остальные узлы хранят зашифрованные данные, не имея возможности их прочитать.
 */
class CryptoManager {

    companion object {
        private const val RSA_ALGORITHM = "RSA"
        private const val RSA_CIPHER = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val AES_ALGORITHM = "AES"
        private const val AES_CIPHER = "AES/GCM/NoPadding"
        private const val RSA_KEY_SIZE = 2048
        private const val AES_KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "meshchat_rsa_key"
    }

    private var keyPair: KeyPair? = null

    /**
     * Инициализация: генерация или загрузка пары ключей RSA.
     * Использует Android Keystore для безопасного хранения приватного ключа.
     */
    fun initialize() {
        keyPair = loadOrGenerateKeyPair()
    }

    /**
     * Инициализация с предоставленной парой ключей (для тестов).
     */
    fun initializeWithKeyPair(keys: KeyPair) {
        keyPair = keys
    }

    /**
     * Получить публичный ключ в формате Base64 для обмена с другими узлами.
     */
    fun getPublicKeyBase64(): String {
        val publicKey = keyPair?.public
            ?: throw IllegalStateException("CryptoManager не инициализирован")
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Восстановить публичный ключ из Base64.
     */
    fun publicKeyFromBase64(base64Key: String): PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Зашифровать сообщение для конкретного получателя.
     *
     * @param plainText текст сообщения
     * @param recipientPublicKey публичный ключ получателя
     * @return тройка: (зашифрованное сообщение Base64, зашифрованный AES-ключ Base64, IV Base64)
     */
    fun encryptMessage(plainText: String, recipientPublicKey: PublicKey): EncryptedPayload {
        // 1. Генерируем случайный AES-256 ключ
        val aesKey = generateAesKey()

        // 2. Генерируем случайный IV для GCM
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        // 3. Шифруем сообщение AES-256-GCM
        val aesCipher = Cipher.getInstance(AES_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)
        val encryptedContent = aesCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // 4. Шифруем AES-ключ публичным RSA-ключом получателя
        val rsaCipher = Cipher.getInstance(RSA_CIPHER)
        rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        return EncryptedPayload(
            encryptedContent = Base64.encodeToString(encryptedContent, Base64.NO_WRAP),
            encryptedAesKey = Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Расшифровать сообщение (только получатель может это сделать).
     *
     * @param payload зашифрованные данные
     * @return расшифрованный текст сообщения
     */
    fun decryptMessage(payload: EncryptedPayload): String {
        val privateKey = keyPair?.private
            ?: throw IllegalStateException("CryptoManager не инициализирован")

        // 1. Расшифровываем AES-ключ приватным RSA-ключом
        val rsaCipher = Cipher.getInstance(RSA_CIPHER)
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val aesKeyBytes = rsaCipher.doFinal(
            Base64.decode(payload.encryptedAesKey, Base64.NO_WRAP)
        )
        val aesKey = SecretKeySpec(aesKeyBytes, AES_ALGORITHM)

        // 2. Расшифровываем сообщение AES-256-GCM
        val iv = Base64.decode(payload.iv, Base64.NO_WRAP)
        val aesCipher = Cipher.getInstance(AES_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        aesCipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)
        val decryptedBytes = aesCipher.doFinal(
            Base64.decode(payload.encryptedContent, Base64.NO_WRAP)
        )

        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Генерация хеша публичного ключа для использования как ID узла.
     */
    fun getNodeId(): String {
        val publicKeyBytes = keyPair?.public?.encoded
            ?: throw IllegalStateException("CryptoManager не инициализирован")
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyBytes)
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Загрузить существующую пару ключей из Android Keystore или сгенерировать новую.
     */
    private fun loadOrGenerateKeyPair(): KeyPair {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
                val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
                KeyPair(publicKey, privateKey)
            } else {
                generateAndStoreKeyPair()
            }
        } catch (e: Exception) {
            // Fallback: генерируем ключи без Keystore (для эмуляторов и тестов)
            generateFallbackKeyPair()
        }
    }

    /**
     * Генерация и сохранение ключей в Android Keystore.
     */
    private fun generateAndStoreKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(RSA_KEY_SIZE)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Fallback генерация ключей без Android Keystore.
     */
    private fun generateFallbackKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        keyPairGenerator.initialize(RSA_KEY_SIZE, SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Генерация случайного AES-256 ключа.
     */
    private fun generateAesKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGenerator.init(AES_KEY_SIZE, SecureRandom())
        return keyGenerator.generateKey()
    }
}

/**
 * Контейнер для зашифрованных данных сообщения.
 */
data class EncryptedPayload(
    /** Зашифрованное содержимое сообщения (AES-256-GCM, Base64) */
    val encryptedContent: String,
    /** Зашифрованный AES-ключ (RSA-OAEP, Base64) */
    val encryptedAesKey: String,
    /** Вектор инициализации для AES-GCM (Base64) */
    val iv: String
)
