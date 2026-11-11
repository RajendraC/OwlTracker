"""Round 5 · bright, glossy objects for the category pictures.

Every object is drawn in scene units with its base at y=0, centred on x=0, using radial gradients for volume,
a blurred white highlight and a soft contact shadow. Painter keeps the <defs> for one SVG.
"""
import math


class P:
    def __init__(self, uid):
        self.uid, self.n, self.defs, self.memo = uid, 0, [], {}

    def _new(self, key, make):
        if key not in self.memo:
            self.n += 1
            i = f"{self.uid}d{self.n}"
            self.defs.append(make(i))
            self.memo[key] = f"url(#{i})"
        return self.memo[key]

    def rad(self, c0, c1, c2, cx=0.34, cy=0.3, r=0.85):
        return self._new(("r", c0, c1, c2, cx, cy, r), lambda i: (
            f'<radialGradient id="{i}" cx="{cx}" cy="{cy}" r="{r}">'
            f'<stop offset="0" stop-color="{c0}"></stop><stop offset="0.5" stop-color="{c1}"></stop>'
            f'<stop offset="1" stop-color="{c2}"></stop></radialGradient>'))

    def lin(self, c0, c1, x2=0, y2=1, c2=None):
        stops = [(0, c0), (1, c1)] if c2 is None else [(0, c0), (0.55, c1), (1, c2)]
        return self._new(("l", c0, c1, x2, y2, c2), lambda i: (
            f'<linearGradient id="{i}" x1="0" y1="0" x2="{x2}" y2="{y2}">'
            + "".join(f'<stop offset="{o}" stop-color="{c}"></stop>' for o, c in stops) + "</linearGradient>"))

    def blur(self, sd):
        return self._new(("b", sd), lambda i: (
            f'<filter id="{i}" x="-60%" y="-60%" width="220%" height="220%">'
            f'<feGaussianBlur stdDeviation="{sd}"></feGaussianBlur></filter>'))

    def defs_svg(self):
        return "<defs>" + "".join(self.defs) + "</defs>"


def f1(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def gloss(p, cx, cy, rx, ry, rot=-28, op=0.6):
    return (f'<ellipse cx="{f1(cx)}" cy="{f1(cy)}" rx="{f1(rx)}" ry="{f1(ry)}" fill="#FFFFFF" opacity="{op}" '
            f'filter="{p.blur(0.7)}" transform="rotate({rot} {f1(cx)} {f1(cy)})"></ellipse>')


def shadow(p, rx, op=0.2, cx=0, cy=0):
    return (f'<ellipse cx="{f1(cx)}" cy="{f1(cy)}" rx="{f1(rx)}" ry="{f1(max(rx * 0.2, 1.4))}" fill="#3A2412" '
            f'opacity="{op}" filter="{p.blur(1.4)}"></ellipse>')


def star_path(cx, cy, ro, ri, n=5, sy=1.0, rot=-90):
    pts = []
    for k in range(n * 2):
        r = ro if k % 2 == 0 else ri
        a = math.radians(rot + k * 180 / n)
        pts.append(f"{f1(cx + r * math.cos(a))},{f1(cy + r * math.sin(a) * sy)}")
    return "M" + " L".join(pts) + " Z"


def sparkle(cx, cy, r, color="#FFFFFF"):
    d = (f"M{f1(cx)},{f1(cy - r)} Q{f1(cx + r * 0.18)},{f1(cy - r * 0.18)} {f1(cx + r)},{f1(cy)} "
         f"Q{f1(cx + r * 0.18)},{f1(cy + r * 0.18)} {f1(cx)},{f1(cy + r)} Q{f1(cx - r * 0.18)},{f1(cy + r * 0.18)} {f1(cx - r)},{f1(cy)} "
         f"Q{f1(cx - r * 0.18)},{f1(cy - r * 0.18)} {f1(cx)},{f1(cy - r)} Z")
    return f'<path d="{d}" fill="{color}"></path>'


# ---------------------------------------------------------------- produce
def tomato(p, r=12):
    return (shadow(p, r * 0.9)
            + f'<circle cx="0" cy="{f1(-r)}" r="{r}" fill="{p.rad("#FF9C80", "#F2402A", "#AE1D12")}"></circle>'
            + f'<path d="M{f1(-r * 0.55)},{f1(-r * 1.7)} Q{f1(-r * 0.2)},{f1(-r)} {f1(-r * 0.5)},{f1(-r * 0.25)}" stroke="#B81E12" stroke-width="1" fill="none" opacity="0.25"></path>'
            + f'<path d="{star_path(0, -r * 2 + 2.2, 6.5, 2.4, sy=0.55)}" fill="{p.lin("#4CC25B", "#1F7F32")}"></path>'
            + f'<path d="M0,{f1(-r * 2 + 2)} q0.6,-3.2 2.4,-4" stroke="#2D7A2E" stroke-width="1.6" stroke-linecap="round" fill="none"></path>'
            + gloss(p, -r * 0.38, -r * 1.42, r * 0.34, r * 0.19))


def orange(p, r=12):
    dots = "".join(f'<circle cx="{f1(x)}" cy="{f1(y)}" r="0.6" fill="#FFFFFF" opacity="0.28"></circle>'
                   for x, y in ((-3, -15), (4, -9), (6, -17), (-6, -8), (1, -5), (-1, -19), (7, -12)))
    return (shadow(p, r * 0.9)
            + f'<circle cx="0" cy="{f1(-r)}" r="{r}" fill="{p.rad("#FFD583", "#FF9A1A", "#D8630A")}"></circle>' + dots
            + f'<path d="M1.5,{f1(-r * 2 + 1)} c3,-6 10,-7 13,-4 c-3,5 -9,6 -13,4 z" fill="{p.lin("#7BD86A", "#2E9A3E")}"></path>'
            + f'<path d="M2,{f1(-r * 2 + 0.6)} q4,-3 10,-3.4" stroke="#1F7A30" stroke-width="0.6" fill="none" opacity="0.6"></path>'
            + gloss(p, -r * 0.38, -r * 1.42, r * 0.33, r * 0.19))


def apple(p, s=1.0):
    def q(d):
        return " ".join(f1(float(t) * s) if t.replace(".", "").replace("-", "").isdigit() else t for t in d.split())
    body = q("M 0 -1 C -15 1 -17 -22 -6 -25 C -3 -26 -1 -24.5 0 -23 C 1 -24.5 3 -26 6 -25 C 17 -22 15 1 0 -1 Z")
    return (shadow(p, 12 * s)
            + f'<path d="{body}" fill="{p.rad("#FF8E8E", "#E5262F", "#9C0E19", 0.32, 0.3)}"></path>'
            + f'<path d="M0,{f1(-22 * s)} q0.8,-5 3.2,-7" stroke="#6B3E1E" stroke-width="{f1(1.6 * s)}" stroke-linecap="round" fill="none"></path>'
            + f'<ellipse cx="{f1(6.5 * s)}" cy="{f1(-27.5 * s)}" rx="{f1(4.6 * s)}" ry="{f1(2.2 * s)}" fill="{p.lin("#7EDB5F", "#2E9A3E")}" transform="rotate(-24 {f1(6.5 * s)} {f1(-27.5 * s)})"></ellipse>'
            + gloss(p, -6.5 * s, -16 * s, 3.6 * s, 2.1 * s))


def broccoli(p):
    florets = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{p.rad("#93E06E", "#3FA131", "#1E6B1B")}"></circle>'
                      for x, y, r in ((-8, -17, 7), (8, -17, 7), (0, -23, 8.5), (-3.5, -13, 5.5), (4, -13, 5.5)))
    dots = "".join(f'<circle cx="{x}" cy="{y}" r="0.9" fill="#1C5E19" opacity="0.35"></circle>'
                   for x, y in ((-9, -18), (-2, -25), (3, -22), (9, -15), (-5, -14), (6, -19), (1, -28)))
    return (shadow(p, 9)
            + f'<path d="M-4.5,0 Q-5,-6 -3,-12 L3,-12 Q5,-6 4.5,0 Z" fill="{p.lin("#CDEFA0", "#7CC24E", 1, 0)}"></path>'
            + florets + dots + gloss(p, -5, -25, 3.4, 2))


