"""조립 안내서 빌드: template.html 의 <img src="파일.jpg"> 를 data URI 로 바꿔 docs/assembly.html 한 파일로 만든다.
template.html 은 같은 폴더의 jpg 를 그대로 읽으므로 따로 열어도 사진이 보인다."""
import base64, pathlib, re
here = pathlib.Path(__file__).parent
html = (here / "template.html").read_text(encoding="utf-8")
def inline(m):
    data = (here / m.group(1)).read_bytes()
    return 'src="data:image/jpeg;base64,' + base64.b64encode(data).decode() + '"'
out, n = re.subn(r'src="([\w.-]+\.jpg)"', inline, html)
target = here.parent.parent / "assembly.html"
target.write_text(out, encoding="utf-8")
print(target, round(len(out) / 1024), "KB,", n, "images")
