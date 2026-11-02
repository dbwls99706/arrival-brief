package studio.ax.arrival

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import android.view.accessibility.AccessibilityManager
import android.widget.*

/** Parked preparation screen. No distraction-optimized declaration or route-control calls. */
class MainActivity : Activity() {
    private val bg = Color.rgb(240, 245, 250)
    private val surface = Color.WHITE
    private val field = Color.rgb(231, 238, 249)
    private val ink = Color.rgb(32, 54, 80)
    private val accent = Color.rgb(54, 91, 214)
    private val muted = Color.rgb(79, 99, 122)
    private val green = Color.rgb(36, 119, 91)
    private val warning = Color.rgb(126, 76, 13)
    private val briefSurface = Color.rgb(32, 54, 80)
    private val briefInk = Color.rgb(247, 250, 251)
    private val briefMuted = Color.rgb(189, 207, 216)
    private val briefAccent = Color.rgb(183, 226, 244)
    private val briefWarning = Color.rgb(255, 210, 147)
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
    private lateinit var demoStatePanel: LinearLayout
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
    private lateinit var confirmHelp: TextView
    private lateinit var destinationSummary: TextView
    private lateinit var destinationDetail: TextView
    private lateinit var noticePanel: LinearLayout
    private lateinit var recentPanel: LinearLayout
    private lateinit var sourcePanel: LinearLayout
    private lateinit var sourceToggle: Button
    private lateinit var briefingPanel: LinearLayout
    private lateinit var briefHeading: TextView
    private lateinit var mainScroll: ScrollView
    private lateinit var sharedPanel: LinearLayout
    private lateinit var sharedStatus: TextView
    private lateinit var sharedSafeCheck: CheckBox
    private lateinit var sharedImport: Button
    private lateinit var sharedDiscard: Button
    private var sharedMessage = ""
    private lateinit var memoStore: ParkingMemoStore
    private lateinit var memoToggle: Button
    private lateinit var memoPanel: LinearLayout
    private lateinit var memoSafeCheck: CheckBox
    private lateinit var memoInput: EditText
    private lateinit var memoStatus: TextView
    private lateinit var memoSave: Button
    private lateinit var memoDelete: Button
    private lateinit var memoQrExport: Button
    private lateinit var memoQrPanel: LinearLayout
    private lateinit var memoQrImage: ImageView
    private var memoQrBitmap: Bitmap? = null
    private var memoUnlocked = false
    private var memoExpiryTask: Runnable? = null
    private var synchronizingVisit = false

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun textView(value: String, size: Float = 18f, color: Int = ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        setPadding(0, dp(5), 0, dp(5)); setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun shape(color: Int, radius: Int = 22) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun button(label: String, secondary: Boolean = false, dark: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 17f
        setTypeface(null, Typeface.BOLD)
        val textColor = if (dark) briefInk else if (secondary) accent else Color.WHITE
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(if (dark) briefMuted else muted, textColor)))
        val fill = if (dark) Color.rgb(39, 65, 81) else if (secondary) field else accent
        background = RippleDrawable(ColorStateList.valueOf(if (dark) 0x335DB3D9 else 0x22003D5E), shape(fill, 12), null)
        backgroundTintList = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(if (dark) Color.rgb(39, 65, 81) else field, fill))
        minHeight = dp(64); minimumWidth = 0
        stateListAnimator = null; elevation = 0f
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        setOnClickListener { action() }
    }
    private fun card(dark: Boolean = false) = column().apply {
        setPadding(dp(24), dp(22), dp(24), dp(24)); background = shape(if (dark) briefSurface else surface)
    }
    private fun divider(dark: Boolean = false) = View(this).apply {
        setBackgroundColor(if (dark) Color.rgb(61, 86, 100) else Color.rgb(216, 226, 229))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(20); bottomMargin = dp(20) }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun check(label: String) = CheckBox(this).apply {
        text = label; textSize = 17f; setTextColor(ink)
        buttonTintList = ColorStateList.valueOf(accent)
        minHeight = dp(64); setPadding(0, dp(10), 0, dp(10)); isSaveEnabled = false
    }
    private fun edit(hintText: String, multiline: Boolean) = EditText(this).apply {
        hint = hintText; setTextColor(ink); setHintTextColor(muted); textSize = 19f
        background = shape(field, 12); setPadding(dp(14), dp(13), dp(14), dp(13))
        minHeight = dp(if (multiline) 120 else 68)
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
        memoStore = ParkingMemoStore(this)
        window.statusBarColor = bg; window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        mainScroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val root = column().apply { setPadding(dp(20), dp(20), dp(20), dp(24)) }
        mainScroll.addView(root)
        mainScroll.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(mainScroll)
        val wideHeader = resources.configuration.screenWidthDp / resources.configuration.fontScale.coerceAtLeast(1f) >= 840
        val header = LinearLayout(this).apply {
            orientation = if (wideHeader) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_arrival); contentDescription = null
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
        brand.addView(textView("Arrival Brief", 19f).apply { setTypeface(null, Typeface.BOLD) })
        header.addView(brand, if (wideHeader) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
        val headerTools = LinearLayout(this).apply {
            orientation = if (wideHeader) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        status = textView("", 16f, accent).apply { setPadding(0, dp(8), dp(if (wideHeader) 16 else 0), dp(8)) }
        headerTools.addView(status, LinearLayout.LayoutParams(if (wideHeader) dp(320) else -1, -2))

        demoEntry = button("차량 밖에서 데모 시작", true) { enterDemo() }
        demoEntry.visibility = if (automotive) View.GONE else View.VISIBLE
        headerTools.addView(demoEntry, LinearLayout.LayoutParams(if (wideHeader) dp(220) else -1, -2))
        demoControls = column()
        demoControls.addView(button("데모 상태 변경", true) {
            demoStatePanel.visibility = if (demoStatePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        headerTools.addView(demoControls, LinearLayout.LayoutParams(if (wideHeader) dp(200) else -1, -2))
        header.addView(headerTools, LinearLayout.LayoutParams(if (wideHeader) -2 else -1, -2))
        root.addView(header)
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        titleRow.addView(textView("도착을 준비하고, 주차 위치를 기억해요", 29f).apply { setTypeface(null, Typeface.BOLD) })
        titleRow.addView(textView("코엑스 삼성동 · 출발 전 진입문·추천 주차 확인 → 주차 후 위치 기록·QR로 가져가기", 16f, muted),
            LinearLayout.LayoutParams(-1, -2))
        root.addView(titleRow)
        demoStatePanel = column().apply { visibility = View.GONE }
        demoStatePanel.addView(textView("화면 동작 체험 · 실제 차량 미연결", 15f, muted))
        val states = LinearLayout(this)
        listOf("정차" to VehicleMode.PARKED, "주행" to VehicleMode.DRIVING, "미확인" to VehicleMode.UNKNOWN).forEach { (label, mode) ->
            states.addView(button(label, true) {
                if (demo && !automotive) { sim.state = mode; refreshMode() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        }
        demoStatePanel.addView(states)
        demoStatePanel.addView(button("데모 종료", true) { leaveDemo() })
        root.addView(demoStatePanel, LinearLayout.LayoutParams(if (wideHeader) dp(400) else -1, -2).apply {
            gravity = Gravity.END
        })
        lockedNotice = textView("", 18f, muted).apply {
            setPadding(dp(20), dp(16), dp(20), dp(16)); background = shape(surface)
        }
        root.addView(lockedNotice, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        sharedPanel = column().apply { setPadding(dp(4), dp(12), dp(4), dp(12)) }
        sharedStatus = textView("", 17f, accent)
        sharedPanel.addView(sharedStatus)
        sharedSafeCheck = check("공유한 안내를 가져오기 전, 안전하게 주차했어요.")
        sharedPanel.addView(sharedSafeCheck)
        sharedImport = button("공유한 행사 안내 가져오기", true) {
            val incoming = session.pendingSharedNotice
            if (incoming != null && ParkingMemoPolicy.allowed(gateway, sharedSafeCheck.isChecked)) {
                session.pendingSharedNotice = null; sharedMessage = ""
                sharedSafeCheck.isChecked = false
                navigationReader?.stop(); navigationReader = null
                notice.setText(incoming); height.setText("")
                noticePanel.visibility = View.VISIBLE
                refreshChoices(); render()
                Toast.makeText(this, "안내를 가져왔습니다. 시설·차량 높이·주차 상태를 다시 확인하세요.", Toast.LENGTH_LONG).show()
            }
        }
        sharedDiscard = button("공유한 안내 지우기", true) {
            if (InputAccess.allowed(gateway)) {
                session.pendingSharedNotice = null; sharedMessage = ""; sharedSafeCheck.isChecked = false; render()
            }
        }
        sharedPanel.addView(sharedImport); sharedPanel.addView(sharedDiscard); root.addView(sharedPanel)
        sharedSafeCheck.setOnCheckedChangeListener { _, _ ->
            if (!synchronizingVisit) renderVisitTools(InputAccess.allowed(gateway))
        }

        val workspace = LinearLayout(this).apply {
            orientation = if (wideHeader) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }
        root.addView(workspace, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        form = card()
        form.addView(textView("01  방문 장소", 16f, accent).apply { setTypeface(null, Typeface.BOLD) })
        destinationSummary = textView("방문할 회의실", 25f).apply { setTypeface(null, Typeface.BOLD) }
        destinationDetail = textView("목록에서 고르거나 행사 안내문을 붙여넣으세요.", 16f, muted)
        form.addView(destinationSummary); form.addView(destinationDetail)
        form.addView(button("회의실 선택", true) { showDestinationPicker() })
        form.addView(button("행사 안내문으로 찾기", true) {
            if (InputAccess.allowed(gateway)) {
                noticePanel.visibility = if (noticePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (noticePanel.visibility == View.VISIBLE) notice.requestFocus()
            }
        })
        noticePanel = column().apply { visibility = if (session.notice.isBlank()) View.GONE else View.VISIBLE }
        noticePanel.addView(textView("이름·전화번호는 지우고 장소 문장만 입력하세요.", 16f, muted))
        notice = edit("예: 코엑스 오디토리움 3층", true).apply {
            setText(session.notice); contentDescription = "행사 장소 안내"
        }
        noticePanel.addView(notice); form.addView(noticePanel)
        choices = column(); form.addView(choices)

        form.addView(divider())
        form.addView(textView("02  차량 확인", 16f, green).apply { setTypeface(null, Typeface.BOLD) })
        form.addView(textView("차량 전체 높이", 23f).apply { setTypeface(null, Typeface.BOLD) })
        form.addView(textView("시설 제한 2.2m · 루프박스와 적재물 포함", 16f, muted))
        height = edit("예: 1.65", false).apply {
            setText(session.heightText)
            contentDescription = "루프박스와 적재물을 포함한 차량 전체 높이, 미터"
        }
        val heightRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heightRow.addView(height, LinearLayout.LayoutParams(0, -2, 1f))
        heightRow.addView(textView("m", 22f, muted).apply { setPadding(dp(16), 0, dp(8), 0) })
        form.addView(heightRow)
        heightCheck = check("적재물을 포함한 전체 높이를 확인했습니다.")
        form.addView(heightCheck)

        form.addView(textView("방문 시설 대조", 18f).apply { setTypeface(null, Typeface.BOLD) })
        navigationStatus = textView("", 16f, muted)
        form.addView(navigationStatus)
        navigationButton = button("차량 내비 목적지 대조", true) { compareNavigationDestination() }
        form.addView(navigationButton)
        navigationManualCheck = check("이 안내가 현재 유효하며, 선택한 행사장이 실제 방문 장소임을 확인했습니다.")
        form.addView(navigationManualCheck)
        form.addView(textView("주최 측의 변경·취소·임시 진입·주차 제한 안내가 있으면 확정하지 마세요.", 16f, warning))
        parkedCheck = check("안전하게 주차한 상태입니다.")
        form.addView(parkedCheck)
        confirm = button("도착 브리핑 확정") {
            if (session.confirm(gateway)) {
                hideKeyboard(); render()
                val justConfirmed = session.confirmed
                briefingPanel.post {
                    if (session.confirmed === justConfirmed && InputAccess.allowed(gateway)) {
                        val destinationY = workspace.top + if (workspace.orientation == LinearLayout.VERTICAL) briefingPanel.top else 0
                        val accessibility = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                        if (accessibility.isEnabled) {
                            mainScroll.scrollTo(0, destinationY)
                            briefHeading.announceForAccessibility(briefHeading.contentDescription)
                        } else mainScroll.smoothScrollTo(0, destinationY)
                    }
                }
                Toast.makeText(this, "도착 브리핑을 확정했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        form.addView(confirm)
        confirmHelp = textView("", 16f, muted)
        form.addView(confirmHelp)

        briefingPanel = card(dark = true)
        briefHeading = textView("03  도착 브리핑", 16f, briefMuted).apply {
            setTypeface(null, Typeface.BOLD); isAccessibilityHeading = true
        }
        briefingPanel.addView(briefHeading)
        briefBadge = textView("아직 확정 전", 17f, briefWarning)
        briefingPanel.addView(briefBadge)
        brief = column(); briefingPanel.addView(brief)
        copy = button("브리핑 복사", secondary = true, dark = true) {
            if (session.canExport(gateway)) session.confirmed?.let {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Arrival Brief", BriefEngine.export(it)))
                Toast.makeText(this, "브리핑을 복사했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        briefingPanel.addView(copy)
        saveRecent = button("목적지 24시간 보관", secondary = true, dark = true) {
            if (session.canExport(gateway)) session.confirmed?.destination?.let {
                recentStore.save(it.id); render()
                Toast.makeText(this, "목적지 1건을 보관했습니다. 안내문·높이는 저장하지 않습니다.", Toast.LENGTH_SHORT).show()
            }
        }
        briefingPanel.addView(saveRecent)
        if (workspace.orientation == LinearLayout.HORIZONTAL) {
            workspace.addView(form, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(20) })
            workspace.addView(briefingPanel, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            workspace.addView(form, LinearLayout.LayoutParams(-1, -2))
            workspace.addView(briefingPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        }

        recentPanel = column().apply { setPadding(dp(4), dp(12), dp(4), 0) }
        recentStatus = textView("", 16f, muted)
        recentPanel.addView(recentStatus)
        val recentActions = LinearLayout(this)
        restoreRecent = button("저장 초안으로 바꾸기", true) { restoreRecentDestination() }
        deleteRecent = button("삭제", true) {
            if (InputAccess.allowed(gateway)) { recentStore.clear(); render() }
        }
        recentActions.addView(restoreRecent, LinearLayout.LayoutParams(0, -2, 3f).apply { marginEnd = dp(8) })
        recentActions.addView(deleteRecent, LinearLayout.LayoutParams(0, -2, 1f))
        recentPanel.addView(recentActions); root.addView(recentPanel)

        memoToggle = button("주차 위치 기록", true) {
            if (InputAccess.allowed(gateway)) {
                clearMemoQr()
                memoPanel.visibility = if (memoPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                memoToggle.text = if (memoPanel.visibility == View.VISIBLE) "주차 위치 기록 접기" else "주차 위치 기록"
                renderVisitTools(true)
            }
        }
        root.addView(memoToggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        memoPanel = column().apply { visibility = View.GONE; setPadding(dp(4), dp(8), dp(4), dp(12)) }
        memoPanel.addView(textView("주차한 층·구역·기둥 번호를 적어두세요. 저장한 뒤 QR을 스캔해 휴대폰에 직접 가져갈 수 있습니다.", 20f).apply { setTypeface(null, Typeface.BOLD) })
        memoPanel.addView(textView("직접 적는 메모입니다. 실제 위치를 감지하거나 길을 안내하지 않습니다.", 16f, muted))
        memoSafeCheck = check("차량을 안전하게 주차했고, 위치 메모를 확인할게요.")
        memoPanel.addView(memoSafeCheck)
        memoInput = edit("예: B3 블루존 E12 기둥", false).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(ParkingMemoPolicy.MAX_LENGTH))
            contentDescription = "직접 적는 주차 위치 메모, 최대 64자"
        }
        memoPanel.addView(memoInput)
        memoStatus = textView("주차 확인 후 메모를 열 수 있습니다.", 16f, muted).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        memoPanel.addView(memoStatus)
        memoSave = button("주차 위치 24시간 기억", true) {
            if (ParkingMemoPolicy.allowed(gateway, memoSafeCheck.isChecked)) {
                clearMemoQr()
                val value = memoInput.text.toString()
                val savedAt = System.currentTimeMillis()
                if (ParkingMemoPolicy.create(value, savedAt) == null) {
                    memoStatus.setText(R.string.memo_invalid)
                } else if (memoStore.save(value, savedAt)) {
                    memoStatus.setText(R.string.memo_saved)
                    memoInput.setText(value.trim())
                    memoDelete.isEnabled = true
                    updateMemoQrAvailability()
                    scheduleMemoExpiry(savedAt + ParkingMemoPolicy.TTL_MS)
                } else memoStatus.text = "메모를 저장하지 못했습니다. 다시 시도해주세요."
            }
        }
        memoDelete = button("주차 위치 삭제", true) {
            if (ParkingMemoPolicy.allowed(gateway, memoSafeCheck.isChecked)) {
                clearMemoQr()
                if (memoStore.clear()) { cancelMemoExpiry(); memoInput.setText(""); memoStatus.text = "저장한 주차 위치를 지웠습니다."; memoDelete.isEnabled = false }
                else memoStatus.text = "삭제하지 못했습니다. 다시 시도해주세요."
            }
        }
        memoPanel.addView(memoSave); memoPanel.addView(memoDelete)
        memoQrExport = button("휴대폰으로 가져가기", true) { showMemoQr() }
        memoPanel.addView(memoQrExport)
        memoPanel.addView(textView("QR은 저장한 메모만 담습니다. 휴대폰 카메라로 스캔한 후 직접 복사·저장하세요. 자동 동기화가 아닙니다.", 16f, muted))
        memoPanel.addView(textView("휴대폰에 복사한 내용에는 이 앱의 24시간 만료·삭제가 적용되지 않습니다.", 16f, warning))
        memoQrPanel = column().apply { visibility = View.GONE }
        memoQrPanel.addView(textView("휴대폰 카메라로 스캔하세요", 20f).apply { setTypeface(null, Typeface.BOLD) })
        val qrSize = dp((resources.configuration.screenWidthDp - 56).coerceIn(256, 320))
        memoQrImage = ImageView(this).apply {
            contentDescription = "저장한 주차 위치를 휴대폰으로 가져가는 QR"
            setBackgroundColor(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isSaveEnabled = false
        }
        memoQrPanel.addView(memoQrImage, LinearLayout.LayoutParams(qrSize, qrSize).apply { gravity = Gravity.CENTER_HORIZONTAL })
        memoQrPanel.addView(button("QR 닫기", true) { clearMemoQr() })
        memoPanel.addView(memoQrPanel)
        memoPanel.addView(textView("메모 1건만 기기에 저장하며 서버 전송·백업하지 않습니다. 만료 뒤 다음 확인 시 삭제됩니다.", 14f, muted))
        root.addView(memoPanel)
        memoSafeCheck.setOnCheckedChangeListener { _, _ ->
            if (!synchronizingVisit) { clearMemoQr(); memoUnlocked = false; renderVisitTools(InputAccess.allowed(gateway)) }
        }
        memoInput.addTextChangedListener(watcher { clearMemoQr(); updateMemoQrAvailability() })

        sourceToggle = button("출처 · 지원 범위 보기", true) {
            if (InputAccess.allowed(gateway)) {
                sourcePanel.visibility = if (sourcePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                sourceToggle.text = if (sourcePanel.visibility == View.VISIBLE) "출처 · 지원 범위 접기" else "출처 · 지원 범위 보기"
            }
        }
        root.addView(sourceToggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        sourcePanel = column().apply { visibility = View.GONE; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        sources = textView("", 16f, muted); sourcePanel.addView(sources)
        sourcePanel.addView(textView("현장 표지와 안내를 우선하세요. 빈자리, 입구 운영 여부, 실내 동선과 하차 지점은 제공하지 않습니다.", 16f, muted))
        sourcePanel.addView(textView("안내문·높이는 현재 세션에만 유지됩니다. 직접 보관한 목적지와 주차 메모 각 1건은 저장 후 24시간 뒤 만료됩니다.", 16f, muted))
        sourcePanel.addView(textView("내비 대조에는 시설명·주소만 사용합니다. 원문을 보관하거나 내비 경로를 변경하지 않습니다.", 16f, muted))
        sourcePanel.addView(textView("", 14f, muted).apply {
            text = Html.fromHtml("QR 생성: <a href='https://github.com/zxing/zxing/blob/zxing-3.5.4/LICENSE'>ZXing · Apache License 2.0 보기</a>", Html.FROM_HTML_MODE_LEGACY)
            setLinkTextColor(accent); movementMethod = LinkMovementMethod.getInstance()
        })
        sourcePanel.addView(textView("독립 출품 앱 · 차량 실증 전. 같은 Android 기기의 텍스트 공유를 받을 수 있으며 휴대폰에서 차량으로 자동 전송하지 않습니다.", 16f, muted))
        root.addView(sourcePanel)

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
        if (state == null) receiveSharedNotice(intent)
        else clearLaunchIntentPayload()
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

    private fun showDestinationPicker() {
        if (!InputAccess.allowed(gateway)) return
        val destinations = BriefEngine.destinations
        val labels = destinations.map {
            "${it.name} · ${it.floor}" + if (it.id in setOf("conferenceE", "rooms300", "rooms400")) " · 자료 확인 필요" else ""
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("코엑스 삼성동 회의실")
            .setItems(labels) { _, index ->
                if (InputAccess.allowed(gateway)) {
                    if (session.notice.isBlank()) notice.setText("코엑스")
                    session.select(destinations[index].id)
                    noticePanel.visibility = if (session.notice == "코엑스") View.GONE else View.VISIBLE
                    refreshChoices(); render(); hideKeyboard()
                }
            }.setNegativeButton("취소", null).show()
    }

    private fun restoreRecentDestination() {
        if (!InputAccess.allowed(gateway)) return
        val record = recentStore.load() ?: run { render(); return }
        navigationReader?.stop(); navigationReader = null
        if (session.restoreDestination(record.destinationId)) {
            notice.setText(session.notice); height.setText("")
            refreshChoices(); render(); hideKeyboard()
            Toast.makeText(this, "저장 초안으로 바꿨습니다. 시설·차량 높이·안전한 주차 상태를 다시 확인하세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshChoices() {
        choices.removeAllViews()
        val decision = session.current
        if (decision.status == "select") {
            choices.addView(textView(decision.message, 17f, warning))
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
        status.text = if (demo) "데모 · ${when (sim.state) {
            VehicleMode.PARKED -> "정차"
            VehicleMode.DRIVING -> "주행 · 입력 잠금"
            else -> "미확인 · 입력 잠금"
        }} · 실제 차량 미연결" else platformMessage
        demoEntry.visibility = if (!automotive && !demo) View.VISIBLE else View.GONE
        demoControls.visibility = if (demo) View.VISIBLE else View.GONE
        if (!demo) demoStatePanel.visibility = View.GONE
        render()
        if (!InputAccess.allowed(gateway) && session.confirmed != null) briefingPanel.post {
            val workspace = briefingPanel.parent as View
            mainScroll.scrollTo(0, workspace.top + briefingPanel.top)
        }
    }

    private fun render() {
        val editable = InputAccess.allowed(gateway)
        renderVisitTools(editable)
        form.visibility = if (editable) View.VISIBLE else View.GONE
        lockedNotice.visibility = if (editable) View.GONE else View.VISIBLE
        lockedNotice.text = if (session.confirmed != null)
            "입력 잠금 · 마지막으로 확정한 브리핑입니다. 현장 안내를 우선하세요."
        else if (automotive) "차량 상태 확인 중입니다. 안전하게 주차하고 플랫폼의 입력 허용을 기다려 주세요."
        else "차량 상태를 확인하기 전에는 입력할 수 없습니다. 차량 밖에서는 데모로 화면을 살펴볼 수 있습니다."
        synchronizingChecks = true
        heightCheck.isChecked = session.heightReviewed; parkedCheck.isChecked = session.parkedConfirmed
        navigationManualCheck.isChecked = session.navigationReviewedManually
        synchronizingChecks = false
        confirm.isEnabled = session.canConfirm(gateway)
        confirm.text = if (session.confirmed != null && !confirm.isEnabled) "시설·정차 상태 다시 확인" else if (session.confirmed != null) "브리핑 확정됨" else "도착 브리핑 확정"
        confirmHelp.text = remainingConfirmation()
        copy.visibility = if (editable && session.confirmed != null) View.VISIBLE else View.GONE
        copy.isEnabled = session.canExport(gateway)
        saveRecent.visibility = copy.visibility
        saveRecent.isEnabled = session.canExport(gateway)
        navigationButton.visibility = if (automotive) View.VISIBLE else View.GONE
        navigationButton.isEnabled = editable && automotive && session.navigationCheck.status != NavigationStatus.CHECKING
        navigationStatus.text = if (!automotive) "내비 미연결 · 방문 시설을 직접 확인하세요."
            else session.navigationCheck.message
        navigationManualCheck.visibility = View.VISIBLE
        navigationManualCheck.text = session.current.destination?.let { "‘${it.name}’이 현재 유효한 행사의 실제 방문 장소임을 확인했습니다." }
            ?: "이 안내가 현재 유효하며, 선택한 행사장이 실제 방문 장소임을 확인했습니다."
        val recent = recentStore.load()
        val recentPlace = BriefEngine.destinations.find { it.id == recent?.destinationId }
        recentPanel.visibility = if (editable && recentPlace != null) View.VISIBLE else View.GONE
        recentStatus.text = recentPlace?.let { "보관한 목적지 · ${it.name} ${it.floor}\n현재 안내문을 저장한 장소의 새 초안으로 바꿉니다. 높이·행사장·주차 상태를 다시 확인해야 합니다. 보관 기한은 24시간입니다." } ?: ""
        restoreRecent.isEnabled = editable && recent != null
        deleteRecent.isEnabled = editable && recent != null
        sourceToggle.visibility = if (editable) View.VISIBLE else View.GONE
        if (!editable) {
            sourcePanel.visibility = View.GONE
            sourceToggle.text = "출처 · 지원 범위 보기"
        }
        val current = session.current
        destinationSummary.text = current.destination?.name ?: if (session.notice.isBlank()) "방문할 회의실" else "장소를 확인해 주세요"
        destinationDetail.text = current.destination?.let { "코엑스 삼성동 · ${it.floor}" }
            ?: if (session.notice.isBlank()) "목록에서 고르거나 행사 안내문을 붙여넣으세요." else "안내문에 적힌 세부 장소를 확인합니다."
        val decision = if (editable) current else session.confirmed
        val confirmed = session.confirmed != null
        val contentInk = if (confirmed) briefInk else ink
        val contentMuted = if (confirmed) briefMuted else muted
        val contentAccent = if (confirmed) briefAccent else accent
        val contentWarning = if (confirmed) briefWarning else warning
        briefingPanel.background = shape(if (confirmed) briefSurface else Color.rgb(227, 237, 250))
        briefHeading.setTextColor(contentMuted)
        briefHeading.contentDescription = session.confirmed?.destination?.let {
            "도착 브리핑을 확정했습니다. ${it.name}, ${it.floor}."
        } ?: "도착 브리핑, 아직 확정 전"
        brief.removeAllViews()
        briefBadge.text = when {
            decision == null -> "아직 확정 전"
            decision.status == "source_conflict" -> "자료 확인 필요 · 확정 불가"
            decision.status == "blocked" -> "높이 제한 초과 · 확정 불가"
            session.confirmed != null -> "확정된 브리핑${if (!editable) " · 입력 잠금" else ""}${if (demo) " · 데모" else ""}"
            else -> "미리보기 · 아직 확정 전"
        }
        briefBadge.setTextColor(if (confirmed) contentAccent else contentWarning)
        // A preparation diagram explains the product without inventing a geographic route.
        // Actual entrance and parking labels remain behind the existing confirmation gate.
        if ((editable || (!automotive && !demo && session.confirmed == null)) && decision?.status != "source_conflict" && decision?.status != "blocked") {
            brief.addView(ArrivalSceneView(this, confirmed), LinearLayout.LayoutParams(-1, dp(190)))
            brief.addView(textView("준비 흐름도 · 실제 지도나 주행 경로가 아닙니다.", 13f, contentMuted))
        }
        if (decision?.status == "ready" && confirmed) {
            val place = decision.destination!!
            brief.addView(textView("${place.name}  ${place.floor}", 27f, contentInk).apply { setTypeface(null, Typeface.BOLD) })
            brief.addView(textView("코엑스 삼성동", 17f, contentMuted))
            brief.addView(divider(dark = confirmed))
            val gate = place.gate.split(" · ", limit = 2)
            addBriefRow("진입문", gate.first(), gate.getOrNull(1))
            addBriefRow("공식 안내 주차 구역", place.parking)
            brief.addView(divider(dark = confirmed))
            brief.addView(textView("차량 ${decision.height}m  /  시설 제한 2.2m", 18f, contentMuted))
            if (decision.height == 2.2) brief.addView(textView(decision.message, 18f, contentWarning))
            brief.addView(textView("현장 표지와 안내를 우선하세요.", 18f, contentAccent))
            if (editable) brief.addView(textView("빈자리·입구 운영 여부는 실시간 정보가 아닙니다.", 16f, contentMuted))
        } else if (decision?.status == "ready") {
            val place = decision.destination!!
            brief.addView(textView("${place.name}  ${place.floor}", 26f, contentInk).apply { setTypeface(null, Typeface.BOLD) })
            brief.addView(textView("코엑스 삼성동", 17f, contentMuted))
            brief.addView(divider())
            brief.addView(textView("출발 전, 세 가지만 확인해요.", 25f, contentInk).apply { setTypeface(null, Typeface.BOLD) })
            listOf(
                session.heightReviewed to "전체 높이 ${decision.height}m · 제한 2.2m",
                (session.navigationAllowsConfirmation() && session.navigationReviewedManually) to "현재 유효한 안내와 실제 행사장 확인",
                session.parkedConfirmed to "안전하게 주차한 상태 확인"
            ).forEach { (done, label) ->
                brief.addView(textView("${if (done) "✓" else "○"}  $label", 18f, if (done) green else contentMuted).apply {
                    setPadding(0, dp(12), 0, dp(12))
                })
            }
            if (decision.height == 2.2) brief.addView(textView(decision.message, 18f, contentWarning))
            brief.addView(textView("확정 전에는 입구·주차 안내를 사용하지 않습니다.", 16f, contentMuted))
        } else {
            val place = decision?.destination
            if (place != null) {
                brief.addView(textView("${place.name}  ${place.floor}", 26f, contentInk).apply { setTypeface(null, Typeface.BOLD) })
                brief.addView(textView("코엑스 삼성동", 17f, contentMuted))
                brief.addView(divider(dark = confirmed))
            }
            brief.addView(textView(if (decision == null || session.notice.isBlank())
                "장소를 고르면,\n도착 준비가 시작돼요." else decision.message, if (place == null) 25f else 22f, contentInk))
            val reviewNotes = decision?.let {
                (it.eventNotes + it.facilityNotes + it.entryNotes + it.unparsedParkingNotes).distinct()
            }.orEmpty()
            if (editable && reviewNotes.isNotEmpty()) {
                brief.addView(textView("확인이 필요한 원문", 18f, contentWarning).apply { setTypeface(null, Typeface.BOLD) })
                reviewNotes.take(3).forEach { note ->
                    val excerpt = if (note.length > 180) note.take(180) + "… (인용 일부)" else note
                    brief.addView(textView("“$excerpt”", 17f, contentInk))
                }
                if (reviewNotes.size > 3) brief.addView(textView("외 ${reviewNotes.size - 3}개 문장은 입력한 안내문에서 확인하세요.", 15f, contentMuted))
            }
            if (decision?.status == "source_conflict") {
                brief.addView(textView("공식 자료 비교 · 현재 사용할 주차 안내가 아닙니다.", 18f, contentWarning))
                ParkingSourceConflicts.forDestination(place?.id).forEach { evidence ->
                    brief.addView(textView("", 17f, contentInk).apply {
                        text = Html.fromHtml("<a href='${evidence.url}'>${evidence.title} ↗</a>", Html.FROM_HTML_MODE_LEGACY)
                        setLinkTextColor(contentAccent)
                        movementMethod = if (editable) LinkMovementMethod.getInstance() else null
                    })
                    brief.addView(textView(evidence.recommendation, 23f, contentInk).apply { setTypeface(null, Typeface.BOLD) })
                    val date = evidence.publishedAt?.let { "공지 $it · " }.orEmpty() + "자료 확인 ${BriefEngine.VERIFIED_DATE}"
                    brief.addView(textView(date, 14f, contentMuted))
                }
                brief.addView(textView("시설의 최신 안내와 현장 표지를 확인할 때까지 확정을 보류합니다.", 18f, contentMuted))
            } else if (session.notice.isBlank() || decision == null) {
                brief.addView(divider(dark = confirmed))
                listOf("행사 안내에서 장소 찾기", "내 차의 높이와 방문 시설 확인", "진입문·주차 구역을 한 장으로").forEach {
                    brief.addView(textView(it, 18f, contentMuted).apply { setPadding(0, dp(10), 0, dp(10)) })
                }
            }
        }
        sources.text = Html.fromHtml("근거: <a href='${BriefEngine.SOURCE}'>코엑스 공식 회의실 주차안내</a><br>자료 확인 ${BriefEngine.VERIFIED_DATE}" +
            if (decision?.status == "source_conflict") "<br>교차 확인: <a href='${BriefEngine.CONFLICT_SOURCE}'>2026-02-11 공식 주차장 공지</a>" else "", Html.FROM_HTML_MODE_LEGACY)
        sources.setLinkTextColor(accent)
        sources.movementMethod = if (editable) LinkMovementMethod.getInstance() else null
        sources.isEnabled = editable
    }

    /** Consume only a plain text extra; ignore ClipData/URI grants and never open external URIs. */
    private fun receiveSharedNotice(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return
        val accepted = try {
            if (incoming.hasExtra(Intent.EXTRA_STREAM) || incoming.data != null) null else
                SharedNoticePolicy.accept(incoming.action, incoming.type, incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
        } catch (_: RuntimeException) { null }
        session.pendingSharedNotice = accepted
        sharedMessage = if (accepted == null) "텍스트 안내만 받을 수 있습니다. 10,000자 이내의 장소 문장을 공유해주세요." else ""
        if (::sharedSafeCheck.isInitialized) sharedSafeCheck.isChecked = false
        clearLaunchIntentPayload()
    }

    /** Retain framework launch identity across recreation; discard transfer data and URI grants. */
    private fun clearLaunchIntentPayload() {
        val clean = Intent(intent).apply {
            replaceExtras(null as Bundle?)
            setDataAndType(null, type)
            clipData = null
            selector = null
            flags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()
        }
        setIntent(clean)
    }

    override fun onNewIntent(incoming: Intent) {
        super.onNewIntent(incoming)
        receiveSharedNotice(incoming)
        render()
    }

    private fun renderVisitTools(editable: Boolean) {
        if (!::memoInput.isInitialized) return
        synchronizingVisit = true
        if (!editable) {
            clearMemoQr()
            sharedSafeCheck.isChecked = false
            memoSafeCheck.isChecked = false
            memoUnlocked = false
            cancelMemoExpiry()
            memoInput.setText("")
            memoPanel.visibility = View.GONE
            memoToggle.text = "주차 위치 기록"
        }
        synchronizingVisit = false
        val hasShared = session.pendingSharedNotice != null
        sharedPanel.visibility = if (hasShared || sharedMessage.isNotBlank()) View.VISIBLE else View.GONE
        sharedStatus.text = when {
            sharedMessage.isNotBlank() -> sharedMessage
            editable -> "공유한 행사 안내 1건이 도착했어요. 가져오면 현재 초안을 바꾸며 다시 확인해야 합니다."
            else -> "공유한 행사 안내 1건 대기 중 · 안전한 주차와 입력 허용 후 가져올 수 있습니다."
        }
        sharedSafeCheck.visibility = if (editable && hasShared) View.VISIBLE else View.GONE
        sharedImport.visibility = sharedSafeCheck.visibility
        sharedImport.isEnabled = hasShared && ParkingMemoPolicy.allowed(gateway, sharedSafeCheck.isChecked)
        sharedDiscard.visibility = if (editable) View.VISIBLE else View.GONE
        sharedDiscard.isEnabled = editable
        memoToggle.visibility = if (editable) View.VISIBLE else View.GONE
        val allowed = ParkingMemoPolicy.allowed(gateway, memoSafeCheck.isChecked)
        memoInput.visibility = if (allowed) View.VISIBLE else View.GONE
        memoSave.visibility = memoInput.visibility; memoDelete.visibility = memoInput.visibility
        memoQrExport.visibility = memoInput.visibility
        memoInput.isEnabled = allowed; memoSave.isEnabled = allowed
        if (!allowed) {
            clearMemoQr()
            memoInput.setText(""); memoStatus.text = "주차 확인 후 메모를 열 수 있습니다."
            memoUnlocked = false
            cancelMemoExpiry()
        } else if (!memoUnlocked) {
            val saved = memoStore.load()
            memoInput.setText(saved?.text.orEmpty())
            memoStatus.text = if (saved == null) "기억한 주차 위치가 없습니다. 새 위치를 적어주세요."
                else "직접 기억한 주차 위치입니다. 현장 표지와 비교해 확인하세요."
            memoDelete.isEnabled = saved != null
            memoUnlocked = true
            saved?.let { scheduleMemoExpiry(it.savedAt + ParkingMemoPolicy.TTL_MS) }
        }
        updateMemoQrAvailability()
    }

    private fun updateMemoQrAvailability() {
        if (!::memoQrExport.isInitialized) return
        val allowed = ParkingMemoPolicy.allowed(gateway, memoSafeCheck.isChecked)
        val record = if (allowed) memoStore.load() else null
        memoQrExport.isEnabled = ParkingMemoQr.canDisplay(record, memoInput.text.toString(), gateway, memoSafeCheck.isChecked, System.currentTimeMillis())
        if (!memoQrExport.isEnabled) clearMemoQr()
    }

    private fun showMemoQr() {
        clearMemoQr()
        if (!ParkingMemoPolicy.allowed(gateway, memoSafeCheck.isChecked)) return
        val now = System.currentTimeMillis()
        val record = memoStore.load(now)
        if (!ParkingMemoQr.canDisplay(record, memoInput.text.toString(), gateway, memoSafeCheck.isChecked, now)) {
            updateMemoQrAvailability(); return
        }
        try {
            val size = memoQrImage.layoutParams.width.coerceIn(256, 1024)
            val matrix = ParkingMemoQr.encode(record!!, now, size) ?: return
            val colors = IntArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
            }
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(colors, 0, matrix.width, 0, 0, matrix.width, matrix.height)
            colors.fill(0); matrix.clear()
            memoQrBitmap = bitmap
            memoQrImage.setImageBitmap(bitmap)
            memoQrPanel.visibility = View.VISIBLE
            hideKeyboard()
            memoQrPanel.post {
                if (memoQrBitmap === bitmap && memoQrPanel.visibility == View.VISIBLE &&
                    ParkingMemoPolicy.allowed(gateway, memoSafeCheck.isChecked)) {
                    mainScroll.scrollTo(0, memoPanel.top + memoQrPanel.top)
                    memoQrImage.announceForAccessibility("주차 위치 QR을 표시했습니다. 휴대폰 카메라로 스캔하세요.")
                }
            }
        } catch (_: Exception) {
            clearMemoQr()
            memoStatus.setText(R.string.memo_qr_error)
        }
    }

    private fun clearMemoQr() {
        if (::memoQrPanel.isInitialized) memoQrPanel.visibility = View.GONE
        if (::memoQrImage.isInitialized) memoQrImage.setImageDrawable(null)
        // The renderer can still own a display-list reference; leave reclamation to Android.
        memoQrBitmap?.let { if (!it.isRecycled && it.isMutable) it.eraseColor(Color.WHITE) }
        memoQrBitmap = null
    }

    private fun cancelMemoExpiry() {
        memoExpiryTask?.let { mainScroll.removeCallbacks(it) }
        memoExpiryTask = null
    }

    private fun scheduleMemoExpiry(expiresAt: Long) {
        cancelMemoExpiry()
        val task = Runnable {
            clearMemoQr()
            memoUnlocked = false
            renderVisitTools(InputAccess.allowed(gateway))
        }
        memoExpiryTask = task
        mainScroll.postDelayed(task, (expiresAt - System.currentTimeMillis()).coerceAtLeast(1L))
    }

    private fun remainingConfirmation(): String {
        val decision = session.current
        return when {
            session.confirmed != null && session.canConfirm(gateway) -> "확정했습니다. 내용을 바꾸면 다시 확인해야 합니다."
            session.notice.isBlank() -> "회의실을 먼저 선택해 주세요."
            decision.status != "ready" -> decision.message
            session.navigationCheck.status == NavigationStatus.MISMATCH -> "내비 목적지가 다릅니다. 목적지를 수정하고 다시 대조하세요."
            session.navigationCheck.status == NavigationStatus.CHECKING -> "차량 내비 목적지를 확인하고 있습니다."
            !session.navigationReviewedManually -> "이 안내가 현재 유효하고, 선택한 행사장이 실제 방문 장소인지 확인해 주세요."
            !session.navigationAllowsConfirmation() -> "방문 시설을 직접 확인하거나 차량 내비와 대조해 주세요."
            !session.heightReviewed -> "루프박스와 적재물을 포함한 높이 확인이 남았습니다."
            !session.parkedConfirmed -> "안전하게 주차한 상태인지 확인해 주세요."
            else -> "확인을 마쳤습니다. 브리핑을 확정할 수 있습니다."
        }
    }

    private fun addBriefRow(label: String, value: String, detail: String? = null) {
        brief.addView(textView(label, 17f, briefMuted).apply { setPadding(0, dp(12), 0, dp(4)) })
        brief.addView(textView(value, 32f, briefInk).apply { setTypeface(null, Typeface.BOLD) })
        if (detail != null) brief.addView(textView(detail, 18f, briefMuted))
    }

    override fun onResume() {
        super.onResume()
        if (automotive) platform?.start()
    }

    override fun onPause() {
        clearMemoQr()
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
        clearMemoQr(); cancelMemoExpiry(); navigationReader?.stop(); platform?.stop(); super.onDestroy()
    }
}
