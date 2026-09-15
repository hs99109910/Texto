package com.texto.sms.helpers

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.updateLayoutParams
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config

/**
 * The pulse that marks what can be recoloured while the appearance editor is open.
 *
 * A ring rather than a tint, and breathing rather than static: the thing being marked is a
 * colour, so anything that paints over it would be lying about what you are about to change,
 * and a still outline on a busy list reads as part of the design rather than as an invitation.
 *
 * One animator drives every target, so they pulse together instead of drifting apart, and the
 * targets are re-read on each frame -- the bubbles are recycled views, and the set of them on
 * screen changes as the list scrolls.
 */
class TextoEditPulse(private val activity: SimpleActivity) {

    private var animator: ValueAnimator? = null
    private var targets: () -> List<View> = { emptyList() }

    fun start(targets: () -> List<View>) {
        stop()
        this.targets = targets
        animator = ValueAnimator.ofFloat(0.25f, 1f).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { frame ->
                val alpha = ((frame.animatedValue as Float) * 255).toInt()
                this@TextoEditPulse.targets().forEach { view ->
                    val ring = view.foreground ?: ringFor(view).also { view.foreground = it }
                    ring.alpha = alpha
                }
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
        targets().forEach { if (it.foreground is EditRing) it.foreground = null }
        targets = { emptyList() }
    }

    /**
     * Two rings, light inside dark, for the reason the colour strips' marker is drawn that
     * way: a single colour disappears against some of what it has to mark, and here what it
     * marks is every colour the user has chosen. Measured: an accent ring around an accent
     * bubble was invisible.
     */
    private fun ringFor(view: View): Drawable {
        val radius = 20f * activity.resources.displayMetrics.density
        val stroke = with(activity) { 2.getScaledPx() }
        fun ring(colour: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.TRANSPARENT)
            setStroke(stroke, colour)
        }
        return EditRing(arrayOf(ring(Color.BLACK), ring(Color.WHITE))).apply {
            setLayerInset(1, stroke, stroke, stroke, stroke)
        }
    }

    /** Marks a ring as ours, so stopping removes only what this put there. */
    private class EditRing(layers: Array<Drawable>) : android.graphics.drawable.LayerDrawable(layers)
}

/**
 * The two strips that recolour one thing, shown against the thing itself.
 *
 * A family and its lightness ladder, which is the choice actually being made when somebody
 * tints a bubble -- the same pair the colour picker's sheet is built from, lifted out of the
 * sheet so that the surface being recoloured is not covered by the control recolouring it.
 *
 * [read] and [write] are the whole contract: read what the element wears now, write what was
 * picked. Every pick writes, so the screen previews itself, and the caller decides what that
 * means -- a working copy in the thread's editor, config on the conversation list.
 */
fun SimpleActivity.inlineColourBars(
    label: String,
    read: () -> Int,
    write: (Int) -> Unit,
    onReset: (() -> Unit)? = null,
): LinearLayout {
    val density = resources.displayMetrics.density

    val group = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padH = 12.getScaledPx()
        val padV = 10.getScaledPx()
        setPadding(padH, padV, padH, padV)
        background = TextoGlass.panel(
            tint = config.recentColor,
            cornerRadius = 22f * density,
            opacity = (config.glassOpacity / 100f).coerceIn(0.82f, 0.96f),
            strokeWidthPx = 1.getScaledPx()
        )
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        elevation = 12 * density
    }

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    header.addView(
        TextView(this).apply {
            text = label
            setTextColor(config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.76f))
            typeface = typefaceFor(Typeface.BOLD)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    )
    // Named rather than drawn: a bare arrow in the corner is a control you have to already
    // know about, and this is the one an unsure user needs to *find* -- it is what makes
    // trying a colour safe. It reads "Reset to default" beside the glyph, in a pill of its
    // own so it looks like something you press.
    onReset?.let { reset ->
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = 9.getScaledPx()
            val padV = 4.getScaledPx()
            setPadding(padH, padV, padH, padV)
            background = TextoGlass.bar(
                tint = config.mainTextColor,
                cornerRadius = 999f,
                opacity = 0.10f,
                strokeWidthPx = 1.getScaledPx(),
                rimAlpha = 0.22f,
            )
            isClickable = true
            setOnClickListener { reset() }
        }
        chip.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_ph_arrow_u_up_left)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    config.mainTextColor.withAlpha(0.85f)
                )
                layoutParams = LinearLayout.LayoutParams(16.getScaledPx(), 16.getScaledPx())
            }
        )
        chip.addView(
            TextView(this).apply {
                text = getString(R.string.appearance_reset_element)
                setTextColor(config.mainTextColor.withAlpha(0.85f))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.68f))
                typeface = typefaceFor(Typeface.NORMAL)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = 5.getScaledPx() }
            }
        )
        header.addView(chip)
    }
    group.addView(header)

    // One bar: hue along it, lightness down it, greys at its start edge.
    val spectrum = TextoSpectrumBar(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 64.getScaledPx()
        ).apply { topMargin = 10.getScaledPx() }
        setColourSilently(read())
        onPicked = { picked -> write(picked) }
    }
    group.addView(spectrum)
    return group
}

