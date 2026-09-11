package com.texto.sms.dialogs

import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.helpers.textoInputDialog
import com.texto.sms.models.Conversation
import com.texto.sms.extensions.toast

/**
 * Renaming a conversation.
 *
 * Built on [textoInputDialog] rather than commons' `setupDialogStuff`, which drew its title
 * bar and its buttons from the base (light) theme: on any of the app's skins that put a white
 * strip above the field and a face nothing else on screen uses.
 */
class RenameConversationDialog(
    private val activity: SimpleActivity,
    private val conversation: Conversation,
    private val callback: (name: String) -> Unit,
) {
    init {
        activity.textoInputDialog(
            title = activity.getString(R.string.rename_conversation),
            hint = conversation.title,
            // Only a title the user set themselves is worth editing; the generated one is
            // just the participant's name, and prefilling it invites renaming to itself.
            initialText = if (conversation.usesCustomTitle) conversation.title else ""
        ) { newTitle ->
            if (newTitle.isBlank()) {
                activity.toast(org.fossify.commons.R.string.empty_name)
            } else {
                callback(newTitle)
            }
        }
    }
}
