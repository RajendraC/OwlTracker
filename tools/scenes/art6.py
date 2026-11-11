"""Bright, glossy objects for the categories Round 5 didn't draw: fuel, recharge, education, rent, EMI,
investments, cash, transfers, income and any other category. Same conventions as art5: base at y=0, centred."""
from art5 import f1, gloss, shadow, star_path


def pump(p):
    return (shadow(p, 22, op=0.24)
            + f'<rect x="-20" y="-7" width="40" height="7" rx="2.5" fill="{p.rad("#E9EEF4", "#B7C2CF", "#7F8C9C")}"></rect>'
            + f'<rect x="-16" y="-64" width="32" height="58" rx="7" fill="{p.rad("#FF9A8A", "#EF3B3B", "#A81818", 0.3, 0.25)}"></rect>'
            + f'<rect x="-18" y="-70" width="36" height="9" rx="3.5" fill="{p.rad("#FFB8AA", "#D92D2D", "#8E1414")}"></rect>'
            + '<rect x="-11" y="-57" width="22" height="13" rx="3" fill="#1B2430"></rect>'
            + '<rect x="-8" y="-53.5" width="10" height="2.4" rx="1.2" fill="#5CF08A"></rect><rect x="-8" y="-49.5" width="15" height="2.4" rx="1.2" fill="#5CF08A" opacity="0.7"></rect>'
            + '<rect x="-6.5" y="-39" width="13" height="29" rx="4" fill="#FFFFFF" opacity="0.92"></rect>'
            + '<path d="M16,-34 C30,-34 30,-22 34,-16" stroke="#2A2F38" stroke-width="3.2" fill="none" stroke-linecap="round"></path>'
            + '<rect x="30" y="-20" width="9" height="5" rx="1.6" fill="#2A2F38" transform="rotate(32 34 -17)"></rect>'
            + gloss(p, -9, -46, 1.8, 10, rot=4, op=0.5))


def phone(p):
    return (shadow(p, 26, op=0.24)
            + f'<rect x="-25" y="-94" width="50" height="94" rx="11" fill="{p.rad("#5A6270", "#22262E", "#101317", 0.3, 0.2)}"></rect>'
            + f'<rect x="-21" y="-87" width="42" height="80" rx="7" fill="{p.lin("#C9EBFF", "#B7A6FF")}"></rect>'
            + '<rect x="-6" y="-91" width="12" height="2.4" rx="1.2" fill="#3A404C"></rect>'
            + '<rect x="-8" y="-4.2" width="16" height="1.8" rx="0.9" fill="#FFFFFF" opacity="0.6"></rect>'
            + gloss(p, -15, -70, 2, 14, rot=6, op=0.35))


