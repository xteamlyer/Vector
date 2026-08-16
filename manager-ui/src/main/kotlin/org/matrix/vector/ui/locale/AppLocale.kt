package org.matrix.vector.ui.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.text.TextUtils
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

/**
 * An in-app language chosen in the app rather than by the system, applied in composition.
 *
 * A manager may not be able to use the platform's per-app language API — Vector runs inside
 * `com.android.shell`, whose language is not its to set — so the override is applied by providing a
 * configuration carrying the chosen locale down the tree along with a context created from it, which
 * is what `stringResource` reads. Every string below re-resolves the moment the choice changes, with
 * no activity restart. An empty tag means "follow the system", and is the default.
 *
 * Which language is chosen comes from the host's [LocaleController], so this reaches into no app's
 * settings.
 */
@Composable
fun LocalizedContent(controller: LocaleController, content: @Composable () -> Unit) {
    val tag by controller.appLocale.collectAsStateWithLifecycle()
    val base = LocalConfiguration.current
    val context = LocalContext.current

    val localized =
        remember(tag, base) {
            if (tag.isBlank()) null
            else Configuration(base).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        }

    if (localized == null) {
        // No override: leave the tree exactly as the system built it, so the common case costs
        // nothing.
        content()
        return
    }

    val localizedContext = remember(localized) { LocalizedContext(context, localized) }

    CompositionLocalProvider(
        LocalConfiguration provides localized,
        LocalContext provides localizedContext,
        LocalLayoutDirection provides localized.layoutDirection(),
    ) {
        // The whole app is about to say something different, and a cut makes that read as a glitch;
        // crossfading makes the change look like the thing the user just asked for.
        AnimatedContent(
            targetState = tag,
            transitionSpec = {
                (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.985f))
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "language",
        ) { _ ->
            content()
        }
    }
}

/**
 * The same override, for anything drawn in its own window (a bottom sheet, a dialog, a dropdown).
 *
 * Each of those gets its own compose view that re-provides `LocalContext`/`LocalConfiguration` from
 * the window's context, silently overwriting what [LocalizedContent] provided — so the override has
 * to be re-established inside the popup. Wire this into `LocalDialogLocalizer` from the host so every
 * shared sheet localises too.
 */
@Composable
fun LocalizedOverlay(controller: LocaleController, content: @Composable () -> Unit) {
    val tag by controller.appLocale.collectAsStateWithLifecycle()
    val base = LocalConfiguration.current
    val context = LocalContext.current

    if (tag.isBlank()) {
        content()
        return
    }

    val localized =
        remember(tag, base) {
            Configuration(base).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        }
    val localizedContext = remember(localized) { LocalizedContext(context, localized) }

    CompositionLocalProvider(
        LocalConfiguration provides localized,
        LocalContext provides localizedContext,
        LocalLayoutDirection provides localized.layoutDirection(),
        content = content,
    )
}

private fun Configuration.layoutDirection(): LayoutDirection {
    val locale = locales.takeIf { it.size() > 0 }?.get(0) ?: Locale.getDefault()
    return if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
}

/**
 * A context that answers with the chosen language but is still the activity — wrapping rather than
 * replacing keeps the activity-lookup chain intact, which `rememberLauncherForActivityResult` walks.
 */
private class LocalizedContext(base: Context, config: Configuration) : ContextWrapper(base) {
    private val localized: Resources = base.createConfigurationContext(config).resources

    override fun getResources(): Resources = localized
}

/** The languages the host is translated into, as [Locale]s, sorted by their own display name. */
fun availableLocales(controller: LocaleController): List<Locale> =
    controller.availableTags
        .map { Locale.forLanguageTag(it) }
        .sortedBy { it.getDisplayName(it).lowercase(it) }

/** The language's name in itself — "Deutsch", not "German". A reader looking for their own. */
fun Locale.nativeName(): String =
    getDisplayName(this).replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(this) else it.toString()
    }

/** The locale the composition is actually using, for date/number formatting under the override. */
@Composable
fun currentLocale(): Locale =
    LocalConfiguration.current.locales.takeIf { it.size() > 0 }?.get(0) ?: Locale.getDefault()
