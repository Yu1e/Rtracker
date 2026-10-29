import math, random, sys
sys.path.insert(0, sys.path[0])
from common import *

W, H = 178, 156
AX0, AY0, AX1, AY1 = 10, 36, 168, 148      # поле ячеек: всё под заголовком
N = 12                                       # дней подряд на эскизе
COLS, ROWS = 6, 5

# Волшебная палитра: перелив, у каждой ячейки свой оттенок
HOLO = [("#7B5CFF", "#2EC4E6"), ("#FF5CA8", "#FFC94A"), ("#2EC4E6", "#6BE3C8"), ("#B04BE0", "#FF8AC2"),
        ("#5B8CFF", "#C9A8FF"), ("#FF7A59", "#FFD36B"), ("#29C9A8", "#7B5CFF"), ("#FF5C8A", "#9C84FF")]


def grads(prefix):
    out = "<defs>"
    for i, (a, b) in enumerate(HOLO):
        out += (f'<linearGradient id="{prefix}{i}" x1="0" y1="0" x2="1" y2="1">'
                f'<stop offset="0" stop-color="{a}"/><stop offset=".55" stop-color="{b}"/><stop offset="1" stop-color="#fff" stop-opacity=".9"/></linearGradient>')
    return out + "</defs>"


def order_cells(cells):
    """Справа сверху: по рядам сверху вниз, в ряду справа налево."""
    return sorted(cells, key=lambda c: (round(c[1] / 8), -c[0]))


def tile(inner, streak=N):
    return f'''<svg width="{int(W*1.75)}" height="{int(H*1.75)}" viewBox="0 0 {W} {H}" class="glass">
  <text x="{W-12}" y="25" text-anchor="end" font-size="12.5" fill="{INK2}">дней подряд <tspan font-size="24" font-weight="600" fill="{INK}" dx="3">{streak}</tspan></text>
  {inner}</svg>'''


# 1 · лежащие книжки в изометрии ─────────────────────────────────────────────
def iso_books():
    out = [grads("ib")]
    cw = (AX1 - AX0) / COLS; rh = (AY1 - AY0) / ROWS
    cells = []
    for r in range(ROWS):
        for c in range(COLS):
            cells.append((AX0 + c * cw + cw / 2 + (cw / 4 if r % 2 else -cw / 4) * 0.4, AY0 + r * rh + rh * 0.72))
    for i, (x, y) in enumerate(order_cells(cells)):
        u = (11.5, -5.6); v = (-8.5, -4.2); t = 4.6 + (i * 37 % 5) * 0.35
        p0 = (x - 1.5, y)
        def P(a, b, up=0): return (p0[0] + a * u[0] + b * v[0], p0[1] + a * u[1] + b * v[1] - up)
        def poly(pts): return " ".join(f"{px:.1f},{py:.1f}" for px, py in pts)
        top = [P(0, 0, t), P(1, 0, t), P(1, 1, t), P(0, 1, t)]
        front = [P(0, 0), P(1, 0), P(1, 0, t), P(0, 0, t)]      # обрез страниц
        side = [P(0, 0), P(0, 1), P(0, 1, t), P(0, 0, t)]       # корешок
        if i < N:
            g = i % len(HOLO)
            out.append(f'<polygon points="{poly(side)}" fill="{HOLO[g][0]}"/>')
            out.append(f'<polygon points="{poly(front)}" fill="#FFF8EC" stroke="{HOLO[g][0]}" stroke-width=".5"/>')
            for k in (1.4, 2.6, 3.6):
                a, b = P(0.05, 0, k), P(0.95, 0, k)
                out.append(f'<line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="#E5DCC8" stroke-width=".45"/>')
            out.append(f'<polygon points="{poly(top)}" fill="url(#ib{g})"/>')
            a, b = P(0.12, 0.2, t), P(0.12, 0.8, t)
            out.append(f'<line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="#fff" stroke-width=".9" opacity=".8"/>')
            rp = P(0.62, 0, 0.2)
            out.append(f'<path d="M{rp[0]:.1f} {rp[1]:.1f} l.8 4 l1 -1.2 l1 1.2 l.2 -4.4" fill="{HOLO[(g+3)%8][0]}"/>')
        else:
            out.append(f'<polygon points="{poly([P(0,0), P(1,0), P(1,0,t), P(1,1,t), P(0,1,t), P(0,1)])}" fill="rgba(255,255,255,.28)" stroke="{INK3}" stroke-width=".7" stroke-dasharray="1.6 1.2"/>')
    return "".join(out)


