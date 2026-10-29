package studio.ax.arrival

/** This prototype deliberately exposes simulator state, not fabricated vehicle readings. */
enum class VehicleMode { PARKED, DRIVING, UNKNOWN }
interface VehicleGateway { val isSimulation:Boolean; fun mode():VehicleMode }
class SimulatorVehicleGateway(var state:VehicleMode=VehicleMode.PARKED):VehicleGateway {
 override val isSimulation=true
 override fun mode()=state
}
/** Fail-closed placeholder until the Pleos State + CarUxRestrictions adapter is implemented and tested. */
class UnconnectedVehicleGateway:VehicleGateway {
 override val isSimulation=false
 override fun mode()=VehicleMode.UNKNOWN
}
