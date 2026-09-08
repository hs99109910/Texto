package com.texto.sms.helpers

import android.content.Context
import android.content.res.Configuration
import androidx.core.os.ConfigurationCompat
import org.fossify.commons.helpers.PREFS_KEY
import java.util.Locale

/**
 * The app's language, and the single place that decides it.
 *
 * The locale used to be hard-locked to fa-IR in every activity, which is why the whole
 * string table was Persian and lived in `values/`. It is now a setting with three states:
 * follow the phone, always Persian, always English. Persian text has moved to `values-fa/`
 * and `values/` carries the English original, so the choice is made by handing Android the
 * right locale rather than by swapping strings by hand.
 *
 * [isPersian] is the read-only mirror the formatting helpers consult. Digits, the calendar
 * and the weekday names are not resources and cannot be resolved by the resource system, so
 * they follow this flag instead. It is refreshed by [wrap], which every context the app
 * builds passes through.
 */
object TextoLocale {

    const val SYSTEM = 0
    const val PERSIAN = 1
    const val ENGLISH = 2

    val PERSIAN_LOCALE: Locale = Locale("fa", "IR")
    val ENGLISH_LOCALE: Locale = Locale.US

    /**
     * True while the app is showing Persian. Written on every [wrap] and read from
     * formatting code that has no Context to hand: `String.toUiDigits()` is a plain
     * extension called from a dozen places, and threading a Context through all of them to
     * answer one boolean would be worse than a flag set where language is decided.
     */
    @Volatile
    var isPersian: Boolean = true
        private set

    /** The language the phone itself is set to, mapped onto the two the app speaks. */
    private fun systemLocale(base: Configuration): Locale {
        val system = ConfigurationCompat.getLocales(base).get(0) ?: Locale.getDefault()
        return if (system.language == "fa" || system.language == "pes") {
            PERSIAN_LOCALE
        } else {
            ENGLISH_LOCALE
        }
    }

    fun localeFor(language: Int, base: Configuration): Locale = when (language) {
        PERSIAN -> PERSIAN_LOCALE
        ENGLISH -> ENGLISH_LOCALE
        else -> systemLocale(base)
    }

    /** The stored choice, without going through Config: see [wrap]. */
    fun storedLanguage(context: Context): Int = try {
        context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
            .getInt(APP_LANGUAGE, SYSTEM)
    } catch (_: Exception) {
        SYSTEM
    }

    /**
     * Returns [context] re-based on the chosen locale, and records which one that was.
     *
     * Called from `attachBaseContext` in both the Application and every activity: a
     * notification built off the application context has to speak the same language as the
     * screen that produced it.
     *
     * The preference is read from the file directly rather than through `Config`, because
     * this runs before the Application has an `applicationContext` and
     * `Config.newInstance(applicationContext)` would dereference null there.
     */
    fun wrap(context: Context): Context {
        val locale = localeFor(storedLanguage(context), context.resources.configuration)
        isPersian = locale.language == "fa"
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }
}
