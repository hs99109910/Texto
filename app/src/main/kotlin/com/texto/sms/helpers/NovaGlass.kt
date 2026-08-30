package com.texto.sms.helpers

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
object NovaGlass {

    private const val BLUR_RADIUS = 28f

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
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(tint.withAlpha(opacity), tintEnd.withAlpha(opacity))
            )
        } else {
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    lighten(tint, 1.25f).withAlpha(opacity),
                    tint.withAlpha(opacity),
                    darken(tint, 0.85f).withAlpha(opacity)
                )
            )
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
            val defaultRimAlpha = if (isDarkTint) 0.22f else 0.5f
            setStroke(strokeWidthPx, Color.WHITE.withAlpha(rimAlpha ?: defaultRimAlpha))
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
    ) {
        view.background = panel(
            tint = tint,
            tintEnd = tintEnd,
            cornerRadius = cornerRadius,
            opacity = opacity,
            strokeWidthPx = strokeWidthPx,
            rimAlpha = rimAlpha,
            sheenAlpha = sheenAlpha,
            outlineColor = outlineColor,
            outlineWidthPx = outlineWidthPx
        )
    }

    /**
     * Solid accent gradient with no glass treatment, for the small emphasis surfaces the skin
     * paints with the accent pair: unread badges, the active filter chip, the FAB.
     */
    fun accent(start: Int, end: Int, cornerRadius: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
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
