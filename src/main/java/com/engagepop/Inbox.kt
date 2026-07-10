package com.engagepop

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One captured notification, for the in-app inbox / bell. */
data class EngagePopMessage(
    val id: String,
    val title: String,
    val body: String,
    val url: String?,
    val receivedAt: Long, // epoch millis
    val read: Boolean,
)

/**
 * A local notification history — the "bell/inbox" the SDK keeps for you.
 * On Android the FCM service runs in the app process, so this captures
 * foreground and tapped notifications without any extra setup. Set [onChanged]
 * to refresh your badge when the list changes.
 */
class Inbox internal constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("engagepop.inbox", Context.MODE_PRIVATE)
    private val key = "messages"
    private val lock = Any()
    private val maxMessages = 200

    /** Invoked (on the calling thread) whenever the inbox changes. */
    @Volatile
    var onChanged: (() -> Unit)? = null

    /** All messages, newest first. */
    fun messages(): List<EngagePopMessage> = synchronized(lock) {
        load().sortedByDescending { it.receivedAt }
    }

    /** Unread count — bind to your bell badge. */
    fun unreadCount(): Int = synchronized(lock) { load().count { !it.read } }

    fun markRead(id: String) = mutate { m -> if (m.id == id) m.copy(read = true) else m }
    fun markAllRead() = mutate { it.copy(read = true) }

    fun remove(id: String) = synchronized(lock) {
        save(load().filter { it.id != id })
        notifyChanged()
    }

    fun clear() = synchronized(lock) {
        save(emptyList())
        notifyChanged()
    }

    // MARK: internal writer

    internal fun add(message: EngagePopMessage) = synchronized(lock) {
        val all = load()
        if (all.any { it.id == message.id }) return  // de-dupe by id
        val next = (all + message).let { if (it.size > maxMessages) it.takeLast(maxMessages) else it }
        save(next)
        notifyChanged()
    }

    // MARK: storage

    private fun mutate(transform: (EngagePopMessage) -> EngagePopMessage) = synchronized(lock) {
        save(load().map(transform))
        notifyChanged()
    }

    private fun load(): List<EngagePopMessage> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EngagePopMessage(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    body = o.optString("body"),
                    url = if (o.isNull("url")) null else o.optString("url"),
                    receivedAt = o.optLong("receivedAt"),
                    read = o.optBoolean("read", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(messages: List<EngagePopMessage>) {
        val arr = JSONArray()
        for (m in messages) {
            arr.put(
                JSONObject()
                    .put("id", m.id).put("title", m.title).put("body", m.body)
                    .put("url", m.url ?: JSONObject.NULL)
                    .put("receivedAt", m.receivedAt).put("read", m.read)
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun notifyChanged() {
        onChanged?.invoke()
    }
}
