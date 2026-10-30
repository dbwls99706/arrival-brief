package studio.ax.arrival

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.constants.NaviErrorCode

/** Read-only destination comparison. Never requests routes or reads the current vehicle location. */
class NavigationDestinationReader(context: Context, private val report: (NavigationCheck) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val request = NavigationRequestState()
    private val navi = NaviHelper(context.applicationContext)
    private var listener: NaviHelperEventListener? = null

    fun start() {
        val token = request.begin()
        listener = object : NaviHelperEventListener {
            override fun onDestinationInfo(destinationInfo: DestinationInfo) {
                // Raw values are consumed here only; neither logging nor persistence is performed.
                finish(token, NavigationMatcher.compare(destinationInfo.name, destinationInfo.address))
            }
            override fun onError(errorCode: NaviErrorCode) { finish(token, NavigationCheck.unavailable()) }
        }
        main.postDelayed({ finish(token, NavigationCheck.unavailable()) }, 6000)
        try {
            navi.initialize(onConnected = {
                main.post {
                    if (!request.isPending(token)) return@post
                    try { listener?.let { navi.addListener(it); navi.getDestinationInfo() } }
                    catch (_: Exception) { finish(token, NavigationCheck.unavailable()) }
                    catch (_: LinkageError) { finish(token, NavigationCheck.unavailable()) }
                }
            }, onFailed = { finish(token, NavigationCheck.unavailable()) })
        } catch (_: Exception) { finish(token, NavigationCheck.unavailable()) }
        catch (_: LinkageError) { finish(token, NavigationCheck.unavailable()) }
    }

    private fun finish(token: Int, check: NavigationCheck) {
        main.post {
            if (request.complete(token)) {
                main.removeCallbacksAndMessages(null)
                release()
                report(check)
            }
        }
    }

    private fun release() {
        try { listener?.let { navi.removeListener(it) } }
        catch (_: Exception) { }
        catch (_: LinkageError) { }
        try { navi.release() }
        catch (_: Exception) { }
        catch (_: LinkageError) { }
        listener = null
    }

    fun stop() { request.cancel(); main.removeCallbacksAndMessages(null); release() }
}
