package com.engagepop

/**
 * Immutable SDK configuration, set once via [EngagePop.configure].
 *
 * @param siteKey  Public site key from the dashboard (e.g. `ep_…`).
 * @param appKey   Per-app key from the dashboard's Mobile Apps screen (`epm_…`).
 * @param apiBaseUrl Delivery-plane base URL (defaults to EngagePop production).
 * @param debugLogging Log network calls to Logcat when true.
 */
data class EngagePopConfig @JvmOverloads constructor(
    val siteKey: String,
    val appKey: String,
    val apiBaseUrl: String = "https://edge.engagepop.com",
    val debugLogging: Boolean = false,
    /** When true (default), an eligible in-app popup shows automatically after
     *  configure. Set false to control placement via refreshInAppMessages(). */
    val autoShowInAppMessages: Boolean = true,
)

internal object EngagePopInfo {
    const val SDK_VERSION = "0.2.1"
    const val PLATFORM = "android"
}

internal object EPLog {
    @Volatile var enabled = false
    private const val TAG = "EngagePop"

    fun d(message: () -> String) {
        if (enabled) android.util.Log.d(TAG, message())
    }

    fun e(message: String, t: Throwable? = null) {
        android.util.Log.w(TAG, message, t)
    }
}
