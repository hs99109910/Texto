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
import com.texto.sms.helpers.MessagingCache

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

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

        // Nocturne is the default look. Setting only the appTheme default would name it in
        // settings without any of its colours being written, so a fresh install gets the
        // whole theme applied once.
        //
        // Existing installs are moved onto it once as well: the app was reskinned to the
        // Nocturne design wholesale, and leaving an upgrader on the old Aurora palette would
        // show them the new layout wearing the old colours. Guarded by its own flag rather
        // than hasStoredAppTheme so it happens exactly once and a theme picked afterwards
        // sticks.
        if (!config.hasStoredAppTheme || !config.nocturneRefreshApplied) {
            AppThemes.apply(config, AppThemes.byId(AppThemes.NOCTURNE))
            config.nocturneRefreshApplied = true
        }

        // Filters used to carry a keyword list. The field is gone from the model and the
        // decoder ignores unknown keys, so old filters still load -- but the dead "keywords"
        // entry would otherwise sit in stored JSON for good. Reading and writing the list
        // back once re-encodes it through the current model and drops it.
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
