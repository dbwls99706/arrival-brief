package studio.ax.arrival

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/** Official AAOS/Pleos UX restriction listener. It does not infer physical parking. */
@android.annotation.TargetApi(29)
class PlatformVehicleGateway(
    private val context: Context,
    private val changed: (String) -> Unit
) : VehicleGateway {
    override val isSimulation = false
    private val connection = PlatformConnectionState()
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var active = false
    private var generation = 0
    private val main = Handler(Looper.getMainLooper())
    override fun mode() = connection.mode

    private fun update(token: Int, restrictions: CarUxRestrictions?) {
        if (!active || !connection.update(token, restrictions?.isRequiresDistractionOptimization, restrictions?.activeRestrictions)) return
        changed(when (connection.mode) {
            VehicleMode.PLATFORM_ALLOWED -> "플랫폼 UX 입력 허용 · 실제 정차 여부를 확인하세요."
            VehicleMode.RESTRICTED -> "플랫폼 주행 규제 활성 · 입력 잠금"
            else -> "플랫폼 상태 미확인 · 입력 잠금"
        })
    }

    fun start() {
        if (active) return
        active = true
        val run = ++generation
        connection.disconnect()
        changed("플랫폼 주행 규제 연결 중 · 입력 잠금")
        try {
            car = Car.createCar(context.applicationContext, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!active || run != generation) return@createCar
                if (!ready) {
                    manager = null
                    connection.disconnect()
                    changed("차량 서비스 연결 끊김 · 입력 잠금")
                } else try {
                    val token = connection.connect()
                    manager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    manager?.registerListener { restrictions -> if (active && run == generation) update(token, restrictions) }
                    update(token, manager?.currentCarUxRestrictions)
                } catch (_: Exception) {
                    connection.disconnect()
                    changed("플랫폼 주행 규제 조회 불가 · 입력 잠금")
                }
            }
            main.postDelayed({
                if (active && run == generation && connection.mode == VehicleMode.UNKNOWN)
                    changed("차량 서비스 응답 없음 · 입력 잠금")
            }, 6000)
        } catch (_: Exception) {
            connection.disconnect()
            changed("차량 서비스 사용 불가 · 입력 잠금")
        } catch (_: LinkageError) {
            connection.disconnect()
            changed("지원하는 차량 플랫폼이 없습니다 · 입력 잠금")
        }
    }

    fun stop() {
        active = false
        generation++
        connection.disconnect()
        main.removeCallbacksAndMessages(null)
        try { manager?.unregisterListener() } catch (_: Exception) { }
        try { car?.disconnect() } catch (_: Exception) { }
        manager = null
        car = null
    }
}
