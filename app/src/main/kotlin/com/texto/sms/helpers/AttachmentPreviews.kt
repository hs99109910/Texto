package com.texto.sms.helpers

import android.app.Activity
import android.net.Uri
import android.view.View

import org.fossify.commons.helpers.SimpleContactsHelper
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.R
import com.texto.sms.databinding.ItemAttachmentDocumentBinding
import com.texto.sms.databinding.ItemAttachmentDocumentPreviewBinding
import com.texto.sms.databinding.ItemAttachmentVcardBinding
import com.texto.sms.databinding.ItemAttachmentVcardPreviewBinding
import com.texto.sms.extensions.*
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.darkenColor
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getBottomNavigationBackgroundColor
import org.fossify.commons.extensions.getColorStateList
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getFilenameFromUri
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getMyFileUri
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.maybeShowNumberPickerDialog
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.openRequestExactAlarmSettings
import org.fossify.commons.extensions.shareTextIntent
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.usableScreenSize
import com.texto.sms.extensions.applyColorFilter
import com.texto.sms.extensions.beGone
import com.texto.sms.extensions.beVisible
import com.texto.sms.extensions.beGoneIf
import com.texto.sms.extensions.beVisibleIf
import com.texto.sms.extensions.toast
import com.texto.sms.extensions.showErrorToast
import com.texto.sms.extensions.viewBinding

fun ItemAttachmentDocumentPreviewBinding.setupDocumentPreview(
    uri: Uri,
    title: String,
    mimeType: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onRemoveButtonClicked: (() -> Unit)? = null
) {
    documentAttachmentHolder.setupDocumentPreview(uri, title, mimeType, onClick, onLongClick)
    removeAttachmentButtonHolder.removeAttachmentButton.apply {
        beVisible()
        background.applyColorFilter(context.config.accentGradientStart)
        if (onRemoveButtonClicked != null) {
            setOnClickListener {
                onRemoveButtonClicked.invoke()
            }
        }
    }
}

fun ItemAttachmentDocumentBinding.setupDocumentPreview(
    uri: Uri,
    title: String,
    mimeType: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val context = root.context
    if (title.isNotEmpty()) {
        filename.text = title
    }

    ensureBackgroundThread {
        try {
            val size = context.getFileSizeFromUri(uri)
            root.post {
                fileSize.beVisible()
                fileSize.text = size.formatSize()
            }
        } catch (_: Exception) {
            root.post {
                fileSize.beGone()
            }
        }
    }

    val textColor = context.config.mainTextColor
    val primaryColor = context.config.accentGradientStart

    filename.setTextColor(textColor)
    fileSize.setTextColor(textColor)

    icon.setImageResource(getIconResourceForMimeType(mimeType))
    icon.background.setTint(primaryColor)
    root.background.applyColorFilter(primaryColor.darkenColor())

    root.setOnClickListener {
        onClick?.invoke()
    }

    root.setOnLongClickListener {
        onLongClick?.invoke()
        true
    }
}

fun ItemAttachmentVcardPreviewBinding.setupVCardPreview(
    activity: Activity,
    uri: Uri,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onRemoveButtonClicked: (() -> Unit)? = null,
) {
    vcardProgress.beVisible()
    vcardAttachmentHolder.setupVCardPreview(activity = activity, uri = uri, attachment = true, onClick = onClick, onLongClick = onLongClick) {
        vcardProgress.beGone()
        removeAttachmentButtonHolder.removeAttachmentButton.apply {
            beVisible()
            background.applyColorFilter(activity.config.accentGradientStart)
            if (onRemoveButtonClicked != null) {
                setOnClickListener {
                    onRemoveButtonClicked.invoke()
                }
            }
        }
    }
}

fun ItemAttachmentVcardBinding.setupVCardPreview(
    activity: Activity,
    uri: Uri,
    attachment: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onVCardLoaded: (() -> Unit)? = null,
) {
    val context = root.context
    val textColor = activity.config.mainTextColor
    val primaryColor = activity.config.accentGradientStart

    root.background.applyColorFilter(primaryColor.darkenColor())
    vcardTitle.setTextColor(textColor)
    vcardSubtitle.setTextColor(textColor)

    arrayOf<View>(vcardPhoto, vcardTitle, vcardSubtitle, viewContactDetails).forEach {
        it.beGone()
    }

    parseVCardFromUri(activity, uri) { vCards ->
        activity.runOnUiThread {
            if (vCards.isEmpty()) {
                vcardTitle.beVisible()
                vcardTitle.text = context.getString(org.fossify.commons.R.string.unknown_error_occurred)
                return@runOnUiThread
            }

            val title = vCards.firstOrNull()?.parseNameFromVCard()
            val imageIcon = if (title != null) {
                SimpleContactsHelper(activity).getContactLetterIcon(title)
            } else {
                null
            }

            arrayOf<View>(vcardPhoto, vcardTitle).forEach {
                it.beVisible()
            }

            vcardPhoto.setImageBitmap(imageIcon)
            vcardTitle.text = title

            if (vCards.size > 1) {
                vcardSubtitle.beVisible()
                val quantity = vCards.size - 1
                vcardSubtitle.text = context.resources.getQuantityString(R.plurals.and_other_contacts, quantity, quantity)
            } else {
                vcardSubtitle.beGone()
            }

            if (attachment) {
                onVCardLoaded?.invoke()
            } else {
                viewContactDetails.setTextColor(primaryColor)
                viewContactDetails.beVisible()
            }

            vcardAttachmentHolder.setOnClickListener {
                onClick?.invoke()
            }
            vcardAttachmentHolder.setOnLongClickListener {
                onLongClick?.invoke()
                true
            }
        }
    }
}

private fun getIconResourceForMimeType(mimeType: String) = when {
    mimeType.isAudioMimeType() -> R.drawable.ic_vector_audio_file
    mimeType.isCalendarMimeType() -> R.drawable.ic_calendar_month_vector
    mimeType.isPdfMimeType() -> R.drawable.ic_vector_pdf
    mimeType.isZipMimeType() -> R.drawable.ic_vector_folder_zip
    else -> R.drawable.ic_document_vector
}
