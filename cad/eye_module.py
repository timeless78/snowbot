"""1단계: 눈 기구 — 눈 테(눈 상자)·흰자 돔·검은자·카메라, 눈꺼풀, 눈썹, 코, 서보 2개와 연결.

좌표·단위는 snowbot_layout.py 와 같다 (x 왼쪽+, y 뒤+, z 위, mm). 눈 각도 φ 는 눈 중심에서 볼 때
정면(-y)이 0°, 위(+z)가 90°, 뒤가 180°, 아래가 270° (x 축을 도는 각).

비율은 C 디자인 기준 그림(docs/assets/design-tech/c-reference-turnaround.png)을 재서 정했다.
구조 (자세한 이유는 docs/05-cad-stages.md 1단계):
- 머리는 둥근 구 그대로. 눈마다 **정면을 보는 둥근 눈 테(구 껍데기 조각)**가 머리 밖으로 나온다.
  두 눈 중심이 x 축 한 줄에 놓여 **축 하나로 두 눈꺼풀을 같이 돌린다.** 축 양 끝은 눈 테 벽 안에 숨는다.
- 눈꺼풀은 흰자 돔보다 조금 큰 반구 껍데기 조각. 눈 테 안에서 위로 돌아 머리 안으로 들어가며 눈을 뜬다.
- 두 눈 사이 축에 크랭크(돌림 팔), 그 아래 뒤에 눈꺼풀 서보. 크랭크·혼 길이가 같고 막대가 두 축 거리와 같은
  평행사변형이라 서보가 돈 만큼 눈꺼풀도 돈다.
- 눈썹은 머리 곡면을 따라 휜 막대. 머리 중심을 지나는 축을 돌아서 곡면과 틈이 일정하다.
  축 안쪽 끝의 레버 두 개를 가운데 슬라이더가 같이 올리고 내린다(좌우 대칭). 슬라이더는 눈썹 서보 혼의 핀이 민다.
"""

import math
from math import cos, radians, sin

from build123d import Box, Cone, Cylinder, Location, Part, Plane, Polygon, Pos, Rot, Sphere, Vector, extrude

from snowbot_layout import HEAD_R, WALL, head_center_z

# ── 눈 (그림 비율) ─────────────────────────────────────────
EYE_X = 19.0          # 눈 중심 좌우 거리
EYE_DZ = 3.0          # 눈이 머리 중심보다 높은 정도
EYE_Y = -37.5         # 눈 중심 y (머리 앞 표면 근처)
DOME_R = 12.5         # 흰자 돔 반지름 (지름 25, 기준 그림)
DOME_T = 1.4
LID_IN, LID_OUT = 13.3, 14.3   # 눈꺼풀 안·바깥 반지름 (두께 1.0)
POD_IN, POD_OUT = 15.0, 16.5   # 눈 테(눈 상자) 안·바깥 반지름 → 테 지름 33
RIM_AHEAD = 3.5       # 눈 테 앞면이 눈 중심보다 앞에 있는 거리
LID_ARC = 70.0        # 눈꺼풀이 덮는 범위 ±70° (닫았을 때)
LID_OPEN = 135.0      # 눈 뜰 때 눈꺼풀이 위로 도는 각
HUB_R, HUB_X0, HUB_X1 = 2.4, 12.9, 14.8   # 눈꺼풀 양옆 축 고정부 (눈 중심 기준 x)
LEG_PHI = 268.0       # 흰자 돔 고정 다리 방향 (눈꺼풀이 지나지 않는 아래쪽)

PUPIL_D, PUPIL_T = 13.0, 1.5   # 검은자 지름·두께
LENS_D = 7.2                   # 카메라 렌즈 구멍 (렌즈 실측 6.96, 2026-11-07)
CAM_BOX = (8.0, 4.0, 8.0)      # 카메라 모듈 가로·두께·세로 (추정, 실측 필요)
CAM_FRONT = -8.5               # 돔 중심에서 카메라 모듈 앞면까지 (y)

