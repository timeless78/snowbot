package io.snowbot.hub

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 봇 설명의 모습(`look`, 규약 3-5절) → 화면 입자가 모일 자리.
 * 그림 그리기(ParticleView)와 떨어진 순수 계산만 (외곽선 글자 읽기 · 고른 간격 점 뽑기 · 입자 자리 만들기).
 */
class LookShapeTest {

    private val square = "M10 10 L110 10 L110 110 L10 110 Z"

    /** 원을 큐빅 네 조각으로 (k = 0.5523 r). 가운데 (60,80), 반지름 40. */
    private val circle = "M100 80 C100 102.1 82.1 120 60 120 C37.9 120 20 102.1 20 80 " +
        "C20 57.9 37.9 40 60 40 C82.1 40 100 57.9 100 80 Z"

    private fun look(outline: String, marks: String = "{}") = LookShape.parse(
        JSONObject("""{"outline":"$outline","viewbox":[120,160],"color":"#9FD8FF","marks":$marks}""")
    )!!

    // ---- 외곽선 글자 읽기 ----

    @Test
    fun `사각형 외곽선은 한 덩어리로 읽히고 네 꼭짓점을 지난다`() {
        val subs = LookShape.path(square)
        assertEquals(1, subs.size)
        val pts = subs[0]
        assertTrue("점이 모자람 ${pts.size}", pts.size >= 5)
        for (corner in listOf(10f to 10f, 110f to 10f, 110f to 110f, 10f to 110f)) {
            assertTrue("꼭짓점 $corner 없음", pts.any { abs(it.x - corner.first) < 0.01f && abs(it.y - corner.second) < 0.01f })
        }
        // Z 로 닫혔으니 마지막이 처음으로 돌아온다
        assertEquals(pts.first().x, pts.last().x, 0.01f)
        assertEquals(pts.first().y, pts.last().y, 0.01f)
    }

    @Test
    fun `큐빅 곡선은 잘게 쪼개져 원 위에 놓인다`() {
        val pts = LookShape.path(circle).single()
        assertTrue("곡선이 안 쪼개짐 ${pts.size}", pts.size >= 40)
        for (p in pts) assertEquals("원 밖 $p", 40f, hypot(p.x - 60.0, p.y - 80.0).toFloat(), 0.6f)
    }

    @Test
    fun `여러 덩어리와 상대 좌표 명령을 읽는다`() {
        val subs = LookShape.path("M0 0 h10 v10 h-10 z m20 0 l10 0 l0 10 z")
        assertEquals(2, subs.size)
        assertTrue(subs[1].any { abs(it.x - 30f) < 0.01f && abs(it.y - 10f) < 0.01f })
    }

    // ---- 고른 간격 ----