APP_GLYPH = {
    "play": '<path d="M-2.4,-3.4 L3.6,0 L-2.4,3.4 Z" fill="#FFFFFF"></path>',
    "music": '<path d="M1.6,-4 V2 A1.8,1.6 0 1 1 0,0.4 V-4 Z M1.6,-4 L4,-3" stroke="#FFFFFF" stroke-width="1.2" fill="#FFFFFF"></path>',
    "cloud": '<path d="M-3.6,2.4 Q-5.4,2.4 -5.4,0.6 Q-5.4,-1.4 -3.4,-1.4 Q-2.8,-4 0,-4 Q3,-4 3.4,-1.2 Q5.4,-1 5.4,0.8 Q5.4,2.4 3.6,2.4 Z" fill="#FFFFFF"></path>',
    "chat": '<path d="M-4.4,-3.6 H4.4 Q5.2,-3.6 5.2,-2.8 V1.6 Q5.2,2.4 4.4,2.4 H-1 L-3.4,4.4 V2.4 H-4.4 Q-5.2,2.4 -5.2,1.6 V-2.8 Q-5.2,-3.6 -4.4,-3.6 Z" fill="#FFFFFF"></path>',
    "tv": '<rect x="-5" y="-3.6" width="10" height="6.6" rx="1.4" fill="none" stroke="#FFFFFF" stroke-width="1.4"></rect><path d="M-2,4.4 H2" stroke="#FFFFFF" stroke-width="1.4" stroke-linecap="round"></path>',
    "game": '<rect x="-5.4" y="-2.8" width="10.8" height="6" rx="3" fill="#FFFFFF"></rect><path d="M-3,0.2 H-0.6 M-1.8,-1 V1.4" stroke="#7C5CF0" stroke-width="1" stroke-linecap="round"></path><circle cx="2.6" cy="0.2" r="0.9" fill="#7C5CF0"></circle>',
    "star": f'<path d="{star_path(0, 0.3, 4.4, 1.9)}" fill="#FFFFFF"></path>',
    "wifi": '<path d="M-4.6,-0.6 a6.4,6.4 0 0 1 9.2,0 M-2.6,1.4 a3.6,3.6 0 0 1 5.2,0" stroke="#FFFFFF" stroke-width="1.4" stroke-linecap="round" fill="none"></path><circle cx="0" cy="3.4" r="1" fill="#FFFFFF"></circle>',
}
APP_COLORS = {"play": ("#FF9A8A", "#F04438", "#B3221A"), "music": ("#8EF0A8", "#1FA65A", "#127A40"), "cloud": ("#9FD6FF", "#2F80ED", "#1A55B0"),
              "chat": ("#8DF0E4", "#14B8A6", "#0B7A70"), "tv": ("#DCCBFF", "#8B5CF6", "#5B2FC8"), "game": ("#FFD0A0", "#FF8A3D", "#C9561A"),
              "star": ("#FFE88A", "#F5B800", "#C48A0E"), "wifi": ("#B9C4FF", "#4F63E8", "#2C3BA8")}


def app_tile(p, kind):
    c0, c1, c2 = APP_COLORS[kind]
    return (f'<rect x="-7" y="-7" width="14" height="14" rx="4" fill="{p.rad(c0, c1, c2, 0.32, 0.25)}"></rect>' + APP_GLYPH[kind]
            + gloss(p, -3, -4.6, 3, 1.1, rot=0, op=0.5))


BOOK_COLORS = [("#FF9A8A", "#F04438", "#B3221A"), ("#9FD6FF", "#2F80ED", "#1A55B0"), ("#8EF0A8", "#1FA65A", "#127A40"),
               ("#FFE88A", "#F5B800", "#C48A0E"), ("#DCCBFF", "#8B5CF6", "#5B2FC8"), ("#FFC48A", "#FF7A3D", "#C9461A"),
               ("#8DF0E4", "#14B8A6", "#0B7A70"), ("#FFB8D6", "#FF5FA2", "#C92E74")]


def book(p, i, w):
    c0, c1, c2 = BOOK_COLORS[i]
    h = w / 2
    return (f'<rect x="{f1(-h)}" y="-8.4" width="{f1(w)}" height="8.4" rx="2" fill="{p.rad(c0, c1, c2, 0.3, 0.2, 1.1)}"></rect>'
            + f'<rect x="{f1(h - 9)}" y="-7" width="7.6" height="5.6" rx="1" fill="#FFF9EC"></rect>'
            + f'<path d="M{f1(h - 8.4)},-5.2 H{f1(h - 2)} M{f1(h - 8.4)},-3.4 H{f1(h - 2)}" stroke="#E6D9BF" stroke-width="0.6"></path>'
            + f'<path d="M{f1(-h + 5)},-8.4 V0 M{f1(-h + 8)},-8.4 V0" stroke="#FFFFFF" stroke-width="0.8" opacity="0.45"></path>'
            + gloss(p, -h * 0.3, -7.4, w * 0.22, 0.9, rot=0, op=0.45))


def grad_cap(p):
    return (f'<path d="M-7,-6 L-7,0 Q0,3 7,0 L7,-6 Z" fill="{p.rad("#4A5260", "#22262E", "#101317")}"></path>'
            + f'<path d="M0,-14 L18,-7 L0,0 L-18,-7 Z" fill="{p.rad("#5A6270", "#2A2F38", "#14171C", 0.4, 0.3)}"></path>'
            + '<path d="M10,-6 V2" stroke="#F5C43A" stroke-width="1.4"></path><circle cx="10" cy="3" r="1.8" fill="#F5C43A"></circle>')


