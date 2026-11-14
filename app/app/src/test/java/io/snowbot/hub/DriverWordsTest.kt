package io.snowbot.hub

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 운전자 말 읽기. 차의 듣기가 적은 글자는 띄어쓰기·기호가 섞이고 낱말이 틀리기도 하므로,
 * **짧은 문장에서만** 맞춰 보고 긴 잡담은 AI 로 넘기는지 고정한다.
 */
class DriverWordsTest {

    private fun said(text: String, waiting: Boolean = false, tip: Boolean = true) =
        DriverWords.parse(text, waiting, tip)

    // ---- 상태 질문: 앱이 바로 답한다 ----

    @Test fun statusQuestions() {
        for (t in listOf("지금 차 어때?", "차 상태 알려줘", "보고해", "차 괜찮아?", "차 상태 어때")) {
            assertEquals(t, DriverWords.StatusAsk, said(t))
        }
    }

    @Test fun intactQuestion() =
        assertEquals(DriverWords.IntactAsk, said("멀쩡한 게 뭐야?"))

    @Test fun rangeQuestions() {
        for (t in listOf("얼마나 더 갈 수 있어?", "기름 얼마나 남았어", "배터리 얼마나 남았어", "주행 가능 거리")) {
            assertEquals(t, DriverWords.RangeAsk, said(t))
        }
    }

    // ---- 결정 ----

    @Test fun acceptOnlyCountsWhileAnOfferIsWaiting() {
        assertEquals("기다리는 제안이 없으면 맞장구다", DriverWords.ToAi, said("응"))
        assertEquals(DriverWords.Accept, said("응", waiting = true))
        assertEquals(DriverWords.Accept, said("그래 해줘", waiting = true))
    }

    @Test fun declineWorksWhileATipIsStanding() {
        for (t in listOf("그냥 둬", "됐어", "괜찮아", "하지 마", "취소")) {
            assertEquals(t, DriverWords.Decline, said(t))
        }
    }

    /** 끌 권유도 기다리는 제안도 없으면 끄라는 뜻이 아니다 — AI 가 대답해야 한다. */
    @Test fun declineWithNothingToTurnOffGoesToTheAi() {
        for (t in listOf("괜찮아", "됐어", "그만", "아니")) {
            assertEquals(t, DriverWords.ToAi, said(t, tip = false))
        }
        assertEquals("기다리는 제안이 있으면 끄는 말이다", DriverWords.Decline, said("괜찮아", waiting = true, tip = false))
    }

    // ---- 수락·거절은 문장 전체이거나 그 말로 시작할 때만 ----

    @Test fun yesAndNoOnlyMatchAtTheStartOfAShortSentence() {
        assertEquals("'응'이 '반응'에 걸리면 안 된다", DriverWords.ToAi, said("반응이 왜 그래", waiting = true))
        assertEquals("'좋아'가 '콜라 좋아'에 걸리면 안 된다", DriverWords.ToAi, said("콜라 좋아", waiting = true))
        assertEquals("'콜'이 '콜라'에 걸리면 안 된다", DriverWords.ToAi, said("콜라 줘", waiting = true))
        assertEquals("'그만'이 '그만 먹을래'에 걸리면 안 된다", DriverWords.ToAi, said("그만 먹을래"))
        assertEquals("'괜찮아'가 '나 괜찮아'에 걸리면 안 된다", DriverWords.ToAi, said("나 괜찮아"))
    }

    @Test fun shortTailsStillCount() {
        assertEquals(DriverWords.Accept, said("응", waiting = true))
        assertEquals(DriverWords.Accept, said("그래 해줘", waiting = true))
        assertEquals(DriverWords.Accept, said("좋아요", waiting = true))
        assertEquals(DriverWords.Decline, said("됐어요"))
        assertEquals(DriverWords.Decline, said("그냥 둬"))
    }

    // ---- 차 조작: 아직 못 한다 ----

    @Test fun carControlRequests() {
        assertEquals(DriverWords.Control("창문"), said("창문 열어줘"))
        assertEquals(DriverWords.Control("에어컨"), said("에어컨 켜줘"))
        assertEquals(DriverWords.Control("공조"), said("외기로 바꿔줘"))
    }

    @Test fun talkingAboutTheAirIsNotARequest() =
        assertEquals("조작하라는 말이 없으면 그냥 이야기다", DriverWords.ToAi, said("에어컨 시원하네"))

    // ---- 경로 ----

    @Test fun routeRequests() {
        assertEquals(DriverWords.Route(Offer.REST), said("쉴 곳 찾아줘"))
        assertEquals(DriverWords.Route(Offer.FUEL), said("주유소 알려줘"))
        assertEquals(DriverWords.Route(Offer.CHARGE), said("충전소 찾아줘"))
        assertEquals(DriverWords.Route(Offer.REROUTE), said("다른 길 있어?"))
    }

    // ---- 오인식 방지 ----

    @Test fun longChatterGoesToTheAi() {
        for (t in listOf(
            "아까 창문 열어줘서 좀 추웠는데 지금은 괜찮아",
            "어제 주유소에서 만난 사람이 재미있는 얘기를 해 줬어",
            "오늘 기분이 좀 멀쩡하지 않은 그런 날이야 무슨 말인지 알지",
            "그냥 둬도 되는 일인지 아닌지 네 생각은 어때 한번 말해 봐",
        )) assertEquals(t, DriverWords.ToAi, said(t, waiting = true))
    }

    @Test fun emptyOrJustPunctuationGoesToTheAi() {
        assertEquals(DriverWords.ToAi, said(""))
        assertEquals(DriverWords.ToAi, said("  ?? "))
    }

    @Test fun punctuationAndSpacingDoNotMatter() {
        assertEquals(DriverWords.StatusAsk, said("지금, 차 어때…?"))
        assertEquals(DriverWords.StatusAsk, said("지금차어때"))
    }
}
