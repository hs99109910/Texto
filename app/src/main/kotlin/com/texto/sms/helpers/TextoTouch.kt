package com.texto.sms.helpers

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import com.texto.sms.R
import com.texto.sms.extensions.getScaledPx

/**
 * The platform's minimum touch target, in dp. Anything the user is meant to hit has to reach
 * it -- the design draws several controls at 40dp and the header they sit in is 58dp, so
 * neither the disc nor its row can be leaned on to get there.
 */
const val MIN_TOUCH_TARGET_DP = 48

/**
 * A [TouchDelegate] that carries more than one target.
 *
 * A View holds exactly one delegate, so a row with two 40dp discs in it could only ever grow
 * whichever was styled last: the gear reached 48dp and the palette beside it -- the same size,
 * the same need -- kept the 40 it was drawn at, because the second `touchDelegate =` threw the
 * first away. That is not a rounding error on a phone: 8dp is most of a fingertip's slop, and
 * the control it was costing sits in the corner of the screen where the thumb is least precise.
 *
 * Hit testing is done here rather than by nesting real [TouchDelegate]s, because a nested one
 * rewrites the coordinates of the event it is handed and probing several of them in turn with
 * the same event is not something that survives.
 *
 * A [ViewGroup] only consults its delegate once no child has claimed the event, so a target
 * whose grown rect overlaps a real neighbour cannot steal from it.
 */
class TextoTouchDelegate(private val host: ViewGroup) : TouchDelegate(Rect(), host) {

    private class Target(val rect: Rect, val view: View)

    private val targets = ArrayList<Target>()
    private var captured: Target? = null

    fun clear() {
        targets.clear()
        captured = null
    }

    fun add(rect: Rect, view: View) {
        targets.removeAll { it.view === view }
        targets.add(Target(rect, view))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val x = event.x.toInt()
            val y = event.y.toInt()
            captured = targets.firstOrNull {
                it.view.isEnabled && it.view.visibility == View.VISIBLE && it.rect.contains(x, y)
            }
        }
        val target = captured ?: return false
        // Landed in the centre of the real control, so a press ripple starts where the finger
        // looks like it is rather than off at the rect's own origin.
        val local = MotionEvent.obtain(event).apply {
            setLocation(target.view.width / 2f, target.view.height / 2f)
        }
        val handled = target.view.dispatchTouchEvent(local)
        local.recycle()
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            captured = null
        }
        return handled
    }
}

/**
 * Grows this view's hit rect out to [minDp] on its parent, keeping whatever else that parent
 * already delegates.
 *
 * Posted because [View.getHitRect] says nothing useful before the parent has been laid out,
 * and re-entrant by design: every screen calls this from `onResume`, so the same view arrives
 * here again and must replace its own entry rather than pile a second one on.
 */
fun View.expandTouchTarget(minDp: Int = MIN_TOUCH_TARGET_DP) {
    post {
        val row = parent as? ViewGroup ?: return@post
        val target = minDp.getScaledPx(context)
        val rect = Rect()
        getHitRect(rect)
        val growX = ((target - rect.width()) / 2).coerceAtLeast(0)
        val growY = ((target - rect.height()) / 2).coerceAtLeast(0)
        if (growX == 0 && growY == 0) return@post
        rect.inset(-growX, -growY)

        val existing = row.getTag(R.id.texto_tag_touch_delegate) as? TextoTouchDelegate
        val delegate = existing ?: TextoTouchDelegate(row).also {
            row.setTag(R.id.texto_tag_touch_delegate, it)
        }
        delegate.add(rect, this)
        row.touchDelegate = delegate
    }
}
