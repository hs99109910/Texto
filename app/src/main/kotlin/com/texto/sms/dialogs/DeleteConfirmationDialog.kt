package com.texto.sms.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.texto.sms.extensions.beGoneIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.DialogDeleteConfirmationBinding
import com.texto.sms.helpers.applyTextoDialogSkin

class DeleteConfirmationDialog(
    private val activity: Activity,
    private val message: String,
    private val showSkipRecycleBinOption: Boolean,
    private val callback: (skipRecycleBin: Boolean) -> Unit
) {

    private var dialog: AlertDialog? = null
    val binding = DialogDeleteConfirmationBinding.inflate(activity.layoutInflater)

    init {
        binding.deleteRememberTitle.text = message
        binding.skipTheRecycleBinCheckbox.beGoneIf(!showSkipRecycleBinOption)
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.action_confirm) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.action_cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                    (activity as? SimpleActivity)?.applyTextoDialogSkin(alertDialog)
                }
            }
    }

    private fun dialogConfirmed() {
        dialog?.dismiss()
        callback(binding.skipTheRecycleBinCheckbox.isChecked)
    }
}
