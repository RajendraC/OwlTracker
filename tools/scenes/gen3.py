"""Round 3: multi-mode Transport and Travel, several sports, gym and meditation, a thali, a shopping cart,
every utility in turn. python3 gen3.py <B3Phone|B3Gallery|canvas base.json>"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen import BG, CATS, INK, LINE, MUTED, ROOT, mix, tab_bar  # noqa: E402
from gen2 import (BASE_CSS, GOLD, GOLD_D, GREY, HAIR, NAVY, RED, SK, SKD, STATIC, TYRE, Ctx, bus, car, coach,  # noqa: E402
                  inr, metro, page, plane, runner, sc_atm, sc_battery, sc_books, sc_cart, sc_coins, sc_fuel, sc_jar,
                  sc_key, sc_piggy, sc_plant, sc_popcorn, sc_transfer, scene_box, scooter, sub_line, track, train)

D6 = 6.5
EXTRA_CSS = """.w16,.w25,.w9{fill:none;stroke-linecap:round;stroke-linejoin:round}.w16{stroke-width:16}.w25{stroke-width:2.5}.w9{stroke-width:9}
.chip{font:700 11px Roboto, system-ui, sans-serif;fill:#16211C}
"""


# ---------------------------------------------------------------- act machinery
def windows(n, end):
    step = end / n
    return [(i * step, (i + 1) * step) for i in range(n)]


def act_cls(ctx, i, n, s, e, scale=True):
    if n == 1:
        return ""
    fd = 2.5
    a0, a1 = ("transform:scale(.86);", "transform:scale(1);") if scale else ("", "")
    if i == 0:
        fr = f"0%,{e - fd:.2f}%{{opacity:1;{a1}}}{e:.2f}%,100%{{opacity:0;{a0}}}"
    elif i == n - 1:
        fr = f"0%,{s:.2f}%{{opacity:0;{a0}}}{s + fd:.2f}%,100%{{opacity:1;{a1}}}"
    else:
        fr = f"0%,{s:.2f}%{{opacity:0;{a0}}}{s + fd:.2f}%,{e - fd:.2f}%{{opacity:1;{a1}}}{e:.2f}%,100%{{opacity:0;{a0}}}"
    return ctx.anim(fr, timing="ease-in-out", box=("fill-box" if scale else None), origin=("center" if scale else None))


def chip(text, cls):
    w = 6.4 * len(text) + 20
    return (f'<g class="{cls}"><rect x="10" y="8" width="{w:.0f}" height="19" rx="9.5" fill="#FFFFFF" stroke="{GREY}" class="w1"></rect>'
            f'<text x="20" y="21.2" class="chip">{text}</text></g>')


def win(ctx, s, e, local, timing="ease-in-out", box=None, origin=None, extra=""):
    """Keyframes given at points u in [0, 1] of the act window [s, e] (percent of the whole timeline)."""
    fr = [f"0%,{s:.2f}%{{{local[0][1]}}}"]
    for u, css in local[1:-1]:
        fr.append(f"{s + (e - s) * u:.2f}%{{{css}}}")
    fr.append(f"{s + (e - s) * local[-1][0]:.2f}%,100%{{{local[-1][1]}}}")
    return ctx.anim("".join(fr), timing=timing, box=box, origin=origin, extra=extra)


def cycles(ctx, frames, cyc, s, n_once, box=None, origin=None, timing="ease-in-out"):
    """A repeating motion that starts when its act starts."""
    return ctx.anim(frames, dur=cyc, timing=timing, delay=round(s / 100 * ctx.dur, 2),
                    count=("infinite" if ctx.loop else str(n_once)), box=box, origin=origin)


def progress_bottom(ctx, c, p, label, over, end=88):
    y, x0, xb = 111, 12, 220
    xp = x0 + (xb - x0) * min(p, 1)
    t1 = end if p <= 1 else end / p
    k = ctx.anim(f"0%{{stroke-dashoffset:100}}{t1:.1f}%,100%{{stroke-dashoffset:0}}", timing="linear", extra="stroke-dasharray:100")
    out = (f'<path class="road" d="M{x0} {y} H236" stroke="{GREY}" style="stroke-width: 5"></path>'
           f'<path class="road {k}" d="M{x0} {y} H{xp:.1f}" stroke="{c}" pathLength="100" style="stroke-width: 5"></path>')
    if p > 1:
        xo = min(x0 + (xb - x0) * p, 236)
        k2 = ctx.anim(f"0%,{t1:.1f}%{{stroke-dashoffset:100}}{end}%,100%{{stroke-dashoffset:0}}", timing="linear", extra="stroke-dasharray:100")
        out += f'<path class="road {k2}" d="M{xb} {y} H{xo:.1f}" stroke="{RED}" pathLength="100" style="stroke-width: 5"></path>'
        out += f'<text x="236" y="21" class="lbl-red {ctx.appear(end)}">{over}</text>'
    out += (f'<path d="M{xb} {y} V{y - 15}" stroke="#46524C" class="w15"></path>'
            f'<path d="M{xb} {y - 15} L{xb + 11} {y - 12} L{xb} {y - 9} Z" fill="{c}"></path>'
            f'<text x="{xb - 6}" y="{y - 4}" class="lbl lbl-end">{label}</text>')
    return out


def ground():
    return f'<path d="M12 96 H236" stroke="#E3E9E5" class="w2"></path>'


# ---------------------------------------------------------------- Transport and Travel: one trip, every mode
VEH = {"car": (car, 0.62), "scooter": (scooter, 0.62), "bus": (bus, 0.5), "metro": (metro, 0.48), "train": (train, 0.4), "coach": (coach, 0.5)}


def placed(kind, c, W):
    fn, s = VEH[kind]
    body, front, gnd = fn(c, W)
    return f'<g transform="translate({-front * s:.1f} {-gnd * s:.1f}) scale({s})">{body}</g>'


def puffs(ctx, times):
    fr = ["0%{opacity:0;transform:scale(.4)}"]
    for t in times:
        fr.append(f"{t - 1.5:.1f}%{{opacity:0;transform:scale(.4)}}{t + 1:.1f}%{{opacity:.95;transform:scale(1)}}{t + 5:.1f}%{{opacity:0;transform:scale(1.5)}}")
    fr.append("100%{opacity:0;transform:scale(1.5)}")
    k = ctx.anim("".join(fr), timing="ease-out", box="fill-box", origin="center")
    return (f'<g class="{k}"><circle cx="-34" cy="-10" r="7" fill="#FFFFFF" stroke="{GREY}" class="w1"></circle>'
            f'<circle cx="-22" cy="-17" r="8" fill="#FFFFFF" stroke="{GREY}" class="w1"></circle>'
            f'<circle cx="-12" cy="-9" r="6" fill="#FFFFFF" stroke="{GREY}" class="w1"></circle></g>')


def sc_transport(ctx, c, p, label, over, modes):
    """modes: [(kind, chip text)]; the vehicle changes as it travels, ending on the last one."""
    end = 84
    n = len(modes)
    wins = windows(n, end)
    W = ctx.anim(f"0%{{transform:rotate(0)}}{end}%,100%{{transform:rotate(2520deg)}}", timing="cubic-bezier(.45,.05,.4,1)",
                 box="fill-box", origin="center")
    # start with every vehicle fully on screen (the bus is about 50 px long)
    start = 72
    target = max(min(12 + 208 * p, 236), start + 14)
    mv = ctx.anim(f"0%{{transform:translate({start}px,95px)}}{end}%{{transform:translate({target + 2:.1f}px,95px)}}"
                  f"{end + 6}%,100%{{transform:translate({target:.1f}px,95px)}}", timing="cubic-bezier(.45,.05,.4,1)")
    vehicles, chips = "", ""
    for i, (kind, text) in enumerate(modes):
        s, e = wins[i]
        vehicles += f'<g class="{act_cls(ctx, i, n, s, e)}">{placed(kind, c, W)}</g>'
        chips += chip(text, act_cls(ctx, i, n, s, e, scale=False))
    mover = f'<g class="{mv}">{vehicles}{puffs(ctx, [w[0] for w in wins[1:]])}</g>'
    return track(ctx, c, p, label, "flag", t=end, over_text=over) + mover + chips, ""


def sc_travel(ctx, c, p, label, over, chips_text):
    end = 84
    xs, xt = 18, min(12 + 208 * p, 236)
    xa, xb = xs + (xt - xs) / 3, xs + 2 * (xt - xs) / 3
    W = ctx.anim(f"0%,32%{{transform:rotate(0)}}{end}%,100%{{transform:rotate(1800deg)}}", timing="linear", box="fill-box", origin="center")
    # flight: an arc from the origin pin, landing at xa
    h = min(44, 0.75 * (xa - xs))
    p0, cc, p2 = (xs, 90), ((xs + xa) / 2, 90 - 2 * h), (xa, 90)
    fr = []
    for i in range(9):
        t = i / 8
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * cc[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * cc[1] + t * t * p2[1]
        dx = 2 * (1 - t) * (cc[0] - p0[0]) + 2 * t * (p2[0] - cc[0])
        dy = 2 * (1 - t) * (cc[1] - p0[1]) + 2 * t * (p2[1] - cc[1])
        a = math.degrees(math.atan2(dy, dx)) * (0.55 if i == 8 else 1)
        fr.append(f"{2 + t * 26:.1f}%{{opacity:1;transform:translate({x:.1f}px,{y:.1f}px) rotate({a:.1f}deg)}}")
    fr.insert(0, f"0%,2%{{opacity:1;transform:translate({xs}px,90px) rotate(-30deg)}}")
    fr.append(f"30%{{opacity:1;transform:translate({xa:.1f}px,90px) rotate(0deg)}}32%,100%{{opacity:0;transform:translate({xa:.1f}px,90px) rotate(0deg)}}")
    kp = ctx.anim("".join(fr), timing="linear", box="view-box", origin="0 0")
    kt = ctx.anim(f"0%,30%{{opacity:0;transform:translate({xa:.1f}px,95px)}}33%{{opacity:1;transform:translate({xa:.1f}px,95px)}}"
                  f"57%{{opacity:1;transform:translate({xb:.1f}px,95px)}}60%,100%{{opacity:0;transform:translate({xb:.1f}px,95px)}}",
                  timing="ease-in-out")
    kc = ctx.anim(f"0%,57%{{opacity:0;transform:translate({xb:.1f}px,95px)}}60%{{opacity:1;transform:translate({xb:.1f}px,95px)}}"
                  f"{end}%{{transform:translate({xt + 2:.1f}px,95px)}}{end + 6}%,100%{{opacity:1;transform:translate({xt:.1f}px,95px)}}",
                  timing="ease-in-out")
    wins = [(0, 31), (31, 58.5), (58.5, 100)]
    chips = "".join(chip(t, act_cls(ctx, i, 3, s, e, scale=False)) for i, (t, (s, e)) in enumerate(zip(chips_text, wins)))
    pin = (f'<path d="M220 97 Q213 88 213 83 A7 7 0 1 1 227 83 Q227 88 220 97 Z" fill="{c}"></path>'
           f'<circle cx="220" cy="83" r="2.6" fill="#FFFFFF"></circle>')
    clouds = ('<ellipse cx="70" cy="34" rx="15" ry="4.5" fill="#FFFFFF"></ellipse><ellipse cx="77" cy="30" rx="8" ry="4.5" fill="#FFFFFF"></ellipse>'
              '<ellipse cx="170" cy="30" rx="13" ry="4" fill="#FFFFFF"></ellipse>')
    trk = track(ctx, c, p, label, "", t=end, over_text=over)
    out = (clouds + trk + pin + f'<circle cx="{xs}" cy="98" r="4" fill="{c}"></circle>'
           f'<g class="{kp}"><g transform="scale(0.42) translate(-42 -14)">{plane(c)}</g></g>'
           f'<g class="{kt}">{placed("train", c, W)}</g><g class="{kc}">{placed("coach", c, W)}</g>' + chips)
    return out, ""


# ---------------------------------------------------------------- people for the acts (local units, ground at y 80)
def head_side(shirt_band=None):
    band = f'<path d="M30 6.2 Q34.5 4.8 40.3 6.6" stroke="{shirt_band}" class="w15"></path>' if shirt_band else ""
    return (f'<path d="M32.6 14.5 L32.2 18.5" stroke="{SK}" class="w4"></path>'
            f'<circle cx="34" cy="9" r="6.6" fill="{SK}"></circle>'
            f'<path d="M27.5 8.8 Q27.6 1.6 34.4 2.4 Q40.6 3.2 40.8 7.6 Q36.6 5.5 33.2 6.6 Q30.2 8.7 27.5 8.8 Z" fill="{HAIR}"></path>'
            f'<circle cx="38.2" cy="8.8" r=".95" fill="{HAIR}"></circle>' + band)


def head_front(eyes="open"):
    e = (f'<circle cx="27.6" cy="10.6" r=".9" fill="{HAIR}"></circle><circle cx="32.4" cy="10.6" r=".9" fill="{HAIR}"></circle>' if eyes == "open"
         else f'<path d="M26.3 10.4 Q27.6 11.6 28.9 10.4 M31.1 10.4 Q32.4 11.6 33.7 10.4" stroke="{HAIR}" class="w1"></path>')
    return (f'<path d="M30 15 L30 19" stroke="{SK}" class="w4"></path>'
            f'<circle cx="30" cy="10" r="6.6" fill="{SK}"></circle>'
            f'<path d="M23.4 9.6 Q23.5 2.6 30 2.6 Q36.5 2.6 36.6 9.6 Q33.6 6.2 30 6.5 Q26.4 6.2 23.4 9.6 Z" fill="{HAIR}"></path>{e}'
            f'<path d="M28.6 13.6 Q30 14.4 31.4 13.6" stroke="#8E5A3C" class="w1"></path>')


def leg_side(skin, shoe, x=28, thigh_cls="", shin_cls="", pants=None):
    th = f'<path d="M{x} 47 L{x} 60" stroke="{pants or skin}" class="w7"></path>'
    return (f'<g class="{thigh_cls}">{th}<g class="{shin_cls}"><path d="M{x} 60 L{x} 73" stroke="{pants or skin}" class="w6"></path>'
            f'<path d="M{x - 2.8} 72.4 H{x + 3.6} Q{x + 8.2} 72.4 {x + 8.4} 76 V77.6 H{x - 2.8} Z" fill="{shoe}" stroke="{TYRE}" class="w1"></path></g></g>')


def act_running(ctx, c, s, e):
    body, _, _ = runner(ctx, c)
    dashes = "".join(f'<path d="M{x} 101 H{x + 9}" stroke="#C9D3CD" class="w2"></path>' for x in range(40, 200, 22))
    kd = cycles(ctx, "0%{transform:translateX(0)}100%{transform:translateX(-22px)}", 0.45, s, 5, timing="linear")
    return f'<g class="{kd}">{dashes}</g><g transform="translate(98 46.6) scale(0.62)">{body}</g>'


def act_gym(ctx, c, s, e):
    D = mix(c, "#000000", 0.3)
    bar = cycles(ctx, "0%,8%{transform:translateY(0)}42%,58%{transform:translateY(-22px)}92%,100%{transform:translateY(0)}", 1.0, s, 2)
    up = cycles(ctx, "0%,24%{opacity:0}28%,72%{opacity:1}76%,100%{opacity:0}", 1.0, s, 2, timing="linear")
    dn = cycles(ctx, "0%,24%{opacity:1}28%,72%{opacity:0}76%,100%{opacity:1}", 1.0, s, 2, timing="linear")
    person = (f'<path d="M26 47 L22 77" stroke="{NAVY}" class="w7"></path><path d="M34 47 L38 77" stroke="{NAVY}" class="w7"></path>'
              f'<path d="M16.5 78 H25 M35 78 H43.5" stroke="{TYRE}" class="w4"></path>'
              f'<path d="M30 25 L30 43" stroke="{c}" class="w16"></path><path d="M30 42 L30 48" stroke="{NAVY}" class="w16"></path>'
              f'<g class="{dn}"><path d="M23 26 L14 31 L14 19" stroke="{SK}" class="w45"></path><path d="M37 26 L46 31 L46 19" stroke="{SK}" class="w45"></path></g>'
              f'<g class="{up}"><path d="M23 26 L17 11 L14 -3" stroke="{SK}" class="w45"></path><path d="M37 26 L43 11 L46 -3" stroke="{SK}" class="w45"></path></g>'
              f'<circle cx="23" cy="26" r="3.8" fill="{c}"></circle><circle cx="37" cy="26" r="3.8" fill="{c}"></circle>'
              + head_front() +
              f'<g class="{bar}"><path d="M3 18 H57" stroke="{TYRE}" class="w25"></path>'
              f'<rect x="1" y="10" width="5" height="16" rx="1.5" fill="{D}"></rect><rect x="54" y="10" width="5" height="16" rx="1.5" fill="{D}"></rect>'
              f'<rect x="6" y="12.5" width="3" height="11" rx="1" fill="{D}"></rect><rect x="51" y="12.5" width="3" height="11" rx="1" fill="{D}"></rect></g>')
    floor = (f'<path d="M66 93 H82" stroke="{TYRE}" class="w2"></path><rect x="62" y="88" width="5" height="10" rx="1.5" fill="{D}"></rect>'
             f'<rect x="81" y="88" width="5" height="10" rx="1.5" fill="{D}"></rect>'
             f'<rect x="150" y="70" width="40" height="5" rx="2" fill="#9AA6A0"></rect><path d="M156 75 V95 M184 75 V95" stroke="#9AA6A0" class="w3"></path>')
    return floor + f'<g transform="translate(106 45.4) scale(0.62)">{person}</g>'


def act_meditate(ctx, c, s, e):
    aura = cycles(ctx, "0%,100%{transform:scale(.9);opacity:.6}50%{transform:scale(1.12);opacity:1}", 2.4, s, 1, box="fill-box", origin="center")
    breath = cycles(ctx, "0%,100%{transform:translateY(0)}50%{transform:translateY(-1.6px)}", 2.4, s, 1)
    L = mix(c, "#FFFFFF", 0.55)
    person = (f'<g class="{aura}"><circle cx="30" cy="46" r="34" fill="{L}" style="fill-opacity: .35"></circle>'
              f'<circle cx="30" cy="46" r="24" fill="{L}" style="fill-opacity: .45"></circle></g>'
              f'<rect x="-4" y="79" width="68" height="3.5" rx="1.7" fill="{mix(c, "#000000", 0.2)}"></rect>'
              f'<path d="M7 74 Q30 61 53 74 Q49 80.5 30 80.5 Q11 80.5 7 74 Z" fill="{NAVY}"></path>'
              f'<ellipse cx="17" cy="74.5" rx="4.5" ry="2.4" fill="{SK}"></ellipse><ellipse cx="43" cy="74.5" rx="4.5" ry="2.4" fill="{SK}"></ellipse>'
              f'<g class="{breath}"><path d="M30 47 L30 66" stroke="{c}" class="w16"></path>'
              f'<path d="M23 49 L17 62 L14 70" stroke="{SK}" class="w45"></path><path d="M37 49 L43 62 L46 70" stroke="{SK}" class="w45"></path>'
              f'<circle cx="13.6" cy="71" r="2.7" fill="{SK}"></circle><circle cx="46.4" cy="71" r="2.7" fill="{SK}"></circle>'
              f'<circle cx="23" cy="49" r="3.8" fill="{c}"></circle><circle cx="37" cy="49" r="3.8" fill="{c}"></circle>'
              f'<g transform="translate(0 25)">{head_front("closed")}</g><circle cx="30" cy="25.6" r="3.2" fill="{HAIR}"></circle></g>')
    leaves = "".join(f'<path d="M{x} {y} q6 -6 12 0 q-6 6 -12 0 Z" fill="{L}"></path>' for x, y in ((60, 40), (176, 30), (184, 64)))
    return leaves + f'<g transform="translate(106 45.4) scale(0.62)">{person}</g>'


def act_cricket(ctx, c, s, e):
    swing = win(ctx, s, e, [(0, "transform:rotate(-165deg)"), (0.28, "transform:rotate(-165deg)"), (0.42, "transform:rotate(-290deg)"),
                            (0.48, "transform:rotate(-345deg)"), (0.62, "transform:rotate(-455deg)"), (1, "transform:rotate(-455deg)")],
                timing="ease-in", box="view-box", origin="29px 24px")
    pads = "".join(f'<path d="{d}" stroke="{TYRE}" class="w7" style="stroke-width: 8.6"></path><path d="{d}" stroke="#FFFFFF" class="w7"></path>'
                   for d in ("M25 46 L21 62 L21 75", "M27 46 L32 62 L33 75"))
    body = (pads + f'<path d="M18 77 H25 M30 77 H38" stroke="{TYRE}" class="w4"></path>'
            f'<path d="M26.5 40 L26 47" stroke="#E7ECE8" class="w12"></path><path d="M28 22 L26.5 41" stroke="{c}" class="w12"></path>'
            f'<circle cx="31" cy="10" r="6.4" fill="{SK}"></circle><path d="M24.4 10 Q24.6 2 31.4 2.4 Q38.2 2.8 38.6 9.6 Z" fill="{NAVY}"></path>'
            f'<path d="M37.4 8.8 L42.2 9.8" stroke="{NAVY}" class="w2"></path><path d="M37.2 11 V16 M39.2 10.6 V15.4" stroke="#9AA6A0" class="w1"></path>'
            f'<g class="{swing}"><path d="M29 24 L37 33" stroke="{SK}" class="w5"></path>'
            f'<circle cx="38" cy="34" r="2.9" fill="#FFFFFF" stroke="{TYRE}" class="w1"></circle>'
            f'<path d="M38.5 34.5 L42 38" stroke="{TYRE}" class="w3"></path><path d="M42 38 L56 52" stroke="#D9B27C" class="w6"></path></g>')
    tx, ty, sc = 62, 46.6, 0.62
    ang = math.radians(60)
    bx, by = tx + (29 + 38.5 * math.cos(ang)) * sc, ty + (24 + 38.5 * math.sin(ang)) * sc
    ball = win(ctx, s, e, [(0, f"opacity:0;transform:translate({bx:.1f}px,{by:.1f}px)"), (0.47, f"opacity:0;transform:translate({bx:.1f}px,{by:.1f}px)"),
                           (0.49, f"opacity:1;transform:translate({bx:.1f}px,{by:.1f}px)"), (0.62, f"opacity:1;transform:translate(150px,36px)"),
                           (0.76, "opacity:1;transform:translate(214px,22px)"), (0.86, "opacity:0;transform:translate(244px,24px)"),
                           (1, "opacity:0;transform:translate(244px,24px)")], timing="linear", box="view-box", origin="0 0")
    stumps = f'<path d="M48 95 V74 M51 95 V74 M54 95 V74" stroke="#D9B27C" class="w2"></path><path d="M47 73.5 H55" stroke="#B8862B" class="w15"></path>'
    return (stumps + f'<g transform="translate({tx} {ty}) scale({sc})">{body}</g>'
            f'<g class="{ball}"><circle cx="0" cy="0" r="3.2" fill="{RED}" stroke="#FFFFFF" class="w1"></circle></g>')


def act_football(ctx, c, s, e):
    th = win(ctx, s, e, [(0, "transform:rotate(0deg)"), (0.3, "transform:rotate(48deg)"), (0.44, "transform:rotate(-72deg)"),
                         (0.58, "transform:rotate(-40deg)"), (0.8, "transform:rotate(0deg)"), (1, "transform:rotate(0deg)")],
             box="view-box", origin="28px 46px")
    sh = win(ctx, s, e, [(0, "transform:rotate(8deg)"), (0.3, "transform:rotate(80deg)"), (0.44, "transform:rotate(0deg)"),
                         (0.8, "transform:rotate(8deg)"), (1, "transform:rotate(8deg)")], box="view-box", origin="28px 60px")
    shorts = "#FFFFFF"
    body = (f'<g transform="rotate(-50 31 23)"><path d="M31 23 L31 33" stroke="{SKD}" class="w5"></path><path d="M31 33 L31 41" stroke="{SKD}" class="w45"></path></g>'
            + leg_side(SKD, "#E7ECE8", x=26)
            + f'<path d="M31 21 L28.6 40.5" stroke="{c}" class="w12"></path><path d="M28.6 40.5 L28.2 46.5" stroke="{shorts}" class="w12"></path>'
            + head_side()
            + leg_side(SK, "#FFFFFF", x=28, thigh_cls=th, shin_cls=sh)
            + f'<g transform="rotate(40 31 23)"><path d="M31 23 L31 33" stroke="{SK}" class="w5"></path><path d="M31 33 L31 41" stroke="{SK}" class="w45"></path></g>'
            + f'<path d="M31 22.5 L31 27" stroke="{c}" class="w7"></path>')
    tx, ty, sc = 60, 46.6, 0.62
    bx, by = tx + 44 * sc, 91
    ball = win(ctx, s, e, [(0, f"transform:translate({bx:.1f}px,{by}px) rotate(0deg)"), (0.44, f"transform:translate({bx:.1f}px,{by}px) rotate(0deg)"),
                           (0.56, "transform:translate(140px,62px) rotate(240deg)"), (0.66, "transform:translate(196px,74px) rotate(420deg)"),
                           (0.76, "transform:translate(200px,91px) rotate(480deg)"), (1, "transform:translate(200px,91px) rotate(480deg)")],
               timing="ease-out", box="view-box", origin="0 0")
    net = "".join(f'<path d="M{x} 64 V95" stroke="#C9D3CD" class="w1"></path>' for x in range(188, 216, 5))
    net += "".join(f'<path d="M184 {y} H216" stroke="#C9D3CD" class="w1"></path>' for y in range(70, 96, 6))
    goal = net + f'<path d="M184 95 V62 H218 V95" stroke="#46524C" class="w25"></path>'
    bulge = win(ctx, s, e, [(0, "transform:translateX(0)"), (0.65, "transform:translateX(0)"), (0.7, "transform:translateX(3px)"), (0.8, "transform:translateX(0)"),
                            (1, "transform:translateX(0)")])
    return (f'<g class="{bulge}">{goal}</g><g transform="translate({tx} {ty}) scale({sc})">{body}</g>'
            f'<g class="{ball}"><circle cx="0" cy="0" r="4.6" fill="#FFFFFF" stroke="{TYRE}" class="w1"></circle>'
            f'<path d="M0 -1.8 L1.7 -0.6 L1.1 1.4 H-1.1 L-1.7 -0.6 Z" fill="{TYRE}"></path></g>')


def act_badminton(ctx, c, s, e):
    arm = win(ctx, s, e, [(0, "transform:rotate(-40deg)"), (0.3, "transform:rotate(-95deg)"), (0.42, "transform:rotate(75deg)"),
                          (0.6, "transform:rotate(45deg)"), (1, "transform:rotate(0deg)")], box="view-box", origin="31px 23px")
    jump = win(ctx, s, e, [(0, "transform:translateY(0)"), (0.22, "transform:translateY(0)"), (0.36, "transform:translateY(-7px)"),
                           (0.54, "transform:translateY(0)"), (1, "transform:translateY(0)")])
    body = (f'<g transform="rotate(30 31 23)"><path d="M31 23 L31 33" stroke="{SKD}" class="w5"></path><path d="M31 33 L31 41" stroke="{SKD}" class="w45"></path></g>'
            + leg_side(SKD, "#E7ECE8", x=24)
            + f'<path d="M31 21 L28.6 40.5" stroke="{c}" class="w12"></path><path d="M28.6 40.5 L28.2 46.5" stroke="{NAVY}" class="w12"></path>'
            + head_side() + leg_side(SK, "#FFFFFF", x=31)
            + f'<g class="{arm}"><path d="M31 23 L31 4" stroke="{SK}" class="w45"></path><path d="M31 23 L31 27" stroke="{c}" class="w7"></path>'
            f'<path d="M31 4 L31 -4" stroke="{TYRE}" class="w25"></path>'
            f'<ellipse cx="31" cy="-12" rx="5" ry="8" fill="#FFFFFF" stroke="{TYRE}" class="w15"></ellipse>'
            f'<path d="M28 -16 V-8 M31 -19 V-5 M34 -16 V-8 M27 -12 H35" stroke="#9AA6A0" class="w1"></path></g>')
    tx, ty, sc = 62, 46.6, 0.62
    a = math.radians(75)
    hx, hy = 31 + 35 * math.sin(a), 23 - 35 * math.cos(a)
    sx, sy = tx + hx * sc, ty + hy * sc
    shut = win(ctx, s, e, [(0, f"opacity:0;transform:translate({sx:.1f}px,{sy:.1f}px) rotate(20deg)"), (0.41, f"opacity:0;transform:translate({sx:.1f}px,{sy:.1f}px) rotate(20deg)"),
                           (0.43, f"opacity:1;transform:translate({sx:.1f}px,{sy:.1f}px) rotate(20deg)"), (0.56, "opacity:1;transform:translate(150px,58px) rotate(25deg)"),
                           (0.7, "opacity:1;transform:translate(204px,90px) rotate(40deg)"), (1, "opacity:1;transform:translate(204px,90px) rotate(40deg)")],
               timing="linear", box="view-box", origin="0 0")
    net = (f'<path d="M150 95 V64" stroke="#46524C" class="w2"></path>'
           + "".join(f'<path d="M147 {y} H153" stroke="#C9D3CD" class="w1"></path>' for y in range(70, 95, 4))
           + f'<path d="M145 66 H155" stroke="{TYRE}" class="w25"></path>')
    return (net + f'<g class="{jump}"><g transform="translate({tx} {ty}) scale({sc})">{body}</g></g>'
            f'<g class="{shut}"><path d="M0 0 L8 -3.5 L8 3.5 Z" fill="#FFFFFF" stroke="{TYRE}" class="w1"></path>'
            f'<circle cx="0" cy="0" r="1.9" fill="{GOLD}"></circle></g>')


# ---------------------------------------------------------------- utilities
def act_power(ctx, c, s, e):
    glow = win(ctx, s, e, [(0, "opacity:0"), (0.12, "opacity:0"), (0.18, "opacity:.9"), (0.22, "opacity:.2"), (0.28, "opacity:1"), (1, "opacity:1")], timing="linear")
    rays = win(ctx, s, e, [(0, "opacity:0;transform:scale(.8)"), (0.28, "opacity:0;transform:scale(.8)"), (0.4, "opacity:1;transform:scale(1)"),
                           (1, "opacity:1;transform:scale(1)")], box="fill-box", origin="center")
    disc = cycles(ctx, "0%{transform:translateX(-7px)}50%{transform:translateX(7px)}100%{transform:translateX(-7px)}", 0.8, s, 2)
    return (f'<g class="{glow}"><circle cx="100" cy="42" r="22" fill="{GOLD}" style="fill-opacity: .35"></circle>'
            f'<circle cx="100" cy="42" r="14" fill="{GOLD}"></circle></g>'
            f'<path d="M92 60 Q82 52 84 40 A16 16 0 1 1 116 40 Q118 52 108 60 Z" fill="none" stroke="{TYRE}" class="w2"></path>'
            f'<path d="M94 44 L98 50 L102 44 L106 50" stroke="{TYRE}" class="w15"></path>'
            f'<rect x="92" y="61" width="16" height="12" rx="2" fill="#9AA6A0"></rect><path d="M92 65 H108 M92 69 H108" stroke="#FFFFFF" class="w1"></path>'
            f'<g class="{rays}"><path d="M100 10 V16 M126 20 L121 25 M74 20 L79 25 M134 42 H128 M66 42 H72" stroke="{GOLD_D}" class="w2"></path></g>'
            f'<rect x="146" y="30" width="44" height="58" rx="5" fill="#FFFFFF" stroke="{c}" class="w2"></rect>'
            f'<rect x="152" y="38" width="32" height="12" rx="2" fill="#16211C"></rect>'
            f'<path d="M156 44 H180" stroke="#7FD3AE" class="w2 dash"></path>'
            f'<ellipse cx="168" cy="70" rx="12" ry="4" fill="{mix(c, "#FFFFFF", 0.6)}"></ellipse>'
            f'<g class="{disc}"><rect x="166.5" y="66.5" width="3" height="7" rx="1" fill="{TYRE}"></rect></g>'
            f'<path d="M146 76 Q128 84 108 70" stroke="{TYRE}" class="w15"></path>')


def act_water(ctx, c, s, e):
    level = win(ctx, s, e, [(0, "transform:scaleY(0)"), (0.12, "transform:scaleY(0)"), (0.85, "transform:scaleY(1)"), (1, "transform:scaleY(1)")],
                timing="ease-out", box="fill-box", origin="50% 100%")
    drops = ""
    for i in range(3):
        k = cycles(ctx, "0%{opacity:0;transform:translateY(0)}10%{opacity:1}70%{opacity:1;transform:translateY(26px)}75%,100%{opacity:0;transform:translateY(26px)}",
                   0.6, s + i * 3, 3, timing="ease-in")
        drops += f'<path class="{k}" d="M116 36 Q119 40.5 116 42 Q113 40.5 116 36 Z" fill="#4E8FD9"></path>'
    return (f'<path d="M50 22 H104" stroke="#9AA6A0" class="w6"></path><rect x="100" y="16" width="20" height="12" rx="3" fill="#9AA6A0"></rect>'
            f'<path d="M110 16 V9 M103 9 H117" stroke="{TYRE}" class="w3"></path>'
            f'<path d="M113 28 H119 V34 H113 Z" fill="#7F8B85"></path>' + drops +
            f'<rect class="{level}" x="98" y="72" width="36" height="20" fill="#9CC8F5"></rect>'
            f'<path d="M92 62 L98 96 H134 L140 62" stroke="{c}" class="w3"></path>'
            f'<path d="M90 62 H142" stroke="{c}" class="w3"></path>'
            f'<path d="M98 60 Q116 48 134 60" stroke="{TYRE}" class="w15"></path>')


def act_broadband(ctx, c, s, e):
    arcs = ""
    for i, r in enumerate((11, 20, 29)):
        k = win(ctx, s, e, [(0, "opacity:0"), (0.12 + i * 0.12, "opacity:0"), (0.2 + i * 0.12, "opacity:1"), (0.7, "opacity:1"), (0.8, "opacity:.45"),
                            (0.9, "opacity:1"), (1, "opacity:1")], timing="linear")
        x1, y1 = 126 - r * math.cos(math.radians(45)), 54 - r * math.sin(math.radians(45))
        x2 = 126 + r * math.cos(math.radians(45))
        arcs += f'<path class="w3 {k}" d="M{x1:.1f} {y1:.1f} A{r} {r} 0 0 1 {x2:.1f} {y1:.1f}" stroke="{c}"></path>'
    leds = ""
    for i, x in enumerate((110, 118, 126, 134)):
        k = cycles(ctx, "0%,100%{opacity:1}50%{opacity:.25}", 0.5 + i * 0.13, s, 4, timing="steps(2)")
        leds += f'<circle class="{k}" cx="{x}" cy="79" r="1.8" fill="#7FD3AE"></circle>'
    return (arcs + f'<circle cx="126" cy="54" r="3" fill="{c}"></circle>'
            f'<path d="M104 70 L98 46 M148 70 L154 46" stroke="{TYRE}" class="w3"></path>'
            f'<rect x="96" y="68" width="60" height="20" rx="6" fill="{c}"></rect>' + leds +
            f'<path d="M156 82 Q176 82 182 94" stroke="{TYRE}" class="w15"></path>')


def act_gas(ctx, c, s, e):
    flames = ""
    for i, x in enumerate((152, 160, 168, 176)):
        k = cycles(ctx, "0%,100%{transform:scaleY(1)}50%{transform:scaleY(.72)}", 0.32 + i * 0.05, s, 6, box="fill-box", origin="50% 100%")
        flames += (f'<g class="{k}"><path d="M{x} 78 Q{x - 4} 72 {x} 64 Q{x + 4} 72 {x} 78 Z" fill="#4E8FD9"></path>'
                   f'<path d="M{x} 78 Q{x - 2} 74 {x} 70 Q{x + 2} 74 {x} 78 Z" fill="#9CC8F5"></path></g>')
    on = win(ctx, s, e, [(0, "opacity:0"), (0.12, "opacity:0"), (0.2, "opacity:1"), (1, "opacity:1")], timing="linear")
    return (f'<rect x="62" y="40" width="32" height="56" rx="12" fill="#C0392B"></rect>'
            f'<rect x="70" y="32" width="16" height="10" rx="2" fill="#7A2318"></rect>'
            f'<path d="M66 54 H90" stroke="#E57A6E" class="w2"></path>'
            f'<path d="M86 36 Q118 26 136 82" stroke="{TYRE}" class="w25"></path>'
            f'<g class="{on}">{flames}</g>'
            f'<ellipse cx="164" cy="79" rx="20" ry="3.2" fill="#3B4A57"></ellipse>'
            f'<rect x="128" y="82" width="74" height="12" rx="3" fill="#3B4A57"></rect>'
            f'<circle cx="140" cy="88" r="2.4" fill="#C9D3CD"></circle><circle cx="190" cy="88" r="2.4" fill="#C9D3CD"></circle>')


def sc_acts(ctx, c, p, label, over, acts):
    n = len(acts)
    wins = windows(n, 88)
    out = ground()
    chips = ""
    for i, (fn, text) in enumerate(acts):
        s, e = wins[i]
        out += f'<g class="{act_cls(ctx, i, n, s, e)}">{fn(ctx, c, s, e)}</g>'
        chips += chip(text, act_cls(ctx, i, n, s, e, scale=False))
    return out + progress_bottom(ctx, c, p, label, over) + chips, ""


# ---------------------------------------------------------------- Food: a thali that fills up
def sc_thali(ctx, c, p, label, over):
    cx, cy = 150, 60
    items = [
        ("Rice", f'<ellipse cx="{cx - 2}" cy="{cy + 6}" rx="13" ry="10" fill="#FFFFFF" stroke="#E3DCC8" class="w1"></ellipse>'
                 + "".join(f'<path d="M{cx - 8 + i * 4} {cy + 2 + (i % 2) * 6} h2.4" stroke="#E3DCC8" class="w1"></path>' for i in range(5))),
        ("Dal", f'<circle cx="{cx - 22}" cy="{cy - 18}" r="10" fill="#C9D3CD"></circle><circle cx="{cx - 22}" cy="{cy - 18}" r="7.6" fill="#E9B949"></circle>'),
        ("Sabzi", f'<circle cx="{cx + 1}" cy="{cy - 28}" r="10" fill="#C9D3CD"></circle><circle cx="{cx + 1}" cy="{cy - 28}" r="7.6" fill="#6E9F3B"></circle>'
                  f'<circle cx="{cx - 2}" cy="{cy - 30}" r="1.8" fill="#C0392B"></circle><circle cx="{cx + 3}" cy="{cy - 26}" r="1.6" fill="#F2B84B"></circle>'),
        ("Roti", f'<circle cx="{cx + 25}" cy="{cy + 8}" r="12.5" fill="#D9A766"></circle><circle cx="{cx + 21}" cy="{cy + 5}" r="1.7" fill="#A8743C"></circle>'
                 f'<circle cx="{cx + 28}" cy="{cy + 12}" r="1.4" fill="#A8743C"></circle><circle cx="{cx + 27}" cy="{cy + 3}" r="1.2" fill="#A8743C"></circle>'),
        ("Curd", f'<circle cx="{cx + 24}" cy="{cy - 17}" r="10" fill="#C9D3CD"></circle><circle cx="{cx + 24}" cy="{cy - 17}" r="7.6" fill="#FBF8EE"></circle>'),
        ("Papad", f'<circle cx="{cx - 24}" cy="{cy + 16}" r="11" fill="#F0D79A"></circle>'
                  + "".join(f'<circle cx="{cx - 24 + dx}" cy="{cy + 16 + dy}" r=".9" fill="#B8862B"></circle>' for dx, dy in ((-4, -3), (3, -4), (-2, 4), (5, 3), (0, 0)))),
        ("Pickle", f'<ellipse cx="{cx - 30}" cy="{cy - 1}" rx="5" ry="4" fill="#C8423B"></ellipse>'),
        ("Sweet", f'<circle cx="{cx + 6}" cy="{cy + 28}" r="9" fill="#C9D3CD"></circle><circle cx="{cx + 3}" cy="{cy + 28}" r="3.6" fill="#7A3B1E"></circle>'
                  f'<circle cx="{cx + 9}" cy="{cy + 28}" r="3.6" fill="#7A3B1E"></circle>'),
    ]
    extras = [("Samosa", f'<path d="M218 98 L230 74 L242 98 Z" fill="#E0A54C" stroke="{RED}" class="w15"></path>'),
              ("Lassi", f'<path d="M210 36 L214 62 H228 L232 36 Z" fill="#FBF8EE" stroke="{RED}" class="w15"></path>')]
    full = len(items)
    n = max(1, round(p * full))
    dishes = ""
    for i, (_, svg) in enumerate((items + extras)[:n]):
        at = 6 + i * (32 / max(n, 1))
        k = ctx.anim(f"0%,{at:.1f}%{{opacity:0;transform:scale(.3)}}{at + 3:.1f}%{{opacity:1;transform:scale(1.12)}}{at + 5:.1f}%,100%{{opacity:1;transform:scale(1)}}",
                     box="fill-box", origin="center")
        dishes += f'<g class="{k}">{svg}</g>'
    steam = ""
    for i, (x, y) in enumerate(((cx - 22, cy - 30), (cx + 1, cy - 40))):
        ks = ctx.anim("0%,30%{opacity:0;transform:translateY(6px)}50%{opacity:.9}85%,100%{opacity:0;transform:translateY(-10px)}", delay=i * 0.4)
        steam += f'<path class="w15 {ks}" d="M{x} {y} q-3 -4 0 -8 q3 -4 0 -8" stroke="#9AA6A0"></path>'
    ring = f"M{cx} {cy - 50} A50 50 0 1 1 {cx - 0.01} {cy - 50}"
    kr = ctx.anim(f"0%{{stroke-dashoffset:100}}40%,100%{{stroke-dashoffset:{100 - min(p, 1) * 100:.1f}}}", extra="stroke-dasharray:100")
    out = (f'<path d="{ring}" stroke="{GREY}" class="w3"></path><path d="{ring}" stroke="{c}" class="w3 {kr}" pathLength="100"></path>'
           f'<circle cx="{cx}" cy="{cy}" r="45" fill="#E7ECE8"></circle><circle cx="{cx}" cy="{cy}" r="40" fill="#F5F7F5" stroke="#D3DBD6" class="w1"></circle>'
           + dishes + steam +
           f'<text x="14" y="50" class="lbl" style="font-size: 13px; font-weight: 700; fill: #16211C">{min(n, full)} of {full} dishes</text>'
           f'<text x="14" y="66" class="lbl">{label}</text>'
           f'<text x="14" y="80" class="lbl">One dish per eighth</text>')
    if p > 1:
        out += f'<text x="14" y="102" class="lbl-red {ctx.appear(44)}" style="text-anchor: start">{over}</text>'
    return out, ""


# ---------------------------------------------------------------- Shopping: a cart filled to the budget
def sc_shopcart(ctx, c, p, label, over):
    W = ctx.spin(t=16, turns=1)
    shapes = [
        f'<path d="M-9 -7 L-4 -10 Q0 -7 4 -10 L9 -7 L7 -2 L5 -3 V9 H-5 V-3 L-7 -2 Z" fill="{c}"></path>',
        f'<path d="M-10 4 Q-10 -3 -4 -3 L0 -8 H4 L4 0 Q10 0 10 4 V7 H-10 Z" fill="#E07B39"></path><path d="M-10 6 H10" stroke="#FFFFFF" class="w1"></path>',
        f'<path d="M-8 -2 H8 L7 9 H-7 Z" fill="#E86F93"></path><path d="M-4 -2 Q-4 -9 0 -9 Q4 -9 4 -2" stroke="#B5576C" class="w15"></path>',
        f'<rect x="-8" y="-6" width="16" height="15" rx="1.5" fill="{RED}"></rect><path d="M0 -6 V9 M-8 1 H8" stroke="{GOLD}" class="w2"></path>'
        f'<path d="M0 -6 Q-5 -11 -3 -6 M0 -6 Q5 -11 3 -6" stroke="{GOLD}" class="w15"></path>',
        f'<rect x="-6" y="-9" width="12" height="18" rx="2.5" fill="#2F3A55"></rect><rect x="-4" y="-6.5" width="8" height="12" rx="1" fill="#9CC8F5"></rect>',
        f'<circle cx="-5" cy="2" r="4.2" fill="#3B4A57"></circle><circle cx="5" cy="2" r="4.2" fill="#3B4A57"></circle><path d="M-1 1 H1 M-9 1 L-10 -3 M9 1 L10 -3" stroke="#3B4A57" class="w15"></path>',
        f'<rect x="-5" y="-4" width="10" height="13" rx="3" fill="#B7A6E8"></rect><rect x="-2" y="-8" width="4" height="4" rx="1" fill="{TYRE}"></rect>',
        f'<path d="M-9 4 Q-9 -6 0 -6 Q9 -6 9 4 Z" fill="#3A86C9"></path><path d="M6 4 H13" stroke="#3A86C9" class="w3"></path>',
    ]
    slots = [(92, 74), (114, 74), (136, 74), (158, 74), (100, 56), (124, 56), (148, 56), (172, 56), (112, 38), (140, 38)]
    full = 8
    n = max(1, round(p * full))
    items = ""
    for i in range(n):
        x, y = slots[i]
        shp = shapes[i % len(shapes)]
        at = 18 + i * (22 / max(n, 1))
        k = ctx.anim(f"0%,{at:.1f}%{{opacity:0;transform:translate({x}px,{y - 50}px)}}{at + 1:.1f}%{{opacity:1}}"
                     f"{at + 5:.1f}%{{transform:translate({x}px,{y + 2}px)}}{at + 7:.1f}%,100%{{opacity:1;transform:translate({x}px,{y}px)}}",
                     timing="ease-in", box="view-box", origin="0 0")
        tag = f'<circle cx="9" cy="-9" r="4" fill="{RED}"></circle>' if i >= full else ""
        items += f'<g class="{k}">{shp}{tag}</g>'
    roll = ctx.anim("0%{transform:translateX(-150px)}14%{transform:translateX(4px)}18%,100%{transform:translateX(0)}", timing="cubic-bezier(.2,.8,.2,1)")
    mesh = mix(c, "#FFFFFF", 0.5)
    front = (f'<path d="M80 62 H190" stroke="{mesh}" class="w1"></path><path d="M82 74 H188" stroke="{mesh}" class="w1"></path>'
             + "".join(f'<path d="M{x} 46 L{x + 2} 86" stroke="{mesh}" class="w1"></path>' for x in range(96, 186, 16))
             + f'<path d="M58 30 H70 L78 46 H196 L186 86 H86 L78 46" stroke="{c}" class="w3" style="fill: none"></path>'
             f'<path d="M86 86 L82 94 H186" stroke="{TYRE}" class="w25"></path>'
             f'<g class="{W}"><circle cx="94" cy="99" r="5" fill="{TYRE}"></circle><circle cx="94" cy="99" r="2" fill="#C9D3CD"></circle></g>'
             f'<g class="{W}"><circle cx="176" cy="99" r="5" fill="{TYRE}"></circle><circle cx="176" cy="99" r="2" fill="#C9D3CD"></circle></g>')
    back = f'<path d="M78 46 H196 L186 86 H86 Z" fill="#FFFFFF" style="fill-opacity: .9"></path>'
    out = (f'<g class="{roll}">{back}{items}{front}</g>'
           f'<path d="M70 46 H232" stroke="{c}" class="w15 dash"></path><text x="236" y="41" class="lbl lbl-end">{label}</text>'
           f'<text x="14" y="112" class="lbl">{min(n, full)} of {full} items · one per eighth of the budget</text>')
    if p > 1:
        out += f'<text x="236" y="21" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out, ""


# ---------------------------------------------------------------- the round-3 set
HEALTH_ACTS = [(act_running, "Running"), (act_gym, "Gym"), (act_meditate, "Meditation")]
SPORTS_ACTS = [(act_cricket, "Cricket"), (act_football, "Football"), (act_badminton, "Badminton")]
UTIL_ACTS = [(act_power, "Electricity"), (act_water, "Water"), (act_broadband, "Broadband"), (act_gas, "Gas")]

ENTRIES = [
    ("transport", "Transport · every mode", "transport", 6480, 8000, "One trip that changes from car to scooter, bus and metro, stopping at 81% of the budget."),
    ("travel", "Travel · every mode", "travel", 4200, 6000, "The plane lands, carries on as a train, then a coach, to 70% of the way."),
    ("fuel", "Fuel", "fuel", 4350, 5000, "The pump meter counts up to the amount; the gauge needle stops at 87%."),
    ("food", "Food & Dining", "thali", 9000, 12000, "A thali fills up: one dish for each eighth of the budget. The ring shows the total."),
    ("groceries", "Groceries", "cart", 6600, 10000, "The cart fills up on its way to the checkout counter."),
    ("shopping", "Shopping", "shopcart", 3750, 5000, "Things drop into the cart, one for each eighth of the budget."),
    ("bills", "Bills & Utilities", "utilities", 4800, 6000, "Electricity, water, broadband and gas, one after another."),
    ("recharge", "Recharge & Subscriptions", "battery", 600, 1000, "The battery charges up towards a full budget."),
    ("entertainment", "Entertainment", "popcorn", 1900, 2000, "Popcorn rises out of the bucket to the budget line."),
    ("sports", "Sports & Fitness", "sports", 1600, 2500, "Cricket, football, then badminton."),
    ("health", "Health", "health", 2140, 3000, "A run, the gym, then meditation."),
    ("education", "Education", "books", 2000, 4000, "Books stack up towards the budget line."),
    ("rent", "Rent", "key", 22000, 22000, "The key reaches the door when this month's rent is paid."),
    ("emi", "EMI & Loans", "coins", 14700, 14700, "Coins stack up to this month's EMI; the calendar gets a tick."),
    ("investments", "Investments", "plant", 8500, 10000, "The plant grows towards your saving goal for the month."),
    ("cash", "Cash", "atm", 2000, 5000, "The ATM hands out a note for each eighth of the budget."),
    ("transfers", "Transfers", "transfer", 15000, 0, "Money moving between your own accounts. Not counted as spending."),
    ("others", "Others", "jar", 900, 3000, "Coins fill the jar towards the budget line."),
    ("income", "Income", "piggy", 71500, 65000, "Coins drop into the piggy bank; the bar compares with last month."),
]

MULTI = {"transport", "travel", "utilities", "sports", "health"}


def build(ctx, kind, cat, spent, budget, opts=None):
    c = CATS[cat][1]
    p = spent / budget if budget else 1
    label = f"Budget {inr(budget)}"
    over = (inr(spent - budget) + " over") if budget and spent > budget else ""
    opts = opts or {}
    if kind == "transport":
        return sc_transport(ctx, c, p, label, over, opts.get("modes", [("car", "Car"), ("scooter", "Scooter"), ("bus", "Bus"), ("metro", "Metro")]))
    if kind == "travel":
        return sc_travel(ctx, c, p, label, over, opts.get("chips", ["Flight", "Train", "Coach"]))
    if kind == "utilities":
        return sc_acts(ctx, c, p, label, over, opts.get("acts", UTIL_ACTS))
    if kind == "sports":
        return sc_acts(ctx, c, p, label, over, SPORTS_ACTS)
    if kind == "health":
        return sc_acts(ctx, c, p, label, over, HEALTH_ACTS)
    if kind == "thali":
        return sc_thali(ctx, c, p, label, over)
    if kind == "shopcart":
        return sc_shopcart(ctx, c, p, label, over)
    if kind == "fuel":
        return sc_fuel(ctx, c, p, f"{round(p * 100)}%", spent)
    if kind == "cart":
        return sc_cart(ctx, c, p, label, over)
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


def gallery():
    css, cards = [BASE_CSS, EXTRA_CSS], []
    scale = 1.4
    for i, (cat, title, kind, spent, budget, caption) in enumerate(ENTRIES):
        ctx = Ctx(f"h{i}", True, D6 if kind in MULTI else 4)
        svg, overlay = build(ctx, kind, cat, spent, budget)
        css += ctx.css
        c = CATS[cat][1]
        sub, red = sub_line(cat, spent, budget, kind)
        cards.append(f"""<div style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 24px; padding: 16px; display: flex; flex-direction: column; gap: 10px">
<div style="display: flex; align-items: center; gap: 10px">
<span style="width: 12px; height: 12px; border-radius: 6px; flex: none; background: {c}"></span>
<span style="flex: 1; min-width: 0; font-size: 16px; font-weight: 500">{title}</span>
<span style="font-size: 18px; font-weight: 700">{inr(spent)}</span>
</div>
<span style="font-size: 13px; color: {"#B3261E" if red else MUTED}; margin-top: -6px; padding-left: 22px">{sub}</span>
<div style="border-radius: 16px; overflow: hidden; background: {mix(c, '#FFFFFF', 0.92)}">{scene_box(svg, overlay, scale)}</div>
<p style="margin: 0; font-size: 14px; color: {MUTED}; line-height: 1.4">{caption}</p>
</div>""")
    body = f"""<div style="width: 1280px; height: 2440px; box-sizing: border-box; padding: 40px 48px; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif; display: flex; flex-direction: column; gap: 22px">
<div style="display: flex; flex-direction: column; gap: 6px">
<h1 style="margin: 0; font-size: 30px; font-weight: 700">Round 3 · every category</h1>
<p style="margin: 0; font-size: 16px; color: {MUTED}">Transport and Travel change mode as they go; Sports, Health and Bills move through each activity. In the app each plays once when you open the category, and again when you tap it. They loop here.</p>
</div>
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px">
{"".join(cards)}
</div>
</div>"""
    return page("Round 3 · every category", body, STATIC, (1280, 2440), "\n".join(css))


PHONE = [  # id, category, kind, spent, budget, merchants, options
    ("food", "food", "thali", 11240, 12000, [["Swiggy", 4820], ["Zomato", 3560], ["Domino's", 1640], ["Café Coffee Day", 1220]], None),
    ("groceries", "groceries", "cart", 8460, 10000, [["Blinkit", 3210], ["BigBasket", 2980], ["Zepto", 1470], ["More Supermarket", 800]], None),
    ("transport", "transport", "transport", 6480, 8000, [["Uber", 2140], ["Rapido", 1860], ["Namma Metro", 1420], ["BMTC", 1060]],
     {"modes": [("bus", "Bus · BMTC"), ("metro", "Metro · Namma Metro"), ("scooter", "Scooter · Rapido"), ("car", "Car · Uber")]}),
    ("shopping", "shopping", "shopcart", 6120, 5000, [["Amazon", 2890], ["Myntra", 1740], ["Flipkart", 990], ["Nykaa", 500]], None),
    ("bills", "bills", "utilities", 5300, 6000, [["BESCOM", 2450], ["Airtel Broadband", 1180], ["BWSSB", 920], ["Indane Gas", 750]],
     {"acts": [(act_power, "Electricity · BESCOM"), (act_broadband, "Broadband · Airtel"), (act_water, "Water · BWSSB"), (act_gas, "Gas · Indane")]}),
    ("travel", "travel", "travel", 4530, 6000, [["IRCTC", 2310], ["IndiGo", 1620], ["redBus", 600]],
     {"chips": ["Flight · IndiGo", "Train · IRCTC", "Coach · redBus"]}),
    ("fuel", "fuel", "fuel", 4350, 5000, [["Shree Ganesh HPCL", 2100], ["IndianOil Koramangala", 1450], ["Shell HSR Layout", 800]], None),
    ("health", "health", "health", 2140, 3000, [["Apollo Pharmacy", 1260], ["Practo", 520], ["MedPlus", 360]], None),
    ("entertainment", "entertainment", "popcorn", 1890, 2000, [["BookMyShow", 1190], ["PVR INOX", 700]], None),
    ("sports", "sports", "sports", 1600, 2500, [["cult.fit", 999], ["Playo", 601]], None),
]


def phone():
    import gen2
    saved = gen2.PHONE
    css = [EXTRA_CSS]
    scenes = ""
    for i, (cid, cat, kind, spent, budget, _, opts) in enumerate(PHONE):
        ctx = Ctx(f"q{i}", False, D6 if kind in MULTI else 4)
        svg, overlay = build(ctx, kind, cat, spent, budget, opts)
        css += ctx.css
        scenes += f'<sc-if value="{{{{show.{cid}}}}}" hint-placeholder-val="{{{{false}}}}">{scene_box(svg, overlay, 326 / 248)}</sc-if>'
    # reuse round 2's phone shell with this round's scenes
    gen2.PHONE = [(cid, cat, kind, spent, budget, merchants) for cid, cat, kind, spent, budget, merchants, _ in PHONE]
    real_build = gen2.build_scene
    gen2.build_scene = lambda ctx, kind, cat, spent, budget: ("", "")
    try:
        html = gen2.phone()
    finally:
        gen2.build_scene, gen2.PHONE = real_build, saved
    # swap the empty scenes for this round's, and add this round's CSS
    start = html.index('<sc-if value="{{showScene}}"')
    inner_start = html.index(">", start) + 1
    inner_end = html.index("</sc-if>\n</button>", inner_start)
    html = html[:inner_start] + "\n" + scenes + "\n" + html[inner_end:]
    html = html.replace("</style>", "\n".join(css) + "\n</style>", 1)
    html = html.replace("<title>Option B · working phone</title>", "<title>Round 3 · working phone</title>", 1)
    return html


def canvas(base):
    idx = json.loads(base)
    pages = idx.get("pages") or []
    if not any(pg["id"] == "r3" for pg in pages):
        pages.append({"id": "r3", "name": "Round 3"})
    idx["pages"] = pages
    idx["boards"]["B3Phone.dc.html"] = {"x": 0, "y": 0, "w": 390, "h": 844, "title": "Round 3 · working phone (tap a category)",
                                        "is_interactive": True, "radius": 36, "page": "r3"}
    idx["boards"]["B3Gallery.dc.html"] = {"x": 470, "y": 0, "w": 1280, "h": 2440, "title": "Round 3 · every category", "page": "r3"}
    for f in ("B3Phone.dc.html", "B3Gallery.dc.html"):
        if f not in idx["order"]:
            idx["order"].append(f)
    idx["notes"]["round3"] = {"x": 0, "y": -300, "text": "Round 3 · every mode, every activity", "kind": "title1", "maxW": 1750, "page": "r3"}
    idx["launch"] = {"view": "canvas", "page": "r3"}
    return json.dumps(idx, indent=1, ensure_ascii=False)


if __name__ == "__main__":
    which = sys.argv[1]
    if which == "canvas":
        out = ("canvas.json", canvas(open(sys.argv[2]).read()))
    elif which == "B3Phone":
        out = ("B3Phone.dc.html", phone())
    else:
        out = ("B3Gallery.dc.html", gallery())
    with open(ROOT + out[0], "w") as f:
        f.write(out[1])
    print("wrote", out[0], len(out[1]))
