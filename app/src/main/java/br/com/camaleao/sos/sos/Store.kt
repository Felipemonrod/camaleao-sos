package br.com.camaleao.sos.sos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.com.camaleao.sos.security.DeviceCrypto
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class Store(context: Context, databaseName: String = "records.db") :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
    val crypto = DeviceCrypto()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE config(name TEXT PRIMARY KEY,value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE occurrence(id TEXT PRIMARY KEY,seq INTEGER,value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit

    @Synchronized
    fun get(name: String, fallback: String = ""): String =
        readableDatabase.rawQuery("SELECT value FROM config WHERE name=?", arrayOf(name)).use {
            if (it.moveToFirst()) crypto.open(it.getString(0)) else fallback
        }

    @Synchronized
    fun put(name: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "config",
            null,
            ContentValues().apply {
                put("name", name)
                put("value", crypto.seal(value))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun flag(name: String) = get(name) == "true"

    fun flag(name: String, value: Boolean) = put(name, value.toString())

    @Synchronized
    fun deviceId(): String =
        get("device_id").ifBlank { UUID.randomUUID().toString().also { put("device_id", it) } }

    fun phrase() = get("phrase", "Preciso buscar meu caderno azul")

    fun threshold() = get("threshold", "1.0").toDoubleOrNull() ?: 1.0

    @Synchronized
    fun list(): List<JSONObject> =
        readableDatabase.rawQuery("SELECT value FROM occurrence ORDER BY rowid DESC", null).use { c
            ->
            buildList { while (c.moveToNext()) add(JSONObject(crypto.open(c.getString(0)))) }
        }

    @Synchronized
    fun record(id: String): JSONObject? =
        readableDatabase.rawQuery("SELECT value FROM occurrence WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) JSONObject(crypto.open(it.getString(0))) else null
        }

    private fun save(record: JSONObject) {
        writableDatabase.insertWithOnConflict(
            "occurrence",
            null,
            ContentValues().apply {
                put("id", record.getString("id"))
                put("seq", record.optLong("sequence", 0))
                put("value", crypto.seal(record.toString()))
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun audit(record: JSONObject, state: String) {
        val trail = record.optJSONArray("audit") ?: JSONArray()
        trail.put(JSONObject().put("at", Instant.now().toString()).put("state", state))
        record.put("audit", trail)
    }

    @Synchronized
    fun create(trigger: String, test: Boolean): String {
        val id = UUID.randomUUID().toString()
        val r =
            JSONObject()
                .put("id", id)
                .put("trigger", trigger)
                .put("test", test)
                .put("created_at", Instant.now().toString())
                .put("due", System.currentTimeMillis() + 5000)
                .put("state", "CREATED")
                .put("cancelled", false)
        audit(r, "CREATED")
        save(r)
        return id
    }

    @Synchronized
    fun cancel(id: String): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val r = record(id) ?: return false
            if (r.getString("state") != "CREATED" || r.optBoolean("cancelled")) return false
            r.put("cancelled", true)
            audit(r, "CANCELLED_BEFORE_QUEUE")
            save(r)
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun enqueue(id: String, location: JSONObject? = null) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val r = record(id) ?: return
            if (r.optBoolean("cancelled") || r.getString("state") != "CREATED") return
            val seq = get("sequence", "0").toLong() + 1
            val manifest =
                JSONObject()
                    .put("event_id", id)
                    .put("device_id", deviceId())
                    .put("sequence", seq)
                    .put("created_at", r.getString("created_at"))
                    .put("app_version", "0.2.0")
                    .put("trigger", r.getString("trigger"))
                    .put("test", r.getBoolean("test"))
                    .put("previous_manifest_hash", get("previous_hash"))
                    .put("audio", JSONObject.NULL)
                    .put("original_sha256", JSONObject.NULL)
                    .put("encrypted_file_sha256", JSONObject.NULL)
                    .put(
                        "location",
                        if (!r.getBoolean("test")) location ?: JSONObject.NULL else JSONObject.NULL,
                    )
                    .toString()
            r.put("sequence", seq)
                .put("manifest", manifest)
                .put("signature", crypto.sign(manifest))
                .put("state", "QUEUED")
            audit(r, "QUEUED")
            save(r)
            put("sequence", seq.toString())
            put("previous_hash", DeviceCrypto.hash(manifest))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun markSent(id: String) {
        val r = record(id) ?: return
        if (r.getString("state") == "ACKNOWLEDGED") return
        r.put("state", "SENT")
        audit(r, "SENT")
        save(r)
    }

    @Synchronized
    fun acknowledge(id: String, receipt: String, signature: String) {
        val r = record(id) ?: error("Ocorrência ausente")
        val parsed = JSONObject(receipt)
        require(
            parsed.getString("event_id") == id &&
                parsed.getString("manifest_hash") == DeviceCrypto.hash(r.getString("manifest"))
        )
        require(DeviceCrypto.verify(get("server_public_key"), receipt, signature)) {
            "Recibo sem assinatura válida"
        }
        r.put("state", "ACKNOWLEDGED").put("receipt", receipt).put("receipt_signature", signature)
        audit(r, "ACKNOWLEDGED")
        save(r)
    }

    @Synchronized
    fun retry(id: String) {
        val r = record(id) ?: return
        if (r.getString("state") == "ACKNOWLEDGED") return
        r.put("state", "QUEUED")
        audit(r, "QUEUED")
        save(r)
    }

    @Synchronized
    fun deleteReceived() {
        list()
            .filter { it.getString("state") == "ACKNOWLEDGED" || it.optBoolean("cancelled") }
            .forEach { writableDatabase.delete("occurrence", "id=?", arrayOf(it.getString("id"))) }
    }
}