def carrot(p):
    return (f'<path d="M0,0 C-3,-9 -6,-21 -6,-27 Q0,-31 6,-27 C6,-21 3,-9 0,0 Z" fill="{p.lin("#FFB257", "#FF6A0C", 1, 0)}"></path>'
            + "".join(f'<path d="M{a},{b} q2.4,0.8 4,0" stroke="#D9530A" stroke-width="0.9" fill="none" opacity="0.55"></path>' for a, b in ((-4.5, -20), (-2.5, -13), (-4, -24), (0.5, -17), (-1.5, -7)))
            + f'<path d="M-1,-29 Q-6,-38 -9,-40 M0,-29 Q0,-39 1,-43 M1,-29 Q6,-37 9,-38" stroke="#3FAE45" stroke-width="2.6" stroke-linecap="round" fill="none"></path>'
            + gloss(p, -3, -23, 1.6, 4, rot=8, op=0.5))


def baguette(p):
    return (f'<g transform="rotate(-72)"><rect x="-1" y="-8" width="46" height="11" rx="5.5" fill="{p.rad("#FADB98", "#E59A3C", "#B5631C", 0.4, 0.25)}"></rect>'
            + "".join(f'<path d="M{x},-6 q3,2.5 6,0.4" stroke="#FCE7B4" stroke-width="1.6" stroke-linecap="round" fill="none"></path>' for x in (10, 19, 28, 37))
            + "</g>")


def milk(p):
    return (shadow(p, 8)
            + f'<path d="M-7.5,0 L-7.5,-19 Q-7.5,-23 -4.2,-26 L-4.2,-29 L4.2,-29 L4.2,-26 Q7.5,-23 7.5,-19 L7.5,0 Z" fill="{p.rad("#FFFFFF", "#F1F4F7", "#C3CDD6", 0.3, 0.3)}"></path>'
            + f'<rect x="-5" y="-34" width="10" height="5.6" rx="1.8" fill="{p.rad("#7FB6FF", "#2F80ED", "#1A55B0")}"></rect>'
            + f'<rect x="-7.5" y="-15" width="15" height="8" fill="{p.lin("#4E9BFF", "#2F6FD8")}"></rect>'
            + '<path d="M-5,-10.5 q2.5,-2.4 5,0 t5,0" stroke="#FFFFFF" stroke-width="1.2" fill="none"></path>'
            + gloss(p, -4, -21, 1.6, 5, rot=4, op=0.75))


def lettuce(p):
    leaves = "".join(f'<ellipse cx="{x}" cy="{y}" rx="{rx}" ry="{ry}" fill="{p.rad(a, b, c, 0.4, 0.3)}" transform="rotate({r} {x} {y})"></ellipse>'
                     for x, y, rx, ry, r, a, b, c in ((-7, -16, 7, 13, -24, "#C9F29A", "#6CC447", "#2E8A2A"),
                                                      (7, -16, 7, 13, 24, "#C9F29A", "#6CC447", "#2E8A2A"),
                                                      (0, -19, 7.5, 15, 0, "#DBF8B6", "#86D35C", "#3C9A34")))
    veins = '<path d="M0,-4 L0,-30 M-6,-6 L-10,-26 M6,-6 L10,-26" stroke="#EAFBD0" stroke-width="1" fill="none" opacity="0.7"></path>'
    return shadow(p, 10) + leaves + veins


def banana(p):
    return (shadow(p, 13)
            + f'<path d="M-15,-6 C-9,3 10,3 16,-11 C17,-13 15.5,-14 14.2,-12.2 C8,-2.5 -6,-2 -12.6,-8.6 Z" fill="{p.lin("#FFEC80", "#FFC21A")}"></path>'
            + '<path d="M-14,-6.5 C-8,0 8,0 15,-11" stroke="#E2A400" stroke-width="0.9" fill="none" opacity="0.6"></path>'
            + '<circle cx="-14.6" cy="-7" r="1.6" fill="#6B4A1E"></circle><path d="M15,-12 l2.4,-2.6" stroke="#6B4A1E" stroke-width="2" stroke-linecap="round"></path>')


def avocado(p):
    return (shadow(p, 10)
            + f'<path d="M0,0 C-11,0 -12,-12 -8,-20 C-5,-26 -3,-31 0,-31 C3,-31 5,-26 8,-20 C12,-12 11,0 0,0 Z" fill="{p.rad("#9BD86A", "#3E8E2E", "#1F5A1A")}"></path>'
            + '<path d="M0,-2.5 C-8,-2.5 -8.6,-11 -6,-17 C-4,-22 -2.4,-26 0,-26 C2.4,-26 4,-22 6,-17 C8.6,-11 8,-2.5 0,-2.5 Z" fill="#E6F4A8"></path>'
            + f'<circle cx="0" cy="-10" r="5" fill="{p.rad("#C08A5A", "#8A5226", "#5A3216")}"></circle>'
            + gloss(p, -2, -14, 1.6, 1, op=0.7))


# ---------------------------------------------------------------- café
def coffee(p, steam_cls=""):
    steam = "".join(f'<path class="{steam_cls}" d="M{x},-27 q-3,-4 0,-8 t0,-8" stroke="#FFFFFF" stroke-width="2.2" stroke-linecap="round" fill="none" opacity="0.85"></path>'
                    for x in (-3, 3)) if steam_cls else ""
    return (shadow(p, 14)
            + f'<ellipse cx="0" cy="-2.2" rx="15" ry="3.6" fill="{p.rad("#FFFFFF", "#F2ECE4", "#C9BBAA", 0.4, 0.2)}"></ellipse>'
            + '<path d="M10,-17 q7.5,-0.5 6.5,6 q-1,4.5 -7,3.4" stroke="#E7DED2" stroke-width="2.6" fill="none"></path>'
            + f'<path d="M-10.5,-4 L-11.5,-21 Q-11.5,-23 -9.5,-23 L9.5,-23 Q11.5,-23 11.5,-21 L10.5,-4 Q10.5,-2.4 8.5,-2.4 L-8.5,-2.4 Q-10.5,-2.4 -10.5,-4 Z" fill="{p.rad("#FFFFFF", "#F6F1EA", "#CDBFAE", 0.3, 0.3)}"></path>'
            + f'<path d="M-11.1,-16.5 L11.1,-16.5 L10.8,-9.5 L-10.8,-9.5 Z" fill="{p.lin("#FF8A57", "#E8552B", 1, 0)}"></path>'
            + f'<ellipse cx="0" cy="-22.5" rx="10" ry="2.6" fill="{p.rad("#B87849", "#6B3A1E", "#4A2410", 0.5, 0.5)}"></ellipse>'
            + '<path d="M0,-21.4 c-2.2,-1.6 -2.4,-3 -0.6,-3.2 c0.4,0 0.6,0.3 0.6,0.5 c0,-0.2 0.2,-0.5 0.6,-0.5 c1.8,0.2 1.6,1.6 -0.6,3.2 z" fill="#F5E2CB"></path>'
            + gloss(p, -7, -14, 1.5, 4.5, rot=4, op=0.7) + steam)


def croissant(p):
    segs = ((-13, -5, 5.5, 4, 55), (-7, -9.5, 6.5, 5, 35), (0, -11.5, 7.5, 6, 0), (7, -9.5, 6.5, 5, -35), (13, -5, 5.5, 4, -55))
    body = "".join(f'<ellipse cx="{x}" cy="{y}" rx="{rx}" ry="{ry}" fill="{p.rad("#FFE59A", "#EDA53D", "#B4661A", 0.4, 0.25)}" transform="rotate({r} {x} {y})"></ellipse>'
                   for x, y, rx, ry, r in segs)
    return shadow(p, 15) + body + gloss(p, -2, -15, 3, 1.3, rot=-5, op=0.65)


