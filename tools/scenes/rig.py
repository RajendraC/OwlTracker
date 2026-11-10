"""A jointed person for the scenes: side view (facing right) or front view, optionally mirrored.
Poses are given as joint angles at a few moments; the motion between them is a smooth, overshoot-free curve
(monotone cubic) sampled densely, and the body is re-grounded on its lowest foot at every sample."""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen import mix  # noqa: E402
from gen2 import HAIR, NAVY, SK, SKD, TYRE  # noqa: E402
from gen3 import win  # noqa: E402

THIGH, SHIN, UPPER, FORE, HAND = 22, 22, 17, 15, 2.5
SHOULDER_Y = -28
JOINTS = ["lean", "head", "nT", "nK", "nF", "fT", "fK", "fF", "nS", "nE", "fS", "fE", "prop"]
CHANNELS = ["x", "air", "lean", "head", "nT", "nK", "nF", "fT", "fK", "fF", "nS", "nE", "fS", "fE", "propW"]
FOOT_SIDE = ((-3.2, 3.6), (10.5, 3.6))
FOOT_FRONT = ((-3.8, 3.6), (3.8, 3.6))
HIP_X, SHOULDER_X = 4.5, 9.0  # front view only


def rot(v, d):
    r = math.radians(d)
    return (v[0] * math.cos(r) - v[1] * math.sin(r), v[0] * math.sin(r) + v[1] * math.cos(r))


def add(*vs):
    return (sum(v[0] for v in vs), sum(v[1] for v in vs))


def ankle(T, K):
    return add(rot((0, THIGH), T), rot((0, SHIN), T + K))


def sole(T, K, F, front=False):
    a, w = ankle(T, K), T + K + F
    return max(add(a, rot(p, w))[1] for p in (FOOT_FRONT if front else FOOT_SIDE))


def ground_y(q, front=False):
    return -max(sole(q["nT"], q["nK"], q["nF"], front), sole(q["fT"], q["fK"], q["fF"], front)) - q["air"]


def resolve(p, front=False):
    q = {k: float(p.get(k, 0)) for k in JOINTS}
    q["air"], q["x"] = float(p.get("air", 0)), float(p.get("x", 0))
    yn, yf = ankle(q["nT"], q["nK"])[1], ankle(q["fT"], q["fK"])[1]
    support = "n" if yn >= yf else "f"
    for side in "nf":
        if p.get(side + "F") is None:
            w = q[side + "T"] + q[side + "K"]
            q[side + "F"] = -w if (front or side == support) else (-w * 0.35 + 16)
    if "propW" in p:
        q["prop"] = p["propW"] - (q["lean"] + q["nS"] + q["nE"])
    q["propW"] = q["lean"] + q["nS"] + q["nE"] + q["prop"]
    q["y"] = ground_y(q, front)
    return q


