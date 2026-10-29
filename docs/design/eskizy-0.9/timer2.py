import math, sys
sys.path.insert(0, sys.path[0])
from common import *

DEFS = f'''<defs>
<linearGradient id="rb" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{VIO2}"/><stop offset=".55" stop-color="#C651E8"/><stop offset="1" stop-color="#FF7A93"/></linearGradient>
<linearGradient id="hot" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{PINK}"/><stop offset="1" stop-color="#FF8A3D"/></linearGradient>
<linearGradient id="gl" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".5"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
<radialGradient id="b1" cx=".15" cy=".1" r=".5"><stop offset="0" stop-color="#B9A8FF"/><stop offset="1" stop-color="#B9A8FF" stop-opacity="0"/></radialGradient>
<radialGradient id="b2" cx=".9" cy=".35" r=".45"><stop offset="0" stop-color="#FFB8D6"/><stop offset="1" stop-color="#FFB8D6" stop-opacity="0"/></radialGradient>
<radialGradient id="b3" cx=".2" cy=".8" r=".5"><stop offset="0" stop-color="#9FE8FF"/><stop offset="1" stop-color="#9FE8FF" stop-opacity="0"/></radialGradient>
</defs>'''


def tile(x, y, w, h, r=18, extra=""):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="#fff" fill-opacity=".58" stroke="#fff" stroke-width="1.2" {extra}/>'


def hourglass(cx, cy, s=1, col="#fff", sand=.5):
    k = s
    return (f'<g transform="translate({cx} {cy}) scale({k})">'
            f'<path d="M-7 -10 H7 M-7 10 H7" stroke="{col}" stroke-width="2.2" stroke-linecap="round"/>'
            f'<path d="M-5.5 -10 C-5.5 -3 -1.5 -2 -1.5 0 C-1.5 2 -5.5 3 -5.5 10 H5.5 C5.5 3 1.5 2 1.5 0 C1.5 -2 5.5 -3 5.5 -10 Z" fill="none" stroke="{col}" stroke-width="1.8" stroke-linejoin="round"/>'
            f'<path d="M-3.6 {-6+4*(1-sand)} H3.6 L0.6 -1 H-0.6 Z" fill="{col}" opacity=".9"/>'
            f'<path d="M-4.6 9 C-3 {9-6*sand} 3 {9-6*sand} 4.6 9 Z" fill="{col}" opacity=".9"/>'
            f'<line x1="0" y1="0" x2="0" y2="7" stroke="{col}" stroke-width="1" stroke-dasharray="1 1.4"/></g>')