def burger(p):
    seeds = "".join(f'<ellipse cx="{x}" cy="{y}" rx="1.1" ry="0.6" fill="#FFF4D6" transform="rotate({r} {x} {y})"></ellipse>'
                    for x, y, r in ((-6, -25, -20), (0, -27, 10), (6, -25, 30), (-2, -22, 0), (4, -21.5, -15), (-8, -20.5, 25), (9, -20.5, -10)))
    return (shadow(p, 14)
            + f'<path d="M-13,-2 Q-13,-7 -10,-7 L10,-7 Q13,-7 13,-2 Q13,0 11,0 L-11,0 Q-13,0 -13,-2 Z" fill="{p.rad("#F4C27C", "#DB9443", "#A55E1E", 0.4, 0.2)}"></path>'
            + f'<rect x="-14" y="-12.4" width="28" height="5.8" rx="2.9" fill="{p.rad("#9C5A34", "#5A2E18", "#38190B", 0.4, 0.2)}"></rect>'
            + '<path d="M-14,-12.8 L14,-12.8 L12,-9.2 L8,-11.6 L4,-8.4 L0,-11.6 L-5,-9 L-9,-11.6 L-14,-9.2 Z" fill="#FFC83A"></path>'
            + '<path d="M-15,-13.8 q2.5,2.4 5,0 t5,0 t5,0 t5,0 t5,0 t5,0 l0,-1.4 l-30,0 z" fill="#5CC245"></path>'
            + f'<path d="M-14.5,-15.5 Q-14.5,-31 0,-31 Q14.5,-31 14.5,-15.5 Q14.5,-13.6 12.5,-13.6 L-12.5,-13.6 Q-14.5,-13.6 -14.5,-15.5 Z" fill="{p.rad("#FFD99A", "#EDA54C", "#B36A22", 0.35, 0.22)}"></path>'
            + seeds + gloss(p, -6, -26, 3.6, 1.8, op=0.55))


def donut(p):
    spr = "".join(f'<rect x="{x}" y="{y}" width="2.6" height="0.9" rx="0.45" fill="{c}" transform="rotate({r} {x} {y})"></rect>'
                  for x, y, r, c in ((-8, -12, 30, "#FFFFFF"), (-4, -15.5, -40, "#FFD43B"), (4, -15, 20, "#5CC8FF"), (8, -11, -30, "#FFFFFF"),
                                     (-9.5, -8.2, 70, "#7CE36A"), (6, -7.6, 50, "#FFD43B"), (-1.5, -6.6, -10, "#5CC8FF"), (1, -16.2, 80, "#7CE36A")))
    return (shadow(p, 14)
            + f'<ellipse cx="0" cy="-7" rx="14.5" ry="7.5" fill="{p.rad("#F2C27A", "#D78F3D", "#9C5A1E", 0.5, 0.3)}"></ellipse>'
            + f'<ellipse cx="0" cy="-10" rx="14.5" ry="7.5" fill="{p.rad("#F7D08E", "#E6A050", "#B66C26", 0.5, 0.3)}"></ellipse>'
            + f'<path d="M-12.6,-10.6 C-13,-15 -6,-17.4 0,-17.4 C7,-17.4 13.4,-15 12.8,-10.6 C12.4,-7.8 9,-5.2 6,-6.2 C3,-4.8 -2,-5 -5,-6 C-9,-4.8 -12.2,-7.6 -12.6,-10.6 Z" fill="{p.rad("#FFC2DC", "#FF5C9E", "#C9286C", 0.4, 0.25)}"></path>'
            + '<ellipse cx="0" cy="-11" rx="4" ry="2.1" fill="#A8622A"></ellipse>' + spr + gloss(p, -6, -14.5, 3, 1.2, rot=-8, op=0.6))


def cake(p):
    return (shadow(p, 15)
            + '<path d="M4,0 L16,-5 L16,-25 L4,-20 Z" fill="#F7A8C4"></path>'
            + f'<path d="M-14,0 L4,0 L4,-20 L-14,-20 Z" fill="{p.lin("#FFE2A0", "#F7C665")}"></path>'
            + '<rect x="-14" y="-14" width="18" height="2.6" fill="#FFF6EA"></rect><rect x="-14" y="-8" width="18" height="2.6" fill="#FF6F91"></rect>'
            + f'<path d="M-14,-20 L4,-20 L16,-25 L-2,-25 Z" fill="{p.lin("#FFFFFF", "#FFE3EE")}"></path>'
            + '<path d="M-14,-20 q2,2.6 4,0 t4,0 t4,0 t4,0 t2,0" stroke="#FFFFFF" stroke-width="2" fill="none"></path>'
            + f'<path d="M3,-31.5 C0,-35 -3,-31 -1,-28 L3,-24.5 L7,-28 C9,-31 6,-35 3,-31.5 Z" fill="{p.rad("#FF8A8A", "#F0263A", "#A30F1E")}"></path>'
            + '<path d="M1.5,-32.5 l1.5,-2.4 l1.5,2.4" stroke="#3FAE45" stroke-width="1.4" fill="none" stroke-linecap="round"></path>'
            + "".join(f'<circle cx="{x}" cy="{y}" r="0.45" fill="#FFE59A"></circle>' for x, y in ((1, -29), (4, -29.5), (2.6, -27), (5.4, -27.4)))
            + gloss(p, 1, -30, 1, 1.6, op=0.7))


def juice(p):
    return (shadow(p, 10)
            + '<rect x="2" y="-40" width="2.2" height="20" rx="1.1" fill="#FF5C9E" transform="rotate(14 3 -30)"></rect>'
            + f'<path d="M-8,0 L-10,-27 L10,-27 L8,0 Z" fill="{p.lin("#FFD15A", "#FF8A00")}"></path>'
            + '<path d="M-10,-27 L10,-27 L9.7,-23 L-9.7,-23 Z" fill="#FFE9A8" opacity="0.8"></path>'
            + '<path d="M-10.4,-28 L10.4,-28" stroke="#FFFFFF" stroke-width="1.2" opacity="0.8"></path>'
            + f'<path d="M5,-27 a7,7 0 0 1 12,-3 z" fill="{p.rad("#FFE07A", "#FFB21A", "#E07A0A", 0.5, 0.8)}"></path>'
            + gloss(p, -6, -14, 1.2, 8, rot=-4, op=0.55))


def macaron(p, top="#FF8FC0", dark="#E0558F"):
    return (shadow(p, 11)
            + f'<path d="M-11,-3 Q-11,-8 0,-8 Q11,-8 11,-3 Q11,0 0,0 Q-11,0 -11,-3 Z" fill="{p.rad(top, top, dark, 0.4, 0.2)}"></path>'
            + '<rect x="-10" y="-11" width="20" height="3.6" rx="1.8" fill="#FFF7EC"></rect>'
            + f'<path d="M-11,-11.4 Q-11,-21 0,-21 Q11,-21 11,-11.4 Q11,-10 9,-10 L-9,-10 Q-11,-10 -11,-11.4 Z" fill="{p.rad("#FFFFFF", top, dark, 0.35, 0.25)}"></path>'
            + gloss(p, -4, -17, 3, 1.4, op=0.6))


def kulhad(p):
    return (shadow(p, 10)
            + f'<path d="M-9,-24 L9,-24 L7,-1 Q7,0 6,0 L-6,0 Q-7,0 -7,-1 Z" fill="{p.rad("#F0A070", "#C8622E", "#8A3A16", 0.35, 0.3)}"></path>'
            + f'<ellipse cx="0" cy="-24" rx="9" ry="2.4" fill="{p.rad("#E8C49A", "#C99A66", "#A3743F", 0.5, 0.4)}"></ellipse>'
            + '<path d="M-7.5,-17 L7.5,-17" stroke="#8A3A16" stroke-width="0.8" opacity="0.4"></path>'
            + gloss(p, -4.5, -15, 1.4, 4.5, rot=6, op=0.45))


def board(p, w=124):
    h = w / 2
    return (shadow(p, h + 4, op=0.24, cy=2)
            + f'<rect x="{-h}" y="-6" width="{w}" height="9" rx="4.5" fill="#8A5226"></rect>'
            + f'<rect x="{-h}" y="-15" width="{w}" height="13" rx="6.5" fill="{p.rad("#F2C48A", "#D2945A", "#9C602E", 0.4, 0.2, 1.1)}"></rect>'
            + f'<circle cx="{h + 7}" cy="-8.5" r="7" fill="{p.rad("#F2C48A", "#D2945A", "#9C602E")}"></circle><circle cx="{h + 8}" cy="-8.5" r="2" fill="#7A4520"></circle>'
            + "".join(f'<path d="M{x},-12 q14,1.5 28,0" stroke="#B9783E" stroke-width="0.8" fill="none" opacity="0.5"></path>' for x in (-50, -12, 22)))


