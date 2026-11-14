package io.snowbot.hub

import io.snowbot.hub.Ladder.Car
import io.snowbot.hub.Ladder.Cue
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 다가가기 사다리: 승인된 수정안의 장면들 + 판정 문서(docs/pleos/13-ladder-review.md)의 근거가 되는 경우들.
 * 사다리는 편의 권유(답답함·김서림)만 맡는다 — 경고등·휴식·인사는 CarCoach 쪽이라 여기 시험이 없다.
 *
 * 2026-11-09: "바쁜 정도"를 없애면서 장면 5(바쁠 때는 아무것도 하지 않는다) 시험을 지웠다.
 */
class LadderTest {

    private val min = 60_000L
    private val recirc = Car(recirc = true)
    private val outside = Car(recirc = false)

    /** 내기 순환으로 달리다 CO₂ 1,500 에서 한마디까지 온 상태 (6분째). */
    private fun saidAtSixMin() = Ladder.air().also { l ->
        assertNull("800 은 아무 일 없음", l.updateAir(800, recirc, 0))
        assertEquals("1,400 → 표정만", Cue.FACE, l.updateAir(1400, recirc, 1 * min))
        assertEquals("1,500 → 한마디", Cue.SAY, l.updateAir(1500, recirc, 6 * min))
    }

    // ---- 칸은 네 개, 경고등 경로는 없다 ----

    @Test fun fourRungsAndNoTurnRung() {
        assertEquals(listOf(0, 1, 2, 3), listOf(Ladder.NONE, Ladder.FACE, Ladder.SAY, Ladder.AGAIN))
        assertEquals(
            "고개 돌림 칸은 없다 — 고개는 말·표정에 곁들인다",
            listOf(Cue.FACE, Cue.SAY, Cue.SAY_AGAIN, Cue.RELIEF, Cue.RELAX), Cue.entries,
        )
    }

    /** 1,800 같은 고정 문턱을 뺐다: 값만으로는 3칸을 원할 수 없다. 3칸은 "말한 때보다 나빠짐"으로만 간다. */
    @Test fun valueAloneNeverAsksForTheThirdRung() {
        for (co2 in 0..5000) assertTrue("$co2", Ladder.airWanted(co2) != Ladder.AGAIN)
        for (h in 0..100) assertTrue("$h", Ladder.fogWanted(h, true) != Ladder.AGAIN)
    }

    // ---- 장면 1: 답답함 정상 흐름 (표정 → 한마디 → 외기 전환 + CO₂ 하강 → 안도 → 0) ----

    @Test fun airFaceThenSayThenReliefWhenOutsideAirActuallyWorks() {
        val l = saidAtSixMin()
        assertEquals(Ladder.SAY, l.rung)
        assertNull("외기로 바꾼 순간: 아직 값이 안 내려갔다", l.updateAir(1500, outside, 7 * min))
        assertNull("조금 내려갔지만 여유 폭 안", l.updateAir(1400, outside, 8 * min))
        assertEquals("실제로 내려갔다 → 안도", Cue.RELIEF, l.updateAir(1350, outside, 9 * min))
        assertEquals(Ladder.NONE, l.rung)
    }

    @Test fun windowOpenIsAlsoAReaction() {
        val l = saidAtSixMin()
        l.updateAir(1500, recirc.copy(windowOpen = true), 7 * min)
        assertEquals(Cue.RELIEF, l.updateAir(1300, recirc.copy(windowOpen = true), 8 * min))
    }

    /** 반응 뒤 유예: 고맙다고 해 놓고 바로 또 답답해하지 않는다. */
    @Test fun graceAfterReliefThenStartsOverIfItClimbsAgain() {
        val l = saidAtSixMin()
        l.updateAir(1300, outside, 7 * min)   // 안도
        assertNull("유예 안에서는 값이 올라도", l.updateAir(1600, outside, 17 * min - 1))
        assertEquals("유예가 끝나면 처음부터", Cue.SAY, l.updateAir(1600, outside, 17 * min))
    }

    // ---- 장면 2: 행동만 있고 결과가 없으면 안도하지 않는다 ----

    @Test fun actionWithoutResultDoesNotRelieve() {
        val l = saidAtSixMin()
        for (t in 7L..30L) assertNull("${t}분: 외기로 바꿨지만 CO₂ 가 그대로다", l.updateAir(1500, outside, t * min))
        assertEquals("칸도 그대로", Ladder.SAY, l.rung)
    }

