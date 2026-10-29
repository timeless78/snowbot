package io.snowbot.hub

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/**
 * 화면 속 가상 스노봇. 실물 스노봇과 같은 표정 명령(03 모듈 규격의 express: expr·head)을 그림으로 보여 준다.
 * 실물이 없거나 꺼져 있어도 앱만으로 표현이 된다. 움직임은 짧게(주행 중 주의 분산 최소화).
 */
class SnowBotView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** 표정 하나 = 눈꺼풀이 덮은 정도, 아래 눈꺼풀(웃음), 눈썹 높이, 눈썹 기울기(+면 안쪽이 올라감). */
    private data class Face(val lid: Float, val smile: Float, val browLift: Float, val browTilt: Float)

    private val faces = mapOf(
        "neutral" to Face(0.18f, 0f, 0f, 0f),
        "happy" to Face(0.05f, 0.35f, 0.6f, -0.1f),
        "greet" to Face(0.05f, 0.35f, 0.6f, -0.1f),
        "sleepy" to Face(0.62f, 0f, -0.4f, 0f),
        "worried" to Face(0.2f, 0f, 0.3f, 0.55f),
        "alert" to Face(0f, 0f, 1f, -0.2f),
        "guard" to Face(0.3f, 0f, -0.3f, -0.5f),
    )

    private var face = faces.getValue("neutral")
    private var headX = 0f
    private var headY = 0f

    private val snow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F4F7FA") }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#AEBBC6")
    }
    private val pod = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B3640") }
    private val sclera = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pupil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B2228") }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E3E9EE") }
    private val lidEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#8E9BA6"); strokeCap = Paint.Cap.ROUND
    }
    private val brow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#6B4A2E"); strokeCap = Paint.Cap.ROUND
    }
    private val nose = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8641C") }
    private val neck = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9AA6B0") }
    private val clip = Path()
    private var faceAnim: ValueAnimator? = null
    private var headAnim: ValueAnimator? = null

    fun setExpression(expr: String, head: String?) {
        val from = face
        val to = faces[expr] ?: faces.getValue("neutral")
        faceAnim?.cancel()
        faceAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                face = Face(
                    lerp(from.lid, to.lid, t), lerp(from.smile, to.smile, t),
                    lerp(from.browLift, to.browLift, t), lerp(from.browTilt, to.browTilt, t),
                )
                invalidate()
            }
            start()
        }
        moveHead(head)
    }

    private fun moveHead(head: String?) {
        headAnim?.cancel()
        headX = 0f; headY = 0f
        val amp = min(width, height) * 0.03f
        val shape: ((Float) -> Unit) = when (head) {
            "nod" -> { t -> headY = amp * wave(t) }
            "shake" -> { t -> headX = amp * wave(t) }
            "turn_left" -> { t -> headX = -amp * 1.5f * bump(t) }
            "turn_right" -> { t -> headX = amp * 1.5f * bump(t) }
            else -> { invalidate(); return }
        }
        headAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            addUpdateListener { shape(it.animatedValue as Float); invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val cx = width / 2f
        val headR = s * 0.30f
        val cy = height * 0.42f
        val bodyR = s * 0.22f
        outline.strokeWidth = s * 0.008f

        // 몸통과 목 (머리 아래에 깔림)
        val bodyY = cy + headR + bodyR * 0.75f
        canvas.drawCircle(cx, bodyY, bodyR, snow)
        canvas.drawCircle(cx, bodyY, bodyR, outline)
        canvas.drawRoundRect(cx - headR * 0.32f, cy + headR * 0.82f, cx + headR * 0.32f, cy + headR * 1.08f,
            s * 0.02f, s * 0.02f, neck)

        canvas.save()
        canvas.translate(headX, headY)
        canvas.drawCircle(cx, cy, headR, snow)
        canvas.drawCircle(cx, cy, headR, outline)

        val ey = cy - headR * 0.05f
        val podR = headR * 0.26f
        for (side in intArrayOf(-1, 1)) {
            drawEye(canvas, cx + side * headR * 0.42f, ey, podR, side)
        }

        // 당근 코: 얼굴 가운데에서 오른쪽 아래로 짧게
        val nx = cx
        val ny = cy + headR * 0.22f
        val path = Path().apply {
            moveTo(nx - headR * 0.07f, ny - headR * 0.06f)
            lineTo(nx + headR * 0.30f, ny + headR * 0.05f)
            lineTo(nx - headR * 0.05f, ny + headR * 0.08f)
            close()
        }
        canvas.drawPath(path, nose)
        canvas.restore()
    }

    private fun drawEye(canvas: Canvas, x: Float, y: Float, podR: Float, side: Int) {
        val scleraR = podR * 0.80f
        canvas.drawCircle(x, y, podR, pod)

        clip.reset()
        clip.addCircle(x, y, scleraR, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawCircle(x, y, scleraR, sclera)
        canvas.drawCircle(x, y + scleraR * 0.08f, scleraR * 0.46f, pupil)
        canvas.drawCircle(x + scleraR * 0.15f, y - scleraR * 0.12f, scleraR * 0.12f, shine)
        // 위 눈꺼풀
        val lidBottom = y - scleraR + 2 * scleraR * face.lid
        if (face.lid > 0.01f) canvas.drawRect(x - scleraR, y - scleraR, x + scleraR, lidBottom, lidPaint)
        // 아래 눈꺼풀(웃을 때 볼이 올라옴)
        val lowTop = y + scleraR - 2 * scleraR * face.smile
        if (face.smile > 0.01f) canvas.drawRect(x - scleraR, lowTop, x + scleraR, y + scleraR, lidPaint)
        canvas.restore()

        lidEdge.strokeWidth = podR * 0.08f
        if (face.lid > 0.01f) canvas.drawLine(x - scleraR, lidBottom, x + scleraR, lidBottom, lidEdge)

        // 나뭇가지 눈썹: 안쪽 끝(얼굴 가운데 쪽)이 browTilt 만큼 올라간다
        brow.strokeWidth = podR * 0.22f
        val by = y - podR * 1.45f - face.browLift * podR * 0.45f
        val half = podR * 0.9f
        val tilt = face.browTilt * podR * 0.5f
        val innerX = x - side * half
        val outerX = x + side * half
        canvas.drawLine(outerX, by + tilt * 0.5f, innerX, by - tilt, brow)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun wave(t: Float) = kotlin.math.sin(t * 4 * Math.PI).toFloat() * (1 - t)
    private fun bump(t: Float) = kotlin.math.sin(t * Math.PI).toFloat()
}
