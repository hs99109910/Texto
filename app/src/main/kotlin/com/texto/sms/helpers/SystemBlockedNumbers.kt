package com.texto.sms.helpers

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import android.telephony.PhoneNumberUtils

/**
 * Direct access to Android's system blocked-numbers provider — the very same list the
 * stock Google Messages app writes to.
 *
 * Fossify commons gates [org.fossify.commons.extensions.getBlockedNumbers] behind
 * `isDefaultDialer()`, which is never true for a messaging app, so it always handed back
 * an empty list and nothing was ever filtered. The platform grants provider access to the
 * default *SMS* app as well, so we query it ourselves.
 */
object SystemBlockedNumbers {

    /** How many trailing digits must match for two numbers to be considered the same. */
    private const val SIGNIFICANT_DIGITS = 9

    private data class Entry(val id: Long, val original: String, val e164: String)

    private fun Context.canAccess(): Boolean {
        return try {
            BlockedNumberContract.canCurrentUserBlockNumbers(this)
        } catch (_: Exception) {
            false
        }
    }

    private fun Context.readEntries(): List<Entry> {
        if (!canAccess()) return emptyList()

        val entries = ArrayList<Entry>()
        try {
            contentResolver.query(
                BlockedNumbers.CONTENT_URI,
                arrayOf(
                    BlockedNumbers.COLUMN_ID,
                    BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                    BlockedNumbers.COLUMN_E164_NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(BlockedNumbers.COLUMN_ID)
                val originalIndex = cursor.getColumnIndex(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                val e164Index = cursor.getColumnIndex(BlockedNumbers.COLUMN_E164_NUMBER)
                while (cursor.moveToNext()) {
                    entries.add(
                        Entry(
                            id = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                            original = if (originalIndex >= 0) {
                                cursor.getString(originalIndex).orEmpty()
                            } else {
                                ""
                            },
                            e164 = if (e164Index >= 0) cursor.getString(e164Index).orEmpty() else ""
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Not the default SMS app (yet) — treat as "nothing blocked" rather than crashing.
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return entries
    }

    /**
     * Reduces a number to a comparable form. Iranian numbers turn up as 09xx…, +989xx…
     * and 00989xx… for the same person, so comparison happens on the trailing digits.
     */
    fun comparable(number: String): String {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return digits.takeLast(SIGNIFICANT_DIGITS)
    }

    /**
     * Whether two sender identifiers name the same sender.
     *
     * Comparing [comparable] forms directly is wrong for alphanumeric sender ids
     * ("BANKMELLI", "Irancell"): they contain no digits, so both sides reduce to the empty
     * string and every such sender compares equal to every other one. Those can only be
     * compared literally.
     */
    fun isSameSender(one: String, other: String): Boolean {
        val oneComparable = comparable(one)
        val otherComparable = comparable(other)
        return if (oneComparable.isEmpty() || otherComparable.isEmpty()) {
            one.trim().equals(other.trim(), ignoreCase = true)
        } else {
            oneComparable == otherComparable
        }
    }

    private fun Entry.matches(candidate: String): Boolean {
        val target = comparable(candidate)
        if (target.isEmpty()) {
            // Alphanumeric sender ids can only be compared literally.
            return original.equals(candidate, ignoreCase = true)
        }
        return comparable(original) == target || comparable(e164) == target
    }

    /** Every blocked number, as originally entered. Empty when the provider is unreachable. */
    fun Context.listAll(): List<String> =
        readEntries().map { it.original }.filter { it.isNotEmpty() }

    /** Snapshot for hot loops, so the provider is queried once instead of per message. */
    fun Context.snapshot(): Set<String> =
        readEntries().flatMap { listOf(it.original, it.e164) }
            .filter { it.isNotEmpty() }
            .toSet()

    fun matchesSnapshot(number: String, snapshot: Set<String>): Boolean {
        if (snapshot.isEmpty()) return false
        val target = comparable(number)
        return snapshot.any { blocked ->
            if (target.isEmpty()) {
                blocked.equals(number, ignoreCase = true)
            } else {
                comparable(blocked) == target
            }
        }
    }

    fun Context.contains(number: String): Boolean = readEntries().any { it.matches(number) }

    fun Context.add(number: String): Boolean {
        if (number.isBlank() || !canAccess()) return false
        if (contains(number)) return true

        return try {
            val values = ContentValues().apply {
                put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                val normalized = PhoneNumberUtils.normalizeNumber(number)
                if (!normalized.isNullOrEmpty() && normalized.any { it.isDigit() }) {
                    put(BlockedNumbers.COLUMN_E164_NUMBER, normalized)
                }
            }
            contentResolver.insert(BlockedNumbers.CONTENT_URI, values) != null
        } catch (_: Exception) {
            false
        }
    }

    fun Context.remove(number: String): Boolean {
        if (!canAccess()) return false
        val matching = readEntries().filter { it.matches(number) }
        if (matching.isEmpty()) return false

        var removed = false
        matching.forEach { entry ->
            try {
                val uri = BlockedNumbers.CONTENT_URI.buildUpon()
                    .appendPath(entry.id.toString())
                    .build()
                if (contentResolver.delete(uri, null, null) > 0) removed = true
            } catch (_: Exception) {
            }
        }
        return removed
    }
}

// ---- Call-site friendly wrappers ------------------------------------------------------

/** One provider read, reused across a whole message/conversation loop. */
fun Context.blockedNumbersSnapshot(): Set<String> = with(SystemBlockedNumbers) { snapshot() }

fun isNumberBlockedIn(number: String, snapshot: Set<String>) =
    SystemBlockedNumbers.matchesSnapshot(number, snapshot)

/** Single-number check; queries the provider, so avoid it inside loops. */
fun Context.isNumberBlockedBySystem(number: String) =
    with(SystemBlockedNumbers) { contains(number) }

fun Context.blockNumber(number: String) = with(SystemBlockedNumbers) { add(number) }

fun Context.unblockNumber(number: String) = with(SystemBlockedNumbers) { remove(number) }

fun Context.allBlockedNumbers(): List<String> = with(SystemBlockedNumbers) { listAll() }
