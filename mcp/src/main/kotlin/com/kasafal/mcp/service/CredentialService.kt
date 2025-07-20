package com.kasafal.mcp.service


import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.*

@Service
class CredentialService {

    @Value("\${app.encryption.password}")
    private lateinit var encryptionPassword: String

    @Value("\${app.encryption.salt}")
    private lateinit var encryptionSalt: String

    private lateinit var textEncryptor: TextEncryptor

    @PostConstruct
    fun initialize() {
        // Generate salt if not provided
        val salt = if (encryptionSalt.isBlank()) {
            generateSalt()
        } else {
            encryptionSalt
        }

        textEncryptor = Encryptors.text(encryptionPassword, salt)
    }

    fun encrypt(plainText: String): String {
        return try {
            textEncryptor.encrypt(plainText)
        } catch (e: Exception) {
            throw RuntimeException("Failed to encrypt credentials", e)
        }
    }

    fun decrypt(encryptedText: String): String {
        return try {
            textEncryptor.decrypt(encryptedText)
        } catch (e: Exception) {
            throw RuntimeException("Failed to decrypt credentials", e)
        }
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }
}