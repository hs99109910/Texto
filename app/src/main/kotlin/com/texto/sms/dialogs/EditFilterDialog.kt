package com.texto.sms.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.DialogEditFilterBinding
import com.texto.sms.helpers.FilterStore
import com.texto.sms.helpers.MessageFilter
import com.texto.sms.helpers.SystemBlockedNumbers
import com.texto.sms.helpers.applyTextoDialogSkin
import com.texto.sms.helpers.senderChip
import com.texto.sms.helpers.textoSenderPicker

/**
 * Creates or edits a user-defined filter chip. Passing an [existing] filter switches the
 * dialog to edit mode and enables the delete button.
 */
class EditFilterDialog(
    private val activity: Activity,
    private val existing: MessageFilter? = null,
    /** Every conversation currently on the main screen, offered as pickable senders. */
    private val pickableSenders: List<Pair<String, String>> = emptyList(),
    /** The phone book, offered as the second source to build a filter from. */
    private val pickableContacts: List<Pair<String, String>> = emptyList(),
    private val onDelete: (() -> Unit)? = null,
    private val callback: (filter: MessageFilter) -> Unit,
) {
    private var dialog: AlertDialog? = null

    private val chosenNumbers = existing?.senders.orEmpty().toMutableList()
    private val chosenLabels = existing?.senderLabels.orEmpty().toMutableList()

    init {
        val binding = DialogEditFilterBinding.inflate(activity.layoutInflater).apply {
            existing?.let { filterNameEditText.setText(it.label) }
            // Both sources are offered as visible rows rather than behind an extra
            // "where from?" prompt, so the choice is obvious without a detour.
            filterPickFromChats.setOnClickListener {
                showSenderPicker(this, pickableSenders, R.string.no_conversations_to_pick, R.string.pick_from_chats)
            }
            filterPickFromContacts.setOnClickListener {
                showSenderPicker(this, pickableContacts, R.string.no_contacts_to_pick, R.string.pick_from_contacts)
            }
            // The dialog can render light or dark depending on the theme, so take the tint
            // from text that is already correct for it rather than hard-coding a colour.
            val iconTint = filterSourcesCaption.currentTextColor
            filterPickFromChatsIcon.setColorFilter(iconTint)
            filterPickFromContactsIcon.setColorFilter(iconTint)
            updateSenderSummary(this)
        }

        val titleId = if (existing == null) R.string.add_filter else R.string.edit_filter

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.action_confirm, null)
            .setNegativeButton(R.string.action_cancel, null)
            .apply {
                if (existing != null && onDelete != null) {
                    setNeutralButton(R.string.delete, null)
                }
            }
            .apply {
                activity.setupDialogStuff(binding.root, this, titleId) { alertDialog ->
                    dialog = alertDialog
                    (activity as? SimpleActivity)?.applyTextoDialogSkin(alertDialog)
                    alertDialog.showKeyboard(binding.filterNameEditText)

                    alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                        val label = binding.filterNameEditText.text.toString().trim()
                        if (label.isEmpty()) {
                            activity.toast(R.string.filter_name_required)
                            return@setOnClickListener
                        }

                        // A filter is defined purely by who it covers now that the keyword
                        // field is gone, so at least one sender has to be picked.
                        if (chosenNumbers.isEmpty()) {
                            activity.toast(R.string.filter_needs_senders)
                            return@setOnClickListener
                        }

                        val filter = existing?.copy(
                            label = label,
                            senders = chosenNumbers.toList(),
                            senderLabels = chosenLabels.toList()
                        ) ?: FilterStore.newCustomFilter(
                            label = label,
                            senders = chosenNumbers.toList(),
                            senderLabels = chosenLabels.toList()
                        )
                        callback(filter)
                        alertDialog.dismiss()
                    }

                    if (existing != null && onDelete != null) {
                        alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                            onDelete.invoke()
                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }

    private fun updateSenderSummary(binding: DialogEditFilterBinding) {
        val simpleActivity = activity as? SimpleActivity
        binding.filterSendersEmpty.beVisibleIf(chosenNumbers.isEmpty())
        binding.filterSendersChipsScroll.beVisibleIf(chosenNumbers.isNotEmpty())
        binding.filterSendersChips.removeAllViews()
        if (simpleActivity == null) return
        chosenNumbers.forEachIndexed { index, number ->
            binding.filterSendersChips.addView(
                simpleActivity.senderChip(chosenLabels[index]) {
                    chosenNumbers.removeAt(index)
                    chosenLabels.removeAt(index)
                    updateSenderSummary(binding)
                }
            )
        }
    }

    /** Search-filterable, multi-pick list of one source, pre-ticked with what is saved. */
    private fun showSenderPicker(
        binding: DialogEditFilterBinding,
        source: List<Pair<String, String>>,
        emptyMessage: Int,
        titleRes: Int,
    ) {
        if (source.isEmpty()) {
            activity.toast(emptyMessage)
            return
        }
        val simpleActivity = activity as? SimpleActivity ?: return

        simpleActivity.textoSenderPicker(
            title = activity.getString(titleRes),
            source = source,
            initiallySelected = chosenNumbers,
        ) { numbers, labels ->
            // Only this source's entries are rewritten; anything picked from the other
            // source stays, so a filter can mix chats and contacts.
            val sourceNumbers = source.map { it.second }
            sourceNumbers.forEach { number ->
                val at = chosenNumbers.indexOfFirst { SystemBlockedNumbers.isSameSender(it, number) }
                if (at >= 0) {
                    chosenNumbers.removeAt(at)
                    chosenLabels.removeAt(at)
                }
            }
            numbers.forEachIndexed { index, number ->
                chosenNumbers.add(number)
                chosenLabels.add(labels[index])
            }
            updateSenderSummary(binding)
        }
    }
}
