package com.texto.sms.adapters
import com.texto.sms.extensions.*

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import org.fossify.commons.databinding.ItemContactWithNumberBinding
import com.texto.sms.extensions.darkenColor
import com.texto.sms.extensions.getContrastColor
import com.texto.sms.extensions.normalizeString
import com.texto.sms.helpers.SimpleContactsHelper
import com.texto.sms.models.SimpleContact
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.helpers.TextoAvatars

class AutoCompleteTextViewAdapter(val activity: SimpleActivity, val contacts: ArrayList<SimpleContact>) : ArrayAdapter<SimpleContact>(activity, 0, contacts) {
    var resultList = ArrayList<SimpleContact>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val contact = resultList.getOrNull(position)
        var listItem = convertView
        if (listItem == null || listItem.tag != contact?.name?.isNotEmpty()) {
            listItem = ItemContactWithNumberBinding.inflate(LayoutInflater.from(activity), parent, false).root
        }

        listItem.tag = contact?.name?.isNotEmpty()
        ItemContactWithNumberBinding.bind(listItem).apply {
            // clickable and focusable properties seem to break Autocomplete clicking, so remove them
            itemContactFrame.apply {
                isClickable = false
                isFocusable = false
            }

            val backgroundColor = activity.config.mainBackgroundColor
            val customTypeface = activity.getCustomTypeface()
            itemContactFrame.setBackgroundColor(backgroundColor.darkenColor())
            itemContactName.apply {
                setTextColor(backgroundColor.getContrastColor())
                typeface = Typeface.create(customTypeface, Typeface.NORMAL)
            }
            itemContactNumber.apply {
                setTextColor(backgroundColor.getContrastColor())
                typeface = Typeface.create(customTypeface, Typeface.NORMAL)
            }

            if (contact != null) {
                itemContactName.text = contact.name
                itemContactNumber.text = contact.phoneNumbers.first().normalizedNumber
                TextoAvatars.clipToSquircle(itemContactImage)
                TextoAvatars.loadInto(context, itemContactImage, contact.photoUri, TextoAvatars.letterAvatar(context, contact.name))
            }
        }

        return listItem
    }

    override fun getFilter() = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterResults = FilterResults()
            if (constraint != null) {
                val results = mutableListOf<SimpleContact>()
                val searchString = constraint.toString().normalizeString()
                contacts.forEach {
                    if (it.doesContainPhoneNumber(searchString) || it.name.contains(searchString, true)) {
                        results.add(it)
                    }
                }

                results.sortWith(compareBy { !it.name.startsWith(searchString, true) })

                filterResults.values = results
                filterResults.count = results.size
            }
            return filterResults
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (results != null && results.count > 0) {
                resultList.clear()
                @Suppress("UNCHECKED_CAST")
                resultList.addAll(results.values as List<SimpleContact>)
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?) = (resultValue as? SimpleContact)?.name
    }

    override fun getItem(index: Int) = resultList[index]

    override fun getCount() = resultList.size
}
