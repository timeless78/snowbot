"""SnowBot 배치 모델 — 부품이 머리·몸통 안에 들어가고 움직여도 부딪히지 않는지 본다 (출력용 아님).
1단계 눈 기구는 eye_module.py, 2단계 목 관절은 neck_module.py, 3단계 머리 안 틀·껍데기 나누기는 head_module.py,
4단계 몸통·바닥은 body_module.py.

좌표: x = 로봇 기준 왼쪽(+)/오른쪽(-), y = 뒤(+)/앞(-), z = 위. 단위 mm. 바닥이 z=0.
치수는 판매 페이지·일반 규격 "자료값"이다. 부품이 오면 실측값으로 바꾼다 (docs/04-body-build-plan.md 2절).

부품은 움직임에 따라 세 무리로 나뉜다.
- 몸통: 몸통 껍데기·목 받침판·바닥판·무게추·고무발, 베어링, 좌우 서보, 스피커·누름 고리, USB-C — 움직이지 않음
- 목: 회전판(요크)·혼·핀 — 고개 좌우(pan)만 따라 돎
- 머리: 앞 껍데기(head)·뒤 덮개(head_back)·나사, 머리 안 틀, 눈 기구, XIAO, 앰프, 끄덕임 서보 — 좌우 + 끄덕임(nod)

실행: .venv/bin/python snowbot_layout.py  → out/ 에 자세별 STL, out/report.txt (겹침 검사)
"""

from pathlib import Path

from build123d import Box, Cylinder, Part, Pos, Rot, Sphere, export_stl

OUT = Path(__file__).parent / "out"

# ── 외형 치수 ────────────────────────────────────────────
WALL = 2.5            # 껍데기 두께

from body_module import BODY_CZ, BODY_R   # noqa: E402  몸통 치수는 body_module (4단계)

HEAD_R = 45           # 머리 반지름 (지름 90)
HEAD_CUT = 15         # 머리 아래를 잘라 낸 높이 → 아래 평면 지름 약 67mm (기준 그림)

# ── 부품 치수 (자료값) ────────────────────────────────────
XIAO = (17.8, 15.0, 21.0)         # 21 × 17.8 × 15 (Sense 포함), 세워서 눈 뒤에


def below(z: float) -> Part:
    """z 아래 전체를 덮는 큰 상자 (잘라 내기용)."""
    return Pos(0, 0, z - 150) * Box(300, 300, 300)


def above(z: float) -> Part:
    return Pos(0, 0, z + 150) * Box(300, 300, 300)


def body_shell() -> Part:
    import body_module as bm

    return bm.shell()


def head_center_z() -> float:
    import neck_module as nm

    return nm.HEAD_BOTTOM_Z + HEAD_R - HEAD_CUT


def head_shell() -> Part:
    import eye_module as em
    import neck_module as nm

    cz = head_center_z()
    bottom = nm.HEAD_BOTTOM_Z
    outer = Pos(0, 0, cz) * Sphere(HEAD_R) - below(bottom)
    inner = Pos(0, 0, cz) * Sphere(HEAD_R - WALL) - below(bottom + WALL)
    shell = outer - inner + em.head_add()
    shell -= Pos(0, 0, bottom) * Cylinder(nm.HEAD_HOLE_R, 20)
    shell -= em.head_cut()
    return shell


def body_parts() -> dict[str, Part]:
    import body_module as bm
    import neck_module as nm

    p = bm.parts()
    p.pop("body")
    p.update(nm.body_neck_parts())
    return p


def head_parts(opened: float, swing: float) -> dict[str, Part]:
    """머리 무리 (끄덕임·좌우 돌리기 전 위치)."""
    import eye_module as em
    import neck_module as nm

    import head_module as hm

    cz = head_center_z()
    front, back = hm.split(head_shell())
    p: dict[str, Part] = {"head": front, "head_back": back, "screws": hm.screws(), "chassis": hm.chassis()}
    p["xiao"] = Pos(hm.XIAO_X, -14.0, cz + 2) * Box(*XIAO)   # 오른쪽 눈(카메라) 뒤, 눈꺼풀이 도는 구 뒤
    p["amp"] = hm.amp()                                       # 머리 안 틀 세로 판 옆
    p.update(nm.head_neck_parts())
    p.update(em.eye_parts(opened, swing))
    return p


