// 시스템 구조 PPT — docs/pleos/06-system-architecture.md 내용을 슬라이드 8장으로
// 실행: pptxgenjs · react · react-dom · react-icons · sharp 가 설치된 곳을 NODE_PATH 로 주고
//   PPTX_SKILL=<pptx 스킬 폴더> node build-deck.js ../../pleos/06-system-architecture.pptx
// 아이콘: vendor/ncp (NAVER Cloud Platform 서비스 아이콘 2025-02-17판), vendor/pleos (Pleos Connect UI Design Guide), 나머지 Tabler · Simple Icons
const fs = require("fs");
const path = require("path");
const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const tb = require("react-icons/tb");
const si = require("react-icons/si");
const { applyTheme } = require(process.env.PPTX_SKILL + "/scripts/apply_theme.js");

const OUT = process.argv[2];
const VENDOR = path.join(__dirname, "vendor");

const THEME = {
  name: "SnowBot Night",
  headFontFace: "Noto Sans CJK KR",
  bodyFontFace: "Noto Sans CJK KR",
  colors: {
    dk1: "16202B", lt1: "FFFFFF", dk2: "111821", lt2: "EEF4F8",
    accent1: "EF8A4D", accent2: "4F7FAE", accent3: "6B4A2F", accent4: "2E9E6B",
    accent5: "C9DCEA", accent6: "8A97A6", hlink: "4F7FAE", folHlink: "6B4A2F",
  },
};
const HEX = THEME.colors;
// 선 모양 = 진행 상태 (구조도 PNG 와 같은 약속)
const ST = {
  now: { color: "2E9E6B", dashType: "solid", label: "지금 동작" },
  pt: { color: "EF8A4D", dashType: "dash", label: "PT(12/3)까지" },
  later: { color: "8A97A6", dashType: "sysDot", label: "이후" },
};
// 덩어리별 바탕색
const ZONE = {
  car: { fill: "EAF1F8", ink: "4F7FAE" },
  cloud: { fill: "E7F6EC", ink: "1E8C4E" },
  ai: { fill: "F1F0F6", ink: "6A5AA8" },
  robot: { fill: "FDF1E8", ink: "C96A2E" },
};
const GLEO = { light: "9BA8E2", dark: "3F4B81" }; // Pleos 디자인 가이드 gleo_bubble 색
const WHO = { 앱: "4F7FAE", 서버: "1E8C4E", AI: "6A5AA8", 운전자: "6B4A2F" };

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.33 x 7.5
pres.title = "스노봇 시스템 구조";
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
const C = pres.SchemeColor;

// ---------- 아이콘 ----------
const png = (buf) => "image/png;base64," + buf.toString("base64");
async function svgIcon(set, name, hex, px = 256) {
  const svg = ReactDOMServer.renderToStaticMarkup(React.createElement(set[name], { color: "#" + hex, size: px }));
  return png(await sharp(Buffer.from(svg)).resize(px, px).png().toBuffer());
}
const T = (name, hex = HEX.dk1) => svgIcon(tb, name, hex);
const S = (name, hex = HEX.dk1) => svgIcon(si, name, hex);
async function ncp(name) {
  const buf = await sharp(path.join(VENDOR, "ncp", name + ".png")).resize(208, 208, { kernel: "lanczos3" }).png().toBuffer();
  return png(buf);
}
// Pleos 아이콘 예시 그림에서 아이콘 한 칸만 잘라 쓴다 (타일 배경 · 글자 빼고)
async function pleos(which) {
  const box = { vehicle: "130 144 80 80", climate: "569.6 144 80 80" }[which];
  let s = fs.readFileSync(path.join(VENDOR, "pleos", "icon-types.svg"), "utf8");
  s = s.replace(/<rect [^>]*\/>/, "").replace(/<path d="[^"]*" fill="white"\/>/g, "");
  s = s.replace(/width="1000" height="400" viewBox="0 0 1000 400"/, `width="256" height="256" viewBox="${box}"`);
  return png(await sharp(Buffer.from(s)).png().toBuffer());
}

// ---------- 레이아웃 ----------
const FOOT = { x: 0.6, y: 7.02, w: 9, h: 0.3, fontSize: 10, color: C.accent6 };
pres.defineSlideMaster({
  title: "TITLE_DARK",
  background: { color: C.text2 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: 0.7, y: 1.7, w: 7.2, h: 1.2, fontSize: 50, bold: true, color: C.background1, valign: "middle", align: "left", margin: 0 }, text: "" } },
    { placeholder: { options: { name: "body", type: "body", x: 0.7, y: 2.95, w: 7.2, h: 1.0, fontSize: 22, color: C.accent5, valign: "top", align: "left", margin: 0 }, text: "" } },
  ],
});
pres.defineSlideMaster({
  title: "CONTENT",
  background: { color: C.background1 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: 0.5, y: 0.3, w: 12.3, h: 0.7, fontSize: 28, bold: true, color: C.text1, valign: "middle", align: "left", margin: 0 }, text: "" } },
    { placeholder: { options: { name: "body", type: "body", x: 0.5, y: 0.98, w: 12.3, h: 0.42, fontSize: 14, color: C.accent6, valign: "top", align: "left", margin: 0 }, text: "" } },
    { text: { text: "스노봇 SnowBot · 시스템 구조 (2026-10-28)", options: FOOT } },
  ],
  slideNumber: { x: 12.3, y: 7.02, w: 0.5, h: 0.3, fontSize: 10, color: C.accent6, align: "right" },
});

