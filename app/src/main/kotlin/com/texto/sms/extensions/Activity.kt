package com.texto.sms.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import org.fossify.commons.extensions.getMimeType
import com.texto.sms.extensions.hideKeyboard
import org.fossify.commons.extensions.launchViewContactIntent
import com.texto.sms.extensions.showErrorToast
import com.texto.sms.extensions.toast
import com.texto.sms.extensions.PERMISSION_CALL_PHONE
import com.texto.sms.helpers.SimpleContactsHelper
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.models.SimpleContact
import com.texto.sms.activities.ConversationDetailsActivity
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.helpers.THREAD_ID
import java.util.Locale

fun SimpleActivity.dialNumber(phoneNumber: String, callback: (() -> Unit)? = null) {
    hideKeyboard()
    handlePermission(PERMISSION_CALL_PHONE) {
        val action = if (it) Intent.ACTION_CALL else Intent.ACTION_DIAL
        Intent(action).apply {
            data = Uri.fromParts("tel", phoneNumber, null)

            try {
                startActivity(this)
                callback?.invoke()
            } catch (_: ActivityNotFoundException) {
                toast(org.fossify.commons.R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }
}

fun Activity.launchViewIntent(uri: Uri, mimetype: String, filename: String) {
    Intent().apply {
        action = Intent.ACTION_VIEW
        setDataAndType(uri, mimetype.lowercase(Locale.getDefault()))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            hideKeyboard()
            startActivity(this)
        } catch (_: ActivityNotFoundException) {
            val newMimetype = filename.getMimeType()
            if (newMimetype.isNotEmpty() && mimetype != newMimetype) {
                launchViewIntent(uri, newMimetype, filename)
            } else {
                toast(org.fossify.commons.R.string.no_app_found)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
}

fun Activity.startContactDetailsIntent(contact: SimpleContact) {
    ensureBackgroundThread {
        val lookupKey = SimpleContactsHelper(this)
            .getContactLookupKey(
                contactId = (contact).rawId.toString()
            )

        val publicUri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey
        )

        runOnUiThread {
            launchViewContactIntent(publicUri)
        }
    }
}

fun Activity.launchConversationDetails(threadId: Long) {
    Intent(this, ConversationDetailsActivity::class.java).apply {
        putExtra(THREAD_ID, threadId)
        startActivity(this)
    }
}
