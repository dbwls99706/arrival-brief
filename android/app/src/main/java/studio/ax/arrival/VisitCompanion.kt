package studio.ax.arrival

/** The shared source is transient and never trusted as a vehicle or confirmation signal. */
object SharedNoticePolicy {
    const val MAX_LENGTH = 10_000
    fun accept(action: String?, mimeType: String?, text: String?): String? {
        if (action != "android.intent.action.SEND" || mimeType != "text/plain" || text == null) return null
        if (text.length > MAX_LENGTH || text.any { it.code < 32 && it !in "\n\r\t" }) return null
        return text.trim().takeIf { it.isNotEmpty() }
    }
}

data class ParkingMemo(val text: String, val savedAt: Long)

/** A manual reminder, never live localization. Zero coordinates or inferred accuracy. */
object ParkingMemoPolicy {
    const val TTL_MS = 24 * 60 * 60 * 1000L
    const val MAX_LENGTH = 64
    fun valid(record: ParkingMemo, now: Long): Boolean =
        record.text.isNotBlank() && record.text.length <= MAX_LENGTH &&
            record.text.none { it.code < 32 || it.code == 127 } &&
            record.savedAt > 0 && now >= record.savedAt && now - record.savedAt < TTL_MS

    fun create(text: String, now: Long): ParkingMemo? {
        if (text.length > MAX_LENGTH) return null
        return ParkingMemo(text.trim(), now).takeIf { valid(it, now) }
    }
    fun allowed(gateway: VehicleGateway, safelyParked: Boolean) = InputAccess.allowed(gateway) && safelyParked
}