// ---------- 조각 ----------
function zone(s, x, y, w, h, z, title, opts = {}) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x, y, w, h, rectRadius: 0.16, fill: { color: z.fill },
    line: opts.status ? { color: ST[opts.status].color, width: 1.5, dashType: ST[opts.status].dashType } : { type: "none" },
    objectName: "zone " + title,
  });
  const tx = opts.icon ? x + 0.55 : x + 0.2;
  s.addText(title, { x: tx, y: y + 0.12, w: w - (tx - x) - 0.15, h: 0.36, fontSize: 13, bold: true, color: z.ink, margin: 0, valign: "middle", isTextBox: true });
  if (opts.icon) s.addImage({ data: opts.icon, x: x + 0.18, y: y + 0.12, w: 0.36, h: 0.36, altText: title });
}
// Pleos 아이콘 카드처럼: 흰 둥근 네모 + 가운데 아이콘
function tile(s, x, y, d, icon, alt, opts = {}) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x, y, w: d, h: d, rectRadius: d * 0.2, fill: { color: opts.fill || "FFFFFF" },
    line: opts.status ? { color: ST[opts.status].color, width: 1.25, dashType: ST[opts.status].dashType } : { color: "DDE3EA", width: 0.75 },
    shadow: { type: "outer", color: "1B2A3A", blur: 4, offset: 1.5, angle: 90, opacity: 0.12 },
    objectName: "tile " + alt,
  });
  const p = d * (opts.pad || 0.2);
  s.addImage({ data: icon, x: x + p, y: y + p, w: d - 2 * p, h: d - 2 * p, altText: alt });
}
function card(s, x, y, w, h, opts = {}) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x, y, w, h, rectRadius: opts.r || 0.1, fill: { color: opts.fill || "FFFFFF" },
    line: opts.status ? { color: ST[opts.status].color, width: 1.25, dashType: ST[opts.status].dashType } : { color: opts.line || "DDE3EA", width: 0.75 },
    objectName: opts.name || "card",
  });
}
// 제목 한 줄 + 설명 (줄 여러 개)
function words(s, x, y, w, h, title, lines = [], opts = {}) {
  const runs = [{ text: title, options: { fontSize: opts.ts || 13, bold: true, color: opts.tc || HEX.dk1, breakLine: lines.length > 0 } }];
  lines.forEach((l, i) => runs.push({ text: l, options: { fontSize: opts.ds || 10.5, color: opts.dc || "5B6773", breakLine: i < lines.length - 1 } }));
  s.addText(runs, { x, y, w, h, margin: 0, valign: opts.valign || "middle", align: opts.align || "left", paraSpaceAfter: 2, isTextBox: true });
}
function arrow(s, x1, y1, x2, y2, st = "now", opts = {}) {
  const line = { color: ST[st].color, width: opts.width || 2, dashType: ST[st].dashType, endArrowType: "triangle" };
  if (opts.both) line.beginArrowType = "triangle";
  s.addShape(pres.shapes.LINE, {
    x: Math.min(x1, x2), y: Math.min(y1, y2), w: Math.abs(x2 - x1), h: Math.abs(y2 - y1),
    flipH: x2 < x1, flipV: y2 < y1, line, objectName: "arrow " + (opts.name || ""),
  });
}
function tag(s, text, x, y, w, h, opts = {}) {
  s.addText(text, {
    x, y, w, h, margin: 0, fontSize: opts.fs || 10, color: opts.color || "4A5561", bold: !!opts.bold,
    align: opts.align || "center", valign: "middle", fill: opts.fill ? { color: opts.fill } : undefined, isTextBox: true,
  });
}
function chip(s, text, x, y, color) {
  const w = 0.16 + text.length * 0.13;
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h: 0.26, rectRadius: 0.13, fill: { color }, line: { type: "none" }, objectName: "chip " + text });
  s.addText(text, { x, y, w, h: 0.26, margin: 0, fontSize: 9.5, bold: true, color: "FFFFFF", align: "center", valign: "middle", isTextBox: true });
  return w;
}
function legend(s, x, y, dark = false) {
  ["now", "pt", "later"].forEach((k, i) => {
    const lx = x + i * 1.75;
    s.addShape(pres.shapes.LINE, { x: lx, y: y + 0.15, w: 0.55, h: 0, line: { color: ST[k].color, width: 2.5, dashType: ST[k].dashType }, objectName: "legend " + k });
    s.addText(ST[k].label, { x: lx + 0.65, y, w: 1.1, h: 0.3, margin: 0, fontSize: 11, color: dark ? HEX.accent5 : "4A5561", valign: "middle", isTextBox: true });
  });
}

