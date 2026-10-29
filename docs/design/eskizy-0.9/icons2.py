import math, sys
sys.path.insert(0, sys.path[0])
from common import *
from icons import gear_path


def st(sel):
    if sel:
        return dict(s=VIO, f="#C9BCFF", f2="#FF9CC9", f3="#9FE8FF", w=1.7)
    return dict(s=INK2, f="none", f2="none", f3="none", w=1.6)


def K(c, w=None):
    return f'stroke="{c["s"]}" stroke-width="{w or c["w"]}" stroke-linejoin="round" stroke-linecap="round"'


def home_tent(sel):
    c = st(sel); k = K(c)
    return (  # вигвам: раскрытая книга поставлена страницами вниз, под ней одна лежащая книга
        f'<path d="M14 2.6 L2.8 17.8 L5.6 17.8 L14 6.4 L22.4 17.8 L25.2 17.8 Z" fill="{c["f"]}" {k}/>'
        f'<path d="M6.6 17.8 L14 7.8 M8.8 17.8 L14 9.6 M11.2 17.8 L14 11.6" {k} stroke-width=".9"/>'
        f'<path d="M21.4 17.8 L14 7.8 M19.2 17.8 L14 9.6 M16.8 17.8 L14 11.6" {k} stroke-width=".9"/>'
        f'<rect x="2.4" y="18.6" width="23.2" height="5.8" rx="1.4" fill="{c["f2"]}" {k}/>'
        f'<path d="M5.2 18.6 V24.4" {k} stroke-width="1.1"/>'
        f'<path d="M7.6 21.5 H23.2" {k} stroke-width=".8"/>'
    )


def iso(x, y, s, fill, c, t=3.8):
    """Лежащая книга по диагонали: обложка, обрез страниц, корешок."""
    u = (7.6 * s, -3.8 * s); v = (-5.4 * s, -2.7 * s); t *= s
    P = lambda a, b, up=0: (x + a * u[0] + b * v[0], y + a * u[1] + b * v[1] - up)
    pts = lambda L: " ".join(f"{px:.2f},{py:.2f}" for px, py in L)
    k = K(c, c["w"] * 0.85)
    return (f'<polygon points="{pts([P(0,0), P(0,1), P(0,1,t), P(0,0,t)])}" fill="{fill if fill != "none" else "none"}" {k}/>'
            f'<polygon points="{pts([P(0,0), P(1,0), P(1,0,t), P(0,0,t)])}" fill="{"#fff" if fill != "none" else "none"}" {k}/>'
            f'<polygon points="{pts([P(0,0,t), P(1,0,t), P(1,1,t), P(0,1,t)])}" fill="{fill}" {k}/>')


def books_iso(sel):
    c = st(sel)
    return (iso(7.2, 12.4, .92, c["f"], c) + iso(20, 12.4, .92, c["f2"], c) +
            iso(7.2, 25.6, .92, c["f3"], c) + iso(20, 25.6, .92, c["f"], c))


def books_iso_stack(sel):
    c = st(sel)
    return (iso(12.6, 27, 1.1, c["f"], c) + iso(13.8, 20.6, 1.02, c["f2"], c) +
            iso(12.4, 14.4, .98, c["f3"], c) + iso(13.6, 8.4, .92, c["f"], c))


def gear_ribbon(sel):
    c = st(sel); k = K(c)
    return (  # шестерёнка, через центр продето ляссе: хвост озорно убежал вбок и расстелился волной
        f'<path d="{gear_path(10.6, 10.4, 9, 6.8)}" fill="{c["f"]}" {k}/>'
        f'<circle cx="10.6" cy="10.4" r="2.8" fill="#fff" {k}/>'
        f'<path d="M9.4 11.8 C8.6 17 10.6 21 14 21.6 C17 22 18.4 19.6 21.2 20.4 C23.2 21 23.8 23.4 26.4 23.2 L27.6 25.6 L25.4 25.2 L24.8 27.4 C22.4 26.4 21.6 24 19.6 23.8 C17.6 23.6 16.4 26 13.2 25.4 C8.4 24.4 6.6 19 7.6 11.8 Z" fill="{c["f2"]}" {k} stroke-width="1.2"/>'
    )


