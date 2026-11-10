"""Writes one artboard of the "category animations & icons" canvas: python3 gen.py <Board|canvas>."""
import json
import math
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out", "")
INK, MUTED, BG, LINE, BRAND = "#16211C", "#46524C", "#F4F6F3", "#E3E9E5", "#0F6B4F"
FONTS = '<link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&amp;family=Roboto+Mono:wght@500;700&amp;display=swap" rel="stylesheet">'

CATS = {  # id: (name, colour) — the app's own seed colours (Db.kt)
    "food": ("Food & Dining", "#D9622B"), "groceries": ("Groceries", "#3F8A3A"), "shopping": ("Shopping", "#7552C4"),
    "transport": ("Transport", "#2A6FB5"), "fuel": ("Fuel", "#9A6B2F"), "bills": ("Bills & Utilities", "#9E740C"),
    "recharge": ("Recharge & Subscriptions", "#C23F73"), "entertainment": ("Entertainment", "#B04FA5"),
    "sports": ("Sports & Fitness", "#C8423B"), "health": ("Health", "#1F8F86"), "travel": ("Travel", "#3A86C9"),
    "education": ("Education", "#5866B8"), "rent": ("Rent", "#6B7D3A"), "emi": ("EMI & Loans", "#8A5A44"),
    "investments": ("Investments", "#2E7D5B"), "cash": ("Cash", "#5E7480"), "transfers": ("Transfers", "#6F787D"),
    "others": ("Others", "#7F8682"), "income": ("Income", "#0F6B4F"),
}
DESC = {
    "food": "Steam rises from the bowl", "groceries": "The cart rolls in and fills up",
    "shopping": "The bag swings, with a sparkle", "transport": "A scooter rides in, wheels spinning",
    "fuel": "The pump lights up and drips", "bills": "The bulb flickers on",
    "recharge": "Signal bars fill up", "entertainment": "The clapperboard snaps shut",
    "sports": "A ball bounces and settles", "health": "A runner jogs over a heartbeat",
    "travel": "A plane takes off along a route", "education": "A book page turns",
    "rent": "The window lights up, smoke from the chimney", "emi": "The month's page tears off: paid",
    "investments": "A plant sprouts and grows", "cash": "Notes fan out",
    "transfers": "Two arrows swap sides", "others": "Sparkles twinkle", "income": "Coins drop into the wallet",
}


