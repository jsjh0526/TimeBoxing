package dev.jsjh.timebox.ads

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object AdsConsentManager {
    var canRequestAds by mutableStateOf(false)
        private set

    var privacyOptionsRequired by mutableStateOf(false)
        private set

    fun gatherConsent(activity: Activity, onCanRequestAds: () -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                updateConsentState(consentInformation)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateConsentState(consentInformation)
                    if (canRequestAds) onCanRequestAds()
                }
            },
            {
                updateConsentState(consentInformation)
                if (canRequestAds) onCanRequestAds()
            }
        )

        updateConsentState(consentInformation)
        if (canRequestAds) onCanRequestAds()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            updateConsentState(UserMessagingPlatform.getConsentInformation(activity))
        }
    }

    private fun updateConsentState(consentInformation: ConsentInformation) {
        canRequestAds = consentInformation.canRequestAds()
        privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
}