def stats_stacks(sel):
    c = st(sel)
    out = ""
    for x, n, f in [(6.8, 2, c["f3"]), (14.2, 4, c["f2"]), (21.6, 6, c["f"])]:
        for i in range(n):
            out += iso(x - 1.2, 27 - i * 3.6, .5, f, c, t=6.6)
    return out


def stats_ribbon(sel):
    c = st(sel); k = K(c)
    return (  # лента-ляссе идёт вверх ступеньками и кончается ласточкиным хвостом
        f'<path d="M2.6 22 L9 15.6 L13.2 19 L21 9.6 L19 7.6 L26 5.4 L24.2 12.6 L22.4 10.8 L13.6 21.6 L9.2 18.2 L4.6 22.8 Z" fill="{c["f2"]}" {k} stroke-width="1.3"/>'
        f'<line x1="2.6" y1="25.6" x2="25.4" y2="25.6" {k}/>'
    )


def stats_fan(sel):
    c = st(sel); k = K(c)
    out = ""   # книга, раскрытая веером на весь круг: страницы — доли диаграммы
    sectors = [(0, 130, c["f"]), (130, 230, c["f2"]), (230, 300, c["f3"]), (300, 360, "none")]
    for a0, a1, f in sectors:
        r = 11 if f != "none" else 9.5
        x0, y0 = 14 + r * math.cos(math.radians(a0 - 90)), 14 + r * math.sin(math.radians(a0 - 90))
        x1, y1 = 14 + r * math.cos(math.radians(a1 - 90)), 14 + r * math.sin(math.radians(a1 - 90))
        large = 1 if a1 - a0 > 180 else 0
        out += f'<path d="M14 14 L{x0:.2f} {y0:.2f} A{r} {r} 0 {large} 1 {x1:.2f} {y1:.2f} Z" fill="{f}" {k} stroke-width="1.2"/>'
        for a in range(a0 + 18, a1, 22):    # кромки страниц внутри доли
            ex, ey = 14 + r * .9 * math.cos(math.radians(a - 90)), 14 + r * .9 * math.sin(math.radians(a - 90))
            out += f'<line x1="14" y1="14" x2="{ex:.2f}" y2="{ey:.2f}" {k} stroke-width=".6"/>'
    out += f'<circle cx="14" cy="14" r="1.6" fill="{c["s"]}"/>'
    return out


def stats_marks(sel):
    c = st(sel); k = K(c)
    out = ""   # из верхнего обреза книги торчат закладки разной высоты
    for x, h, f in [(7, 6, c["f3"]), (12.4, 10, c["f2"]), (17.8, 14, c["f"])]:
        y = 14 - h
        out += f'<path d="M{x} 15 V{y} H{x+3.6} V15" fill="{f}" {k} stroke-width="1.2"/><path d="M{x} {y} " />'
    out += (f'<rect x="3.6" y="14" width="20.8" height="10.6" rx="1.6" fill="#fff" fill-opacity="{.9 if sel else 0}" {k}/>'
            f'<line x1="5.2" y1="21.8" x2="22.8" y2="21.8" {k} stroke-width=".9"/>'
            f'<line x1="23.6" y1="16.6" x2="9" y2="16.6" {k} stroke-width=".7" stroke-dasharray="1 1.4"/>')
    return out


