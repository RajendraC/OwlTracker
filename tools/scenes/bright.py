"""The bright category pictures, as the app draws them. This is the reference for app/src/main/assets/scenes/bright.js,
which is a line-by-line port (tools/scenes/check_bright.py compares the two).

Glossy objects come from a registry of pre-drawn pieces (ART: name -> (defs, markup)), exported to bright-art.js;
the composer here only places them, times their animations and writes the amount, bar and chip.

scene(spec, uid, loop, dur, width) -> (svg, css), where spec is the dict the app sends:
  kind, name, color, p (fraction of the budget, past 1 when over), spent, budget, sub, chip, over (bool), order, still
"""
import math

import art5 as A
import art6 as B

W, H = 320, 180
FONT = "Plus Jakarta Sans, system-ui, sans-serif"
DARK, MUTED, RED = "#17251D", "#4E5A53", "#C8281C"

# top tint, bottom tint, label ink, bar light, bar dark
LOOK = {
    "groceries": ("#E4F8C6", "#F8FDEE", "#2B6620", "#9BE06A", "#3E9E2E"),
    "food": ("#FFDDBE", "#FFF6EC", "#A63A08", "#FFB04D", "#EE5F18"),
    "shopping": ("#E9DFFF", "#F9F6FF", "#5A2EC0", "#B9A2FF", "#7552C4"),
    "transport": ("#D3EAFF", "#F3F9FF", "#1A589E", "#86C6FF", "#2A6FB5"),
    "travel": ("#CDF3EC", "#F1FCFA", "#0B6A62", "#6FE0CF", "#0F9488"),
    "health": ("#FFDCE8", "#FFF5F8", "#A81E52", "#FF9DBC", "#E0346A"),
    "entertainment": ("#F6DDF6", "#FDF5FD", "#8C2582", "#EBA6E6", "#B04FA5"),
    "bills": ("#FFF0BA", "#FFFBEA", "#7A5600", "#FFD966", "#DDA000"),
    "sports": ("#FFDDD4", "#FFF6F3", "#B42E24", "#FFA28E", "#E5483A"),
    "fuel": ("#FFE6CC", "#FFF8EF", "#8A400A", "#FFC07A", "#E07A1A"),
    "recharge": ("#FFE0EE", "#FFF6FA", "#A3195B", "#FF9CC8", "#D63B85"),
    "education": ("#DDE6FF", "#F5F8FF", "#2C46A8", "#94AEFF", "#3F5FD9"),
    "rent": ("#DCF4E6", "#F4FCF7", "#1F6B45", "#8EE0B0", "#2E9E62"),
    "emi": ("#E3E7F2", "#F7F8FC", "#3B4A6B", "#AAB7D6", "#55678F"),
    "investments": ("#D7F5E5", "#F2FCF6", "#11704A", "#7EDDB0", "#18A06A"),
    "cash": ("#EAF6D5", "#F9FDF1", "#46660E", "#C2E57A", "#7FAE22"),
    "transfers": ("#D8F1FA", "#F3FBFE", "#0F6383", "#86D4F0", "#1E9BC8"),
    "income": ("#FFF1C9", "#FFFBEE", "#7A5600", "#FFD966", "#DDA000"),
}


def f1(v):
    return A.f1(v)


def f2(v):
    return f"{v:.2f}"