# 2 · раскрытые книжки-бабочки ────────────────────────────────────────────────
def butterflies():
    out = [grads("bf")]
    cw = (AX1 - AX0) / COLS; rh = (AY1 - AY0) / ROWS
    cells = [(AX0 + c * cw + cw / 2, AY0 + r * rh + rh / 2 + 1) for r in range(ROWS) for c in range(COLS)]
    random.seed(3)
    for i, (x, y) in enumerate(order_cells(cells)):
        if i < N:
            g = i % len(HOLO); rot = random.uniform(-16, 16); lift = random.uniform(1.5, 3.5)
            out.append(f'<g transform="translate({x:.1f} {y-lift:.1f}) rotate({rot:.0f}) scale(1.06)">'
                       f'<path d="M0 3 C-4 -4 -9 -6 -12 -2 C-10 1 -9 4 -9 7 C-6 4 -3 4 0 6 Z" fill="url(#bf{g})"/>'
                       f'<path d="M0 3 C4 -4 9 -6 12 -2 C10 1 9 4 9 7 C6 4 3 4 0 6 Z" fill="url(#bf{(g+2)%8})"/>'
                       f'<path d="M-2.5 1.5 C-5 -1.6 -7.5 -2.6 -9.5 -1.4 M2.5 1.5 C5 -1.6 7.5 -2.6 9.5 -1.4" stroke="#fff" stroke-width=".7" fill="none" opacity=".85"/>'
                       f'<line x1="0" y1="2.6" x2="0" y2="6.4" stroke="{INK}" stroke-width=".9"/></g>'
                       f'<circle cx="{x+9:.1f}" cy="{y-8:.1f}" r=".9" fill="#fff"/>')
        else:
            out.append(f'<g transform="translate({x:.1f} {y+2:.1f})">'
                       f'<path d="M0 0 Q-6 -3.4 -11 -1.4 V4 Q-6 2 0 4.6 Q6 2 11 4 V-1.4 Q6 -3.4 0 0 Z" fill="rgba(255,255,255,.3)" stroke="{INK3}" stroke-width=".7"/>'
                       f'<line x1="0" y1="0" x2="0" y2="4.6" stroke="{INK3}" stroke-width=".6"/></g>')
    return "".join(out)


# 3 · стеклянные шарики на нитях-ляссе (счёты смелее) ─────────────────────────
def marbles():
    out = ['<defs>']
    for i, (a, b) in enumerate(HOLO):
        out.append(f'<radialGradient id="mb{i}" cx=".35" cy=".3" r=".75"><stop offset="0" stop-color="#fff"/><stop offset=".25" stop-color="{b}"/><stop offset=".8" stop-color="{a}"/><stop offset="1" stop-color="{a}" stop-opacity=".85"/></radialGradient>')
    out.append('</defs>')
    rh = (AY1 - AY0) / ROWS
    cells = []
    for r in range(ROWS):
        y0 = AY0 + r * rh + 7
        x0, x1 = AX0 + 2, AX1 - 2
        sag = 5 + r % 2 * 2
        out.append(f'<path d="M{x0} {y0} Q{(x0+x1)/2} {y0+sag*2} {x1} {y0}" stroke="{HOLO[r][0]}" stroke-width="2.2" fill="none" opacity=".55"/>'
                   f'<path d="M{x0} {y0} Q{(x0+x1)/2} {y0+sag*2} {x1} {y0}" stroke="#fff" stroke-width=".6" fill="none" opacity=".8"/>')
        for c in range(COLS):
            t = (c + 0.5) / COLS
            x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * (x0 + x1) / 2 + t * t * x1
            y = (1 - t) ** 2 * y0 + 2 * (1 - t) * t * (y0 + sag * 2) + t * t * y0
            cells.append((x, y + 4.5))
    for i, (x, y) in enumerate(order_cells(cells)):
        R = 8.4
        if i < N:
            g = i % len(HOLO)
            out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{R}" fill="url(#mb{g})"/>'
                       f'<path d="M{x-5:.1f} {y+2:.1f} C{x-2:.1f} {y-4:.1f} {x+3:.1f} {y+5:.1f} {x+6:.1f} {y-1:.1f}" stroke="{HOLO[(g+4)%8][1]}" stroke-width="1.6" fill="none" opacity=".9"/>'
                       f'<ellipse cx="{x-3:.1f}" cy="{y-3.6:.1f}" rx="2.6" ry="1.5" fill="#fff" opacity=".95"/>')
        else:
            out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{R-.6}" fill="rgba(255,255,255,.25)" stroke="{INK3}" stroke-width=".7"/>'
                       f'<path d="M{x-4:.1f} {y-4:.1f} a6 6 0 0 1 5 -2" stroke="#fff" stroke-width="1" fill="none"/>')
    return "".join(out)


