package io.snowbot.hub

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Calendar

/**
 * Pleos 차량 데이터. Vehicle SDK 조회 권한(CAR_DRIVE·CAR_SAFETY·CAR_LIGHTS·CAR_WINDOWS·CAR_CLIMATE)만 쓴다:
 * 매니페스트에 적기만 하면 되고 신청이 필요 없다.
 *
 * - 기어: registerGearSelection 으로 바뀔 때마다 받는다.
 * - 연속 운전 시간: P 에서 다른 기어로 바뀐 때부터 앱이 잰다. 차의 트립 운전 시간은 단위가 문서에 없어
 *   화면에 참고로만 보인다(tripRaw).
 * - 경고등(telltale): 켜진 것 전부를 이름으로 모은다 → 통역([CarWarnings])과 주의 경고(DRIVER_ATTENTION).
 *   조회·구독 모두 `PRIVATE_CAR_SAFETY` 를 요구한다(2.3.2 aar 의 `@RequiresCustomPermission` 실측 —
 *   문서는 조회가 CAR_SAFETY 라고 적고 있지만 다르다). 구독으로 바로 받고, POLL_MS 마다 조회로 한 번 더 맞춘다.
 *   권한이 없는 차에서는 둘 다 실패하고(로그 한 번) 경고등 없이 나머지 기능만 돈다.
 * - 판단에 쓰는 신호(와이퍼·전조등): 구독으로 바뀌는 즉시 받는다. 와이퍼·밤은 김서림과 "비 오는데 창문 열림",
 *   전조등은 "밤인데 전조등 꺼짐" 대조에 쓴다([CarWatch]).
 * - 내기 순환(HVAC): 봇에 주는 차 정보(car.cabin)와 해석기(답답함)에 쓴다.
 * - 주행 가능 거리는 **1km 미만을 모름으로 본다**([Checkup.usableRange]) — 차가 막 깨어났을 때 0 이 온다.
 * - 출발·하차 점검([Checkup]) 재료: 좌석 탑승·안전벨트(CAR_SEATS) · 창문(CAR_WINDOWS) · 문(CAR_DOORS) ·
 *   주행 가능 거리(CAR_DRIVE) · 연료·EV 잔량(CAR_ENERGY). 차가 알려 주는 구역만 구독하고(capability),
 *   못 읽는 값은 비워 둔다 — [Checkup] 이 없는 값으로는 말을 만들지 않는다.
 *
 * SDK 콜백은 다른 스레드에서 오므로 모두 메인 스레드로 옮겨 onChange 를 부른다. 같은 값이면 다시 부르지 않는다.
 * 신호 하나가 실패해도(차에 없는 기능 등) 나머지는 그대로 쓴다 — 실패한 신호는 꺼진 것으로 본다.
 */
class CarState(context: Context, private val onChange: (Snapshot) -> Unit) {

    /**
     * 판단에 쓰는 차 신호. 못 읽는 값은 null·false 로 둔다.
     *
     * @param wiper 와이퍼 작동 (비로 본다) · @param lowBeam 전조등 켜짐 · @param night 19시~6시
     * @param recirc 내기 순환 · @param ac 에어컨 · @param defrost 앞유리 성에 제거
     *   (뒤 셋은 김서림·답답함 권유를 따랐는지 보는 신호 — [Ladder.Car])
     */
    data class Signals(
        val gear: String = "?",           // P R N D ?
        val wiper: Boolean = false,
        val lowBeam: Boolean = false,
        val night: Boolean = false,
        val recirc: Boolean? = null,
        val ac: Boolean? = null,
        val defrost: Boolean? = null,
    )

