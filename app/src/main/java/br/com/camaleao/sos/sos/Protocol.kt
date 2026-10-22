package br.com.camaleao.sos.sos

import android.content.Context
import android.os.SystemClock
import androidx.work.*
import br.com.camaleao.sos.LiveState
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

object Protocol {
    private val executor = Executors.newSingleThreadExecutor()
    private val pumping = AtomicBoolean(false)

    fun create(context: Context, origin: String, test: Boolean): String? {
        val app = context.applicationContext
        val store = Store(app)
        if (!test && !store.flag("share_consent")) return null
        val id = store.create(origin, test)
        schedule(app)
        LiveState.changed()
        LiveState.main.postDelayed(
            {
                executor.execute {
                    store.enqueue(id, position(store))
                    pump(app)
                    LiveState.changed()
                }
            },
            5000,
        )
        return id
    }

    private fun position(store: Store): JSONObject? {
        if (!store.flag("location_consent")) return null
        val l = LiveState.location ?: return null
        val age = SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos
        if (age !in 0..120_000_000_000L) return null
        return JSONObject()
            .put("lat", l.latitude)
            .put("lon", l.longitude)
            .put("accuracy", l.accuracy.toDouble())
            .put("age_seconds", age / 1_000_000_000)
    }

    fun schedule(context: Context) {
        val work =
            OneTimeWorkRequestBuilder<QueueWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("occurrence-outbox", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }

    fun pump(context: Context) {
        executor.execute { sendPending(context.applicationContext) }
    }

    fun sendPending(context: Context): Boolean {
        if (!pumping.compareAndSet(false, true)) return false
        try {
            val store = Store(context)
            for (record in store.list().reversed()) {
                if (
                    record.getString("state") == "CREATED" &&
                        !record.optBoolean("cancelled") &&
                        record.getLong("due") <= System.currentTimeMillis()
                )
                    store.enqueue(record.getString("id"), position(store))
            }
            if (store.get("endpoint").isBlank()) return false
            for (record in
                store
                    .list()
                    .filter { it.getString("state") in listOf("QUEUED", "SENT") }
                    .sortedBy { it.getLong("sequence") }) {
                if (!record.getBoolean("test") && !store.flag("share_consent")) return false
                try {
                    PanelTransport(store).send(record)
                    LiveState.changed()
                } catch (_: Exception) {
                    store.retry(record.getString("id"))
                    LiveState.changed()
                    return false
                }
            }
            return store.list().none {
                it.getString("state") == "CREATED" && !it.optBoolean("cancelled")
            }
        } finally {
            pumping.set(false)
        }
    }
}

class QueueWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result =
        if (Protocol.sendPending(applicationContext)) Result.success() else Result.retry()
}
