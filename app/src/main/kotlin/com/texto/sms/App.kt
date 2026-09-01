package com.texto.sms

import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.ensureBackgroundThread
import com.texto.sms.extensions.config
import com.texto.sms.extensions.rescheduleAllScheduledMessages
import com.texto.sms.helpers.AppThemes
import com.texto.sms.helpers.Config
import com.texto.sms.helpers.MessagingCache
import com.texto.sms.helpers.TextoLauncherIcon

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun getPackageName(): String {
        return super.getPackageName()
    }

    override fun getApplicationInfo(): ApplicationInfo {
        return super.getApplicationInfo()
    }

    /**
     * Swaps the launcher icon to the tonality's rotation, but only once the app has left the
     * foreground.
     *
     * Doing it the moment the strip is released closed the app. The task is rooted at the
     * activity-alias it was launched from, so disabling that alias to enable another one
     * pulls the task's own root out from under it and Android finishes the task.
     * DONT_KILL_APP keeps the process, which is a different thing entirely.
     *
     * Counting started activities rather than pulling in ProcessLifecycleOwner: this is the
     * only place the app needs the signal, and the count is exact for what it is asked.
     */
    private var startedActivities = 0

    private fun watchForBackground() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivities++
            }

            override fun onActivityStopped(activity: android.app.Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    startedActivities = 0
                    // Cheap and self-checking: apply() returns immediately when the alias
                    // already matches, which is every time but the first after a change.
                    TextoLauncherIcon.apply(this@App, config.accentHueShift)
                }
            }

            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
    }

    override fun onCreate() {
        super.onCreate()
        watchForBackground()
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
        if (!config.hasStoredAppTheme || !config.neonRefreshApplied ||
            !config.neonLightDefaultApplied
        ) {
            AppThemes.apply(config, AppThemes.byId(AppThemes.NEON_LIGHT))
            config.glassOpacity = Config.DEFAULT_GLASS_OPACITY
            config.neonRefreshApplied = true
            config.nocturneRefreshApplied = true
            config.neonLightDefaultApplied = true
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
