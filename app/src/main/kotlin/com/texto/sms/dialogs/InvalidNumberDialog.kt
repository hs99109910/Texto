package com.texto.sms.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.DialogInvalidNumberBinding
import com.texto.sms.helpers.applyTextoDialogSkin

class InvalidNumberDialog(val activity: BaseSimpleActivity, val text: String) {
    init {
        val binding = DialogInvalidNumberBinding.inflate(activity.layoutInflater).apply {
            dialogInvalidNumberDesc.text = text
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.action_confirm) { _, _ -> }
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    (activity as? SimpleActivity)?.applyTextoDialogSkin(alertDialog)
                }
            }
    }
}
