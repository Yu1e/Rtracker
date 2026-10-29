import sys
sys.path.insert(0, sys.path[0])
from common import *
import icons2 as I
import icons3 as J


def P(*pts):
    return " ".join(f"{x:.2f},{y:.2f}" for x, y in pts)


def roof(sel, variant=0):
    """Раскрытая книга корешком вверх, в полуфас: передний торец со страницами и правая крышка уходит вглубь."""
    c = I.st(sel); k = I.K(c)
    A = (10, 7.2); L = (2.4, 22); R = (17.6, 22)            # передний торец: корешок и края крышек
    d = (8.4, -4.6) if variant == 0 else (7, -3.4)          # глубина книги
    A2 = (A[0] + d[0], A[1] + d[1]); R2 = (R[0] + d[0], R[1] + d[1])
    out = ""
    # правая крышка — плоскость в перспективе
    out += f'<polygon points="{P(A, R, R2, A2)}" fill="{c["f"]}" {k}/>'
    # корешок — гребень крыши
    out += f'<line x1="{A[0]}" y1="{A[1]}" x2="{A2[0]}" y2="{A2[1]}" {k} stroke-width="{c["w"]+.4}"/>'
    # передний торец: под крышками клинья страниц, между ними пусто
    def wedge(base, inner):
        return f'<polygon points="{P(A, base, inner)}" fill="{"#fff" if sel else "none"}" {k} stroke-width=".9"/>'
    out += wedge(L, (L[0] + 3.4, L[1])) + wedge(R, (R[0] - 3.4, R[1]))
    for t in (.45, .72):     # кромки страниц внутри клиньев
        for base, s in ((L, 1), (R, -1)):
            x = A[0] + (base[0] + s * 1.7 - A[0]) * t
            y = A[1] + (base[1] - A[1]) * t
            out += f'<line x1="{A[0]:.2f}" y1="{A[1]:.2f}" x2="{x:.2f}" y2="{y:.2f}" {k} stroke-width=".5" opacity=".7"/>'
    # крышки по краям торца — толще страниц
    out += f'<polyline points="{P(L, A, R)}" fill="none" stroke="{c["s"]}" stroke-width="{c["w"]+.5}" stroke-linejoin="round" stroke-linecap="round"/>'
    return out


def roof_a(sel): return roof(sel, 0)
def roof_b(sel): return roof(sel, 1)


def stacks_clean(sel):
    s = J.two_stacks(4, 3, sel)
    import re
    # без вертикальных полосок-корешков: они читались как свисающие ляссе
    return re.sub(r'<line x1="[^"]+" y1="[^"]+" x2="[^"]+" y2="[^"]+" stroke="[^"]+" stroke-width="[^"]+" stroke-linejoin="round" stroke-linecap="round" stroke-width="\.7"/>', '', s)


groups = [("главная · А", roof_a), ("главная · Б, площе", roof_b), ("книги, без полосок", stacks_clean)]
body = '<h1>вигвам в полуфас и стопки без полосок</h1><p class="sub">Одна раскрытая книга корешком вверх: спереди видны края крышек и клинья страниц, правая крышка уходит вглубь. Слева обычное состояние, справа выбранное.</p><div class="row" style="gap:20px">'
for n, fn in groups:
    body += f'<div class="card glass" style="padding:14px 16px;align-items:center"><div class="cap">{n}</div><div style="display:flex;gap:10px">{I.icon(fn, False, 110)}{I.icon(fn, True, 110)}</div></div>'
body += '</div><div class="row" style="gap:26px;margin-top:22px">'
for home, cap in [(roof_a, "вариант А"), (roof_b, "вариант Б")]:
    body += f'<div class="card"><div class="note">{cap}, выбрана главная</div>{I.nav([home, stacks_clean, I.stats_fan, I.gear_ribbon], 0)}</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1080))
