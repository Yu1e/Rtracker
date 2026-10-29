import sys
sys.path.insert(0, sys.path[0])
from common import *
import icons2 as I
import stacks2 as S
from rainbow import band, G


def rainbow_theme(sel, scheme=1):
    c = I.st(sel)
    left = [(1, 16.4), (2, 12.2), (4.2, 8.4), (7.8, 5)]
    right = [(27, 16.4), (26, 12.2), (23.8, 8.4), (20.2, 5)]
    if sel:
        if scheme == 1:    # обе стороны одинаково: от лилового снаружи к розовому у середины
            lc = rc = ["#6343F0", "#8F74FF", "#C9A4FF", "#FF8AC2"]
        else:              # слева к розовому, справа к голубому
            lc = ["#6343F0", "#8F74FF", "#C9A4FF", "#FF8AC2"]
            rc = ["#6343F0", "#8F74FF", "#9FC9FF", "#5FD3F0"]
    else:
        lc = rc = [G(.9), G(.65), G(.45), G(.28)]
    out = ""
    for i in range(4):
        out += band((13.4, 18.4), left[i], 1.25, lc[i]) + band((14.6, 18.4), right[i], 1.25, rc[i])
    base = c["s"] if not sel else "#4A2FC4"
    out += f'<path d="M0.6 19.4 Q8 17.6 12.8 20.6 Q14 21.8 15.2 20.6 Q20 17.6 27.4 19.4" fill="none" stroke="{base}" stroke-width="2" stroke-linecap="round"/>'
    return out


def r1(sel): return rainbow_theme(sel, 1)
def r2(sel): return rainbow_theme(sel, 2)


body = '<h1>главная: радуга страниц в общей гамме</h1><p class="sub">Обложка тёмно-фиолетовая, страницы от лилового к розовому (и голубому). Слева обычное состояние, справа выбранное.</p><div class="row" style="gap:20px">'
for fn, cap in [(r1, "1 · обе стороны к розовому"), (r2, "2 · слева к розовому, справа к голубому")]:
    body += f'<div class="card glass" style="padding:14px 16px;align-items:center"><div class="cap">{cap}</div><div style="display:flex;gap:10px">{I.icon(fn, False, 150)}{I.icon(fn, True, 150)}</div></div>'
body += '</div><div class="row" style="gap:26px;margin-top:22px">'
for fn, cap in [(r1, "1"), (r2, "2")]:
    body += f'<div class="card"><div class="note">{cap}, выбрана главная</div>{I.nav([fn, S.stacks, I.stats_fan, I.gear_ribbon], 0)}</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1000))
