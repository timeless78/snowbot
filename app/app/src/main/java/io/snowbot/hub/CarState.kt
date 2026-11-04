package io.snowbot.hub

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Calendar

/**
 * Pleos 차량 데이터. Vehicle SDK 조회 권한(CAR_DRIVE·CAR_SAFETY·CAR_LIGHTS·CAR_WINDOWS)만 쓴다:
 * 매니페스트에 적기만 하면 되고 신청이 필요 없다.
 *
 * - 기어: registerGearSelection 으로 바뀔 때마다 받는다.
 * - 연속 운전 시간: P 에서 다른 기어로 바뀐 때부터 앱이 잰다. 차의 트립 운전 시간은 단위가 문서에 없어
 *   화면에 참고로만 보인다(tripRaw).
 * - 주의 경고(DRIVER_ATTENTION 경고등): 구독(registerTelltales)은 내부용 권한이라 POLL_MS 마다 다시 물어본다.
 * - 판단기 신호(방향지시등·핸들 각도·와이퍼·전조등·크루즈): 모두 구독으로 바뀌는 즉시 받는다 → 운전 부담 단계(load).
 *   단계는 DrivingLoad.Tracker 가 "바로 올리고 5초 뒤 내린다".
 *
 * SDK 콜백은 다른 스레드에서 오므로 모두 메인 스레드로 옮겨 onChange 를 부른다. 같은 값이면 다시 부르지 않는다.
 * 신호 하나가 실패해도(차에 없는 기능 등) 나머지는 그대로 쓴다 — 실패한 신호는 꺼진 것으로 본다.
 */
class CarState(context: Context, private val onChange: (Snapshot) -> Unit) {