AXLE_D = 2.0          # 눈꺼풀 축 (황동봉 2mm)
AXLE_HALF = EYE_X + 15.8       # 축 끝 = 바깥쪽 눈 테 벽 속
CRANK_R = 5.5         # 크랭크·혼 길이
CRANK_T = 2.0
CRANK_CLOSED = 75.0   # 눈 감았을 때 크랭크 각 φ → 뜨면 +135 = 210°
LINK_D = 1.0
LINK_X = -1.7
EYELID_SERVO_OFS = (15.0, -12.0)   # 크랭크 축 → 눈꺼풀 서보 축 (y, z). 이 방향(약 229°)을 크랭크가 지나면 안 됨
#   2026-11-07: SG51R 실측(바닥~축 끝 24.89)으로 몸통이 길어져 (10.5, -12) 에서는 머리 벽에 닿음 → 뒤로 4.5mm 옮기고 축 둘레로 -18° 돌림 (아래로 옮기면 끄덕임 혼, 덜 옮기면 왼쪽 눈꺼풀과 닿음)

# ── 코 ────────────────────────────────────────────────────
NOSE_DZ = -7.0        # 머리 중심 기준 높이
NOSE_D, NOSE_LEN = 12.0, 14.0
NOSE_PEG_D, NOSE_PEG_LEN = 5.0, 6.0

# ── 눈썹 ───────────────────────────────────────────────────
BROW_X, BROW_DZ = 19.5, 24.0   # 눈썹 가운데 (머리 중심 기준)
BROW_LEN, BROW_H, BROW_T = 15.0, 4.0, 3.0
BROW_GAP = 0.8                 # 눈썹과 머리 표면 사이
BROW_SWING = 20.0              # 눈썹 회전 ±20°
LEVER_R = 33.0                 # 레버가 있는 깊이 (머리 중심에서)
LEVER_LEN = 13.0               # 축 → 레버 끝
SLIDER_Y0, SLIDER_Y1 = -5.0, 19.5   # 슬라이더 아래·위 끝 (서보 축 기준). 아래는 열린 눈꺼풀, 위는 머리 벽에 닿는다
GUIDE_YS, GUIDE_D = (9.6, 13.0), 2.0  # 안내 핀 2개: 서보 축 위 9.6·13 (톱니 덮개가 축 위 8.1 까지 있어 그 뒤 몸통 윗면에)·지름
GUIDE_SLOT = 2 * 4.5 + 2.0 + 0.6   # 홈 길이 = 슬라이더 움직임 ±4.5 + 핀 + 여유
GUIDE_PLATE_T = 1.0
GUIDE_PLATE_W = 11.7           # 안내 판 폭 (서보 폭과 따로 — 서보 실측 12.3 으로 넓히면 틀과 겹친다)
SERVO_GAP = 1.6                # 혼과 서보 윗면 사이 (안내 판 1mm 가 들어감)
HORN_SLOT_W = 5.0              # 슬라이더 가로 홈 폭 (혼 핀이 오가는 x ±1.2 + 핀 + 여유)

# SG51R (자료값 21.5 × 11.7, 높이는 추정)
# SG51R 실측 2026-11-07: 길이 21.61, 폭 12.32(위에서 잰 값 11.82), 바닥~몸통 윗면 19.4, 바닥~축 끝 24.89.
#   몸통 윗면 위에 축 쪽 끝부터 약 13.8 길이의 볼록한 톱니 덮개(높이 약 3.7, 사진 비율)가 있고, 그 위로 흰 축이 약 1.8 나온다.
#   (15.94 는 날개·이음선 높이였다.) 혼은 덮개 윗면(축 어깨)에 얹힌다.
SG_L, SG_W, SG_H = 21.6, 12.3, 19.4
SG_TOTAL = 24.89                      # 바닥 ~ 축 끝
SG_BUMP_L, SG_BUMP_H = 13.8, 3.7      # 톱니 덮개: 축 쪽 끝면부터 길이, 높이
HORN_SEAT = 0.2                       # 혼 아랫면과 덮개 윗면 사이
SG_SHAFT_FROM_END = 5.68              # 실측: 축 먼 쪽 ~ 덮개 끝면 7.59 − 축 지름 3.82/2
HORN_T = 1.6
SG_BUMP_TOP = HORN_T / 2 + HORN_SEAT                 # 혼 가운데 면 → 덮개 윗면 (축 방향 거리)
SG_CASE_TOP = SG_BUMP_TOP + SG_BUMP_H                # 혼 가운데 면 → 몸통 윗면


