package com.texto.sms.activities

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.texto.sms.extensions.beVisibleIf
import com.texto.sms.extensions.toast
import com.texto.sms.helpers.NavigationIcon
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.extensions.viewBinding
import com.texto.sms.R
import com.texto.sms.databinding.ActivityBlockedNumbersBinding
import com.texto.sms.extensions.asLtrPhone
import com.texto.sms.extensions.config
import com.texto.sms.helpers.TextoGlass
import com.texto.sms.helpers.allBlockedNumbers
import com.texto.sms.helpers.unblockNumber
import com.texto.sms.helpers.emptyStateFor
import com.texto.sms.helpers.pickerButton
import com.texto.sms.helpers.textoConfirmDialog
import com.texto.sms.extensions.beGone

/**
 * The phone's own block list — the same one the stock messaging app writes to. Rows are
 * added straight to a column rather than through a RecyclerView: the list is short, and
 * this keeps the screen to one file plus one layout.
 */
class BlockedNumbersActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityBlockedNumbersBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupTextoTopAppBar(
            binding.blockedNumbersAppbar,
            NavigationIcon.Arrow,
            screenIcon = R.drawable.ic_ph_prohibit,
        )
        binding.blockedNumbersToolbar.setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        setupTextoTopAppBar(
            binding.blockedNumbersAppbar,
            NavigationIcon.Arrow,
            screenIcon = R.drawable.ic_ph_prohibit,
        )
        reload()
        applyCustomColors()
        updateAppFonts(binding.root)
    }

    private fun reload() {
        ensureBackgroundThread {
            val numbers = allBlockedNumbers()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                render(numbers)
            }
        }
    }

    private fun render(numbers: List<String>) {
        binding.blockedNumbersList.removeAllViews()
        binding.blockedNumbersPlaceholder.beGone()
        emptyStateFor(
            placeholder = binding.blockedNumbersPlaceholder,
            icon = R.drawable.ic_ph_prohibit,
            title = getString(R.string.empty_blocked_title),
            body = getString(R.string.empty_blocked_body),
        ).beVisibleIf(numbers.isEmpty())

        val density = resources.displayMetrics.density
        numbers.forEach { number ->
            binding.blockedNumbersList.addView(buildRow(number, density))
        }
    }

    private fun buildRow(number: String, density: Float): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.getScaledPx(), 12.getScaledPx(), 8.getScaledPx(), 12.getScaledPx())
            TextoGlass.applyPanel(
                view = this,
                tint = if (config.topBarColor != 0) config.topBarColor else Color.BLACK,
                cornerRadius = 18f * density,
                opacity = if (config.glassTheme) 0.6f else 0.95f,
                strokeWidthPx = 1.getScaledPx()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.getScaledPx() }
        }

        row.addView(
            TextView(this).apply {
                // The number, isolated so a leading "+" stays leading, and at the app's own
                // scaled size : a fixed 16sp here ignored the UI-scale setting while the
                // padding around it, two lines up, honoured it.
                text = number.asLtrPhone()
                setTextColor(config.topBarTextColor)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
                typeface = typefaceFor(android.graphics.Typeface.NORMAL)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        // A word, not a bare x. An x at the end of a row says "remove", and on this screen that
        // could as easily mean deleting the entry as letting the number's messages back in --
        // which is the one that actually happens. It asks first, because undoing it means
        // typing the number out again.
        row.addView(
            pickerButton(getString(R.string.unblock), filled = false) {
                textoConfirmDialog(
                    message = getString(R.string.unblock_confirmation, number.asLtrPhone()),
                    positiveLabel = getString(R.string.unblock),
                ) { unblock(number) }
            }.apply {
                val padH = 16.getScaledPx()
                setPadding(padH, paddingTop, padH, paddingBottom)
                minHeight = com.texto.sms.helpers.MIN_TOUCH_TARGET_DP.getScaledPx()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        return row
    }

    private fun unblock(number: String) {
        ensureBackgroundThread {
            val removed = unblockNumber(number)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!removed) toast(R.string.unknown_error_occurred)
                reload()
            }
        }
    }
}
