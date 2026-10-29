import sys
sys.path.insert(0, sys.path[0])
from common import *
import icons2 as I


def P(*pts): return " ".join(f"{x:.2f},{y:.2f}" for x, y in pts)


def stacks(sel, dx=2.4, dy=-2.2):
    """Две стопки в 2,5D: у каждой книги свой торец, своя боковина и (у верхней) крышка."""
    c = I.st(sel); k = I.K(c, 1.0)
    fills = [c["f"], c["f2"], c["f3"], c["f"]]
    bg = "#F4F0FC"

    def book(x, y, w, h, f, top):
        face = f if sel else bg
        side = "#E3DCFA" if sel else bg
        o = (f'<polygon points="{P((x+w, y), (x+w+dx, y+dy), (x+w+dx, y+h+dy), (x+w, y+h))}" fill="{side}" {k}/>'
             f'<rect x="{x:.2f}" y="{y:.2f}" width="{w}" height="{h}" rx=".6" fill="{face}" {k}/>')
        if top:
            o += f'<polygon points="{P((x, y), (x+dx, y+dy), (x+w+dx, y+dy), (x+w, y))}" fill="{side}" {k}/>'
        return o

    def stack(x0, base, n, shift):
        o = ""; jit = [0, .9, -.5, .6]; widths = [14, 12.8, 13.6, 12.2]; h = 4
        y = base
        for i in range(n):
            y -= h
            o += book(x0 + jit[i], y, widths[i], h, fills[(i + shift) % 4], i == n - 1)
        return o

    return stack(10.6, 22.6, 4, 1) + stack(1.6, 27.4, 3, 0)


body = '<h1>книги: стопки, у каждой книги своя боковина</h1><p class="sub">Сзади стопка из четырёх, впереди из трёх. Каждая книга в перспективе отделена от соседней: свой торец, своя боковина, у верхней видна крышка. Слева обычное состояние, справа выбранное.</p><div class="row" style="gap:20px">'
body += f'<div class="card glass" style="padding:14px 16px;align-items:center"><div class="cap">стопки</div><div style="display:flex;gap:10px">{I.icon(stacks, False, 140)}{I.icon(stacks, True, 140)}</div></div>'
body += f'<div class="card"><div class="note">в меню, выбраны книги</div>{I.nav([I.home_tent, stacks, I.stats_fan, I.gear_ribbon], 1)}</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1000))
