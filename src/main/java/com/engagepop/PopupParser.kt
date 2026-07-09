package com.engagepop

import org.json.JSONObject

internal data class PopupRoot(
    val bgColor: String,
    val maxWidth: Int,
    val borderRadius: Float,
    val padding: Float,
    val borderWidth: Float,
    val borderColor: String,
)

internal data class PopupSettings(
    val displayMode: String,
    val position: String,
    val overlayEnabled: Boolean,
    val overlayColor: String,
    val overlayOpacity: Int,
    val closeButton: Boolean,
)

/** A renderable block — a pragmatic subset of the web component set. */
internal sealed class Block {
    data class Heading(val text: String, val fontSize: Float, val color: String, val align: String, val weight: Int) : Block()
    data class Text(val text: String, val fontSize: Float, val color: String, val align: String) : Block()
    data class Image(val src: String, val maxWidth: Int, val radius: Float) : Block()
    data class Button(val text: String, val action: String, val url: String?, val bgColor: String, val textColor: String, val radius: Float) : Block()
    data class EmailCapture(val placeholder: String, val buttonText: String, val buttonColor: String, val buttonTextColor: String) : Block()
    data class Divider(val color: String) : Block()
    data class Spacer(val height: Float) : Block()
    data class Unsupported(val type: String) : Block()
}

internal data class Popup(
    val campaignId: Long,
    val variant: String?,
    val root: PopupRoot,
    val settings: PopupSettings,
    val blocks: List<Block>,
) {
    companion object {
        fun parse(campaignId: Long, variant: String?, config: JSONObject): Popup? {
            val puck = config.optJSONObject("puckData") ?: return null
            val rootProps = puck.optJSONObject("root")?.optJSONObject("props") ?: JSONObject()
            val settingsJson = config.optJSONObject("popupSettings") ?: JSONObject()
            val content = puck.optJSONArray("content") ?: return null

            val blocks = ArrayList<Block>(content.length())
            for (i in 0 until content.length()) {
                val item = content.optJSONObject(i) ?: continue
                val type = item.optString("type").ifEmpty { continue }
                blocks.add(parseBlock(type, item.optJSONObject("props") ?: JSONObject()))
            }
            if (blocks.isEmpty()) return null

            return Popup(campaignId, variant, parseRoot(rootProps), parseSettings(settingsJson), blocks)
        }

        private fun parseRoot(p: JSONObject) = PopupRoot(
            bgColor = p.optString("bgColor", "#ffffff"),
            maxWidth = p.optInt("maxWidth", 420),
            borderRadius = p.optDouble("borderRadius", 16.0).toFloat(),
            padding = p.optDouble("padding", 28.0).toFloat(),
            borderWidth = p.optDouble("borderWidth", 0.0).toFloat(),
            borderColor = p.optString("borderColor", "#e5e7eb"),
        )

        private fun parseSettings(p: JSONObject): PopupSettings {
            val mode = p.optString("displayMode", "modal")
            val overlayDefault = !(mode == "slide-in" || mode == "bar")
            return PopupSettings(
                displayMode = mode,
                position = p.optString("position", "center"),
                overlayEnabled = p.optBoolean("overlayEnabled", overlayDefault),
                overlayColor = p.optString("overlayColor", "#000000"),
                overlayOpacity = p.optInt("overlayOpacity", 50),
                closeButton = p.optBoolean("closeButton", true),
            )
        }

        private fun parseBlock(type: String, p: JSONObject): Block = when (type) {
            "Heading" -> Block.Heading(
                p.optString("text", ""), p.optDouble("fontSize", 28.0).toFloat(),
                p.optString("color", "#111111"), p.optString("align", "center"),
                p.optInt("fontWeight", 700),
            )
            "Text", "DynamicText" -> Block.Text(
                p.optString("text", ""), p.optDouble("fontSize", 16.0).toFloat(),
                p.optString("color", "#333333"), p.optString("align", "center"),
            )
            "Image" -> Block.Image(
                p.optString("src", ""), p.optInt("maxWidth", 320), p.optDouble("borderRadius", 0.0).toFloat(),
            )
            "Button" -> Block.Button(
                p.optString("text", "Continue"), p.optString("action", "close"),
                p.optString("url", "").ifEmpty { null },
                p.optString("bgColor", "#111111"), p.optString("textColor", "#ffffff"),
                p.optDouble("borderRadius", 10.0).toFloat(),
            )
            "EmailCapture" -> Block.EmailCapture(
                p.optString("placeholder", "Your email"), p.optString("buttonText", "Subscribe"),
                p.optString("buttonColor", "#111111"), p.optString("buttonTextColor", "#ffffff"),
            )
            "Divider" -> Block.Divider(p.optString("color", "#e5e7eb"))
            "Spacer" -> Block.Spacer(p.optDouble("height", 16.0).toFloat())
            else -> Block.Unsupported(type)
        }
    }
}

/** Parses `#rgb` / `#rrggbb` / `#rrggbbaa` to an ARGB int, or null. */
internal fun parseHexColor(hex: String): Int? {
    var s = hex.trim()
    if (!s.startsWith("#")) return null
    s = s.substring(1)
    if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
    return try {
        when (s.length) {
            6 -> (0xFF000000.toInt()) or s.toInt(16)
            8 -> {
                val v = s.toLong(16)
                val a = (v and 0xFF).toInt()
                val rgb = (v shr 8).toInt() and 0xFFFFFF
                (a shl 24) or rgb
            }
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}
