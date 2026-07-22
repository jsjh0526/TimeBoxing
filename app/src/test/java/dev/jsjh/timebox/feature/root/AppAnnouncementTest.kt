package dev.jsjh.timebox.feature.root

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAnnouncementTest {
    @Test
    fun `new installations do not see the update announcement`() {
        assertFalse(
            isAppAnnouncementEligible(
                audienceEligible = false,
                displayCount = 0,
                maxDisplayCount = 3,
                shownThisProcess = false
            )
        )
    }

    @Test
    fun `updated installations see the announcement up to three times`() {
        assertTrue(
            isAppAnnouncementEligible(
                audienceEligible = true,
                displayCount = 0,
                maxDisplayCount = 3,
                shownThisProcess = false
            )
        )
        assertTrue(
            isAppAnnouncementEligible(
                audienceEligible = true,
                displayCount = 2,
                maxDisplayCount = 3,
                shownThisProcess = false
            )
        )
        assertFalse(
            isAppAnnouncementEligible(
                audienceEligible = true,
                displayCount = 3,
                maxDisplayCount = 3,
                shownThisProcess = false
            )
        )
    }

    @Test
    fun `announcement is shown only once per process`() {
        assertFalse(
            isAppAnnouncementEligible(
                audienceEligible = true,
                displayCount = 1,
                maxDisplayCount = 3,
                shownThisProcess = true
            )
        )
    }
}
