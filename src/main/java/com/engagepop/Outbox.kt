package com.engagepop

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A persisted, serialized outbox for the SDK's writes (register + events).
 *
 *  1. **Reliability** — a POST that fails (offline at launch, exactly when
 *     register fires) is retried with exponential backoff and survives process
 *     death, so a device still eventually registers instead of silently not.
 *  2. **Thread safety** — one single-thread scheduled executor owns the queue,
 *     so it's also the data layer's concurrency boundary (`serial { }`).
 */
internal class Outbox(context: Context, private val config: EngagePopConfig) {

    private data class Item(val id: String, val path: String, val body: String, var attempts: Int)

    private val exec = Executors.newSingleThreadScheduledExecutor()
    private val prefs = context.applicationContext.getSharedPreferences("engagepop.outbox", Context.MODE_PRIVATE)
    private val storeKey = "items"
    private val items = ArrayList<Item>()
    private var flushing = false
    private val maxItems = 500

    init {
        load()
    }

    /** Run work on the outbox's serial thread (SDK's single writer). */
    fun serial(block: () -> Unit) = exec.execute(block)

    fun enqueue(path: String, body: String) {
        exec.execute {
            items.add(Item(UUID.randomUUID().toString(), path, body, 0))
            trimAndPersist()
            flushLocked()
        }
    }

    fun flush() = exec.execute { flushLocked() }

    // MARK: internals (always on `exec`)

    private fun flushLocked() {
        if (flushing || items.isEmpty()) return
        flushing = true
        val first = items[0]
        val done = send(first) // synchronous on this worker thread
        flushing = false
        if (done) {
            items.removeAll { it.id == first.id }
            trimAndPersist()
            flushLocked()
        } else {
            first.attempts += 1
            trimAndPersist()
            val delaySec = Math.min(Math.pow(2.0, first.attempts.toDouble()).toLong(), 300L)
            exec.schedule({ flushLocked() }, delaySec, TimeUnit.SECONDS)
        }
    }

    /** true = done with this item (delivered, or permanently rejected). */
    private fun send(item: Item): Boolean {
        val conn = URL(config.apiBaseUrl.trimEnd('/') + item.path).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(item.body.toByteArray()) }
            val status = conn.responseCode
            when {
                status in 200..299 -> true                       // delivered
                status in 400..499 && status != 429 -> {          // permanent → drop
                    EPLog.e("outbox ${item.path} rejected ($status) — dropping")
                    true
                }
                else -> false                                     // transient → retry
            }
        } catch (e: Exception) {
            EPLog.e("outbox ${item.path} failed", e)
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun trimAndPersist() {
        if (items.size > maxItems) {
            val extra = items.size - maxItems
            repeat(extra) { items.removeAt(0) }
        }
        val arr = JSONArray()
        for (i in items) {
            arr.put(JSONObject().put("id", i.id).put("path", i.path).put("body", i.body).put("attempts", i.attempts))
        }
        prefs.edit().putString(storeKey, arr.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(storeKey, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Item(o.getString("id"), o.getString("path"), o.getString("body"), o.optInt("attempts", 0)))
            }
        } catch (e: Exception) {
            EPLog.e("outbox load failed", e)
        }
    }
}
