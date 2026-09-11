package com.texto.sms.activities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.provider.Settings

import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.SimpleContactsHelper
import com.texto.sms.extensions.ensureBackgroundThread
import org.fossify.commons.models.SimpleContact
import com.texto.sms.adapters.ContactsAdapter
import com.texto.sms.databinding.ActivityConversationDetailsBinding
import com.texto.sms.dialogs.RenameConversationDialog
import com.texto.sms.extensions.*
import com.texto.sms.helpers.THREAD_ID
import com.texto.sms.models.Conversation
import android.util.TypedValue
import android.view.View
import com.texto.sms.helpers.TextoAvatars
import com.texto.sms.helpers.TextoGlass
import com.texto.sms.extensions.copyToClipboard
import com.texto.sms.extensions.darkenColor
import com.texto.sms.extensions.getContrastColor
import com.texto.sms.extensions.hideKeyboard
import com.texto.sms.extensions.notificationManager
import com.texto.sms.extensions.showKeyboard
import com.texto.sms.extensions.usableScreenSize
import com.texto.sms.extensions.applyColorFilter
import com.texto.sms.extensions.beGone
import com.texto.sms.extensions.beVisible
import com.texto.sms.extensions.beGoneIf
import com.texto.sms.extensions.beVisibleIf
import com.texto.sms.extensions.toast
import com.texto.sms.extensions.showErrorToast
import com.texto.sms.extensions.viewBinding

class ConversationDetailsActivity : SimpleActivity() {

    private var threadId: Long = 0L
    private var conversation: Conversation? = null
    private lateinit var participants: ArrayList<SimpleContact>

    private val binding by viewBinding(ActivityConversationDetailsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomSystem = listOf(binding.conversationDetailsNestedScrollview))

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
            participants = if (conversation != null && conversation!!.isScheduled) {
                val message = messagesDB.getThreadMessages(conversation!!.threadId).firstOrNull()
                message?.participants ?: arrayListOf()
            } else {
                getThreadParticipants(threadId, null)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setupHeroSection()
                setupRenaming()
                setupParticipants()
                setupCustomNotifications()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.conversationDetailsAppbar, NavigationIcon.Arrow)
        applyCustomColors()
        styleDetails()
    }

    /**
     * Paints the screen from the live theme.
     *
     * This one was never brought onto the new design: its three rows were filled with
     * `pill_background_small`, whose solid colour is `colorPrimary` -- the Fossify red -- so
     * they came up as red bars in whatever theme the user had picked, with white-on-red text
     * and a 6dp lift the rest of the app had already dropped. Everything here now reads from
     * Config, the way every other screen does.
     */
    private fun styleDetails() = binding.apply {
        val density = resources.displayMetrics.density
        val ink = config.mainTextColor
        val cardRadius = config.cardCornerRadiusDp * density

        // The hero card: a flat wash of the card colour behind the divider hairline, not the
        // lighten/darken gradient that made every surface in the app look embossed.
        detailsHeroGradient.background = TextoGlass.panel(
            tint = config.recentColor,
            cornerRadius = cardRadius,
            opacity = 0.68f,
            strokeWidthPx = 1.getScaledPx(),
            rimAlpha = 0.10f,
            sheenAlpha = 0f
        )
        detailsHeroSection.elevation = 0f
        detailsHeroSection.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        detailsHeroName.setTextColor(ink)
        detailsHeroName.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.3f))
        TextoAvatars.clipToSquircle(detailsHeroImage)

        // The rows are the same glass capsule the filter chips and the composer are.
        val rowRadius = 100f * density
        fun row(view: View) {
            view.background = TextoGlass.bar(
                tint = config.recentColor,
                cornerRadius = rowRadius,
                opacity = 0.5f,
                strokeWidthPx = 1.getScaledPx(),
                rimAlpha = 0.18f
            )
            view.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            view.elevation = 0f
        }
        row(detailsRenamePill)
        row(detailsNotificationsPill)
        row(detailsCustomizePill)

        listOf(conversationNameLabel, notificationsLabel, detailsCustomizeLabel).forEach {
            it.setTextColor(ink)
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.92f))
        }
        listOf(detailsRenameIcon, detailsCustomizeIcon).forEach {
            it.applyColorFilter(ink.withAlpha(0.68f))
        }

        // The heading is the one accented thing on the screen, the way the thread header's
        // status line is: it reads as a section marker rather than a second title.
        membersHeadingLabel.setTextColor(config.accentGradientStart)
        membersHeadingLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.0f))

        conversationDetailsToolbarTitle.setTextColor(config.topBarTextColor)
        conversationDetailsToolbarTitle.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.35f)
        )

        val accent = config.accentGradientStart
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        customNotificationsSwitch.trackTintList = android.content.res.ColorStateList(
            states, intArrayOf(accent.withAlpha(0.45f), ink.withAlpha(0.16f))
        )
        customNotificationsSwitch.thumbTintList = android.content.res.ColorStateList(
            states, intArrayOf(accent, ink.withAlpha(0.62f))
        )
    }

    private fun setupHeroSection() {
        val title = conversation?.title ?: participants.getThreadTitle()
        binding.detailsHeroName.text = title
        
        TextoAvatars.clipToSquircle(binding.detailsHeroImage)
        SimpleContactsHelper(this).loadContactImage(
            path = participants.firstOrNull()?.photoUri ?: "",
            imageView = binding.detailsHeroImage,
            placeholderName = title,
            placeholderImage = TextoAvatars.letterAvatar(this, title)
        )
    }

    private fun setupRenaming() {
        binding.detailsRenamePill.setOnClickListener {
            RenameConversationDialog(this, conversation!!) { title ->
                binding.detailsHeroName.text = title
                ensureBackgroundThread {
                    conversation = renameConversation(conversation!!, newTitle = title)
                }
            }
        }
    }

    private fun setupCustomNotifications() {
        binding.apply {
            customNotificationsSwitch.isChecked = config.customNotifications.contains(threadId.toString())
            detailsCustomizePill.beVisibleIf(customNotificationsSwitch.isChecked)

            detailsNotificationsPill.setOnClickListener {
                customNotificationsSwitch.toggle()
                if (customNotificationsSwitch.isChecked) {
                    detailsCustomizePill.beVisible()
                    config.addCustomNotificationsByThreadId(threadId)
                    createNotificationChannel()
                } else {
                    detailsCustomizePill.beGone()
                    config.removeCustomNotificationsByThreadId(threadId)
                    removeNotificationChannel()
                }
            }

            detailsCustomizePill.setOnClickListener {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, threadId.toString())
                    startActivity(this)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val name = conversation?.title
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        NotificationChannel(threadId.toString(), name, NotificationManager.IMPORTANCE_HIGH).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                audioAttributes
            )
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun removeNotificationChannel() {
        notificationManager.deleteNotificationChannel(threadId.toString())
    }

    private fun setupParticipants() {
        // Force 2-column grid layout for members
        binding.participantsGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        
        val adapter = ContactsAdapter(this, participants, binding.participantsGrid) {
            val contact = it as SimpleContact
            val address = contact.phoneNumbers.first().normalizedNumber
            getContactFromAddress(address) { simpleContact ->
                if (simpleContact != null) {
                    startContactDetailsIntent(simpleContact)
                }
            }
        }
        adapter.setUseModernPills(true)
        binding.participantsGrid.adapter = adapter
    }
}
