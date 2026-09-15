package com.texto.sms.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.view.updateLayoutParams
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.texto.sms.R
import com.texto.sms.adapters.ContactsAdapter
import com.texto.sms.databinding.ActivityNewConversationBinding
import com.texto.sms.dialogs.maybeShowNumberPickerDialog
import com.texto.sms.extensions.*
import com.texto.sms.helpers.NavigationIcon
import com.texto.sms.helpers.PICK_CONTACT_MODE
import com.texto.sms.helpers.PICK_CONTACT_RESULT_NAME
import com.texto.sms.helpers.PICK_CONTACT_RESULT_NUMBERS
import com.texto.sms.helpers.SimpleContactsHelper
import com.texto.sms.helpers.SmsIntentParser
import com.texto.sms.helpers.SystemBlockedNumbers
import com.texto.sms.helpers.THREAD_ATTACHMENT_URI
import com.texto.sms.helpers.THREAD_ATTACHMENT_URIS
import com.texto.sms.helpers.THREAD_ID
import com.texto.sms.helpers.THREAD_NUMBER
import com.texto.sms.helpers.THREAD_TEXT
import com.texto.sms.helpers.THREAD_TITLE
import com.texto.sms.helpers.TextoGlass
import com.texto.sms.helpers.emptyStateFor
import com.texto.sms.helpers.expandTouchTarget
import com.texto.sms.messaging.isShortCodeWithLetters
import com.texto.sms.models.SimpleContact
import java.util.Locale

/**
 * The app's contact picker: who to start a conversation with, or -- in pick mode -- whose card
 * to attach to one.
 *
 * The field leads, directly under the header. It used to be a 240dp pill parked at the foot of
 * the screen that had to be tapped open, animated out to full width, and once open sat on top
 * of the bottom of the list. A typed number nobody in the phone book has now gets a row saying
 * where the message will go, rather than a bare check mark at the end of the field.
 */
