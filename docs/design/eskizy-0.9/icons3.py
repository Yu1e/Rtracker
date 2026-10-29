import math, sys
sys.path.insert(0, sys.path[0])
from common import *
import icons2 as I


def home_tent2(sel):
    c = I.st(sel); k = I.K(c)
    return (  # вигвам: крышки обложки цветные, страницы под ними прозрачные
        f'<path d="M14 2.6 L2.8 17.8 L5.6 17.8 L14 6.4 L22.4 17.8 L25.2 17.8 Z" fill="{c["f"]}" {k}/>'
        f'<path d="M7.4 17.8 L14 8.6 M10.6 17.8 L14 12 M20.6 17.8 L14 8.6 M17.4 17.8 L14 12" {k} stroke-width=".7" opacity=".45"/>'
        f'<rect x="2.4" y="18.6" width="23.2" height="5.8" rx="1.4" fill="{c["f2"]}" {k}/>'
        f'<path d="M5.2 18.6 V24.4" {k} stroke-width="1.1"/>'
        f'<path d="M7.6 21.5 H23.2" {k} stroke-width=".8"/>'
    )


def fan_half(cx, cy, r, a0, a1, cols, c, k):
    """Полукруг из пачек страниц: каждая пачка — доля диаграммы."""
    out = ""
    step = (a1 - a0) / len(cols)
    for i, f in enumerate(cols):
        b0, b1 = a0 + i * step, a0 + (i + 1) * step
        p = lambda a, rr=r: (cx + rr * math.cos(math.radians(a)), cy + rr * math.sin(math.radians(a)))
        x0, y0 = p(b0); x1, y1 = p(b1)
        out += f'<path d="M{cx} {cy} L{x0:.2f} {y0:.2f} A{r} {r} 0 0 1 {x1:.2f} {y1:.2f} Z" fill="{f}" {k} stroke-width="1"/>'
    return out


def stats_two_books(sel):
    c = I.st(sel); k = I.K(c)
    cx, cy, r = 14, 14, 11
    # нижняя книга: обложка лежит по диаметру, страницы веером вниз; верхняя — веером вверх
    top = fan_half(cx, cy - 1.2, r - .4, 186, 354, [c["f"], c["f2"], c["f3"]], c, k)
    bot = fan_half(cx, cy + 1.2, r - .4, 6, 174, [c["f2"], "none", c["f"]], c, k)
    # обложки — по краям вееров: у каждой книги две крышки, вместе плоский крестик через центр
    def cover(cy0, a):
        x = cx + (r + 1.2) * math.cos(math.radians(a)); y = cy0 + (r + 1.2) * math.sin(math.radians(a))
        return f'<line x1="{cx}" y1="{cy0}" x2="{x:.2f}" y2="{y:.2f}" {k} stroke-width="2"/>'
    covers = cover(cy - 1.2, 186) + cover(cy - 1.2, 354) + cover(cy + 1.2, 6) + cover(cy + 1.2, 174)
    covers += f'<circle cx="{cx}" cy="{cy}" r="1.3" fill="{c["s"]}"/>'
    return bot + top + covers


def book_flat(x, y, w, h, f, c, k, shift=0):
    return (f'<rect x="{x+shift}" y="{y}" width="{w}" height="{h}" rx="1" fill="{f}" {k} stroke-width="1.1"/>'
            f'<line x1="{x+shift+2.2}" y1="{y}" x2="{x+shift+2.2}" y2="{y+h}" {k} stroke-width=".8"/>')


def two_stacks(back_n, front_n, sel):
    c = I.st(sel); k = I.K(c)
    fills = [c["f"], c["f2"], c["f3"], c["f"]]
    out = ""

    def stack(x0, base, n, shade):
        o = ""
        jit = [0, 1.2, -.6, .8]
        widths = [15, 13.6, 14.4, 12.8]
        y = base
        for i in range(n):
            w = widths[i]; x = x0 + jit[i]; h = 4
            y -= h
            f = fills[(i + shade) % 4] if sel else "#F4F0FC"
            o += (f'<rect x="{x:.1f}" y="{y:.1f}" width="{w}" height="{h}" rx=".9" fill="{f}" {k} stroke-width="1.05"/>'
                  f'<line x1="{x+w-2.4:.1f}" y1="{y+.6:.1f}" x2="{x+w-2.4:.1f}" y2="{y+h-.6:.1f}" {k} stroke-width=".7"/>')
        # верхняя крышка в 3D: ромбик сверху
        x = x0 + jit[n - 1]; w = widths[n - 1]
        o += f'<path d="M{x:.1f} {y:.1f} L{x+2.6:.1f} {y-2.4:.1f} H{x+w+2.6:.1f} L{x+w:.1f} {y:.1f} Z" fill="{fills[(n + shade) % 4] if sel else "#F4F0FC"}" {k} stroke-width="1.05"/>'
        o += f'<path d="M{x+w:.1f} {y:.1f} L{x+w+2.6:.1f} {y-2.4:.1f} V{base-2.4:.1f} L{x+w:.1f} {base:.1f} Z" fill="{"#E3DCFA" if sel else "#F4F0FC"}" {k} stroke-width="1.05"/>'
        return o

    out += stack(11.6, 23.6, back_n, 1)                         # задняя: выше и правее
    out += f'<rect x="2" y="{27.4 - front_n*4 - 1}" width="17" height="{front_n*4 + 1}" fill="{"#fff" if sel else "none"}" opacity="0"/>'
    out += stack(1.4, 27.6, front_n, 0)                         # передняя: ниже и левее
    return out


def back_high(sel): return two_stacks(4, 3, sel)
def front_high(sel): return two_stacks(3, 4, sel)


groups = [
    ("главная", [(home_tent2, "вигвам: страницы прозрачные, цветные только крышки и лежащая книга")]),
    ("статистика", [(stats_two_books, "две раскрытые книги одна на другой: у верхней веер страниц вверх, у нижней вниз, вместе круг-диаграмма"),
                    (I.stats_fan, "прежний веер — запасной")]),
    ("книги", [(back_high, "стопка из четырёх сзади, из трёх впереди, чуть выдвинута"),
               (front_high, "наоборот: высокая впереди")]),
]
body = '<h1>значки меню, третий круг</h1><p class="sub">Слева обычное состояние, справа выбранное. Ниже меню целиком с таймером посередине.</p><div class="row" style="gap:20px">'
for name, vs in groups:
    body += f'<div class="card glass" style="padding:16px 18px;width:340px;box-sizing:border-box"><div class="cap">{name}</div>'
    for fn, note in vs:
        body += f'<div style="display:flex;gap:14px;align-items:center">{I.icon(fn, False, 96)}{I.icon(fn, True, 96)}<div class="note" style="max-width:120px">{note}</div></div>'
    body += '</div>'
body += '</div><div class="row" style="gap:26px;margin-top:22px">'
for books, cap in [(back_high, "высокая стопка сзади"), (front_high, "высокая стопка впереди")]:
    body += f'<div class="card"><div class="note">{cap}, выбраны книги</div>{I.nav([home_tent2, books, stats_two_books, I.gear_ribbon], 1)}</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1180))
