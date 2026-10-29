"""out/<자세>/*.stl 을 미리보기 PNG 로 그린다.

실행: MPLCONFIGDIR=$TMPDIR/mpl .venv/bin/python render.py
  → out/preview.png      전체 (정면 · 옆 단면)
  → out/preview_eye.png  1단계 눈 기구 확대 (눈 뜸/감음, 단면, 기구만)
  → out/preview_neck.png 2단계 목 관절 (숙임·젖힘 단면, 목만, 좌우 돌림)
"""

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import trimesh
from mpl_toolkits.mplot3d.art3d import Poly3DCollection

OUT = Path(__file__).parent / "out"
SHELLS = {"body", "head", "head_back"}

# 이름 앞부분 → (색, 범례 이름)
PALETTE = [
    ("body_top", "#c8c8c4", "목 받침판"), ("body_base", "#b5b5b0", "바닥판"), ("body_screws", "#6b6f75", None),
    ("body", "#e9e9e6", None), ("head", "#e9e9e6", None), ("screws", "#6b6f75", "육각 나사"),
    ("pad", "#333333", "미끄럼 방지 패드"), ("weight", "#8d939a", "무게추(와셔)"), ("speaker_ring", "#7fb3d5", None),
    ("usb_shelf", "#7fb3d5", None),
    ("chassis", "#7fb3d5", "머리 안 틀"),
    ("dome", "#f7f7f2", "흰자 돔"), ("pupil", "#1d1d1d", "검은자"), ("camera", "#555555", "카메라"),
    ("eyelid_axle", "#c9a227", "축·막대(황동·피아노선)"), ("eyelid_link", "#c9a227", None),
    ("brow_shaft", "#c9a227", None),
    ("eyelid_crank", "#e67e22", "크랭크·레버·슬라이더"), ("brow_lever", "#e67e22", None), ("brow_slider", "#e67e22", None),
    ("eyelid_horn", "#8e44ad", "서보 혼"), ("brow_horn", "#8e44ad", None),
    ("pan_horn", "#8e44ad", None), ("nod_horn", "#8e44ad", None),
    ("neck_yoke", "#4a4d52", "회전판·요크"), ("nod_pivot", "#c9a227", None),
    ("eyelid", "#d9d9d4", "눈꺼풀"), ("brow", "#3a3a3a", "눈썹"),
    ("sg51r", "#3a6fd0", "SG51R 서보"), ("mg90s", "#1f3f9a", "MG90S 서보"), ("xiao", "#2f7d4f", "XIAO"),
    ("amp", "#8e44ad", None), ("bearing", "#9aa0a6", "베어링"), ("speaker", "#c0392b", "스피커"),
    ("usbc", "#d4a017", "USB-C"), ("nose", "#e8661c", "코"),
]


def color_of(name: str) -> str:
    for prefix, color, _ in PALETTE:
        if name.startswith(prefix):
            return color
    return "#999999"


def shade(mesh: trimesh.Trimesh, color: str, light=(-0.4, -1.0, 0.6)) -> np.ndarray:
    base = np.array(matplotlib.colors.to_rgb(color))
    l = np.array(light) / np.linalg.norm(light)
    k = 0.45 + 0.55 * np.clip(mesh.face_normals @ l, 0, 1)
    return np.clip(base[None, :] * k[:, None], 0, 1)


def load(pose: str) -> dict[str, trimesh.Trimesh]:
    return {p.stem: trimesh.load(p) for p in sorted((OUT / pose).glob("*.stl"))}


def cut_shells(meshes: dict[str, trimesh.Trimesh], keep_x_max: float) -> dict[str, trimesh.Trimesh]:
    """껍데기에서 x > keep_x_max 쪽 면을 지운다 (단면처럼 속을 보이게)."""
    out = {}
    for name, m in meshes.items():
        if name in SHELLS:
            keep = m.triangles_center[:, 0] <= keep_x_max
            m = trimesh.Trimesh(m.vertices, m.faces[keep], process=False)
        out[name] = m
    return out


def draw(ax, meshes, elev, azim, title, lims, shell_alpha=1.0, hide=()):
    """모든 면을 한 묶음으로 그린다 — 그래야 면마다 앞뒤 순서가 정렬되어 가려짐이 맞는다."""
    light = (-0.4, -1.0, 0.6) if azim < -45 else (0.8, -0.3, 0.6)
    tris, colors = [], []
    for name, m in meshes.items():
        if name in hide or len(m.faces) == 0:
            continue
        a = shell_alpha if name in SHELLS else 1.0
        rgb = shade(m, color_of(name), light)
        tris.append(m.vertices[m.faces])
        colors.append(np.hstack([rgb, np.full((len(rgb), 1), a)]))
    ax.add_collection3d(Poly3DCollection(np.concatenate(tris), facecolors=np.concatenate(colors), linewidths=0))
    (x0, x1), (y0, y1), (z0, z1) = lims
    ax.set_xlim(x0, x1); ax.set_ylim(y0, y1); ax.set_zlim(z0, z1)
    ax.set_box_aspect((x1 - x0, y1 - y0, z1 - z0), zoom=1.35)
    ax.view_init(elev=elev, azim=azim)
    ax.set_proj_type("ortho")
    ax.set_axis_off()
    ax.set_title(title, fontsize=12)


