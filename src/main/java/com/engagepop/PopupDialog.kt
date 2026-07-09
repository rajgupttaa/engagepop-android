package com.engagepop

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.URL
import java.util.concurrent.Executors

/**
 * Renders a parsed [Popup] as a native modal dialog. Supports the common block
 * subset (heading, text, image, button, email capture, divider, spacer);
 * unsupported blocks are skipped.
 */
internal class PopupDialog(
    context: Context,
    private val popup: Popup,
    private val attributes: Map<String, String>,
) : Dialog(context) {

    var onImpression: (() -> Unit)? = null
    var onButton: ((action: String, url: String?) -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onSubmit: ((email: String) -> Unit)? = null

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var emailField: EditText? = null
    private var reported = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildCard())
        window?.setBackgroundDrawable(overlayDrawable())
        setOnCancelListener { onClose?.invoke() }
    }

    override fun onStart() {
        super.onStart()
        if (!reported) {
            reported = true
            onImpression?.invoke()
        }
    }

    private fun overlayDrawable(): android.graphics.drawable.Drawable {
        val s = popup.settings
        if (!s.overlayEnabled) return android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        val base = parseHexColor(s.overlayColor) ?: Color.BLACK
        val alpha = (s.overlayOpacity.coerceIn(0, 100) * 255 / 100)
        return android.graphics.drawable.ColorDrawable((alpha shl 24) or (base and 0xFFFFFF))
    }

    private fun buildCard(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(popup.root.padding)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(parseHexColor(popup.root.bgColor) ?: Color.WHITE)
                cornerRadius = dp(popup.root.borderRadius).toFloat()
                if (popup.root.borderWidth > 0) {
                    setStroke(dp(popup.root.borderWidth), parseHexColor(popup.root.borderColor) ?: Color.LTGRAY)
                }
            }
        }

        for (block in popup.blocks) {
            makeView(block)?.let { card.addView(it) }
        }

        val scroll = ScrollView(context).apply {
            addView(card, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val margin = dp(20f)
            setPadding(margin, margin, margin, margin)
            clipToPadding = false
        }
        return scroll
    }

    private fun makeView(block: Block): View? = when (block) {
        is Block.Heading -> textView(merge(block.text), block.fontSize, block.color, block.align, block.weight >= 600)
        is Block.Text -> textView(merge(block.text), block.fontSize, block.color, block.align, false)
        is Block.Image -> imageView(block.src, block.radius)
        is Block.Button -> button(merge(block.text), block.action, block.url, block.bgColor, block.textColor, block.radius)
        is Block.EmailCapture -> emailCapture(block)
        is Block.Divider -> View(context).apply {
            setBackgroundColor(parseHexColor(block.color) ?: Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).also { it.topMargin = dp(8f); it.bottomMargin = dp(8f) }
        }
        is Block.Spacer -> View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(block.height))
        }
        is Block.Unsupported -> { EPLog.d { "skipping unsupported block: ${block.type}" }; null }
    }

    private fun merge(text: String) = MergeTags.resolve(text, attributes)

    private fun textView(text: String, size: Float, color: String, align: String, bold: Boolean) = TextView(context).apply {
        this.text = text
        // Web font sizes are px; map them to dp so they scale sensibly on Android.
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, size)
        setTextColor(parseHexColor(color) ?: Color.BLACK)
        gravity = gravityFor(align)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = spacedRow()
    }

    private fun imageView(src: String, radius: Float): View {
        val iv = ImageView(context).apply {
            adjustViewBounds = true
            maxHeight = dp(240f)
            layoutParams = spacedRow()
        }
        io.execute {
            try {
                val bytes = URL(src).openStream().use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                main.post { iv.setImageBitmap(bmp) }
            } catch (e: Exception) {
                EPLog.e("image load failed", e)
            }
        }
        return iv
    }

    private fun button(text: String, action: String, url: String?, bg: String, fg: String, radius: Float) = Button(context).apply {
        this.text = text
        setTextColor(parseHexColor(fg) ?: Color.WHITE)
        background = GradientDrawable().apply {
            setColor(parseHexColor(bg) ?: Color.BLACK)
            cornerRadius = dp(radius).toFloat()
        }
        layoutParams = spacedRow()
        setOnClickListener {
            onButton?.invoke(action, url)
            if (action == "close" || action == "url" || action == "none") dismiss()
        }
    }

    private fun emailCapture(block: Block.EmailCapture): View {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = spacedRow() }
        val field = EditText(context).apply {
            hint = block.placeholder
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        emailField = field
        val submit = Button(context).apply {
            text = block.buttonText
            setTextColor(parseHexColor(block.buttonTextColor) ?: Color.WHITE)
            background = GradientDrawable().apply {
                setColor(parseHexColor(block.buttonColor) ?: Color.BLACK)
                cornerRadius = dp(10f).toFloat()
            }
            setOnClickListener {
                val email = field.text.toString().trim()
                if (email.contains("@")) {
                    onSubmit?.invoke(email)
                    dismiss()
                }
            }
        }
        col.addView(field)
        col.addView(submit)
        return col
    }

    private fun gravityFor(align: String) = when (align) {
        "left" -> Gravity.START
        "right" -> Gravity.END
        else -> Gravity.CENTER
    }

    private fun spacedRow() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).also { it.topMargin = dp(6f); it.bottomMargin = dp(6f) }

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).toInt()
}
