"""4단계: 몸통·바닥 — 몸통 껍데기, 목 받침판(베어링·좌우 서보), 바닥판·무게추·고무발, 스피커·USB-C 자리.

좌표·단위는 snowbot_layout.py 와 같다 (x 왼쪽+, y 뒤+, z 위, mm). 바닥이 z=0.

구조 (자세한 이유는 docs/05-cad-stages.md 4단계):
- 몸통은 기준 그림처럼 아래가 둥글게 말려 들어간 공(지름 81, 중심 높이 36, 바닥 평면 지름 약 37).
  한 덩어리로 출력한다 — 위를 바닥에 놓고 거꾸로 출력하면 지지대 없이 껍데기가 나온다.
- 위가 크게 열려 있고(지름 55), **목 받침판**이 덮는다. 받침판에 베어링 자리·선 지나는 반달 홈·좌우 서보 기둥이 붙어 있어
  베어링과 서보 축이 한 부품 안에서 맞는다. 받침판은 몸통 안 기둥 4곳에 나사로 고정.
- 아래 구멍(지름 24)은 **바닥판**이 덮는다. 몸통 안 바닥 고리 위에 M24 강철 와셔 2장(약 64 g)을 에폭시로 붙인 무게추,
  바닥 전체에 실리콘 미끄럼 방지 패드(지름 36). 바닥이 작아(기준 그림) 고무발 4개로는 지지 폭이 좁다.
- 앞: 스피커 그릴(점 구멍), 그 둘레 육각 나사 4개가 안쪽 스피커 누름 고리를 잡는다. 오른쪽 아래 "SNW-01" 각인.
- 뒤: USB-C 구멍(바닥에서 23mm, 와셔 위). USB-C 보드는 뒤 벽에 매단 선반에 놓는다.
- 옆: 기준 그림의 세로 이음선 자리에 장식 홈.
"""

import math

from build123d import Box, Cylinder, Part, Plane, Pos, Rot, Sphere, Text, extrude

BODY_R = 40.5           # 몸통 반지름 (지름 81, 기준 그림)
BODY_CZ = 33.0          # 몸통 중심 높이 → 바닥 평면 지름 약 47 (기준 그림 37 → 넘어짐 여유 때문에 넓힘, 사용자 승인 2026-10-26)
WALL = 2.5
TOP_Z = 60.0            # 몸통 윗면 (= 목 받침판 윗면, neck_module.BODY_TOP_Z 와 같아야 함)
TOP_OPEN_R = 26.0       # 위 구멍
PLATE_R, PLATE_T = 27.5, 3.0      # 목 받침판 (위 구멍보다 1.5 크고, 몸통 턱에 얹힌다)
PLATE_SCREW_R = 24.5    # 받침판 나사 위치 (회전판 가장자리 바로 밖, 머리를 묻어 회전판과 닿지 않음)
BOTTOM_OPEN_R = 12.0    # 아래 구멍
BASE_R, BASE_T = 16.0, 3.0        # 바닥판 (몸통 바닥 홈에 묻힘)
BASE_SCREW_R = 15.0
PAD_R, PAD_T = 23.0, 1.5          # 미끄럼 방지 패드 (바닥 전체, 지름 46) — 고무발 4개는 지지 폭이 좁다
WASHER_OD, WASHER_T, WASHER_N = 44.0, 4.0, 2      # M24 평와셔 (약 32 g × 2), 몸통 바닥 고리 위에 에폭시로 붙임.
#   2026-11-07: 목 서보가 실측 31.6 으로 길어져 3장(높이 12)이면 서보 바닥(z 18.9)과 1.1mm 겹침 → 2장(높이 8)
FLOOR_Z = 8.0                     # 몸통 안 바닥 고리 윗면 (바닥판 나사 인서트가 들어갈 두께)
INSERT_D, INSERT_L, SCREW_D = 3.0, 4.0, 2.2   # 공차 판 A: 인서트 3.0 · 나사 2.2 (2026-11-07)
MOUNT_T, MOUNT_HALF_W, POST_Y = 3.0, 15.0, 12.5   # 좌우 서보 받침판 두께·반폭, 기둥 위치 (혼 반지름 10 바깥)

