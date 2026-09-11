package com.texto.sms.extensions

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat

/**
 * The permissions this app asks for, by their platform names.
 *
 * Commons numbered these and translated the number back into a string at the point of use.
 * The indirection bought nothing here -- every call site names the constant, never the
 * number -- and cost a lookup table that had to stay in step with two enums.
 */
const val PERMISSION_READ_SMS = Manifest.permission.READ_SMS
const val PERMISSION_SEND_SMS = Manifest.permission.SEND_SMS
const val PERMISSION_READ_CONTACTS = Manifest.permission.READ_CONTACTS
const val PERMISSION_READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE
const val PERMISSION_READ_CALL_LOG = Manifest.permission.READ_CALL_LOG
const val PERMISSION_CALL_PHONE = Manifest.permission.CALL_PHONE
const val PERMISSION_CAMERA = Manifest.permission.CAMERA
const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
const val PERMISSION_POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