# ---------------------------------------------------------------- shopping
def bag(p, c0, c1, c2, w=26, h=30, mark="heart"):
    hw = w / 2
    marks = {"heart": f'<path d="M0,{f1(-h * 0.42 + 3)} c-3.6,-2.6 -3.8,-5.6 -1.4,-5.9 c0.8,-0.1 1.2,0.4 1.4,0.9 c0.2,-0.5 0.6,-1 1.4,-0.9 c2.4,0.3 2.2,3.3 -1.4,5.9 z" fill="{c1}"></path>',
             "star": f'<path d="{star_path(0, -h * 0.42, 3.6, 1.5)}" fill="{c1}"></path>',
             "leaf": f'<path d="M-2.6,{f1(-h * 0.42 + 2)} q0,-5 5.2,-5 q0,5 -5.2,5 z" fill="{c1}"></path>'}
    return (shadow(p, hw + 2)
            + f'<path d="M-6,{-h} q0,-12 6,-12 q6,0 6,12" stroke="{c2}" stroke-width="2.2" fill="none"></path>'
            + f'<path d="M{-hw},0 L{-hw + 2},{-h} L{hw - 2},{-h} L{hw},0 Z" fill="{p.rad(c0, c1, c2, 0.3, 0.25, 0.95)}"></path>'
            + f'<path d="M{hw - 5},{-h} L{hw - 2},{-h} L{hw},0 L{hw - 4},0 Z" fill="#000000" opacity="0.12"></path>'
            + f'<path d="M{-hw + 2},{-h} L{hw - 2},{-h} L{hw - 1.7},{-h + 4} L{-hw + 1.7},{-h + 4} Z" fill="#000000" opacity="0.12"></path>'
            + f'<circle cx="0" cy="{f1(-h * 0.42)}" r="6" fill="#FFFFFF" opacity="0.92"></circle>' + marks[mark]
            + gloss(p, -hw + 6, -h * 0.62, 1.6, h * 0.22, rot=4, op=0.5))


def gift(p):
    return (shadow(p, 13)
            + f'<rect x="-11" y="-20" width="22" height="20" rx="2" fill="{p.rad("#9AE8FF", "#2FB5F0", "#167FB8")}"></rect>'
            + f'<rect x="-13" y="-25" width="26" height="6" rx="2" fill="{p.rad("#B6EEFF", "#3CC0F5", "#1A86C0")}"></rect>'
            + '<rect x="-2" y="-25" width="4" height="25" fill="#FF4F8B"></rect>'
            + '<path d="M0,-25 C-9,-34 -12,-26 -3,-25 M0,-25 C9,-34 12,-26 3,-25" stroke="#FF4F8B" stroke-width="3" fill="none" stroke-linecap="round"></path>'
            + gloss(p, -6, -15, 1.6, 4, rot=4, op=0.55))


def sneaker(p, c0="#7C6CFF", c1="#5B47E8", c2="#3826B0"):
    return (shadow(p, 16)
            + f'<path d="M-16,-6 L-15,-15 Q-14,-19 -9,-18 L-3,-16 Q2,-15 5,-11 L13,-9 Q18,-8 18,-4 L18,-6 L-16,-6 Z" fill="{p.rad(c0, c1, c2, 0.3, 0.3)}"></path>'
            + '<path d="M-17,-1 Q-17,-6 -15,-6 L17,-6 Q19,-6 19,-3 Q19,0 16,0 L-15,0 Q-17,0 -17,-1 Z" fill="#FFFFFF"></path>'
            + '<path d="M-17,-2.6 L19,-2.6" stroke="#E2E6EF" stroke-width="1.2"></path>'
            + '<path d="M-8,-10 Q0,-6 10,-8" stroke="#FFD43B" stroke-width="2.2" fill="none" stroke-linecap="round"></path>'
            + '<path d="M-6,-16 l3,-1 M-3,-14.6 l3,-1 M0,-13 l3,-1" stroke="#FFFFFF" stroke-width="1.2" stroke-linecap="round"></path>'
            + gloss(p, -10, -14, 3, 1.4, op=0.5))


def perfume(p):
    return (shadow(p, 10)
            + f'<rect x="-3" y="-30" width="6" height="5" rx="1" fill="{p.rad("#FFE7A0", "#E8B33A", "#A97A12")}"></rect>'
            + f'<rect x="-10" y="-26" width="20" height="26" rx="6" fill="{p.rad("#FFE1F0", "#FF8CC6", "#D9488E", 0.3, 0.25)}"></rect>'
            + '<rect x="-6" y="-16" width="12" height="7" rx="1.6" fill="#FFFFFF" opacity="0.8"></rect>'
            + gloss(p, -5, -18, 1.5, 5, rot=4, op=0.7))


def handbag(p):
    return (shadow(p, 15)
            + '<path d="M-8,-20 q0,-10 8,-10 q8,0 8,10" stroke="#B3471E" stroke-width="2.6" fill="none"></path>'
            + f'<path d="M-15,-3 Q-15,-20 -11,-20 L11,-20 Q15,-20 15,-3 Q15,0 12,0 L-12,0 Q-15,0 -15,-3 Z" fill="{p.rad("#FFC48A", "#FF7A3D", "#C9461A", 0.3, 0.25)}"></path>'
            + '<path d="M-15,-12 L15,-12" stroke="#C9461A" stroke-width="1.2" opacity="0.6"></path>'
            + f'<rect x="-3" y="-14.5" width="6" height="5" rx="1.2" fill="{p.rad("#FFF0B0", "#F2C33A", "#B08612")}"></rect>'
            + gloss(p, -9, -14, 3, 1.4, op=0.5))


# ---------------------------------------------------------------- vehicles (facing right)
def wheel(p, x, r=5):
    return (f'<circle cx="{x}" cy="{-r}" r="{r}" fill="{p.rad("#5A6270", "#2A2F38", "#14171C", 0.4, 0.3)}"></circle>'
            f'<circle cx="{x}" cy="{-r}" r="{f1(r * 0.45)}" fill="#D9DEE6"></circle>')


def auto_rickshaw(p):
    return (shadow(p, 22)
            + f'<path d="M-22,-21 L-22,-31 Q-22,-38 -15,-38 L5,-38 Q12,-38 14,-31 L16,-21 Z" fill="{p.rad("#FFE680", "#FFC928", "#D99A0A", 0.3, 0.2)}"></path>'
            + '<path d="M-22,-21 L15,-21" stroke="#C98A0A" stroke-width="1.6"></path>'
            + f'<path d="M-21,-7 L-21,-21 L9,-21 L17,-13 L21,-9 L21,-5 Q21,-3 19,-3 L-19,-3 Q-21,-3 -21,-7 Z" fill="{p.rad("#6FE39A", "#1FA65A", "#127A40", 0.3, 0.3)}"></path>'
            + '<path d="M-19,-20 L-2,-20 L-2,-10 L-19,-10 Z" fill="#14402A" opacity="0.55"></path>'
            + f'<path d="M9,-21 L14,-30 L15.2,-21 Z" fill="{p.lin("#E6F6FF", "#9BD5F5")}"></path>'
            + '<circle cx="19" cy="-10" r="2" fill="#FFF7C2"></circle>'
            + wheel(p, -13, 5.2) + wheel(p, 14, 4.4)
            + gloss(p, -12, -32, 6, 1.6, rot=-4, op=0.6) + gloss(p, -6, -16, 6, 1.2, rot=0, op=0.35))


def taxi(p, c0="#FFE58A", c1="#FFC928", c2="#D9940A"):
    return (shadow(p, 26)
            + f'<path d="M-25,-6 Q-26,-15 -18,-16 L-12,-16 L-6,-25 Q-4,-27 0,-27 L11,-27 Q15,-27 17,-23 L21,-16 Q27,-15 27,-8 L27,-6 Q27,-3.5 24.5,-3.5 L-23,-3.5 Q-25,-3.5 -25,-6 Z" fill="{p.rad(c0, c1, c2, 0.3, 0.25)}"></path>'
            + f'<path d="M-4,-24 L1,-24 L1,-17 L-10,-17 Z" fill="{p.lin("#EAF7FF", "#93CDF2")}"></path>'
            + f'<path d="M3,-24 L11,-24 Q13.5,-24 15,-21 L18,-17 L3,-17 Z" fill="{p.lin("#EAF7FF", "#93CDF2")}"></path>'
            + '<rect x="-3" y="-30.5" width="9" height="3.6" rx="1.2" fill="#1B2430"></rect>'
            + '<path d="M-24,-11 L26,-11" stroke="#1B2430" stroke-width="1.8" stroke-dasharray="3 3"></path>'
            + '<circle cx="25" cy="-9" r="1.8" fill="#FFF7C2"></circle><rect x="-25.4" y="-10.6" width="2" height="3" fill="#FF3B3B"></rect>'
            + wheel(p, -14, 5.4) + wheel(p, 16, 5.4) + gloss(p, -12, -20, 6, 1.5, rot=-6, op=0.6))