SPK_D, SPK_T = 28.0, 5.5          # 스피커 (실측 27.84 × 5.5, 2026-11-07)
SPK_Z = 36.0                      # 기준 그림: 그릴 가운데가 몸통 중심 높이
GRILLE_R, GRILLE_HOLE_D, GRILLE_PITCH = 11.0, 1.5, 3.0
SPK_SCREW_OFS = 14.0              # 그릴 가운데에서 누름 고리 나사까지 (대각선 4곳)

USB_Z = 23.0                      # USB-C 구멍 높이 (와셔 위)
USB_SLOT = (9.6, 3.8)             # 구멍 가로·세로 (USB-C 단자 8.9 × 3.2 + 여유)
USB_BOARD = (12.0, 12.0, 1.6)     # USB-C 보드 (추정, 실측 필요 — 15 이상이면 와셔와 닿음)

TEXT = "SNW-01"
TEXT_POS = (12.0, 18.0)           # 앞에서 본 x, z (기준 그림: 오른쪽 아래)
SEAM_Y = 2.0                      # 옆 장식 홈 위치


def sphere_y(x: float, z: float, r: float) -> float:
    """몸통 구 위 (x, z) 에서 앞면(-y) 좌표."""
    return -math.sqrt(max(r**2 - x**2 - (z - BODY_CZ) ** 2, 0.0))


def big(z0: float, z1: float) -> Part:
    return Pos(0, 0, (z0 + z1) / 2) * Box(300, 300, z1 - z0)


def shell() -> Part:
    outer = Pos(0, 0, BODY_CZ) * Sphere(BODY_R) & big(0, TOP_Z)
    inner = Pos(0, 0, BODY_CZ) * Sphere(BODY_R - WALL) & big(FLOOR_Z, TOP_Z - PLATE_T)
    s = outer - inner
    s -= Pos(0, 0, TOP_Z) * Cylinder(TOP_OPEN_R, 2 * PLATE_T + 0.01)                         # 위 구멍
    s -= Pos(0, 0, TOP_Z - PLATE_T / 2) * Cylinder(PLATE_R + 0.2, PLATE_T + 0.01)           # 받침판 턱
    s -= Pos(0, 0, 0) * Cylinder(BOTTOM_OPEN_R, 20)                                          # 아래 구멍
    s -= Pos(0, 0, BASE_T / 2) * Cylinder(BASE_R + 0.2, BASE_T + 0.01)                       # 바닥판 홈
    # 받침판 나사 기둥 (몸통 벽에서 안으로)
    for i in range(4):
        a = math.radians(45 + 90 * i)
        x, y = PLATE_SCREW_R * math.cos(a), PLATE_SCREW_R * math.sin(a)
        boss = Pos(x, y, TOP_Z - PLATE_T - 4) * Cylinder(3.5, 8)
        boss += Rot(0, 0, 45 + 90 * i) * Pos(PLATE_SCREW_R + 6, 0, TOP_Z - PLATE_T - 4) * Box(12, 5, 8)   # 벽까지 잇는 다리
        boss &= Pos(0, 0, BODY_CZ) * Sphere(BODY_R - 0.5)
        s += boss
        s -= Pos(x, y, TOP_Z - PLATE_T - INSERT_L / 2) * Cylinder(INSERT_D / 2, INSERT_L + 0.01)
    # 바닥판 나사 인서트 (몸통 바닥 고리 안)
    for i in range(3):
        a = math.radians(90 + 120 * i)
        s -= Pos(BASE_SCREW_R * math.cos(a), BASE_SCREW_R * math.sin(a), BASE_T + INSERT_L / 2) * Cylinder(INSERT_D / 2, INSERT_L + 0.01)
    # 스피커 그릴 (점 구멍)
    for gx, gz in grille_points():
        s -= Pos(gx, -BODY_R, gz) * Rot(90, 0, 0) * Cylinder(GRILLE_HOLE_D / 2, 30)
    for sx, sz in speaker_screw_points():
        s -= Pos(sx, -BODY_R, sz) * Rot(90, 0, 0) * Cylinder(SCREW_D / 2, 30)
        s -= Pos(sx, sphere_y(sx, sz, BODY_R) + 0.5, sz) * Rot(90, 0, 0) * Cylinder(2.1, 2.0)   # 나사 머리 자리
    # USB-C 구멍 (뒤 아래)
    s -= Pos(0, BODY_R, USB_Z) * Box(USB_SLOT[0], 30, USB_SLOT[1])
    # 각인
    tx, tz = TEXT_POS
    ty = sphere_y(tx, tz, BODY_R)
    s -= Pos(tx, ty + 0.5, tz) * extrude(Plane.XZ * Text(TEXT, font_size=3.2, font="Arial"), amount=3)
    s -= Pos(tx + 0.3, ty + 0.5, tz - 2.6) * Box(9.5, 3, 0.35)                                  # 밑줄
    # 옆 장식 홈 (기준 그림의 세로 이음선)
    groove = (Pos(0, 0, BODY_CZ) * Sphere(BODY_R + 1) - Pos(0, 0, BODY_CZ) * Sphere(BODY_R - 0.5)) & (Pos(0, SEAM_Y, 0) * Box(300, 0.8, 300))
    s -= groove & big(BASE_T + 1, TOP_Z - 1)
    return s


