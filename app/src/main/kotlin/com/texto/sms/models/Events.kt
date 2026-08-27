package com.texto.sms.models

class Events {
    class RefreshMessages
    class RefreshConversations(val isManualReorder: Boolean = false)
}
