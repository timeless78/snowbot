package io.snowbot.hub

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 봇 설명의 모습(`look`, 규약 3-5절)을 화면 입자가 모일 자리로 바꾼다.
 *
 * 허브 화면의 AI 는 입자 구름 하나다. 봇마다 그림을 갖지 않고, 봇이 보낸 외곽선(SVG path `d`)과
 * 얼굴 자리(`marks`)만 보고 입자를 모은다. 그리기(ParticleView)와 떨어진 순수 계산이라 단위 시험이 된다.
 *
 *  - [path]    : 외곽선 글자 → 꺾은선 덩어리들 (곡선은 잘게 쪼갠다)
 *  - [along]   : 꺾은선 위에 고른 간격으로 점 뽑기
 *  - [targets] : 외곽선 + 얼굴 자리 + 속 채움 → 입자 자리 (뷰 크기 1 로 정규화, 가운데가 0)
 */
object LookShape {

    data class Pt(val x: Float, val y: Float)

    /** 입자 색갈래. 코·목도리처럼 봇이 알려 준 따뜻한 부분만 주황. */
    enum class Tint { BLUE, ORANGE }

    /**
     * 입자 하나가 모일 자리. 좌표는 뷰 크기 1 기준(가운데 0, ±0.5 안).
     * [head] 는 끄덕일 때 함께 움직이는 머리 쪽, [edge] 는 외곽선 — 말할 때 출렁이는 가장자리.
     */
    data class Target(val x: Float, val y: Float, val tint: Tint, val head: Boolean, val edge: Boolean)

    /** 봇이 알려 준 모습. marks 는 eyes·brows·mouth(규약) + nose·scarf(허브가 더 그려 주는 자리). */
    data class Look(
        val outline: String,
        val width: Float,
        val height: Float,
        val color: String?,
        val marks: Map<String, List<Pt>>,
    )

    private val MARK_KEYS = listOf("eyes", "brows", "mouth", "nose", "scarf")

    fun parse(look: JSONObject?): Look? {
        val outline = look?.optString("outline").orEmpty()
        if (outline.isBlank()) return null
        val box = look!!.optJSONArray("viewbox")
        val w = box?.optDouble(0)?.toFloat() ?: 0f
        val h = box?.optDouble(1)?.toFloat() ?: 0f
        if (!(w > 0f) || !(h > 0f)) return null
        val marksJson = look.optJSONObject("marks")
        val marks = LinkedHashMap<String, List<Pt>>()
        for (key in MARK_KEYS) {
            val pts = points(marksJson?.optJSONArray(key))
            if (pts.isNotEmpty()) marks[key] = pts
        }
        return Look(outline, w, h, look.optString("color").ifEmpty { null }, marks)
    }

    private fun points(a: JSONArray?): List<Pt> = (0 until (a?.length() ?: 0)).mapNotNull { i ->
        a?.optJSONArray(i)?.takeIf { it.length() >= 2 }?.let { Pt(it.optDouble(0).toFloat(), it.optDouble(1).toFloat()) }
    }

    // ---- 외곽선 글자 읽기 ----