    /** 외기로 바꿨는데도 더 나빠지면, 다른 말로 한 번 더 한다(창문 쪽을 권한다). */
    @Test fun actionButWorseStillSaysOnceMore() {
        val l = saidAtSixMin()
        l.updateAir(1500, outside, 7 * min)
        assertEquals(Cue.SAY_AGAIN, l.updateAir(1700, outside, 16 * min))
    }

    /** 3칸까지 간 뒤에도 따랐는지는 계속 본다. */
    @Test fun reliefStillWorksAfterTheThirdRung() {
        val l = saidAtSixMin()
        assertEquals(Cue.SAY_AGAIN, l.updateAir(1700, recirc, 16 * min))
        assertNull("창문을 열었지만 아직", l.updateAir(1700, recirc.copy(windowOpen = true), 17 * min))
        assertEquals(Cue.RELIEF, l.updateAir(1500, recirc.copy(windowOpen = true), 20 * min))
    }

    // ---- 장면 3: 무시 + 악화 → 한 번 더 → 그 뒤 침묵 ----

    @Test fun ignoredAndWorseSaysOnceMoreThenStaysSilent() {
        val l = saidAtSixMin()
        assertEquals(Cue.SAY_AGAIN, l.updateAir(1700, recirc, 16 * min))
        assertEquals(Ladder.AGAIN, l.rung)
        for (t in 17L..120L) assertNull("${t}분", l.updateAir(2500, recirc, t * min))
    }

    @Test fun onceMoreNeedsBothTimeAndWorse() {
        assertNull("나빠졌지만 아직 10분 전", saidAtSixMin().updateAir(1700, recirc, 16 * min - 1))
        assertNull("10분 지났지만 여유 폭 안(1,650 이하)", saidAtSixMin().updateAir(1650, recirc, 30 * min))
    }

    // ---- 장면 4: 무시 + 그대로이거나 나아짐 → 침묵 ----

    @Test fun ignoredButNotWorseStaysSilent() {
        for (co2 in listOf(1500, 1400, 1360)) {
            val l = saidAtSixMin()
            assertNull("$co2 ppm 으로 그대로·나아짐", l.updateAir(co2, recirc, 30 * min))
            assertEquals(Ladder.SAY, l.rung)
        }
    }

    /** 처음 읽은 값이 아무리 높아도 한마디부터. 그 값이 그대로면 더 나빠진 게 아니므로 다시 말하지 않는다. */
    @Test fun firstReadingVeryHighStartsAtSayNotAgain() {
        val l = Ladder.air()
        assertEquals(Cue.SAY, l.updateAir(2000, recirc, 0))
        assertNull("10분 지났지만 그대로", l.updateAir(2000, recirc, 10 * min))
        assertEquals(Cue.SAY_AGAIN, l.updateAir(2200, recirc, 20 * min))
    }

    /** "나빠졌다"의 비교 기준은 **실제로 말한 때**의 값이다. */
    @Test fun worseIsComparedWithTheValueWhenActuallySaid() {
        val l = Ladder.air()
        assertNull("1,200~1,349 는 판단 보류", l.updateAir(1250, recirc, 0))
        assertEquals(Cue.SAY, l.updateAir(1900, recirc, 5 * min))
        assertNull("1,500 보다는 높지만 말한 때(1,900)보다 낮다", l.updateAir(1850, recirc, 15 * min))
        assertEquals(Cue.SAY_AGAIN, l.updateAir(2100, recirc, 16 * min))
    }

    /** 풀린 뒤 다시 시작하면 지난번 높은 값과 비교하지 않는다. */
    @Test fun worseIsNotComparedWithAnEarlierEpisode() {
        val l = Ladder.air()
        assertEquals(Cue.SAY, l.updateAir(2500, recirc, 0))
        assertEquals(Cue.RELAX, l.updateAir(1100, recirc, 5 * min))
        assertEquals(Cue.SAY, l.updateAir(1500, recirc, 10 * min))
        assertEquals(Cue.SAY_AGAIN, l.updateAir(1700, recirc, 20 * min))
    }

    // ---- 장면 6: 문턱 근처에서 흔들려도 표정은 한 번 ----

    @Test fun noFlickerAroundTheFaceThreshold() {
        val l = Ladder.air()
        val cues = listOf(1360, 1340, 1355, 1345, 1350, 1338, 1365, 1342).mapIndexedNotNull { i, co2 ->
            l.updateAir(co2, recirc, i * 10_000L)
        }
        assertEquals(listOf(Cue.FACE), cues)
        assertEquals("1,200 아래로 내려가야 풀린다", Ladder.FACE, l.rung)
    }

