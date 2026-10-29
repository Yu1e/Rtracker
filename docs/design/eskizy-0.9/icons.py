import math, sys
sys.path.insert(0, sys.path[0])
from common import *

# Значки рисуются в поле 28×28: контур — обычное состояние, с заливкой — выбранный пункт.

def st(sel):
    if sel:
        return dict(s=VIO, f="#C9BCFF", f2="#FF9CC9", w=1.7)
    return dict(s=INK2, f="none", f2="none", w=1.6)


def home1(sel):
    c = st(sel)
    k = f'stroke="{c["s"]}" stroke-width="{c["w"]}" stroke-linejoin="round" stroke-linecap="round"'
    return (  # крыша — две книги домиком, стены — два корешка, фундамент — две лежащие книги
        f'<g transform="rotate(-33 14 4)"><rect x="3.6" y="2.2" width="11.2" height="3.6" rx="1" fill="{c["f2"]}" {k}/></g>'
        f'<g transform="rotate(33 14 4)"><rect x="13.2" y="2.2" width="11.2" height="3.6" rx="1" fill="{c["f"]}" {k}/></g>'
        f'<rect x="6" y="11" width="3.4" height="8" rx=".8" fill="{c["f"]}" {k}/>'
        f'<rect x="18.6" y="11" width="3.4" height="8" rx=".8" fill="{c["f2"]}" {k}/>'
        f'<rect x="3.5" y="19.2" width="21" height="3.2" rx=".9" fill="{c["f"]}" {k}/>'
        f'<rect x="4.5" y="22.4" width="19" height="3.2" rx=".9" fill="{c["f2"]}" {k}/>'
        f'<line x1="21.5" y1="20.2" x2="21.5" y2="21.4" {k}/>'
    )


def home2(sel):
    c = st(sel)
    k = f'stroke="{c["s"]}" stroke-width="{c["w"]}" stroke-linejoin="round" stroke-linecap="round"'
    return (  # крыша — раскрытая книга страницами вниз, под ней три корешка
        f'<path d="M3 13 Q8 8.5 14 4.5 Q20 8.5 25 13 Q20 11.3 14 12 Q8 11.3 3 13 Z" fill="{c["f2"]}" {k}/>'
        f'<line x1="14" y1="4.8" x2="14" y2="12" {k}/>'
        f'<rect x="6" y="13.5" width="4.4" height="11.5" rx=".9" fill="{c["f"]}" {k}/>'
        f'<rect x="11.8" y="15.5" width="4.4" height="9.5" rx=".9" fill="none" {k}/>'
        f'<rect x="17.6" y="13.5" width="4.4" height="11.5" rx=".9" fill="{c["f"]}" {k}/>'
        f'<line x1="6" y1="16" x2="10.4" y2="16" {k}/><line x1="17.6" y1="16" x2="22" y2="16" {k}/>'
    )


def cards1(sel):
    c = st(sel)
    k = f'stroke="{c["s"]}" stroke-width="{c["w"]}" stroke-linejoin="round" stroke-linecap="round"'
    out = ""
    for i, (x, y) in enumerate([(3.5, 3.5), (15, 3.5), (3.5, 15), (15, 15)]):
        f = c["f"] if i in (0, 3) else c["f2"]
        out += (f'<rect x="{x}" y="{y}" width="9.5" height="9.5" rx="2" fill="{f}" {k}/>'
                f'<line x1="{x+2.8}" y1="{y+2.4}" x2="{x+2.8}" y2="{y+7.1}" {k}/>'
                f'<line x1="{x+5.2}" y1="{y+3.4}" x2="{x+7.2}" y2="{y+3.4}" {k} stroke-width="1.2"/>')
    return out


def cards2(sel):
    c = st(sel)
    k = f'stroke="{c["s"]}" stroke-width="{c["w"]}" stroke-linejoin="round" stroke-linecap="round"'
    out = ""
    for i, a in enumerate([-24, -8, 8, 24]):
        f = [c["f"], c["f2"], c["f"], c["f2"]][i]
        out += (f'<g transform="rotate({a} 14 25)"><rect x="9" y="4" width="10" height="15" rx="1.8" fill="{f}" {k}/>'
                f'<line x1="11.6" y1="6.5" x2="11.6" y2="16.5" {k} stroke-width="1.1"/></g>')
    return out


def stats1(sel):
    c = st(sel)
    k = f'stroke="{c["s"]}" stroke-width="{c["w"]}" stroke-linejoin="round" stroke-linecap="round"'
    out = ""
    for i, (x, h) in enumerate([(4, 8), (9.5, 13), (15, 10), (20.5, 18)]):
        f = c["f"] if i % 2 else c["f2"]
        y = 23 - h
        out += (f'<rect x="{x}" y="{y}" width="4.2" height="{h}" rx=".9" fill="{f}" {k}/>'
                f'<line x1="{x}" y1="{y+2.4}" x2="{x+4.2}" y2="{y+2.4}" {k} stroke-width="1.1"/>')
    out += f'<line x1="2.5" y1="24.4" x2="25.5" y2="24.4" {k}/>'
    return out


def stats2(sel):
    c = st(sel)
    k = f'stroke="{c["s"]}" stroke-width="{c["w"]}" stroke-linejoin="round" stroke-linecap="round"'
    return (  # раскрытая книга, по страницам растёт линия графика
        f'<path d="M2.5 7 Q8 5 14 7.5 V24 Q8 21.5 2.5 23.5 Z" fill="{c["f"]}" {k}/>'
        f'<path d="M25.5 7 Q20 5 14 7.5 V24 Q20 21.5 25.5 23.5 Z" fill="{c["f2"]}" {k}/>'
        f'<polyline points="4.5,19 8,15.5 11,17 14,12.5 17.5,14 20.5,9.5 23.5,11" fill="none" {k}/>'
        f'<circle cx="20.5" cy="9.5" r="1.2" fill="{c["s"]}"/>'
    )


