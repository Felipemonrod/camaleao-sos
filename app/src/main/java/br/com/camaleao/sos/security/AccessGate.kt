package br.com.camaleao.sos.security

import br.com.camaleao.sos.sos.Store
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AccessGate(private val store: Store) {
    private fun derive(pin: String, salt: ByteArray) =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120000, 256))
            .encoded

    fun configure(access: String, sos: String) {
        require(
            access.matches(Regex("[0-9]{6,12}")) &&
                sos.matches(Regex("[0-9]{6,12}")) &&
                access != sos
        )
        for ((key, pin) in listOf("access" to access, "sos" to sos)) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            store.put("${key}_salt", DeviceCrypto.b64(salt))
            store.put("${key}_hash", DeviceCrypto.b64(derive(pin, salt)))
        }
        store.flag("configured", true)
    }

    fun match(value: String): String? {
        if (!store.flag("configured")) return if (value == "2580") "access" else null
        if (!value.matches(Regex("[0-9]{6,12}"))) return null
        for (key in listOf("sos", "access")) {
            // Bloquear tentativas de acesso não bloqueia o Gatilho de coação.
            if (
                key == "access" &&
                    store.get("lock_until", "0").toLong() > System.currentTimeMillis()
            )
                return null
            if (
                MessageDigest.isEqual(
                    DeviceCrypto.un64(store.get("${key}_hash")),
                    derive(value, DeviceCrypto.un64(store.get("${key}_salt"))),
                )
            ) {
                store.put("failures", "0")
                return key
            }
        }
        val failed = store.get("failures", "0").toInt() + 1
        store.put("failures", failed.toString())
        if (failed >= 5) {
            store.put("lock_until", (System.currentTimeMillis() + 60000).toString())
            store.put("failures", "0")
        }
        return null
    }
}
