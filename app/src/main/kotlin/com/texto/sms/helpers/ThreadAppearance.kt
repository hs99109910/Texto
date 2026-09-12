package com.texto.sms.helpers

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One conversation's own colours, as a set of overrides.
 *
 * The same shape and the same rule as a filter's appearance fields: null means "follow
 * whatever this thread would have been drawn in anyway", so a thread nobody has customised
 * stores nothing and looks exactly as it did before this existed. That is what lets the three
 * layers stack per field rather than per theme -- a thread that only recolours its own sent
 * bubble still takes the rest from the filter it is in, and the rest of *that* from the app.
 *
 * Keyed by thread id rather than by phone number because that is what the screen doing the
 * editing already has, and because a number can move between threads (a group, a second SIM)
 * while the conversation the user customised stays the one they were looking at.
 */
@Serializable
data class ThreadAppearance(
    val sentBubbleColor: Int? = null,
    val sentBubbleTextColor: Int? = null,
    val receivedBubbleColor: Int? = null,
    val receivedBubbleTextColor: Int? = null,
    val backgroundColor: Int? = null,
) {
    /** True once nothing here overrides anything, which is when the entry can be dropped. */
    val isEmpty: Boolean
        get() = sentBubbleColor == null && sentBubbleTextColor == null &&
            receivedBubbleColor == null && receivedBubbleTextColor == null &&
            backgroundColor == null
}

/**
 * Thread appearances, stored together as one JSON object keyed by thread id.
 *
 * Prefs rather than a Room table, for two reasons. They are sparse -- only threads somebody
 * has deliberately recoloured appear at all -- so the whole map is a few entries, and the
 * conversation rows themselves are not a safe home for them: `pruneVanishedConversations`
 * deletes a cached conversation whenever the provider stops returning it, and a theme stored
 * on that row would go with it and come back as the app's default.
 */
object ThreadThemeStore {

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String): Map<Long, ThreadAppearance> {
        if (raw.isBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<Long, ThreadAppearance>>(raw)
                .filterValues { !it.isEmpty }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encode(appearances: Map<Long, ThreadAppearance>): String = try {
        json.encodeToString(appearances.filterValues { !it.isEmpty })
    } catch (_: Exception) {
        ""
    }

    /**
     * [thread] laid over [filter], field by field, as the single override the thread is drawn
     * from. Null when neither layer has anything to say, which is the case the adapter already
     * handles by reading the app's own colours.
     *
     * The label is never shown: this is the same carrier the filter path uses, so that the
     * adapter and the window background keep reading one object rather than learning about a
     * second source of colour.
     */
    fun merge(filter: MessageFilter?, thread: ThreadAppearance?): MessageFilter? {
        if (thread == null || thread.isEmpty) return filter
        val base = filter ?: MessageFilter(id = MERGED_ID, label = "")
        return base.copy(
            sentBubbleColor = thread.sentBubbleColor ?: base.sentBubbleColor,
            sentBubbleTextColor = thread.sentBubbleTextColor ?: base.sentBubbleTextColor,
            receivedBubbleColor = thread.receivedBubbleColor ?: base.receivedBubbleColor,
            receivedBubbleTextColor = thread.receivedBubbleTextColor
                ?: base.receivedBubbleTextColor,
            backgroundColor = thread.backgroundColor ?: base.backgroundColor,
        )
    }

    /** Storage-only id for a merge that had no filter under it. Never shown, never stored. */
    private const val MERGED_ID = "thread_appearance"
}
