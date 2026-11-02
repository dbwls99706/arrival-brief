package studio.ax.arrival

import org.junit.Assert.*
import org.junit.Test

class VisitCompanionTest {
    private val now = 1_800_000_000_000L
    @Test fun onlyPlainTextExplicitSharingIsAccepted() {
        assertEquals("코엑스 오디토리움", SharedNoticePolicy.accept("android.intent.action.SEND", "text/plain", " 코엑스 오디토리움 "))
        for (action in listOf(null, "android.intent.action.VIEW", "android.intent.action.SEND_MULTIPLE"))
            assertNull(SharedNoticePolicy.accept(action, "text/plain", "코엑스"))
        for (mime in listOf(null, "text/html", "image/png", "*/*"))
            assertNull(SharedNoticePolicy.accept("android.intent.action.SEND", mime, "코엑스"))
    }
    @Test fun sharingRejectsEmptyOversizedAndControlPayloads() {
        for (value in listOf(null, "", " \n ", "x".repeat(10_001), "코엑스\u0000오디토리움"))
            assertNull(SharedNoticePolicy.accept("android.intent.action.SEND", "text/plain", value))
        assertNotNull(SharedNoticePolicy.accept("android.intent.action.SEND", "text/plain", "x".repeat(10_000)))
        assertEquals("코엑스\n오디토리움", SharedNoticePolicy.accept("android.intent.action.SEND", "text/plain", "코엑스\n오디토리움"))
    }
    @Test fun parkingMemoHasExactExpiryAndRejectsFutureClock() {
        val memo = ParkingMemoPolicy.create("B3 블루존 E12 기둥", now)!!
        assertTrue(ParkingMemoPolicy.valid(memo, now))
        assertTrue(ParkingMemoPolicy.valid(memo, now + ParkingMemoPolicy.TTL_MS - 1))
        assertFalse(ParkingMemoPolicy.valid(memo, now + ParkingMemoPolicy.TTL_MS))
        assertFalse(ParkingMemoPolicy.valid(memo, now - 1))
    }
    @Test fun parkingMemoRejectsInvalidOrUnboundedRecords() {
        for (text in listOf("", "  ", "x".repeat(65), "B3\nE12", "\u0000", "A\u007f"))
            assertNull(ParkingMemoPolicy.create(text, now))
        assertNull(ParkingMemoPolicy.create("B3", 0))
        assertNotNull(ParkingMemoPolicy.create("x".repeat(64), now))
        assertEquals("B3", ParkingMemoPolicy.create(" B3 ", now)?.text)
    }
    @Test fun memoCannotBeReadOrWrittenWithoutBothSignals() {
        val parked = SimulatorVehicleGateway(VehicleMode.PARKED)
        assertTrue(ParkingMemoPolicy.allowed(parked, true))
        assertFalse(ParkingMemoPolicy.allowed(parked, false))
        for (mode in VehicleMode.values().filter { it != VehicleMode.PARKED })
            assertFalse(ParkingMemoPolicy.allowed(SimulatorVehicleGateway(mode), true))
        assertFalse(ParkingMemoPolicy.allowed(UnconnectedVehicleGateway(), true))
    }
}