def sg51r_body() -> Part:
    """SG51R 몸통 + 톱니 덮개. 원점 = 혼 가운데 면의 축 위치, 축은 +z(혼 쪽), 몸통 길이 방향 +y."""
    y0 = -SG_SHAFT_FROM_END
    case = Pos(0, y0 + SG_L / 2, -SG_CASE_TOP - SG_H / 2) * Box(SG_W, SG_L, SG_H)
    bump = Pos(0, y0 + SG_BUMP_L / 2, -SG_BUMP_TOP - SG_BUMP_H / 2) * Box(SG_W, SG_BUMP_L, SG_BUMP_H)
    return case + bump


def center() -> Vector:
    return Vector(0, 0, head_center_z())


def eye_center(side: int) -> tuple[float, float, float]:
    """side = +1 왼쪽 눈, -1 오른쪽 눈(카메라)."""
    return (side * EYE_X, EYE_Y, head_center_z() + EYE_DZ)


def axle_center() -> tuple[float, float, float]:
    _, y, z = eye_center(1)
    return (0.0, y, z)


def phi_dir(phi: float) -> tuple[float, float]:
    """각 φ 방향의 (y, z) 단위 벡터."""
    return (-cos(radians(phi)), sin(radians(phi)))


def along_x(r: float, length: float) -> Part:
    return Rot(0, 90, 0) * Cylinder(r, length)


def along_y(r: float, length: float) -> Part:
    return Rot(90, 0, 0) * Cylinder(r, length)


def wedge(phi_from: float, phi_to: float, r: float = 40, half: float = 40) -> Part:
    """x 축을 둘러싼 부채꼴 기둥 (φ 범위)."""
    steps = max(2, int((phi_to - phi_from) / 5))
    pts = [(0.0, 0.0)]
    for i in range(steps + 1):
        dy, dz = phi_dir(phi_from + (phi_to - phi_from) * i / steps)
        pts.append((r * dy, r * dz))
    return extrude(Plane.YZ * Polygon(*pts, align=None), amount=half, both=True)


# ── 머리 껍데기 가공 ─────────────────────────────────────────
def pod(side: int) -> Part:
    """눈 테: 눈 중심을 둘러싼 구 껍데기 중 앞면(테) 뒤쪽, 머리 안쪽 빈 공간과 겹치는 부분은 뺀다."""
    shell = Sphere(POD_OUT) - Sphere(POD_IN)
    shell &= Pos(0, -RIM_AHEAD + 50, 0) * Box(100, 100, 100)
    return Pos(*eye_center(side)) * shell


def brow_point_dir(side: int) -> Vector:
    """눈썹 가운데의 머리 중심 기준 방향 (단위)."""
    y = -math.sqrt(HEAD_R**2 - BROW_X**2 - BROW_DZ**2)
    return Vector(side * BROW_X, y, BROW_DZ).normalized()


def brow_frame(side: int) -> Location:
    """원점 = 머리 중심, z = 눈썹 방향, x = 가로 접선(바깥 +). 눈썹 축이 이 z 축."""
    r = brow_point_dir(side)
    t = Vector(0, 0, 1).cross(r).normalized()
    if t.X * side < 0:
        t = -t
    return Plane(origin=center(), x_dir=t, z_dir=r).location