def hourglass_btn():
    return (f'<svg width="62" height="62" viewBox="0 0 62 62" style="margin:-14px 2px 0"><defs><linearGradient id="hb" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{VIO2}"/><stop offset=".6" stop-color="#C651E8"/><stop offset="1" stop-color="#FF7A93"/></linearGradient></defs>'
            f'<circle cx="31" cy="31" r="28" fill="url(#hb)" stroke="#fff" stroke-width="2"/><ellipse cx="31" cy="19" rx="18" ry="8" fill="#fff" opacity=".3"/>'
            f'<g transform="translate(31 31)"><path d="M-8 -11 H8 M-8 11 H8" stroke="#fff" stroke-width="2.4" stroke-linecap="round"/>'
            f'<path d="M-6 -11 C-6 -3 -1.6 -2 -1.6 0 C-1.6 2 -6 3 -6 11 H6 C6 3 1.6 2 1.6 0 C1.6 -2 6 -3 6 -11 Z" fill="none" stroke="#fff" stroke-width="2"/>'
            f'<path d="M-4 -6 H4 L.6 -1 H-.6 Z M-5 10 C-3 6 3 6 5 10 Z" fill="#fff"/></g></svg>')


def icon(fn, sel, size=80):
    bg = 'style="background:rgba(255,255,255,.55);border-radius:16px"' if sel else ''
    return f'<svg width="{size}" height="{size}" viewBox="-2 -2 32 32" {bg}>{fn(sel)}</svg>'


def nav(fns, sel_i):
    items = []
    for i, fn in enumerate(fns):
        sel = i == sel_i
        lens = ('<div style="position:absolute;inset:3px 4px;border-radius:22px;background:linear-gradient(180deg,rgba(255,255,255,.8),rgba(123,92,255,.22));border:1px solid #fff"></div>' if sel else '')
        items.append(f'<div style="position:relative;flex:1;display:flex;align-items:center;justify-content:center;height:52px">{lens}<div style="position:relative">{icon(fn, sel, 36)}</div></div>')
    items.insert(2, hourglass_btn())
    return f'<div class="glass" style="display:flex;width:390px;border-radius:32px;padding:2px 8px;height:56px;align-items:center;margin-top:16px">{"".join(items)}</div>'


groups = [
    ("главная", [(home_tent, "вигвам: раскрытая книга обложкой вверх, под ней одна лежащая книга")]),
    ("книги", [(books_iso, "1 · четыре книжки в 3D квадратом"), (books_iso_stack, "2 · четыре книжки в 3D стопкой, чуть вразнобой")]),
    ("настройки", [(gear_ribbon, "ляссе продето в шестерёнку, хвост ушёл вбок и расстелился, на конце ласточкин хвост")]),
    ("статистика", [(stats_stacks, "1 · стопки 3D-книжек: 2, 4, 6 — столбики из книг"),
                    (stats_ribbon, "2 · лента-ляссе ступеньками вверх, на конце ласточкин хвост"),
                    (stats_fan, "3 · книга, раскрытая веером на весь круг: доли — пачки страниц"),
                    (stats_marks, "4 · из книги торчат закладки разной высоты")]),
]
body = '<h1>значки меню, второй круг</h1><p class="sub">Без подписей. Слева обычное состояние, справа выбранное. Внизу меню целиком: две пары значков, между ними песочные часы таймера.</p><div class="row" style="gap:20px">'
for name, vs in groups:
    body += f'<div class="card glass" style="padding:16px 18px;width:310px;box-sizing:border-box"><div class="cap">{name}</div>'
    for fn, note in vs:
        body += f'<div style="display:flex;gap:14px;align-items:center">{icon(fn, False)}{icon(fn, True)}<div class="note" style="max-width:150px">{note}</div></div>'
    body += '</div>'
body += '</div><h1 style="margin-top:30px;font-size:20px">так в меню</h1><div class="row" style="gap:26px">'
for st_fn, cap in [(stats_stacks, "статистика 1"), (stats_ribbon, "статистика 2"), (stats_fan, "статистика 3"), (stats_marks, "статистика 4")]:
    body += f'<div class="card"><div class="note">книги 1, {cap}, выбрана главная</div>{nav([home_tent, books_iso, st_fn, gear_ribbon], 0)}</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1400))