def legend(fig, names):
    used = [(c, l) for p, c, l in PALETTE if l and any(n.startswith(p) for n in names)]
    handles = [plt.Rectangle((0, 0), 1, 1, color=c) for c, _ in used]
    fig.legend(handles, [l for _, l in used], loc="lower center", ncol=min(len(used), 8), fontsize=10)


def main() -> None:
    plt.rcParams["font.family"] = "AppleGothic"
    opened, closed = load("open"), load("closed")
    full = ((-50, 50), (-50, 50), (0, 165))
    head = ((-40, 40), (-56, 24), (72, 156))

    fig = plt.figure(figsize=(14, 8))
    draw(fig.add_subplot(1, 3, 1, projection="3d"), opened, 0, -90, "정면", full, hide={"camera"})
    draw(fig.add_subplot(1, 3, 2, projection="3d"), cut_shells(opened, 0), 0, 0, "옆 단면 (왼쪽 절반 잘라냄)", full, 0.5)
    draw(fig.add_subplot(1, 3, 3, projection="3d"), cut_shells(opened, 0), 20, -50, "비스듬히 (단면)", full, 0.35)
    legend(fig, opened)
    fig.tight_layout(rect=(0, 0.06, 1, 1))
    fig.savefig(OUT / "preview.png", dpi=110)
    plt.close(fig)

    body_parts = {n for n in opened if n.startswith(("body", "mg90s_pan", "speaker", "usbc", "bearing"))}
    fig = plt.figure(figsize=(16, 9))
    draw(fig.add_subplot(2, 3, 1, projection="3d"), opened, 0, -90, "정면 — 눈 뜸, 눈썹 가운데", head, hide=body_parts | {"camera"})
    draw(fig.add_subplot(2, 3, 2, projection="3d"), closed, 0, -90, "정면 — 눈 감음, 눈썹 화남", head, hide=body_parts | {"camera"})
    draw(fig.add_subplot(2, 3, 3, projection="3d"), opened, 25, -60, "비스듬히 — 눈 뜸", head, hide=body_parts)
    draw(fig.add_subplot(2, 3, 4, projection="3d"), cut_shells(opened, 0.3), 0, 0, "옆 단면 x=0 — 눈 뜸 (크랭크·막대·서보)", head, 0.45, hide=body_parts)
    draw(fig.add_subplot(2, 3, 5, projection="3d"), cut_shells(closed, 0.3), 0, 0, "옆 단면 x=0 — 눈 감음", head, 0.45, hide=body_parts)
    hide_back = body_parts | {"head", "xiao", "mg90s_nod", "amp"}
    draw(fig.add_subplot(2, 3, 6, projection="3d"), opened, 20, 135, "기구만 (뒤에서 비스듬히)", head, hide=hide_back)
    legend(fig, opened)
    fig.tight_layout(rect=(0, 0.05, 1, 1))
    fig.savefig(OUT / "preview_eye.png", dpi=105)
    plt.close(fig)
    down, up, pan = load("nod_down"), load("nod_up"), load("pan_left")
    eye_names = {n for n in opened if n.startswith(("dome", "pupil", "camera", "eyelid", "brow", "sg51r", "xiao", "amp", "nose"))}
    neck = ((-34, 34), (-34, 34), (18, 98))
    fig = plt.figure(figsize=(16, 9))
    draw(fig.add_subplot(2, 3, 1, projection="3d"), cut_shells(down, 0.3), 0, 0, "옆 단면 — 숙임 25°", full, 0.45, hide={"camera"})
    draw(fig.add_subplot(2, 3, 2, projection="3d"), cut_shells(up, 0.3), 0, 0, "옆 단면 — 젖힘 25°", full, 0.45, hide={"camera"})
    draw(fig.add_subplot(2, 3, 3, projection="3d"), pan, 15, -60, "왼쪽으로 60° 돌림", full, hide={"camera"})
    draw(fig.add_subplot(2, 3, 4, projection="3d"), cut_shells(opened, 0.3), 0, 0, "목 단면 (베어링·허브·혼·좌우 서보)", neck, 0.45, hide=eye_names)
    draw(fig.add_subplot(2, 3, 5, projection="3d"), opened, 0, -90, "목 정면 (요크·끄덕임 서보)", neck, 1.0, hide=eye_names | {"head", "body"})
    draw(fig.add_subplot(2, 3, 6, projection="3d"), opened, 25, -55, "목만 비스듬히", neck, 1.0, hide=eye_names | {"head", "body"})
    legend(fig, opened)
    fig.tight_layout(rect=(0, 0.05, 1, 1))
    fig.savefig(OUT / "preview_neck.png", dpi=105)
    plt.close(fig)
    # 기준 그림과 비교 (정면 · 옆)
    from PIL import Image
    ref = Image.open(Path(__file__).parent.parent / "docs/assets/design-tech/c-reference-turnaround.png")
    pose = load("ref")
    fig = plt.figure(figsize=(16, 8))
    for i, (box, title) in enumerate([((0, 0, 470, 700), "기준 그림 — 정면"), ((470, 0, 930, 700), "기준 그림 — 옆")]):
        ax = fig.add_subplot(1, 4, 1 + 2 * i)
        ax.imshow(ref.crop(box)); ax.set_axis_off(); ax.set_title(title, fontsize=12)
    lims = ((-48, 48), (-48, 48), (-2, 156))
    draw(fig.add_subplot(1, 4, 2, projection="3d"), pose, 0, -90, "설계 — 정면 (목도리 없음)", lims, hide={"camera"})
    draw(fig.add_subplot(1, 4, 4, projection="3d"), pose, 0, 0, "설계 — 옆", lims, hide={"camera"})
    fig.tight_layout()
    fig.savefig(OUT / "preview_compare.png", dpi=100)
    plt.close(fig)
    # 3단계: 머리 안 틀 + 껍데기 나누기
    ex = load("exploded")
    lower = {n for n in ex if n.startswith(("body", "mg90s_pan", "speaker", "usbc", "bearing", "pan_horn"))}
    hd = ((-50, 50), (-50, 85), (62, 158))
    inner = {"head", "head_back", "screws"} | {n for n in ex if n.startswith(("eyelid", "brow", "dome", "pupil", "nose", "camera"))}
    fig = plt.figure(figsize=(16, 9))
    draw(fig.add_subplot(2, 3, 1, projection="3d"), opened, 0, 0, "옆 — 이음선과 육각 나사", ((-50, 50), (-50, 50), (62, 158)), hide=lower | {"camera"})
    draw(fig.add_subplot(2, 3, 2, projection="3d"), opened, 10, 90, "뒤 — 환기 슬롯·마이크 구멍", ((-50, 50), (-50, 50), (62, 158)), hide=lower)
    draw(fig.add_subplot(2, 3, 3, projection="3d"), ex, 20, 40, "분해 — 뒤 덮개를 뺀 모습", hd, hide=lower | {"camera"})
    draw(fig.add_subplot(2, 3, 4, projection="3d"), opened, 20, 40, "머리 안 틀 + 서보·보드 (껍데기·눈 숨김)", ((-45, 45), (-45, 45), (62, 158)), hide=lower | inner)
    draw(fig.add_subplot(2, 3, 5, projection="3d"), opened, 15, 140, "머리 안 틀 (반대쪽)", ((-45, 45), (-45, 45), (62, 158)), hide=lower | inner)
    only = {n for n in ex if n not in {"chassis", "head"}}
    draw(fig.add_subplot(2, 3, 6, projection="3d"), ex, 20, 130, "앞 껍데기 안쪽 — 고정 기둥·나사 탭", ((-50, 50), (-50, 50), (62, 158)), hide=only)
    legend(fig, ex)
    fig.tight_layout(rect=(0, 0.05, 1, 1))
    fig.savefig(OUT / "preview_head.png", dpi=105)
    plt.close(fig)
    # 4단계: 몸통·바닥
    head_names = {n for n in opened if n.startswith(("head", "screws", "chassis", "dome", "pupil", "eyelid", "brow", "nose", "camera",
                                                       "sg51r", "xiao", "amp", "mg90s_nod", "neck", "nod_"))}
    bd = ((-45, 45), (-45, 45), (-3, 72))
    fig = plt.figure(figsize=(16, 9))
    draw(fig.add_subplot(2, 3, 1, projection="3d"), opened, 0, -90, "앞 — 스피커 그릴·나사·SNW-01", bd, hide=head_names)
    draw(fig.add_subplot(2, 3, 2, projection="3d"), opened, 0, 90, "뒤 — USB-C", bd, hide=head_names)
    draw(fig.add_subplot(2, 3, 3, projection="3d"), opened, -60, -60, "아래 — 바닥판·패드", bd, hide=head_names)
    draw(fig.add_subplot(2, 3, 4, projection="3d"), cut_shells(opened, 0.3), 0, 0, "옆 단면 (몸통 껍데기 반 잘라냄)", bd, 0.35, hide=head_names)
    no_shell = head_names | {"body", "pad"}
    draw(fig.add_subplot(2, 3, 5, projection="3d"), opened, 25, -50, "몸통 속 (껍데기 숨김)", bd, hide=no_shell)
    draw(fig.add_subplot(2, 3, 6, projection="3d"), opened, 30, 130, "목 받침판 + 좌우 서보 받침", bd, hide=no_shell | {"speaker", "speaker_ring", "weight", "usbc", "usb_shelf", "body_base", "body_screws"})
    legend(fig, {n for n in opened if n not in head_names})
    fig.tight_layout(rect=(0, 0.05, 1, 1))
    fig.savefig(OUT / "preview_body.png", dpi=105)
    plt.close(fig)
    print(OUT / "preview.png", OUT / "preview_eye.png", OUT / "preview_neck.png", OUT / "preview_compare.png", OUT / "preview_head.png", OUT / "preview_body.png")


if __name__ == "__main__":
    main()
