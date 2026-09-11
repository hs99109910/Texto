package com.texto.sms.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.texto.sms.extensions.notificationManager
import org.fossify.commons.helpers.SimpleContactsHelper
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.R
import com.texto.sms.activities.ThreadActivity
import com.texto.sms.extensions.config
import com.texto.sms.extensions.shortcutHelper
import com.texto.sms.messaging.isShortCodeWithLetters
import com.texto.sms.receivers.CopyCodeReceiver
import com.texto.sms.receivers.DeleteSmsReceiver
import com.texto.sms.receivers.DirectReplyReceiver

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager
    private val soundUri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    private val user = Person.Builder()
        .setName(context.getString(R.string.me))
        .build()

    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        sender: String?,
        alertOnlyOnce: Boolean = false
    ) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        // A filter can carry a sound of its own, for every sender it covers. The per-thread
        // channel wins over it: that one was set on this exact conversation, which is more
        // specific than a rule about a group of senders.
        val filterSound = if (hasCustomNotifications) {
            null
        } else {
            FilterStore.customisedFilterFor(context.config.customFilters, address) {
                it.notificationSoundUri != null
            }
        }
        val notificationChannelId = when {
            hasCustomNotifications -> threadId.toString()
            filterSound != null -> filterChannelId(filterSound)
            else -> NOTIFICATION_CHANNEL_ID
        }
        if (!hasCustomNotifications) {
            createChannel(
                id = notificationChannelId,
                name = filterSound?.label ?: context.getString(R.string.channel_received_sms),
                sound = filterSound?.notificationSoundUri?.let { Uri.parse(it) } ?: soundUri
            )
        }

        val notificationId = threadId.hashCode()
        val contentIntent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(IS_FROM_NOTIFICATION, true)
        }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val deleteSmsIntent = Intent(context, DeleteSmsReceiver::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(MESSAGE_ID, messageId)
        }
        val deleteSmsPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                deleteSmsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        var copyCodeAction: NotificationCompat.Action? = null
        val otpCode = if (MessageClassifier.isOtpMessage(body)) MessageClassifier.extractCode(body) else null
        if (otpCode != null) {
            val copyCodeIntent = Intent(context, CopyCodeReceiver::class.java).apply {
                action = COPY_CODE
                putExtra(MESSAGE_CODE, otpCode)
            }
            val copyCodePendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    copyCodeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            copyCodeAction = NotificationCompat.Action.Builder(
                R.drawable.ic_copy_vector,
                context.getString(R.string.copy_code),
                copyCodePendingIntent
            ).build()
        }

        var replyAction: NotificationCompat.Action? = null
        val isNoReplySms = isShortCodeWithLetters(address)
        if (!isNoReplySms) {
            val replyLabel = context.getString(R.string.reply)
            val remoteInput = RemoteInput.Builder(REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(THREAD_ID, threadId)
                putExtra(THREAD_NUMBER, address)
            }
            // The one that has to stay mutable: RemoteInput fills the typed reply into this
            // intent as it is delivered. Every other intent here carries only extras this code
            // has already set, so they are immutable and cannot be rewritten by whoever ends
            // up holding them.

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    context.applicationContext,
                    notificationId,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_send_vector,
                replyLabel,
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()
        }

        val largeIcon = bitmap ?: if (sender != null) {
            SimpleContactsHelper(context).getContactLetterIcon(sender)
        } else {
            null
        }
        val builder = NotificationCompat.Builder(context, notificationChannelId).apply {
            when (context.config.lockScreenVisibilitySetting) {
                LOCK_SCREEN_SENDER_MESSAGE -> {
                    setLargeIcon(largeIcon)
                    setStyle(getMessagesStyle(address, body, notificationId, sender))
                }

                LOCK_SCREEN_SENDER -> {
                    setContentTitle(sender)
                    setLargeIcon(largeIcon)
                    val summaryText = context.getString(R.string.new_message)
                    setStyle(
                        NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body)
                    )
                }
            }

            color = context.config.accentGradientStart
            setSmallIcon(R.drawable.ic_notification_bubble)
            setContentIntent(contentPendingIntent)
            priority = NotificationCompat.PRIORITY_MAX
            setDefaults(Notification.DEFAULT_LIGHTS)
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setOnlyAlertOnce(alertOnlyOnce)
            // The channel carries the sound from Oreo on; this is what plays below it, so the
            // filter's choice has to be repeated here or it would only apply on newer phones.
            setSound(
                filterSound?.notificationSoundUri?.let { Uri.parse(it) }
                    ?: if (filterSound != null) null else soundUri,
                AudioManager.STREAM_NOTIFICATION
            )
            // Paired watches (Galaxy Watch, Wear OS) only mirror notifications that are
            // not marked phone-only.
            setLocalOnly(false)
        }

        if (replyAction != null && context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
            builder.addAction(replyAction)
        }

        if (copyCodeAction != null) {
            builder.addAction(copyCodeAction)
        }

        builder.addAction(
            org.fossify.commons.R.drawable.ic_delete_vector,
            context.getString(org.fossify.commons.R.string.delete),
            deleteSmsPendingIntent
        ).setChannelId(notificationChannelId)

        var shortcut = context.shortcutHelper.getShortcut(threadId)
        if (shortcut == null) {
            shortcut = context.shortcutHelper.createOrUpdateShortcut(threadId)
        }
        
        if (shortcut != null) {
            builder.setShortcutInfo(shortcut)
        }
        
        notificationManager.notify(notificationId, builder.build())
        ensureBackgroundThread {
            context.shortcutHelper.reportReceiveMessageUsage(threadId)
        }
    }

    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val notificationChannelId =
            if (hasCustomNotifications) threadId.toString() else NOTIFICATION_CHANNEL_ID
        if (!hasCustomNotifications) {
            createChannel(notificationChannelId, context.getString(R.string.message_not_sent_short))
        }

        val notificationId = generateRandomId().hashCode()
        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryText =
            String.format(context.getString(R.string.message_sending_error), recipientName)
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.config.accentGradientStart)
            .setSmallIcon(R.drawable.ic_notification_bubble)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setLocalOnly(false)
            .setChannelId(notificationChannelId)

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * A channel per filter *and per sound it has carried*.
     *
     * A channel's sound is frozen the moment it is created -- createNotificationChannel on an
     * id that already exists updates the name and nothing else -- so a filter whose sound is
     * changed has to move to a new id or it keeps playing the old one for good. Hashing the
     * uri into the id is what makes changing the sound take effect; the abandoned channel is
     * left behind rather than deleted, because deleting one and recreating the same id later
     * restores the settings the user had on it, which is worse than an unused entry in the
     * system list.
     */
    private fun filterChannelId(filter: MessageFilter) =
        "filter_${filter.id}_${filter.notificationSoundUri?.hashCode() ?: 0}"

    private fun createChannel(id: String, name: String, sound: Uri? = soundUri) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        val importance = IMPORTANCE_HIGH
        NotificationChannel(id, name, importance).apply {
            setBypassDnd(false)
            enableLights(true)
            // A null uri is the picker's "Silent", which is a choice and has to survive as
            // one: setSound(null, ...) is how a channel is told to stay quiet.
            setSound(sound, audioAttributes)
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun getMessagesStyle(
        address: String,
        body: String,
        notificationId: Int,
        name: String?
    ): NotificationCompat.MessagingStyle {
        val sender = if (name != null) {
            Person.Builder()
                .setName(name)
                .setKey(address)
                .build()
        } else {
            null
        }

        return NotificationCompat.MessagingStyle(user).also { style ->
            getOldMessages(notificationId).forEach {
                style.addMessage(it)
            }
            val newMessage =
                NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
            style.addMessage(newMessage)
        }
    }

    private fun getOldMessages(notificationId: Int): List<NotificationCompat.MessagingStyle.Message> {
        val currentNotification =
            notificationManager.activeNotifications.find { it.id == notificationId }
        return if (currentNotification != null) {
            val activeStyle =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
                    currentNotification.notification
                )
            activeStyle?.messages.orEmpty()
        } else {
            emptyList()
        }
    }
}
