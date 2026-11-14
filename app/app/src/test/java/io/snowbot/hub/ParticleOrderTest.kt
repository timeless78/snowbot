package io.snowbot.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 빛 방울 입자 차례 — 주행 중(앞쪽 340개만 그림)에도 구 전체에 퍼져야 한다 (11/8 반구로만 보인 문제). */
class ParticleOrderTest {

    private val max = 720

    /** 나선 자리 k 의 높이(위 +1 ~ 아래 -1). ParticleView 의 dy 와 같은 식. */
    private fun height(slot: Int) = 1 - 2 * (slot + 0.5) / max

    @Test fun everySlotIsUsedOnce() {
        val slots = (0 until max).map { ParticleView.sphereSlot(it, max) }
        assertEquals(max, slots.toSet().size)
    }

    @Test fun drivingSubsetCoversBothHalves() {
        for (count in listOf(340, 100, 30)) {
            val hs = (0 until count).map { height(ParticleView.sphereSlot(it, max)) }
            val upper = hs.count { it > 0 }
            assertTrue("$count 개 중 위쪽 $upper 개", upper in (count * 4 / 10)..(count * 6 / 10))
            assertTrue("맨 위 근처까지", hs.max() > 0.9)
            assertTrue("맨 아래 근처까지", hs.min() < -0.9)
        }
    }
}
