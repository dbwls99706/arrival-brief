package studio.ax.arrival

/** PARKED/DRIVING are demo labels, never inferred from a brake-pedal reading. */
enum class VehicleMode { UNKNOWN, PARKED, DRIVING, PLATFORM_ALLOWED, RESTRICTED }

interface VehicleGateway {
    val isSimulation: Boolean
    fun mode(): VehicleMode
}

class SimulatorVehicleGateway(var state: VehicleMode = VehicleMode.UNKNOWN) : VehicleGateway {
    override val isSimulation = true
    override fun mode() = state
}

class UnconnectedVehicleGateway : VehicleGateway {
    override val isSimulation = false
    override fun mode() = VehicleMode.UNKNOWN
}

object InputAccess {
    fun allowed(gateway: VehicleGateway): Boolean =
        if (gateway.isSimulation) gateway.mode() == VehicleMode.PARKED
        else gateway.mode() == VehicleMode.PLATFORM_ALLOWED

    /** Requiring both signals is intentionally more conservative than per-feature flags. */
    fun platformMode(requiresDistractionOptimization: Boolean?, activeRestrictions: Int?): VehicleMode =
        when {
            requiresDistractionOptimization == null || activeRestrictions == null -> VehicleMode.UNKNOWN
            requiresDistractionOptimization || activeRestrictions != 0 -> VehicleMode.RESTRICTED
            else -> VehicleMode.PLATFORM_ALLOWED
        }
}

/** Reject delayed unrestricted callbacks from an old or disconnected car service. */
class PlatformConnectionState {
    private var epoch = 0
    private var connected = false
    var mode = VehicleMode.UNKNOWN
        private set

    fun connect(): Int {
        epoch++
        connected = true
        mode = VehicleMode.UNKNOWN
        return epoch
    }

    fun disconnect() {
        epoch++
        connected = false
        mode = VehicleMode.UNKNOWN
    }

    fun update(token: Int, requiresDistractionOptimization: Boolean?, activeRestrictions: Int?): Boolean {
        if (!connected || token != epoch) return false
        mode = InputAccess.platformMode(requiresDistractionOptimization, activeRestrictions)
        return true
    }
}