def head_add() -> Part:
    inner = Pos(*center()) * Sphere(HEAD_R - WALL)
    add = Part()
    for s in (1, -1):
        add += pod(s) - inner
        add += brow_frame(s) * (Pos(0, 0, HEAD_R - WALL - 1.5) * Cylinder(3.0, 3.0))   # 눈썹 축 받침
    return add


def head_cut() -> Part:
    cut = Part()
    _, ay, az = axle_center()
    for s in (1, -1):
        cut += Pos(*eye_center(s)) * Sphere(POD_IN)
        cut += brow_frame(s) * (Pos(0, 0, HEAD_R) * Cylinder(AXLE_D / 2 + 0.15, 16))
        # 눈꺼풀 축이 바깥쪽 눈 테 벽을 뚫고 지나는 구멍 (2026-11-07 1차 출력에서 빠진 것 발견).
        # 축은 한쪽 구멍으로 밀어 넣고, 끝은 벽 바깥면에 맞춰 자른다.
        cut += Pos(s * (EYE_X + POD_OUT), ay, az) * along_x(AXLE_D / 2 + 0.15, 6)
    cz = head_center_z()
    ny = -math.sqrt(HEAD_R**2 - NOSE_DZ**2)
    cut += Pos(0, ny + NOSE_PEG_LEN / 2, cz + NOSE_DZ) * along_y(NOSE_PEG_D / 2 + 0.15, NOSE_PEG_LEN + 2)
    return cut


# ── 눈 부품 ─────────────────────────────────────────────────
def dome(side: int) -> Part:
    shell = Sphere(DOME_R) - Sphere(DOME_R - DOME_T)
    shell &= Pos(0, -50 + 2, 0) * Box(100, 100, 100)
    shell -= Pos(0, -DOME_R, 0) * along_y(PUPIL_D / 2, 2 * PUPIL_T)
    shell -= along_x(AXLE_D / 2 + 0.15, 2 * DOME_R + 4)
    y, z = phi_dir(LEG_PHI)
    leg = Pos(0, y * (DOME_R + 1.5), z * (DOME_R + 1.5)) * Rot(-LEG_PHI + 90, 0, 0) * Box(4, 2, 6)
    return Pos(*eye_center(side)) * (shell + (leg - Sphere(DOME_R - DOME_T)))


def pupil(side: int) -> Part:
    """검은자: 앞면이 돔과 같은 곡면(반지름 +0.3)이라 눈꺼풀이 걸리지 않는다."""
    p = (Sphere(DOME_R + 0.3) - Sphere(DOME_R - PUPIL_T)) & (Pos(0, -DOME_R, 0) * along_y(PUPIL_D / 2 - 0.1, 2 * PUPIL_T + 2))
    if side < 0:
        p -= Pos(0, -DOME_R, 0) * along_y(LENS_D / 2, 10)
    return Pos(*eye_center(side)) * p


def camera() -> Part:
    body = Pos(0, CAM_FRONT + CAM_BOX[1] / 2, 0) * Box(*CAM_BOX)
    lens = Pos(0, (CAM_FRONT - DOME_R + 0.3) / 2, 0) * along_y(LENS_D / 2 - 0.2, DOME_R + CAM_FRONT - 0.3)
    return Pos(*eye_center(-1)) * (body + lens)


def eyelid(side: int, opened: float) -> Part:
    """opened: 0 = 감음, 1 = 뜸."""
    lid = (Sphere(LID_OUT) - Sphere(LID_IN)) & wedge(-LID_ARC, LID_ARC)
    for s in (1, -1):
        hub = Pos(s * (HUB_X0 + HUB_X1) / 2, 0, 0) * along_x(HUB_R, HUB_X1 - HUB_X0)
        lid += hub & Sphere(LID_OUT + 1)
    lid -= along_x(AXLE_D / 2, 40)
    return Pos(*eye_center(side)) * Rot(-LID_OPEN * opened, 0, 0) * lid


