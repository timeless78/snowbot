package io.snowbot.hub

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Pleos 차량 데이터 세 가지 — 기어(주행/주차), 연속 운전 시간, 운전자 주의 경고.
 * Vehicle SDK 조회 권한(CAR_DRIVE·CAR_SAFETY)만 쓴다: 매니페스트에 적기만 하면 되고 신청이 필요 없다.
 *
 * - 기어: registerGearSelection 으로 바뀔 때마다 받는다.
 * - 연속 운전 시간: P 에서 다른 기어로 바뀐 때부터 앱이 잰다. 차의 트립 운전 시간은 단위가 문서에 없어
 *   화면에 참고로만 보인다(tripRaw).
 * - 주의 경고(DRIVER_ATTENTION 경고등): 구독(registerTelltales)은 내부용 권한이라 POLL_MS 마다 다시 물어본다.
 *
 * SDK 콜백은 다른 스레드에서 오므로 모두 메인 스레드로 옮겨 onChange 를 부른다. 같은 값이면 다시 부르지 않는다.
 */
class CarState(context: Context, private val onChange: (Snapshot) -> Unit) {

    data class Snapshot(
        val gear: String,        // P R N D, 아직 모르면 "?"
        val drivingMin: Int,     // 이번에 출발한 뒤 연속 운전 분 (주차 중이면 마지막 운전)
        val attention: Boolean,  // 차가 운전자 주의 경고등을 켰는지
        val tripRaw: Int?,       // 차 트립 운전 시간 값 그대로 (단위 미확인)
        val error: String?,      // 차량 서비스에서 값을 못 받을 때
    ) {
        val driving get() = gear != "P" && gear != "?"
    }

    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private var gear = "?"
    private var drivingSince: Long? = null   // elapsedRealtime, 출발 순간
    private var lastDrivingMin = 0
    private var attention = false
    private var tripRaw: Int? = null
    private var error: String? = null
    private var extraMin = 0                 // 시험용: 운전 시간을 앞당긴다
    private var forcedAttention: Boolean? = null
    private var last: Snapshot? = null
    private var started = false

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(value: VehicleGear?) {
            main.post { setGear(gearName(value)) }
        }
        override fun onFailed(e: Exception) = fail(e)
    }
    private val tripListener = object : DrivingTimeListener {
        override fun onDrivingTimeUpdated(value: Int?) {
            main.post { tripRaw = value; publish() }
        }
        override fun onFailed(e: Exception) = fail(e)
    }
    private val poll = object : Runnable {
        override fun run() {
            askTelltales()
            publish()   // 운전 분이 바뀌었으면 알린다
            main.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        if (started) return
        started = true
        vehicle.initialize()
        vehicle.getGear().getCurrentGearSelectionState({ g -> main.post { setGear(gearName(g)) } }, ::fail)
        vehicle.getGear().registerGearSelection(gearListener)
        vehicle.getOdometer().registerTripCurrentDrivingTime(tripListener)
        main.post(poll)
    }

    fun stop() {
        if (!started) return
        started = false
        main.removeCallbacks(poll)
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getOdometer().unregisterTripCurrentDrivingTime(tripListener)
        }
        vehicle.release()
    }

    /** 시험용(디버그): 에뮬레이터에서 못 바꾸는 값을 대신 넣는다. null 이면 그대로. */
    fun pretend(gear: String?, addMin: Int?, attention: Boolean?) {
        if (gear != null) setGear(gear)
        if (addMin != null) extraMin += addMin
        if (attention != null) forcedAttention = attention
        publish()
    }

    private fun askTelltales() {
        vehicle.getSafety().getTelltalesInfo({ info ->
            val on = info.telltales.any { it.type == TelltaleType.DRIVER_ATTENTION && it.state != TelltaleState.OFF }
            main.post { attention = on; error = null; publish() }
        }, ::fail)
    }

    private fun setGear(value: String) {
        if (value == gear) return
        val now = SystemClock.elapsedRealtime()
        if (value != "P" && value != "?" && drivingSince == null) {
            drivingSince = now   // 출발
            extraMin = 0
        } else if (value == "P") {
            lastDrivingMin = drivingMinutes(now)
            drivingSince = null  // 도착: 다음 출발 때 다시 잰다
            forcedAttention = null   // 시험으로 켠 경고도 주차하면 푼다
        }
        gear = value
        error = null
        publish()
    }

    private fun drivingMinutes(now: Long): Int =
        drivingSince?.let { ((now - it) / 60_000).toInt() + extraMin } ?: lastDrivingMin

    private fun fail(e: Exception) {
        main.post {
            error = e.message ?: e.javaClass.simpleName
            publish()
        }
    }

    private fun publish() {
        val snap = Snapshot(gear, drivingMinutes(SystemClock.elapsedRealtime()), forcedAttention ?: attention, tripRaw, error)
        if (snap == last) return
        last = snap
        onChange(snap)
    }

    private fun gearName(g: VehicleGear?) = when (g) {
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_UNKNOWN, null -> "?"
        else -> "D"   // DRIVE, GEAR_1~9
    }

    companion object {
        private const val POLL_MS = 5_000L
    }
}
