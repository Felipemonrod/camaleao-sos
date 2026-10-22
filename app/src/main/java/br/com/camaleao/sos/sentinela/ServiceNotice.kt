package br.com.camaleao.sos.sentinela

import android.app.*
import android.content.Context
import android.content.Intent
import br.com.camaleao.sos.R
import br.com.camaleao.sos.ui.MainActivity

object ServiceNotice {
    const val ACTIVE = 201
    const val REMIND = 202

    fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun init(c: Context) {
        manager(c)
            .createNotificationChannel(
                NotificationChannel(
                        "sentinela-v2",
                        "Microfone e rearme",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = Notification.VISIBILITY_SECRET
                    }
            )
    }

    fun notice(c: Context, message: String, active: Boolean): Notification {
        init(c)
        val open =
            PendingIntent.getActivity(
                c,
                0,
                Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Builder(c, "sentinela-v2")
            .setSmallIcon(R.drawable.ic_calculator)
            .setContentTitle("Cálculos · microfone")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(open)
            .setOngoing(active)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .apply {
                if (active)
                    addAction(
                        Notification.Action.Builder(
                                null,
                                "Desarmar",
                                PendingIntent.getService(
                                    c,
                                    1,
                                    Intent(c, SentinelaService::class.java).setAction("STOP"),
                                    PendingIntent.FLAG_IMMUTABLE,
                                ),
                            )
                            .build()
                    )
            }
            .build()
    }

    fun reminder(c: Context) {
        init(c)
        if (manager(c).areNotificationsEnabled())
            manager(c)
                .notify(
                    REMIND,
                    notice(c, "Sentinela desarmada. Abra a Área protegida para rearmar.", false),
                )
    }
}
