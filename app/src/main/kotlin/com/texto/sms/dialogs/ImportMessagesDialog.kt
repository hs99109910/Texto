package com.texto.sms.dialogs

import androidx.appcompat.app.AlertDialog
import com.texto.sms.helpers.getAlertDialogBuilder
import com.texto.sms.helpers.setupDialogStuff
import com.texto.sms.extensions.toast
import com.texto.sms.extensions.MEDIUM_ALPHA
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.DialogImportMessagesBinding
import com.texto.sms.extensions.config
import com.texto.sms.helpers.MessagesImporter
import com.texto.sms.helpers.applyTextoDialogSkin
import com.texto.sms.models.ImportResult
import com.texto.sms.models.MessagesBackup

class ImportMessagesDialog(
    private val activity: SimpleActivity,
    private val messages: List<MessagesBackup>,
) {

    private val config = activity.config

    init {
        var ignoreClicks = false
        val binding = DialogImportMessagesBinding.inflate(activity.layoutInflater).apply {
            importSmsCheckbox.isChecked = config.importSms
            importMmsCheckbox.isChecked = config.importMms
        }

        binding.importProgress.setIndicatorColor(activity.config.accentGradientStart)

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.action_confirm, null)
            .setNegativeButton(R.string.action_cancel, null)
            .apply {
                activity.setupDialogStuff(
                    view = binding.root,
                    dialog = this,
                    titleId = R.string.import_messages
                ) { alertDialog ->
                    activity.applyTextoDialogSkin(alertDialog)
                    val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    val negativeButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    positiveButton.setOnClickListener {
                        if (ignoreClicks) {
                            return@setOnClickListener
                        }

                        if (!binding.importSmsCheckbox.isChecked && !binding.importMmsCheckbox.isChecked) {
                            activity.toast(R.string.no_option_selected)
                            return@setOnClickListener
                        }

                        ignoreClicks = true
                        activity.toast(R.string.importing)
                        config.importSms = binding.importSmsCheckbox.isChecked
                        config.importMms = binding.importMmsCheckbox.isChecked

                        alertDialog.setCanceledOnTouchOutside(false)
                        binding.importProgress.show()
                        arrayOf(
                            binding.importMmsCheckbox,
                            binding.importSmsCheckbox,
                            positiveButton,
                            negativeButton
                        ).forEach {
                            it.isEnabled = false
                            it.alpha = MEDIUM_ALPHA
                        }

                        ensureBackgroundThread {
                            MessagesImporter(activity).restoreMessages(messages) {
                                handleParseResult(it)
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }

    private fun handleParseResult(result: ImportResult) {
        activity.toast(
            when (result) {
                ImportResult.IMPORT_OK -> R.string.importing_successful
                ImportResult.IMPORT_PARTIAL -> R.string.importing_some_entries_failed
                ImportResult.IMPORT_FAIL -> R.string.importing_failed
                else -> R.string.no_items_found
            }
        )
    }
}
