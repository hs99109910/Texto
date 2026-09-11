package com.texto.sms.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

/**
 * Replaces commons' `MyAppBarLayout`. Flattens the Material lift/elevation animation, which
 * this app never wants -- every app bar here is its own painted capsule, not the platform's
 * shadow-on-scroll bar -- and pads itself for the system bars, which turns out to be load-
 * bearing everywhere this is used: nothing else in the app gives a toolbar top clearance for
 * the status bar, so dropping this silently would have put every screen's header behind it.
 *
 * `applyFontToToolbar()` is not reproduced. It ran once on attach, before the content is even
 * laid out; `SimpleActivity.updateAppFonts()` walks the same title view on every resume and is
 * what the app actually relies on, so the commons pass never outlived its own first frame.
 */
open class TextoAppBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppBarLayout(context, attrs) {

    var applyWindowInsets: Boolean = true

    private var cachedToolbar: MaterialToolbar? = null
    private var basePadding: IntArray? = null

    init {
        elevation = 0f
        ViewCompat.setElevation(this, 0f)
        stateListAnimator = null
        setLiftOnScroll(false)
        setLifted(false)
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            if (applyWindowInsets) {
                val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
                updatePaddingWithBase(view, bars.left, bars.top, bars.right, null)
            }
            insets
        }
    }

    fun getToolbar(): MaterialToolbar? {
        cachedToolbar?.let { return it }
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is MaterialToolbar) {
                cachedToolbar = child
                return child
            }
        }
        return null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        cachedToolbar = null
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        cachedToolbar = null
    }

    /**
     * Adds [left]/[top]/[right]/[bottom] to whatever padding the view was laid out with,
     * rather than overwriting it outright -- an inset callback that fires more than once (the
     * usual case) must not keep stacking on top of its own previous result. A null side is
     * left exactly as it currently is.
     */
    private fun updatePaddingWithBase(view: View, left: Int?, top: Int?, right: Int?, bottom: Int?) {
        val base = basePadding
            ?: intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
                .also { basePadding = it }
        view.setPadding(
            if (left != null) base[0] + left else view.paddingLeft,
            if (top != null) base[1] + top else view.paddingTop,
            if (right != null) base[2] + right else view.paddingRight,
            if (bottom != null) base[3] + bottom else view.paddingBottom,
        )
    }
}
