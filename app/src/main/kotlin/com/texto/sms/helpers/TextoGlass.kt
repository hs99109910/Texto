package com.texto.sms.helpers

import com.texto.sms.R

import android.app.Activity
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi

/**
 * iOS-style frosted panels: a translucent tinted fill, a bright hairline rim and a
 * top-down sheen. On Android 12+ the content behind a popup is blurred for real,
 * everywhere else the scrim alone carries the effect.
 */
object TextoGlass {

    private const val BLUR_RADIUS = 28f

    /**
     * How far in from the screen edge every full-width floating bar sits: the header capsule
     * and the nav pill. In dp, applied against `density`.
     *
     * It is here because the two had drifted apart. The header was inset 12dp against the
     * pill's 16dp, so on a 1080px screen the header spanned 31..1049 and the pill 42..1038 --
     * four dp further in on each side, and a comment in `setupOverlayBars` claimed they
     * matched.
     */
    const val FLOATING_BAR_INSET_DP = 16

    /**
     * The two fixed-height floating bars are **true capsules**: their corner radius is half
     * their own height, computed where they are painted rather than taken from here.
     *
     * A fixed radius cannot keep them looking alike, because they are not the same height.
     * At a shared 26dp the 58dp header was 89% of the way to a capsule while the 76dp nav
     * pill was only 68%, and the two read as different shapes -- measured corner profiles
     * that matched row for row, on bars that plainly did not match. Half the height is the
     * one rule that holds at any height.
     *
     * The composer is the exception and keeps this fixed 26dp -- the design's own `1.6rem`.
     * It grows with the text it holds, and a capsule that tall reads as a lozenge.
     */
    const val COMPOSER_RADIUS_DP = 26

