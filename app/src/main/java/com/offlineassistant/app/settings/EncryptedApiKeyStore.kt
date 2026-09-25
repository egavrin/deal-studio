package com.offlineassistant.app.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class EncryptedApiKeyStore(
    context: Context,
    private val credentialId: String,
    private val keyAlias: String,
    private val displayName: String
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val ciphertextKey = "${credentialId}_ciphertext"
    private val ivKey = "${credentialId}_iv"
    private val provenanceKey = "${credentialId}_provenance"

    val provenance: CredentialProvenance?
        @Synchronized get() = CredentialProvenance.resolve(
            preferences.getString(provenanceKey, null),
            preferences.contains(ciphertextKey) || preferences.contains(ivKey)
        )

    @Synchronized
    fun save(value: String) {
        saveEncrypted(value, CredentialProvenance.USER)
    }

    /** Explicit bootstrap never replaces user ownership or an intentional clear. */
    @Synchronized
    fun bootstrap(value: String, refreshExisting: Boolean = false): Boolean {
        if (!CredentialProvenance.permitsBootstrap(provenance, refreshExisting) || value.isBlank()) return false
        saveEncrypted(value, CredentialProvenance.BOOTSTRAP)
        return true
    }

    private fun saveEncrypted(value: String, source: CredentialProvenance) {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "$displayName API key cannot be empty" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        val encrypted = cipher.doFinal(normalized.encodeToByteArray())
        preferences.edit(commit = true) {
            putString(ciphertextKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString(provenanceKey, source.name)
        }
    }

    @Synchronized
    fun readOrNull(): String? {
        val ciphertext = preferences.getString(ciphertextKey, null) ?: return null
        val iv = preferences.getString(ivKey, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
                )
            }
            cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).decodeToString()
        }.getOrElse {
            // Keep both ownership and ciphertext on transient keystore failures.
            null
        }
    }

    fun isConfigured(): Boolean = readOrNull() != null

    @Synchronized
    fun clear() {
        preferences.edit(commit = true) {
            remove(ciphertextKey)
            remove(ivKey)
            putString(provenanceKey, CredentialProvenance.CLEARED.name)
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    internal companion object {
        const val PREFERENCES_NAME = "offline_assistant_secure_cloud_credentials"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