def axle() -> Part:
    return Pos(*axle_center()) * along_x(AXLE_D / 2, 2 * AXLE_HALF)


def arm(r: float, t: float, phi: float) -> Part:
    """축 중심에서 φ 방향으로 뻗은 팔 (크랭크·혼 공용). x 두께 t."""
    a = along_x(2.2, t) + Pos(0, -r / 2, 0) * Box(t, r, 3.6) + Pos(0, -r, 0) * along_x(1.8, t)
    a -= along_x(AXLE_D / 2, 10)
    a -= Pos(0, -r, 0) * along_x(LINK_D / 2 + 0.1, 10)
    return Rot(-phi, 0, 0) * a


def rod(p0: tuple[float, float, float], p1: tuple[float, float, float], d: float) -> Part:
    v = Vector(*p1) - Vector(*p0)
    mid = (Vector(*p0) + Vector(*p1)) * 0.5
    return Plane(origin=mid, z_dir=v.normalized()).location * Cylinder(d / 2, v.length)


def eyelid_servo_center() -> tuple[float, float, float]:
    x, y, z = axle_center()
    return (x, y + EYELID_SERVO_OFS[0], z + EYELID_SERVO_OFS[1])


EYELID_SERVO_ROLL = -18.0   # 눈꺼풀 서보를 축 둘레로 돌린 각 (뒤 아래 모서리를 끄덕임 혼 위로 올림)


def eyelid_servo_location() -> Location:
    """눈꺼풀 서보 좌표계: 원점 = 혼 가운데 면의 축, 로컬 +z = 혼 쪽(-x), 로컬 +y = 몸통 길이(뒤)."""
    return Plane(origin=eyelid_servo_center(), x_dir=Vector(0, 0, 1), z_dir=Vector(-1, 0, 0)).location * Rot(0, 0, EYELID_SERVO_ROLL)


def eyelid_linkage(opened: float) -> dict[str, Part]:
    phi = CRANK_CLOSED + LID_OPEN * opened
    ax = axle_center()
    hs = eyelid_servo_center()
    dy, dz = phi_dir(phi)
    tip_a = (LINK_X, ax[1] + CRANK_R * dy, ax[2] + CRANK_R * dz)
    tip_h = (LINK_X, hs[1] + CRANK_R * dy, hs[2] + CRANK_R * dz)
    servo = eyelid_servo_location() * sg51r_body()   # 몸통이 +x 로
    return {
        "eyelid_crank": Pos(*ax) * arm(CRANK_R, CRANK_T, phi),
        "eyelid_horn": Pos(*hs) * arm(CRANK_R, HORN_T, phi),
        "eyelid_link": rod(tip_a, tip_h, LINK_D),
        "sg51r_eyelid": servo,
    }


def nose() -> Part:
    """당근 코: 밑면 지름 12, 길이 14 원뿔(끝 뭉툭) + 머리에 꽂는 핀. 밑면은 머리 표면에 0.5mm 묻힌다. 홈은 출력용 모델에서."""
    cz = head_center_z()
    ny = -math.sqrt(HEAD_R**2 - NOSE_DZ**2)
    cone = Pos(0, ny - NOSE_LEN / 2 + 0.5, cz + NOSE_DZ) * Rot(90, 0, 0) * Cone(NOSE_D / 2, NOSE_D / 2 * 0.45, NOSE_LEN)
    tip = Pos(0, ny - NOSE_LEN + 0.5, cz + NOSE_DZ) * Sphere(NOSE_D / 2 * 0.45)
    peg = Pos(0, ny + NOSE_PEG_LEN / 2, cz + NOSE_DZ) * along_y(NOSE_PEG_D / 2, NOSE_PEG_LEN)
    return cone + tip + peg


