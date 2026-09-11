package com.texto.sms.adapters

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import com.texto.sms.extensions.beGone
import com.texto.sms.extensions.beVisibleIf
import com.texto.sms.helpers.SimpleContactsHelper
import com.texto.sms.models.SimpleContact
import org.fossify.commons.views.MyRecyclerView
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.*
import com.texto.sms.databinding.ItemConversationBinding
import com.texto.sms.databinding.ItemConversationRecentBinding
import com.texto.sms.models.ConversationListItem
import java.util.ArrayList
import com.texto.sms.helpers.TextoAvatars
import com.texto.sms.helpers.TextoGlass

class ContactsAdapter(
    activity: SimpleActivity,
    items: ArrayList<out Any>,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewListAdapter<Any>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = ContactsDiffCallback(),
    itemClick = itemClick,
    onRefresh = {}
) {

    private var useModernPills: Boolean = false
    private var suggestionsCount: Int = 0

    fun setUseModernPills(use: Boolean) {
        useModernPills = use
    }

    companion object {
        const val VIEW_TYPE_SUGGESTION = 1
        const val VIEW_TYPE_CONTACT = 2
        const val VIEW_TYPE_MODERN_PILL = 3

        /** The list avatar, in dp before the UI:scale setting. Matches the conversations list. */
        private const val AVATAR_DP = 46
    }

    init {
        submitList(items as List<Any>)
    }

    override fun getActionMenuId() = 0
    override fun prepareActionMode(menu: android.view.Menu) {}
    override fun actionItemPressed(id: Int) {}
    override fun getSelectableItemCount() = 0
    override fun getIsItemSelectable(position: Int) = false
    override fun getItemSelectionKey(position: Int) = null
    override fun getItemKeyPosition(key: Int) = -1
    override fun onActionModeCreated() {}
    override fun onActionModeDestroyed() {}

    fun setSuggestionsCount(count: Int) {
        suggestionsCount = count
        notifyDataSetChanged()
    }

    fun updateContacts(newItems: List<Any>) {
        submitList(newItems)
    }

    override fun getItemViewType(position: Int): Int {
        if (useModernPills) return VIEW_TYPE_MODERN_PILL
        return if (position < suggestionsCount) VIEW_TYPE_SUGGESTION else VIEW_TYPE_CONTACT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRecyclerViewListAdapter<Any>.ViewHolder {
        val binding = when (viewType) {
            VIEW_TYPE_SUGGESTION -> com.texto.sms.databinding.ItemConversationRecentBinding.inflate(layoutInflater, parent, false)
            VIEW_TYPE_MODERN_PILL -> com.texto.sms.databinding.ItemConversationPillBinding.inflate(layoutInflater, parent, false)
            else -> ItemConversationBinding.inflate(layoutInflater, parent, false)
        }
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewListAdapter<Any>.ViewHolder, position: Int) {
        val item = getItem(position)
        val view = holder.itemView
        when (getItemViewType(position)) {
            VIEW_TYPE_SUGGESTION -> if (item is ConversationListItem) setupSuggestionView(view, item, holder)
            VIEW_TYPE_MODERN_PILL -> setupModernPillView(view, item, holder)
            else -> setupContactView(view, item, holder)
        }
        bindViewHolder(holder)
    }

    private fun setupSuggestionView(view: View, item: ConversationListItem, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        val conversation = item.conversation
        ItemConversationRecentBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            recentAddress.text = conversation.title
            recentAddress.setTextColor(mainTextColor)
            recentBody.text = "Suggested"
            recentBody.setTextColor(mainTextColor)
            recentBody.alpha = 0.7f
            recentDate.visibility = View.GONE
            
            val baseColor = activity.config.recentColor
            val lightened = baseColor.adjustColor(1.2f)
            val darkened = baseColor.adjustColor(0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            gd.cornerRadius = 1000f
            recentFrame.background = gd
            recentFrame.elevation = 8f * resources.displayMetrics.density
            recentFrame.setOnClickListener { holder.viewClicked(item) }
        }
    }

    private fun setupContactView(view: View, item: Any, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        ItemConversationBinding.bind(view).apply {
            val texto = activity as SimpleActivity
            val mainTextColor = activity.config.mainTextColor
            // Both the name and the number line can *be* a phone number, so both are isolated
            // before they are shown: see String.asLtrPhone for why the "+" moved without it.
            //
            // Someone with no contact card is named by their own number, and the second line
            // then repeated it verbatim -- the row said the same thing twice. The number line
            // is dropped whenever it would only echo the name above it.
            val name: String
            val number: String
            if (item is SimpleContact) {
                name = item.name
                number = item.phoneNumbers.firstOrNull()?.normalizedNumber.orEmpty()
            } else if (item is ConversationListItem) {
                name = item.conversation.title
                number = item.conversation.phoneNumber
            } else {
                name = ""
                number = ""
            }

            conversationAddress.text = name.asLtrPhone()
            val subtitle = if (number.isBlank() || number == name) "" else number.asLtrPhone()
            conversationBodyShort.text = subtitle
            conversationBodyShort.beVisibleIf(subtitle.isNotEmpty())

            // This list has no drafts, dates, pins or unread counts in it. All four default
            // to visible in the shared row layout and only the conversations adapter ever
            // hid them, so every contact row carried a "draft" label from a different screen
            // and reserved width for a date that is never set.
            draftIndicator.beGone()
            conversationDate.beGone()
            pinIndicator.beGone()
            unreadCountBadge.beGone()

            // The face beside the name. Nothing ever loaded it, so every row on the
            // new-conversation screen reserved a square of empty space and the list read as
            // a column of labels rather than of people.
            val photoUri = (item as? SimpleContact)?.photoUri
                ?: (item as? ConversationListItem)?.conversation?.photoUri
                ?: ""
            val avatarSize = AVATAR_DP.getScaledPxIn(texto)
            conversationImage.updateLayoutParams {
                width = avatarSize
                height = avatarSize
            }
            TextoAvatars.clipToSquircle(conversationImage)
            TextoAvatars.loadInto(activity, conversationImage, photoUri, TextoAvatars.letterAvatar(texto, name))

            val fontSize = texto.getScaledTextSize()
            conversationAddress.apply {
                setTextColor(mainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.05f)
                typeface = texto.typefaceFor(Typeface.BOLD)
            }
            conversationBodyShort.apply {
                setTextColor(mainTextColor.withAlpha(0.58f))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.85f)
                typeface = texto.typefaceFor(Typeface.NORMAL)
                alpha = 1f
            }

            // The same card the conversations list is drawn on, from the same theme slot.
            // This was a flat wash of the *background* colour lightened by a tenth, which on
            // a pale skin left the rows all but invisible against the screen behind them.
            val density = resources.displayMetrics.density
            val padH = 16.getScaledPxIn(texto)
            val padV = 10.getScaledPxIn(texto)
            conversationFrame.setPadding(padH, padV, padH, padV)
            conversationFrame.minimumHeight = 0
            TextoGlass.applyPanel(
                view = conversationFrame,
                tint = activity.config.recentColor,
                cornerRadius = activity.config.cardCornerRadiusDp * density,
                opacity = 0.68f,
                strokeWidthPx = density.toInt().coerceAtLeast(1),
                rimAlpha = 0.10f,
                sheenAlpha = 0f
            )
            conversationFrame.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            conversationFrame.setOnClickListener { holder.viewClicked(item) }
        }
    }

    private fun setupModernPillView(view: View, item: Any, holder: MyRecyclerViewListAdapter<Any>.ViewHolder) {
        com.texto.sms.databinding.ItemConversationPillBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            val contact = item as SimpleContact
            pillAddress.text = contact.name
            pillAddress.setTextColor(mainTextColor)
            
            TextoAvatars.clipToSquircle(pillImage)
            TextoAvatars.loadInto(activity, pillImage, contact.photoUri, TextoAvatars.letterAvatar(activity, contact.name))

            // A flat glass capsule, the same one the filter chips and the composer are.
            // The lighten/darken wash plus 6dp of lift was the embossed look the rest of the
            // app dropped, and it left these chips floating above a flat card.
            val density = resources.displayMetrics.density
            val radius = 1000f
            val base = TextoGlass.bar(
                tint = activity.config.recentColor,
                cornerRadius = radius,
                opacity = 0.55f,
                strokeWidthPx = 1,
                rimAlpha = 0.14f
            )

            pillFrame.background =
                if (activity.config.smallContactsOutline && activity.config.useNewUi) {
                    val thickStroke =
                        (activity.config.smallContactsOutlineThickness * density).toInt()
                    val outline = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = radius
                        setColor(Color.TRANSPARENT)
                        setStroke(thickStroke, activity.config.smallContactsOutlineColor)
                    }
                    LayerDrawable(arrayOf(base, outline))
                } else {
                    base
                }

            pillFrame.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            pillFrame.elevation = 0f
            pillFrame.setOnClickListener { holder.viewClicked(item) }
        }
    }

    private class ContactsDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            if (oldItem is SimpleContact && newItem is SimpleContact) return oldItem.rawId == newItem.rawId
            if (oldItem is ConversationListItem && newItem is ConversationListItem) return oldItem.id == newItem.id
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return oldItem == newItem
        }
    }

    inner class ContactViewHolder(val binding: androidx.viewbinding.ViewBinding) : ViewHolder(binding.root)
}
