package com.engagepop

/**
 * Resolves `{{ variable | fallback }}` tags against the identify store — same
 * syntax + regex as the web loader and the iOS SDK, so text renders identically.
 */
internal object MergeTags {
    private val regex = Regex("""\{\{\s*([a-zA-Z][\w-]*)\s*(?:\|([^}]*))?\}\}""")

    fun resolve(text: String, attributes: Map<String, String>): String {
        if (!text.contains("{{")) return text
        return regex.replace(text) { m ->
            val key = m.groupValues[1]
            val fallback = m.groupValues[2]
            val value = attributes[key]
            if (!value.isNullOrEmpty()) value else fallback
        }
    }
}
