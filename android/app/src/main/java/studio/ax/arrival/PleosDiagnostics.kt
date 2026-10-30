package studio.ax.arrival

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.constants.NaviErrorCode

/** Read-only real SDK integration. A brake pedal reading never proves that a vehicle is parked. */
class PleosDiagnostics(context:Context,private val report:(String)->Unit) {
 private val main=Handler(Looper.getMainLooper())
 private val vehicle=Vehicle(context.applicationContext)
 private val navi=NaviHelper(context.applicationContext)
 @Volatile private var active=false
 private var brake="Vehicle State 응답 대기"
 private var navigation="NaviHelper 연결 대기"
 private fun emit(){if(active)main.post{if(active)report("$brake\n$navigation\n읽기 진단 결과입니다. 입력 허용은 별도 CarUxRestrictions 상태로 결정하며, 브레이크 페달로 정차를 판단하지 않습니다.")}}
 private val listener=object:NaviHelperEventListener {
  override fun onDestinationInfo(destinationInfo:DestinationInfo){navigation="NaviHelper 목적지 정보 수신 성공 (개인 위치는 표시·저장하지 않음)";emit()}
  override fun onError(errorCode:NaviErrorCode){navigation="NaviHelper 조회 실패: $errorCode";emit()}
 }
 fun start(){
  if(active)return
  active=true;emit()
  try {
   vehicle.initialize()
   main.postDelayed({if(active)try {vehicle.getBrake().checkBrakePedalStatusCapabilities(
    onComplete=capability@{config->
     if(!active)return@capability
     if(config.accessType==AccessType.READ||config.accessType==AccessType.READ_WRITE){
      vehicle.getBrake().getCurrentBrakePedalStatus(onComplete={pressed->brake="Vehicle State 브레이크 페달: ${pressed?.let{if(it)"눌림" else "해제"}?:"알 수 없음"}";emit()},onFailed={e->brake="Vehicle State 읽기 실패: ${e.javaClass.simpleName}";emit()})
     } else {brake="Vehicle State 읽기 미지원: ${config.accessType}";emit()}
    },onFailed={e->brake="Vehicle State 권한·연결 확인 필요: ${e.javaClass.simpleName}";emit()}
   )}catch(e:Exception){brake="Vehicle State 사용 불가: ${e.javaClass.simpleName}";emit()}},800)
  } catch(e:Exception){brake="Vehicle State 연결 불가: ${e.javaClass.simpleName}";emit()}
  try {
   navi.initialize(onConnected={if(active){navigation="NaviHelper 연결됨 · 목적지 조회 중";emit();try{navi.addListener(listener);navi.getDestinationInfo()}catch(e:Exception){navigation="NaviHelper 권한 확인 필요: ${e.javaClass.simpleName}";emit()}}},onFailed={e->navigation="NaviHelper 연결 실패: ${e.javaClass.simpleName}";emit()})
  }catch(e:Exception){navigation="NaviHelper 사용 불가: ${e.javaClass.simpleName}";emit()}
  main.postDelayed({if(active){if(brake.contains("응답 대기"))brake="Vehicle State 응답 없음 · 서비스·권한 확인 필요";if(navigation.contains("대기")||navigation.contains("조회 중"))navigation="NaviHelper 응답 없음 · 서비스·권한 확인 필요";emit()}},6000)
 }
 fun stop(){active=false;main.removeCallbacksAndMessages(null);try{navi.removeListener(listener);navi.release()}catch(_:Exception){};try{vehicle.release()}catch(_:Exception){}}
}
