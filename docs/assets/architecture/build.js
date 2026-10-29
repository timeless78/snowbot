// 스노봇 시스템 구조도 3장 — SVG 를 직접 그리고 sharp 로 PNG(2배)를 만든다.
// 실행: npm i sharp@0.33.5 가 된 곳에서 node build.js docs/assets/architecture (글꼴: Noto Sans CJK KR 설치 필요)
const fs = require("fs");
const path = require("path");
const sharp = require("sharp");

const OUT = process.argv[2];
const K = {
  ink: "16202B", sub: "4A5866", line: "8A97A6", panel: "EEF4F8", ice: "C9DCEA",
  carrot: "EF8A4D", blue: "4F7FAE", green: "2E9E6B", gray: "A3AEB9", white: "FFFFFF",
};
const FONT = "Noto Sans CJK KR";

const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
// 글자 폭 어림: 한글 1.0, 그 밖 0.56 (배경 칸 크기용)
const width = (s, size) => [...s].reduce((w, c) => w + (/[ㄱ-힝]/.test(c) ? 1.0 : c === " " ? 0.3 : 0.56), 0) * size;

function Svg(w, h) {
  const parts = [];
  const add = (s) => parts.push(s);
  const markers = Object.entries({ green: K.green, carrot: K.carrot, gray: K.gray, ink: K.ink, blue: K.blue })
    .map(([n, c]) => `<marker id="a-${n}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#${c}"/></marker>`)
    .join("");
  const api = {
    rect(x, y, w, h, o = {}) {
      add(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${o.rx ?? 14}" fill="#${o.fill ?? K.white}"` +
        (o.stroke ? ` stroke="#${o.stroke}" stroke-width="${o.sw ?? 2}"` : "") +
        (o.dash ? ` stroke-dasharray="${o.dash}"` : "") + "/>");
    },
    text(x, y, s, o = {}) {
      add(`<text x="${x}" y="${y}" font-family="${FONT}" font-size="${o.size ?? 15}" font-weight="${o.weight ?? 400}"` +
        ` fill="#${o.fill ?? K.ink}" text-anchor="${o.anchor ?? "start"}"` +
        (o.rotate ? ` transform="rotate(${o.rotate} ${x} ${y})"` : "") + `>${esc(s)}</text>`);
    },
    // 선 위에 얹는 글자: 흰 바탕을 깔아 선과 겹쳐도 읽힌다
    tag(x, y, s, o = {}) {
      const size = o.size ?? 13, w = width(s, size) + 12;
      const left = o.anchor === "start" ? x - 6 : x - w / 2;
      add(`<rect x="${left}" y="${y - size - 2}" width="${w}" height="${size + 9}" rx="4" fill="#${o.bg ?? K.white}"/>`);
      api.text(x, y, s, { anchor: "middle", ...o, size });
    },
    path(d, o = {}) {
      const c = o.color ?? "ink";
      add(`<path d="${d}" fill="none" stroke="#${K[c]}" stroke-width="${o.sw ?? 2.5}"` +
        (o.dash ? ` stroke-dasharray="${o.dash}"` : "") +
        (o.end !== false ? ` marker-end="url(#a-${c})"` : "") +
        (o.start ? ` marker-start="url(#a-${c})"` : "") + "/>");
    },
    arrow(x1, y1, x2, y2, o = {}) { api.path(`M${x1},${y1} L${x2},${y2}`, o); },
    dot(x, y, c, r = 5) { add(`<circle cx="${x}" cy="${y}" r="${r}" fill="#${K[c] ?? c}"/>`); },
    raw: add,
    toString() {
      return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
        `<defs>${markers}</defs><rect width="${w}" height="${h}" fill="#${K.white}"/>${parts.join("")}</svg>`;
    },
  };
  return api;
}

const DASH = { now: undefined, pt: "9 6", later: "3 5" };
const COLOR = { now: "green", pt: "carrot", later: "gray" };

