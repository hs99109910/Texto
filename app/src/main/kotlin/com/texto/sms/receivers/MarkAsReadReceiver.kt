package com.texto.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.texto.sms.extensions.notificationManager
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.extensions.conversationsDB
import com.texto.sms.extensions.markThreadMessagesRead
import com.texto.sms.helpers.MARK_AS_READ
import com.texto.sms.helpers.THREAD_ID
import com.texto.sms.helpers.refreshConversations

class MarkAsReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MARK_AS_READ -> {
                val threadId = intent.getLongExtra(THREAD_ID, 0L)
                context.notificationManager.cancel(threadId.hashCode())
                ensureBackgroundThread {
                    context.markThreadMessagesRead(threadId)
                    context.conversationsDB.markRead(threadId)
                    refreshConversations()
                }
            }
        }
    }
}
