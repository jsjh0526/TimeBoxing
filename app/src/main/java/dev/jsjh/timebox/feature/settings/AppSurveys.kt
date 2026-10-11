package dev.jsjh.timebox.feature.settings

internal object AppSurveys {
    const val FEATURES_URL = "https://forms.gle/aNrqJNmCrr2qaVr29"
    const val LANGUAGE_URL = "https://forms.gle/3GKB9VFp9btoddve6"

    fun open(url: String, openUri: (String) -> Unit): Boolean =
        runCatching { openUri(url) }.isSuccess
}
