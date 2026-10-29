import sys
sys.path.insert(0, sys.path[0])
from common import *
import icons2 as I
import stacks2 as S
import icons7 as F
from rainbow2 import r2

fns = [r2, S.stacks, F.fan2, I.gear_ribbon]
names = ["главная", "книги", "статистика", "настройки"]
body = '<h1>значки меню: утверждено</h1><p class="sub">Главная — радуга страниц (слева к розовому, справа к голубому), книги — две стопки, статистика — веер, настройки — шестерёнка с ляссе. В центре меню — песочные часы таймера, подсказка «Таймер: читаю в другом месте». Подписей нет.</p><div class="row" style="gap:20px">'
for fn, n in zip(fns, names):
    body += f'<div class="card glass" style="padding:14px 16px;align-items:center"><div class="cap">{n}</div><div style="display:flex;gap:10px">{I.icon(fn, False, 110)}{I.icon(fn, True, 110)}</div></div>'
body += '</div><div class="row" style="gap:26px;margin-top:22px">'
for sel in range(4):
    body += f'<div class="card"><div class="note">выбрана {names[sel]}</div>{I.nav(fns, sel)}</div>'
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1000))