/**
 * Shows [above] and [below] -- the colour and, where there is one, its ink -- in a sheet
 * docked to the bottom of [overlay], and keeps [anchor] in view above it.
 *
 * Both editors share it, so a bar, a card and a bubble are restyled the same way. The strips
 * used to float against the element itself, one pair above it and one below, which put a
 * panel directly over the message text next to the bubble being recoloured. In a sheet the
 * controls sit in one predictable place and the thread stays readable above them: the list the
 * anchor lives in is padded by the sheet's height and scrolled so the anchor clears it, and
 * that padding is handed back when the sheet goes.
 *
 * [ceiling] is kept for the callers' sake; a docked sheet has no top edge to respect.
 */
@Suppress("UNUSED_PARAMETER")
fun SimpleActivity.showInlineBarsAround(
    overlay: FrameLayout,
    anchor: View,
    above: View,
    below: View?,
    ceiling: () -> Int,
) {
    overlay.hideInlineBars()
    val density = resources.displayMetrics.density
    val navInset = androidx.core.view.ViewCompat.getRootWindowInsets(overlay)
        ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0

    val radius = 28f * density
    val sheet = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.getScaledPx(), 10.getScaledPx(), 16.getScaledPx(), 16.getScaledPx() + navInset)
        background = TextoGlass.panel(
            tint = config.recentColor,
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
            opacity = 0.97f,
            strokeWidthPx = 1.getScaledPx(),
        )
        elevation = 16 * density
        // A tap on the sheet itself is not a tap outside it.
        isClickable = true
    }
    sheet.addView(
        View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = 100f * density
                setColor(config.mainTextColor.withAlpha(0.25f))
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },
        LinearLayout.LayoutParams(36.getScaledPx(), 4.getScaledPx()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = 12.getScaledPx()
        }
    )
    listOfNotNull(above, below).forEachIndexed { index, group ->
        // The sheet is the surface now, so each group loses its own floating card.
        group.background = null
        group.elevation = 0f
        group.clipToOutline = false
        group.setPadding(0, if (index == 0) 0 else 14.getScaledPx(), 0, 0)
        sheet.addView(
            group,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
    overlay.addView(
        sheet,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        )
    )
    overlay.visibility = View.VISIBLE
    overlay.setOnClickListener { overlay.hideInlineBars() }

    sheet.doOnLayout {
        sheet.translationY = sheet.height.toFloat()
        sheet.animate()
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        val list = anchor.ancestorRecyclerView() ?: return@doOnLayout
        val originalPadding = list.paddingBottom
        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, originalPadding + sheet.height)
        overlay.setTag(R.id.texto_tag_sheet_restore, Runnable {
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, originalPadding)
        })
        // After the padding has been laid out, not before: a list anchored to its end (the
        // thread) moves its content up by the padding it just gained, which on device carried
        // the tapped bubble straight up under the header. So the anchor is placed where it ends
        // up rather than where it was -- just above the sheet, moving the list either way.
        fun placeAnchor() {
            if (!anchor.isAttachedToWindow || overlay.visibility != View.VISIBLE) return
            val sheetTop = IntArray(2).also { sheet.getLocationOnScreen(it) }[1]
            val anchorBottom = IntArray(2).also { anchor.getLocationOnScreen(it) }[1] + anchor.height
            val offset = anchorBottom - (sheetTop - 16.getScaledPx())
            if (kotlin.math.abs(offset) > 4.getScaledPx()) list.smoothScrollBy(0, offset)
        }
        // Twice: once the padding is laid out, and again once that first scroll and the sheet's
        // own slide-in have settled. Measured on device, a single pass left the last bubble of
        // a thread half behind the sheet, because the list was still moving when it measured.
        list.doOnNextLayout {
            list.post { placeAnchor() }
            list.postDelayed({ placeAnchor() }, 400)
        }
    }
}

private fun View.ancestorRecyclerView(): androidx.recyclerview.widget.RecyclerView? {
    var parent = parent
    while (parent is View) {
        if (parent is androidx.recyclerview.widget.RecyclerView) return parent
        parent = parent.parent
    }
    return null
}

fun FrameLayout.hideInlineBars() {
    (getTag(R.id.texto_tag_sheet_restore) as? Runnable)?.run()
    setTag(R.id.texto_tag_sheet_restore, null)
    removeAllViews()
    visibility = View.GONE
}

/**
 * Brings [view] in the way a sheet arrives in this app: up from the edge it is anchored to,
 * with a little scale, rather than appearing fully formed.
 */
fun View.animateInlineIn(fromBelow: Boolean) {
    alpha = 0f
    scaleX = 0.94f
    scaleY = 0.94f
    translationY = (if (fromBelow) 12f else -12f) * resources.displayMetrics.density
    animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .translationY(0f)
        .setDuration(180)
        .setInterpolator(android.view.animation.DecelerateInterpolator())
        .start()
}

