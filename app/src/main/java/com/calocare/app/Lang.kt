package com.calocare.app

import com.calocare.app.data.Food
import com.calocare.app.data.FoodNames
import com.calocare.app.data.LogEntry
import java.util.Locale

/**
 * The app speaks Vietnamese on phones set to Vietnamese and English everywhere else. Android 13+ also lets
 * the user pick the app's language in Settings → Apps → CaloCare → Language (res/xml/locales_config.xml).
 * Read on every use, so a language change applies as soon as the screen is redrawn.
 */
object Lang {
    val en: Boolean get() = Locale.getDefault().language != "vi"

    /** The language AI answers should be written in. */
    val code: String get() = if (en) "en" else "vi"
}

/** The text in the user's language: [vi] on Vietnamese phones, [en] elsewhere. */
fun L(vi: String, en: String): String = if (Lang.en) en else vi

/** Built-in dishes have an English name; dishes users added keep the name they were given. */
val Food.label: String get() = if (Lang.en) FoodNames.en[id]?.first ?: name else name
val Food.servingLabel: String get() = if (Lang.en) FoodNames.en[id]?.second ?: serving else serving
val LogEntry.label: String get() = if (Lang.en) FoodNames.en[foodId]?.first ?: name else name
val LogEntry.servingLabel: String get() = if (Lang.en) FoodNames.en[foodId]?.second ?: serving else serving
