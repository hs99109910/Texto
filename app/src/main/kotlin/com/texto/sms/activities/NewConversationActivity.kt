package com.texto.sms.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.SimpleContact
import com.texto.sms.R
import com.texto.sms.adapters.ContactsAdapter
import com.texto.sms.databinding.ActivityNewConversationBinding
import com.texto.sms.extensions.*
import com.texto.sms.helpers.SmsIntentParser
import com.texto.sms.helpers.SystemBlockedNumbers
import com.texto.sms.helpers.THREAD_ATTACHMENT_URI
import com.texto.sms.helpers.THREAD_ATTACHMENT_URIS
import com.texto.sms.helpers.THREAD_ID
import com.texto.sms.helpers.THREAD_NUMBER
import com.texto.sms.helpers.THREAD_TEXT
import com.texto.sms.helpers.THREAD_TITLE
import com.texto.sms.messaging.isShortCodeWithLetters
import java.util.Locale

class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var wasImeVisible = false

    /** Whether the address field is asking the IME for a keypad rather than a text keyboard. */
    private var isKeypadMode = false

    /** Comparable phone number -> timestamp of the newest message or call with it. */
    private var contactRecency: Map<String, Long> = emptyMap()

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
        title = getString(R.string.new_conversation)
        updateTextColors(binding.newConversationHolder)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.contactsList))
        setupSearchEdgeToEdge()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.newConversationAddress.requestFocus()

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }

        // Keyboard Sync for Search Bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.newConversationCoordinator) { _, insets ->
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (wasImeVisible && !isImeVisible && config.useNewUi && binding.newConversationAddress.text?.isEmpty() == true) {
                shrinkSearchBar()
            }
            wasImeVisible = isImeVisible
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        applyOutlines()
        setupTopAppBar(binding.newConversationAppbar, NavigationIcon.Arrow)
        binding.newConversationToolbar.setNavigationOnClickListener {
            finish()
        }
        binding.newConversationToolbar.title = "" // Clear standard title to use custom TextView

        binding.noContactsPlaceholder2.setTextColor(config.accentGradientStart)
        binding.noContactsPlaceholder2.underlineText()
        updateActivityCustomColors()

        setupModernSearchBar()
    }

    private fun setupModernSearchBar() = binding.apply {
        if (!config.useNewUi) return@apply
        
        val collapsedWidth = 240.getScaledPx()
        
        // New UI: Centered search bar that expands
        newConversationSearchContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            width = if (newConversationAddress.text.isNullOrEmpty() && !newConversationAddress.hasFocus()) collapsedWidth else androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        }

        newConversationSearchContainer.setOnClickListener {
            if (!newConversationAddress.isFocusable) {
                expandSearchBar()
            } else {
                showKeyboard(newConversationAddress)
            }
        }

        newConversationAddress.isFocusable = !newConversationAddress.text.isNullOrEmpty() || newConversationAddress.hasFocus()
        newConversationAddress.isFocusableInTouchMode = newConversationAddress.isFocusable

        newConversationAddress.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                expandSearchBar()
            } else if (newConversationAddress.text.isNullOrEmpty()) {
                shrinkSearchBar()
            }
        }
    }

    private fun expandSearchBar() = binding.apply {
        val startWidth = newConversationSearchContainer.width
        val endWidth = root.width - 32.getScaledPx()
        
        if (startWidth >= endWidth - 5) return@apply

        val animator = ValueAnimator.ofInt(startWidth, endWidth)
        animator.duration = 450
        animator.interpolator = android.view.animation.OvershootInterpolator(1.2f)
        animator.addUpdateListener { animation ->
            newConversationSearchContainer.updateLayoutParams {
                width = animation.animatedValue as Int
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                newConversationAddress.isFocusable = true
                newConversationAddress.isFocusableInTouchMode = true
                newConversationAddress.requestFocus()
                showKeyboard(newConversationAddress)
            }
        })
        animator.start()
    }

    private fun shrinkSearchBar() = binding.apply {
        val startWidth = newConversationSearchContainer.width
        val endWidth = 240.getScaledPx()
        
        if (startWidth <= endWidth + 5) return@apply

        val animator = ValueAnimator.ofInt(startWidth, endWidth)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            newConversationSearchContainer.updateLayoutParams {
                width = animation.animatedValue as Int
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                newConversationAddress.isFocusable = false
                newConversationAddress.isFocusableInTouchMode = false
                hideKeyboard()
            }
        })
        animator.start()
    }

    private fun updateActivityCustomColors() {
        applyCustomColors()
        val mainTextColor = config.mainTextColor
        binding.newConversationAddress.setTextColor(config.inputBarTextColor)
        binding.newConversationAddress.setHintTextColor(config.inputBarTextColor.withAlpha(0.5f))
        binding.newConversationConfirm.applyColorFilter(config.inputBarTextColor)
        binding.newConversationKeypadToggle.applyColorFilter(config.inputBarTextColor)

        // Fast Scroller Sync
        val properPrimaryColor = config.accentGradientStart
        binding.contactsLetterFastscroller.textColor = mainTextColor.getColorStateList()
        binding.contactsLetterFastscroller.pressedTextColor = properPrimaryColor
        binding.contactsLetterFastscrollerThumb.setupWithFastScroller(binding.contactsLetterFastscroller)
        binding.contactsLetterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        binding.contactsLetterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
    }

    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        setupKeypadToggle()
        binding.newConversationAddress.onTextChangeListener { searchString ->
            // Suggestions land in allContacts, so the filter has to run *after* they arrive,
            // not before: filtering first and refreshing afterwards showed the list as it was
            // without them, which is what kept recent senders off the empty screen.
            if (config.useNewUi && searchString.isEmpty()) {
                fillSuggestedContacts { showFilteredContacts(searchString) }
            } else {
                showFilteredContacts(searchString)
            }

            val shortCodeWithLetters = isShortCodeWithLetters(searchString)
            binding.newConversationConfirm.beVisibleIf(searchString.isNotEmpty() && !shortCodeWithLetters)
            binding.newConversationConfirm.applyColorFilter(config.inputBarTextColor)
            binding.newConversationConfirm.setOnClickListener {
                if (shortCodeWithLetters) {
                    toast(R.string.invalid_short_code, length = Toast.LENGTH_LONG)
                } else {
                    launchThreadActivity(searchString, searchString)
                }
            }
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

                        val privateCursor = getMyContactsCursor(false, true)
                        privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

                        if (privateContacts.isNotEmpty()) {
                            allContacts.addAll(privateContacts)
                            allContacts.sortBy { it.name.lowercase() }
                        }

                        contactRecency = getContactRecency()

                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (config.useNewUi) {
                                fillSuggestedContacts {
                                    sortByRecency(allContacts, "")
                                    setupAdapter(allContacts)
                                }
                            } else {
                                sortByRecency(allContacts, "")
                                setupAdapter(allContacts)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupSearchEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.newConversationSearchContainer) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navigationHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = 16.getScaledPx() + Math.max(imeHeight, navigationHeight)
            }
            insets
        }
    }

    private fun setupAdapter(contacts: ArrayList<SimpleContact>) {
        val hasContacts = contacts.isNotEmpty()
        binding.contactsList.beVisibleIf(hasContacts)
        binding.noContactsPlaceholder.beVisibleIf(!hasContacts)
        binding.noContactsPlaceholder2.beVisibleIf(!hasContacts && !hasPermission(PERMISSION_READ_CONTACTS))

        val placeholderText = if (hasPermission(PERMISSION_READ_CONTACTS)) org.fossify.commons.R.string.no_contacts_found else org.fossify.commons.R.string.no_access_to_contacts
        binding.noContactsPlaceholder.text = getString(placeholderText)

        val currAdapter = binding.contactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(this, contacts, binding.contactsList) {
                val contact = it as SimpleContact
                if (contact.phoneNumbers.size == 1) {
                    launchThreadActivity(contact.phoneNumbers.first().normalizedNumber, contact.name)
                } else {
                    maybeShowNumberPickerDialog(contact.phoneNumbers) { number ->
                        launchThreadActivity(number.normalizedNumber, contact.name)
                    }
                }
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
            val privateCursor = getMyContactsCursor(false, true)
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            
            val suggestions = getSuggestedContacts(privateContacts)

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

    override fun getAppIconIDs() = arrayListOf(R.mipmap.ic_launcher)
    override fun getAppLauncherName() = getString(R.string.app_launcher_name)
    override fun getRepositoryName() = "Messages"

    private fun applyOutlines() = binding.apply {
        val density = resources.displayMetrics.density
        val isNewUi = config.useNewUi
        
        // Top Bar Outline (New Conversation)
        if (config.topBarOutline && isNewUi) {
            val r26 = 26f * density
            val thickness = config.topBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val outline = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
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
            val r_base = 100f * density
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.searchBarOutlineColor)
                cornerRadius = r_base
                setColor(Color.TRANSPARENT)
            }
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(drawable))
            layerDrawable.setLayerInset(0, 0, 0, 0, 0)
            binding.newConversationSearchContainer.foreground = layerDrawable
        } else {
            binding.newConversationSearchContainer.foreground = null
        }
    }
}