# 4 · созвездие в форме раскрытой книги ──────────────────────────────────────
def constellation():
    out = [f'<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1B1240"/><stop offset="1" stop-color="#3A1E66"/></linearGradient>'
           f'<radialGradient id="glow"><stop offset="0" stop-color="#fff"/><stop offset=".35" stop-color="#FFE7A8"/><stop offset="1" stop-color="#FFC94A" stop-opacity="0"/></radialGradient></defs>',
           f'<rect x="{AX0-2}" y="{AY0-2}" width="{AX1-AX0+4}" height="{AY1-AY0+4}" rx="12" fill="url(#sky)"/>']
    random.seed(9)
    for _ in range(40):
        out.append(f'<circle cx="{random.uniform(AX0, AX1):.1f}" cy="{random.uniform(AY0, AY1):.1f}" r="{random.uniform(.2,.6):.2f}" fill="#fff" opacity="{random.uniform(.3,.8):.2f}"/>')
    # контур раскрытой книги: путь от правого верхнего угла
    cx = (AX0 + AX1) / 2; top = AY0 + 14; bot = AY1 - 12; hw = 66
    def page_top(t, side):   # t 0..1 от края к корешку
        x = cx + side * hw * (1 - t)
        y = top + 6 * math.sin(math.pi * t) * -1 + 10 * t
        return x, y
    pts = []
    for k in range(7): pts.append(page_top(k / 7, 1))               # верх правой страницы, справа налево
    pts.append((cx, top + 10))                                       # корешок сверху
    for k in range(1, 8): pts.append(page_top(1 - k / 7, -1))       # верх левой страницы
    for k in range(1, 4): pts.append((cx - hw, top + k * (bot - top) / 4))   # левый край вниз
    for k in range(0, 4): pts.append((cx - hw + k * hw / 4, bot + 6 * math.sin(math.pi * k / 4)))  # низ левой
    pts.append((cx, bot + 8))
    for k in range(1, 4): pts.append((cx + k * hw / 4, bot + 6 * math.sin(math.pi * k / 4)))
    for k in range(1, 3): pts.append((cx + hw, bot - k * (bot - top) / 3))  # правый край вверх
    pts = pts[:30]
    guide = " ".join(f"{x:.1f},{y:.1f}" for x, y in pts + [pts[0]])
    out.append(f'<polyline points="{guide}" fill="none" stroke="#C9B6FF" stroke-width=".5" stroke-dasharray="1.2 2" opacity=".55"/>')
    for i in range(1, N):
        a, b = pts[i - 1], pts[i]
        out.append(f'<line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="#FFE7A8" stroke-width=".7" opacity=".75"/>')
    # корешок пунктиром — будущая линия
    out.append(f'<line x1="{cx}" y1="{top+10}" x2="{cx}" y2="{bot+8}" stroke="#fff" stroke-width=".5" stroke-dasharray="1 2" opacity=".35"/>')
    for i, (x, y) in enumerate(pts):
        if i < N:
            s = 3.6 if i == N - 1 else 2.6
            out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{s*2.4:.1f}" fill="url(#glow)" opacity=".8"/>'
                       f'<path d="M{x:.1f} {y-s:.1f} Q{x:.1f} {y:.1f} {x+s:.1f} {y:.1f} Q{x:.1f} {y:.1f} {x:.1f} {y+s:.1f} Q{x:.1f} {y:.1f} {x-s:.1f} {y:.1f} Q{x:.1f} {y:.1f} {x:.1f} {y-s:.1f} Z" fill="#fff"/>')
        else:
            out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="1.7" fill="none" stroke="#C9B6FF" stroke-width=".8" opacity=".95"/>')
    return "".join(out)


