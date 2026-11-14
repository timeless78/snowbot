E='#8a8f98'; A='#2f6fde'; INK='#1f2328'; Q='#5b6170'; TINT='#f1f3f6'
def mk(id): return f"<defs><marker id='{id}' viewBox='0 0 10 10' refX='9' refY='5' markerWidth='6' markerHeight='6' orient='auto-start-reverse'><path d='M0 0L10 5L0 10z' fill='{E}'/></marker></defs>"
def t(x,y,s,size=13,w=400,fill=INK,anchor='middle'): return f"<text x='{x}' y='{y}' text-anchor='{anchor}' font-size='{size}' font-weight='{w}' fill='{fill}'>{s}</text>"
head="<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 760 {h}' width='1520' height='{h2}' font-family='Noto Sans CJK KR' font-size='13'><rect width='760' height='{h}' fill='white'/>"
# 1 loop
s=head.format(h=384,h2=768)+mk('a')
s+=t(24,34,'앱이 판단하고 봇이 표현하면, 운전자의 반응이 다시 입력이 됩니다',15,600,anchor='start')
s+=f"<g fill='none' stroke='{E}' stroke-width='1.25'>"
for d in ['M196 100L280 160','M196 160L280 180','M196 220L280 200','M196 280L280 220','M480 190H548','M640 238V306','M470 330H110V304']:
    s+=f"<path d='{d}' marker-end='url(#a)'/>"
s+="</g>"
for cy,a,b in [(100,'차 신호','경고등·기어·창문·공조'),(160,'내비','목적지까지 거리·시간'),(220,'봇 센서','실내 CO₂·습도'),(280,'운전자 말','묻기·수락·거절·요청')]:
    s+=f"<rect x='24' y='{cy-24}' width='172' height='48' rx='8' fill='none' stroke='{E}' stroke-width='1.25'/>"+t(110,cy-4,a,w=600)+t(110,cy+14,b,11.5,fill=Q)
s+=f"<rect x='280' y='142' width='200' height='96' rx='8' fill='{A}' fill-opacity='0.12' stroke='{A}' stroke-width='2'/>"+t(380,170,'판단 (앱)',15,600)+t(380,192,'무슨 상황인지')+t(380,210,'안전 · 권유 · 인사')+t(380,228,'근거 숫자')
s+=f"<rect x='548' y='142' width='184' height='96' rx='8' fill='none' stroke='{E}' stroke-width='1.25'/>"+t(640,170,'표현 (봇의 몸)',15,600)+t(640,192,'표정 · 몸짓')+t(640,210,'목소리')+t(640,228,'화면 입자 구름')
s+=f"<rect x='470' y='306' width='266' height='48' rx='8' fill='none' stroke='{E}' stroke-width='1.25'/>"+t(603,326,'운전자 반응',w=600)+t(603,344,'대답 · 창문 열기 · 외기 전환',11.5,fill=Q)
s+=t(290,322,'다시 입력으로',11.5,fill=Q)+"</svg>"
open('fig1.svg','w').write(s)
# 2 arch
s=head.format(h=404,h2=808)+mk('b')
s+=t(24,34,'차 데이터는 앱만 읽고, 봇과 백엔드에는 앱이 필요한 것만 보냅니다',15,600,anchor='start')
s+=f"<g fill='none' stroke='{E}' stroke-width='1.25'>"
for d,dash in [('M204 188H284',''),('M476 188H556',''),('M380 236V300',''),('M646 236V340H476',"stroke-dasharray='4 4'")]:
    s+=f"<path d='{d}' {dash} marker-start='url(#b)' marker-end='url(#b)'/>"
s+="</g>"
s+=f"<rect x='24' y='70' width='180' height='234' rx='8' fill='{TINT}' stroke='{E}' stroke-width='1.25'/>"+t(114,96,'Pleos 차',15,600)
for y,a,b in [(112,'Vehicle SDK','차 신호'),(176,'NaviHelper','목적지 값 · 화면 열기'),(240,'음성 인식·합성','차 안 엔진')]:
    s+=f"<rect x='40' y='{y}' width='148' height='48' rx='8' fill='white' stroke='{E}' stroke-width='1.25'/>"+t(114,y+20,a,w=600)+t(114,y+38,b,11.5,fill=Q)
s+=f"<rect x='284' y='140' width='192' height='96' rx='8' fill='{A}' fill-opacity='0.12' stroke='{A}' stroke-width='2'/>"+t(380,168,'커넥트 앱',15,600)+t(380,190,'판단 · 표현')+t(380,208,'차 상태 요약')+t(380,226,'인터넷 없이도 동작',11.5,fill=Q)
s+=f"<rect x='556' y='140' width='180' height='96' rx='8' fill='none' stroke='{E}' stroke-width='1.25'/>"+t(646,168,'봇',15,600)+t(646,190,'스노봇 · LED 큐브')+t(646,208,'센서 · 표정 · 목소리')
s+=f"<rect x='284' y='300' width='192' height='80' rx='8' fill='none' stroke='{E}' stroke-width='1.25'/>"+t(380,326,'백엔드 (클라우드)',15,600)+t(380,346,'AI 대답 (Claude)')+t(380,364,'AI 키는 여기에만',11.5,fill=Q)
s+=t(244,180,'읽기',11.5,fill=Q)+t(244,206,'말하기',11.5,fill=Q)+t(516,180,'봇 커넥트 규약',11.5,fill=Q)+t(516,206,'표현 · 센서 값',11.5,fill=Q)
s+=t(392,264,'말 + 차 상태 요약',11.5,fill=Q,anchor='start')+t(392,282,'대답 문장',11.5,fill=Q,anchor='start')+t(561,330,'시제품은 Wi-Fi 중계',11.5,fill=Q)+"</svg>"
open('fig2.svg','w').write(s)
