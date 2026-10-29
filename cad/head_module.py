"""3단계: 머리 안 틀 + 머리 껍데기 나누기.

좌표·단위는 snowbot_layout.py 와 같다 (x 왼쪽+, y 뒤+, z 위, mm).

구조 (자세한 이유는 docs/05-cad-stages.md 3단계):
- 머리 껍데기를 머리 중심 12mm 뒤(y=SEAM_Y)의 세로 면에서 앞(얼굴)·뒤(덮개)로 나눈다. 기준 그림의 옆 이음선 자리.
- 이음선을 따라 6곳: 앞 껍데기 안쪽에 탭을 두고 M2 열압입 인서트를 박는다. 뒤 덮개 밖에서 육각 M2 나사가
  머리 중심 쪽(지름 방향)으로 들어간다 — 기준 그림처럼 나사 머리가 옆에서 보인다.
- 뒤 덮개: 가로 환기 슬롯 5줄, 그 위 마이크 구멍 무리 (기준 그림 뒤 모습).
- 머리 안 틀(한 덩어리, 출력 1개): 세로 판(척추) + 끄덕임 서보 고정 창 + 뒤 받침판과 핀 받침 + 눈꺼풀 서보 뒤 블록 +
  눈썹 서보 옆 받침 + XIAO 받침과 가로 막대 + 앞 껍데기에 나사로 붙는 탭 3곳.
  서보·XIAO·앰프를 틀에 먼저 붙이고, 틀을 뒤쪽 열린 곳으로 넣어 앞 껍데기에 고정한 다음 뒤 덮개를 닫는다.
"""

import math

from build123d import Box, Cylinder, Part, Pos, Rot, Sphere

from snowbot_layout import HEAD_R, WALL, head_center_z

SEAM_Y = 12.0           # 앞·뒤 나누는 면
SCREW_ANGLES = (10.0, 75.0, 105.0, 170.0, -35.0, 215.0)   # 이음선 둘레 각 (x+ 에서 위로)
SCREW_BACK = 4.0        # 이음선 뒤 몇 mm 에 나사가 들어가나
INSERT_D, INSERT_L = 3.0, 4.0     # M2 인서트 구멍 (인서트 바깥지름 3.2 → 녹여 박을 구멍 3.0, 시험 출력으로 정함)
SCREW_D, HEAD_D, HEAD_SINK = 2.4, 4.2, 1.0   # 나사 구멍, 나사 머리 자리 지름·깊이
TAB_W, TAB_T, TAB_LEN = 8.0, 4.0, 10.0        # 인서트 탭 폭·두께(지름 방향)·길이(y, 이음선 앞 3mm 포함)

VENT_N, VENT_W, VENT_H, VENT_PITCH, VENT_DZ = 5, 26.0, 2.2, 4.4, -2.0   # 환기 슬롯
MIC_HOLES_DZ, MIC_HOLE_D = 22.0, 1.2

SPINE_X0, SPINE_X1 = 6.5, 9.0     # 세로 판 (끄덕임 서보 날개 바로 옆)
SPINE_TOP = 32.0                  # 세로 판 꼭대기 (머리 중심 기준)
# 틀을 앞 껍데기에 붙이는 자리 (x, 귀 앞면 y, 머리 중심 기준 z). 앞 껍데기 안쪽 벽에서 기둥이 뒤로 뻗어 나온다
MOUNTS = ((10.5, -12.5, 30.0), (-33.0, -4.5, 8.0), (33.0, -11.0, -9.0))
LUG_W, LUG_T = 7.0, 2.5
BOSS_D = 7.0
SPINE_Y0, SPINE_Y1 = -11.0, 24.5
CLEAR = 1.0                       # 틀과 머리 안쪽 벽 사이
XIAO_X = -19.6                    # XIAO 가운데 x (눈썹 서보 옆)
BROW_SERVO_X1 = 1.45              # 눈썹 서보 왼쪽 옆면 x (eye_module 위치에서 옴) — 받침이 여기까지


def cz() -> float:
    return head_center_z()


def back_half() -> Part:
    return Pos(0, SEAM_Y + 100, 0) * Box(300, 200, 400)


def front_half() -> Part:
    return Pos(0, SEAM_Y - 100, 0) * Box(300, 200, 400)


def screw_axis(angle: float) -> tuple[float, float, float]:
    """이음선 뒤 SCREW_BACK 위치에서 머리 중심 쪽 방향 단위벡터의 (x, z) 성분과 y."""
    a = math.radians(angle)
    return math.cos(a), SEAM_Y + SCREW_BACK, math.sin(a)