def gear_path(cx, cy, ro, ri, teeth=8):
    pts = []
    for i in range(teeth):
        a = 2 * math.pi * i / teeth
        for r, da in [(ri, -0.36), (ro, -0.2), (ro, 0.2), (ri, 0.36)]:
            pts.append((cx + r * math.cos(a + da), cy + r * math.sin(a + da)))
    return "M" + " L".join(f"{x:.2f} {y:.2f}" for x, y in pts) + " Z"


def gear1(sel):
    c = st(sel)
    k = f'stroke="{c["s"]}" stroke-width="{c["w"]}" stroke-linejoin="round" stroke-linecap="round"'
    return (  # шестерёнка, в центре раскрытая книжечка
        f'<path d="{gear_path(14, 14, 12, 9.2)}" fill="{c["f"]}" {k}/>'
        f'<path d="M8.6 11 Q11.3 10 14 11.4 V18 Q11.3 16.9 8.6 17.6 Z" fill="#fff" {k} stroke-width="1.3"/>'
        f'<path d="M19.4 11 Q16.7 10 14 11.4 V18 Q16.7 16.9 19.4 17.6 Z" fill="#fff" {k} stroke-width="1.3"/>'
    )


def gear2(sel):
    c = st(sel)
    k = f'stroke="{c["s"]}" stroke-width="{c["w"]}" stroke-linejoin="round" stroke-linecap="round"'
    return (  # шестерёнка, через неё свисает ляссе с ласточкиным хвостом
        f'<path d="{gear_path(14, 12.5, 10.5, 8)}" fill="{c["f"]}" {k}/>'
        f'<circle cx="14" cy="12.5" r="3.4" fill="#fff" {k}/>'
        f'<path d="M12.3 15.2 V26 L14 24.3 L15.7 26 V15.2" fill="{c["f2"]}" {k} stroke-width="1.4"/>'
    )


def icon(fn, sel, size=64):
    bg = 'style="background:rgba(255,255,255,.55);border-radius:16px"' if sel else ''
    return f'<svg width="{size}" height="{size}" viewBox="-2 -2 32 32" {bg}>{fn(sel)}</svg>'


sets = [
    ("главная", [(home1, "1 · домик из книг: крыша — две книги домиком, стены — корешки, фундамент — две лежащие книги"),
                 (home2, "2 · крыша — раскрытая книга страницами вниз, под ней три корешка, средний — «дверь»")]),
    ("книги", [(cards1, "1 · четыре карточки книг, у каждой корешок и строчка"),
               (cards2, "2 · четыре карточки веером, как карты в руке")]),
    ("статистика", [(stats1, "1 · столбики из корешков разной высоты на полке"),
                    (stats2, "2 · раскрытая книга, по страницам растёт график")]),
    ("настройки", [(gear1, "1 · шестерёнка с раскрытой книжечкой в центре"),
                   (gear2, "2 · шестерёнка, через которую продета лента-ляссе")]),
]

body = '<h1>значки меню: книжная тема</h1><p class="sub">Обычное состояние — контур цвета текста. Выбранный пункт — заливка в два цвета оформления (лиловый и розовый) под стеклянной линзой. Подписи под значками можно оставить мелко или убрать совсем.</p><div class="row" style="gap:20px">'
for name, variants in sets:
    body += f'<div class="card glass" style="padding:16px 18px; width:310px; box-sizing:border-box"><div class="cap">{name}</div>'
    for fn, note in variants:
        body += f'<div style="display:flex;gap:14px;align-items:center">{icon(fn, False)}{icon(fn, True)}<div class="note" style="max-width:150px">{note}</div></div>'
    body += '</div>'
body += '</div>'


def nav(fns, sel_i, labels):
    items = ""
    for i, fn in enumerate(fns):
        sel = i == sel_i
        lens = ('<div style="position:absolute;inset:4px 6px;border-radius:22px;background:linear-gradient(180deg,rgba(255,255,255,.75),rgba(123,92,255,.22));border:1px solid rgba(255,255,255,.95)"></div>' if sel else '')
        lab = f'<div style="font-size:11px;color:{VIO if sel else INK2};font-weight:{600 if sel else 400}">{labels[i]}</div>' if labels else ''
        items += f'<div style="position:relative;flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:6px 0">{lens}<div style="position:relative;display:flex;flex-direction:column;align-items:center">{icon(fn, sel, 34)}{lab}</div></div>'
    return f'<div class="glass" style="display:flex;width:390px;border-radius:32px;padding:4px 8px;height:{66 if labels else 54}px;align-items:center">{items}</div>'


body += '<h1 style="margin-top:30px;font-size:20px">так в меню (стекло)</h1><div class="row" style="gap:30px;margin-top:12px">'
body += '<div class="card"><div class="note">вариант 1, без подписей, выбрана «главная»</div>' + nav([home1, cards1, stats1, gear1], 0, None) + '</div>'
body += '<div class="card"><div class="note">вариант 1, с мелкими подписями, выбраны «книги»</div>' + nav([home1, cards1, stats1, gear1], 1, ["главная", "книги", "статистика", "настройки"]) + '</div>'
body += '<div class="card"><div class="note">вариант 2, выбрана «статистика»</div>' + nav([home2, cards2, stats2, gear2], 2, None) + '</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1400))
