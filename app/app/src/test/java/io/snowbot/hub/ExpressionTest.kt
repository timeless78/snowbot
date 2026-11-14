package io.snowbot.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 표현: 판단 결과 → 감정·몸짓·문장 조각. 기술 명세서 4-1 표를 고정한다.
 * 글자로 바꾸는 것은 안드로이드 자원을 가진 서비스가 하므로, 여기서는 **어떤 자원을 고르는지**만 본다.
 */
class ExpressionTest {

    /** 어느 상황이든 말을 만들 수 있을 만큼의 근거 값. */
    private val ALL_FACTS: Map<String, Any> = mapOf(
        "hours" to 2, "mins" to 5, "co2" to 1500, "from" to 1500, "to" to 1700, "humidity" to 78,
        "range_km" to 38, "dest_km" to 52, "dest_min" to 40, "congestion_km" to 3, "what" to "창문",
    )

    private fun look(s: Situation, d: Delivery = Delivery.SAY) = Expression.look(Judgement(s, d))

    // ---- 감정 표 (기술 명세서 4-1) ----

    @Test fun theEmotionTable() {
        assertEquals("worried", look(Situation.WARNING_ON).emotion)
        assertEquals("worried", look(Situation.ATTENTION).emotion)
        assertEquals("sleepy", look(Situation.LONG_DRIVE).emotion)
        assertEquals("sleepy", look(Situation.STUFFY).emotion)
        assertEquals("worried", look(Situation.FOG_RISK).emotion)
        assertEquals("greet", look(Situation.DEPART).emotion)
        assertEquals("happy", look(Situation.ARRIVE).emotion)
    }

    @Test fun reliefIsHappyAndBackToNormalIsNeutral() {
        assertEquals(Ladder.RELIEF_EMOTION, look(Situation.STUFFY, Delivery.RELIEF).emotion)
        assertEquals("happy", Ladder.RELIEF_EMOTION)
        assertEquals("neutral", look(Situation.STUFFY, Delivery.CALM).emotion)
    }

    @Test fun nodGoesWithGreetingsAndTheFogWord() {
        assertEquals("nod", look(Situation.DEPART).gesture)
        assertEquals("nod", look(Situation.ARRIVE).gesture)
        assertEquals("nod", look(Situation.FOG_RISK).gesture)
        assertNull("경고등은 고개를 움직이지 않는다", look(Situation.WARNING_ON).gesture)
    }

    @Test fun faceOnlyNeverMovesTheHead() {
        assertNull(look(Situation.FOG_RISK, Delivery.FACE_ONLY).gesture)
        assertEquals("worried", look(Situation.FOG_RISK, Delivery.FACE_ONLY).emotion)
    }

    /** 쉬자는 말은 안전이다 — "그냥 둬"로 끌 수 없다(13-ladder-review 4-2). */
    @Test fun theRestWarningIsSafetyAndCannotBeTurnedOff() {
        assertEquals(Kind.SAFETY, Situation.LONG_DRIVE.kind)
        assertEquals(Kind.SAFETY, Situation.ATTENTION.kind)
        val dropped = setOf(Situation.LONG_DRIVE, Situation.ATTENTION, Situation.RAIN_WINDOW_OPEN)
        assertFalse("안전은 못 끈다", muted(Judgement(Situation.LONG_DRIVE), dropped))
        assertFalse(muted(Judgement(Situation.ATTENTION), dropped))
        assertTrue("권유는 끌 수 있다", muted(Judgement(Situation.RAIN_WINDOW_OPEN), dropped))
        assertFalse("표정만 가는 것은 막지 않는다",
            muted(Judgement(Situation.RAIN_WINDOW_OPEN, Delivery.CALM), dropped))
        assertFalse("끄라고 하지 않은 것은 그대로", muted(Judgement(Situation.STUFFY), dropped))
    }

    /** 도착 전에 미리 알리는 쪽은 제안이라 권유로 둔다. */
    @Test fun theArrivalTimeOfferIsATip() =
        assertEquals(Kind.TIP, Situation.DRIVE_TIME_OVER.kind)

    @Test fun everySituationHasAnEmotion() {
        for (s in Situation.entries) assertTrue(s.name, Expression.look(Judgement(s)).emotion.isNotEmpty())
    }

    // ---- 말 없이 가는 전달 방식 ----

    @Test fun silentDeliveriesHaveNoWords() {
        for (d in listOf(Delivery.FACE_ONLY, Delivery.RELIEF, Delivery.CALM)) {
            assertTrue(d.name, Expression.silent(d))
            assertEquals(d.name, emptyList<Expression.Piece>(), Expression.pieces(Judgement(Situation.STUFFY, d, ALL_FACTS)))
        }
        assertTrue(Expression.pieces(Judgement(Situation.STUFFY, Delivery.SAY, ALL_FACTS)).isNotEmpty())
    }