def brows(swing: float) -> dict[str, Part]:
    """swing: -1 ~ 1. +1 이면 눈썹 안쪽 끝이 올라간다 (걱정·슬픔), -1 이면 내려간다 (화남)."""
    parts: dict[str, Part] = {}
    ang = BROW_SWING * swing
    r_out0 = HEAD_R + BROW_GAP
    band = Sphere(r_out0 + BROW_T) - Sphere(r_out0)
    band &= Pos(0, 0, HEAD_R) * Box(BROW_LEN, BROW_H, 40)
    shaft_len = r_out0 + BROW_T / 2 - (LEVER_R - 1)
    shaft = Pos(0, 0, (r_out0 + BROW_T / 2 + LEVER_R - 1) / 2) * Cylinder(AXLE_D / 2, shaft_len)
    tips = []
    for s, name in ((1, "left"), (-1, "right")):
        frame = brow_frame(s)
        rot = Rot(0, 0, -s * ang)
        lever = Pos(-LEVER_LEN / 2 + 1.5, 0, LEVER_R) * Box(LEVER_LEN + 3, 4.0, 2.0)
        lever -= Pos(-LEVER_LEN, 0, LEVER_R) * Box(5, 1.4, 4)      # 슬라이더 핀이 미끄러지는 홈
        parts[f"brow_{name}"] = frame * rot * band
        parts[f"brow_shaft_{name}"] = frame * rot * shaft
        parts[f"brow_lever_{name}"] = frame * rot * lever
        tips.append(frame * rot * Pos(-LEVER_LEN, 0, LEVER_R))
    # 슬라이더: 두 레버 끝 사이, 레버 바로 뒤 판. 레버 끝이 움직이는 방향(위·뒤)으로 미끄러진다
    # 서보·혼·안내 핀은 눈썹이 가운데일 때의 레버 끝 위치(c0)에 고정, 슬라이더만 위아래로 움직인다
    c0 = brow_tip_mid(0.0)
    up = Vector(0, 0.457, 0.889)
    normal = Vector(1, 0, 0).cross(up)            # 판의 앞쪽 방향
    back = -normal.normalized()
    slide = LEVER_LEN * sin(radians(ang))
    tip_x = abs(tips[0].position.X)
    slider_frame = Plane(origin=c0 + up * slide + back * 2.2, x_dir=Vector(1, 0, 0), z_dir=-back).location
    slider = Pos(0, (SLIDER_Y0 + SLIDER_Y1) / 2, 0) * Box(2 * tip_x + 3, SLIDER_Y1 - SLIDER_Y0, 2.0)
    slider -= Box(HORN_SLOT_W, 1.4, 4)                                           # 가운데 가로 홈: 혼 핀 (판을 끊지 않는 폭)
    for gy in GUIDE_YS:
        slider -= Pos(0, gy - slide, 0) * Box(GUIDE_D + 0.4, GUIDE_SLOT, 4)      # 세로 홈: 안내 핀 (슬라이더와 같이 움직임)
    parts["brow_slider"] = slider_frame * slider
    # 눈썹 서보: 슬라이더 뒤, 축이 판에 수직. 혼 끝 핀이 슬라이더 가로 홈을 민다
    horn_r = 5.5                                  # 눈썹 ±20° → 슬라이더 ±4.4 → 혼 약 ±54°
    # 혼 핀은 가운데에서 x = r, 끝에서 r·cos54° 로 오간다 → 축을 그 가운데만큼 옆으로 옮겨 핀이 슬라이더 가운데에서 움직이게
    shift = horn_r * (1 + math.cos(math.radians(54))) / 2
    horn_frame = Plane(origin=c0 - Vector(shift, 0, 0) + back * (2.2 + 1.0 + 0.4 + HORN_T / 2), x_dir=Vector(1, 0, 0), z_dir=-back).location
    horn_ang = math.degrees(math.asin(max(-1.0, min(1.0, slide / horn_r)))) if ang else 0.0
    horn = Rot(0, 0, horn_ang) * Pos(horn_r / 2, 0, 0) * Box(horn_r + 3.6, 3.6, HORN_T)
    parts["brow_horn"] = horn_frame * horn
    # 안내 블록: 톱니 덮개 뒤 몸통 윗면에 붙이는 블록 + 핀 2개 (슬라이더 세로 홈을 지난다)
    face_z = -(HORN_T / 2 + SERVO_GAP)                       # 블록 윗면 = 예전 안내 판 아랫면 높이
    gy0 = -SG_SHAFT_FROM_END + SG_BUMP_L + 0.2               # 덮개 바로 뒤
    gy1 = GUIDE_YS[1] + 2.5
    z0 = -SG_CASE_TOP
    guide = Pos(0, (gy0 + gy1) / 2, (z0 + face_z + GUIDE_PLATE_T) / 2) * Box(GUIDE_PLATE_W, gy1 - gy0, face_z + GUIDE_PLATE_T - z0)   # 핀(x=shift)도 서보 폭 안
    for gy in GUIDE_YS:
        pin_len = (2.2 + 1.0 + 0.4 + HORN_T / 2 + 1.5) - (face_z + GUIDE_PLATE_T)
        guide += Pos(shift, gy, face_z + GUIDE_PLATE_T + pin_len / 2) * Cylinder(GUIDE_D / 2, pin_len)   # 슬라이더 가운데
    parts["brow_guide"] = horn_frame * guide
    # 서보 몸체는 판을 따라 위(뒤쪽 위)로 세운다 — 옆으로 눕히면 레버에 닿는다
    parts["sg51r_brow"] = horn_frame * sg51r_body()
    return parts