def bus(p):
    wins = "".join(f'<rect x="{x}" y="-27" width="9" height="9" rx="2" fill="{p.lin("#EAF7FF", "#8CC8F0")}"></rect>' for x in (-26, -15, -4, 7))
    return (shadow(p, 32)
            + f'<rect x="-31" y="-34" width="62" height="30" rx="7" fill="{p.rad("#FF9A8A", "#F04438", "#B3221A", 0.3, 0.2)}"></rect>'
            + f'<rect x="-31" y="-14" width="62" height="5" fill="#FFFFFF" opacity="0.85"></rect>' + wins
            + f'<path d="M19,-28 L29,-28 Q30,-28 30,-26 L30,-16 L19,-16 Z" fill="{p.lin("#EAF7FF", "#8CC8F0")}"></path>'
            + '<rect x="18" y="-38" width="12" height="4.6" rx="1.6" fill="#FFD43B"></rect>'
            + '<circle cx="29" cy="-8" r="1.8" fill="#FFF7C2"></circle>'
            + wheel(p, -19, 5.4) + wheel(p, 19, 5.4) + gloss(p, -16, -31, 10, 1.4, rot=0, op=0.55))


def scooter(p):
    return (shadow(p, 18)
            + '<path d="M11,-12 L7,-34" stroke="#2A2F38" stroke-width="2.4" stroke-linecap="round"></path>'
            + '<path d="M3,-34 L11,-34" stroke="#2A2F38" stroke-width="2.6" stroke-linecap="round"></path>'
            + f'<path d="M-17,-6 Q-18,-16 -9,-17 L4,-17 L8,-9 L14,-8 Q16,-7 15,-5 L-15,-5 Q-17,-5 -17,-6 Z" fill="{p.rad("#8DF0E4", "#14B8A6", "#0B7A70", 0.3, 0.25)}"></path>'
            + f'<path d="M5,-17 L9,-30 Q12,-30 12,-27 L10,-12 Z" fill="{p.rad("#8DF0E4", "#14B8A6", "#0B7A70")}"></path>'
            + '<rect x="-15" y="-21" width="13" height="4.4" rx="2.2" fill="#2A2F38"></rect>'
            + '<circle cx="11.6" cy="-26" r="1.8" fill="#FFF7C2"></circle>'
            + wheel(p, -12, 5) + wheel(p, 12, 5) + gloss(p, -8, -14, 5, 1.2, rot=0, op=0.55))


def metro(p):
    wins = "".join(f'<rect x="{x}" y="-26" width="10" height="9" rx="2" fill="{p.lin("#F2ECFF", "#B9A8F5")}"></rect>' for x in (-30, -18, -6))
    return (shadow(p, 34)
            + '<rect x="-36" y="-2.4" width="72" height="2.4" fill="#8A93A3"></rect>'
            + f'<path d="M-35,-6 L-35,-28 Q-35,-32 -31,-32 L18,-32 Q30,-32 34,-18 L36,-10 Q36,-5 32,-5 L-34,-5 Z" fill="{p.rad("#C9B8FF", "#7C5CF0", "#4A2FB0", 0.3, 0.2)}"></path>'
            + wins + f'<path d="M8,-26 L20,-26 Q26,-26 28,-19 L28,-17 L8,-17 Z" fill="{p.lin("#F2ECFF", "#B9A8F5")}"></path>'
            + '<rect x="-35" y="-14" width="71" height="3" fill="#FFD43B"></rect>'
            + '<circle cx="-24" cy="-3" r="3" fill="#2A2F38"></circle><circle cx="22" cy="-3" r="3" fill="#2A2F38"></circle>'
            + gloss(p, -14, -29, 12, 1.2, rot=0, op=0.55))


# ---------------------------------------------------------------- travel
def suitcase(p):
    return (shadow(p, 32, op=0.24)
            + '<rect x="-12" y="-74" width="24" height="10" rx="5" fill="none" stroke="#9C3A12" stroke-width="3.4"></rect>'
            + f'<rect x="-31" y="-66" width="62" height="60" rx="10" fill="{p.rad("#FFB38A", "#FF6A2B", "#C4401A", 0.3, 0.25, 0.95)}"></rect>'
            + "".join(f'<rect x="{x}" y="-62" width="3.4" height="52" rx="1.7" fill="#FFFFFF" opacity="0.18"></rect>' for x in (-15, -1.7, 11.6))
            + '<rect x="-31" y="-38.5" width="62" height="3" fill="#C4401A" opacity="0.5"></rect>'
            + '<circle cx="-21" cy="-3" r="3.4" fill="#2A2F38"></circle><circle cx="21" cy="-3" r="3.4" fill="#2A2F38"></circle>'
            + gloss(p, -22, -52, 2.4, 9, rot=6, op=0.5))


def sticker(p, kind, c0, c1, r=9):
    icon = {
        "plane": '<path d="M-5,1 L5,-3 L6,-1 L-1,1.6 L-2.6,5 L-4,5 L-3.4,1.8 L-5.4,2 Z" fill="#FFFFFF"></path>',
        "palm": '<path d="M0,5 Q0.6,0 0,-2 M0,-2 Q-4,-5 -6,-2 M0,-2 Q4,-5 6,-2 M0,-2 Q-1,-6 -3,-6 M0,-2 Q1,-6 3,-6" stroke="#FFFFFF" stroke-width="1.6" stroke-linecap="round" fill="none"></path>',
        "heart": '<path d="M0,4 c-5,-3.4 -5.4,-7.6 -2,-8 c1,-0.1 1.6,0.5 2,1.2 c0.4,-0.7 1,-1.3 2,-1.2 c3.4,0.4 3,4.6 -2,8 z" fill="#FFFFFF"></path>',
        "star": f'<path d="{star_path(0, 0, 5, 2.2)}" fill="#FFFFFF"></path>',
        "mountain": '<path d="M-6,4 L-1.5,-3.5 L1,0 L2.6,-2 L6,4 Z" fill="#FFFFFF"></path><circle cx="3.5" cy="-4" r="1.3" fill="#FFFFFF"></circle>',
        "sun": '<circle cx="0" cy="0" r="2.8" fill="#FFFFFF"></circle><path d="M0,-6 V-4.4 M0,4.4 V6 M-6,0 H-4.4 M4.4,0 H6 M-4.2,-4.2 l1.1,1.1 M3.1,3.1 l1.1,1.1 M-4.2,4.2 l1.1,-1.1 M3.1,-3.1 l1.1,-1.1" stroke="#FFFFFF" stroke-width="1.3" stroke-linecap="round"></path>',
        "globe": '<circle cx="0" cy="0" r="5" fill="none" stroke="#FFFFFF" stroke-width="1.3"></circle><path d="M-5,0 H5 M0,-5 Q-3,0 0,5 M0,-5 Q3,0 0,5" stroke="#FFFFFF" stroke-width="1.1" fill="none"></path>',
        "camera": '<rect x="-5" y="-3" width="10" height="7" rx="1.6" fill="#FFFFFF"></rect><circle cx="0" cy="0.6" r="2" fill="' + c1 + '"></circle><rect x="-2" y="-4.6" width="4" height="2" rx="0.6" fill="#FFFFFF"></rect>',
    }[kind]
    return (f'<circle cx="0" cy="0" r="{r + 1.6}" fill="#FFFFFF"></circle>'
            f'<circle cx="0" cy="0" r="{r}" fill="{p.rad(c0, c1, c1, 0.35, 0.3)}"></circle>{icon}')


