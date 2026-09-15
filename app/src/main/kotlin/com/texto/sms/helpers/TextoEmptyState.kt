package com.texto.sms.helpers

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config
import com.texto.sms.extensions.withAlpha

/**
 * An empty screen that says what it is, why it is empty and what to do about it.
 *
 * The archive, the recycle bin and the blocked list each showed one italic line at the top of
 * an otherwise bare page -- "No archived conversations found" -- which reads like a search that
 * came back empty, or like a screen that failed to load. This is the same three parts on all
 * three: the screen's own glyph on a soft accent disc, a short title, one sentence of what
 * lives here and how it gets here, and a way out where there is somewhere useful to go.
 *
 * Built beside the placeholder rather than instead of it, because the placeholder is still
 * what some screens use to say "loading"; the caller hides one and shows the other. Built once
 * and repainted on every call, so a theme change between visits lands on it too.
 *
 * Added last in its parent, so it draws -- and takes touches -- above an empty RecyclerView,
 * which would otherwise swallow the tap meant for the button.
 */
fun SimpleActivity.emptyStateFor(
    placeholder: TextView,
    @DrawableRes icon: Int,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
): View {
    val parent = placeholder.parent as ViewGroup
    val state = (placeholder.getTag(R.id.texto_tag_empty_state) as? LinearLayout)
        ?: LinearLayout(this).also { built ->
            built.orientation = LinearLayout.VERTICAL
            built.gravity = Gravity.CENTER_HORIZONTAL
            built.visibility = View.GONE
            parent.addView(built, copyParams(placeholder.layoutParams))
            placeholder.setTag(R.id.texto_tag_empty_state, built)
        }

    state.removeAllViews()
    state.setPadding(28.getScaledPx(), 56.getScaledPx(), 28.getScaledPx(), 24.getScaledPx())

    val accent = config.accentGradientStart
    val ink = config.mainTextColor
    val discSide = 88.getScaledPx()
    val disc = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accent.withAlpha(0.12f))
            setStroke(1.getScaledPx(), accent.withAlpha(0.28f))
        }
        addView(
            ImageView(this@emptyStateFor).apply {
                setImageResource(icon)
                imageTintList = android.content.res.ColorStateList.valueOf(accent)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(40.getScaledPx(), 40.getScaledPx(), Gravity.CENTER)
        )
    }
    state.addView(disc, LinearLayout.LayoutParams(discSide, discSide))

    state.addView(
        TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.15f))
            typeface = typefaceFor(Typeface.BOLD)
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 20.getScaledPx() }
    )

    // 72% rather than the list's 58%: this sits on the bare page, not on a card, and it is the
    // sentence that explains the screen, so it has to clear 4.5:1 on every skin's ground.
    state.addView(
        TextView(this).apply {
            text = body
            gravity = Gravity.CENTER
            maxWidth = 320.getScaledPx()
            setLineSpacing(0f, 1.25f)
            setTextColor(ink.withAlpha(0.72f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.9f))
            typeface = typefaceFor(Typeface.NORMAL)
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8.getScaledPx() }
    )

    if (actionLabel != null && onAction != null) {
        val button = pickerButton(actionLabel, filled = false) { onAction() }.apply {
            val padH = 24.getScaledPx()
            setPadding(padH, paddingTop, padH, paddingBottom)
            minHeight = MIN_TOUCH_TARGET_DP.getScaledPx()
        }
        state.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24.getScaledPx() }
        )
    }

    return state
}

/** The placeholder's own placement, in whatever kind of parent it happens to live in. */
private fun copyParams(source: ViewGroup.LayoutParams): ViewGroup.LayoutParams = when (source) {
    is RelativeLayout.LayoutParams -> RelativeLayout.LayoutParams(source)
    is LinearLayout.LayoutParams -> LinearLayout.LayoutParams(source)
    is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(source)
    is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(source)
    else -> ViewGroup.LayoutParams(source)
}
