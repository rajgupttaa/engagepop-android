package com.engagepop

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.TimeZone

/**
 * EngagePop Android SDK — native push + in-app messages.
 *
 * ```
 * EngagePop.configure(context, EngagePopConfig(siteKey = "ep_…", appKey = "epm_…"))
 * EngagePop.requestNotificationPermission(activity)   // Android 13+
 * EngagePop.deepLinkHandler = { url -> /* navigate */ }
 * ```
 * In your launcher Activity, forward taps so opens are tracked + routed:
 * ```
 * override fun onCreate(b: Bundle?) { …; EngagePop.handleNotificationOpen(intent) }
 * override fun onNewIntent(i: Intent) { …; EngagePop.handleNotificationOpen(i) }
 * ```
 */
object EngagePop {
    val shared: EngagePop get() = this

    private lateinit var appContext: Context
    private lateinit var config: EngagePopConfig
    private lateinit var api: ApiClient
    private lateinit var storage: Storage
    private var inApp: InAppManager? = null
    private var configured = false
    private var currentActivityRef: WeakReference<Activity>? = null

    /** Called with a deep-link URL when the user taps a push/popup that carries one. */
    @JvmStatic
    var deepLinkHandler: ((String) -> Unit)? = null

    // MARK: - Configuration

    @JvmStatic
    fun configure(context: Context, config: EngagePopConfig) {
        appContext = context.applicationContext
        this.config = config
        EPLog.enabled = config.debugLogging
        api = ApiClient(config)
        storage = Storage(appContext)
        inApp = InAppManager(appContext, config, api, storage)
        configured = true

        trackActivities()

        // Capture the current FCM token and register (onNewToken covers refreshes).
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            handleNewToken(token)
        }
        inApp?.refresh()
        EPLog.d { "configured for site ${config.siteKey}" }
    }

    /** Android 13+ runtime notification permission. No-op below API 33. */
    @JvmStatic
    fun requestNotificationPermission(activity: Activity, requestCode: Int = 9613) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), requestCode)
        }
    }

    // MARK: - Push

    @JvmStatic
    fun handleNewToken(token: String) {
        if (!configured) return
        val attrs = storage.attributes
        val body = Requests.register(
            config, token, attrs,
            locale = java.util.Locale.getDefault().toString(),
            timezone = TimeZone.getDefault().id,
            appVersion = appVersion(),
        )
        api.register(body) { ok -> if (ok) storage.lastPushToken = token }
    }

    /** Show a foreground FCM message. (Backgrounded `notification` messages are
     *  drawn by the system; their tap is handled by [handleNotificationOpen].) */
    @JvmStatic
    fun handleRemoteMessage(context: Context, message: RemoteMessage) {
        val data = message.data
        val nid = data["nid"]?.toLongOrNull() ?: 0L
        val title = message.notification?.title ?: data["title"] ?: return
        val body = message.notification?.body ?: data["body"] ?: ""
        PushDisplay.show(context, title, body, nid, data["url"])
    }

    /** Report a push open + route its deep link. Call from your launcher Activity. */
    @JvmStatic
    fun handleNotificationOpen(intent: Intent?) {
        val nid = intent?.getLongExtra(PushDisplay.EXTRA_NID, 0L) ?: 0L
        if (nid <= 0L || !configured) return
        val event = JSONObject().put("type", "push_open").put("nid", nid)
        api.sendEvents(Requests.events(config, storage.lastPushToken, JSONArray().put(event)))
        intent?.getStringExtra(PushDisplay.EXTRA_URL)?.let { url ->
            deepLinkHandler?.invoke(url) ?: currentActivity()?.let { openUrl(it, url) }
        }
    }

    // MARK: - Data layer

    @JvmStatic
    fun identify(attributes: Map<String, String>) {
        if (!configured) return
        val merged = HashMap(storage.attributes).apply { putAll(attributes) }
        storage.attributes = merged
        val event = JSONObject().put("type", "identify").put("attributes", JSONObject(merged as Map<*, *>))
        api.sendEvents(Requests.events(config, storage.lastPushToken, JSONArray().put(event)))
    }

    @JvmStatic
    @JvmOverloads
    fun track(event: String, properties: Map<String, String>? = null) {
        if (!configured) return
        val p = properties ?: emptyMap()
        val obj = JSONObject().put("type", "track").put("event", event)
        p["product"]?.let { obj.put("product", it) }
        p["name"]?.let { obj.put("name", it) }
        p["location"]?.let { obj.put("location", it) }
        p["url"]?.let { obj.put("url", it) }
        api.sendEvents(Requests.events(config, null, JSONArray().put(obj)))
    }

    @JvmStatic
    @JvmOverloads
    fun convert(value: Double, order: String? = null, campaignId: Long? = null) {
        if (!configured) return
        val obj = JSONObject().put("type", "convert").put("value", value)
        order?.let { obj.put("order", it) }
        campaignId?.let { obj.put("campaign_id", it) }
        api.sendEvents(Requests.events(config, null, JSONArray().put(obj)))
        campaignId?.let { inApp?.recordConvert(it) }
    }

    /** Forget identify attributes (e.g. on logout). The device stays registered. */
    @JvmStatic
    fun reset() {
        if (configured) storage.clearAttributes()
    }

    /** Re-check for in-app popups (e.g. on a screen change / foreground). */
    @JvmStatic
    fun refreshInAppMessages() {
        inApp?.refresh()
    }

    // MARK: - Internals

    internal fun currentActivity(): Activity? = currentActivityRef?.get()

    internal fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            EPLog.e("openUrl failed", e)
        }
    }

    private fun appVersion(): String = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun trackActivities() {
        (appContext as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { currentActivityRef = WeakReference(activity) }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