# 5 · витраж ──────────────────────────────────────────────────────────────────
def clip(poly, a, b, c):
    """Оставить часть многоугольника, где a*x + b*y <= c."""
    out = []
    for i in range(len(poly)):
        p, q = poly[i], poly[(i + 1) % len(poly)]
        fp = a * p[0] + b * p[1] - c; fq = a * q[0] + b * q[1] - c
        if fp <= 0: out.append(p)
        if fp * fq < 0:
            t = fp / (fp - fq)
            out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out


def stained():
    random.seed(21)
    cw = (AX1 - AX0) / COLS; rh = (AY1 - AY0) / ROWS
    seeds = [(AX0 + (c + .5 + random.uniform(-.32, .32)) * cw, AY0 + (r + .5 + random.uniform(-.3, .3)) * rh) for r in range(ROWS) for c in range(COLS)]
    rect = [(AX0, AY0), (AX1, AY0), (AX1, AY1), (AX0, AY1)]
    out = ['<defs>']
    for i, (a, b) in enumerate(HOLO):
        out.append(f'<radialGradient id="sg{i}" cx=".4" cy=".35" r=".8"><stop offset="0" stop-color="#fff" stop-opacity=".95"/><stop offset=".3" stop-color="{b}"/><stop offset="1" stop-color="{a}"/></radialGradient>')
    out.append(f'<clipPath id="win"><rect x="{AX0}" y="{AY0}" width="{AX1-AX0}" height="{AY1-AY0}" rx="12"/></clipPath></defs><g clip-path="url(#win)">')
    ordered = order_cells(seeds)
    for i, s in enumerate(ordered):
        poly = rect
        for o in seeds:
            if o == s: continue
            a, b = o[0] - s[0], o[1] - s[1]
            c = (o[0] ** 2 + o[1] ** 2 - s[0] ** 2 - s[1] ** 2) / 2
            poly = clip(poly, a, b, c)
        d = " ".join(f"{x:.1f},{y:.1f}" for x, y in poly)
        if i < N:
            out.append(f'<polygon points="{d}" fill="url(#sg{i % 8})"/>')
        else:
            out.append(f'<polygon points="{d}" fill="rgba(255,255,255,.35)"/>')
        out.append(f'<polygon points="{d}" fill="none" stroke="#2A2350" stroke-width="1.5" stroke-linejoin="round"/>')
    out.append(f'</g><rect x="{AX0}" y="{AY0}" width="{AX1-AX0}" height="{AY1-AY0}" rx="12" fill="none" stroke="#2A2350" stroke-width="2.2"/>')
    return "".join(out)


cards = [
    ("1 · книжки в 3D", iso_books(), "Каждый день — лежащая книга в изометрии: переливающаяся обложка, кремовый обрез, корешок и хвостик ляссе. Пустые места — пунктирные силуэты книг."),
    ("2 · книжки-бабочки", butterflies(), "Раскрытые книжки, как бабочки: прочитанный день взлетает, чуть поворачивается и переливается. Пустые лежат раскрытыми на столе."),
    ("3 · стеклянные шарики на лентах", marbles(), "Развитие «счётов»: пять провисших лент-ляссе, на них стеклянные шарики с цветным вихрем внутри. Пустые — прозрачные пузырьки."),
    ("4 · созвездие-книга", constellation(), "В плитке кусочек ночного неба. Тридцать звёзд рисуют раскрытую книгу; дни зажигаются от правого верхнего угла, звёзды соединяются линией. Самая свежая — ярче всех."),
    ("5 · витраж", stained(), "Тридцать осколков цветного стекла в свинцовых переплётах. Прочитанный день — осколок загорается, как витраж на солнце. Узор осколков у каждого месяца свой."),
]
body = ('<h1>дней подряд: ячейки смелее</h1><p class="sub">Подписи «до 30» больше нет (она переедет на экран статистики), ячейки занимают всю плитку под заголовком. Тридцать делений, заполнение справа сверху. На эскизах 12 дней.</p><div class="row">')
for cap, inner, note in cards:
    body += f'<div class="card"><div class="cap">{cap}</div>{tile(inner)}<div class="note" style="max-width:300px">{note}</div></div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1060))
