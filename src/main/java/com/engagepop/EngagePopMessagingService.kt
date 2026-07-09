package com.engagepop

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Default FCM service. Registered in the SDK manifest, so most apps need no FCM
 * plumbing of their own. Apps that already have a FirebaseMessagingService should
 * remove this from the merged manifest and instead forward events:
 *
 * ```
 * override fun onNewToken(token: String) { EngagePop.handleNewToken(token) }
 * override fun onMessageReceived(msg: RemoteMessage) { EngagePop.handleRemoteMessage(this, msg) }
 * ```
 */
class EngagePopMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        EngagePop.handleNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        EngagePop.handleRemoteMessage(this, message)
    }
}
