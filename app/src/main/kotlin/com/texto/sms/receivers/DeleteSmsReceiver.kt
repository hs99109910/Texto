package com.texto.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.texto.sms.extensions.notificationManager
import org.fossify.commons.helpers.ensureBackgroundThread
import com.texto.sms.extensions.config
import com.texto.sms.extensions.deleteMessage
import com.texto.sms.extensions.enforceRecycleBinThreadLimit
import com.texto.sms.extensions.moveMessageToRecycleBin
import com.texto.sms.extensions.updateLastConversationMessage
import com.texto.sms.helpers.IS_MMS
import com.texto.sms.helpers.MESSAGE_ID
import com.texto.sms.helpers.THREAD_ID
import com.texto.sms.helpers.refreshConversations
import com.texto.sms.helpers.refreshMessages

class DeleteSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        context.notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            if (context.config.useRecycleBin) {
                context.moveMessageToRecycleBin(messageId)
                context.enforceRecycleBinThreadLimit()
            } else {
                context.deleteMessage(messageId, isMms)
            }
            context.updateLastConversationMessage(threadId)
            refreshMessages()
            refreshConversations()
        }
    }
}