    data class Snapshot(
        val gear: String,        // P R N D, 아직 모르면 "?"
        val drivingMin: Int,     // 이번에 출발한 뒤 연속 운전 분 (주차 중이면 마지막 운전)
        val attention: Boolean,  // 차가 운전자 주의 경고등을 켰는지
        val tripRaw: Int?,       // 차 트립 운전 시간 값 그대로 (단위 미확인)
        val error: String?,      // 차량 서비스에서 기어 값을 못 받을 때
        val signals: Signals = Signals(),
        val warnings: Set<String> = emptySet(),             // 켜진 경고등 (Pleos TelltaleType 이름)
        val warningsRead: Boolean = false,                  // 경고등 목록을 한 번이라도 읽었는지 (못 읽는 차에서는 계속 false)
        val cabin: Checkup.Cabin = Checkup.Cabin(),         // 출발·하차 점검에 쓰는 실내 상태
    ) {
        val driving get() = gear != "P" && gear != "?"
    }

    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val windshield = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private var recircArea: SeatArea? = null
    private var acArea: SeatArea? = null
    private var defrostArea: WindowArea? = null
    private var gear = "?"
    private var drivingSince: Long? = null   // elapsedRealtime, 출발 순간
    private var lastDrivingMin = 0
    private var attention = false
    private var tripRaw: Int? = null
    private var error: String? = null
    private var signals = Signals()
    private var extraMin = 0                 // 시험용: 운전 시간을 앞당긴다
    private var forcedAttention: Boolean? = null
    private var last: Snapshot? = null
    private var started = false

    // 경고등·실내 상태 (구역 이름 → 값). 구역 이름은 Pleos SeatValue·WindowValue·DoorValue 이름을 "+"로 이은 것
    private var telltales: Set<String> = emptySet()
    private var telltalesRead = false            // 경고등 목록을 한 번이라도 받았는지
    private var telltaleFailed: String? = null   // 같은 실패를 한 번만 적는다
    private val occupied = linkedMapOf<String, Boolean>()
    private val beltOff = linkedMapOf<String, Boolean>()
    private val windowOpen = linkedMapOf<String, Boolean>()
    private val doorOpen = linkedMapOf<String, Boolean>()
    private var rangeKm: Int? = null
    private var fuelPct: Int? = null
    private var socPct: Int? = null
    private var rearUsed = false             // 이번 운행에서 뒷좌석을 썼다 (탑승 또는 뒷문 열림)

    // 시험용 덮어쓰기 (null 이면 차가 준 값 그대로)
    private var forcedWarnings = emptySet<String>()
    private var forcedBelt: Boolean? = null
    private var forcedRange: Int? = null
    private var forcedFuel: Int? = null
    private var forcedSoc: Int? = null
    private var forcedWindow: Boolean? = null
    private var forcedDoor: Boolean? = null
    private var forcedRear: Boolean? = null

