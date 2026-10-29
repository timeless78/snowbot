"""5단계: 출력 파일 — 출력할 부품마다 STL 1개, 공차 시험 판 3장, 부품표(부피·무게·색).

실행: .venv/bin/python print_kit.py → out/print/1_test/, out/print/2_final/, out/print/parts.csv

- 1차(시험) 묶음은 가장 어려운 얼굴·눈 기구·머리 안 틀·목을 **실제 모양 그대로** 뽑는다. 맞으면 그대로 쓴다.
- 공차 시험 판으로 인서트 구멍·축 구멍·베어링 자리·서보 창 크기를 고른 뒤, 2차(본) 묶음 숫자를 고쳐 뽑는다.
- 부품은 조립 위치 그대로 내보낸다. 출력 방향은 docs/06-print-order.md 의 메모를 업체에 전달한다.
"""

import csv
from pathlib import Path

from build123d import Box, Cylinder, Part, Plane, Pos, Text, export_stl, extrude

import body_module as bm
import eye_module as em
import neck_module as nm
import snowbot_layout as L

OUT = Path(__file__).parent / "out" / "print"
PLA_G_PER_CM3 = 1.24

# 이름: (묶음, 색, 개수, 메모)
PARTS = {
    "head_front":   ("1_test",  "흰색",       1, "이음선 면을 바닥에. 안쪽 기둥·탭·눈 테 아래 지지대 필요"),
    "chassis":      ("1_test",  "아무 색",    1, "머리 안 틀. 지지대 필요"),
    "dome_left":    ("1_test",  "흰색",       1, "흰자 돔"),
    "dome_right":   ("1_test",  "흰색",       1, "흰자 돔 (카메라 쪽)"),
    "pupil_left":   ("1_test",  "검정(광택)", 1, "검은자"),
    "pupil_right":  ("1_test",  "검정(광택)", 1, "검은자, 렌즈 구멍"),
    "eyelid_left":  ("1_test",  "흰색",       1, "두께 1mm 곡면 — 실 출력이 거칠면 레진으로"),
    "eyelid_right": ("1_test",  "흰색",       1, "두께 1mm 곡면 — 실 출력이 거칠면 레진으로"),
    "eyelid_crank": ("1_test",  "아무 색",    1, "작은 부품"),
    "brow_left":    ("1_test",  "검정",       1, "눈썹"),
    "brow_right":   ("1_test",  "검정",       1, "눈썹"),
    "brow_lever_left":  ("1_test", "아무 색", 1, "눈썹 레버"),
    "brow_lever_right": ("1_test", "아무 색", 1, "눈썹 레버"),
    "brow_slider":  ("1_test",  "아무 색",    1, "눈썹 슬라이더"),
    "brow_guide":   ("1_test",  "아무 색",    1, "눈썹 서보 위 안내 판 + 핀 2개"),
    "nose":         ("1_test",  "주황",       1, "당근 코"),
    "neck_yoke":    ("1_test",  "은색 또는 짙은 회색", 1, "회전판·요크·가로 원통 한 덩어리. 아래 허브 지지대 필요"),
    "body_top":     ("1_test",  "흰색",       1, "목 받침판 (베어링 자리·좌우 서보 받침)"),
    "coupon_holes": ("1_test",  "아무 색",    1, "공차 시험 판 A: 인서트·나사·축·피아노선 구멍"),
    "coupon_servo": ("1_test",  "아무 색",    1, "공차 시험 판 B: 서보 창"),
    "coupon_bearing": ("1_test", "아무 색",   1, "공차 시험 판 C: 베어링 자리"),
    "head_back":    ("2_final", "흰색",       1, "이음선 면을 바닥에"),
    "body":         ("2_final", "흰색",       1, "위(넓은 면)를 바닥에 놓고 거꾸로 — 지지대 거의 없음"),
    "body_base":    ("2_final", "흰색",       1, "바닥판"),
    "speaker_ring": ("2_final", "아무 색",    1, "스피커 누름 고리"),
    "usb_shelf":    ("2_final", "아무 색",    1, "USB-C 보드 선반 (몸통 안 뒤 벽에 붙임)"),
}


def labeled_plate(w: float, d: float, t: float) -> Part:
    return Pos(0, 0, t / 2) * Box(w, d, t)


def label(text: str, x: float, y: float, top: float) -> Part:
    return Pos(x, y, top - 0.4) * extrude(Plane.XY * Text(text, font_size=2.4, font="Arial"), amount=1)


def coupon_holes() -> Part:
    """A: 위 줄 M2 인서트 구멍(깊이 5), 둘째 줄 M2 나사 구멍, 셋째 줄 황동봉 2mm 축 구멍, 넷째 줄 피아노선 1mm 구멍."""
    rows = [
        ("인서트", [2.9, 3.0, 3.1, 3.2], 5.0),
        ("M2", [2.2, 2.4, 2.6], None),
        ("축2", [2.0, 2.15, 2.3], None),
        ("선1", [1.1, 1.25, 1.4], None),
    ]
    t, w, pitch, row_h = 6.0, 64.0, 9.0, 11.0
    p = labeled_plate(w, row_h * len(rows) + 4, t)
    y0 = (row_h * len(rows)) / 2 - row_h / 2
    for r, (_, sizes, depth) in enumerate(rows):
        y = y0 - r * row_h
        for i, d in enumerate(sizes):
            x = -w / 2 + 12 + i * pitch
            h = depth if depth else t + 2
            p -= Pos(x, y, t - h / 2 + (0 if depth else 1)) * Cylinder(d / 2, h)
            p -= label(f"{d:g}", x - 2.2, y - 4.6, t)
    return p


