package com.engagepop

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests (no Android framework) — merge tags, deterministic A/B
 * bucketing, and audience conditions. Run with `./gradlew test`.
 */
class LogicTest {

    @Test
    fun mergeTagsResolveAndFallback() {
        val attrs = mapOf("name" to "Sarah", "empty" to "")
        assertEquals("Hi Sarah!", MergeTags.resolve("Hi {{name}}!", attrs))
        assertEquals("Hi there!", MergeTags.resolve("Hi {{first|there}}!", attrs))
        assertEquals("Hi guest!", MergeTags.resolve("Hi {{empty|guest}}!", attrs))
        assertEquals("Hi !", MergeTags.resolve("Hi {{missing}}!", attrs))
        assertEquals("plain", MergeTags.resolve("plain", attrs))
    }

    @Test
    fun variantPickIsDeterministic() {
        val variants = listOf(ABVariant("a", 50, null), ABVariant("b", 50, null))
        val first = VariantPicker.pick("v_abc123", 7, variants)?.key
        repeat(20) {
            assertEquals(first, VariantPicker.pick("v_abc123", 7, variants)?.key)
        }
    }

    @Test
    fun variantPickRespectsFullSplit() {
        val variants = listOf(ABVariant("a", 100, null), ABVariant("b", 0, null))
        for (id in listOf("v_1", "v_2", "v_xyz")) {
            assertEquals("a", VariantPicker.pick(id, 1, variants)?.key)
        }
    }

    @Test
    fun variantParseNeedsTwo() {
        val ab = JSONObject("""{"variants":[{"key":"a","split":50},{"key":"b","split":50}]}""")
        assertEquals(2, VariantPicker.parse(ab).size)
        assertTrue(VariantPicker.parse(JSONObject("""{"variants":[{"key":"a","split":100}]}""")).isEmpty())
    }

    @Test
    fun audienceMatch() {
        val t = Targeting(
            frequencyMode = "every", frequencyMax = 0, stopOnConvert = false, matchAll = true,
            conditions = listOf(
                Targeting.Condition("device", "is", "mobile"),
                Targeting.Condition("subscribed", "is", "no"),
            ),
        )
        assertTrue(Audience.matches(t, RuleContext("mobile", null, false)))
        assertFalse(Audience.matches(t, RuleContext("tablet", null, false)))
        assertFalse(Audience.matches(t, RuleContext("mobile", null, true)))
    }

    @Test
    fun audienceIgnoresUnsupportedFields() {
        val t = Targeting(
            frequencyMode = "every", frequencyMax = 0, stopOnConvert = false, matchAll = true,
            conditions = listOf(Targeting.Condition("page", "contains", "/checkout")),
        )
        assertTrue(Audience.matches(t, RuleContext("mobile", null, false)))
    }

    @Test
    fun targetingParse() {
        val t = Targeting.parse(JSONObject("""{"frequency":{"mode":"day","stopOnConvert":true},"match":"any"}"""))
        assertEquals("day", t.frequencyMode)
        assertTrue(t.stopOnConvert)
        assertFalse(t.matchAll)
    }
}
