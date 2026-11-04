"""기획서(01-proposal.md)와 봇 커넥트 규약(03-module-protocol.md)을 검토 페이지 한 장(proposal-review.html)으로 만든다.

  python3 docs/pleos/deck/build-review.py

마크다운에서 쓰는 것만 옮긴다: 제목(# ## ###), 문단, 목록(-, 1., - [ ]), 표, 인용(>), 코드 블록(```json, ```mermaid),
굵게·코드·링크. 상태 표시는 배지로: ✅ 글 → 초록, 🔧 글 → 파랑, 표 칸 첫머리 "PT까지" → 주황, "이후"·"공개 대기" → 회색,
*(바뀜)* *(옮김)* → 보라, (실측)(가정)(목표)(추정) → 작은 꼬리표. 저장소 안 상대 링크는 글자만 남긴다(페이지 밖에서는 열리지 않음).
"""
import html
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
OUT = ROOT / "proposal-review.html"
MERMAID = "https://cdnjs.cloudflare.com/ajax/libs/mermaid/10.9.1/mermaid.min.js"

TAGS = ("실측", "가정", "목표", "추정")


def inline(text: str) -> str:
    codes: list[str] = []

    def keep_code(m):
        codes.append(f"<code>{html.escape(m.group(1))}</code>")
        return f"\x00{len(codes) - 1}\x00"

    text = re.sub(r"`([^`]+)`", keep_code, text)
    text = html.escape(text, quote=False)
    text = re.sub(r"\[([^\]]+)\]\((https?://[^)]+)\)", r'<a href="\2">\1</a>', text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r"\1", text)            # 저장소 안 링크는 글자만
    text = re.sub(r"(?<![\"'>])(https://claude\.ai/artifact/\S+?)(?=[)\s]|$)", r'<a href="\1">\1</a>', text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"\*\((바뀜|옮김)\)\*", r'<span class="b b-ch">\1</span>', text)
    for t in TAGS:
        text = text.replace(f"({t})", f'<span class="t">{t}</span>')
    text = re.sub(r"✅ (권유 동작|동작|있음)", r'<span class="b b-ok">\1</span>', text)
    text = re.sub(r"🔧 (11/15 목표|11/\d+(?:~\d+)?(?: 녹화)?|구현)", r'<span class="b b-soon">\1</span>', text)
    text = re.sub(r"🔧(?=\))", '<span class="b b-soon">개발 중</span>', text)
    text = text.replace("🔧", '<span class="b b-soon">11/15 목표</span>')
    text = re.sub(r"\x00(\d+)\x00", lambda m: codes[int(m.group(1))], text)
    return text


def cell(text: str) -> str:
    out = inline(text.strip())
    for word, cls in (("PT까지", "b-pt"), ("이후", "b-later"), ("공개 대기", "b-later"), ("계획", "b-later")):
        if out == word or out.startswith(word + " "):
            out = f'<span class="b {cls}">{word}</span>' + out[len(word):]
    return out


def table(rows: list[str]) -> str:
    def split(r):
        return [c for c in r.strip().strip("|").split("|")]

    head = split(rows[0])
    body = [split(r) for r in rows[2:]]
    out = ['<div class="tbl"><table><thead><tr>']
    out += [f"<th>{inline(h.strip())}</th>" for h in head]
    out.append("</tr></thead><tbody>")
    for r in body:
        out.append("<tr>" + "".join(f"<td>{cell(c)}</td>" for c in r) + "</tr>")
    out.append("</tbody></table></div>")
    return "".join(out)


