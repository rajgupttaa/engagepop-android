package com.engagepop

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.content.res.Configuration
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches the site's in-app campaigns and shows the first eligible popup:
 * resolves the A/B variant, checks targeting + frequency, waits for the trigger,
 * renders natively via [PopupDialog], and reports the funnel.
 */
internal class InAppManager(
    private val context: Context,
    private val config: EngagePopConfig,
    private val api: ApiClient,
    private val storage: Storage,
) {
    private val gate = FrequencyGate(context)
    private val main = Handler(Looper.getMainLooper())

    fun recordConvert(campaignId: Long) = gate.recordConvert(campaignId)

    fun refresh() {
        api.fetchConfig(config.siteKey) { resp ->
            val campaigns = resp?.optJSONArray("campaigns") ?: return@fetchConfig
            evaluate(campaigns)
        }
    }

    private fun evaluate(campaigns: JSONArray) {
        for (i in 0 until campaigns.length()) {
            val c = campaigns.optJSONObject(i) ?: continue
            if (c.optString("type") != "popup") continue
            val campaignId = c.optString("id").toLongOrNull() ?: continue
            val baseConfig = c.optJSONObject("config") ?: continue

            val variants = VariantPicker.parse(baseConfig.optJSONObject("ab"))
            val variant = if (variants.isNotEmpty())
                VariantPicker.pick(storage.visitorId, campaignId, variants) else null
            val resolvedConfig = variant?.config ?: baseConfig

            val popup = Popup.parse(campaignId, variant?.key, resolvedConfig) ?: continue
            val targeting = Targeting.parse(c.optJSONObject("targeting"))
            if (!gate.allows(campaignId, targeting)) continue
            if (!Audience.matches(targeting, ruleContext())) continue

            val delayMs = triggerDelayMs(c.optJSONArray("triggers"))
            main.postDelayed({ present(popup) }, delayMs)
            return // one popup per refresh
        }
    }

    private fun ruleContext(): RuleContext {
        val isTablet = context.resources.configuration.screenLayout and
            Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE
        return RuleContext(device = if (isTablet) "tablet" else "mobile", country = null, subscribed = storage.subscribed)
    }

    /** Native triggers: load (0), delay (seconds). Web-only triggers → 1s. */
    private fun triggerDelayMs(triggers: JSONArray?): Long {
        if (triggers == null || triggers.length() == 0) return 1000
        for (i in 0 until triggers.length()) {
            val t = triggers.optJSONObject(i) ?: continue
            when (t.optString("type")) {
                "load" -> return 0
                "delay" -> return (t.optDouble("value", 5.0) * 1000).toLong()
            }
        }
        return 1000
    }

    private fun present(popup: Popup) {
        val activity = EngagePop.currentActivity() ?: return
        val dialog = PopupDialog(activity, popup, storage.attributes)
        dialog.onImpression = {
            gate.recordShow(popup.campaignId)
            send("impression", popup)
        }
        dialog.onButton = { action, url ->
            send("click", popup, label = url)
            if (action == "url" && url != null) {
                EngagePop.shared.deepLinkHandler?.invoke(url) ?: EngagePop.openUrl(activity, url)
            }
        }
        dialog.onClose = { send("close", popup) }
        dialog.onSubmit = { email ->
            storage.subscribed = true
            send("submit", popup, email = email)
        }
        dialog.show()
    }

    private fun send(type: String, popup: Popup, label: String? = null, email: String? = null) {
        val event = JSONObject().apply {
            put("type", type)
            put("campaign_id", popup.campaignId)
            popup.variant?.let { put("variant", it) }
            label?.let { put("label", it) }
            email?.let {
                put("email", it)
                put("fields", JSONObject().put("email", it))
            }
        }
        api.sendEvents(Requests.events(config, storage.lastPushToken, JSONArray().put(event)))
    }
}
