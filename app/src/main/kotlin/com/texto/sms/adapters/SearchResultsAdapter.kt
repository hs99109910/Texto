package com.texto.sms.adapters

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.texto.sms.views.TextoRecyclerView
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.ItemSearchResultBinding
import com.texto.sms.extensions.*
import com.texto.sms.models.SearchResult
import com.texto.sms.helpers.TextoAvatars
import com.texto.sms.helpers.TextoGlass

class SearchResultsAdapter(
    activity: SimpleActivity, var searchResults: ArrayList<SearchResult>, recyclerView: TextoRecyclerView, highlightText: String, itemClick: (Any) -> Unit
) : BaseTextoRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private var fontSize = activity.getScaledTextSize()
    private var textToHighlight = highlightText

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = searchResults.size

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = searchResults.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = searchResults.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val searchResult = searchResults[position]
        holder.bindView(searchResult, allowSingleClick = true, allowLongClick = false) { itemView, _ ->
            setupView(itemView, searchResult)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = searchResults.size

    fun updateItems(newItems: ArrayList<SearchResult>, highlightText: String = "") {
        if (newItems.hashCode() != searchResults.hashCode()) {
            searchResults = newItems.clone() as ArrayList<SearchResult>
            textToHighlight = highlightText
            notifyDataSetChanged()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    /**
     * A result is drawn as the conversation list draws a row -- the same card, the same 48dp
     * squircle, the same type ramp and inks -- so moving between the two screens does not
     * change what a conversation looks like. It used to be a bare row on the page with the
     * match in commons' green, which was neither the theme's accent nor readable on it.
     */
    private fun setupView(view: View, searchResult: SearchResult) {
        ItemSearchResultBinding.bind(view).apply {
            val simpleActivity = activity as SimpleActivity
            val config = simpleActivity.config
            val ink = config.mainTextColor
            val density = resources.displayMetrics.density

            searchResultHolder.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = 16.getScaledPxIn(simpleActivity)
                marginEnd = 16.getScaledPxIn(simpleActivity)
                topMargin = 2.getScaledPxIn(simpleActivity)
                bottomMargin = 2.getScaledPxIn(simpleActivity)
            }
            val padH = 16.getScaledPxIn(simpleActivity)
            val padV = 10.getScaledPxIn(simpleActivity)
            searchResultHolder.setPadding(padH, padV, padH, padV)
            TextoGlass.applyPanel(
                view = searchResultHolder,
                tint = config.recentColor,
                cornerRadius = config.cardCornerRadiusDp * density,
                opacity = 0.68f,
                strokeWidthPx = 1.getScaledPxIn(simpleActivity),
                rimAlpha = 0.10f,
                sheenAlpha = 0f
            )

            searchResultTitle.apply {
                text = highlight(searchResult.title.asLtrPhone(), textToHighlight)
                setTextColor(ink)
                alpha = 1f
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.15f)
                typeface = simpleActivity.typefaceFor(Typeface.BOLD)
            }

            searchResultSnippet.apply {
                text = highlight(searchResult.snippet, textToHighlight)
                setTextColor(ink.withAlpha(0.58f))
                alpha = 1f
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
                typeface = simpleActivity.typefaceFor(Typeface.NORMAL)
            }

            searchResultDate.apply {
                text = searchResult.date
                setTextColor(ink.withAlpha(0.52f))
                alpha = 1f
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.85f)
                typeface = simpleActivity.typefaceFor(Typeface.NORMAL)
            }

            searchResultImage.updateLayoutParams {
                width = 48.getScaledPxIn(simpleActivity)
                height = 48.getScaledPxIn(simpleActivity)
            }
            TextoAvatars.clipToSquircle(searchResultImage)
            TextoAvatars.loadInto(activity, searchResultImage, searchResult.photoUri, TextoAvatars.letterAvatar(activity, searchResult.title))
        }
    }

    /**
     * Marks every occurrence of [needle] with a soft wash of the theme's accent and bold
     * weight, leaving the ink alone: the text keeps the contrast it already has, and the mark
     * follows whatever accent the theme and the tonality strip have chosen.
     */
    private fun highlight(text: String, needle: String): CharSequence {
        if (needle.isBlank() || text.isEmpty()) return text
        val haystack = text.lowercase()
        val target = needle.lowercase()
        // Lowercasing can change a string's length for a handful of letters; spans placed on
        // the lowered copy would then land on the wrong characters, so give up instead.
        if (haystack.length != text.length) return text
        val wash = (activity as SimpleActivity).config.accentGradientStart.withAlpha(0.22f)
        val out = SpannableString(text)
        var index = haystack.indexOf(target)
        while (index >= 0) {
            val end = index + target.length
            out.setSpan(BackgroundColorSpan(wash), index, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(StyleSpan(Typeface.BOLD), index, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = haystack.indexOf(target, end)
        }
        return out
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = ItemSearchResultBinding.bind(holder.itemView)
            Glide.with(activity).clear(binding.searchResultImage)
        }
    }
}
