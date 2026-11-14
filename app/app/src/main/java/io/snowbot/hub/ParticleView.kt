package io.snowbot.hub

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 화면 속 AI = 입자 구름. **AI 는 하나, 몸은 여럿**(docs/pleos/01-proposal.md 3장, 03-module-protocol.md 3-5절).
 *
 *  - 봇이 없으면 **빛 방울**: 숨 쉬듯 커졌다 작아지고, 입자는 늘 미세하게 떨린다.
 *  - 봇이 연결되면 방울이 한 번 크게 부풀었다 돌아오고(연결됨 신호) → 풀려서 소용돌이 →
 *    그 봇이 알려 준 모습(`look` 외곽선)으로 모인다. 끊기면 흩어져 다시 빛 방울.
 *  - 감정(express)은 색과 움직임 빠르기로, 몸짓 `nod`·`shake` 는 머리 쪽 입자로.
 *  - 말하는 중이면 가장자리가 출렁이고, 듣는 중이면 아래(운전자 쪽)로 살짝 모인다.
 *  - 주행 중에는 입자 수와 움직임을 줄인다(Pleos: 주행 중 애니메이션 최소화).
 *  - 입자는 창 밖으로 나가지 않는다.
 */
class ParticleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private enum class Phase { ORB, TO_SHAPE, SHAPE, TO_ORB }

    /** 감정 하나 = 색 · 움직임 빠르기 · 처지는 정도 · 퍼지는 정도. */
    private data class Mood(val color: Int, val speed: Float, val drop: Float, val spread: Float)

    private val moods = mapOf(
        "neutral" to Mood(0xFF46A0FF.toInt(), 1.0f, 0f, 1f),
        "happy" to Mood(0xFF4ED8FF.toInt(), 1.25f, -0.02f, 1.05f),
        "greet" to Mood(0xFF4ED8FF.toInt(), 1.3f, -0.02f, 1.06f),
        "worried" to Mood(0xFFFF9A42.toInt(), 0.65f, 0.012f, 0.96f),
        "sleepy" to Mood(0xFF2F6BA6.toInt(), 0.45f, 0.05f, 0.9f),
        "alert" to Mood(0xFF8CE0FF.toInt(), 1.5f, -0.012f, 1.07f),
        "guard" to Mood(0xFF6D86FF.toInt(), 0.85f, 0f, 0.97f),
    )

    // 입자마다 가진 것: 구 위 방향 · 소용돌이 · 떨림 위상 · 모이는 차례
    private val dx = FloatArray(MAX)
    private val dy = FloatArray(MAX)
    private val dz = FloatArray(MAX)
    private val wr = FloatArray(MAX)
    private val wa = FloatArray(MAX)
    private val ws = FloatArray(MAX)
    private val j1 = FloatArray(MAX)
    private val j2 = FloatArray(MAX)
    private val jw1 = FloatArray(MAX)
    private val jw2 = FloatArray(MAX)
    private val delay = FloatArray(MAX)

    // 그리기 묶음: 한 번에 그리려고 색갈래·깊이별로 모은다 (drawPoints)
    private val bufFar = FloatArray(MAX * 2)
    private val bufNear = FloatArray(MAX * 2)
    private val bufEdge = FloatArray(MAX * 2)
    private val bufWarm = FloatArray(MAX * 2)
    private var nFar = 0
    private var nNear = 0
    private var nEdge = 0
    private var nWarm = 0

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }

    // 자리 계산이 돌려주는 값 (프레임마다 새로 만들지 않는다)
    private val atOrb = FloatArray(4)   // x · y · 앞뒤(z) · 가장자리(겉테)
    private val atSwirl = FloatArray(2)
    private val atForm = FloatArray(2)
    private val atShift = FloatArray(2)

    private var wanted: LookShape.Look? = null
    private var shape: List<LookShape.Target>? = null
    private var shapePivot = 0f          // 끄덕임의 축: 머리 입자의 아래 끝
    private var shapeColor = 0xFF46A0FF.toInt()
    private var phase = Phase.ORB
    private var phaseAt = 0f

    private var mood = moods.getValue("neutral")
    private var moodFrom = mood
    private var moodAt = -10f
    private var gesture: String? = null
    private var gestureAt = -10f

    private var driving = false
    private var speaking = false
    private var listening = false

    private var t = 0f
    private var startNanos = 0L

    init {
        val rnd = java.util.Random(20_261_108L)
        for (i in 0 until MAX) {
            // 구 위에 고르게 (피보나치 나선). 나선 순서를 그대로 쓰면 위에서 아래로 차례대로 놓여서,
            // 주행 중처럼 앞쪽 일부만 그릴 때 반구만 남는다 → 차례를 섞어 앞쪽 몇 개만 그려도 구 전체에 퍼지게
            val k = sphereSlot(i, MAX) + 0.5
            val phi = Math.acos(1 - 2 * k / MAX)
            val th = Math.PI * (1 + sqrt(5.0)) * k
            dx[i] = (sin(phi) * cos(th)).toFloat()
            dy[i] = cos(phi).toFloat()
            dz[i] = (sin(phi) * sin(th)).toFloat()
            wr[i] = 0.13f + rnd.nextFloat() * 0.33f
            wa[i] = rnd.nextFloat() * 6.2832f
            ws[i] = 0.9f + rnd.nextFloat() * 0.8f
            j1[i] = rnd.nextFloat() * 6.2832f
            j2[i] = rnd.nextFloat() * 6.2832f
            jw1[i] = 2f + rnd.nextFloat() * 3f
            jw2[i] = 2f + rnd.nextFloat() * 3f
            delay[i] = rnd.nextFloat() * 0.35f
        }
    }

    // ---- 바깥에서 알려 주는 것 ----

    /** 연결된 봇의 모습. null 이면 빛 방울로 돌아간다. 같은 모습이면 아무것도 하지 않는다. */
    fun setLook(look: LookShape.Look?) {
        if (look == wanted) return
        wanted = look
        val want = look?.let { LookShape.targets(it, MAX) }?.takeIf { it.isNotEmpty() }
        if (want == null) {
            // 흩어지는 동안에는 모양을 들고 있어야 한다(다 흩어지면 advance 가 버린다)
            if (shape != null) go(Phase.TO_ORB)
            return
        }
        shape = want
        shapePivot = want.filter { it.head }.maxOfOrNull { it.y } ?: 0f
        shapeColor = runCatching { Color.parseColor(look!!.color ?: "") }.getOrDefault(0xFF46A0FF.toInt())
        go(Phase.TO_SHAPE)
    }

    /** 앱이 봇에 보낸 감정·몸짓을 입자도 따른다. */
    fun setExpression(expr: String, gesture: String?) {
        val next = moods[expr] ?: moods.getValue("neutral")
        if (next != mood) {
            moodFrom = current()
            mood = next
            moodAt = t
        }
        if (gesture != null) {
            this.gesture = gesture
            gestureAt = t
        }
    }

    /** 주행 중에는 입자 수와 움직임을 줄인다. */
    fun setDriving(driving: Boolean) {
        this.driving = driving
    }

    /** 말하는 중(가장자리 출렁임) · 듣는 중(운전자 쪽으로 모임). */
    fun setVoice(speaking: Boolean, listening: Boolean) {
        this.speaking = speaking
        this.listening = listening
    }

    private fun go(next: Phase) {
        phase = next
        phaseAt = t
    }

    // ---- 그리기 ----

    override fun onDraw(canvas: Canvas) {
        if (startNanos == 0L) startNanos = System.nanoTime()
        t = (System.nanoTime() - startNanos) / 1e9f

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val pad = size * 0.03f

        advance()
        val m = current()
        val speed = m.speed * if (driving) 0.7f else 1f
        val live = if (driving) 0.45f else 1f
        val spread = m.spread * if (listening) 0.93f else 1f
        val sink = m.drop * size + if (listening) size * 0.05f else 0f

        nFar = 0; nNear = 0; nEdge = 0; nWarm = 0
        val target = shape
        val count = min(if (driving) DRIVING else MAX, target?.size ?: MAX)
        val into = t - phaseAt
        // 봇 모습 쪽에 와 있으면 외곽선 입자를, 빛 방울 쪽이면 방울의 겉테를 밝게 그린다
        val useEdge = target != null && when (phase) {
            Phase.SHAPE -> true
            Phase.TO_SHAPE -> into >= SWIRL
            Phase.TO_ORB -> into < BACK_SWIRL
            Phase.ORB -> false
        }
        for (i in 0 until count) {
            var x: Float
            var y: Float
            var z = 0f
            var rim = 0f
            when (phase) {
                Phase.ORB -> { val p = orb(i, cx, cy, size, spread, speed, 1f); x = p[0]; y = p[1]; z = p[2]; rim = p[3] }
                Phase.TO_SHAPE -> {
                    val u = t - phaseAt
                    val swell = 1f + 0.34f * sin(Math.PI.toFloat() * ease(prog(u, 0f, SWELL)))
                    val p = orb(i, cx, cy, size, spread, speed, swell)
                    when {
                        u < SWELL -> { x = p[0]; y = p[1]; z = p[2]; rim = p[3] }
                        u < SWIRL -> {
                            val k = ease(prog(u, SWELL, SWIRL))
                            val s = swirl(i, cx, cy, size, spread, speed)
                            x = lerp(p[0], s[0], k); y = lerp(p[1], s[1], k); z = p[2] * (1 - k); rim = p[3] * (1 - k)
                        }
                        else -> {
                            val k = ease(((prog(u, SWIRL, FORM) - delay[i]) / 0.65f).coerceIn(0f, 1f))
                            val s = swirl(i, cx, cy, size, spread, speed)
                            val f = form(i, target, cx, cy, size, spread, sink)
                            x = lerp(s[0], f[0], k); y = lerp(s[1], f[1], k)
                        }
                    }
                }
                Phase.SHAPE -> { val p = form(i, target, cx, cy, size, spread, sink); x = p[0]; y = p[1] }
                Phase.TO_ORB -> {
                    val u = t - phaseAt
                    val s = swirl(i, cx, cy, size, spread, speed)
                    if (u < BACK_SWIRL) {
                        val k = ease(prog(u, 0f, BACK_SWIRL))
                        val f = form(i, target, cx, cy, size, spread, sink)
                        x = lerp(f[0], s[0], k); y = lerp(f[1], s[1], k)
                    } else {
                        val k = ease(prog(u, BACK_SWIRL, BACK_ORB))
                        val p = orb(i, cx, cy, size, spread, speed, 1f)
                        x = lerp(s[0], p[0], k); y = lerp(s[1], p[1], k); z = p[2] * k; rim = p[3] * k
                    }
                }
            }

            // 살아 있는 떨림: 어떤 모양이든 늘 조금씩
            val amp = size * 0.0032f * live
            x += sin(t * jw1[i] * speed + j1[i]) * amp
            y += cos(t * jw2[i] * speed + j2[i]) * amp

            x = x.coerceIn(pad, w - pad)
            y = y.coerceIn(pad, h - pad)

            val warm = target != null && phase != Phase.ORB && target[i].tint == LookShape.Tint.ORANGE
            val bright = if (useEdge) target!![i].edge else rim > 0.88f
            when {
                warm -> { bufWarm[nWarm * 2] = x; bufWarm[nWarm * 2 + 1] = y; nWarm++ }
                bright -> { bufEdge[nEdge * 2] = x; bufEdge[nEdge * 2 + 1] = y; nEdge++ }
                z < 0f -> { bufFar[nFar * 2] = x; bufFar[nFar * 2 + 1] = y; nFar++ }
                else -> { bufNear[nNear * 2] = x; bufNear[nNear * 2 + 1] = y; nNear++ }
            }
        }

        val core = max(2f, size * 0.0042f)
        val cold = if (shape != null && phase != Phase.ORB) blend(m.color, shapeColor, 0.45f) else m.color
        glow(canvas, bufFar, nFar, cold, core * 0.85f, 95)
        glow(canvas, bufNear, nNear, cold, core, 205)
        glow(canvas, bufEdge, nEdge, light(cold), core * 1.5f, 255)
        glow(canvas, bufWarm, nWarm, WARM, core * 1.2f, 240)

        postInvalidateOnAnimation()
    }

    private fun glow(canvas: Canvas, buf: FloatArray, n: Int, color: Int, core: Float, alpha: Int) {
        if (n == 0) return
        dot.color = color
        dot.strokeWidth = core * 4.2f
        dot.alpha = (alpha * 0.17f).toInt().coerceIn(1, 255)
        canvas.drawPoints(buf, 0, n * 2, dot)
        dot.strokeWidth = core
        dot.alpha = alpha
        canvas.drawPoints(buf, 0, n * 2, dot)
    }

    /** 겉테는 한 겹 더 밝게. */
    private fun light(c: Int) = Color.rgb(
        min(255, Color.red(c) + 70), min(255, Color.green(c) + 50), min(255, Color.blue(c) + 20),
    )

    /** 다 모였으면 다음 단계로. 다 흩어졌으면 들고 있던 모양을 버린다. */
    private fun advance() {
        val u = t - phaseAt
        if (phase == Phase.TO_SHAPE && u > FORM) go(Phase.SHAPE)
        if (phase == Phase.TO_ORB && u > BACK_ORB) {
            go(Phase.ORB)
            shape = null
        }
    }

    /** 빛 방울: 천천히 도는 구 + 숨 쉬기. 말하는 중이면 겉이 출렁인다. */
    private fun orb(i: Int, cx: Float, cy: Float, size: Float, spread: Float, speed: Float, swell: Float): FloatArray {
        val a = t * 0.35f * speed
        val x = dx[i] * cos(a) - dz[i] * sin(a)
        val z = dx[i] * sin(a) + dz[i] * cos(a)
        val breathe = 1f + (if (driving) 0.018f else 0.04f) * sin(t * 6.2832f / 2.6f * speed)
        val ripple = if (speaking) 1f + 0.035f * sin(t * 9f + j1[i]) else 1f
        val r = size * 0.30f * breathe * swell * spread * ripple
        val bob = gestureShift(size)
        atOrb[0] = cx + x * r + bob[0]
        atOrb[1] = cy + dy[i] * r + bob[1] + drop(size)
        atOrb[2] = z
        atOrb[3] = sqrt(x * x + dy[i] * dy[i])   // 1 에 가까울수록 방울의 겉테 — 밝게 그린다
        return atOrb
    }

    /** 풀려서 도는 소용돌이. 창 안에서만 돈다. */
    private fun swirl(i: Int, cx: Float, cy: Float, size: Float, spread: Float, speed: Float): FloatArray {
        val a = wa[i] + (t - phaseAt) * ws[i] * 2.2f * speed
        val r = wr[i] * size * spread
        atSwirl[0] = cx + cos(a) * r
        atSwirl[1] = cy + sin(a) * r * 1.25f + drop(size)
        return atSwirl
    }

    /** 봇 모습: 알려 준 외곽선 자리. 머리 쪽 입자는 끄덕임·고개 젓기를 따른다. */
    private fun form(i: Int, target: List<LookShape.Target>?, cx: Float, cy: Float, size: Float, spread: Float, sink: Float): FloatArray {
        if (target == null) return orb(i, cx, cy, size, spread, 1f, 1f)
        val p = target[i]
        val s = size * 0.92f * spread
        var x = p.x * s
        var y = p.y * s
        if (p.head) {
            val pivot = shapePivot * s
            val n = prog(t - gestureAt, 0f, 0.9f)
            if (n > 0f && n < 1f) {
                val d = y - pivot
                when (gesture) {
                    "nod" -> {
                        val ang = sin(n * Math.PI.toFloat()) * 0.3f
                        y = pivot + d * cos(ang) + sin(n * Math.PI.toFloat()) * size * 0.02f
                        x -= d * sin(ang) * 0.15f
                    }
                    "shake", "turn_left", "turn_right" -> x += size * 0.025f * wave(n) * if (gesture == "turn_left") -1f else 1f
                }
            }
        }
        if (speaking && p.edge) {
            val push = 1f + 0.03f * sin(t * 11f + j1[i])
            x *= push; y *= push
        }
        atForm[0] = cx + x
        atForm[1] = cy + y + sink
        return atForm
    }

    /** 빛 방울일 때의 몸짓: 통째로 끄덕이거나 좌우로 흔든다. */
    private fun gestureShift(size: Float): FloatArray {
        atShift[0] = 0f
        atShift[1] = 0f
        val n = prog(t - gestureAt, 0f, 0.9f)
        if (phase != Phase.ORB || n <= 0f || n >= 1f) return atShift
        val amp = size * 0.03f * if (driving) 0.4f else 1f
        when (gesture) {
            "nod" -> atShift[1] = amp * wave(n)
            "shake" -> atShift[0] = amp * wave(n)
            "turn_left" -> atShift[0] = -amp * 1.5f * sin(n * Math.PI.toFloat())
            "turn_right" -> atShift[0] = amp * 1.5f * sin(n * Math.PI.toFloat())
        }
        return atShift
    }

    private fun drop(size: Float) = current().drop * size + if (listening) size * 0.05f else 0f

    /** 감정 색은 0.4초에 걸쳐 건너간다. */
    private fun current(): Mood {
        val k = prog(t - moodAt, 0f, 0.4f)
        if (k >= 1f) return mood
        return Mood(
            blend(moodFrom.color, mood.color, k),
            lerp(moodFrom.speed, mood.speed, k),
            lerp(moodFrom.drop, mood.drop, k),
            lerp(moodFrom.spread, mood.spread, k),
        )
    }

    private fun blend(a: Int, b: Int, k: Float) = Color.rgb(
        lerp(Color.red(a).toFloat(), Color.red(b).toFloat(), k).toInt(),
        lerp(Color.green(a).toFloat(), Color.green(b).toFloat(), k).toInt(),
        lerp(Color.blue(a).toFloat(), Color.blue(b).toFloat(), k).toInt(),
    )

    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun ease(u: Float) = if (u < 0.5f) 4 * u * u * u else 1 - Math.pow(-2.0 * u + 2, 3.0).toFloat() / 2
    private fun prog(v: Float, a: Float, b: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)
    private fun wave(u: Float) = sin(u * 4 * Math.PI.toFloat()) * (1 - u)

    companion object {
        /** i 번째 입자가 피보나치 나선의 몇 번째 자리에 놓이는지. 황금비에 가까운 보폭(443/720)으로 건너뛰어
         *  앞에서 몇 개를 잘라 그려도 위아래 고르게 퍼진다. 보폭이 n 과 서로소라 모든 자리를 한 번씩 쓴다. */
        internal fun sphereSlot(i: Int, n: Int): Int = ((i.toLong() * STRIDE) % n).toInt()
        private const val STRIDE = 443
        private const val MAX = 720          // 입자 수 (정차 중)
        private const val DRIVING = 340      // 주행 중에는 줄인다
        private const val SWELL = 0.65f      // 연결됨: 크게 부풀었다 돌아오기
        private const val SWIRL = 1.45f      // 풀려서 소용돌이
        private const val FORM = 2.5f        // 봇 모습으로 모임
        private const val BACK_SWIRL = 0.75f // 끊김: 모양이 풀림
        private const val BACK_ORB = 1.6f    // 다시 빛 방울
        private val WARM = 0xFFFF9A3C.toInt()   // 당근 코·목도리
    }
}