    @Test
    fun `점은 달라는 수만큼 고른 간격으로 나온다`() {
        val pts = LookShape.along(LookShape.path(square), 40)
        assertEquals(40, pts.size)
        val gaps = pts.zipWithNext { a, b -> hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) }
        val mean = gaps.average()
        assertEquals(400.0 / 40, mean, 1.0)   // 둘레 400 / 40
        for (g in gaps) assertTrue("간격이 들쭉날쭉 $g (평균 $mean)", abs(g - mean) < mean * 0.35)
    }

    // ---- 봇 설명 읽기 ----

    @Test
    fun `look 이 없는 봇은 모습이 없다`() {
        assertNull(LookShape.parse(null))
        assertNull(LookShape.parse(JSONObject("""{"viewbox":[1,1]}""")))
        assertNull(BotConnect.parse("A", JSONObject("""{"bcp":"0.3","type":"hello","parts":[]}"""), true).look)
    }

    @Test
    fun `봇 설명에서 모습을 읽는다`() {
        val hello = JSONObject(
            """{"bcp":"0.3","type":"hello","bot":{"id":"SNW","name":"스노봇"},
                "look":{"outline":"$square","viewbox":[120,160],"color":"#F4F8FF",
                        "marks":{"eyes":[[46,44],[74,44]],"brows":[[46,30],[74,30]]}},
                "parts":[{"id":"spk","kind":"speaker"}],
                "expressions":{"emotions":["neutral"],"gestures":[]}}"""
        )
        val look = BotConnect.parse("SNW", hello, true).look
        assertNotNull(look)
        assertEquals(120f, look!!.width, 0f)
        assertEquals(160f, look.height, 0f)
        assertEquals("#F4F8FF", look.color)
        assertEquals(2, look.marks.getValue("eyes").size)
        assertEquals(46f, look.marks.getValue("eyes")[0].x, 0f)
    }

    // ---- 입자 자리 ----

    @Test
    fun `입자는 달라는 수만큼 나오고 창 안에 들어간다`() {
        val targets = LookShape.targets(look(square), 300)
        assertEquals(300, targets.size)
        // 뷰 크기 1 로 정규화 — 가로세로 모두 ±0.5 안
        for (t in targets) {
            assertTrue("창 밖 $t", abs(t.x) <= 0.5f && abs(t.y) <= 0.5f)
        }
    }

    @Test
    fun `코와 목도리는 주황, 나머지는 파랑`() {
        val marks = """{"eyes":[[46,44],[74,44]],"nose":[[60,56]],"scarf":[[30,80],[60,76],[90,80]]}"""
        val targets = LookShape.targets(look(square, marks), 400)
        assertTrue("주황 입자가 없다", targets.count { it.tint == LookShape.Tint.ORANGE } > 30)
        assertTrue("파랑 입자가 없다", targets.count { it.tint == LookShape.Tint.BLUE } > 100)
        // 목도리는 끄덕일 때 따라 움직이지 않는다 (머리가 아니다)
        assertTrue(targets.filter { it.tint == LookShape.Tint.ORANGE }.none { it.head })
    }

    @Test
    fun `눈 위쪽은 머리로 잡혀 끄덕임을 따른다`() {
        val marks = """{"eyes":[[46,44],[74,44]],"nose":[[60,56]]}"""
        val targets = LookShape.targets(look(square, marks), 400)
        val head = targets.filter { it.head }
        assertTrue("머리 입자가 없다", head.size > 50)
        assertTrue("머리가 아래쪽까지 내려왔다", head.all { it.y < 0.2f })
    }

    @Test
    fun `입자 수를 줄여도 외곽선 입자가 고루 남는다`() {
        val targets = LookShape.targets(look(circle), 400)
        val few = targets.take(140)
        assertTrue("줄인 뒤 외곽선이 사라짐", few.count { it.edge } > 25)
    }

    /** server/tools/sim.py 의 가상 스노봇이 보내는 모습 그대로. */
    @Test
    fun `가상 스노봇 모습은 머리·몸통·그릴 세 덩어리로 읽힌다`() {
        val outline =
            "M98 48 C98 69 81 86 60 86 C39 86 22 69 22 48 C22 27 39 10 60 10 C81 10 98 27 98 48 Z " +
                "M90 112 C90 128.6 76.6 142 60 142 C43.4 142 30 128.6 30 112 C30 95.4 43.4 82 60 82 C76.6 82 90 95.4 90 112 Z " +
                "M67 118 C67 121.9 63.9 125 60 125 C56.1 125 53 121.9 53 118 C53 114.1 56.1 111 60 111 C63.9 111 67 114.1 67 118 Z"
        assertEquals(3, LookShape.path(outline).size)
        val marks = """{"eyes":[[46,44],[74,44]],"brows":[[46,30],[74,30]],"nose":[[60,56]],
                        "scarf":[[26,82],[43,77],[60,76],[77,77],[94,82]]}"""
        val targets = LookShape.targets(look(outline, marks), 520)
        assertEquals(520, targets.size)
        // 머리(눈·눈썹·코·머리 외곽선)는 끄덕임을 따르고, 몸통·목도리는 가만히 있는다
        assertTrue(targets.count { it.head } > 80)
        assertTrue(targets.count { !it.head } > 80)
        assertTrue(targets.count { it.tint == LookShape.Tint.ORANGE } > 50)
    }

    @Test
    fun `같은 모습은 늘 같은 자리를 준다`() {
        val a = LookShape.targets(look(circle), 200)
        val b = LookShape.targets(look(circle), 200)
        assertEquals(a, b)
    }
}
