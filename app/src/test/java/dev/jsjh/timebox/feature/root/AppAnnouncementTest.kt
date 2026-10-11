package dev.jsjh.timebox.feature.root

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppAnnouncementTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val announcement = CurrentAppAnnouncement.value.copy(updatedInstallOnly = false)
    private val today = LocalDate.of(2026, 10, 11)

    @Before fun resetLaunchAndPreferences() {
        context.getSharedPreferences("app_announcements", Context.MODE_PRIVATE).edit().clear().commit()
        AppAnnouncementStore.beginLaunch()
    }

    @Test fun currentAnnouncementIsANewUpdateNotice() {
        assertTrue(CurrentAppAnnouncement.value.updatedInstallOnly)
        assertEquals("statistics_and_feature_survey_v1", CurrentAppAnnouncement.value.id)
    }

    @Test fun audienceAndLaunchAndSuppressionAlwaysGateDisplay() {
        fun eligible(audience: Boolean = true, shown: Boolean = false, suppressed: Boolean = false) =
            isAppAnnouncementEligible(audience, 0, Long.MIN_VALUE, today.toEpochDay(), suppressed, shown)
        assertTrue(eligible())
        assertFalse(eligible(audience = false))
        assertFalse(eligible(shown = true))
        assertFalse(eligible(suppressed = true))
    }

    @Test fun firstThreeDisplaysRequireSeparateLaunchesButMayShareADate() {
        (1..3).forEach { count ->
            AppAnnouncementStore.beginLaunch()
            assertTrue(AppAnnouncementStore.shouldShow(context, announcement, today))
            assertEquals(count, AppAnnouncementStore.markShownIfEligible(context, announcement, today))
            assertFalse(AppAnnouncementStore.shouldShow(context, announcement, today))
            assertNull(AppAnnouncementStore.markShownIfEligible(context, announcement, today))
        }
        AppAnnouncementStore.beginLaunch()
        assertFalse(AppAnnouncementStore.shouldShow(context, announcement, today))
        assertNull(AppAnnouncementStore.markShownIfEligible(context, announcement, today))
    }

    @Test fun thirdDisplayDatePreventsFourthUntilAnotherLocalDate() {
        repeat(3) {
            AppAnnouncementStore.beginLaunch()
            AppAnnouncementStore.markShownIfEligible(context, announcement, today)
        }
        AppAnnouncementStore.beginLaunch()
        assertNull(AppAnnouncementStore.markShownIfEligible(context, announcement, today))
        assertEquals(4, AppAnnouncementStore.markShownIfEligible(context, announcement, today.plusDays(1)))
        AppAnnouncementStore.beginLaunch()
        assertNull(AppAnnouncementStore.markShownIfEligible(context, announcement, today.plusDays(1)))
        assertEquals(5, AppAnnouncementStore.markShownIfEligible(context, announcement, today.plusDays(2)))
    }

    @Test fun dailyDisplaysHaveNoFixedTotalLimit() {
        (0..12).forEach { days ->
            AppAnnouncementStore.beginLaunch()
            assertEquals(days + 1, AppAnnouncementStore.markShownIfEligible(context, announcement, today.plusDays(days.toLong())))
        }
    }

    @Test fun suppressionPersistsOnlyForTheSameIdAndRevision() {
        AppAnnouncementStore.markShownIfEligible(context, announcement, today)
        AppAnnouncementStore.suppress(context, announcement)
        AppAnnouncementStore.beginLaunch()
        assertFalse(AppAnnouncementStore.shouldShow(context, announcement, today.plusMonths(1)))
        assertTrue(AppAnnouncementStore.shouldShow(context, announcement.copy(revision = 2), today))
        assertTrue(AppAnnouncementStore.shouldShow(context, announcement.copy(key = "another"), today))
    }

    @Test fun eligibilityChecksDoNotConsumeADisplayOrResetExistingPreferences() {
        repeat(5) { assertTrue(AppAnnouncementStore.shouldShow(context, announcement, today)) }
        assertEquals(1, AppAnnouncementStore.markShownIfEligible(context, announcement, today))
        AppAnnouncementStore.beginLaunch()
        assertEquals(2, AppAnnouncementStore.markShownIfEligible(context, announcement, today))
    }

    @Test fun oldTutorialNoticeCountDoesNotHideTheNewAnnouncement() {
        context.getSharedPreferences("app_announcements", Context.MODE_PRIVATE).edit()
            .putInt("tutorial_refresh_v1_display_count", 2).commit()
        assertEquals(1, AppAnnouncementStore.markShownIfEligible(context, announcement, today))
    }
}
