package studio.ax.arrival

/** Volatile session only: rotation retains it; process death drops private pasted text. */
class BriefSession {
    var notice = ""
        private set
    var heightText = ""
        private set
    var selectedId: String? = null
        private set
    var confirmed: Decision? = null
        private set
    var heightReviewed = false
        private set
    var parkedConfirmed = false
        private set

    val current: Decision get() = BriefEngine.resolve(notice, heightText, selectedId)

    fun updateNotice(value: String) {
        if (notice == value) return
        notice = value
        selectedId = null
        confirmed = null
    }

    fun updateHeight(value: String) {
        if (heightText == value) return
        heightText = value
        heightReviewed = false
        confirmed = null
        // A height correction must not silently discard an explicit destination choice.
    }

    fun select(id: String) { selectedId = id; confirmed = null }
    fun reviewHeight(value: Boolean) { heightReviewed = value; if (!value) confirmed = null }
    fun confirmParked(value: Boolean) { parkedConfirmed = value; if (!value) confirmed = null }

    fun canConfirm(gateway: VehicleGateway) =
        InputAccess.allowed(gateway) && heightReviewed && parkedConfirmed && current.status == "ready"

    fun confirm(gateway: VehicleGateway): Boolean {
        if (!canConfirm(gateway)) return false
        confirmed = current
        return true
    }

    /** Keep the previously confirmed briefing, but require a new parking check before editing. */
    fun lock() { parkedConfirmed = false }
}
