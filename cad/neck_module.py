"""2단계: 목 관절 — 베어링 자리, 회전판(요크), 고개 좌우 서보(몸통), 끄덕임 서보(머리).

좌표·단위는 snowbot_layout.py 와 같다 (x 왼쪽+, y 뒤+, z 위, mm).

구조 (자세한 이유는 docs/05-cad-stages.md 2단계):
- 몸통 위를 z=BODY_TOP_Z 에서 평평하게 자르고, 가운데 아래로 내민 고리에 베어링 6800ZZ 를 끼운다.
  머리 무게는 회전판 → 베어링 → 몸통으로 간다. 좌우 서보는 돌리는 힘만 낸다.
- 회전판 = 몸통 위에 보이는 짙은 회색 원판 + 베어링 안쪽에 끼우는 통(허브) + 양옆으로 세운 판 2장(요크).
  허브 아래 끝에 좌우 서보(MG90S, 몸통 안에 세움)의 혼을 나사로 고정한다.
- 끄덕임 서보(MG90S)는 머리 안에 눕혀 축이 x 방향. 서보 혼은 요크 오른쪽 판(x+)에 고정, 반대쪽 판의 핀이
  서보 축과 같은 선에서 머리 쪽 받침을 잡는다. 서보가 돌면 서보와 함께 머리 전체가 끄덕인다.
- 요크 판은 머리 아래 구멍으로 들어간다. 머리 아래를 평평하게 잘라 구멍 둘레 테두리를 남긴다.
- 선(5V·GND·스피커·서보 신호)은 회전판 뒤쪽 구멍 → 몸통 위의 반달 홈으로 지나간다. 고개가 좌우로 돌아도 홈 안에서 움직인다.
"""

from math import cos, radians, sin

from build123d import Box, Cylinder, Location, Part, Pos, Rot

# ── 높이 ───────────────────────────────────────────────────
BODY_TOP_Z = 60.0       # 몸통 위 평평한 면 (지름 약 30mm)
HEAD_BOTTOM_Z = 77.0    # 머리 아래 평평한 면 (기준 그림은 키 150 — 끄덕임 여유 때문에 2mm 높임)
NOD_Z = 87.0            # 끄덕임 축 높이 (머리 아래 면 + 10)
HEAD_HOLE_R = 25.0      # 머리 아래 구멍 (요크 판이 들어감). 테두리 폭 약 3.3mm

# ── 베어링 6800ZZ (규격) ─────────────────────────────────────
BRG_OD, BRG_ID, BRG_W = 19.0, 10.0, 5.0
BRG_FIT = 0.2           # 베어링 자리 지름 여유 (공차 판 C 19.2, 베어링 실측 18.98 — 2026-11-07)
SEAT_OD = 25.0          # 베어링 자리 고리 바깥지름
SEAT_LIP_D = 15.0       # 베어링 바깥 고리를 받치는 턱 안지름
SEAT_LIP_T = 1.5

# ── 회전판·요크 ─────────────────────────────────────────────
TT_R = 22.0             # 회전판 반지름 (지름 44)
TT_T = 3.0
TT_GAP = 0.5            # 몸통 윗면과 회전판 사이
HUB_R = BRG_ID / 2      # 베어링 안쪽에 끼우는 통
COLLAR_R, COLLAR_T = 6.5, 0.5    # 베어링 안쪽 고리만 닿게 하는 턱
CHEEK_X0, CHEEK_T = 19.0, 2.5    # 요크 판 안쪽면 x, 두께
CHEEK_HALF_W = 4.5               # 요크 판 앞뒤 반폭
CHEEK_TOP_R = 6.0                # 요크 판 위쪽 둥근 끝 반지름 (끄덕임 축 둘레)
WIRE_R, WIRE_HOLE_D = 19.0, 6.0  # 회전판 선 구멍 (뒤쪽). 26AWG 실리콘선 5가닥 묶음 약 지름 4.5
BARREL_R, BARREL_Z = 5.0, 69.0   # 요크 판 사이 가로 원통 (기준 그림의 은색 목). 요크를 묶어 단단하게도 한다
CAP_R, CAP_T = 5.5, 1.5          # 요크 판 바깥 둥근 캡
SLOT_R0, SLOT_R1, SLOT_HALF_ANG = 16.5, 21.5, 110.0   # 목 받침판 반달 홈 (뒤쪽 중심 ±110°)

