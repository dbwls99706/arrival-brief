package studio.ax.arrival

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View

/** Semantic preparation sequence, deliberately not a map or a navigation instruction. */
class ArrivalSceneView @JvmOverloads constructor(context: Context, private val confirmed: Boolean = false) : View(context) {
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val navy = Color.rgb(32, 54, 80)
    private val blue = Color.rgb(54, 91, 214)
    private val green = Color.rgb(36, 119, 91)
    private val gold = Color.rgb(245, 200, 92)
    private val white = Color.rgb(250, 252, 255)
    private val textInk = if (confirmed) white else navy

    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val arrows = listOf(133f, 235f).map { x ->
        Path().apply { moveTo(x - 5, 88f); lineTo(x, 93f); lineTo(x - 5, 98f); close() }
    }
    private val roof = Path().apply {
        moveTo(42f, 84f); lineTo(50f, 66f); quadTo(52f, 63f, 58f, 63f)
        lineTo(72f, 63f); quadTo(78f, 64f, 80f, 69f); lineTo(88f, 85f); close()
    }
    private val windowColumns = floatArrayOf(325f, 337f)
    private val windowRows = floatArrayOf(84f, 94f)

    init {
        contentDescription = "출발 전에 방문 장소와 차량을 확인해 진입문과 주차 안내를 준비하는 흐름도. 실제 지도가 아닙니다."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 400f, height / 190f)
        canvas.save()
        canvas.translate((width - 400 * scale) / 2, (height - 190 * scale) / 2)
        canvas.scale(scale, scale)

        // The connecting line expresses sequence, not a real entrance location.
        pen.color = if (confirmed) Color.rgb(80, 102, 124) else Color.rgb(190, 205, 227)
        pen.style = Paint.Style.STROKE; pen.strokeWidth = 3f; pen.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(57f, 93f, 342f, 93f, pen)
        pen.style = Paint.Style.FILL
        arrows.forEach { canvas.drawPath(it, pen) }

        // A small car with an intentionally soft silhouette, no decorative animation.
        rounded(canvas, 17f, 53f, 108f, 123f, 24f, if (confirmed) Color.rgb(54, 75, 98) else Color.rgb(212, 225, 246))
        rounded(canvas, 30f, 83f, 96f, 106f, 10f, blue)
        pen.color = blue; canvas.drawPath(roof, pen)
        rounded(canvas, 51f, 68f, 73f, 81f, 3f, Color.rgb(197, 220, 255))
        pen.color = navy; canvas.drawCircle(45f, 106f, 7f, pen); canvas.drawCircle(82f, 106f, 7f, pen)
        pen.color = white; canvas.drawCircle(45f, 106f, 3f, pen); canvas.drawCircle(82f, 106f, 3f, pen)
        rounded(canvas, 88f, 89f, 94f, 94f, 2f, gold)

        // The gate and parking sign use standard physical-world cues.
        rounded(canvas, 151f, 53f, 220f, 123f, 19f, if (confirmed) Color.rgb(73, 73, 70) else Color.rgb(250, 235, 191))
        rounded(canvas, 166f, 73f, 174f, 112f, 3f, navy)
        rounded(canvas, 170f, 70f, 206f, 79f, 3f, gold)
        pen.color = navy; canvas.drawRect(179f, 70f, 185f, 79f, pen); canvas.drawRect(195f, 70f, 201f, 79f, pen)
        rounded(canvas, 197f, 83f, 207f, 111f, 3f, white)
        pen.color = green; canvas.drawCircle(202f, 90f, 2f, pen)

        rounded(canvas, 267f, 53f, 367f, 123f, 22f, if (confirmed) Color.rgb(41, 83, 76) else Color.rgb(208, 235, 220))
        rounded(canvas, 281f, 68f, 313f, 104f, 6f, green)
        label(canvas, "P", 297f, 94f, 26f, white)
        // COEX is a facility illustration only: no scaled floor plan or invented coordinates.
        rounded(canvas, 320f, 78f, 352f, 109f, 3f, if (confirmed) Color.rgb(172, 205, 188) else Color.rgb(250, 252, 255))
        pen.color = green
        for (x in windowColumns) for (y in windowRows) canvas.drawRect(x, y, x + 6, y + 5, pen)

        label(canvas, "내 차 확인", 63f, 148f, 15f, textInk)
        label(canvas, "진입문", 185f, 148f, 15f, textInk)
        label(canvas, "추천 주차", 317f, 148f, 15f, textInk)
        canvas.restore()
    }

    private fun rounded(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        pen.color = color; pen.style = Paint.Style.FILL
        c.drawRoundRect(l, t, r, b, radius, radius, pen)
    }

    private fun label(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int) {
        pen.color = color; pen.textSize = size; pen.textAlign = Paint.Align.CENTER
        pen.typeface = bold
        c.drawText(value, x, y, pen)
    }
}