def assembly(opened: float = 1.0, swing: float = 0.0, nod: float = 0.0, pan: float = 0.0) -> dict[str, Part]:
    import neck_module as nm

    head_loc = nm.head_location(pan, nod)
    out = {"body": body_shell(), **body_parts(), **nm.neck_parts(pan)}
    out.update({k: head_loc * v for k, v in head_parts(opened, swing).items()})
    return out


SHELLS = {"body", "head", "head_back", "body_top", "body_base"}

POSES = [  # (눈 뜬 정도 0~1, 눈썹 -1~1, 끄덕임°, 좌우°, 이름)
    (1.0, 0.0, 0, 0, "뜸"), (0.0, 0.0, 0, 0, "감음"), (0.5, 0.0, 0, 0, "반쯤"),
    (1.0, 1.0, 0, 0, "뜸+눈썹 걱정"), (1.0, -1.0, 0, 0, "뜸+눈썹 화남"), (0.0, 1.0, 0, 0, "감음+눈썹 걱정"),
    (1.0, 0.0, 25, 0, "숙임 25°"), (1.0, 0.0, -25, 0, "젖힘 25°"),
    (1.0, 0.0, 25, 90, "숙임 25° + 왼쪽 90°"), (1.0, 0.0, -25, -90, "젖힘 25° + 오른쪽 90°"),
]


def boxes_touch(a: Part, b: Part) -> bool:
    ba, bb = a.bounding_box(), b.bounding_box()
    return not (ba.max.X < bb.min.X or bb.max.X < ba.min.X or ba.max.Y < bb.min.Y or bb.max.Y < ba.min.Y
                or ba.max.Z < bb.min.Z or bb.max.Z < ba.min.Z)


def overlap(a: Part, b: Part) -> float:
    return (a & b).volume if boxes_touch(a, b) else 0.0


def check_interference(solids: dict[str, Part]) -> list[str]:
    """겹치는 부피(mm³). 맞물려 움직이도록 만든 짝과, 껍데기를 뚫고 지나가도록 구멍을 낸 부품은 뺀다."""
    import body_module as bm
    import eye_module as em
    import head_module as hm
    import neck_module as nm

    joined = em.JOINED | nm.JOINED | hm.JOINED | bm.JOINED
    through = {frozenset((p, "head")) for p in em.THROUGH_SHELL}
    lines = []
    names = list(solids)
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            pair = frozenset((a, b))
            if pair in joined or pair in through:
                continue
            v = overlap(solids[a], solids[b])
            if v > 0.5:
                where = "껍데기와" if (a in SHELLS or b in SHELLS) else "부품끼리"
                lines.append(f"{where} 겹침: {a} ↔ {b}  {v:.0f} mm³")
    return lines


def export(dirname: str, solids: dict[str, Part]) -> None:
    d = OUT / dirname
    d.mkdir(parents=True, exist_ok=True)
    for old in d.glob("*.stl"):
        old.unlink()
    for name, solid in solids.items():
        export_stl(solid, str(d / f"{name}.stl"), tolerance=0.2, angular_tolerance=0.2)


def main() -> None:
    OUT.mkdir(exist_ok=True)
    cz = head_center_z()
    summary = [
        f"전체 높이: {cz + HEAD_R:.0f} mm  (머리 지름 {HEAD_R * 2}, 몸통 지름 {BODY_R * 2})",
        f"바닥 평면 지름: {2 * (BODY_R**2 - BODY_CZ**2) ** 0.5:.0f} mm",
    ]
    for opened, swing, nod, pan, label in POSES:
        found = check_interference(assembly(opened, swing, nod, pan))
        summary.append(f"[{label}] " + ("겹침 없음" if not found else f"겹침 {len(found)}건"))
        summary += ["    " + x for x in found]
    export("open", assembly(1.0, 0.0))
    export("closed", assembly(0.0, -1.0))
    export("nod_down", assembly(1.0, 0.0, 25, 0))
    export("nod_up", assembly(1.0, 0.0, -25, 0))
    export("pan_left", assembly(1.0, 0.0, 0, 60))
    export("ref", assembly(0.55, 0.0))      # 기준 그림처럼 눈을 반쯤 감은 모습
    exploded = assembly(1.0, 0.0)
    for k in ("head_back", "screws"):
        exploded[k] = Pos(0, 35, 0) * exploded[k]                 # 뒤 덮개를 뒤로 빼낸 분해도
    exploded["chassis"] = Pos(0, 0, 0) * exploded["chassis"]
    export("exploded", exploded)
    (OUT / "report.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("\n".join(summary))


if __name__ == "__main__":
    main()
