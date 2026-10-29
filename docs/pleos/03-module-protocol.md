# 모듈 ↔ 앱 연동 규격 초안 (v0.1)

목표: 모양과 상관없이 같은 규격이면 연결. SnowBot은 레퍼런스 구현.
전송: **WebSocket, 서버 경유 (2026-10-26 변경 — [../07-communication-ai.md](../07-communication-ai.md)).** BLE GATT 는 나중 선택지(에뮬레이터 블루투스 미지원, 허용 여부 문의 중). 메시지는 JSON(개발 단계) → 이후 CBOR 등 경량화 검토.

## 1. 연결 흐름
1. 모듈 광고(advertise) — 서비스 UUID `TODO`
2. 앱이 연결 → 모듈이 `hello`로 기능 선언
3. 앱이 기능에 맞는 스킬 활성화 → `subscribe`로 필요한 센서 이벤트 요청
4. 모듈 → 앱: 센서 이벤트 / 앱 → 모듈: 표현·제어 명령

## 2. 기능 선언 (모듈 → 앱)
```json
{
  "type": "hello",
  "device_id": "SNW-01-A1B2",
  "model": "SnowBot SNW-01",
  "fw": "0.1.0",
  "capabilities": [
    { "id": "express", "expr": ["neutral","happy","sleepy","worried","alert","guard","greet"], "head": ["nod","shake","turn_left","turn_right"], "brow": true, "eyelid": true },
    { "id": "speaker" },
    { "id": "mic" },
    { "id": "camera", "position": "dash_cabin", "on_device_vision": ["face","drowsy"] },
    { "id": "env", "sensors": ["temp","co2"] }
  ],
  "power": "usb"
}
```
다른 모듈 예:
- 카메라 큐브(뒷좌석): `camera(position=rear_cabin, on_device_vision=[pet,motion,bark])`, `env(temp)`
- 외부 카메라: `camera(position=exterior_front, on_device_vision=[ocr_sign,approach])`
- 센서 태그: `env(temp, co2, humidity)`, `power=battery`

## 3. 센서 이벤트 (모듈 → 앱)
원본 영상은 보내지 않고 **모듈에서 처리한 결과만** 보내는 것이 기본 (개인정보·대역폭).
```json
{ "type": "event", "cap": "env",    "data": { "temp": 31.5, "co2": 1450 }, "ts": 1761465600 }
{ "type": "event", "cap": "camera", "data": { "kind": "ocr_sign", "text": "B3 C-12", "conf": 0.91 } }
{ "type": "event", "cap": "camera", "data": { "kind": "pet", "state": "panting", "conf": 0.8 } }
{ "type": "event", "cap": "camera", "data": { "kind": "drowsy", "level": 2 } }
```
- 실시간 영상 보기(펫 확인 등)는 사용자 명시 요청 시에만 별도 채널 `TODO`

## 4. 표현 명령 (앱 → 모듈)
```json
{ "type": "express", "expr": "sleepy", "head": "nod", "brow": "down", "say": "조금 쉬어갈까요? 다음 쉼터 12km예요", "priority": "safety" }
```
- `say`는 모듈 스피커로 재생할 TTS 텍스트(앱에서 Gleo TTS로 음성 생성 후 오디오 전송할지, 모듈 자체 TTS일지 `TODO`)
- 모듈이 express 기능이 없으면 앱이 가상 SnowBot(화면)으로 대신 표현

## 5. 우선순위
`safety` > `guide` > `fun`
- 상위 우선순위 명령이 오면 하위 재생 중단
- 주행 중에는 `fun` 차단 (앱에서 판단)

## 6. 열린 항목
- 서비스/특성 UUID, MTU, 재연결 정책
- 인증·페어링 (차량 1대 ↔ 모듈 다수)
- 오디오 전송 방식
- 펌웨어 업데이트(서버 경유)
- 모듈이 차에서 분리돼 집에서 쓸 때 연결 경로(폰/와이파이 → 서버)