# ── 서보 MG90S (일반 규격, 실측 필요) ──────────────────────────
MG_L, MG_W, MG_BODY_H = 22.8, 11.8, 22.4   # 몸체 길이·폭·높이 (폭·높이 실측 2026-11-07, 길이 22.41 은 재측정 대기)
MG_TOTAL_H = 31.59                          # 바닥 ~ 출력축 끝 (실측 2026-11-07, 설계 29.0 보다 2.6 큼)
MG_SHAFT_FROM_END = 5.64                    # 몸체 끝에서 출력축 가운데 (카드 방법 실측: 8.89 - 0.81 - 4.89/2)
MG_TAB_TOP = 19.50                          # 바닥 ~ 날개 윗면 (실측 2026-11-07)
MG_TAB_L, MG_TAB_T = 32.2, 2.4              # 날개 끝~끝, 날개 두께 (실측 2026-11-07)
MG_TAB_Z = MG_TAB_TOP - MG_TAB_T            # 바닥 ~ 날개 아랫면 = 받침판에 닿는 면
MG_BOSS_R = 3.0                             # 몸체 위 기어 뭉치(축 둘레) 반지름
HORN_R, HORN_T = 10.0, 2.0                  # 원형 혼 (MG90S 부속, 실측 필요)
PIVOT_D = 3.0                               # 반대쪽 핀 (M3 나사 또는 3mm 봉)
PIVOT_ARM_X0, PIVOT_ARM_X1 = CHEEK_X0 - 0.5, CHEEK_X0 - 3.9   # 머리 안 핀 받침 판 (|x| 18.5 ~ 15.1). 서보가 길어져(31.6) 4→3.4 두께로 바깥으로 옮김


def head_location(pan: float, nod: float) -> Location:
    """머리에 붙은 모든 것의 위치. nod + 는 고개를 숙임(앞이 내려감), pan + 는 로봇 기준 왼쪽으로 돎."""
    return Rot(0, 0, pan) * Pos(0, 0, NOD_Z) * Rot(nod, 0, 0) * Pos(0, 0, -NOD_Z)


def neck_location(pan: float) -> Location:
    return Rot(0, 0, pan)


def servo(shaft_up: bool = True) -> Part:
    """MG90S. 원점 = 출력축 끝, 축이 +z. 몸체는 -x 쪽으로 길다(축이 몸체 +x 끝에서 6mm)."""
    cx = -(MG_L / 2 - MG_SHAFT_FROM_END)
    bottom = -MG_TOTAL_H
    body = Pos(cx, 0, bottom + MG_BODY_H / 2) * Box(MG_L, MG_W, MG_BODY_H)
    tabs = Pos(cx, 0, bottom + MG_TAB_Z + MG_TAB_T / 2) * Box(MG_TAB_L, MG_W, MG_TAB_T)
    boss = Pos(0, 0, (bottom + MG_BODY_H) / 2) * Cylinder(MG_BOSS_R, -(bottom + MG_BODY_H))
    return body + tabs + boss


# ── 몸통 위 가공 ────────────────────────────────────────────
def body_top_add() -> Part:
    """몸통 윗판 아래로 내민 베어링 자리 고리."""
    h = BRG_W + SEAT_LIP_T
    return Pos(0, 0, BODY_TOP_Z - h / 2) * Cylinder(SEAT_OD / 2, h)


def body_top_cut() -> Part:
    pocket = Pos(0, 0, BODY_TOP_Z - BRG_W / 2 + 0.5) * Cylinder(BRG_OD / 2 + BRG_FIT / 2, BRG_W + 1)
    lip_hole = Pos(0, 0, BODY_TOP_Z - BRG_W - SEAT_LIP_T) * Cylinder(SEAT_LIP_D / 2, 10)
    slot = Part()
    steps = 48          # 원기둥 간격이 지름보다 작아야 이어진 홈이 된다
    for i in range(steps + 1):
        a = radians(90 - SLOT_HALF_ANG + 2 * SLOT_HALF_ANG * i / steps)
        r = (SLOT_R0 + SLOT_R1) / 2
        slot += Pos(r * cos(a), r * sin(a), BODY_TOP_Z) * Cylinder((SLOT_R1 - SLOT_R0) / 2, 8)
    return pocket + lip_hole + slot


