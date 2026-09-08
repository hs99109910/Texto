package com.texto.sms.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.DialogSelectTextBinding
import com.texto.sms.helpers.applyTextoDialogSkin

// helper dialog for selecting just a part of a message, not copying the whole into clipboard
class SelectTextDialog(val activity: BaseSimpleActivity, val text: String) {
    init {
        val binding = DialogSelectTextBinding.inflate(activity.layoutInflater).apply {
            dialogSelectTextValue.text = text
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.action_confirm) { _, _ -> { } }
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    (activity as? SimpleActivity)?.applyTextoDialogSkin(alertDialog)
                }
            }
    }
}
