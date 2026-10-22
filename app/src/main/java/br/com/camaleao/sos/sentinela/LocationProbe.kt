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
    private const val GOOD_M = 50f

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

    /**
     * Chama [done] uma única vez, na thread principal, com a posição ou null.
     *
     * Pede a todos os provedores ao mesmo tempo. Só GPS falhava dentro de casa com a tela
     * apagada: sem fix em 15 s, a posição dependia de haver leitura recente por acaso.
     * Responde já com precisão de até [GOOD_M]; senão espera o prazo e fica com a melhor.
     */
    fun request(c: Context, store: Store, timeoutMs: Long = 12_000, done: (Position?) -> Unit) {
        if (!allowed(c, store)) {
            LiveState.main.post { done(null) }
            return
        }
        val manager = c.getSystemService(LocationManager::class.java)
        val finished = AtomicBoolean(false)
        val cancels = mutableListOf<CancellationSignal>()
        val listeners = mutableListOf<LocationListener>()
        var best: Location? = null
        var pending = 0
        fun finish() {
            if (!finished.compareAndSet(false, true)) return
            cancels.forEach { it.cancel() }
            listeners.forEach { manager.removeUpdates(it) }
            val fresh = toPosition(best)
            if (fresh != null) LiveState.location = best
            done(fresh ?: recent(manager))
        }
        // Sempre na thread principal: best e pending não precisam de trava.
        fun arrived(l: Location?) {
            if (finished.get()) return
            pending--
            if (toPosition(l) != null && (best == null || l!!.accuracy < best!!.accuracy)) best = l
            if ((best?.accuracy ?: Float.MAX_VALUE) <= GOOD_M || pending <= 0) finish()
        }
        LiveState.main.postDelayed({ finish() }, timeoutMs)
        try {
            val fine =
                c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            val enabled = manager.getProviders(true)
            val providers =
                listOfNotNull(
                        if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null,
                        LocationManager.NETWORK_PROVIDER,
                        if (fine) LocationManager.GPS_PROVIDER else null,
                    )
                    .filter { it in enabled }
            if (providers.isEmpty()) {
                LiveState.main.post { finish() }
                return
            }
            pending = providers.size
            for (provider in providers) {
                if (Build.VERSION.SDK_INT >= 30) {
                    val cancel = CancellationSignal()
                    cancels += cancel
                    manager.getCurrentLocation(provider, cancel, c.mainExecutor) {
                        LiveState.main.post { arrived(it) }
                    }
                } else {
                    val l = LocationListener { LiveState.main.post { arrived(it) } }
                    listeners += l
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, l, Looper.getMainLooper())
                }
            }
        } catch (_: SecurityException) {
            LiveState.main.post { finish() }
        } catch (_: IllegalArgumentException) {
            LiveState.main.post { finish() }
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