def mix(hex_color, other, t):
    a = [int(hex_color[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(other[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(a, b))


def star(r):
    k = r * 0.26
    return f"M0 {-r} L{k} {-k} L{r} 0 L{k} {k} L0 {r} L{-k} {k} L{-r} 0 L{-k} {-k} Z"


SCOOTER = """<path class="s" d="M22 64 H58" stroke="{c}"></path>
<path class="s" d="M18 64 Q16 50 28 48 H42 L48 64" stroke="{c}"></path>
<path class="s" d="M28 41 H42" stroke="{c}"></path>
<path class="s" d="M58 64 L68 32" stroke="{c}"></path>
<path class="s" d="M63 32 H75" stroke="{c}"></path>
<g class="WHEEL"><circle class="s" cx="26" cy="72" r="8" stroke="{c}"></circle><path class="t" d="M26 66 V78 M20 72 H32" stroke="{c}"></path></g>
<g class="WHEEL"><circle class="s" cx="70" cy="72" r="8" stroke="{c}"></circle><path class="t" d="M70 66 V78 M64 72 H76" stroke="{c}"></path></g>"""

RUNNER = """<circle class="s" cx="66" cy="18" r="6" stroke="{c}"></circle>
<path class="s" d="M63 27 L57 47" stroke="{c}"></path>
<path class="s a-armA" d="M62 31 L52 40 L46 36" stroke="{c}"></path>
<path class="s a-armB" d="M62 31 L71 40 L79 35" stroke="{c}"></path>
<path class="s a-legA" d="M57 47 L48 58 L40 68" stroke="{c}"></path>
<path class="s a-legB" d="M57 47 L65 59 L61 71" stroke="{c}"></path>"""

ANIM = {
    "transport": """<g class="a-speed"><path class="t" d="M4 50 H16 M8 58 H20 M4 66 H14" stroke="{c}"></path></g>
<g class="a-scoot">""" + SCOOTER.replace("WHEEL", "a-wheel") + """</g>
<path class="t" d="M4 83 H116" stroke="#C9D3CD"></path>""",
    "fuel": """<rect class="s" x="24" y="16" width="34" height="62" rx="5" stroke="{c}"></rect>
<rect class="a-display" x="30" y="23" width="22" height="13" rx="2" fill="{c}"></rect>
<path class="t" d="M31 46 H51 M31 54 H51" stroke="{c}"></path>
<path class="s" d="M18 78 H64" stroke="{c}"></path>
<path class="s" d="M58 30 H68 Q76 30 76 40 V58" stroke="{c}"></path>
<path class="s" d="M76 58 L86 64" stroke="{c}"></path>
<circle class="a-drop" cx="88" cy="70" r="2.8" fill="{c}"></circle>
<circle class="a-drop d2" cx="88" cy="70" r="2.8" fill="{c}"></circle>
<circle class="a-drop d3" cx="88" cy="70" r="2.8" fill="{c}"></circle>""",
    "health": """<g class="a-bob">""" + RUNNER + """</g>
<path class="t a-beat" d="M6 82 H38 L44 74 L50 88 L55 78 H114" stroke="{c}" pathLength="100"></path>""",
    "food": """<path class="t a-steam" d="M44 42 q-5 -6 0 -12 q5 -6 0 -12" stroke="{c}"></path>
<path class="t a-steam s2" d="M60 42 q-5 -6 0 -12 q5 -6 0 -12" stroke="{c}"></path>
<path class="t a-steam s3" d="M76 42 q-5 -6 0 -12 q5 -6 0 -12" stroke="{c}"></path>
<path class="s" d="M24 50 H96" stroke="{c}"></path>
<path class="s" d="M28 50 Q30 76 60 76 Q90 76 92 50" stroke="{c}"></path>
<path class="s" d="M50 82 H70" stroke="{c}"></path>""",
    "groceries": """<rect class="s a-item" x="40" y="22" width="11" height="16" rx="2" stroke="{c}"></rect>
<circle class="s a-item i2" cx="66" cy="30" r="7" stroke="{c}"></circle>
<g class="a-roll">
<path class="s" d="M12 24 H22 L30 56 H80 L88 32 H26" stroke="{c}"></path>
<path class="t" d="M30 56 L27 62 H82" stroke="{c}"></path>
<g class="a-wheel"><circle class="s" cx="38" cy="70" r="5" stroke="{c}"></circle><path class="t" d="M38 66 V74" stroke="{c}"></path></g>
<g class="a-wheel"><circle class="s" cx="74" cy="70" r="5" stroke="{c}"></circle><path class="t" d="M74 66 V74" stroke="{c}"></path></g>
</g>""",
    "shopping": """<g class="a-swing">
<path class="s" d="M48 38 Q48 22 60 22 Q72 22 72 38" stroke="{c}"></path>
<path class="s" d="M36 38 H84 L88 80 H32 Z" stroke="{c}"></path>
<path class="t" d="M50 50 Q60 58 70 50" stroke="{c}"></path>
</g>
<g transform="translate(97 26)"><path class="a-twinkle" d=\"""" + star(7) + """\" fill="{c}"></path></g>
<g transform="translate(21 52)"><path class="a-twinkle w2" d=\"""" + star(5) + """\" fill="{c}"></path></g>""",
    "bills": """<circle class="a-glow" cx="60" cy="36" r="17" fill="{c}"></circle>
<path class="s" d="M52 56 Q42 48 43 36 A17 17 0 1 1 77 36 Q78 48 68 56 Z" stroke="{c}"></path>
<path class="t" d="M54 40 L58 46 L62 40 L66 46" stroke="{c}"></path>
<path class="s" d="M53 62 H67 M54 68 H66 M57 74 H63" stroke="{c}"></path>
<g class="a-rays"><path class="t" d="M60 6 V12 M88 18 L83 23 M32 18 L37 23 M98 38 H92 M22 38 H28" stroke="{c}"></path></g>""",
    "recharge": """<rect class="s" x="38" y="10" width="44" height="72" rx="7" stroke="{c}"></rect>
<path class="t" d="M54 17 H66" stroke="{c}"></path>
<rect class="a-bar" x="46" y="58" width="6" height="10" rx="1.5" fill="{c}"></rect>
<rect class="a-bar b2" x="55" y="50" width="6" height="18" rx="1.5" fill="{c}"></rect>
<rect class="a-bar b3" x="64" y="42" width="6" height="26" rx="1.5" fill="{c}"></rect>
<rect class="a-bar b4" x="73" y="34" width="6" height="34" rx="1.5" fill="{c}"></rect>""",
    "entertainment": """<rect class="s" x="28" y="42" width="64" height="38" rx="4" stroke="{c}"></rect>
<path class="t" d="M36 54 H84 M36 64 H70" stroke="{c}"></path>
<g class="a-clap">
<rect class="s" x="28" y="28" width="64" height="11" rx="2" stroke="{c}"></rect>
<path class="t" d="M40 28 L46 39 M54 28 L60 39 M68 28 L74 39" stroke="{c}"></path>
</g>""",
    "sports": """<ellipse class="a-shadow" cx="60" cy="83" rx="14" ry="3" fill="{c}"></ellipse>
<g class="a-ball">
<circle class="s" cx="60" cy="28" r="12" stroke="{c}"></circle>
<path class="t" d="M48 28 H72 M60 16 Q52 28 60 40 M60 16 Q68 28 60 40" stroke="{c}"></path>
</g>""",
    "travel": """<path class="t dots" d="M10 76 Q60 4 112 30" stroke="#B7C3BC"></path>
<g class="a-fly"><path class="s" d="M-10 0 H10 M3 0 L-4 -9 M3 0 L-4 9 M-8 0 L-12 -5 M-8 0 L-12 5" stroke="{c}"></path></g>""",
    "education": """<path class="s" d="M60 26 Q44 20 24 24 V74 Q44 70 60 76 Z" stroke="{c}"></path>
<path class="s" d="M60 26 Q76 20 96 24 V74 Q76 70 60 76 Z" stroke="{c}"></path>
<path class="t" d="M32 36 H50 M32 44 H50 M32 52 H46 M70 36 H88 M70 44 H88 M70 52 H84" stroke="{c}"></path>
<path class="sf a-page" d="M60 26 Q76 20 96 24 V74 Q76 70 60 76 Z" stroke="{c}" fill="#FFFFFF"></path>""",
    "rent": """<path class="t a-steam" d="M84 16 q-4 -4 0 -8 q4 -4 0 -8" stroke="{c}"></path>
<path class="s" d="M80 31 V20 H88 V37" stroke="{c}"></path>
<path class="s" d="M22 46 L60 16 L98 46" stroke="{c}"></path>
<path class="s" d="M30 40 V80 H90 V40" stroke="{c}"></path>
<path class="s" d="M52 80 V60 H68 V80" stroke="{c}"></path>
<rect class="a-light" x="72" y="50" width="12" height="10" rx="1" fill="#F2B84B"></rect>
<rect class="t" x="72" y="50" width="12" height="10" rx="1" stroke="{c}"></rect>
<rect class="t" x="36" y="50" width="12" height="10" rx="1" stroke="{c}"></rect>""",
    "emi": """<rect class="s" x="26" y="20" width="68" height="60" rx="6" stroke="{c}"></rect>
<path class="s" d="M26 34 H94 M42 14 V26 M78 14 V26" stroke="{c}"></path>
<path class="s a-tick" d="M46 58 L56 66 L74 48" stroke="{c}" pathLength="100"></path>
<g class="a-tear">
<rect class="sf" x="32" y="38" width="56" height="36" rx="3" stroke="{c}" fill="#FFFFFF"></rect>
<path class="t" d="M40 48 H80 M40 56 H80 M40 64 H66" stroke="{c}"></path>
</g>""",
    "investments": """<path class="s a-stem" d="M60 64 V26" stroke="{c}" pathLength="100"></path>
<path class="sf lf a-leaf" d="M60 48 Q44 48 40 34 Q56 32 60 48 Z" stroke="{c}" fill="{c}"></path>
<path class="sf lf a-leaf l2" d="M60 38 Q76 38 80 24 Q64 22 60 38 Z" stroke="{c}" fill="{c}"></path>
<path class="sf" d="M42 64 H78 L74 82 H46 Z" stroke="{c}" fill="#FFFFFF"></path>""",
    "cash": """<g transform="translate(60 50)">
<rect class="sf a-n1" x="-26" y="-15" width="52" height="30" rx="4" stroke="{c}" fill="#FFFFFF"></rect>
<rect class="sf a-n3" x="-26" y="-15" width="52" height="30" rx="4" stroke="{c}" fill="#FFFFFF"></rect>
<g class="a-n2"><rect class="sf" x="-26" y="-15" width="52" height="30" rx="4" stroke="{c}" fill="#FFFFFF"></rect><circle class="t" cx="0" cy="0" r="7" stroke="{c}"></circle></g>
</g>""",
    "transfers": """<path class="s a-right" d="M28 36 H88 M78 26 L88 36 L78 46" stroke="{c}"></path>
<path class="s a-left" d="M92 58 H32 M42 48 L32 58 L42 68" stroke="{c}"></path>""",
    "others": """<g transform="translate(46 50)"><path class="a-twinkle" d=\"""" + star(14) + """\" fill="{c}"></path></g>
<g transform="translate(80 28)"><path class="a-twinkle w2" d=\"""" + star(9) + """\" fill="{c}"></path></g>
<g transform="translate(84 66)"><path class="a-twinkle w3" d=\"""" + star(6) + """\" fill="{c}"></path></g>""",
    "income": """<circle class="sf a-coin" cx="60" cy="14" r="7" stroke="{c}" fill="#FFFFFF"></circle>
<circle class="sf a-coin c2" cx="60" cy="14" r="7" stroke="{c}" fill="#FFFFFF"></circle>
<circle class="sf a-coin c3" cx="60" cy="14" r="7" stroke="{c}" fill="#FFFFFF"></circle>
<path class="sf" d="M28 42 H88 Q92 42 92 46 V78 Q92 82 88 82 H28 Q24 82 24 78 V46 Q24 42 28 42 Z" stroke="{c}" fill="#FFFFFF"></path>
<path class="s" d="M74 56 H92 V68 H74 Q69 68 69 62 Q69 56 74 56 Z" stroke="{c}"></path>""",
}


def plane_keyframes():
    p0, c, p2 = (10, 76), (60, 4), (112, 30)
    stops = []
    for i, t in enumerate([0, 0.2, 0.4, 0.6, 0.75, 0.88]):
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p2[1]
        dx = 2 * (1 - t) * (c[0] - p0[0]) + 2 * t * (p2[0] - c[0])
        dy = 2 * (1 - t) * (c[1] - p0[1]) + 2 * t * (p2[1] - c[1])
        a = math.degrees(math.atan2(dy, dx))
        pct = round(i * 9.6, 1)
        stops.append(f"{pct}%{{transform:translate({x:.1f}px,{y:.1f}px) rotate({a:.1f}deg)}}")
    last = stops[-1].split("{", 1)[1]
    return "@keyframes fly{" + "".join(stops) + "100%{" + last + "}"


ANIM_CSS = """
.s{fill:none;stroke-width:3;stroke-linecap:round;stroke-linejoin:round}
.t{fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.sf{stroke-width:3;stroke-linecap:round;stroke-linejoin:round}
.lf{fill-opacity:.22}
.dots{stroke-dasharray:2 6}
.a-scoot{animation:scoot 3s cubic-bezier(.2,.8,.2,1) infinite both}
@keyframes scoot{0%{transform:translateX(-90px)}38%{transform:translateX(17px)}46%{transform:translateX(11px)}54%,100%{transform:translateX(13px)}}
.a-wheel{transform-box:fill-box;transform-origin:center;animation:spin 3s ease-out infinite both}
@keyframes spin{0%{transform:rotate(0)}45%,100%{transform:rotate(900deg)}}
.a-speed{animation:speed 3s ease-out infinite both}
@keyframes speed{0%{opacity:0;transform:translateX(-20px)}12%{opacity:1}40%{opacity:0;transform:translateX(10px)}100%{opacity:0}}
.a-display{animation:blink 3s linear infinite both}
@keyframes blink{0%,6%{opacity:.25}8%,14%{opacity:1}16%,20%{opacity:.4}22%,100%{opacity:1}}
.a-drop{animation:drop 3s ease-in infinite both}
.d2{animation-delay:.3s}.d3{animation-delay:.6s}
@keyframes drop{0%{opacity:0;transform:translateY(-4px)}6%{opacity:1}26%{opacity:0;transform:translateY(14px)}100%{opacity:0;transform:translateY(14px)}}
.a-armA,.a-armB{transform-box:view-box;transform-origin:62px 31px}
.a-legA,.a-legB{transform-box:view-box;transform-origin:57px 47px}
.a-armA,.a-legB{animation:swing .6s ease-in-out infinite alternate both}
.a-armB,.a-legA{animation:swing .6s ease-in-out infinite alternate-reverse both}
@keyframes swing{from{transform:rotate(-22deg)}to{transform:rotate(22deg)}}
.a-bob{animation:bob .3s ease-in-out infinite alternate both}
@keyframes bob{from{transform:translateY(0)}to{transform:translateY(-3px)}}
.a-beat{stroke-dasharray:100;animation:draw 3s ease-out infinite both}
@keyframes draw{0%{stroke-dashoffset:100}45%,100%{stroke-dashoffset:0}}
.a-steam{animation:steam 3s ease-out infinite both}
.s2{animation-delay:.35s}.s3{animation-delay:.7s}
@keyframes steam{0%{opacity:0;transform:translateY(8px)}20%{opacity:1}55%{opacity:0;transform:translateY(-10px)}100%{opacity:0;transform:translateY(-10px)}}
.a-roll{animation:roll 3s cubic-bezier(.2,.8,.2,1) infinite both}
@keyframes roll{0%{transform:translateX(-80px)}32%{transform:translateX(5px)}40%,100%{transform:translateX(0)}}
.a-item{animation:dropin 3s ease-in infinite both}
.i2{animation-delay:.18s}
@keyframes dropin{0%,30%{opacity:0;transform:translateY(-24px)}36%{opacity:1}48%{transform:translateY(16px)}54%{transform:translateY(12px)}60%,100%{opacity:1;transform:translateY(14px)}}
.a-swing{transform-box:fill-box;transform-origin:50% 0%;animation:bag 3s ease-out infinite both}
@keyframes bag{0%{transform:rotate(-16deg)}14%{transform:rotate(11deg)}26%{transform:rotate(-7deg)}38%{transform:rotate(3deg)}48%,100%{transform:rotate(0)}}
.a-twinkle{transform-box:fill-box;transform-origin:center;animation:twinkle 3s ease-out infinite both}
.w2{animation-delay:.4s}.w3{animation-delay:.8s}
@keyframes twinkle{0%,20%{opacity:0;transform:scale(.2)}35%{opacity:1;transform:scale(1.15)}50%{transform:scale(1)}70%{opacity:1}85%,100%{opacity:0;transform:scale(.6)}}
.a-glow{animation:flick 3s linear infinite both}
@keyframes flick{0%,4%{opacity:0}8%{opacity:.35}11%{opacity:.05}16%{opacity:.4}20%{opacity:.12}26%,100%{opacity:.3}}
.a-rays{transform-box:fill-box;transform-origin:center;animation:rays 3s ease-out infinite both}
@keyframes rays{0%,22%{opacity:0;transform:scale(.8)}34%,100%{opacity:1;transform:scale(1)}}
.a-bar{transform-box:fill-box;transform-origin:50% 100%;animation:bar 3s ease-out infinite both}
.b2{animation-delay:.12s}.b3{animation-delay:.24s}.b4{animation-delay:.36s}
@keyframes bar{0%{transform:scaleY(0)}18%{transform:scaleY(1.12)}24%,100%{transform:scaleY(1)}}
.a-clap{transform-box:fill-box;transform-origin:0% 100%;animation:clap 3s ease-in infinite both}
@keyframes clap{0%,18%{transform:rotate(-30deg)}30%{transform:rotate(0)}34%{transform:rotate(-5deg)}38%,100%{transform:rotate(0)}}
.a-ball{transform-box:fill-box;transform-origin:50% 100%;animation:ball 3s ease-in infinite both}
@keyframes ball{0%{transform:translateY(-6px)}14%{transform:translateY(40px) scale(1.12,.88);animation-timing-function:ease-out}24%{transform:translateY(8px)}34%{transform:translateY(40px) scale(1.06,.94);animation-timing-function:ease-out}42%{transform:translateY(26px)}50%,100%{transform:translateY(40px)}}
.a-shadow{transform-box:fill-box;transform-origin:center;animation:shadow 3s ease-in infinite both}
@keyframes shadow{0%{opacity:.08;transform:scale(.5)}14%{opacity:.3;transform:scale(1)}24%{opacity:.12;transform:scale(.6)}34%{opacity:.3;transform:scale(1)}42%{opacity:.18;transform:scale(.8)}50%,100%{opacity:.3;transform:scale(1)}}
.a-fly{transform-box:view-box;transform-origin:0 0;animation:fly 3s ease-in-out infinite both}
""" + plane_keyframes() + """
.a-page{transform-box:view-box;transform-origin:60px 50px;animation:flip 3s ease-in-out infinite both}
@keyframes flip{0%,10%{transform:scaleX(1);opacity:1}42%{transform:scaleX(-1);opacity:1}52%,100%{transform:scaleX(-1);opacity:0}}
.a-light{animation:light 3s ease-out infinite both}
@keyframes light{0%,12%{opacity:0}16%{opacity:.8}19%{opacity:.2}24%,100%{opacity:1}}
.a-tear{transform-box:fill-box;transform-origin:50% 0%;animation:tear 3s ease-in infinite both}
@keyframes tear{0%,14%{transform:none;opacity:1}38%,100%{transform:translateY(26px) rotate(12deg);opacity:0}}
.a-tick{stroke-dasharray:100;animation:tick 3s ease-out infinite both}
@keyframes tick{0%,34%{stroke-dashoffset:100}52%,100%{stroke-dashoffset:0}}
.a-stem{stroke-dasharray:100;animation:grow 3s ease-out infinite both}
@keyframes grow{0%{stroke-dashoffset:100}30%,100%{stroke-dashoffset:0}}
.a-leaf{transform-box:fill-box;transform-origin:100% 100%;animation:leaf 3s ease-out infinite both}
.l2{transform-origin:0% 100%;animation-delay:.15s}
@keyframes leaf{0%,22%{transform:scale(0)}38%{transform:scale(1.1)}46%,100%{transform:scale(1)}}
.a-n1,.a-n3{transform-box:view-box;transform-origin:0 18px}
.a-n1{animation:fanL 3s ease-out infinite both}.a-n3{animation:fanR 3s ease-out infinite both}
.a-n2{animation:lift 3s ease-out infinite both}
@keyframes fanL{0%,10%{transform:rotate(0)}36%,100%{transform:rotate(-18deg)}}
@keyframes fanR{0%,10%{transform:rotate(0)}36%,100%{transform:rotate(18deg)}}
@keyframes lift{0%,10%{transform:translateY(0)}36%,100%{transform:translateY(-4px)}}
.a-right{animation:goR 3s ease-in-out infinite both}
.a-left{animation:goL 3s ease-in-out infinite both;animation-delay:.15s}
@keyframes goR{0%{transform:translateX(-14px);opacity:0}18%{opacity:1}42%,100%{transform:translateX(0);opacity:1}}
@keyframes goL{0%{transform:translateX(14px);opacity:0}18%{opacity:1}42%,100%{transform:translateX(0);opacity:1}}
.a-coin{animation:coin 3s ease-in infinite both}
.c2{animation-delay:.25s}.c3{animation-delay:.5s}
@keyframes coin{0%{transform:translateY(-14px);opacity:0}8%{opacity:1}30%{transform:translateY(34px);opacity:1}34%,100%{transform:translateY(34px);opacity:0}}
"""


def anim_svg(cat, w=120, h=90):
    color = CATS[cat][1]
    return (f'<svg viewBox="0 0 120 90" width="{w}" height="{h}" aria-hidden="true" style="overflow: visible">'
            + ANIM[cat].replace("{c}", color) + "</svg>")


def page(title, body, script, preview, extra_css=""):
    css = "body{margin:0}\na{color:#0F6B4F}a:hover{color:#0B4D38}\n" + ANIM_CSS + extra_css
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
{FONTS}
<style>
{css}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{preview[0]},"height":{preview[1]}}}}}'>
{script}
</script>
</body>
</html>
"""


STATIC_SCRIPT = """class Component extends DCLogic {
  renderVals() {
    return {};
  }
}"""

TAB_ICONS = {
    "Home": "M4 11 L12 4 L20 11 V20 H14 V14 H10 V20 H4 Z",
    "Activity": "M9 6 H20 M9 12 H20 M9 18 H20 M4.5 6 H5 M4.5 12 H5 M4.5 18 H5",
    "Budgets": "M4 8 H18 Q20 8 20 10 V18 Q20 20 18 20 H6 Q4 20 4 18 V8 Q4 5 7 5 H16 M15 14 H17",
    "More": "M4 4 H10 V10 H4 Z M14 4 H20 V10 H14 Z M4 14 H10 V20 H4 Z M14 14 H20 V20 H14 Z",
}


def tab_bar(active):
    items = []
    for name, d in TAB_ICONS.items():
        on = name == active
        bg = "#D3EBDF" if on else "transparent"
        col = "#0B4D38" if on else INK
        items.append(
            f'<a href="#" style="flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; min-height: 52px; border-radius: 26px; background: {bg}; color: {col}; text-decoration: none; font-size: 12px; font-weight: 500">'
            f'<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path class="t" d="{d}" stroke="{col}"></path></svg>{name}</a>')
    return ('<nav aria-label="Tabs" style="position: absolute; left: 16px; right: 16px; bottom: 18px; display: flex; gap: 4px; padding: 6px; border-radius: 32px; background: rgba(255,255,255,0.94); border: 1px solid #E3E9E5; box-shadow: 0 8px 24px rgba(22,33,28,0.10)">'
            + "".join(items) + "</nav>")


# ---------------------------------------------------------------- Main: Option A prototype
MAIN_CATS = ["food", "groceries", "transport", "shopping", "bills", "travel", "fuel", "health"]


def main_board():
    centre = "".join(
        f'<sc-if value="{{{{anim.{c}}}}}" hint-placeholder-val="{{{{false}}}}">{anim_svg(c, 104, 78)}</sc-if>' for c in MAIN_CATS)
    body = f"""<div style="width: 390px; height: 844px; position: relative; overflow: hidden; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif">
