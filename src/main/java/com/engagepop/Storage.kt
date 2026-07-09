package com.engagepop

import android.content.Context
import java.util.UUID

/**
 * SharedPreferences-backed persistence: the last registered token, the identify
 * attribute store, a stable visitor id (for A/B bucketing), and the subscribed
 * flag. The FCM token is not a secret, so plain prefs are fine.
 */
internal class Storage(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("engagepop", Context.MODE_PRIVATE)

    var lastPushToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var subscribed: Boolean
        get() = prefs.getBoolean(KEY_SUBSCRIBED, false)
        set(value) = prefs.edit().putBoolean(KEY_SUBSCRIBED, value).apply()

    /** Stable per-install id, generated once. */
    val visitorId: String
        get() {
            prefs.getString(KEY_VISITOR, null)?.let { return it }
            val id = "v_" + UUID.randomUUID().toString().replace("-", "").lowercase()
            prefs.edit().putString(KEY_VISITOR, id).apply()
            return id
        }

    var attributes: Map<String, String>
        get() {
            val raw = prefs.getString(KEY_ATTRS, null) ?: return emptyMap()
            return try {
                val obj = org.json.JSONObject(raw)
                buildMap { obj.keys().forEach { k -> put(k, obj.optString(k)) } }
            } catch (e: Exception) {
                emptyMap()
            }
        }
        set(value) {
            val obj = org.json.JSONObject()
            value.forEach { (k, v) -> obj.put(k, v) }
            prefs.edit().putString(KEY_ATTRS, obj.toString()).apply()
        }

    fun clearAttributes() = prefs.edit().remove(KEY_ATTRS).apply()

    private companion object {
        const val KEY_TOKEN = "lastPushToken"
        const val KEY_ATTRS = "attributes"
        const val KEY_VISITOR = "visitorId"
        const val KEY_SUBSCRIBED = "subscribed"
    }
}
