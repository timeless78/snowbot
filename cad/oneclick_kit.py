"""원클릭팩토리용 출력 파일: 부품마다 출력 방향을 잡고, 같은 색 작은 부품을 한 판으로 묶는다.

원클릭팩토리는 올린 방향 그대로 출력한다. print_kit.py 가 만든 조립 위치 STL(out/print/)을 읽어
→ 돌리고 → 바닥(z=0)에 내려 → out/print/oneclick/{1_test,2_final}/ 에 저장한다.

실행: .venv/bin/python print_kit.py && .venv/bin/python oneclick_kit.py
"""

from pathlib import Path

import numpy as np
import trimesh

SRC = Path(__file__).parent / "out" / "print"
DST = SRC / "oneclick"
GAP = 4.0          # 한 판에 묶을 때 부품 사이
ROW_MAX = 150.0    # 한 줄 최대 폭

# 큰 부품: 정해 둔 방향 (축, 각도). None 은 그대로
FIXED = {
    "head_front": ("x", -90),   # 이음선 면(뒤)을 바닥에 → 얼굴이 위
    "head_back": ("x", 90),     # 이음선 면(앞)을 바닥에
    "body": ("x", 180),         # 넓은 윗면을 바닥에, 거꾸로
    "body_top": ("x", 180),     # 판 윗면을 바닥에 → 베어링 고리·서보 받침이 위로
    "neck_yoke": None,          # 허브 아래만 지지대
    "chassis": ("x", -90),      # 뒤 받침판·세로 판 뒤끝을 바닥에 (세로 판을 눕히면 귀 팔 위에 떠서 안 됨)
    "pupil_left": ("x", -90),   # 앞면이 위
    "pupil_right": ("x", -90),
    "coupon_holes": None, "coupon_servo": None, "coupon_bearing": None,
}

# 원클릭팩토리에 올릴 파일: 이름 → (묶을 부품들, 색, 층 높이 mm, 속 채움 %)
UPLOADS = {
    "1_test": {
        "head_front":      (["head_front"], "화이트", 0.20, 15),
        "body_top":        (["body_top"], "화이트", 0.20, 30),
        "neck_yoke":       (["neck_yoke"], "그레이", 0.20, 30),
        "chassis":         (["chassis"], "그레이", 0.20, 30),
        "white_eye_parts": (["dome_left", "dome_right", "eyelid_left", "eyelid_right"], "화이트", 0.12, 15),
        "black_parts":     (["pupil_left", "pupil_right", "brow_left", "brow_right"], "블랙", 0.12, 15),
        "nose":            (["nose"], "오렌지", 0.12, 15),
        "gray_small_parts": (["eyelid_crank", "brow_lever_left", "brow_lever_right", "brow_slider", "brow_guide"], "그레이", 0.12, 30),
        "coupons":         (["coupon_holes", "coupon_servo", "coupon_bearing"], "그레이", 0.20, 15),
    },
    "2_final": {
        "head_back":       (["head_back"], "화이트", 0.20, 15),
        "body":            (["body"], "화이트", 0.20, 15),
        "body_small_parts": (["body_base", "speaker_ring", "usb_shelf"], "화이트", 0.20, 30),
    },
}


def rot(axis: str, deg: float) -> np.ndarray:
    v = {"x": [1, 0, 0], "y": [0, 1, 0], "z": [0, 0, 1]}[axis]
    return trimesh.transformations.rotation_matrix(np.radians(deg), v)


def largest_flat_face_down(m: trimesh.Trimesh) -> trimesh.Trimesh:
    """가장 넓은 평면 중 맨 바깥(지지면이 되는) 면을 바닥으로."""
    order = np.argsort(m.facets_area)[::-1]
    for i in order[:12]:
        n = m.facets_normal[i]
        t = trimesh.geometry.align_vectors(n, [0, 0, -1])
        c = m.copy()
        c.apply_transform(t)
        face_z = c.vertices[np.unique(c.faces[m.facets[i]])][:, 2].mean()
        if abs(face_z - c.bounds[0][2]) < 0.05:
            return c
    return m


def orient(name: str, kit: str) -> trimesh.Trimesh:
    m = trimesh.load(SRC / kit / f"{name}.stl")
    if name in FIXED:
        if FIXED[name]:
            m.apply_transform(rot(*FIXED[name]))
    else:
        m = largest_flat_face_down(m)
    lo, hi = m.bounds
    m.apply_translation([-(lo[0] + hi[0]) / 2, -(lo[1] + hi[1]) / 2, -lo[2]])
    return m


def plate(meshes: list[trimesh.Trimesh]) -> trimesh.Trimesh:
    """여러 부품을 줄지어 한 판에."""
    placed, x, y, row_h = [], 0.0, 0.0, 0.0
    for m in meshes:
        w, d = m.extents[0], m.extents[1]
        if x > 0 and x + w > ROW_MAX:
            x, y, row_h = 0.0, y + row_h + GAP, 0.0
        c = m.copy()
        lo = c.bounds[0]
        c.apply_translation([x - lo[0], y - lo[1], 0])
        placed.append(c)
        x += w + GAP
        row_h = max(row_h, d)
    out = trimesh.util.concatenate(placed)
    lo, hi = out.bounds
    out.apply_translation([-(lo[0] + hi[0]) / 2, -(lo[1] + hi[1]) / 2, -lo[2]])
    return out


def main() -> None:
    for kit, files in UPLOADS.items():
        d = DST / kit
        d.mkdir(parents=True, exist_ok=True)
        for old in d.glob("*.stl"):
            old.unlink()
        for fname, (parts, color, layer, infill) in files.items():
            meshes = [orient(p, kit) for p in parts]
            m = meshes[0] if len(meshes) == 1 else plate(meshes)
            m.export(d / f"{fname}.stl")
            ex = m.extents
            print(f"{kit}/{fname}.stl  {ex[0]:.0f}×{ex[1]:.0f}×{ex[2]:.0f}mm  {m.volume / 1000:.1f}cm³  {color} 층{layer} 채움{infill}%  ← {', '.join(parts)}")


if __name__ == "__main__":
    main()