def plane(p):
    return (f'<path d="M-14,0 Q-14,-3 -10,-3 L10,-3 Q15,-3 16,0 Q15,3 10,3 L-10,3 Q-14,3 -14,0 Z" fill="{p.rad("#FFFFFF", "#EAF0F8", "#AFC0D6", 0.4, 0.3)}"></path>'
            '<path d="M-2,-2 L-8,-11 L-4,-11 L5,-2 Z" fill="#2F80ED"></path><path d="M-2,2 L-7,9 L-3.6,9 L4,2 Z" fill="#1F64C8"></path>'
            '<path d="M-12,-2 L-15,-8 L-12,-8 L-8,-2 Z" fill="#FF6A2B"></path>'
            + "".join(f'<circle cx="{x}" cy="-0.6" r="0.9" fill="#2F80ED"></circle>' for x in (-4, -1, 2, 5, 8))
            + '<path d="M13,-1.6 Q15,-1 15.6,0" stroke="#2F80ED" stroke-width="1.2" fill="none"></path>')


def cloud(p, s=1.0, op=0.95):
    return (f'<g opacity="{op}" transform="scale({s})"><path d="M-14,0 Q-18,0 -18,-4 Q-18,-9 -12,-9 Q-11,-16 -4,-16 Q2,-20 7,-14 Q14,-15 15,-8 Q20,-7 19,-3 Q19,0 15,0 Z" '
            f'fill="{p.rad("#FFFFFF", "#FFFFFF", "#DCEBFA", 0.4, 0.2)}"></path></g>')


# ---------------------------------------------------------------- health
def heart(p):
    return (shadow(p, 22, op=0.22)
            + f'<path d="M0,-2 C-28,-18 -27,-44 -13,-46 C-6,-47 -1.6,-42 0,-38 C1.6,-42 6,-47 13,-46 C27,-44 28,-18 0,-2 Z" fill="{p.rad("#FF9FBF", "#F2386A", "#AE1446", 0.32, 0.25)}"></path>'
            + '<path d="M-20,-26 L-9,-26 L-5,-33 L0,-17 L4,-29 L7,-26 L20,-26" stroke="#FFFFFF" stroke-width="2.4" stroke-linejoin="round" stroke-linecap="round" fill="none"></path>'
            + gloss(p, -13, -38, 5, 2.6, rot=-30, op=0.65))


def capsule(p, a="#5CC8FF", b="#FFFFFF", da="#1F86C8"):
    return (shadow(p, 10)
            + '<g transform="rotate(-28 0 -6)">'
            + f'<path d="M-12,-6 Q-12,-11.5 -6.5,-11.5 L0,-11.5 L0,-0.5 L-6.5,-0.5 Q-12,-0.5 -12,-6 Z" fill="{p.rad(a, a, da, 0.4, 0.2)}"></path>'
            + f'<path d="M12,-6 Q12,-11.5 6.5,-11.5 L0,-11.5 L0,-0.5 L6.5,-0.5 Q12,-0.5 12,-6 Z" fill="{p.rad("#FFFFFF", b, "#C9D2DC", 0.4, 0.2)}"></path>'
            + gloss(p, -5, -9.5, 4, 1.1, rot=0, op=0.7) + "</g>")


def pill_bottle(p):
    return (shadow(p, 10)
            + f'<rect x="-9" y="-27" width="18" height="27" rx="3" fill="{p.rad("#FFC27A", "#FF8A1F", "#C95A0A", 0.3, 0.25)}"></rect>'
            + f'<rect x="-10" y="-33" width="20" height="7" rx="2" fill="{p.rad("#FFFFFF", "#F2F4F7", "#C3CDD6")}"></rect>'
            + '<rect x="-9" y="-19" width="18" height="10" fill="#FFFFFF" opacity="0.9"></rect>'
            + '<path d="M-1.6,-14 h3.2 M0,-15.6 v3.2" stroke="#FF5C5C" stroke-width="1.6" stroke-linecap="round"></path>'
            + gloss(p, -5.5, -24, 1.4, 3, rot=4, op=0.6))


def dumbbell(p):
    plate = lambda x: f'<rect x="{x}" y="-15" width="6" height="15" rx="2" fill="{p.rad("#C9B8FF", "#7C5CF0", "#4A2FB0", 0.35, 0.2)}"></rect>'
    return (shadow(p, 16)
            + '<rect x="-12" y="-9" width="24" height="3" rx="1.5" fill="#A7B0BE"></rect>'
            + plate(-16) + plate(-10.5) + plate(4.5) + plate(10) + gloss(p, -13, -11, 1, 3, rot=0, op=0.6))


def water_bottle(p):
    return (shadow(p, 8)
            + f'<rect x="-3.4" y="-36" width="6.8" height="5" rx="1.6" fill="{p.rad("#9AE8FF", "#2FB5F0", "#167FB8")}"></rect>'
            + f'<path d="M-7,-1 L-7,-26 Q-7,-30 -3.4,-31 L3.4,-31 Q7,-30 7,-26 L7,-1 Q7,0 6,0 L-6,0 Q-7,0 -7,-1 Z" fill="{p.lin("#E9F8FF", "#7FD3F7", 1, 0)}" opacity="0.95"></path>'
            + '<path d="M-7,-14 L7,-14 L7,-1 Q7,0 6,0 L-6,0 Q-7,0 -7,-1 Z" fill="#3AAEEA" opacity="0.7"></path>'
            + gloss(p, -3.6, -22, 1.2, 6, rot=2, op=0.75))


def plaster(p):
    return (shadow(p, 12)
            + '<g transform="rotate(-20 0 -6)">'
            + f'<rect x="-14" y="-10" width="28" height="9" rx="4.5" fill="{p.rad("#FFE2C4", "#F4BE8C", "#C98A52", 0.4, 0.2)}"></rect>'
            + '<rect x="-5" y="-9.4" width="10" height="7.8" rx="1.6" fill="#FFF3E6"></rect>'
            + "".join(f'<circle cx="{x}" cy="{y}" r="0.5" fill="#C98A52"></circle>' for x, y in ((-10, -6), (-8, -4), (9, -6), (11, -4), (-10, -3.6), (10, -7.6)))
            + "</g>")


# ---------------------------------------------------------------- entertainment
def popcorn_bucket_back(p):
    return f'<ellipse cx="0" cy="-38" rx="22" ry="4" fill="#B81E1E"></ellipse>'


def popcorn_bucket(p):
    stripes = "".join(f'<path d="M{a},0 L{b},-38 L{c},-38 L{d},0 Z" fill="#FFFFFF"></path>'
                      for a, b, c, d in ((-13, -15.4, -9.2, -7.6), (-2, -3.2, 3.2, 2), (7.6, 9.2, 15.4, 13)))
    return (shadow(p, 20, op=0.24)
            + f'<path d="M-18,0 L-22,-38 L22,-38 L18,0 Z" fill="{p.rad("#FF8A7A", "#EF3B3B", "#A81818", 0.3, 0.3)}"></path>'
            + stripes
            + f'<path d="M-23,-41 L23,-41 L22.4,-35 L-22.4,-35 Z" fill="{p.rad("#FF8A7A", "#EF3B3B", "#A81818", 0.4, 0.2)}"></path>'
            + '<circle cx="0" cy="-20" r="8" fill="#FFD43B"></circle>'
            + f'<path d="{star_path(0, -20, 5, 2.1)}" fill="#EF3B3B"></path>'
            + gloss(p, -15, -26, 1.6, 8, rot=8, op=0.45))


def kernel(p):
    return "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{p.rad("#FFFFFF", "#FFF0BC", "#E8BE5C", 0.35, 0.3)}"></circle>'
                   for x, y, r in ((-2.4, -2.6, 2.8), (2.4, -2.8, 2.8), (0, -5.2, 3), (0.2, -1.4, 2.4)))


def ticket(p):
    return (shadow(p, 13)
            + '<g transform="rotate(-14 0 -8)">'
            + f'<path d="M-14,-15 L14,-15 L14,-11 A2.5,2.5 0 0 0 14,-6 L14,-1 L-14,-1 L-14,-6 A2.5,2.5 0 0 0 -14,-11 Z" fill="{p.rad("#FFE07A", "#FFB21A", "#D98A0A", 0.4, 0.3)}"></path>'
            + '<path d="M5,-14 V-2" stroke="#FFFFFF" stroke-width="1" stroke-dasharray="1.4 1.4"></path>'
            + f'<path d="{star_path(-4.5, -8, 3.6, 1.5)}" fill="#FFFFFF"></path>'
            + "</g>")


