package com.engagepop

import android.content.Context
import org.json.JSONObject

/** Parsed `campaigns.targeting`. */
internal data class Targeting(
    val frequencyMode: String,
    val frequencyMax: Int,
    val stopOnConvert: Boolean,
    val matchAll: Boolean,
    val conditions: List<Condition>,
) {
    data class Condition(val field: String, val op: String, val value: String)

    companion object {
        fun parse(v: JSONObject?): Targeting {
            val root = v ?: JSONObject()
            val freq = root.optJSONObject("frequency") ?: JSONObject()
            val conds = ArrayList<Condition>()
            root.optJSONArray("conditions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val field = c.optString("field").ifEmpty { continue }
                    conds.add(Condition(field, c.optString("op", "is"), c.optString("value", "")))
                }
            }
            return Targeting(
                frequencyMode = freq.optString("mode", "session"),
                frequencyMax = freq.optInt("max", 3),
                stopOnConvert = freq.optBoolean("stopOnConvert", true),
                matchAll = root.optString("match", "all") != "any",
                conditions = conds,
            )
        }
    }
}

/** Runtime facts a native app can supply for audience conditions. */
internal data class RuleContext(val device: String, val country: String?, val subscribed: Boolean)

/**
 * Evaluates audience conditions. Fields a native app can't know (page/referrer/
 * utm_*) are treated as satisfied so they never silently suppress a campaign.
 */
internal object Audience {
    fun matches(t: Targeting, ctx: RuleContext): Boolean {
        if (t.conditions.isEmpty()) return true
        val results = t.conditions.map { evaluate(it, ctx) }
        return if (t.matchAll) results.all { it } else results.any { it }
    }

    private fun evaluate(c: Targeting.Condition, ctx: RuleContext): Boolean {
        val actual = when (c.field) {
            "device" -> ctx.device
            "country" -> ctx.country ?: ""
            "subscribed" -> if (ctx.subscribed) "yes" else "no"
            else -> return true // unsupported field on native — don't block
        }.lowercase()
        val expect = c.value.lowercase()
        return when (c.op) {
            "is_not" -> actual != expect
            "contains" -> actual.contains(expect)
            "not_contains" -> !actual.contains(expect)
            else -> actual == expect
        }
    }
}

/**
 * Frequency capping persisted per campaign — mirrors the web loader's
 * `frequencyAllows` (stop-after-convert, once-per-session/day/week, or max
 * count). Session = process lifetime (an in-memory set).
 */
internal class FrequencyGate(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("engagepop.freq", Context.MODE_PRIVATE)
    private val sessionSeen = HashSet<Long>()

    fun allows(campaignId: Long, t: Targeting): Boolean {
        if (t.stopOnConvert && prefs.getBoolean(key("conv", campaignId), false)) return false
        return when (t.frequencyMode) {
            "session" -> !sessionSeen.contains(campaignId)
            "day" -> elapsedSinceLast(campaignId) >= 24 * 3600_000L
            "week" -> elapsedSinceLast(campaignId) >= 7 * 24 * 3600_000L
            "max" -> prefs.getInt(key("count", campaignId), 0) < t.frequencyMax
            else -> true // "every"
        }
    }

    fun recordShow(campaignId: Long) {
        sessionSeen.add(campaignId)
        prefs.edit()
            .putLong(key("last", campaignId), System.currentTimeMillis())
            .putInt(key("count", campaignId), prefs.getInt(key("count", campaignId), 0) + 1)
            .apply()
    }

    fun recordConvert(campaignId: Long) {
        prefs.edit().putBoolean(key("conv", campaignId), true).apply()
    }

    private fun elapsedSinceLast(id: Long): Long {
        val last = prefs.getLong(key("last", id), 0L)
        if (last == 0L) return Long.MAX_VALUE
        return System.currentTimeMillis() - last
    }

    private fun key(kind: String, id: Long) = "$kind.$id"
}
