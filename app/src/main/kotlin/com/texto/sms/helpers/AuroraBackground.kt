package com.texto.sms.helpers

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * The skin's background: a flat ground colour with three large, heavily blurred colour halos
 * drifting slowly across it. This is the Android counterpart of the design's `aurora-bg`
 * plus its `float-slow` blobs.
 *
 * Drawn as radial gradients rather than blurred circles because a real blur of this radius
 * would cost a full-screen offscreen pass every frame; a radial gradient whose alpha falls to
 * zero at the edge is visually equivalent here and essentially free.
 *
 * The animation is a single 28-second [ValueAnimator] shared by all three halos, each offset
 * along the cycle. It only runs while the drawable is actually visible -- Android calls
 * [setVisible] on window/visibility changes -- so a backgrounded screen costs nothing.
 */
class AuroraBackgroundDrawable(
    private val groundColor: Int,
    haloColors: List<Int>,
    private val animate: Boolean,
) : Drawable() {

    private companion object {
        /** One full drift cycle. Slow enough to read as ambient rather than as motion. */
        const val CYCLE_MILLIS = 28_000L

        /** Halo radius as a fraction of the larger screen edge. */
        const val RADIUS_FRACTION = 0.85f

        /** How far a halo wanders from its anchor, as a fraction of the screen. */
        const val DRIFT_FRACTION = 0.06f
    }

    /**
     * Anchor position (as a fraction of width/height), peak alpha, and the phase offset that
     * keeps the three halos from moving in lockstep. Mirrors the design's three blob
     * placements: top-right, mid-left, bottom-centre.
     */
    private data class Halo(
        val anchorX: Float,
        val anchorY: Float,
        val alpha: Float,
        val phase: Float,
        val color: Int,
    )

    private val halos: List<Halo> = listOf(
        Triple(1.02f, -0.05f, 0.55f),
        Triple(-0.08f, 0.38f, 0.50f),
        Triple(0.62f, 1.04f, 0.42f),
    ).mapIndexed { index, (x, y, alpha) ->
        Halo(
            anchorX = x,
            anchorY = y,
            alpha = alpha,
            phase = index * (2f * Math.PI.toFloat() / 3f),
            // Fewer colours than halos is fine: they cycle.
            color = haloColors[index % haloColors.size],
        )
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f

    private val animator: ValueAnimator? = if (animate) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CYCLE_MILLIS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidateSelf()
            }
        }
    } else {
        null
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        canvas.drawColor(groundColor)

        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val radius = maxOf(w, h) * RADIUS_FRACTION
        val drift = maxOf(w, h) * DRIFT_FRACTION
        val angleBase = progress * 2f * Math.PI.toFloat()

        halos.forEach { halo ->
            val angle = angleBase + halo.phase
            val cx = bounds.left + halo.anchorX * w + cos(angle) * drift
            val cy = bounds.top + halo.anchorY * h + sin(angle) * drift * 0.7f

            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    withAlpha(halo.color, halo.alpha),
                    withAlpha(halo.color, halo.alpha * 0.45f),
                    Color.TRANSPARENT
                ),
                // Most of the falloff happens in the outer half, which is what gives the
                // halo its soft edge without an actual blur.
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, paint)
        }

        paint.shader = null
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        animator?.let {
            if (visible) {
                if (!it.isStarted) it.start()
            } else {
                it.cancel()
            }
        }
        return changed
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable, but still abstract on the minSdk we compile against.")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    /** Frees the animator when the drawable is swapped out, so nothing keeps ticking. */
    fun release() {
        animator?.cancel()
    }

    private fun withAlpha(color: Int, fraction: Float): Int {
        val a = (fraction * 255).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}