    /** SVG path `d` → 꺾은선 덩어리들. 곡선(C S Q T)은 잘게 쪼갠다. 원호(A)는 끝점까지 직선으로 잇는다. */
    fun path(d: String): List<List<Pt>> {
        val subs = mutableListOf<MutableList<Pt>>()
        var cur: MutableList<Pt>? = null
        val s = Scan(d)
        var x = 0f; var y = 0f          // 지금 붓 끝
        var sx = 0f; var sy = 0f        // 덩어리 시작점
        var cx = 0f; var cy = 0f        // 바로 앞 곡선의 두 번째 조절점 (S·T 용)
        var cmd = ' '
        var prev = ' '
        fun open() { cur = mutableListOf(Pt(x, y)).also { subs += it } }
        fun line(nx: Float, ny: Float) { if (cur == null) open(); cur!! += Pt(nx, ny); x = nx; y = ny }
        fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            if (cur == null) open()
            val steps = steps(hypot(x1 - x, y1 - y) + hypot(x2 - x1, y2 - y1) + hypot(x3 - x2, y3 - y2))
            val x0 = x; val y0 = y
            for (i in 1..steps) {
                val t = i / steps.toFloat(); val u = 1 - t
                cur!! += Pt(
                    u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                    u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3,
                )
            }
            cx = x2; cy = y2; x = x3; y = y3
        }
        fun quad(x1: Float, y1: Float, x2: Float, y2: Float) {
            val c1x = x + 2f / 3f * (x1 - x); val c1y = y + 2f / 3f * (y1 - y)
            val c2x = x2 + 2f / 3f * (x1 - x2); val c2y = y2 + 2f / 3f * (y1 - y2)
            cubic(c1x, c1y, c2x, c2y, x2, y2)
            cx = x1; cy = y1
        }
        while (true) {
            val letter = s.letter()
            if (letter != null) cmd = letter else if (!s.hasNumber()) break
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            when (cmd.uppercaseChar()) {
                'M' -> {
                    x = s.number() + ox; y = s.number() + oy
                    sx = x; sy = y
                    open()
                    cmd = if (rel) 'l' else 'L'   // 이어지는 숫자는 선 긋기
                }
                'L' -> line(s.number() + ox, s.number() + oy)
                'H' -> line(s.number() + ox, y)
                'V' -> line(x, s.number() + oy)
                'C' -> cubic(s.number() + ox, s.number() + oy, s.number() + ox, s.number() + oy, s.number() + ox, s.number() + oy)
                'S' -> {
                    val smooth = prev.uppercaseChar() in setOf('C', 'S')
                    val x1 = if (smooth) 2 * x - cx else x
                    val y1 = if (smooth) 2 * y - cy else y
                    cubic(x1, y1, s.number() + ox, s.number() + oy, s.number() + ox, s.number() + oy)
                }
                'Q' -> quad(s.number() + ox, s.number() + oy, s.number() + ox, s.number() + oy)
                'T' -> {
                    val smooth = prev.uppercaseChar() in setOf('Q', 'T')
                    quad(if (smooth) 2 * x - cx else x, if (smooth) 2 * y - cy else y, s.number() + ox, s.number() + oy)
                }
                'A' -> { repeat(5) { s.number() }; line(s.number() + ox, s.number() + oy) }
                'Z' -> { line(sx, sy); cur = null }
                else -> break
            }
            prev = cmd
        }
        return subs.filter { it.size >= 2 }
    }

    private fun steps(len: Float) = min(48, max(6, (len / 1.5f).toInt()))

    private class Scan(private val s: String) {
        private var i = 0
        private fun skip() { while (i < s.length && (s[i] == ',' || s[i].isWhitespace())) i++ }
        fun letter(): Char? {
            skip()
            if (i >= s.length || !s[i].isLetter()) return null
            return s[i++]
        }
        fun hasNumber(): Boolean {
            skip()
            return i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '+' || s[i] == '.')
        }
        fun number(): Float {
            skip()
            val from = i
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            var dot = false
            while (i < s.length && (s[i].isDigit() || (s[i] == '.' && !dot))) { if (s[i] == '.') dot = true; i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            return s.substring(from, i).toFloatOrNull() ?: 0f
        }
    }

    // ---- 고른 간격으로 점 뽑기 ----

    /** 꺾은선 덩어리들의 길이를 합쳐 [count] 개를 고른 간격으로. 덩어리가 길수록 점이 많이 간다. */
    fun along(subs: List<List<Pt>>, count: Int): List<Pt> {
        if (count <= 0) return emptyList()
        val seg = mutableListOf<Triple<Pt, Pt, Float>>()
        var total = 0f
        for (sub in subs) for (i in 0 until sub.size - 1) {
            val len = hypot(sub[i + 1].x - sub[i].x, sub[i + 1].y - sub[i].y)
            if (len <= 0f) continue
            total += len
            seg += Triple(sub[i], sub[i + 1], total)
        }
        if (seg.isEmpty() || total <= 0f) return emptyList()
        val out = ArrayList<Pt>(count)
        var k = 0
        var from = 0f
        for (i in 0 until count) {
            val want = total * i / count
            while (k < seg.size - 1 && seg[k].third < want) { from = seg[k].third; k++ }
            val (a, b, to) = seg[k]
            val len = to - from
            val u = if (len > 0f) ((want - from) / len).coerceIn(0f, 1f) else 0f
            out += Pt(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u)
        }
        return out
    }

    // ---- 입자 자리 ----

    /**
     * 외곽선(절반) + 얼굴 자리 + 속 채움 → 입자 [count] 개.
     * 어느 앞부분을 떼어 써도 모양이 읽히도록 정해진 차례로 섞는다(주행 중에는 입자를 줄인다).
     */
    fun targets(look: Look, count: Int): List<Target> {
        if (count <= 0) return emptyList()
        val subs = path(look.outline)
        if (subs.isEmpty()) return emptyList()
        val w = look.width
        val h = look.height
        val small = min(w, h)
        val cut = headCut(look)
        val rnd = Lcg(1_103_515_245L)
        val raw = ArrayList<Target>(count + 256)
        fun add(x: Float, y: Float, tint: Tint, edge: Boolean, head: Boolean = y < cut && tint != Tint.ORANGE) {
            val scale = 1f / max(w, h)
            raw += Target(
                ((x - w / 2) * scale).coerceIn(-0.5f, 0.5f),
                ((y - h / 2) * scale).coerceIn(-0.5f, 0.5f),
                tint, head, edge,
            )
        }

        along(subs, (count * 0.42f).toInt()).forEach { add(it.x, it.y, Tint.BLUE, edge = true) }

        for (eye in look.marks["eyes"].orEmpty()) {
            val r = small * 0.075f
            for (i in 0 until 26) {
                val a = i / 26f * 2 * Math.PI
                add(eye.x + (Math.cos(a) * r).toFloat(), eye.y + (Math.sin(a) * r).toFloat(), Tint.BLUE, edge = true)
            }
            for (i in 0 until 8) {
                val a = rnd.unit() * 2 * Math.PI
                val rr = sqrt(rnd.unit()) * r * 0.3f
                add(eye.x + (Math.cos(a) * rr).toFloat(), eye.y + (Math.sin(a) * rr).toFloat(), Tint.BLUE, edge = false)
            }
        }
        for (brow in look.marks["brows"].orEmpty()) {
            for (i in 0 until 14) {
                val u = i / 13f
                add(brow.x + (u - 0.5f) * w * 0.13f, brow.y - sin(u * Math.PI).toFloat() * h * 0.018f, Tint.BLUE, edge = true)
            }
        }
        for (mouth in look.marks["mouth"].orEmpty()) {
            for (i in 0 until 14) {
                val u = i / 13f
                add(mouth.x + (u - 0.5f) * w * 0.14f, mouth.y + sin(u * Math.PI).toFloat() * h * 0.015f, Tint.BLUE, edge = true)
            }
        }
        // 당근 코: 가운데에서 오른쪽으로 뻗는 삼각형 (주황)
        for (nose in look.marks["nose"].orEmpty()) {
            val ax = nose.x - w * 0.02f; val ay = nose.y - h * 0.018f
            val bx = nose.x + w * 0.15f; val by = nose.y + h * 0.008f
            val cxp = nose.x - w * 0.01f; val cyp = nose.y + h * 0.026f
            for (i in 0 until 26) {
                var u = rnd.unit(); var v = rnd.unit()
                if (u + v > 1f) { u = 1 - u; v = 1 - v }
                add(ax + (bx - ax) * u + (cxp - ax) * v, ay + (by - ay) * u + (cyp - ay) * v, Tint.ORANGE, edge = false)
            }
        }
        // 목도리: 알려 준 점들을 잇는 띠 두 줄 (주황)
        look.marks["scarf"]?.let { band ->
            if (band.size >= 2) {
                val line = listOf(band)
                for (row in 0 until 2) {
                    along(line, 48).forEach { add(it.x, it.y + row * h * 0.022f, Tint.ORANGE, edge = row == 0) }
                }
            }
        }

        // 속 채움: 외곽선 안쪽에 옅게 (그림의 몸통 느낌)
        val box = subs.flatten()
        val x0 = box.minOf { it.x }; val x1 = box.maxOf { it.x }
        val y0 = box.minOf { it.y }; val y1 = box.maxOf { it.y }
        var guard = 0
        while (raw.size < count && guard < count * 40) {
            guard++
            val px = x0 + rnd.unit() * (x1 - x0)
            val py = y0 + rnd.unit() * (y1 - y0)
            if (inside(subs, px, py)) add(px, py, Tint.BLUE, edge = false)
        }

        // 정해진 차례로 섞기 (Fisher-Yates, 같은 모습이면 늘 같은 결과)
        val shuffle = Lcg(22_695_477L)
        for (i in raw.size - 1 downTo 1) {
            val j = (shuffle.unit() * (i + 1)).toInt().coerceIn(0, i)
            val tmp = raw[i]; raw[i] = raw[j]; raw[j] = tmp
        }
        return if (raw.size > count) raw.subList(0, count).toList() else raw
    }

    /** 끄덕일 때 함께 움직이는 머리의 아래 끝. 얼굴 자리가 있으면 그 아래, 없으면 위에서 45%. */
    private fun headCut(look: Look): Float {
        val face = (look.marks["eyes"].orEmpty() + look.marks["brows"].orEmpty() + look.marks["nose"].orEmpty())
        if (face.isEmpty()) return look.height * 0.45f
        return face.maxOf { it.y } + look.height * 0.15f
    }

    private fun inside(subs: List<List<Pt>>, px: Float, py: Float): Boolean {
        var hit = false
        for (sub in subs) for (i in 0 until sub.size - 1) {
            val a = sub[i]; val b = sub[i + 1]
            if ((a.y > py) != (b.y > py) && abs(b.y - a.y) > 1e-6f &&
                px < a.x + (py - a.y) / (b.y - a.y) * (b.x - a.x)
            ) hit = !hit
        }
        return hit
    }

    /** 늘 같은 자리를 주는 작은 난수 (시험에서 결과가 흔들리지 않게). */
    private class Lcg(seed: Long) {
        private var s = seed and 0xFFFFFFFFL
        fun unit(): Float {
            s = (s * 1_664_525L + 1_013_904_223L) and 0xFFFFFFFFL
            return (s ushr 8).toFloat() / (1 shl 24).toFloat()
        }
    }
}