    val supportsRealBlur: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * @param tint base color the panel is tinted with
     * @param tintEnd when set, the fill becomes a diagonal [tint]-to-[tintEnd] gradient
     *   instead of the default vertical lighten/darken shade of [tint] alone. This is how
     *   the skin's accent surfaces (sent bubbles, the FAB, the active chip) are painted.
     * @param cornerRadius uniform radius in px, ignored when [cornerRadii] is given
     * @param cornerRadii per-corner radii (8 values, as GradientDrawable expects)
     * @param opacity 0f..1f of the tint fill; the lower the glassier
     * @param rimAlpha overrides the hairline rim's own alpha. The default rim is sized to
     *   read against a solid-ish fill; a panel that is nearly transparent needs a far
     *   fainter one or the rim becomes the only thing you see.
     * @param sheenAlpha overrides the top-down sheen's alpha, for the same reason
     * @param outlineColor optional user-configured outline drawn on top of the hairline rim
     */
    fun panel(
        tint: Int,
        tintEnd: Int? = null,
        /** Middle stop of the [tint]-to-[tintEnd] gradient; null for a plain two-stop blend. */
        tintMid: Int? = null,
        cornerRadius: Float = 0f,
        cornerRadii: FloatArray? = null,
        opacity: Float = 0.55f,
        strokeWidthPx: Int = 1,
        rimAlpha: Float? = null,
        sheenAlpha: Float = 0.14f,
        outlineColor: Int? = null,
        outlineWidthPx: Int = 0,
    ): Drawable {
        val isDarkTint = isDark(tint)

        fun GradientDrawable.applyCorners() {
            if (cornerRadii != null) {
                this.cornerRadii = cornerRadii
            } else {
                this.cornerRadius = cornerRadius
            }
        }

        val fill = if (tintEnd != null) {
            // Same three-stop rule as accent(): the design's `--grad` passes through a colour
            // the straight blend between its ends never reaches.
            val stops = if (tintMid == null) {
                intArrayOf(tint.withAlpha(opacity), tintEnd.withAlpha(opacity))
            } else {
                intArrayOf(
                    tint.withAlpha(opacity),
                    tintMid.withAlpha(opacity),
                    tintEnd.withAlpha(opacity)
                )
            }
            GradientDrawable(GradientDrawable.Orientation.TL_BR, stops)
        } else {
            // A flat fill, as the design has it: every card, sheet and received bubble is a
            // plain `var(--glass)`. This used to be a light-to-dark vertical wash (1.25x at
            // the top down to 0.85x at the bottom), which is what made the cards and bubbles
            // read as embossed against a design that draws them flush.
            GradientDrawable().apply { setColor(tint.withAlpha(opacity)) }
        }.apply {
            shape = GradientDrawable.RECTANGLE
            applyCorners()
        }

        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.WHITE.withAlpha(sheenAlpha), Color.TRANSPARENT)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            applyCorners()
        }

        val rim = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            applyCorners()
            setColor(Color.TRANSPARENT)
            setStroke(strokeWidthPx, rimFor(tint, rimAlpha ?: 0.22f))
        }

        val layers = mutableListOf<android.graphics.drawable.Drawable>(fill, sheen, rim)

        if (outlineColor != null && outlineWidthPx > 0) {
            layers.add(
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    applyCorners()
                    setColor(Color.TRANSPARENT)
                    setStroke(outlineWidthPx, outlineColor)
                }
            )
        }

        return LayerDrawable(layers.toTypedArray())
    }

    /**
     * The design's bar recipe, shared by the header and the floating nav pill so the two
     * read as the same material: a vertical wash of [tint] fading from full strength to
     * about four fifths of it, under a hairline rim.
     *
     * [opacity] is the user's glass setting rather than the mockup's fixed 0.84, and the
     * lower stop is derived from it, so turning the glass up or down keeps the fade instead
     * of flattening it.
     */
    fun bar(
        tint: Int,
        cornerRadius: Float = 0f,
        cornerRadii: FloatArray? = null,
        opacity: Float = 0.84f,
        strokeWidthPx: Int = 1,
        rimAlpha: Float = 0.20f,
        rimColor: Int? = null,
    ): Drawable {
        fun GradientDrawable.applyCorners() {
            if (cornerRadii != null) this.cornerRadii = cornerRadii else this.cornerRadius = cornerRadius
        }

        // The fade closes as the glass is turned up, rather than staying a fixed ratio.

        // Held at .79 the bar's lower edge could never exceed 79% opaque, so the settings slider

        // ran out of travel with the bar still visibly see-through: the "least transparent"

        // setting was not opaque, and there was no way to ask for more.

        val fade = FADE_RATIO + (1f - FADE_RATIO) * opacity * opacity

        val fill = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(tint.withAlpha(opacity), tint.withAlpha(opacity * fade))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            applyCorners()
        }

        val rim = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            applyCorners()
            setColor(Color.TRANSPARENT)
            setStroke(strokeWidthPx, rimColor ?: rimFor(tint, rimAlpha))
        }

        return LayerDrawable(arrayOf(fill, rim))
    }

    /** The mockup's bar gradient runs .84 -> .66; keeping the ratio is what carries over. */
    private const val FADE_RATIO = 0.79f

    /** The design's own light-theme rim, `--rim: 20,23,43`. */
    private val LIGHT_RIM = Color.rgb(20, 23, 43)

    /**
     * Alpha the light rim takes relative to the dark one. The design pairs a white rim at
     * .20 on its dark theme with a near-black at .07 on its light one; a dark hairline reads
     * far louder than a white one, so it is drawn much fainter to weigh the same.
     */
    private const val LIGHT_RIM_RATIO = 0.35f

    /**
     * The hairline for a surface tinted [tint]. White on dark grounds, as the design has it.
     * On light grounds white is invisible against the fill, so the rim flips to the design's
     * near-black and drops to a matching weight -- otherwise every glass panel in a light
     * theme loses its edge and the cards bleed into the background.
     */
    fun rimFor(tint: Int, alpha: Float): Int = if (isDark(tint)) {
        Color.WHITE.withAlpha(alpha)
    } else {
        LIGHT_RIM.withAlpha(alpha * LIGHT_RIM_RATIO)
    }

    /** Blurs everything behind a popup while it is open. No-op below Android 12. */
    fun setBlurBehind(activity: Activity, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val root = activity.window?.decorView?.findViewById<ViewGroup>(android.R.id.content) ?: return
        try {
            applyBlur(root, enabled)
        } catch (_: Exception) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyBlur(root: ViewGroup, enabled: Boolean) {
        root.setRenderEffect(
            if (enabled) {
                RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP)
            } else {
                null
            }
        )
    }

    fun applyPanel(
        view: View,
        tint: Int,
        tintEnd: Int? = null,
        cornerRadius: Float,
        opacity: Float = 0.55f,
        strokeWidthPx: Int = 1,
        rimAlpha: Float? = null,
        sheenAlpha: Float = 0.14f,
        outlineColor: Int? = null,
        outlineWidthPx: Int = 0,
        /** Per-corner radii (TL, TR, BR, BL pairs); overrides [cornerRadius] when given. */
        cornerRadii: FloatArray? = null,
    ) {
        view.background = panel(
            tint = tint,
            tintEnd = tintEnd,
            cornerRadius = cornerRadius,
            cornerRadii = cornerRadii,
            opacity = opacity,
            strokeWidthPx = strokeWidthPx,
            rimAlpha = rimAlpha,
            sheenAlpha = sheenAlpha,
            outlineColor = outlineColor,
            outlineWidthPx = outlineWidthPx
        )
    }

    /**
     * Solid accent gradient with no glass treatment, for the emphasis surfaces the skin paints
     * with the accent: sent bubbles, avatars, unread badges, the active filter chip, the send
     * disc.
     *
     * [mid] is the design's third stop. Passing 0 (the stored "unset") falls back to a plain
     * two-stop blend, which is what a user-picked accent pair gets. It matters more than it
     * looks: the design's cyan-to-violet runs through a bright sky blue, and the straight
     * blend between its two ends instead passes through a dull grey-mauve, which is what made
     * the accent read as washed out even with both ends matching the mockup exactly.
     */
    fun accent(start: Int, end: Int, cornerRadius: Float, mid: Int = 0): GradientDrawable {
        val colors = if (mid == 0) intArrayOf(start, end) else intArrayOf(start, mid, end)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
        }
    }

    fun isDark(color: Int): Boolean {
        val luminance =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return luminance < 0.5
    }

    fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }

    private fun lighten(color: Int, factor: Float) = scale(color, factor)

    private fun darken(color: Int, factor: Float) = scale(color, factor)

    private fun scale(color: Int, factor: Float) = Color.argb(
        Color.alpha(color),
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )
}
