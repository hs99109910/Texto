package com.texto.sms.helpers

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

/**
 * The composer's field, which accepts stickers, GIFs and images straight from the keyboard.
 *
 * This is what replaced the sticker button. That button opened a grid of twenty-four emoji
 * built into the app: not stickers, unable to grow, and sitting next to a keyboard that
 * already ships thousands and knows which ones this person actually sends. Declaring the
 * accepted MIME types here is the whole reason Gboard offers its sticker and GIF panels at
 * all -- without it they are hidden, because the keyboard has nowhere to put the result.
 *
 * The content arrives as a URI the *keyboard* owns and can revoke, so [onContentReceived]
 * has to attach or copy it there and then rather than keep it. requestPermission is the ask
 * that makes it readable, and it is the step that fails silently when it is forgotten.
 */
class TextoComposerEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /** Called with each image the keyboard sends. Null means content is not accepted yet. */
    var onContentReceived: ((Uri) -> Unit)? = null

    /**
     * What the keyboard is told this field takes. Kept to the still formats: an animated GIF
     * or WebP sticker is sent as an MMS attachment like any other picture, and a video
     * sticker would be a different attachment path for no gain here.
     */
    private val acceptedMimeTypes = arrayOf(
        "image/gif", "image/png", "image/webp", "image/jpeg"
    )

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(editorInfo) ?: return null
        val handler = onContentReceived ?: return connection

        EditorInfoCompat.setContentMimeTypes(editorInfo, acceptedMimeTypes)
        return InputConnectionCompat.createWrapper(connection, editorInfo) { content, flags, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
                flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0
            ) {
                try {
                    content.requestPermission()
                } catch (_: Exception) {
                    return@createWrapper false
                }
            }
            try {
                handler(content.contentUri)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
