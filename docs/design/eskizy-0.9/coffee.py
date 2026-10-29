import sys
sys.path.insert(0, sys.path[0])
from common import *
import icons2 as I
import stacks2 as S


def book_coffee(sel, cup_side="right"):
    c = I.st(sel); k = I.K(c, 1.3); kt = I.K(c, .9)
    cover = VIO if sel else c["s"]
    paper = "#fff" if sel else "#F4F0FC"
    out = ""
    cx = 11.2
    # обложка сзади: выступает по бокам и снизу
    out += f'<path d="M{cx-11.2} 8.4 H{cx+11.2} V22.4 Q{cx+5} 21.6 {cx} 23.6 Q{cx-5} 21.6 {cx-11.2} 22.4 Z" fill="{cover}"/>'
    # страницы с выгнутым верхом
    out += (f'<path d="M{cx} 9.4 Q{cx-5} 6.2 {cx-9.9} 7.4 V20.8 Q{cx-5} 19.6 {cx} 21.6 Z" fill="{paper}" {k}/>'
            f'<path d="M{cx} 9.4 Q{cx+5} 6.2 {cx+9.9} 7.4 V20.8 Q{cx+5} 19.6 {cx} 21.6 Z" fill="{paper}" {k}/>')
    for i in range(4):
        y = 10.6 + i * 2.6
        out += f'<path d="M{cx-8} {y} Q{cx-4.6} {y-1} {cx-1.6} {y+.6}" fill="none" {kt}/>'
        if i < 2:   # справа нижние строчки закрывает чашка
            out += f'<path d="M{cx+1.6} {y+.6} Q{cx+4.6} {y-1} {cx+8} {y}" fill="none" {kt}/>'
    # чашка кофе перед книгой справа
    cupf = c["f2"] if sel else "#F4F0FC"
    out += (f'<path d="M18.8 16.8 H26.4 V21 Q26.4 25.4 22.6 25.4 Q18.8 25.4 18.8 21 Z" fill="{cupf}" {I.K(c, 1.4)}/>'
            f'<path d="M26.4 17.8 Q29.2 17.8 29.2 20 Q29.2 22.2 26.4 22.2" fill="none" {I.K(c, 1.4)}/>'
            f'<path d="M17.6 26.6 H27.6" {I.K(c, 1.4)}/>')
    # пар
    out += (f'<path d="M23.8 15 Q23 13.6 23.8 12.4 Q24.6 11.2 23.8 9.8" fill="none" {I.K(c, 1.1)}/>'
            f'<path d="M26.4 15 Q25.6 13.6 26.4 12.4 Q27.2 11.2 26.4 9.8" fill="none" {I.K(c, 1.1)}/>')
    return out


def bc(sel): return book_coffee(sel)


body = '<h1>главная: раскрытая книга и чашка кофе</h1><p class="sub">Простая раскрытая книга, как на образце: обложка выступает по бокам и снизу, страницы с выгнутым верхом и строчками. Перед ней справа чашка с паром. Слева обычное состояние, справа выбранное.</p><div class="row" style="gap:20px">'
body += f'<div class="card glass" style="padding:14px 16px;align-items:center"><div class="cap">книга и кофе</div><div style="display:flex;gap:10px">{I.icon(bc, False, 150)}{I.icon(bc, True, 150)}</div></div>'
body += '</div><div class="row" style="gap:26px;margin-top:22px">'
for sel, cap in [(0, "выбрана главная"), (1, "выбраны книги")]:
    body += f'<div class="card"><div class="note">{cap}</div>{I.nav([bc, S.stacks, I.stats_fan, I.gear_ribbon], sel)}</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1000))