class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()

    /** Whether the address field is asking the IME for a keypad rather than a text keyboard. */
    private var isKeypadMode = false

    /** Picking the members of a group, rather than the one person to message. */
    private var isGroupMode = false

    /** The group so far: the number each member will be messaged on, to their name. */
    private val groupMembers = LinkedHashMap<String, String>()

    /** Comparable phone number -> timestamp of the newest message or call with it. */
    private var contactRecency: Map<String, Long> = emptyMap()

    /** Opened to hand one contact back to a conversation, rather than to start one. */
    private val isPickMode by lazy { intent.getBooleanExtra(PICK_CONTACT_MODE, false) }

    /**
     * The one form a number is compared in on this screen, matching how [getContactRecency]
     * and [getSuggestedContacts] key theirs. Alphanumeric sender ids have no digits, so their
     * comparable form is empty and they fall back to the lowercased id itself.
     */
    private fun recencyKey(number: String): String =
        SystemBlockedNumbers.comparable(number).ifEmpty { number.lowercase() }

    private val binding by viewBinding(ActivityNewConversationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(if (isPickMode) R.string.pick_a_contact else R.string.new_conversation)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.contactsList))

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.newConversationAddress.requestFocus()
        binding.newConversationSearchContainer.setOnClickListener {
            binding.newConversationAddress.requestFocus()
            showKeyboard(binding.newConversationAddress)
        }
        binding.newConversationGroupRow.setOnClickListener { setGroupMode(true) }

        // Back steps out of picking a group before it leaves the screen, so a half-built group
        // is abandoned deliberately rather than by the same gesture that closes the picker.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isGroupMode) {
                    setGroupMode(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        applyOutlines()
        setupTextoTopAppBar(
            binding.newConversationAppbar,
            NavigationIcon.Arrow,
            screenIcon = if (isPickMode) R.drawable.ic_ph_user_circle else R.drawable.ic_ph_user_plus,
        )
        binding.newConversationToolbar.setNavigationOnClickListener {
            if (isGroupMode) setGroupMode(false) else finish()
        }
        binding.newConversationToolbar.title = "" // Clear standard title to use custom TextView
        updateActivityCustomColors()
        refreshDirectRow(currentQuery())
        refreshGroupRow()
        renderGroupPanel()
    }

    private fun currentQuery() = binding.newConversationAddress.text?.toString().orEmpty()

    /**
     * Everything on this screen is painted here, from the live theme and the UI scale. The
     * layout used to carry white ink, a white search glyph and a #888888 hint, which on any
     * light skin was a white field label on a white capsule.
     */
    private fun updateActivityCustomColors() = binding.apply {
        applyCustomColors()
        val density = resources.displayMetrics.density
        val ink = config.mainTextColor
        val fieldInk = config.inputBarTextColor
        val accent = config.accentGradientStart

        newConversationToolbarTitle.apply {
            text = if (isGroupMode) getString(R.string.new_group) else title
            setTextColor(config.topBarTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.35f))
            typeface = typefaceFor(Typeface.BOLD)
        }

        // The header capsule's own material, so the field reads as part of the same bar
        // family rather than a card: see styleFilterChip for why the recipe must match.
        val fieldHeight = 52.getScaledPx()
        newConversationSearchContainer.updateLayoutParams<RelativeLayout.LayoutParams> {
            height = fieldHeight
            marginStart = 16.getScaledPx()
            marginEnd = 16.getScaledPx()
            topMargin = 8.getScaledPx()
        }
        newConversationSearchContainer.background = TextoGlass.bar(
            tint = if (config.topBarColor != 0) config.topBarColor else Color.BLACK,
            cornerRadius = fieldHeight / 2f,
            opacity = config.glassOpacity / 100f,
            strokeWidthPx = 1.getScaledPx(),
            rimAlpha = 0.20f
        )
        newConversationSearchIcon.applyColorFilter(fieldInk.withAlpha(0.68f))
        newConversationAddress.apply {
            setTextColor(fieldInk)
            setHintTextColor(fieldInk.withAlpha(0.58f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.0f))
            typeface = typefaceFor(Typeface.NORMAL)
        }
        newConversationConfirm.applyColorFilter(accent)
        newConversationKeypadToggle.applyColorFilter(fieldInk)
        newConversationKeypadToggle.expandTouchTarget()
        newConversationConfirm.expandTouchTarget()

        // The same card a contact row is drawn on, led by an accent disc so it reads as the
        // one row on the screen that is an action rather than a person.
        newConversationDirectRow.updateLayoutParams<RelativeLayout.LayoutParams> {
            marginStart = 16.getScaledPx()
            marginEnd = 16.getScaledPx()
            topMargin = 12.getScaledPx()
        }
        newConversationDirectRow.minimumHeight = 64.getScaledPx()
        TextoGlass.applyPanel(
            view = newConversationDirectRow,
            tint = config.recentColor,
            cornerRadius = config.cardCornerRadiusDp * density,
            opacity = 0.68f,
            strokeWidthPx = 1.getScaledPx(),
            rimAlpha = 0.10f,
            sheenAlpha = 0f
        )
        newConversationDirectRow.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        newConversationDirectIcon.apply {
            updateLayoutParams {
                width = 44.getScaledPx()
                height = 44.getScaledPx()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent.withAlpha(0.14f))
                setStroke(1.getScaledPx(), accent.withAlpha(0.30f))
            }
            applyColorFilter(accent)
        }
        newConversationDirectLabel.apply {
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.0f))
            typeface = typefaceFor(Typeface.BOLD)
        }

        // "Create group" is the direct row's sibling: the same card, the same accent disc.
        newConversationGroupRow.updateLayoutParams<RelativeLayout.LayoutParams> {
            marginStart = 16.getScaledPx()
            marginEnd = 16.getScaledPx()
            topMargin = 12.getScaledPx()
        }
        newConversationGroupRow.minimumHeight = 56.getScaledPx()
        TextoGlass.applyPanel(
            view = newConversationGroupRow,
            tint = config.recentColor,
            cornerRadius = config.cardCornerRadiusDp * density,
            opacity = 0.68f,
            strokeWidthPx = 1.getScaledPx(),
            rimAlpha = 0.10f,
            sheenAlpha = 0f
        )
        newConversationGroupIcon.apply {
            updateLayoutParams {
                width = 40.getScaledPx()
                height = 40.getScaledPx()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent.withAlpha(0.14f))
                setStroke(1.getScaledPx(), accent.withAlpha(0.30f))
            }
            applyColorFilter(accent)
        }
        newConversationGroupLabel.apply {
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.0f))
            typeface = typefaceFor(Typeface.BOLD)
        }
        TextoGlass.applyPanel(
            view = newConversationGroupPanel,
            tint = config.recentColor,
            cornerRadius = config.cardCornerRadiusDp * density,
            opacity = 0.68f,
            strokeWidthPx = 1.getScaledPx(),
            rimAlpha = 0.10f,
            sheenAlpha = 0f
        )
        newConversationGroupHint.apply {
            setTextColor(ink.withAlpha(0.58f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.85f))
            typeface = typefaceFor(Typeface.NORMAL)
        }
        newConversationGroupDone.apply {
            setTextColor(config.accentInkColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.95f))
            typeface = typefaceFor(Typeface.BOLD)
            setPaddingRelative(20.getScaledPx(), 10.getScaledPx(), 20.getScaledPx(), 10.getScaledPx())
            minHeight = 44.getScaledPx()
            background = TextoGlass.accent(
                config.accentGradientStart,
                config.accentGradientEnd,
                100f * density,
                config.accentGradientMid
            )
        }

        // Fast Scroller Sync
        contactsLetterFastscroller.textColor = ink.getColorStateList()
        contactsLetterFastscroller.pressedTextColor = accent
        contactsLetterFastscrollerThumb.setupWithFastScroller(contactsLetterFastscroller)
        contactsLetterFastscrollerThumb.textColor = accent.getContrastColor()
        contactsLetterFastscrollerThumb.thumbColor = accent.getColorStateList()
    }

    private fun initContacts() {
        if (!isPickMode && isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        setupKeypadToggle()
        binding.newConversationAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO && binding.newConversationDirectRow.visibility == View.VISIBLE) {
                binding.newConversationDirectRow.performClick()
                true
            } else {
                false
            }
        }
        binding.newConversationAddress.onTextChangeListener { searchString ->
            // Before the list: whether the direct row is up decides whether an empty list
            // means "nobody found" or simply "send it to this number".
            refreshDirectRow(searchString)
            refreshGroupRow()

            // Suggestions land in allContacts, so the filter has to run *after* they arrive,
            // not before: filtering first and refreshing afterwards showed the list as it was
            // without them, which is what kept recent senders off the empty screen.
            if (config.useNewUi && searchString.isEmpty()) {
                fillSuggestedContacts { showFilteredContacts(searchString) }
            } else {
                showFilteredContacts(searchString)
            }

            val shortCodeWithLetters = isShortCodeWithLetters(searchString)
            binding.newConversationConfirm.beVisibleIf(
                !isPickMode && searchString.isNotEmpty() && !shortCodeWithLetters
            )
            binding.newConversationConfirm.setOnClickListener {
                if (shortCodeWithLetters) {
                    toast(R.string.invalid_short_code, length = Toast.LENGTH_LONG)
                } else if (isGroupMode) {
                    toggleGroupMember(searchString, searchString)
                } else {
                    launchThreadActivity(searchString, searchString)
                }
            }
        }
    }

    /**
     * Shows "Message <number>" for a typed number nobody in the phone book has.
     *
     * The only way to send to such a number was the check mark at the far end of the field,
     * which said nothing about what it would do. Hidden in pick mode: a bare number is not a
     * contact card, and attaching one is not what that flow is for.
     */
    private fun refreshDirectRow(query: String) = binding.apply {
        val typed = query.trim()
        val digits = typed.count { it.isDigit() }
        val dialable = !isPickMode && digits >= 3 && typed.all { it.isDigit() || it in "+-() " }
        val needle = recencyKey(typed)
        val known = dialable && allContacts.any { contact ->
            contact.phoneNumbers.any { recencyKey(it.normalizedNumber) == needle }
        }
        val show = dialable && !known
        newConversationDirectRow.beVisibleIf(show)
        if (show) {
            newConversationDirectLabel.text = getString(R.string.send_to_number, typed.asLtrPhone())
            newConversationDirectRow.contentDescription = newConversationDirectLabel.text
            newConversationDirectRow.setOnClickListener {
                if (isGroupMode) toggleGroupMember(typed, typed) else launchThreadActivity(typed, typed)
            }
        }
    }

    /** "Create group" is offered on the untouched screen only: once something is typed, the
     * rows below it are search results and it would sit on top of them. */
    private fun refreshGroupRow() {
        binding.newConversationGroupRow.beVisibleIf(
            !isPickMode && !isGroupMode && currentQuery().isEmpty()
        )
    }

    private fun setGroupMode(on: Boolean) {
        isGroupMode = on
        if (!on) groupMembers.clear()
        binding.newConversationToolbarTitle.text =
            getString(if (on) R.string.new_group else R.string.new_conversation)
        refreshGroupRow()
        renderGroupPanel()
    }

    /** Adds [number] to the group, or takes it back out when it is already in. */
    private fun toggleGroupMember(number: String, name: String) {
        if (groupMembers.remove(number) == null) {
            groupMembers[number] = name.ifBlank { number }
        }
        if (currentQuery().isNotEmpty()) binding.newConversationAddress.setText("")
        renderGroupPanel()
    }

    /**
     * Who is in the group so far, as chips a tap removes, and the button that starts it once
     * there are at least two people -- one person is a conversation, not a group.
     */
    // Explicitly typed: it calls itself from the chips' listeners, and Kotlin cannot infer a
    // return type through that recursion.
    private fun renderGroupPanel(): ActivityNewConversationBinding = binding.apply {
        newConversationGroupPanel.beVisibleIf(isGroupMode)
        if (!isGroupMode) return@apply

        val density = resources.displayMetrics.density
        val ink = config.mainTextColor
        val accent = config.accentGradientStart
        newConversationGroupChips.removeAllViews()
        newConversationGroupChips.lineSpacing = 6.getScaledPx()
        groupMembers.forEach { (number, name) ->
            val chip = android.widget.TextView(this@NewConversationActivity).apply {
                // The remove mark is a drawable at the end, not a character in the text: after
                // an isolated number, a trailing "✕" resolved to either side depending on
                // whether the name was Latin, a number or Persian.
                text = name.asLtrPhone()
                textDirection = View.TEXT_DIRECTION_LOCALE
                contentDescription = name
                maxLines = 1
                val remove = androidx.appcompat.content.res.AppCompatResources
                    .getDrawable(this@NewConversationActivity, R.drawable.ic_ph_x)
                    ?.mutate()
                    ?.apply {
                        val size = 14.getScaledPx()
                        setBounds(0, 0, size, size)
                        setTint(ink.withAlpha(0.6f))
                    }
                setCompoundDrawablesRelative(null, null, remove, null)
                compoundDrawablePadding = 6.getScaledPx()
                gravity = android.view.Gravity.CENTER_VERTICAL
                setTextColor(ink)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.85f))
                typeface = typefaceFor(Typeface.NORMAL)
                setPaddingRelative(12.getScaledPx(), 7.getScaledPx(), 12.getScaledPx(), 7.getScaledPx())
                background = TextoGlass.bar(
                    tint = accent,
                    cornerRadius = 100f * density,
                    opacity = 0.12f,
                    strokeWidthPx = 1.getScaledPx(),
                    rimAlpha = 0.28f
                )
                setOnClickListener {
                    groupMembers.remove(number)
                    renderGroupPanel()
                }
            }
            newConversationGroupChips.addView(
                chip,
                android.view.ViewGroup.MarginLayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = 3.getScaledPx()
                    rightMargin = 3.getScaledPx()
                }
            )
        }
        newConversationGroupChips.beVisibleIf(groupMembers.isNotEmpty())

        val ready = groupMembers.size >= 2
        newConversationGroupHint.beVisibleIf(!ready)
        newConversationGroupDone.text =
            getString(R.string.group_create_action, groupMembers.size.toString().toUiDigits())
        newConversationGroupDone.isEnabled = ready
        newConversationGroupDone.alpha = if (ready) 1f else 0.4f
        newConversationGroupDone.setOnClickListener {
            if (groupMembers.size < 2) return@setOnClickListener
            launchThreadActivity(
                groupMembers.keys.joinToString(";"),
                groupMembers.values.joinToString(", ")
            )
        }
    }

    /**
     * Everyone whose name or number matches [searchString], most recently in touch first.
     * A number is matched on its comparable form so spacing and a country prefix do not
     * decide whether it is found.
     */
    private fun showFilteredContacts(searchString: String) {
        val needle = recencyKey(searchString)
        val filteredContacts = allContacts.filterTo(ArrayList()) { contact ->
            contact.name.contains(searchString, true) ||
                contact.name.contains(searchString.normalizeString(), true) ||
                contact.name.normalizeString().contains(searchString, true) ||
                contact.phoneNumbers.any {
                    it.normalizedNumber.contains(searchString, true) ||
                        (needle.isNotEmpty() && recencyKey(it.normalizedNumber).contains(needle))
                }
        }

        sortByRecency(filteredContacts, searchString)
        setupAdapter(filteredContacts)
    }

    /**
     * Flips the field between searching the phone book by name and dialling a number.
     *
     * The field is declared `textCapWords`, so reaching someone not in contacts meant typing
     * their number on an alphabetic keyboard. This asks the IME for a keypad instead, and
     * carries the caret over so a half-typed number is not lost in the switch.
     */
    private fun setupKeypadToggle() = binding.apply {
        newConversationKeypadToggle.setOnClickListener {
            isKeypadMode = !isKeypadMode
            applyKeypadMode()
            newConversationAddress.requestFocus()
            showKeyboard(newConversationAddress)
        }
        applyKeypadMode()
    }

    private fun applyKeypadMode() = binding.apply {
        newConversationAddress.inputType = if (isKeypadMode) {
            android.text.InputType.TYPE_CLASS_PHONE
        } else {
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        // Setting inputType moves the caret to the start, which would silently reverse what
        // has been typed so far the first time the mode is flipped.
        newConversationAddress.setSelection(newConversationAddress.text?.length ?: 0)
        newConversationAddress.setHint(
            if (isKeypadMode) R.string.type_a_number else R.string.add_contact_or_number
        )

        newConversationKeypadToggle.setImageResource(
            if (isKeypadMode) R.drawable.ic_ph_text_aa else R.drawable.ic_ph_numpad
        )
        newConversationKeypadToggle.contentDescription = getString(
            if (isKeypadMode) R.string.switch_to_search else R.string.switch_to_keypad
        )
        // The active mode is marked by weight rather than by a second colour, so the row
        // still reads as one control.
        newConversationKeypadToggle.applyColorFilter(config.inputBarTextColor)
        newConversationKeypadToggle.alpha = if (isKeypadMode) 1f else 0.6f
    }

    private fun isThirdPartyIntent(): Boolean {
        val result = SmsIntentParser.parse(intent)
        if (result != null && result.recipients.isNotEmpty()) {
            // Show the contact's name in the toolbar when we can resolve one, rather
            // than the bare number the dialer handed us.
            val name = getNameAndPhotoFromPhoneNumber(result.recipients).name
                .ifBlank { result.recipients }
            launchThreadActivity(result.recipients, name, result.body)
            finish()
            return true
        }
        return false
    }

    private fun fetchContacts() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                ensureBackgroundThread {
                    SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
                        allContacts = contacts as ArrayList<SimpleContact>
                        contactRecency = getContactRecency()

                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            // Contacts can arrive after a number was already typed, and
                            // only now is it known whether someone has that number.
                            refreshDirectRow(currentQuery())
                            if (config.useNewUi) {
                                fillSuggestedContacts {
                                    showFilteredContacts(currentQuery())
                                }
                            } else {
                                showFilteredContacts(currentQuery())
                            }
                        }
                    }
                }
            } else {
                setupAdapter(ArrayList())
            }
        }
    }

    private fun setupAdapter(contacts: ArrayList<SimpleContact>) {
        val hasContacts = contacts.isNotEmpty()
        val granted = hasPermission(PERMISSION_READ_CONTACTS)
        binding.contactsList.beVisibleIf(hasContacts)
        binding.noContactsPlaceholder.beGone()
        binding.noContactsPlaceholder2.beGone()

        // Nothing to list is only worth saying when there is also nothing else to do: a typed
        // number already has its own row, and a second message under it would contradict it.
        val showEmpty = !hasContacts && binding.newConversationDirectRow.visibility != View.VISIBLE
        emptyStateFor(
            placeholder = binding.noContactsPlaceholder,
            icon = R.drawable.ic_ph_user_circle,
            title = getString(if (granted) R.string.no_contacts_found else R.string.no_access_to_contacts),
            body = getString(
                when {
                    !granted -> R.string.contacts_permission_body
                    isPickMode -> R.string.contacts_empty_body_pick
                    else -> R.string.contacts_empty_body
                }
            ),
            actionLabel = if (granted) null else getString(R.string.request_the_required_permissions),
            onAction = if (granted) null else ({
                handlePermission(PERMISSION_READ_CONTACTS) { allowed -> if (allowed) fetchContacts() }
            }),
        ).beVisibleIf(showEmpty)

        val currAdapter = binding.contactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(this, contacts, binding.contactsList) {
                onContactTapped(it as SimpleContact)
            }.apply {
                binding.contactsList.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                binding.contactsList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).updateContacts(contacts as List<Any>)
        }

        setupLetterFastscroller(contacts)
    }

    private fun onContactTapped(contact: SimpleContact) {
        if (isPickMode) {
            // A card carries every number the person has, so there is nothing to choose
            // between and no number dialog.
            hideKeyboard()
            setResult(RESULT_OK, Intent().apply {
                putExtra(PICK_CONTACT_RESULT_NAME, contact.name)
                putStringArrayListExtra(
                    PICK_CONTACT_RESULT_NUMBERS,
                    contact.phoneNumbers.mapTo(ArrayList()) { it.value.ifBlank { it.normalizedNumber } }
                )
            })
            finish()
            return
        }

        if (isGroupMode) {
            if (contact.phoneNumbers.size <= 1) {
                val number = contact.phoneNumbers.firstOrNull()?.normalizedNumber ?: return
                toggleGroupMember(number, contact.name)
            } else {
                maybeShowNumberPickerDialog(contact.phoneNumbers) { number ->
                    toggleGroupMember(number.normalizedNumber, contact.name)
                }
            }
            return
        }

        if (contact.phoneNumbers.size == 1) {
            launchThreadActivity(contact.phoneNumbers.first().normalizedNumber, contact.name)
        } else {
            maybeShowNumberPickerDialog(contact.phoneNumbers) { number ->
                launchThreadActivity(number.normalizedNumber, contact.name)
            }
        }
    }

    /**
     * People messaged most recently come first. When the user is typing, a name that
     * *starts* with the query still outranks recency, since that is almost always the
     * person being looked for.
     */
    private fun sortByRecency(contacts: ArrayList<SimpleContact>, searchString: String) {
        fun lastUsed(contact: SimpleContact): Long {
            return contact.phoneNumbers.maxOfOrNull { number ->
                contactRecency[recencyKey(number.normalizedNumber)] ?: 0L
            } ?: 0L
        }

        contacts.sortWith(
            compareBy<SimpleContact> { !it.name.startsWith(searchString, true) }
                .thenByDescending { lastUsed(it) }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun fillSuggestedContacts(callback: () -> Unit) {
        ensureBackgroundThread {
            val suggestions = getSuggestedContacts()

            // Merged on the number, not on contactId. Every suggestion is built with
            // contactId 0, so an id comparison matched the first one already added and threw
            // away every suggestion after it -- the screen offered exactly one recent person
            // and then only ever the phone book.
            val known = allContacts
                .mapNotNullTo(HashSet()) { contact ->
                    contact.phoneNumbers.firstOrNull()?.normalizedNumber?.let(::recencyKey)
                }
            suggestions.forEach { contact ->
                val key = contact.phoneNumbers.firstOrNull()?.normalizedNumber?.let(::recencyKey)
                if (key != null && known.add(key)) {
                    allContacts.add(contact)
                }
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                callback()
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        binding.contactsLetterFastscroller.setupWithRecyclerView(binding.contactsList, { position ->
            try {
                val name = contacts[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(
                    character.uppercase(Locale.getDefault()).normalizeString()
                )
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    private fun launchThreadActivity(phoneNumber: String, name: String, body: String = "") {
        hideKeyboard()
        val numbers = phoneNumber.split(";").toSet()

        // A forward arrives here carrying the text/attachments on our own intent, so fall
        // back to those when the caller did not pass a body explicitly. A third-party share
        // (e.g. ACTION_SEND from another app) carries the same payload on the standard
        // Intent.EXTRA_TEXT/EXTRA_STREAM extras instead, so fall back to those last.
        val forwardedText = body.ifEmpty {
            intent.getStringExtra(THREAD_TEXT).orEmpty().ifEmpty {
                // ACTION_SEND text is declared as CharSequence, not always a String
                // (e.g. a SpannableString), so read it as such rather than with getStringExtra.
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            }
        }
        val forwardedUris = intent.getParcelableArrayListExtra<Uri>(THREAD_ATTACHMENT_URIS)

        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, this@NewConversationActivity.getThreadId(numbers))
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_NUMBER, phoneNumber)
            putExtra(THREAD_TEXT, forwardedText)

            val uri = intent.getParcelableExtra<Uri>(THREAD_ATTACHMENT_URI)
            val singleSharedUri = intent.takeIf {
                it.action == Intent.ACTION_SEND && it.extras?.containsKey(Intent.EXTRA_STREAM) == true
            }?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

            if (uri != null) {
                putExtra(THREAD_ATTACHMENT_URI, uri.toString())
            } else if (!forwardedUris.isNullOrEmpty()) {
                putParcelableArrayListExtra(THREAD_ATTACHMENT_URIS, forwardedUris)
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.extras?.containsKey(
                    Intent.EXTRA_STREAM
                ) == true
            ) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URIS, uris)
            } else if (singleSharedUri != null) {
                putExtra(THREAD_ATTACHMENT_URI, singleSharedUri.toString())
            }

            startActivity(this)
        }
        // The picker has done its job; going back should return to the thread list.
        finish()
    }

    private fun applyOutlines() = binding.apply {
        val density = resources.displayMetrics.density
        val isNewUi = config.useNewUi

        // Top Bar Outline (New Conversation)
        if (config.topBarOutline && isNewUi) {
            val r26 = 26f * density
            val thickness = config.topBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val outline = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.topBarOutlineColor)
                setColor(Color.TRANSPARENT)
                cornerRadii = FloatArray(8) { r26 }
            }
            val drawable = android.graphics.drawable.LayerDrawable(arrayOf(outline))
            // Sits exactly on the painted bar, which is now rounded all round and
            // starts below the status bar rather than behind it.
            drawable.setLayerInset(0, 0, statusBarInsetOf(binding.newConversationAppbar), 0, 0)
            binding.newConversationAppbar.foreground = drawable
        } else {
            binding.newConversationAppbar.foreground = null
        }

        // Search Bar Outline
        if (config.searchBarOutline && isNewUi) {
            val thickness = config.searchBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.searchBarOutlineColor)
                cornerRadius = 100f * density
                setColor(Color.TRANSPARENT)
            }
            binding.newConversationSearchContainer.foreground = drawable
        } else {
            binding.newConversationSearchContainer.foreground = null
        }
    }
}
