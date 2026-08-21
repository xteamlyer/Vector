package org.matrix.vector.ui.locale

import java.util.Locale

/** Someone who translated an app, and where to find them if they want to be found. */
data class Translator(val name: String, val url: String? = null)

/**
 * The three languages whose resource folder is spelled with the code Java retired.
 *
 * A folder is `values-in`, `values-iw`, `values-ji`; the BCP-47 tag for the same language is `id`,
 * `he`, `yi`. Which of the two a [Locale] answers with depends on the platform -- Android's
 * `getLanguage()` keeps the retired codes, a desktop JVM normalises them away, and
 * `toLanguageTag()` gives the modern one on both -- so neither spelling can be assumed and the pair
 * is written out instead.
 */
private val RETIRED_CODES =
    mapOf("id" to "in", "in" to "id", "he" to "iw", "iw" to "he", "yi" to "ji", "ji" to "yi")

/**
 * The credits for a language, whichever way a contributor keyed them.
 *
 * Shared because both managers credit their translators from the same sheet, and because this
 * matching is the part that is easy to get subtly wrong -- a credit that silently fails to match is
 * indistinguishable from one nobody added, so nobody notices it is broken.
 */
fun Map<String, List<Translator>>.forLocale(locale: Locale): List<Translator> {
    val tag = locale.toLanguageTag()
    val bare = tag.substringBefore('-')
    val candidates =
        listOf(tag, locale.language, bare) +
            listOfNotNull(RETIRED_CODES[locale.language], RETIRED_CODES[bare])
    return candidates.firstNotNullOfOrNull { this[it] } ?: emptyList()
}
