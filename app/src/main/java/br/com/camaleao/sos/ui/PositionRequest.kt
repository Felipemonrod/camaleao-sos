package br.com.camaleao.sos.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import br.com.camaleao.sos.LiveState

internal fun MainActivity.requestLocation() {
    if (!store.flag("location_consent")) {
        toast("Autorize a inclusão da localização primeiro.")
        return
    }
    if (
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            3,
        )
        return
    }
    locator?.stop()
    locator = PositionRequest(this)
    locator?.start()
}

internal class PositionRequest(private val activity: MainActivity) {
    private val manager = activity.getSystemService(LocationManager::class.java)
    private var cancel: CancellationSignal? = null
    private var listener: LocationListener? = null
    private val timeout = Runnable {
        stop()
        activity.toast("Sem posição recente. Continue sem localização.")
    }

    fun start() {
        val provider =
            if (
                activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED &&
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            )
                LocationManager.GPS_PROVIDER
            else LocationManager.NETWORK_PROVIDER
        LiveState.main.postDelayed(timeout, 12000)
        try {
            if (
                activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED &&
                    activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED
            )
                throw SecurityException()
            if (Build.VERSION.SDK_INT >= 30) {
                cancel = CancellationSignal()
                manager.getCurrentLocation(provider, cancel, activity.mainExecutor) { accept(it) }
            } else {
                val l =
                    object : LocationListener {
                        override fun onLocationChanged(v: Location) {
                            accept(v)
                        }

                        override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}

                        override fun onProviderEnabled(p: String) {}

                        override fun onProviderDisabled(p: String) {}
                    }
                listener = l
                manager.requestSingleUpdate(provider, l, Looper.getMainLooper())
            }
            activity.toast("Buscando posição por até 12 segundos…")
        } catch (_: SecurityException) {
            stop()
            activity.toast("Permissão de localização revogada.")
        } catch (_: IllegalArgumentException) {
            stop()
            activity.toast("Provedor indisponível. Continue sem posição.")
        }
    }

    private fun accept(l: Location?) {
        stop()
        if (
            l != null &&
                SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos in 0..120_000_000_000L
        ) {
            LiveState.location = l
            activity.toast(
                "Posição disponível por até 2 minutos; precisão aproximada ${l.accuracy.toInt()} m."
            )
        } else activity.toast("Sem posição recente.")
    }

    fun stop() {
        cancel?.cancel()
        cancel = null
        listener?.let { manager.removeUpdates(it) }
        listener = null
        LiveState.main.removeCallbacks(timeout)
    }
}