def tabs() -> Part:
    """앞 껍데기 안쪽, 이음선 뒤로 튀어나온 인서트 탭 (뒤 덮개 안쪽 벽에 붙는다)."""
    out = Part()
    r_in = HEAD_R - WALL
    for ang in SCREW_ANGLES:
        _, y, _ = screw_axis(ang)
        # 인서트 부분: 이음선 뒤, 뒤 덮개 안쪽 벽 바로 안
        r_mid = math.sqrt(r_in**2 - y**2) - TAB_T / 2 - 0.2
        tab = Pos(r_mid, SEAM_Y + (TAB_LEN - 3) / 2 - y, 0) * Box(TAB_T, TAB_LEN - 3, TAB_W)
        # 뿌리: 이음선 앞 앞 껍데기 벽 속에 묻혀 탭을 붙잡는다
        y_root = SEAM_Y - 2.5
        r_wall = math.sqrt(r_in**2 - y_root**2)
        root = Pos((r_mid - TAB_T / 2 + r_wall + 1.0) / 2, y_root - y, 0) * Box(r_wall + 1.0 - (r_mid - TAB_T / 2), 5.0, TAB_W)
        tab = tab + root
        tab -= Pos(r_mid + TAB_T / 2 - INSERT_L / 2, 0, 0) * Rot(0, 90, 0) * Cylinder(INSERT_D / 2, INSERT_L + 0.01)
        out += Pos(0, y, cz()) * Rot(0, -ang, 0) * tab
    return out


def screw_holes() -> Part:
    out = Part()
    for ang in SCREW_ANGLES:
        _, y, _ = screw_axis(ang)
        r_surf = math.sqrt(HEAD_R**2 - y**2)          # 이 높이(y)에서 머리 바깥면까지
        hole = Pos(r_surf - 3, 0, 0) * Rot(0, 90, 0) * Cylinder(SCREW_D / 2, 12)
        sink = Pos(r_surf - HEAD_SINK / 2 + 0.5, 0, 0) * Rot(0, 90, 0) * Cylinder(HEAD_D / 2, HEAD_SINK + 1)
        out += Pos(0, y, cz()) * Rot(0, -ang, 0) * (hole + sink)
    return out


def screws() -> Part:
    """나사 (M2 × 6, 버튼 머리) — 그림용."""
    out = Part()
    for ang in SCREW_ANGLES:
        _, y, _ = screw_axis(ang)
        r_surf = math.sqrt(HEAD_R**2 - y**2)
        shank = Pos(r_surf - HEAD_SINK - 3, 0, 0) * Rot(0, 90, 0) * Cylinder(1.0, 6)
        head = Pos(r_surf - HEAD_SINK + 0.6, 0, 0) * Rot(0, 90, 0) * Cylinder(HEAD_D / 2 - 0.3, 1.6)   # 0.2mm 만 튀어나옴
        out += Pos(0, y, cz()) * Rot(0, -ang, 0) * (shank + head)
    return out


def back_cover_cuts() -> Part:
    out = Part()
    top = VENT_DZ + (VENT_N - 1) * VENT_PITCH / 2
    for i in range(VENT_N):
        out += Pos(0, HEAD_R - 3, cz() + top - i * VENT_PITCH) * Box(VENT_W, 12, VENT_H)
    for dx, dz in ((0, 0), (-2.2, 0), (2.2, 0), (-1.1, 1.9), (1.1, 1.9), (-1.1, -1.9), (1.1, -1.9)):
        out += Pos(dx, HEAD_R - 3, cz() + MIC_HOLES_DZ + dz) * Rot(90, 0, 0) * Cylinder(MIC_HOLE_D / 2, 12)
    return out


def bosses() -> Part:
    """앞 껍데기 안쪽 벽에서 뒤로 뻗은 기둥. 뒤끝에 M2 인서트."""
    c = cz()
    out = Part()
    outer = Pos(0, 0, c) * Sphere(HEAD_R - 0.3)
    for x, y, z in MOUNTS:
        y_wall = -math.sqrt(max((HEAD_R - WALL) ** 2 - x**2 - z**2, 0.0))
        y0 = y_wall - 1.5
        b = Pos(x, (y0 + y - 0.1) / 2, c + z) * Rot(90, 0, 0) * Cylinder(BOSS_D / 2, (y - 0.1) - y0)
        b -= Pos(x, y - INSERT_L / 2, c + z) * Rot(90, 0, 0) * Cylinder(INSERT_D / 2, INSERT_L + 0.2)
        out += b & outer
    return out


def split(shell: Part) -> tuple[Part, Part]:
    front = (shell & front_half()) + tabs() + bosses()
    back = (shell & back_half()) - screw_holes() - back_cover_cuts() - tabs()
    return front, back