function header(s, title, subtitle, legendX) {
  s.text(40, 56, title, { size: 30, weight: 700 });
  s.text(40, 88, subtitle, { size: 16, fill: K.sub });
  if (legendX == null) return;
  [["now", "지금 동작"], ["pt", "PT(12/3)까지"], ["later", "이후"]].forEach(([k, label], i) => {
    const x = legendX + i * 165;
    s.arrow(x, 62, x + 44, 62, { color: COLOR[k], dash: DASH[k], end: false, sw: 3 });
    s.text(x + 54, 67, label, { size: 15, fill: K.sub });
  });
}

// 이름(굵게) + 설명 한 줄, 왼쪽에 상태 점
function item(s, x, y, name, desc, state = "now") {
  s.dot(x, y - 6, COLOR[state]);
  s.text(x + 16, y, name, { size: 17, weight: 700 });
  s.text(x + 16, y + 23, desc, { size: 15, fill: K.sub });
}

function group(s, x, y, w, h, title, sub, o = {}) {
  s.rect(x, y, w, h, { fill: K.panel, rx: 18, ...o });
  s.text(x + 22, y + 36, title, { size: 21, weight: 700 });
  if (sub) s.text(x + 22 + width(title, 21) + 10, y + 36, sub, { size: 14, fill: K.sub });
}

