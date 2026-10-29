"""Общие куски эскизов: стеклянный фон, плитки, шрифт."""
FONT = "/home/user/Rtracker/app/src/main/assets/fonts/Rubik.ttf"
INK = "#1E1838"; INK2 = "#4F4870"; INK3 = "#9A94B5"
VIO = "#6343F0"; VIO2 = "#7B5CFF"; PINK = "#FF5CA8"; CYAN = "#2EC4E6"; AMBER = "#FFC94A"; MINT = "#6BE38E"


def page(body, width=1200, dark=False):
    bg = ("radial-gradient(circle at 15% 12%, #B9A8FFcc, transparent 40%),"
          "radial-gradient(circle at 90% 30%, #FFB8D6cc, transparent 40%),"
          "radial-gradient(circle at 20% 70%, #9FE8FFcc, transparent 45%),"
          "radial-gradient(circle at 85% 88%, #FFD39Acc, transparent 40%),"
          "linear-gradient(135deg,#F3EEFF,#FFF1EA)")
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Rubik; src: url('file://{FONT}'); font-weight: 300 900; }}
html {{ background:#F3EEFF }}\nbody {{ margin:0; font-family: Rubik, sans-serif; color:{INK}; background:{bg}; width:{width}px; }}
.sheet {{ padding: 28px 32px 36px; }}
h1 {{ font-size: 26px; font-weight: 600; margin: 0 0 4px; }}
.sub {{ font-size: 15px; color:{INK2}; margin: 0 0 22px; max-width: 1050px; line-height:1.4 }}
.row {{ display:flex; gap: 26px; flex-wrap: wrap; align-items:flex-start }}
.card {{ display:flex; flex-direction:column; gap:10px; }}
.cap {{ font-size: 15px; font-weight:600 }}
.note {{ font-size: 13px; color:{INK2}; max-width: 260px; line-height:1.35 }}
.glass {{ background: linear-gradient(180deg, rgba(255,255,255,.66), rgba(255,255,255,.36));
          border: 1px solid rgba(255,255,255,.9); border-radius: 18px; box-shadow: 0 2px 10px rgba(58,42,128,.08) }}
.dark {{ background: radial-gradient(circle at 20% 20%, #5B3DE0aa, transparent 45%),
                     radial-gradient(circle at 85% 70%, #E0408A88, transparent 45%),
                     radial-gradient(circle at 40% 95%, #1FB6D988, transparent 40%),
                     linear-gradient(180deg,#0A0818,#120A26); color:#F3EEFF; border-radius: 22px; padding: 18px }}
.dark .glass {{ background: linear-gradient(180deg, rgba(255,255,255,.16), rgba(255,255,255,.06)); border-color: rgba(255,255,255,.3) }}
.dark .note {{ color:#BDB6E0 }}
</style></head><body><div class="sheet">{body}</div></body></html>"""
