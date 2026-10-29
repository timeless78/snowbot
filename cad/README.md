# cad — SnowBot 3D 설계 (코드)

3D 모양을 Python 코드(build123d)로 만든다. 치수는 코드 위쪽 숫자로 모여 있어, 부품을 실측하면 숫자만 고치고 다시 실행한다.
결정 배경과 순서: [../docs/04-body-build-plan.md](../docs/04-body-build-plan.md)

## 실행

```bash
cd cad
.venv/bin/python snowbot_layout.py   # out/*.stl + out/report.txt (부품 겹침 검사)
MPLCONFIGDIR=$TMPDIR/mpl .venv/bin/python render.py   # out/preview.png (정면·옆 단면·비스듬히)
```

환경 처음 만들기 (2026-10-26, Python 3.12, build123d 0.13):

```bash
uv venv --python 3.12 .venv
VIRTUAL_ENV=$PWD/.venv uv pip install build123d trimesh matplotlib
```

## 파일

| 파일 | 내용 |
|---|---|
| `snowbot_layout.py` | **배치 모델 (출력용 아님).** 몸통·목·머리 무리로 나눠 10가지 자세(눈·눈썹·끄덕임·좌우)에서 겹침 검사 → `out/report.txt` |
| `eye_module.py` | 1단계 눈 기구 (흰자 돔·검은자·카메라·눈꺼풀·축·크랭크·눈썹·슬라이더·서보) |
| `neck_module.py` | 2단계 목 관절 (베어링 자리·회전판·요크·좌우/끄덕임 서보·선 지나는 홈) |
| `head_module.py` | 3단계 머리 껍데기 앞뒤 나누기·이음선 나사·환기 슬롯, 머리 안 틀·고정 기둥 |
| `body_module.py` | 4단계 몸통 껍데기·목 받침판·좌우 서보 받침·바닥판·무게추·패드·스피커·USB-C |
| `oneclick_kit.py` | 원클릭팩토리용: 부품마다 출력 방향 잡고 같은 색 작은 부품을 한 판으로 → `out/print/oneclick/` (복사본 프로젝트 `print/oneclick/`) |
| `print_kit.py` | 5단계 출력 STL(1차·2차), 공차 시험 판 3장, 부품표 `parts.csv`, 부품 그림 → 복사본은 프로젝트 `print/` |
| `render.py` | 자세별 STL → `out/preview.png`, `out/preview_eye.png`, `out/preview_neck.png`, `out/preview_compare.png`(기준 그림과 비교), `out/preview_head.png`(3단계), `out/preview_body.png`(4단계) |
| `out/` | 실행 결과 (다시 만들 수 있음) |

## 현재 상태

단계별 진행과 결정: [../docs/05-cad-stages.md](../docs/05-cad-stages.md). 1~5단계 완료. 출력 주문표는 [../docs/06-print-order.md](../docs/06-print-order.md). 다음은 부품 실측(11/7) 후 숫자 고치기.
