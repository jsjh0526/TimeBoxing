package dev.jsjh.timebox.ads

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dev.jsjh.timebox.ui.runWhenWindowReady
import java.lang.ref.WeakReference

object AdsConsentManager {
    private class ConsentFlow(activity: Activity) {
        val owner = WeakReference(activity)
    }

    private var activeFlow: ConsentFlow? = null

    var canRequestAds by mutableStateOf(false)
        private set

    var privacyOptionsRequired by mutableStateOf(false)
        private set

    fun gatherConsent(activity: Activity, onCanRequestAds: () -> Unit) {
        val flow = beginFlow(activity) ?: return
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (activeFlow !== flow) return@requestConsentInfoUpdate
                updateConsentState(consentInformation)
                if (
                    consentInformation.consentStatus != ConsentInformation.ConsentStatus.REQUIRED ||
                    !consentInformation.isConsentFormAvailable
                ) {
                    finishFlow(flow)
                    if (canRequestAds && !activity.isFinishing && !activity.isDestroyed) onCanRequestAds()
                    return@requestConsentInfoUpdate
                }
                UserMessagingPlatform.loadConsentForm(activity.applicationContext, { form ->
                    if (activeFlow !== flow) return@loadConsentForm
                    activity.runWhenWindowReady(onCancelled = { finishFlow(flow) }) {
                        if (activeFlow === flow) {
                            form.show(activity) {
                                finishFlow(flow)
                                updateConsentState(consentInformation)
                                if (canRequestAds && !activity.isFinishing && !activity.isDestroyed) onCanRequestAds()
                            }
                        }
                    }
                }, {
                    finishFlow(flow)
                    updateConsentState(consentInformation)
                    if (canRequestAds && !activity.isFinishing && !activity.isDestroyed) onCanRequestAds()
                })
            },
            {
                finishFlow(flow)
                updateConsentState(consentInformation)
                if (canRequestAds && !activity.isFinishing && !activity.isDestroyed) onCanRequestAds()
            }
        )

        updateConsentState(consentInformation)
        if (canRequestAds) onCanRequestAds()
    }

    fun showPrivacyOptions(activity: Activity) {
        val flow = beginFlow(activity) ?: return
        activity.runWhenWindowReady(onCancelled = { finishFlow(flow) }) {
            if (activeFlow === flow) {
                UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                    finishFlow(flow)
                    updateConsentState(UserMessagingPlatform.getConsentInformation(activity))
                }
            }
        }
    }

    fun isConsentFlowActive(activity: Activity): Boolean =
        activeFlow?.owner?.get() === activity && !activity.isFinishing && !activity.isDestroyed

    private fun beginFlow(activity: Activity): ConsentFlow? {
        if (activity.isFinishing || activity.isDestroyed || isConsentFlowActive(activity)) return null
        return ConsentFlow(activity).also { activeFlow = it }
    }

    private fun finishFlow(flow: ConsentFlow) {
        if (activeFlow === flow) activeFlow = null
    }

    private fun updateConsentState(consentInformation: ConsentInformation) {
        canRequestAds = consentInformation.canRequestAds()
        privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
}
