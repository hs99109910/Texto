package com.texto.sms.extensions

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Column readers, by name rather than by index.
 *
 * `getColumnIndexOrThrow` and not `getColumnIndex`: the latter answers -1 for a column that
 * is not there, which `getString(-1)` then turns into a crash several frames away from the
 * mistake. Throwing on the lookup names the missing column instead, and every caller here
 * queries with a projection it wrote itself, so a miss is a bug rather than a condition to
 * handle.
 */
fun Cursor.getStringValue(key: String): String = getString(getColumnIndexOrThrow(key))

fun Cursor.getStringValueOrNull(key: String): String? = getString(getColumnIndexOrThrow(key))

fun Cursor.getIntValue(key: String): Int = getInt(getColumnIndexOrThrow(key))

fun Cursor.getLongValue(key: String): Long = getLong(getColumnIndexOrThrow(key))

fun Cursor.getBlobValue(key: String): ByteArray = getBlob(getColumnIndexOrThrow(key))

/** The same, for a column the caller knows may be absent from this particular query. */
fun Cursor.getIntValueOr(key: String, defaultValue: Int): Int {
    val index = getColumnIndex(key)
    return if (index == -1) defaultValue else getInt(index)
}

fun Cursor.getLongValueOr(key: String, defaultValue: Long): Long {
    val index = getColumnIndex(key)
    return if (index == -1) defaultValue else getLong(index)
}

fun Cursor.getStringValueOr(key: String, defaultValue: String): String {
    val index = getColumnIndex(key)
    return if (index == -1) defaultValue else getString(index) ?: defaultValue
}

/**
 * Walks a provider query row by row, closing the cursor whatever happens.
 *
 * [showErrors] decides whether a failure is surfaced or swallowed. Most callers here sweep
 * the SMS and MMS providers on a background pass where an OEM's provider raising on a column
 * it does not have is expected and not worth a toast; the ones that run because somebody
 * pressed something pass true.
 */
fun Context.queryCursor(
    uri: Uri,
    projection: Array<String>? = null,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    showErrors: Boolean = false,
    callback: (cursor: Cursor) -> Unit,
) {
    try {
        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    callback(cursor)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        if (showErrors) showErrorToast(e)
    }
}

/** The Bundle form, for the paging and sort arguments the newer provider API takes. */
fun Context.queryCursor(
    uri: Uri,
    projection: Array<String>?,
    queryArgs: Bundle,
    showErrors: Boolean = false,
    callback: (cursor: Cursor) -> Unit,
) {
    try {
        contentResolver.query(uri, projection, queryArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    callback(cursor)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        if (showErrors) showErrorToast(e)
    }
}
