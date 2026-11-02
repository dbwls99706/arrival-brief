package studio.ax.arrival

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test

class ParkingMemoQrTest {
    private val now = 1_800_000_000_000L
    private val parked = SimulatorVehicleGateway(VehicleMode.PARKED)
    private val memo = ParkingMemo("B3 블루존 E12", now)

    @Test fun exportRequiresSavedUneditedMemoAndBothSafetySignals() {
        assertTrue(ParkingMemoQr.canDisplay(memo, memo.text, parked, true, now))
        assertFalse(ParkingMemoQr.canDisplay(null, memo.text, parked, true, now))
        assertFalse(ParkingMemoQr.canDisplay(memo, "B2 새 위치", parked, true, now))
        assertFalse(ParkingMemoQr.canDisplay(memo, memo.text, parked, false, now))
        for (mode in VehicleMode.values().filter { it != VehicleMode.PARKED })
            assertFalse(ParkingMemoQr.canDisplay(memo, memo.text, SimulatorVehicleGateway(mode), true, now))
        assertFalse(ParkingMemoQr.canDisplay(memo, memo.text, UnconnectedVehicleGateway(), true, now))
    }

    @Test fun expiredFutureOrInvalidMemoCannotBecomeQr() {
        assertNull(ParkingMemoQr.payload(memo, now - 1))
        assertNull(ParkingMemoQr.payload(memo, now + ParkingMemoPolicy.TTL_MS))
        assertNull(ParkingMemoQr.encode(ParkingMemo("x".repeat(65), now), now, 512))
        assertNull(ParkingMemoQr.payload(ParkingMemo("B3\nsecret", now), now))
    }

    @Test fun textPayloadHasOnlyMemoTimestampAndCopyNotice() {
        val text = ParkingMemoQr.payload(memo, now)!!
        assertTrue(text.startsWith("Arrival Brief\n주차 위치: B3 블루존 E12\n저장 시각: "))
        assertTrue(text.contains("KST"))
        assertTrue(text.endsWith("휴대폰 복사본은 자동 삭제되지 않습니다."))
        assertFalse(text.contains("http"))
        assertFalse(text.contains("geo:"))
        assertEquals(text, ParkingMemoQr.payload(memo, now + 1000))
    }

    @Test fun unicodePayloadRoundTripsThroughRenderedPixels() {
        for (text in listOf("B3 블루존 E12", "한".repeat(64), "B2 🅿️ 기둥 12", "A".repeat(64))) {
            val record = ParkingMemo(text, now)
            for (size in listOf(256, 512)) {
                val matrix = ParkingMemoQr.encode(record, now, size)!!
                val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) -0x1000000 else -1 }
                val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels))),
                    mapOf(DecodeHintType.CHARACTER_SET to "UTF-8"))
                assertEquals(ParkingMemoQr.payload(record, now), decoded.text)
                assertEquals(matrix, ParkingMemoQr.encode(record, now, size))
            }
        }
    }
}
