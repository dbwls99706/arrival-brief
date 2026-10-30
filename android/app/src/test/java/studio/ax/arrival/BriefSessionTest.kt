package studio.ax.arrival

import org.junit.Assert.*
import org.junit.Test

class BriefSessionTest {
    private val demoParked = SimulatorVehicleGateway(VehicleMode.PARKED)
    private fun readySession() = BriefSession().apply {
        updateNotice("코엑스 오디토리움 3층 행사")
        updateHeight("1.65")
        reviewHeight(true)
        confirmParked(true)
        reviewNavigationManually(true)
    }

    @Test fun defaultAndUnknownModesFailClosed() {
        assertFalse(InputAccess.allowed(SimulatorVehicleGateway()))
        assertFalse(InputAccess.allowed(UnconnectedVehicleGateway()))
        assertFalse(readySession().confirm(UnconnectedVehicleGateway()))
        assertFalse(readySession().confirm(SimulatorVehicleGateway(VehicleMode.DRIVING)))
        assertFalse(readySession().confirm(SimulatorVehicleGateway(VehicleMode.UNKNOWN)))
    }

    @Test fun platformRequiresExplicitUnrestrictedValues() {
        assertEquals(VehicleMode.UNKNOWN, InputAccess.platformMode(null, 0))
        assertEquals(VehicleMode.UNKNOWN, InputAccess.platformMode(false, null))
        assertEquals(VehicleMode.RESTRICTED, InputAccess.platformMode(true, 0))
        assertEquals(VehicleMode.RESTRICTED, InputAccess.platformMode(false, 1))
        assertEquals(VehicleMode.RESTRICTED, InputAccess.platformMode(false, -1))
        assertEquals(VehicleMode.PLATFORM_ALLOWED, InputAccess.platformMode(false, 0))
    }

    @Test fun platformAndSimulationSignalsCannotBeConfused() {
        assertFalse(InputAccess.allowed(SimulatorVehicleGateway(VehicleMode.PLATFORM_ALLOWED)))
        val physicalParkedLabel = object : VehicleGateway {
            override val isSimulation = false
            override fun mode() = VehicleMode.PARKED
        }
        assertFalse(InputAccess.allowed(physicalParkedLabel))
        val platform = object : VehicleGateway {
            override val isSimulation = false
            override fun mode() = VehicleMode.PLATFORM_ALLOWED
        }
        assertTrue(readySession().confirm(platform))
    }

    @Test fun lateUnrestrictedCallbackCannotUnlockDisconnectedService() {
        val connection = PlatformConnectionState()
        val token = connection.connect()
        assertTrue(connection.update(token, false, 0))
        assertEquals(VehicleMode.PLATFORM_ALLOWED, connection.mode)
        connection.disconnect()
        assertFalse(connection.update(token, false, 0))
        assertEquals(VehicleMode.UNKNOWN, connection.mode)
    }

    @Test fun reconnectRejectsPreviousServiceCallbacks() {
        val connection = PlatformConnectionState()
        val oldToken = connection.connect()
        connection.disconnect()
        val newToken = connection.connect()
        assertTrue(connection.update(newToken, true, 1))
        assertFalse(connection.update(oldToken, false, 0))
        assertEquals(VehicleMode.RESTRICTED, connection.mode)
        assertTrue(connection.update(newToken, false, 0))
        assertEquals(VehicleMode.PLATFORM_ALLOWED, connection.mode)
    }

    @Test fun confirmationRequiresBothHumanChecks() {
        val session = readySession()
        session.reviewHeight(false)
        assertFalse(session.confirm(demoParked))
        session.reviewHeight(true)
        session.confirmParked(false)
        assertFalse(session.confirm(demoParked))
        session.confirmParked(true)
        assertTrue(session.confirm(demoParked))
    }

    @Test fun lockingRetainsConfirmedBriefAndRequiresReconfirmation() {
        val session = readySession()
        assertTrue(session.confirm(demoParked))
        val confirmed = session.confirmed
        session.lock()
        assertSame(confirmed, session.confirmed)
        assertFalse(session.canConfirm(demoParked))
        assertFalse(session.confirmParkedAndConfirmUnknown())
    }

    private fun BriefSession.confirmParkedAndConfirmUnknown(): Boolean {
        confirmParked(true)
        return confirm(UnconnectedVehicleGateway())
    }

    @Test fun heightCorrectionPreservesSelectedPlaceButInvalidatesReview() {
        val session = BriefSession()
        session.updateNotice("코엑스 행사")
        session.updateHeight("1.65")
        session.select("auditorium")
        session.reviewHeight(true); session.confirmParked(true)
        session.reviewNavigationManually(true)
        assertTrue(session.confirm(demoParked))
        session.updateHeight("1.8")
        assertEquals("auditorium", session.selectedId)
        assertEquals("ready", session.current.status)
        assertFalse(session.heightReviewed)
        assertNull(session.confirmed)
        assertFalse(session.confirm(demoParked))
    }

    @Test fun editingNoticeInvalidatesOldConfirmationAndSelection() {
        val session = readySession()
        session.select("auditorium")
        session.reviewHeight(true); session.confirmParked(true)
        assertTrue(session.confirm(demoParked))
        session.updateNotice("코엑스 아셈볼룸 2층")
        assertNull(session.selectedId); assertNull(session.confirmed)
        assertEquals("asem", session.current.destination?.id)
    }

    @Test fun conflictedSourcesNeverConfirmEvenWithAllChecks() {
        val session = readySession()
        session.updateNotice("코엑스 컨퍼런스룸 E 3층")
        assertFalse(session.confirm(demoParked))
        assertNull(session.confirmed)
    }
}