def grille_points() -> list[tuple[float, float]]:
    pts = []
    rows = int(GRILLE_R / (GRILLE_PITCH * 0.866)) + 1
    for j in range(-rows, rows + 1):
        z = j * GRILLE_PITCH * 0.866
        off = GRILLE_PITCH / 2 if j % 2 else 0.0
        for i in range(-6, 7):
            x = i * GRILLE_PITCH + off
            if x * x + z * z <= GRILLE_R**2:
                pts.append((x, SPK_Z + z))
    return pts


def speaker_screw_points() -> list[tuple[float, float]]:
    d = SPK_SCREW_OFS / math.sqrt(2)
    return [(sx * d, SPK_Z + sz * d) for sx in (1, -1) for sz in (1, -1)]


def speaker() -> Part:
    y_front = sphere_y(0, SPK_Z + SPK_D / 2, BODY_R - WALL) + 0.5      # 스피커 가장자리가 안쪽 벽에 닿는 위치
    return Pos(0, y_front + SPK_T / 2, SPK_Z) * Rot(90, 0, 0) * Cylinder(SPK_D / 2, SPK_T)


def speaker_ring() -> Part:
    """스피커 누름 고리: 스피커 뒤를 눌러 앞벽에 붙인다. 앞에서 나사 4개가 고리의 인서트로 들어간다."""
    spk = speaker().bounding_box()
    y0 = spk.max.Y
    ring = Pos(0, y0 + 1.0, SPK_Z) * Rot(90, 0, 0) * (Cylinder(SPK_D / 2 + 1.5, 2.0) - Cylinder(SPK_D / 2 - 4, 2.1))
    for sx, sz in speaker_screw_points():
        yw = sphere_y(sx, sz, BODY_R - WALL)
        post = Pos(sx, (yw - 0.5 + y0 + 2.0) / 2, sz) * Rot(90, 0, 0) * Cylinder(3.0, (y0 + 2.0) - (yw - 0.5))
        post -= Pos(sx, yw + INSERT_L / 2, sz) * Rot(90, 0, 0) * Cylinder(INSERT_D / 2, INSERT_L + 0.01)
        ring += post & (Pos(0, 0, BODY_CZ) * Sphere(BODY_R - WALL - 0.05))
    return ring


def top_plate() -> Part:
    """목 받침판: 베어링 자리·선 지나는 반달 홈 + 좌우 서보 받침(기둥 2개에 매단 판).
    서보 고정 날개 구멍은 서보 혼 반지름 안쪽이라 기둥을 날개 위로 내릴 수 없다 → 날개 높이에 판을 두고
    기둥은 혼 바깥(앞뒤 y ±11)에 세운다."""
    import neck_module as nm

    p = Pos(0, 0, TOP_Z - PLATE_T / 2) * Cylinder(PLATE_R, PLATE_T)
    p += nm.body_top_add()
    servo_bottom = nm.BODY_TOP_Z - nm.BRG_W - nm.SEAT_LIP_T - 1.0 - nm.HORN_T - nm.MG_TOTAL_H
    tab_bottom = servo_bottom + nm.MG_TAB_Z
    cx = -(nm.MG_L / 2 - nm.MG_SHAFT_FROM_END)
    mount = Pos(cx, 0, tab_bottom - MOUNT_T / 2) * Box(nm.MG_TAB_L + 3, 2 * MOUNT_HALF_W, MOUNT_T)
    mount -= Pos(cx, 0, tab_bottom - MOUNT_T / 2) * Box(nm.MG_L + 0.6, nm.MG_W + 0.6, MOUNT_T + 1)       # 서보 몸체 창
    for sx in (1, -1):
        x = cx + sx * (nm.MG_TAB_L / 2 - 2.4)
        mount -= Pos(x, 0, tab_bottom - INSERT_L / 2) * Cylinder(INSERT_D / 2, INSERT_L + 0.01)     # 날개 나사 인서트
    for sy in (1, -1):
        h = (TOP_Z - PLATE_T) - tab_bottom
        p += Pos(cx, sy * POST_Y, tab_bottom + h / 2) * Box(5.0, 4.0, h + 0.01)
    p += mount
    p -= nm.body_top_cut()
    for i in range(4):
        a = math.radians(45 + 90 * i)
        x, y = PLATE_SCREW_R * math.cos(a), PLATE_SCREW_R * math.sin(a)
        p -= Pos(x, y, TOP_Z) * Cylinder(SCREW_D / 2, 10)
        p -= Pos(x, y, TOP_Z) * Cylinder(2.1, 2.0)                                      # 나사 머리 묻힘
    return p