// ───────────── 1. 시스템 구성도 ─────────────
function overview() {
  const s = Svg(1600, 960);
  header(s, "스노봇 시스템 구성도", "2026-10-28 기준 · Pleos 에뮬레이터와 클라우드 서버에서 동작 확인", 1085);

  // 차 안
  group(s, 40, 120, 540, 690, "차 안", "Pleos Connect");
  s.rect(60, 176, 500, 304, { stroke: K.blue, sw: 2.5 });
  s.text(84, 214, "스노봇 차량 앱", { size: 20, weight: 700 });
  s.text(84 + width("스노봇 차량 앱", 20) + 10, 214, "Kotlin · 에뮬레이터에서 실행", { size: 14, fill: K.sub });
  item(s, 86, 258, "차 상태 읽기", "기어 · 연속 운전 시간 · 주의 경고등(5초마다)");
  item(s, 86, 314, "먼저 말 걸기 — 앱 안 규칙", "출발 · 도착 · 주의 경고 · 2시간 휴식");
  item(s, 86, 370, "말하기 · 듣기", "말하기 버튼 → 마이크(16kHz) · 차 스피커로 재생");
  item(s, 86, 426, "화면 속 스노봇", "표정 · 말풍선 · 주행 중엔 버튼 숨김");

  s.rect(60, 548, 240, 142, { stroke: K.green });
  s.text(80, 580, "Vehicle SDK", { size: 18, weight: 700 });
  ["Gear — 주행 / 주차", "Safety — 주의 경고등", "(운전 시간은 기어로 앱이 잼)"].forEach((t, i) => s.text(80, 610 + i * 25, t, { size: 15 }));
  s.arrow(180, 548, 180, 484, { color: "green" });
  s.tag(190, 522, "조회 권한만", { anchor: "start", size: 13, bg: K.panel, fill: K.sub });

  s.rect(320, 548, 240, 142, { stroke: K.carrot, dash: DASH.pt });
  s.text(340, 580, "Gleo AI (차량 비서)", { size: 18, weight: 700 });
  ["Gleo Interface 로", "ask_snowbot ·", "snowbot_status 부르기"].forEach((t, i) => s.text(340, 610 + i * 25, t, { size: 15 }));
  s.arrow(440, 548, 440, 484, { color: "carrot", dash: DASH.pt });

  s.rect(60, 712, 500, 76, { stroke: K.carrot, dash: DASH.pt });
  s.text(80, 744, "NaviHelper — 쉼터 경유지 추가 · 회전 안내 중 말 멈춤", { size: 15 });
  s.text(80, 770, "HVAC — 내기 순환이 오래 + CO₂ 높음 → 환기 권유", { size: 15 });

  // 서버
  group(s, 720, 120, 410, 690, "클라우드 스노봇 서버", "NAVER Cloud VM");
  s.rect(740, 180, 370, 70, { stroke: K.green });
  s.text(760, 210, "Caddy — 암호화 연결(TLS) 입구", { size: 16, weight: 700 });
  s.text(760, 236, "바깥에는 https · wss 만 열림", { size: 14, fill: K.sub });
  s.rect(740, 270, 370, 314, { stroke: K.green, sw: 2.5 });
  s.text(760, 306, "중계 서버 (FastAPI WebSocket)", { size: 18, weight: 700 });
  item(s, 764, 350, "인증 · 짝 맺기", "QR 등록 코드 → 앱 토큰 · 기기 키");
  item(s, 764, 406, "차 상태 → 대화 상황", "주행 중 2문장 · 주차 중 3문장");
  item(s, 764, 462, "대화 진행", "표정 먼저 → 문장마다 목소리");
  item(s, 764, 518, "메시지 전달", "차량 앱 ↔ 스노봇 로봇");
  s.rect(740, 604, 370, 184, { stroke: K.green });
  s.text(760, 636, "보관하는 것", { size: 17, weight: 700 });
  ["기기 DB (SQLite) — 키 · 토큰은 해시만", "대화 기억 — 최근 8번, 메모리에만", "AI API 키 — 서버에만", "녹음 · 말 내용 — 저장 안 함"]
    .forEach((t, i) => s.text(760, 668 + i * 28, t, { size: 15 }));

  // 차량 앱 ↔ 서버
  s.arrow(560, 330, 740, 330, { color: "green" });
  s.tag(650, 318, "차 상태 · 말소리", { size: 14 });
  s.tag(650, 362, "WebSocket (wss)", { size: 14, weight: 700, fill: K.green });
  s.arrow(740, 390, 560, 390, { color: "green" });
  s.tag(650, 418, "표정 · 목소리 · 글자", { size: 14 });

  // 외부 AI
  group(s, 1200, 200, 360, 370, "외부 AI", "API");
  const ai = [["① 음성 인식", "ElevenLabs Scribe v2 Realtime"], ["② 대답 · 표정 고르기", "Claude Haiku 5.5 (문장 단위로 받음)"], ["③ 목소리", "ElevenLabs v4 Turbo · 목소리 Suzu"]];
  ai.forEach(([a, b], i) => {
    const y = 256 + i * 100;
    s.rect(1218, y, 324, 84, { stroke: K.green });
    s.text(1238, y + 35, a, { size: 17, weight: 700 });
    s.text(1238, y + 61, b, { size: 15, fill: K.sub });
    s.arrow(1110, y + 42, 1218, y + 42, { color: "green", start: true });
  });
  s.tag(1380, 598, "API 키는 서버에만 둔다", { size: 13, fill: K.sub });

  // 스노봇 로봇
  group(s, 1200, 620, 360, 190, "스노봇 로봇", "XIAO ESP32-S3", { stroke: K.carrot, dash: DASH.pt, sw: 2 });
  ["서보 4개 — 눈꺼풀 · 눈썹 · 고개", "마이크 · 스피커 · CO₂ · 온도 센서", "부품 11/7 도착 → 조립", "서버 쪽 연결은 가상 스노봇으로 시험 완료"]
    .forEach((t, i) => s.text(1222, 690 + i * 28, t, { size: 15, fill: i < 2 ? K.ink : K.sub }));
  s.path("M1110,560 L1160,560 L1160,700 L1200,700", { color: "carrot", dash: DASH.pt, start: true });
  s.tag(1160, 640, "Wi-Fi", { size: 13, fill: K.carrot, weight: 700 });

  // 블루투스 (이후)
  s.path("M310,810 L310,830 L1380,830 L1380,810", { color: "gray", dash: DASH.later, start: true });
  s.tag(845, 835, "블루투스 직접 연결 — 최종 목표: 차량 앱이 모듈 허브 (이후)", { size: 14, fill: K.sub });

  // 아래 요점
  [["AI 는 서버에서만", "앱 · 로봇에는 API 키가 없다. AI 를 바꿔도 앱은 그대로"],
   ["인터넷이 끊겨도", "출발 · 경고 · 휴식 알림은 앱 안 규칙으로 화면에 뜬다"],
   ["개인정보", "녹음 저장 안 함 · 서버 기록에 말 내용 없음(길이 · 소리 크기만)"]]
    .forEach(([a, b], i) => {
      const x = 40 + i * 515;
      s.rect(x, 862, 490, 76, { fill: K.panel, rx: 12 });
      s.text(x + 20, 892, a, { size: 17, weight: 700, fill: K.blue });
      s.text(x + 20, 920, b, { size: 14.5 });
    });
  return s;
}

