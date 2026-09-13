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
        binding.blockedNumbersPlaceholder.beVisibleIf(numbers.isEmpty())
        binding.blockedNumbersPlaceholder.setTextColor(config.mainTextColor)

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

        row.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_ph_x)
                imageTintList = android.content.res.ColorStateList.valueOf(config.topBarTextColor)
                contentDescription = getString(R.string.unblock)
                setPadding(10.getScaledPx(), 10.getScaledPx(), 10.getScaledPx(), 10.getScaledPx())
                setOnClickListener { unblock(number) }
            },
            LinearLayout.LayoutParams(40.getScaledPx(), 40.getScaledPx())
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