def base_plate() -> Part:
    b = Pos(0, 0, BASE_T / 2) * Cylinder(BASE_R, BASE_T)
    for i in range(3):
        a = math.radians(90 + 120 * i)
        x, y = BASE_SCREW_R * math.cos(a), BASE_SCREW_R * math.sin(a)
        b -= Pos(x, y, 0) * Cylinder(SCREW_D / 2, 10)
        b -= Pos(x, y, 0) * Cylinder(2.1, 2.0)
    return b


def pad() -> Part:
    """바닥 전체에 붙이는 실리콘 미끄럼 방지 패드 (바닥판 나사 위를 덮는다)."""
    return Pos(0, 0, -PAD_T / 2) * Cylinder(PAD_R, PAD_T)


def weight() -> Part:
    h = WASHER_T * WASHER_N
    return Pos(0, 0, FLOOR_Z + h / 2) * (Cylinder(WASHER_OD / 2, h) - Cylinder(12.5, h + 1))


def usb_board() -> Part:
    """USB-C 보드: 단자가 뒤 구멍에 맞도록 뒤쪽에 눕힌다."""
    y_back = -sphere_y(0, USB_Z, BODY_R - WALL)          # 안쪽 벽 (뒤)
    w, l, t = USB_BOARD
    board = Pos(0, y_back - l / 2 - 0.3, USB_Z - 3.2 / 2 - t / 2) * Box(w, l, t)
    plug = Pos(0, y_back + 1.5 - 7.3 / 2, USB_Z) * Box(8.9, 7.3, 3.2)    # 단자 앞 1.5mm 가 벽 구멍 안으로
    return board + plug


def usb_shelf() -> Part:
    """USB-C 보드 받침: 뒤 벽에서 앞으로 내민 선반 (아래 와셔를 피해 벽에 매단다)."""
    y_back = -sphere_y(0, USB_Z, BODY_R - WALL)
    l = USB_BOARD[1]
    z_top = USB_Z - 3.2 / 2 - USB_BOARD[2]
    shelf = Pos(0, y_back - l / 2 + 2, z_top - 1.0) * Box(USB_BOARD[0] + 4, l + 4, 2.0)
    return shelf & (Pos(0, 0, BODY_CZ) * Sphere(BODY_R - 1.0))


def screws() -> Part:
    """보이는 나사 머리 (앞 4개). 그림용."""
    out = Part()
    for sx, sz in speaker_screw_points():
        y = sphere_y(sx, sz, BODY_R)
        out += Pos(sx, y + 0.6, sz) * Rot(90, 0, 0) * Cylinder(1.8, 1.6)
    return out


def parts() -> dict[str, Part]:
    return {
        "body": shell(), "body_top": top_plate(), "body_base": base_plate(), "pad": pad(), "weight": weight(),
        "speaker": speaker(), "speaker_ring": speaker_ring(), "usbc": usb_board(), "usb_shelf": usb_shelf(),
        "body_screws": screws(),
    }


JOINED = {
    frozenset(x) for x in [
        ("body", "body_top"), ("body", "body_base"), ("body_base", "pad"), ("body", "pad"),
        ("body", "speaker"), ("speaker", "speaker_ring"), ("body", "speaker_ring"), ("usbc", "usb_shelf"),
        ("body", "usb_shelf"), ("body", "usbc"), ("body", "body_screws"), ("body_top", "bearing"),
        ("body_top", "mg90s_pan"), ("weight", "body"),
    ]
}
