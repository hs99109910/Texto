package com.texto.sms.receivers

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import com.klinker.android.send_message.MmsReceivedReceiver
import com.texto.sms.extensions.config
import com.texto.sms.helpers.isNumberBlockedBySystem
import com.texto.sms.extensions.showErrorToast
import com.texto.sms.helpers.ContactLookupResult
import com.texto.sms.helpers.SimpleContactsHelper
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.R
import com.texto.sms.extensions.getConversations
import com.texto.sms.extensions.getLatestMMS
import com.texto.sms.extensions.getNameFromAddress
import com.texto.sms.extensions.insertOrUpdateConversation
import com.texto.sms.extensions.shouldUnarchive
import com.texto.sms.extensions.showReceivedMessageNotification
import com.texto.sms.extensions.updateConversationArchivedStatus
import com.texto.sms.helpers.refreshConversations
import com.texto.sms.helpers.refreshMessages
import com.texto.sms.models.Message

class MmsReceiver : MmsReceivedReceiver() {

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        if (context.isNumberBlockedBySystem(address)) return true
        if (context.config.blockUnknownNumbers) {
            val result = SimpleContactsHelper(context).existsSync(address, null)
            return result == ContactLookupResult.NotFound
        }

        return false
    }

    override fun isContentBlocked(context: Context, content: String): Boolean = false

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.getSender()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: ""
        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        ensureBackgroundThread {
            handleMmsMessage(context, mms, size, address)
        }
    }

    override fun onError(context: Context, error: String) {
        context.showErrorToast(context.getString(R.string.couldnt_download_mms))
    }

    private fun handleMmsMessage(
        context: Context,
        mms: Message,
        size: Int,
        address: String
    ) {
        val glideBitmap = try {
            Glide.with(context)
                .asBitmap()
                .load(mms.attachment!!.attachments.first().getUri())
                .centerCrop()
                .into(size, size)
                .get()
        } catch (e: Exception) {
            null
        }


        val senderName = context.getNameFromAddress(address)

        context.showReceivedMessageNotification(
            messageId = mms.id,
            address = address,
            senderName = senderName,
            body = mms.body,
            threadId = mms.threadId,
            bitmap = glideBitmap
        )

        val conversation = context.getConversations(mms.threadId).firstOrNull() ?: return
        runCatching { context.insertOrUpdateConversation(conversation) }
        if (context.shouldUnarchive()) {
            context.updateConversationArchivedStatus(mms.threadId, false)
        }
        refreshMessages()
        refreshConversations()
    }
}
