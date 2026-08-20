package com.texto.sms.helpers

import android.content.Context
import com.texto.sms.extensions.config

object ReceiverUtils {

    fun isMessageFilteredOut(context: Context, body: String): Boolean {
        android.util.Log.d("ReceiverUtils", "Checking message body: '$body'")
        for (blockedKeyword in context.config.blockedKeywords) {
            if (body.contains(blockedKeyword, ignoreCase = true)) {
                android.util.Log.d("ReceiverUtils", "Message filtered out by keyword: '$blockedKeyword'")
                return true
            }
        }

        return false
    }
}
