package com.texto.sms.dialogs

import android.annotation.SuppressLint
import android.telephony.SubscriptionInfo
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config
import com.texto.sms.extensions.getTimeFormatWithSeconds
import com.texto.sms.extensions.showErrorToast
import com.texto.sms.extensions.toast
import com.texto.sms.helpers.applyTextoDialogSkin
import com.texto.sms.helpers.getAlertDialogBuilder
import com.texto.sms.helpers.setupDialogStuff
import com.texto.sms.extensions.copyToClipboard
import com.texto.sms.extensions.subscriptionManagerCompat
import com.texto.sms.extensions.withAlpha
import com.texto.sms.models.Message
import com.texto.sms.R
import org.joda.time.DateTime

/**
 * Replaces commons' `BasePropertiesDialog`: a scrolling stack of label/value rows, each row
 * copying its value to the clipboard on long-press -- the one behaviour that dialog gave for
 * free and this app's own message-details screen still wants.
 */
class MessageDetailsDialog(val activity: SimpleActivity, val message: Message) {
    init {
        @SuppressLint("MissingPermission")
        val availableSIMs = activity.subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty()

        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val side = px(16)
            setPadding(side, 0, side, 0)
        }
        val scroll = ScrollView(activity).apply { addView(holder) }

        addProperty(holder, message.getSenderOrReceiverLabel(), message.getSenderOrReceiverPhoneNumbers())
        if (availableSIMs.count() > 1) {
            addProperty(holder, R.string.message_details_sim, message.getSIM(availableSIMs))
        }
        addProperty(holder, message.getSentOrReceivedAtLabel(), message.getSentOrReceivedAt())

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.action_confirm) { _, _ -> }
            .apply {
                activity.setupDialogStuff(
                    scroll, this, R.string.message_details
                ) { alertDialog ->
                    activity.applyTextoDialogSkin(alertDialog)
                }
            }
    }

    private fun px(dp: Int): Int = with(activity) { dp.getScaledPx() }

    private fun addProperty(holder: LinearLayout, labelRes: Int, value: String) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isLongClickable = true
        }
        val label = TextView(activity).apply {
            text = activity.getString(labelRes)
            setTextColor(activity.config.mainTextColor.withAlpha(0.6f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getScaledTextSize(0.85f))
            gravity = Gravity.START
            setPadding(px(4), px(16), px(4), 0)
        }
        val valueView = TextView(activity).apply {
            text = value
            setTextColor(activity.config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, activity.getScaledTextSize(1.05f))
            gravity = Gravity.START
            setPadding(px(4), px(2), px(4), px(4))
        }
        row.addView(label)
        row.addView(valueView)
        row.setOnLongClickListener {
            try {
                activity.copyToClipboard(value)
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
            true
        }
        holder.addView(row)
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
            ?: activity.getString(R.string.unknown)
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