    @Test fun everyTalkingSituationHasWords() {
        for (s in Situation.entries) {
            if (s == Situation.DROPPED) continue        // 그만두라는 말에는 표정만 바꾼다
            val j = Judgement(s, facts = ALL_FACTS, warning = CarWarnings.info("TPMS"))
            assertTrue(s.name, Expression.pieces(j).isNotEmpty())
        }
    }

    // ---- 근거 숫자가 문장에 들어간다 ----

    @Test fun theStuffyWordCarriesTheCo2Value() {
        val j = Judgement(Situation.STUFFY, facts = mapOf("co2" to 1500, "recirc" to true))
        val p = Expression.pieces(j).single() as Expression.Text
        assertEquals(R.string.line_stuffy_recirc, p.res)
        assertEquals(listOf<Any?>("1,500"), p.args)
    }

    @Test fun sayingItAgainTalksAboutTheChange() {
        val j = Judgement(Situation.STUFFY, Delivery.SAY_AGAIN, facts = mapOf("from" to 1500, "to" to 1700))
        val p = Expression.pieces(j).single() as Expression.Text
        assertEquals(R.string.line_air_again, p.res)
        assertEquals(listOf<Any?>("1,500", "1,700"), p.args)
    }

    @Test fun theFogWordChangesWhenTheAcIsAlreadyOn() {
        fun res(ac: Boolean) = (Expression.pieces(
            Judgement(Situation.FOG_RISK, facts = mapOf("ac" to ac, "humidity" to 78))
        ).single() as Expression.Text).res
        assertEquals(R.string.line_fog, res(false))
        assertEquals(R.string.line_fog_ac_on, res(true))
    }

    @Test fun theRangeWordNamesBothDistancesAndThePlace() {
        val j = Judgement(Situation.RANGE_SHORT, facts = mapOf("range_km" to 38, "dest_km" to 52), offer = Offer.FUEL)
        val p = Expression.pieces(j).single() as Expression.Text
        assertEquals(R.string.watch_range_short, p.res)
        assertEquals(listOf<Any?>(38, 52, "주유소"), p.args)
    }

    @Test fun electricCarsHearAboutChargers() {
        val j = Judgement(Situation.RANGE_SHORT, facts = mapOf("range_km" to 38, "dest_km" to 52), offer = Offer.CHARGE)
        assertEquals("충전소", ((Expression.pieces(j).single()) as Expression.Text).args[2])
    }

    @Test fun theRestWordNamesTheHoursAndWhatIsLeft() {
        val j = Judgement(Situation.DRIVE_TIME_OVER, facts = mapOf("hours" to 2, "dest_min" to 40))
        val p = Expression.pieces(j).single() as Expression.Text
        assertEquals(R.string.watch_time_over, p.res)
        assertEquals(listOf<Any?>(2, 40), p.args)
    }

    @Test fun theJamWordOnlyNamesTheLengthWhenTheNaviKnowsIt() {
        assertEquals(R.string.watch_jam,
            (Expression.pieces(Judgement(Situation.ROUTE_CONGESTED)).single() as Expression.Text).res)
        assertEquals(R.string.watch_jam_km,
            (Expression.pieces(Judgement(Situation.ROUTE_CONGESTED, facts = mapOf("congestion_km" to 3)))
                .single() as Expression.Text).res)
    }

    @Test fun theRestWordLeavesOutTheMinutesWhenItIsAWholeHour() {
        fun res(mins: Int) = (Expression.pieces(
            Judgement(Situation.LONG_DRIVE, facts = mapOf("hours" to 2, "mins" to mins))
        ).single() as Expression.Text).res
        assertEquals(R.string.car_rest_h, res(0))
        assertEquals(R.string.car_rest_hm, res(5))
    }

    // ---- 경고등 ----

    @Test fun theLampOnWordComesFromTheTable() {
        val info = CarWarnings.info("TPMS")!!
        assertEquals(Expression.Raw(info.line), Expression.pieces(Judgement(Situation.WARNING_ON, warning = info)).single())
    }

    @Test fun theLampOffWordNamesTheLamp() {
        val p = Expression.pieces(Judgement(Situation.WARNING_OFF, warning = CarWarnings.info("TPMS"))).single() as Expression.Text
        assertEquals(R.string.warn_off, p.res)
        assertEquals(listOf<Any?>("타이어 공기압"), p.args)
    }

    // ---- 인사 + 점검을 한 호흡으로 ----

    @Test fun theGreetingIsPickedFromAListAndTheChecksFollow() {
        val j = Judgement(Situation.DEPART, items = listOf(Checkup.Range(30)))
        val pieces = Expression.pieces(j)
        assertEquals(Expression.OneOf(R.array.car_depart), pieces[0])
        assertEquals(Expression.Text(R.string.check_range, listOf(30)), pieces[1])
    }

