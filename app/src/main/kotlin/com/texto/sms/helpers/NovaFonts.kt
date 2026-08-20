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
    const val FONT_IRAN_NASTALIQ = 9

    private const val ASSET_DIR = "fonts"

    /** Every accepted file name per font, first match wins. */
    private val assetNames = mapOf(
        FONT_B_KOODAK to listOf("BKoodak.ttf", "B Koodak.ttf", "b_koodak.ttf", "BKoodak.otf"),
        FONT_B_NAZANIN to listOf("BNazanin.ttf", "B Nazanin.ttf", "b_nazanin.ttf", "BNazanin.otf"),
        FONT_IRAN_NASTALIQ to listOf(
            "IranNastaliq.ttf", "Iran Nastaliq.ttf", "iran_nastaliq.ttf", "IranNastaliq.otf"
        )
    )

    val displayNames = mapOf(
        FONT_B_KOODAK to "B Koodak (ب کودک)",
        FONT_B_NAZANIN to "B Nazanin (ب نازنین)",
        FONT_IRAN_NASTALIQ to "Iran Nastaliq (ایران نستعلیق)"
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

        val fileName = assetNames[id]?.firstOrNull { candidate ->
            available.any { it.equals(candidate, ignoreCase = true) }
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