def blocks(md: str) -> list[tuple[str, str]]:
    """마크다운 → (종류, html) 목록. 종류 h1/h2 는 카드를 나누는 데 쓴다."""
    lines = md.splitlines()
    out: list[tuple[str, str]] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("```"):
            lang = line[3:].strip()
            j = i + 1
            while j < len(lines) and not lines[j].startswith("```"):
                j += 1
            body = "\n".join(lines[i + 1:j])
            if lang == "mermaid":
                out.append(("p", f'<div class="diagram"><pre class="mermaid">{html.escape(body)}</pre></div>'))
            else:
                out.append(("p", f'<pre class="code"><code>{html.escape(body)}</code></pre>'))
            i = j + 1
            continue
        if line.startswith("# "):
            out.append(("h1", inline(line[2:])))
        elif line.startswith("## "):
            out.append(("h2", inline(line[3:])))
        elif line.startswith("### "):
            out.append(("p", f"<h3>{inline(line[4:])}</h3>"))
        elif line.startswith("|"):
            j = i
            while j < len(lines) and lines[j].startswith("|"):
                j += 1
            out.append(("p", table(lines[i:j])))
            i = j
            continue
        elif line.startswith("> "):
            j = i
            parts = []
            while j < len(lines) and lines[j].startswith(">"):
                parts.append(lines[j][1:].strip())
                j += 1
            out.append(("p", f'<div class="quote">{inline(" ".join(parts))}</div>'))
            i = j
            continue
        elif re.match(r"^(- |\d+\. )", line):
            ordered = bool(re.match(r"^\d+\. ", line))
            j = i
            items = []
            while j < len(lines) and re.match(r"^(- |\d+\. |  )", lines[j]) and lines[j].strip():
                if lines[j].startswith("  ") and items:
                    items[-1] += " " + lines[j].strip()
                else:
                    items.append(re.sub(r"^(- |\d+\. )", "", lines[j]))
                j += 1
            tag = "ol" if ordered else "ul"
            lis = []
            for it in items:
                box = re.match(r"^\[( |x)\] ", it)
                if box:
                    mark = "☑" if box.group(1) == "x" else "☐"
                    lis.append(f'<li class="check">{mark} {inline(it[4:])}</li>')
                else:
                    lis.append(f"<li>{inline(it)}</li>")
            out.append(("p", f"<{tag}>{''.join(lis)}</{tag}>"))
            i = j
            continue
        elif line.strip() == "---":
            pass
        elif line.strip():
            j = i
            parts = []
            while j < len(lines) and lines[j].strip() and not re.match(r"^(#|\||>|```|- |\d+\. )", lines[j]):
                parts.append(lines[j].strip())
                j += 1
            text = " ".join(parts)
            if not text.startswith("원본 검토 페이지"):
                out.append(("p", f"<p>{inline(text)}</p>"))
            i = j
            continue
        i += 1
    return out


def sections(md: str, prefix: str) -> tuple[str, str, list[tuple[str, str]]]:
    """h1 제목, 머리말 html, [(h2 제목, 본문 html)]"""
    title, intro, cards = "", [], []
    for kind, h in blocks(md):
        if kind == "h1":
            title = h
        elif kind == "h2":
            cards.append([h, []])
        elif cards:
            cards[-1][1].append(h)
        else:
            intro.append(h)
    return title, "".join(intro), [(t, "".join(b)) for t, b in cards]


def anchor(prefix: str, n: int) -> str:
    return f"{prefix}-{n}"


def render_part(md: str, prefix: str, label: str) -> tuple[str, str]:
    title, intro, cards = sections(md, prefix)
    toc = "".join(f'<a href="#{anchor(prefix, n)}">{re.sub(r"<[^>]+>", "", t)}</a>' for n, (t, _) in enumerate(cards))
    body = [f'<section class="part" id="{prefix}"><div class="part-head"><p class="eyebrow">{label}</p><h1>{title}</h1>'
            f'<div class="intro">{intro}</div><nav class="toc" aria-label="{label} 목차">{toc}</nav></div>']
    for n, (t, b) in enumerate(cards):
        cls = "card decide" if t.startswith("검토용") else "card"
        body.append(f'<section class="{cls}" id="{anchor(prefix, n)}"><h2>{t}</h2>{b}</section>')
    body.append("</section>")
    return "".join(body), title


EXTRA_CSS = """
.part { display: grid; gap: 22px; }
.part + .part { margin-top: 36px; padding-top: 28px; border-top: 2px solid var(--line); }
.part-head { display: grid; gap: 10px; }
.intro p { color: var(--muted); }
.code { background: var(--bg); border: 1px solid var(--line); border-radius: 8px; padding: 12px 14px; overflow-x: auto;
  font-family: var(--font-mono); font-size: 13px; line-height: 1.55; }
.diagram { overflow-x: auto; background: var(--paper); border: 1px solid var(--line); border-radius: 8px; padding: 10px; }
.diagram pre.mermaid { margin: 0; background: transparent; }
li.check { list-style: none; margin-left: -18px; }
.topnav { display: flex; gap: 10px; flex-wrap: wrap; }
.topnav a { border: 1px solid var(--line); border-radius: 999px; padding: 4px 14px; text-decoration: none; color: var(--ink); background: var(--paper); font-size: 14px; }
.topnav a:hover, .topnav a:focus-visible { border-color: var(--accent); }
.ask { border: 2px solid var(--accent); }
.ask h2 { color: var(--accent); }
"""