def mix(hex_color, other, t):
    a = [int(hex_color[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(other[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(a, b))


def look(kind, color):
    if kind in LOOK:
        return LOOK[kind]
    return (mix(color, "#FFFFFF", 0.82), mix(color, "#FFFFFF", 0.95), mix(color, "#000000", 0.4), mix(color, "#FFFFFF", 0.45), color)


# ---------------------------------------------------------------- the pre-drawn pieces
def _groc_back(p):
    return (A.shadow(p, 34, op=0.24) + '<path d="M-14,-46 q0,-20 14,-20 q14,0 14,20" stroke="#0F5E30" stroke-width="3.6" fill="none"></path>'
            '<path d="M-27,-40 L-25,-46 L25,-46 L27,-40 Z" fill="#0E5A2E"></path>')


def _groc_front(p):
    return (f'<path d="M-30,0 L-27,-40 L27,-40 L30,0 Q30,3 27,3 L-27,3 Q-30,3 -30,0 Z" fill="{p.rad("#6EE29A", "#22A85A", "#12723A", 0.3, 0.25, 0.95)}"></path>'
            f'<rect x="-28.5" y="-44" width="57" height="7.5" rx="2.5" fill="{p.lin("#3CC874", "#178A47")}"></rect>'
            '<circle cx="0" cy="-19" r="8.5" fill="#FFFFFF" opacity="0.95"></circle>'
            '<path d="M-3.6,-15.6 q0,-7.4 7.2,-7.4 q0,7.4 -7.2,7.4 z M-3.6,-15.6 l5,-5" fill="#22A85A" stroke="#22A85A" stroke-width="0.8"></path>'
            + A.gloss(p, -21, -24, 2, 9, rot=4, op=0.45))


def _rug(p):
    return f'<ellipse cx="-4" cy="6" rx="72" ry="13" fill="{p.rad("#FFFFFF", "#F1EAFF", "#DCCEFF", 0.5, 0.3)}" opacity="0.9"></ellipse>'


def _road(p):
    return (f'<rect x="138" y="124" width="180" height="50" rx="25" fill="{p.lin("#7D8BA6", "#55627C")}"></rect>'
            '<rect x="138" y="124" width="180" height="50" rx="25" fill="none" stroke="#FFFFFF" stroke-width="2.4" opacity="0.6"></rect>'
            + "".join(f'<rect x="{x}" y="148" width="12" height="2.6" rx="1.3" fill="#FFFFFF" opacity="0.85"></rect>' for x in range(156, 312, 22)))


STICKERS = [("plane", "#8FD0FF", "#2F80ED"), ("palm", "#8EF0A8", "#1FA65A"), ("heart", "#FFA6C4", "#F2386A"), ("sun", "#FFE27A", "#F5A800"),
            ("globe", "#B6A5FF", "#7C5CF0"), ("camera", "#FFB48A", "#E8552B"), ("mountain", "#7EE0D2", "#0F9488"), ("star", "#FFD36B", "#F08A0A")]
BOOK_W = [46, 42, 44, 40, 43, 38, 41, 36]

ART_MAKERS = {
    "lettuce": A.lettuce, "baguette": A.baguette, "milk": A.milk, "carrot": A.carrot, "broccoli": A.broccoli, "apple": A.apple,
    "orange": A.orange, "tomato": A.tomato, "groc_back": _groc_back, "groc_front": _groc_front,
    "coffee": lambda p: A.coffee(p, "__STEAM__"), "burger": A.burger, "juice": A.juice, "kulhad": A.kulhad, "croissant": A.croissant,
    "donut": A.donut, "cake": A.cake, "macaron": A.macaron, "board": A.board,
    "bag_purple": lambda p: A.bag(p, "#C9B5FF", "#8B5CF6", "#5B2FC8", 26, 34, "star"),
    "bag_pink": lambda p: A.bag(p, "#FFB8D6", "#FF5FA2", "#C92E74", 28, 38, "heart"),
    "bag_teal": lambda p: A.bag(p, "#9AF0E4", "#14B8A6", "#0B7A70", 24, 30, "leaf"),
    "bag_yellow": lambda p: A.bag(p, "#FFE58A", "#FFC928", "#D9940A", 22, 26, "star"),
    "bag_orange": lambda p: A.bag(p, "#FFC48A", "#FF7A3D", "#C9461A", 22, 26, "heart"),
    "gift": A.gift, "handbag": A.handbag, "perfume": A.perfume, "sneaker": A.sneaker, "rug": _rug,
    "auto": A.auto_rickshaw, "taxi": A.taxi, "scooter": A.scooter, "bus": A.bus, "metro": A.metro,
    "taxi_blue": lambda p: A.taxi(p, "#9DD0FF", "#3B8BEB", "#1E5DB0"), "road": _road,
    "cloud": A.cloud, "plane": A.plane, "suitcase": A.suitcase,
    "heart": A.heart, "capsule": A.capsule, "capsule_pink": lambda p: A.capsule(p, "#FF8FBA", "#FFFFFF", "#C9286C"),
    "pill_bottle": A.pill_bottle, "dumbbell": A.dumbbell, "apple85": lambda p: A.apple(p, 0.85), "water_bottle": A.water_bottle,
    "avocado": A.avocado, "plaster": A.plaster,
    "popcorn_back": A.popcorn_bucket_back, "kernel": A.kernel, "popcorn": A.popcorn_bucket, "ticket": A.ticket, "glasses3d": A.glasses3d,
    "clapper": A.clapper, "gauge": lambda p: A.gauge(p, "__NEEDLE__", 0),
    "grass140": lambda p: A.grass(p, 140), "grass160": lambda p: A.grass(p, 160), "bat": A.bat, "football": A.football,
    "cricket_ball": A.cricket_ball, "racket": A.racket, "shuttle": A.shuttle, "paddle": A.paddle, "pickleball": A.pickleball, "trophy": A.trophy,
    "pump": B.pump, "drop_fuel": B.drop, "phone": B.phone, "grad_cap": B.grad_cap, "pencil": B.pencil, "house": B.house, "tree": B.tree,
    "light_win": lambda p: B.light(p, "win"), "light_round": lambda p: B.light(p, "round"), "light_lamp": lambda p: B.light(p, "lamp"),
    "calendar": B.calendar, "coin_flat": B.coin_flat, "coin": A.coin, "coin75": lambda p: A.coin(p, 7.5), "pot": B.pot,
    "leaf_r": lambda p: B.leaf(p, 1), "leaf_l": lambda p: B.leaf(p, -1), "wallet_back": B.wallet_back, "wallet_front": B.wallet_front,
    "note": B.note, "avatar_a": lambda p: B.avatar(p, "#DCCBFF", "#8B5CF6", "#5B2FC8"), "avatar_b": lambda p: B.avatar(p, "#8DF0E4", "#14B8A6", "#0B7A70"),
    "jar_back": B.jar_back, "jar_plus": lambda p: B.jar_front(p, "#F5B800", "plus"), "jar_dot": lambda p: B.jar_front(p, "#__TINT__", "dot"),
}
for _k in ("flame", "bulb", "drop", "wifi"):
    ART_MAKERS["utility_" + _k] = (lambda k: lambda p: A.utility(p, k))(_k)
for _s, _c0, _c1 in STICKERS:
    ART_MAKERS["sticker_" + _s] = (lambda s, c0, c1: lambda p: A.sticker(p, s, c0, c1))(_s, _c0, _c1)
for _k in B.APP_GLYPH:
    ART_MAKERS["app_" + _k] = (lambda k: lambda p: B.app_tile(p, k))(_k)
for _i, _w in enumerate(BOOK_W):
    ART_MAKERS["book" + str(_i)] = (lambda i, w: lambda p: B.book(p, i, w))(_i, _w)


def build_art():
    out = {}
    for name, make in ART_MAKERS.items():
        p = A.P("x" + name.replace("_", ""))
        body = make(p)
        out[name] = ("".join(p.defs), body)
    return out


ART = build_art()


# ---------------------------------------------------------------- composing
def inr(n):
    s = str(int(round(n)))
    neg = s.startswith("-")
    s = s.lstrip("-")
    if len(s) > 3:
        head, tail = s[:-3], s[-3:]
        parts = []
        while len(head) > 2:
            parts.insert(0, head[-2:])
            head = head[:-2]
        if head:
            parts.insert(0, head)
        s = ",".join(parts + [tail])
    return ("-" if neg else "") + "₹" + s


class Scene:
    """One picture: its own gradients, the pieces it uses and its keyframes."""

    def __init__(self, uid, loop, dur):
        self.uid, self.loop, self.dur, self.n, self.css = uid, loop, dur, 0, []
        self.defs, self.memo, self.used = [], {}, []

    # gradients of the picture itself (background, bar, fuel)
    def _def(self, key, make):
        if key not in self.memo:
            self.n += 1
            i = f"{self.uid}d{self.n}"
            self.defs.append(make(i))
            self.memo[key] = f"url(#{i})"
        return self.memo[key]

    def lin(self, c0, c1, x2=0, y2=1):
        return self._def("l" + c0 + c1 + str(x2) + str(y2), lambda i: (
            f'<linearGradient id="{i}" x1="0" y1="0" x2="{x2}" y2="{y2}"><stop offset="0" stop-color="{c0}"></stop>'
            f'<stop offset="1" stop-color="{c1}"></stop></linearGradient>'))

    def blur(self, sd):
        return self._def("b" + str(sd), lambda i: (
            f'<filter id="{i}" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="{sd}"></feGaussianBlur></filter>'))

    def art(self, name):
        if name not in self.used:
            self.used.append(name)
        return ART[name][1]

    # animations
    def pc(self, t):
        return max(0.0, min(100.0, t / self.dur * 100))

    def key(self, frames, dur=None, delay=0.0, count=None, timing="ease-out", origin="50% 100%"):
        self.n += 1
        name = f"{self.uid}a{self.n}"
        cnt = count or ("infinite" if self.loop else "1")
        self.css.append(f".{name}{{transform-box:fill-box;transform-origin:{origin};"
                        f"animation:{name} {dur or self.dur}s {timing} {delay}s {cnt} both}}@keyframes {name}{{{frames}}}")
        return name

    def end(self, d):
        if self.loop:
            return f"{f2(d + 0.01)}%,92%{{opacity:1;transform:none}}97%,100%{{opacity:0;transform:none}}"
        return f"{f2(d + 0.01)}%,100%{{opacity:1;transform:none}}"

    def enter(self, t, kind="drop", origin="50% 100%"):
        frm, span, land = ENTRIES[kind]
        a, b, c, d = self.pc(t - span), self.pc(t), self.pc(t + 0.15), self.pc(t + 0.3)
        ease_in = "cubic-bezier(.55,0,1,.45)" if kind in ("drop", "fly") else "cubic-bezier(.2,.8,.3,1)"
        mid = f"{f2((a + b) / 2)}%{{transform:translate(-60px,-96px) scale(.9)}}" if kind == "fly" else ""
        frames = (f"0%,{f2(a)}%{{opacity:0;transform:{frm};animation-timing-function:{ease_in}}}"
                  f"{f2(a + 0.05)}%{{opacity:1}}" + mid
                  + f"{f2(b)}%{{transform:{land};animation-timing-function:ease-out}}"
                  f"{f2(c)}%{{transform:{'translateY(-2px) scale(.97,1.04)' if kind in ('drop', 'rise', 'fly') else 'none'}}}"
                  f"{f2(d)}%{{transform:none}}" + self.end(d))
        return self.key(frames, origin=origin)

    def idle(self, kind, delay):
        frames, dur, origin = IDLES[kind]
        return self.key(frames, dur=dur, delay=delay, count="infinite", timing="ease-in-out", origin=origin)


ENTRIES = {
    "drop": ("translateY(-48px) scale(.92)", 0.42, "translateY(0) scale(1.12,.86)"),
    "rise": ("translateY(22px) scale(.85)", 0.38, "translateY(-3px) scale(.96,1.06)"),
    "pop": ("scale(0) rotate(-30deg)", 0.34, "scale(1.18) rotate(4deg)"),
    "drive": ("translateX(-70px)", 0.6, "translateX(3px) rotate(-2deg)"),
    "drive_l": ("translateX(70px)", 0.6, "translateX(-3px) rotate(2deg)"),
    "fade": ("translateY(6px)", 0.4, "translateY(0)"),
    "grow": ("scale(.6)", 0.4, "scale(1.06)"),
    "fly": ("translate(-120px,-70px) scale(.7)", 0.7, "translateY(0) scale(1.1,.88)"),
    "up": ("scaleY(0)", 0.9, "scaleY(1.04)"),
}
IDLES = {
    "bob": ("0%,100%{transform:translateY(0)}50%{transform:translateY(-2.2px)}", 2.4, "50% 100%"),
    "sway": ("0%,100%{transform:rotate(-3deg)}50%{transform:rotate(3deg)}", 2.8, "50% 100%"),
    "pulse": ("0%,100%{transform:scale(1)}12%{transform:scale(1.08)}24%{transform:scale(.98)}36%{transform:scale(1.05)}50%{transform:scale(1)}", 1.6, "50% 70%"),
    "bounce": ("0%,100%{transform:translateY(0)}45%{transform:translateY(-7px)}55%{transform:translateY(-7px)}", 1.4, "50% 100%"),
    "shake": ("0%,100%{transform:rotate(0)}25%{transform:rotate(-4deg)}75%{transform:rotate(4deg)}", 0.5, "50% 100%"),
    "twinkle": ("0%,100%{opacity:0;transform:scale(.4) rotate(0)}50%{opacity:1;transform:scale(1) rotate(45deg)}", 1.9, "50% 50%"),
    "steam": ("0%{opacity:0;transform:translateY(2px)}40%{opacity:.85}100%{opacity:0;transform:translateY(-7px)}", 2.2, "50% 100%"),
    "drip": ("0%{opacity:0;transform:translateY(0)}20%{opacity:1}100%{opacity:0;transform:translateY(16px)}", 1.2, "50% 100%"),
    "glow": ("0%,100%{opacity:.55}50%{opacity:1}", 1.8, "50% 50%"),
}


def g(x, y, s, inner, cls="", rot=0):
    tr = f"translate({f1(x)} {f1(y)})" + (f" scale({s})" if s != 1 else "") + (f" rotate({rot})" if rot else "")
    return f'<g transform="{tr}"><g class="{cls}">{inner}</g></g>'


def item(sc, x, y, s, inner, t, kind="drop", idle=None, rot=0):
    if t is None:
        return g(x, y, s, inner, rot=rot)
    cls = sc.enter(t, kind)
    if idle:
        inner = f'<g class="{sc.idle(idle, t + 0.5)}">{inner}</g>'
    return g(x, y, s, inner, cls, rot)


def times(k, start=0.8, total=2.3):
    step = min(0.34, total / max(k, 1))
    return [start + i * step for i in range(k)]


def count(f, n):
    return max(1, min(n, math.ceil(f * n - 1e-9))) if f > 0 else 0


def T(still, t):
    return None if still else t


def sparkle(x, y, r, c):
    return A.sparkle(x, y, r, c)


# ---------------------------------------------------------------- clusters
def groceries(sc, f, still, spec):
    slots = [("lettuce", -10, -32, 1.2, None), ("baguette", -21, -30, 1.0, None), ("milk", 16, -31, 1.12, None),
             ("carrot", 6, -30, 1.05, "sway"), ("broccoli", -2, -34, 1.05, None), ("apple", -13, -36, 1.0, None),
             ("orange", 13, -37, 1.0, None), ("tomato", 1, -37, 0.95, "bob")]
    k = count(f, 8)
    ts = times(k)
    inner = "".join(item(sc, x, y, s, sc.art(n), T(still, ts[i]), "rise", None if still else idle) for i, (n, x, y, s, idle) in enumerate(slots[:k]))
    spill = ""
    if f > 1:
        spill = item(sc, -48, 3, 0.8, sc.art("tomato"), T(still, 3.3), "drop") + item(sc, -66, 3, 0.75, sc.art("orange"), T(still, 3.6), "drop")
    bag = sc.art("groc_back") + inner + sc.art("groc_front")
    return item(sc, 240, 164, 1.25, bag + spill, T(still, 0.35), "grow")


def food(sc, f, still, spec):
    slots = [("coffee", -42, -11, 1.0), ("burger", -12, -11, 1.0), ("juice", 17, -11, 0.95), ("kulhad", 43, -11, 0.92),
             ("croissant", -32, -3, 1.0), ("donut", -1, -3, 1.0), ("cake", 28, -3, 0.92), ("macaron", 52, -3, 0.82)]
    k = count(f, 8)
    ts = times(k)
    parts = [sc.art("board")]
    for i, (n, x, y, s) in enumerate(slots[:k]):
        art = sc.art(n)
        if n == "coffee":
            art = art.replace('class="__STEAM__"', 'class="' + ("" if still else sc.idle("steam", ts[i] + 0.6)) + '"')
        parts.append(item(sc, x, y, s, art, T(still, ts[i]), "drop"))
    if f > 1:
        parts.append(item(sc, 74, 8, 0.8, sc.art("donut"), T(still, 3.4), "drop", rot=-30))
    return item(sc, 230, 162, 1.16, "".join(parts), T(still, 0.3), "fade")


def shopping(sc, f, still, spec):
    slots = [("bag_purple", -28, 0, 1.15), ("bag_pink", 2, 0, 1.15), ("bag_teal", 31, 0, 1.1), ("gift", -52, 10, 0.95),
             ("handbag", -18, 12, 1.0), ("perfume", 12, 12, 0.9), ("sneaker", 42, 12, 0.92), ("bag_yellow", 62, 6, 0.95)]
    k = count(f, 8)
    ts = times(k)
    parts = [sc.art("rug")] + [item(sc, x, y, s, sc.art(n), T(still, ts[i]), "drop", None if still or i != 1 else "bob")
                               for i, (n, x, y, s) in enumerate(slots[:k])]
    if f > 1:
        parts.append(item(sc, -76, 14, 0.8, sc.art("bag_orange"), T(still, 3.4), "drop", rot=-20))
    return g(238, 150, 1.14, "".join(parts))


TRANSPORT = {"bus": (196, 146, 0.86), "metro": (272, 146, 0.78), "auto": (176, 171, 1.05), "taxi": (238, 171, 1.0), "scooter": (294, 171, 1.0)}
TRANSPORT_PARTS = {"car": ["taxi"], "scooter": ["auto", "scooter"], "bus": ["bus"], "metro": ["metro"]}


def transport_order(order):
    seq = []
    for part in order:
        for v in TRANSPORT_PARTS.get(part, []):
            if v not in seq:
                seq.append(v)
    for v in ("bus", "metro", "auto", "taxi", "scooter"):
        if v not in seq:
            seq.append(v)
    return seq


def transport(sc, f, still, spec):
    seq = transport_order(spec.get("order", []))
    k = count(f, 5)
    ts = times(k, total=2.0)
    back, front = [], []
    for i in range(k):
        n = seq[i]
        x, y, s = TRANSPORT[n]
        el = item(sc, x, y, s, sc.art(n), T(still, ts[i]), "drive", None if still else "bob")
        (back if y < 150 else front).append(el)
    extra = item(sc, 296, 120, 0.6, sc.art("utility_flame"), T(still, 3.4), "pop", None if still else "pulse") if f > 1 else ""
    return sc.art("road") + "".join(back) + "".join(front) + extra


def travel(sc, f, still, spec):
    spots = [(-16, -52, -12), (8, -56, 10), (19, -40, -8), (-19, -31, 8), (1, -35, -6), (18, -18, 12), (-14, -13, -10), (4, -14, 6)]
    k = count(f, 8)
    ts = times(k, start=1.0)
    st = "".join(item(sc, x, y, 1.0, sc.art("sticker_" + STICKERS[i][0]), T(still, ts[i]), "pop", rot=r) for i, (x, y, r) in enumerate(spots[:k]))
    case = item(sc, 236, 164, 1.36, sc.art("suitcase") + st, T(still, 0.35), "drop")
    plane_cls = "" if still else sc.key(
        "0%{transform:translate(-36px,26px) rotate(-14deg);opacity:0}12%{opacity:1}50%{transform:translate(20px,-2px) rotate(-6deg)}"
        "86%{opacity:1}100%{transform:translate(64px,-20px) rotate(-12deg);opacity:0}", dur=4.6, delay=0.6, count="infinite", timing="ease-in-out", origin="50% 50%")
    drift = "" if still else sc.key("0%,100%{transform:translateX(0)}50%{transform:translateX(8px)}", dur=5, count="infinite", timing="ease-in-out", origin="50% 50%")
    sky = (f'<g class="{drift}">' + g(176, 54, 0.9, sc.art("cloud")) + g(300, 36, 0.7, sc.art("cloud")) + "</g>"
           + g(196, 46, 1.05, sc.art("plane"), plane_cls))
    extra = item(sc, 292, 168, 0.9, sc.art("sticker_heart"), T(still, 3.4), "drop", rot=30) if f > 1 else ""
    return sky + case + extra


def health(sc, f, still, spec):
    slots = [("capsule", 180, 170, 1.15), ("pill_bottle", 288, 150, 1.05), ("dumbbell", 184, 148, 1.0), ("apple85", 264, 173, 1.0),
             ("water_bottle", 306, 152, 0.98), ("avocado", 210, 175, 0.85), ("capsule_pink", 238, 176, 0.98), ("plaster", 297, 175, 0.98)]
    k = count(f, 8)
    ts = times(k, start=1.0)
    hrt = item(sc, 240, 158, 1.42, sc.art("heart"), T(still, 0.4), "pop", None if still else "pulse")
    back = [item(sc, x, y, s, sc.art(n), T(still, ts[i]), "drop") for i, (n, x, y, s) in enumerate(slots[:k]) if y < 160]
    front = [item(sc, x, y, s, sc.art(n), T(still, ts[i]), "drop") for i, (n, x, y, s) in enumerate(slots[:k]) if y >= 160]
    return "".join(back) + hrt + "".join(front)


KERNELS = ([(x, -38) for x in (-18, -11, -4, 3, 10, 17)] + [(x, -44) for x in (-15, -8, -1, 6, 13)]
           + [(x, -50) for x in (-11, -4, 3, 10)] + [(x, -56) for x in (-7, 0, 7)] + [(x, -61) for x in (-3, 4)])


def entertainment(sc, f, still, spec):
    n = len(KERNELS)
    k = max(1, min(n, round(f * n))) if f > 0 else 0
    step = min(0.12, 2.2 / max(k, 1))
    kern = "".join(item(sc, x, y, 1.25, sc.art("kernel"), T(still, 1.0 + i * step), "rise") for i, (x, y) in enumerate(KERNELS[:k]))
    over = ""
    if f > 1:
        over = "".join(item(sc, x, y, 1.25, sc.art("kernel"), T(still, 3.3 + i * 0.12), "drop") for i, (x, y) in enumerate(((-30, 0), (27, 0), (-38, 2), (35, 1))))
    bucket = sc.art("popcorn_back") + kern + sc.art("popcorn") + over
    deco = (item(sc, 180, 170, 1.2, sc.art("ticket"), T(still, 0.5), "drop", None if still else "sway")
            + item(sc, 296, 172, 1.1, sc.art("glasses3d"), T(still, 0.7), "drop"))
    back = item(sc, 298, 140, 1.0, sc.art("clapper"), T(still, 0.6), "drop")
    return back + item(sc, 238, 166, 1.25, bucket, T(still, 0.3), "grow") + deco


def bills(sc, f, still, spec):
    ang = -120 + 240 * min(f, 1.12)
    gauge = sc.art("gauge")
    if still:
        gauge = gauge.replace('class="__NEEDLE__"', f'transform="rotate({f1(ang)})"')
    else:
        frames = (f"0%,{f2(sc.pc(0.8))}%{{transform:rotate(-120deg)}}{f2(sc.pc(2.6))}%{{transform:rotate({f1(ang + 8)}deg)}}"
                  f"{f2(sc.pc(2.9))}%{{transform:rotate({f1(ang - 3)}deg)}}{f2(sc.pc(3.1))}%,100%{{transform:rotate({f1(ang)}deg)}}")
        gauge = gauge.replace('class="__NEEDLE__"', 'class="' + sc.key(frames, timing="cubic-bezier(.3,.6,.3,1)") + '"')
    out = item(sc, 234, 170, 1.28, gauge, T(still, 0.3), "grow")
    if f > 1 and not still:
        out = f'<g class="{sc.idle("shake", 3.2)}">' + out + "</g>"
    icons = [("bulb", 176, 74), ("drop", 294, 68), ("flame", 168, 132), ("wifi", 302, 128)]
    k = count(f, 4)
    ts = times(k, start=1.0, total=1.8)
    return out + "".join(item(sc, x, y, 1.0, sc.art("utility_" + n), T(still, ts[i]), "pop", None if still else "bob") for i, (n, x, y) in enumerate(icons[:k]))


def sports(sc, f, still, spec):
    slots = [("bat", 198, 162, 1.4), ("football", 176, 172, 1.3), ("cricket_ball", 220, 173, 1.3), ("racket", 254, 162, 1.3),
             ("shuttle", 240, 174, 1.25), ("paddle", 284, 164, 1.25), ("pickleball", 298, 174, 1.3), ("trophy", 270, 174, 1.25)]
    k = count(f, 8)
    ts = times(k)
    idles = {1: "bounce", 2: "bob", 6: "bounce"}
    back = [item(sc, x, y, s, sc.art(n), T(still, ts[i]), "drop", None if still else idles.get(i)) for i, (n, x, y, s) in enumerate(slots[:k]) if y < 166]
    front = [item(sc, x, y, s, sc.art(n), T(still, ts[i]), "drop", None if still else idles.get(i)) for i, (n, x, y, s) in enumerate(slots[:k]) if y >= 166]
    turf = item(sc, 236, 176, 1.0, sc.art("grass140"), T(still, 0.3), "grow")
    return turf + "".join(back) + "".join(front)


def fuel(sc, f, still, spec):
    fc = min(f, 1)
    h = 26 * fc
    liquid = f'<rect class="{"" if still else sc.enter(1.8, "up")}" x="-5" y="{f1(-11.5 - h)}" width="10" height="{f1(h)}" rx="3" fill="{sc.lin("#FFD15A", "#FF8A00")}"></rect>' if h > 0 else ""
    pump = item(sc, 198, 168, 1.2, sc.art("pump") + liquid, T(still, 0.35), "grow")
    car = item(sc, 274, 171, 1.05, '<g transform="scale(-1 1)">' + sc.art("taxi_blue") + "</g>", T(still, 0.9), "drive_l")
    drops = "" if still else "".join(g(243, 152, 1.1, sc.art("drop_fuel"), sc.idle("drip", 1.4 + i * 0.4)) for i in range(3))
    over = item(sc, 262, 174, 1.0, f'<ellipse cx="0" cy="0" rx="22" ry="3.4" fill="{sc.lin("#FFD15A", "#FF8A00")}" opacity="0.85"></ellipse>', T(still, 3.3), "grow") if f > 1 else ""
    return over + pump + car + drops


def recharge(sc, f, still, spec):
    kinds = ["play", "music", "cloud", "chat", "tv", "game", "star", "wifi"]
    spots = [(x, y) for y in (-74, -56, -38, -20) for x in (-9.5, 9.5)]
    k = count(f, 8)
    ts = times(k, start=1.0)
    tiles = "".join(item(sc, x, y + 7, 1.0, sc.art("app_" + kinds[i]), T(still, ts[i]), "pop") for i, (x, y) in enumerate(spots[:k]))
    ph = item(sc, 238, 170, 1.15, sc.art("phone") + tiles, T(still, 0.35), "grow")
    extra = item(sc, 296, 96, 1.6, sc.art("app_star"), T(still, 3.4), "pop", None if still else "bob") if f > 1 else ""
    deco = item(sc, 178, 168, 1.0, sc.art("utility_wifi"), T(still, 0.7), "pop", None if still else "bob")
    return deco + ph + extra


BOOK_DX = [0, 2, -2, 3, -1, 2, -3, 1]
BOOK_ROT = [0, -2, 2, -1, 2, -2, 1, -1]


def education(sc, f, still, spec):
    k = count(f, 8)
    ts = times(k)
    books = "".join(item(sc, BOOK_DX[i], -8.4 * i, 1.0, sc.art("book" + str(i)), T(still, ts[i]), "drop", rot=BOOK_ROT[i]) for i in range(k))
    cap = item(sc, 0, -8.4 * 8, 1.0, sc.art("grad_cap"), T(still, ts[-1] + 0.4 if ts else 3.0), "pop") if k == 8 else ""
    stack = g(236, 170, 1.2, '<ellipse cx="0" cy="0" rx="28" ry="5" fill="#3A2412" opacity="0.16"></ellipse>' + books + cap)
    deco = (item(sc, 176, 170, 1.15, sc.art("pencil"), T(still, 0.5), "drop")
            + item(sc, 296, 170, 0.85, sc.art("apple"), T(still, 0.65), "drop"))
    extra = item(sc, 296, 160, 0.9, sc.art("book0"), T(still, 3.4), "drop", rot=-14) if f > 1 else ""
    return stack + deco + extra


def rent(sc, f, still, spec):
    k = count(f, 6)
    ts = times(k, start=1.0, total=1.8)
    lights = "".join(item(sc, x, y + 5 if shape == "win" else y, 1.0, sc.art("light_" + shape), T(still, ts[i]), "fade", None if still else "glow")
                     for i, (x, y, shape) in enumerate(B.HOUSE_LIGHTS[:k]))
    smoke = ""
    if f > 1 and not still:
        smoke = "".join(g(16.5 + i * 3, -74, 1.0, '<circle cx="0" cy="0" r="3.4" fill="#FFFFFF" opacity="0.9"></circle>', sc.idle("steam", 3.2 + i * 0.6)) for i in range(2))
    house = item(sc, 240, 170, 1.3, sc.art("house") + lights + smoke, T(still, 0.35), "grow")
    turf = item(sc, 238, 174, 1.0, sc.art("grass160"), T(still, 0.3), "grow")
    tree = item(sc, 176, 170, 1.15, sc.art("tree"), T(still, 0.6), "grow", None if still else "sway")
    return turf + tree + house


def emi(sc, f, still, spec):
    k = count(f, 8)
    ts = times(k)
    coins = "".join(item(sc, 0, -3.4 * i, 1.0, sc.art("coin_flat"), T(still, ts[i]), "drop") for i in range(k))
    stack = g(274, 170, 1.3, '<ellipse cx="0" cy="0" rx="14" ry="3.2" fill="#3A2412" opacity="0.18"></ellipse>' + coins)
    cal = item(sc, 198, 170, 1.15, sc.art("calendar"), T(still, 0.35), "drop")
    extra = item(sc, 300, 170, 0.9, sc.art("coin"), T(still, 3.4), "drop") if f > 1 else ""
    return cal + stack + extra


def investments(sc, f, still, spec):
    k = count(f, 8)
    ts = times(k, start=1.1, total=2.0)
    top = (B.PLANT_LEAVES[k - 1][0] - 6) if k else -30
    stem = (f'<path class="{"" if still else sc.enter(0.9, "up")}" d="M0,-26 C3,{f1((top - 26) / 2)} -3,{f1((top - 26) / 2)} 0,{f1(top)}" '
            'stroke="#2E8A3A" stroke-width="2.8" stroke-linecap="round" fill="none"></path>')
    leaves = "".join(item(sc, 0, y, 1.0, sc.art("leaf_r" if side > 0 else "leaf_l"), T(still, ts[i]), "pop") for i, (y, side) in enumerate(B.PLANT_LEAVES[:k]))
    fruit = item(sc, 0, top + 2, 0.9, sc.art("coin"), T(still, ts[-1] + 0.4), "pop", None if still else "bob") if k == 8 else ""
    plant = item(sc, 236, 170, 1.2, sc.art("pot") + stem + leaves + fruit, T(still, 0.35), "grow")
    deco = item(sc, 292, 172, 1.0, sc.art("coin") + g(12, 2, 0.85, sc.art("coin")), T(still, 0.6), "drop")
    extra = item(sc, 182, 100, 1.1, sc.art("coin"), T(still, 3.4), "pop", None if still else "bob") if f > 1 else ""
    return deco + plant + extra


def cash(sc, f, still, spec):
    k = count(f, 8)
    ts = times(k)
    notes = "".join(item(sc, x, y + 16, 1.0, sc.art("note"), T(still, ts[i]), "rise", rot=r) for i, (x, y, r) in enumerate(B.NOTE_FAN[:k]))
    wallet = item(sc, 240, 170, 1.3, sc.art("wallet_back") + notes + sc.art("wallet_front"), T(still, 0.35), "grow")
    deco = item(sc, 180, 172, 1.0, sc.art("coin") + g(13, 1, 0.8, sc.art("coin")), T(still, 0.6), "drop")
    extra = item(sc, 296, 96, 0.9, sc.art("note"), T(still, 3.4), "pop", rot=24) if f > 1 else ""
    return deco + wallet + extra


def transfers(sc, f, still, spec):
    k = count(f, 8)
    ts = times(k, start=1.1)
    arc = '<path d="M200,80 Q238,38 276,80" stroke="#0F6383" stroke-width="2" stroke-dasharray="4 5" fill="none" opacity="0.35"></path>'
    left = item(sc, 182, 110, 1.1, sc.art("avatar_a"), T(still, 0.35), "pop")
    right = item(sc, 292, 110, 1.1, sc.art("avatar_b"), T(still, 0.5), "pop")
    coins = "".join(item(sc, 0, -3.4 * i, 1.0, sc.art("coin_flat"), T(still, ts[i]), "fly") for i in range(k))
    stack = g(292, 172, 1.2, '<ellipse cx="0" cy="0" rx="14" ry="3.2" fill="#3A2412" opacity="0.18"></ellipse>' + coins)
    extra = item(sc, 252, 172, 0.9, sc.art("coin"), T(still, 3.4), "drop") if f > 1 else ""
    return arc + left + right + stack + extra


def jar(sc, f, still, spec, front):
    k = count(f, 8)
    ts = times(k)
    coins = "".join(item(sc, x, y, 1.0, sc.art("coin75"), T(still, ts[i]), "drop") for i, (x, y) in enumerate(B.JAR_COINS[:k]))
    fr = sc.art(front)
    if front == "jar_dot":
        fr = fr.replace("#__TINT__", spec.get("color", "#7F8682"))
    spill = ""
    if f > 1:
        spill = item(sc, -38, 0, 0.9, sc.art("coin"), T(still, 3.3), "drop") + item(sc, 36, 0, 0.8, sc.art("coin"), T(still, 3.6), "drop")
    return item(sc, 240, 170, 1.25, sc.art("jar_back") + coins + fr + spill, T(still, 0.35), "grow")


def income(sc, f, still, spec):
    return jar(sc, f, still, spec, "jar_plus")


def others(sc, f, still, spec):
    return jar(sc, f, still, spec, "jar_dot")


CLUSTER = {"groceries": groceries, "food": food, "shopping": shopping, "transport": transport, "travel": travel, "health": health,
           "entertainment": entertainment, "bills": bills, "sports": sports, "fuel": fuel, "recharge": recharge, "education": education,
           "rent": rent, "emi": emi, "investments": investments, "cash": cash, "transfers": transfers, "income": income}


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def scene(spec, uid="s", loop=False, dur=4.4, width=None):
    kind = spec["kind"] if spec["kind"] in CLUSTER else "others"
    top, bottom, ink, b0, b1 = look(kind, spec.get("color", "#7F8682"))
    sc = Scene(uid, loop, dur)
    f = spec["p"]
    still = spec.get("still", False)
    over = spec.get("over", False)
    bg = (f'<rect width="{W}" height="{H}" fill="{sc.lin(top, bottom, 1, 1)}"></rect>'
          f'<circle cx="250" cy="66" r="78" fill="#FFFFFF" opacity="0.5" filter="{sc.blur(14)}"></circle>'
          f'<circle cx="40" cy="168" r="46" fill="#FFFFFF" opacity="0.55" filter="{sc.blur(12)}"></circle>'
          + "".join(f'<circle cx="{x}" cy="{y}" r="1.3" fill="{ink}" opacity="0.10"></circle>' for x in range(150, 320, 14) for y in range(12, 60, 14)))
    chip = spec["chip"]
    chip_w = 16 + len(chip) * 6.3
    chip_fill, chip_ink = (RED, "#FFFFFF") if over else ("#FFFFFF", ink)
    bar_w = 124
    fill_w = bar_w * min(max(f, 0), 1)
    grow = "" if still else sc.key(f"0%,{f2(sc.pc(0.6))}%{{transform:scaleX(0)}}{f2(sc.pc(3.0))}%{{transform:scaleX(1)}}" + sc.end(sc.pc(3.0)),
                                   timing="cubic-bezier(.3,.7,.3,1)", origin="0% 50%")
    rise = "" if still else sc.enter(0.45, "fade")
    bar_fill = sc.lin("#FFA36B", "#E0341E", 1, 0) if over else sc.lin(b0, b1, 1, 0)
    name = spec["name"].upper()
    if len(name) > 22:
        name = name[:21].rstrip() + "…"
    amount = inr(spec["spent"])
    amount_size = 31 if len(amount) <= 8 else (27 if len(amount) <= 10 else 23)
    text = (f'<g class="{rise}">'
            f'<text x="18" y="31" font-family="{FONT}" font-size="10.5" font-weight="800" letter-spacing="0.7" fill="{ink}">{esc(name)}</text>'
            f'<text x="17" y="66" font-family="{FONT}" font-size="{amount_size}" font-weight="800" letter-spacing="-0.6" fill="{DARK}">{amount}</text>'
            f'<text x="18" y="84" font-family="{FONT}" font-size="11.5" font-weight="600" fill="{MUTED}">{esc(spec["sub"])}</text>'
            f'<rect x="18" y="95" width="{bar_w}" height="12" rx="6" fill="#FFFFFF" stroke="{ink}" stroke-opacity="0.14"></rect>'
            + (f'<rect class="{grow}" x="18" y="95" width="{f1(fill_w)}" height="12" rx="6" fill="{bar_fill}"></rect>' if fill_w > 0 else "")
            + f'<rect x="22" y="97" width="{f1(max(fill_w - 8, 0))}" height="3" rx="1.5" fill="#FFFFFF" opacity="0.45"></rect>'
            f'<rect x="18" y="116" width="{f1(chip_w)}" height="22" rx="11" fill="{chip_fill}" stroke="{ink}" stroke-opacity="{0 if over else 0.14}"></rect>'
            f'<text x="{f1(18 + chip_w / 2)}" y="131" text-anchor="middle" font-family="{FONT}" font-size="11" font-weight="800" fill="{chip_ink}">{esc(chip)}</text>'
            "</g>")
    cluster = CLUSTER.get(kind, others)(sc, f, still, spec)
    sparkles = "" if still else "".join(
        f'<g class="{sc.idle("twinkle", d)}">{sparkle(x, y, r, c)}</g>'
        for x, y, r, c, d in ((168, 34, 5, "#FFFFFF", 1.2), (306, 98, 4, "#FFFFFF", 2.0), (214, 22, 3.4, "#FFD43B", 2.6)))
    defs = "".join(sc.defs) + "".join(ART[n][0] for n in sc.used)
    wattr = f' width="{width}" height="{round(width * H / W)}"' if width else ' width="100%" height="100%"'
    svg = (f'<svg viewBox="0 0 {W} {H}"{wattr} preserveAspectRatio="xMidYMid slice" aria-hidden="true" style="display:block">'
           f"<defs>{defs}</defs>{bg}{cluster}{sparkles}{text}</svg>")
    return svg, "\n".join(sc.css)