def pencil(p):
    return (shadow(p, 14)
            + '<g transform="rotate(-8 0 -4)">'
            + f'<rect x="-14" y="-7" width="24" height="6" rx="1" fill="{p.lin("#FFE07A", "#F5B800")}"></rect>'
            + '<rect x="-17" y="-7" width="4" height="6" rx="1" fill="#FF8FB1"></rect>'
            + '<path d="M10,-7 L17,-4 L10,-1 Z" fill="#F2D2A8"></path><path d="M15,-4.9 L17,-4 L15,-3.1 Z" fill="#2A2F38"></path>'
            + "</g>")


def house(p):
    wins = "".join(f'<rect x="{x - 6}" y="{y - 5}" width="12" height="10" rx="2" fill="#3B4A6B"></rect>'
                   f'<path d="M{x},{y - 5} V{y + 5} M{x - 6},{y} H{x + 6}" stroke="#FFFFFF" stroke-width="1" opacity="0.5"></path>'
                   for x, y in ((-18, -32), (18, -32), (-18, -14), (18, -14)))
    return (shadow(p, 36, op=0.24)
            + '<rect x="12" y="-72" width="9" height="18" rx="1.6" fill="#C9461A"></rect>'
            + f'<rect x="-30" y="-46" width="60" height="46" rx="3" fill="{p.rad("#FFFFFF", "#FFF1DC", "#E8CFA8", 0.3, 0.2)}"></rect>'
            + f'<path d="M-37,-43 L0,-76 L37,-43 Q38,-41 36,-41 L-36,-41 Q-38,-41 -37,-43 Z" fill="{p.rad("#FFA88A", "#F0533A", "#B3321A", 0.35, 0.2)}"></path>'
            + '<circle cx="0" cy="-55" r="6" fill="#3B4A6B"></circle>'
            + wins
            + f'<rect x="-6.5" y="-24" width="13" height="24" rx="3" fill="{p.rad("#C08A5A", "#8A5226", "#5A3216")}"></rect>'
            + '<circle cx="3.4" cy="-11" r="1.2" fill="#F5C43A"></circle>'
            + gloss(p, -16, -60, 8, 2, rot=-40, op=0.55))


HOUSE_LIGHTS = [(-18, -32, "win"), (18, -32, "win"), (-18, -14, "win"), (18, -14, "win"), (0, -55, "round"), (0, -27.5, "lamp")]


def light(p, shape):
    glow = p.rad("#FFFBE0", "#FFE27A", "#FFC928", 0.5, 0.5)
    if shape == "round":
        return f'<circle cx="0" cy="0" r="6" fill="{glow}"></circle>'
    if shape == "lamp":
        return f'<circle cx="0" cy="0" r="2.6" fill="{glow}"></circle><circle cx="0" cy="0" r="6" fill="#FFE27A" opacity="0.35" filter="{p.blur(1.6)}"></circle>'
    return (f'<rect x="-6" y="-5" width="12" height="10" rx="2" fill="{glow}"></rect>'
            '<path d="M0,-5 V5 M-6,0 H6" stroke="#E0A100" stroke-width="1" opacity="0.6"></path>'
            f'<rect x="-8" y="-7" width="16" height="14" rx="4" fill="#FFE27A" opacity="0.3" filter="{p.blur(1.6)}"></rect>')


def tree(p):
    return (shadow(p, 12)
            + f'<rect x="-2.4" y="-16" width="4.8" height="16" rx="2" fill="{p.lin("#B07A4A", "#7A4A22", 1, 0)}"></rect>'
            + f'<circle cx="0" cy="-28" r="14" fill="{p.rad("#9BE07A", "#3FA131", "#1E6B1B")}"></circle>'
            + f'<circle cx="-8" cy="-20" r="8" fill="{p.rad("#9BE07A", "#3FA131", "#1E6B1B")}"></circle>'
            + f'<circle cx="8" cy="-20" r="8" fill="{p.rad("#9BE07A", "#3FA131", "#1E6B1B")}"></circle>'
            + gloss(p, -5, -34, 4, 2, op=0.5))