def pchip(xs, ys):
    """Monotone cubic through the points: smooth, and never overshoots a pose."""
    n = len(xs)
    h = [xs[i + 1] - xs[i] for i in range(n - 1)]
    d = [(ys[i + 1] - ys[i]) / h[i] for i in range(n - 1)]
    m = [0.0] * n
    for i in range(1, n - 1):
        if d[i - 1] * d[i] > 0:
            w1, w2 = 2 * h[i] + h[i - 1], h[i] + 2 * h[i - 1]
            m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])

    def f(x):
        if x <= xs[0]:
            return ys[0]
        if x >= xs[-1]:
            return ys[-1]
        i = max(j for j in range(n - 1) if xs[j] <= x)
        t = (x - xs[i]) / h[i]
        t2, t3 = t * t, t * t * t
        return ((2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h[i] * m[i]
                + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h[i] * m[i + 1])
    return f


def sample(keys, front=False, n=44):
    R = [(u, resolve(p, front)) for u, p in keys]
    xs = [u for u, _ in R]
    fns = {ch: pchip(xs, [q[ch] for _, q in R]) for ch in CHANNELS}
    us = sorted(set([round(i / n, 4) for i in range(n + 1)] + [round(u, 4) for u in xs]))
    S = []
    for u in us:
        q = {ch: fns[ch](u) for ch in CHANNELS}
        q["air"] = max(0.0, q["air"])
        q["prop"] = q["propW"] - (q["lean"] + q["nS"] + q["nE"])
        q["y"] = ground_y(q, front)
        S.append((u, q))
    return R, S


def hand_at(q, side="n", front=False):
    off = (SHOULDER_X if side == "n" else -SHOULDER_X) if front else 0
    sh = rot((off, SHOULDER_Y), q["lean"])
    el = add(sh, rot((0, UPPER), q["lean"] + q[side + "S"]))
    return add(el, rot((0, FORE + HAND), q["lean"] + q[side + "S"] + q[side + "E"]))


def foot_at(q, side="n", local=(6, 3)):
    return add(ankle(q[side + "T"], q[side + "K"]), rot(local, q[side + "T"] + q[side + "K"] + q[side + "F"]))


def world(q, local, x0, gy, sc, mirror=False):
    """Scene position of a point given in the person's hip-centred units."""
    dx = (q["x"] + local[0]) * sc
    return (x0 - dx if mirror else x0 + dx, gy + (q["y"] + local[1]) * sc)


def tip(q, length, side="n"):
    """A point along the held object, `length` units from the hand."""
    return add(hand_at(q, side), rot((0, length), q["propW"]))


# ---------------------------------------------------------------- shapes
def capsule(y1, y2, w1, w2, back=0.0, front=0.0):
    r1, r2, h = w1 / 2, w2 / 2, y2 - y1
    return (f"M{-r1:.2f} {y1:.2f} C{-r1 - back:.2f} {y1 + h * 0.35:.2f} {-r2 - back:.2f} {y1 + h * 0.7:.2f} {-r2:.2f} {y2:.2f} "
            f"A{r2:.2f} {r2:.2f} 0 0 0 {r2:.2f} {y2:.2f} "
            f"C{r2 + front:.2f} {y1 + h * 0.7:.2f} {r1 + front:.2f} {y1 + h * 0.35:.2f} {r1:.2f} {y1:.2f} "
            f"A{r1:.2f} {r1:.2f} 0 0 0 {-r1:.2f} {y1:.2f} Z")


def head_svg(skin, hair, cap=None, helmet=None):
    out = (f'<path d="{capsule(-36, -29, 5.2, 5.6)}" fill="{skin}"></path>'
           f'<path d="M-4.8 -40 Q-5.4 -47.8 1.6 -48 Q8.4 -48 8.4 -41.6 L8.6 -40.2 L10.2 -37.6 L8.4 -37 Q8.2 -33.8 5.6 -33 Q1.4 -32.2 -1.6 -34.4 Q-5 -36.4 -4.8 -40 Z" fill="{skin}"></path>'
           f'<ellipse cx="-0.4" cy="-40" rx="1.7" ry="2.3" fill="{SKD}"></ellipse>'
           f'<circle cx="5.4" cy="-41.6" r=".85" fill="{HAIR}"></circle>'
           f'<path d="M4.2 -43.6 Q5.6 -44.4 7 -43.8" stroke="{HAIR}" class="w1"></path>'
           f'<path d="M5.4 -35.6 Q6.8 -35.2 7.6 -36" stroke="#8E5A3C" class="w1"></path>')
    if helmet:
        out += (f'<path d="M-6.2 -38.6 Q-7 -51 2 -51 Q10.2 -50.6 9.6 -42.2 L12.6 -41.2 L9.2 -40 L-4.6 -40.4 Q-5.4 -37 -6.2 -38.6 Z" fill="{helmet}"></path>'
                f'<path d="M9.2 -40 Q12.4 -37 10.4 -32.4 M9 -37.4 H11.8 M8.6 -34.8 H11.4" stroke="#C9D3CD" class="w1"></path>')
    elif cap:
        out += (f'<path d="M-5.6 -40.6 Q-5.6 -49.4 1.8 -49.4 Q9 -49.4 9 -42.4 Z" fill="{cap}"></path>'
                f'<path d="M7.6 -42.6 L13 -41.6" stroke="{cap}" class="w2"></path>')
    else:
        out += (f'<path d="M-5.6 -38.4 Q-6.8 -49.6 2.2 -49 Q9.4 -48.4 8.8 -42.6 Q5 -45.6 1.4 -44.6 Q-1.2 -43.2 -1.4 -40.2 Q-3.4 -36.6 -5.6 -38.4 Z" fill="{hair}"></path>')
    return out


def head_front_svg(skin, hair):
    return (f'<path d="{capsule(-36, -29, 5.6, 6)}" fill="{skin}"></path>'
            f'<ellipse cx="-5.7" cy="-40" rx="1.4" ry="2.1" fill="{SKD}"></ellipse><ellipse cx="5.7" cy="-40" rx="1.4" ry="2.1" fill="{SKD}"></ellipse>'
            f'<ellipse cx="0" cy="-40.6" rx="5.7" ry="6.8" fill="{skin}"></ellipse>'
            f'<path d="M-6 -41.4 Q-6.6 -49 0 -49.2 Q6.6 -49 6 -41.4 Q4.4 -45.6 0 -45.8 Q-4.4 -45.6 -6 -41.4 Z" fill="{hair}"></path>'
            f'<circle cx="-2.2" cy="-40.6" r=".85" fill="{HAIR}"></circle><circle cx="2.2" cy="-40.6" r=".85" fill="{HAIR}"></circle>'
            f'<path d="M-1.8 -36.6 Q0 -35.4 1.8 -36.6" stroke="#8E5A3C" class="w1"></path>')


def torso_svg(shirt, dark):
    return (f'<path d="M-6.4 2 C-7.8 -8 -8.6 -19 -6.6 -27.6 C-4.4 -32.4 4 -32.6 6.4 -28.2 C8.2 -21.4 7.8 -13 6.2 -5.4 C5.6 -2 5.8 0 6 2.4 Z" fill="{shirt}"></path>'
            f'<path d="M-6.4 2 C-7.8 -8 -8.6 -19 -6.6 -27.6 C-5.4 -29.4 -3.6 -30.4 -2.4 -30.6 C-4.2 -22 -4.2 -9 -3 2.2 Z" fill="{dark}"></path>')


def torso_front_svg(shirt, dark, shorts):
    return (f'<path d="M-9.6 -3 H9.6 L10 8 H-10 Z" fill="{shorts}"></path>'
            f'<path d="M-8.6 2 C-9.6 -8 -10.8 -20 -9.6 -27 C-6 -31.6 6 -31.6 9.6 -27 C10.8 -20 9.6 -8 8.6 2 Z" fill="{shirt}"></path>'
            f'<path d="M-8.6 2 C-9.6 -8 -10.8 -20 -9.6 -27 C-8.6 -28.6 -7.6 -29.4 -6.4 -30 C-7.8 -20 -7.4 -8 -6.2 2 Z" fill="{dark}"></path>'
            f'<path d="M-3.2 -30.4 Q0 -26.6 3.2 -30.4" stroke="{dark}" class="w15"></path>')


def leg_svg(cls_t, cls_k, cls_f, skin, shorts, shoe, socks="#FFFFFF", bare=False, front=False, pads=False):
    if bare:
        foot = f'<path d="M-3 40.6 Q-4.4 46.8 -1.4 47 L9.6 47.4 Q11.8 47.2 10.8 45.2 Q8 43 3.4 40.6 Z" fill="{skin}"></path>'
        sock = ""
    elif front:
        foot = (f'<path d="M-3.8 41 Q-4.6 47.6 0 47.6 Q4.6 47.6 3.8 41 Z" fill="{shoe}"></path>'
                f'<path d="M-3.8 47.6 H3.8" stroke="#E7ECE8" class="w15"></path>')
        sock = f'<path d="{capsule(35, 43, 6.6, 6.2)}" fill="{socks}"></path>'
    else:
        foot = (f'<path d="M-3.4 41.4 Q-4.8 47.6 -1.6 47.6 H10.4 Q12.8 47.6 11.6 45 Q8.8 42.8 3.4 41.4 Z" fill="{shoe}"></path>'
                f'<path d="M-3.8 47.6 H11.2" stroke="#E7ECE8" class="w15"></path>')
        sock = f'<path d="{capsule(37, 44, 6.2, 5.8)}" fill="{socks}"></path>'
    pad = ""
    if pads:
        pad = (f'<path d="{capsule(18, 43, 10, 8)}" fill="#F7F8F6"></path>'
               f'<path d="M-2 22 V40 M1 21 V41 M4 22 V40" stroke="#D3DBD6" style="stroke-width: .7"></path>')
    return (f'<g class="{cls_t}"><path d="{capsule(-1, 22, 11.4, 8.4, back=0.6, front=1.2)}" fill="{skin}"></path>'
            f'<path d="{capsule(-4, 10, 13, 12)}" fill="{shorts}"></path>'
            f'<g class="{cls_k}"><path d="{capsule(22, 44, 8.2, 5.4, back=1.8, front=0.2)}" fill="{skin}"></path>'
            f'{sock}{pad}<g class="{cls_f}">{foot}</g></g></g>')


def arm_svg(cls_s, cls_e, skin, sleeve, prop_cls=None, prop_svg="", glove=None):
    prop = f'<g transform="translate(0 {FORE + HAND - 11:.1f})"><g class="{prop_cls}">{prop_svg}</g></g>' if prop_svg else ""
    hand = (f'<ellipse cx="0.3" cy="6.8" rx="3.9" ry="4.4" fill="{glove}"></ellipse><path d="M-2.6 3.6 H3.2" stroke="{mix(glove, "#000000", 0.2)}" class="w1"></path>'
            if glove else f'<ellipse cx="0.3" cy="6.6" rx="3" ry="3.6" fill="{skin}"></ellipse>')
    return (f'<g class="{cls_s}"><path d="{capsule(-28, -11, 6.6, 5.4, back=0.4, front=0.8)}" fill="{skin}"></path>'
            f'<path d="{capsule(-31, -21, 8.8, 8.2)}" fill="{sleeve}"></path>'
            f'<g class="{cls_e}"><path d="{capsule(-11, 4, 5.2, 4.2, front=0.5)}" fill="{skin}"></path>'
            f'{hand}{prop}</g></g>')


def bat_svg():
    return (f'<path d="M0 -2 V8" stroke="{TYRE}" class="w25"></path>'
            f'<path d="M-2.8 8 H2.8 L3.2 33 Q0 35.4 -3.2 33 Z" fill="#E2BF86"></path>'
            f'<path d="M1.6 9 L2 32.4" stroke="#B88E52" class="w1"></path>')


def racket_svg():
    return (f'<path d="M0 -2 V9" stroke="{TYRE}" class="w25"></path><path d="M0 9 V15" stroke="#5B6770" class="w1"></path>'
            f'<ellipse cx="0" cy="23.5" rx="6.2" ry="8.8" fill="#FFFFFF" stroke="{TYRE}" style="fill-opacity: .55; stroke-width: 1.6"></ellipse>'
            f'<path d="M-3 16.5 V30.5 M0 15 V32 M3 16.5 V30.5 M-5.6 20 H5.6 M-6 23.5 H6 M-5.6 27 H5.6" stroke="#9AA6A0" style="stroke-width: .5"></path>')


def paddle_svg(face):
    return (f'<path d="M0 -2 V7" stroke="{TYRE}" class="w3"></path>'
            f'<rect x="-7" y="6" width="14" height="16" rx="4.5" fill="{face}"></rect>'
            f'<rect x="-7" y="6" width="14" height="16" rx="4.5" fill="none" stroke="{TYRE}" class="w1"></rect>')


# ---------------------------------------------------------------- the animated person
def athlete(ctx, s, e, keys, c, x0, gy, sc=0.6, prop=None, shorts=NAVY, cap=None, socks="#FFFFFF", bare=False,
            front=False, mirror=False, pads=False, glove=None, helmet=None, shoes=("#3B4A57", "#26303A"), n=44, echoes=(), **_):
    """echoes: [(delay, opacity)] faint copies that trail the person by `delay` of the act, like a long exposure."""
    R, S = sample(keys, front, n)
    trail = ""
    for d, op in echoes:
        w = (e - s) * d
        trail += f'<g class="ghost" style="opacity: {op}">' + _draw(ctx, s + w, e + w, S, c, x0, gy, sc, prop, shorts, cap, socks, bare,
                                                                     front, mirror, pads, glove, helmet, shoes) + "</g>"
    return trail + _draw(ctx, s, e, S, c, x0, gy, sc, prop, shorts, cap, socks, bare, front, mirror, pads, glove, helmet, shoes), R


def _draw(ctx, s, e, S, c, x0, gy, sc, prop, shorts, cap, socks, bare, front, mirror, pads, glove, helmet, shoes):
    def a(fn, origin):
        frames = [(u, fn(q)) for u, q in S]
        if len({f for _, f in frames}) == 1:
            return ctx.anim("0%,100%{" + frames[0][1] + "}", box="view-box", origin=origin)
        return win(ctx, s, e, frames, timing="linear", box="view-box", origin=origin)
    root = a(lambda q: f"transform:translate({x0 + q['x'] * sc:.2f}px,{gy + q['y'] * sc:.2f}px)", "0 0")
    lean = a(lambda q: f"transform:rotate({q['lean']:.1f}deg)", "0px 0px")
    head = a(lambda q: f"transform:rotate({q['head']:.1f}deg)", "0px -32px")
    J = {}
    for side in "nf":
        J[side + "T"] = a(lambda q, k=side + "T": f"transform:rotate({q[k]:.1f}deg)", "0px 0px")
        J[side + "K"] = a(lambda q, k=side + "K": f"transform:rotate({q[k]:.1f}deg)", "0px 22px")
        J[side + "F"] = a(lambda q, k=side + "F": f"transform:rotate({q[k]:.1f}deg)", "0px 44px")
        J[side + "S"] = a(lambda q, k=side + "S": f"transform:rotate({q[k]:.1f}deg)", "0px -28px")
        J[side + "E"] = a(lambda q, k=side + "E": f"transform:rotate({q[k]:.1f}deg)", "0px -11px")
    pc = a(lambda q: f"transform:rotate({q['prop']:.1f}deg)", "0px 0px") if prop else None
    shadow = a(lambda q: f"transform:translate({x0 + q['x'] * sc:.2f}px,{gy + 1:.1f}px) scale({max(0.4, 1 - q['air'] * 0.035):.2f})", "0 0")
    shirt_d = mix(c, "#000000", 0.24)
    if front:
        legs = (f'<g transform="translate({-HIP_X} 0)">' + leg_svg(J["fT"], J["fK"], J["fF"], SK, shorts, shoes[0], socks=socks, front=True, pads=pads) + "</g>"
                f'<g transform="translate({HIP_X} 0)">' + leg_svg(J["nT"], J["nK"], J["nF"], SK, shorts, shoes[0], socks=socks, front=True, pads=pads) + "</g>")
        upper = (f'<g class="{lean}">' + torso_front_svg(c, shirt_d, shorts) + f'<g class="{head}">{head_front_svg(SK, HAIR)}</g>'
                 + f'<g transform="translate({-SHOULDER_X} 0)">' + arm_svg(J["fS"], J["fE"], SK, c, glove=glove) + "</g>"
                 + f'<g transform="translate({SHOULDER_X} 0)">' + arm_svg(J["nS"], J["nE"], SK, c, pc, prop or "", glove=glove) + "</g></g>")
        body = legs + upper
    else:
        far = leg_svg(J["fT"], J["fK"], J["fF"], SKD, mix(shorts, "#000000", 0.25), shoes[1], socks="#E7ECE8", bare=bare, pads=pads)
        near = leg_svg(J["nT"], J["nK"], J["nF"], SK, shorts, shoes[0], socks=socks, bare=bare, pads=pads)
        back = (f'<g class="{lean}">' + arm_svg(J["fS"], J["fE"], SKD, shirt_d, glove=glove) + torso_svg(c, shirt_d)
                + f'<g class="{head}">{head_svg(SK, HAIR, cap, helmet)}</g></g>')
        fore = f'<g class="{lean}">' + arm_svg(J["nS"], J["nE"], SK, c, pc, prop or "", glove=glove) + "</g>"
        body = far + back + near + fore
    out = (f'<g class="{shadow}"><ellipse cx="2" cy="0" rx="16" ry="2.6" fill="#16211C" class="sh"></ellipse></g>'
           f'<g class="{root}"><g transform="scale({sc})">{body}</g></g>')
    if mirror:
        out = f'<g transform="translate({2 * x0} 0) scale(-1 1)">{out}</g>'
    return out


# ---------------------------------------------------------------- things in flight
def flight(ctx, s, e, legs, spin=0.0, orient=None, hide_after=None, rate=90):
    """legs: [(u0, p0, u1, p1, ctrl, scale0, scale1)]. With a control point the leg is a quadratic Bezier in time,
    which is a true projectile path when ctrl x is the midpoint. orient: angle added to the direction of travel."""
    pts = []
    for (u0, p0, u1, p1, ctrl, s0, s1) in legs:
        k = max(3, int(round((u1 - u0) * rate)))
        for i in range(k + 1):
            if i == 0 and pts and abs(pts[-1][0] - u0) < 1e-6:
                continue
            t = i / k
            if ctrl is None:
                x, y = p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t
            else:
                x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * ctrl[0] + t * t * p1[0]
                y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * ctrl[1] + t * t * p1[1]
            pts.append((u0 + (u1 - u0) * t, x, y, s0 + (s1 - s0) * t))
    frames, ang = [], 0.0
    for i, (u, x, y, k) in enumerate(pts):
        if orient is not None:
            j = min(i + 1, len(pts) - 1)
            a0 = pts[j - 1] if j > 0 else pts[0]
            if math.dist(a0[1:3], pts[j][1:3]) > 0.05:
                ang = math.degrees(math.atan2(pts[j][2] - a0[2], pts[j][1] - a0[1])) + orient
        elif i:
            ang += spin * math.dist(pts[i - 1][1:3], (x, y))
        frames.append((u, f"opacity:1;transform:translate({x:.1f}px,{y:.1f}px) rotate({ang:.0f}deg) scale({k:.2f})"))
    first = frames[0][1].replace("opacity:1", "opacity:0")
    if pts[0][0] > 0.003:
        out = [(0, first), (pts[0][0] - 0.002, first)] + frames
    else:
        out = [(0, frames[0][1])] + frames[1:]
    if hide_after is not None:
        out.append((hide_after, out[-1][1].replace("opacity:1", "opacity:0")))
    return win(ctx, s, e, out, timing="linear", box="view-box", origin="0 0")


def pop_text(ctx, s, e, text, x, y, at, color, size=13, neon=False):
    k = win(ctx, s, e, [(0, f"opacity:0;transform:translate({x}px,{y}px) scale(.5)"), (at, f"opacity:0;transform:translate({x}px,{y}px) scale(.5)"),
                        (at + 0.05, f"opacity:1;transform:translate({x}px,{y}px) scale(1.15)"), (at + 0.1, f"opacity:1;transform:translate({x}px,{y}px) scale(1)"),
                        (1, f"opacity:1;transform:translate({x}px,{y}px) scale(1)")], box="view-box", origin="0 0")
    look = ("fill: #F2F5FF; letter-spacing: 1.5px" if neon else f"fill: {color}; paint-order: stroke; stroke: #FFFFFF; stroke-width: 3px")
    return (f'<g class="{k}"><text x="0" y="0" class="lbl" style="text-anchor: middle; font-size: {size}px; font-weight: 800; {look}">{text}</text></g>')
