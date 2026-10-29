import math, sys
sys.path.insert(0, sys.path[0])
from common import *
import icons2 as I
import icons6 as R


def fan2(sel):
    """Веер-диаграмма. Обычное состояние: доли серые и прозрачные вперемежку, выбранное — цветные."""
    c = I.st(sel); k = I.K(c)
    if sel:
        sectors = [(0, 130, c["f"]), (130, 230, c["f2"]), (230, 300, c["f3"]), (300, 360, "none")]
    else:
        sectors = [(0, 130, "rgba(79,72,112,.42)"), (130, 230, "none"), (230, 300, "rgba(79,72,112,.2)"), (300, 360, "none")]
    out = ""
    for a0, a1, f in sectors:
        r = 11 if (f != "none" or not sel) and not (a0 == 300) else 9.5
        x0, y0 = 14 + r * math.cos(math.radians(a0 - 90)), 14 + r * math.sin(math.radians(a0 - 90))
        x1, y1 = 14 + r * math.cos(math.radians(a1 - 90)), 14 + r * math.sin(math.radians(a1 - 90))
        large = 1 if a1 - a0 > 180 else 0
        out += f'<path d="M14 14 L{x0:.2f} {y0:.2f} A{r} {r} 0 {large} 1 {x1:.2f} {y1:.2f} Z" fill="{f}" {k} stroke-width="1.2"/>'
        for a in range(a0 + 18, a1, 22):
            ex, ey = 14 + r * .9 * math.cos(math.radians(a - 90)), 14 + r * .9 * math.sin(math.radians(a - 90))
            out += f'<line x1="14" y1="14" x2="{ex:.2f}" y2="{ey:.2f}" {k} stroke-width=".55" opacity="{1 if sel else .45}"/>'
    out += f'<circle cx="14" cy="14" r="1.6" fill="{c["s"]}"/>'
    return out


body = '<h1>веер: серые и прозрачные доли</h1><p class="sub">Обычное состояние: одна доля тёмно-серая, одна светло-серая, две прозрачные, кромки страниц бледнее. Выбранное — как было, в цвете. Внизу меню целиком: крыша А, стопки без полосок, веер, шестерёнка.</p><div class="row" style="gap:20px">'
body += f'<div class="card glass" style="padding:14px 16px;align-items:center"><div class="cap">было</div><div style="display:flex;gap:10px">{I.icon(I.stats_fan, False, 120)}</div></div>'
body += f'<div class="card glass" style="padding:14px 16px;align-items:center"><div class="cap">стало</div><div style="display:flex;gap:10px">{I.icon(fan2, False, 120)}{I.icon(fan2, True, 120)}</div></div>'
body += '</div><div class="row" style="gap:26px;margin-top:22px">'
fns = [R.roof_a, R.stacks_clean, fan2, I.gear_ribbon]
for sel, cap in [(0, "выбрана главная"), (2, "выбрана статистика")]:
    body += f'<div class="card"><div class="note">{cap}</div>{I.nav(fns, sel)}</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1080))
