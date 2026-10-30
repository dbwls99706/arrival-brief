package studio.ax.arrival

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Html
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Parked preparation screen. No distraction-optimized declaration or route-control calls. */
class MainActivity : Activity() {
    private val bg = Color.rgb(12, 27, 39)
    private val surface = Color.rgb(22, 44, 59)
    private val field = Color.rgb(17, 35, 48)
    private val ink = Color.rgb(243, 247, 250)
    private val accent = Color.rgb(167, 220, 241)
    private val muted = Color.rgb(174, 196, 210)
    private val warning = Color.rgb(255, 209, 146)
    private val sim = SimulatorVehicleGateway()
    private var gateway: VehicleGateway = UnconnectedVehicleGateway()
    private var platform: PlatformVehicleGateway? = null
    private var navigationReader: NavigationDestinationReader? = null
    private lateinit var recentStore: RecentDestinationStore
    private lateinit var session: BriefSession
    private var automotive = false
    private var demo = false
    private var platformMessage = "차량 상태 미확인 · 입력 잠금"
    private var synchronizingChecks = false

    private lateinit var notice: EditText
    private lateinit var height: EditText
    private lateinit var form: LinearLayout
    private lateinit var status: TextView
    private lateinit var lockedNotice: TextView
    private lateinit var choices: LinearLayout
    private lateinit var confirm: Button
    private lateinit var copy: Button
    private lateinit var demoEntry: Button
    private lateinit var demoControls: LinearLayout
    private lateinit var brief: LinearLayout
    private lateinit var briefBadge: TextView
    private lateinit var sources: TextView
    private lateinit var heightCheck: CheckBox
    private lateinit var parkedCheck: CheckBox
    private lateinit var navigationStatus: TextView
    private lateinit var navigationButton: Button
    private lateinit var navigationManualCheck: CheckBox
    private lateinit var recentStatus: TextView
    private lateinit var restoreRecent: Button
    private lateinit var deleteRecent: Button
    private lateinit var saveRecent: Button

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun textView(value: String, size: Float = 16f, color: Int = ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        setPadding(0, dp(5), 0, dp(5)); setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun shape(color: Int, radius: Int = 16) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun button(label: String, secondary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTextColor(if (secondary) accent else bg)
        backgroundTintList = ColorStateList.valueOf(if (secondary) surface else accent)
        minHeight = dp(52); setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { action() }
    }
    private fun card() = column().apply {
        setPadding(dp(20), dp(18), dp(20), dp(20)); background = shape(surface)
    }
    private fun check(label: String) = CheckBox(this).apply {
        text = label; textSize = 14f; setTextColor(ink)
        buttonTintList = ColorStateList.valueOf(accent)
        minHeight = dp(54); setPadding(0, dp(6), 0, dp(6)); isSaveEnabled = false
    }
    private fun edit(hintText: String, multiline: Boolean) = EditText(this).apply {
        hint = hintText; setTextColor(ink); setHintTextColor(muted); textSize = 17f
        background = shape(field, 12); setPadding(dp(14), dp(13), dp(14), dp(13))
        minHeight = dp(if (multiline) 112 else 54)
        inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        if (multiline) { minLines = 3; maxLines = 5; gravity = Gravity.TOP or Gravity.START }
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        filters = arrayOf(InputFilter.LengthFilter(if (multiline) 10000 else 10))
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION")
        session = (lastNonConfigurationInstance as? BriefSession) ?: BriefSession()
        automotive = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        recentStore = RecentDestinationStore(this)
        window.statusBarColor = bg; window.navigationBarColor = bg
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val root = column().apply { setPadding(dp(24), dp(20), dp(24), dp(28)) }
        scroll.addView(root)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
        root.addView(textView("ARRIVAL / BRIEF", 13f, accent).apply { letterSpacing = 0.12f })
        root.addView(textView("도착 전에, 한 번 확인", 27f).apply { setTypeface(null, Typeface.BOLD) })
        root.addView(textView("행사 안내에서 코엑스 입구와 주차 구역까지", 15f, muted))
        status = textView("", 14f, accent).apply { setPadding(0, dp(14), 0, dp(12)) }
        root.addView(status)

        demoEntry = button("차량 연결 없이 데모 체험", true) { enterDemo() }
        demoEntry.visibility = if (automotive) View.GONE else View.VISIBLE
        root.addView(demoEntry)
        demoControls = column()
        demoControls.addView(textView("데모 상태 전환 · 실제 차량 신호와 무관", 12f, warning))
        val states = LinearLayout(this)
        listOf("정차" to VehicleMode.PARKED, "주행" to VehicleMode.DRIVING, "미확인" to VehicleMode.UNKNOWN).forEach { (label, mode) ->
            states.addView(button(label, true) {
                if (demo && !automotive) { sim.state = mode; refreshMode() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        }
        demoControls.addView(states)
        demoControls.addView(button("데모 종료", true) { leaveDemo() })
        root.addView(demoControls)
        lockedNotice = textView("", 16f, muted).apply {
            setPadding(dp(18), dp(14), dp(18), dp(14)); background = shape(surface)
        }
        root.addView(lockedNotice)

        val recentPanel = card()
        recentStatus = textView("", 13f, muted)
        recentPanel.addView(recentStatus)
        val recentActions = LinearLayout(this)
        restoreRecent = button("보관한 목적지 불러오기", true) { restoreRecentDestination() }
        deleteRecent = button("삭제", true) {
            if (InputAccess.allowed(gateway)) { recentStore.clear(); render() }
        }
        recentActions.addView(restoreRecent, LinearLayout.LayoutParams(0, -2, 3f))
        recentActions.addView(deleteRecent, LinearLayout.LayoutParams(0, -2, 1f))
        recentPanel.addView(recentActions)
        root.addView(recentPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val workspace = LinearLayout(this).apply {
            orientation = if (resources.configuration.screenWidthDp >= 840) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        root.addView(workspace, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        form = card()
        form.addView(textView("01 / 안내 확인", 16f, accent))
        form.addView(textView("행사 장소가 적힌 문장", 19f).apply { setTypeface(null, Typeface.BOLD) })
        form.addView(button("안내문 대신 회의실 목록에서 선택", true) {
            if (InputAccess.allowed(gateway)) { notice.setText("코엑스"); hideKeyboard() }
        })
        form.addView(textView("이름·전화번호 등 개인정보는 지우고 붙여넣으세요.", 12f, muted))
        notice = edit("예: 코엑스 오디토리움 3층 행사", true).apply { setText(session.notice); contentDescription = "행사 장소 안내" }
        form.addView(notice)
        val samples = LinearLayout(this)
        samples.addView(button("오디토리움 선택", true) {
            if (InputAccess.allowed(gateway)) notice.setText("코엑스 오디토리움 3층 행사")
        }, LinearLayout.LayoutParams(0, -2, 1f))
        samples.addView(button("아셈볼룸 선택", true) {
            if (InputAccess.allowed(gateway)) notice.setText("코엑스 아셈볼룸 2층 행사")
        }, LinearLayout.LayoutParams(0, -2, 1f))
        form.addView(samples)
        choices = column(); form.addView(choices)
        form.addView(textView("차량 내비 목적지 대조", 16f))
        navigationStatus = textView("", 13f, muted)
        form.addView(navigationStatus)
        navigationButton = button("내비 목적지와 행사 시설 대조", true) { compareNavigationDestination() }
        form.addView(navigationButton)
        navigationManualCheck = check("내비 대조 없이 코엑스 삼성동 행사 시설임을 직접 확인했습니다.")
        form.addView(navigationManualCheck)
        form.addView(textView("내비의 시설명·주소만 이번 확인에 사용합니다. 원문은 표시·보관하지 않으며 경로를 변경하지 않습니다.", 12f, muted))
        form.addView(textView("차량 전체 높이 · m", 16f))
        height = edit("직접 확인한 전체 높이 (예: 1.65)", false).apply { setText(session.heightText); contentDescription = "루프박스와 적재물을 포함한 차량 전체 높이, 미터" }
        form.addView(height)
        form.addView(textView("차량 제원에 루프박스·적재물 높이를 포함하세요. 시설 제한은 2.2m입니다.", 12f, muted))
        heightCheck = check("루프박스·적재물을 포함한 전체 높이를 확인했습니다.")
        parkedCheck = check("안전하게 주차한 상태에서 확인하고 있습니다.")
        form.addView(heightCheck); form.addView(parkedCheck)
        confirm = button("이 내용으로 브리핑 확정") {
            if (session.confirm(gateway)) {
                hideKeyboard(); render()
                Toast.makeText(this, "도착 브리핑을 확정했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        form.addView(confirm)
        val panel = card()
        panel.addView(textView("02 / 도착 브리핑", 16f, accent))
        briefBadge = textView("확인 전", 13f, warning); panel.addView(briefBadge)
        brief = column(); panel.addView(brief)
        copy = button("확정 브리핑 복사", true) {
            if (InputAccess.allowed(gateway) && session.parkedConfirmed) session.confirmed?.let {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Arrival Brief", BriefEngine.export(it)))
                Toast.makeText(this, "브리핑을 복사했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        panel.addView(copy)
        saveRecent = button("목적지만 이 기기에 24시간 보관", true) {
            if (InputAccess.allowed(gateway) && session.parkedConfirmed) session.confirmed?.destination?.let {
                recentStore.save(it.id); render()
                Toast.makeText(this, "목적지 1건을 보관했습니다. 안내문·높이는 저장하지 않습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        panel.addView(saveRecent)
        if (workspace.orientation == LinearLayout.HORIZONTAL) {
            workspace.addView(form, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(20) })
            workspace.addView(panel, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            workspace.addView(form, LinearLayout.LayoutParams(-1, -2))
            workspace.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        }
        sources = textView("", 12f, muted)
        root.addView(sources, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        root.addView(textView("독립 출품 프로토타입 · 차량 실증 전\n실시간 주차·내비 경로·하차 지점·휴대폰에서 차량으로 전송은 제공하지 않습니다. 안내문과 높이는 현재 세션에만 유지됩니다.", 12f, muted))

        notice.addTextChangedListener(watcher { navigationReader?.stop(); navigationReader = null; session.updateNotice(it); refreshChoices(); render() })
        height.addTextChangedListener(watcher { session.updateHeight(it); render() })
        heightCheck.setOnCheckedChangeListener { _, checked -> if (!synchronizingChecks) { session.reviewHeight(checked); render() } }
        parkedCheck.setOnCheckedChangeListener { _, checked -> if (!synchronizingChecks) { session.confirmParked(checked); render() } }
        navigationManualCheck.setOnCheckedChangeListener { _, checked -> if (!synchronizingChecks) { session.reviewNavigationManually(checked); render() } }
        if (automotive && Build.VERSION.SDK_INT >= 29) {
            try {
                platform = PlatformVehicleGateway(this) { message -> platformMessage = message; refreshMode() }
                gateway = platform!!
            } catch (_: LinkageError) { platformMessage = "차량 플랫폼 라이브러리 없음 · 입력 잠금" }
        }
        refreshChoices(); refreshMode()
    }

    private fun watcher(change: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { change(s.toString()) }
        override fun afterTextChanged(s: Editable?) { }
    }

    private fun enterDemo() {
        if (automotive) return
        AlertDialog.Builder(this).setTitle("차량 연결 없는 데모")
            .setMessage("실제 차량 상태를 읽지 않습니다. 실제 운전 중에는 사용하지 마세요. 정차·주행 전환은 화면 동작을 확인하기 위한 예시입니다.")
            .setNegativeButton("취소", null).setPositiveButton("데모 시작") { _, _ ->
                if (!automotive) {
                    navigationReader?.stop(); navigationReader = null
                    demo = true; sim.state = VehicleMode.PARKED; gateway = sim
                    if (session.notice.isBlank()) { notice.setText("코엑스 오디토리움 3층 행사"); height.setText(R.string.demo_height) }
                    refreshMode()
                }
            }.show()
    }

    private fun leaveDemo() {
        demo = false; sim.state = VehicleMode.UNKNOWN
        gateway = platform ?: UnconnectedVehicleGateway()
        session.lock(); refreshMode()
    }

    private fun compareNavigationDestination() {
        if (!automotive || !InputAccess.allowed(gateway)) return
        navigationReader?.stop()
        session.updateNavigation(NavigationCheck.checking()); render()
        try {
            navigationReader = NavigationDestinationReader(this) { result ->
                if (InputAccess.allowed(gateway)) { session.updateNavigation(result); render() }
            }
            navigationReader?.start()
        } catch (_: Exception) { session.updateNavigation(NavigationCheck.unavailable()); render() }
        catch (_: LinkageError) { session.updateNavigation(NavigationCheck.unavailable()); render() }
    }

    private fun restoreRecentDestination() {
        if (!InputAccess.allowed(gateway)) return
        val record = recentStore.load() ?: run { render(); return }
        navigationReader?.stop(); navigationReader = null
        if (session.restoreDestination(record.destinationId)) {
            notice.setText(session.notice); height.setText("")
            refreshChoices(); render(); hideKeyboard()
            Toast.makeText(this, "초안을 불러왔습니다. 시설·차량 높이·안전한 주차 상태를 다시 확인하세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshChoices() {
        choices.removeAllViews()
        val decision = session.current
        if (decision.status == "select") {
            choices.addView(textView(decision.message, 14f, warning))
            decision.candidates.forEach { destination ->
                choices.addView(button("${destination.name} · ${destination.floor}${if (destination.id in setOf("conferenceE", "rooms300", "rooms400")) " · 자료 상충" else ""}", true) {
                    if (InputAccess.allowed(gateway)) { session.select(destination.id); refreshChoices(); render() }
                })
            }
        }
    }

    private fun hideKeyboard() {
        currentFocus?.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(notice.windowToken, 0)
    }

    private fun refreshMode() {
        if (!InputAccess.allowed(gateway)) { navigationReader?.stop(); navigationReader = null; session.lock(); hideKeyboard() }
        status.text = if (demo) "DEMO · ${when (sim.state) {
            VehicleMode.PARKED -> "정차 예시 · 입력 가능"
            VehicleMode.DRIVING -> "주행 예시 · 입력 잠금"
            else -> "상태 미확인 예시 · 입력 잠금"
        }} · 실제 차량 미연결" else platformMessage
        demoEntry.visibility = if (!automotive && !demo) View.VISIBLE else View.GONE
        demoControls.visibility = if (demo) View.VISIBLE else View.GONE
        render()
    }

    private fun render() {
        val editable = InputAccess.allowed(gateway)
        form.visibility = if (editable) View.VISIBLE else View.GONE
        lockedNotice.visibility = if (editable) View.GONE else View.VISIBLE
        lockedNotice.text = if (session.confirmed != null)
            "입력과 복사를 잠갔습니다. 마지막으로 확정한 브리핑을 유지합니다. 실제 차량에서는 운영체제가 화면 전체를 제한할 수 있습니다."
        else "차량 상태를 확인하기 전에는 입력할 수 없습니다. 연결된 차량에서 플랫폼이 입력을 허용하거나, 차량 밖에서 데모를 시작하세요."
        synchronizingChecks = true
        heightCheck.isChecked = session.heightReviewed; parkedCheck.isChecked = session.parkedConfirmed
        navigationManualCheck.isChecked = session.navigationReviewedManually
        synchronizingChecks = false
        confirm.isEnabled = session.canConfirm(gateway)
        confirm.alpha = if (confirm.isEnabled) 1f else 0.45f
        copy.visibility = if (editable && session.confirmed != null) View.VISIBLE else View.GONE
        copy.isEnabled = editable && session.parkedConfirmed
        saveRecent.visibility = copy.visibility
        saveRecent.isEnabled = editable && session.parkedConfirmed && session.confirmed != null
        navigationButton.isEnabled = editable && automotive && session.navigationCheck.status != NavigationStatus.CHECKING
        navigationStatus.text = if (!automotive) getString(R.string.navigation_demo_only)
            else session.navigationCheck.message
        navigationManualCheck.visibility = if (session.navigationCheck.status in setOf(NavigationStatus.MATCHED, NavigationStatus.MISMATCH, NavigationStatus.CHECKING)) View.GONE else View.VISIBLE
        val recent = recentStore.load()
        val recentPlace = BriefEngine.destinations.find { it.id == recent?.destinationId }
        recentStatus.text = recentPlace?.let { getString(R.string.recent_destination_message, it.name) } ?: getString(R.string.recent_none)
        restoreRecent.isEnabled = editable && recent != null
        deleteRecent.isEnabled = editable && recent != null
        val decision = if (editable) session.current else session.confirmed
        brief.removeAllViews()
        briefBadge.text = when {
            decision == null -> "아직 확정된 내용 없음"
            decision.status == "source_conflict" -> "자료 확인 필요 · 확정 차단"
            decision.status == "blocked" -> "높이 제한 초과 · 확정 차단"
            session.confirmed != null -> "확정됨 · ${if (demo) "데모" else "현장 안내 우선"}"
            else -> "확인 전"
        }
        if (decision?.status == "ready") {
            val place = decision.destination!!
            brief.addView(textView("코엑스 ${place.name}", 23f).apply { setTypeface(null, Typeface.BOLD) })
            brief.addView(textView(place.floor, 17f, accent))
            addBriefRow("진입 방향", place.gate)
            addBriefRow("공식 안내 주차 구역", place.parking)
            addBriefRow("차량 / 시설 제한", "${decision.height}m / 2.2m")
            if (decision.height == 2.2) brief.addView(textView(decision.message, 16f, warning))
            if (editable) brief.addView(textView("빈자리와 입구 운영 여부는 실시간 정보가 아닙니다.", 13f, muted))
            brief.addView(textView("현장 표지와 안내를 우선하세요.", 14f, accent))
        } else {
            brief.addView(textView(if (decision == null || session.notice.isBlank())
                "입구와 주차 구역,\n한 화면에 정리합니다." else decision.message, 21f))
            if (decision?.status == "source_conflict") {
                brief.addView(textView("일반차량 안내와 2026-02-11 주차장 안내의 추천 구역이 다릅니다. 확인 전에는 주차 동선을 제시하지 않습니다.", 14f, muted))
            } else if (session.notice.isBlank()) {
                brief.addView(textView("장소를 확인하고 차량 전체 높이를 입력하면 브리핑을 만들 수 있습니다.", 14f, muted))
            }
        }
        sources.text = Html.fromHtml("근거: <a href='${BriefEngine.SOURCE}'>코엑스 공식 회의실 주차안내</a><br>자료 확인 ${BriefEngine.VERIFIED_DATE}" +
            if (decision?.status == "source_conflict") "<br>교차 확인: <a href='${BriefEngine.CONFLICT_SOURCE}'>2026-02-11 공식 주차장 공지</a>" else "", Html.FROM_HTML_MODE_LEGACY)
        sources.setLinkTextColor(accent)
        sources.movementMethod = if (editable) LinkMovementMethod.getInstance() else null
        sources.isEnabled = editable
    }

    private fun addBriefRow(label: String, value: String) {
        brief.addView(textView(label, 12f, muted).apply { setPadding(0, dp(15), 0, 0) })
        brief.addView(textView(value, 18f))
    }

    override fun onResume() {
        super.onResume()
        if (automotive) platform?.start()
    }

    override fun onPause() {
        navigationReader?.stop(); navigationReader = null
        platform?.stop()
        platformMessage = "차량 상태 다시 확인 중 · 입력 잠금"
        sim.state = VehicleMode.UNKNOWN
        session.lock()
        refreshMode()
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onRetainNonConfigurationInstance(): Any = session

    override fun onDestroy() {
        navigationReader?.stop(); platform?.stop(); super.onDestroy()
    }
}
