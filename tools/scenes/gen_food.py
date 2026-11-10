"""Food: five options to choose from. python3 gen_food.py <FoodOptions|canvas base.json>"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen import BG, CATS, INK, LINE, MUTED, ROOT, mix  # noqa: E402
from gen2 import BASE_CSS, GOLD, GREY, RED, STATIC, TYRE, Ctx, inr, page, scene_box, scooter  # noqa: E402
from gen3 import EXTRA_CSS  # noqa: E402

C = CATS["food"][1]
HALO = "paint-order: stroke; stroke: #FFFFFF; stroke-width: 3px; stroke-linejoin: round"


def over_note(ctx, p, over, x=236, y=21):
    return f'<text x="{x}" y="{y}" class="lbl-red {ctx.appear(70)}" style="{HALO}">{over}</text>' if p > 1 else ""


# ---------------------------------------------------------------- 1. the order on its way
def opt_delivery(ctx, c, p, label, over):
    """A delivery rider brings the order down the road; the house is the budget."""
    W = ctx.spin(t=70, turns=8)
    D = mix(c, "#000000", 0.25)
    box = (f'<rect x="-1" y="-7" width="19" height="19" rx="2.5" fill="{c}"></rect><rect x="-1" y="-7" width="19" height="4" rx="2" fill="{D}"></rect>'
           f'<path d="M5 0 V8 M5 0 Q4 3 5.6 4 M11 0 V8 M10 0 V3 M12 0 V3 M10 3 H12" stroke="#FFFFFF" class="w1"></path>')
    body, front, gnd = scooter(c, W)
    s = 0.62
    rider = f'<g transform="translate({-front * s:.1f} {-gnd * s:.1f}) scale({s})">{box}{body}</g>'
    xb = 214
    xe = 16 + (xb - 16) * min(p, 1) if p <= 1 else min(16 + (xb - 16) * p, 246)
    k = ctx.anim(f"0%{{transform:translate(-10px,95px)}}70%,100%{{transform:translate({xe:.1f}px,95px)}}", timing="cubic-bezier(.3,.6,.3,1)",
                 box="view-box", origin="0 0")
    bob = ctx.anim("0%,100%{transform:translateY(0)}50%{transform:translateY(-.8px)}", dur=0.3, count="12")
    road = ('<rect x="0" y="95" width="248" height="7" fill="#DCE3DE"></rect>'
            + "".join(f'<path d="M{x} 98.5 H{x + 8}" stroke="#FFFFFF" class="w15"></path>' for x in range(4, 248, 20)))
    town = ""
    for x, h, w in ((8, 22, 16), (28, 30, 14), (46, 18, 18), (130, 26, 14), (148, 16, 18), (170, 32, 12)):
        town += f'<rect x="{x}" y="{95 - h}" width="{w}" height="{h}" rx="1.5" fill="#E7ECE8"></rect>'
        town += "".join(f'<rect x="{x + 3 + i * 5}" y="{95 - h + 4}" width="2.4" height="3" fill="#FFFFFF"></rect>' for i in range(max(1, (w - 4) // 5)))
    house = (f'<path d="M{xb + 2} 95 V74 L{xb + 14} 63 L{xb + 26} 74 V95 Z" fill="#FFFFFF" stroke="{GREY}" class="w1"></path>'
             f'<path d="M{xb - 1} 75 L{xb + 14} 61 L{xb + 29} 75" stroke="{D}" class="w3"></path>'
             f'<rect x="{xb + 10}" y="82" width="8" height="13" rx="1" fill="{c}"></rect><circle cx="{xb + 16}" cy="89" r=".8" fill="#FFFFFF"></circle>')
    done = ctx.anim("0%,70%{opacity:0;transform:translateY(4px)}78%,100%{opacity:1;transform:translateY(0)}")
    tick = f'<g class="{done}"><circle cx="{xe + 6:.1f}" cy="58" r="6" fill="{c}"></circle><path d="M{xe + 3:.1f} 58 l2 2.2 l4 -4.4" stroke="#FFFFFF" class="w15"></path></g>' if p <= 1 else ""
    pct = f"{round(min(p, 1) * 100)}% of the way"
    return (town + road + house + f'<g class="{k}"><g class="{bob}">{rider}</g></g>' + tick
            + f'<text x="244" y="54" class="lbl lbl-end" style="{HALO}">{label}</text>'
            + f'<text x="14" y="114" class="lbl">{pct} · the house is the budget</text>' + over_note(ctx, p, over))


# ---------------------------------------------------------------- 2. a pizza, eaten slice by slice
def opt_pizza(ctx, c, p, label, over):
    """A whole pizza is the budget; a slice goes for each eighth spent."""
    cx, cy, r = 80, 60, 36
    full = 8
    n = min(full, max(1, round(p * full)))
    slices = ""
    for i in range(full):
        a0, a1 = math.radians(-90 + i * 45), math.radians(-90 + (i + 1) * 45)
        am = (a0 + a1) / 2

        def pt(rr, a):
            return cx + rr * math.cos(a), cy + rr * math.sin(a)
        p0, p1 = pt(r, a0), pt(r, a1)
        q0, q1 = pt(r - 5, a0), pt(r - 5, a1)
        wedge = (f'<path d="M{cx} {cy} L{p0[0]:.1f} {p0[1]:.1f} A{r} {r} 0 0 1 {p1[0]:.1f} {p1[1]:.1f} Z" fill="#D9A766"></path>'
                 f'<path d="M{cx} {cy} L{q0[0]:.1f} {q0[1]:.1f} A{r - 5} {r - 5} 0 0 1 {q1[0]:.1f} {q1[1]:.1f} Z" fill="#F2C861"></path>')
        tops = ""
        for rr, da, kind in ((19, -0.18, "p"), (24, 0.2, "p"), (11, 0.05, "b")):
            x, y = pt(rr, am + da)
            tops += (f'<circle cx="{x:.1f}" cy="{y:.1f}" r="3.4" fill="#C8423B"></circle><circle cx="{x - 1:.1f}" cy="{y - 1:.1f}" r=".8" fill="#E8776E"></circle>'
                     if kind == "p" else f'<ellipse cx="{x:.1f}" cy="{y:.1f}" rx="2.6" ry="1.4" fill="#3F8A3A" transform="rotate({math.degrees(am) + 40:.0f} {x:.1f} {y:.1f})"></ellipse>')
        # the order slices are taken in: from the top, going round
        if i < n:
            at = 16 + i * (52 / n)
            dx, dy = 16 * math.cos(am), 16 * math.sin(am)
            k = ctx.anim(f"0%,{at:.1f}%{{opacity:1;transform:translate(0,0)}}{at + 2.5:.1f}%{{opacity:1;transform:translate({dx * 0.4:.1f}px,{dy * 0.4 - 4:.1f}px)}}"
                         f"{at + 6:.1f}%,100%{{opacity:0;transform:translate({dx:.1f}px,{dy - 10:.1f}px)}}", timing="ease-in")
            slices += f'<g class="{k}">{wedge}{tops}</g>'
        else:
            slices += f'<g>{wedge}{tops}</g>'
    cuts = "".join(f'<path d="M{cx} {cy} L{cx + r * math.cos(math.radians(-90 + i * 45)):.1f} {cy + r * math.sin(math.radians(-90 + i * 45)):.1f}" '
                   f'stroke="#C9935A" style="stroke-width: .7"></path>' for i in range(full))
    board = (f'<circle cx="{cx}" cy="{cy + 2}" r="{r + 7}" fill="#B9874F"></circle><circle cx="{cx}" cy="{cy}" r="{r + 7}" fill="#D7A86A"></circle>'
             f'<path d="M{cx + r + 5} {cy - 4} L{cx + r + 18} {cy - 9}" stroke="#B9874F" class="w6"></path>'
             f'<circle cx="{cx}" cy="{cy}" r="{r + 0.5}" fill="#EEE2CF"></circle>')
    eaten = f'<text x="140" y="44" class="lbl" style="font-size: 13px; font-weight: 700; fill: #16211C">{n} of {full} slices</text>'
    extra = ""
    if p > 1:
        more = max(1, round((p - 1) * full))
        extra = (f'<g class="{ctx.appear(72)}"><circle cx="160" cy="92" r="12" fill="#D9A766"></circle><circle cx="160" cy="92" r="9" fill="#F2C861"></circle>'
                 + "".join(f'<path d="M160 92 L{160 + 12 * math.cos(math.radians(-90 + j * 45)):.1f} {92 + 12 * math.sin(math.radians(-90 + j * 45)):.1f} '
                           f'A12 12 0 0 1 {160 + 12 * math.cos(math.radians(-45 + j * 45)):.1f} {92 + 12 * math.sin(math.radians(-45 + j * 45)):.1f} Z" '
                           f'fill="#FFFFFF" stroke="{RED}" style="stroke-width: .8"></path>' for j in range(more)) + "</g>")
    return (board + slices + cuts + extra + eaten
            + f'<text x="140" y="59" class="lbl">the whole pizza is</text>'
            + f'<text x="140" y="72" class="lbl">the {label.replace("Budget ", "")} budget</text>' + over_note(ctx, p, over))


# ---------------------------------------------------------------- 3. a tiffin that stacks up
def opt_tiffin(ctx, c, p, label, over):
    """A steel tiffin carrier: a tier for each quarter of the budget, then the lid clips on."""
    x, w, h = 112, 52, 14
    base = 94
    full = 4
    n = max(1, round(p * full))
    foods = [("#F6F1E6", "#E3DCC8"), ("#E9B949", "#C9962E"), ("#6E9F3B", "#4E7A26"), ("#D9A766", "#B97D3E"), ("#C8423B", "#9E2F28")]
    tiers = ""
    for i in range(n):
        y = base - (i + 1) * h
        top_food, edge = foods[i % len(foods)] if i < full else ("#F3C6C2", RED)
        at = 10 + i * (50 / n)
        k = ctx.anim(f"0%,{at:.1f}%{{opacity:0;transform:translateY(-36px)}}{at + 4:.1f}%{{opacity:1;transform:translateY(1.4px)}}{at + 6:.1f}%,100%{{opacity:1;transform:translateY(0)}}",
                     timing="ease-in")
        steel = "#D3DBD6" if i < full else "#F0D2CF"
        tiers += (f'<g class="{k}"><rect x="{x}" y="{y}" width="{w}" height="{h - 1}" rx="3" fill="{steel}"></rect>'
                  f'<rect x="{x}" y="{y}" width="{w}" height="3.4" rx="1.7" fill="{top_food}"></rect>'
                  f'<path d="M{x + 2} {y + 3.4} H{x + w - 2}" stroke="{edge}" class="w1"></path>'
                  f'<rect x="{x + 4}" y="{y + 6}" width="{w - 8}" height="2" rx="1" fill="#FFFFFF" style="fill-opacity: .7"></rect>'
                  f'<path d="M{x - 3} {y + 3} V{y + h - 1} M{x + w + 3} {y + 3} V{y + h - 1}" stroke="#9AA6A0" class="w2"></path></g>')
    top = base - min(n, full) * h - (max(0, n - full) * h)
    lid_at = 10 + 50 + 4
    lid_k = ctx.anim(f"0%,{lid_at:.1f}%{{opacity:0;transform:translateY(-30px)}}{lid_at + 4:.1f}%{{opacity:1;transform:translateY(1px)}}{lid_at + 6:.1f}%,100%{{opacity:1;transform:translateY(0)}}",
                     timing="ease-in")
    lid = (f'<g class="{lid_k}"><path d="M{x} {top} Q{x} {top - 8} {x + w / 2} {top - 9} Q{x + w} {top - 8} {x + w} {top} Z" fill="#C9D3CD"></path>'
           f'<path d="M{x - 3} {top + 2} V{top - 12} H{x + w + 3} V{top + 2}" stroke="{c}" class="w25"></path>'
           f'<path d="M{x + w / 2 - 8} {top - 12} Q{x + w / 2} {top - 22} {x + w / 2 + 8} {top - 12}" stroke="{c}" class="w3"></path></g>')
    steam = ""
    for i, dx in enumerate((-10, 0, 10)):
        ks = ctx.anim("0%,30%{opacity:0;transform:translateY(4px)}50%{opacity:.85}80%,100%{opacity:0;transform:translateY(-10px)}", delay=i * 0.35)
        steam += f'<path class="w15 {ks}" d="M{x + w / 2 + dx} {top - 24} q-3 -4 0 -8 q3 -4 0 -8" stroke="#9AA6A0"></path>'
    line_y = base - full * h
    guide = (f'<path d="M{x - 10} {line_y} H232" stroke="{c}" class="w15 dash"></path>'
             f'<text x="236" y="{line_y - 4}" class="lbl lbl-end" style="{HALO}">{label}</text>')
    shadow = f'<ellipse cx="{x + w / 2}" cy="{base + 1.5}" rx="{w / 2 + 6}" ry="2.4" fill="#16211C" class="sh"></ellipse>'
    labels = "".join(f'<text x="{x - 8}" y="{base - (i + 1) * h + 9}" class="lbl lbl-end">{t}</text>'
                     for i, t in enumerate(("Rice", "Dal", "Sabzi", "Roti")))
    return (shadow + tiers + lid + steam + guide + labels
            + f'<text x="14" y="114" class="lbl">{min(n, full)} of {full} tiers · one per quarter of the budget</text>' + over_note(ctx, p, over))


# ---------------------------------------------------------------- 4. a coffee cup that fills
def opt_coffee(ctx, c, p, label, over):
    """A café cup fills to the budget line; the foam and a heart go on top; past the budget it spills."""
    x0, x1, top, bot = 92, 152, 40, 92
    fill = min(p, 1)
    level = bot - (bot - top - 4) * fill
    cup = (f'<path d="M{x0} {top} H{x1} L{x1 - 6} {bot} Q{(x0 + x1) / 2} {bot + 5} {x0 + 6} {bot} Z" fill="#FFFFFF" stroke="{GREY}" class="w2"></path>'
           f'<path d="M{x1 - 1} 50 Q{x1 + 16} 50 {x1 + 14} 64 Q{x1 + 12} 76 {x1 - 4} 76" stroke="{GREY}" class="w4"></path>'
           f'<ellipse cx="{(x0 + x1) / 2}" cy="{bot + 4}" rx="44" ry="5" fill="#E7ECE8"></ellipse>')
    k = ctx.anim(f"0%{{transform:translateY({bot - level:.1f}px)}}62%,100%{{transform:translateY(0)}}", timing="cubic-bezier(.3,.6,.3,1)")
    coffee = (f'<g class="{k}"><path d="M{x0 + 1.5} {level} H{x1 - 1.5} L{x1 - 6.5} {bot - 0.5} Q{(x0 + x1) / 2} {bot + 4} {x0 + 6.5} {bot - 0.5} Z" fill="#6F4426"></path>'
              f'<path d="M{x0 + 1.5} {level} H{x1 - 1.5} L{x1 - 2.4} {level + 5} H{x0 + 2.4} Z" fill="#C8955F"></path></g>')
    foam_k = ctx.anim("0%,62%{opacity:0;transform:scale(.4)}70%,100%{opacity:1;transform:scale(1)}", box="fill-box", origin="center")
    heart = (f'<g class="{foam_k}"><path d="M{(x0 + x1) / 2} {level + 3.6} q-6 -3 -5 -6 q1.4 -3 5 -0.6 q3.6 -2.4 5 0.6 q1 3 -5 6 Z" fill="#F7EBD8"></path></g>'
             if p <= 1 else "")
    steam = ""
    for i, dx in enumerate((-12, 0, 12)):
        ks = ctx.anim("0%,40%{opacity:0;transform:translateY(4px)}60%{opacity:.8}90%,100%{opacity:0;transform:translateY(-10px)}", delay=i * 0.3)
        steam += f'<path class="w15 {ks}" d="M{(x0 + x1) / 2 + dx} {top - 6} q-3 -4 0 -8 q3 -4 0 -8" stroke="#C9D3CD"></path>'
    spill = ""
    if p > 1:
        kd = ctx.anim("0%,62%{opacity:0;transform:scaleY(0)}74%,100%{opacity:1;transform:scaleY(1)}", box="fill-box", origin="top")
        spill = (f'<g class="{kd}"><path d="M{x1 - 1} {top} q4 6 2 16 q-1 5 1 10" stroke="{RED}" class="w3"></path>'
                 f'<ellipse cx="{x1 + 6}" cy="{bot + 6}" rx="10" ry="2" fill="{RED}" style="fill-opacity: .6"></ellipse></g>')
    guide = (f'<path d="M{x0 - 6} {top + 4} H232" stroke="{c}" class="w15 dash"></path>'
             f'<text x="236" y="{top}" class="lbl lbl-end" style="{HALO}">{label}</text>')
    return (cup + coffee + heart + steam + spill + guide
            + f'<text x="14" y="114" class="lbl">{round(min(p, 1) * 100)}% full · the brim is the budget</text>' + over_note(ctx, p, over))


# ---------------------------------------------------------------- 5. dishes arrive at the table
DISHES = [
    ("Dosa", '<ellipse cx="0" cy="0" rx="11" ry="4" fill="#FFFFFF" stroke="#DCE3DE" style="stroke-width: .7"></ellipse>'
             '<path d="M-10 0.6 L9 -3 Q11 -1.2 9 1.4 Z" fill="#D9A766"></path><circle cx="-6" cy="2" r="1.6" fill="#F2F0E6"></circle><circle cx="-2.6" cy="2.4" r="1.4" fill="#C8423B"></circle>'),
    ("Biryani", '<ellipse cx="0" cy="0" rx="9" ry="3.4" fill="#C9D3CD"></ellipse><path d="M-8 -0.6 Q-6 -8 0 -8.6 Q6 -8 8 -0.6 Z" fill="#E9B949"></path>'
                '<path d="M-3 -6 l1 -.4 M2 -5 l1 -.4 M-1 -3 l1 -.4 M4 -2.4 l1 -.4" stroke="#F6F1E6" class="w1"></path><path d="M2 -9 Q6 -12 7 -9 Q5 -6 2 -7 Z" fill="#9A5A2E"></path>'),
    ("Burger", '<ellipse cx="0" cy="0" rx="10" ry="3.6" fill="#FFFFFF" stroke="#DCE3DE" style="stroke-width: .7"></ellipse>'
               '<path d="M-6 -1 H6 Q6 0.6 4.6 0.6 H-4.6 Q-6 0.6 -6 -1 Z" fill="#C98A48"></path><rect x="-6.6" y="-3.6" width="13.2" height="2.6" rx="1.2" fill="#6F4426"></rect>'
               '<path d="M-6.8 -3.6 H6.8" stroke="#7FB04A" class="w15"></path><path d="M-6 -4 Q-6 -10 0 -10 Q6 -10 6 -4 Z" fill="#D9A766"></path>'),
    ("Noodles", '<path d="M-8 -4 H8 Q7 3 0 3 Q-7 3 -8 -4 Z" fill="#E86F5A"></path><path d="M-7 -4 Q-3 -7 0 -4.4 Q3 -7.4 7 -4" stroke="#F2C861" class="w2"></path>'
                '<path d="M2 -6 L10 -14 M4 -5 L11.4 -12.6" stroke="#9A5A2E" class="w1"></path>'),
    ("Juice", '<path d="M-3.6 -10 H3.6 L2.6 2 H-2.6 Z" fill="#F29A2E"></path><path d="M-3.6 -10 H3.6" stroke="#DCE3DE" class="w1"></path><path d="M1 -10 L4 -15" stroke="#E86F93" class="w1"></path>'
              '<circle cx="3.6" cy="-10" r="2.2" fill="#F5C26B"></circle>'),
    ("Ice cream", '<path d="M-3.6 -3 L0 6 L3.6 -3 Z" fill="#D9A766"></path><circle cx="-1.8" cy="-5" r="3.4" fill="#F7C6D5"></circle><circle cx="2" cy="-5.4" r="3.2" fill="#F7EBD8"></circle>'
                  '<circle cx="0" cy="-8.6" r="3" fill="#8A5A3C"></circle>'),
    ("Pizza", '<path d="M-8 2 L8 2 L0 -11 Z" fill="#F2C861"></path><path d="M-8.6 2.6 H8.6" stroke="#D9A766" class="w3"></path>'
              '<circle cx="-1.6" cy="-2" r="1.6" fill="#C8423B"></circle><circle cx="2" cy="-5.6" r="1.4" fill="#C8423B"></circle>'),
    ("Coffee", '<ellipse cx="0" cy="2" rx="7" ry="2.2" fill="#E7ECE8"></ellipse><path d="M-4.4 -6 H4.4 L3.6 1.6 H-3.6 Z" fill="#FFFFFF" stroke="#DCE3DE" style="stroke-width: .7"></path>'
               '<ellipse cx="0" cy="-6" rx="4.2" ry="1.2" fill="#6F4426"></ellipse><path d="M4.2 -4.4 Q7 -4 6 -1.4 L3.8 -1" stroke="#DCE3DE" class="w1"></path>'),
]


def opt_table(ctx, c, p, label, over):
    """A table that fills with dishes, one for each eighth of the budget."""
    full = 8
    n = max(1, round(p * full))
    table = (f'<path d="M34 60 H214 L236 96 H12 Z" fill="#F4E6D0"></path><path d="M12 96 H236 V100 H12 Z" fill="#D9BE95"></path>'
             f'<path d="M34 60 H214" stroke="#E3CFAE" class="w1"></path>'
             + "".join(f'<path d="M{x} 60 L{x - 12} 96" stroke="#EBDABD" style="stroke-width: .6"></path>' for x in range(64, 214, 30)))
    spots = [(66, 69), (110, 69), (154, 69), (198, 69), (50, 87), (100, 87), (150, 87), (200, 87), (122, 52), (166, 52)]
    dishes = ""
    for i in range(n):
        x, y = spots[min(i, len(spots) - 1)]
        sc = 1.75 if y > 80 else (1.45 if y > 60 else 1.3)
        at = 10 + i * (54 / n)
        k = ctx.anim(f"0%,{at:.1f}%{{opacity:0;transform:translate({x + 40}px,{y - 6}px) scale({sc})}}{at + 4:.1f}%{{opacity:1;transform:translate({x - 1}px,{y}px) scale({sc})}}"
                     f"{at + 5.5:.1f}%,100%{{opacity:1;transform:translate({x}px,{y}px) scale({sc})}}", timing="cubic-bezier(.2,.8,.2,1)", box="view-box", origin="0 0")
        tag = f'<circle cx="8" cy="-9" r="3.2" fill="{RED}"></circle>' if i >= full else ""
        dishes += f'<g class="{k}">{DISHES[i % len(DISHES)][1]}{tag}</g>'
    ksteam = ctx.anim("0%,40%{opacity:0;transform:translateY(3px)}60%{opacity:.8}90%,100%{opacity:0;transform:translateY(-8px)}")
    lamp = (f'<path d="M124 0 V14" stroke="#9AA6A0" class="w1"></path><path d="M112 22 Q112 12 124 12 Q136 12 136 22 Z" fill="{c}"></path>'
            f'<ellipse cx="124" cy="22" rx="12" ry="2" fill="{GOLD}" style="fill-opacity: .7"></ellipse>')
    return (table + dishes + f'<path class="w1 {ksteam}" d="M110 56 q-2 -3 0 -6 q2 -3 0 -6" stroke="#C9D3CD"></path>'
            + f'<text x="236" y="36" class="lbl lbl-end">{label}</text><text x="236" y="48" class="lbl lbl-end">a full table</text>'
            + f'<text x="14" y="114" class="lbl">{min(n, full)} of {full} dishes · one per eighth of the budget</text>' + over_note(ctx, p, over))


OPTIONS = [
    ("1 · Order on its way", opt_delivery, 5.0,
     "A delivery rider carries the order down the road to the house. How far they get is how much of the budget is used."),
    ("2 · Pizza, slice by slice", opt_pizza, 5.0,
     "The whole pizza is the budget. A slice is eaten for each eighth you spend; over the budget, a second pizza starts."),
    ("3 · Tiffin stacks up", opt_tiffin, 5.0,
     "A steel tiffin: a tier drops on for each quarter of the budget, then the lid clips on and it steams."),
    ("4 · Coffee cup fills", opt_coffee, 5.0,
     "The cup fills to the brim, which is the budget, and gets a heart on top. Past the budget it spills over."),
    ("5 · Dishes reach the table", opt_table, 5.0,
     "Dosa, biryani, a burger, noodles and more are served, one for each eighth of the budget."),
]


def board():
    css, cards = [BASE_CSS, EXTRA_CSS], []
    spent, budget = 9000, 12000
    for i, (title, fn, dur, caption) in enumerate(OPTIONS):
        for j, (sp, note) in enumerate(((spent, "75% used"), (13900, "over budget"))):
            ctx = Ctx(f"f{i}{j}", True, dur)
            p = sp / budget
            over = inr(sp - budget) + " over" if sp > budget else ""
            svg = fn(ctx, C, p, f"Budget {inr(budget)}", over)
            css += ctx.css
            cards.append((i, j, title, caption, note, svg))
    blocks = []
    for i, (title, fn, dur, caption) in enumerate(OPTIONS):
        pair = [cd for cd in cards if cd[0] == i]
        scenes = "".join(f"""<div style="display: flex; flex-direction: column; gap: 6px">
