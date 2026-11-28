package com.owl.tracker.i18n

import android.content.Context
import java.util.Locale

/**
 * The app's languages. Text in the code is English and is its own key: `tr("Saved {0} · {1}", amount, name)`. A
 * language is one file, `assets/i18n/<code>.json`, mapping each English text to its translation; anything missing
 * there shows in English. `{0}`, `{1}`… are filled in after translating, so a translation can put them in any order.
 *
 * The language lives in the "look" preferences too, so it is known before the database opens (first frame,
 * widgets, notifications). Changing it reloads the table and recreates the screen.
 */
object I18n {
    /** Languages the app offers: code, its own name, the English name. */
    data class Language(val code: String, val ownName: String, val englishName: String)

    val languages = listOf(
        Language("en", "English", "English"),
        Language("hi", "हिंदी", "Hindi"),
        Language("mr", "मराठी", "Marathi"),
        Language("bn", "বাংলা", "Bengali"),
        Language("ta", "தமிழ்", "Tamil"),
        Language("te", "తెలుగు", "Telugu"),
        Language("kn", "ಕನ್ನಡ", "Kannada"),
    )

    @Volatile var lang: String = "en"
        private set

    @Volatile private var table: Map<String, String> = emptyMap()

    /** Dates, month and day names follow the language; digits stay 0–9 (Latin), as Indian apps and bank SMS show them. */
    val locale: Locale
        get() = if (lang == "en") Locale.ENGLISH else Locale.Builder().setLanguage(lang).setRegion("IN").setExtension('u', "nu-latn").build()

    fun prefsLanguage(context: Context): String =
        context.getSharedPreferences("look", Context.MODE_PRIVATE).getString("language", null)?.takeIf { c -> languages.any { it.code == c } } ?: "en"

    /** Loads [code]'s table (or the saved language's, when null). English needs none. */
    fun load(context: Context, code: String? = null) {
        val c = code ?: prefsLanguage(context)
        // commit, not apply: switching restarts the process straight away, which would drop an unwritten apply()
        if (code != null) context.getSharedPreferences("look", Context.MODE_PRIVATE).edit().putString("language", c).commit()
        table = if (c == "en") emptyMap() else runCatching {
            context.assets.open("i18n/$c.json").bufferedReader().use { parse(it.readText()) }
        }.getOrDefault(emptyMap())
        lang = c
    }

    /** For tests: a table given directly. */
    internal fun useForTests(code: String, entries: Map<String, String>) { table = entries; lang = code }

    fun tr(en: String, args: Array<out Any?>): String {
        val text = table[en] ?: en
        return if (args.isEmpty()) text else fill(text, args)
    }

    /** Puts `{0}`, `{1}`… in their places; a placeholder with no argument stays as it is. */
    fun fill(text: String, args: Array<out Any?>): String {
        if ('{' !in text) return text
        val out = StringBuilder(text.length + 16)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '{') {
                val close = text.indexOf('}', i + 1)
                val n = if (close > i + 1 && close - i <= 3) text.substring(i + 1, close).toIntOrNull() else null
                if (n != null && n in args.indices) { out.append(args[n].toString()); i = close + 1; continue }
            }
            out.append(ch); i++
        }
        return out.toString()
    }

    /** The language files: one JSON object of "English": "translation". Empty translations are left out. */
    fun parse(json: String): Map<String, String> {
        val o = org.json.JSONObject(json)
        val m = HashMap<String, String>(o.length() * 2)
        o.keys().forEach { k -> val v = o.optString(k); if (v.isNotBlank() && !k.startsWith("//")) m[k] = v }
        return m
    }
}

/** The text in the current language. English is the key; see [I18n]. */
fun tr(en: String, vararg args: Any?): String = I18n.tr(en, args)

/** A count with its word: `trn(n, "{0} payment", "{0} payments")`. Each form is its own text to translate. */
fun trn(n: Int, one: String, many: String): String = if (n == 1) I18n.tr(one, arrayOf(n)) else I18n.tr(many, arrayOf(n))
