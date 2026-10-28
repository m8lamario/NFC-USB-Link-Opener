package it.nfcusb.linkopener.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.nfcusb.linkopener.OpenLinkActivity
import it.nfcusb.linkopener.R

class ReaderNotifier(private val context: Context) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.notif_channel_status),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
        }
        val links = NotificationChannel(
            CHANNEL_LINK,
            context.getString(R.string.notif_channel_link),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(status)
        manager.createNotificationChannel(links)
    }

    fun ongoing(status: String): Notification {
        ensureChannels()
        val stop = PendingIntent.getService(
            context,
            REQUEST_STOP,
            Intent(context, ReaderForegroundService::class.java).setAction(ReaderForegroundService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.notif_stop), stop)
            .build()
    }

    fun showLink(url: String) {
        ensureChannels()
        val open = PendingIntent.getActivity(
            context,
            url.hashCode(),
            Intent(context, OpenLinkActivity::class.java)
                .putExtra(OpenLinkActivity.EXTRA_URL, url)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_LINK)
            .setSmallIcon(R.drawable.ic_stat_link)
            .setContentTitle(context.getString(R.string.notif_link_title))
            .setContentText(url)
            .setStyle(NotificationCompat.BigTextStyle().bigText(url))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notif_open), open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(LINK_ID, notification)
        } catch (_: SecurityException) {
            // Il permesso notifiche è stato negato: il link resta nel log.
        }
    }

    companion object {
        const val ONGOING_ID = 1001
        const val LINK_ID = 1002
        private const val REQUEST_STOP = 7
        const val CHANNEL_STATUS = "reader_status"
        const val CHANNEL_LINK = "link_detected"
    }
}
