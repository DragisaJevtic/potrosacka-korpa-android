package dev.intelisystems.potrosackakorpa

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Shared visual language for catalog, shopping, location and price history. */
object KorpaUi {
    val paper = Color.rgb(244, 245, 239)
    val ink = Color.rgb(26, 42, 34)
    val muted = Color.rgb(80, 97, 85)
    val forest = Color.rgb(27, 76, 57)
    val mint = Color.rgb(222, 238, 224)
    val white = Color.WHITE
    val warning = Color.rgb(131, 56, 22)
    val border = Color.rgb(211, 221, 211)

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun rounded(context: Context, color: Int, radius: Int = 19) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat()
    }
    fun card(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(context, white).apply { setStroke(dp(context, 1), border) }
        val padding = dp(context, 16)
        setPadding(padding, padding, padding, padding)
    }
    fun label(context: Context, value: String, size: Int = 15, color: Int = ink, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    fun styleButton(button: Button, fill: Int = mint, color: Int = forest) = button.apply {
        isAllCaps = false; textSize = 14f; setTextColor(color)
        setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
        minimumHeight = dp(context, 48); minHeight = dp(context, 48)
        minimumWidth = dp(context, 48); minWidth = dp(context, 48)
        stateListAnimator = null
        background = RippleDrawable(ColorStateList.valueOf(0x22407558), rounded(context, fill, 14), null)
    }
    fun button(context: Context, value: String, primary: Boolean = false, action: () -> Unit) =
        styleButton(Button(context), if (primary) forest else mint, if (primary) white else forest).apply {
            text = value; setOnClickListener { action() }
        }
    fun input(view: EditText) = view.apply {
        textSize = 16f; setTextColor(ink); setHintTextColor(muted)
        minHeight = dp(context, 52)
        setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
        background = rounded(context, white, 14).apply { setStroke(dp(context, 1), border) }
    }
}