# ── 부품 ───────────────────────────────────────────────────
def yoke() -> Part:
    """회전판 + 허브 + 요크 판 2장 (한 덩어리로 출력)."""
    tt_z0 = BODY_TOP_Z + TT_GAP
    disc = Pos(0, 0, tt_z0 + TT_T / 2) * Cylinder(TT_R, TT_T)
    collar = Pos(0, 0, BODY_TOP_Z + COLLAR_T / 2) * Cylinder(COLLAR_R, COLLAR_T + 0.01)
    hub_bottom = BODY_TOP_Z - BRG_W - SEAT_LIP_T - 1.0
    hub = Pos(0, 0, (BODY_TOP_Z + hub_bottom) / 2) * Cylinder(HUB_R - 0.05, BODY_TOP_Z - hub_bottom)
    y = disc + collar + hub
    top = tt_z0 + TT_T
    for s in (1, -1):
        x = s * (CHEEK_X0 + CHEEK_T / 2)
        col = Pos(x, 0, (top + NOD_Z) / 2) * Box(CHEEK_T, 2 * CHEEK_HALF_W, NOD_Z - top)
        cap = Pos(x, 0, NOD_Z) * Rot(0, 90, 0) * Cylinder(CHEEK_HALF_W, CHEEK_T)
        y += col + cap
        y -= Pos(x, 0, NOD_Z) * Rot(0, 90, 0) * Cylinder(PIVOT_D / 2 if s < 0 else 1.0, CHEEK_T + 1)
    barrel = Pos(0, 0, BARREL_Z) * Rot(0, 90, 0) * Cylinder(BARREL_R, 2 * CHEEK_X0 + 1)
    y += barrel
    for s in (1, -1):
        y += Pos(s * (CHEEK_X0 + CHEEK_T + CAP_T / 2), 0, BARREL_Z) * Rot(0, 90, 0) * Cylinder(CAP_R, CAP_T)
    y -= Pos(0, WIRE_R, top) * Cylinder(WIRE_HOLE_D / 2, 20)
    return y


def pan_horn() -> Part:
    z = BODY_TOP_Z - BRG_W - SEAT_LIP_T - 1.0 - HORN_T / 2
    return Pos(0, 0, z) * Cylinder(HORN_R, HORN_T)


def pan_servo() -> Part:
    z_top = BODY_TOP_Z - BRG_W - SEAT_LIP_T - 1.0 - HORN_T
    return Pos(0, 0, z_top) * servo()


def nod_servo() -> Part:
    """머리 안. 출력축이 +x, 축 끝이 혼 바로 안쪽. 몸체는 뒤(+y)로 길게."""
    shaft_tip_x = CHEEK_X0 - HORN_T
    return Pos(shaft_tip_x, 0, NOD_Z) * Rot(-90, 0, 0) * Rot(0, 90, 0) * servo()


def nod_horn() -> Part:
    return Pos(CHEEK_X0 - HORN_T / 2, 0, NOD_Z) * Rot(0, 90, 0) * Cylinder(HORN_R, HORN_T)


def pivot_pin() -> Part:
    x1 = -(CHEEK_X0 + CHEEK_T)
    x0 = -PIVOT_ARM_X1          # 핀 안쪽 끝 = 머리 핀 받침 안쪽 면 (끄덕임 서보 바닥과 0.5 띄움)
    return Pos((x0 + x1) / 2, 0, NOD_Z) * Rot(0, 90, 0) * Cylinder(PIVOT_D / 2, x0 - x1)


def bearing() -> Part:
    z = BODY_TOP_Z - BRG_W / 2
    return Pos(0, 0, z) * (Cylinder(BRG_OD / 2, BRG_W) - Cylinder(BRG_ID / 2, BRG_W + 1))


def body_neck_parts() -> dict[str, Part]:
    return {"bearing": bearing(), "mg90s_pan": pan_servo()}


def neck_parts(pan: float) -> dict[str, Part]:
    loc = neck_location(pan)
    return {"neck_yoke": loc * yoke(), "pan_horn": loc * pan_horn(), "nod_horn": loc * nod_horn(), "nod_pivot": loc * pivot_pin()}


def head_neck_parts() -> dict[str, Part]:
    """머리와 같이 움직이는 목 부품 (위치는 head_location 을 곱하기 전)."""
    return {"mg90s_nod": nod_servo()}


JOINED = {
    frozenset(x) for x in [
        ("bearing", "neck_yoke"), ("bearing", "body"), ("pan_horn", "neck_yoke"), ("pan_horn", "mg90s_pan"),
        ("nod_horn", "neck_yoke"), ("nod_horn", "mg90s_nod"), ("nod_pivot", "neck_yoke"),
    ]
}