<div style="height: 844px; overflow-y: auto; box-sizing: border-box; padding: 20px 16px 120px; display: flex; flex-direction: column; gap: 14px">
<div style="display: flex; flex-direction: column; gap: 2px; padding: 4px 4px 0">
<span style="font-size: 13px; color: {MUTED}">Budget month · day 10 of 30</span>
<h1 style="margin: 0; font-size: 26px; font-weight: 700">November 2026</h1>
</div>
<section style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 26px; padding: 16px; display: flex; flex-direction: column; gap: 10px">
<sc-if value="{{{{isRoot}}}}" hint-placeholder-val="{{{{true}}}}">
<div style="display: flex; align-items: flex-start; gap: 12px">
<div style="flex: 1; display: flex; flex-direction: column; gap: 2px">
<h2 style="margin: 0; font-size: 20px; font-weight: 700">Where it went</h2>
<span style="font-size: 14px; color: {MUTED}">Tap a category to open it</span>
</div>
<a href="#" style="font-size: 15px; font-weight: 500; padding: 4px 0; text-decoration: none">See all</a>
</div>
<div style="display: flex; justify-content: center; padding: 6px 0">
<div class="ring-in" style="width: 200px; height: 200px; border-radius: 100px; display: flex; align-items: center; justify-content: center; background: {{{{ringAll}}}}">
<div style="width: 152px; height: 152px; border-radius: 76px; background: #FFFFFF; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px">
<span style="font-size: 13px; color: {MUTED}">Total</span>
<span style="font-size: 26px; font-weight: 700">{{{{total}}}}</span>
<span style="font-size: 13px; color: {MUTED}">8 categories</span>
</div>
</div>
</div>
<div style="display: flex; flex-direction: column">
<sc-for list="{{{{cats}}}}" as="row" hint-placeholder-count="8">
<button type="button" onClick="{{{{row.open}}}}" style="display: flex; align-items: center; gap: 12px; width: 100%; min-height: 50px; padding: 4px; border: 0; border-top: 1px solid {LINE}; background: transparent; font: inherit; color: inherit; text-align: left; cursor: pointer">
<span style="width: 12px; height: 12px; border-radius: 6px; flex: none; background: {{{{row.color}}}}"></span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column"><span style="font-size: 16px; font-weight: 500">{{{{row.name}}}}</span><span style="font-size: 13px; color: {MUTED}">{{{{row.pct}}}}</span></span>
<span style="font-size: 16px; font-weight: 700">{{{{row.amount}}}}</span>
<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path class="t" d="M9 6 L15 12 L9 18" stroke="{MUTED}"></path></svg>
</button>
</sc-for>
</div>
</sc-if>
<sc-if value="{{{{isOpen}}}}" hint-placeholder-val="{{{{false}}}}">
<button type="button" onClick="{{{{back}}}}" style="align-self: flex-start; display: flex; align-items: center; gap: 4px; min-height: 44px; padding: 0 12px 0 4px; border: 0; border-radius: 22px; background: #E7F2EC; color: #0B4D38; font: inherit; font-size: 15px; font-weight: 500; cursor: pointer">
<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path class="t" d="M15 6 L9 12 L15 18" stroke="#0B4D38"></path></svg>All categories</button>
<div style="display: flex; align-items: baseline; gap: 8px; padding: 0 4px">
<h2 style="margin: 0; flex: 1; font-size: 20px; font-weight: 700">{{{{cur.name}}}}</h2>
<span style="font-size: 14px; color: {MUTED}">{{{{cur.share}}}}</span>
</div>
<div style="display: flex; justify-content: center; padding: 4px 0">
<div class="ring-in" style="width: 200px; height: 200px; border-radius: 100px; display: flex; align-items: center; justify-content: center; background: {{{{cur.ring}}}}">
<div style="width: 152px; height: 152px; border-radius: 76px; background: #FFFFFF; display: flex; flex-direction: column; align-items: center; justify-content: center">
{centre}
<span style="font-size: 22px; font-weight: 700; margin-top: 2px">{{{{cur.total}}}}</span>
</div>
</div>
</div>
<div style="display: flex; flex-direction: column">
<sc-for list="{{{{cur.merchants}}}}" as="m" hint-placeholder-count="4">
<div style="display: flex; align-items: center; gap: 12px; min-height: 52px; padding: 4px; border-top: 1px solid {LINE}">
<span style="width: 36px; height: 36px; border-radius: 18px; flex: none; display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 700; background: {{{{m.tint}}}}; color: {{{{m.ink}}}}">{{{{m.initial}}}}</span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column"><span style="font-size: 16px; font-weight: 500">{{{{m.name}}}}</span><span style="font-size: 13px; color: {MUTED}">{{{{m.pct}}}}</span></span>
<span style="font-size: 16px; font-weight: 700">{{{{m.amount}}}}</span>
</div>
</sc-for>
</div>
</sc-if>
</section>
</div>
{tab_bar("Home")}
</div>"""
    data = {c: {"name": CATS[c][0], "color": CATS[c][1]} for c in MAIN_CATS}
    merchants = {
        "food": [["Swiggy", 4820], ["Zomato", 3560], ["Domino's", 1640], ["Café Coffee Day", 1220]],
        "groceries": [["Blinkit", 3210], ["BigBasket", 2980], ["Zepto", 1470], ["More Supermarket", 800]],
        "transport": [["Uber", 2140], ["Rapido", 1860], ["Namma Yatri", 1420], ["Ola", 1060]],
        "shopping": [["Amazon", 2890], ["Myntra", 1740], ["Flipkart", 990], ["Nykaa", 500]],
        "bills": [["BESCOM", 2450], ["Airtel Broadband", 1180], ["BWSSB", 920], ["Indane Gas", 750]],
        "travel": [["IRCTC", 2310], ["MakeMyTrip", 1620], ["redBus", 600]],
        "fuel": [["Shree Ganesh HPCL", 2100], ["IndianOil Koramangala", 1450], ["Shell HSR Layout", 800]],
        "health": [["Apollo Pharmacy", 1260], ["Practo", 520], ["MedPlus", 360]],
    }
    for c in MAIN_CATS:
        col = CATS[c][1]
        data[c]["merchants"] = [{"name": n, "paise": a, "tint": mix(col, "#FFFFFF", t), "ink": col if t < 0.4 else mix(col, "#000000", 0.25)}
                                for (n, a), t in zip(merchants[c], [0.0, 0.3, 0.5, 0.66])]
        for m in data[c]["merchants"]:
            m["ink"] = "#FFFFFF" if m["tint"] == col else mix(col, "#000000", 0.3)
    script = """class Component extends DCLogic {
  constructor(props) {
    super(props);
    this.state = { open: null };
  }
  renderVals() {
    const DATA = """ + json.dumps(data, ensure_ascii=False) + """;
    const ORDER = """ + json.dumps(MAIN_CATS) + """;
    const fmt = (n) => '\\u20b9' + n.toLocaleString('en-IN');
    const sum = (xs) => xs.reduce((a, m) => a + m.paise, 0);
    const ring = (parts) => {
      const total = parts.reduce((a, p) => a + p.v, 0);
      let at = 0;
      const stops = [];
      parts.forEach((p) => {
        const end = at + (p.v / total) * 360;
        stops.push(p.c + ' ' + at.toFixed(2) + 'deg ' + Math.max(at, end - 1.6).toFixed(2) + 'deg');
        stops.push('#FFFFFF ' + Math.max(at, end - 1.6).toFixed(2) + 'deg ' + end.toFixed(2) + 'deg');
        at = end;
      });
      return 'conic-gradient(' + stops.join(', ') + ')';
    };
    const totals = ORDER.map((id) => ({ id, v: sum(DATA[id].merchants) })).sort((a, b) => b.v - a.v);
    const grand = totals.reduce((a, t) => a + t.v, 0);
    const open = this.state.open;
    const cats = totals.map((t) => ({
      name: DATA[t.id].name,
      color: DATA[t.id].color,
      amount: fmt(t.v),
      pct: Math.round((t.v / grand) * 100) + '% of spending',
      open: () => this.setState({ open: t.id }),
    }));
    const anim = {};
    ORDER.forEach((id) => { anim[id] = open === id; });
    let cur = { name: '', share: '', total: '', ring: 'none', merchants: [] };
    if (open) {
      const d = DATA[open];
      const v = sum(d.merchants);
      cur = {
        name: d.name,
        share: Math.round((v / grand) * 100) + '% of spending',
        total: fmt(v),
        ring: ring(d.merchants.map((m) => ({ c: m.tint, v: m.paise }))),
        merchants: d.merchants.map((m) => ({
          name: m.name, amount: fmt(m.paise), pct: Math.round((m.paise / v) * 100) + '%',
          initial: m.name.charAt(0), tint: m.tint, ink: m.ink,
        })),
      };
    }
    return {
      isRoot: !open,
      isOpen: !!open,
      total: fmt(grand),
      ringAll: ring(totals.map((t) => ({ c: DATA[t.id].color, v: t.v }))),
      cats, cur, anim,
      back: () => this.setState({ open: null }),
    };
  }
}"""
    extra = """