def brow_tip_mid(ang: float) -> Vector:
    """두 레버 끝의 가운데 점 (눈썹 각 ang)."""
    pts = []
    for s in (1, -1):
        pts.append((brow_frame(s) * Rot(0, 0, -s * ang) * Pos(-LEVER_LEN, 0, LEVER_R)).position)
    return (pts[0] + pts[1]) * 0.5


def eye_parts(opened: float = 1.0, swing: float = 0.0) -> dict[str, Part]:
    p: dict[str, Part] = {}
    for s, name in ((1, "left"), (-1, "right")):
        p[f"dome_{name}"] = dome(s)
        p[f"pupil_{name}"] = pupil(s)
        p[f"eyelid_{name}"] = eyelid(s, opened)
    p["camera"] = camera()
    p["eyelid_axle"] = axle()
    p["nose"] = nose()
    p.update(eyelid_linkage(opened))
    p.update(brows(swing))
    return p


# 서로 맞닿아 움직이도록 만든 짝 — 겹침 검사에서 뺀다
JOINED = {
    frozenset(x) for x in [
        ("eyelid_axle", "dome_left"), ("eyelid_axle", "dome_right"),
        ("eyelid_axle", "eyelid_left"), ("eyelid_axle", "eyelid_right"), ("eyelid_axle", "eyelid_crank"),
        ("eyelid_axle", "head"),
        ("eyelid_link", "eyelid_crank"), ("eyelid_link", "eyelid_horn"), ("eyelid_horn", "sg51r_eyelid"),
        ("dome_left", "pupil_left"), ("dome_right", "pupil_right"), ("camera", "pupil_right"), ("camera", "dome_right"),
        ("brow_left", "brow_shaft_left"), ("brow_right", "brow_shaft_right"),
        ("brow_lever_left", "brow_shaft_left"), ("brow_lever_right", "brow_shaft_right"),
        ("brow_shaft_left", "head"), ("brow_shaft_right", "head"),
        ("brow_lever_left", "brow_slider"), ("brow_lever_right", "brow_slider"),
        ("brow_horn", "brow_slider"), ("brow_horn", "sg51r_brow"), ("brow_guide", "sg51r_brow"),
        ("nose", "head"), ("dome_left", "head"), ("dome_right", "head"),
    ]
}
# 머리 껍데기를 뚫고 지나가는 부품 (THROUGH_SHELL 은 JOINED 에 "head" 짝으로 넣었다)
THROUGH_SHELL: set[str] = set()
