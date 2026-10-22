package br.com.camaleao.sos.sentinela

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import br.com.camaleao.sos.LiveState
import br.com.camaleao.sos.domain.Position
import br.com.camaleao.sos.sos.Store
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Posição pontual dentro de um orçamento de tempo (RF-40). Sem rastreamento contínuo:
 * cada chamada pede uma leitura e desiste no prazo, seguindo sem posição.
 */
object LocationProbe {
    private const val MAX_AGE_NS = 120_000_000_000L

    fun permitted(c: Context) =
        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    fun allowed(c: Context, store: Store) = store.flag("location_consent") && permitted(c)

    fun toPosition(l: Location?): Position? {
        if (l == null) return null
        val age = SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos
        if (age !in 0..MAX_AGE_NS) return null
        return Position(l.latitude, l.longitude, l.accuracy, age / 1_000_000_000)
    }

    /** Chama [done] uma única vez, na thread principal, com a posição ou null. */
    fun request(c: Context, store: Store, timeoutMs: Long = 12_000, done: (Position?) -> Unit) {
        if (!allowed(c, store)) {
            LiveState.main.post { done(null) }
            return
        }
        val manager = c.getSystemService(LocationManager::class.java)
        val finished = AtomicBoolean(false)
        var cancel: CancellationSignal? = null
        var listener: LocationListener? = null
        fun finish(l: Location?) {
            if (!finished.compareAndSet(false, true)) return
            cancel?.cancel()
            listener?.let { manager.removeUpdates(it) }
            val fresh = toPosition(l)
            if (fresh != null) LiveState.location = l
            done(fresh ?: recent(manager))
        }
        LiveState.main.postDelayed({ finish(null) }, timeoutMs)
        try {
            val providers = manager.getProviders(true)
            val provider =
                when {
                    LocationManager.GPS_PROVIDER in providers &&
                        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED -> LocationManager.GPS_PROVIDER
                    LocationManager.NETWORK_PROVIDER in providers ->
                        LocationManager.NETWORK_PROVIDER
                    else -> providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
                } ?: return finish(null)
            if (Build.VERSION.SDK_INT >= 30) {
                cancel = CancellationSignal()
                manager.getCurrentLocation(provider, cancel, c.mainExecutor) {
                    LiveState.main.post { finish(it) }
                }
            } else {
                val l = LocationListener { LiveState.main.post { finish(it) } }
                listener = l
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, l, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            LiveState.main.post { finish(null) }
        } catch (_: IllegalArgumentException) {
            LiveState.main.post { finish(null) }
        }
    }

    /** Última posição conhecida, só se tiver menos de 2 minutos. */
    private fun recent(manager: LocationManager): Position? =
        try {
            (listOfNotNull(LiveState.location) +
                    manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) })
                .mapNotNull { toPosition(it) }
                .minByOrNull { it.ageSeconds }
        } catch (_: SecurityException) {
            null
        }
}
