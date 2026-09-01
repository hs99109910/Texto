package com.texto.sms.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Keeps the launcher icon on the tonality the user picked.
 *
 * The launcher renders an app's icon in its OWN process, from static resources in the
 * package. Nothing the app runs at launch or afterwards can recolour it: there is no runtime
 * tint, `setTaskDescription` only reaches the recents screen, and the adaptive icon's
 * monochrome layer is tinted by the system from the wallpaper rather than by us.
 *
 * So the twelve rotations are pre rendered and declared as [activity-alias] entries in the
 * manifest, exactly one of them enabled. Switching the enabled alias is the only supported
 * way to change an app's icon, and it is what this does.
 *
 * The cost is real and worth knowing: most launchers treat the swap as the old entry going
 * away and a new one arriving, so a home screen shortcut can lose its place. That is why the
 * new alias is enabled BEFORE the old ones are disabled : with every alias disabled, even for
 * an instant, the app has no launcher entry at all.
 */
object TextoLauncherIcon {

    /** Rotations available, evenly spaced around the wheel. */
    const val STEPS = 12

    private const val DEGREES_PER_STEP = 360 / STEPS

    /**
     * Built by hand rather than with a format string. The app's locale is hard locked to
     * fa-IR, under which `"%02d".format(n)` emits Persian digits, so this asked the package
     * manager for a component called `IconH۰۷` and it threw: the component really does not
     * exist under that name. A class name is not display text and must never be shaped.
     */
    private fun aliasName(index: Int) =
        ".IconH" + (if (index < 10) "0" else "") + index.toString()

    /** The alias nearest [shiftDegrees], which may be any angle the strip can produce. */
    fun indexFor(shiftDegrees: Int): Int =
        (Math.round(shiftDegrees / DEGREES_PER_STEP.toFloat()).mod(STEPS))

    /**
     * Points the launcher at the rotation nearest [shiftDegrees]. Does nothing when that
     * alias is already the enabled one, so this is safe to call on every resume: a swap the
     * user cannot see is still a swap the launcher would animate.
     */
    fun apply(context: Context, shiftDegrees: Int) {
        val pm = context.packageManager
        val pkg = context.packageName
        val wanted = indexFor(shiftDegrees)
        val wantedComponent = ComponentName(pkg, pkg + aliasName(wanted))

        val already = pm.getComponentEnabledSetting(wantedComponent)
        // A component nobody has touched reports DEFAULT rather than ENABLED, so a fresh
        // install sitting on the untouched hue is already correct and must not be rewritten :
        // doing so would make the launcher drop and re-add the icon right after install.
        val untouchedAndCorrect =
            wanted == 0 && already == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        if (already == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || untouchedAndCorrect) return

        // Enable first. Disabling every alias before enabling one would drop the app out of
        // the launcher entirely for as long as the two calls take.
        pm.setComponentEnabledSetting(
            wantedComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        for (i in 0 until STEPS) {
            if (i == wanted) continue
            val other = ComponentName(pkg, pkg + aliasName(i))
            if (pm.getComponentEnabledSetting(other) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) continue
            pm.setComponentEnabledSetting(
                other,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
