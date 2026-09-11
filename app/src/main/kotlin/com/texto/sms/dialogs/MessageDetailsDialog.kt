package com.texto.sms.dialogs

import android.annotation.SuppressLint
import android.telephony.SubscriptionInfo
import com.texto.sms.activities.SimpleActivity
import org.fossify.commons.dialogs.BasePropertiesDialog
import com.texto.sms.helpers.getAlertDialogBuilder
import org.fossify.commons.extensions.getTimeFormatWithSeconds
import com.texto.sms.helpers.setupDialogStuff
import com.texto.sms.R
import com.texto.sms.extensions.config
import com.texto.sms.helpers.applyTextoDialogSkin
import com.texto.sms.extensions.subscriptionManagerCompat
import com.texto.sms.models.Message
import org.joda.time.DateTime

class MessageDetailsDialog(val activity: SimpleActivity, val message: Message) : BasePropertiesDialog(activity) {
    init {
        @SuppressLint("MissingPermission")
        val availableSIMs = activity.subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty()

        addProperty(message.getSenderOrReceiverLabel(), message.getSenderOrReceiverPhoneNumbers())
        if (availableSIMs.count() > 1) {
            addProperty(R.string.message_details_sim, message.getSIM(availableSIMs))
        }
        addProperty(message.getSentOrReceivedAtLabel(), message.getSentOrReceivedAt())

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.action_confirm) { _, _ -> }
            .apply {
                activity.setupDialogStuff(
                    mDialogView.root, this, R.string.message_details
                ) { alertDialog ->
                    (activity as? SimpleActivity)?.applyTextoDialogSkin(alertDialog)
                }
            }
    }

    private fun Message.getSenderOrReceiverLabel(): Int {
        return if (isReceivedMessage()) {
            R.string.message_details_sender
        } else {
            R.string.message_details_receiver
        }
    }

    private fun Message.getSenderOrReceiverPhoneNumbers(): String {
        return if (isReceivedMessage()) {
            formatContactInfo(senderName, senderPhoneNumber)
        } else {
            participants.joinToString(", ") {
                formatContactInfo(it.name, it.phoneNumbers.first().value)
            }
        }
    }

    private fun formatContactInfo(name: String, phoneNumber: String): String {
        return if (name != phoneNumber) {
            "$name ($phoneNumber)"
        } else {
            phoneNumber
        }
    }

    private fun Message.getSIM(availableSIMs: List<SubscriptionInfo>): String {
        return availableSIMs.firstOrNull { it.subscriptionId == subscriptionId }?.displayName?.toString()
            ?: activity.getString(org.fossify.commons.R.string.unknown)
    }

    private fun Message.getSentOrReceivedAtLabel(): Int {
        return if (isReceivedMessage()) {
            R.string.message_details_received_at
        } else {
            R.string.message_details_sent_at
        }
    }

    private fun Message.getSentOrReceivedAt(): String {
        return DateTime(date * 1000L).toString("${activity.config.dateFormat} ${activity.getTimeFormatWithSeconds()}")
    }
}
