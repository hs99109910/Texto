package com.texto.sms.receivers

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import org.fossify.commons.extensions.toast
import com.texto.sms.R
import com.texto.sms.helpers.COPY_CODE
import com.texto.sms.helpers.MESSAGE_CODE

/**
 * Copies an OTP/verification code straight from the notification shade, no activity launch.
 * Unlike mark-as-read/delete, this doesn't resolve the message, so the notification stays put.
 */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            COPY_CODE -> {
                val code = intent.getStringExtra(MESSAGE_CODE) ?: return
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.copy_code), code))
                // API 33+ already shows its own system confirmation UI when the clipboard changes.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    context.toast(R.string.code_copied)
                }
            }
        }
    }
}