ASK = """<section class="card ask" id="ask"><h2>정한 것 (11/4)</h2><ol>
<li><strong>앱 이름:</strong> 스노봇 커넥트(SnowBot Connect). 커넥트 앱이라는 성격이 이름에 드러나고, 첫 봇 스노봇과도 이어집니다.</li>
<li><strong>규약 이름:</strong> 봇 커넥트 규약(Bot Connect Protocol, BCP). 여러 봇을 위한 규약이라 스노봇 이름을 넣지 않았습니다. 메시지 버전 표시는 <code>bcp</code>입니다.</li>
<li><strong>두 번째 가상 봇:</strong> LED 큐브. 얼굴 없이 빛·효과음·습도 센서만 있어서, 같은 규약으로 다르게 표현하는 모습과 봇이 주는 정보가 잘 보입니다.</li>
</ol></section>"""


def main() -> None:
    css = (HERE / "review-style.css").read_text() + EXTRA_CSS
    p1, t1 = render_part((ROOT / "01-proposal.md").read_text(), "plan", "1부 · 기획서")
    p2, t2 = render_part((ROOT / "03-module-protocol.md").read_text(), "spec", "2부 · 연결 규약")
    legend = """<div class="legend">
    <span><span class="b b-ok">동작</span> 에뮬레이터·서버에서 확인됨</span>
    <span><span class="b b-soon">11/15 목표</span> 접수 전에 만들 것 (아직 안 됨)</span>
    <span><span class="b b-pt">PT까지</span> 12/3 전</span>
    <span><span class="b b-later">이후</span> 선정 뒤</span>
    <span><span class="b b-ch">바뀜</span> 3판에서 고친 부분</span>
  </div>
  <div class="legend"><span><span class="t">실측</span> 직접 잰 값</span><span><span class="t">가정</span> 근거가 약한 입력값</span>
    <span><span class="t">목표</span> 이루려는 값</span><span><span class="t">추정</span> 공개 단가로 계산</span></div>"""
    page = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>스노봇 기획서 검토판</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;500;700&family=IBM+Plex+Mono:wght@500&display=swap" rel="stylesheet">
<style>
{css}
</style>
</head>
<body>
<div class="wrap">
<header class="top">
  <div class="eyebrow">Pleos 차량용 앱 공모전 · 접수양식 2장 「앱 프로젝트 소개」 + 봇 커넥트 규약 · 2026-11-04</div>
  <h1>스노봇 기획서 검토판</h1>
  <p class="lead">11/4 방향 결정을 반영한 기획서 4판과 봇 커넥트 규약(BCP) v0.3입니다. 이 앱은 감성 봇을 차에 연결하는 커넥트 앱이고, 중심 기능은 차와 봇이 정보와 기능을 주고받는 규약입니다: 연결 · 봇 설명 · 정보와 해석 · 피드백.</p>
  {legend}
  <nav class="topnav" aria-label="문서"><a href="#ask">정한 것</a><a href="#plan">1부 기획서</a><a href="#spec">2부 연결 규약</a></nav>
</header>
{ASK}
{p1}
{p2}
<footer>원본: 저장소 docs/pleos/01-proposal.md · docs/pleos/03-module-protocol.md · 만드는 코드: docs/pleos/deck/build-review.py</footer>
</div>
<script src="{MERMAID}"></script>
<script>
(function () {{
  var root = document.documentElement;
  var dark = root.getAttribute('data-theme') === 'dark' ||
    (root.getAttribute('data-theme') !== 'light' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  if (window.mermaid) {{
    try {{ mermaid.initialize({{ startOnLoad: true, theme: dark ? 'dark' : 'neutral', securityLevel: 'strict', fontFamily: 'IBM Plex Sans KR, sans-serif' }}); }}
    catch (e) {{ console.log('mermaid', e); }}
  }}
}})();
</script>
</body>
</html>
"""
    OUT.write_text(page)
    print(OUT, len(page))


if __name__ == "__main__":
    main()
