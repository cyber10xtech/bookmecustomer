package com.bookmebusiness.bookmeapp

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles incoming FCM push notifications for BookMe Business.
 *
 * Previously there was NO FirebaseMessagingService in the project, which meant:
 *  - Notifications only appeared when the app was in the FOREGROUND via Firebase's
 *    default handling (and even then, only if sent as "data" messages).
 *  - Background / killed-state notifications were delivered by the system tray but
 *    tapping them opened a blank MainActivity with no context.
 *  - Token refreshes were never captured, so the server could hold stale tokens.
 *
 * This service fixes all of that.
 */
class BookmeFirebaseMessagingService : FirebaseMessagingService() {

    /**
     * Called when a new FCM registration token is generated (first install, app
     * data cleared, token rotated by Firebase). Send the token to your server here
     * so it can target this device.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: send `token` to your back-end so the server can address this device.
        // Example:
        //   sendTokenToServer(token)
        android.util.Log.d("FCM", "New token: $token")
    }

    /**
     * Called for every incoming message while the app is in the foreground.
     *
     * For background / killed-state messages that contain a `notification` block,
     * Android displays the notification automatically. If you send *data-only*
     * messages you must build the notification yourself – this method handles both.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Prefer explicit `notification` block fields; fall back to `data` payload.
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: getString(R.string.app_name)

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: return // nothing to show

        // Optional deep-link URL coming from the data payload
        val deepUrl = remoteMessage.data["url"]

        showNotification(title, body, deepUrl)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun showNotification(title: String, body: String, deepUrl: String?) {
        // Build the intent that opens MainActivity (and carries the deep-link URL
        // so MainActivity.handleNotificationIntent() can navigate to it).
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            deepUrl?.let { putExtra("url", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // unique request code avoids clobbering
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, MainActivity.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)          // your app icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // wrap long text
            .setAutoCancel(true)                    // dismiss on tap
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use a unique ID per notification so multiple can appear simultaneously
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