def glasses3d(p):
    return (shadow(p, 15)
            + '<path d="M-17,-11 L17,-11 L17,-4 Q17,-1 14,-1 L4,-1 Q2,-1 1,-3.6 Q0,-5 -1,-3.6 Q-2,-1 -4,-1 L-14,-1 Q-17,-1 -17,-4 Z" fill="#FFFFFF" stroke="#E2E6EF" stroke-width="0.6"></path>'
            + '<rect x="-14.6" y="-9" width="11.6" height="6.4" rx="2" fill="#FF4D6A"></rect>'
            + '<rect x="3" y="-9" width="11.6" height="6.4" rx="2" fill="#2FC6E8"></rect>'
            + gloss(p, -10, -7.5, 2.6, 0.8, rot=0, op=0.7) + gloss(p, 8, -7.5, 2.6, 0.8, rot=0, op=0.7))


def clapper(p):
    return (shadow(p, 14)
            + f'<rect x="-13" y="-17" width="26" height="17" rx="2" fill="{p.rad("#4A5260", "#22262E", "#101317", 0.3, 0.2)}"></rect>'
            + '<g transform="rotate(-16 -13 -17)"><rect x="-13" y="-23" width="26" height="6" rx="1" fill="#22262E"></rect>'
            + "".join(f'<path d="M{x},-23 L{x + 4},-23 L{x + 1},-17 L{x - 3},-17 Z" fill="#FFFFFF"></path>' for x in (-9, -1, 7)) + "</g>"
            + '<rect x="-9" y="-12" width="18" height="2" rx="1" fill="#FFFFFF" opacity="0.5"></rect><rect x="-9" y="-7" width="12" height="2" rx="1" fill="#FFFFFF" opacity="0.5"></rect>')


# ---------------------------------------------------------------- bills
def gauge(p, needle_cls, angle):
    segs, cols = 4, ("#3CC46B", "#B7DB3A", "#FFB21A", "#F04438")
    arcs = []
    for k in range(segs):
        a0, a1 = math.radians(150 + k * 60 + 1.5), math.radians(150 + (k + 1) * 60 - 1.5)
        r = 31
        arcs.append(f'<path d="M{f1(r * math.cos(a0))},{f1(-44 + r * math.sin(a0))} A{r},{r} 0 0 1 {f1(r * math.cos(a1))},{f1(-44 + r * math.sin(a1))}" '
                    f'stroke="{cols[k]}" stroke-width="7" stroke-linecap="round" fill="none"></path>')
    ticks = "".join(f'<path d="M{f1(22 * math.cos(math.radians(a)))},{f1(-44 + 22 * math.sin(math.radians(a)))} L{f1(25 * math.cos(math.radians(a)))},{f1(-44 + 25 * math.sin(math.radians(a)))}" stroke="#9AA4B2" stroke-width="1.2" stroke-linecap="round"></path>'
                    for a in range(150, 391, 30))
    return (shadow(p, 32, op=0.24)
            + f'<rect x="-10" y="-8" width="20" height="8" rx="3" fill="{p.rad("#E9EEF4", "#B7C2CF", "#7F8C9C")}"></rect>'
            + f'<circle cx="0" cy="-44" r="42" fill="{p.rad("#FFFFFF", "#D6DEE8", "#8C99AA", 0.3, 0.2)}"></circle>'
            + f'<circle cx="0" cy="-44" r="37" fill="{p.rad("#FFFFFF", "#FFFFFF", "#E9EEF4", 0.4, 0.3)}"></circle>'
            + "".join(arcs) + ticks
            + (f'<g transform="translate(0 -44)"><g class="{needle_cls}"><path d="M-2.4,0 L0,-27 L2.4,0 Z" fill="#1B2430"></path></g></g>' if needle_cls
               else f'<g transform="translate(0 -44) rotate({f1(angle)})"><path d="M-2.4,0 L0,-27 L2.4,0 Z" fill="#1B2430"></path></g>')
            + f'<circle cx="0" cy="-44" r="5" fill="{p.rad("#6A7586", "#2A2F38", "#14171C")}"></circle>'
            + gloss(p, -18, -66, 9, 3, rot=-30, op=0.75))


def utility(p, kind):
    cols = {"bulb": ("#FFF3A0", "#FFC928", "#D9940A"), "drop": ("#A9E6FF", "#2FA8F0", "#1670B8"),
            "flame": ("#FFC9A0", "#FF7A2B", "#C9461A"), "wifi": ("#DCCBFF", "#8B5CF6", "#5B2FC8")}[kind]
    icon = {
        "bulb": '<path d="M0,-9 a5.6,5.6 0 0 1 3.4,10 L3,3 L-3,3 L-3.4,1 A5.6,5.6 0 0 1 0,-9 Z" fill="#FFFFFF"></path><rect x="-2.6" y="4" width="5.2" height="2.2" rx="1" fill="#FFFFFF"></rect>',
        "drop": '<path d="M0,-9 C3,-4 6,-1 6,2.6 A6,6 0 0 1 -6,2.6 C-6,-1 -3,-4 0,-9 Z" fill="#FFFFFF"></path>',
        "flame": '<path d="M0,-9 C5,-4 6.4,0 5,4 A5.4,5.4 0 0 1 -5,4 C-6,0 -3,-2 -1.6,-6 C-0.4,-3 1.4,-3 0,-9 Z" fill="#FFFFFF"></path>',
        "wifi": '<path d="M-7,-2 a10,10 0 0 1 14,0 M-4.4,1 a6,6 0 0 1 8.8,0" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" fill="none"></path><circle cx="0" cy="4.4" r="1.6" fill="#FFFFFF"></circle>',
    }[kind]
    return (f'<circle cx="0" cy="0" r="12" fill="{p.rad(*cols, 0.32, 0.28)}"></circle>' + icon
            + gloss(p, -5, -6, 3.4, 1.8, op=0.6))


# ---------------------------------------------------------------- sports
def grass(p, w=130):
    return (shadow(p, w / 2 + 6, op=0.18, cy=3)
            + f'<ellipse cx="0" cy="-2" rx="{w / 2}" ry="9" fill="{p.rad("#C8F59A", "#6BCB4A", "#3F9A2E", 0.5, 0.2, 0.9)}"></ellipse>'
            + "".join(f'<path d="M{x},-4 l1,-3 l1,3" stroke="#3F9A2E" stroke-width="0.9" fill="none" opacity="0.6"></path>' for x in range(-50, 51, 13)))


def bat(p):
    return ('<g transform="rotate(18)">'
            + f'<rect x="-1.8" y="-46" width="3.6" height="14" rx="1.6" fill="{p.lin("#3B6FE0", "#2347A8", 1, 0)}"></rect>'
            + "".join(f'<path d="M-1.8,{y} l3.6,1.4" stroke="#FFFFFF" stroke-width="0.6" opacity="0.6"></path>' for y in (-44, -41, -38, -35))
            + f'<path d="M-5.5,-32 L5.5,-32 L5,-2 Q5,0 3,0 L-3,0 Q-5,0 -5,-2 Z" fill="{p.rad("#FFE3B0", "#F2C27A", "#C98E44", 0.3, 0.3)}"></path>'
            + '<path d="M0,-30 V-3" stroke="#D9A45C" stroke-width="1" opacity="0.6"></path>'
            + gloss(p, -2.6, -18, 1, 9, rot=0, op=0.5) + "</g>")


def cricket_ball(p, r=7):
    return (shadow(p, r)
            + f'<circle cx="0" cy="{-r}" r="{r}" fill="{p.rad("#FF8A8A", "#D9262F", "#8E0E16")}"></circle>'
            + f'<path d="M{f1(-r * 0.3)},{f1(-r * 1.95)} Q{f1(r * 0.4)},{-r} {f1(-r * 0.3)},{f1(-r * 0.05)}" stroke="#FFFFFF" stroke-width="0.8" stroke-dasharray="1 0.9" fill="none"></path>'
            + gloss(p, -r * 0.4, -r * 1.4, r * 0.32, r * 0.18))


