import math, sys
sys.path.insert(0, sys.path[0])
from common import *
import icons2 as I
import stacks2 as S

G = lambda a: f"rgba(79,72,112,{a})"


def band(Sx, E, hw, color):
    """Страница-лента: от корешка круто вверх, потом отгибается наружу; к корешку сужается."""
    dx, dy = E[0] - Sx[0], E[1] - Sx[1]
    L = math.hypot(dx, dy); nx, ny = -dy / L, dx / L
    Et = (E[0] + nx * hw, E[1] + ny * hw); Eb = (E[0] - nx * hw, E[1] - ny * hw)
    C1 = (Sx[0] + dx * .18 + nx * hw * .6, E[1] + (Sx[1] - E[1]) * .42)
    C2 = (Sx[0] + dx * .3 - nx * hw * .4, E[1] + (Sx[1] - E[1]) * .3)
    return (f'<path d="M{Sx[0]:.2f} {Sx[1]:.2f} Q{C1[0]:.2f} {C1[1]:.2f} {Et[0]:.2f} {Et[1]:.2f} '
            f'L{Eb[0]:.2f} {Eb[1]:.2f} Q{C2[0]:.2f} {C2[1]:.2f} {Sx[0]:.2f} {Sx[1]:.2f} Z" fill="{color}"/>')


def rainbow(sel):
    c = I.st(sel)
    left = [(1, 16.4), (2, 12.2), (4.2, 8.4), (7.8, 5)]
    right = [(27, 16.4), (26, 12.2), (23.8, 8.4), (20.2, 5)]
    if sel:
        lc = ["#2EC4A0", "#8BD94A", "#FF9A3D", "#FF4F5E"]
        rc = ["#2EB7F0", "#5B5BE0", "#A64BD9", "#FF4FA0"]
    else:
        lc = rc = [G(.9), G(.65), G(.45), G(.28)]
    out = ""
    for i in range(4):
        out += band((13.4, 18.4), left[i], 1.25, lc[i]) + band((14.6, 18.4), right[i], 1.25, rc[i])
    base = c["s"] if not sel else "#4F4870"
    out += f'<path d="M0.6 19.4 Q8 17.6 12.8 20.6 Q14 21.8 15.2 20.6 Q20 17.6 27.4 19.4" fill="none" stroke="{base}" stroke-width="2" stroke-linecap="round"/>'
    return out


def slabs(sel):
    if sel:
        fl, fr, bl, br = "#2EB7F0", "#FF2E7E", "#8BD94A", "#FFC94A"
    else:
        fl, fr, bl, br = G(.85), G(.62), G(.3), G(.22)
    def page(x0, x1, dx, dy, col):
        s = 1 if x1 > x0 else -1
        return (f'<path d="M{x0+dx} {7.6+dy} Q{(x0+x1)/2+dx} {4.6+dy} {x1+dx} {6.4+dy} L{x1+dx+s*.4} {21.4+dy} '
                f'Q{(x0+x1)/2+dx} {19.6+dy} {x0+dx} {22.8+dy} Z" fill="{col}"/>')
    out = page(13.4, 2.6, -1.6, 1.4, bl) + page(14.6, 25.4, 1.6, 1.4, br)   # задние листы
    out += page(13.4, 2.6, 0, 0, fl) + page(14.6, 25.4, 0, 0, fr)              # передние
    return out


body = '<h1>главная: два узнаваемых силуэта</h1><p class="sub">Подогнано под меню: в обычном состоянии серые доли, как у веера статистики, в выбранном — цвет. Вторая без квадратиков. Слева обычное состояние, справа выбранное.</p><div class="row" style="gap:20px">'
for fn, cap in [(rainbow, "А · радуга страниц"), (slabs, "Б · две страницы слоями")]:
    body += f'<div class="card glass" style="padding:14px 16px;align-items:center"><div class="cap">{cap}</div><div style="display:flex;gap:10px">{I.icon(fn, False, 150)}{I.icon(fn, True, 150)}</div></div>'
body += '</div><div class="row" style="gap:26px;margin-top:22px">'
for fn, cap in [(rainbow, "А"), (slabs, "Б")]:
    for sel, sc in [(0, "выбрана главная"), (1, "выбраны книги")]:
        body += f'<div class="card"><div class="note">{cap}, {sc}</div>{I.nav([fn, S.stacks, I.stats_fan, I.gear_ribbon], sel)}</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1000))
