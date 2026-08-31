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
    /** Scales every halo's peak alpha; the design dims them to 40% on its light variant. */
    haloOpacity: Float = 1f,
    private val animate: Boolean,
) : Drawable() {

    private companion object {
        /** One full drift cycle. Slow enough to read as ambient rather than as motion. */
        const val CYCLE_MILLIS = 28_000L

        /** How far a halo wanders from its anchor, as a fraction of the screen. */
        const val DRIFT_FRACTION = 0.06f

        /**
         * Where each halo's colour is half gone and where it is fully gone, as fractions of
         * its radius. The design's stops are `colour 0%` to `transparent 60-65%`, so the tail
         * ends well inside the ellipse rather than trailing to its edge.
         */
        const val FADE_MID_STOP = 0.30f
        const val FADE_END_STOP = 0.62f
    }

    /**
     * One halo, taken from the design's own radial-gradient list: where it is anchored (as a
     * fraction of width/height), how wide and tall its ellipse is (likewise), its peak alpha,
     * and the phase offset that keeps the three from drifting in lockstep.
     */
    private data class Halo(
        val anchorX: Float,
        val anchorY: Float,
        val radiusFractionX: Float,
        val radiusFractionY: Float,
        val alpha: Float,
        val phase: Float,
        val color: Int,
    )

    /**
     * The design's three background radials, verbatim:
     * ```
     * radial-gradient(100% 70% at 80% -10%,  teal   / 0.5)
     * radial-gradient( 90% 60% at  5%  25%,  blue   / 0.55)
     * radial-gradient(120% 80% at 35% 110%,  violet / 0.5)
     * ```
     */
    private val halos: List<Halo> = listOf(
        HaloSpec(0.80f, -0.10f, 1.00f, 0.70f, 0.50f),
        HaloSpec(0.05f, 0.25f, 0.90f, 0.60f, 0.55f),
        HaloSpec(0.35f, 1.10f, 1.20f, 0.80f, 0.50f),
    ).mapIndexed { index, spec ->
        Halo(
            anchorX = spec.x,
            anchorY = spec.y,
            radiusFractionX = spec.rx,
            radiusFractionY = spec.ry,
            alpha = spec.alpha * haloOpacity,
            phase = index * (2f * Math.PI.toFloat() / 3f),
            // Fewer colours than halos is fine: they cycle.
            color = haloColors[index % haloColors.size],
        )
    }

    private data class HaloSpec(
        val x: Float,
        val y: Float,
        val rx: Float,
        val ry: Float,
        val alpha: Float,
    )

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
        val drift = maxOf(w, h) * DRIFT_FRACTION
        val angleBase = progress * 2f * Math.PI.toFloat()

        halos.forEach { halo ->
            val angle = angleBase + halo.phase
            val cx = bounds.left + halo.anchorX * w + cos(angle) * drift
            val cy = bounds.top + halo.anchorY * h + sin(angle) * drift * 0.7f

            // The design's halos are ellipses measured against the viewport -- e.g.
            // `radial-gradient(100% 70% at 80% -10%, ...)` -- not circles. Sizing them off the
            // larger edge instead made each one about the whole screen across, so all three
            // overlapped everywhere and washed a near-black ground in bright accent colour.
            val radiusX = (w * halo.radiusFractionX).coerceAtLeast(1f)
            val radiusY = (h * halo.radiusFractionY).coerceAtLeast(1f)

            // Android's RadialGradient is circular, so the ellipse is a circle of radiusX
            // squashed vertically by the shader's own matrix.
            val shader = RadialGradient(
                cx, cy, radiusX,
                intArrayOf(
                    withAlpha(halo.color, halo.alpha),
                    withAlpha(halo.color, halo.alpha * 0.45f),
                    Color.TRANSPARENT
                ),
                // Fully transparent well before the edge, as the design's `transparent 60%`
                // stops do. Running the fade all the way to 1.0 is what let the tails reach
                // across the whole screen.
                floatArrayOf(0f, FADE_MID_STOP, FADE_END_STOP),
                Shader.TileMode.CLAMP
            ).apply {
                setLocalMatrix(
                    android.graphics.Matrix().apply {
                        setScale(1f, radiusY / radiusX, cx, cy)
                    }
                )
            }

            paint.shader = shader
            canvas.drawRect(bounds, paint)
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
