package br.com.camaleao.sos.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

class DeviceCrypto {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun wrappingKey(): SecretKey =
        synchronized(DeviceCrypto::class.java) {
            if (!keyStore.containsAlias("local-v1"))
                KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                "local-v1",
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                    generateKey()
                }
            keyStore.getKey("local-v1", null) as SecretKey
        }

    private fun signingKey(): PrivateKey =
        synchronized(DeviceCrypto::class.java) {
            if (!keyStore.containsAlias("device-sign-v1"))
                KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply {
                    initialize(
                        KeyGenParameterSpec.Builder(
                                "device-sign-v1",
                                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                            )
                            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .build()
                    )
                    generateKeyPair()
                }
            keyStore.getKey("device-sign-v1", null) as PrivateKey
        }

    fun publicKey(): String {
        signingKey()
        return b64(keyStore.getCertificate("device-sign-v1").publicKey.encoded)
    }

    fun sign(text: String): String =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(signingKey())
            update(text.toByteArray(Charsets.UTF_8))
            b64(sign())
        }

    fun seal(text: String): String {
        val dataKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val data =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, dataKey) }
        val encrypted = data.doFinal(text.toByteArray(Charsets.UTF_8))
        val wrap =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, wrappingKey())
            }
        return JSONObject()
            .put("v", 1)
            .put("iv", b64(data.iv))
            .put("data", b64(encrypted))
            .put("wrap_iv", b64(wrap.iv))
            .put("key", b64(wrap.doFinal(dataKey.encoded)))
            .toString()
    }

    fun open(envelope: String): String {
        val o = JSONObject(envelope)
        val wrap =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.DECRYPT_MODE,
                    wrappingKey(),
                    GCMParameterSpec(128, un64(o.getString("wrap_iv"))),
                )
            }
        val key = SecretKeySpec(wrap.doFinal(un64(o.getString("key"))), "AES")
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, un64(o.getString("iv"))))
            String(doFinal(un64(o.getString("data"))), Charsets.UTF_8)
        }
    }

    companion object {
        fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)

        fun un64(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)

        fun hash(s: String) = hash(s.toByteArray(Charsets.UTF_8))

        fun hash(b: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

        fun verify(publicKey: String, message: String, signature: String): Boolean =
            runCatching {
                    val key =
                        KeyFactory.getInstance("EC")
                            .generatePublic(X509EncodedKeySpec(un64(publicKey)))
                    Signature.getInstance("SHA256withECDSA").run {
                        initVerify(key)
                        update(message.toByteArray(Charsets.UTF_8))
                        verify(un64(signature))
                    }
                }
                .getOrDefault(false)
    }
}
