package io.snowbot.hub

import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 내비게이션 값 (NaviHelper SDK 3.0.5, docs/pleos/05-vehicle-data.md 9절).
 * 목적지까지 남은 거리·시간과 앞 정체를 읽어 [Nav] 로 알린다. 주유소·충전소 화면을 여는 것도 여기서 한다.
 *
 * - 받는 법이 Vehicle SDK 와 다르다: `getXxxInfo()` 를 부르면 값이 바로 오지 않고 등록해 둔
 *   [NaviHelperEventListener] 의 `onXxxInfo` 로 온다. 내비 상태가 바뀌면 이벤트가 알아서도 온다.
 * - **내비가 없는 차에서도 앱이 죽지 않아야 한다.** `initialize` 가 실패하면 [Nav.available] 이 false 로 남고
 *   나머지 기능은 그대로 돈다. 판단 쪽은 값이 null 이면 그 조건을 아예 쓰지 않는다.
 * - 권한 `NAVI_ROUTE`(조회)·`NAVI_ROUTE_SEARCH`(경로 요청)·`NAVI_CUSTOM_MAP`(화면 열기) — 신청 불필요.
 * - 앱이 장소를 **검색하는** API 는 없다. 운전자에게 고르게 하려면 내비 검색 화면을 여는 것만 된다
 *   (`requestNavigationScreen`). 그래서 수락하면 화면만 열고 "열었어요"라고 알린다.
 */
class NaviState(context: Context, private val onChange: (Nav) -> Unit) {

    /**
     * 지금 내비가 아는 것. 못 읽은 값은 null 로 둔다 — [CarWatch] 가 null 조건은 건너뛴다.
     *
     * @param available 내비 SDK 가 붙었다 (내비 없는 차에서는 끝까지 false)
     * @param guiding 길 안내 중 (`ROUTE_DRIVE`)
     * @param destKm 목적지까지 남은 거리(km) · @param destMin 남은 시간(분)
     * @param congested 앞에 정체 구간이 있다 (SLOW·HEAVY) · @param congestionKm 그 길이(km)
     */
    data class Nav(
        val available: Boolean = false,
        val status: String = "?",
        val guiding: Boolean = false,
        val destName: String? = null,
        val destKm: Int? = null,
        val destMin: Int? = null,
        val congested: Boolean = false,
        val congestionKm: Int? = null,
    )

    private val helper = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var nav = Nav()
    private var started = false
    private var ready = false