    @Test fun airWantedBoundaries() {
        assertEquals("해제 값은 해석기의 1,200 과 같다", Interpreter.CO2_OFF, Ladder.AIR_CLEAR)
        assertEquals("한마디 값은 해석기의 1,500 과 같다", Interpreter.CO2_ON, Ladder.AIR_SAY)
        assertTrue("1칸 문턱은 해제 값과 떨어져 있다", Ladder.AIR_FACE - Ladder.AIR_CLEAR >= 100)
        assertEquals(Ladder.NONE, Ladder.airWanted(1199))
        assertNull("1,200~1,349 는 보류", Ladder.airWanted(1200))
        assertNull(Ladder.airWanted(1349))
        assertEquals(Ladder.FACE, Ladder.airWanted(1350))
        assertEquals(Ladder.FACE, Ladder.airWanted(1499))
        assertEquals(Ladder.SAY, Ladder.airWanted(1500))
        assertNull("값이 없으면 판단하지 않는다", Ladder.airWanted(null))
    }

    @Test fun sensorDropoutKeepsRung() {
        val l = saidAtSixMin()
        assertNull(l.updateAir(null, recirc, 7 * min))
        assertEquals(Ladder.SAY, l.rung)
    }

    @Test fun selfRecoveryRelaxesAndCanStartOver() {
        val l = saidAtSixMin()
        assertNull("1,200 까지는 풀린 게 아니다", l.updateAir(1250, recirc, 20 * min))
        assertEquals(Cue.RELAX, l.updateAir(1190, recirc, 25 * min))
        assertEquals(Ladder.NONE, l.rung)
        assertEquals("다시 처음부터", Cue.FACE, l.updateAir(1400, recirc, 30 * min))
    }

    // ---- 반응을 읽는 규칙 ----

    /** 말하기 전(표정만)에 운전자가 한 일은 봇 때문이라고 볼 근거가 없다 — 안도하지 않고 조용히 풀린다. */
    @Test fun reactionBeforeSpeakingIsNotCreditedToTheBot() {
        val l = Ladder.air()
        l.updateAir(800, recirc, 0)
        assertEquals(Cue.FACE, l.updateAir(1400, recirc, 1 * min))
        assertNull(l.updateAir(1400, outside, 2 * min))
        assertEquals("안도가 아니라 조용히 풀림", Cue.RELAX, l.updateAir(1100, outside, 5 * min))
    }

    @Test fun onlyAChangeIsAReaction() {
        // 처음부터 외기·창문 열림 — 운전자가 지금 한 일이 아니다
        val l = Ladder.air()
        assertEquals(Cue.SAY, l.updateAir(1600, Car(false, true), 0))
        assertNull(l.updateAir(1400, Car(false, true), 1 * min))
        assertEquals(Ladder.SAY, l.rung)
        // 값을 몰랐다가 처음 알게 된 것도 반응이 아니다
        val m = Ladder.air()
        assertEquals(Cue.SAY, m.updateAir(1600, Car(null), 0))
        assertNull(m.updateAir(1400, Car(false), 1 * min))
    }

    @Test fun reactionAtRungZeroIsNotRelief() {
        val l = Ladder.air()
        l.updateAir(800, recirc, 0)
        assertNull(l.updateAir(800, outside, 1 * min))
    }

    @Test fun carSignalsAllComeFromTodaysSnapshot() {
        val snap = CarState.Snapshot(
            gear = "D", drivingMin = 0, attention = false, tripRaw = null, error = null,
            signals = CarState.Signals(gear = "D", recirc = true, ac = false, defrost = true),
            cabin = Checkup.Cabin(windowsOpen = listOf("ROW_1_LEFT")),
        )
        assertEquals(Car(recirc = true, windowOpen = true, ac = false, defrost = true), Car.of(snap))
    }

    // ---- 장면 7: 김서림 ----

    @Test fun fogFaceThenSayThenReliefWhenWindowOpens() {
        val l = Ladder.fog()
        assertNull(l.updateFog(60, true, recirc, 0))
        assertEquals(Cue.FACE, l.updateFog(73, true, recirc, 1 * min))
        assertEquals(Cue.SAY, l.updateFog(78, true, recirc, 6 * min))
        val opened = recirc.copy(windowOpen = true)
        assertNull("열었지만 아직 습도가 그대로", l.updateFog(78, true, opened, 7 * min))
        assertEquals(Cue.RELIEF, l.updateFog(75, true, opened, 8 * min))
        assertEquals(Ladder.NONE, l.rung)
    }