def calendar(p):
    cells = "".join(f'<rect x="{x}" y="{y}" width="8" height="7" rx="1.6" fill="#EEF1F6"></rect>'
                    for y in (-36, -26, -16) for x in (-20, -10, 0, 10))
    return (shadow(p, 26)
            + f'<rect x="-26" y="-56" width="52" height="56" rx="7" fill="{p.rad("#FFFFFF", "#F7F8FB", "#CCD3DE", 0.3, 0.2)}"></rect>'
            + f'<path d="M-26,-49 Q-26,-56 -19,-56 L19,-56 Q26,-56 26,-49 L26,-42 L-26,-42 Z" fill="{p.rad("#FF9A8A", "#F04438", "#B3221A", 0.3, 0.2)}"></path>'
            + '<rect x="-15" y="-61" width="4" height="10" rx="2" fill="#2A2F38"></rect><rect x="11" y="-61" width="4" height="10" rx="2" fill="#2A2F38"></rect>'
            + cells
            + f'<rect x="0" y="-26" width="8" height="7" rx="1.6" fill="{p.rad("#9BE07A", "#2FAE52", "#1E7A3E")}"></rect>'
            + '<path d="M1.8,-22.6 l1.6,1.6 l3,-3" stroke="#FFFFFF" stroke-width="1.2" fill="none" stroke-linecap="round" stroke-linejoin="round"></path>'
            + gloss(p, -16, -52, 6, 1.4, rot=0, op=0.55))


def coin_flat(p):
    return (f'<ellipse cx="0" cy="-2.6" rx="12" ry="4.2" fill="#C48A0E"></ellipse>'
            f'<rect x="-12" y="-5.8" width="24" height="3.2" fill="#D9A21A"></rect>'
            f'<ellipse cx="0" cy="-5.8" rx="12" ry="4.2" fill="{p.rad("#FFF3B0", "#F7C33A", "#C48A0E", 0.4, 0.3)}"></ellipse>'
            '<ellipse cx="0" cy="-5.8" rx="8" ry="2.6" fill="none" stroke="#FFFFFF" stroke-width="0.7" opacity="0.6"></ellipse>')


def pot(p):
    return (shadow(p, 18, op=0.22)
            + f'<path d="M-13,0 L-16,-22 L16,-22 L13,0 Z" fill="{p.rad("#FFB38A", "#E06A3A", "#A8431A", 0.35, 0.3)}"></path>'
            + f'<rect x="-18.5" y="-27" width="37" height="7" rx="2.5" fill="{p.rad("#FFC4A0", "#E87A48", "#B04A1E", 0.4, 0.2)}"></rect>'
            + '<ellipse cx="0" cy="-27" rx="15.5" ry="2.6" fill="#6B3E1E"></ellipse>'
            + gloss(p, -9, -14, 1.6, 5.5, rot=8, op=0.45))


def leaf(p, side):
    s = 1 if side > 0 else -1
    return (f'<path d="M0,0 C{4 * s},-8 {14 * s},-9 {18 * s},-4 C{14 * s},2 {5 * s},3 0,0 Z" fill="{p.rad("#B6F08A", "#3FB04A", "#1E7A2E", 0.4, 0.3)}"></path>'
            + f'<path d="M1,-0.6 Q{9 * s},-4 {16 * s},-4" stroke="#E6FBD0" stroke-width="0.8" fill="none" opacity="0.7"></path>')


PLANT_LEAVES = [(-30, 1), (-38, -1), (-46, 1), (-54, -1), (-62, 1), (-70, -1), (-78, 1), (-86, -1)]


def wallet_back(p):
    return f'<path d="M-30,-40 L30,-40 L30,-34 L-30,-34 Z" fill="#5A2E14"></path>'


def wallet_front(p):
    return (shadow(p, 32, op=0.24)
            + f'<rect x="-32" y="-38" width="64" height="38" rx="8" fill="{p.rad("#C9875A", "#8A4A22", "#5A2E14", 0.3, 0.2)}"></rect>'
            + '<path d="M-28,-34 H28" stroke="#E8B48A" stroke-width="1" stroke-dasharray="2 2" opacity="0.7"></path>'
            + f'<path d="M8,-26 H34 V-10 H8 Q2,-10 2,-18 Q2,-26 8,-26 Z" fill="{p.rad("#B9744A", "#7A3E1C", "#4A2410", 0.3, 0.2)}"></path>'
            + f'<circle cx="12" cy="-18" r="3.4" fill="{p.rad("#FFF3B0", "#F2C33A", "#B08612")}"></circle>'
            + gloss(p, -18, -26, 9, 2.4, rot=-6, op=0.4))