    @Test fun aLongDriveGetsTheOtherArrivalGreeting() {
        assertEquals(Expression.OneOf(R.array.car_arrive_long),
            Expression.pieces(Judgement(Situation.ARRIVE, facts = mapOf("long" to true))).first())
        assertEquals(Expression.OneOf(R.array.car_arrive),
            Expression.pieces(Judgement(Situation.ARRIVE)).first())
    }

    @Test fun manySeatsAreNotReadOneByOne() {
        val seats = listOf("ROW_1_RIGHT", "ROW_2_LEFT", "ROW_2_RIGHT")
        val p = Expression.pieces(Judgement(Situation.DEPART, items = listOf(Checkup.Belt(seats))))[1] as Expression.Text
        assertEquals(R.string.check_belt_many, p.res)
    }

    // ---- 없는 값을 0 으로 말하지 않는다 ----

    @Test fun aMissingNumberIsNeverSaidAsZero() {
        // 근거 값이 없으면 그 숫자가 없는 문장을 고르거나 아예 말하지 않는다
        assertEquals(emptyList<Expression.Piece>(), Expression.pieces(Judgement(Situation.RANGE_SHORT)))
        assertEquals(emptyList<Expression.Piece>(), Expression.pieces(Judgement(Situation.RANGE_LOW)))
        assertEquals(emptyList<Expression.Piece>(), Expression.pieces(Judgement(Situation.DRIVE_TIME_OVER)))
        assertEquals(emptyList<Expression.Piece>(), Expression.pieces(Judgement(Situation.LONG_DRIVE)))
        assertEquals(emptyList<Expression.Piece>(), Expression.pieces(Judgement(Situation.STUFFY)))
        assertEquals(emptyList<Expression.Piece>(), Expression.pieces(Judgement(Situation.FOG_RISK)))
        // 숫자가 없는 문장이 있는 것은 그쪽을 쓴다
        assertEquals(R.string.watch_jam,
            (Expression.pieces(Judgement(Situation.ROUTE_CONGESTED)).single() as Expression.Text).res)
        assertEquals(R.string.cant_control_any,
            (Expression.pieces(Judgement(Situation.CANT_CONTROL)).single() as Expression.Text).res)
    }

    @Test fun missingFactsFlashNothing() {
        assertNull(Expression.flash(Judgement(Situation.RANGE_SHORT, facts = mapOf("range_km" to 38))))
        assertNull(Expression.flash(Judgement(Situation.RANGE_LOW)))
        assertNull(Expression.flash(Judgement(Situation.DRIVE_TIME_OVER, facts = mapOf("hours" to 2))))
    }

    // ---- 화면에 크게 보일 근거 숫자 ----

    @Test fun flashShowsTheNumberWeJustSaid() {
        assertEquals("38km / 목적지 52km", Expression.flash(
            Judgement(Situation.RANGE_SHORT, facts = mapOf("range_km" to 38, "dest_km" to 52))))
        assertEquals("CO₂ 1,500ppm", Expression.flash(Judgement(Situation.STUFFY, facts = mapOf("co2" to 1500))))
        assertEquals("습도 78%", Expression.flash(Judgement(Situation.FOG_RISK, facts = mapOf("humidity" to 78))))
    }

    @Test fun nothingFlashesWhenWeDidNotQuoteANumber() {
        assertNull(Expression.flash(Judgement(Situation.WARNING_ON, warning = CarWarnings.info("TPMS"))))
        assertNull("표정만 갈 때는 숫자도 안 띄운다",
            Expression.flash(Judgement(Situation.STUFFY, Delivery.FACE_ONLY, facts = mapOf("co2" to 1500))))
        assertNull("값을 못 읽었으면 띄우지 않는다", Expression.flash(Judgement(Situation.STUFFY)))
    }

    // ---- 운전자 말에 대한 답 ----

    @Test fun theCantControlWordNamesThePartWithTheRightParticle() {
        val p = Expression.pieces(Judgement(Situation.CANT_CONTROL, facts = mapOf("what" to "창문"))).single() as Expression.Text
        assertEquals(R.string.cant_control, p.res)
        assertEquals(listOf<Any?>("창문은"), p.args)
        val ac = Expression.pieces(Judgement(Situation.CANT_CONTROL, facts = mapOf("what" to "에어컨"))).single() as Expression.Text
        assertEquals(listOf<Any?>("에어컨은"), ac.args)
        val seat = Expression.pieces(Judgement(Situation.CANT_CONTROL, facts = mapOf("what" to "시트"))).single() as Expression.Text
        assertEquals(listOf<Any?>("시트는"), seat.args)
    }
}
