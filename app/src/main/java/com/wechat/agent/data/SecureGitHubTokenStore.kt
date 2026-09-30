package com.wechat.agent.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** GitHub 调试 Token 的本地安全存储；旧版 debug_prefs 明文 Token 会在成功迁移后删除。 */
class SecureGitHubTokenStore(context: Context) {
    companion object {
        private const val PREFS = "secure_debug_credentials"
        private const val CIPHERTEXT_KEY = "github_token_ciphertext"
        private const val KEY_ALIAS = "wechat_agent_github_token_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val LEGACY_PREFS = "debug_prefs"
        private const val LEGACY_KEY = "github_token"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): String {
        val encoded = prefs.getString(CIPHERTEXT_KEY, null) ?: return migrateLegacy()
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_BYTES)
            decrypt(packed)
        }.getOrElse {
            clear()
            ""
        }
    }

    fun write(token: String): Boolean {
        val value = token.trim()
        if (value.isBlank()) {
            clear()
            return true
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            val encoded = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
            prefs.edit().putString(CIPHERTEXT_KEY, encoded).commit()
        }.getOrDefault(false)
    }

    fun clear() {
        prefs.edit().remove(CIPHERTEXT_KEY).apply()
    }

    private fun migrateLegacy(): String {
        val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString(LEGACY_KEY, "").orEmpty().trim()
        if (legacy.isBlank()) return ""
        if (!write(legacy)) return ""
        val verified = readEncryptedOnly()
        if (verified == legacy) {
            legacyPrefs.edit().remove(LEGACY_KEY).apply()
            return verified
        }
        return ""
    }

    private fun readEncryptedOnly(): String {
        val encoded = prefs.getString(CIPHERTEXT_KEY, null) ?: return ""
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_BYTES)
            decrypt(packed)
        }.getOrDefault("")
    }

    private fun decrypt(packed: ByteArray): String {
        val iv = packed.copyOfRange(0, IV_BYTES)
        val ciphertext = packed.copyOfRange(IV_BYTES, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
