package com.engagepop

import org.json.JSONObject
import kotlin.math.abs

/** One A/B variant parsed from `config.ab`. `config` null = inherit the base. */
internal data class ABVariant(val key: String, val split: Int, val config: JSONObject?)

/**
 * Deterministic variant bucketing — identical to the web loader's `pickVariant`
 * and the iOS SDK (djb2-style 32-bit hash of `visitorId:campaignId`, then a
 * cumulative-split walk). Kotlin Int arithmetic wraps at 32 bits like JS `|0`,
 * so a device always sees the same variant and all platforms agree.
 */
internal object VariantPicker {
    fun pick(visitorId: String, campaignId: Long, variants: List<ABVariant>): ABVariant? {
        if (variants.isEmpty()) return null
        val total = variants.sumOf { maxOf(0, it.split) }
        if (total <= 0) return variants.first()

        var h = 0
        for (c in "$visitorId:$campaignId") {
            h = (h shl 5) - h + c.code
        }
        val bucket = abs(h) % total

        var cumulative = 0
        for (v in variants) {
            cumulative += maxOf(0, v.split)
            if (bucket < cumulative) return v
        }
        return variants.last()
    }

    fun parse(ab: JSONObject?): List<ABVariant> {
        val arr = ab?.optJSONArray("variants") ?: return emptyList()
        if (arr.length() < 2) return emptyList()
        val out = ArrayList<ABVariant>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            val key = v.optString("key").ifEmpty { continue }
            val cfg = v.optJSONObject("config")
            out.add(ABVariant(key, v.optInt("split", 0), cfg))
        }
        return if (out.size >= 2) out else emptyList()
    }
}