def coupon_servo() -> Part:
    """B: 위 줄 MG90S 몸체 창(22.8 × 12.2 + 여유), 아래 줄 SG51R 창(21.5 × 11.7 + 여유). 여유 0.2 / 0.4 / 0.6."""
    t = 3.0
    p = labeled_plate(96, 52, t)
    for i, extra in enumerate((0.2, 0.4, 0.6)):
        x = -32 + i * 32
        p -= Pos(x, 11, t / 2) * Box(nm.MG_L + extra, nm.MG_W + extra, t + 2)
        p -= Pos(x, -13, t / 2) * Box(em.SG_L + extra, em.SG_W + extra, t + 2)
        p -= label(f"+{extra:g}", x - 3, 1.5, t)
        p -= label(f"+{extra:g}", x - 3, -23.5, t)
    return p


def coupon_bearing() -> Part:
    """C: 6800ZZ 자리 지름 19.0 / 19.1 / 19.2 / 19.3, 깊이 5. 아래 구멍(지름 15)으로 밀어 뺄 수 있다."""
    t = 6.0
    p = labeled_plate(100, 30, t)
    for i, d in enumerate((19.0, 19.1, 19.2, 19.3)):
        x = -37.5 + i * 25
        p -= Pos(x, 2, t - 2.5) * Cylinder(d / 2, 5.01)               # 바닥 1mm 남김
        p -= Pos(x, 2, t / 2) * Cylinder(7.5, t + 2)
        p -= label(f"{d:g}", x - 4, -12.5, t)
    return p


def collect() -> dict[str, Part]:
    a = L.assembly(0.0, 0.0)          # 눈 감음·눈썹 가운데 (모양은 자세와 상관없음)
    parts = {k: a[k] for k in PARTS if k in a}
    parts["head_front"] = a["head"]
    parts["coupon_holes"] = coupon_holes()
    parts["coupon_servo"] = coupon_servo()
    parts["coupon_bearing"] = coupon_bearing()
    return parts


def main() -> None:
    parts = collect()
    missing = [k for k in PARTS if k not in parts]
    assert not missing, missing
    rows = []
    for name, (kit, color, qty, memo) in PARTS.items():
        d = OUT / kit
        d.mkdir(parents=True, exist_ok=True)
        export_stl(parts[name], str(d / f"{name}.stl"), tolerance=0.02, angular_tolerance=0.1)
        cm3 = parts[name].volume / 1000
        bb = parts[name].bounding_box()
        rows.append([kit, name, color, qty, round(cm3, 2), round(cm3 * PLA_G_PER_CM3, 1),
                     f"{bb.size.X:.0f}×{bb.size.Y:.0f}×{bb.size.Z:.0f}", memo])
    with open(OUT / "parts.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["묶음", "파일", "색", "개수", "부피cm3", "PLA꽉채움g", "크기mm", "메모"])
        w.writerows(rows)
    for r in rows:
        print(r)
    sheet(parts)


def sheet(parts: dict[str, Part]) -> None:
    """부품마다 작은 그림을 한 장에 — 업체에 보낼 목록 확인용."""
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import numpy as np
    import trimesh
    from mpl_toolkits.mplot3d.art3d import Poly3DCollection

    plt.rcParams["font.family"] = "AppleGothic"
    colors = {"흰색": "#ecebe6", "검정(광택)": "#222222", "검정": "#333333", "주황": "#e8661c", "은색 또는 짙은 회색": "#8d939a"}
    names = list(PARTS)
    cols = 7
    fig = plt.figure(figsize=(cols * 2.6, 4 * 2.9))
    for i, name in enumerate(names):
        kit, color, _, _ = PARTS[name]
        m = trimesh.load(OUT / kit / f"{name}.stl")
        ax = fig.add_subplot(4, cols, i + 1, projection="3d")
        base = np.array(matplotlib.colors.to_rgb(colors.get(color, "#7fb3d5")))
        light = np.array([-0.4, -1.0, 0.7]); light /= np.linalg.norm(light)
        k = 0.45 + 0.55 * np.clip(m.face_normals @ light, 0, 1)
        ax.add_collection3d(Poly3DCollection(m.vertices[m.faces], facecolors=np.clip(base * k[:, None], 0, 1), linewidths=0))
        lo, hi = m.bounds
        c = (lo + hi) / 2
        r = max(hi - lo) / 2
        ax.set_xlim(c[0] - r, c[0] + r); ax.set_ylim(c[1] - r, c[1] + r); ax.set_zlim(c[2] - r, c[2] + r)
        ax.set_box_aspect((1, 1, 1)); ax.view_init(22, -60); ax.set_axis_off()
        tag = "1차" if kit == "1_test" else "2차"
        ax.set_title(f"{tag} {name}\n{max(hi - lo):.0f}mm", fontsize=9)
    fig.tight_layout()
    fig.savefig(OUT / "parts_sheet.png", dpi=90)
    plt.close(fig)
    print(OUT / "parts_sheet.png")


if __name__ == "__main__":
    main()