// ───────────── 2. 대화 한 번의 처리 순서 ─────────────
function conversation() {
  const s = Svg(1600, 1050);
  header(s, "말 한마디가 지나가는 길", "운전자가 말하고 스노봇이 대답하기까지 · 모든 단계를 조각 단위로 흘려 보내 기다림을 줄인다");
  const lanes = [["운전자", 170, "ink"], ["차량 앱", 420, "blue"], ["스노봇 서버", 720, "green"], ["음성 인식", 990, "green"], ["대답 AI", 1230, "green"], ["목소리", 1460, "green"]];
  const sub = { "음성 인식": "Scribe v2", "대답 AI": "Claude Haiku 5.5", "목소리": "ElevenLabs" };
  const X = Object.fromEntries(lanes.map(([n, x]) => [n, x]));
  lanes.forEach(([n, x, c]) => {
    s.rect(x - 95, 116, 190, 62, { fill: c === "ink" ? K.panel : K.white, stroke: K[c], sw: 2.5, rx: 12 });
    s.text(x, sub[n] ? 143 : 154, n, { size: 18, weight: 700, anchor: "middle" });
    if (sub[n]) s.text(x, 166, sub[n], { size: 13, fill: K.sub, anchor: "middle" });
    s.arrow(x, 178, x, 1000, { color: "gray", dash: "4 6", end: false, sw: 1.5 });
  });
  const msg = (from, to, y, label, o = {}) => {
    const x1 = X[from], x2 = X[to], dir = Math.sign(x2 - x1);
    s.arrow(x1 + dir * 6, y, x2 - dir * 6, y, { color: o.color ?? "ink", dash: o.dash, sw: o.sw ?? 2.2 });
    s.tag((x1 + x2) / 2, y - 9, label, { size: 14, fill: o.fill ?? K.ink, weight: o.weight });
  };
  const self = (lane, y, h, lines, o = {}) => {
    const w = o.w ?? 330, x = X[lane] - w / 2;
    s.rect(x, y, w, h, { fill: o.fill ?? K.panel, stroke: o.stroke ?? K.green, rx: 10 });
    lines.forEach((t, i) => s.text(x + 16, y + 26 + i * 24, t, { size: 14.5, weight: i === 0 ? 700 : 400 }));
  };

  msg("운전자", "차량 앱", 216, "말하기 버튼 누르고 말함");
  msg("차량 앱", "스노봇 서버", 258, "마이크 소리를 0.1초씩 바로 보냄", { sw: 3.5, color: "blue" });
  msg("스노봇 서버", "음성 인식", 300, "그대로 흘려 보냄", { sw: 3.5, color: "green" });
  msg("음성 인식", "스노봇 서버", 340, "받아 적는 중인 글자", { dash: "6 5", color: "green" });
  msg("스노봇 서버", "차량 앱", 380, "화면에 글자 (heard)", { dash: "6 5", color: "green" });
  msg("운전자", "차량 앱", 428, "버튼 뗌 = 말 끝", { weight: 700 });
  msg("차량 앱", "스노봇 서버", 468, "listen_end", { color: "blue" });
  msg("음성 인식", "스노봇 서버", 508, "확정 글자 (0.2~0.35초)", { dash: "6 5", color: "green" });
  self("스노봇 서버", 524, 114, ["상황 붙이기", "차: 주행 중 · 운전 41분 · 경고 없음", "스노봇이 먼저 한 말 · 최근 대화 8번", ], { w: 340 });
  s.text(X["스노봇 서버"] - 154, 624, "→ 주행 중이면 2문장(30자 안팎)", { size: 14.5, fill: K.carrot, weight: 700 });
  msg("스노봇 서버", "대답 AI", 668, "질문 + 상황 + 문장 수");
  msg("대답 AI", "스노봇 서버", 708, "대답 맨 앞 표정 태그 [sleepy nod] — 오는 즉시", { dash: "6 5", color: "green" });
  msg("스노봇 서버", "차량 앱", 748, "표정 먼저 (reply_start)", { color: "green", weight: 700, fill: K.green });
  msg("대답 AI", "스노봇 서버", 788, "첫 문장 — 다 쓰는 대로", { dash: "6 5", color: "green" });
  msg("스노봇 서버", "목소리", 828, "첫 문장");
  msg("목소리", "스노봇 서버", 868, "목소리 조각 (PCM)", { dash: "6 5", color: "green" });
  msg("스노봇 서버", "차량 앱", 908, "목소리 흘려 보냄", { sw: 3.5, color: "green" });
  msg("차량 앱", "운전자", 948, "차 스피커 첫 소리", { weight: 700 });
  s.tag(X["차량 앱"] + 30, 988, "다음 문장도 같은 길로 이어 말하고, 끝에 글자와 걸린 시간(reply)을 보낸다", { size: 14.5, fill: K.sub, anchor: "start" });

  // 걸린 시간 괄호
  s.path("M60,428 L44,428 L44,948 L60,948", { color: "carrot", end: false, sw: 3 });
  s.text(32, 688, "말 끝 → 첫 소리 보통 1.5초 (17번 실측, 1.3~2.5초)", { size: 16, weight: 700, fill: K.carrot, anchor: "middle", rotate: -90 });

  s.text(40, 1030, "실물 스노봇이 연결돼 있으면 같은 표정 명령과 목소리가 로봇으로도 간다. 대답 AI 가 실패하거나 못 들었으면 정해 둔 말(걱정 표정)로 대신한다.", { size: 14.5, fill: K.sub });
  return s;
}

