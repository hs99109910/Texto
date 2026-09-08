package com.texto.sms.helpers

import android.content.Context
import android.graphics.Typeface

/**
 * Persian typefaces bundled as assets instead of res/font, so a missing file degrades
 * to the system font at runtime rather than breaking the build.
 *
 * Vazirmatn ships with the app (SIL OFL, see assets/fonts/Vazirmatn-OFL.txt) and is the
 * only family that supplies a real bold cut; the rest fall back to the synthetic bold
 * Android derives from their single regular file.
 */
object TextoFonts {

    const val FONT_B_KOODAK = 6
    const val FONT_B_NAZANIN = 7
    const val FONT_B_KAMRAN = 8
    const val FONT_IRAN_NASTALIQ = 9
    const val FONT_VAZIRMATN = 10

    private const val ASSET_DIR = "fonts"

    /** Every accepted file name per font, first match wins. */
    private val assetNames = mapOf(
        FONT_B_KOODAK to listOf("BKoodak.ttf", "B Koodak.ttf", "b_koodak.ttf", "BKoodak.otf"),
        FONT_B_NAZANIN to listOf("BNazanin.ttf", "B Nazanin.ttf", "b_nazanin.ttf", "BNazanin.otf"),
        FONT_B_KAMRAN to listOf("BKamran.ttf", "B Kamran.ttf", "b_kamran.ttf", "BKamran.otf"),
        FONT_IRAN_NASTALIQ to listOf(
            "IranNastaliq.ttf", "Iran Nastaliq.ttf", "iran_nastaliq.ttf", "IranNastaliq.otf"
        ),
        FONT_VAZIRMATN to listOf("Vazirmatn-Regular.ttf", "Vazirmatn.ttf", "vazirmatn.ttf")
    )

    /** Real bold cuts, where one is shipped. Missing entries fall back to synthetic bold. */
    private val boldAssetNames = mapOf(
        FONT_VAZIRMATN to listOf("Vazirmatn-Bold.ttf", "vazirmatn-bold.ttf")
    )

    private val persianNames = mapOf(
        FONT_VAZIRMATN to "وزیرمتن",
        FONT_B_KOODAK to "ب کودک",
        FONT_B_NAZANIN to "ب نازنین",
        FONT_B_KAMRAN to "ب کامران",
        FONT_IRAN_NASTALIQ to "ایران نستعلیق"
    )

    /** The same families spelled the way an English UI names a typeface. */
    private val latinNames = mapOf(
        FONT_VAZIRMATN to "Vazirmatn",
        FONT_B_KOODAK to "B Koodak",
        FONT_B_NAZANIN to "B Nazanin",
        FONT_B_KAMRAN to "B Kamran",
        FONT_IRAN_NASTALIQ to "Iran Nastaliq"
    )

    /**
     * A typeface name is not a resource: the map is keyed by the font id the setting stores,
     * so the two spellings live here and the language picks between them.
     */
    val displayNames: Map<Int, String>
        get() = if (TextoLocale.isPersian) persianNames else latinNames

    private val cache = HashMap<String, Typeface?>()

    fun isPersianFont(id: Int) = assetNames.containsKey(id)

    /** Returns null when the font file has not been dropped into assets/fonts yet. */
    fun getTypeface(context: Context, id: Int): Typeface? = load(context, id, bold = false)

    /**
     * Resolves [id] against [style], preferring a shipped bold file over Android's synthetic
     * bold. Always returns something usable: the regular cut when no bold file exists, and
     * null only when the family itself is missing (callers then get the system font).
     */
    fun create(context: Context, id: Int, style: Int): Typeface? {
        val wantsBold = style == Typeface.BOLD || style == Typeface.BOLD_ITALIC
        val base = (if (wantsBold) load(context, id, bold = true) else null)
            ?: load(context, id, bold = false)
            ?: return null

        // The real bold cut already carries the weight, so only ask Android to add italics.
        val hasRealBold = wantsBold && load(context, id, bold = true) != null
        val effectiveStyle = if (!hasRealBold) {
            style
        } else if (style == Typeface.BOLD_ITALIC) {
            Typeface.ITALIC
        } else {
            Typeface.NORMAL
        }

        return Typeface.create(base, effectiveStyle)
    }

    private fun load(context: Context, id: Int, bold: Boolean): Typeface? {
        if (!isPersianFont(id)) return null

        val cacheKey = "$id/${if (bold) "bold" else "regular"}"
        if (cache.containsKey(cacheKey)) return cache[cacheKey]

        val candidates = if (bold) boldAssetNames[id] else assetNames[id]
        if (candidates == null) {
            cache[cacheKey] = null
            return null
        }

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
        val fileName = candidates.firstNotNullOfOrNull { candidate ->
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

        cache[cacheKey] = typeface
        return typeface
    }

    fun isInstalled(context: Context, id: Int) = getTypeface(context, id) != null
}
