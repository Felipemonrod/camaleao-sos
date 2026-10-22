package br.com.camaleao.sos.sos

import br.com.camaleao.sos.security.DeviceCrypto
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*
import org.json.JSONObject

class PanelTransport(private val store: Store) {
    private fun connection(base: String, path: String, pin: String): HttpsURLConnection {
        val url = URL(base.trimEnd('/') + path)
        require(url.protocol == "https" && !url.host.isNullOrBlank() && url.userInfo == null) {
            "Use uma URL HTTPS"
        }
        require(pin.matches(Regex("[a-fA-F0-9]{64}"))) { "Fingerprint inválido" }
        val trust =
            object : X509TrustManager {
                override fun getAcceptedIssuers() = emptyArray<X509Certificate>()

                override fun checkClientTrusted(chain: Array<X509Certificate>, type: String) =
                    throw java.security.cert.CertificateException("Somente servidor")

                override fun checkServerTrusted(chain: Array<X509Certificate>, type: String) {
                    if (chain.isEmpty() || !DeviceCrypto.hash(chain[0].encoded).equals(pin, true))
                        throw java.security.cert.CertificateException(
                            "Certificado diferente do pareamento"
                        )
                    chain[0].checkValidity()
                }
            }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        return (url.openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = ssl.socketFactory
            connectTimeout = 6000
            readTimeout = 6000
            instanceFollowRedirects = false
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
    }

    private fun request(
        base: String,
        path: String,
        pin: String,
        body: JSONObject,
        token: String = "",
        sent: () -> Unit = {},
    ): JSONObject {
        val connection = connection(base, path, pin)
        try {
            if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use {
                it.write(bytes)
                it.flush()
            }
            sent()
            require(connection.responseCode == 200) {
                "Painel respondeu HTTP ${connection.responseCode}"
            }
            val text =
                connection.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val block = ByteArray(4096)
                    while (true) {
                        val n = input.read(block)
                        if (n < 0) break
                        require(out.size() + n <= 65_536)
                        out.write(block, 0, n)
                    }
                    String(out.toByteArray(), Charsets.UTF_8)
                }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    fun pair(code: String, overrideUrl: String = "") {
        val config = JSONObject(String(DeviceCrypto.un64(code.trim()), Charsets.UTF_8))
        val url = overrideUrl.ifBlank { config.getString("url") }
        val pin = config.getString("pin")
        require(store.get("pin").isBlank() || store.get("pin").equals(pin, true)) {
            "Outro Painel exige uma nova cadeia. Exporte os recibos antes de apagar dados."
        }
        val response =
            request(
                url,
                "/api/pair",
                pin,
                JSONObject()
                    .put("device_id", store.deviceId())
                    .put("public_key", store.crypto.publicKey())
                    .put("code", config.getString("code")),
            )
        require(response.getString("token").length >= 32)
        store.put("endpoint", url)
        store.put("pin", pin)
        store.put("transport_token", response.getString("token"))
        store.put("server_public_key", response.getString("server_public_key"))
    }

    fun send(record: JSONObject) {
        val id = record.getString("id")
        val result =
            request(
                store.get("endpoint"),
                "/api/events",
                store.get("pin"),
                JSONObject()
                    .put("manifest", record.getString("manifest"))
                    .put("signature", record.getString("signature")),
                store.get("transport_token"),
            ) {
                store.markSent(id)
            }
        store.acknowledge(id, result.getString("receipt"), result.getString("signature"))
    }
}