(async () => {
  const I = {
    users: await ncp("users"), server: await ncp("server"), vpc: await ncp("vpc"), cert: await ncp("certificate-manager"),
    internet: await ncp("global-internet"),
    vehicle: await pleos("vehicle"), climate: await pleos("climate"),
    snowman: await T("TbSnowman", HEX.accent2), mic: await T("TbMicrophone", ZONE.ai.ink), wave: await T("TbWaveSine", ZONE.ai.ink),
    anthropic: await S("SiAnthropic", "191919"), fastapi: await S("SiFastapi", "05998B"), caddy: await S("SiCaddy", "1F88C0"),
    esp: await S("SiEspressif", "E7352C"), gleo: await T("TbMessageCircle", GLEO.dark),
  };

  // ===== 1. 표지 =====
  {
    const s = pres.addSlide({ masterName: "TITLE_DARK" });
    s.addText("스노봇 시스템 구조", { placeholder: "title" });
    s.addText("차량 앱 · 클라우드 서버 · 외부 AI · 스노봇 로봇이\n어떻게 이어지고, 무엇이 지금 동작하는가", { placeholder: "body" });
    // 오른쪽: 네 덩어리를 잇는 한 줄
    const row = [["snowman", "차량 앱"], ["server", "NAVER Cloud"], ["anthropic", "외부 AI"], ["esp", "스노봇 로봇"]];
    const x0 = 8.35, y0 = 2.05, d = 0.95, gap = 0.2;
    for (let i = 0; i < row.length; i++) {
      const y = y0 + i * (d + gap + 0.08);
      tile(s, x0, y, d, I[row[i][0]], row[i][1], { status: i === 3 ? "pt" : undefined });
      s.addText(row[i][1], { x: x0 + d + 0.25, y, w: 2.6, h: d, margin: 0, fontSize: 18, bold: true, color: HEX.lt1, valign: "middle", isTextBox: true });
    }
    s.addText("선 모양으로 진행 상태를 나눴다", { x: 0.7, y: 5.55, w: 6, h: 0.3, margin: 0, fontSize: 12, color: HEX.accent6, isTextBox: true });
    legend(s, 0.7, 5.95, true);
    s.addText("2026-10-28 · Pleos 공모전 기획서 첨부 (docs/pleos/06-system-architecture.md)", { x: 0.7, y: 6.85, w: 9, h: 0.3, margin: 0, fontSize: 10, color: HEX.accent6, isTextBox: true });
    s.addNotes("스노봇은 네 덩어리로 이루어져 있습니다. 차 안의 Pleos 앱, 네이버 클라우드의 중계 서버, 서버만 부르는 외부 AI, 그리고 책상 위·차 안의 스노봇 로봇입니다. 초록 실선은 지금 동작하는 것, 주황 점선은 PT 까지, 회색 점선은 그 뒤에 할 것입니다.");
  }

  // ===== 2. 한눈에 =====
  {
    const s = pres.addSlide({ masterName: "CONTENT" });
    s.addText("한눈에 — 차량 앱과 서버는 암호화 연결(wss) 하나로 이어진다", { placeholder: "title" });
    s.addText("AI 는 서버만 부른다. 앱 · 로봇에는 API 키가 없다.", { placeholder: "body" });

    // 차 안
    zone(s, 0.5, 1.6, 3.75, 4.85, ZONE.car, "차 안 · Pleos Connect", { icon: I.vehicle });
    card(s, 0.7, 2.15, 3.35, 0.85);
    tile(s, 0.8, 2.22, 0.7, I.users, "운전자", { pad: 0.16 });
    words(s, 1.65, 2.2, 2.35, 0.75, "운전자", ["말하기 버튼 · 차 스피커로 듣기"]);
    arrow(s, 1.5, 3.02, 1.5, 3.33, "now", { both: true, width: 1.75 });
    tag(s, "말 · 목소리", 1.62, 3.05, 1.2, 0.26, { align: "left" });
    card(s, 0.7, 3.35, 3.35, 1.05, { line: HEX.accent2 });
    tile(s, 0.8, 3.5, 0.75, I.snowman, "스노봇 차량 앱");
    words(s, 1.7, 3.4, 2.3, 0.95, "스노봇 차량 앱", ["Kotlin · 차 상태 읽기", "먼저 말 걸기 · 화면 속 스노봇"]);
    // 차 → 앱, Gleo ↔ 앱
    tile(s, 0.95, 4.95, 0.7, I.vehicle, "Vehicle SDK");
    arrow(s, 1.3, 4.93, 1.3, 4.42, "now", { width: 1.75 });
    words(s, 0.6, 5.7, 1.5, 0.65, "Vehicle SDK", ["기어 · 주의 경고등"], { ts: 11.5, ds: 9.5, align: "center", valign: "top" });
    tile(s, 2.75, 4.95, 0.7, I.gleo, "Gleo AI", { status: "pt", fill: "F2F4FC" });
    arrow(s, 3.1, 4.93, 3.1, 4.42, "pt", { both: true, width: 1.75 });
    words(s, 2.3, 5.7, 1.6, 0.65, "Gleo AI", ["Gleo Interface 로 연결"], { ts: 11.5, ds: 9.5, align: "center", valign: "top", tc: GLEO.dark });

    // 클라우드
    zone(s, 5.05, 1.6, 3.6, 3.35, ZONE.cloud, "NAVER Cloud Platform · 서버 1대", { icon: I.server });
    // 입구(Caddy)
    card(s, 5.25, 2.15, 0.95, 2.6, { line: "B9DCC7" });
    s.addImage({ data: I.caddy, x: 5.45, y: 2.35, w: 0.55, h: 0.55, altText: "Caddy" });
    s.addImage({ data: I.cert, x: 5.48, y: 3.05, w: 0.5, h: 0.5, altText: "TLS 인증서 (NCP Certificate 아이콘)" });
    words(s, 5.25, 3.65, 0.95, 1.0, "Caddy", ["TLS 입구", "443 하나"], { ts: 11, ds: 9.5, align: "center", valign: "top" });
    arrow(s, 6.22, 3.0, 6.48, 3.0, "now", { width: 1.75 });
    // 중계 서버
    card(s, 6.5, 2.15, 1.95, 1.65);
    s.addImage({ data: I.fastapi, x: 6.62, y: 2.27, w: 0.42, h: 0.42, altText: "FastAPI" });
    words(s, 7.1, 2.22, 1.3, 0.5, "중계 서버", [], { ts: 12 });
    words(s, 6.62, 2.75, 1.78, 1.0, "", ["짝 맺기 (QR)", "차 상태 → 대화 상황", "표정 먼저 → 목소리"], { ds: 9.5, valign: "top" });
    card(s, 6.5, 3.95, 1.95, 0.8);
    words(s, 6.62, 3.98, 1.78, 0.74, "보관하는 것", ["키 · 토큰은 해시만", "대화 8번 · 메모리에만"], { ts: 10.5, ds: 9.5 });

    // 앱 ↔ 서버
    arrow(s, 4.07, 3.62, 5.23, 3.62, "now", { width: 2.25 });
    tag(s, "차 상태 · 말소리", 4.0, 3.3, 1.3, 0.28, { bold: true, color: "1E6B45" });
    arrow(s, 5.23, 4.05, 4.07, 4.05, "now", { width: 2.25 });
    tag(s, "표정 · 목소리 · 글자", 3.95, 4.1, 1.4, 0.28, { bold: true, color: "1E6B45" });

    // 외부 AI
    zone(s, 9.4, 1.6, 3.45, 3.35, ZONE.ai, "외부 AI · 서버만 부른다", { icon: await T("TbSparkles", ZONE.ai.ink) });
    const ai = [[I.mic, "음성 인식", "ElevenLabs Scribe v2"], [I.anthropic, "대답 · 표정 고르기", "Claude Haiku 5.5"], [I.wave, "목소리", "ElevenLabs v4 Turbo"]];
    const flow = ["소리 → 글자", "질문 → 문장", "문장 → 소리"];
    ai.forEach(([ic, t1, t2], i) => {
      const y = 2.15 + i * 0.92;
      card(s, 9.6, y, 3.05, 0.8);
      tile(s, 9.68, y + 0.08, 0.64, ic, t1, { pad: 0.2 });
      words(s, 10.45, y + 0.05, 2.15, 0.7, t1, [t2], { ts: 12, ds: 9.5 });
      arrow(s, 8.47, y + 0.4, 9.58, y + 0.4, "now", { both: true, width: 1.75 });
      tag(s, flow[i], 8.5, y + 0.1, 1.05, 0.26, { fs: 9.5 });
    });

    // 로봇
    zone(s, 5.05, 5.35, 3.6, 1.45, ZONE.robot, "스노봇 로봇", { status: "pt", icon: I.esp });
    words(s, 5.25, 5.78, 3.3, 0.95, "XIAO ESP32-S3", ["서보 4개 · 마이크 · 스피커 · CO₂ 센서", "부품 11/7 도착 → 조립"], { ts: 11.5, ds: 9.5, valign: "top" });
    arrow(s, 6.85, 4.97, 6.85, 5.33, "pt", { both: true, width: 1.75 });
    tag(s, "Wi-Fi · 표정 명령 · 목소리", 6.95, 5.0, 1.9, 0.3, { align: "left", color: "B5561D" });
    arrow(s, 4.27, 6.1, 5.03, 6.1, "later", { both: true, width: 1.75 });
    tag(s, "블루투스", 4.2, 5.8, 0.9, 0.26, { color: "6B7785" });

    // 오른쪽 아래: 핵심 한 줄 + 범례
    card(s, 9.4, 5.35, 3.45, 1.45, { fill: "F7F9FB" });
    words(s, 9.6, 5.45, 3.15, 0.7, "API 키는 서버에만", ["AI 를 바꿔도 앱 · 로봇은 그대로"], { ts: 12, ds: 10 });
    ["now", "pt", "later"].forEach((k, i) => {
      const y = 6.17 + i * 0.2;
      s.addShape(pres.shapes.LINE, { x: 9.62, y: y + 0.08, w: 0.42, h: 0, line: { color: ST[k].color, width: 2, dashType: ST[k].dashType }, objectName: "legend " + k });
      s.addText(ST[k].label, { x: 10.12, y: y - 0.02, w: 1.6, h: 0.2, margin: 0, fontSize: 9, color: "4A5561", valign: "middle", isTextBox: true });
    });
    s.addNotes("왼쪽 차 안에서 앱이 Vehicle SDK 로 차 상태를 읽고, 서버에 wss 하나로 붙습니다. 서버 입구는 Caddy 가 맡아 443 하나만 열려 있습니다. 중계 서버가 세 가지 외부 AI 를 부르고, 키는 서버에만 있습니다. 로봇은 같은 서버에 Wi-Fi 로 붙고, 부품이 11월 7일 도착하면 조립합니다. 블루투스 직접 연결은 그 뒤의 목표입니다.");
  }

  // ===== 3. 서버 안쪽 =====
  {
    const s = pres.addSlide({ masterName: "CONTENT" });
    s.addText("서버 안쪽 — 문은 하나, 키는 안쪽에만", { placeholder: "title" });
    s.addText("밖에서 들어오는 길은 Caddy 의 443(https · wss) 하나다. 중계 서버 포트는 밖에서 닿지 않는다.", { placeholder: "body" });

    // 왼쪽: 붙는 쪽
    const clients = [[I.snowman, "차량 앱", "앱 토큰으로 인증", null], [I.esp, "스노봇 로봇", "기기 키로 인증", "pt"]];
    clients.forEach(([ic, t1, t2, st], i) => {
      const y = 2.0 + i * 1.75;
      card(s, 0.5, y, 2.35, 1.3, { status: st || undefined });
      tile(s, 0.65, y + 0.25, 0.8, ic, t1);
      words(s, 1.55, y + 0.15, 1.25, 1.0, t1, [t2], { ts: 12.5, ds: 9.5 });
    });
    s.addImage({ data: I.internet, x: 3.45, y: 2.95, w: 0.55, h: 0.55, altText: "인터넷 (NCP Global Internet 아이콘)" });
    tag(s, "인터넷", 3.25, 3.52, 0.95, 0.26, { fs: 9.5 });
    arrow(s, 2.87, 2.65, 3.45, 3.1, "now", { width: 1.75 });
    arrow(s, 2.87, 4.4, 3.45, 3.4, "pt", { width: 1.75 });

    // NCP 구역
    zone(s, 4.35, 1.6, 8.5, 5.2, ZONE.cloud, "NAVER Cloud Platform · VPC 안 서버 1대", { icon: I.vpc });
    arrow(s, 4.05, 3.22, 4.95, 3.22, "now", { width: 2.25 });
    tag(s, "wss · https", 4.0, 2.88, 1.0, 0.28, { fs: 9.5, bold: true, color: "1E6B45" });
    // 입구
    card(s, 4.97, 2.15, 1.75, 2.15, { line: "B9DCC7" });
    s.addImage({ data: I.caddy, x: 5.2, y: 2.3, w: 0.6, h: 0.6, altText: "Caddy" });
    s.addImage({ data: I.cert, x: 5.92, y: 2.33, w: 0.55, h: 0.55, altText: "TLS 인증서" });
    words(s, 5.07, 3.0, 1.6, 1.25, "Caddy", ["암호화(TLS) 입구", "인증서 자동 갱신", "열린 문: 443 하나"], { ts: 12.5, ds: 9.5, valign: "top" });
    arrow(s, 6.74, 3.22, 7.2, 3.22, "now", { width: 2 });
    tag(s, "안쪽으로만", 6.5, 3.3, 0.95, 0.26, { fs: 8.5 });

    // 중계 서버
    card(s, 7.22, 2.15, 5.4, 2.6);
    s.addImage({ data: I.fastapi, x: 7.4, y: 2.28, w: 0.45, h: 0.45, altText: "FastAPI" });
    words(s, 7.95, 2.25, 4.5, 0.5, "중계 서버 (FastAPI WebSocket)", [], { ts: 13 });
    const fn = [
      ["TbQrcode", "인증 · 짝 맺기", "QR 등록 코드 → 앱 토큰 · 기기 키"],
      ["TbSteeringWheel", "차 상태 → 대화 상황", "주행 중 2문장 · 주차 중 3문장"],
      ["TbMessageDots", "대화 진행", "표정 먼저 → 문장마다 목소리"],
      ["TbArrowsExchange", "메시지 전달", "차량 앱 ↔ 스노봇 로봇"],
    ];
    for (let i = 0; i < fn.length; i++) {
      const cx = 7.4 + (i % 2) * 2.6, cy = 2.9 + Math.floor(i / 2) * 0.88;
      s.addShape(pres.shapes.OVAL, { x: cx, y: cy + 0.06, w: 0.5, h: 0.5, fill: { color: "E7F6EC" }, line: { type: "none" }, objectName: "icon circle" });
      s.addImage({ data: await T(fn[i][0], ZONE.cloud.ink), x: cx + 0.1, y: cy + 0.16, w: 0.3, h: 0.3, altText: fn[i][1] });
      words(s, cx + 0.62, cy, 1.95, 0.65, fn[i][1], [fn[i][2]], { ts: 11, ds: 9 });
    }
    // 보관하는 것
    card(s, 7.22, 4.95, 5.4, 1.65, { fill: "F7FBF8", line: "B9DCC7" });
    words(s, 7.4, 5.0, 4, 0.35, "보관하는 것 · 보관하지 않는 것", [], { ts: 12 });
    const keep = [
      ["TbDatabase", "기기 DB (SQLite)", "키 · 토큰은 해시만"],
      ["TbMessages", "대화 기억", "최근 8번 · 메모리에만"],
      ["TbKey", "AI API 키", "서버 비밀 파일에만"],
      ["TbMicrophoneOff", "녹음 · 말 내용", "저장 안 함 · 기록에도 없음"],
    ];
    for (let i = 0; i < keep.length; i++) {
      const cx = 7.4 + (i % 2) * 2.6, cy = 5.4 + Math.floor(i / 2) * 0.58;
      s.addShape(pres.shapes.OVAL, { x: cx, y: cy + 0.04, w: 0.44, h: 0.44, fill: { color: i === 3 ? "FDF1E8" : "E7F6EC" }, line: { type: "none" }, objectName: "icon circle" });
      s.addImage({ data: await T(keep[i][0], i === 3 ? ZONE.robot.ink : ZONE.cloud.ink), x: cx + 0.09, y: cy + 0.13, w: 0.26, h: 0.26, altText: keep[i][1] });
      words(s, cx + 0.55, cy, 2.0, 0.52, keep[i][1], [keep[i][2]], { ts: 10, ds: 8.5 });
    }
    // 외부 AI 로
    arrow(s, 9.9, 4.77, 9.9, 4.93, "now", { width: 1.5 });
    s.addImage({ data: I.anthropic, x: 0.62, y: 5.25, w: 0.4, h: 0.4, altText: "Anthropic" });
    s.addImage({ data: I.wave, x: 1.08, y: 5.22, w: 0.46, h: 0.46, altText: "ElevenLabs" });
    words(s, 0.5, 5.75, 3.4, 0.8, "외부 AI 는 서버가 HTTPS 로 부른다", ["키는 서버에만 · ElevenLabs 키는 최소 권한"], { ts: 11, ds: 9.5, valign: "top" });
    arrow(s, 7.2, 4.5, 3.3, 5.45, "now", { width: 1.75, both: true });
    tag(s, "중계 서버가 부름", 4.55, 5.08, 1.4, 0.26, { fs: 9.5, color: "1E6B45" });
    s.addNotes("밖에서 들어오는 문은 Caddy 의 443 하나입니다. Caddy 가 암호화를 풀어 안쪽 중계 서버로만 넘깁니다. 중계 서버는 짝 맺기, 차 상태를 대화 상황으로 바꾸기, 대화 진행, 앱과 로봇 사이 메시지 전달을 합니다. 기기 키와 토큰은 해시만, 대화는 최근 8번만 메모리에 두고, 녹음과 말 내용은 저장하지 않습니다.");
  }

  // ===== 4. 말 한마디가 지나가는 길 =====
  {
    const s = pres.addSlide({ masterName: "CONTENT" });
    s.addText("말 한마디가 지나가는 길", { placeholder: "title" });
    s.addText("모든 단계를 조각 단위로 흘려 보낸다 — 다음 단계가 앞 단계 끝을 기다리지 않는다", { placeholder: "body" });
    const rows = [
      ["듣기", [
        ["TbHandFinger", "운전자", "말하기 버튼", "누르고 말한다"],
        ["TbMicrophone", "앱", "0.1초씩 바로 보냄", "마이크 16kHz → 서버"],
        ["TbTextRecognition", "AI", "듣는 동안 받아 적기", "Scribe v2 Realtime"],
        ["TbTextCaption", "앱", "화면에 글자", "받아 적는 중인 글자 (heard)"],
        ["TbHandStop", "서버", "버튼 뗌 = 말 끝", "확정 글자 0.2~0.35초"],
      ]],
      ["대답하기", [
        ["TbSteeringWheel", "서버", "상황 붙이기", "차 상태 · 먼저 한 말 · 최근 대화 8번"],
        ["TbSparkles", "AI", "표정 태그 먼저", "Claude 대답 맨 앞 [표정]"],
        ["TbMoodHappy", "앱", "표정 먼저", "태그 오는 즉시 reply_start"],
        ["TbWaveSine", "AI", "첫 문장 목소리", "다 쓴 첫 문장 → ElevenLabs"],
        ["TbVolume", "앱", "차 스피커 첫 소리", "다음 문장도 같은 길로"],
      ]],
    ];
    const W = 2.05, G = 0.28, X0 = 1.35;
    for (let r = 0; r < rows.length; r++) {
      const [name, steps] = rows[r];
      const y = 1.75 + r * 2.6;
      s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.5, y, w: 0.65, h: 2.2, rectRadius: 0.1, fill: { color: r ? "F1F0F6" : "EAF1F8" }, line: { type: "none" }, objectName: "row " + name });
      s.addText(name, { x: 0.5, y, w: 0.65, h: 2.2, margin: 0, fontSize: 14, bold: true, color: r ? ZONE.ai.ink : ZONE.car.ink, align: "center", valign: "middle", vert: "eaVert", isTextBox: true });
      for (let i = 0; i < steps.length; i++) {
        const [ic, who, t1, t2] = steps[i];
        const x = X0 + i * (W + G);
        card(s, x, y, W, 2.2, { line: "DDE3EA" });
        s.addShape(pres.shapes.OVAL, { x: x + 0.15, y: y + 0.15, w: 0.34, h: 0.34, fill: { color: HEX.dk1 }, line: { type: "none" }, objectName: "step" });
        s.addText(String(r * 5 + i + 1), { x: x + 0.15, y: y + 0.15, w: 0.34, h: 0.34, margin: 0, fontSize: 11, bold: true, color: "FFFFFF", align: "center", valign: "middle", isTextBox: true });
        chip(s, who, x + W - 0.15 - (0.16 + who.length * 0.13), y + 0.19, WHO[who]);
        s.addShape(pres.shapes.OVAL, { x: x + W / 2 - 0.36, y: y + 0.45, w: 0.72, h: 0.72, fill: { color: WHO[who] + "" , transparency: 85 }, line: { type: "none" }, objectName: "icon circle" });
        s.addImage({ data: await T(ic, WHO[who]), x: x + W / 2 - 0.22, y: y + 0.59, w: 0.44, h: 0.44, altText: t1 });
        words(s, x + 0.12, y + 1.25, W - 0.24, 0.9, t1, [t2], { ts: 12.5, ds: 9.5, align: "center", valign: "top" });
        if (i < steps.length - 1) arrow(s, x + W + 0.03, y + 1.1, x + W + G - 0.03, y + 1.1, "now", { width: 1.75 });
      }
    }
    // 줄 사이: 확정 → 상황 붙이기
    arrow(s, X0 + 4 * (W + G) + W / 2, 3.97, X0 + 4 * (W + G) + W / 2, 4.12, "now", { width: 1.5 });
    s.addShape(pres.shapes.LINE, { x: X0 + W / 2, y: 4.12, w: 4 * (W + G), h: 0, line: { color: ST.now.color, width: 1.5 }, objectName: "wrap" });
    arrow(s, X0 + W / 2, 4.12, X0 + W / 2, 4.33, "now", { width: 1.5 });
    tag(s, "주행 중이면 2문장(30자 안팎)으로 줄인다", X0 + W / 2 + 0.15, 3.98, 4.0, 0.26, { fs: 9.5, align: "left", fill: "FFFFFF", color: "1E6B45" });
    s.addNotes("위 줄이 듣기, 아래 줄이 대답하기입니다. 말하는 동안 소리를 0.1초씩 보내고 음성 인식도 듣는 동안 받아 적어서, 버튼을 떼면 0.2~0.35초 안에 글자가 확정됩니다. 서버는 차 상태와 최근 대화를 붙여 Claude 에 보냅니다. 대답 맨 앞의 표정 태그가 오는 즉시 표정부터 보내고, 첫 문장을 다 쓰면 그 문장의 목소리를 조각으로 흘려 보냅니다.");
  }

  // ===== 5. 걸린 시간과 대신하는 길 =====
  {
    const s = pres.addSlide({ masterName: "CONTENT" });
    s.addText("말 끝에서 첫 소리까지 보통 1.5초", { placeholder: "title" });
    s.addText("2026-10-28 맥 → 클라우드 서버, 시험 도구로 17번 잰 값 (기록: docs/12-latency-measurements.md)", { placeholder: "body" });
    // 큰 숫자 두 개
    const big = [["0.2~0.35초", "말 끝 → 글자 확정 (13번)", HEX.accent2], ["1.5초", "말 끝 → 첫 소리 중간값 (17번, 1.3~2.5초)", HEX.accent1]];
    big.forEach(([n, l, c], i) => {
      const y = 1.75 + i * 1.6;
      s.addText(n, { x: 0.5, y, w: 4.2, h: 0.95, margin: 0, fontSize: 54, bold: true, color: c, valign: "middle", isTextBox: true });
      s.addText(l, { x: 0.5, y: y + 0.95, w: 4.2, h: 0.4, margin: 0, fontSize: 15, color: "4A5561", valign: "top", isTextBox: true });
    });
    // 17번 잰 값을 점으로 (docs/12-latency-measurements.md)
    const RUNS = [1455, 1307, 1336, 1421, 1368, 1399, 1310, 1362, 2537, 1696, 1676, 1997, 2142, 1424, 1491, 2297, 2244];
    const X = 5.2, Wd = 7.3, Y = 2.2, sec = Wd / 3;
    s.addShape(pres.shapes.LINE, { x: X, y: Y + 0.75, w: Wd, h: 0, line: { color: "9AA5B1", width: 1 }, objectName: "axis" });
    for (let t = 0; t <= 3; t += 0.5) {
      s.addShape(pres.shapes.LINE, { x: X + t * sec, y: Y + 0.75, w: 0, h: 0.1, line: { color: "9AA5B1", width: 1 }, objectName: "tick" });
      tag(s, t.toFixed(1) + "초", X + t * sec - 0.35, Y + 0.88, 0.7, 0.26, { fs: 9.5, color: "6B7785" });
    }
    s.addShape(pres.shapes.RECTANGLE, { x: X, y: Y + 0.3, w: 0.29 * sec, h: 0.3, fill: { color: HEX.accent2, transparency: 20 }, line: { type: "none" }, objectName: "to text" });
    tag(s, "글자 확정", X + 0.29 * sec + 0.05, Y + 0.3, 0.9, 0.3, { fs: 9.5, align: "left", color: HEX.accent2, bold: true });
    RUNS.slice().sort((a, b) => a - b).forEach((ms, i) => {
      s.addShape(pres.shapes.OVAL, { x: X + (ms / 1000) * sec - 0.07, y: Y + 0.2 + (i % 3) * 0.17, w: 0.14, h: 0.14, fill: { color: HEX.accent1 }, line: { color: "FFFFFF", width: 0.5 }, objectName: "run " + ms });
    });
    s.addShape(pres.shapes.LINE, { x: X + 1.455 * sec, y: Y - 0.05, w: 0, h: 0.8, line: { color: "B5561D", width: 2 }, objectName: "median" });
    tag(s, "중간값 1.46초", X + 1.455 * sec - 0.7, Y - 0.38, 1.4, 0.3, { fs: 10.5, bold: true, color: "B5561D" });
    s.addShape(pres.shapes.LINE, { x: X + 3 * sec, y: Y - 0.05, w: 0, h: 0.8, line: { color: "8A97A6", width: 1.5, dashType: "dash" }, objectName: "target" });
    tag(s, "목표 3초", X + 3 * sec - 0.9, Y - 0.38, 0.9, 0.3, { fs: 10.5, align: "right", color: "6B7785" });
    tag(s, "말 끝", X - 0.2, Y - 0.38, 0.8, 0.3, { fs: 10.5, align: "left", bold: true });
    // 왜 빠른가
    words(s, X, 3.45, 3.6, 0.4, "기다림을 줄인 방법", [], { ts: 13 });
    const why = [["TbMicrophone", "듣는 동안 받아 적는다"], ["TbSparkles", "대답을 문장 단위로 받는다"], ["TbVolume", "첫 문장부터 목소리를 만든다"]];
    for (let i = 0; i < why.length; i++) {
      const y = 3.95 + i * 0.5;
      s.addImage({ data: await T(why[i][0], HEX.accent2), x: X, y, w: 0.34, h: 0.34, altText: why[i][1] });
      tag(s, why[i][1], X + 0.48, y, 3.2, 0.34, { fs: 12, align: "left", color: HEX.dk1 });
    }
    // 대신하는 길
    card(s, 9.0, 3.45, 3.5, 1.9, { fill: "FDF6F0", line: "F2C9A8" });
    s.addImage({ data: await T("TbMoodNervous", ZONE.robot.ink), x: 9.18, y: 3.6, w: 0.42, h: 0.42, altText: "걱정 표정" });
    words(s, 9.7, 3.58, 2.7, 0.5, "실패해도 말은 한다", [], { ts: 12.5 });
    words(s, 9.18, 4.08, 3.2, 1.1, "", ["대답 AI 가 실패하거나 못 들었으면", "정해 둔 말 + 걱정 표정으로 대신한다"], { ds: 10.5, valign: "top" });
    card(s, 0.5, 5.6, 12.0, 1.0, { fill: "F7F9FB", status: "pt" });
    s.addImage({ data: I.esp, x: 0.7, y: 5.82, w: 0.55, h: 0.55, altText: "스노봇 로봇" });
    words(s, 1.45, 5.65, 10.9, 0.9, "실물 스노봇이 연결돼 있으면", ["같은 표정 명령과 목소리가 로봇으로도 간다 (서버 쪽은 가상 스노봇으로 시험 완료, 실물은 11/7 부품 도착 후)"], { ts: 12.5, ds: 10.5 });
    s.addNotes("말이 끝난 뒤 글자가 확정되기까지 0.2~0.35초, 첫 소리가 나기까지 17번 잰 값의 중간이 1.46초였습니다. 느린 때(2초 넘게)는 Claude 첫 응답이 늦은 경우입니다. 이 값은 맥에서 시험 도구로 잰 것이고, 에뮬레이터 앱으로는 다시 잴 예정입니다. 듣는 동안 받아 적고, 대답을 문장 단위로 받고, 첫 문장부터 목소리를 만들어서 기다림을 줄였습니다. AI 가 실패하면 정해 둔 말과 걱정 표정으로 대신합니다.");
  }

  // ===== 6. 차 상태가 스노봇을 바꾸는 길 =====
  {
    const s = pres.addSlide({ masterName: "CONTENT" });
    s.addText("차 상태가 스노봇을 바꾸는 길", { placeholder: "title" });
    s.addText("차 상태 세 가지(SDK 2개 + 앱이 잰 운전 시간) → ① 앱이 먼저 말 걸기 ② 서버가 대답 길이 · 내용 바꾸기", { placeholder: "body" });
    zone(s, 0.5, 1.6, 3.2, 4.4, ZONE.car, "차 상태 세 가지", { icon: I.vehicle });
    const v = [["TbManualGearbox", "기어 P · R · N · D", "SDK Gear · 바뀔 때마다"], ["TbClockHour4", "연속 운전 시간", "앱이 잼 (P 에서 출발한 때부터)"], ["TbAlertTriangle", "주의 경고등", "SDK Safety · 5초마다 조회"]];
    for (let i = 0; i < v.length; i++) {
      const y = 2.15 + i * 1.22;
      card(s, 0.68, y, 2.84, 1.05);
      tile(s, 0.8, y + 0.17, 0.7, await T(v[i][0], ZONE.car.ink), v[i][1]);
      words(s, 1.62, y + 0.08, 1.85, 0.9, v[i][1], [v[i][2]], { ts: 11.5, ds: 9.5 });
    }
    tag(s, "Virtual Console 에 경고등이 없어 시험용 명령으로 확인", 0.5, 6.1, 3.2, 0.5, { fs: 9, color: "6B7785", align: "left" });
    arrow(s, 3.72, 3.8, 4.15, 3.8, "now", { width: 2 });
    // CarState
    card(s, 4.17, 2.95, 2.0, 1.7, { line: HEX.accent2 });
    s.addImage({ data: I.snowman, x: 4.32, y: 3.08, w: 0.42, h: 0.42, altText: "차량 앱" });
    words(s, 4.8, 3.05, 1.3, 0.5, "차 상태 정리", [], { ts: 12 });
    words(s, 4.32, 3.55, 1.8, 1.05, "", ["기어 D · 운전 41분", "경고 꺼짐", "같은 값이면 다시 안 알림"], { ds: 9.5, valign: "top" });
    // 갈래
    arrow(s, 6.19, 3.5, 6.55, 2.55, "now", { width: 2 });
    arrow(s, 6.19, 4.15, 6.55, 5.25, "now", { width: 2 });
    // ① 앱 안 규칙
    zone(s, 6.6, 1.6, 6.25, 2.85, ZONE.car, "① 앱이 먼저 말 건다 — 앱 안 규칙 (서버 없어도 화면에 뜸)");
    const rule = [
      ["TbMoodHappy", "출발 (P → 주행)", "반가운 표정 + 인사"],
      ["TbParking", "도착 (주행 → P)", "수고 인사 · 1시간 넘게 달렸으면 푹 쉬라고"],
      ["TbAlertTriangle", "주의 경고등 켜짐", "걱정 표정 + 쉼터 권유 (10분에 한 번)"],
      ["TbZzz", "운전 120분째, 그 뒤 60분마다", "졸린 표정 + \"벌써 2시간째 운전 중이에요. 쉼터에서…\""],
    ];
    for (let i = 0; i < rule.length; i++) {
      const y = 2.1 + i * 0.57;
      s.addShape(pres.shapes.OVAL, { x: 6.8, y, w: 0.44, h: 0.44, fill: { color: "FFFFFF" }, line: { type: "none" }, objectName: "icon circle" });
      s.addImage({ data: await T(rule[i][0], ZONE.car.ink), x: 6.88, y: y + 0.08, w: 0.28, h: 0.28, altText: rule[i][1] });
      tag(s, rule[i][1], 7.35, y, 2.15, 0.44, { fs: 10.5, bold: true, align: "left", color: HEX.dk1 });
      tag(s, rule[i][2], 9.5, y, 3.3, 0.44, { fs: 9.5, align: "left" });
    }
    // ② 서버
    zone(s, 6.6, 4.65, 6.25, 2.2, ZONE.cloud, "② 서버가 대답을 바꾼다 — 바뀔 때마다 car 메시지로 보냄");
    const srv = [
      ["TbRoad", "주행 중", "2문장, 30자 안팎 · 주차 중엔 3문장"],
      ["TbCoffee", "경고 켜짐 · 2시간 넘음", "농담을 부탁해도 휴식 권유가 먼저"],
      ["TbRefresh", "스노봇이 먼저 한 말", "10분 동안 기억 → \"아까 쉬자고 했죠?\"로 이어 감"],
    ];
    for (let i = 0; i < srv.length; i++) {
      const y = 5.12 + i * 0.55;
      s.addShape(pres.shapes.OVAL, { x: 6.8, y, w: 0.44, h: 0.44, fill: { color: "FFFFFF" }, line: { type: "none" }, objectName: "icon circle" });
      s.addImage({ data: await T(srv[i][0], ZONE.cloud.ink), x: 6.88, y: y + 0.08, w: 0.28, h: 0.28, altText: srv[i][1] });
      tag(s, srv[i][1], 7.35, y, 2.15, 0.44, { fs: 10.5, bold: true, align: "left", color: HEX.dk1 });
      tag(s, srv[i][2], 9.5, y, 3.3, 0.44, { fs: 9.5, align: "left" });
    }
    s.addNotes("앱은 Vehicle SDK 로 기어와 주의 경고등 두 가지만 조회하고, 연속 운전 시간은 기어가 P 에서 바뀐 때부터 앱이 직접 잽니다. 값이 바뀌면 두 갈래로 씁니다. 첫째, 출발·도착·경고·2시간 휴식은 앱이 직접 정해 서버가 없어도 화면에 뜹니다. 둘째, 서버가 대화 상황에 차 상태를 붙여 주행 중이면 2문장으로 줄이고, 경고가 켜졌으면 농담보다 휴식 권유를 먼저 합니다.");
  }

  // ===== 7. 안전 장치 =====
  {
    const s = pres.addSlide({ masterName: "CONTENT" });
    s.addText("구조로 지키는 세 가지", { placeholder: "title" });
    s.addText("규칙을 사람이 기억하지 않아도 되도록 구조에 넣었다", { placeholder: "body" });
    const three = [
      ["TbKey", "AI 는 서버에서만", ["앱 · 로봇에는 API 키가 없다", "AI 를 바꿔도 앱은 그대로", "ElevenLabs 키는 최소 권한"], ZONE.cloud],
      ["TbCloudOff", "인터넷이 끊겨도", ["출발 · 경고 · 휴식 알림은 앱 안 규칙", "화면 속 스노봇 표정 · 말풍선은 뜬다", "목소리만 서버가 만든다"], ZONE.car],
      ["TbShieldLock", "개인정보", ["녹음은 저장하지 않는다", "서버 기록엔 말 내용 없음 (길이 · 크기만)", "키 · 토큰은 해시만 보관"], ZONE.robot],
    ];
    for (let i = 0; i < three.length; i++) {
      const [ic, t, lines, z] = three[i];
      const x = 0.5 + i * 4.15;
      card(s, x, 1.75, 3.9, 4.0, { fill: z.fill, line: z.fill, r: 0.16 });
      s.addShape(pres.shapes.OVAL, { x: x + 0.35, y: 2.1, w: 1.1, h: 1.1, fill: { color: "FFFFFF" }, line: { type: "none" }, objectName: "icon circle" });
      s.addImage({ data: await T(ic, z.ink), x: x + 0.6, y: 2.35, w: 0.6, h: 0.6, altText: t });
      s.addText(t, { x: x + 0.35, y: 3.45, w: 3.3, h: 0.6, margin: 0, fontSize: 22, bold: true, color: z.ink, valign: "middle", isTextBox: true });
      s.addText(lines.map((l, j) => ({ text: l, options: { bullet: true, breakLine: j < lines.length - 1 } })), {
        x: x + 0.35, y: 4.15, w: 3.35, h: 1.5, margin: 0, fontSize: 13.5, color: HEX.dk1, valign: "top", paraSpaceAfter: 6, isTextBox: true,
      });
    }
    card(s, 0.5, 6.0, 12.2, 0.7, { fill: "F7F9FB" });
    s.addImage({ data: await T("TbEyeOff", "6B7785"), x: 0.7, y: 6.15, w: 0.4, h: 0.4, altText: "공개하지 않는 것" });
    tag(s, "그림 · 문서 · 공개 저장소에는 서버 주소 · 이름 · 계정을 넣지 않는다 (공모전 개인정보 안내)", 1.25, 6.05, 11.3, 0.6, { fs: 13, align: "left", color: HEX.dk1 });
    s.addNotes("세 가지를 구조로 지킵니다. AI 키는 서버에만 있어서 앱이나 로봇을 뜯어도 키가 나오지 않습니다. 인터넷이 끊겨도 출발·경고·휴식 알림은 앱 안 규칙으로 화면에 뜹니다. 녹음은 저장하지 않고 서버 기록에도 말 내용 대신 길이와 소리 크기만 남깁니다.");
  }

  // ===== 8. 진행 상태와 다음 =====
  {
    const s = pres.addSlide({ masterName: "CONTENT" });
    s.addText("지금 되는 것 · PT(12/3)까지 · 그 뒤", { placeholder: "title" });
    s.addText("구조도의 선 모양과 같은 약속 — 실선은 지금 코드에서 실제로 동작하는 것만", { placeholder: "body" });
    const cols = [
      ["now", "지금 동작 (10/28)", [
        [I.snowman, "차량 앱 + Vehicle SDK(기어 · 경고등)"], [I.server, "NCP 중계 서버 · 짝 맺기"], [await T("TbSparkles", ZONE.ai.ink), "음성 인식 · 대답 · 목소리"], [await T("TbMoodHappy", ZONE.car.ink), "앱 안 규칙 4가지"],
      ]],
      ["pt", "PT(12/3)까지", [
        [I.esp, "스노봇 로봇 조립 · 서버 연결 (부품 11/7)"], [I.gleo, "Gleo Interface — snowbot_status"], [await T("TbMapPin", ZONE.robot.ink), "NaviHelper — 쉼터 경유지 · 회전 안내 중 말 멈춤"], [I.climate, "HVAC — 내기 순환 + CO₂ 높음 → 환기 권유"], [await T("TbLayoutBottombar", ZONE.robot.ink), "CarUxRestrictions — 주행 중 화면 규제"],
      ]],
      ["later", "이후", [
        [await T("TbBluetooth", "6B7785"), "블루투스 직접 연결 — 차량 앱이 모듈 허브"],
      ]],
    ];
    for (let c = 0; c < cols.length; c++) {
      const [st, head, items] = cols[c];
      const x = 0.5 + c * 4.15;
      s.addShape(pres.shapes.LINE, { x, y: 1.8, w: 3.9, h: 0, line: { color: ST[st].color, width: 4, dashType: ST[st].dashType }, objectName: "status " + st });
      s.addText(head, { x, y: 1.92, w: 3.9, h: 0.45, margin: 0, fontSize: 16, bold: true, color: ST[st].color === "8A97A6" ? "6B7785" : ST[st].color, valign: "middle", isTextBox: true });
      for (let i = 0; i < items.length; i++) {
        const y = 2.55 + i * 0.82;
        card(s, x, y, 3.9, 0.7, { status: st === "now" ? undefined : st });
        s.addImage({ data: items[i][0], x: x + 0.14, y: y + 0.14, w: 0.42, h: 0.42, altText: items[i][1] });
        tag(s, items[i][1], x + 0.7, y + 0.05, 3.1, 0.6, { fs: 11, align: "left", color: HEX.dk1 });
      }
    }
    s.addText("아이콘 출처: NAVER Cloud Platform 서비스 아이콘(2025-02-17판) · Pleos Connect UI Design Guide(Iconography) · Tabler Icons · Simple Icons", {
      x: 0.5, y: 6.62, w: 12.3, h: 0.3, margin: 0, fontSize: 9, color: HEX.accent6, isTextBox: true,
    });
    s.addNotes("지금은 차량 앱, 중계 서버, 세 가지 AI, 앱 안 규칙이 실제로 동작합니다. PT 까지는 실물 로봇 연결, Gleo Interface, NaviHelper, HVAC 와 CO₂ 를 묶은 환기 권유, 주행 규제 대응을 더합니다. 블루투스로 앱이 모듈 허브가 되는 구조는 그 뒤 목표입니다.");
  }

  await pres.writeFile({ fileName: OUT });
  await applyTheme(OUT, THEME);
  console.log("wrote", OUT);
})();
