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
    var (familyIndex, shadeIndex) = TextoPalette.locate(read() or 0xFF000000.toInt())

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
    onReset?.let { reset ->
        header.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_ph_arrow_u_up_left)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    config.mainTextColor.withAlpha(0.7f)
                )
                contentDescription = getString(R.string.appearance_reset_element)
                val pad = 5.getScaledPx()
                setPadding(pad, pad, pad, pad)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(26.getScaledPx(), 26.getScaledPx())
                setOnClickListener { reset() }
            }
        )
    }
    group.addView(header)

    val familyStrip = TextoHueStrip(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 34.getScaledPx()
        ).apply { topMargin = 8.getScaledPx() }
    }
    val shadeStrip = TextoHueStrip(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 34.getScaledPx()
        ).apply { topMargin = 6.getScaledPx() }
    }

    fun renderShades() {
        shadeStrip.submit(TextoPalette.families[familyIndex].toList(), shadeIndex)
    }

    // The family band shows each family's middle rung, which is what it looks like before you
    // choose how light you want it.
    familyStrip.submit(
        List(TextoPalette.families.size) { TextoPalette.faceOf(it) },
        familyIndex
    )
    renderShades()

    familyStrip.onPicked = { index ->
        familyIndex = index
        renderShades()
        write(TextoPalette.families[familyIndex][shadeIndex])
    }
    shadeStrip.onPicked = { index ->
        shadeIndex = index
        write(TextoPalette.families[familyIndex][shadeIndex])
    }

    group.addView(familyStrip)
    group.addView(shadeStrip)
    return group
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