def home(today_extra="", week_head="", today_bar=None, book_extra="", nav=None, overlay="", book_dy=0):
    s = [DEFS, '<rect width="390" height="800" fill="#F6F2FF"/>',
         '<rect width="390" height="800" fill="url(#b1)"/><rect width="390" height="800" fill="url(#b2)"/><rect width="390" height="800" fill="url(#b3)"/>']
    # неделя: сегодня последним справа
    s.append(tile(12, 14, 366, 150))
    s.append(f'<text x="26" y="38" font-size="13" fill="{INK2}">минут чтения по дням</text>' + week_head)
    vals = [35, 60, 12, 0, 48, 72, 25 if today_bar is None else today_bar]
    days = ["пт", "сб", "вс", "пн", "вт", "ср", "чт"]
    for i, v in enumerate(vals):
        x = 28 + i * 49; h = 4 if v == 0 else max(14, v)
        col = ["#C9BCFF", "#A58EFF", "#8466FF", "#6343F0"][min(3, v // 20)] if v else "#D8D0F5"
        s.append(f'<rect x="{x}" y="{136-h}" width="36" height="{h}" rx="8" fill="{col}"/>')
        s.append(f'<text x="{x+18}" y="{128-h}" font-size="13" font-weight="600" fill="{INK}" text-anchor="middle">{v}</text>')
        s.append(f'<text x="{x+18}" y="155" font-size="11.5" fill="{INK2}" text-anchor="middle">{days[i]}</text>')
    s.append(f'<rect x="{28+6*49-3}" y="{136-max(14, vals[6])-3}" width="42" height="{max(14, vals[6])+6}" rx="10" fill="none" stroke="{INK}" stroke-width="1.4"/>')
    # книга
    s.append(f'<text x="18" y="186" font-size="13" fill="{INK2}">сейчас читаю</text>')
    by = 200 + book_dy
    s.append(f'<g>'
             f'<rect x="22" y="{by+8}" width="150" height="225" rx="2" fill="#E3DCFA" stroke="{INK2}" stroke-width="1.2"/>'
             f'<rect x="14" y="{by}" width="150" height="225" rx="3" fill="#fff" fill-opacity=".94" stroke="{INK2}" stroke-width="1.4"/>'
             f'<path d="M74 {by+225} c4 14 20 18 34 16" stroke="{VIO}" stroke-width="8" fill="none"/>'
             f'<text x="34" y="{by+28}" font-size="12" fill="{INK2}">М. Булгаков</text>'
             f'<text x="34" y="{by+54}" font-size="19" font-weight="600" fill="{INK}">Мастер</text>'
             f'<text x="34" y="{by+77}" font-size="19" font-weight="600" fill="{INK}">и Маргарита</text>'
             f'<rect x="11.5" y="{by+138}" width="153" height="64" fill="#fff" stroke="{INK2}" stroke-width="1"/>'
             f'<text x="34" y="{by+172}" font-size="20" font-weight="600" fill="{INK}">5 ч 12 мин</text>'
             + "".join(f'<rect x="{34+i*13}" y="{by+181}" width="10" height="8" rx="1.5" fill="{"#A58EFF" if i % 3 else "none"}" stroke="#A58EFF"/>' for i in range(9))
             + book_extra + '</g>')
    # полка и читать
    s.append(tile(184, 230, 194, 116, 16))
    s.append(f'<text x="198" y="252" font-size="12.5" fill="{INK2}">книг за октябрь</text><text x="198" y="288" font-size="32" font-weight="600" fill="{INK}">3</text>')
    for i, (h, c) in enumerate([(44, VIO), (40, PINK), (48, CYAN)]):
        s.append(f'<rect x="{326+i*13}" y="{338-h}" width="11" height="{h}" rx="1.5" fill="{c}"/>')
    s.append(f'<line x1="280" y1="339" x2="370" y2="339" stroke="{INK2}" stroke-width="2.5"/>')
    s.append(f'<rect x="184" y="356" width="194" height="98" rx="26" fill="url(#rb)"/><rect x="188" y="359" width="186" height="48" rx="23" fill="url(#gl)"/>'
             f'<text x="281" y="409" font-size="28" font-weight="600" fill="#fff" text-anchor="middle">читать</text><text x="281" y="429" font-size="13" fill="#fff" text-anchor="middle">ЛитРес</text>')
    # читалки — остаются
    s.append(f'<text x="18" y="476" font-size="13" fill="{INK2}">открыть читалку</text>')
    for i, n in enumerate(["ЛитРес", "Яндекс Книги", "AlReader", "Свет"]):
        x = 12 + i * 93
        s.append(f'<rect x="{x}" y="486" width="87" height="42" rx="21" fill="#fff" fill-opacity=".5" stroke="#fff" stroke-width="1.2"/><text x="{x+43.5}" y="512" font-size="12.5" fill="{INK}" text-anchor="middle">{n}</text>')
    # сегодня и дни подряд
    s.append(tile(12, 540, 178, 156))
    s.append(f'<text x="101" y="562" font-size="13" fill="{INK2}" text-anchor="middle">сегодня за книгой</text>'
             f'<circle cx="101" cy="620" r="35" fill="none" stroke="#362A6B" stroke-opacity=".18" stroke-width="9"/>'
             f'<path d="M101 585 A35 35 0 0 1 135 628" fill="none" stroke="{VIO2}" stroke-width="9" stroke-linecap="round"/>'
             f'<text x="101" y="626" font-size="24" font-weight="600" fill="{INK}" text-anchor="middle">25</text><text x="101" y="640" font-size="11" fill="{INK2}" text-anchor="middle">мин</text>'
             f'<text x="101" y="684" font-size="12" fill="{INK2}" text-anchor="middle">цель: 60 мин</text>' + today_extra)
    s.append(tile(200, 540, 178, 156))
    s.append(f'<text x="364" y="564" font-size="12.5" fill="{INK2}" text-anchor="end">дней подряд <tspan font-size="24" font-weight="600" fill="{INK}">12</tspan></text>')
    for i in range(30):
        r, c = i // 5, 4 - i % 5
        x = 276 + c * 18.4; y = 578 + r * 17
        if i < 12:
            s.append(f'<rect x="{x}" y="{y+2}" width="15" height="13" rx="1.4" fill="{[VIO, PINK, CYAN, AMBER][i % 4]}"/>')
        else:
            s.append(f'<rect x="{x+.5}" y="{y+2.5}" width="14" height="12" rx="1.4" fill="none" stroke="{INK3}" stroke-width=".8" stroke-dasharray="1.8 1.3"/>')
    s.append(f'<text x="212" y="686" font-size="11" fill="{INK2}">до 30: 18</text>')
    # меню
    s.append(nav if nav is not None else nav_plain())
    s.append(overlay)
    return f'<svg width="{int(390*0.64)}" height="{int(800*0.64)}" viewBox="0 0 390 800" style="border-radius:22px;box-shadow:0 4px 18px rgba(58,42,128,.18)">' + "".join(s) + '</svg>'


def nav_icons(xs):
    out = ""
    for i, x in enumerate(xs):
        col = VIO if i == 0 else INK2
        if i == 0:
            out += f'<rect x="{x-30}" y="722" width="60" height="44" rx="22" fill="#fff" fill-opacity=".8" stroke="#fff"/>'
        out += f'<rect x="{x-9}" y="736" width="18" height="16" rx="3" fill="none" stroke="{col}" stroke-width="1.8"/>'
    return out


def nav_plain():
    return (f'<rect x="12" y="716" width="366" height="56" rx="28" fill="#fff" fill-opacity=".7" stroke="#fff" stroke-width="1.2"/>'
            + nav_icons([66, 152, 238, 324]))


# ── А · кнопка-песочные часы посередине меню ────────────────────────────────
def nav_center(running):
    cap = (f'<rect x="12" y="716" width="366" height="56" rx="28" fill="#fff" fill-opacity=".7" stroke="#fff" stroke-width="1.2"/>'
           + nav_icons([50, 120, 270, 340]))
    if running:
        return cap + (f'<circle cx="195" cy="728" r="38" fill="{PINK}" opacity=".18"/>'
                      f'<circle cx="195" cy="728" r="31" fill="url(#hot)" stroke="#fff" stroke-width="2"/>'
                      f'<text x="195" y="728" font-size="19" font-weight="600" fill="#fff" text-anchor="middle">12</text>'
                      f'<text x="195" y="742" font-size="10" fill="#fff" text-anchor="middle">мин</text>')
    return cap + (f'<circle cx="195" cy="728" r="31" fill="url(#rb)" stroke="#fff" stroke-width="2"/>'
                  f'<rect x="168" y="702" width="54" height="26" rx="13" fill="url(#gl)"/>' + hourglass(195, 728, 1.15))


# ── Б · капля над меню, растягивается в пилюлю ──────────────────────────────
def drop(running):
    if running:
        return (f'<rect x="150" y="652" width="228" height="52" rx="26" fill="url(#hot)" stroke="#fff" stroke-width="1.5"/>'
                f'<rect x="156" y="655" width="216" height="22" rx="11" fill="url(#gl)"/>'
                + hourglass(178, 678, .95, sand=.3) +
                f'<text x="200" y="674" font-size="11" fill="#fff">Мастер и Маргарита</text>'
                f'<text x="200" y="694" font-size="18" font-weight="600" fill="#fff">12 мин</text>'
                f'<circle cx="352" cy="678" r="15" fill="#fff" fill-opacity=".3"/><rect x="346" y="672" width="12" height="12" rx="2" fill="#fff"/>')
    return (f'<circle cx="350" cy="676" r="26" fill="url(#rb)" stroke="#fff" stroke-width="1.8"/>'
            f'<ellipse cx="344" cy="664" rx="13" ry="7" fill="#fff" opacity=".45"/>' + hourglass(350, 677, 1))


# ── В · в шапке недели: таймер дописывает сегодняшний столбик ───────────────
def week_pill(running):
    if running:
        return (f'<rect x="268" y="22" width="100" height="26" rx="13" fill="url(#hot)"/>'
                f'<circle cx="282" cy="35" r="4" fill="#fff"/><text x="292" y="40" font-size="13" font-weight="600" fill="#fff">12 мин</text>'
                f'<rect x="347" y="29" width="11" height="11" rx="2" fill="#fff"/>')
    return (f'<rect x="268" y="22" width="100" height="26" rx="13" fill="#fff" fill-opacity=".8" stroke="{VIO}" stroke-width="1.2"/>'
            + hourglass(284, 35, .62, col=VIO) + f'<text x="298" y="40" font-size="13" fill="{VIO}">таймер</text>')


# ── Г · потянуть книгу вниз ─────────────────────────────────────────────────
def pull_hint():
    # подсказка на свободном месте обложки, между названием и манжеткой
    return (f'<rect x="34" y="300" width="112" height="24" rx="12" fill="{VIO}" fill-opacity=".1"/>'
            f'<path d="M46 305 v13 m-5 -5 l5 5 l5 -5" stroke="{VIO}" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>'
            f'<text x="58" y="316" font-size="10.5" fill="{VIO}">потяните — таймер</text>')


def pulled():
    return (f'<rect x="10" y="196" width="158" height="233" rx="6" fill="none" stroke="{PINK}" stroke-width="3" opacity=".7"/>'
            f'<rect x="4" y="190" width="170" height="245" rx="10" fill="none" stroke="{PINK}" stroke-width="2" opacity=".25"/>'
            f'<rect x="40" y="290" width="98" height="32" rx="16" fill="url(#hot)"/>'
            f'<text x="80" y="311" font-size="15" font-weight="600" fill="#fff" text-anchor="middle">12 мин</text>'
            f'<rect x="114" y="300" width="11" height="11" rx="2" fill="#fff"/>')


rows = [
    ("А · песочные часы в центре меню",
     home(nav=nav_center(False)), home(nav=nav_center(True)),
     "Меню делится на две пары, между ними выступает круглая стеклянная кнопка с песочными часами. Таймер на виду на любом экране и ничего не теснит. Когда идёт — кнопка розово-оранжевая, внутри минуты, вокруг тихо пульсирует ореол. Нажатие останавливает."),
    ("Б · капля над меню",
     home(overlay=drop(False)), home(overlay=drop(True)),
     "Над правым нижним углом висит стеклянная капля с песочными часами. Нажали — капля перетекает в пилюлю: название книги, минуты, «стоп». Пилюля видна на всех экранах, её можно утянуть пальцем в сторону."),
    ("В · в шапке «минут по дням»",
     home(week_head=week_pill(False)), home(week_head=week_pill(True), today_bar=37),
     "Таймер стоит справа в заголовке недели: он добавляет минуты именно в сегодняшний столбик. Когда идёт — пилюля розовая с минутами, а сегодняшний столбик растёт на глазах."),
    ("Г · потянуть книгу",
     home(book_extra=pull_hint()), home(book_dy=6, book_extra=pulled()),
     "Кнопки нет совсем: книгу тянут пальцем вниз, как достают с полки. Книга чуть опускается, вокруг светится розовая рамка, на обложке минуты и «стоп». Под книгой первое время подсказка, потом исчезает."),
]
body = '<h1>таймер: ещё четыре места</h1><p class="sub">Ряд кнопок читалок остаётся на месте. Слева — таймер ждёт, справа — идёт 12 минут. Значки меню на эскизах условные.</p>'
for cap, a, b, note in rows:
    body += f'<div class="row" style="align-items:center;margin-bottom:26px"><div class="card" style="width:300px"><div class="cap">{cap}</div><div class="note" style="max-width:290px">{note}</div></div>{a}{b}</div>'
open(sys.argv[1], "w").write(page(body, width=940))