def note(p):
    return (f'<rect x="-15" y="-34" width="30" height="34" rx="2" fill="{p.lin("#C9F2B8", "#6CC24A")}"></rect>'
            '<rect x="-12" y="-31" width="24" height="28" rx="1.4" fill="none" stroke="#FFFFFF" stroke-width="0.8" opacity="0.7"></rect>'
            '<ellipse cx="0" cy="-17" rx="7" ry="9" fill="#FFFFFF" opacity="0.5"></ellipse>'
            '<text x="0" y="-13" text-anchor="middle" font-size="11" font-weight="800" fill="#2E7A1E" font-family="Plus Jakarta Sans, system-ui, sans-serif">₹</text>')


NOTE_FAN = [(-16, -30, -22), (-8, -33, -12), (0, -35, -2), (8, -33, 8), (16, -30, 18), (-12, -31, -16), (4, -34, 4), (12, -31, 14)]


def avatar(p, c0, c1, c2):
    return (shadow(p, 16)
            + f'<circle cx="0" cy="-18" r="18" fill="{p.rad(c0, c1, c2, 0.32, 0.25)}"></circle>'
            + '<circle cx="0" cy="-24" r="6.4" fill="#FFFFFF" opacity="0.95"></circle>'
            + '<path d="M-10.5,-6.4 Q-10,-15.5 0,-15.5 Q10,-15.5 10.5,-6.4 Q5,-2.6 0,-2.6 Q-5,-2.6 -10.5,-6.4 Z" fill="#FFFFFF" opacity="0.95"></path>'
            + gloss(p, -7, -28, 4.4, 2.2, op=0.55))


def jar_back(p):
    return (f'<path d="M-24,-60 L24,-60 L24,-4 Q24,0 20,0 L-20,0 Q-24,0 -24,-4 Z" fill="#FFFFFF" opacity="0.35"></path>'
            '<ellipse cx="0" cy="-60" rx="22" ry="4.4" fill="#C9D6E3" opacity="0.8"></ellipse>')


def jar_front(p, tint="#F5B800", mark="plus"):
    marks = {"plus": '<path d="M0,-30.5 V-21.5 M-4.5,-26 H4.5" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round"></path>',
             "dot": '<circle cx="0" cy="-26" r="3.6" fill="#FFFFFF"></circle>'}[mark]
    return (shadow(p, 28, op=0.22)
            + '<path d="M-24,-60 L24,-60 L24,-4 Q24,0 20,0 L-20,0 Q-24,0 -24,-4 Z" fill="#E9F3FB" opacity="0.32" stroke="#FFFFFF" stroke-width="1.6" stroke-opacity="0.9"></path>'
            + f'<rect x="-26" y="-70" width="52" height="11" rx="4" fill="{p.rad("#FFF3B0", "#F2C33A", "#B08612", 0.4, 0.2)}"></rect>'
            + f'<rect x="-14" y="-35" width="28" height="18" rx="6" fill="{tint}" opacity="0.92"></rect>' + marks
            + '<rect x="-19" y="-54" width="4" height="40" rx="2" fill="#FFFFFF" opacity="0.6"></rect>'
            + '<rect x="15" y="-50" width="2.4" height="30" rx="1.2" fill="#FFFFFF" opacity="0.35"></rect>')


JAR_COINS = [(-10, -3), (10, -3), (0, -12), (-10, -21), (10, -22), (0, -31), (-9, -40), (9, -41)]


def drop(p, c0="#FFE08A", c1="#FFB21A", c2="#D98A0A"):
    return (f'<path d="M0,-9 C2.6,-5 4.4,-3 4.4,-0.6 A4.4,4.4 0 0 1 -4.4,-0.6 C-4.4,-3 -2.6,-5 0,-9 Z" fill="{p.rad(c0, c1, c2, 0.35, 0.4)}"></path>'
            + gloss(p, -1.4, -3, 0.9, 1.4, rot=0, op=0.7))
