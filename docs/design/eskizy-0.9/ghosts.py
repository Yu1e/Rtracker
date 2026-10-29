import sys
sys.path.insert(0, sys.path[0])
from common import *

W, H = 172, 104


def shelf(ghost, dark):
    ink2 = "#BDB6E0" if dark else INK2
    ink = "#F3F0FF" if dark else INK
    books = [(132, 12, 44, VIO if not dark else "#9A7BFF"), (145, 11, 40, PINK if not dark else "#FF6FB5"), (157, 10, 46, CYAN if not dark else "#3FD0F0")]
    out = [f'<text x="12" y="20" font-size="12" fill="{ink2}">книг за октябрь</text>',
           f'<text x="12" y="52" font-size="30" font-weight="600" fill="{ink}">3<tspan font-size="13" dy="-14" fill="{INK3}">+2</tspan></text>']
    base = 92
    for x, w, h, c in books:
        out.append(f'<rect x="{x}" y="{base-h}" width="{w}" height="{h}" rx="1.5" fill="{c}"/>'
                   f'<rect x="{x}" y="{base-h+5}" width="{w}" height="1.8" fill="#fff" opacity=".7"/>')
    # два призрака домино: первый прислонился к книгам, второй к нему
    import math
    g = []
    w = 10.5
    prev = None
    for h, a in [(42, 12), (39, 25)]:
        r2 = math.radians(a)
        if prev is None:
            xr = 131.4 - h * math.sin(r2)
        else:
            pw, ppx, pa = prev
            r1 = math.radians(pa)
            t = (h * math.cos(r2) - pw * math.sin(r1)) / math.cos(r1)
            xr = ppx - pw * math.cos(r1) + t * math.sin(r1) - h * math.sin(r2)
        g.append((xr, h, a))
        prev = (w, xr, a)
    for xr, h, a in g:
        out.append(f'<g transform="rotate({a} {xr} {base})">{ghost(xr - 10.5, base - h, 10.5, h, dark)}</g>')
    out.append(f'<line x1="70" y1="{base+1.2}" x2="{W-8}" y2="{base+1.2}" stroke="{ink2}" stroke-width="2.5"/>'
               f'<line x1="{W-8.6}" y1="{base+1}" x2="{W-8.6}" y2="40" stroke="{ink2}" stroke-width="2.5" stroke-linecap="round"/>')
    return "".join(out)


def stripes(x, y, w, h, col, op=1):
    return (f'<line x1="{x+1.5}" y1="{y+h*0.12}" x2="{x+w-1.5}" y2="{y+h*0.12}" stroke="{col}" stroke-width="1.3" opacity="{op}"/>'
            f'<line x1="{x+1.5}" y1="{y+h*0.8}" x2="{x+w-1.5}" y2="{y+h*0.8}" stroke="{col}" stroke-width="1.3" opacity="{op}"/>')


def now(x, y, w, h, dark):
    c = "#8A84B0" if dark else "#8C86A6"
    return (f'<rect x="{x+.5}" y="{y+.5}" width="{w-1}" height="{h-1}" rx="1.3" fill="none" stroke="{c}" stroke-width="1.1" stroke-dasharray="2.6 1.9"/>'
            + stripes(x, y, w, h, "#3E3866" if dark else "#D8D0F5"))


def a(x, y, w, h, dark):
    c = "#C9B6FF" if dark else "#3B2A8F"
    return (f'<rect x="{x+.8}" y="{y+.8}" width="{w-1.6}" height="{h-1.6}" rx="1.5" fill="none" stroke="{c}" stroke-width="1.7" stroke-dasharray="3.2 2"/>'
            + stripes(x, y, w, h, c, .8))


def b(x, y, w, h, dark):
    fill = "rgba(255,255,255,.18)" if dark else "rgba(255,255,255,.7)"
    c = "rgba(255,255,255,.9)" if dark else "#4F3BC0"
    return (f'<rect x="{x+.7}" y="{y+.7}" width="{w-1.4}" height="{h-1.4}" rx="1.5" fill="{fill}" stroke="{c}" stroke-width="1.4"/>'
            + stripes(x, y, w, h, c, .7))


def cglass(x, y, w, h, dark):
    c = "#B9A3FF" if dark else VIO
    return (f'<defs><linearGradient id="gg{int(x)}{dark}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{c}" stop-opacity=".45"/><stop offset="1" stop-color="{c}" stop-opacity=".12"/></linearGradient></defs>'
            f'<rect x="{x+.8}" y="{y+.8}" width="{w-1.6}" height="{h-1.6}" rx="1.6" fill="url(#gg{int(x)}{dark})" stroke="{c}" stroke-width="1.6"/>'
            f'<line x1="{x+2.4}" y1="{y+3}" x2="{x+2.4}" y2="{y+h-3}" stroke="#fff" stroke-width="1.2" opacity=".9"/>'
            + stripes(x, y, w, h, c, .9))


def d(x, y, w, h, dark):
    c = "#F3F0FF" if dark else INK
    return (f'<rect x="{x+1.2}" y="{y+1.6}" width="{w-1}" height="{h-1}" rx="1.5" fill="{"#000" if dark else "#3A2A80"}" opacity=".18"/>'
            f'<rect x="{x+.7}" y="{y+.7}" width="{w-1.4}" height="{h-1.4}" rx="1.5" fill="none" stroke="{c}" stroke-width="1.4" stroke-dasharray="1.6 1.6"/>'
            + stripes(x, y, w, h, c, .6))


variants = [
    ("сейчас", now, "Бледный пунктир — на стекле почти пропадает."),
    ("А · тёмно-фиолетовый пунктир", a, "Тот же пунктир, только толще (1,7) и тёмно-фиолетовый. Остаётся «призраком», но читается."),
    ("Б · матовое стекло", b, "Сплошной фиолетовый контур, внутри белое матовое стекло: книга как будто из льда."),
    ("В · стеклянная книга", cglass, "Лиловая заливка тает к краю, контур цвета акцента, белый блик по корешку — как стеклянная фигурка."),
    ("Г · чернильный пунктир с тенью", d, "Частый пунктир цвета текста и лёгкая тень: самый строгий, ближе к ч/б."),
]
body = '<h1>призраки на полке: как сделать их видимыми</h1><p class="sub">Призрак — книга, которая сейчас читается. Сверху светлое стекло, снизу тёмное. Слева — как сейчас.</p>'
for dark in (False, True):
    body += f'<div class="row{" dark" if dark else ""}" style="margin-bottom:22px">'
    for cap, fn, note in variants:
        body += (f'<div class="card"><div class="cap">{cap}</div>'
                 f'<svg width="{int(W*1.5)}" height="{int(H*1.5)}" viewBox="0 0 {W} {H}" class="glass">{shelf(fn, dark)}</svg>'
                 + (f'<div class="note" style="max-width:255px">{note}</div>' if not dark else '') + '</div>')
    body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1520))
