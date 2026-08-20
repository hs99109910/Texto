package com.texto.sms.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import com.texto.sms.R
import com.texto.sms.databinding.DialogEditFilterBinding
import com.texto.sms.databinding.DialogPickSendersBinding
import com.texto.sms.helpers.FilterStore
import com.texto.sms.helpers.MessageFilter
import com.texto.sms.helpers.SystemBlockedNumbers

/**
 * Creates or edits a user-defined filter chip. Passing an [existing] filter switches the
 * dialog to edit mode and enables the delete button.
 */
class EditFilterDialog(
    private val activity: Activity,
    private val existing: MessageFilter? = null,
    /** Every conversation currently on the main screen, offered as pickable senders. */
    private val pickableSenders: List<Pair<String, String>> = emptyList(),
    private val onDelete: (() -> Unit)? = null,
    private val callback: (filter: MessageFilter) -> Unit,
) {
    private var dialog: AlertDialog? = null

    private val chosenNumbers = existing?.senders.orEmpty().toMutableList()
    private val chosenLabels = existing?.senderLabels.orEmpty().toMutableList()

    init {
        val binding = DialogEditFilterBinding.inflate(activity.layoutInflater).apply {
            existing?.let {
                filterNameEditText.setText(it.label)
                filterKeywordsEditText.setText(it.keywords.joinToString("، "))
            }
            filterPickSenders.setOnClickListener { showSenderPicker(this) }
            updateSenderSummary(this)
        }

        val titleId = if (existing == null) R.string.add_filter else R.string.edit_filter

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                if (existing != null && onDelete != null) {
                    setNeutralButton(org.fossify.commons.R.string.delete, null)
                }
            }
            .apply {
                activity.setupDialogStuff(binding.root, this, titleId) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(binding.filterNameEditText)

                    alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                        val label = binding.filterNameEditText.text.toString().trim()
                        if (label.isEmpty()) {
                            activity.toast(R.string.filter_name_required)
                            return@setOnClickListener
                        }

                        val keywords =
                            FilterStore.parseKeywords(binding.filterKeywordsEditText.text.toString())
                        // Either source on its own is enough to define a filter.
                        if (keywords.isEmpty() && chosenNumbers.isEmpty()) {
                            activity.toast(R.string.filter_needs_keywords_or_senders)
                            return@setOnClickListener
                        }

                        val filter = existing?.copy(
                            label = label,
                            keywords = keywords,
                            senders = chosenNumbers.toList(),
                            senderLabels = chosenLabels.toList()
                        ) ?: FilterStore.newCustomFilter(
                            label = label,
                            keywords = keywords,
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
        binding.filterSendersSummary.text = if (chosenNumbers.isEmpty()) {
            activity.getString(R.string.no_senders_picked)
        } else {
            chosenLabels.joinToString("، ")
        }
    }

    /** Multi-choice, search-filterable list of the current conversations, pre-ticked with what is saved. */
    private fun showSenderPicker(binding: DialogEditFilterBinding) {
        if (pickableSenders.isEmpty()) {
            activity.toast(R.string.no_conversations_to_pick)
            return
        }

        val labels = pickableSenders.map { it.first }
        val numbers = pickableSenders.map { it.second }

        // Selection is tracked by index into the full (unfiltered) arrays above, so it
        // survives the search box narrowing/widening the visible rows.
        val selectedIndices = HashSet<Int>()
        numbers.forEachIndexed { index, number ->
            val isChosen = chosenNumbers.any { SystemBlockedNumbers.isSameSender(it, number) }
            if (isChosen) selectedIndices.add(index)
        }

        val pickerBinding = DialogPickSendersBinding.inflate(activity.layoutInflater)
        var visibleIndices = labels.indices.toMutableList()

        val listAdapter = object : BaseAdapter() {
            override fun getCount() = visibleIndices.size
            override fun getItem(position: Int) = visibleIndices[position]
            override fun getItemId(position: Int) = visibleIndices[position].toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val itemIndex = visibleIndices[position]
                val view = convertView as? CheckedTextView ?: activity.layoutInflater.inflate(
                    android.R.layout.simple_list_item_multiple_choice, parent, false
                ) as CheckedTextView
                view.text = labels[itemIndex]
                view.isChecked = selectedIndices.contains(itemIndex)
                return view
            }
        }

        pickerBinding.pickSendersList.apply {
            adapter = listAdapter
            setOnItemClickListener { _, view, position, _ ->
                val itemIndex = visibleIndices[position]
                if (!selectedIndices.add(itemIndex)) selectedIndices.remove(itemIndex)
                (view as? CheckedTextView)?.isChecked = selectedIndices.contains(itemIndex)
            }
        }

        pickerBinding.pickSendersSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                visibleIndices = if (query.isEmpty()) {
                    labels.indices.toMutableList()
                } else {
                    labels.indices.filter { index ->
                        labels[index].contains(query, ignoreCase = true) ||
                            numbers[index].contains(query, ignoreCase = true)
                    }.toMutableList()
                }
                listAdapter.notifyDataSetChanged()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Built and shown directly rather than through setupDialogStuff, which swaps in
        // its own content view and takes the picker layout and its buttons with it.
        AlertDialog.Builder(activity)
            .setTitle(R.string.pick_senders)
            .setView(pickerBinding.root)
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                chosenNumbers.clear()
                chosenLabels.clear()
                selectedIndices.sorted().forEach { index ->
                    chosenNumbers.add(numbers[index])
                    chosenLabels.add(labels[index])
                }
                updateSenderSummary(binding)
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .show()
    }
}
