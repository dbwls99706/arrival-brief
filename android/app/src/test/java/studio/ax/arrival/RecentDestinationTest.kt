package studio.ax.arrival

import org.junit.Assert.*
import org.junit.Test

class RecentDestinationTest {
    private val now = 1_800_000_000_000L
    private fun record(id: String = "auditorium", time: Long = now, version: String = RecentDestinationPolicy.DATASET_VERSION) = RecentDestination(id, time, version)

    @Test fun ttlFutureAndDatasetChangesInvalidateRecentDestination() {
        assertTrue(RecentDestinationPolicy.valid(record(), now))
        assertTrue(RecentDestinationPolicy.valid(record(time = now - RecentDestinationPolicy.TTL_MILLIS + 1), now))
        assertFalse(RecentDestinationPolicy.valid(record(time = now - RecentDestinationPolicy.TTL_MILLIS), now))
        assertFalse(RecentDestinationPolicy.valid(record(time = now + 1), now))
        assertFalse(RecentDestinationPolicy.valid(record(version = "old"), now))
        assertFalse(RecentDestinationPolicy.valid(record(id = "missing"), now))
        assertFalse(RecentDestinationPolicy.valid(record(id = "conferenceE"), now))
        assertFalse(RecentDestinationPolicy.valid(null, now))
    }

    @Test fun restorationNeverRestoresHeightOrConsentOrConfirmation() {
        val session = BriefSession().apply {
            updateNotice("코엑스 아셈볼룸 2층 사용자 원문"); updateHeight("2.10")
            reviewHeight(true); confirmParked(true); reviewNavigationManually(true)
        }
        val parked = SimulatorVehicleGateway(VehicleMode.PARKED)
        assertTrue(session.confirm(parked))
        assertTrue(session.restoreDestination("auditorium"))
        assertEquals("auditorium", session.selectedId)
        assertEquals("", session.heightText)
        assertFalse(session.notice.contains("사용자 원문"))
        assertNull(session.confirmed)
        assertFalse(session.heightReviewed); assertFalse(session.parkedConfirmed)
        assertFalse(session.navigationReviewedManually)
        assertFalse(session.confirm(parked))
    }
}
