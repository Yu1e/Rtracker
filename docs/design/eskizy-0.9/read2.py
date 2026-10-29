import math, sys
sys.path.insert(0, sys.path[0])
from common import *

BW, BH, R = 170, 98, 26


def blob_path(cx, cy, w, h, wave_top, wave_bot, phase, n=60):
    """Капсула с волнистыми верхом и низом: волна по поверхности, как у водяного матраса."""
    pts = []
    x0, x1 = cx - w / 2 + R, cx + w / 2 - R
    for k in range(n + 1):           # верх слева направо
        t = k / n; x = x0 + (x1 - x0) * t
        pts.append((x, cy - h / 2 + wave_top * math.sin(math.pi * 2 * t + phase)))
    for k in range(1, 20):           # правый край
        a = -math.pi / 2 + math.pi * k / 20
        pts.append((x1 + R * math.cos(a) * 1.0, cy + (h / 2) * math.sin(a)))
    for k in range(n + 1):           # низ справа налево
        t = k / n; x = x1 - (x1 - x0) * t
        pts.append((x, cy + h / 2 + wave_bot * math.sin(math.pi * 2 * t + phase + 1.3)))
    for k in range(1, 20):
        a = math.pi / 2 + math.pi * k / 20
        pts.append((x0 + R * math.cos(a), cy + (h / 2) * math.sin(a)))
    return "M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in pts) + " Z"


def frame(i, content, label):
    return (f'<div class="card" style="gap:6px"><svg width="230" height="160" viewBox="0 0 230 160" class="glass">{content}</svg>'
            f'<div class="note" style="max-width:230px">{label}</div></div>')


def drop(phase, colors, sx, sy, dy=0):
    a, b, c = colors
    g = f'<defs><linearGradient id="d{phase}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{a}"/><stop offset=".55" stop-color="{b}"/><stop offset="1" stop-color="{c}"/></linearGradient></defs>'
    cx, cy = 115, 82 + dy
    w, h = BW * sx, BH * sy
    rx = min(R * (sx + sy) / 2 + (6 if sy < 1 else 0), h / 2)
    return (g + f'<rect x="{cx-w/2:.1f}" y="{cy-h/2:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{rx:.1f}" fill="url(#d{phase})"/>'
            f'<rect x="{cx-w/2+5:.1f}" y="{cy-h/2+3:.1f}" width="{w-10:.1f}" height="{h*0.45:.1f}" rx="{rx-3:.1f}" fill="#fff" opacity=".3"/>'
            f'<text x="{cx}" y="{cy+5}" text-anchor="middle" font-size="28" font-weight="600" fill="#fff">читать</text>'
            f'<text x="{cx}" y="{cy+25}" text-anchor="middle" font-size="13" fill="#fff" opacity=".9">ЛитРес</text>')


def mattress(phase, wt, wb, swirl):
    cx, cy = 115, 82
    d = blob_path(cx, cy, BW, BH, wt, wb, phase)
    blobs = ""
    for k, (col, ox, oy, r) in enumerate([(CYAN, -40, -10, 42), (PINK, 30, 14, 46), (AMBER, 50, -22, 30), (MINT, -55, 22, 32), (VIO2, 0, 0, 40)]):
        ang = swirl + k * 1.3
        blobs += f'<circle cx="{cx+ox+math.cos(ang)*14:.1f}" cy="{cy+oy+math.sin(ang)*9:.1f}" r="{r}" fill="{col}" opacity=".85" filter="url(#bl)"/>'
    return (f'<defs><clipPath id="m{phase}"><path d="{d}"/></clipPath><filter id="bl" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="12"/></filter></defs>'
            f'<g clip-path="url(#m{phase})"><rect x="0" y="0" width="230" height="160" fill="{VIO}"/>{blobs}'
            f'<path d="{d}" fill="none" stroke="#fff" stroke-width="10" opacity=".18"/></g>'
            f'<path d="{d}" fill="none" stroke="#fff" stroke-width="1.2" opacity=".75"/>'
            f'<text x="{cx}" y="{cy+5+wt*0.4:.1f}" text-anchor="middle" font-size="28" font-weight="600" fill="#fff">читать</text>'
            f'<text x="{cx}" y="{cy+25+wt*0.4:.1f}" text-anchor="middle" font-size="13" fill="#fff" opacity=".9">ЛитРес</text>')


p1 = ("#7B5CFF", "#C651E8", "#FF7A93"); p2 = ("#2EC4E6", "#6BE38E", "#FFC94A"); mid = ("#5B8CFF", "#9C84FF", "#FFB36B")
body = '<h1>«читать»: капля и водяной матрас</h1><p class="sub">Принятые эффекты: волна-перелив, веер страниц, ляссе-серпантин, капля (без брызг) и новый водяной матрас. Буквы и искры убраны. Ниже два эффекта по кадрам, каждый около 0,6 с.</p>'
body += '<div class="cap" style="margin-bottom:8px">капля</div><div class="row" style="gap:14px;margin-bottom:24px">'
body += frame(0, drop(0, p1, 1, 1), "покой")
body += frame(1, drop(1, p1, 1.12, .78, 6), "палец: капля сплющивается и растекается вширь")
body += frame(2, drop(2, mid, .92, 1.14, -4), "отпустили: подпрыгивает и вытягивается вверх, цвет перетекает")
body += frame(3, drop(3, p2, 1, 1), "успокоилась в новых цветах")
body += '</div><div class="cap" style="margin-bottom:8px">водяной матрас</div><div class="row" style="gap:14px">'
body += frame(4, mattress(0, 0, 0, 0), "покой: внутри медленно плавают цветные пятна")
body += frame(5, mattress(.4, 9, -6, 1.2), "палец: поверхность прогибается тяжёлой волной")
body += frame(6, mattress(2.6, -7, 7, 2.6), "волна перекатывается на другой край, пятна внутри размешиваются")
body += frame(7, mattress(4.4, 3, -2, 4.2), "колышется всё слабее и затихает, цвета уже другие")
body += '</div>'
open(sys.argv[1], "w").write(page(body, width=1060))