// ───────────── 3. 차 상태가 쓰이는 길 ─────────────
function car() {
  const s = Svg(1600, 880);
  header(s, "차 상태가 스노봇을 바꾸는 길", "차 상태 세 가지(기어 · 운전 시간 · 경고등) → 앱이 먼저 말 걸기 + 서버가 대답 길이 · 내용 바꾸기");

  s.rect(40, 130, 340, 470, { fill: K.panel, rx: 18 });
  s.text(62, 168, "차 상태 세 가지", { size: 21, weight: 700 });
  s.text(62, 194, "Vehicle SDK 2개(조회만) + 앱이 잰 시간", { size: 14, fill: K.sub });
  [["기어 P · R · N · D", "바뀔 때마다 알려 줌 (Gear)"], ["연속 운전 시간", "P 에서 출발한 때부터 앱이 잼"], ["운전자 주의 경고등", "5초마다 물어봄 (Safety)"]]
    .forEach(([a, b], i) => {
      const y = 218 + i * 122;
      s.rect(60, y, 300, 102, { stroke: K.green });
      s.text(82, y + 42, a, { size: 18, weight: 700 });
      s.text(82, y + 72, b, { size: 15, fill: K.sub });
    });
  s.text(62, 625, "에뮬레이터 Virtual Console 에 주의 경고등이 없어", { size: 13, fill: K.sub });
  s.text(62, 645, "경고는 시험용 명령으로 확인했다", { size: 13, fill: K.sub });

  s.arrow(380, 380, 440, 380, { color: "green" });
  s.rect(440, 290, 250, 180, { stroke: K.blue, sw: 2.5 });
  s.text(462, 330, "차 상태 정리", { size: 19, weight: 700 });
  s.text(462, 354, "CarState", { size: 14, fill: K.sub });
  s.text(462, 392, "기어 D · 운전 41분", { size: 16 });
  s.text(462, 418, "경고 꺼짐", { size: 16 });
  s.text(462, 450, "같은 값이면 다시 안 알림", { size: 13, fill: K.sub });

  s.path("M690,340 L730,340 L730,250 L770,250", { color: "green" });
  s.path("M690,420 L730,420 L730,570 L770,570", { color: "green" });

  const table = (x, y, title, sub, rows, colW) => {
    s.text(x, y, title, { size: 20, weight: 700 });
    s.text(x, y + 26, sub, { size: 14.5, fill: K.sub });
    rows.forEach(([a, b], i) => {
      const ry = y + 46 + i * 54;
      s.rect(x, ry, 770, 46, { fill: i % 2 ? K.white : K.panel, rx: 8 });
      s.text(x + 16, ry + 30, a, { size: 15.5, weight: 700 });
      s.text(x + 16 + colW, ry + 30, b, { size: 15.5 });
    });
  };
  s.rect(770, 120, 800, 296, { stroke: K.green, rx: 18 });
  table(786, 160, "① 앱이 먼저 말 건다 — 앱 안 규칙", "인터넷이 끊겨도 화면 속 스노봇의 표정 · 말풍선으로 뜬다 · 목소리는 서버가 만든다", [
    ["출발 (P → 주행)", "반가운 표정 + 인사"],
    ["도착 (주행 → P)", "수고 인사 · 1시간 넘게 달렸으면 푹 쉬라고"],
    ["주의 경고등 켜짐", "걱정 표정 + 쉼터 권유 (10분에 한 번)"],
    ["운전 120분째, 그 뒤 60분마다", "졸린 표정 + \"벌써 2시간째 운전 중이에요. 쉼터에서…\""],
  ], 290);
  s.rect(770, 440, 800, 262, { stroke: K.green, rx: 18 });
  table(786, 480, "② 서버가 대답을 바꾼다 — 대화 상황에 차 상태", "앱이 바뀔 때마다 car 메시지로 보낸다 (기어 · 운전 분 · 경고)", [
    ["주행 중", "2문장, 30자 안팎 · 주차 중엔 3문장"],
    ["경고 켜짐 · 2시간 넘음", "농담을 부탁해도 휴식 권유가 먼저"],
    ["스노봇이 먼저 한 말", "10분 동안 기억 → \"아까 쉬자고 했죠?\"로 이어 감"],
  ], 290);

  s.rect(40, 732, 1530, 108, { stroke: K.carrot, dash: DASH.pt, rx: 14 });
  s.text(62, 768, "PT(12/3)까지 더할 것", { size: 18, weight: 700, fill: K.carrot });
  s.text(62, 798, "NaviHelper — 휴식 권유에 \"응\" 하면 쉼터를 경유지로 추가 · 회전 안내 중엔 말 멈춤", { size: 15.5 });
  s.text(62, 824, "HVAC — 내기 순환이 오래 + 스노봇 CO₂ 센서 값이 높음 → 환기 권유 · CarUxRestrictions — 주행 규제에 맞춰 화면 줄이기", { size: 15.5 });
  return s;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  for (const [name, fn] of [["01-system-overview", overview], ["02-conversation-flow", conversation], ["03-car-data-flow", car]]) {
    const svg = fn().toString();
    fs.writeFileSync(path.join(OUT, name + ".svg"), svg);
    await sharp(Buffer.from(svg), { density: 144 }).png().toFile(path.join(OUT, name + ".png"));
    console.log("wrote", name);
  }
})();