# ── 머리 안 틀 ──────────────────────────────────────────────
def chassis() -> Part:
    """머리 안 틀 (끄덕이기 전 위치). 치수는 eye_module·neck_module 의 부품 위치에 맞춘다."""
    import neck_module as nm

    c = cz()
    inner_clip = Pos(0, 0, c) * Sphere(HEAD_R - WALL - CLEAR)
    above_bottom = Pos(0, 0, nm.HEAD_BOTTOM_Z + WALL + 0.6 + 100) * Box(300, 300, 200)

    # 1) 세로 판 — 끄덕임 서보 몸체가 지나는 창
    spine = Pos((SPINE_X0 + SPINE_X1) / 2, (SPINE_Y0 + SPINE_Y1) / 2, c + 4) * Box(SPINE_X1 - SPINE_X0, SPINE_Y1 - SPINE_Y0, 64)
    nod_window = Pos(0, (-6.0 + 16.8) / 2, nm.NOD_Z) * Box(40, 16.8 + 6.0 + 1.0, nm.MG_W + 1.0)
    spine -= nod_window
    # 2) 눈썹 서보 옆 받침 (세로 판에서 서보 옆면까지)
    brow_pad = Pos((BROW_SERVO_X1 + 0.1 + SPINE_X0) / 2, -11.0, c + 16) * Box(SPINE_X0 - BROW_SERVO_X1 - 0.1 + 0.01, 18, 26)
    # 3) 눈꺼풀 서보 뒤 블록
    lid_block = Pos(10.3, -9.5, c - 9.0) * Box(17.0, 3.0, 11.0)
    # 4) 끄덕임 서보 뒤 받침판 + 핀 받침 (반대쪽 요크 판의 핀이 들어온다)
    back_plate = Pos(-4.0, 23.0, nm.NOD_Z) * Box(26.0, 3.0, 12.0)
    pivot_arm = Pos(-15.0, 9.5, nm.NOD_Z) * Box(4.0, 30.0, 8.0)
    pivot_hole = Pos(-15.0, 0, nm.NOD_Z) * Rot(0, 90, 0) * Cylinder(nm.PIVOT_D / 2 + 0.15, 10)
    # 5) XIAO 받침 (뒤판 + 아래 턱) + 세로 판까지 가로 막대
    xiao_back = Pos(XIAO_X, -5.5, c + 0.25) * Box(17.8, 2.0, 25.5)
    xiao_ledge = Pos(XIAO_X, -13.0, c - 9.5) * Box(17.8, 17.0, 2.0)
    beam = Pos((XIAO_X + 8.9 + SPINE_X0) / 2, -5.5, c - 10.5) * Box(SPINE_X0 - XIAO_X - 8.9, 2.0, 3.0)
    # 6) 앞 껍데기 기둥에 나사로 붙는 귀 3곳 (나사는 뒤에서 앞으로)
    lugs = Part()
    for x, y, z in MOUNTS:
        lugs += Pos(x, y + LUG_T / 2, c + z) * Box(LUG_W, LUG_T, LUG_W)
        lugs -= Pos(x, y, c + z) * Rot(90, 0, 0) * Cylinder(SCREW_D / 2, 10)
    lug_arms = (Pos((SPINE_X1 + MOUNTS[0][0]) / 2, MOUNTS[0][1] + 1.5, c + MOUNTS[0][2]) * Box(MOUNTS[0][0] - SPINE_X0 + 3, 3, LUG_W)
                + Pos((XIAO_X - 8.9 + MOUNTS[1][0]) / 2, MOUNTS[1][1] + 1.5, c + MOUNTS[1][2]) * Box(abs(MOUNTS[1][0] - XIAO_X + 8.9) + 3, 3, LUG_W)
                + Pos((18.8 + MOUNTS[2][0]) / 2, MOUNTS[2][1] + 1.5, c + MOUNTS[2][2]) * Box(abs(MOUNTS[2][0] - 18.8) + 3, 3, LUG_W))

    spine &= Pos(0, 0, c + SPINE_TOP - 100) * Box(300, 300, 200)    # 이음선 나사 탭(75°)을 피해 꼭대기를 낮춤
    body = spine + brow_pad + lid_block + back_plate + pivot_arm + xiao_back + xiao_ledge + beam + lugs + lug_arms
    for x, y, z in MOUNTS:
        body -= Pos(x, y, c + z) * Rot(90, 0, 0) * Cylinder(SCREW_D / 2, 10)
    body &= inner_clip
    body &= above_bottom
    body -= pivot_hole
    return body


def amp() -> Part:
    """MAX98357A: 세로 판 바깥(+x)쪽에 기둥으로 띄워 붙인다."""
    return Pos(SPINE_X1 + 0.5 + 1.5, 1.75, cz() + 3.0) * Box(3.0, 19.5, 17.8)


JOINED = {
    frozenset(x) for x in [
        ("chassis", "head"), ("chassis", "mg90s_nod"), ("chassis", "sg51r_eyelid"), ("chassis", "sg51r_brow"),
        ("chassis", "xiao"), ("chassis", "amp"), ("head", "head_back"),
        ("screws", "head_back"), ("screws", "head"),
    ]
}