.ring-in{animation:ringin .6s cubic-bezier(.2,.8,.2,1) both}
@keyframes ringin{from{transform:rotate(-40deg) scale(.9);opacity:0}to{transform:none;opacity:1}}
"""
    return page("Option A prototype", body, script, (390, 844), extra)


# ---------------------------------------------------------------- Gallery: all 19
def gallery_board():
    tiles = []
    for c, (name, col) in CATS.items():
        tiles.append(f"""<div style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 20px; padding: 12px; display: flex; flex-direction: column; gap: 6px">
<div style="height: 104px; border-radius: 14px; background: {mix(col, '#FFFFFF', 0.9)}; display: flex; align-items: center; justify-content: center">{anim_svg(c)}</div>
<span style="font-size: 15px; font-weight: 700">{name}</span>
<span style="font-size: 13px; color: {MUTED}; line-height: 1.35">{DESC[c]}</span>
</div>""")
    body = f"""<div style="width: 1280px; height: 960px; box-sizing: border-box; padding: 44px 48px; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif; display: flex; flex-direction: column; gap: 24px">
<div style="display: flex; flex-direction: column; gap: 6px">
<h1 style="margin: 0; font-size: 30px; font-weight: 700">One small animation per category</h1>
<p style="margin: 0; font-size: 16px; color: {MUTED}">In the app each plays once, in about a second, when you open that category. They loop here so you can watch them.</p>
</div>
<div style="display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 18px">
{"".join(tiles)}
</div>
</div>"""
    return page("All 19 category animations", body, STATIC_SCRIPT, (1280, 960))


# ---------------------------------------------------------------- Scenes: Option B
SCENE_CSS = """
.road{fill:none;stroke-width:6;stroke-linecap:round}
.gauge{fill:none;stroke-width:8;stroke-linecap:round}
.lbl{font:500 11px Roboto, system-ui, sans-serif;fill:#46524C}
.lbl-end{text-anchor:end}.lbl-mid{text-anchor:middle}
.lbl-red{font:700 11px Roboto, system-ui, sans-serif;fill:#B3261E;text-anchor:end}
.tape{stroke-dasharray:4 3}
.b-wheel{transform-box:fill-box;transform-origin:center;animation:spin4 4s ease-out infinite both}
@keyframes spin4{0%{transform:rotate(0)}42%,100%{transform:rotate(1080deg)}}
.b-prog{stroke-dasharray:100;animation:bprog 4s ease-out infinite both}
@keyframes bprog{0%{stroke-dashoffset:100}40%,100%{stroke-dashoffset:0}}
.b-ride{animation:ride 4s cubic-bezier(.25,.8,.25,1) infinite both}
@keyframes ride{0%{transform:translateX(0)}40%{transform:translateX(126px)}46%,100%{transform:translateX(122px)}}
.b-ride2{animation:ride2 4s cubic-bezier(.25,.8,.25,1) infinite both}
@keyframes ride2{0%{transform:translateX(0)}42%{transform:translateX(182px)}48%,100%{transform:translateX(178px)}}
.b-prog2{stroke-dasharray:100;animation:bprog2 4s ease-out infinite both}
@keyframes bprog2{0%{stroke-dashoffset:100}36%,100%{stroke-dashoffset:0}}
.b-over{stroke-dasharray:100;animation:bover 4s ease-out infinite both}
@keyframes bover{0%,34%{stroke-dashoffset:100}44%,100%{stroke-dashoffset:0}}
.b-overlbl{animation:overlbl 4s ease-out infinite both}
@keyframes overlbl{0%,38%{opacity:0}48%,100%{opacity:1}}
.b-gauge{stroke-dasharray:100;animation:bgauge 4s ease-out infinite both}
@keyframes bgauge{0%{stroke-dashoffset:100}40%,100%{stroke-dashoffset:13}}
.b-needle{transform-box:view-box;transform-origin:186px 96px;animation:needle 4s ease-out infinite both}
@keyframes needle{0%{transform:rotate(-90deg)}40%{transform:rotate(71deg)}46%,100%{transform:rotate(66.6deg)}}
.b-run{animation:runx 4s cubic-bezier(.3,.7,.3,1) infinite both}
@keyframes runx{0%{transform:translateX(0)}40%,100%{transform:translateX(115px)}}
.pump{position:absolute;left:24px;top:30px;width:44px;height:20px;border-radius:3px;background:#16211C;color:#7FD3AE;display:flex;align-items:center;justify-content:center;font:700 12px 'Roboto Mono', ui-monospace, monospace;overflow:hidden}
.dg{display:inline-block;width:7.4px;height:14px;overflow:hidden;line-height:14px}
.col{display:flex;flex-direction:column;animation-duration:4s;animation-timing-function:cubic-bezier(.2,.7,.2,1);animation-iteration-count:infinite;animation-fill-mode:both}
""" + "".join(
    f".r{d}{{animation-name:r{d}}}@keyframes r{d}{{0%{{transform:translateY(0)}}40%,100%{{transform:translateY(-{(10 + d) * 14}px)}}}}\n"
    for d in range(10))


def digit(d):
    return f'<span class="dg"><span class="col r{d}">' + "".join(f"<span>{i % 10}</span>" for i in range(20)) + "</span></span>"


def scene_card(color, name, amount, sub, sub_red, svg, caption, overlay=""):
    sub_col = "#B3261E" if sub_red else MUTED
    return f"""<div style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 24px; padding: 16px 15px; display: flex; flex-direction: column; gap: 10px">
<div style="display: flex; align-items: center; gap: 10px">
<span style="width: 12px; height: 12px; border-radius: 6px; flex: none; background: {color}"></span>
<span style="flex: 1; font-size: 16px; font-weight: 500">{name}</span>
<span style="font-size: 18px; font-weight: 700">{amount}</span>
</div>
<span style="font-size: 13px; color: {sub_col}; margin-top: -6px; padding-left: 22px">{sub}</span>
<div style="position: relative; width: 248px; height: 120px; border-radius: 14px; background: {mix(color, '#FFFFFF', 0.92)}">
<svg viewBox="0 0 248 120" width="248" height="120" aria-hidden="true">{svg}</svg>{overlay}
</div>
<p style="margin: 0; font-size: 14px; color: {MUTED}; line-height: 1.4">{caption}</p>
</div>"""


def scenes_board():
    tc, fc, hc = CATS["transport"][1], CATS["fuel"][1], CATS["health"][1]
    scooter = SCOOTER.replace("WHEEL", "b-wheel")
    t1 = f"""<path class="road" d="M12 98 H236" stroke="#DCE3DE"></path>
<path class="road b-prog" d="M12 98 H180" stroke="{tc}" pathLength="100"></path>
<path class="t" d="M220 98 V56" stroke="#46524C"></path><path d="M220 56 L240 62 L220 68 Z" fill="{tc}"></path>
<text x="214" y="50" class="lbl lbl-end">Budget ₹8,000</text>
<g class="b-ride"><g transform="translate(2 39) scale(0.72)">{scooter.replace("{c}", tc)}</g></g>"""
    t2 = f"""<path class="road" d="M12 98 H236" stroke="#DCE3DE"></path>
<path class="road b-prog2" d="M12 98 H220" stroke="{tc}" pathLength="100"></path>
<path class="road b-over" d="M220 98 H236" stroke="#C8423B" pathLength="100"></path>
<path class="t" d="M220 98 V56" stroke="#46524C"></path><path d="M220 56 L240 62 L220 68 Z" fill="{tc}"></path>
<text x="214" y="50" class="lbl lbl-end">Budget ₹8,000</text>
<text x="238" y="22" class="lbl-red b-overlbl">₹1,200 over</text>
<g class="b-ride2"><g transform="translate(2 39) scale(0.72)">{scooter.replace("{c}", tc)}</g></g>"""
    f1 = f"""<rect class="s" x="18" y="22" width="56" height="76" rx="6" stroke="{fc}"></rect>
<rect x="24" y="30" width="44" height="20" rx="3" fill="#16211C"></rect>
<path class="t" d="M28 62 H64 M28 72 H64" stroke="{fc}"></path>
<path class="s" d="M12 98 H80" stroke="{fc}"></path>
<path class="s" d="M74 44 H86 Q94 44 94 54 V74" stroke="{fc}"></path>
<path class="s" d="M94 74 L102 80" stroke="{fc}"></path>
<circle class="a-drop" cx="104" cy="86" r="2.8" fill="{fc}"></circle>
<circle class="a-drop d2" cx="104" cy="86" r="2.8" fill="{fc}"></circle>
<path class="gauge" d="M136 96 A50 50 0 0 1 236 96" stroke="#DCE3DE"></path>
<path class="gauge b-gauge" d="M136 96 A50 50 0 0 1 236 96" stroke="{fc}" pathLength="100"></path>
<g class="b-needle"><path class="s" d="M186 96 V58" stroke="#16211C"></path></g>
<circle cx="186" cy="96" r="5" fill="#16211C"></circle>
<text x="136" y="114" class="lbl lbl-mid">E</text><text x="236" y="114" class="lbl lbl-mid">F</text>"""
    pump = '<div class="pump">₹' + digit(4) + "," + digit(3) + digit(5) + digit(0) + "</div>"
    h1 = f"""<path class="road" d="M12 98 H236" stroke="#DCE3DE"></path>
<path class="road b-prog" d="M12 98 H160" stroke="{hc}" pathLength="100"></path>
<path class="t" d="M214 98 V60 M234 98 V60" stroke="#46524C"></path>
<path class="t tape" d="M214 66 H234" stroke="{hc}"></path>
<text x="224" y="52" class="lbl lbl-mid">Budget ₹3,000</text>
<g class="b-run"><g transform="translate(-4 54) scale(0.62)">{RUNNER.replace("{c}", hc)}</g></g>"""
    cards = [
        scene_card(tc, "Transport", "₹6,480", "of ₹8,000 budget · 81% used", False, t1,
                   "The scooter rides 81% of the way to the budget flag, then stops."),
        scene_card(tc, "Transport", "₹9,200", "of ₹8,000 budget · ₹1,200 over", True, t2,
                   "Over budget: it rides past the flag and the road beyond turns red."),
        scene_card(fc, "Fuel", "₹4,350", "of ₹5,000 budget · 87% used", False, f1,
                   "The pump meter counts up to the amount; the gauge needle stops at 87%.", pump),
        scene_card(hc, "Health", "₹2,140", "of ₹3,000 budget · 71% used", False, h1,
                   "The runner jogs 71% of the way to the finish tape."),
    ]
    body = f"""<div style="width: 1280px; height: 520px; box-sizing: border-box; padding: 40px 48px; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif; display: flex; flex-direction: column; gap: 22px">
<div style="display: flex; flex-direction: column; gap: 6px">
<h1 style="margin: 0; font-size: 30px; font-weight: 700">A scene that shows the number</h1>
<p style="margin: 0; font-size: 16px; color: {MUTED}">Plays when a category opens. How far it goes is how much of that category's budget you've used.</p>
</div>
<div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 24px">
{"".join(cards)}
</div>
</div>"""
    return page("Option B scenes", body, STATIC_SCRIPT, (1280, 520), SCENE_CSS)


# ---------------------------------------------------------------- Icons
GLYPHS = {
    "jewellery": "M12 9 m-6 6 a6 6 0 1 0 12 0 a6 6 0 1 0 -12 0 M9 4.5 L10.5 2.5 H13.5 L15 4.5 L12 8 Z",
    "clothing": "M8 3 L3 6.5 L5.5 10.5 L7.5 9.5 V21 H16.5 V9.5 L18.5 10.5 L21 6.5 L16 3 Q12 6.5 8 3 Z",
    "books": "M3 5.5 Q7.5 4 12 6.5 Q16.5 4 21 5.5 V19 Q16.5 17.5 12 20 Q7.5 17.5 3 19 Z M12 6.5 V20",
    "pharmacy": "M10.6 4.4 a4 4 0 0 1 5.66 5.66 l-6.86 6.86 a4 4 0 0 1 -5.66 -5.66 Z M7.2 7.8 l5.66 5.66",
    "opticals": "M3 14 a3.5 3.5 0 1 0 7 0 a3.5 3.5 0 1 0 -7 0 M14 14 a3.5 3.5 0 1 0 7 0 a3.5 3.5 0 1 0 -7 0 M10 13.5 Q12 12 14 13.5 M3.2 13 L5 7 M20.8 13 L19 7",
    "bakery": "M4 20 H20 V12.5 H4 Z M4 16 Q8 18 12 16 Q16 14 20 16 M12 12.5 V8.5 M12 6.5 V5.5",
    "salon": "M6 17 a2.5 2.5 0 1 0 0.01 0 M18 17 a2.5 2.5 0 1 0 0.01 0 M7.8 15.3 L18 4 M16.2 15.3 L6 4",
    "mobiles": "M7.5 3 H16.5 Q18 3 18 4.5 V19.5 Q18 21 16.5 21 H7.5 Q6 21 6 19.5 V4.5 Q6 3 7.5 3 Z M10.5 18 H13.5",
    "hardware": "M14.5 3.5 a4.5 4.5 0 0 0 5.6 5.6 L11 18.2 a2.1 2.1 0 0 1 -3 -3 L17.1 6.1",
    "clinic": "M4 4 H20 V20 H4 Z M12 8 V16 M8 12 H16",
    "app": "M5 5 H10 V10 H5 Z M14 5 H19 V10 H14 Z M5 14 H10 V19 H5 Z M14 14 H19 V19 H14 Z",
}


def glyph_tile(kind, color, size=44, r=12):
    return (f'<span style="width: {size}px; height: {size}px; border-radius: {r}px; flex: none; display: flex; align-items: center; justify-content: center; background: {mix(color, "#FFFFFF", 0.86)}">'
            f'<svg viewBox="0 0 24 24" width="{round(size * 0.55)}" height="{round(size * 0.55)}" aria-hidden="true"><path class="t" d="{GLYPHS[kind]}" stroke="{color}"></path></svg></span>')


def app_tile(size=44):
    return (f'<span style="width: {size}px; height: {size}px; box-sizing: border-box; border-radius: 12px; flex: none; display: flex; align-items: center; justify-content: center; background: #FFFFFF; border: 1.5px dashed #8A9690">'
            f'<svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path class="t" d="{GLYPHS["app"]}" stroke="{MUTED}"></path></svg></span>')


def icons_board():
    sh, ed, tr = CATS["shopping"][1], CATS["education"][1], CATS["transfers"][1]

    def row(tile, name, sub, amount, money_in=False):
        return f"""<div style="display: flex; align-items: center; gap: 12px; min-height: 64px; padding: 4px 0; border-top: 1px solid {LINE}">
{tile}
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px"><span style="font-size: 16px; font-weight: 500">{name}</span><span style="font-size: 13px; color: {MUTED}">{sub}</span></span>
<span style="font-size: 16px; font-weight: 700">{amount}</span>
</div>"""

    def day(label, total, rows):
        return f"""<section style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 22px; padding: 6px 14px 4px">
<div style="display: flex; justify-content: space-between; padding: 8px 0 6px; font-size: 13px; color: {MUTED}"><span style="font-weight: 500">{label}</span><span>{total}</span></div>
{"".join(rows)}
</section>"""

    d1 = day("Tue 10 Nov", "₹69,149 spent", [
        row(glyph_tile("jewellery", sh), "Sri Lakshmi Jewellers", "Shopping · jewellery shop icon", "−₹18,500"),
        row(app_tile(), "Tanishq", "Shopping · icon from the Tanishq app", "−₹42,000"),
        row(glyph_tile("clothing", sh), "Kumar Silks &amp; Sarees", "Shopping · clothing shop icon", "−₹6,250"),
        row(app_tile(), "AJIO", "Shopping · icon from the AJIO app", "−₹2,399"),
    ])
    d2 = day("Mon 9 Nov", "₹5,589 spent", [
        row(glyph_tile("books", ed), "Crossword Bookstores", "Education · book shop icon", "−₹899"),
        row(glyph_tile("books", ed), "Sapna Book House", "Education · book shop icon", "−₹1,240"),
        row(glyph_tile("opticals", sh), "Vision Opticals", "Shopping · opticals shop icon", "−₹3,450"),
        row(app_tile(), "HDFC Bank card bill", "Card bill · icon from the HDFC Bank app", "−₹23,800"),
    ])
    chips = "".join(
        f'<button type="button" style="min-height: 36px; padding: 0 14px; border-radius: 18px; border: 1px solid {"#D3EBDF" if i == 0 else LINE}; background: {"#D3EBDF" if i == 0 else "#FFFFFF"}; color: {"#0B5A41" if i == 0 else INK}; font: inherit; font-size: 14px; font-weight: 500">{c}</button>'
        for i, c in enumerate(["All", "Spent", "Received", "Needs review"]))
    body = f"""<div style="width: 390px; height: 844px; position: relative; overflow: hidden; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif">
<div style="height: 844px; overflow-y: auto; box-sizing: border-box; padding: 20px 16px 120px; display: flex; flex-direction: column; gap: 12px">
<div style="display: flex; flex-direction: column; gap: 2px; padding: 4px 4px 0">
<h1 style="margin: 0; font-size: 28px; font-weight: 700">Activity</h1>
<span style="font-size: 14px; color: {MUTED}">November 2026 · 8 transactions</span>
</div>
<label style="display: flex; align-items: center; gap: 8px; min-height: 48px; padding: 0 16px; border-radius: 24px; background: #FFFFFF; border: 1px solid {LINE}">
<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path class="t" d="M11 4 a7 7 0 1 0 0.01 0 M16 16 L20 20" stroke="{MUTED}"></path></svg>
<span style="position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0)">Search</span>
<input type="search" placeholder="Search merchant, category or note" style="flex: 1; border: 0; outline: none; background: transparent; font: inherit; font-size: 15px; color: {INK}">
</label>
<div style="display: flex; gap: 8px; flex-wrap: wrap">{chips}</div>
{d1}
{d2}
</div>
{tab_bar("Activity")}
</div>"""
    return page("Activity with the new icons", body, STATIC_SCRIPT, (390, 844))


def shop_icons_board():
    sh, ed, he, fo = CATS["shopping"][1], CATS["education"][1], CATS["health"][1], CATS["food"][1]
    items = [
        ("jewellery", sh, "Jewellers", "JEWELLERS, GOLD, DIAMONDS"),
        ("clothing", sh, "Clothing", "SILKS, SAREES, FASHION, GARMENTS"),
        ("books", ed, "Books", "BOOK HOUSE, BOOKS, STATIONERY"),
        ("pharmacy", he, "Pharmacy", "MEDICALS, PHARMACY, CHEMIST"),
        ("opticals", sh, "Opticals", "OPTICALS, OPTICIANS"),
        ("bakery", fo, "Bakery &amp; sweets", "BAKERY, SWEETS, CAKES"),
        ("salon", sh, "Salon", "SALON, PARLOUR, SPA"),
        ("mobiles", sh, "Mobiles", "MOBILES, ELECTRONICS"),
        ("hardware", sh, "Hardware", "HARDWARE, PAINTS, TOOLS"),
        ("clinic", he, "Clinic", "HOSPITAL, CLINIC, DIAGNOSTICS"),
    ]
    tiles = "".join(f"""<div style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 18px; padding: 14px 12px; display: flex; flex-direction: column; align-items: flex-start; gap: 8px">
{glyph_tile(k, c, 48, 14)}
<span style="font-size: 15px; font-weight: 700">{label}</span>
<span style="font-size: 12px; color: {MUTED}; line-height: 1.35">{words}</span>
</div>""" for k, c, label, words in items)
    body = f"""<div style="width: 720px; height: 480px; box-sizing: border-box; padding: 32px; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif; display: flex; flex-direction: column; gap: 18px">
<div style="display: flex; flex-direction: column; gap: 4px">
<h1 style="margin: 0; font-size: 24px; font-weight: 700">Icons for shops with no app</h1>
<p style="margin: 0; font-size: 15px; color: {MUTED}">Picked from words in the shop's name in the bank SMS.</p>
</div>
<div style="display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px">
{tiles}
</div>
</div>"""
    return page("Shop-type icons", body, STATIC_SCRIPT, (720, 480))


def canvas():
    return json.dumps({
        "v": 3,
        "createdOnFiles": {"v": 1, "at": "2026-11-10T00:28:09Z"},
        "title": "Owl Tracker · category animations & icons",
        "launch": {"view": "canvas"},
        "pages": [],
        "boards": {
            "Main.dc.html": {"x": 0, "y": 0, "w": 390, "h": 844, "title": "Option A · tap a category (works)", "is_interactive": True, "radius": 36},
            "Gallery.dc.html": {"x": 470, "y": 0, "w": 1280, "h": 960, "title": "Option A · all 19 animations"},
            "Scenes.dc.html": {"x": 0, "y": 1240, "w": 1280, "h": 520, "title": "Option B · a scene that shows the number"},
            "Icons.dc.html": {"x": 0, "y": 2120, "w": 390, "h": 844, "title": "Activity with the new icons", "radius": 36},
            "ShopIcons.dc.html": {"x": 470, "y": 2120, "w": 720, "h": 480, "title": "Icons for shops with no app"},
        },
        "order": ["Main.dc.html", "Gallery.dc.html", "Scenes.dc.html", "Icons.dc.html", "ShopIcons.dc.html"],
        "notes": {
            "optionA": {"x": 0, "y": -300, "text": "Option A · the category's icon comes alive", "kind": "title1", "maxW": 1750},
            "icons": {"x": 0, "y": 1820, "text": "More icons · apps, and shops with no app", "kind": "title1", "maxW": 1190},
        },
        "designSystems": [],
    }, indent=1, ensure_ascii=False)


if __name__ == "__main__":
    which = sys.argv[1]
    out = {"canvas": ("canvas.json", canvas), "Main": ("Main.dc.html", main_board), "Gallery": ("Gallery.dc.html", gallery_board),
           "Scenes": ("Scenes.dc.html", scenes_board), "Icons": ("Icons.dc.html", icons_board),
           "ShopIcons": ("ShopIcons.dc.html", shop_icons_board)}[which]
    with open(ROOT + out[0], "w") as f:
        f.write(out[1]())
    print("wrote", out[0])