    /** 봇이 "에어컨 켜 볼까요?"라고 권하므로 에어컨을 켠 것도, 앞유리 성에 제거를 켠 것도 따른 것이다. */
    @Test fun fogReliefWhenAcOrDefrostIsTurnedOn() {
        for (acted in listOf(Car(ac = true, defrost = false), Car(ac = false, defrost = true))) {
            val l = Ladder.fog()
            val before = Car(ac = false, defrost = false)
            assertEquals(Cue.SAY, l.updateFog(78, true, before, 0))
            assertNull("켰지만 아직 습도가 그대로", l.updateFog(78, true, acted, 1 * min))
            assertEquals("$acted", Cue.RELIEF, l.updateFog(75, true, acted, 3 * min))
        }
    }

    @Test fun acAlreadyOnIsNotAReaction() {
        // 처음부터 켜져 있던 에어컨은 운전자가 봇 말을 듣고 한 일이 아니다 — 바뀐 순간만 센다
        val l = Ladder.fog()
        val on = Car(ac = true)
        assertEquals(Cue.SAY, l.updateFog(78, true, on, 0))
        assertEquals(Cue.RELAX, l.updateFog(60, true, on, 3 * min))   // 저절로 풀림이지 안도가 아니다
    }

    @Test fun fogIgnoredAndWorseSaysOnceMoreThenSilent() {
        val l = Ladder.fog()
        assertEquals(Cue.SAY, l.updateFog(78, true, recirc, 0))
        assertNull("1% 올랐지만 여유 폭 안", l.updateFog(79, true, recirc, 10 * min))
        assertEquals(Cue.SAY_AGAIN, l.updateFog(82, true, recirc, 20 * min))
        for (t in 21L..60L) assertNull("${t}분", l.updateFog(95, true, recirc, t * min))
    }

    @Test fun fogWantedBoundaries() {
        assertEquals("해제·한마디 값은 해석기와 같다", Interpreter.HUMID_OFF to Interpreter.HUMID_ON, Ladder.FOG_CLEAR to Ladder.FOG_SAY)
        assertEquals("비도 밤도 아니면 상황이 아니다", Ladder.NONE, Ladder.fogWanted(95, false))
        assertEquals(Ladder.NONE, Ladder.fogWanted(69, true))
        assertNull("70~72 는 보류", Ladder.fogWanted(70, true))
        assertNull(Ladder.fogWanted(72, true))
        assertEquals(Ladder.FACE, Ladder.fogWanted(73, true))
        assertEquals(Ladder.FACE, Ladder.fogWanted(74, true))
        assertEquals(Ladder.SAY, Ladder.fogWanted(75, true))
        assertNull(Ladder.fogWanted(null, true))
    }

    /** 비가 그치거나 날이 밝으면 상황 자체가 사라진다 — 운전자가 한 일이 아니라 조용히 풀린다. */
    @Test fun fogClearsQuietlyWhenTheRainStops() {
        val l = Ladder.fog()
        assertEquals(Cue.SAY, l.updateFog(80, true, recirc, 0))
        assertEquals(Cue.RELAX, l.updateFog(80, false, recirc, 5 * min))
        assertEquals(Ladder.NONE, l.rung)
    }

    // ---- 봇의 몸에 닿는지 (판정 4-4의 근거) ----

    /** 안도 표정은 새 감정 없이 된다: 규약이 정한 최소 어휘만 가진 봇에도 그대로 간다. */
    @Test fun reliefEmotionIsInMinimumVocabulary() {
        assertEquals(Ladder.RELIEF_EMOTION, BotConnect.express(minimalBot(), Ladder.RELIEF_EMOTION, null).getString("emotion"))
    }

    /** 판정 근거: 어휘가 최소인 봇은 답답함의 sleepy 가 neutral 로 바뀐다 — 1칸을 했는데 아무것도 안 보인다. */
    @Test fun faceRungCanBeInvisibleOnMinimalBot() {
        assertEquals("neutral", BotConnect.express(minimalBot(), Interpreter.Situation.STUFFY.emotion, null).getString("emotion"))
    }

    private fun minimalBot() = BotConnect.parse("B", JSONObject(
        """{"bcp":"0.3","bot":{"name":"최소"},"expressions":{"emotions":["neutral","happy","worried","alert"],"gestures":[]}}""",
    ), true)
}
