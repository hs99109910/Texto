package com.texto.sms.dialogs

import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.asLtrPhone
import com.texto.sms.helpers.CapsuleChoice
import com.texto.sms.helpers.textoCapsuleDialog
import com.texto.sms.models.PhoneNumber

/**
 * Asks which of a contact's numbers to write to, and asks only when there is a choice.
 *
 * One number, or several that normalize to the same one, is not a question: a contact whose
 * mobile is stored twice in two spellings would otherwise open a sheet offering the same
 * person twice.
 */
fun SimpleActivity.maybeShowNumberPickerDialog(
    numbers: List<PhoneNumber>,
    callback: (PhoneNumber) -> Unit,
) {
    val distinct = numbers.distinctBy { it.normalizedNumber.ifEmpty { it.value } }
    if (distinct.size <= 1) {
        distinct.firstOrNull()?.let(callback)
        return
    }

    var dialog: android.app.Dialog? = null
    dialog = textoCapsuleDialog(
        title = getString(R.string.select_phone_number),
        choices = distinct.map { number ->
            CapsuleChoice(
                label = number.value.asLtrPhone(),
                subtitle = number.label.takeIf { it.isNotEmpty() },
            ) {
                dialog?.dismiss()
                callback(number)
            }
        },
    )
}
