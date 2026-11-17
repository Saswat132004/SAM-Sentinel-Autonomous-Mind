package com.sam.assistant.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SAM_MASTER_MEMORY_KEY"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    init {
        initKey()
    }

    private fun initKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Cannot retrieve master memory key from Keystore")
        return entry.secretKey
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryption = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encryption.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryption, 0, combined, iv.size, encryption.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(cipherTextBase64: String): String {
        if (cipherTextBase64.isEmpty()) return ""
        try {
            val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH_BYTE) return cipherTextBase64
            val iv = ByteArray(IV_LENGTH_BYTE)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTE)
            val encryptedBytes = ByteArray(combined.size - IV_LENGTH_BYTE)
            System.arraycopy(combined, IV_LENGTH_BYTE, encryptedBytes, 0, encryptedBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails (e.g. data wasn't encrypted), return original text
            return cipherTextBase64
        }
    }
}