    /** 시험용 덮어쓰기 (`--es nav 'dest_km=52 eta_min=40'`). null 이면 내비가 준 값 그대로. */
    private var forced = Nav()
    private var forcedKeys = mutableSetOf<String>()

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            Log.i(TAG, "내비 상태 $status")
            set { it.copy(status = status.name, guiding = status == NavigationStatus.ROUTE_DRIVE) }
            if (status == NavigationStatus.ROUTE_DRIVE) ask()
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            Log.i(TAG, "목적지 ${info.name} 남은 ${info.remainDistance}m ${info.remainTime}s")
            set { it.copy(destName = info.name.ifEmpty { null }, destKm = meters(info.remainDistance), destMin = seconds(info.remainTime)) }
        }

        override fun onDrivingInfo(info: DrivingInfo) {
            val d = info.destination
            Log.i(TAG, "주행 정보 ${d.name} 남은 ${d.distance}m ${d.duration}s")
            set { it.copy(destName = d.name.ifEmpty { it.destName }, destKm = meters(d.distance), destMin = seconds(d.duration)) }
        }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            val items = runCatching { info.trafficStatus?.congestionItemList }.getOrNull().orEmpty()
            val slow = items.filter { it.trafficCongestionType in SLOW_TYPES }
            val km = slow.sumOf { it.distance }.toInt().takeIf { it > 0 }?.let { it / 1000 }
            Log.i(TAG, "경로 상태 안내중=${info.isRouting} 모드=${info.naviMode} 정체구간=${slow.size}")
            set { it.copy(congested = slow.isNotEmpty(), congestionKm = km) }
        }

        override fun onRouteCancelled() = set { it.copy(guiding = false, destKm = null, destMin = null, destName = null) }
        override fun onDestinationArrived(info: ai.pleos.playground.navi.data.DestinationArrivedInfo) =
            set { it.copy(guiding = false, destKm = null, destMin = null) }

        override fun onError(code: ai.pleos.playground.navi.constants.NaviErrorCode) {
            Log.w(TAG, "내비 오류 $code")
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            helper.addListener(listener)
            helper.initialize({
                main.post {
                    ready = true
                    Log.i(TAG, "내비 SDK 붙음")
                    set { it.copy(available = true) }
                    ask()
                }
            }, { e ->
                main.post { Log.w(TAG, "내비 SDK 못 붙음: ${e.message ?: e.javaClass.simpleName} — 내비 조건은 쉰다") }
            })
        }.onFailure { Log.w(TAG, "내비 SDK 시작 실패: ${it.message ?: it.javaClass.simpleName} — 내비 조건은 쉰다") }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching {
            helper.removeListener(listener)
            helper.release()
        }
    }

    /** 지금 값을 다시 물어본다. 답은 리스너로 온다. */
    fun ask() {
        if (!ready) return
        runCatching {
            helper.getDestinationInfo()
            helper.getRouteStateInfo()
        }.onFailure { Log.w(TAG, "내비 조회 실패: ${it.message}") }
    }

    /**
     * 운전자가 고를 수 있는 내비 화면을 연다. **앱이 장소를 검색할 수는 없다** — 화면만 열어 준다.
     *   [keyword] 가 있으면 그 말로 장소 검색 화면, 없으면 주변 충전소 화면,
     *   [routeOptions] 면 경로(다른 길) 고르기 화면.
     * @return 열었으면 true. 내비가 없으면 false — 부르는 쪽이 "내비를 쓸 수 없어요"라고 말한다.
     */
    fun openPlaces(keyword: String?, routeOptions: Boolean = false): Boolean {
        if (!ready) return false
        return runCatching {
            helper.requestNavigationScreen(when {
                routeOptions -> NavigationScreenRequest.RouteOptionList
                keyword.isNullOrBlank() -> NavigationScreenRequest.NearbyChargingStations
                else -> NavigationScreenRequest.SearchPlace(keyword)
            })
            Log.i(TAG, "내비 화면 열기 요청 ${if (routeOptions) "경로 고르기" else keyword ?: "주변 충전소"}")
            true
        }.getOrElse {
            Log.w(TAG, "내비 화면 열기 실패: ${it.message}")
            false
        }
    }

    /** 시험용: 에뮬레이터에 내비가 없을 때 값을 넣는다. 예) "dest_km=52 eta_min=40 congested=1" */
    fun pretend(spec: String) {
        val kv = spec.split(' ', ',').mapNotNull { it.split('=').takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        for ((k, v) in kv) {
            forcedKeys += k
            forced = when (k) {
                "dest_km" -> forced.copy(destKm = v.toIntOrNull())
                "eta_min" -> forced.copy(destMin = v.toIntOrNull())
                "dest" -> forced.copy(destName = v)
                "congested" -> forced.copy(congested = v == "1", congestionKm = v.toIntOrNull()?.takeIf { it > 1 })
                "guiding" -> forced.copy(guiding = v == "1")
                "off" -> { forcedKeys.clear(); forced = Nav(); Nav() }
                else -> forced
            }
        }
        publish()
    }

    private fun set(change: (Nav) -> Nav) {
        main.post {
            nav = change(nav)
            publish()
        }
    }

    /** 시험용 값이 들어온 항목만 덮어쓴다. */
    private fun publish() {
        val merged = nav.copy(
            available = nav.available || forcedKeys.isNotEmpty(),
            guiding = if ("guiding" in forcedKeys) forced.guiding else nav.guiding || "dest_km" in forcedKeys,
            destName = if ("dest" in forcedKeys) forced.destName else nav.destName,
            destKm = if ("dest_km" in forcedKeys) forced.destKm else nav.destKm,
            destMin = if ("eta_min" in forcedKeys) forced.destMin else nav.destMin,
            congested = if ("congested" in forcedKeys) forced.congested else nav.congested,
            congestionKm = if ("congested" in forcedKeys) forced.congestionKm else nav.congestionKm,
        )
        onChange(merged)
    }

    companion object {
        private const val TAG = "NaviState"
        private val SLOW_TYPES = setOf(TrafficCongestionType.SLOW, TrafficCongestionType.HEAVY)

        /** m → km. 0 이하는 "모름"으로 둔다(내비가 값을 안 채웠을 때 0 이 온다). */
        fun meters(m: Int): Int? = if (m > 0) m / 1000 else null

        /** 초 → 분. 0 이하는 "모름". */
        fun seconds(s: Int): Int? = if (s > 0) s / 60 else null
    }
}
