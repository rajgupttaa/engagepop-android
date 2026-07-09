package com.engagepop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Builds and shows a notification for a foreground FCM message. (When the app is
 * backgrounded, the system tray shows `notification` messages itself; the tap is
 * then handled by [EngagePop.handleNotificationOpen] in the launched Activity.)
 */
internal object PushDisplay {
    private const val CHANNEL_ID = "engagepop_default"
    const val EXTRA_NID = "ep_nid"
    const val EXTRA_URL = "ep_url"

    fun show(context: Context, title: String, body: String, nid: Long, url: String?) {
        val ctx = context.applicationContext
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        // Reopen the app's launcher, carrying nid/url so the tap is tracked +
        // routed by handleNotificationOpen.
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_NID, nid)
            if (url != null) putExtra(EXTRA_URL, url)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(ctx, nid.toInt(), launch, flags)

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(nid.toInt(), notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Notifications", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }
}