def football(p, r=11):
    pents = "".join(f'<path d="{star_path(x, y - r, s, s * 0.82, n=5)}" fill="#1B2430"></path>'
                    for x, y, s in ((0, 0, 3.6), (-7, -4.6, 2.6), (7, -4.6, 2.6), (-4.4, 6.4, 2.6), (4.4, 6.4, 2.6)))
    return (shadow(p, r * 0.9)
            + f'<circle cx="0" cy="{-r}" r="{r}" fill="{p.rad("#FFFFFF", "#F2F4F8", "#AEB7C4")}"></circle>' + pents
            + gloss(p, -r * 0.4, -r * 1.45, r * 0.3, r * 0.17))


def shuttle(p):
    return (shadow(p, 7)
            + '<g transform="rotate(-24 0 -10)">'
            + f'<path d="M-3.6,-6 L-8,-22 L8,-22 L3.6,-6 Z" fill="{p.lin("#FFFFFF", "#E8EDF4")}" stroke="#D4DAE3" stroke-width="0.5"></path>'
            + '<path d="M-4.4,-12 L4.4,-12 M-2.6,-6 L-5,-22 M2.6,-6 L5,-22 M0,-6 L0,-22" stroke="#C9D0DA" stroke-width="0.6"></path>'
            + f'<path d="M-3.8,-6 L3.8,-6 Q3.8,0 0,0 Q-3.8,0 -3.8,-6 Z" fill="{p.rad("#FF8A8A", "#F04438", "#B3221A")}"></path>'
            + "</g>")


def racket(p):
    return ('<g transform="rotate(-22)">'
            + f'<rect x="-1.6" y="-16" width="3.2" height="16" rx="1.4" fill="{p.lin("#FF9A5A", "#E8552B", 1, 0)}"></rect>'
            + f'<ellipse cx="0" cy="-27" rx="8.5" ry="11" fill="none" stroke="{p.lin("#4DD4C0", "#109A88")}" stroke-width="2.4"></ellipse>'
            + "".join(f'<path d="M{x},-37 V-17" stroke="#FFFFFF" stroke-width="0.5" opacity="0.8"></path>' for x in (-5, -2.5, 0, 2.5, 5))
            + "".join(f'<path d="M-7,{y} H7" stroke="#FFFFFF" stroke-width="0.5" opacity="0.8"></path>' for y in (-33, -30, -27, -24, -21))
            + "</g>")


def paddle(p):
    return ('<g transform="rotate(16)">'
            + f'<rect x="-2" y="-12" width="4" height="12" rx="1.8" fill="#1B2430"></rect>'
            + f'<rect x="-9" y="-33" width="18" height="22" rx="8" fill="{p.rad("#9AF0E4", "#14B8A6", "#0B7A70", 0.3, 0.25)}"></rect>'
            + gloss(p, -4, -26, 1.6, 4, rot=0, op=0.55) + "</g>")


def pickleball(p, r=5.6):
    holes = "".join(f'<circle cx="{x}" cy="{y - r}" r="0.9" fill="#B89A0A" opacity="0.6"></circle>' for x, y in ((0, 0), (-2.8, -2.4), (2.8, -2.4), (-2.6, 2.6), (2.6, 2.6)))
    return shadow(p, r) + f'<circle cx="0" cy="{-r}" r="{r}" fill="{p.rad("#FBFF9A", "#E6F03A", "#B4BC12")}"></circle>' + holes


def trophy(p):
    return (shadow(p, 11)
            + f'<rect x="-9" y="-6" width="18" height="6" rx="1.6" fill="{p.rad("#6A4A2A", "#4A2E16", "#2A180A")}"></rect>'
            + f'<rect x="-2.4" y="-14" width="4.8" height="8" fill="{p.lin("#FFE7A0", "#D9A21A", 1, 0)}"></rect>'
            + '<path d="M-9,-31 q-7,0 -6,6 q1,5 7,5 M9,-31 q7,0 6,6 q-1,5 -7,5" stroke="#E8B33A" stroke-width="2.2" fill="none"></path>'
            + f'<path d="M-10,-33 L10,-33 L9,-23 Q8,-14 0,-14 Q-8,-14 -9,-23 Z" fill="{p.rad("#FFF3B0", "#F5C43A", "#B98A12", 0.3, 0.25)}"></path>'
            + f'<path d="{star_path(0, -24.5, 3.6, 1.5)}" fill="#FFFFFF" opacity="0.8"></path>'
            + gloss(p, -5.5, -27, 1.4, 3.6, rot=6, op=0.7))


# ---------------------------------------------------------------- money (UI tiles, hero)
def coin(p, r=9):
    return (f'<ellipse cx="0" cy="{-r + 2}" rx="{r}" ry="{r}" fill="#C98A12"></ellipse>'
            f'<circle cx="0" cy="{-r}" r="{r}" fill="{p.rad("#FFF3B0", "#F7C33A", "#C48A0E", 0.35, 0.3)}"></circle>'
            f'<circle cx="0" cy="{-r}" r="{f1(r * 0.72)}" fill="none" stroke="#FFFFFF" stroke-width="0.9" opacity="0.55"></circle>'
            f'<text x="0" y="{f1(-r + r * 0.36)}" text-anchor="middle" font-size="{f1(r * 0.95)}" font-weight="800" fill="#B07A08" font-family="Plus Jakarta Sans, system-ui, sans-serif">₹</text>'
            + gloss(p, -r * 0.4, -r * 1.4, r * 0.3, r * 0.16))


def piggy(p):
    return (shadow(p, 24, op=0.22)
            + f'<rect x="-16" y="-8" width="6" height="8" rx="2.4" fill="#E9729E"></rect><rect x="8" y="-8" width="6" height="8" rx="2.4" fill="#E9729E"></rect>'
            + f'<ellipse cx="0" cy="-20" rx="24" ry="17" fill="{p.rad("#FFD3E4", "#FF8FBA", "#D9497E", 0.32, 0.28)}"></ellipse>'
            + f'<path d="M-12,-34 L-8,-44 L-3,-36 Z" fill="#F577A6"></path>'
            + f'<ellipse cx="22" cy="-20" rx="6" ry="7" fill="{p.rad("#FFC2DA", "#FF8FBA", "#D9497E")}"></ellipse>'
            + '<circle cx="21" cy="-21" r="1.2" fill="#B3346A"></circle><circle cx="24" cy="-21" r="1.2" fill="#B3346A"></circle>'
            + '<circle cx="12" cy="-26" r="1.8" fill="#3A1A2A"></circle>'
            + '<rect x="-7" y="-37" width="12" height="2.6" rx="1.3" fill="#B3346A"></rect>'
            + '<path d="M-24,-22 q-6,-2 -5,-7" stroke="#F577A6" stroke-width="2" fill="none" stroke-linecap="round"></path>'
            + gloss(p, -10, -30, 7, 3, rot=-20, op=0.6))


def owl(p):
    """The app's own mascot, round and glossy, for the hero."""
    return (shadow(p, 20, op=0.22)
            + f'<ellipse cx="0" cy="-24" rx="20" ry="23" fill="{p.rad("#7FD8A8", "#1E9E62", "#0E6A40", 0.35, 0.25)}"></ellipse>'
            + '<path d="M-17,-40 L-12,-52 L-6,-44 Z M17,-40 L12,-52 L6,-44 Z" fill="#168A55"></path>'
            + f'<ellipse cx="0" cy="-16" rx="12" ry="12" fill="{p.rad("#E6FFF1", "#B8F0D2", "#8ED6B0")}"></ellipse>'
            + "".join(f'<circle cx="{x}" cy="-33" r="8" fill="#FFFFFF"></circle><circle cx="{x + 1}" cy="-32.5" r="4" fill="#16211C"></circle><circle cx="{x + 2.4}" cy="-34" r="1.3" fill="#FFFFFF"></circle>' for x in (-8, 8))
            + '<path d="M-2.6,-26 L0,-21.5 L2.6,-26 Z" fill="#FFB21A"></path>'
            + '<path d="M-6,-1 l-2,-3 M-3,-1 l0,-3 M6,-1 l2,-3 M3,-1 l0,-3" stroke="#FFB21A" stroke-width="1.8" stroke-linecap="round"></path>'
            + gloss(p, -10, -40, 5, 2.4, rot=-30, op=0.55))
