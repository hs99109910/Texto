package com.texto.sms.receivers

import com.texto.sms.R

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.texto.sms.extensions.showErrorToast
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.extensions.conversationsDB
import com.texto.sms.extensions.deleteScheduledMessage
import com.texto.sms.extensions.getAddresses
import com.texto.sms.extensions.messagesDB
import com.texto.sms.helpers.SCHEDULED_MESSAGE_ID
import com.texto.sms.helpers.THREAD_ID
import com.texto.sms.helpers.refreshConversations
import com.texto.sms.helpers.refreshMessages
import com.texto.sms.messaging.sendMessageCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "simple.messenger:scheduled.message.receiver"
        )
        wakelock.acquire(1.minutes.inWholeMilliseconds)

        val pendingResult = goAsync()
        ensureBackgroundThread {
            try {
                handleIntent(context, intent)
            } finally {
                try {
                    if (wakelock.isHeld) wakelock.release()
                } catch (_: Exception) {
                }

                pendingResult.finish()
            }
        }
    }

    private fun handleIntent(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(SCHEDULED_MESSAGE_ID, 0L)
        val message = try {
            context.messagesDB.getScheduledMessageWithId(threadId, messageId)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        val addresses = message.participants.getAddresses()
        val attachments = message.attachment?.attachments ?: emptyList()

        // The send has to run on the main thread, but the queued rows must not be deleted
        // until it has actually happened. Previously the delete ran straight after posting,
        // so it raced the send, and a failure raised on the main thread could never reach a
        // catch out here -- a message that never went out was erased either way. Waiting for
        // the posted block turns that into: send first, delete only on success.
        val sendFinished = CountDownLatch(1)
        var sendFailure: Throwable? = null

        Handler(Looper.getMainLooper()).post {
            try {
                context.sendMessageCompat(message.body, addresses, message.subscriptionId, attachments)
            } catch (e: Exception) {
                sendFailure = e
            } catch (e: Error) {
                sendFailure = e
            } finally {
                sendFinished.countDown()
            }
        }

        // Bounded by the wakelock this receiver holds, so a send that never returns cannot
        // leave the thread parked here.
        if (!sendFinished.await(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return
        }

        sendFailure?.let { failure ->
            context.showErrorToast(
                failure.localizedMessage
                    ?: context.getString(R.string.unknown_error_occurred)
            )
            // Left in the database on purpose: the user can still see it and retry.
            return
        }

        try {
            // delete temporary conversation and message as it's already persisted to the telephony db now
            context.deleteScheduledMessage(messageId)
            context.conversationsDB.deleteThreadId(messageId)
            refreshMessages()
            refreshConversations()
        } catch (e: Exception) {
            context.showErrorToast(e)
        }
    }

    private companion object {
        const val SEND_TIMEOUT_SECONDS = 30L
    }
}
