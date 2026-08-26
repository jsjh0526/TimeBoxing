package dev.jsjh.timebox.feature.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAnnouncementTest {
    @Test
    fun `current announcement targets updated installations up to two times`() {
        assertTrue(CurrentAppAnnouncement.value.updatedInstallOnly)
        assertEquals(2, CurrentAppAnnouncement.value.maxDisplayCount)
    }

    @Test
    fun `new installations do not see the update announcement`() {
        assertFalse(
            isAppAnnouncementEligible(
                audienceEligible = false,
                displayCount = 0,
                maxDisplayCount = 2,
                shownThisProcess = false
            )
        )
    }

    @Test
    fun `updated installations see the announcement up to two times`() {
        assertTrue(
            isAppAnnouncementEligible(
                audienceEligible = true,
                displayCount = 0,
                maxDisplayCount = 2,
                shownThisProcess = false
            )
        )
        assertTrue(
            isAppAnnouncementEligible(
                audienceEligible = true,
                displayCount = 1,
                maxDisplayCount = 2,
                shownThisProcess = false
            )
        )
        assertFalse(
            isAppAnnouncementEligible(
                audienceEligible = true,
                displayCount = 2,
                maxDisplayCount = 2,
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
                maxDisplayCount = 2,
                shownThisProcess = true
            )
        )
    }
}
