package com.engagepop

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Delivery-plane HTTP client. Runs off the main thread on a small executor;
 * callbacks are invoked on that background thread (the caller marshals to the UI
 * thread when it needs to). Dependency-free (HttpURLConnection + org.json).
 */
internal class ApiClient(private val config: EngagePopConfig) {
    private val executor = Executors.newSingleThreadExecutor()

    fun register(body: JSONObject, onResult: ((Boolean) -> Unit)? = null) {
        post("/v1/mobile/register", body, onResult)
    }

    fun sendEvents(body: JSONObject, onResult: ((Boolean) -> Unit)? = null) {
        post("/v1/mobile/events", body, onResult)
    }

    fun fetchConfig(siteKey: String, onResult: (JSONObject?) -> Unit) {
        executor.execute {
            val conn = open("/v1/mobile/config/$siteKey", "GET")
            try {
                conn.connect()
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    onResult(JSONObject(text))
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                EPLog.e("fetchConfig failed", e)
                onResult(null)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun post(path: String, body: JSONObject, onResult: ((Boolean) -> Unit)?) {
        executor.execute {
            val conn = open(path, "POST")
            try {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val ok = conn.responseCode in 200..299
                if (!ok) EPLog.e("$path returned HTTP ${conn.responseCode}")
                onResult?.invoke(ok)
            } catch (e: Exception) {
                EPLog.e("$path failed", e)
                onResult?.invoke(false)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val url = URL(config.apiBaseUrl.trimEnd('/') + path)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
    }
}

/** Small helpers to build the request bodies the Go edge expects. */
internal object Requests {
    fun register(
        config: EngagePopConfig,
        token: String,
        attributes: Map<String, String>,
        locale: String,
        timezone: String,
        appVersion: String,
    ): JSONObject = JSONObject().apply {
        put("site_key", config.siteKey)
        put("app_key", config.appKey)
        put("platform", EngagePopInfo.PLATFORM)
        put("token", token)
        if (attributes.isNotEmpty()) put("attributes", JSONObject(attributes as Map<*, *>))
        put("locale", locale)
        put("timezone", timezone)
        put("app_version", appVersion)
        put("sdk_version", EngagePopInfo.SDK_VERSION)
    }

    fun events(config: EngagePopConfig, token: String?, events: JSONArray): JSONObject =
        JSONObject().apply {
            put("site_key", config.siteKey)
            put("app_key", config.appKey)
            if (token != null) put("token", token)
            put("events", events)
        }
}
