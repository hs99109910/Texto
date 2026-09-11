package com.texto.sms.models

/**
 * One number on a contact.
 *
 * The field names are the ones commons used, because a conversation's participants are stored
 * in Room as Gson JSON keyed on field names: renaming any of these would leave every cached
 * row unreadable by the build that renamed it.
 */
data class PhoneNumber(
    var value: String,
    var type: Int,
    var label: String,
    var normalizedNumber: String,
    var isPrimary: Boolean = false,
)
