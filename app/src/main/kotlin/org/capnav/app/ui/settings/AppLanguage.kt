package org.capnav.app.ui.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import kotlin.system.exitProcess

/**
 * In-app language (FR / EN). Android 13+ uses the system per-app language API, which also shows
 * the choice in system settings. Older versions keep the tag in a plain preference (not sensitive)
 * applied in attachBaseContext, and restart the app to reload every resource.
 */
object AppLanguage {
    val supported = listOf("fr", "en")
    private const val PREFS = "cap_locale"
    private const val KEY = "lang"

    /** Language tag explicitly chosen in the app, or null if following the system. */
    fun chosen(context: Context): String? =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }

    /** Language currently displayed. */
    fun effective(context: Context): String =
        chosen(context) ?: context.resources.configuration.locales[0].language.takeIf { it in supported } ?: "en"

    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
            // The trip state is persisted, so restarting only resumes it paused.
            activity.startActivity(
                Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            activity.finishAffinity()
            exitProcess(0)
        }
    }

    /** Wraps [base] with the chosen locale on Android < 13. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
