package com.texto.sms.helpers

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * One horizontal row of colours, where the one in the middle is the one chosen.
 *
 * A grid of equal dots gave every colour the same weight and left the selection to a thin
 * ring, which on the darker half of a hue sweep is nearly invisible; four rows of them also
 * ate a third of the settings screen. Here the row carries as many colours as it likes, the
 * centre one is *large* -- selection you read from across the room rather than hunt for --
 * and the rest fall away by size and weight the further out they sit.
 *
 * Deliberately not the overlapping carousel this idea came from: overlapped circles hide the
 * edge of every colour behind the next one, which is the one thing a colour picker must not
 * do. These keep a real gap, and the middle one simply grows.
 */
class TextoColorWheel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    /** Diameter of the centred swatch, in px. The rest are scaled down from it. */
    var itemSize: Int = 0
        set(value) {
            field = value
            adapter?.notifyDataSetChanged()
            requestLayout()
        }

    /** Fired once the row settles, with the index now in the middle. */
    var onPicked: ((Int) -> Unit)? = null

    private var colours: List<Int> = emptyList()
    private var selected = 0

    /** Suppresses [onPicked] while the row is being positioned rather than driven. */
    private var settling = false

    private val snap = LinearSnapHelper()
    private val manager = LinearLayoutManager(context, HORIZONTAL, false)

    init {
        layoutManager = manager
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        clipChildren = false
        itemAnimator = null
        snap.attachToRecyclerView(this)

        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = shapeChildren()

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != SCROLL_STATE_IDLE) return
                val centred = centredPosition() ?: return
                if (centred == selected) return
                selected = centred
                shapeChildren()
                if (!settling) onPicked?.invoke(centred)
            }
        })
    }

    fun submit(colours: List<Int>, selectedIndex: Int) {
        this.colours = colours
        selected = selectedIndex.coerceIn(0, (colours.size - 1).coerceAtLeast(0))
        if (adapter == null) adapter = SwatchAdapter() else adapter?.notifyDataSetChanged()
        // Centre the current choice without reporting it back as a new pick.
        settling = true
        post {
            centreOn(selected, animate = false)
            settling = false
        }
    }

    private fun centreOn(position: Int, animate: Boolean) {
        val offset = (width - itemSize) / 2
        if (animate) {
            smoothScrollToPosition(position)
        } else {
            manager.scrollToPositionWithOffset(position, offset)
            post { shapeChildren() }
        }
    }

    private fun centredPosition(): Int? {
        val view = snap.findSnapView(manager) ?: return null
        return manager.getPosition(view)
    }

    /**
     * Size and weight by distance from the middle. Recomputed on every scrolled frame rather
     * than bound once, because the value being shown is *where the row is*, not which row it is.
     */
    private fun shapeChildren() {
        val centre = width / 2f
        val reach = (width / 2f).coerceAtLeast(1f)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val distance = abs((child.left + child.right) / 2f - centre)
            val nearness = (1f - distance / reach).coerceIn(0f, 1f)
            val scale = MIN_SCALE + (1f - MIN_SCALE) * nearness
            child.scaleX = scale
            child.scaleY = scale
            child.alpha = MIN_ALPHA + (1f - MIN_ALPHA) * nearness
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Half a row of padding at each end, so the first and last colour can still reach
        // the middle. Without it neither end is selectable at all.
        val side = ((w - itemSize) / 2).coerceAtLeast(0)
        setPadding(side, paddingTop, side, paddingBottom)
        if (colours.isNotEmpty()) {
            settling = true
            post {
                centreOn(selected, animate = false)
                settling = false
            }
        }
    }

    private inner class SwatchAdapter : Adapter<SwatchAdapter.Holder>() {
        inner class Holder(val swatch: View) : ViewHolder(swatch)

        override fun getItemCount() = colours.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = View(parent.context).apply {
                layoutParams = LayoutParams(itemSize, itemSize).apply {
                    marginStart = GAP_RATIO
                    marginEnd = GAP_RATIO
                }
                isClickable = true
            }
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val colour = colours[position]
            holder.swatch.background = swatchDrawable(colour)
            holder.swatch.setOnClickListener {
                val target = holder.bindingAdapterPosition
                if (target == NO_POSITION) return@setOnClickListener
                if (target == selected) return@setOnClickListener
                selected = target
                centreOn(target, animate = true)
                onPicked?.invoke(target)
            }
        }
    }

    /**
     * Size alone marks the selection -- the centred swatch is nearly twice the diameter of
     * the ones at the edges, which no ring can compete with anyway.
     *
     * A ring was tried first and taken out: it lives in the item's *background*, so it only
     * moves when the row rebinds, while the scaling moves every frame. Keeping the two in
     * step through snapping, flinging and the rebuild that follows a pick meant the ring
     * regularly sat on a swatch that was no longer the chosen one. One signal, always right,
     * beats two that can disagree.
     */
    private fun swatchDrawable(colour: Int): android.graphics.drawable.Drawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colour)
            // So a swatch close to the card's own colour still has an edge.
            setStroke(HAIRLINE, TextoGlass.rimFor(colour, 0.55f))
        }

    private companion object {
        /** How small the furthest swatch gets. Small enough to read as "not chosen". */
        const val MIN_SCALE = 0.52f
        const val MIN_ALPHA = 0.45f
        const val GAP_RATIO = 10
        const val HAIRLINE = 2
    }
}
