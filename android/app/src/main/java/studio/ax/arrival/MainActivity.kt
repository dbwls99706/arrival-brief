package studio.ax.arrival

import android.app.Activity
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*

class MainActivity:Activity() {
 private val bg=Color.rgb(16,35,49); private val surface=Color.rgb(25,52,67); private val ink=Color.rgb(243,247,250); private val accent=Color.rgb(167,220,241); private val muted=Color.rgb(179,198,211)
 private val sim=SimulatorVehicleGateway(); private var gateway:VehicleGateway=sim
 private var confirmed:Decision?=null; private var current:Decision?=null
 private lateinit var text:EditText;private lateinit var height:EditText;private lateinit var output:TextView;private lateinit var form:LinearLayout;private lateinit var status:TextView;private lateinit var choices:LinearLayout;private lateinit var confirm:Button;private lateinit var copy:Button
 private var selection:String?=null
 private var diagnostics:PleosDiagnostics?=null
 private lateinit var sdkStatus:TextView
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun textView(value:String,size:Float=16f,color:Int=ink)=TextView(this).apply { this.text=value;textSize=size;setTextColor(color);setPadding(0,dp(8),0,dp(8)) }
 private fun button(label:String,action:()->Unit)=Button(this).apply { text=label;isAllCaps=false;setTextColor(bg);backgroundTintList=android.content.res.ColorStateList.valueOf(accent);minHeight=dp(52);setOnClickListener { action() } }
 private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
 override fun onCreate(state:Bundle?) {super.onCreate(state);window.statusBarColor=bg;window.navigationBarColor=bg
  val scroll=ScrollView(this).apply {setBackgroundColor(bg)};val root=column().apply {setPadding(dp(24),dp(24),dp(24),dp(30))};scroll.addView(root);setContentView(scroll)
  root.addView(textView("Arrival Brief",28f).apply {setTypeface(null,Typeface.BOLD)})
  root.addView(textView("도착을 준비하는 한 장",15f,accent))
  status=textView("",13f,muted);root.addView(status)
  val controls=LinearLayout(this);listOf("정차" to VehicleMode.PARKED,"주행" to VehicleMode.DRIVING,"미확인" to VehicleMode.UNKNOWN).forEach{(label,mode)->controls.addView(button(label){gateway=sim;sim.state=mode;refreshMode()},LinearLayout.LayoutParams(0,dp(54),1f))};root.addView(controls)
  sdkStatus=textView("Pleos SDK 진단은 버튼을 눌렀을 때만 연결합니다.",12f,muted)
  root.addView(button("Pleos 실제 SDK 연결 확인"){gateway=UnconnectedVehicleGateway();refreshMode();diagnostics?.stop();diagnostics=PleosDiagnostics(this){message->sdkStatus.text=message};diagnostics?.start()})
  root.addView(sdkStatus)
  form=column();root.addView(form);form.addView(textView("01  행사 안내",18f))
  text=EditText(this).apply {setTextColor(ink);setHintTextColor(muted);setText("코엑스 오디토리움 3층 디자인 컨퍼런스");minLines=2;maxLines=6;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE};form.addView(text)
  val samples=LinearLayout(this);samples.addView(button("오디토리움"){text.setText("코엑스 오디토리움 3층 행사")},LinearLayout.LayoutParams(0,dp(54),1f));samples.addView(button("컨퍼런스 E"){text.setText("코엑스 3층 컨퍼런스룸 E")},LinearLayout.LayoutParams(0,dp(54),1f));form.addView(samples)
  form.addView(textView("차량 전체 높이 (m)",14f));height=EditText(this).apply {setTextColor(ink);setText("1.65");inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL};form.addView(height)
  form.addView(textView("초기 1.65m는 예시입니다. 루프박스·적재물 포함 실제 높이로 바꾸세요.",12f,muted))
  choices=column();form.addView(choices)
  confirm=button("도착 브리핑 확정"){if(gateway.mode()==VehicleMode.PARKED&&current?.status=="ready"){confirmed=current;render()}};form.addView(confirm)
  val panel=column().apply {setPadding(dp(20),dp(18),dp(20),dp(18));background=GradientDrawable().apply {setColor(surface);cornerRadius=dp(18).toFloat()}};root.addView(panel,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(24)})
  panel.addView(textView("02  도착 브리핑",17f,accent));output=textView("",23f);panel.addView(output)
  copy=button("확정 브리핑 복사"){confirmed?.let { d -> if(gateway.mode()==VehicleMode.PARKED){(getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Arrival Brief",BriefEngine.export(d)));Toast.makeText(this,"브리핑을 복사했습니다.",Toast.LENGTH_SHORT).show()}}};panel.addView(copy)
  root.addView(textView("코엑스 공식 회의실 주차안내\n자료 확인 2026-10-28\n${BriefEngine.SOURCE}",12f,muted).apply {setTextIsSelectable(true)})
  root.addView(textView("독립 출품 준비 프로토타입 · 입력은 기기에만 유지\n실시간 내비게이션·주차면·승객 하차 지점 제공 없음. Pleos SDK 읽기 연결은 구현되어 있으며, 차량 실증·주행규제 연동은 미완료입니다.",12f,muted))
  val watcher=object:TextWatcher {override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,f:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){selection=null;confirmed=null;compute()};override fun afterTextChanged(e:Editable?){} };text.addTextChangedListener(watcher);height.addTextChangedListener(watcher);compute();refreshMode()
 }
 override fun onDestroy(){diagnostics?.stop();super.onDestroy()}
 private fun compute(){current=BriefEngine.resolve(text.text.toString(),height.text.toString(),selection);choices.removeAllViews();if(current?.status=="select")current!!.candidates.forEach {d->choices.addView(button(d.name){selection=d.id;confirmed=null;compute()})};render()}
 private fun refreshMode(){val parked=gateway.mode()==VehicleMode.PARKED;form.visibility=if(parked)View.VISIBLE else View.GONE;status.text=if(gateway.isSimulation)"차량 상태 시뮬레이션 · ${gateway.mode()} · 실제 차량 미연결" else "실차 SDK 미연결 · 상태 미확인 · 입력 잠금";render()}
 private fun render(){val parked=gateway.mode()==VehicleMode.PARKED;confirm.isEnabled=parked&&current?.status=="ready";copy.visibility=if(parked)View.VISIBLE else View.GONE;copy.isEnabled=parked&&confirmed!=null;val d=if(parked)current else confirmed
  output.text=when{d==null->"확정된 브리핑이 없습니다.\n정차 후 장소와 차량 높이를 확인하세요.";d.status!="ready"->d.message;else->{val p=d.destination!!;"${if(confirmed!=null)"확정됨" else "확인 전"}\n코엑스 ${p.name} (${p.floor})\n\n${p.gate}\n${p.parking}\n\n차량 ${d.height}m / 시설 제한 2.2m\n\n${if(parked)"빈자리·혼잡도·현장 진입은 실시간 확인되지 않았습니다.\n" else ""}현장 표지와 안내를 우선하세요."}}
 }
}
