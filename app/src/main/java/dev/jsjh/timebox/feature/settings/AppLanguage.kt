package dev.jsjh.timebox.feature.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import androidx.core.os.ConfigurationCompat
import java.util.Locale

object AppLanguage {
    private const val PREFS_NAME = "app_language"
    private const val KEY_LANGUAGE_TAG = "language_tag"
    val supportedLanguageCodes = setOf("en", "ko", "es", "hi", "fil", "zu")

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val languageTag = savedLanguageTag(base)
        if (languageTag.isBlank()) return base

        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(base.resources.configuration)
        Locale.setDefault(locale)
        configuration.setLocales(LocaleList(locale))
        return ContextWrapper(base.createConfigurationContext(configuration))
    }

    fun currentLanguageTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.toLanguageTags()
                .orEmpty()
        }
        return savedLanguageTag(context)
    }

    fun appliedLanguageCode(context: Context): String {
        val language = ConfigurationCompat.getLocales(context.resources.configuration)
            .get(0)
            ?.language
            .orEmpty()
            .lowercase()
        return language.takeIf { it in supportedLanguageCodes } ?: "en"
    }

    fun setLanguage(activity: Activity, languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = activity.getSystemService(LocaleManager::class.java) ?: return
            val nextLocales = if (languageTag.isBlank()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(languageTag)
            }
            if (localeManager.applicationLocales.toLanguageTags() != nextLocales.toLanguageTags()) {
                localeManager.applicationLocales = nextLocales
            }
            return
        }

        activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_LANGUAGE_TAG, languageTag) }
        activity.recreate()
    }

    private fun savedLanguageTag(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, "")
            .orEmpty()
}
