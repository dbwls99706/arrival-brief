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
    var navigationCheck = NavigationCheck.initial()
        private set
    var navigationReviewedManually = false
        private set
    private var navigationMismatchKnown = false

    val current: Decision get() = BriefEngine.resolve(notice, heightText, selectedId)

    fun updateNotice(value: String) {
        if (notice == value) return
        notice = value
        selectedId = null
        confirmed = null
        heightReviewed = false
        parkedConfirmed = false
        navigationCheck = if (navigationMismatchKnown) unresolvedMismatch() else NavigationCheck.initial()
        navigationReviewedManually = false
    }

    fun updateHeight(value: String) {
        if (heightText == value) return
        heightText = value
        heightReviewed = false
        parkedConfirmed = false
        confirmed = null
        // A height correction must not silently discard an explicit destination choice.
    }

    fun select(id: String) { selectedId = id; confirmed = null; heightReviewed = false; parkedConfirmed = false }
    fun reviewHeight(value: Boolean) { heightReviewed = value; if (!value) confirmed = null }
    fun confirmParked(value: Boolean) { parkedConfirmed = value; if (!value) confirmed = null }
    fun reviewNavigationManually(value: Boolean) { navigationReviewedManually = value; if (!value) confirmed = null }
    fun updateNavigation(value: NavigationCheck) {
        if (value.status == NavigationStatus.MISMATCH) navigationMismatchKnown = true
        if (value.status == NavigationStatus.MATCHED) navigationMismatchKnown = false
        navigationCheck = if (navigationMismatchKnown && value.status !in setOf(NavigationStatus.MISMATCH, NavigationStatus.CHECKING)) unresolvedMismatch() else value
        navigationReviewedManually = false
        confirmed = null
    }

    private fun unresolvedMismatch() = NavigationCheck(NavigationStatus.MISMATCH, "앞서 확인한 내비 목적지 불일치가 해소되지 않았습니다. 목적지를 확인하고 다시 대조하세요.")

    fun navigationAllowsConfirmation(): Boolean = when (navigationCheck.status) {
        NavigationStatus.MATCHED -> true
        NavigationStatus.CHECKING, NavigationStatus.MISMATCH -> false
        else -> navigationReviewedManually
    }

    fun restoreDestination(id: String): Boolean {
        val destination = BriefEngine.destinations.find { it.id == id } ?: return false
        notice = "코엑스 ${destination.name} ${destination.floor}"
        heightText = ""
        selectedId = id
        confirmed = null
        heightReviewed = false
        parkedConfirmed = false
        navigationCheck = if (navigationMismatchKnown) unresolvedMismatch() else NavigationCheck.initial()
        navigationReviewedManually = false
        return true
    }

    fun canConfirm(gateway: VehicleGateway) =
        InputAccess.allowed(gateway) && heightReviewed && parkedConfirmed && navigationAllowsConfirmation() && current.status == "ready"

    fun confirm(gateway: VehicleGateway): Boolean {
        if (!canConfirm(gateway)) return false
        confirmed = current
        return true
    }

    /** Keep the previously confirmed briefing, but require a new parking check before editing. */
    fun lock() {
        parkedConfirmed = false
        navigationReviewedManually = false
        navigationCheck = if (navigationMismatchKnown) unresolvedMismatch() else NavigationCheck.initial()
    }
}
