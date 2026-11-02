package studio.ax.arrival

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import android.widget.CheckBox
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real Activity/Intent lifecycle and rendered app controls, not vehicle stubs. */
@RunWith(AndroidJUnit4::class)
class VisitFlowInstrumentedTest {
    @get:Rule val testName = TestName()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun clearState() {
        context.getSharedPreferences("parking_memo", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("recent_destination", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun close() {
        if (::scenario.isInitialized) {
            capture(testName.methodName)
            scenario.close()
        }
    }
    private fun capture(label: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val output = File(context.getExternalFilesDir(null), "arrival-$label.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        android.util.Log.i("ArrivalEvidence", "Runtime test screenshot: ${output.absolutePath}")
    }
    private fun share(text: String) = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    private fun enterDemo() {
        onView(withText("차량 밖에서 데모 시작")).perform(scrollTo(), click())
        onView(withText("데모 시작")).perform(click())
    }
    @Test fun sharedIntentWaitsForParkingAndNeverAutoConfirms() {
        scenario = ActivityScenario.launch(share("코엑스 스튜디오 159 2층 행사"))
        scenario.onActivity { assertNull(it.intent.extras); assertNull(it.intent.clipData); assertNull(it.intent.data) }
        onView(withSubstring("공유한 행사 안내 1건 대기 중")).check(matches(isDisplayed()))
        onView(withText("공유한 행사 안내 가져오기")).check(matches(not(isDisplayed())))
        capture("shared-pending-locked")
        enterDemo()
        onView(withText("공유한 행사 안내 가져오기")).check(matches(not(isEnabled())))
        onView(withText("공유한 안내를 가져오기 전, 안전하게 주차했어요.")).perform(scrollTo(), click())
        onView(withText("공유한 행사 안내 가져오기")).perform(scrollTo(), click())
        onView(withText("스튜디오 159")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("도착 브리핑 확정")).perform(scrollTo()).check(matches(not(isEnabled())))
    }
    @Test fun malformedSharedPayloadCannotEnterTheForm() {
        scenario = ActivityScenario.launch(share("x".repeat(10_001)))
        onView(withSubstring("텍스트 안내만 받을 수 있습니다.")).check(matches(isDisplayed()))
        onView(withText("공유한 행사 안내 가져오기")).check(matches(not(isDisplayed())))
        scenario.onActivity { it.startActivity(share("코엑스 오디토리움 3층")
            .setDataAndType(Uri.parse("content://untrusted.example/private"), "text/plain")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        onView(withSubstring("텍스트 안내만 받을 수 있습니다.")).check(matches(isDisplayed()))
        scenario.onActivity { assertNull(it.intent.data); assertNull(it.intent.extras) }
    }
    @Test fun parkingMemoSurvivesRecreationButRemainsLockedUntilReconfirmed() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo()
        onView(withText("주차 위치 기록")).perform(scrollTo(), click())
        onView(withText("차량을 안전하게 주차했고, 위치 메모를 확인할게요.")).perform(scrollTo(), click())
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자")).perform(scrollTo(), replaceText("B3 블루존 E12"), closeSoftKeyboard())
        onView(withText("주차 위치 24시간 기억")).perform(scrollTo(), click())
        onView(withSubstring("주차 위치를 이 기기에 기억했어요.")).check(matches(isDisplayed()))
        scenario.recreate()
        onView(withText("주차 위치 기록")).check(matches(not(isDisplayed())))
        enterDemo()
        onView(withText("주차 위치 기록")).perform(scrollTo(), click())
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자")).check(matches(not(isDisplayed())))
        onView(withText("차량을 안전하게 주차했고, 위치 메모를 확인할게요.")).perform(scrollTo(), click())
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자")).check(matches(withText("B3 블루존 E12")))
        capture("parking-memo-restored")
        onView(withText("주차 위치 삭제")).perform(scrollTo(), click())
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자")).check(matches(withText("")))
    }
    @Test fun confirmedBriefStaysVisibleButDrivingHidesEditingAndCopy() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo()
        onView(withText("도착 브리핑 확정")).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withText("적재물을 포함한 전체 높이를 확인했습니다.")).perform(scrollTo(), click())
        onView(withText("‘오디토리움’이 현재 유효한 행사의 실제 방문 장소임을 확인했습니다.")).perform(scrollTo(), click())
        onView(withText("안전하게 주차한 상태입니다.")).perform(scrollTo(), click())
        onView(withText("도착 브리핑 확정")).perform(scrollTo()).check(matches(isEnabled())).perform(click())
        onView(withText("브리핑 복사")).perform(scrollTo()).check(matches(isDisplayed()))
        SystemClock.sleep(2500) // Evidence only: let the successful-confirmation toast finish.
        capture("confirmed-parked")
        onView(withText("데모 상태 변경")).perform(scrollTo(), click())
        onView(withText("주행")).perform(scrollTo(), click())
        onView(withContentDescription("루프박스와 적재물을 포함한 차량 전체 높이, 미터")).check(matches(not(isDisplayed())))
        onView(withText("브리핑 복사")).check(matches(not(isDisplayed())))
        onView(withText("주차 위치 기록")).check(matches(not(isDisplayed())))
        onView(withText("공식 안내 주차 구역")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withSubstring("확정된 브리핑 · 입력 잠금")).perform(scrollTo()).check(matches(isDisplayed()))
        capture("confirmed-driving-locked")
        onView(withText("정차")).perform(scrollTo(), click())
        onView(withText("안전하게 주차한 상태입니다.")).perform(scrollTo(), click())
        onView(withText("브리핑 복사")).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withText("목적지 24시간 보관")).perform(scrollTo()).check(matches(not(isEnabled())))
        capture("return-checks-required")
        onView(withText("‘오디토리움’이 현재 유효한 행사의 실제 방문 장소임을 확인했습니다.")).perform(scrollTo(), click())
        onView(withText("브리핑 복사")).perform(scrollTo()).check(matches(isEnabled()))
        onView(withText("목적지 24시간 보관")).perform(scrollTo()).check(matches(isEnabled()))
    }
    @Test fun conflictingSourcesShowBothValuesAndPreventConfirmation() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo()
        onView(withText("행사 안내문으로 찾기")).perform(scrollTo(), click())
        onView(withContentDescription("행사 장소 안내")).perform(scrollTo(), replaceText("코엑스 컨퍼런스룸 E 3층"), closeSoftKeyboard())
        onView(withText("도착 브리핑 확정")).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withText("B2 퍼플존")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("B2·B3·B4 블루존")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withSubstring("공지 2026-02-11 · 자료 확인")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("브리핑 복사")).check(matches(not(isDisplayed())))
        capture("source-conflict-evidence")
    }
    private fun openSafeMemo() {
        onView(withText("주차 위치 기록")).perform(scrollTo(), click())
        onView(withText("차량을 안전하게 주차했고, 위치 메모를 확인할게요.")).perform(scrollTo(), click())
    }
    private fun saveMemo(text: String) {
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자")).perform(scrollTo(), replaceText(text), closeSoftKeyboard())
        onView(withText("주차 위치 24시간 기억")).perform(scrollTo(), click())
    }
    private fun assertQrCleared() {
        onView(withContentDescription("저장한 주차 위치를 휴대폰으로 가져가는 QR")).check(matches(not(isDisplayed())))
            .check { view, _ -> assertNull((view as ImageView).drawable) }
    }
    @Test fun savedMemoQrRoundTripsAndClearsWhenEditedOrDriving() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo(); openSafeMemo()
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자")).perform(scrollTo(), replaceText("B3 블루존 E12"), closeSoftKeyboard())
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withText("주차 위치 24시간 기억")).perform(scrollTo(), click())
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo(), click())
        var rendered: Bitmap? = null
        // Opening the result must reveal the full code without a second user scroll.
        onView(withContentDescription("저장한 주차 위치를 휴대폰으로 가져가는 QR")).check(matches(isCompletelyDisplayed()))
            .check { view, _ ->
                val bitmap = ((view as ImageView).drawable as BitmapDrawable).bitmap
                assertTrue("QR pixels must be mutable so clearing is safe", bitmap.isMutable)
                rendered = bitmap
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
                assertTrue(decoded.contains("주차 위치: B3 블루존 E12"))
                assertTrue(decoded.contains("휴대폰 복사본은 자동 삭제되지 않습니다."))
            }
        capture("saved-memo-qr")
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자")).perform(scrollTo(), replaceText("B2 퍼플존 P7"), closeSoftKeyboard())
        assertQrCleared()
        val clearedPixels = IntArray(rendered!!.width * rendered!!.height)
        rendered!!.getPixels(clearedPixels, 0, rendered!!.width, 0, 0, rendered!!.width, rendered!!.height)
        assertTrue(clearedPixels.all { it == Color.WHITE })
        rendered = null
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo()).check(matches(not(isEnabled())))
        saveMemo("B2 퍼플존 P7")
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo(), click())
        onView(withText("데모 상태 변경")).perform(scrollTo(), click())
        onView(withText("주행")).perform(scrollTo(), click())
        assertQrCleared()
        onView(withText("휴대폰으로 가져가기")).check(matches(not(isDisplayed())))
        capture("memo-qr-cleared-driving")
    }
    @Test fun memoQrDoesNotRestoreAfterRecreationAndDeletionClearsIt() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo(); openSafeMemo(); saveMemo("B3 블루존 E12")
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo(), click())
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertQrCleared()
        onView(withText("데모 상태 변경")).perform(scrollTo(), click())
        onView(withText("정차")).perform(scrollTo(), click())
        openSafeMemo()
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo(), click())
        scenario.recreate()
        assertQrCleared()
        enterDemo(); openSafeMemo()
        assertQrCleared()
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo(), click())
        onView(withText("주차 위치 삭제")).perform(scrollTo(), click())
        assertQrCleared()
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo()).check(matches(not(isEnabled())))
    }
    @Test fun vehicleEntryRestrictionsCannotBeOverriddenBySelectionOrChecks() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo()
        onView(withText("행사 안내문으로 찾기")).perform(scrollTo(), click())
        val restrictions = listOf(
            "금일은 차량 진입이 불가합니다.", "입차 불가", "오늘은 차량 출입을 금합니다.",
            "오늘은 주차장을 이용할 수 없습니다.", "오늘 출구를 사용할 수 없습니다.", "후문은 사용하지 마세요.", "금일 휴관합니다.",
            "오늘 행사는 안 합니다.", "행사가 종료되었습니다.", "종료되었습니다."
        )
        restrictions.forEachIndexed { index, restriction ->
            val original = "코엑스 오디토리움 3층 행사\n$restriction"
            onView(withContentDescription("행사 장소 안내")).perform(scrollTo(), replaceText(original), closeSoftKeyboard())
            onView(withText("회의실 선택")).perform(scrollTo(), click())
            onView(withText("오디토리움 · 3F")).perform(click())
            onView(withContentDescription("루프박스와 적재물을 포함한 차량 전체 높이, 미터")).check(matches(withText("1.65")))
            onView(withText("적재물을 포함한 전체 높이를 확인했습니다.")).perform(scrollTo(), click()).check(matches(isChecked()))
            onView(allOf(isAssignableFrom(CheckBox::class.java), withText(containsString("현재 유효"))))
                .perform(scrollTo(), click()).check(matches(isChecked()))
            onView(withText("안전하게 주차한 상태입니다.")).perform(scrollTo(), click()).check(matches(isChecked()))
            onView(withText("도착 브리핑 확정")).perform(scrollTo()).check(matches(not(isEnabled())))
            onView(withText("브리핑 복사")).check(matches(not(isDisplayed())))
            onView(withContentDescription("행사 장소 안내")).perform(scrollTo()).check(matches(withText(original)))
            onView(withText("“$restriction”")).perform(scrollTo()).check(matches(isDisplayed()))
            capture("vehicle-entry-restriction-${index + 1}")
        }
    }
    @Test fun sponsoringFacilityCannotReplaceTheActualVenueEvenAfterSelection() {
        val original = "장소: 연세대학교 백양누리 그랜드볼룸\n후원: 코엑스"
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo()
        onView(withText("행사 안내문으로 찾기")).perform(scrollTo(), click())
        onView(withContentDescription("행사 장소 안내")).perform(scrollTo(), replaceText(original), closeSoftKeyboard())
        fun verifyAllChecksStillCannotConfirm() {
            onView(withContentDescription("루프박스와 적재물을 포함한 차량 전체 높이, 미터")).check(matches(withText("1.65")))
            onView(withText("적재물을 포함한 전체 높이를 확인했습니다.")).perform(scrollTo(), click()).check(matches(isChecked()))
            onView(allOf(isAssignableFrom(CheckBox::class.java), withText(containsString("현재 유효"))))
                .perform(scrollTo(), click()).check(matches(isChecked()))
            onView(withText("안전하게 주차한 상태입니다.")).perform(scrollTo(), click()).check(matches(isChecked()))
            onView(withText("도착 브리핑 확정")).perform(scrollTo()).check(matches(not(isEnabled())))
            onView(withText("브리핑 복사")).check(matches(not(isDisplayed())))
            onView(withContentDescription("행사 장소 안내")).perform(scrollTo()).check(matches(withText(original)))
        }
        verifyAllChecksStillCannotConfirm()
        capture("venue-attribution-blocked")
        onView(withText("회의실 선택")).perform(scrollTo(), click())
        onView(withText("그랜드볼룸 · 1F")).perform(click())
        verifyAllChecksStillCannotConfirm()
        capture("venue-attribution-picker-blocked")
    }
    @Test fun destinationPickerPreservesConflictingNoticeAndCannotBypassReview() {
        val original = "코엑스 오디토리움 3층\n주차는 퍼플존만 이용하세요."
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo()
        onView(withText("행사 안내문으로 찾기")).perform(scrollTo(), click())
        onView(withContentDescription("행사 장소 안내")).perform(scrollTo(), replaceText(original), closeSoftKeyboard())
        onView(withText("회의실 선택")).perform(scrollTo(), click())
        onView(withText("오디토리움 · 3F")).perform(click())
        onView(withContentDescription("행사 장소 안내")).perform(scrollTo()).check(matches(withText(original)))
        onView(withContentDescription("루프박스와 적재물을 포함한 차량 전체 높이, 미터")).check(matches(withText("1.65")))
        onView(withText("적재물을 포함한 전체 높이를 확인했습니다.")).perform(scrollTo(), click())
        onView(allOf(isAssignableFrom(CheckBox::class.java), withText(containsString("현재 유효")))).perform(scrollTo(), click())
        onView(withText("안전하게 주차한 상태입니다.")).perform(scrollTo(), click())
        onView(withText("도착 브리핑 확정")).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withText("브리핑 복사")).check(matches(not(isDisplayed())))
        capture("picker-preserves-conflicting-source")
    }
    @Test fun activeMemoAndQrExpireAtTheStoredDeadline() {
        val savedAt = System.currentTimeMillis() - ParkingMemoPolicy.TTL_MS + 60_000
        assertTrue(ParkingMemoStore(context).save("B3 TTL E12", savedAt))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo(); openSafeMemo()
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo(), click())
        onView(withContentDescription("저장한 주차 위치를 휴대폰으로 가져가는 QR")).perform(scrollTo()).check(matches(isDisplayed()))
        capture("memo-qr-before-expiry")
        onView(withContentDescription("저장한 주차 위치를 휴대폰으로 가져가는 QR")).perform(object : ViewAction {
            override fun getDescription() = "Wait across the saved memo's actual expiration deadline"
            override fun getConstraints(): org.hamcrest.Matcher<View> = isAssignableFrom(ImageView::class.java)
            override fun perform(ui: UiController, view: View) {
                val deadline = savedAt + ParkingMemoPolicy.TTL_MS + 10_000
                while ((view as ImageView).drawable != null && System.currentTimeMillis() < deadline)
                    ui.loopMainThreadForAtLeast(250)
                assertTrue("The real stored deadline must have elapsed", System.currentTimeMillis() >= savedAt + ParkingMemoPolicy.TTL_MS)
                assertNull("Expiration must remove the displayed QR", (view as ImageView).drawable)
            }
        })
        assertQrCleared()
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자")).check(matches(withText("")))
        onView(withText("휴대폰으로 가져가기")).perform(scrollTo()).check(matches(not(isEnabled())))
        assertNull(ParkingMemoStore(context).load())
        assertTrue(!context.getSharedPreferences("parking_memo", Context.MODE_PRIVATE).contains("text"))
        capture("memo-qr-after-expiry")
    }
    @Test fun realKeyboardKeepsMemoInputVisibleAndAllowsSaving() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo(); openSafeMemo()
        var frameBeforeKeyboard = 0
        scenario.onActivity {
            val frame = Rect()
            it.window.decorView.getWindowVisibleDisplayFrame(frame)
            frameBeforeKeyboard = frame.height()
        }
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자"))
            .perform(scrollTo(), click(), typeText("B3 E12"))
            .perform(object : ViewAction {
                override fun getDescription() = "Wait for the actual IME and verify the editor stays above it"
                override fun getConstraints(): org.hamcrest.Matcher<View> = isAssignableFrom(android.widget.EditText::class.java)
                override fun perform(ui: UiController, view: View) {
                    val frame = Rect()
                    val threshold = (100 * view.resources.displayMetrics.density).toInt()
                    val deadline = SystemClock.uptimeMillis() + 6000
                    do {
                        view.rootView.getWindowVisibleDisplayFrame(frame)
                        if (frameBeforeKeyboard - frame.height() > threshold) break
                        ui.loopMainThreadForAtLeast(100)
                    } while (SystemClock.uptimeMillis() < deadline)
                    assertTrue("An actual software keyboard must reduce the visible window", frameBeforeKeyboard - frame.height() > threshold)
                    val position = IntArray(2)
                    view.getLocationOnScreen(position)
                    assertTrue("Memo editor must stay above the keyboard", position[1] >= frame.top && position[1] + view.height <= frame.bottom)
                }
            }).check(matches(withText("B3 E12")))
        capture("real-ime-memo-visible")
        onView(withContentDescription("직접 적는 주차 위치 메모, 최대 64자")).perform(closeSoftKeyboard())
        onView(withText("주차 위치 24시간 기억")).perform(scrollTo(), click())
        onView(withSubstring("주차 위치를 이 기기에 기억했어요.")).check(matches(isDisplayed()))
    }
    @Test fun warmShareKeepsCurrentDraftUntilExplicitImport() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        enterDemo()
        scenario.onActivity { it.startActivity(share("코엑스 스튜디오 159 2층 행사").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        onView(withText("데모 상태 변경")).perform(scrollTo(), click())
        onView(withText("정차")).perform(scrollTo(), click())
        onView(withText("공유한 행사 안내 가져오기")).check(matches(not(isEnabled())))
        onView(withText("오디토리움")).perform(scrollTo()).check(matches(isDisplayed()))
    }
}
