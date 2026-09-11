package com.texto.sms

import android.app.Application
import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.texto.sms.extensions.hasPermission
import com.texto.sms.extensions.PERMISSION_READ_CONTACTS
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.extensions.config
import com.texto.sms.extensions.rescheduleAllScheduledMessages
import com.texto.sms.helpers.AppThemes
import com.texto.sms.helpers.Config
import com.texto.sms.helpers.MessagingCache
import com.texto.sms.helpers.TextoLocale

class App : Application() {
    /**
     * Notifications, toasts posted from a receiver and anything else built off the
     * application context resolve their strings here, not on an activity. Without this the
     * app would speak the chosen language on screen and the phone's language in the
     * notification shade.
     */
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(TextoLocale.wrap(base))
    }

    override fun getPackageName(): String {
        return super.getPackageName()
    }

    override fun getApplicationInfo(): ApplicationInfo {
        return super.getApplicationInfo()
    }

    override fun onCreate() {
        super.onCreate()
        if (hasPermission(PERMISSION_READ_CONTACTS)) {
            listOf(
                ContactsContract.Contacts.CONTENT_URI,
                ContactsContract.Data.CONTENT_URI,
                ContactsContract.DisplayPhoto.CONTENT_URI
            ).forEach {
                try {
                    contentResolver.registerContentObserver(it, true, contactsObserver)
                } catch (_: Exception) {
                }
            }
        }

        config.appId = packageName
        config.appSideloadingStatus = 0
        config.hadThankYouInstalled = true
        config.appRunCount = 100 // Avoid early popups

        // Neon is the default look now. Setting only the appTheme default would name it in
        // settings without any of its colours being written, so a fresh install gets the
        // whole theme applied once.
        //
        // Existing installs are moved onto it once as well: the app was reskinned to the
        // Neon design wholesale, and leaving an upgrader on the Nocturne palette would show
        // them the new layout wearing the old colours -- which is exactly what happened when
        // Neon was added as a pickable theme but nothing ever selected it. Guarded by its
        // own flag rather than hasStoredAppTheme so it happens exactly once and a theme
        // picked afterwards sticks.
        //
        // nocturneRefreshApplied is left set on installs that already ran it, so nobody gets
        // moved twice; this flag supersedes it.
        //
        // The default is Neon's LIGHT variant now, not its dark one, and the surfaces open
        // solid rather than frosted. Both are carried by neonLightDefaultApplied so installs
        // that already ran the earlier Neon migration -- which set neonRefreshApplied, and so
        // would never enter this block again -- are moved across exactly once too.
        // Classic is the default now, carrying the palette in AppThemes: light bars, blue ink
        // on all three text settings, and an accent turned onto the blue of Samsung's own
        // messaging icon. Its own flag again, for the same reason as the two before it.
        // Nocturne is the default now, and it is the one the picker calls "Default". Every
        // flag below is already true on an install that has run the earlier migrations, so
        // this block is reached only by a fresh install: changing which theme it applies
        // moves what the app opens on without touching a theme anybody has chosen.
        if (!config.hasStoredAppTheme || !config.neonRefreshApplied ||
            !config.neonLightDefaultApplied || !config.classicDefaultApplied ||
            !config.neonLightRestored || !config.bubbleSidesSwapped
        ) {
            AppThemes.apply(config, AppThemes.byId(AppThemes.DEFAULT))
            config.glassOpacity = Config.DEFAULT_GLASS_OPACITY
            config.neonRefreshApplied = true
            config.nocturneRefreshApplied = true
            config.neonLightDefaultApplied = true
            config.classicDefaultApplied = true
            config.neonLightRestored = true
            config.bubbleSidesSwapped = true
        }

        // Filters used to carry a keyword list. The field is gone from the model and the
        // decoder ignores unknown keys, so old filters still load -- but the dead "keywords"
        // entry would otherwise sit in stored JSON for good. Reading and writing the list
        // back once re-encodes it through the current model and drops it.
        // The glass scale was recalibrated: the bars used to be capped below opaque, so
        // anyone who had already turned the slider up was sitting at a value that no longer
        // means what it did. Move them onto the new default once and leave later edits alone.
        if (!config.glassRecalibrated) {
            config.glassOpacity = Config.DEFAULT_GLASS_OPACITY
            config.glassRecalibrated = true
        }

        // Aurora was removed. An install still wearing it has an appTheme naming a skin that
        // no longer exists, so the picker would show it the wrong name while its stored
        // colours stayed Aurora's -- a theme nothing can select and nothing can leave.
        // Moved onto Neon, matching the variant they were on, and marked once so a theme
        // picked afterwards sticks.
        if (!config.auroraRetired) {
            val wasAurora = config.appTheme == AppThemes.RETIRED_AURORA ||
                config.appTheme == AppThemes.RETIRED_AURORA_LIGHT
            if (wasAurora) {
                val replacement = if (config.appTheme == AppThemes.RETIRED_AURORA) {
                    AppThemes.NEON
                } else {
                    AppThemes.NEON_LIGHT
                }
                AppThemes.apply(config, AppThemes.byId(replacement))
            }
            config.auroraRetired = true
        }

        if (config.customFilters.isNotEmpty()) {
            config.customFilters = config.customFilters
        }

        ensureBackgroundThread {
            rescheduleAllScheduledMessages()
        }
    }

    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            MessagingCache.namePhoto.evictAll()
            MessagingCache.participantsCache.evictAll()
        }
    }
}