    data class Snapshot(
        val gear: String,        // P R N D, 아직 모르면 "?"
        val drivingMin: Int,     // 이번에 출발한 뒤 연속 운전 분 (주차 중이면 마지막 운전)
        val attention: Boolean,  // 차가 운전자 주의 경고등을 켰는지
        val tripRaw: Int?,       // 차 트립 운전 시간 값 그대로 (단위 미확인)
        val error: String?,      // 차량 서비스에서 기어 값을 못 받을 때
        val load: Int = DrivingLoad.NORMAL,                 // 운전 부담 0~3 (유지 시간 반영)
        val signals: DrivingLoad.Signals = DrivingLoad.Signals(),
        val loadChangedAt: Long = 0L,                       // elapsedRealtime, 단계가 바뀐 순간 (반응 시간 측정용)
    ) {
        val driving get() = gear != "P" && gear != "?"
    }

    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private var gear = "?"
    private var drivingSince: Long? = null   // elapsedRealtime, 출발 순간
    private var lastDrivingMin = 0
    private var attention = false
    private var tripRaw: Int? = null
    private var error: String? = null
    private var signals = DrivingLoad.Signals()
    private val tracker = DrivingLoad.Tracker()
    private var loadChangedAt = 0L
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
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = signal { it.copy(turnSignal = isOn(value)) }
        override fun onFailed(e: Exception) = signalFailed("방향지시등", e)
    }
    private val steerListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(value: Float?) = signal { it.copy(steerDeg = value) }
        override fun onFailed(e: Exception) = signalFailed("핸들 각도", e)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(value: WiperState?) = signal { it.copy(wiper = value == WiperState.ON) }
        override fun onFailed(e: Exception) = signalFailed("와이퍼", e)
    }
    private val beamListener = object : LightStateListener {
        override fun onLightStateUpdated(value: LightState?) = signal { it.copy(lowBeam = value == LightState.ON) }
        override fun onFailed(e: Exception) = signalFailed("전조등", e)
    }
    private val cruiseListener = object : CruiseControlStateListener {
        override fun onCruiseControlStateUpdated(value: CruiseControl?) = signal { it.copy(cruise = value == CruiseControl.ACTIVATED) }
        override fun onFailed(e: Exception) = signalFailed("크루즈", e)
    }
    private val poll = object : Runnable {
        override fun run() {
            askTelltales()
            publish()   // 운전 분·밤 여부가 바뀌었으면 알린다
            main.postDelayed(this, POLL_MS)
        }
    }
    private val lowerLater = Runnable { publish() }

    fun start() {
        if (started) return
        started = true
        vehicle.initialize()
        vehicle.getGear().getCurrentGearSelectionState({ g -> main.post { setGear(gearName(g)) } }, ::fail)
        vehicle.getGear().registerGearSelection(gearListener)
        vehicle.getOdometer().registerTripCurrentDrivingTime(tripListener)
        runCatching {
            vehicle.getTurnSignal().getCurrentTurnSignalState({ v -> signal { it.copy(turnSignal = isOn(v)) } }, { signalFailed("방향지시등", it) })
            vehicle.getTurnSignal().registerTurnSignalState(turnListener)
            vehicle.getSteeringWheel().getCurrentSteeringWheelAngle({ v -> signal { it.copy(steerDeg = v) } }, { signalFailed("핸들 각도", it) })
            vehicle.getSteeringWheel().registerSteeringWheelAngle(steerListener)
            vehicle.getWiper().getCurrentWiperState(windshield, { v -> signal { it.copy(wiper = v == WiperState.ON) } }, { signalFailed("와이퍼", it) })
            vehicle.getWiper().registerWiperState(windshield, wiperListener)
            vehicle.getLight().getCurrentLowBeamLightState({ v -> signal { it.copy(lowBeam = v == LightState.ON) } }, { signalFailed("전조등", it) })
            vehicle.getLight().registerLowBeamLightState(beamListener)
            vehicle.getDrivingMode().getCruiseControlState({ v -> signal { it.copy(cruise = v == CruiseControl.ACTIVATED) } }, { signalFailed("크루즈", it) })
            vehicle.getDrivingMode().registerCruiseControlState(cruiseListener)
        }.onFailure { signalFailed("판단기 신호", it as? Exception ?: Exception(it)) }
        main.post(poll)
    }

    fun stop() {
        if (!started) return
        started = false
        main.removeCallbacks(poll)
        main.removeCallbacks(lowerLater)
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getOdometer().unregisterTripCurrentDrivingTime(tripListener)
            vehicle.getTurnSignal().unregisterTurnSignalState(turnListener)
            vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steerListener)
            vehicle.getWiper().unregisterWiperState(windshield, wiperListener)
            vehicle.getLight().unregisterLowBeamLightState(beamListener)
            vehicle.getDrivingMode().unregisterCruiseControlState(cruiseListener)
        }
        vehicle.release()
    }

    /**
     * 시험용(디버그): 에뮬레이터에서 못 바꾸는 값을 대신 넣는다. null 이면 그대로.
     * turn·steer·wiper·beam·cruise 는 Virtual Console 로도 바꿀 수 있다 — 이쪽은 화면 녹화 없이 빨리 시험할 때.
     */
    fun pretend(
        gear: String? = null, addMin: Int? = null, attention: Boolean? = null,
        turn: Boolean? = null, steer: Float? = null, wiper: Boolean? = null, beam: Boolean? = null, cruise: Boolean? = null,
    ) {
        if (gear != null) setGear(gear)
        if (addMin != null) extraMin += addMin
        if (attention != null) forcedAttention = attention
        signals = signals.copy(
            turnSignal = turn ?: signals.turnSignal,
            steerDeg = steer ?: signals.steerDeg,
            wiper = wiper ?: signals.wiper,
            lowBeam = beam ?: signals.lowBeam,
            cruise = cruise ?: signals.cruise,
        )
        publish()
    }

    private fun signal(change: (DrivingLoad.Signals) -> DrivingLoad.Signals) {
        main.post { signals = change(signals); publish() }
    }

    private fun signalFailed(name: String, e: Exception) {
        Log.w(TAG, "$name 값을 못 받음: ${e.message ?: e.javaClass.simpleName}")
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
        val now = SystemClock.elapsedRealtime()
        val s = signals.copy(gear = gear, night = DrivingLoad.isNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
        signals = s
        val before = tracker.shown
        val load = tracker.update(DrivingLoad.level(s), now)
        if (load != before) loadChangedAt = now
        main.removeCallbacks(lowerLater)
        tracker.waitMs(now)?.let { main.postDelayed(lowerLater, it + 1) }
        val snap = Snapshot(gear, drivingMinutes(now), forcedAttention ?: attention, tripRaw, error, load, s, loadChangedAt)
        if (snap == last) return
        last = snap
        onChange(snap)
    }

    private fun isOn(v: TurnSignalValue?) = v == TurnSignalValue.LEFT || v == TurnSignalValue.RIGHT

    private fun gearName(g: VehicleGear?) = when (g) {
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_UNKNOWN, null -> "?"
        else -> "D"   // DRIVE, GEAR_1~9
    }

    companion object {
        private const val TAG = "CarState"
        private const val POLL_MS = 5_000L
    }
}
