"""Round 2 of the canvas: Option B scenes for every category, a working phone, better people and vehicles.
python3 gen2.py <BPhone|BGallery|canvas>"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen import BG, BRAND, CATS, FONTS, INK, LINE, MUTED, ROOT, mix, tab_bar  # noqa: E402

SK, SKD, HAIR, NAVY, TYRE, HUB = "#C68A5E", "#A9714A", "#2B2118", "#2F3A55", "#2F3A35", "#C9D3CD"
GOLD, GOLD_D, GREY, RED = "#F2B84B", "#B8862B", "#DCE3DE", "#C8423B"

BASE_CSS = """body{margin:0}
a{color:#0F6B4F}a:hover{color:#0B4D38}
.w1,.w15,.w2,.w3,.w4,.w45,.w5,.w6,.w7,.w10,.w12,.road,.gauge{fill:none;stroke-linecap:round;stroke-linejoin:round}
.w1{stroke-width:1.2}.w15{stroke-width:1.6}.w2{stroke-width:2}.w3{stroke-width:3}.w4{stroke-width:4}.w45{stroke-width:4.5}
.w5{stroke-width:5}.w6{stroke-width:6}.w7{stroke-width:7}.w10{stroke-width:10}.w12{stroke-width:12}
.road{stroke-width:6}.gauge{stroke-width:8}
.sh{fill-opacity:.13}.tint{fill-opacity:.88}
.dash{stroke-dasharray:4 4}.dots{stroke-dasharray:1 6}
.lbl{font:500 11px Roboto, system-ui, sans-serif;fill:#46524C}
.lbl-end{text-anchor:end}.lbl-mid{text-anchor:middle}
.lbl-red{font:700 11px Roboto, system-ui, sans-serif;fill:#B3261E;text-anchor:end}
.lbl-good{font:700 11px Roboto, system-ui, sans-serif;fill:#0B5A41;text-anchor:end}
.tiny{font:700 6px Roboto, system-ui, sans-serif;fill:#FFFFFF;text-anchor:middle}
.pump{position:absolute;left:24px;top:30px;width:44px;height:20px;border-radius:3px;background:#16211C;color:#7FD3AE;display:flex;align-items:center;justify-content:center;font:700 12px 'Roboto Mono', ui-monospace, monospace;overflow:hidden}
.dg{display:inline-block;width:7.4px;height:14px;overflow:hidden;line-height:14px}
.col{display:flex;flex-direction:column}
"""


def inr(n):
    s = str(int(round(n)))
    if len(s) <= 3:
        return "₹" + s
    head, tail = s[:-3], s[-3:]
    parts = []
    while len(head) > 2:
        parts.insert(0, head[-2:])
        head = head[:-2]
    if head:
        parts.insert(0, head)
    return "₹" + ",".join(parts) + "," + tail


class Ctx:
    def __init__(self, uid, loop, dur=4):
        self.uid, self.loop, self.n, self.css, self.dur = uid, loop, 0, [], dur

    def it(self):
        return "infinite" if self.loop else "1"

    def anim(self, frames, dur=None, timing="ease-out", delay=0, count=None, box=None, origin=None, extra=""):
        dur = self.dur if dur is None else dur
        self.n += 1
        name = f"{self.uid}k{self.n}"
        props = []
        if box:
            props.append(f"transform-box:{box}")
        if origin:
            props.append(f"transform-origin:{origin}")
        if extra:
            props.append(extra)
        cnt = count if count is not None else self.it()
        props.append(f"animation:{name} {dur}s {timing} {delay}s {cnt} both")
        self.css.append("." + name + "{" + ";".join(props) + "}@keyframes " + name + "{" + frames + "}")
        return name

    def spin(self, t=40, turns=3):
        return self.anim(f"0%{{transform:rotate(0)}}{t}%,100%{{transform:rotate({360 * turns}deg)}}", box="fill-box", origin="center")

    def appear(self, at, dur=None):
        return self.anim(f"0%,{max(at - 4, 0)}%{{opacity:0}}{at}%,100%{{opacity:1}}", dur=dur)


def wheel(cx, cy, r, W):
    return (f'<g class="{W}"><circle cx="{cx}" cy="{cy}" r="{r}" fill="{TYRE}"></circle>'
            f'<circle cx="{cx}" cy="{cy}" r="{r * 0.42:.1f}" fill="{HUB}"></circle>'
            f'<path d="M{cx} {cy - r + 1.4:.1f} V{cy - r * 0.42:.1f}" stroke="{HUB}" class="w15"></path></g>')


# ---------------------------------------------------------------- vehicles (facing right; front x, ground y in local units)
def car(c, W):
    D, L = mix(c, "#000000", 0.28), mix(c, "#FFFFFF", 0.72)
    return (f'<ellipse cx="38" cy="40" rx="32" ry="2.2" fill="#16211C" class="sh"></ellipse>'
            f'<path d="M6 31 Q4 24 10 22 L20 13 Q24 9 30 9 H46 Q52 9 56 13 L63 21 Q70 22 70 29 V31 Q70 33 68 33 H8 Q6 33 6 31 Z" fill="{c}"></path>'
            f'<path d="M22 21 L27 15 Q29 13 32 13 H36.5 V21 Z" fill="{L}"></path>'
            f'<path d="M39.5 13 H46 Q50 13 53 16 L58 21 H39.5 Z" fill="{L}"></path>'
            f'<path d="M38 22.5 V31 M15 26 H62" stroke="{D}" class="w1"></path>'
            f'<rect x="64.5" y="24" width="4.5" height="3" rx="1" fill="{GOLD}"></rect>'
            f'<rect x="6" y="24" width="2.5" height="4" rx="1" fill="{RED}"></rect>'
            + wheel(18, 33, 6.5, W) + wheel(57, 33, 6.5, W)), 70, 39.5


def scooter(c, W):
    D = mix(c, "#000000", 0.28)
    return (f'<ellipse cx="36" cy="40" rx="28" ry="2" fill="#16211C" class="sh"></ellipse>'
            f'<path d="M8 28 Q6 18 16 17 H34 L39 28 Z" fill="{c}"></path>'
            f'<path d="M14 16.5 H32" stroke="{TYRE}" class="w5"></path>'
            f'<path d="M30 29 H50" stroke="{D}" class="w4"></path>'
            f'<path d="M49 30 L55 9" stroke="{c}" class="w5"></path>'
            f'<path d="M46 31 Q54 24 62 31" stroke="{c}" class="w4"></path>'
            f'<path d="M52 9 H60" stroke="{TYRE}" class="w3"></path>'
            f'<circle cx="58.5" cy="13.5" r="2" fill="{GOLD}"></circle>'
            f'<path d="M24 15 L36 17 L38 27" stroke="#3B4A6B" class="w6"></path>'
            f'<path d="M36 27.5 H43" stroke="{TYRE}" class="w4"></path>'
            f'<path d="M23 15 L30 -3" stroke="{NAVY}" class="w10"></path>'
            f'<path d="M29 -1 L41 5 L51 9" stroke="{NAVY}" class="w5"></path>'
            f'<circle cx="52.5" cy="9" r="2.3" fill="{SK}"></circle>'
            f'<circle cx="32" cy="-10" r="6.8" fill="{GOLD}"></circle>'
            f'<path d="M34 -11.5 H39.2 Q39.6 -7 36 -6.4 H34 Z" fill="{TYRE}"></path>'
            + wheel(16, 33, 6.5, W) + wheel(56, 33, 6.5, W)), 62, 39.5


def bus(c, W):
    D, L = mix(c, "#000000", 0.28), mix(c, "#FFFFFF", 0.72)
    win = "".join(f'<rect x="{x}" y="8" width="13" height="13" rx="2" fill="{L}"></rect>' for x in (8, 24, 40, 56))
    return (f'<ellipse cx="50" cy="47.5" rx="46" ry="2" fill="#16211C" class="sh"></ellipse>'
            f'<rect x="2" y="2" width="96" height="38" rx="6" fill="{c}"></rect>'
            f'<rect x="2" y="27" width="96" height="4" fill="{D}"></rect>{win}'
            f'<rect x="72" y="8" width="10" height="28" rx="1.5" fill="{L}"></rect>'
            f'<path d="M77 8 V36" stroke="{c}" class="w1"></path>'
            f'<path d="M86 7 H93 Q97 7 97 11 V23 H86 Z" fill="{L}"></path>'
            f'<rect x="84" y="3.2" width="11" height="2.6" rx="1" fill="{GOLD}"></rect>'
            f'<rect x="94" y="30" width="3" height="3" rx="1" fill="{GOLD}"></rect>'
            + wheel(20, 40, 7, W) + wheel(78, 40, 7, W)), 99, 47


def metro(c, W):
    win = "".join(f'<rect x="{x}" y="9" width="9" height="8" rx="1.5" fill="#3B4A57"></rect>' for x in (7, 33, 45, 65, 91))
    doors = "".join(f'<rect x="{x}" y="8" width="9" height="20" rx="1" fill="#E7ECE8" stroke="#9AA6A0" class="w1"></rect>' for x in (20, 78))
    bog = "".join(f'<rect x="{x}" y="30" width="12" height="3" rx="1" fill="{TYRE}"></rect>' for x in (8, 38, 66, 98))
    return (f'<rect x="2" y="4" width="54" height="26" rx="4" fill="#FFFFFF" stroke="#9AA6A0" class="w1"></rect>'
            f'<path d="M60 4 H106 Q117 4 119.5 17 V30 H60 Z" fill="#FFFFFF" stroke="#9AA6A0" class="w1"></path>'
            f'<rect x="2.6" y="21" width="52.8" height="3.5" fill="{c}"></rect>'
            f'<rect x="60.6" y="21" width="58.6" height="3.5" fill="{c}"></rect>{win}{doors}'
            f'<path d="M108 8 Q114.5 9 117 16 H108 Z" fill="#3B4A57"></path>'
            f'<circle cx="117" cy="26.5" r="1.5" fill="{GOLD}"></circle>'
            f'<path d="M56 25 H60" stroke="#9AA6A0" class="w2"></path>{bog}'), 120, 33


def train(c, W):
    D, L = mix(c, "#000000", 0.32), mix(c, "#FFFFFF", 0.72)
    coaches = ""
    for x0 in (2, 50):
        coaches += f'<rect x="{x0}" y="6" width="44" height="24" rx="3" fill="{c}"></rect>'
        coaches += "".join(f'<rect x="{x0 + 4 + i * 6.6:.1f}" y="11" width="4.4" height="6" rx="1" fill="{L}"></rect>' for i in range(6))
        coaches += f'<rect x="{x0}" y="23" width="44" height="2" fill="{L}"></rect>'
    wheels = "".join(f'<circle cx="{x}" cy="32" r="3.4" fill="{TYRE}"></circle>' for x in (9, 39, 57, 87, 106, 142))
    return (coaches + f'<path d="M98 6 H136 Q147 6 150 18 V30 H98 Z" fill="{D}"></path>'
            f'<path d="M138 9 Q145 10 147 17 H138 Z" fill="{L}"></path>'
            f'<rect x="98" y="20" width="52" height="3" fill="{GOLD}"></rect>'
            f'<circle cx="148.4" cy="25.5" r="1.8" fill="{GOLD}"></circle>'
            f'<path d="M46 26 H50 M94 26 H98" stroke="{TYRE}" class="w2"></path>{wheels}'), 150, 35.5


def coach(c, W):
    D = mix(c, "#000000", 0.28)
    return (f'<ellipse cx="52" cy="44" rx="48" ry="2" fill="#16211C" class="sh"></ellipse>'
            f'<path d="M2 6 H88 Q98 6 100.5 15 L102 31 Q102 37 96 37 H6 Q2 37 2 33 Z" fill="{c}"></path>'
            f'<rect x="6" y="10" width="80" height="11" rx="2" fill="{TYRE}" class="tint"></rect>'
            f'<path d="M90 9 Q97.5 10 99.5 17 L100.5 23 H90 Z" fill="{TYRE}"></path>'
            f'<path d="M5 26 H100" stroke="#FFFFFF" class="w2"></path>'
            f'<path d="M30 28.5 V35 M52 28.5 V35" stroke="{D}" class="w1"></path>'
            f'<rect x="97" y="29" width="4" height="3" rx="1" fill="{GOLD}"></rect>'
            + wheel(22, 37, 6.5, W) + wheel(82, 37, 6.5, W)), 102, 43.5


def plane(c):
    D = mix(c, "#000000", 0.25)
    wins = "".join(f'<circle cx="{x}" cy="13" r="1.3" fill="{c}"></circle>' for x in range(22, 67, 6))
    return (f'<path d="M8 9 L3 -5 H11 L21 8 Z" fill="{c}"></path>'
            f'<path d="M4 14 Q4 8 14 8 H62 Q75 8 82 14 Q75 20 62 20 H14 Q4 20 4 14 Z" fill="#FFFFFF" stroke="#9AA6A0" class="w1"></path>'
            f'<path d="M74 10.5 Q78.5 11.2 80.5 13.5 H74 Z" fill="#3B4A57"></path>{wins}'
            f'<path d="M36 16 L49 16 L38 31 L29 31 Z" fill="{D}"></path>'
            f'<ellipse cx="42" cy="21.5" rx="5" ry="2.4" fill="#3B4A57"></ellipse>')


# ---------------------------------------------------------------- people
def runner(ctx, shirt):
    cyc, cnt = 0.66, ("infinite" if ctx.loop else "2.5")
    dark_shirt = mix(shirt, "#000000", 0.22)

    def a(frames, origin, delay=0.0):
        return ctx.anim(frames, dur=cyc, timing="ease-in-out", delay=delay, count=cnt, box="view-box", origin=origin)
    th = "0%{transform:rotate(-38deg)}50%{transform:rotate(30deg)}100%{transform:rotate(-38deg)}"
    sh = "0%{transform:rotate(10deg)}35%{transform:rotate(22deg)}60%{transform:rotate(85deg)}85%{transform:rotate(55deg)}100%{transform:rotate(10deg)}"
    ar = "0%{transform:rotate(40deg)}50%{transform:rotate(-40deg)}100%{transform:rotate(40deg)}"
    thA, thB = a(th, "28px 46px"), a(th, "28px 46px", -cyc / 2)
    shA, shB = a(sh, "28px 60px"), a(sh, "28px 60px", -cyc / 2)
    arA, arB = a(ar, "31px 23px"), a(ar, "31px 23px", -cyc / 2)
    bob = ctx.anim("0%,100%{transform:translateY(0)}50%{transform:translateY(-2.5px)}", dur=cyc / 2, timing="ease-in-out",
                   count=("infinite" if ctx.loop else "5"))

    def leg(t, s, skin, shoe):
        return (f'<g class="{t}"><path d="M28 47 L28 60" stroke="{skin}" class="w7"></path><g class="{s}">'
                f'<path d="M28 60 L28 73" stroke="{skin}" class="w6"></path>'
                f'<path d="M25.2 72.4 H31.6 Q36.2 72.4 36.4 76 V77.6 H25.2 Z" fill="{shoe}" stroke="{TYRE}" class="w1"></path></g></g>')

    def arm(t, skin, sleeve):
        return (f'<g class="{t}"><path d="M31 23 L31 33" stroke="{skin}" class="w5"></path>'
                f'<path d="M31 22.5 L31 27" stroke="{sleeve}" class="w7"></path>'
                f'<g transform="rotate(-80 31 33)"><path d="M31 33 L31 40.5" stroke="{skin}" class="w45"></path>'
                f'<circle cx="31" cy="41.6" r="2.5" fill="{skin}"></circle></g></g>')
    head = (f'<path d="M32.6 14.5 L32.2 18.5" stroke="{SK}" class="w4"></path>'
            f'<circle cx="34" cy="9" r="6.6" fill="{SK}"></circle>'
            f'<path d="M27.5 8.8 Q27.6 1.6 34.4 2.4 Q40.6 3.2 40.8 7.6 Q36.6 5.5 33.2 6.6 Q30.2 8.7 27.5 8.8 Z" fill="{HAIR}"></path>'
            f'<circle cx="38.2" cy="8.8" r=".95" fill="{HAIR}"></circle>'
            f'<path d="M30 6.2 Q34.5 4.8 40.3 6.6" stroke="{shirt}" class="w15"></path>')
    torso = (f'<path d="M31 21 L28.6 40.5" stroke="{shirt}" class="w12"></path>'
             f'<path d="M28.6 40.5 L28.2 46.5" stroke="{NAVY}" class="w12"></path>')
    body = (arm(arB, SKD, dark_shirt) + leg(thB, shB, SKD, "#E7ECE8") + torso + head
            + leg(thA, shA, SK, "#FFFFFF") + arm(arA, SK, shirt))
    return f'<g class="{bob}"><g transform="rotate(8 28 46)">{body}</g></g>', 40, 78


def batsman(ctx, jersey):
    """Static stance with a swinging bat; returns (svg, contact point in local units)."""
    swing = ctx.anim("0%,24%{transform:rotate(-165deg)}31%{transform:rotate(-290deg)}35%{transform:rotate(-345deg)}"
                     "44%,100%{transform:rotate(-455deg)}", timing="ease-in", box="view-box", origin="29px 24px")
    pad = "#FFFFFF"
    legs = (f'<path d="M25 46 L21 62 L21 75" stroke="{TYRE}" class="w7" style="stroke-width: 8.6"></path>'
            f'<path d="M25 46 L21 62 L21 75" stroke="{pad}" class="w7"></path>'
            f'<path d="M27 46 L32 62 L33 75" stroke="{TYRE}" class="w7" style="stroke-width: 8.6"></path>'
            f'<path d="M27 46 L32 62 L33 75" stroke="{pad}" class="w7"></path>'
            f'<path d="M18 77 H25 M30 77 H38" stroke="{TYRE}" class="w4"></path>')
    torso = (f'<path d="M26.5 40 L26 47" stroke="#E7ECE8" class="w12"></path>'
             f'<path d="M28 22 L26.5 41" stroke="{jersey}" class="w12"></path>')
    head = (f'<circle cx="31" cy="10" r="6.4" fill="{SK}"></circle>'
            f'<path d="M24.4 10 Q24.6 2 31.4 2.4 Q38.2 2.8 38.6 9.6 Z" fill="{NAVY}"></path>'
            f'<path d="M37.4 8.8 L42.2 9.8" stroke="{NAVY}" class="w2"></path>'
            f'<path d="M37.2 11 V16 M39.2 10.6 V15.4" stroke="#9AA6A0" class="w1"></path>')
    bat = (f'<g class="{swing}"><path d="M29 24 L37 33" stroke="{SK}" class="w5"></path>'
           f'<circle cx="38" cy="34" r="2.9" fill="#FFFFFF" stroke="{TYRE}" class="w1"></circle>'
           f'<path d="M38.5 34.5 L42 38" stroke="{TYRE}" class="w3"></path>'
           f'<path d="M42 38 L56 52" stroke="#D9B27C" class="w6"></path></g>')
    # bat tip at rotate(-345deg) (= +15 from rest): the rest direction from the shoulder is about 45 deg
    ang = math.radians(45 + 15)
    tip = (29 + 38.5 * math.cos(ang), 24 + 38.5 * math.sin(ang))
    return legs + torso + head + bat, tip


# ---------------------------------------------------------------- shared scene parts
X0, XB, XE, YG = 12, 220, 236, 98


def track(ctx, c, p, label, marker="flag", t=40, y=YG, xb=XB, budget=None, over_text=None):
    out = [f'<path class="road" d="M{X0} {y} H{XE}" stroke="{GREY}"></path>']
    pp = min(p, 1.0)
    xp = X0 + (xb - X0) * pp
    t1 = t if p <= 1 else round(t / p)
    k = ctx.anim(f"0%{{stroke-dashoffset:100}}{t1}%,100%{{stroke-dashoffset:0}}", extra="stroke-dasharray:100")
    out.append(f'<path class="road {k}" d="M{X0} {y} H{xp:.1f}" stroke="{c}" pathLength="100"></path>')
    if p > 1:
        xo = min(X0 + (xb - X0) * p, XE)
        k2 = ctx.anim(f"0%,{t1}%{{stroke-dashoffset:100}}{t}%,100%{{stroke-dashoffset:0}}", extra="stroke-dasharray:100")
        out.append(f'<path class="road {k2}" d="M{xb} {y} H{xo:.1f}" stroke="{RED}" pathLength="100"></path>')
        out.append(f'<text x="{XE}" y="16" class="lbl-red {ctx.appear(t + 4)}">{over_text}</text>')
    out.append(marker_svg(marker, c, xb, y))
    out.append(f'<text x="{xb - 7}" y="{y - 47}" class="lbl lbl-end">{label}</text>')
    return "".join(out)


def marker_svg(kind, c, x, y):
    D = mix(c, "#000000", 0.28)
    if kind == "flag":
        return (f'<path d="M{x} {y} V{y - 42}" stroke="#46524C" class="w2"></path>'
                f'<path d="M{x} {y - 42} L{x + 18} {y - 36} L{x} {y - 30} Z" fill="{c}"></path>')
    if kind == "stop":
        return (f'<path d="M{x} {y} V{y - 40}" stroke="#46524C" class="w2"></path>'
                f'<rect x="{x - 9}" y="{y - 46}" width="18" height="12" rx="2" fill="{c}"></rect>'
                f'<text x="{x}" y="{y - 37.8}" class="tiny">STOP</text>')
    if kind == "station":
        return (f'<path d="M{x - 8} {y} V{y - 30} M{x + 8} {y} V{y - 30}" stroke="#46524C" class="w2"></path>'
                f'<rect x="{x - 13}" y="{y - 40}" width="26" height="11" rx="2" fill="{GOLD}" stroke="{TYRE}" class="w1"></rect>'
                f'<path d="M{x - 7} {y - 34.5} H{x + 7}" stroke="{TYRE}" class="w15"></path>')
    if kind == "milestone":
        return (f'<path d="M{x - 7} {y - 3} V{y - 18} Q{x - 7} {y - 26} {x} {y - 26} Q{x + 7} {y - 26} {x + 7} {y - 18} V{y - 3} Z" fill="#FFFFFF" stroke="#9AA6A0" class="w1"></path>'
                f'<path d="M{x - 7} {y - 18} Q{x - 7} {y - 26} {x} {y - 26} Q{x + 7} {y - 26} {x + 7} {y - 18} Z" fill="{c}"></path>')
    if kind == "tape":
        return (f'<path d="M{x - 6} {y} V{y - 38} M{x + 14} {y} V{y - 38}" stroke="#46524C" class="w2"></path>'
                f'<path d="M{x - 6} {y - 32} H{x + 14}" stroke="{c}" class="w2 dash"></path>')
    if kind == "platform":
        return (f'<rect x="{x - 4}" y="{y - 10}" width="20" height="6" rx="1" fill="#9AA6A0"></rect>'
                f'<path d="M{x - 2} {y - 10} V{y - 36} M{x + 14} {y - 10} V{y - 36} M{x - 6} {y - 36} H{x + 18}" stroke="#46524C" class="w2"></path>'
                f'<rect x="{x - 1}" y="{y - 34}" width="14" height="7" rx="1.5" fill="{c}"></rect>')
    if kind == "rope":
        return (f'<path d="M{x - 2} {y - 4} H{x + 14}" stroke="{c}" class="w6"></path>'
                f'<path d="M{x + 6} {y - 4} V{y - 30}" stroke="#46524C" class="w15"></path>'
                f'<path d="M{x + 6} {y - 30} L{x + 18} {y - 26} L{x + 6} {y - 22} Z" fill="{D}"></path>')
    return ""


def moving(ctx, body, p, front, ground, scale, t=40, y_ground=95):
    target = min(X0 + (XB - X0) * p, XE)
    start = -6
    tx, ty = start - front * scale, y_ground - ground * scale
    d = target - start
    k = ctx.anim(f"0%{{transform:translateX(0)}}{t}%{{transform:translateX({d + 3:.1f}px)}}{t + 6}%,100%{{transform:translateX({d:.1f}px)}}",
                 timing="cubic-bezier(.25,.8,.25,1)")
    return f'<g class="{k}"><g transform="translate({tx:.1f} {ty:.1f}) scale({scale})">{body}</g></g>'


def over_text(spent, budget):
    return inr(spent - budget) + " over"


# ---------------------------------------------------------------- scenes (248 x 120)
def sc_vehicle(ctx, c, p, kind, label, over):
    W = ctx.spin()
    body, front, ground = {"car": car, "scooter": scooter, "bus": bus, "metro": metro, "train": train, "coach": coach}[kind](c, W)
    scale = {"car": 0.62, "scooter": 0.62, "bus": 0.5, "metro": 0.48, "train": 0.4, "coach": 0.5}[kind]
    marker = {"car": "flag", "scooter": "flag", "bus": "stop", "metro": "platform", "train": "station", "coach": "milestone"}[kind]
    extra = ""
    if kind == "metro":
        extra = (f'<rect x="4" y="101" width="240" height="5" fill="#C9D3CD"></rect>'
                 + "".join(f'<rect x="{x}" y="106" width="7" height="14" fill="{GREY}"></rect>' for x in (30, 90, 150, 210)))
    if kind == "train":
        extra = "".join(f'<path d="M{x} 101 V105" stroke="#9AA6A0" class="w2"></path>' for x in range(16, 236, 9))
    if kind == "coach":
        extra = "".join(f'<path d="M{x} 110 H{x + 8}" stroke="#C9D3CD" class="w2"></path>' for x in range(14, 236, 18))
    return extra + track(ctx, c, p, label, marker, over_text=over) + moving(ctx, body, p, front, ground, scale), ""


def sc_flight(ctx, c, p, label, over):
    p0, cc, p2 = (16, 96), (118, 4), (220, 96)

    def at(t):
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * cc[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * cc[1] + t * t * p2[1]
        dx = 2 * (1 - t) * (cc[0] - p0[0]) + 2 * t * (p2[0] - cc[0])
        dy = 2 * (1 - t) * (cc[1] - p0[1]) + 2 * t * (p2[1] - cc[1])
        return x, y, math.degrees(math.atan2(dy, dx))
    # arc length fraction to t = min(p, 1)
    pts = [at(i / 200) for i in range(201)]
    seg = [math.dist(pts[i][:2], pts[i + 1][:2]) for i in range(200)]
    total = sum(seg)
    upto = sum(seg[: int(min(p, 1) * 200)])
    frac = upto / total * 100
    k = ctx.anim(f"0%{{stroke-dashoffset:100}}40%,100%{{stroke-dashoffset:{100 - frac:.1f}}}", extra="stroke-dasharray:100")
    tend = min(p, 1.12)
    stops = []
    for i in range(9):
        tt = tend * i / 8
        x, y, a = at(tt)
        stops.append(f"{i * 5}%{{transform:translate({x:.1f}px,{y:.1f}px) rotate({a:.1f}deg)}}")
    x, y, a = at(tend)
    stops.append(f"100%{{transform:translate({x:.1f}px,{y:.1f}px) rotate({a:.1f}deg)}}")
    kp = ctx.anim("".join(stops), timing="ease-in-out", box="view-box", origin="0 0")
    clouds = ('<ellipse cx="60" cy="30" rx="16" ry="5" fill="#FFFFFF"></ellipse><ellipse cx="68" cy="26" rx="9" ry="5" fill="#FFFFFF"></ellipse>'
              '<ellipse cx="176" cy="22" rx="14" ry="4.5" fill="#FFFFFF"></ellipse><ellipse cx="183" cy="18.5" rx="8" ry="4.5" fill="#FFFFFF"></ellipse>')
    path = "M16 96 Q118 4 220 96"
    out = (clouds + f'<path d="{path}" stroke="#9AA6A0" class="w2 dots"></path>'
           f'<path d="{path}" stroke="{c}" class="w3 {k}" pathLength="100"></path>'
           f'<circle cx="16" cy="96" r="4" fill="{c}"></circle>'
           f'<path d="M220 97 Q213 88 213 83 A7 7 0 1 1 227 83 Q227 88 220 97 Z" fill="{c}"></path><circle cx="220" cy="83" r="2.6" fill="#FFFFFF"></circle>'
           f'<text x="210" y="70" class="lbl lbl-end">{label}</text>'
           f'<g class="{kp}"><g transform="scale(0.42) translate(-42 -14)">{plane(c)}</g></g>')
    if p > 1:
        out += f'<text x="{XE}" y="16" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_fuel(ctx, c, p, label, amount):
    L, D = mix(c, "#FFFFFF", 0.72), mix(c, "#000000", 0.28)
    drop = "0%,6%{opacity:0;transform:translateY(-4px)}10%{opacity:1}30%{opacity:0;transform:translateY(14px)}100%{opacity:0;transform:translateY(14px)}"
    d1, d2 = ctx.anim(drop, timing="ease-in"), ctx.anim(drop, timing="ease-in", delay=0.3)
    kg = ctx.anim(f"0%{{stroke-dashoffset:100}}40%,100%{{stroke-dashoffset:{100 - min(p, 1) * 100:.1f}}}", extra="stroke-dasharray:100")
    ang = -90 + min(p, 1) * 180
    kn = ctx.anim(f"0%{{transform:rotate(-90deg)}}40%{{transform:rotate({ang + 4:.1f}deg)}}46%,100%{{transform:rotate({ang:.1f}deg)}}",
                  box="view-box", origin="186px 96px")
    svg = (f'<rect x="18" y="22" width="56" height="76" rx="7" fill="{c}"></rect>'
           f'<rect x="22" y="27" width="48" height="26" rx="4" fill="{L}"></rect>'
           f'<rect x="24" y="30" width="44" height="20" rx="3" fill="#16211C"></rect>'
           f'<rect x="28" y="60" width="36" height="22" rx="3" fill="{L}"></rect>'
           f'<path d="M34 66 H58 M34 72 H52" stroke="{D}" class="w2"></path>'
           f'<rect x="12" y="96" width="68" height="5" rx="2" fill="{D}"></rect>'
           f'<path d="M74 44 H86 Q94 44 94 54 V74" stroke="{TYRE}" class="w4"></path>'
           f'<path d="M90 73 H98 L102 80 H96 Z" fill="{TYRE}"></path>'
           f'<circle class="{d1}" cx="102" cy="86" r="2.8" fill="{c}"></circle>'
           f'<circle class="{d2}" cx="102" cy="86" r="2.8" fill="{c}"></circle>'
           f'<path class="gauge" d="M136 96 A50 50 0 0 1 236 96" stroke="{GREY}"></path>'
           f'<path class="gauge {kg}" d="M136 96 A50 50 0 0 1 236 96" stroke="{c}" pathLength="100"></path>'
           f'<g class="{kn}"><path d="M186 96 V58" stroke="#16211C" class="w3"></path></g>'
           f'<circle cx="186" cy="96" r="5" fill="#16211C"></circle>'
           f'<text x="136" y="113" class="lbl lbl-mid">E</text><text x="236" y="113" class="lbl lbl-mid">F</text>'
           f'<text x="186" y="113" class="lbl lbl-mid">{label}</text>')
    digits = [int(ch) for ch in str(int(amount))]
    cols = ""
    for i, dgt in enumerate(digits):
        k = ctx.anim(f"0%{{transform:translateY(0)}}40%,100%{{transform:translateY(-{(10 + dgt) * 14}px)}}",
                     timing="cubic-bezier(.2,.7,.2,1)")
        cols += f'<span class="dg"><span class="col {k}">' + "".join(f"<span>{j % 10}</span>" for j in range(20)) + "</span></span>"
        if len(digits) - i == 4:
            cols += ","
    return svg, f'<div class="pump">₹{cols}</div>'


def sc_runner(ctx, c, p, label, over):
    body, front, ground = runner(ctx, c)
    return track(ctx, c, p, label, "tape", over_text=over) + moving(ctx, body, p, front, ground, 0.62), ""


def sc_sports(ctx, c, p, label, over):
    body, tip = batsman(ctx, c)
    s, tx, ty = 0.62, 4, 95 - 78 * 0.62
    cx, cy = tx + tip[0] * s, ty + tip[1] * s
    land = min(X0 + (XB - X0) * p, XE + 4)
    apex = 14 if p > 0.5 else 40
    stops = [f"0%,34%{{opacity:0;transform:translate({cx:.1f}px,{cy:.1f}px)}}"]
    for i in range(1, 9):
        u = i / 8
        x = cx + (land - cx) * u
        y = cy + (YG - 4 - cy) * u - 4 * (cy - apex) * u * (1 - u) * 1.2
        stops.append(f"{36 + i * 3.5:.1f}%{{opacity:1;transform:translate({x:.1f}px,{y:.1f}px)}}")
    stops.append(f"70%,100%{{opacity:1;transform:translate({land + 3:.1f}px,{YG - 4}px)}}")
    kb = ctx.anim("".join(stops), timing="linear", box="view-box", origin="0 0")
    t1 = 66 if p <= 1 else round(66 / p)
    kt = ctx.anim(f"0%,36%{{stroke-dashoffset:100}}{t1}%,100%{{stroke-dashoffset:0}}", extra="stroke-dasharray:100")
    xp = X0 + (XB - X0) * min(p, 1)
    out = (f'<path class="road" d="M{X0} {YG} H{XE}" stroke="{GREY}"></path>'
           f'<path class="road {kt}" d="M{X0} {YG} H{xp:.1f}" stroke="{c}" pathLength="100"></path>'
           + marker_svg("rope", c, XB, YG) + f'<text x="{XB - 7}" y="{YG - 47}" class="lbl lbl-end">{label}</text>'
           f'<g transform="translate({tx} {ty:.1f}) scale({s})">{body}</g>'
           f'<g class="{kb}"><circle cx="0" cy="0" r="3.2" fill="{RED}" stroke="#FFFFFF" class="w1"></circle></g>')
    if p > 1:
        out += f'<text x="{XE}" y="16" class="lbl-red {ctx.appear(70)}">{over}</text>'
    return out, ""


def sc_chai(ctx, c, p, label, over):
    full = 56
    h = min(full * p, 66)
    kf = ctx.anim(f"0%{{transform:scaleY(0)}}40%,100%{{transform:scaleY(1)}}", box="fill-box", origin="50% 100%")
    steam = ""
    for i, x in enumerate((70, 80, 90)):
        ks = ctx.anim("0%,30%{opacity:0;transform:translateY(8px)}48%{opacity:1}80%,100%{opacity:0;transform:translateY(-10px)}",
                      delay=i * 0.25)
        steam += f'<path class="w2 {ks}" d="M{x} 26 q-4 -5 0 -10 q4 -5 0 -10" stroke="#9AA6A0"></path>'
    spill = ""
    if p > 1:
        kd = ctx.appear(44)
        spill = (f'<path class="{kd}" d="M58 32 Q54 40 55 52 Q56 58 53 62" stroke="#C58B4E" style="stroke-width: 3; fill: none; stroke-linecap: round"></path>'
                 f'<text x="{XE}" y="114" class="lbl-red {ctx.appear(46)}">{over}</text>')
    svg = (steam + f'<rect class="{kf}" x="61" y="{97 - h:.1f}" width="38" height="{h:.1f}" fill="#C58B4E"></rect>'
           f'<path d="M58 30 L61.5 98 H98.5 L102 30" stroke="#9AA6A0" class="w2"></path>'
           f'<path d="M64 38 L66 92" stroke="#FFFFFF" class="w2"></path>'
           f'<path d="M50 41 H244" stroke="{c}" class="w15 dash"></path>'
           f'<text x="244" y="36" class="lbl lbl-end">{label}</text>' + spill +
           f'<ellipse cx="190" cy="98" rx="34" ry="5" fill="#FFFFFF" stroke="#9AA6A0" class="w1"></ellipse>'
           f'<path d="M168 95 L182 70 L196 95 Z" fill="#E0A54C" stroke="#B8792C" class="w15"></path>'
           f'<path d="M190 95 L202 76 L214 95 Z" fill="#E8B560" stroke="#B8792C" class="w15"></path>')
    return svg, ""


def sc_cart(ctx, c, p, label, over):
    W = ctx.spin()
    mesh = mix(c, "#FFFFFF", 0.45)
    items = [(f'<circle cx="24" cy="8" r="4.5" fill="{RED}"></circle>', 8), (f'<rect x="30" y="-3" width="8" height="15" rx="1.5" fill="#FFFFFF" stroke="#9AA6A0" class="w1"></rect>', 16),
             (f'<path d="M40 10 Q46 0 54 4 Q47 4 42 11 Z" fill="{GOLD}"></path>', 24), (f'<rect x="44" y="1" width="9" height="11" rx="2" fill="#C58B4E"></rect>', 32),
             (f'<circle cx="35" cy="6" r="4" fill="#3F8A3A"></circle>', 38)]
    n = max(1, min(5, round(p * 5)))
    its = ""
    for svg, at in items[:n]:
        k = ctx.anim(f"0%,{at}%{{transform:scale(0)}}{at + 4}%,100%{{transform:scale(1)}}", box="fill-box", origin="50% 100%")
        its += f'<g class="{k}">{svg}</g>'
    body = (f'<path d="M2 6 H10 L14 12" stroke="{TYRE}" class="w3"></path>' + its +
            f'<path d="M12 12 H58 L52 34 H18 Z" fill="#FFFFFF" stroke="{c}" class="w3"></path>'
            f'<path d="M20 18 H54 M22 24 H52 M24 30 H50 M26 12 V34 M35 12 V34 M44 12 V34" stroke="{mesh}" class="w1"></path>'
            f'<path d="M18 34 L16 40 H54" stroke="{TYRE}" class="w2"></path>'
            + wheel(20, 43, 3.6, W) + wheel(50, 43, 3.6, W))
    shelves = ""
    for y in (26, 50):
        shelves += f'<path d="M14 {y} H180" stroke="#C9D3CD" class="w2"></path>'
        for i, x in enumerate(range(20, 176, 16)):
            col = [mix(c, "#FFFFFF", 0.4), "#E8B560", "#9AC5E0", mix(c, "#FFFFFF", 0.65)][i % 4]
            shelves += f'<rect x="{x}" y="{y - 12}" width="10" height="12" rx="1.5" fill="{col}"></rect>'
    counter = (f'<rect x="206" y="72" width="36" height="26" rx="2" fill="{mix(c, "#FFFFFF", 0.6)}"></rect>'
               f'<rect x="214" y="60" width="16" height="12" rx="2" fill="{TYRE}"></rect>'
               f'<rect x="216" y="62" width="12" height="5" rx="1" fill="#7FD3AE"></rect>')
    return (shelves + f'<path class="road" d="M{X0} {YG} H{XE}" stroke="{GREY}"></path>' + counter
            + track(ctx, c, p, label, "", over_text=over).replace(f'<path class="road" d="M{X0} {YG} H{XE}" stroke="{GREY}"></path>', "", 1)
            + moving(ctx, body, p, 58, 46.5, 0.62)), ""


def sc_receipt(ctx, c, p, label, over):
    xs, xb = 54, 220
    xe = min(xs + (xb - xs) * p, 240)
    w = xe - xs
    k = ctx.anim("0%{transform:scaleX(0)}40%,100%{transform:scaleX(1)}", box="fill-box", origin="0% 50%")
    lines = "".join(f'<path d="M{x} 86 H{x + 8} M{x} 91 H{x + 5}" stroke="#9AA6A0" class="w1"></path>' for x in range(xs + 6, int(xe) - 8, 14))
    zig = "".join(f"L{x + 3.5} 79 L{x + 7} 81 " for x in range(xs, int(xe) - 6, 7))
    red = ""
    if p > 1:
        red = f'<rect x="{xb}" y="80" width="{xe - xb:.1f}" height="16" fill="#F6D3D0"></rect>'
    paper = (f'<g class="{k}"><path d="M{xs} 81 {zig}L{xe:.1f} 81 V96 H{xs} Z" fill="#FFFFFF" stroke="#C9D3CD" class="w1"></path>'
             f'{red}{lines}</g>')
    till = (f'<path d="M20 22 H36 Q36 10 28 10 Q20 10 20 22" stroke="{mix(c, "#000000", 0.25)}" class="w2"></path>'
            f'<path d="M16 22 H40 L42 44 H14 Z" fill="{c}"></path>'
            f'<rect x="12" y="50" width="44" height="48" rx="5" fill="{mix(c, "#000000", 0.25)}"></rect>'
            f'<rect x="17" y="55" width="34" height="13" rx="2" fill="#7FD3AE"></rect>'
            + "".join(f'<circle cx="{21 + (i % 4) * 8.5}" cy="{76 + (i // 4) * 7}" r="2" fill="{mix(c, "#FFFFFF", 0.6)}"></circle>' for i in range(8))
            + f'<rect x="54" y="78" width="3" height="20" fill="{TYRE}"></rect>')
    cut = (f'<path d="M{xb} 64 V102" stroke="{TYRE}" class="w15 dash"></path>'
           f'<text x="{xb - 6}" y="60" class="lbl lbl-end">{label}</text>')
    out = till + paper + cut
    if p > 1:
        out += f'<text x="{XE}" y="16" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_bulbs(ctx, c, p, label, over):
    n = 10
    lit = min(n, round(p * n))
    def wy(x):
        t = (x - 14) / 220
        return (1 - t) ** 2 * 30 + 2 * (1 - t) * t * 62 + t * t * 30
    out = f'<path d="M14 30 Q124 62 234 30" stroke="{mix(c, "#000000", 0.3)}" class="w15"></path>'
    out += f'<path d="M14 30 V100 M234 30 V100" stroke="#9AA6A0" class="w2"></path>'
    for i in range(n):
        x = 24 + i * 21
        y = wy(x)
        glow = ""
        if i < lit:
            at = 4 + i * 3.6
            kg = ctx.anim(f"0%,{at:.1f}%{{opacity:0}}{at + 1.5:.1f}%{{opacity:1}}{at + 2.5:.1f}%{{opacity:.4}}{at + 3.5:.1f}%,100%{{opacity:1}}", timing="linear")
            col = RED if (p > 1 and i >= n - 2) else GOLD
            glow = (f'<g class="{kg}"><circle cx="{x}" cy="{y + 12:.1f}" r="9" fill="{col}" class="sh" style="fill-opacity: .35"></circle>'
                    f'<circle cx="{x}" cy="{y + 12:.1f}" r="5" fill="{col}"></circle></g>')
        out += (f'<rect x="{x - 2.5}" y="{y + 1:.1f}" width="5" height="5" rx="1" fill="{c}"></rect>'
                f'<circle cx="{x}" cy="{y + 12:.1f}" r="5" fill="#FFFFFF" stroke="#9AA6A0" class="w1"></circle>' + glow)
    out += f'<text x="14" y="114" class="lbl">{lit} of 10 lit · {label}</text>'
    if p > 1:
        out += f'<text x="{XE}" y="16" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_battery(ctx, c, p, label, over):
    w = 150 * min(p, 1)
    col = RED if p > 1 else c
    k = ctx.anim("0%{transform:scaleX(0)}40%,100%{transform:scaleX(1)}", box="fill-box", origin="0% 50%")
    kb = ctx.anim("0%,100%{opacity:.55}50%{opacity:1}", dur=1.2, timing="ease-in-out", count=("infinite" if ctx.loop else "3"))
    cells = "".join(f'<path d="M{x} 46 V86" stroke="#FFFFFF" class="w2"></path>' for x in (78, 116, 154))
    out = (f'<rect x="36" y="38" width="164" height="56" rx="10" fill="#FFFFFF" stroke="{TYRE}" class="w3"></rect>'
           f'<rect x="200" y="56" width="9" height="20" rx="3" fill="{TYRE}"></rect>'
           f'<rect class="{k}" x="43" y="45" width="{w:.1f}" height="42" rx="6" fill="{col}"></rect>{cells}'
           f'<path class="w1 {kb}" d="M122 50 L106 70 H117 L112 84 L130 62 H119 Z" fill="#FFFFFF" stroke="{TYRE}"></path>'
           f'<text x="200" y="28" class="lbl lbl-end">{label}</text>')
    if p > 1:
        out += f'<text x="{XE}" y="16" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_popcorn(ctx, c, p, label, over):
    rise = 40 * (1 - min(p, 1.15))
    k = ctx.anim(f"0%{{transform:translateY(46px);opacity:0}}6%{{opacity:1}}40%{{transform:translateY({rise - 3:.1f}px)}}46%,100%{{opacity:1;transform:translateY({rise:.1f}px)}}",
                 timing="cubic-bezier(.3,.7,.3,1)")
    puffs = ""
    for (x, y, r) in [(104, 54, 8), (116, 48, 9), (130, 46, 9.5), (144, 49, 8.5), (152, 56, 7), (98, 60, 6), (110, 58, 8), (124, 56, 8), (138, 57, 8), (120, 38, 7), (134, 37, 6.5), (127, 30, 6)]:
        puffs += f'<circle cx="{x}" cy="{y + 2}" r="{r}" fill="#FFF3D6" stroke="#E8C98A" class="w1"></circle>'
    heap = f'<g class="{k}"><rect x="100" y="50" width="48" height="50" fill="#FFF3D6"></rect>{puffs}</g>'
    pops = ""
    for i, (dx, dy) in enumerate([(-30, -26), (26, -30), (40, -14)]):
        kp = ctx.anim(f"0%,{20 + i * 6}%{{opacity:0;transform:translate(0,0)}}{24 + i * 6}%{{opacity:1}}{40 + i * 6}%,100%{{opacity:1;transform:translate({dx}px,{dy + 30}px)}}",
                      timing="ease-out")
        pops += f'<g class="{kp}"><circle cx="125" cy="40" r="4.5" fill="#FFF3D6" stroke="#E8C98A" class="w1"></circle></g>'
    stripes = "".join(f'<path d="M{x} 60 L{x + 1.5} 104 H{x + 9} L{x + 8} 60 Z" fill="{c}"></path>' for x in (95, 113, 131, 149))
    bucket = (f'<path d="M90 60 L98 104 H150 L158 60 Z" fill="#FFFFFF" stroke="#C9D3CD" class="w1"></path>'
              f'<g>{stripes}</g><rect x="88" y="56" width="72" height="6" rx="2" fill="{mix(c, "#000000", 0.25)}"></rect>')
    line = (f'<path d="M80 18 H244" stroke="{c}" class="w15 dash"></path><text x="244" y="13" class="lbl lbl-end">{label}</text>')
    ticket = (f'<path d="M24 64 H64 V72 A4 4 0 0 0 64 80 V88 H24 V80 A4 4 0 0 0 24 72 Z" fill="{mix(c, "#FFFFFF", 0.55)}"></path>'
              f'<path d="M36 66 V86" stroke="#FFFFFF" class="w1 dash"></path>')
    out = ticket + heap + pops + bucket + line
    if p > 1:
        out += f'<text x="{XE}" y="114" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_books(ctx, c, p, label, over):
    full = 8
    n = max(1, round(p * full))
    out = (f'<path d="M80 34 H244" stroke="{c}" class="w15 dash"></path><text x="244" y="29" class="lbl lbl-end">{label}</text>'
           f'<path d="M60 98 H200" stroke="#C9D3CD" class="w3"></path>')
    shades = [c, mix(c, "#000000", 0.25), mix(c, "#FFFFFF", 0.35), mix(c, "#FFFFFF", 0.15)]
    widths = [58, 52, 62, 48, 56, 60, 50, 54, 58, 52]
    for i in range(n):
        y = 96.5 - (i + 1) * 8
        w = widths[i % len(widths)]
        x = 135 - w / 2 + (3 if i % 2 else -2)
        col = RED if i >= full else shades[i % 4]
        at = 3 + i * 4
        k = ctx.anim(f"0%,{at}%{{opacity:0;transform:translateY(-50px)}}{at + 1}%{{opacity:1}}{at + 4}%{{transform:translateY(1.5px)}}{at + 5}%,100%{{opacity:1;transform:translateY(0)}}",
                     timing="ease-in")
        out += (f'<g class="{k}"><rect x="{x:.1f}" y="{y:.1f}" width="{w}" height="7.4" rx="1.5" fill="{col}"></rect>'
                f'<path d="M{x + 4:.1f} {y + 3.7:.1f} H{x + w - 4:.1f}" stroke="#FFFFFF" class="w1"></path></g>')
    out += (f'<path d="M30 92 L46 70" stroke="{GOLD}" class="w5"></path><path d="M46 70 L49 66" stroke="{TYRE}" class="w3"></path>'
            f'<path d="M29 93.5 L30.5 91" stroke="#E8B560" class="w3"></path>')
    if p > 1:
        out += f'<text x="{XE}" y="114" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_key(ctx, c, p, label, over):
    door_x = 210
    target = 12 + (door_x - 12 - 14) * min(p, 1)
    hop = []
    for i in range(9):
        u = i / 8
        x = target * u
        y = -6 * abs(math.sin(u * math.pi * 4))
        hop.append(f"{u * 40:.1f}%{{transform:translate({x:.1f}px,{y:.1f}px)}}")
    hop.append(f"100%{{transform:translate({target:.1f}px,0px)}}")
    kk = ctx.anim("".join(hop), timing="linear")
    key = (f'<g class="{kk}"><g transform="translate(0 84)"><circle cx="6" cy="6" r="5.5" fill="none" stroke="{GOLD_D}" class="w3"></circle>'
           f'<path d="M11.5 6 H26 M21 6 V10 M25 6 V9" stroke="{GOLD_D}" class="w3"></path></g></g>')
    paid = p >= 1
    house = (f'<path d="M168 56 L204 26 L240 56 Z" fill="{c}"></path>'
             f'<rect x="176" y="54" width="56" height="44" fill="#FFFFFF" stroke="{c}" class="w2"></rect>'
             f'<rect x="183" y="62" width="14" height="12" rx="1" fill="{mix(c, "#FFFFFF", 0.7)}"></rect>')
    if paid:
        kw = ctx.anim("0%,40%{opacity:0}46%,100%{opacity:1}")
        house += f'<rect class="{kw}" x="183" y="62" width="14" height="12" rx="1" fill="{GOLD}"></rect>'
        kd = ctx.anim("0%,40%{transform:scaleX(1)}48%,100%{transform:scaleX(.35)}", box="fill-box", origin="0% 50%")
        house += f'<rect x="{door_x}" y="70" width="16" height="28" fill="#3B4A57"></rect><rect class="{kd}" x="{door_x}" y="70" width="16" height="28" fill="{mix(c, "#000000", 0.3)}"></rect>'
    else:
        house += f'<rect x="{door_x}" y="70" width="16" height="28" fill="{mix(c, "#000000", 0.3)}"></rect>'
    k = ctx.anim("0%{stroke-dashoffset:100}40%,100%{stroke-dashoffset:0}", extra="stroke-dasharray:100")
    out = (f'<path class="road" d="M12 {YG} H{door_x}" stroke="{GREY}"></path>'
           f'<path class="road {k}" d="M12 {YG} H{12 + (door_x - 12) * min(p, 1):.1f}" stroke="{c}" pathLength="100"></path>'
           + house + key + f'<text x="160" y="40" class="lbl lbl-end">{label}</text>')
    return out, ""


def sc_coins(ctx, c, p, label, over):
    full = 10
    n = max(1, round(p * full))
    out = (f'<path d="M86 28 H244" stroke="{c}" class="w15 dash"></path><text x="244" y="23" class="lbl lbl-end">{label}</text>'
           f'<path d="M70 98 H180" stroke="#C9D3CD" class="w3"></path>')
    for i in range(n):
        y = 91 - i * 7
        at = 3 + i * 3.4
        col, edge = (RED, "#8E2B26") if i >= full else (GOLD, GOLD_D)
        k = ctx.anim(f"0%,{at:.1f}%{{opacity:0;transform:translateY(-40px)}}{at + 1:.1f}%{{opacity:1}}{at + 3:.1f}%,100%{{opacity:1;transform:translateY(0)}}", timing="ease-in")
        out += f'<g class="{k}"><rect x="112" y="{y}" width="40" height="7" rx="3.5" fill="{col}" stroke="{edge}" class="w1"></rect></g>'
    done = p >= 1
    cal = (f'<rect x="22" y="46" width="44" height="50" rx="5" fill="#FFFFFF" stroke="{c}" class="w2"></rect>'
           f'<rect x="22" y="46" width="44" height="12" rx="5" fill="{c}"></rect><rect x="22" y="52" width="44" height="6" fill="{c}"></rect>'
           f'<text x="44" y="55.5" class="tiny" style="font-size: 7px">NOV</text>')
    if done:
        kt = ctx.anim("0%,40%{stroke-dashoffset:100}52%,100%{stroke-dashoffset:0}", extra="stroke-dasharray:100")
        cal += f'<path class="w3 {kt}" d="M33 76 L41 84 L56 68" stroke="{c}" pathLength="100"></path>'
    out = cal + out
    if p > 1:
        out += f'<text x="{XE}" y="114" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_plant(ctx, c, p, label, over):
    top = 76 - 56 * min(p, 1.1)
    k = ctx.anim("0%{stroke-dashoffset:100}40%,100%{stroke-dashoffset:0}", extra="stroke-dasharray:100")
    leaves = ""
    y = 68
    i = 0
    while y > top + 4:
        at = 4 + (76 - y) / (76 - top) * 36
        side = -1 if i % 2 == 0 else 1
        kl = ctx.anim(f"0%,{at:.1f}%{{transform:scale(0)}}{at + 5:.1f}%,100%{{transform:scale(1)}}", box="fill-box",
                      origin=("100% 100%" if side < 0 else "0% 100%"))
        if side < 0:
            d = f"M120 {y} Q106 {y} 102 {y - 12} Q116 {y - 14} 120 {y} Z"
        else:
            d = f"M120 {y} Q134 {y} 138 {y - 12} Q124 {y - 14} 120 {y} Z"
        leaves += f'<path class="{kl}" d="{d}" fill="{mix(c, "#FFFFFF", 0.15)}"></path>'
        y -= 11
        i += 1
    out = (f'<circle cx="34" cy="30" r="12" fill="{GOLD}" class="sh" style="fill-opacity: .55"></circle>'
           f'<path d="M90 20 H244" stroke="{c}" class="w15 dash"></path><text x="244" y="15" class="lbl lbl-end">{label}</text>'
           f'<path class="w3 {k}" d="M120 78 V{top:.1f}" stroke="{mix(c, "#000000", 0.2)}" pathLength="100"></path>' + leaves +
           f'<path d="M100 76 H140 L135 98 H105 Z" fill="#C9774A"></path><rect x="97" y="73" width="46" height="6" rx="2" fill="#B5643A"></rect>')
    if p > 1:
        out += f'<text x="{XE}" y="114" class="lbl-good {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_atm(ctx, c, p, label, over):
    full = 8
    n = max(1, round(p * full))
    out = (f'<rect x="16" y="18" width="56" height="80" rx="6" fill="{c}"></rect>'
           f'<rect x="22" y="24" width="44" height="22" rx="3" fill="#7FD3AE"></rect>'
           + "".join(f'<rect x="{25 + (i % 3) * 13}" y="{52 + (i // 3) * 7}" width="9" height="4.5" rx="1" fill="{mix(c, "#FFFFFF", 0.6)}"></rect>' for i in range(6))
           + f'<rect x="58" y="70" width="16" height="4" rx="1" fill="{TYRE}"></rect>'
           f'<path d="M140 {98 - 8 * 4.2:.1f} H220" stroke="{c}" class="w15 dash"></path>'
           f'<text x="220" y="{98 - 8 * 4.2 - 5:.1f}" class="lbl lbl-end">{label}</text>'
           f'<path d="M120 98 H200" stroke="#C9D3CD" class="w3"></path>')
    for i in range(n):
        at = 4 + i * 4.2
        ex, ey = 142 + (i % 2) * 3, 92 - i * 4.2
        sx, sy = 66, 70
        k = ctx.anim(f"0%,{at:.1f}%{{opacity:0;transform:translate({sx}px,{sy}px) rotate(-12deg)}}{at + 1:.1f}%{{opacity:1}}"
                     f"{at + 5:.1f}%,100%{{opacity:1;transform:translate({ex}px,{ey:.1f}px) rotate(0deg)}}", timing="ease-out", box="view-box", origin="0 0")
        col = "#F6D3D0" if i >= full else "#A8D5B9"
        out += (f'<g class="{k}"><rect x="0" y="0" width="34" height="5" rx="1" fill="{col}" stroke="#4F8A6B" class="w1"></rect></g>')
    if p > 1:
        out += f'<text x="{XE}" y="16" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_transfer(ctx, c, p, label, over):
    D = mix(c, "#000000", 0.3)
    k = ctx.anim("0%,6%{opacity:0;transform:translate(60px,58px)}12%{opacity:1}36%{transform:translate(124px,26px)}"
                 "48%{opacity:1;transform:translate(182px,58px)}54%,100%{opacity:0;transform:translate(182px,58px)}",
                 timing="ease-in-out", box="view-box", origin="0 0")
    kb = ctx.anim("0%,48%{transform:scale(1)}52%{transform:scale(1.06)}58%,100%{transform:scale(1)}", box="fill-box", origin="center")
    out = (f'<rect x="14" y="42" width="78" height="48" rx="8" fill="{D}"></rect><rect x="24" y="54" width="14" height="10" rx="2" fill="{GOLD}"></rect>'
           f'<text x="24" y="82" class="tiny" style="text-anchor: start; font-size: 7px">HDFC ••4821</text>'
           f'<g class="{kb}"><rect x="156" y="42" width="78" height="48" rx="8" fill="{c}"></rect><rect x="166" y="54" width="14" height="10" rx="2" fill="{GOLD}"></rect>'
           f'<text x="166" y="82" class="tiny" style="text-anchor: start; font-size: 7px">SBI ••3307</text></g>'
           f'<path d="M92 60 Q124 18 156 60" stroke="#9AA6A0" class="w2 dots"></path>'
           f'<g class="{k}"><circle cx="0" cy="0" r="8" fill="{GOLD}" stroke="{GOLD_D}" class="w15"></circle>'
           f'<text x="0" y="3" class="tiny" style="font-size: 9px; fill: #6B4A12">₹</text></g>'
           f'<text x="124" y="110" class="lbl lbl-mid">{label}</text>')
    return out, ""


def sc_jar(ctx, c, p, label, over):
    full = 52
    h = min(full * p, 60)
    kf = ctx.anim("0%{transform:scaleY(0)}40%,100%{transform:scaleY(1)}", box="fill-box", origin="50% 100%")
    coins = ""
    for i in range(3):
        kc = ctx.anim(f"0%,{4 + i * 8}%{{opacity:0;transform:translateY(-30px)}}{6 + i * 8}%{{opacity:1}}{14 + i * 8}%{{opacity:1;transform:translateY(30px)}}{16 + i * 8}%,100%{{opacity:0;transform:translateY(30px)}}",
                      timing="ease-in")
        coins += f'<g class="{kc}"><rect x="{116 + (i - 1) * 8}" y="14" width="14" height="5" rx="2.5" fill="{GOLD}" stroke="{GOLD_D}" class="w1"></rect></g>'
    out = (coins + f'<rect class="{kf}" x="102" y="{96 - h:.1f}" width="48" height="{h:.1f}" rx="4" fill="{mix(c, "#FFFFFF", 0.35)}"></rect>'
           f'<rect x="98" y="34" width="56" height="64" rx="10" fill="none" stroke="#9AA6A0" class="w2"></rect>'
           f'<rect x="102" y="26" width="48" height="9" rx="3" fill="{c}"></rect>'
           f'<path d="M106 44 V86" stroke="#FFFFFF" class="w2"></path>'
           f'<path d="M86 44 H244" stroke="{c}" class="w15 dash"></path><text x="244" y="39" class="lbl lbl-end">{label}</text>')
    if p > 1:
        out += f'<text x="{XE}" y="114" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


def sc_piggy(ctx, c, p, label, over):
    coins = ""
    for i in range(3):
        kc = ctx.anim(f"0%,{4 + i * 8}%{{opacity:0;transform:translateY(-26px)}}{6 + i * 8}%{{opacity:1}}{14 + i * 8}%{{opacity:1;transform:translateY(14px)}}{16 + i * 8}%,100%{{opacity:0;transform:translateY(14px)}}",
                      timing="ease-in")
        coins += f'<g class="{kc}"><circle cx="108" cy="22" r="6" fill="{GOLD}" stroke="{GOLD_D}" class="w1"></circle></g>'
    pig = ('<ellipse cx="104" cy="72" rx="38" ry="25" fill="#F4A9BA"></ellipse>'
           '<ellipse cx="142" cy="70" rx="7" ry="9" fill="#EE93A8"></ellipse><circle cx="141" cy="67" r="1.4" fill="#B5576C"></circle><circle cx="141" cy="73" r="1.4" fill="#B5576C"></circle>'
           '<path d="M118 50 L126 40 L130 54 Z" fill="#EE93A8"></path><circle cx="126" cy="62" r="2.2" fill="#3B2B30"></circle>'
           '<path d="M80 92 V100 M94 95 V100 M114 95 V100 M128 92 V100" stroke="#EE93A8" class="w6"></path>'
           '<path d="M66 66 Q58 60 62 54" stroke="#EE93A8" class="w2"></path>'
           '<rect x="98" y="47" width="20" height="4" rx="2" fill="#B5576C"></rect>')
    level = 96 - 60 * min(p, 1.25) / 1.25
    line_y = 96 - 60 / 1.25
    k = ctx.anim("0%{transform:scaleY(0)}40%,100%{transform:scaleY(1)}", box="fill-box", origin="50% 100%")
    meter = (f'<rect x="196" y="30" width="14" height="66" rx="7" fill="{GREY}"></rect>'
             f'<rect class="{k}" x="196" y="{level:.1f}" width="14" height="{96 - level:.1f}" rx="7" fill="{c}"></rect>'
             f'<path d="M186 {line_y:.1f} H220" stroke="{TYRE}" class="w15 dash"></path>'
             f'<text x="182" y="{line_y + 3:.1f}" class="lbl lbl-end">{label}</text>')
    out = coins + pig + meter
    if p > 1:
        out += f'<text x="{XE}" y="16" class="lbl-good {ctx.appear(44)}">{over}</text>'
    return out, ""


# ---------------------------------------------------------------- the 24 gallery entries
# (key, category, title, kind, spent, budget, caption, extra label)
ENTRIES = [
    ("car", "transport", "Transport · car", "car", 6480, 8000, "Uber or Ola trips: a car drives 81% of the way to the budget flag."),
    ("scooter", "transport", "Transport · scooter", "scooter", 5120, 8000, "Rapido and bike taxis: a scooter with its rider."),
    ("bus", "transport", "Transport · bus", "bus", 3600, 8000, "BMTC and city buses: a bus heads for the bus stop."),
    ("metro", "transport", "Transport · metro", "metro", 7360, 8000, "Namma Metro: a train glides along the viaduct to the platform."),
    ("flight", "travel", "Travel · flight", "flight", 4200, 6000, "Flights: the plane flies towards the destination pin."),
    ("train", "travel", "Travel · train", "train", 3300, 6000, "IRCTC: a train pulls in towards the station board."),
    ("coach", "travel", "Travel · coach", "coach", 6480, 6000, "redBus and coaches. Over budget: it passes the milestone and the road turns red."),
    ("fuel", "fuel", "Fuel", "fuel", 4350, 5000, "The pump meter counts up to the amount; the gauge needle stops at 87%."),
    ("food", "food", "Food & Dining", "chai", 11240, 12000, "A glass of chai fills up to the budget line."),
    ("groceries", "groceries", "Groceries", "cart", 6600, 10000, "The cart fills up on its way to the checkout counter."),
    ("shopping", "shopping", "Shopping", "receipt", 6120, 5000, "The bill prints out; past the cut line it turns red."),
    ("bills", "bills", "Bills & Utilities", "bulbs", 4800, 6000, "One bulb lights up for each tenth of the budget used."),
    ("recharge", "recharge", "Recharge & Subscriptions", "battery", 600, 1000, "The battery charges up towards a full budget."),
    ("entertainment", "entertainment", "Entertainment", "popcorn", 1900, 2000, "Popcorn rises out of the bucket to the budget line."),
    ("sports", "sports", "Sports & Fitness", "sports", 1600, 2500, "The batter hits it 64% of the way to the boundary rope."),
    ("health", "health", "Health", "runner", 2140, 3000, "The runner jogs 71% of the way to the finish tape."),
    ("education", "education", "Education", "books", 2000, 4000, "Books stack up towards the budget line."),
    ("rent", "rent", "Rent", "key", 22000, 22000, "The key reaches the door when this month's rent is paid."),
    ("emi", "emi", "EMI & Loans", "coins", 14700, 14700, "Coins stack up to this month's EMI; the calendar gets a tick."),
    ("investments", "investments", "Investments", "plant", 8500, 10000, "The plant grows towards your saving goal for the month."),
    ("cash", "cash", "Cash", "atm", 2000, 5000, "The ATM hands out a note for each eighth of the budget."),
    ("transfers", "transfers", "Transfers", "transfer", 15000, 0, "Money moving between your own accounts. Not counted as spending."),
    ("others", "others", "Others", "jar", 900, 3000, "Coins fill the jar towards the budget line."),
    ("income", "income", "Income", "piggy", 71500, 65000, "Coins drop into the piggy bank; the bar compares with last month."),
]


def build_scene(ctx, kind, cat, spent, budget):
    c = CATS[cat][1]
    p = spent / budget if budget else 1
    label = f"Budget {inr(budget)}"
    over = over_text(spent, budget) if budget else ""
    if kind in ("car", "scooter", "bus", "metro", "train", "coach"):
        return sc_vehicle(ctx, c, p, kind, label, over)
    if kind == "flight":
        return sc_flight(ctx, c, p, label, over)
    if kind == "fuel":
        return sc_fuel(ctx, c, p, f"{round(p * 100)}%", spent)
    if kind == "runner":
        return sc_runner(ctx, c, p, label, over)
    if kind == "sports":
        return sc_sports(ctx, c, p, label, over)
    if kind == "chai":
        return sc_chai(ctx, c, p, label, over)
    if kind == "cart":
        return sc_cart(ctx, c, p, label, over)
    if kind == "receipt":
        return sc_receipt(ctx, c, p, label, over)
    if kind == "bulbs":
        return sc_bulbs(ctx, c, p, label, over)
    if kind == "battery":
        return sc_battery(ctx, c, p, label, over)
    if kind == "popcorn":
        return sc_popcorn(ctx, c, p, label, over)
    if kind == "books":
        return sc_books(ctx, c, p, label, over)
    if kind == "key":
        return sc_key(ctx, c, p, "Rent " + inr(budget) if p >= 1 else label, over)
    if kind == "coins":
        return sc_coins(ctx, c, p, "EMI " + inr(budget), over)
    if kind == "plant":
        return sc_plant(ctx, c, p, "Goal " + inr(budget), f"{inr(spent - budget)} more than the goal")
    if kind == "atm":
        return sc_atm(ctx, c, p, label, over)
    if kind == "transfer":
        return sc_transfer(ctx, c, p, f"{inr(spent)} moved this month", "")
    if kind == "jar":
        return sc_jar(ctx, c, p, label, over)
    if kind == "piggy":
        return sc_piggy(ctx, c, p, "Last month " + inr(budget), f"{round((p - 1) * 100)}% more than last month")
    raise ValueError(kind)


def sub_line(cat, spent, budget, kind):
    if not budget:
        return "Not counted as spending", False
    p = spent / budget
    if cat == "income":
        return f"{inr(spent)} in · last month {inr(budget)}", False
    if cat == "investments":
        return f"{inr(spent)} of {inr(budget)} goal · {round(p * 100)}%", False
    if p > 1:
        return f"of {inr(budget)} budget · {inr(spent - budget)} over", True
    if p == 1 and cat in ("rent", "emi"):
        return "Paid for this month", False
    return f"of {inr(budget)} budget · {round(p * 100)}% used", False


def scene_box(svg, overlay, scale=1.0):
    inner = (f'<div style="position: relative; width: 248px; height: 120px">'
             f'<svg viewBox="0 0 248 120" width="248" height="120" aria-hidden="true">{svg}</svg>{overlay}</div>')
    if scale == 1.0:
        return inner
    return (f'<div style="width: {248 * scale:.0f}px; height: {120 * scale:.0f}px; overflow: hidden">'
            f'<div style="width: 248px; height: 120px; transform: scale({scale}); transform-origin: 0 0">{inner}</div></div>')


def page(title, body, script, preview, css):
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


STATIC = "class Component extends DCLogic {\n  renderVals() {\n    return {};\n  }\n}"


def gallery():
    css, cards = [BASE_CSS], []
    for i, (key, cat, title, kind, spent, budget, caption) in enumerate(ENTRIES):
        ctx = Ctx(f"g{i}", True)
        svg, overlay = build_scene(ctx, kind, cat, spent, budget)
        css += ctx.css
        c = CATS[cat][1]
        sub, red = sub_line(cat, spent, budget, kind)
        cards.append(f"""<div style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 24px; padding: 16px 15px; display: flex; flex-direction: column; gap: 10px">
<div style="display: flex; align-items: center; gap: 10px">
<span style="width: 12px; height: 12px; border-radius: 6px; flex: none; background: {c}"></span>
<span style="flex: 1; min-width: 0; font-size: 15px; font-weight: 500">{title}</span>
<span style="font-size: 17px; font-weight: 700">{inr(spent)}</span>
</div>
<span style="font-size: 13px; color: {"#B3261E" if red else MUTED}; margin-top: -6px; padding-left: 22px">{sub}</span>
<div style="border-radius: 14px; overflow: hidden; background: {mix(c, '#FFFFFF', 0.92)}">{scene_box(svg, overlay)}</div>
<p style="margin: 0; font-size: 13px; color: {MUTED}; line-height: 1.4">{caption}</p>
</div>""")
    body = f"""<div style="width: 1280px; height: 1840px; box-sizing: border-box; padding: 40px 48px; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif; display: flex; flex-direction: column; gap: 22px">
<div style="display: flex; flex-direction: column; gap: 6px">
<h1 style="margin: 0; font-size: 30px; font-weight: 700">Every category, as a scene</h1>
<p style="margin: 0; font-size: 16px; color: {MUTED}">In the app each plays once when you open a category, and again when you tap it. They loop here. Transport and Travel show the vehicle you spent most on.</p>
</div>
<div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 20px">
{"".join(cards)}
</div>
</div>"""
    return page("Option B · every category", body, STATIC, (1280, 1840), "\n".join(css))


# ---------------------------------------------------------------- the phone (Option B, plays once like the app)
PHONE = [  # id, category, kind, spent, budget, merchants
    ("food", "food", "chai", 11240, 12000, [["Swiggy", 4820], ["Zomato", 3560], ["Domino's", 1640], ["Café Coffee Day", 1220]]),
    ("groceries", "groceries", "cart", 8460, 10000, [["Blinkit", 3210], ["BigBasket", 2980], ["Zepto", 1470], ["More Supermarket", 800]]),
    ("transport", "transport", "car", 6480, 8000, [["Uber", 2140], ["Rapido", 1860], ["Namma Metro", 1420], ["BMTC", 1060]]),
    ("shopping", "shopping", "receipt", 6120, 5000, [["Amazon", 2890], ["Myntra", 1740], ["Flipkart", 990], ["Nykaa", 500]]),
    ("bills", "bills", "bulbs", 5300, 6000, [["BESCOM", 2450], ["Airtel Broadband", 1180], ["BWSSB", 920], ["Indane Gas", 750]]),
    ("travel", "travel", "train", 4530, 6000, [["IRCTC", 2310], ["IndiGo", 1620], ["redBus", 600]]),
    ("fuel", "fuel", "fuel", 4350, 5000, [["Shree Ganesh HPCL", 2100], ["IndianOil Koramangala", 1450], ["Shell HSR Layout", 800]]),
    ("health", "health", "runner", 2140, 3000, [["Apollo Pharmacy", 1260], ["Practo", 520], ["MedPlus", 360]]),
    ("entertainment", "entertainment", "popcorn", 1890, 2000, [["BookMyShow", 1190], ["PVR INOX", 700]]),
    ("sports", "sports", "sports", 1600, 2500, [["cult.fit", 999], ["Playo", 601]]),
]


def phone():
    css = [BASE_CSS, ".ring-in{animation:ringin .6s cubic-bezier(.2,.8,.2,1) both}@keyframes ringin{from{transform:rotate(-40deg) scale(.9);opacity:0}to{transform:none;opacity:1}}",
           ".rise{animation:rise .45s cubic-bezier(.2,.8,.2,1) both}@keyframes rise{from{transform:translateY(16px);opacity:0}to{transform:none;opacity:1}}"]
    scenes = ""
    for i, (cid, cat, kind, spent, budget, _) in enumerate(PHONE):
        ctx = Ctx(f"p{i}", False)
        svg, overlay = build_scene(ctx, kind, cat, spent, budget)
        css += ctx.css
        scenes += f'<sc-if value="{{{{show.{cid}}}}}" hint-placeholder-val="{{{{false}}}}">{scene_box(svg, overlay, 326 / 248)}</sc-if>'
    data = []
    for cid, cat, kind, spent, budget, merchants in PHONE:
        col = CATS[cat][1]
        sub, red = sub_line(cat, spent, budget, kind)
        data.append({"id": cid, "name": CATS[cat][0], "color": col, "spent": spent, "budget": budget, "sub": sub, "red": red,
                     "tint": mix(col, "#FFFFFF", 0.92), "merchants": [{"name": n, "paise": a, "tint": mix(col, "#FFFFFF", 0.82), "ink": mix(col, "#000000", 0.3)} for n, a in merchants]})
    body = f"""<div style="width: 390px; height: 844px; position: relative; overflow: hidden; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif">
<div style="height: 844px; overflow-y: auto; box-sizing: border-box; padding: 20px 16px 120px; display: flex; flex-direction: column; gap: 14px">
<sc-if value="{{{{isRoot}}}}" hint-placeholder-val="{{{{true}}}}">
<div style="display: flex; flex-direction: column; gap: 2px; padding: 4px 4px 0">
<span style="font-size: 13px; color: {MUTED}">Budget month · day 22 of 30</span>
<h1 style="margin: 0; font-size: 26px; font-weight: 700">November 2026</h1>
</div>
<section style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 26px; padding: 16px; display: flex; flex-direction: column; gap: 10px">
<div style="display: flex; align-items: flex-start; gap: 12px">
<div style="flex: 1; display: flex; flex-direction: column; gap: 2px">
<h2 style="margin: 0; font-size: 20px; font-weight: 700">Where it went</h2>
<span style="font-size: 14px; color: {MUTED}">Tap a category to open it</span>
</div>
<a href="#" style="font-size: 15px; font-weight: 500; padding: 4px 0; text-decoration: none">See all</a>
</div>
<div style="display: flex; justify-content: center; padding: 6px 0">
<div class="ring-in" style="width: 200px; height: 200px; border-radius: 100px; display: flex; align-items: center; justify-content: center; background: {{{{ring}}}}">
<div style="width: 152px; height: 152px; border-radius: 76px; background: #FFFFFF; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px">
<span style="font-size: 13px; color: {MUTED}">Total</span>
<span style="font-size: 26px; font-weight: 700">{{{{total}}}}</span>
<span style="font-size: 13px; color: {MUTED}">10 categories</span>
</div>
</div>
</div>
<div style="display: flex; flex-direction: column">
<sc-for list="{{{{cats}}}}" as="row" hint-placeholder-count="10">
<button type="button" onClick="{{{{row.open}}}}" style="display: flex; align-items: center; gap: 12px; width: 100%; min-height: 54px; padding: 4px; border: 0; border-top: 1px solid {LINE}; background: transparent; font: inherit; color: inherit; text-align: left; cursor: pointer">
<span style="width: 12px; height: 12px; border-radius: 6px; flex: none; background: {{{{row.color}}}}"></span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column"><span style="font-size: 16px; font-weight: 500">{{{{row.name}}}}</span><span style="font-size: 13px; color: {{{{row.subColor}}}}">{{{{row.sub}}}}</span></span>
<span style="font-size: 16px; font-weight: 700">{{{{row.amount}}}}</span>
<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path class="w2" d="M9 6 L15 12 L9 18" stroke="{MUTED}"></path></svg>
</button>
</sc-for>
</div>
</section>
</sc-if>
<sc-if value="{{{{isOpen}}}}" hint-placeholder-val="{{{{false}}}}">
<button type="button" onClick="{{{{back}}}}" style="align-self: flex-start; display: flex; align-items: center; gap: 4px; min-height: 44px; padding: 0 14px 0 6px; border: 0; border-radius: 22px; background: #E7F2EC; color: #0B4D38; font: inherit; font-size: 15px; font-weight: 500; cursor: pointer">
<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path class="w2" d="M15 6 L9 12 L15 18" stroke="#0B4D38"></path></svg>Where it went</button>
<div class="rise" style="display: flex; flex-direction: column; gap: 2px; padding: 0 4px">
<h1 style="margin: 0; font-size: 26px; font-weight: 700">{{{{cur.name}}}}</h1>
<span style="font-size: 14px; color: {MUTED}">November 2026</span>
</div>
<section style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 26px; padding: 16px; display: flex; flex-direction: column; gap: 10px">
<div style="display: flex; align-items: baseline; gap: 8px">
<span style="font-size: 30px; font-weight: 700">{{{{cur.amount}}}}</span>
<span style="font-size: 14px; color: {{{{cur.subColor}}}}">{{{{cur.sub}}}}</span>
</div>
<button type="button" onClick="{{{{replay}}}}" aria-label="Play the animation again" style="padding: 0; border: 0; border-radius: 16px; overflow: hidden; cursor: pointer; background: {{{{cur.tint}}}}; width: 326px; height: 158px">
<sc-if value="{{{{showScene}}}}" hint-placeholder-val="{{{{true}}}}">
{scenes}
</sc-if>
</button>
<span style="font-size: 12px; color: {MUTED}; text-align: center">Tap the picture to play it again</span>
</section>
<section style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 26px; padding: 6px 16px; display: flex; flex-direction: column">
<sc-for list="{{{{cur.merchants}}}}" as="m" hint-placeholder-count="4">
<div style="display: flex; align-items: center; gap: 12px; min-height: 56px; padding: 4px 0; border-bottom: 1px solid {LINE}">
<span style="width: 38px; height: 38px; border-radius: 12px; flex: none; display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 700; background: {{{{m.tint}}}}; color: {{{{m.ink}}}}">{{{{m.initial}}}}</span>
<span style="flex: 1; min-width: 0; display: flex; flex-direction: column"><span style="font-size: 16px; font-weight: 500">{{{{m.name}}}}</span><span style="font-size: 13px; color: {MUTED}">{{{{m.pct}}}}</span></span>
<span style="font-size: 16px; font-weight: 700">{{{{m.amount}}}}</span>
</div>
</sc-for>
</section>
</sc-if>
</div>
{tab_bar("Home")}
</div>"""
    script = """class Component extends DCLogic {
  constructor(props) {
    super(props);
    this.state = { open: null, hide: false };
  }
  renderVals() {
    const DATA = """ + json.dumps(data, ensure_ascii=False) + """;
    const fmt = (n) => '\\u20b9' + n.toLocaleString('en-IN');
    const total = DATA.reduce((a, d) => a + d.spent, 0);
    let at = 0;
    const stops = [];
    DATA.forEach((d) => {
      const end = at + (d.spent / total) * 360;
      const cut = Math.max(at, end - 1.6);
      stops.push(d.color + ' ' + at.toFixed(2) + 'deg ' + cut.toFixed(2) + 'deg');
      stops.push('#FFFFFF ' + cut.toFixed(2) + 'deg ' + end.toFixed(2) + 'deg');
      at = end;
    });
    const open = this.state.open;
    const cats = DATA.map((d) => ({
      name: d.name, color: d.color, amount: fmt(d.spent),
      sub: d.sub, subColor: d.red ? '#B3261E' : '#46524C',
      open: () => this.setState({ open: d.id, hide: false }),
    }));
    const show = {};
    DATA.forEach((d) => { show[d.id] = open === d.id; });
    let cur = { name: '', amount: '', sub: '', subColor: '#46524C', tint: '#FFFFFF', merchants: [] };
    if (open) {
      const d = DATA.find((x) => x.id === open);
      cur = {
        name: d.name, amount: fmt(d.spent), sub: d.sub, subColor: d.red ? '#B3261E' : '#46524C', tint: d.tint,
        merchants: d.merchants.map((m) => ({
          name: m.name, amount: fmt(m.paise), pct: Math.round((m.paise / d.spent) * 100) + '% of ' + d.name,
          initial: m.name.charAt(0), tint: m.tint, ink: m.ink,
        })),
      };
    }
    return {
      isRoot: !open, isOpen: !!open, total: fmt(total),
      ring: 'conic-gradient(' + stops.join(', ') + ')',
      cats, cur, show, showScene: !this.state.hide,
      back: () => this.setState({ open: null }),
      replay: () => {
        this.setState({ hide: true });
        setTimeout(() => this.setState({ hide: false }), 40);
      },
    };
  }
}"""
    return page("Option B · working phone", body, script, (390, 844), "\n".join(css))


def canvas(base):
    idx = json.loads(base)
    idx["pages"] = [{"id": "r1", "name": "Round 1"}, {"id": "r2", "name": "Round 2 · Option B"}]
    for b in idx["boards"].values():
        b.setdefault("page", "r1")
    for n in idx["notes"].values():
        n.setdefault("page", "r1")
    idx["boards"]["BPhone.dc.html"] = {"x": 0, "y": 0, "w": 390, "h": 844, "title": "Option B · working phone (tap a category)",
                                       "is_interactive": True, "radius": 36, "page": "r2"}
    idx["boards"]["BGallery.dc.html"] = {"x": 470, "y": 0, "w": 1280, "h": 1840, "title": "Option B · every category", "page": "r2"}
    idx["order"] += ["BPhone.dc.html", "BGallery.dc.html"]
    idx["notes"]["optionB"] = {"x": 0, "y": -300, "text": "Round 2 · Option B, with people and vehicles", "kind": "title1", "maxW": 1750, "page": "r2"}
    idx["launch"] = {"view": "canvas", "page": "r2"}
    return json.dumps(idx, indent=1, ensure_ascii=False)


if __name__ == "__main__":
    which = sys.argv[1]
    if which == "canvas":
        out = ("canvas.json", canvas(open(sys.argv[2]).read()))
    elif which == "BPhone":
        out = ("BPhone.dc.html", phone())
    else:
        out = ("BGallery.dc.html", gallery())
    with open(ROOT + out[0], "w") as f:
        f.write(out[1])
    print("wrote", out[0], len(out[1]))