    // 구역마다 만든 구독(해제용)
    private val occupancyWatch = mutableMapOf<SeatArea, SeatOccupancyListener>()
    private val beltWatch = mutableMapOf<SeatArea, SeatBeltBuckledStateListener>()
    private val windowWatch = mutableMapOf<WindowArea, WindowPositionListener>()
    private val doorWatch = mutableMapOf<DoorArea, DoorPositionListener>()

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
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(value: WiperState?) = signal { it.copy(wiper = value == WiperState.ON) }
        override fun onFailed(e: Exception) = signalFailed("와이퍼", e)
    }
    private val beamListener = object : LightStateListener {
        override fun onLightStateUpdated(value: LightState?) = signal { it.copy(lowBeam = value == LightState.ON) }
        override fun onFailed(e: Exception) = signalFailed("전조등", e)
    }
    // 메서드 이름에 "Auto" 가 붙어 있지만 **수동 내·외기**가 온다. SDK 가 수동·자동 두 구독에 리스너 인터페이스를
    // 하나로 같이 쓰면서 메서드 이름을 자동 쪽으로 붙였다. 어느 값이 오는지는 register 쪽이 정한다 —
    // registerHvacRecirculationState → HVAC_RECIRC_ON(수동), registerHvacAutoRecirculationState → HVAC_AUTO_RECIRC_ON.
    // 2.3.2 aar 바이트코드 실측, docs/pleos/05-vehicle-data.md 5절.
    private val acListener = object : HvacAcStateListener {
        override fun onHvacAcStateUpdated(value: Boolean?) = signal { it.copy(ac = value) }
        override fun onFailed(e: Exception) = signalFailed("에어컨", e)
    }
    private val defrostListener = object : HvacDefrosterStateListener {
        override fun onHvacDefrosterStateUpdated(value: Boolean?) = signal { it.copy(defrost = value) }
        override fun onFailed(e: Exception) = signalFailed("성에 제거", e)
    }
    private val recircListener = object : HvacRecirculationStateListener {
        override fun onHvacAutoRecirculationStateUpdated(value: Boolean?) = signal { it.copy(recirc = value) }
        override fun onFailed(e: Exception) = signalFailed("내기 순환", e)
    }
    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) {
            if (telltales != null) setTelltales(telltales)
        }
        override fun onFailed(e: Exception) = telltaleFail("경고등 구독", e)
    }
    private val rangeListener = object : RangeRemainingListener {
        override fun onRangeRemainingChanged(value: Float?) {
            main.post { rangeKm = value?.let { (it / 1000f).toInt() }; publish() }
        }
        override fun onFailed(e: Exception) = signalFailed("주행 가능 거리", e)
    }
    private val fuelListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(value: Int?) {
            main.post { fuelPct = value; publish() }
        }
        override fun onFailed(e: Exception) = signalFailed("연료 잔량", e)
    }
    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            main.post { socPct = value; publish() }
        }
        override fun onFailed(e: Exception) = signalFailed("EV 배터리 잔량", e)
    }
    private val poll = object : Runnable {
        override fun run() {
            askTelltales()
            publish()   // 운전 분·밤 여부가 바뀌었으면 알린다
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
        runCatching {
            vehicle.getWiper().getCurrentWiperState(windshield, { v -> signal { it.copy(wiper = v == WiperState.ON) } }, { signalFailed("와이퍼", it) })
            vehicle.getWiper().registerWiperState(windshield, wiperListener)
            vehicle.getLight().getCurrentLowBeamLightState({ v -> signal { it.copy(lowBeam = v == LightState.ON) } }, { signalFailed("전조등", it) })
            vehicle.getLight().registerLowBeamLightState(beamListener)
            // 내기 순환은 차가 알려 주는 구역으로 등록해야 값이 온다(에뮬레이터는 운전석 하나가 아니라 앞줄 묶음 구역)
            vehicle.getHVAC().checkHvacRecirculationCapabilities({ areas: List<SeatArea> ->
                main.post {
                    val area = areas.firstOrNull() ?: driverSeat
                    recircArea = area
                    vehicle.getHVAC().getHvacRecirculationState(area, { v -> signal { it.copy(recirc = v) } }, { signalFailed("내기 순환", it) })
                    vehicle.getHVAC().registerHvacRecirculationState(area, recircListener)
                }
            }, { signalFailed("내기 순환 구역", it) })
            // 에어컨·앞유리 성에 제거: 김서림 권유("에어컨 켜 볼까요?")를 따랐는지 보는 신호. 내기와 같이 차가 알려 주는 구역으로
            vehicle.getHVAC().checkHvacAcCapabilities({ areas: List<SeatArea> ->
                main.post {
                    val area = areas.firstOrNull() ?: driverSeat
                    acArea = area
                    vehicle.getHVAC().getHvacAcState(area, { v -> signal { it.copy(ac = v) } }, { signalFailed("에어컨", it) })
                    vehicle.getHVAC().registerHvacAcState(area, acListener)
                }
            }, { signalFailed("에어컨 구역", it) })
            vehicle.getHVAC().checkHvacDefrosterCapabilities({ areas: List<WindowArea> ->
                main.post {
                    val area = areas.firstOrNull { it == windshield } ?: areas.firstOrNull() ?: windshield
                    defrostArea = area
                    vehicle.getHVAC().getHvacDefrosterState(area, { v -> signal { it.copy(defrost = v) } }, { signalFailed("성에 제거", it) })
                    vehicle.getHVAC().registerHvacDefrosterState(area, defrostListener)
                }
            }, { signalFailed("성에 제거 구역", it) })
        }.onFailure { signalFailed("판단기 신호", it as? Exception ?: Exception(it)) }
        runCatching { watchCabin() }.onFailure { signalFailed("실내 상태", it as? Exception ?: Exception(it)) }
        runCatching { vehicle.getSafety().registerTelltales(telltaleListener) }
            .onFailure { telltaleFail("경고등 구독 등록", it as? Exception ?: Exception(it)) }
        main.post(poll)
    }

    /**
     * 출발·하차 점검 재료를 구독한다. 차가 알려 주는 구역만 쓴다(`check…Capabilities`) — 구역을 임의로 만들면
     * 값이 오지 않는다(내기 순환에서 겪은 것과 같다). 한 도메인이 실패해도 나머지는 그대로 쓴다.
     */
    private fun watchCabin() {
        val seat = vehicle.getSeat()
        seat.checkSeatOccupancyCapabilities({ areas: List<SeatArea> ->
            main.post { areas.forEach(::watchOccupancy) }
        }, { signalFailed("좌석 탑승 구역", it) })
        seat.checkSeatBeltBuckledCapabilities({ areas: List<SeatArea> ->
            main.post { areas.forEach(::watchBelt) }
        }, { signalFailed("안전벨트 구역", it) })
        // 창문·문은 구역마다 "닫힘~열림" 범위도 함께 알려 준다 (열림 = 최솟값보다 큰 값)
        vehicle.getWindow().checkWindowPositionCapabilities({ bounds: Map<WindowArea, AreaBounds<Int>> ->
            main.post { bounds.forEach { (a, b) -> watchWindow(a, b.minValue ?: 0) } }
        }, { signalFailed("창문 구역", it) })
        vehicle.getDoor().checkDoorPositionCapabilities({ bounds: Map<DoorArea, AreaBounds<Int>> ->
            main.post { bounds.forEach { (a, b) -> watchDoor(a, b.minValue ?: 0) } }
        }, { signalFailed("문 구역", it) })
        vehicle.getOdometer().getRangeRemainingDistance({ v -> main.post { rangeKm = v?.let { (it / 1000f).toInt() }; publish() } },
            { signalFailed("주행 가능 거리", it) })
        vehicle.getOdometer().registerRangeRemainingListener(rangeListener)
        vehicle.getFuel().getFuelPercent({ v -> main.post { fuelPct = v; publish() } }, { signalFailed("연료 잔량", it) })
        vehicle.getFuel().registerFuelPercent(fuelListener)
        vehicle.getEvBattery().getEvBatteryStateOfCharge({ v -> main.post { socPct = v; publish() } }, { signalFailed("EV 배터리 잔량", it) })
        vehicle.getEvBattery().registerEvBatteryStateOfCharge(socListener)
    }

    private fun watchOccupancy(area: SeatArea) {
        val key = areaKey(area.values.map { it.name })
        fun set(v: Boolean?) = main.post {
            occupied[key] = v == true
            if (v == true && Checkup.isRear(key)) rearUsed = true
            publish()
        }
        val l = object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(value: Boolean?) { set(value) }
            override fun onFailed(e: Exception) = signalFailed("좌석 탑승($key)", e)
        }
        occupancyWatch[area] = l
        vehicle.getSeat().getCurrentSeatOccupancy(area, { set(it) }, { signalFailed("좌석 탑승($key)", it) })
        vehicle.getSeat().registerSeatOccupancy(area, l)
    }

    private fun watchBelt(area: SeatArea) {
        val key = areaKey(area.values.map { it.name })
        fun set(buckled: Boolean?) = main.post { beltOff[key] = buckled == false; publish() }
        val l = object : SeatBeltBuckledStateListener {
            override fun onSeatBeltBuckledStateUpdated(value: Boolean?) { set(value) }
            override fun onFailed(e: Exception) = signalFailed("안전벨트($key)", e)
        }
        beltWatch[area] = l
        vehicle.getSeat().getCurrentSeatBeltBuckledState(area, { set(it) }, { signalFailed("안전벨트($key)", it) })
        vehicle.getSeat().registerSeatBeltBuckledState(area, l)
    }

    private fun watchWindow(area: WindowArea, closed: Int) {
        val key = areaKey(area.values.map { it.name })
        if (key.startsWith("FRONT_WINDSH") || key.startsWith("REAR_WINDSH")) return   // 앞·뒷유리는 열리지 않는다
        fun set(pos: Int?) = main.post { windowOpen[key] = (pos ?: closed) > closed; publish() }
        val l = object : WindowPositionListener {
            override fun onWindowPositionUpdated(value: Int?) { set(value) }
            override fun onFailed(e: Exception) = signalFailed("창문($key)", e)
        }
        windowWatch[area] = l
        vehicle.getWindow().getCurrentWindowPosition(area, { set(it) }, { signalFailed("창문($key)", it) })
        vehicle.getWindow().registerWindowPosition(area, l)
    }

    private fun watchDoor(area: DoorArea, closed: Int) {
        val key = areaKey(area.values.map { it.name })
        fun set(pos: Int?) = main.post {
            val open = (pos ?: closed) > closed
            doorOpen[key] = open
            if (open && Checkup.isRear(key)) rearUsed = true   // 뒷문·트렁크를 열었다 = 뒤에 뭔가 실었을 수 있다
            publish()
        }
        val l = object : DoorPositionListener {
            override fun onPositionUpdated(value: Int?) { set(value) }
            override fun onFailed(e: Exception) = signalFailed("문($key)", e)
        }
        doorWatch[area] = l
        vehicle.getDoor().getCurrentDoorPosition(area, { set(it) }, { signalFailed("문($key)", it) })
        vehicle.getDoor().registerDoorPosition(area, l)
    }

    private fun areaKey(names: List<String>) = names.joinToString("+")

    fun stop() {
        if (!started) return
        started = false
        main.removeCallbacks(poll)
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getOdometer().unregisterTripCurrentDrivingTime(tripListener)
            vehicle.getWiper().unregisterWiperState(windshield, wiperListener)
            vehicle.getLight().unregisterLowBeamLightState(beamListener)
            recircArea?.let { vehicle.getHVAC().unregisterHvacRecirculationState(it, recircListener) }
            acArea?.let { vehicle.getHVAC().unregisterHvacAcState(it, acListener) }
            defrostArea?.let { vehicle.getHVAC().unregisterHvacDefrosterState(it, defrostListener) }
        }
        runCatching {
            vehicle.getSafety().unregisterTelltales(telltaleListener)
            occupancyWatch.forEach { (a, l) -> vehicle.getSeat().unregisterSeatOccupancy(a, l) }
            beltWatch.forEach { (a, l) -> vehicle.getSeat().unregisterSeatBeltBuckledState(a, l) }
            windowWatch.forEach { (a, l) -> vehicle.getWindow().unregisterWindowPosition(a, l) }
            doorWatch.forEach { (a, l) -> vehicle.getDoor().unregisterDoorPosition(a, l) }
            vehicle.getOdometer().unregisterRangeRemainingListener(rangeListener)
            vehicle.getFuel().unregisterFuelPercent(fuelListener)
            vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
        }
        occupancyWatch.clear(); beltWatch.clear(); windowWatch.clear(); doorWatch.clear()
        vehicle.release()
    }

    /**
     * 시험용(디버그): 에뮬레이터에서 못 바꾸는 값을 대신 넣는다. null 이면 그대로.
     * wiper·beam 은 Virtual Console 로도 바꿀 수 있다 — 이쪽은 화면 녹화 없이 빨리 시험할 때.
     */
    fun pretend(
        gear: String? = null, addMin: Int? = null, attention: Boolean? = null,
        wiper: Boolean? = null, beam: Boolean? = null,
        recirc: Boolean? = null, ac: Boolean? = null, defrost: Boolean? = null,
        warn: Set<String>? = null, belt: Boolean? = null, range: Int? = null, fuel: Int? = null, soc: Int? = null,
        window: Boolean? = null, door: Boolean? = null, rear: Boolean? = null,
    ) {
        // 기어보다 **먼저** 덮어쓴다 — 같은 명령 안의 `gear=D` 가 출발 점검을 바로 부르기 때문에
        // (`gear=D belt=0` 이면 벨트를 맨 상태로 출발해야 한다)
        if (attention != null) forcedAttention = attention
        if (warn != null) forcedWarnings = warn
        if (belt != null) forcedBelt = belt
        if (range != null) forcedRange = range
        if (fuel != null) forcedFuel = fuel
        if (soc != null) forcedSoc = soc
        if (window != null) forcedWindow = window
        if (door != null) forcedDoor = door
        if (rear != null) forcedRear = rear
        if (gear != null) setGear(gear)
        if (addMin != null) extraMin += addMin   // 출발이 extraMin 을 0 으로 되돌리므로 기어 뒤에
        signals = signals.copy(
            wiper = wiper ?: signals.wiper,
            lowBeam = beam ?: signals.lowBeam,
            recirc = recirc ?: signals.recirc,
            ac = ac ?: signals.ac,
            defrost = defrost ?: signals.defrost,
        )
        publish()
    }

    private fun signal(change: (Signals) -> Signals) {
        main.post { signals = change(signals); publish() }
    }

    private fun signalFailed(name: String, e: Exception) {
        Log.w(TAG, "$name 값을 못 받음: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun askTelltales() {
        runCatching {
            vehicle.getSafety().getTelltalesInfo(::setTelltales) { telltaleFail("경고등 조회", it) }
        }.onFailure { telltaleFail("경고등 조회", it as? Exception ?: Exception(it)) }
    }

    /** 켜진(ON·BLINK) 경고등 이름 전부 + 그중 운전자 주의 경고. */
    private fun setTelltales(info: Telltales) {
        val lit = info.telltales.filter { it.state != TelltaleState.OFF }.map { it.type.name }.toSet()
        main.post {
            telltales = lit
            telltalesRead = true
            attention = TelltaleType.DRIVER_ATTENTION.name in lit
            telltaleFailed = null
            publish()
        }
    }

    /**
     * 경고등을 못 읽는 차. 기어 값과 달리 화면의 "차량 정보를 못 받음"으로 올리지 않는다 —
     * 경고등 하나가 막혀도 나머지 기능은 그대로 돌기 때문이다. 같은 이유는 한 번만 적는다.
     */
    private fun telltaleFail(what: String, e: Exception) {
        val why = e.message ?: e.javaClass.simpleName
        if (telltaleFailed == why) return
        telltaleFailed = why
        Log.w(TAG, "$what 실패: $why — 경고등 통역은 쉰다 (PRIVATE_CAR_SAFETY 권한 확인)")
    }

    private fun setGear(value: String) {
        if (value == gear) return
        val now = SystemClock.elapsedRealtime()
        if (value != "P" && value != "?" && drivingSince == null) {
            drivingSince = now   // 출발
            extraMin = 0
            // 이번 운행의 "뒷좌석을 썼다"를 지금 상태로 다시 잡는다. 출발 뒤에 뒷문이 열리거나
            // 뒷좌석에 사람이 앉으면 그때 다시 켜진다.
            rearUsed = occupied.any { it.value && Checkup.isRear(it.key) } || doorOpen.any { it.value && Checkup.isRear(it.key) }
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
        val s = signals.copy(gear = gear, night = isNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
        signals = s
        val snap = Snapshot(gear, drivingMinutes(now), forcedAttention ?: attention, tripRaw, error, s,
            telltales + forcedWarnings, telltalesRead || forcedWarnings.isNotEmpty(), cabin())
        if (snap == last) return
        last = snap
        onChange(snap)
    }

    /**
     * 출발·하차 점검에 넘길 실내 상태. 못 읽는 값은 비워 둔다.
     * 벨트는 **사람이 앉은 자리**만 본다 — 빈 자리는 벨트를 안 매는 게 당연하다.
     * 탑승 여부를 못 읽는 차에서는 운전석만 사람이 있다고 본다(출발하는 중이니 운전자는 앉아 있다).
     */
    private fun cabin(): Checkup.Cabin {
        val belts = beltOff.filterValues { it }.keys
            .filter { occupied[it] ?: it.startsWith(DRIVER_SEAT) }
        fun open(map: Map<String, Boolean>, forced: Boolean?, demo: String) = when (forced) {
            true -> listOf(demo)
            false -> emptyList()
            null -> map.filterValues { it }.keys.toList()
        }
        return Checkup.Cabin(
            beltsOff = when (forcedBelt) {
                true -> listOf(DRIVER_SEAT)
                false -> emptyList()
                null -> belts
            },
            // 1km 미만은 모름으로 본다 — 값이 아직 안 올라온 때와 구별할 수 없다(Checkup.MIN_RANGE_KM)
            rangeKm = Checkup.usableRange(forcedRange ?: rangeKm),
            fuelPct = forcedFuel ?: fuelPct,
            socPct = forcedSoc ?: socPct,
            windowsOpen = open(windowOpen, forcedWindow, DRIVER_SEAT),
            doorsOpen = open(doorOpen, forcedDoor, "ROW_2_LEFT"),
            rearUsed = forcedRear ?: rearUsed,
        )
    }

    private fun gearName(g: VehicleGear?) = when (g) {
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_UNKNOWN, null -> "?"
        else -> "D"   // DRIVE, GEAR_1~9
    }

    companion object {
        private const val TAG = "CarState"

        /** 밤으로 보는 시간 (19시~6시). 시작 값이다. 김서림·전조등 대조에 쓴다. */
        fun isNight(hour: Int) = hour >= 19 || hour < 6

        private const val POLL_MS = 5_000L
        private const val DRIVER_SEAT = "ROW_1_LEFT"
    }
}
