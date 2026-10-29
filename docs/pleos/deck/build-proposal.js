// 스노봇 기획서 검토용 PPT — docs/pleos/01-proposal.md (2026-10-28 개정·검증 반영) 내용
// 실행: pptxgenjs · react · react-dom · react-icons · sharp 가 있는 곳을 NODE_PATH 로 주고
//   PPTX_SKILL=<pptx 스킬 폴더> node build-proposal.js ../01-proposal.pptx
const path = require("path");
const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const md = require("react-icons/md");
const { applyTheme } = require(process.env.PPTX_SKILL + "/scripts/apply_theme.js");

const IMG = path.join(__dirname, "img");
const ARCH = path.join(__dirname, "..", "..", "assets", "architecture");
const OUT = process.argv[2];

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

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.33 x 7.5
pres.title = "스노봇 기획서 검토본";
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
const C = pres.SchemeColor;

async function icon(name, hex, px = 256) {
  const svg = ReactDOMServer.renderToStaticMarkup(React.createElement(md[name], { color: "#" + hex, size: px }));
  const buf = await sharp(Buffer.from(svg)).resize(px, px).png().toBuffer();
  return "image/png;base64," + buf.toString("base64");
}

// ---------- 레이아웃 ----------
const FOOT = { x: 0.6, y: 7.0, w: 9, h: 0.3, fontSize: 10, color: C.accent6 };
pres.defineSlideMaster({
  title: "TITLE_DARK",
  background: { color: C.text2 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: 0.7, y: 2.2, w: 6.4, h: 1.2, fontSize: 54, bold: true, color: C.background1, valign: "middle", align: "left", margin: 0 }, text: "" } },
    { placeholder: { options: { name: "body", type: "body", x: 0.7, y: 3.45, w: 6.4, h: 0.8, fontSize: 28, color: C.accent5, valign: "top", align: "left", margin: 0 }, text: "" } },
  ],
});
pres.defineSlideMaster({
  title: "CONTENT",
  background: { color: C.background1 },
  margin: [0.5, 0.6, 0.6, 0.6],
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: 0.6, y: 0.35, w: 12.1, h: 0.85, fontSize: 32, bold: true, color: C.text1, valign: "middle", align: "left", margin: 0 }, text: "" } },
    { text: { text: "스노봇 SnowBot · 기획서 검토본 (2026-10-28)", options: FOOT } },
  ],
  slideNumber: { x: 12.2, y: 7.0, w: 0.5, h: 0.3, fontSize: 10, color: C.accent6, align: "right" },
});
pres.defineSlideMaster({
  title: "CONTENT_DARK",
  background: { color: C.text2 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: 0.6, y: 0.35, w: 12.1, h: 0.85, fontSize: 32, bold: true, color: C.background1, valign: "middle", align: "left", margin: 0 }, text: "" } },
    { text: { text: "스노봇 SnowBot · 기획서 검토본 (2026-10-28)", options: { ...FOOT, color: C.accent6 } } },
  ],
  slideNumber: { x: 12.2, y: 7.0, w: 0.5, h: 0.3, fontSize: 10, color: C.accent6, align: "right" },
});

// ---------- 조각 ----------
function card(s, x, y, w, h, opts = {}) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x, y, w, h, rectRadius: 0.12, fill: { color: opts.fill || C.background2, transparency: opts.transparency || 0 },
    line: { type: "none" }, objectName: opts.name || "card",
  });
}
async function badge(s, x, y, d, iconName, bg, fg = HEX.lt1) {
  s.addShape(pres.shapes.OVAL, { x, y, w: d, h: d, fill: { color: bg }, line: { type: "none" }, objectName: "icon circle" });
  const pad = d * 0.22;
  s.addImage({ data: await icon(iconName, fg), x: x + pad, y: y + pad, w: d - 2 * pad, h: d - 2 * pad, altText: iconName });
}
function numberBadge(s, x, y, d, n, bg = C.accent1) {
  s.addShape(pres.shapes.OVAL, { x, y, w: d, h: d, fill: { color: bg }, line: { type: "none" }, objectName: "step " + n });
  s.addText(String(n), { x, y, w: d, h: d, fontSize: 16, bold: true, color: C.background1, align: "center", valign: "middle", margin: 0, isTextBox: true });
}
function text(s, t, o) { s.addText(t, { margin: 0, isTextBox: true, valign: "top", color: C.text1, fontSize: 14, ...o }); }
function statusTag(s, x, y, label, kind) {
  const fill = { done: C.accent4, pt: C.accent1, later: C.accent6 }[kind];
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: 1.45, h: 0.36, rectRadius: 0.18, fill: { color: fill }, line: { type: "none" }, objectName: "status " + label });
  s.addText(label, { x, y, w: 1.45, h: 0.36, fontSize: 11, bold: true, color: C.background1, align: "center", valign: "middle", margin: 0, isTextBox: true });
}

