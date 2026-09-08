package com.texto.sms.models

/**
 * Thread item representations for the main thread recyclerview. [Message] is also a [ThreadItem]
 */
sealed class ThreadItem {
    data class ThreadDateTime(val date: Int, val simID: String) : ThreadItem()
    data class ThreadError(
        val messageId: Long,
        /** The body of the message that failed, which is what a tap on the row offers to send again. */
        val messageText: String,
        val isMMS: Boolean = false,
    ) : ThreadItem()
    data class ThreadSent(val messageId: Long, val delivered: Boolean) : ThreadItem()
    data class ThreadSending(val messageId: Long) : ThreadItem()
}
