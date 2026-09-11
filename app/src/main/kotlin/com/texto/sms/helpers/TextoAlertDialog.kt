package com.texto.sms.helpers

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config

/**
 * The builder and the show step the app's own dialogs are built on.
 *
 * These keep commons' names and argument shapes on purpose: eight dialogs here are written
 * against them, and every one already finishes by calling [applyTextoDialogSkin] to undo the
 * base theme commons had just applied. Taking the two functions over means the skin is the
 * only styling that ever runs, rather than the second of two.
 */
fun SimpleActivity.getAlertDialogBuilder(): AlertDialog.Builder = AlertDialog.Builder(this)

/**
 * Titles, shows, and hands the live dialog back.
 *
 * The title is built here rather than inflated: commons carried a layout for it whose only
 * job was to hold one TextView, and its id is what [applyTextoDialogSkin] reached for. The
 * view is the same shape, under an id of ours.
 */
fun SimpleActivity.setupDialogStuff(
    view: View,
    dialog: AlertDialog.Builder,
    titleId: Int = 0,
    titleText: String = "",
    cancelOnTouchOutside: Boolean = true,
    callback: ((AlertDialog) -> Unit)? = null,
) {
    val title = titleText.ifEmpty { if (titleId != 0) getString(titleId) else "" }
    if (title.isNotEmpty()) {
        dialog.setCustomTitle(
            TextView(this).apply {
                id = R.id.texto_dialog_title
                text = title
                gravity = Gravity.START
                setTextColor(config.mainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
                typeface = typefaceFor(Typeface.BOLD)
                val pad = 20.getScaledPx()
                setPadding(pad, pad, pad, 8.getScaledPx())
            }
        )
    }

    dialog.setView(view)
    val alertDialog = dialog.create()
    alertDialog.setCanceledOnTouchOutside(cancelOnTouchOutside)
    alertDialog.show()
    callback?.invoke(alertDialog)
}