<span style="font-size: 13px; color: {MUTED}">{note}</span>
<div style="border-radius: 16px; overflow: hidden; background: {mix(C, '#FFFFFF', 0.92)}">{scene_box(svg, '', 1.4)}</div>
</div>""" for (_, _, _, _, note, svg) in pair)
        blocks.append(f"""<div style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 24px; padding: 18px 20px; display: flex; flex-direction: column; gap: 12px">
<div style="display: flex; align-items: baseline; gap: 14px">
<span style="font-size: 20px; font-weight: 700">{title}</span>
<span style="flex: 1; min-width: 0; font-size: 14px; color: {MUTED}; line-height: 1.4">{caption}</span>
</div>
<div style="display: flex; gap: 20px">{scenes}</div>
</div>""")
    body = f"""<div style="width: 820px; height: 2140px; box-sizing: border-box; padding: 36px 40px; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif; display: flex; flex-direction: column; gap: 20px">
<div style="display: flex; flex-direction: column; gap: 6px">
<h1 style="margin: 0; font-size: 28px; font-weight: 700">Food · pick one</h1>
<p style="margin: 0; font-size: 16px; color: {MUTED}">Five ideas to replace the thali. Each is shown at 75% of a ₹12,000 budget and over it. Tell me the number you like.</p>
</div>
{"".join(blocks)}
</div>"""
    return page("Food · pick one", body, STATIC, (820, 2140), "\n".join(css))


def canvas(base):
    idx = json.loads(base)
    if not any(pg["id"] == "food" for pg in idx["pages"]):
        idx["pages"].append({"id": "food", "name": "Food options"})
    idx["boards"]["FoodOptions.dc.html"] = {"x": 0, "y": 0, "w": 820, "h": 2140, "title": "Food · five options", "page": "food"}
    if "FoodOptions.dc.html" not in idx["order"]:
        idx["order"].append("FoodOptions.dc.html")
    idx["notes"]["food"] = {"x": 0, "y": -300, "text": "Food · pick one", "kind": "title1", "maxW": 820, "page": "food"}
    idx["launch"] = {"view": "canvas", "page": "food"}
    return json.dumps(idx, indent=1, ensure_ascii=False)


if __name__ == "__main__":
    if sys.argv[1] == "canvas":
        out = ("canvas.json", canvas(open(sys.argv[2]).read()))
    else:
        out = ("FoodOptions.dc.html", board())
    with open(ROOT + out[0], "w") as f:
        f.write(out[1])
    print("wrote", out[0], len(out[1]))
