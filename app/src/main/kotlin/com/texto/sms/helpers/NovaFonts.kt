package com.texto.sms.helpers

import android.content.Context
import android.graphics.Typeface

/**
 * Persian typefaces bundled as assets instead of res/font, so a missing file degrades
 * to the system font at runtime rather than breaking the build.
 */
object NovaFonts {

    const val FONT_B_KOODAK = 6
    const val FONT_B_NAZANIN = 7
    const val FONT_B_KAMRAN = 8
    const val FONT_IRAN_NASTALIQ = 9

    private const val ASSET_DIR = "fonts"

    /** Every accepted file name per font, first match wins. */
    private val assetNames = mapOf(
        FONT_B_KOODAK to listOf("BKoodak.ttf", "B Koodak.ttf", "b_koodak.ttf", "BKoodak.otf"),
        FONT_B_NAZANIN to listOf("BNazanin.ttf", "B Nazanin.ttf", "b_nazanin.ttf", "BNazanin.otf"),
        FONT_B_KAMRAN to listOf("BKamran.ttf", "B Kamran.ttf", "b_kamran.ttf", "BKamran.otf"),
        FONT_IRAN_NASTALIQ to listOf(
            "IranNastaliq.ttf", "Iran Nastaliq.ttf", "iran_nastaliq.ttf", "IranNastaliq.otf"
        )
    )

    val displayNames = mapOf(
        FONT_B_KOODAK to "ب کودک",
        FONT_B_NAZANIN to "ب نازنین",
        FONT_B_KAMRAN to "ب کامران",
        FONT_IRAN_NASTALIQ to "ایران نستعلیق"
    )

    private val cache = HashMap<Int, Typeface?>()

    fun isPersianFont(id: Int) = assetNames.containsKey(id)

    /** Returns null when the font file has not been dropped into assets/fonts yet. */
    fun getTypeface(context: Context, id: Int): Typeface? {
        if (!isPersianFont(id)) return null
        if (cache.containsKey(id)) return cache[id]

        val available = try {
            context.assets.list(ASSET_DIR)?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        // Match case-insensitively but open the name exactly as it appears in assets:
        // AssetManager itself is case-sensitive, so resolving "BNazanin.TTF" and then asking
        // for the candidate spelling "BNazanin.ttf" threw and silently fell back to the
        // system font -- which is why B Nazanin never applied while the lower-case
        // BKoodak.ttf and IranNastaliq.ttf did.
        val fileName = assetNames[id]?.firstNotNullOfOrNull { candidate ->
            available.firstOrNull { it.equals(candidate, ignoreCase = true) }
        }

        val typeface = if (fileName == null) {
            null
        } else {
            try {
                Typeface.createFromAsset(context.assets, "$ASSET_DIR/$fileName")
            } catch (_: Exception) {
                null
            }
        }

        cache[id] = typeface
        return typeface
    }

    fun isInstalled(context: Context, id: Int) = getTypeface(context, id) != null
}
