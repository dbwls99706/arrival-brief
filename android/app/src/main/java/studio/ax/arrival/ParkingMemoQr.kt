package studio.ax.arrival

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A plain-text, offline handoff. No URL, invitation, account or live position is added. */
object ParkingMemoQr {
    private val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'KST'", Locale.ROOT)
        .withZone(ZoneId.of("Asia/Seoul"))

    fun canDisplay(record: ParkingMemo?, draft: String, gateway: VehicleGateway, safelyParked: Boolean, now: Long): Boolean =
        record != null && ParkingMemoPolicy.allowed(gateway, safelyParked) &&
            ParkingMemoPolicy.valid(record, now) && draft == record.text

    fun payload(record: ParkingMemo, now: Long): String? {
        if (!ParkingMemoPolicy.valid(record, now)) return null
        // Keep the handoff readable at the maximum memo length without dropping
        // the original memo, save time, or the independent lifetime of a phone copy.
        return "Arrival Brief\n주차 메모: ${record.text}\n${timestamp.format(Instant.ofEpochMilli(record.savedAt))}\n휴대폰 복사본은 자동 삭제되지 않음."
    }

    fun encode(record: ParkingMemo, now: Long, pixels: Int): BitMatrix? {
        val content = payload(record, now) ?: return null
        require(pixels in 256..1024)
        return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, pixels, pixels, mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4
        ))
    }
}
