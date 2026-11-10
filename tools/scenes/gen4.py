"""Round 4: a jointed athlete for natural sports (cricket, football, badminton, pickleball, chess, running),
yoga in Health, a clear runway take-off in Travel, Groceries as a filling cart. python3 gen4.py <R4Phone|R4Gallery|canvas base>"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen import BG, CATS, INK, LINE, MUTED, ROOT, mix  # noqa: E402
import gen2  # noqa: E402
from gen2 import (BASE_CSS, GOLD, GREY, HAIR, NAVY, RED, SK, SKD, STATIC, TYRE, Ctx, inr, page, plane, scene_box,  # noqa: E402
                  sub_line)
import gen3  # noqa: E402
from gen3 import (EXTRA_CSS, UTIL_ACTS, act_broadband, act_cls, act_gas, act_gym, act_meditate, act_power, act_water,  # noqa: E402
                  cycles, placed, progress_bottom, win, windows)

# ---------------------------------------------------------------- the athlete (see rig.py)
from rig import (FORE, HAND, SHIN, SHOULDER_Y, THIGH, UPPER, add, ankle, athlete, bat_svg, capsule, flight, foot_at,  # noqa: E402
                 hand_at, paddle_svg, pop_text, racket_svg, resolve, rot, tip, world)


# ---------------------------------------------------------------- sports acts
NEON_GROUND = {
    "grass": '<path d="M0 94.6 H248" stroke="#FFFFFF" class="w1"></path>'
             + "".join(f'<path d="M{x} 94.6 l1.4 -2.6 M{x + 3} 94.6 l-.8 -2.2" stroke="#FFFFFF" style="stroke-width: .6"></path>' for x in range(6, 248, 23)),
    "pitch": '<path d="M0 94.6 H248 M30 97.6 H226" stroke="#FFFFFF" class="w1"></path>',
    "court": '<path d="M0 94.6 H248 M18 94.6 v-2.4 M230 94.6 v-2.4" stroke="#FFFFFF" class="w1"></path>',
    "pickle": '<path d="M0 94.6 H248 M103 94.6 v-3 M151 94.6 v-3" stroke="#FFFFFF" class="w1"></path>',
    "track": '<path d="M0 89.6 H248 M0 97.4 H248" stroke="#FFFFFF" class="w1"></path>',
}
NEON_CSS = """.neon{filter:drop-shadow(0 0 .7px rgba(255,255,255,.95)) drop-shadow(0 0 2.4px rgba(110,140,255,.95))}
.neon path,.neon ellipse,.neon circle,.neon rect{fill:#0C1234;stroke:#F2F5FF;stroke-width:1.15px;stroke-linejoin:round;stroke-linecap:round}
.neon .w1,.neon .w15,.neon .w2,.neon .w25,.neon .w3,.neon .w4,.neon .w45,.neon .w5,.neon .w6,.neon .w7,.neon .w9,.neon .w10,.neon .w12,.neon .w16{fill:none}
.neon .ghost path,.neon .ghost ellipse,.neon .ghost circle,.neon .ghost rect{fill:none;stroke:#B4C4FF;stroke-width:.85px}
.neon .lit{fill:rgba(170,190,255,.2)}
.neon .solid *,.neon path.solid{fill:#F2F5FF}
.neon .sh{display:none}
.neon text{fill:#F2F5FF}
.neon .tw{stroke-width:9.4px}.neon .tn{stroke:#0C1234;stroke-width:7px}.neon .sw{stroke-width:12.4px}.neon .sn{stroke:#0C1234;stroke-width:10px}"""
TRAIL = ((0.03, 0.55), (0.06, 0.32), (0.09, 0.16))
SPORTS_NEON = False  # the neon line-art look; off = the same colours as every other category
GHOST_CSS = ".ghost path,.ghost ellipse,.ghost circle,.ghost rect{fill:#C3CCDD;stroke:none}"


def neon_wall(ctx):
    """A dark brick wall with a soft light in the middle, behind the neon line art."""
    rows = []
    for r, y in enumerate(range(6, 99, 6)):
        rows.append(f"M0 {y} H248")
        off = 0 if r % 2 == 0 else 7
        rows += [f"M{x} {y - 6} V{y}" for x in range(off, 248, 14)]
    return ('<rect x="0" y="0" width="248" height="99" fill="#0A1030"></rect>'
            '<ellipse cx="124" cy="50" rx="160" ry="49" fill="#18224F" style="fill-opacity: .55"></ellipse>'
            '<ellipse cx="124" cy="54" rx="100" ry="40" fill="#202C66" style="fill-opacity: .45"></ellipse>'
            f'<path d="{" ".join(rows)}" stroke="#FFFFFF" style="stroke-opacity: .05; stroke-width: .6; fill: none"></path>'
            '<rect x="0" y="0" width="248" height="99" fill="none" stroke="#0A1030" style="stroke-width: 6; stroke-opacity: .6"></rect>')


def surface(kind, neon=False):
    if neon:
        return NEON_GROUND.get(kind, "")
    if kind == "grass":
        return ('<rect x="0" y="89" width="248" height="9" fill="#7DB46C"></rect>'
                + "".join(f'<rect x="{x}" y="89" width="20" height="9" fill="#8CC07A"></rect>' for x in range(0, 248, 40)))
    if kind == "pitch":
        return ('<rect x="0" y="89" width="248" height="9" fill="#7DB46C"></rect><rect x="20" y="89" width="210" height="9" fill="#D9C39A"></rect>'
                '<path d="M60 89 V98" stroke="#FFFFFF" class="w15"></path>')
    if kind == "court":
        return '<rect x="0" y="89" width="248" height="9" fill="#5B9E7A"></rect><path d="M0 93.5 H248" stroke="#FFFFFF" class="w1"></path>'
    if kind == "pickle":
        return ('<rect x="0" y="89" width="248" height="9" fill="#3E7CB1"></rect><rect x="112" y="89" width="76" height="9" fill="#5A93C4"></rect>'
                '<path d="M112 89 V98 M188 89 V98" stroke="#FFFFFF" class="w15"></path>')
    if kind == "track":
        return '<rect x="0" y="89" width="248" height="9" fill="#C8664A"></rect>'
    return ""


# ---------------------------------------------------------------- sports, as real moments of play
RUN4 = [dict(lean=14, nT=-30, nK=24, fT=26, fK=40, nS=36, nE=-84, fS=-36, fE=-84),
        dict(lean=14, nT=-6, nK=34, fT=-14, fK=96, nS=4, nE=-84, fS=-4, fE=-84, air=3),
        dict(lean=14, nT=26, nK=40, fT=-30, fK=24, nS=-36, nE=-84, fS=36, fE=-84),
        dict(lean=14, nT=-14, nK=96, fT=-6, fK=34, nS=-4, nE=-84, fS=4, fE=-84, air=3)]


def cricket_ball(cls):
    return (f'<g class="{cls} solid"><circle cx="0" cy="0" r="2.6" fill="#B3261E"></circle>'
            f'<path d="M-2 -1.2 Q0 0.4 2 -1.2" stroke="#FFFFFF" style="stroke-width: .5; fill: none"></path></g>')


def act_cover_drive(ctx, c, s, e):
    """The bowler runs in and delivers; the batsman lifts the bat, strides to the pitch of the ball, drives it
    along the ground through the covers with a straight bat and a high finish; the keeper rises; FOUR."""
    gy = 94
    bx0, bsc = 88, 0.6
    st = dict(lean=14, nT=-8, nK=16, fT=12, fK=18, nS=30, nE=-62, fS=34, fE=-58, propW=18, head=-12)
    keys = [
        (0.0, st),
        (0.14, dict(st, propW=8)),
        (0.22, dict(st, propW=20)),
        (0.32, dict(st, lean=12, nS=56, nE=-74, fS=60, fE=-70, propW=150, head=-12)),
        (0.41, dict(x=4, lean=18, nT=-34, nK=60, fT=10, fK=18, nS=58, nE=-76, fS=62, fE=-72, propW=166, head=-6)),
        (0.48, dict(x=9, lean=26, nT=-42, nK=34, fT=30, fK=6, fF=56, nS=24, nE=-44, fS=28, fE=-40, propW=96, head=0)),
        (0.53, dict(x=9, lean=30, nT=-44, nK=40, fT=32, fK=6, fF=60, nS=-26, nE=-12, fS=-22, fE=-10, propW=-4, head=2)),
        (0.61, dict(x=9, lean=22, nT=-44, nK=36, fT=30, fK=8, fF=60, nS=-118, nE=-48, fS=-114, fE=-46, propW=-148, head=-4)),
        (0.72, dict(x=9, lean=16, nT=-42, nK=32, fT=28, fK=10, fF=60, nS=-150, nE=-62, fS=-146, fE=-60, propW=-205, head=-8)),
        (1.0, dict(x=9, lean=15, nT=-42, nK=32, fT=28, fK=10, fF=60, nS=-150, nE=-62, fS=-146, fE=-60, propW=-205, head=-10)),
    ]
    batsman, R = athlete(ctx, s, e, keys, c, bx0, gy, bsc, prop=bat_svg(), pads=True, glove="#F7F8F6", helmet="#2F3A55", echoes=TRAIL)
    wx0, wsc = 214, 0.5
    bk = [(0.0, dict(RUN4[0], x=-64)), (0.05, dict(RUN4[1], x=-53)), (0.1, dict(RUN4[2], x=-42)), (0.15, dict(RUN4[3], x=-31)),
          (0.2, dict(RUN4[0], x=-20)),
          (0.25, dict(x=-8, air=10, lean=-6, nT=-50, nK=80, fT=20, fK=60, nS=30, nE=-30, fS=-150, fE=-10, head=-6)),
          (0.29, dict(x=0, lean=-12, nT=-30, nK=20, fT=24, fK=16, nS=120, nE=-10, fS=-170, fE=-6, head=-8)),
          (0.33, dict(x=6, lean=6, nT=-34, nK=4, fT=30, fK=20, nS=196, nE=-4, fS=-40, fE=-40, head=-4)),
          (0.42, dict(x=16, lean=34, nT=-20, nK=26, fT=40, fK=40, nS=330, nE=-20, fS=20, fE=-40, head=0)),
          (0.56, dict(x=24, lean=14, nT=-10, nK=20, fT=14, fK=30, nS=360, nE=-30, fS=10, fE=-40, head=-6)),
          (1.0, dict(x=26, lean=6, nT=-6, nK=10, fT=8, fK=12, nS=368, nE=-20, fS=6, fE=-20, head=-8))]
    bowler, BR = athlete(ctx, s, e, bk, NAVY, wx0, gy, wsc, mirror=True, shorts="#E7ECE8")
    crouch = dict(lean=46, nT=-74, nK=128, fT=-64, fK=124, nS=-36, nE=-30, fS=-30, fE=-34, head=-30)
    kk = [(0, crouch), (0.42, crouch), (0.5, dict(crouch, lean=36, nT=-60, nK=100, fT=-52, fK=96, head=-24)),
          (0.64, dict(lean=12, nT=-14, nK=20, fT=-6, fK=18, nS=-50, nE=-40, fS=-46, fE=-44, head=-14)),
          (1.0, dict(lean=10, nT=-12, nK=18, fT=-4, fK=16, nS=-30, nE=-50, fS=-26, fE=-54, head=-18))]
    keeper, _ = athlete(ctx, s, e, kk, NAVY, 30, gy, 0.5, shorts="#E7ECE8", pads=True, glove="#F2B84B", cap="#2F3A55")
    qr = BR[7][1]
    rel = world(qr, add(hand_at(qr), (0, 2)), wx0, gy, wsc, mirror=True)
    q = R[6][1]
    hit = world(q, tip(q, 25), bx0, gy, bsc)
    pitch = (hit[0] + 44, gy - 2.6)
    ball = flight(ctx, s, e, [
        (0.33, rel, 0.45, pitch, ((rel[0] + pitch[0]) / 2, (rel[1] + pitch[1]) / 2 - 5), 1, 1),
        (0.45, pitch, 0.53, hit, ((pitch[0] + hit[0]) / 2, min(pitch[1], hit[1]) - 4), 1, 1),
        (0.53, hit, 0.68, (262, 124), (176, hit[1] + 4), 1, 2.8)], spin=14, hide_after=0.69)
    ground = surface("pitch", SPORTS_NEON) + ('<path d="M74 91 V98" stroke="#FFFFFF" class="w1"></path>' if SPORTS_NEON
                                              else '<path d="M74 89 V98" stroke="#FFFFFF" class="w15"></path>')
    stumps = (f'<path d="M60 94 V70 M63.5 94 V70 M67 94 V70" stroke="#E2BF86" class="w2"></path>'
              f'<path d="M59 69.4 H68" stroke="#B88E52" class="w15"></path>')
    return ground + keeper + stumps + batsman + bowler + cricket_ball(ball) + pop_text(ctx, s, e, "FOUR!", 172, 46, 0.66, c, 15, neon=SPORTS_NEON)


def football_ball(cls):
    return (f'<g class="{cls}"><circle cx="0" cy="0" r="4.4" fill="#FFFFFF" stroke="{TYRE}" style="stroke-width: .9"></circle>'
            f'<path d="M0 -1.9 L1.8 -0.6 L1.1 1.5 H-1.1 L-1.8 -0.6 Z" fill="{TYRE}"></path>'
            f'<path d="M0 -1.9 V-4.4 M1.8 -0.6 L4.2 -1.4 M-1.8 -0.6 L-4.2 -1.4 M1.1 1.5 L2.6 3.8 M-1.1 1.5 L-2.6 3.8" stroke="{TYRE}" style="stroke-width: .6"></path></g>')


def act_penalty(ctx, c, s, e):
    """A penalty: a short run-up, plant, strike with the instep, the keeper dives the wrong way, top corner, GOAL!"""
    gy = 94
    nb, nt, fb, ft = (166, 92.5), (166, 46), (236, 86), (236, 50)

    def gp(u, v):
        bx, by = nb[0] + (fb[0] - nb[0]) * u, nb[1] + (fb[1] - nb[1]) * u
        tx, ty = nt[0] + (ft[0] - nt[0]) * u, nt[1] + (ft[1] - nt[1]) * u
        return (bx + (tx - bx) * v, by + (ty - by) * v)
    dx, dy = 9, -6
    mesh = ""
    for i in range(1, 9):
        a, b = gp(i / 9, 0), gp(i / 9, 1)
        mesh += f'<path d="M{a[0] + dx:.1f} {a[1] + dy:.1f} L{b[0] + dx:.1f} {b[1] + dy:.1f}" stroke="#E3E9E5" style="stroke-width: .7"></path>'
    for j in range(1, 6):
        a, b = gp(0, j / 6), gp(1, j / 6)
        mesh += f'<path d="M{a[0] + dx:.1f} {a[1] + dy:.1f} L{b[0] + dx:.1f} {b[1] + dy:.1f}" stroke="#E3E9E5" style="stroke-width: .7"></path>'
    back = (f'<path d="M{nt[0]} {nt[1]} L{nt[0] + dx} {nt[1] + dy} L{ft[0] + dx} {ft[1] + dy} L{ft[0]} {ft[1]} M{ft[0] + dx} {ft[1] + dy} L{fb[0] + dx} {fb[1] + dy} L{fb[0]} {fb[1]}'
            f' M{nt[0] + dx} {nt[1] + dy} L{nb[0] + dx} {nb[1] + dy} L{nb[0]} {nb[1]}" stroke="#C9D3CD" class="w1"></path>')
    bulge = win(ctx, s, e, [(0, "transform:translate(0,0)"), (0.56, "transform:translate(0,0)"), (0.6, "transform:translate(3px,-1.5px)"),
                            (0.7, "transform:translate(0,0)"), (1, "transform:translate(0,0)")])
    frame = (f'<path d="M{nb[0]} {nb[1]} L{nt[0]} {nt[1]} L{ft[0]} {ft[1]} L{fb[0]} {fb[1]}" stroke="#FFFFFF" class="w3"></path>'
             f'<path d="M{nb[0]} {nb[1]} L{nt[0]} {nt[1]} L{ft[0]} {ft[1]} L{fb[0]} {fb[1]}" stroke="#9AA6A0" style="stroke-width: .6; fill: none"></path>')
    goal_line = f'<path d="M146 94.2 L248 84.6" stroke="#FFFFFF" class="w15"></path>'
    # the keeper, facing us on the line
    gx, gyk = gp(0.5, 0)
    ready = dict(nT=-14, nK=22, fT=14, fK=-22, nS=-34, nE=-30, fS=34, fE=30)
    gk = [(0, ready), (0.2, dict(ready, x=-2)), (0.3, dict(ready, x=-2, air=3)),
          (0.36, dict(ready, x=-2, nT=-20, nK=34, fT=20, fK=-34)),
          (0.41, dict(x=-6, lean=-14, nT=-26, nK=30, fT=8, fK=-10, nS=-60, nE=-40, fS=60, fE=40)),
          (0.5, dict(x=-30, air=16, lean=-74, nT=-80, nK=0, fT=-66, fK=0, nS=-176, nE=-4, fS=184, fE=4, head=-10)),
          (0.6, dict(x=-44, lean=-88, nT=-90, nK=0, fT=-84, fK=0, nS=-182, nE=0, fS=178, fE=0, head=-6)),
          (1.0, dict(x=-44, lean=-88, nT=-90, nK=0, fT=-84, fK=0, nS=-182, nE=0, fS=178, fE=0, head=-20))]
    keeper, _ = athlete(ctx, s, e, gk, "#F2B84B", gx, gyk, 0.46, front=True, glove="#FFFFFF", shorts="#2F3A35", socks="#F2B84B", shoes=("#2F3A35", "#2F3A35"))
    # the taker, side on
    kx0, ksc = 46, 0.6
    ra, rb, rc, rd = (dict(r, lean=10) for r in RUN4)
    kk = [(0.0, dict(lean=2, nT=-4, nK=4, fT=6, fK=6, nS=10, nE=-14, fS=-8, fE=-14, head=8)),
          (0.1, dict(lean=4, nT=-4, nK=8, fT=6, fK=10, nS=10, nE=-20, fS=-8, fE=-20, head=8)),
          (0.16, dict(ra, x=6)), (0.21, dict(rb, x=15)), (0.26, dict(rc, x=24)), (0.31, dict(rd, x=34)),
          (0.36, dict(x=43, lean=-4, fT=-22, fK=14, nT=40, nK=112, nS=62, nE=-24, fS=-108, fE=-22, head=12)),
          (0.42, dict(x=47, lean=-10, fT=-14, fK=16, nT=-34, nK=22, nS=34, nE=-24, fS=-100, fE=-24, head=14)),
          (0.5, dict(x=51, air=3, lean=-16, fT=-4, fK=10, nT=-96, nK=8, nS=44, nE=-30, fS=-92, fE=-28, head=6)),
          (0.6, dict(x=59, lean=4, nT=-20, nK=16, fT=20, fK=40, nS=20, nE=-40, fS=-20, fE=-40, head=-4)),
          (0.7, dict(x=63, air=8, lean=-4, nT=-20, nK=60, fT=10, fK=70, nS=-168, nE=-20, fS=-160, fE=-24, head=-14)),
          (0.8, dict(x=65, lean=-2, nT=-6, nK=12, fT=8, fK=14, nS=-170, nE=-18, fS=-164, fE=-22, head=-12)),
          (1.0, dict(x=65, lean=-2, nT=-6, nK=12, fT=8, fK=14, nS=-170, nE=-18, fS=-164, fE=-22, head=-12))]
    taker, K = athlete(ctx, s, e, kk, c, kx0, gy, ksc, shorts="#FFFFFF", socks=c, echoes=TRAIL)
    q = K[7][1]
    fx, fy = world(q, foot_at(q, "n", (9, 2)), kx0, gy, ksc)
    spot = (fx + 3.5, gy - 4.4)
    corner = gp(0.86, 0.84)
    inside = (corner[0] + dx * 0.8, corner[1] + dy * 0.8)
    floor = gp(0.86, 0.0)
    ball = flight(ctx, s, e, [
        (0.0, spot, 0.42, spot, None, 1, 1),
        (0.42, spot, 0.55, corner, ((spot[0] + corner[0]) / 2, min(spot[1], corner[1]) - 8), 1, 0.74),
        (0.55, corner, 0.6, inside, None, 0.74, 0.72),
        (0.6, inside, 0.74, (floor[0] + dx * 0.8, floor[1] + dy * 0.8 - 3.2), ((inside[0] + floor[0]) / 2 + 4, inside[1]), 0.72, 0.72)], spin=10)
    marks = f'<ellipse cx="{spot[0]:.1f}" cy="{gy - 0.4:.1f}" rx="3" ry=".9" fill="#FFFFFF"></ellipse>'
    return (surface("grass", SPORTS_NEON) + goal_line + marks + back + f'<g class="{bulge}">{mesh}</g>' + keeper + frame
            + taker + football_ball(ball) + pop_text(ctx, s, e, "GOAL!", 200, 36, 0.6, c, 15, neon=SPORTS_NEON))


def shuttle_svg(cls):
    return (f'<g class="{cls}"><path d="M0 0 L10 -4.6 L10 4.6 Z" fill="#FFFFFF" stroke="#9AA6A0" style="stroke-width: .6"></path>'
            f'<path d="M3 -1.6 L10 -4.6 M3 1.6 L10 4.6 M3 0 H10" stroke="#C9D3CD" style="stroke-width: .5"></path>'
            f'<circle cx="0" cy="0" r="2.3" fill="#F2F0E6" stroke="#B88E52" style="stroke-width: .6"></circle></g>')


def act_jump_smash(ctx, c, s, e):
    """The opponent lifts high; the player turns, loads, jumps, scissor-kicks and smashes steeply over the net."""
    gy = 94
    ax0, asc = 96, 0.5
    ready = dict(lean=6, nT=-12, nK=24, fT=12, fK=24, nS=-50, nE=-80, propW=-150, fS=-30, fE=-60, head=-4)
    keys = [(0.0, ready),
            (0.12, dict(ready, x=-4, nT=-4, nK=20, fT=18, fK=26)),
            (0.24, dict(x=-8, lean=-6, nT=-18, nK=46, fT=20, fK=44, nS=140, nE=-120, propW=40, fS=-160, fE=-8, head=-14)),
            (0.34, dict(x=-8, lean=-12, nT=-22, nK=56, fT=24, fK=54, nS=150, nE=-134, propW=14, fS=-166, fE=-6, head=-16)),
            (0.4, dict(x=-6, air=9, lean=-10, nT=-8, nK=10, fT=10, fK=10, nS=156, nE=-130, propW=12, fS=-168, fE=-6, head=-16)),
            (0.45, dict(x=-4, air=20, lean=-12, nT=-40, nK=70, fT=28, fK=60, nS=168, nE=-100, propW=70, fS=-150, fE=-20, head=-14)),
            (0.5, dict(x=-2, air=23, lean=2, nT=24, nK=54, fT=-38, fK=66, nS=196, nE=-4, propW=206, fS=-40, fE=-90, head=-6)),
            (0.56, dict(x=0, air=11, lean=22, nT=12, nK=34, fT=-32, fK=44, nS=304, nE=-10, propW=332, fS=-20, fE=-80, head=2)),
            (0.62, dict(x=2, lean=18, nT=8, nK=36, fT=-24, fK=34, nS=336, nE=-30, propW=352, fS=-10, fE=-70, head=0)),
            (0.8, dict(x=2, lean=8, nT=-8, nK=24, fT=12, fK=24, nS=330, nE=-70, propW=210, fS=-30, fE=-60, head=-4)),
            (1.0, dict(x=2, lean=6, nT=-10, nK=24, fT=12, fK=24, nS=328, nE=-80, propW=210, fS=-30, fE=-60, head=-4))]
    player, R = athlete(ctx, s, e, keys, c, ax0, gy, asc, prop=racket_svg(), echoes=TRAIL)
    bx0 = 214
    lunge = dict(x=10, lean=30, nT=-52, nK=74, fT=40, fK=8, nS=-10, nE=-20, propW=10, fS=30, fE=-30, head=-8)
    bk = [(0.0, lunge),
          (0.06, dict(lunge, nS=-120, nE=-30, propW=-160)),
          (0.18, dict(x=4, lean=10, nT=-14, nK=26, fT=12, fK=26, nS=-60, nE=-70, propW=-150, fS=-30, fE=-60, head=-6)),
          (0.4, dict(x=0, lean=6, nT=-12, nK=24, fT=12, fK=24, nS=-50, nE=-80, propW=-150, fS=-30, fE=-60, head=-10)),
          (0.46, dict(x=0, air=2, lean=6, nT=-12, nK=20, fT=12, fK=20, nS=-50, nE=-80, propW=-150, fS=-30, fE=-60, head=-10)),
          (0.56, dict(x=14, lean=34, nT=-56, nK=78, fT=42, fK=8, nS=-80, nE=-10, propW=-100, fS=40, fE=-20, head=-4)),
          (1.0, dict(x=14, lean=30, nT=-54, nK=76, fT=40, fK=8, nS=-76, nE=-12, propW=-96, fS=36, fE=-20, head=-8))]
    rival, B = athlete(ctx, s, e, bk, NAVY, bx0, gy, 0.5, prop=racket_svg(), mirror=True)
    lift = world(B[1][1], tip(B[1][1], 23.5), bx0, gy, 0.5, mirror=True)
    hit = world(R[6][1], tip(R[6][1], 23.5), ax0, gy, asc)
    land = (184, gy - 1.5)
    shuttle = flight(ctx, s, e, [(0.06, lift, 0.5, hit, (hit[0] + 22, -14), 1, 1), (0.5, hit, 0.555, land, None, 1, 1)], orient=180)
    net = (f'<path d="M158 94 V58" stroke="#46524C" class="w2"></path>'
           + "".join(f'<path d="M155.4 {y} H160.6" stroke="#FFFFFF" style="stroke-width: .7"></path>' for y in range(64, 84, 3))
           + f'<path d="M155 61 H161" stroke="#FFFFFF" class="w2"></path>')
    return surface("court", SPORTS_NEON) + net + rival + player + shuttle_svg(shuttle)


def pickle_ball(cls):
    holes = "".join(f'<circle cx="{dx}" cy="{dy}" r=".5" fill="#9DB12F"></circle>' for dx, dy in ((-1.1, -0.9), (1.1, -0.5), (0, 1.2)))
    return f'<g class="{cls} solid"><circle cx="0" cy="0" r="2.7" fill="#D8E84A"></circle>{holes}</g>'


def act_rally(ctx, c, s, e):
    """A real point: underhand serve, a forehand return, then a tennis-style forehand drive that beats the opponent."""
    gy = 94
    ax0, bx0, sc = 54, 200, 0.5
    rdy = dict(lean=14, nT=-16, nK=36, fT=18, fK=36, nS=-40, nE=-70, propW=-150, fS=-30, fE=-70, head=-6)
    ak = [(0.0, dict(lean=6, nT=-14, nK=10, fT=12, fK=10, nS=40, nE=-10, propW=30, fS=-62, fE=-20, head=8)),
          (0.06, dict(lean=6, nT=-14, nK=10, fT=12, fK=10, nS=44, nE=-10, propW=36, fS=-50, fE=-24, head=10)),
          (0.12, dict(lean=12, nT=-18, nK=16, fT=14, fK=14, nS=-30, nE=-8, propW=-50, fS=-20, fE=-40, head=8)),
          (0.22, dict(x=6, lean=10, nT=-16, nK=20, fT=16, fK=22, nS=-120, nE=-40, propW=-170, fS=-30, fE=-50, head=0)),
          (0.36, dict(rdy, x=10)),
          (0.5, dict(rdy, x=10)),
          (0.57, dict(x=12, lean=8, nT=-20, nK=34, fT=24, fK=30, nS=70, nE=-24, propW=90, fS=-60, fE=-40, head=-2)),
          (0.64, dict(x=18, lean=16, nT=-36, nK=30, fT=30, fK=14, fF=40, nS=-24, nE=-8, propW=-64, fS=-20, fE=-60, head=0)),
          (0.74, dict(x=20, lean=12, nT=-34, nK=26, fT=28, fK=14, fF=40, nS=-140, nE=-70, propW=-196, fS=10, fE=-60, head=-4)),
          (1.0, dict(rdy, x=20))]
    pa, A = athlete(ctx, s, e, ak, c, ax0, gy, sc, prop=paddle_svg("#F2B84B"), cap="#FFFFFF", echoes=TRAIL)
    bk = [(0.0, rdy), (0.2, dict(rdy, x=-4)), (0.26, dict(rdy, x=-4, air=2)),
          (0.33, dict(x=-6, lean=8, nT=-18, nK=30, fT=22, fK=30, nS=70, nE=-24, propW=90, fS=-60, fE=-40, head=-2)),
          (0.4, dict(x=-2, lean=16, nT=-34, nK=30, fT=28, fK=14, fF=40, nS=-20, nE=-8, propW=-60, fS=-20, fE=-60, head=0)),
          (0.5, dict(x=0, lean=12, nT=-30, nK=26, fT=26, fK=16, nS=-130, nE=-66, propW=-190, fS=10, fE=-60, head=-4)),
          (0.6, dict(rdy, x=2)), (0.67, dict(rdy, x=2, air=2)),
          (0.76, dict(x=-10, lean=26, nT=-50, nK=70, fT=40, fK=8, nS=40, nE=-10, propW=60, fS=-40, fE=-30, head=4)),
          (1.0, dict(x=-10, lean=22, nT=-48, nK=66, fT=38, fK=8, nS=40, nE=-10, propW=60, fS=-40, fE=-30, head=-10))]
    pb, B = athlete(ctx, s, e, bk, NAVY, bx0, gy, sc, prop=paddle_svg("#3A86C9"), mirror=True)
    hand = world(A[0][1], add(hand_at(A[0][1], "f"), (0, 4)), ax0, gy, sc)
    a1 = world(A[2][1], tip(A[2][1], 14), ax0, gy, sc)
    b1 = world(B[4][1], tip(B[4][1], 14), bx0, gy, sc, mirror=True)
    a2 = world(A[7][1], tip(A[7][1], 14), ax0, gy, sc)
    r = 2.7
    bo1, bo2, bo3 = (172, gy - r), (88, gy - r), (226, gy - r)
    legs = [(0.0, hand, 0.06, hand, None, 1, 1), (0.06, hand, 0.12, a1, hand, 1, 1),
            (0.12, a1, 0.3, bo1, ((a1[0] + bo1[0]) / 2, 22), 1, 1), (0.3, bo1, 0.4, b1, ((bo1[0] + b1[0]) / 2, min(bo1[1], b1[1]) - 26), 1, 1),
            (0.4, b1, 0.56, bo2, ((b1[0] + bo2[0]) / 2, 30), 1, 1), (0.56, bo2, 0.64, a2, ((bo2[0] + a2[0]) / 2, min(bo2[1], a2[1]) - 16), 1, 1),
            (0.64, a2, 0.73, bo3, ((a2[0] + bo3[0]) / 2, 62), 1, 1), (0.73, bo3, 0.84, (266, 74), (246, 70), 1, 1)]
    ball = flight(ctx, s, e, legs, spin=6, hide_after=0.85)
    net = (f'<path d="M127 94 V74" stroke="#46524C" class="w2"></path>'
           + "".join(f'<path d="M124.5 {y} H129.5" stroke="#E3E9E5" style="stroke-width: .7"></path>' for y in range(78, 94, 3))
           + f'<path d="M124 75.5 H130" stroke="#FFFFFF" class="w2"></path>')
    court = surface("pickle", True) if SPORTS_NEON else (
        '<rect x="0" y="89" width="248" height="9" fill="#3E7CB1"></rect><rect x="103" y="89" width="48" height="9" fill="#5A93C4"></rect>'
        '<path d="M103 89 V98 M151 89 V98" stroke="#FFFFFF" class="w15"></path>')
    return court + net + pb + pa + pickle_ball(ball)


def act_block_start(ctx, c, s, e):
    """Set in the blocks, the drive out, then full speed with the camera following."""
    gy, sc, x0 = 94, 0.6, 30
    setp = dict(lean=72, nT=-62, nK=104, fT=-22, fK=118, nS=-72, nE=-2, fS=-68, fE=-2, head=12)
    A = dict(nT=-72, nK=100, fT=26, fK=30, nS=52, nE=-84, fS=-100, fE=-84)
    Bp = dict(nT=26, nK=30, fT=-72, fK=100, nS=-100, nE=-84, fS=52, fE=-84)
    steps = [14, 18, 22, 26, 30, 32, 34, 35, 35, 35]
    keys = [(0.0, setp), (0.13, setp),
            (0.18, dict(x=8, lean=58, nT=-70, nK=86, fT=10, fK=40, nS=-150, nE=-60, fS=60, fE=-70, head=8))]
    px, u, lean = 8.0, 0.18, 54.0
    for i, step in enumerate(steps):
        u = round(u + 0.075, 3)
        px += step / sc
        lean = max(14.0, lean - 7)
        pose = Bp if i % 2 == 0 else A
        keys.append((min(u, 1.0), dict(pose, x=px, lean=lean, air=3, head=max(-6.0, 6 - i * 2))))
        if u >= 1.0:
            break
    if keys[-1][0] < 1.0:
        last = keys[-1][1]
        keys.append((1.0, dict(last, x=last["x"] + (1.0 - keys[-1][0]) / 0.075 * 35 / sc)))
    from rig import pchip
    fx = pchip([k for k, _ in keys], [p.get("x", 0) for _, p in keys])
    cams = []
    for i in range(61):
        uu = i / 60
        cams.append((uu, max(0.0, x0 + fx(uu) * sc - 124)))
    scroll = win(ctx, s, e, [(uu, f"transform:translateX({-w:.1f}px)") for uu, w in cams], timing="linear")
    runner, R = athlete(ctx, s, e, keys, c, x0, gy, sc, shorts="#2F3A55", echoes=TRAIL)
    q0 = R[0][1]
    blocks = ""
    for side in "nf":
        bx, by = world(q0, foot_at(q0, side, (2, 3.6)), x0, gy, sc)
        blocks += f'<path d="M{bx - 3:.1f} {gy:.1f} L{bx + 3:.1f} {gy:.1f} L{bx - 1:.1f} {gy - 6:.1f} Z" fill="#9AA6A0"></path>'
    ticks = "".join(f'<path d="M{x} 89.6 V92.4" stroke="#FFFFFF" class="w15"></path>' for x in range(60, 560, 30))
    start = f'<path d="M{x0 + 30} 89.5 V98" stroke="#FFFFFF" class="w2"></path>'
    track = surface("track", True) if SPORTS_NEON else (
        '<rect x="0" y="89" width="248" height="9" fill="#C8664A"></rect><path d="M0 89.6 H248 M0 97.4 H248" stroke="#FFFFFF" class="w1"></path>')
    return track + f'<g class="{scroll}">{ticks}{start}{blocks}{runner}</g>'


def act_cricket2(ctx, c, s, e):
    x0, gy, sc = 70, 94, 0.62
    keys = [
        (0.0, dict(lean=18, nT=-6, nK=14, fT=14, fK=16, nS=25, nE=-40, fS=32, fE=-48, propW=32, head=-6)),
        (0.22, dict(lean=12, nT=-4, nK=12, fT=16, fK=18, nS=120, nE=-100, fS=126, fE=-92, propW=160, head=-6)),
        (0.40, dict(x=6, lean=26, nT=-38, nK=22, fT=22, fK=18, nS=60, nE=-80, fS=70, fE=-70, propW=120, head=-4)),
        (0.50, dict(x=8, lean=30, nT=-40, nK=24, fT=24, fK=20, nS=-12, nE=-22, fS=-4, fE=-30, propW=-8, head=-2)),
        (0.68, dict(x=8, lean=16, nT=-36, nK=18, fT=22, fK=26, fF=40, nS=-150, nE=-55, fS=-140, fE=-70, propW=-200, head=-4)),
        (1.0, dict(x=8, lean=14, nT=-36, nK=18, fT=22, fK=26, fF=40, nS=-150, nE=-55, fS=-140, fE=-70, propW=-200, head=-4)),
    ]
    body, R = athlete(ctx, s, e, keys, c, x0, gy, sc, prop=bat_svg(), cap="#2F3A55")
    q = R[3][1]
    hx, hy = hand_at(q)
    tip = add((hx, hy), rot((0, 28), q["propW"]))
    bx, by = world(q, tip, x0, gy, sc)
    ball = win(ctx, s, e, [(0, "opacity:0;transform:translate(250px,60px)"), (0.28, "opacity:0;transform:translate(250px,60px)"),
                           (0.3, "opacity:1;transform:translate(244px,62px)"), (0.42, "opacity:1;transform:translate(160px,92px)"),
                           (0.5, f"opacity:1;transform:translate({bx:.1f}px,{by:.1f}px)"), (0.62, "opacity:1;transform:translate(176px,42px)"),
                           (0.76, "opacity:1;transform:translate(236px,8px)"), (0.84, "opacity:0;transform:translate(260px,0px)"),
                           (1, "opacity:0;transform:translate(260px,0px)")], timing="linear", box="view-box", origin="0 0")
    stumps = (f'<path d="M46 94 V69 M49.5 94 V69 M53 94 V69" stroke="#E2BF86" class="w2"></path>'
              f'<path d="M45 68.4 H54" stroke="#B88E52" class="w15"></path>')
    return (surface("pitch") + stumps + body +
            f'<g class="{ball}"><circle cx="0" cy="0" r="3" fill="#B3261E"></circle><path d="M-2.4 -1 Q0 0.6 2.4 -1" stroke="#FFFFFF" style="stroke-width: .6; fill: none"></path></g>')


def act_football2(ctx, c, s, e):
    x0, gy, sc = 64, 94, 0.62
    run_a = dict(lean=12, nT=-26, nK=20, fT=26, fK=42, nS=40, nE=-80, fS=-42, fE=-88)
    run_b = dict(lean=12, nT=26, nK=42, fT=-26, fK=20, nS=-42, nE=-88, fS=40, fE=-80)
    keys = [
        (0.0, dict(run_a, x=-28)),
        (0.13, dict(run_b, x=-17)),
        (0.28, dict(x=-4, lean=4, nT=42, nK=105, fT=-6, fK=12, nS=60, nE=-30, fS=-75, fE=-20, head=8)),
        (0.38, dict(x=0, lean=-6, nT=-52, nK=8, fT=4, fK=14, nS=82, nE=-20, fS=-45, fE=-25, head=10)),
        (0.52, dict(x=2, lean=-10, nT=-88, nK=2, fT=10, fK=12, fF=30, nS=70, nE=-20, fS=-60, fE=-20, head=0)),
        (0.78, dict(x=4, lean=4, nT=-14, nK=24, fT=8, fK=14, nS=20, nE=-40, fS=-10, fE=-40, head=-2)),
        (1.0, dict(x=4, lean=2, nT=-6, nK=8, fT=6, fK=8, nS=10, nE=-30, fS=4, fE=-30, head=-4)),
    ]
    body, R = athlete(ctx, s, e, keys, c, x0, gy, sc, shorts="#FFFFFF", socks=c)
    q = R[3][1]
    foot = add(ankle(q["nT"], q["nK"]), (8, 2))
    fx, fy = world(q, foot, x0, gy, sc)
    bx0 = fx + 1
    ball = win(ctx, s, e, [(0, f"transform:translate({bx0:.1f}px,89.5px) rotate(0deg)"), (0.38, f"transform:translate({bx0:.1f}px,89.5px) rotate(0deg)"),
                           (0.5, "transform:translate(150px,64px) rotate(260deg)"), (0.6, "transform:translate(204px,70px) rotate(420deg)"),
                           (0.7, "transform:translate(208px,89.5px) rotate(470deg)"), (1, "transform:translate(208px,89.5px) rotate(470deg)")],
               timing="ease-out", box="view-box", origin="0 0")
    net = "".join(f'<path d="M{x} 61 V94" stroke="#E3E9E5" class="w1"></path>' for x in range(192, 222, 5))
    net += "".join(f'<path d="M188 {y} H222" stroke="#E3E9E5" class="w1"></path>' for y in range(66, 94, 5))
    bulge = win(ctx, s, e, [(0, "transform:translateX(0)"), (0.58, "transform:translateX(0)"), (0.62, "transform:translateX(3px)"),
                            (0.72, "transform:translateX(0)"), (1, "transform:translateX(0)")])
    goal = f'<g class="{bulge}">{net}</g><path d="M188 94 V59 H224 V94" stroke="#FFFFFF" class="w3"></path><path d="M188 94 V59 H224 V94" stroke="#9AA6A0" class="w1"></path>'
    return (surface("grass") + goal + body +
            f'<g class="{ball}"><circle cx="0" cy="0" r="4.6" fill="#FFFFFF" stroke="{TYRE}" style="stroke-width: 1"></circle>'
            f'<path d="M0 -1.9 L1.8 -0.6 L1.1 1.5 H-1.1 L-1.8 -0.6 Z" fill="{TYRE}"></path>'
            f'<path d="M0 -1.9 V-4.4 M1.8 -0.6 L4.2 -1.4 M-1.8 -0.6 L-4.2 -1.4 M1.1 1.5 L2.6 3.8 M-1.1 1.5 L-2.6 3.8" stroke="{TYRE}" style="stroke-width: .6"></path></g>')


def act_badminton2(ctx, c, s, e):
    x0, gy, sc = 74, 94, 0.62
    keys = [
        (0.0, dict(lean=-4, nT=-10, nK=26, fT=18, fK=28, nS=140, nE=-140, propW=12, fS=-160, fE=-10, head=-12)),
        (0.24, dict(air=12, lean=-8, nT=-30, nK=62, fT=12, fK=72, nS=150, nE=-120, propW=120, fS=-165, fE=-10, head=-14)),
        (0.38, dict(air=13, lean=6, nT=-24, nK=50, fT=16, fK=56, nS=186, nE=-6, propW=196, fS=-60, fE=-40, head=-6)),
        (0.5, dict(air=0, lean=16, nT=-22, nK=34, fT=14, fK=28, nS=300, nE=-30, propW=330, fS=-30, fE=-50, head=2)),
        (1.0, dict(air=0, lean=8, nT=-12, nK=22, fT=14, fK=22, nS=330, nE=-80, propW=280, fS=-20, fE=-60, head=0)),
    ]
    body, R = athlete(ctx, s, e, keys, c, x0, gy, sc, prop=racket_svg())
    q = R[2][1]
    hx, hy = hand_at(q)
    rh = add((hx, hy), rot((0, 24), q["propW"]))
    cx_, cy_ = world(q, rh, x0, gy, sc)
    shut = win(ctx, s, e, [(0, "opacity:0;transform:translate(250px,6px) rotate(-60deg)"), (0.12, "opacity:1;transform:translate(236px,4px) rotate(-60deg)"),
                           (0.38, f"opacity:1;transform:translate({cx_:.1f}px,{cy_ - 4:.1f}px) rotate(-150deg)"),
                           (0.46, "opacity:1;transform:translate(168px,54px) rotate(30deg)"), (0.56, "opacity:1;transform:translate(206px,89px) rotate(30deg)"),
                           (1, "opacity:1;transform:translate(206px,89px) rotate(30deg)")], timing="linear", box="view-box", origin="0 0")
    net = (f'<path d="M156 94 V58" stroke="#46524C" class="w2"></path>'
           + "".join(f'<path d="M153.4 {y} H158.6" stroke="#FFFFFF" style="stroke-width: .7"></path>' for y in range(64, 84, 3))
           + f'<path d="M153 61 H159" stroke="#FFFFFF" class="w2"></path>')
    shuttle = (f'<g class="{shut}"><path d="M0 0 L10 -4.6 L10 4.6 Z" fill="#FFFFFF" stroke="#9AA6A0" style="stroke-width: .6"></path>'
               f'<path d="M3 -1.6 L10 -4.6 M3 1.6 L10 4.6 M3 0 H10" stroke="#C9D3CD" style="stroke-width: .5"></path>'
               f'<circle cx="0" cy="0" r="2.3" fill="#F2F0E6" stroke="#B88E52" style="stroke-width: .6"></circle></g>')
    return surface("court") + net + body + shuttle


def act_pickleball(ctx, c, s, e):
    x0, gy, sc = 88, 94, 0.62
    ready = dict(lean=24, nT=-18, nK=50, fT=24, fK=48, nS=-40, nE=-60, propW=-60, fS=-28, fE=-74, head=-10)
    keys = [
        (0.0, ready),
        (0.3, dict(ready, nS=12, nE=-50, propW=24)),
        (0.45, dict(ready, x=4, nT=-28, nK=52, nS=-48, nE=-30, propW=-42, head=-6)),
        (0.62, dict(ready, x=4, nT=-28, nK=52, nS=-86, nE=-40, propW=-118, head=-8)),
        (1.0, dict(ready, x=4)),
    ]
    body, R = athlete(ctx, s, e, keys, c, x0, gy, sc, prop=paddle_svg("#F2B84B"), cap="#FFFFFF")
    q = R[2][1]
    hx, hy = hand_at(q)
    pf = add((hx, hy), rot((0, 15), q["propW"]))
    px, py = world(q, pf, x0, gy, sc)
    ball = win(ctx, s, e, [(0, "opacity:0;transform:translate(244px,56px)"), (0.12, "opacity:1;transform:translate(236px,58px)"),
                           (0.24, "opacity:1;transform:translate(156px,64px)"),
                           (0.34, "opacity:1;transform:translate(132px,91px)"), (0.45, f"opacity:1;transform:translate({px + 3:.1f}px,{py:.1f}px)"),
                           (0.56, "opacity:1;transform:translate(152px,70px)"), (0.66, "opacity:1;transform:translate(172px,91px)"),
                           (0.74, "opacity:1;transform:translate(184px,85px)"), (0.82, "opacity:1;transform:translate(194px,91px)"),
                           (1, "opacity:1;transform:translate(198px,91px)")], timing="linear", box="view-box", origin="0 0")
    net = (f'<path d="M150 94 V74" stroke="#46524C" class="w2"></path>'
           + "".join(f'<path d="M147.5 {y} H152.5" stroke="#E3E9E5" style="stroke-width: .7"></path>' for y in range(78, 94, 3))
           + f'<path d="M147 75.5 H153" stroke="#FFFFFF" class="w2"></path>')
    holes = "".join(f'<circle cx="{dx}" cy="{dy}" r=".55" fill="#9DB12F"></circle>' for dx, dy in ((-1.2, -1), (1.2, -0.6), (0, 1.3)))
    return (surface("pickle") + net + body + f'<g class="{ball}"><circle cx="0" cy="0" r="2.9" fill="#D8E84A"></circle>{holes}</g>')


def act_running2(ctx, c, s, e, laps=3):
    x0, gy, sc = 112, 94, 0.62
    k0 = dict(lean=16, nT=-32, nK=16, fT=30, fK=30, fF=38, nS=52, nE=-84, fS=-62, fE=-96, head=-6)
    k1 = dict(lean=16, nT=-4, nK=30, fT=-12, fK=118, nS=8, nE=-90, fS=-14, fE=-90, head=-6)
    k2 = dict(lean=16, nT=30, nK=30, nF=38, fT=-32, fK=16, nS=-62, nE=-96, fS=52, fE=-84, head=-6)
    k3 = dict(lean=16, nT=-12, nK=118, fT=-4, fK=30, nS=-14, nE=-90, fS=8, fE=-90, head=-6)
    cyc = [k0, k1, k2, k3]
    keys = []
    n = laps * 4
    for i in range(n + 1):
        keys.append((i / n, cyc[i % 4]))
    body, _ = athlete(ctx, s, e, keys, c, x0, gy, sc, timing="linear")
    lines = "".join(f'<path d="M{x} 93.5 H{x + 14}" stroke="#FFFFFF" class="w15"></path>' for x in range(-20, 270, 34))
    kd = cycles(ctx, "0%{transform:translateX(0)}100%{transform:translateX(-34px)}", 0.34, s, 6, timing="linear")
    marks = ""
    return surface("track") + f'<g class="{kd}">{lines}{marks}</g>' + body


def piece(kind, fill, stroke):
    paths = {
        "pawn": "M-5 0 H5 L4 -2 H2.2 L1.6 -8 A3 3 0 1 0 -1.6 -8 L-2.2 -2 H-4 Z",
        "rook": "M-5.4 0 H5.4 L4.6 -2.4 H3.2 L3 -11 H4.4 V-15 H2.4 V-13 H1 V-15 H-1 V-13 H-2.4 V-15 H-4.4 V-11 H-3 L-3.2 -2.4 H-4.6 Z",
        "knight": "M-5.4 0 H5.6 L4.8 -2.4 H3.4 Q4.4 -8 2.2 -12 L4.6 -12.4 Q5.4 -14 3.4 -15.4 L-0.6 -17.6 Q-1.8 -18.4 -2.4 -17 L-4.4 -12.6 Q-5.2 -10.6 -3.2 -10.2 L-0.4 -10.6 Q-3.4 -6.4 -3.6 -2.4 H-4.6 Z",
        "king": "M-5.6 0 H5.6 L4.8 -2.4 H3.2 L3.8 -12 Q4.4 -15 0 -15 Q-4.4 -15 -3.8 -12 L-3.2 -2.4 H-4.8 Z M-0.8 -15 V-17.4 H-2.4 V-19 H-0.8 V-20.6 H0.8 V-19 H2.4 V-17.4 H0.8 V-15 Z",
        "queen": "M-5.6 0 H5.6 L4.8 -2.4 H3.2 L4.6 -13.6 L2.2 -10.8 L0 -15.4 L-2.2 -10.8 L-4.6 -13.6 L-3.2 -2.4 H-4.8 Z",
    }
    solid = ' class="solid"' if fill == "#2F3A35" else ""
    return f'<path d="{paths[kind]}" fill="{fill}" stroke="{stroke}" style="stroke-width: .8"{solid}></path>'


def act_chess(ctx, c, s, e):
    # a board in perspective: front edge y 94 (x 40..208), back edge y 62 (x 70..178), 8 columns x 4 rows visible
    def pt(i, j):  # i column 0..8, j row 0..4 (0 = front)
        t = j / 4
        y = 88 - 30 * t
        xl, xr = 40 + 30 * t, 208 - 30 * t
        return xl + (xr - xl) * i / 8, y
    sq = ""
    for j in range(4):
        for i in range(8):
            a, b, c2, d = pt(i, j), pt(i + 1, j), pt(i + 1, j + 1), pt(i, j + 1)
            col = "#E9D7B4" if (i + j) % 2 == 0 else "#A9774E"
            lit = "" if (i + j) % 2 == 0 else ' class="lit"'
            sq += f'<path d="M{a[0]:.1f} {a[1]:.1f} L{b[0]:.1f} {b[1]:.1f} L{c2[0]:.1f} {c2[1]:.1f} L{d[0]:.1f} {d[1]:.1f} Z" fill="{col}"{lit}></path>'
    board = sq + '<path d="M40 88 H208 L206 94 H42 Z" fill="#7A5232"></path>'

    def centre(i, j):
        a, b, c2, d = pt(i, j), pt(i + 1, j), pt(i + 1, j + 1), pt(i, j + 1)
        return (a[0] + b[0] + c2[0] + d[0]) / 4, (a[1] + b[1] + c2[1] + d[1]) / 4 + 2.5
    W, Wd, B, Bd = "#F6F1E6", "#6B5B45", "#2F3A35", "#111814"

    def scale_at(j):
        return 1.0 - 0.07 * j
    statics = [("rook", 1, 0, W, Wd), ("king", 6, 3, B, Bd), ("pawn", 5, 2, B, Bd), ("queen", 2, 3, B, Bd), ("pawn", 3, 0, W, Wd), ("king", 5, 0, W, Wd)]
    pieces = ""
    for kind, i, j, f, st in sorted(statics, key=lambda t: -t[2]):
        x, y = centre(i, j)
        pieces += f'<g transform="translate({x:.1f} {y:.1f}) scale({scale_at(j) * 1.25:.2f})">{piece(kind, f, st)}</g>'
    kx0, ky0 = centre(2, 0)
    kx1, ky1 = centre(4, 1)
    knight = win(ctx, s, e, [(0, f"transform:translate({kx0:.1f}px,{ky0:.1f}px) scale(1.25)"), (0.3, f"transform:translate({kx0:.1f}px,{ky0:.1f}px) scale(1.25)"),
                             (0.4, f"transform:translate({kx0 + 4:.1f}px,{ky0 - 18:.1f}px) scale(1.25)"),
                             (0.55, f"transform:translate({kx1:.1f}px,{ky1 - 18:.1f}px) scale(1.18)"),
                             (0.63, f"transform:translate({kx1:.1f}px,{ky1:.1f}px) scale(1.18)"), (1, f"transform:translate({kx1:.1f}px,{ky1:.1f}px) scale(1.18)")],
                 box="view-box", origin="0 0")
    px, py = centre(4, 1)
    taken = win(ctx, s, e, [(0, f"opacity:1;transform:translate({px:.1f}px,{py:.1f}px) rotate(0deg)"), (0.58, f"opacity:1;transform:translate({px:.1f}px,{py:.1f}px) rotate(0deg)"),
                            (0.66, f"opacity:1;transform:translate({px + 14:.1f}px,{py - 4:.1f}px) rotate(70deg)"),
                            (0.76, f"opacity:0;transform:translate({px + 30:.1f}px,{py + 6:.1f}px) rotate(90deg)"),
                            (1, f"opacity:0;transform:translate({px + 30:.1f}px,{py + 6:.1f}px) rotate(90deg)")], box="view-box", origin="0 0")
    hand_shape = (f'<path d="M-48 -98 L-33 -71" stroke="{c}" class="w10 sw"></path><path d="M-48 -98 L-33 -71" stroke="{c}" class="w10 sn"></path>'
                  f'<path d="M-36 -74 L-9 -24" stroke="{SK}" class="w7 tw"></path><path d="M-36 -74 L-9 -24" stroke="{SK}" class="w7 tn"></path>'
                  f'<path d="M-40 -79 L-31 -66" stroke="{mix(c, "#FFFFFF", 0.3)}" class="w4"></path>'
                  f'<ellipse cx="-6" cy="-17" rx="6.4" ry="7.4" fill="{SK}" transform="rotate(-28 -6 -17)"></ellipse>'
                  f'<path d="M-3 -13 Q1 -9 1.4 -2.6" stroke="{SK}" class="w3"></path>'
                  f'<path d="M-1.2 -16 Q3.6 -12 4 -6" stroke="{SKD}" class="w25"></path>'
                  f'<path d="M-11 -12 Q-8.6 -5 -2.4 -2" stroke="{SK}" class="w3"></path>'
                  f'<path d="M-8 -20 Q-4 -18 -1 -20" stroke="{SKD}" class="w1"></path>')
    g = 15  # the fingers hold the piece this far above its base
    cx_, cy_ = 220, 62  # the clock button
    hand = win(ctx, s, e, [(0, f"transform:translate({kx0 + 20:.1f}px,-30px)"), (0.24, f"transform:translate({kx0:.1f}px,{ky0 - g:.1f}px)"),
                           (0.3, f"transform:translate({kx0:.1f}px,{ky0 - g:.1f}px)"), (0.4, f"transform:translate({kx0 + 4:.1f}px,{ky0 - g - 18:.1f}px)"),
                           (0.55, f"transform:translate({kx1:.1f}px,{ky1 - g - 18:.1f}px)"), (0.63, f"transform:translate({kx1:.1f}px,{ky1 - g:.1f}px)"),
                           (0.68, f"transform:translate({kx1:.1f}px,{ky1 - g - 6:.1f}px)"), (0.82, f"transform:translate({cx_:.1f}px,{cy_ - 10:.1f}px)"),
                           (0.87, f"transform:translate({cx_:.1f}px,{cy_ + 1:.1f}px)"), (0.92, f"transform:translate({cx_:.1f}px,{cy_ - 8:.1f}px)"),
                           (1, f"transform:translate({cx_ + 30:.1f}px,-40px)")],
               box="view-box", origin="0 0")
    press = win(ctx, s, e, [(0, "transform:translateY(0)"), (0.84, "transform:translateY(0)"), (0.87, "transform:translateY(2.5px)"), (0.93, "transform:translateY(0)"),
                            (1, "transform:translateY(0)")])
    clock = (f'<rect x="206" y="66" width="30" height="20" rx="3" fill="{TYRE}"></rect>'
             f'<circle cx="214" cy="76" r="5" fill="#FFFFFF"></circle><circle cx="228" cy="76" r="5" fill="#FFFFFF"></circle>'
             f'<path d="M214 76 V72.6 M228 76 L230.4 74" stroke="{TYRE}" class="w1"></path>'
             f'<g class="{press}"><rect x="216" y="62" width="8" height="4" rx="1" fill="{c}"></rect></g><rect x="208" y="62" width="8" height="4" rx="1" fill="#9AA6A0"></rect>')
    return (board + pieces + f'<g class="{taken}">{piece("pawn", B, Bd)}</g><g class="{knight}">{piece("knight", W, Wd)}</g>'
            + clock + f'<g class="{hand}">{hand_shape}</g>')


def act_yoga(ctx, c, s, e):
    x0, gy, sc = 112, 94, 0.62
    stand = dict(fT=0, fK=2)
    keys = [
        (0.0, dict(stand, nS=4, nE=-6, fS=6, fE=-6)),
        (0.24, dict(stand, nS=-178, nE=-2, fS=-176, fE=-4, head=-6)),
        (0.36, dict(stand, nS=-178, nE=-2, fS=-176, fE=-4, head=-6)),
        (0.56, dict(stand, lean=48, nT=46, nK=4, nF=40, nS=-178, nE=-2, fS=-176, fE=-4, head=-14)),
        (0.74, dict(stand, lean=84, nT=86, nK=0, nF=70, nS=-178, nE=-2, fS=-176, fE=-4, head=-26)),
        (0.87, dict(stand, lean=82, nT=84, nK=0, nF=70, nS=-178, nE=-2, fS=-176, fE=-4, head=-26)),
        (1.0, dict(stand, lean=84, nT=86, nK=0, nF=70, nS=-178, nE=-2, fS=-176, fE=-4, head=-26)),
    ]
    body, _ = athlete(ctx, s, e, keys, c, x0, gy, sc, shorts=mix(c, "#000000", 0.35), bare=True)
    mat = f'<rect x="74" y="93" width="84" height="3.5" rx="1.7" fill="{mix(c, "#FFFFFF", 0.3)}"></rect>'
    return mat + body


SPORTS6 = [(act_cover_drive, "Cricket"), (act_penalty, "Football"), (act_jump_smash, "Badminton"),
           (act_rally, "Pickleball"), (act_chess, "Chess"), (act_block_start, "Running")]
def on_floor(fn):
    return lambda ctx, c, s, e: gen3.ground() + fn(ctx, c, s, e)


HEALTH4 = [(act_running2, "Running"), (on_floor(act_gym), "Gym"), (act_yoga, "Yoga"), (on_floor(act_meditate), "Meditation")]


def chip(text, cls):
    # a filled pill, so the name stays readable over dark scenes
    w = 6.4 * len(text) + 20
    return (f'<g class="{cls}"><rect x="10" y="8" width="{w:.0f}" height="19" rx="9.5" fill="#FFFFFF" stroke="{GREY}" style="stroke-width: 1.2"></rect>'
            f'<text x="20" y="21.2" class="chip">{text}</text></g>')


gen3.chip = chip  # Transport and Bills use the filled pill too


def sc_acts(ctx, c, p, label, over, acts, grounded=True, neon=False):
    n = len(acts)
    wins = windows(n, 88)
    out = neon_wall(ctx) if neon else ""
    chips = ""
    for i, (fn, text) in enumerate(acts):
        s, e = wins[i]
        out += f'<g class="{act_cls(ctx, i, n, s, e, scale=False)}{" neon" if neon else ""}">{fn(ctx, c, s, e)}</g>'
        chips += chip(text, act_cls(ctx, i, n, s, e, scale=False))
    return out + progress_bottom(ctx, c, p, label, over) + chips, ""


# ---------------------------------------------------------------- Travel: three clear acts
def act_flight(ctx, c, s, e):
    gear = (f'<path d="M34 20 V26 M64 20 V25" stroke="{TYRE}" class="w15"></path>'
            f'<circle cx="34" cy="27.4" r="2.2" fill="{TYRE}"></circle><circle cx="64" cy="26.4" r="2" fill="{TYRE}"></circle>')
    gear_cls = win(ctx, s, e, [(0, "opacity:1"), (0.6, "opacity:1"), (0.7, "opacity:0"), (1, "opacity:0")], timing="linear")
    # taxi and speed up along the runway, rotate, lift off, climb out of view (linear between frames that speed up)
    pl = win(ctx, s, e, [(0, "transform:translate(54px,86px) rotate(0deg) scale(.7)"), (0.16, "transform:translate(62px,86px) rotate(0deg) scale(.7)"),
                         (0.32, "transform:translate(82px,86px) rotate(0deg) scale(.7)"), (0.46, "transform:translate(116px,86px) rotate(0deg) scale(.7)"),
                         (0.54, "transform:translate(142px,84.5px) rotate(-8deg) scale(.7)"), (0.62, "transform:translate(170px,74px) rotate(-14deg) scale(.68)"),
                         (0.8, "transform:translate(222px,46px) rotate(-16deg) scale(.6)"),
                         (1, "transform:translate(282px,14px) rotate(-16deg) scale(.5)")], timing="linear", box="view-box", origin="0 0")
    dust = win(ctx, s, e, [(0, "opacity:0;transform:translate(126px,90px) scale(.5)"), (0.46, "opacity:0;transform:translate(126px,90px) scale(.5)"),
                           (0.52, "opacity:.9;transform:translate(130px,90px) scale(1)"), (0.66, "opacity:0;transform:translate(122px,90px) scale(1.6)"),
                           (1, "opacity:0;transform:translate(122px,90px) scale(1.6)")], box="view-box", origin="0 0")
    lights = "".join(f'<circle cx="{x}" cy="96" r="1.2" fill="{GOLD}"></circle>' for x in range(16, 240, 22))
    runway = ('<rect x="0" y="86" width="248" height="12" fill="#4A5560"></rect>'
              + "".join(f'<path d="M{x} 92 H{x + 10}" stroke="#FFFFFF" class="w2"></path>' for x in range(6, 248, 24)) + lights)
    tower = ('<rect x="12" y="58" width="6" height="28" fill="#C3CCC7"></rect><path d="M7 55 H23 L20 60 H10 Z" fill="#9AA6A0"></path>'
             '<rect x="9.5" y="51.5" width="11" height="3.5" rx="1" fill="#BFDDF8"></rect>')
    clouds = ('<ellipse cx="96" cy="30" rx="16" ry="5" fill="#FFFFFF"></ellipse><ellipse cx="104" cy="26" rx="9" ry="5" fill="#FFFFFF"></ellipse>'
              '<ellipse cx="190" cy="22" rx="14" ry="4.5" fill="#FFFFFF"></ellipse><ellipse cx="197" cy="18.5" rx="8" ry="4.5" fill="#FFFFFF"></ellipse>')
    return (clouds + tower + runway +
            f'<g class="{dust}"><circle cx="0" cy="0" r="5" fill="#FFFFFF" style="fill-opacity: .8"></circle><circle cx="-8" cy="-2" r="4" fill="#FFFFFF" style="fill-opacity: .8"></circle></g>'
            f'<g class="{pl}"><g transform="translate(-42 -20)">{plane(c)}<g class="{gear_cls}">{gear}</g></g></g>')


def act_train(ctx, c, s, e):
    W = win(ctx, s, e, [(0, "transform:rotate(0)"), (1, "transform:rotate(1440deg)")], timing="ease-out", box="fill-box", origin="center")
    tr = win(ctx, s, e, [(0, "transform:translate(-10px,92.5px)"), (0.75, "transform:translate(214px,92.5px)"), (1, "transform:translate(220px,92.5px)")],
             timing="cubic-bezier(.3,.6,.3,1)", box="view-box", origin="0 0")
    rails = ("".join(f'<rect x="{x}" y="92.5" width="5" height="4.5" fill="#8C6A4A"></rect>' for x in range(2, 248, 10))
             + '<path d="M0 92.5 H248 M0 95.5 H248" stroke="#7F8B85" class="w15"></path>')
    poles = "".join(f'<path d="M{x} 93 V46 M{x} 50 H{x + 14}" stroke="#9AA6A0" class="w15"></path>' for x in (40, 130, 220))
    wire = '<path d="M0 52 H248" stroke="#9AA6A0" class="w1"></path>'
    from gen2 import train as train_shape
    body, front, gnd = train_shape(c, W)
    sc = 0.5
    return rails + poles + wire + f'<g class="{tr}"><g transform="translate({-front * sc:.1f} {-gnd * sc:.1f}) scale({sc})">{body}</g></g>'


def act_coach(ctx, c, s, e):
    W = win(ctx, s, e, [(0, "transform:rotate(0)"), (1, "transform:rotate(1080deg)")], timing="ease-out", box="fill-box", origin="center")
    co = win(ctx, s, e, [(0, "transform:translate(-10px,94px)"), (0.7, "transform:translate(196px,94px)"), (1, "transform:translate(196px,94px)")],
             timing="cubic-bezier(.3,.6,.3,1)", box="view-box", origin="0 0")
    road = ('<rect x="0" y="84" width="248" height="14" fill="#5B6770"></rect>'
            + "".join(f'<path d="M{x} 91 H{x + 12}" stroke="#FFFFFF" class="w2"></path>' for x in range(4, 248, 26)))
    hills = '<path d="M0 84 Q40 54 84 84 Q130 48 176 84 Q210 60 248 84 Z" fill="#D7E8D0"></path>'
    from gen2 import coach as coach_shape
    body, front, gnd = coach_shape(c, W)
    sc = 0.6
    return hills + road + f'<g class="{co}"><g transform="translate({-front * sc:.1f} {-gnd * sc:.1f}) scale({sc})">{body}</g></g>'


# ---------------------------------------------------------------- Groceries: the same cart as Shopping
GROCERY = [
    '<circle cx="0" cy="1" r="7" fill="#C8423B"></circle><path d="M0 -6 Q1 -9 3 -10" stroke="#5B3A22" class="w15"></path><path d="M1 -8 Q6 -10 7 -6 Q3 -5 1 -8 Z" fill="#3F8A3A"></path>',
    '<path d="M-6 -4 L-3 -9 H3 L6 -4 V9 H-6 Z" fill="#FFFFFF" stroke="#9AA6A0" style="stroke-width: 1"></path><rect x="-6" y="0" width="12" height="4" fill="#3A86C9"></rect>',
    '<path d="M-10 6 Q-11 -6 0 -6 Q11 -6 10 6 Z" fill="#C58B4E"></path><path d="M-5 -3 L-3 2 M0 -4 L2 1 M5 -3 L6 2" stroke="#9A6534" class="w1"></path>',
    '<path d="M-9 -2 Q0 10 10 -4" stroke="#F2B84B" class="w4"></path><path d="M-8 -6 Q1 6 11 -8" stroke="#E8C24A" class="w4"></path>',
    '<circle cx="0" cy="1" r="8" fill="#6E9F3B"></circle><path d="M-5 -2 Q0 4 5 -2 M-4 3 Q0 7 4 3" stroke="#4E7A26" class="w1"></path>',
    '<path d="M-2 8 L-6 -6 H2 Z M4 8 L1 -5 H8 Z" fill="#E07B39"></path><path d="M-4 -6 L-6 -11 M-2 -6 L-1 -11 M4 -5 L3 -10 M6 -5 L8 -10" stroke="#3F8A3A" class="w15"></path>',
    '<path d="M-8 9 Q-9 -2 -6 -7 H6 Q9 -2 8 9 Z" fill="#E9DCC0" stroke="#B8A57A" style="stroke-width: 1"></path><rect x="-5" y="0" width="10" height="5" rx="1" fill="#C8423B"></rect>',
    '<path d="M-3 -10 H3 V-6 L5 -3 V9 H-5 V-3 L-3 -6 Z" fill="#F2C94C" stroke="#C9A227" style="stroke-width: 1"></path><rect x="-3.5" y="-11.5" width="7" height="2.5" rx="1" fill="#3F8A3A"></rect>',
]


def shop_shapes(c):
    return [
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


CART_K = 0.74  # the cart is drawn at this size, standing on the same spot


def _shopcart(ctx, c, p, label, over, shapes, noun):
    W = ctx.spin(t=16, turns=1)
    slots = [(92, 74), (114, 74), (136, 74), (158, 74), (100, 56), (124, 56), (148, 56), (172, 56), (112, 38), (140, 38)]
    full = 8
    n = max(1, round(p * full))
    items = ""
    for i in range(n):
        x, y = slots[i]
        at = 18 + i * (22 / max(n, 1))
        k = ctx.anim(f"0%,{at:.1f}%{{opacity:0;transform:translate({x}px,{y - 50}px)}}{at + 1:.1f}%{{opacity:1}}"
                     f"{at + 5:.1f}%{{transform:translate({x}px,{y + 2}px)}}{at + 7:.1f}%,100%{{opacity:1;transform:translate({x}px,{y}px)}}",
                     timing="ease-in", box="view-box", origin="0 0")
        tag = f'<circle cx="9" cy="-9" r="4" fill="{RED}"></circle>' if i >= full else ""
        items += f'<g class="{k}">{shapes[i % len(shapes)]}{tag}</g>'
    roll = ctx.anim("0%{transform:translateX(-200px)}14%{transform:translateX(4px)}18%,100%{transform:translateX(0)}", timing="cubic-bezier(.2,.8,.2,1)")
    mesh = mix(c, "#FFFFFF", 0.5)
    front = (f'<path d="M80 62 H190" stroke="{mesh}" class="w1"></path><path d="M82 74 H188" stroke="{mesh}" class="w1"></path>'
             + "".join(f'<path d="M{x} 46 L{x + 2} 86" stroke="{mesh}" class="w1"></path>' for x in range(96, 186, 16))
             + f'<path d="M58 30 H70 L78 46 H196 L186 86 H86 L78 46" stroke="{c}" class="w3" style="fill: none"></path>'
             f'<path d="M86 86 L82 94 H186" stroke="{TYRE}" class="w25"></path>'
             f'<g class="{W}"><circle cx="94" cy="99" r="5" fill="{TYRE}"></circle><circle cx="94" cy="99" r="2" fill="#C9D3CD"></circle></g>'
             f'<g class="{W}"><circle cx="176" cy="99" r="5" fill="{TYRE}"></circle><circle cx="176" cy="99" r="2" fill="#C9D3CD"></circle></g>')
    back = '<path d="M78 46 H196 L186 86 H86 Z" fill="#FFFFFF" style="fill-opacity: .9"></path>'
    k, ox, oy = CART_K, 127, 104
    rim = oy + (46 - oy) * k
    out = (f'<g transform="translate({ox} {oy}) scale({k}) translate({-ox} {-oy})"><g class="{roll}">{back}{items}{front}</g></g>'
           f'<path d="M{ox + (70 - ox) * k:.1f} {rim:.1f} H232" stroke="{c}" class="w15 dash"></path><text x="236" y="{rim - 5:.1f}" class="lbl lbl-end">{label}</text>'
           f'<text x="14" y="112" class="lbl">{min(n, full)} of {full} {noun} · one per eighth of the budget</text>')
    if p > 1:
        out += f'<text x="236" y="21" class="lbl-red {ctx.appear(44)}">{over}</text>'
    return out


# ---------------------------------------------------------------- Groceries: shelves of fruit, vegetables and household things
HALO = "paint-order: stroke; stroke: #FFFFFF; stroke-width: 3px; stroke-linejoin: round"
PANTRY = [  # drawn standing on y = 0
    ("Apple", '<circle cx="0" cy="-6.4" r="6.2" fill="#C8423B"></circle><circle cx="-2.2" cy="-8.4" r="1.6" fill="#FFFFFF" style="fill-opacity: .45"></circle>'
              '<path d="M0 -12 Q0.8 -14.6 2.4 -15.4" stroke="#5B3A22" class="w15"></path><path d="M1.2 -13.4 Q5.4 -15.8 6.4 -12.4 Q3 -11.4 1.2 -13.4 Z" fill="#3F8A3A"></path>'),
    ("Bananas", '<path d="M-8 -3 Q-6 -16 6 -15 Q-2 -12 -3 -2 Z" fill="#F2C94C"></path><path d="M-6 -1 Q-1 -14 9 -10 Q1 -9 -1 0 Z" fill="#E8B83A"></path>'
                '<path d="M-4 0 Q3 -10 10 -5 Q3 -5 1 0.6 Z" fill="#F2C94C"></path><path d="M5.6 -15.4 L7.6 -16.4" stroke="#5B3A22" class="w15"></path>'),
    ("Oranges", '<circle cx="-3.4" cy="-5.4" r="5.4" fill="#F29A2E"></circle><circle cx="4" cy="-5" r="5" fill="#E8892A"></circle>'
                '<circle cx="0.4" cy="-11" r="4.6" fill="#F5A640"></circle><path d="M0.4 -15.4 Q3 -17 4.6 -15.4 Q2.6 -14.2 0.4 -15.4 Z" fill="#3F8A3A"></path>'),
    ("Grapes", "".join(f'<circle cx="{x}" cy="{y}" r="2.5" fill="#7E4AA8"></circle>' for x, y in ((-4.4, -10), (0, -10.6), (4.4, -10), (-2.2, -6.6), (2.2, -6.6), (0, -3)))
               + '<path d="M0 -12.6 V-15.6 Q2.6 -16.6 4 -15" stroke="#5B3A22" class="w15"></path>'),
    ("Tomatoes", '<circle cx="-3.6" cy="-4.8" r="4.8" fill="#D9443A"></circle><circle cx="3.8" cy="-5" r="5" fill="#C8382F"></circle>'
                 '<path d="M2 -10 L3.8 -8.6 L5.6 -10 L4.6 -8 L6.4 -7.4 L3.8 -7.6 Z M-5.4 -9.4 L-3.6 -8.2 L-1.8 -9.4 L-2.8 -7.4 L-1.2 -6.8 L-3.6 -7 Z" fill="#3F8A3A"></path>'),
    ("Carrots", '<path d="M-6 0 L-8.4 -13 H-3.6 Z M0 0 L-2.4 -14 H2.4 Z M6 0 L3.6 -12 H8.4 Z" fill="#E07B39"></path>'
                '<path d="M-6 -13 L-7.4 -17 M-6 -13 L-4.6 -17.4 M0 -14 L-1.2 -18.4 M0 -14 L1.4 -18 M6 -12 L5 -16 M6 -12 L7.6 -16.4" stroke="#3F8A3A" class="w15"></path>'),
    ("Cabbage", '<circle cx="0" cy="-7.4" r="7.4" fill="#7FB04A"></circle><path d="M-5 -9 Q0 -3 5 -9 M-4.6 -4 Q0 -0.6 4.6 -4 M0 -14.6 V-1" stroke="#5C8A2E" class="w1"></path>'),
    ("Brinjal", '<path d="M-7 -2.4 Q-9 -8.6 -2 -10.4 Q4 -12 7.6 -9.4 Q9 -6 4 -3.6 Q-2 -1 -7 -2.4 Z" fill="#5B2C6F"></path>'
                '<path d="M4.4 -10.6 Q7.6 -12.6 9.4 -10.4 L8 -8 Z" fill="#3F8A3A"></path><path d="M-4 -7.6 Q0 -9.4 3 -9" stroke="#8A5AA0" class="w1"></path>'),
    ("Detergent", '<rect x="-6.6" y="-16" width="13.2" height="16" rx="1.2" fill="#3A86C9"></rect><rect x="-6.6" y="-11" width="13.2" height="5" fill="#FFFFFF"></rect>'
                  '<circle cx="0" cy="-8.5" r="2.2" fill="#E8453C"></circle><rect x="-2.6" y="-17.6" width="5.2" height="2" rx="1" fill="#2D6CA6"></rect>'),
    ("Dish wash", '<path d="M-4.6 0 V-10 Q-4.6 -12.6 -2 -13 V-15 H2 V-13 Q4.6 -12.6 4.6 -10 V0 Z" fill="#4CAF7A"></path>'
                  '<rect x="-2.4" y="-17.6" width="4.8" height="3" rx="1" fill="#F2B84B"></rect><rect x="-3.2" y="-8" width="6.4" height="4" rx="1" fill="#FFFFFF" style="fill-opacity: .8"></rect>'),
    ("Tissue rolls", '<rect x="-8" y="-11" width="7.4" height="11" rx="1.2" fill="#FFFFFF" stroke="#C9D3CD" style="stroke-width: .8"></rect>'
                     '<rect x="0.6" y="-11" width="7.4" height="11" rx="1.2" fill="#FFFFFF" stroke="#C9D3CD" style="stroke-width: .8"></rect>'
                     '<ellipse cx="-4.3" cy="-11" rx="3.7" ry="1.2" fill="#EEF2EF"></ellipse><ellipse cx="4.3" cy="-11" rx="3.7" ry="1.2" fill="#EEF2EF"></ellipse>'
                     '<circle cx="-4.3" cy="-11" r=".9" fill="#C9D3CD"></circle><circle cx="4.3" cy="-11" r=".9" fill="#C9D3CD"></circle>'),
    ("Cleaner", '<path d="M-4 0 V-9 Q-4 -11 -2 -11.4 H2 Q4 -11 4 -9 V0 Z" fill="#E86F93"></path>'
                '<path d="M-2.4 -11.4 V-14.4 H3 L6.4 -13 V-11.8 L3 -12.4 V-11.4 Z" fill="#46524C"></path><path d="M-1 -14.4 L-2.6 -10.4" stroke="#46524C" class="w1"></path>'),
]


def sc_pantry(ctx, c, p, label, over):
    """Shelves that fill from the top: fruit, then vegetables, then household things. Full shelves = the budget."""
    boards = (46, 70, 94)
    xs = (92, 118, 144, 170)
    wood, wood_d = "#C99A62", "#A57A48"
    full = 12
    n = max(1, round(p * full))
    unit = (f'<rect x="78" y="22" width="112" height="72" fill="#FBF6EE"></rect>'
            f'<path d="M78 22 V97 M190 22 V97" stroke="{wood_d}" class="w4"></path>'
            + "".join(f'<rect x="76" y="{b}" width="116" height="3.4" rx="1" fill="{wood}"></rect>' for b in boards))
    stripes = "".join(f'<path d="M{x} 12 H{x + 14} V24 Q{x + 7} 30 {x} 24 Z" fill="{c if i % 2 == 0 else "#FFFFFF"}"></path>'
                      for i, x in enumerate(range(71, 197, 14)))
    awning = f'<rect x="70" y="9" width="128" height="4" rx="2" fill="{mix(c, "#000000", 0.25)}"></rect>{stripes}'
    names = "".join(f'<text x="14" y="{b - 6}" class="lbl">{t}</text>' for b, t in zip(boards, ("Fruit", "Vegetables", "Household")))
    items = ""
    spill = [(206, 94), (226, 94), (216, 80), (206, 66), (226, 66)]
    for i in range(n):
        name, svg = PANTRY[i % len(PANTRY)]
        if i < full:
            x, y = xs[i % 4], boards[i // 4]
        else:
            x, y = spill[(i - full) % len(spill)]
        at = 8 + i * (60 / max(n, 1))
        k = ctx.anim(f"0%,{at:.1f}%{{opacity:0;transform:translate({x}px,{y - 18}px) scale(.8)}}{at + 2.5:.1f}%{{opacity:1;transform:translate({x}px,{y + 1.2}px) scale(1.04,.94)}}"
                     f"{at + 4.5:.1f}%,100%{{opacity:1;transform:translate({x}px,{y}px) scale(1)}}", timing="ease-out", box="view-box", origin="0 0")
        tag = f'<circle cx="7" cy="-15" r="3.4" fill="{RED}"></circle>' if i >= full else ""
        items += f'<g class="{k}">{svg}{tag}</g>'
    appear = ctx.anim("0%{opacity:0;transform:translateY(6px)}6%,100%{opacity:1;transform:translateY(0)}", timing="ease-out")
    out = (f'<g class="{appear}">{awning}{unit}</g>{items}{names}'
           + (f'<text x="14" y="112" class="lbl">{full} of {full} filled · <tspan style="fill: #B3261E; font-weight: 700">{over}</tspan></text>' if p > 1 else
              f'<text x="14" y="112" class="lbl">{n} of {full} filled · full shelves = {label.replace("Budget ", "")}</text>'))
    return out


# ---------------------------------------------------------------- Groceries: four bowls side by side that fill up
BOWL_ITEMS = {  # standing on y = 0
    "Fruit": [PANTRY[0][1], PANTRY[1][1], PANTRY[3][1]],
    "Vegetables": [PANTRY[4][1], PANTRY[5][1], PANTRY[7][1]],
    "Bread": [
        '<path d="M-8 0 V-8 Q-8 -13.4 -2.6 -13.4 H2.6 Q8 -13.4 8 -8 V0 Z" fill="#D9A766"></path><path d="M-8 -8 Q-8 -13.4 -2.6 -13.4 H2.6 Q8 -13.4 8 -8 Z" fill="#B97D3E"></path>'
        '<path d="M-3 -12 L-1.4 -9.6 M1 -12.4 L2.6 -10 M4.6 -11.6 L5.8 -9.6" stroke="#E8C48C" class="w1"></path>',
        '<path d="M-8.4 0 Q-9 -7.6 -4.2 -7.8 Q0.6 -7.6 0 0 Z M0 0 Q-0.6 -8.6 4.4 -8.8 Q9.4 -8.6 8.8 0 Z" fill="#C98A48"></path>'
        '<path d="M-6.6 -5 Q-4.2 -6.2 -1.8 -5 M2 -5.8 Q4.4 -7 6.8 -5.8" stroke="#E8C48C" class="w1"></path>',
        '<path d="M-9 -1 Q-10 -4 -7 -5.4 L6.4 -12.4 Q9.6 -13.4 10 -10.4 Q10.2 -8.6 8 -7.6 L-5.6 -0.6 Q-8 0.4 -9 -1 Z" fill="#D9A766"></path>'
        '<path d="M-4 -4.6 L-2.4 -2.4 M0 -6.8 L1.6 -4.4 M4 -8.8 L5.6 -6.6" stroke="#B97D3E" class="w1"></path>',
    ],
    "Others": [
        '<path d="M-5 0 V-11 L-2.6 -14.4 H2.6 L5 -11 V0 Z" fill="#FFFFFF" stroke="#C9D3CD" style="stroke-width: .8"></path>'
        '<rect x="-5" y="-8" width="10" height="4" fill="#3A86C9"></rect><path d="M-2.6 -14.4 L0 -11 L2.6 -14.4" stroke="#C9D3CD" class="w1"></path>',
        "".join(f'<ellipse cx="{x}" cy="{y}" rx="2.7" ry="3.4" fill="#F7EBD8" stroke="#D9C39A" style="stroke-width: .6"></ellipse>'
                for x, y in ((-5, -3.4), (0, -3.6), (5, -3.4), (-2.5, -8.6), (2.5, -8.6))),
        '<path d="M-3.4 0 V-10 L-1.6 -12.4 V-15 H1.6 V-12.4 L3.4 -10 V0 Z" fill="#F2C94C" stroke="#C9A227" style="stroke-width: .8"></path>'
        '<rect x="-2" y="-16.8" width="4" height="2" rx=".8" fill="#3F8A3A"></rect><rect x="-2.6" y="-7.6" width="5.2" height="3.4" rx=".8" fill="#FFFFFF" style="fill-opacity: .8"></rect>',
    ],
}


def sc_bowls(ctx, c, p, label, over):
    """Fruit, vegetables, bread and everything else, in four bowls side by side. Full bowls = the budget."""
    names = list(BOWL_ITEMS)
    xs = (35, 93, 151, 209)
    rim_y, rx, depth = 66, 26, 20
    full = 12
    n = max(1, round(p * full))
    inner = ["#F4D9D2", "#DCEBCF", "#F1E1C8", "#D7E4F2"]
    band = ["#E07B6A", "#7FB04A", "#C98A48", "#3A86C9"]
    seats = [(-9, rim_y + 3), (9, rim_y + 3), (0, rim_y - 5), (-8, rim_y - 13), (8, rim_y - 13), (0, rim_y - 21)]
    backs, fronts, items = "", "", ""
    for b, x in enumerate(xs):
        backs += f'<ellipse cx="{x}" cy="{rim_y}" rx="{rx}" ry="5.4" fill="{inner[b]}"></ellipse>'
        body = (f"M{x - rx} {rim_y} Q{x - rx + 1} {rim_y + depth} {x} {rim_y + depth} Q{x + rx - 1} {rim_y + depth} {x + rx} {rim_y} "
                f"Q{x} {rim_y + 5.6} {x - rx} {rim_y} Z")
        fronts += (f'<path d="M{x - 10} {rim_y + depth - 0.6} L{x - 8} {rim_y + depth + 3.4} H{x + 8} L{x + 10} {rim_y + depth - 0.6} Z" fill="#DCE3DE"></path>'
                   f'<path d="{body}" fill="#FFFFFF" stroke="#C9D3CD" style="stroke-width: .8"></path>'
                   f'<path d="M{x - rx + 1.6} {rim_y + 6} Q{x} {rim_y + 12.6} {x + rx - 1.6} {rim_y + 6}" stroke="{band[b]}" class="w3"></path>'
                   f'<path d="M{x - rx + 6} {rim_y + 13} Q{x - 6} {rim_y + 18} {x + 4} {rim_y + 17.6}" stroke="#EEF2EF" class="w2"></path>'
                   f'<text x="{x}" y="99" class="lbl lbl-mid">{names[b]}</text>')
    for i in range(n):
        b = i % 4
        k_in_bowl = i // 4
        svg = BOWL_ITEMS[names[b]][k_in_bowl % 3]
        dx, y = seats[min(k_in_bowl, len(seats) - 1)]
        x = xs[b] + dx
        at = 10 + i * (58 / max(n, 1))
        k = ctx.anim(f"0%,{at:.1f}%{{opacity:0;transform:translate({x}px,{y - 34}px) rotate(-14deg) scale(1.2)}}"
                     f"{at + 3:.1f}%{{opacity:1;transform:translate({x}px,{y + 1.8}px) rotate(4deg) scale(1.26,1.12)}}"
                     f"{at + 5:.1f}%,100%{{opacity:1;transform:translate({x}px,{y}px) rotate(0deg) scale(1.2)}}", timing="ease-in", box="view-box", origin="0 0")
        tag = f'<circle cx="7" cy="-13" r="3.2" fill="{RED}"></circle>' if i >= full else ""
        items += f'<g class="{k}">{svg}{tag}</g>'
    appear = ctx.anim("0%{opacity:0;transform:translateY(8px)}7%,100%{opacity:1;transform:translateY(0)}", timing="ease-out")
    line = (f'{full} of {full} · <tspan style="fill: #B3261E; font-weight: 700">{over}</tspan>' if p > 1
            else f'{n} of {full} · full bowls = {label.replace("Budget ", "")}')
    # the bowls' fronts sit in front of what is in them
    return (f'<g class="{appear}">{backs}</g>{items}<g class="{appear}">{fronts}</g>'
            f'<text x="14" y="114" class="lbl">{line}</text>')


# ---------------------------------------------------------------- Shopping: a trolley that fills, one thing at a time
def sc_trolley2(ctx, c, p, label, over):
    """The trolley rolls in and stops; things drop in one by one, the basket dips with each and a counter ticks up."""
    K = 1.12  # trolley size: small to medium
    gx, gyy = 92, 94 - 34 * K
    full = 8
    n = max(1, round(p * full))
    roll_end = 14.0
    lands = [20 + i * (52 / max(n, 1)) + 4.2 for i in range(n)]
    fr = [f"0%{{transform:translateX(-190px)}}{roll_end - 2:.1f}%{{transform:translateX(3px)}}{roll_end:.1f}%{{transform:translateX(0)}}"]
    for t in lands:
        fr.append(f"{t - 0.2:.1f}%{{transform:translateX(0) translateY(0)}}{t + 0.8:.1f}%{{transform:translateX(0) translateY(1.8px)}}"
                  f"{t + 2.6:.1f}%{{transform:translateX(0) translateY(0)}}")
    fr.append("100%{transform:translateX(0) translateY(0)}")
    body_k = ctx.anim("".join(fr), timing="ease-out")
    W = ctx.anim(f"0%{{transform:rotate(0deg)}}{roll_end:.1f}%,100%{{transform:rotate({190 / (2 * math.pi * 3.4) * 360:.0f}deg)}}", timing="ease-out",
                 box="fill-box", origin="center")
    back, front = trolley(c, W)
    shapes = shop_shapes(c)
    slots = [(23, 14), (33, 14), (43, 14), (53, 14), (26, 3), (36, 3), (46, 3), (56, 3), (30, -9), (42, -9), (54, -9), (40, -19)]
    items, sparks = "", ""
    for i in range(n):
        lx, ly = slots[min(i, len(slots) - 1)]
        tx, ty = gx + lx * K, gyy + ly * K
        t0 = (20 + i * (52 / max(n, 1))) / 100
        side = -1 if i % 2 == 0 else 1
        start = (tx + side * 70, -14)
        k = flight(ctx, 0, 100, [(t0, start, t0 + 0.042, (tx, ty), ((start[0] + tx) / 2, -26), 0.75, 0.75)], spin=-side * 3)
        tag = f'<circle cx="9" cy="-9" r="4" fill="{RED}"></circle>' if i >= full else ""
        items += f'<g class="{k}">{shapes[i % len(shapes)]}{tag}</g>'
        at = lands[i]
        sk = ctx.anim(f"0%,{at - 0.5:.1f}%{{opacity:0;transform:translate({tx:.1f}px,{ty - 8:.1f}px) scale(.3)}}{at + 0.8:.1f}%{{opacity:1;transform:translate({tx:.1f}px,{ty - 8:.1f}px) scale(1)}}"
                      f"{at + 4:.1f}%,100%{{opacity:0;transform:translate({tx:.1f}px,{ty - 12:.1f}px) scale(1.3)}}", box="view-box", origin="0 0")
        sparks += (f'<g class="{sk}"><path d="M0 -6 L1.2 -1.2 L6 0 L1.2 1.2 L0 6 L-1.2 1.2 L-6 0 L-1.2 -1.2 Z" fill="{GOLD}"></path></g>')
    # a counter on the trolley that ticks up with each thing added
    bx, by = gx - 2, gyy - 12
    counts = ""
    for i in range(n):
        a = lands[i]
        b = lands[i + 1] if i + 1 < n else 101
        vis = (f"0%,{a - 0.1:.1f}%{{opacity:0}}{a:.1f}%{{opacity:1}}" + (f"{b - 0.1:.1f}%{{opacity:1}}{b:.1f}%,100%{{opacity:0}}" if b <= 100 else "100%{opacity:1}"))
        counts += f'<text x="{bx}" y="{by + 3.6:.1f}" class="lbl lbl-mid {ctx.anim(vis, timing="step-end")}" style="fill: #FFFFFF; font-weight: 700; font-size: 10px">{i + 1}</text>'
    pulse = ctx.anim("".join(f"{t - 0.1:.1f}%{{transform:scale(1)}}{t + 0.9:.1f}%{{transform:scale(1.3)}}{t + 2.4:.1f}%{{transform:scale(1)}}" for t in lands)
                     + "100%{transform:scale(1)}", box="fill-box", origin="center")
    badge_in = ctx.anim(f"0%,{lands[0] - 0.2:.1f}%{{opacity:0}}{lands[0]:.1f}%,100%{{opacity:1}}", timing="linear")
    badge = f'<g class="{badge_in}"><g class="{pulse}"><circle cx="{bx}" cy="{by}" r="7" fill="{c}" stroke="#FFFFFF" style="stroke-width: 1.4"></circle></g>{counts}</g>'
    rim = gyy - 4 * K
    cart = (f'<g class="{body_k}"><g transform="translate({gx} {gyy:.1f}) scale({K})">{back}</g>{items}'
            f'<g transform="translate({gx} {gyy:.1f}) scale({K})">{front}</g>{badge}</g>')
    shadow = f'<ellipse cx="{gx + 38 * K:.1f}" cy="95" rx="{34 * K:.1f}" ry="2.4" fill="#16211C" class="sh"></ellipse>'
    floor = '<path d="M12 95 H236" stroke="#E3E9E5" class="w2"></path>'
    out = (floor + shadow + cart + sparks
           + f'<path d="M{gx + 8 * K:.1f} {rim:.1f} H232" stroke="{c}" class="w15 dash"></path>'
           f'<text x="236" y="{rim - 5:.1f}" class="lbl lbl-end">{label}</text>'
           f'<text x="14" y="112" class="lbl">{min(n, full)} of {full} items · one per eighth of the budget</text>')
    if p > 1:
        out += f'<text x="236" y="21" class="lbl-red {ctx.appear(74)}">{over}</text>'
    return out


# ---------------------------------------------------------------- Shopping: pushing a trolley down the aisle, picking things off the shelves
def trolley(c, W):
    """A side-view trolley with its grip at (0, 0); wheels touch the ground at y = 34. Returns (back, front)."""
    tl, tr, br, bl = (8, -3), (68, -5), (61, 21), (15, 21)

    def at(a, b, t):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
    wires = ""
    for i in range(1, 12):
        t = i / 12
        a, b = at(tl, tr, t), at(bl, br, t)
        wires += f'<path d="M{a[0]:.1f} {a[1]:.1f} L{b[0]:.1f} {b[1]:.1f}" stroke="#AAB4AE" style="stroke-width: .8"></path>'
    for j in (0.33, 0.66):
        a, b = at(tl, bl, j), at(tr, br, j)
        wires += f'<path d="M{a[0]:.1f} {a[1]:.1f} L{b[0]:.1f} {b[1]:.1f}" stroke="#AAB4AE" style="stroke-width: .8"></path>'
    back = f'<path d="M{tl[0]} {tl[1]} L{tr[0]} {tr[1]} L{br[0]} {br[1]} L{bl[0]} {bl[1]} Z" fill="#FFFFFF" style="fill-opacity: .55"></path>'
    far = (f'<path d="M23 22 L25 30 M63 22 L63 30" stroke="#9AA6A0" class="w15"></path>'
           f'<circle cx="25" cy="30.6" r="3.2" fill="#46524C"></circle><circle cx="63" cy="30.6" r="3.2" fill="#46524C"></circle>')
    wheel = (lambda x: f'<g class="{W}"><circle cx="{x}" cy="31" r="3.4" fill="{TYRE}"></circle><path d="M{x - 2.2} 31 H{x + 2.2}" stroke="#9AA6A0" class="w1"></path></g>')
    front = (wires
             + f'<path d="M{tl[0]} {tl[1]} L{tr[0]} {tr[1]} L{br[0]} {br[1]} L{bl[0]} {bl[1]} Z" stroke="#5B6770" class="w2" style="fill: none"></path>'
             + f'<path d="M{tr[0] - 1} {tr[1] - 0.6} L{tr[0] + 3} {tr[1] + 3}" stroke="{c}" class="w3"></path>'
             + f'<path d="M1.4 1.6 L{bl[0]} {bl[1]} L17 26 H59 L{br[0]} {br[1]} M17 26 L19 30 M59 26 L57 30" stroke="#5B6770" class="w15"></path>'
             + f'<path d="M20 25.4 H56" stroke="#AAB4AE" class="w1"></path>'
             + f'<path d="M0 0 L{tl[0]} {tl[1]}" stroke="#5B6770" class="w2"></path>'
             + f'<rect x="-2.6" y="-3.4" width="5.2" height="6.8" rx="2" fill="{c}"></rect>'
             + wheel(19) + wheel(57))
    return far + back, front


def sc_trolley(ctx, c, p, label, over):
    gy, x0, sc = 94, 70, 0.5
    walk = [dict(nT=-22, nK=6, fT=18, fK=14), dict(nT=-4, nK=12, fT=-10, fK=50),
            dict(nT=18, nK=14, fT=-22, fK=6), dict(nT=-10, nK=50, fT=-4, fK=12)]
    arms = dict(lean=10, nS=-50, nE=-46, fS=-46, fE=-50, head=-4)
    keys, u, i = [], 0.0, 0
    while u <= 0.76:
        keys.append((round(u, 4), dict(arms, **walk[i % 4])))
        u += 0.0385
        i += 1
    stop = round(u + 0.05, 4)
    keys.append((stop, dict(arms, nT=-6, nK=8, fT=6, fK=8, lean=8)))
    keys.append((1.0, dict(arms, nT=-6, nK=8, fT=6, fK=8, lean=8, head=-10)))
    walk_end = keys[-3][0]
    shopper, R = athlete(ctx, 0, 100, keys, c, x0, gy, sc, shorts="#3B4A57", n=150)
    hx, hy = world(R[0][1], hand_at(R[0][1]), x0, gy, sc)
    gx, gyy = hx + 1, gy - 34
    dist = 33 * sc * (walk_end / 0.0385)
    floor_k = win(ctx, 0, 100, [(0, "transform:translateX(0)"), (walk_end, f"transform:translateX({-dist:.1f}px)"),
                                (stop, f"transform:translateX({-dist - 4:.1f}px)"), (1, f"transform:translateX({-dist - 4:.1f}px)")], timing="linear")
    shelf_k = win(ctx, 0, 100, [(0, "transform:translateX(0)"), (walk_end, f"transform:translateX({-dist * 0.6:.1f}px)"),
                                (stop, f"transform:translateX({-dist * 0.6 - 2:.1f}px)"), (1, f"transform:translateX({-dist * 0.6 - 2:.1f}px)")], timing="linear")
    turns = (dist + 4) / (2 * math.pi * 3.4) * 360
    W = win(ctx, 0, 100, [(0, "transform:rotate(0deg)"), (stop, f"transform:rotate({turns:.0f}deg)"), (1, f"transform:rotate({turns:.0f}deg)")],
            timing="linear", box="fill-box", origin="center")
    back, front = trolley(c, W)
    # the store behind: two shelves of boxes, bottles and bags
    palette = ["#E86F93", "#3A86C9", "#F2B84B", "#7FB04A", "#B7A6E8", "#E07B39", "#4CAF7A", "#C8423B"]
    goods = ""
    for k in range(34):
        x = -8 + k * 13
        col = palette[(k * 3) % len(palette)]
        col2 = palette[(k * 5 + 2) % len(palette)]
        h1, h2 = 8 + (k * 7) % 6, 7 + (k * 5) % 5
        goods += (f'<rect x="{x}" y="{36 - h1}" width="10" height="{h1}" rx="1.2" fill="{col}"></rect>'
                  f'<rect x="{x + 1.5}" y="{56 - h2}" width="9" height="{h2}" rx="{3 if k % 3 == 0 else 1}" fill="{col2}"></rect>')
    store = (f'<g style="opacity: .5"><g class="{shelf_k}">{goods}'
             + "".join(f'<rect x="-10" y="{y}" width="460" height="2.6" fill="#C9D3CD"></rect>' for y in (36, 56))
             + "".join(f'<path d="M{x} 16 V92" stroke="#DCE3DE" class="w2"></path>' for x in range(-10, 460, 104)) + "</g></g>")
    tiles = "".join(f'<path d="M{x} 95 L{x - 3} 101" stroke="#D3DBD6" class="w1"></path>' for x in range(-6, 560, 16))
    floor = f'<rect x="0" y="94" width="248" height="7" fill="#EEF2EF"></rect><g class="{floor_k}">{tiles}</g>'
    # things jump off the shelf into the trolley
    shapes = shop_shapes(c)
    slots = [(23, 14), (33, 14), (43, 14), (53, 14), (26, 3), (36, 3), (46, 3), (56, 3), (30, -9), (42, -9), (54, -9), (40, -19)]
    full = 8
    n = max(1, round(p * full))
    items = ""
    for i in range(n):
        lx, ly = slots[min(i, len(slots) - 1)]
        tx, ty = gx + lx, gyy + ly
        u0 = 0.06 + i * (min(walk_end, 0.74) - 0.12) / max(n, 1)
        sx, sy = tx + 34 - (i % 3) * 8, 30 if i % 2 == 0 else 50
        k = flight(ctx, 0, 100, [(u0, (sx, sy), u0 + 0.07, (tx, ty), ((sx + tx) / 2, min(sy, ty) - 18), 0.66, 0.66)], spin=4)
        tag = f'<circle cx="9" cy="-9" r="4" fill="{RED}"></circle>' if i >= full else ""
        items += f'<g class="{k}">{shapes[i % len(shapes)]}{tag}</g>'
    rim = gyy - 4
    cart = (f'<g transform="translate({gx:.1f} {gyy:.1f})">{back}</g>{items}'
            f'<g transform="translate({gx:.1f} {gyy:.1f})">{front}</g>')
    shadow = f'<ellipse cx="{gx + 38:.1f}" cy="{gy + 1}" rx="34" ry="2.4" fill="#16211C" class="sh"></ellipse>'
    out = (store + floor + shadow + shopper + cart
           + f'<path d="M{gx + 8:.1f} {rim:.1f} H232" stroke="{c}" class="w15 dash"></path>'
           f'<text x="236" y="{rim - 5:.1f}" class="lbl lbl-end" style="{HALO}">{label}</text>'
           f'<text x="14" y="112" class="lbl">{min(n, full)} of {full} items · one per eighth of the budget</text>')
    if p > 1:
        out += f'<text x="236" y="21" class="lbl-red {ctx.appear(70)}" style="{HALO}">{over}</text>'
    return out


# ---------------------------------------------------------------- Entertainment: movies, amusement park, concert, bowling, streaming
def floor_line(y=94):
    return f'<path d="M8 {y} H240" stroke="#D3DBD6" class="w2"></path>'


def act_movies(ctx, c, s, e):
    D = mix(c, "#000000", 0.45)
    beam = win(ctx, s, e, [(0, "opacity:0"), (0.12, "opacity:.5"), (0.4, "opacity:.38"), (0.55, "opacity:.5"), (0.8, "opacity:.4"), (1, "opacity:.5")], timing="linear")
    glow = win(ctx, s, e, [(0, "opacity:0"), (0.14, "opacity:1"), (1, "opacity:1")], timing="linear")
    sun = win(ctx, s, e, [(0, "transform:translate(70px,60px)"), (1, "transform:translate(150px,42px)")], timing="linear", box="view-box", origin="0 0")
    bird = win(ctx, s, e, [(0, "transform:translate(184px,44px)"), (1, "transform:translate(84px,52px)")], timing="linear", box="view-box", origin="0 0")
    flap = cycles(ctx, "0%,100%{transform:scaleY(1)}50%{transform:scaleY(-.6)}", 0.32, s, 6, box="fill-box", origin="center")
    screen = (f'<rect x="50" y="22" width="148" height="54" rx="3" fill="#2B3138"></rect>'
              f'<g class="{glow}"><rect x="54" y="26" width="140" height="46" fill="#CFE6F7"></rect>'
              f'<g class="{sun}"><circle cx="0" cy="0" r="7" fill="{GOLD}"></circle></g>'
              f'<path d="M54 72 V60 Q78 46 104 58 Q128 68 150 54 Q172 42 194 56 V72 Z" fill="#7DB46C"></path>'
              f'<path d="M54 72 V66 Q90 58 124 66 Q160 74 194 64 V72 Z" fill="#5E9A55"></path>'
              f'<g class="{bird}"><g class="{flap}"><path d="M-5 0 Q-2.5 -3 0 0 Q2.5 -3 5 0" stroke="#2B3138" class="w15"></path></g></g></g>')
    screen = f'<g transform="translate(10 9) scale(.94)">{screen}</g>'
    proj = (f'<g class="{beam}"><path d="M236 6 L60.8 33.4 L60.8 76.7 Z" fill="#FFFFFF" style="fill-opacity: .55"></path></g>'
            f'<rect x="230" y="2" width="14" height="8" rx="2" fill="#46524C"></rect>')
    seats = "".join(f'<rect x="{x}" y="80" width="26" height="15" rx="5" fill="{D}"></rect>' for x in (34, 64, 94, 124, 154, 184))
    heads = (f'<circle cx="77" cy="76" r="6" fill="#2B3138"></circle><path d="M69 84 Q77 76 85 84 Z" fill="#2B3138"></path>'
             f'<circle cx="137" cy="77" r="5.6" fill="#2B3138"></circle><circle cx="137" cy="72.6" r="3.4" fill="#2B3138"></circle>')
    pops = ""
    for i, (dx, h, d) in enumerate(((-3, 16, 0), (2, 22, 0.12), (5, 13, 0.24), (0, 19, 0.36))):
        k = cycles(ctx, f"0%{{transform:translate(0,0)}}45%{{transform:translate({dx * 1.6}px,-{h}px)}}100%{{transform:translate({dx * 2.4}px,0)}}", 0.6, s + d, 3,
                   timing="ease-in-out")
        pops += f'<g class="{k}"><circle cx="{221 + dx}" cy="74" r="2.3" fill="#FFF6DD" stroke="#E2C27A" style="stroke-width: .6"></circle></g>'
    bucket = (f'<path d="M210 76 H232 L229 95 H213 Z" fill="#FFFFFF"></path>'
              f'<path d="M214.6 76 L216.4 95 M221 76 V95 M227.4 76 L225.6 95" stroke="{RED}" class="w3"></path>'
              + "".join(f'<circle cx="{x}" cy="{y}" r="3" fill="#FFF6DD"></circle>' for x, y in ((213, 75), (218, 73), (223, 74), (228, 75), (220.5, 71)))
              + '<path d="M210 76 H232" stroke="#E2C27A" class="w1"></path>')
    return screen + proj + seats + heads + pops + bucket


def bez(p0, p1, p2, p3, t):
    u = 1 - t
    return (u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
            u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1])


def act_park(ctx, c, s, e):
    # a Ferris wheel that turns with its cabins hanging level, and a coaster that climbs, drops and leaves
    cx, cy, r = 78, 60, 30
    turn = win(ctx, s, e, [(0, "transform:rotate(0deg)"), (1, "transform:rotate(150deg)")], timing="linear", box="view-box", origin=f"{cx}px {cy}px")
    level = win(ctx, s, e, [(0, "transform:rotate(0deg)"), (1, "transform:rotate(-150deg)")], timing="linear", box="view-box", origin="0px 0px")
    cols = [c, GOLD, "#3A86C9", "#E86F93"]
    spokes = "".join(f'<path d="M{cx} {cy} L{cx + r * math.sin(math.radians(a)):.1f} {cy - r * math.cos(math.radians(a)):.1f}" stroke="#9AA6A0" class="w1"></path>'
                     for a in range(0, 360, 30))
    cabins = ""
    for i, a in enumerate(range(0, 360, 45)):
        x, y = cx + r * math.sin(math.radians(a)), cy - r * math.cos(math.radians(a))
        cabins += (f'<g transform="translate({x:.1f} {y:.1f})"><g class="{level}"><path d="M0 0 V2" stroke="#46524C" class="w1"></path>'
                   f'<path d="M-4.4 2 H4.4 L3.8 8 Q0 10 -3.8 8 Z" fill="{cols[i % 4]}"></path><rect x="-3" y="3" width="6" height="2" rx="1" fill="#FFFFFF" style="fill-opacity: .7"></rect></g></g>')
    wheel = (f'<path d="M{cx} {cy} L{cx - 18} 94 M{cx} {cy} L{cx + 18} 94" stroke="#7F8B85" class="w3"></path>'
             f'<g class="{turn}"><circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{c}" class="w2"></circle>'
             f'<circle cx="{cx}" cy="{cy}" r="{r - 6}" fill="none" stroke="{mix(c, "#FFFFFF", 0.5)}" class="w1"></circle>{spokes}{cabins}</g>'
             f'<circle cx="{cx}" cy="{cy}" r="3.4" fill="#46524C"></circle>')
    segs = [((112, 92), (124, 92), (134, 92), (142, 88)), ((142, 88), (154, 80), (158, 34), (172, 31)), ((172, 31), (188, 28), (190, 90), (206, 90)),
            ((206, 90), (218, 90), (222, 62), (234, 62)), ((234, 62), (248, 62), (262, 84), (300, 90))]
    pts = []
    for sg in segs:
        pts += [bez(*sg, i / 40) for i in range(40)]
    pts.append(segs[-1][3])
    dist = [0.0]
    for a, b in zip(pts, pts[1:]):
        dist.append(dist[-1] + math.dist(a, b))

    def at(d):
        d = max(0.0, min(d, dist[-1]))
        for i in range(1, len(dist)):
            if dist[i] >= d:
                f = (d - dist[i - 1]) / (dist[i] - dist[i - 1] or 1)
                a, b = pts[i - 1], pts[i]
                return a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
        return pts[-1][0], pts[-1][1], 0.0
    # time to travel: a steady chain lift to the top, then gravity
    top = min(range(len(pts)), key=lambda i: pts[i][1] if pts[i][0] < 180 else 999)
    s0, v_lift, g = 30.0, 1.0, 0.09
    times, t = [(0.0, s0)], 0.0
    d = s0
    step = 1.0
    while d < dist[-1]:
        y = at(d)[1]
        v = v_lift if d < dist[top] else math.sqrt(v_lift ** 2 + 2 * g * max(0.0, y - pts[top][1]))
        t += step / v
        d += step
        times.append((t, d))
    T = times[-1][0]

    def d_at(u):  # u in [0, 1] of the moving part
        tt = u * T
        for (t0, d0), (t1, d1) in zip(times, times[1:]):
            if t1 >= tt:
                return d0 + (d1 - d0) * ((tt - t0) / ((t1 - t0) or 1))
        return times[-1][1]
    track = "M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in pts[::2])
    supports = "".join(f'<path d="M{x:.1f} {y + 1.5:.1f} V94" stroke="#C3CCC7" class="w1"></path>' for x, y in pts[6:160:7] if y < 90)
    cars = ""
    for k in range(3):
        frames = []
        for i in range(41):
            u = i / 40
            mv = 0.0 if u < 0.08 else min(1.0, (u - 0.08) / 0.9)
            x, y, a = at(d_at(mv) - 11 * k)
            frames.append((u, f"transform:translate({x:.1f}px,{y:.1f}px) rotate({a:.1f}deg)"))
        kc = win(ctx, s, e, frames, timing="linear", box="view-box", origin="0 0")
        arms = '<path d="M-1 -9 L-2.4 -13 M2 -9 L3.4 -13" stroke="#C68A5E" class="w1"></path>' if k == 0 else ""
        cars += (f'<g class="{kc}"><circle cx="-1.6" cy="-7.6" r="2" fill="#2B2118"></circle><circle cx="2.4" cy="-7.6" r="2" fill="#7A5232"></circle>{arms}'
                 f'<path d="M-5 -6 H5 Q5.6 -6 5.4 -5 L4.6 -1.4 H-4.6 L-5.4 -5 Q-5.6 -6 -5 -6 Z" fill="{cols[k]}"></path>'
                 f'<circle cx="-2.6" cy="-0.8" r="1.1" fill="#2F3A35"></circle><circle cx="2.6" cy="-0.8" r="1.1" fill="#2F3A35"></circle></g>')
    coaster = supports + f'<path d="{track}" stroke="#7F8B85" class="w2" style="fill: none"></path>' + cars
    flags = "".join(f'<path d="M{x} {y} l3 6 l3 -6 Z" fill="{cols[i % 4]}"></path>' for i, (x, y) in enumerate(((14, 38), (21, 40.5), (28, 42.5), (35, 44))))
    return floor_line() + '<path d="M12 38 Q26 46 42 44" stroke="#9AA6A0" class="w1"></path>' + flags + wheel + coaster


def mic_svg():
    return f'<path d="M0 -1 V3.4" stroke="{TYRE}" class="w2"></path><circle cx="0" cy="5" r="2.2" fill="#5B6770"></circle>'


def act_concert(ctx, c, s, e):
    back = (f'<rect x="38" y="12" width="172" height="62" rx="3" fill="#1F2630"></rect>'
            f'<path d="M38 17 H210" stroke="#5B6770" class="w3"></path>'
            + "".join(f'<path d="M{x} 15.5 L{x + 4} 18.5 L{x + 8} 15.5" stroke="#5B6770" class="w1"></path>' for x in range(42, 206, 8)))
    beams = ""
    for i, (x, col, a0, a1) in enumerate(((70, GOLD, -14, 16), (178, c, 14, -16), (124, "#9CC8F5", -8, 8))):
        k = cycles(ctx, f"0%,100%{{transform:rotate({a0}deg)}}50%{{transform:rotate({a1}deg)}}", 1.2, s, 2, box="view-box", origin=f"{x}px 18px")
        beams += (f'<g class="{k}"><path d="M{x} 18 L{x - 15} 74 H{x + 15} Z" fill="{col}" style="fill-opacity: .32"></path></g>'
                  f'<rect x="{x - 4}" y="17" width="8" height="5" rx="1.5" fill="#46524C"></rect>')
    stage = '<rect x="38" y="74" width="172" height="6" fill="#3B4A57"></rect>'
    A = dict(lean=-6, nT=-6, nK=10, fT=8, fK=12, nS=-60, nE=-142, fS=-168, fE=-8, head=-10)
    B = dict(lean=-2, nT=-4, nK=24, fT=10, fK=26, nS=-60, nE=-142, fS=-138, fE=-34, head=-4)
    keys = [(i / 8, A if i % 2 == 0 else B) for i in range(9)]
    singer, _ = athlete(ctx, s, e, keys, c, 124, 74, 0.5, prop=mic_svg(), shorts="#2B3138")
    notes = ""
    for i, (x, d) in enumerate(((148, 0), (100, 0.5), (160, 1.0))):
        k = cycles(ctx, f"0%{{opacity:0;transform:translate({x}px,46px)}}20%{{opacity:1}}100%{{opacity:0;transform:translate({x + 10}px,22px)}}", 1.5, s + d * 5, 1,
                   box="view-box", origin="0 0", timing="ease-out")
        notes += (f'<g class="{k}"><path d="M0 0 V-8 L5 -9.6 V-1.6" stroke="#FFFFFF" class="w1"></path>'
                  f'<ellipse cx="-1.4" cy="0.4" rx="1.8" ry="1.3" fill="#FFFFFF"></ellipse><ellipse cx="3.6" cy="-1.2" rx="1.8" ry="1.3" fill="#FFFFFF"></ellipse></g>')
    crowd = ""
    for i, x in enumerate((26, 52, 80, 108, 136, 164, 192, 220)):
        up = i in (1, 4, 6)
        bob = cycles(ctx, "0%,100%{transform:translateY(0)}50%{transform:translateY(-2.5px)}", 0.6, s + (i % 3) * 0.05, 4)
        arm = (f'<path d="M{x + 5} 92 L{x + 9} 80 L{x + 10} 74" stroke="#2B3138" class="w3"></path>'
               f'<circle cx="{x + 10}" cy="73" r="1.6" fill="{GOLD}"></circle>') if up else ""
        crowd += f'<g class="{bob}"><circle cx="{x}" cy="85" r="5.4" fill="#2B3138"></circle><path d="M{x - 10} 98 Q{x - 9} 89 {x} 89 Q{x + 9} 89 {x + 10} 98 Z" fill="#2B3138"></path>{arm}</g>'
    return back + beams + stage + singer + notes + crowd


def act_bowling(ctx, c, s, e):
    x0, gy, sc = 40, 91, 0.52
    stand = dict(nT=-4, nK=6, fT=6, fK=8, nS=-40, nE=-96, fS=-30, fE=-80, head=-4)
    keys = [
        (0.0, stand),
        (0.18, dict(lean=14, nT=-16, nK=22, fT=16, fK=22, nS=62, nE=-6, fS=-60, fE=-30, head=-6)),
        (0.34, dict(x=6, lean=34, nT=-46, nK=62, fT=58, fK=8, fF=60, nS=-22, nE=-4, fS=-80, fE=-10, head=-14)),
        (0.6, dict(x=6, lean=30, nT=-46, nK=62, fT=58, fK=8, fF=60, nS=-128, nE=-24, fS=-84, fE=-12, head=-12)),
        (1.0, dict(x=6, lean=28, nT=-46, nK=62, fT=58, fK=8, fF=60, nS=-132, nE=-26, fS=-84, fE=-12, head=-10)),
    ]
    hold = win(ctx, s, e, [(0, "opacity:1"), (0.335, "opacity:1"), (0.34, "opacity:0"), (1, "opacity:0")], timing="linear")
    ball_c = mix(c, "#000000", 0.25)
    held = f'<g class="{hold}"><circle cx="0" cy="3.4" r="7" fill="{ball_c}"></circle></g>'
    body, R = athlete(ctx, s, e, keys, c, x0, gy, sc, prop=held, shorts="#3B4A57")
    q = R[2][1]
    hx, hy = add(hand_at(q), rot((0, 3.4), q["propW"]))
    bx, by = world(q, (hx, hy), x0, gy, sc)
    rb = 3.6
    roll = win(ctx, s, e, [(0, f"opacity:0;transform:translate({bx:.1f}px,{by:.1f}px) rotate(0deg)"), (0.335, f"opacity:0;transform:translate({bx:.1f}px,{by:.1f}px) rotate(0deg)"),
                           (0.34, f"opacity:1;transform:translate({bx:.1f}px,{by:.1f}px) rotate(0deg)"),
                           (0.38, f"opacity:1;transform:translate({bx + 6:.1f}px,{gy - rb:.1f}px) rotate(120deg)"),
                           (0.6, f"opacity:1;transform:translate(204px,{gy - rb:.1f}px) rotate(1500deg)"),
                           (0.7, f"opacity:1;transform:translate(236px,{gy - rb:.1f}px) rotate(1900deg)"),
                           (0.76, f"opacity:0;transform:translate(252px,{gy - rb:.1f}px) rotate(2100deg)"), (1, f"opacity:0;transform:translate(252px,{gy - rb:.1f}px) rotate(2100deg)")],
               timing="linear", box="view-box", origin="0 0")
    ball = (f'<g class="{roll}"><circle cx="0" cy="0" r="{rb}" fill="{ball_c}"></circle>'
            f'<circle cx="1.2" cy="-1.4" r=".7" fill="#1F2630"></circle><circle cx="-0.4" cy="-1.9" r=".7" fill="#1F2630"></circle><circle cx="-0.9" cy="0.6" r=".7" fill="#1F2630"></circle></g>')
    pin_d = "M-2.2 0 Q-3.6 -4 -2.1 -7.2 Q-1.2 -9 -1.5 -10.6 Q-1.6 -13.2 0 -13.2 Q1.6 -13.2 1.5 -10.6 Q1.2 -9 2.1 -7.2 Q3.6 -4 2.2 0 Z"
    pins = ""
    for i, (x, y, dx, dy, r1) in enumerate(((210, 91, 12, -16, 110), (216, 89.6, 18, -24, -160), (222, 91, 16, -10, 200), (228, 89.6, 10, -20, -120))):
        k = win(ctx, s, e, [(0, f"transform:translate({x}px,{y}px) rotate(0deg)"), (0.58 + i * 0.012, f"transform:translate({x}px,{y}px) rotate(0deg)"),
                            (0.68 + i * 0.01, f"transform:translate({x + dx}px,{y + dy}px) rotate({r1}deg)"),
                            (0.8, f"transform:translate({x + dx * 1.7:.1f}px,{gy - 3}px) rotate({90 if r1 > 0 else -90}deg)"),
                            (1, f"transform:translate({x + dx * 1.7:.1f}px,{gy - 3}px) rotate({90 if r1 > 0 else -90}deg)")],
                timing="ease-out", box="view-box", origin="0 0")
        pins += (f'<g class="{k}"><path d="{pin_d}" fill="#FFFFFF" stroke="#C9D3CD" style="stroke-width: .6"></path>'
                 f'<path d="M-1.6 -9.6 H1.6 M-1.7 -8.4 H1.7" stroke="{RED}" style="stroke-width: .7"></path></g>')
    strike = win(ctx, s, e, [(0, "opacity:0;transform:translate(214px,50px) scale(.6)"), (0.66, "opacity:0;transform:translate(214px,50px) scale(.6)"),
                             (0.72, "opacity:1;transform:translate(214px,50px) scale(1.1)"), (0.78, "opacity:1;transform:translate(214px,50px) scale(1)"),
                             (1, "opacity:1;transform:translate(214px,50px) scale(1)")], box="view-box", origin="0 0")
    lane = (f'<rect x="0" y="{gy - 1}" width="248" height="6" fill="#E3C08D"></rect><path d="M0 {gy + 0.6} H248" stroke="#F2DDB5" class="w1"></path>'
            f'<rect x="0" y="{gy + 5}" width="248" height="2" fill="#7F8B85"></rect><path d="M60 {gy - 1} V{gy + 5}" stroke="{RED}" class="w1"></path>'
            + "".join(f'<path d="M{x} {gy + 2} l2 -1.4 l2 1.4" stroke="#B88E52" style="stroke-width: .7; fill: none"></path>' for x in (110, 124, 138)))
    return (lane + body + pins + ball
            + f'<g class="{strike}"><text x="0" y="0" class="lbl" style="text-anchor: middle; font-size: 13px; font-weight: 800; fill: {c}">STRIKE!</text></g>')


def act_streaming(ctx, c, s, e):
    D = mix(c, "#000000", 0.35)
    sofa_back = (f'<rect x="20" y="56" width="16" height="30" rx="5" fill="{D}"></rect>'
                 f'<rect x="20" y="76" width="70" height="12" rx="4" fill="{mix(c, "#000000", 0.2)}"></rect>')
    sofa_front = f'<rect x="80" y="70" width="12" height="20" rx="4" fill="{D}"></rect><path d="M24 88 V94 M86 88 V94" stroke="#46524C" class="w2"></path>'
    seated = dict(lean=-14, nT=-90, nK=88, fT=-84, fK=86, nS=-58, nE=-40, fS=-20, fE=-70, head=-6)
    press = dict(seated, nS=-74, nE=-14)
    keys = [(0, seated), (0.1, press), (0.18, seated), (0.6, dict(seated, head=-2)), (1, seated)]
    remote = f'<rect x="-1.4" y="-1" width="2.8" height="7" rx="1" fill="#2B3138"></rect>'
    viewer, _ = athlete(ctx, s, e, keys, c, 52, 94, 0.5, prop=remote, shorts="#3B4A57", bare=True)
    play = win(ctx, s, e, [(0, "opacity:1;transform:scale(1)"), (0.14, "opacity:1;transform:scale(1.15)"), (0.24, "opacity:0;transform:scale(.4)"), (1, "opacity:0;transform:scale(.4)")],
               box="fill-box", origin="center")
    show = win(ctx, s, e, [(0, "opacity:0"), (0.2, "opacity:0"), (0.3, "opacity:1"), (1, "opacity:1")], timing="linear")
    scenes = ""
    for i, (sky, land) in enumerate((("#9CC8F5", "#7DB46C"), ("#F7C59F", "#C8664A"), ("#2F3A55", "#5B6770"))):
        a, b = 0.28 + i * 0.24, 0.52 + i * 0.24
        fr = [(0, "opacity:0"), (max(a - 0.02, 0.01), "opacity:0"), (a, "opacity:1"), (min(b, 0.99), "opacity:1"), (min(b + 0.02, 1.0), "opacity:0" if i < 2 else "opacity:1")]
        if fr[-1][0] < 1:
            fr.append((1, fr[-1][1]))
        k = win(ctx, s, e, fr, timing="linear")
        moon = f'<circle cx="{150 + i * 12}" cy="46" r="5" fill="{"#FFFFFF" if i == 2 else GOLD}"></circle>'
        scenes += (f'<g class="{k}"><rect x="128" y="36" width="92" height="40" fill="{sky}"></rect>{moon}'
                   f'<path d="M128 76 V64 Q150 54 172 64 Q196 72 220 60 V76 Z" fill="{land}"></path></g>')
    bar = win(ctx, s, e, [(0, "transform:scaleX(0)"), (0.26, "transform:scaleX(0)"), (1, "transform:scaleX(1)")], timing="linear", box="fill-box", origin="left center")
    tv = (f'<rect x="124" y="32" width="100" height="48" rx="3" fill="#2B3138"></rect><rect x="128" y="36" width="92" height="40" fill="#1F2630"></rect>'
          f'<g class="{show}">{scenes}<rect x="132" y="72" width="84" height="1.6" rx=".8" fill="#FFFFFF" style="fill-opacity: .5"></rect>'
          f'<g class="{bar}"><rect x="132" y="72" width="84" height="1.6" rx=".8" fill="{RED}"></rect></g></g>'
          f'<g class="{play}"><circle cx="174" cy="56" r="9" fill="{RED}"></circle><path d="M171 51.5 L178.5 56 L171 60.5 Z" fill="#FFFFFF"></path></g>'
          f'<path d="M166 80 L160 86 M182 80 L188 86" stroke="#46524C" class="w2"></path>'
          f'<rect x="146" y="86" width="56" height="8" rx="2" fill="#9AA6A0"></rect>')
    return floor_line() + tv + sofa_back + viewer + sofa_front


FUN5 = [(act_movies, "Movies"), (act_park, "Amusement park"), (act_concert, "Concert"), (act_bowling, "Bowling"), (act_streaming, "Streaming")]
FUN_BY = {"movies": act_movies, "park": act_park, "concert": act_concert, "bowling": act_bowling, "streaming": act_streaming}


# ---------------------------------------------------------------- Food: home tiffin, a café, a restaurant table (options 3, 4 and 5)
import re as _re  # noqa: E402

import gen_food as GF  # noqa: E402


class Sub:
    """Runs a whole-scene animation inside one act's window [s, e] of the timeline."""

    def __init__(self, ctx, s, e):
        self.ctx, self.s, self.e, self.loop, self.dur, self.uid = ctx, s, e, ctx.loop, ctx.dur, ctx.uid

    def _map(self, frames):
        out = []
        for block in frames.split("}"):
            if "{" not in block:
                continue
            sel, body = block.split("{", 1)
            parts = []
            for part in sel.split(","):
                v = float(part.strip().rstrip("%"))
                if v <= 0:
                    parts += ["0%", f"{self.s:.2f}%"]
                elif v >= 100:
                    parts += [f"{self.e:.2f}%", "100%"]
                else:
                    parts.append(f"{self.s + (self.e - self.s) * v / 100:.2f}%")
            out.append(",".join(parts) + "{" + body + "}")
        return "".join(out)

    def anim(self, frames, dur=None, timing="ease-out", delay=0, count=None, box=None, origin=None, extra=""):
        return self.ctx.anim(self._map(frames), dur, timing, delay, count, box, origin, extra)

    def appear(self, at, dur=None):
        return self.anim(f"0%,{max(at - 4, 0)}%{{opacity:0}}{at}%,100%{{opacity:1}}")

    def spin(self, t=40, turns=3):
        return self.anim(f"0%{{transform:rotate(0)}}{t}%,100%{{transform:rotate({360 * turns}deg)}}", box="fill-box", origin="center")


FOOD_KINDS = {"tiffin": (GF.opt_tiffin, "Home tiffin"), "cafe": (GF.opt_coffee, "Café"), "table": (GF.opt_table, "Restaurant")}


def sc_food(ctx, c, p, label, over, order=None):
    """Each act fills to the share of the budget used; the names say which kind of food spend it is."""
    order = order or [("tiffin", "Home tiffin"), ("cafe", "Café"), ("table", "Restaurant")]
    n = len(order)
    wins = windows(n, 88)
    out, chips = "", ""
    for i, (kind, text) in enumerate(order):
        s, e = wins[i]
        fn = FOOD_KINDS[kind][0]
        out += f'<g class="{act_cls(ctx, i, n, s, e, scale=False)}">{fn(Sub(ctx, s, e), c, p, label, "")}</g>'
        chips += chip(text, act_cls(ctx, i, n, s, e, scale=False))
    if p > 1:
        out += f'<text x="236" y="21" class="lbl-red {ctx.appear(30)}" style="{HALO}">{over}</text>'
    return out + chips, ""


# ---------------------------------------------------------------- the round-4 set
DUR = {"food3": 9.2, "shopcart": 6.5, "groceries": 5, "sports": 17.0, "health": 8.2, "travel": 6.5, "transport": 6.5, "utilities": 6.5, "fun": 10.5}


def build(ctx, kind, cat, spent, budget, opts=None):
    c = CATS[cat][1]
    p = spent / budget if budget else 1
    label = f"Budget {inr(budget)}"
    over = (inr(spent - budget) + " over") if budget and spent > budget else ""
    opts = opts or {}
    if kind == "sports":
        return sc_acts(ctx, c, p, label, over, SPORTS6, neon=SPORTS_NEON)
    if kind == "health":
        return sc_acts(ctx, c, p, label, over, HEALTH4)
    if kind == "travel":
        chips = opts.get("chips", ["Flight", "Train", "Coach"])
        return sc_acts(ctx, c, p, label, over, [(act_flight, chips[0]), (act_train, chips[1]), (act_coach, chips[2])])
    if kind == "groceries":
        return sc_bowls(ctx, c, p, label, over), ""
    if kind == "shopcart":
        return sc_trolley2(ctx, c, p, label, over), ""
    if kind == "food3":
        return sc_food(ctx, c, p, label, over, opts.get("order"))
    if kind == "fun":
        return sc_acts(ctx, c, p, label, over, opts.get("acts") or FUN5)
    return gen3.build(ctx, kind, cat, spent, budget, opts)


ENTRIES = [(cat, title, ("groceries" if kind == "cart" else kind), spent, budget, caption) for cat, title, kind, spent, budget, caption in gen3.ENTRIES]
ENTRIES = [("entertainment", "Entertainment · every outing", "fun", 1900, 2000, "") if row[0] == "entertainment" else row for row in ENTRIES]
ENTRIES = [("food", "Food & Dining", "food3", 9000, 12000, "") if row[0] == "food" else row for row in ENTRIES]
CAPTIONS = {
    "travel": "The plane takes off from the runway, then a train and a coach. The bar shows 70% of the budget.",
    "transport": "One trip that changes from car to scooter, bus and metro, stopping at 81% of the budget.",
    "groceries": "Fruit, vegetables, bread and other things fill four bowls, one item for each twelfth of the budget.",
    "shopcart": "The trolley rolls in; things drop in one at a time, one for each eighth of the budget, and the counter ticks up.",
    "sports": "A cover drive for four, a penalty into the top corner, a jump smash, a pickleball point, a chess capture, a sprint start.",
    "health": "A run, the gym, yoga, then meditation.",
    "fun": "Movies, an amusement park, a concert, bowling, then streaming at home.",
    "food3": "A home tiffin stacks up, a café cup fills, then dishes reach a restaurant table, each to the share of the budget used.",
}


def gallery():
    css, cards = [BASE_CSS, EXTRA_CSS, GHOST_CSS, NEON_CSS], []
    for i, (cat, title, kind, spent, budget, caption) in enumerate(ENTRIES):
        ctx = Ctx(f"r{i}", True, DUR.get(kind, 4))
        svg, overlay = build(ctx, kind, cat, spent, budget)
        css += ctx.css
        c = CATS[cat][1]
        sub, red = sub_line(cat, spent, budget, kind)
        cap = CAPTIONS.get(kind, caption)
        cards.append(f"""<div style="background: #FFFFFF; border: 1px solid {LINE}; border-radius: 24px; padding: 16px; display: flex; flex-direction: column; gap: 10px">
<div style="display: flex; align-items: center; gap: 10px">
<span style="width: 12px; height: 12px; border-radius: 6px; flex: none; background: {c}"></span>
<span style="flex: 1; min-width: 0; font-size: 16px; font-weight: 500">{title}</span>
<span style="font-size: 18px; font-weight: 700">{inr(spent)}</span>
</div>
<span style="font-size: 13px; color: {"#B3261E" if red else MUTED}; margin-top: -6px; padding-left: 22px">{sub}</span>
<div style="border-radius: 16px; overflow: hidden; background: {mix(c, '#FFFFFF', 0.92)}">{scene_box(svg, overlay, 1.4)}</div>
<p style="margin: 0; font-size: 14px; color: {MUTED}; line-height: 1.4">{cap}</p>
</div>""")
    body = f"""<div style="width: 1280px; height: 2440px; box-sizing: border-box; padding: 40px 48px; background: {BG}; color: {INK}; font-family: Roboto, system-ui, sans-serif; display: flex; flex-direction: column; gap: 22px">
<div style="display: flex; flex-direction: column; gap: 6px">
<h1 style="margin: 0; font-size: 30px; font-weight: 700">Round 4 · every category</h1>
<p style="margin: 0; font-size: 16px; color: {MUTED}">New: real athletes for Sports and Health, a runway take-off for Travel, Groceries as a filling cart, the bus fully on screen. In the app each plays once when you open the category, and again when you tap it. They loop here.</p>
</div>
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px">
{"".join(cards)}
</div>
</div>"""
    return page("Round 4 · every category", body, STATIC, (1280, 2440), "\n".join(css))


PHONE = [(cid, cat, ("groceries" if kind == "cart" else kind), spent, budget, merchants, opts) for cid, cat, kind, spent, budget, merchants, opts in gen3.PHONE]
FUN_ROW = ("entertainment", "entertainment", "fun", 3389, 4000, [["Wonderla", 1450], ["PVR INOX", 700], ["Netflix", 649], ["BookMyShow", 590]],
           {"acts": [(act_park, "Amusement park · Wonderla"), (act_movies, "Movies · PVR INOX"), (act_streaming, "Streaming · Netflix"),
                     (act_concert, "Concert · BookMyShow")]})
PHONE = [FUN_ROW if row[0] == "entertainment" else row for row in PHONE]
FOOD_ROW = ("food", "food", "food3", 11240, 12000, [["Swiggy", 4820], ["Zomato", 3560], ["Tiffin service", 1640], ["Café Coffee Day", 1220]],
            {"order": [("table", "Restaurant · Swiggy"), ("tiffin", "Tiffin service"), ("cafe", "Café Coffee Day")]})
PHONE = [FOOD_ROW if row[0] == "food" else row for row in PHONE]


def phone():
    css = [EXTRA_CSS, GHOST_CSS, NEON_CSS]
    scenes = ""
    for i, (cid, cat, kind, spent, budget, _, opts) in enumerate(PHONE):
        ctx = Ctx(f"s{i}", False, DUR.get(kind, 4))
        svg, overlay = build(ctx, kind, cat, spent, budget, opts)
        css += ctx.css
        scenes += f'<sc-if value="{{{{show.{cid}}}}}" hint-placeholder-val="{{{{false}}}}">{scene_box(svg, overlay, 326 / 248)}</sc-if>'
    saved, real = gen2.PHONE, gen2.build_scene
    gen2.PHONE = [(cid, cat, kind, spent, budget, merchants) for cid, cat, kind, spent, budget, merchants, _ in PHONE]
    gen2.build_scene = lambda ctx, kind, cat, spent, budget: ("", "")
    try:
        html = gen2.phone()
    finally:
        gen2.build_scene, gen2.PHONE = real, saved
    start = html.index('<sc-if value="{{showScene}}"')
    inner_start = html.index(">", start) + 1
    inner_end = html.index("</sc-if>\n</button>", inner_start)
    html = html[:inner_start] + "\n" + scenes + "\n" + html[inner_end:]
    html = html.replace("</style>", "\n".join(css) + "\n</style>", 1)
    return html.replace("<title>Option B · working phone</title>", "<title>Round 4 · working phone</title>", 1)


def canvas(base):
    idx = json.loads(base)
    if not any(pg["id"] == "r4" for pg in idx["pages"]):
        idx["pages"].append({"id": "r4", "name": "Round 4"})
    idx["boards"]["R4Phone.dc.html"] = {"x": 0, "y": 0, "w": 390, "h": 844, "title": "Round 4 · working phone (tap a category)",
                                        "is_interactive": True, "radius": 36, "page": "r4"}
    idx["boards"]["R4Gallery.dc.html"] = {"x": 470, "y": 0, "w": 1280, "h": 2440, "title": "Round 4 · every category", "page": "r4"}
    for f in ("R4Phone.dc.html", "R4Gallery.dc.html"):
        if f not in idx["order"]:
            idx["order"].append(f)
    idx["notes"]["round4"] = {"x": 0, "y": -300, "text": "Round 4 · real athletes, take-off, yoga", "kind": "title1", "maxW": 1750, "page": "r4"}
    idx["launch"] = {"view": "canvas", "page": "r4"}
    return json.dumps(idx, indent=1, ensure_ascii=False)


if __name__ == "__main__":
    which = sys.argv[1]
    if which == "canvas":
        out = ("canvas.json", canvas(open(sys.argv[2]).read()))
    elif which == "R4Phone":
        out = ("R4Phone.dc.html", phone())
    else:
        out = ("R4Gallery.dc.html", gallery())
    with open(ROOT + out[0], "w") as f:
        f.write(out[1])
    print("wrote", out[0], len(out[1]))