async function build() {
  // ===== 1. 표지 =====
  pres.addSection({ title: "소개" });
  let s = pres.addSlide({ masterName: "TITLE_DARK", sectionTitle: "소개" });
  text(s, "Pleos 차량용 앱 개발 공모전 · 일반 부문", { x: 0.7, y: 1.6, w: 6.4, h: 0.4, fontSize: 16, color: C.accent1, bold: true });
  s.addText("스노봇 SnowBot", { placeholder: "title" });
  s.addText("도로를 보는 눈사람 동승자", { placeholder: "body" });
  text(s, "기획서 검토본 · 2026-10-28", { x: 0.7, y: 4.5, w: 6.4, h: 0.4, fontSize: 14, color: C.accent6 });
  s.addImage({ path: path.join(IMG, "hero.jpg"), x: 7.4, y: 1.0, w: 5.4, h: 4.94, altText: "스노봇 기준 디자인 — 당근 코와 주황 목도리를 한 눈사람 로봇" });
  text(s, "기준 디자인 그림 (완성품 목표)", { x: 7.4, y: 6.05, w: 5.4, h: 0.3, fontSize: 10, color: C.accent6, align: "right" });

  // ===== 2. 한 줄 소개 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "소개" });
  s.addText("한 줄로 말하면", { placeholder: "title" });
  text(s, [
    { text: "차의 신호를 읽고 ", options: {} },
    { text: "말할 때와 길이를 스스로 고르는", options: { color: C.accent1, bold: true } },
    { text: " 눈사람 동승자", options: {} },
  ], { x: 0.6, y: 1.4, w: 12.1, h: 0.6, fontSize: 24, bold: true });
  const pillars = [
    ["MdDirectionsCar", "차 신호를 읽는다", "기어(P·D)·운전자 주의 경고등은 Pleos Vehicle SDK, 연속 운전 시간은 기어로 앱이 잰다"],
    ["MdRecordVoiceOver", "말할 때와 길이를 고른다", "주행 중엔 짧게, 경고가 켜지면 쉬자는 말 먼저, 주차 중엔 수다"],
    ["MdFace", "표정 + 목소리, 그리고 집까지", "화면 대신 표정과 짧은 말. 차에서 내리면 책상 위 로봇으로 이어짐"],
  ];
  for (let i = 0; i < 3; i++) {
    const y = 2.35 + i * 1.45;
    card(s, 0.6, y, 7.3, 1.2);
    await badge(s, 0.85, y + 0.25, 0.7, pillars[i][0], HEX.accent1);
    text(s, pillars[i][1], { x: 1.8, y: y + 0.18, w: 5.9, h: 0.4, fontSize: 18, bold: true });
    text(s, pillars[i][2], { x: 1.8, y: y + 0.6, w: 5.9, h: 0.5, fontSize: 14, color: C.text2 });
  }
  s.addImage({ path: path.join(IMG, "car-depart-full.jpg"), x: 8.3, y: 2.35, w: 4.4, h: 3.16, altText: "에뮬레이터 앱 화면 — 기어 D 에서 스노봇이 출발 인사" });
  text(s, "에뮬레이터 실제 화면: 기어 D → \"출발해요! 오늘도 안전하게 가요.\"", { x: 8.3, y: 5.6, w: 4.4, h: 0.6, fontSize: 11, color: C.accent6 });

  // ===== 3. 배경 =====
  pres.addSection({ title: "배경" });
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "배경" });
  s.addText("운전 중 불편한 순간들", { placeholder: "title" });
  const pains = [
    ["MdBedtime", "졸음", "고속도로 사망사고의 절반 이상이 졸음·전방주시 태만. 졸음 경고는 고급 트림 카메라에 기댄다"],
    ["MdTouchApp", "화면 조작", "화면을 보고 누르는 것 자체가 주의 분산. Pleos 도 주행 중 키보드·영상을 막는다"],
    ["MdRecordVoiceOver", "음성도 부담", "차량 음성 시스템 인지 부담 3.34/5, 라디오는 1.2 (AAA 재단, 2015). 긴 안내는 기억을 방해"],
    ["MdLocalParking", "지하주차장", "\"몇 층에 세웠지?\" 반층·게이트별 층 표기 때문에 위치만으로는 잘 안 된다"],
    ["MdLogout", "내리면 끝", "차 안 AI 는 하차와 함께 끊긴다. 집에서 이어 갈 곳이 없다"],
  ];
  const gx = [0.6, 4.75, 8.9], gy = [1.45, 4.15];
  for (let i = 0; i < 5; i++) {
    const x = gx[i % 3], y = gy[Math.floor(i / 3)];
    card(s, x, y, 3.85, 2.45);
    await badge(s, x + 0.25, y + 0.25, 0.65, pains[i][0], HEX.accent2);
    text(s, pains[i][1], { x: x + 1.05, y: y + 0.32, w: 2.6, h: 0.5, fontSize: 18, bold: true });
    text(s, pains[i][2], { x: x + 0.25, y: y + 1.05, w: 3.35, h: 1.3, fontSize: 14, color: C.text2 });
  }
  card(s, 8.9, 4.15, 3.85, 2.45, { fill: C.accent1 });
  text(s, "그런데", { x: 9.15, y: 4.4, w: 3.35, h: 0.4, fontSize: 14, color: C.background1, bold: true });
  text(s, "옆자리 동승자와의 대화는 전화보다 덜 위험하다", { x: 9.15, y: 4.85, w: 3.35, h: 1.2, fontSize: 20, bold: true, color: C.background1 });
  text(s, "→ 다음 장", { x: 9.15, y: 6.1, w: 3.35, h: 0.35, fontSize: 12, color: C.background1 });

  // ===== 4. 핵심 통찰 =====
  s = pres.addSlide({ masterName: "CONTENT_DARK", sectionTitle: "배경" });
  s.addText("동승자는 도로를 보고 말을 멈춘다", { placeholder: "title" });
  text(s, "동승자 대화가 전화보다 안전한 이유는 동승자가 교통 상황을 보고 말을 멈추거나 위험을 알려 주기 때문이다 (동승자·통화 비교 연구). 스노봇은 차량 데이터로 이 일을 한다.",
    { x: 0.6, y: 1.35, w: 12.1, h: 0.9, fontSize: 16, color: C.accent5 });
  const cols = [
    ["MdChat", "글레오 AI", "묻는 말에 답하는 비서", ["차량 조작 (창문·공조·시트)", "지식 검색·추천", "무엇이든 대답"], HEX.accent2],
    ["MdFace", "스노봇", "말할 때를 고르는 동승자", ["차 상태를 보고 말할지·얼마나 말할지 정함", "졸음·공기·주차, 나에 대한 기억", "표정으로 먼저 알림"], HEX.accent1],
  ];
  for (let i = 0; i < 2; i++) {
    const x = 0.6 + i * 6.2;
    card(s, x, 2.55, 5.9, 3.55, { fill: C.text1 });
    await badge(s, x + 0.3, 2.85, 0.8, cols[i][0], cols[i][4]);
    text(s, cols[i][1], { x: x + 1.3, y: 2.85, w: 4.3, h: 0.45, fontSize: 22, bold: true, color: C.background1 });
    text(s, cols[i][2], { x: x + 1.3, y: 3.32, w: 4.3, h: 0.4, fontSize: 15, color: i ? C.accent1 : C.accent5 });
    text(s, cols[i][3].map((t, j) => ({ text: t, options: { bullet: true, breakLine: j < 2 } })),
      { x: x + 0.35, y: 4.05, w: 5.3, h: 1.8, fontSize: 15, color: C.background1, paraSpaceAfter: 6 });
  }
  text(s, "그렇다면 글레오가 있는데 왜 스노봇이 또 필요할까? → 다음 장",
    { x: 0.6, y: 6.3, w: 12.1, h: 0.45, fontSize: 15, bold: true, color: C.accent1 });

  // ===== 4-1. 왜 스노봇인가 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "배경" });
  s.addText("글레오가 있는데, 왜 스노봇인가", { placeholder: "title" });
  text(s, "무엇이든 글레오에 더할 수는 있다. 하지만 모든 차에 들어가는 글레오는 검증된 기능만 담아야 한다.",
    { x: 0.6, y: 1.35, w: 12.1, h: 0.5, fontSize: 17, color: C.text2 });
  const why = [
    ["MdPeople", "1", "역할이 다르다", "글레오는 묻는 말에 답하는 비서. 스노봇은 차 상태를 보고 말할 때를 고르는 동승자."],
    ["MdScience", "2", "글레오의 미리보기 실험실", "차에 바로 넣기엔 검증이 더 필요한 기능을 원하는 사용자에게 먼저 경험시키고, 데이터로 효과를 확인해 검증된 것만 글레오로 넘긴다."],
    ["MdSensors", "3", "차에 없는 감각을 먼저", "CO₂·카메라 같은 센서를 모듈로 먼저 달아 본다. 쓸모가 증명되면 차에 센서를 넣을 근거가 된다."],
  ];
  for (let i = 0; i < 3; i++) {
    const x = 0.6 + i * 4.1;
    card(s, x, 2.1, 3.85, 3.6, { fill: i === 1 ? C.accent1 : C.background2, transparency: i === 1 ? 85 : 0 });
    await badge(s, x + 0.3, 2.4, 0.8, why[i][0], i === 1 ? HEX.accent1 : HEX.accent2);
    text(s, why[i][2], { x: x + 0.3, y: 3.4, w: 3.3, h: 0.5, fontSize: 19, bold: true });
    text(s, why[i][3], { x: x + 0.3, y: 4.0, w: 3.3, h: 1.6, fontSize: 14, color: C.text2 });
  }
  card(s, 0.6, 5.95, 12.1, 0.8, { fill: C.text2 });
  text(s, [
    { text: "글레오와 스노봇은 서로를 채운다 — ", options: { color: C.background1 } },
    { text: "글레오는 검증된 기능을 모든 차에, 스노봇은 새 기능을 먼저 원하는 사람에게", options: { color: C.accent1 } },
  ], { x: 0.85, y: 5.95, w: 11.6, h: 0.8, fontSize: 16, bold: true, valign: "middle" });

  // ===== 4-2. 실험실 순환 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "배경" });
  s.addText("스노봇은 글레오의 미리보기 실험실", { placeholder: "title" });
  const loop = [
    [1.0, 1.55, "① 시범", "새 기능을\n스노봇에서 먼저", C.accent1],
    [4.1, 1.55, "② 데이터", "동의한 사용자의\n익명 통계", C.accent2],
    [4.1, 4.35, "③ 검증", "효과 있는 것만\n골라낸다", C.accent2],
    [1.0, 4.35, "④ 글레오 반영", "검증된 기능을\n모든 차에", C.text2],
  ];
  for (const [x, y, head, sub, col] of loop) {
    s.addShape(pres.shapes.OVAL, { x, y, w: 1.75, h: 1.75, fill: { color: col }, line: { type: "none" }, objectName: "loop " + head });
    text(s, [{ text: head, options: { bold: true, fontSize: 15, breakLine: true } }, { text: sub.replace("\\n", "\n"), options: { fontSize: 11 } }],
      { x: x + 0.05, y, w: 1.65, h: 1.75, color: C.background1, align: "center", valign: "middle" });
  }
  s.addShape(pres.shapes.RIGHT_ARROW, { x: 2.9, y: 2.2, w: 1.1, h: 0.45, fill: { color: C.accent5 }, line: { type: "none" }, objectName: "loop arrow 1" });
  s.addShape(pres.shapes.DOWN_ARROW, { x: 4.75, y: 3.4, w: 0.45, h: 0.85, fill: { color: C.accent5 }, line: { type: "none" }, objectName: "loop arrow 2" });
  s.addShape(pres.shapes.LEFT_ARROW, { x: 2.9, y: 5.0, w: 1.1, h: 0.45, fill: { color: C.accent5 }, line: { type: "none" }, objectName: "loop arrow 3" });
  s.addShape(pres.shapes.UP_ARROW, { x: 1.65, y: 3.4, w: 0.45, h: 0.85, fill: { color: C.accent5 }, line: { type: "none" }, objectName: "loop arrow 4" });
  text(s, "반복", { x: 2.6, y: 3.55, w: 1.75, h: 0.5, fontSize: 14, bold: true, color: C.accent6, align: "center", valign: "middle" });
  const DH = (t) => ({ text: t, options: { bold: true, color: C.background1, fill: { color: C.text2 } } });
  s.addTable([
    [DH("쌓는 데이터"), DH("예"), DH("글레오·차량에 주는 것")],
    ["기능 반응", "휴식 권유를 받아들였나, 꺼 버렸나", "어떤 권유가 행동을 바꾸나"],
    ["대화 호흡", "말 걸었을 때 대답했나, 무시했나", "말 거는 때·길이 기준"],
    ["추가 센서", "실내 CO₂·온도 × 운전 시간", "공기 센서·자동 환기 근거"],
    ["AI 기능", "어떤 질문을 하나, 어디서 틀리나", "글레오에 더할 기능 순위"],
  ], { x: 6.6, y: 1.55, w: 6.1, colW: [1.25, 2.55, 2.3], rowH: 0.62, fontSize: 13, color: C.text1, valign: "middle",
       border: { type: "solid", pt: 0.75, color: HEX.accent5 }, fill: { color: C.background1 }, margin: [0.05, 0.1, 0.05, 0.1] });
  card(s, 6.6, 5.0, 6.1, 1.35, { fill: C.background2 });
  await badge(s, 6.8, 5.37, 0.6, "MdLock", HEX.accent4);
  text(s, "동의한 사용자만, 개인을 알 수 없는 통계로. 목소리 원본·대화 내용은 저장하지 않는다 (지금도 서버 기록에 말 내용 없음).",
    { x: 7.6, y: 5.1, w: 4.95, h: 1.15, fontSize: 13, valign: "middle" });

  // ===== 5. 앱 + 모듈 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "배경" });
  s.addText("차가 주는 것, 모듈이 채우는 것", { placeholder: "title" });
  const have = ["기어 P·R·N·D (주행/주차)", "트립 운전 시간", "운전자 주의 경고등 (DRIVER_ATTENTION)", "내비 경로·회전 안내·경유지 (NaviHelper)"];
  const fill = ["실내 CO₂·공기질 → 모듈 센서", "졸음 '수준' 값 → 운전 시간 + 경고등으로", "대시보드 위 표정·고개 → 스노봇 로봇", "(이후) 주차 표지 읽기 → 카메라 모듈"];
  card(s, 0.6, 1.45, 5.6, 4.65);
  await badge(s, 0.9, 1.7, 0.7, "MdDirectionsCar", HEX.accent2);
  text(s, "Pleos 차량 데이터", { x: 1.8, y: 1.72, w: 4.2, h: 0.4, fontSize: 18, bold: true });
  text(s, "Vehicle SDK 2.3.2 — 조회 권한, 신청 불필요", { x: 1.8, y: 2.12, w: 4.2, h: 0.35, fontSize: 12, color: C.accent6 });
  text(s, have.map((t, j) => ({ text: t, options: { bullet: true, breakLine: j < have.length - 1 } })), { x: 0.95, y: 2.85, w: 5.0, h: 3.0, fontSize: 15, paraSpaceAfter: 10 });
  s.addShape(pres.shapes.RIGHT_ARROW, { x: 6.38, y: 3.45, w: 0.55, h: 0.6, fill: { color: C.accent1 }, line: { type: "none" }, objectName: "arrow" });
  card(s, 7.1, 1.45, 5.6, 4.65, { fill: C.accent1, transparency: 85 });
  await badge(s, 7.4, 1.7, 0.7, "MdSmartToy", HEX.accent1);
  text(s, "차에 없는 것 → 스노봇", { x: 8.3, y: 1.72, w: 4.2, h: 0.4, fontSize: 18, bold: true });
  text(s, "앱만으로는 만들 수 없는 감각", { x: 8.3, y: 2.12, w: 4.2, h: 0.35, fontSize: 12, color: C.accent6 });
  text(s, fill.map((t, j) => ({ text: t, options: { bullet: true, breakLine: j < fill.length - 1 } })), { x: 7.45, y: 2.85, w: 5.0, h: 3.0, fontSize: 15, paraSpaceAfter: 10 });
  text(s, "SDK 파일과 공식 문서로 2026-10 직접 확인: 실내 CO₂·졸음 수준 값은 앱에 제공되지 않는다", { x: 0.6, y: 6.3, w: 12.1, h: 0.4, fontSize: 12, color: C.accent6 });

  // ===== 6. 동작하는 기능 =====
  pres.addSection({ title: "기능" });
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "기능" });
  s.addText("지금 동작하는 기능", { placeholder: "title" });
  const now = [
    ["MdRecordVoiceOver", "① 상황을 보고 말하는 대화", "말하기 버튼 → 음성 인식 → AI 대답(표정 + 목소리). 주행 중엔 짧게, 주차 중엔 길게", "기어·경고등(과 기어로 잰 운전 시간)이 대화의 길이와 내용을 바꾼다"],
    ["MdCampaign", "② 먼저 말 거는 순간", "출발·도착 인사, 주의 경고 → 쉼터 권유, 2시간째부터 1시간마다 휴식 권유", "앱 안 규칙 — 인터넷이 끊겨도 표정·말풍선은 뜬다 (목소리는 서버)"],
    ["MdFace", "③ 표정 동반자", "화면 속 스노봇 7가지 표정 + 고개 4방향. 실물 스노봇은 같은 명령을 받는다(제작 중)", "주행 중엔 글 대신 표정, 시험 버튼 숨김"],
    ["MdAir", "④ 공기질 케어", "모듈 CO₂ 1,500ppm 이상 → 걱정 표정 + 환기 제안. \"공기 어때?\"에 센서 값으로 답", "차가 주지 않는 실내 CO₂ 를 모듈이 더함"],
  ];
  for (let i = 0; i < 4; i++) {
    const x = 0.6 + (i % 2) * 6.2, y = 1.45 + Math.floor(i / 2) * 2.7;
    card(s, x, y, 5.9, 2.45);
    await badge(s, x + 0.25, y + 0.25, 0.7, now[i][0], HEX.accent1);
    text(s, now[i][1], { x: x + 1.15, y: y + 0.32, w: 3.2, h: 0.5, fontSize: 17, bold: true });
    statusTag(s, x + 4.25, y + 0.33, "✓ 동작", "done");
    text(s, now[i][2], { x: x + 0.3, y: y + 1.05, w: 5.3, h: 0.75, fontSize: 14 });
    text(s, now[i][3], { x: x + 0.3, y: y + 1.85, w: 5.3, h: 0.4, fontSize: 12, color: C.accent2, bold: true });
  }

  // ===== 7. 더할 기능 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "기능" });
  s.addText("PT 까지, 그리고 이후 더할 기능", { placeholder: "title" });
  const next = [
    ["MdNavigation", "⑤ 쉼터 경유지 · 회전 구간 침묵", "\"응, 쉼터 넣어 줘\" → 경유지 추가. 회전 안내 구간에선 듣는 표정만, 지나간 뒤 대답", "NaviHelper — \"도로를 보는 동승자\"를 가장 짧게 보여 주는 장면", "PT 까지", "pt"],
    ["MdChat", "⑥ 글레오 연동", "\"글레오, 스노봇한테 주차 어디 했는지 물어봐\" — 글레오가 스노봇 기능을 부른다", "Gleo Interface (App Schema 등록), 에뮬레이터 시험 가능 여부 확인 중", "PT 까지", "pt"],
    ["MdLocalParking", "⑦ 주차 위치 기억", "기어 P 순간 위치 저장, 모듈 카메라가 기둥 표지(\"B3 C-12\")를 읽어 층·구역까지", "FusedLocation SDK 공개 대기 (Pleos \"준비 중\")", "이후", "later"],
    ["MdHome", "⑧ 집으로 이어짐", "같은 스노봇이 집 책상에서 대화·알림을 이어 간다", "스노봇 1대에 차량 앱·폰 앱 여러 개를 짝지을 수 있다 — 서버 짝 맺기 완료, 대화 이어 가기·폰 앱은 이후", "이후", "later"],
  ];
  for (let i = 0; i < 4; i++) {
    const x = 0.6 + (i % 2) * 6.2, y = 1.45 + Math.floor(i / 2) * 2.7;
    card(s, x, y, 5.9, 2.45);
    await badge(s, x + 0.25, y + 0.25, 0.7, next[i][0], next[i][5] === "pt" ? HEX.accent1 : HEX.accent6);
    text(s, next[i][1], { x: x + 1.15, y: y + 0.32, w: 3.2, h: 0.5, fontSize: 17, bold: true });
    statusTag(s, x + 4.25, y + 0.33, next[i][4], next[i][5]);
    text(s, next[i][2], { x: x + 0.3, y: y + 1.05, w: 5.3, h: 0.75, fontSize: 14 });
    text(s, next[i][3], { x: x + 0.3, y: y + 1.85, w: 5.3, h: 0.45, fontSize: 12, color: C.accent2, bold: true });
  }

  // ===== 8. 주행 상태별 규칙 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "기능" });
  s.addText("주행 상태에 따라 말이 달라진다", { placeholder: "title" });
  const rules = [
    ["MdSpeed", "주행 중", "기어 D·R·N", "짧은 두 문장(30자), 표정 위주, 버튼 숨김", "\"졸리면 위험해요. 다음 쉼터에서 잠깐 쉬어 가요.\""],
    ["MdWarning", "경고 · 2시간 이상", "주의 경고등 / 연속 운전", "어떤 말에도 쉬자는 말 먼저", "\"재밌는 얘기는 다음 쉼터에서 쉬면서 들려줄게요.\""],
    ["MdLocalParking", "주차 중", "기어 P", "세 문장까지, 수다·위로", "\"오늘 많이 힘드셨군요. 잠깐 숨 고르세요.\""],
    ["MdWavingHand", "출발 · 도착", "P ↔ D 바뀌는 순간", "먼저 인사 (인터넷 없이도 표정·말풍선)", "\"도착! 오래 운전했으니 푹 쉬어요.\""],
  ];
  for (let i = 0; i < 4; i++) {
    const x = 0.6 + i * 3.1;
    card(s, x, 1.45, 2.85, 4.3);
    await badge(s, x + 0.25, 1.7, 0.75, rules[i][0], i === 1 ? HEX.accent1 : HEX.accent2);
    text(s, rules[i][1], { x: x + 0.25, y: 2.6, w: 2.4, h: 0.45, fontSize: 18, bold: true });
    text(s, rules[i][2], { x: x + 0.25, y: 3.05, w: 2.4, h: 0.35, fontSize: 12, color: C.accent6 });
    text(s, rules[i][3], { x: x + 0.25, y: 3.5, w: 2.4, h: 1.1, fontSize: 16 });
    text(s, rules[i][4], { x: x + 0.25, y: 4.7, w: 2.4, h: 0.95, fontSize: 15, italic: true, color: C.accent3 });
  }
  text(s, "말하는 중에도 안전 알림은 하고 인사는 건너뜀 · 주의 경고 10분에 한 번 · 휴식 2시간째부터 1시간마다 · 공기 1,500ppm 에서 한 번",
    { x: 0.6, y: 6.05, w: 12.1, h: 0.4, fontSize: 14, color: C.text2, bold: true });

  // ===== 9. 시나리오 A =====
  pres.addSection({ title: "시나리오" });
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "시나리오" });
  s.addText("시나리오 A — 금요일 밤 퇴근길", { placeholder: "title" });
  const shots = [
    ["car-depart-face.jpg", "출발 — 기어 D", "\"출발해요! 오늘도 안전하게 가요.\""],
    ["car-long-drive-face.jpg", "두 시간째", "\"벌써 2시간째 운전 중이에요. 쉼터에서 잠깐 쉬어 가요.\""],
    ["car-attention-face.jpg", "주의 경고등", "\"많이 피곤한가 봐요. 쉼터에서 잠깐 쉬었다 가요.\""],
    ["car-arrive-face.jpg", "도착 — 기어 P", "\"도착! 오래 운전했으니 푹 쉬어요.\""],
  ];
  for (let i = 0; i < 4; i++) {
    const x = 0.75 + i * 3.05;
    s.addImage({ path: path.join(IMG, shots[i][0]), x, y: 1.45, w: 2.6, h: 3.76, altText: "에뮬레이터 화면 — " + shots[i][1] });
    numberBadge(s, x - 0.15, 1.3, 0.5, i + 1);
    text(s, shots[i][1], { x, y: 5.3, w: 2.6, h: 0.4, fontSize: 16, bold: true });
    text(s, shots[i][2], { x, y: 5.7, w: 2.6, h: 0.75, fontSize: 12, color: C.text2 });
  }
  text(s, "에뮬레이터 실제 화면 (2026-10-28). 기어는 Pleos Virtual Console 로 바꿨고, 에뮬레이터에 없는 주의 경고등·긴 운전 시간은 시험 명령으로 재현",
    { x: 0.6, y: 6.5, w: 12.1, h: 0.35, fontSize: 10, color: C.accent6 });

  // ===== 10. 시나리오 B·C·D =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "시나리오" });
  s.addText("정차 중엔 수다, 그리고 다음 장면", { placeholder: "title" });
  text(s, "B. 주차장에서 잠깐 (동작)", { x: 0.6, y: 1.4, w: 6.6, h: 0.4, fontSize: 17, bold: true });
  const talk = [
    ["나", "오늘 회사에서 힘들었어"],
    ["스노봇", "오늘 많이 힘드셨군요. 잠깐 숨 고르세요. 얘기하고 싶으면 들을게요."],
    ["나", "아재개그 하나 해줘"],
    ["스노봇", "눈사람이 제일 무서워하는 과일은? 천도복숭아요, 천 도라니까요!"],
    ["나", "너 글레오보다 똑똑해?"],
    ["스노봇", "글레오는 척척박사 비서고, 저는 옆자리 눈치 담당이에요."],
  ];
  let ty = 1.95;
  for (const [who, line] of talk) {
    const me = who === "나";
    const h = me ? 0.5 : 0.72;
    const x = me ? 3.4 : 0.6, w = me ? 3.8 : 6.0;
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: ty, w, h, rectRadius: 0.15, fill: { color: me ? C.background2 : C.accent1, transparency: me ? 0 : 82 }, line: { type: "none" }, objectName: "bubble " + who });
    text(s, line, { x: x + 0.2, y: ty, w: w - 0.4, h, fontSize: 14, valign: "middle", align: me ? "right" : "left" });
    ty += h + 0.12;
  }
  const later = [
    ["MdLocalParking", "C. 대형마트 지하주차장 (이후)", "기어 P → 위치 저장, 카메라가 \"B3 C-12\" 표지를 읽음. \"스노봇, 차 어디 있어?\" → \"지하 3층 C-12예요.\""],
    ["MdChat", "D. 글레오와 함께 (PT 까지)", "\"글레오, 스노봇 기분 어때?\" → 글레오가 스노봇 기능을 부르고, 스노봇이 웃으며 오늘 주행을 한 문장으로 요약"],
  ];
  for (let i = 0; i < 2; i++) {
    const y = 1.45 + i * 2.55;
    card(s, 7.6, y, 5.1, 2.3);
    await badge(s, 7.85, y + 0.25, 0.65, later[i][0], HEX.accent6);
    text(s, later[i][1], { x: 8.7, y: y + 0.32, w: 3.8, h: 0.5, fontSize: 16, bold: true });
    text(s, later[i][2], { x: 7.9, y: y + 1.05, w: 4.55, h: 1.15, fontSize: 14, color: C.text2 });
  }

  // ===== 11. 만든 것 =====
  pres.addSection({ title: "개발" });
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "개발" });
  s.addText("이미 동작하는 것 (2026-10-28)", { placeholder: "title" });
  const stats = [
    ["1.5초", "말 끝 → 첫 소리 중간값 (17번 실측, 1.3~2.5초)"],
    ["0.35초 안", "말 끝 → 글자 확정 (13번 실측)"],
    ["2종 + 1", "Vehicle SDK(기어·경고등) + 앱이 잰 운전 시간"],
    ["20개", "서버 자동 시험 통과"],
  ];
  for (let i = 0; i < 4; i++) {
    const x = 0.6 + i * 3.1;
    card(s, x, 1.45, 2.85, 1.75, { fill: C.text2 });
    text(s, stats[i][0], { x: x + 0.2, y: 1.6, w: 2.45, h: 0.85, fontSize: 36, bold: true, color: C.accent1, align: "center", valign: "middle" });
    text(s, stats[i][1], { x: x + 0.2, y: 2.5, w: 2.45, h: 0.55, fontSize: 12, color: C.background1, align: "center" });
  }
  const built = [
    ["MdPhoneAndroid", "차량 앱", "Kotlin · Pleos 에뮬레이터. 짝 맺기, 가상 스노봇, 말하기 버튼, 차 스피커, Vehicle SDK(기어·경고등) + 앱이 잰 운전 시간, 먼저 말 걸기 4종"],
    ["MdCloud", "클라우드 서버", "FastAPI WebSocket(TLS). 앱 ↔ 스노봇 중계, 음성 인식 → 대답 → 목소리, 차 상태 반영"],
    ["MdRecordVoiceOver", "대화 AI", "음성 인식 ElevenLabs Scribe v2 → 대답·표정 Claude Haiku 5.5 → 목소리 ElevenLabs v4 turbo, 문장 단위로 흘려 보냄"],
    ["MdSmartToy", "스노봇 시제품", "3D 설계 완료(서보 4개: 눈꺼풀·눈썹·고개), 1차 출력 주문, 부품 11/7 도착 → 조립"],
  ];
  for (let i = 0; i < 4; i++) {
    const y = 3.45 + i * 0.78;
    await badge(s, 0.6, y + 0.05, 0.55, built[i][0], HEX.accent2);
    text(s, built[i][1], { x: 1.35, y: y + 0.1, w: 2.1, h: 0.45, fontSize: 15, bold: true, valign: "middle" });
    text(s, built[i][2], { x: 3.45, y: y, w: 9.25, h: 0.65, fontSize: 13, color: C.text2, valign: "middle" });
  }
  text(s, "개인정보: 녹음 저장 안 함 · 서버 기록에 말 내용 없음(길이·소리 크기만) · 최근 대화 8번만 서버 메모리에",
    { x: 0.6, y: 6.55, w: 12.1, h: 0.35, fontSize: 11, color: C.accent6 });

  // ===== 12. 구조 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "개발" });
  s.addText("어떻게 연결되나", { placeholder: "title" });
  const boxes = [
    ["MdPhoneAndroid", "차량 앱 (Pleos)", ["Vehicle SDK: 기어·경고등", "마이크 · 차 스피커", "앱 안 규칙: 출발·도착·경고·휴식"], HEX.accent2],
    ["MdCloud", "클라우드 서버", ["음성 인식 (Scribe v2)", "대답·표정 (Claude Haiku 5.5)", "목소리 (ElevenLabs)", "차 상태 → 대답 길이"], HEX.accent1],
    ["MdSmartToy", "스노봇 모듈", ["눈꺼풀·눈썹·고개 서보 4개", "CO₂·온도 센서", "스피커 · 마이크 (ESP32-S3)"], HEX.accent2],
  ];
  for (let i = 0; i < 3; i++) {
    const x = 0.6 + i * 4.275;
    card(s, x, 1.5, 3.55, 3.3, { fill: i === 1 ? C.accent1 : C.background2, transparency: i === 1 ? 85 : 0 });
    await badge(s, x + 0.25, 1.75, 0.7, boxes[i][0], boxes[i][3]);
    text(s, boxes[i][1], { x: x + 1.1, y: 1.85, w: 2.4, h: 0.5, fontSize: 17, bold: true });
    text(s, boxes[i][2].map((t, j) => ({ text: t, options: { bullet: true, breakLine: j < boxes[i][2].length - 1 } })),
      { x: x + 0.3, y: 2.7, w: 3.05, h: 2.0, fontSize: 13, paraSpaceAfter: 6 });
    if (i < 2) {
      s.addShape(pres.shapes.LEFT_RIGHT_ARROW, { x: x + 3.62, y: 2.9, w: 0.6, h: 0.45, fill: { color: C.accent6 }, line: { type: "none" }, objectName: "link" });
    }
  }
  const notes = [
    ["연결 방법", "앱·스노봇 ↔ 서버는 WebSocket(TLS). 지금: 스노봇이 Wi-Fi 로 직접 · 최종: 블루투스로 차량 앱에 붙어 앱의 인터넷을 빌림 (차량 앱 = 모듈 허브)"],
    ["AI 엔진 바꿔 끼우기", "인터넷 있으면 클라우드 AI · 글레오 호출 시 글레오 · 인터넷 없으면 앱 안 규칙(표정·말풍선, 구현됨) + 차 안 음성 엔진(공개 시) · 차량 LLM 이 열리면 교체"],
  ];
  for (let i = 0; i < 2; i++) {
    const y = 5.1 + i * 0.78;
    text(s, notes[i][0], { x: 0.6, y, w: 2.4, h: 0.65, fontSize: 14, bold: true, color: C.accent1, valign: "middle" });
    text(s, notes[i][1], { x: 3.0, y, w: 9.7, h: 0.65, fontSize: 13, valign: "middle" });
  }

  // ===== 12-2. 시스템 구성도 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "개발" });
  s.addText("전체 구성 한눈에", { placeholder: "title" });
  s.addImage({ path: path.join(ARCH, "01-system-overview.png"), x: 1.92, y: 1.25, w: 9.5, h: 5.7,
    altText: "스노봇 시스템 구성도 — 차량 앱(Vehicle SDK·앱 안 규칙)과 스노봇 로봇이 클라우드 서버에 WebSocket 으로 붙고, 서버가 음성 인식·Claude·목소리 API 를 부른다" });

  // ===== 13. SDK 표 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "개발" });
  s.addText("Pleos SDK 사용 계획", { placeholder: "title" });
  const H = (t) => ({ text: t, options: { bold: true, color: C.background1, fill: { color: C.text2 } } });
  const st = (t, kind) => ({ text: t, options: { bold: true, color: { done: C.accent4, pt: C.accent1, later: C.accent6 }[kind] } });
  const rows = [
    [H("SDK / API"), H("쓰는 곳"), H("상태")],
    ["Vehicle — Gear", "주행/주차 → 출발·도착 인사, 대화 길이", st("구현", "done")],
    ["Vehicle — Odometer", "트립 운전 시간 — 단위 미확인이라 구독만 (휴식 권유는 앱이 잰 시간)", st("구독만", "pt")],
    ["Vehicle — Safety (경고등)", "운전자 주의 경고 → 걱정 표정·쉼터 권유", st("구현 (5초 조회)", "done")],
    ["NaviHelper", "쉼터 경유지 추가, 회전 안내 → 말 멈춤", st("PT 까지", "pt")],
    ["Vehicle — HVAC", "내기 순환 오래 + CO₂ 높음 → 환기 권유", st("PT 까지", "pt")],
    ["CarUxRestrictions", "주행 규제에 맞춰 화면 줄이기 (지금은 기어로)", st("PT 까지", "pt")],
    ["Gleo Interface", "ask_snowbot · get_parking_spot · snowbot_status", st("PT 까지 (확인 중)", "pt")],
    ["TTS · STT SDK", "차 안 엔진으로 오프라인 대화", st("공개되면", "later")],
    ["FusedLocation", "주차 위치 저장", st("공개되면", "later")],
  ];
  s.addTable(rows, {
    x: 0.6, y: 1.4, w: 12.1, colW: [3.4, 6.2, 2.5], rowH: 0.48, fontSize: 14, color: C.text1,
    border: { type: "solid", pt: 0.75, color: HEX.accent5 }, fill: { color: C.background1 }, valign: "middle", margin: [0, 0.12, 0, 0.12],
  });
  text(s, "Vehicle SDK 는 조회 권한(CAR_DRIVE · CAR_SAFETY)만 쓴다 — 매니페스트 선언만으로 되고 별도 신청이 필요 없다. TTS · STT · FusedLocation 은 Pleos 에서 \"준비 중\".",
    { x: 0.6, y: 6.35, w: 12.1, h: 0.45, fontSize: 12, color: C.accent6 });

  // ===== 14. 일정 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "개발" });
  s.addText("일정", { placeholder: "title" });
  const marks = [
    ["10/28", "오늘", "대화 · 차량 데이터 동작"],
    ["11/7", "부품 도착", "실물 스노봇 조립"],
    ["11/15", "접수 마감", "기획서 + 시연 영상 + GitHub(APK)"],
    ["11/23", "1차 발표", "20팀 선정"],
    ["12/3~4", "최종 PT", "에뮬레이터 + 실물 연결 시연"],
    ["2027-02", "전시·시상", "선정 시"],
  ];
  s.addShape(pres.shapes.LINE, { x: 1.5, y: 2.2, w: 10.3, h: 0, line: { color: C.accent5, width: 3 }, objectName: "timeline" });
  for (let i = 0; i < marks.length; i++) {
    const cx = 1.5 + i * (10.3 / 5);
    const hot = i === 2 || i === 4;
    s.addShape(pres.shapes.OVAL, { x: cx - 0.2, y: 2.0, w: 0.4, h: 0.4, fill: { color: hot ? C.accent1 : C.accent2 }, line: { color: C.background1, width: 2 }, objectName: "mark " + marks[i][0] });
    text(s, marks[i][0], { x: cx - 1.0, y: 1.45, w: 2.0, h: 0.4, fontSize: 16, bold: true, align: "center", color: hot ? C.accent1 : C.text1 });
    text(s, marks[i][1], { x: cx - 1.0, y: 2.55, w: 2.0, h: 0.35, fontSize: 14, bold: true, align: "center" });
    text(s, marks[i][2], { x: cx - 1.0, y: 2.9, w: 2.0, h: 0.65, fontSize: 12, align: "center", color: C.text2 });
  }
  const phases = [
    ["접수(11/15)까지", ["지금 상태 그대로 제출", "시연 영상 (에뮬레이터 + Virtual Console)", "GitHub 소스 + release APK", "시스템 구조도"]],
    ["PT(12/3)까지", ["NaviHelper 쉼터 경유지 · 회전 구간 침묵", "Gleo App Schema", "주행 규제 대응, 대화 기억", "실물 스노봇과 연결 시연"]],
    ["이후", ["블루투스 연결 (차량 앱 = 모듈 허브)", "주차 위치", "모듈 규격 공개", "차량 제어 SDK 공개 시 공조 직접 제어"]],
  ];
  for (let i = 0; i < 3; i++) {
    const x = 0.6 + i * 4.1;
    card(s, x, 3.85, 3.85, 2.85, { fill: i === 0 ? C.accent1 : C.background2, transparency: i === 0 ? 85 : 0 });
    text(s, phases[i][0], { x: x + 0.25, y: 4.0, w: 3.35, h: 0.45, fontSize: 16, bold: true });
    text(s, phases[i][1].map((t, j) => ({ text: t, options: { bullet: true, breakLine: j < 3 } })),
      { x: x + 0.25, y: 4.5, w: 3.4, h: 2.1, fontSize: 13, paraSpaceAfter: 5 });
  }

  // ===== 15. 사업성 =====
  pres.addSection({ title: "사업성" });
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "사업성" });
  s.addText("누가, 얼마나, 얼마에", { placeholder: "title" });
  const biz = [
    ["약 6,000", "예상 MAU · 2027년 말", "가정: 누적 30만 대 × 설치 5% × 월 사용 40%"],
    ["약 40,000", "예상 MAU · 2030년", "가정: 누적 200만 대 × 5% × 40%"],
    ["35%+", "30일 재사용률 (목표)", "출발·도착마다 먼저 말을 건다 (지금은 앱 화면이 열려 있을 때)"],
    ["$0.003", "대화 1번 비용 (추정)", "하루 10번 → 월 약 1,200원. 인사·경고 한마디는 0원"],
  ];
  for (let i = 0; i < 4; i++) {
    const x = 0.6 + i * 3.1;
    card(s, x, 1.45, 2.85, 2.35);
    text(s, biz[i][0], { x: x + 0.2, y: 1.6, w: 2.45, h: 0.8, fontSize: 34, bold: true, color: C.accent1 });
    text(s, biz[i][1], { x: x + 0.2, y: 2.4, w: 2.45, h: 0.4, fontSize: 14, bold: true });
    text(s, biz[i][2], { x: x + 0.2, y: 2.8, w: 2.45, h: 0.9, fontSize: 11, color: C.text2 });
  }
  text(s, "타깃 고객", { x: 0.6, y: 4.0, w: 5.6, h: 0.4, fontSize: 16, bold: true });
  const targets = ["출퇴근·장거리 운전이 잦은 Pleos 차량 운전자", "고급 감지 기능이 없는 트림의 차주 (모듈로 확장)", "지하주차장을 자주 쓰는 운전자", "(향후) 반려동물·아이 동승 가족"];
  text(s, targets.map((t, j) => ({ text: t, options: { bullet: true, breakLine: j < 3 } })), { x: 0.6, y: 4.45, w: 5.6, h: 2.1, fontSize: 14, paraSpaceAfter: 6 });
  text(s, "수익", { x: 6.9, y: 4.0, w: 5.8, h: 0.4, fontSize: 16, bold: true });
  const money = [["앱", "기본 무료 — 인사·경고·휴식은 오프라인도 표정으로"], ["모듈 판매", "스노봇 로봇, CO₂ 태그, 카메라 큐브"], ["구독 (선택)", "AI 대화 무제한, 집 연동, 주행 요약"], ["모듈 플랫폼", "규격 공개 → 다른 하드웨어 업체 참여"], ["시범 기능 협력", "현대차그룹과 시범 기능 검증·익명 통계 보고"]];
  for (let i = 0; i < money.length; i++) {
    const y = 4.45 + i * 0.43;
    text(s, money[i][0], { x: 6.9, y, w: 1.75, h: 0.42, fontSize: 14, bold: true, color: C.accent2 });
    text(s, money[i][1], { x: 8.65, y, w: 4.05, h: 0.42, fontSize: 13 });
  }
  text(s, "MAU·Retention 은 가정·목표, 비용은 2026-10 공개 단가 기준 추정 — 제출 전 다시 확인", { x: 0.6, y: 6.65, w: 12.1, h: 0.3, fontSize: 10, color: C.accent6 });

  // ===== 16. 확대 계획 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "사업성" });
  s.addText("확대 계획", { placeholder: "title" });
  const road = [
    ["2027", "국내 Pleos 차량", "스노봇 모듈 + 졸음·공기·주차·글레오 연동"],
    ["2028", "모듈 규격 공개", "검증된 시범 기능을 글레오로 이관, 카메라 큐브(펫·아이 동승), 공조 직접 제어"],
    ["2029~", "해외 · 집", "해외 Pleos 차량, 집 스마트홈 연동, (번외) 브랜드 캐릭터 모듈"],
  ];
  for (let i = 0; i < 3; i++) {
    const y = 1.5 + i * 1.65;
    card(s, 0.6, y, 6.9, 1.4);
    text(s, road[i][0], { x: 0.85, y: y + 0.2, w: 1.6, h: 1.0, fontSize: 28, bold: true, color: C.accent1, valign: "middle" });
    text(s, road[i][1], { x: 2.6, y: y + 0.22, w: 4.7, h: 0.45, fontSize: 17, bold: true });
    text(s, road[i][2], { x: 2.6, y: y + 0.68, w: 4.7, h: 0.6, fontSize: 13, color: C.text2 });
  }
  s.addImage({ path: path.join(IMG, "turnaround.jpg"), x: 7.85, y: 1.9, w: 4.85, h: 2.43, altText: "스노봇 기준 디자인 — 앞·옆·뒤 모습" });
  text(s, "같은 기구(눈꺼풀·눈썹·고개)가 모든 모듈의 바탕이 된다", { x: 7.85, y: 4.45, w: 4.85, h: 0.6, fontSize: 13, color: C.text2 });

  // ===== 17. 번외 =====
  s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "사업성" });
  s.addText("번외 — 향후 추가 제안: 브랜드 캐릭터 모듈", { placeholder: "title" });
  text(s, "본 제안의 범위 밖에서, 같은 기구에 겉모습만 바꾼 캐릭터 봇을 제안한다. 브랜드 캐릭터가 대시보드 위에서 같은 방식으로 표정을 짓고 말한다.",
    { x: 0.6, y: 1.35, w: 12.1, h: 0.7, fontSize: 15, color: C.text2 });
  const brands = [["brand-h.jpg", "예시: SD 휴머노이드 로봇"], ["brand-k.jpg", "예시: SD 호랑이"]];
  for (let i = 0; i < 2; i++) {
    const x = 0.6 + i * 6.2;
    s.addImage({ path: path.join(IMG, brands[i][0]), x, y: 2.2, w: 5.9, h: 2.95, altText: brands[i][1] + " 시안" });
    text(s, brands[i][1], { x, y: 5.25, w: 5.9, h: 0.4, fontSize: 15, bold: true });
  }
  text(s, "기대효과: 브랜드 굿즈 + 차량 모듈, 신차 출고 선물·한정판, 캐릭터별 목소리·말투",
    { x: 0.6, y: 5.75, w: 12.1, h: 0.4, fontSize: 14 });
  text(s, "예시 시안일 뿐이다. 로고는 넣지 않았고, 실제 브랜드 자산·상표 사용은 주최사와 협의를 전제로 한다.",
    { x: 0.6, y: 6.2, w: 12.1, h: 0.4, fontSize: 12, color: C.accent6 });

  // ===== 18. 남은 일 =====
  pres.addSection({ title: "다음 단계" });
  s = pres.addSlide({ masterName: "CONTENT_DARK", sectionTitle: "다음 단계" });
  s.addText("제출(11/15) 전 남은 일", { placeholder: "title" });
  const todo = [
    ["10/28 설명회 답변 반영", "실물 동반 시연 · 외부 AI 서버 경유 · TTS/STT/Gleo 사용 가능 여부", "사용자"],
    ["구조도 · 문서 검증 — 완료", "구조도 3장 · 구조 PPT 8장 · 사실 오류 10곳 고침 · 응답 시간 기록", "Claude"],
    ["시연 영상", "에뮬레이터 + Virtual Console 화면 녹화, 자막", "Claude · 녹화 함께"],
    ["GitHub + release APK", "서버 주소·키·이름 없이, 이력 없는 새 저장소 · README 필수 항목", "Claude · 계정 연결 사용자"],
    ["접수양식 DOCX", "2장 8개 항목 옮기기 → 1장·서명은 사용자가 직접", "Claude → 사용자"],
    ["이메일 제출", "appdevchallenge@hyundai-partners.com (11/14 목표, 하루 여유)", "사용자"],
  ];
  for (let i = 0; i < todo.length; i++) {
    const y = 1.45 + i * 0.85;
    await badge(s, 0.6, y + 0.08, 0.55, "MdCheckCircle", HEX.accent1);
    text(s, todo[i][0], { x: 1.4, y: y + 0.05, w: 3.6, h: 0.6, fontSize: 17, bold: true, color: C.background1, valign: "middle" });
    text(s, todo[i][1], { x: 5.0, y: y + 0.05, w: 5.4, h: 0.6, fontSize: 13, color: C.accent5, valign: "middle" });
    text(s, todo[i][2], { x: 10.5, y: y + 0.05, w: 2.2, h: 0.6, fontSize: 12, color: C.accent1, valign: "middle", align: "right", bold: true });
  }

  await pres.writeFile({ fileName: OUT });
  await applyTheme(OUT, THEME);
  console.log("wrote", OUT);
}

build().catch((e) => { console.error(e); process.exit(1); });
