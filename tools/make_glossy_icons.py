"""Glossy category icons for the clay look: the pictures' own objects (a basket of vegetables, a cup, an auto...)
as small PNGs, one per category icon, plus three for the figures under the budget (coins, a bag, a piggy bank).

    python3 tools/make_glossy_icons.py            # writes app/src/main/res/drawable-nodpi/glossy_*.webp
    python3 tools/make_glossy_icons.py --preview  # also /tmp/glossy-preview.png
    python3 tools/make_glossy_icons.py --only holiday  # just that one

Needs Playwright's Chromium. Each icon is drawn large, cropped to what was drawn and centred in a square with a
little room, 144 px (48 dp at the densest screens; smaller tiles scale it down), saved as WebP (lossy colour, exact
transparency): about half the size of a PNG, with no visible difference.
"""
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "scenes"))
import art5 as A  # noqa: E402
import art6 as B  # noqa: E402
import bright as BR  # noqa: E402

OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable-nodpi")
SIZE = 144
# --only holiday,travel: just those (the others are left as they are)
ONLY = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None


def g(x, y, s, art, rot=0):
    r = f" rotate({rot})" if rot else ""
    return f'<g transform="translate({x} {y}) scale({s}){r}">{art}</g>'


def bag(p, name):
    c = {"pink": ("#FFB8D6", "#FF5FA2", "#C92E74", 28, 38, "heart"), "purple": ("#C9B5FF", "#8B5CF6", "#5B2FC8", 26, 34, "star")}[name]
    return A.bag(p, *c)


ART = {
    "groceries": lambda p: g(-8, -2, 1.05, A.lettuce(p)) + g(10, 0, 0.95, A.orange(p)) + g(-6, 2, 0.9, A.tomato(p)),
    "food": lambda p: g(-7, 0, 1.0, A.coffee(p)) + g(11, 3, 0.75, A.croissant(p)),
    "coffee": lambda p: A.coffee(p),
    "shopping": lambda p: g(-7, 0, 0.95, bag(p, "pink")) + g(11, 2, 0.8, bag(p, "purple")),
    "transport": lambda p: A.auto_rickshaw(p),
    "fuel": lambda p: B.pump(p),
    "bills": lambda p: g(-9, -18, 1.05, A.utility(p, "bulb")) + g(11, -10, 0.85, A.utility(p, "drop")),
    "recharge": lambda p: B.phone(p) + "".join(g(x, y + 7, 1.0, B.app_tile(p, k)) for (x, y), k in
                                                zip([(-9.5, -74), (9.5, -74), (-9.5, -56), (9.5, -56)], ["play", "music", "chat", "star"])),
    "entertainment": lambda p: A.popcorn_bucket_back(p) + "".join(g(x, y, 1.25, A.kernel(p)) for x, y in BR.KERNELS[:11]) + A.popcorn_bucket(p),
    "health": lambda p: A.heart(p),
    "travel": lambda p: A.suitcase(p) + g(-12, -50, 1.0, A.sticker(p, "plane", "#8FD0FF", "#2F80ED")) + g(10, -26, 1.0, A.sticker(p, "palm", "#8EF0A8", "#1FA65A")),
    "holiday": lambda p: B.beach(p),
    "education": lambda p: "".join(g(BR.BOOK_DX[i], -8.4 * i, 1.0, B.book(p, i, 44 - 2 * i), BR.BOOK_ROT[i]) for i in range(3)) + g(0, -8.4 * 3, 1.0, B.grad_cap(p)),
    "rent": lambda p: B.house(p),
    "emi": lambda p: B.calendar(p),
    "investments": lambda p: (B.pot(p) + '<path d="M0,-26 C3,-40 -3,-50 0,-62" stroke="#2E8A3A" stroke-width="2.8" stroke-linecap="round" fill="none"></path>'
                              + "".join(g(0, y, 1.0, B.leaf(p, side)) for y, side in B.PLANT_LEAVES[:5]) + g(0, -66, 0.9, A.coin(p))),
    "cash": lambda p: B.wallet_back(p) + "".join(g(x, y + 16, 1.0, B.note(p), r) for x, y, r in B.NOTE_FAN[:5]) + B.wallet_front(p),
    "transfer": lambda p: "".join(g(0, -3.4 * i, 1.0, B.coin_flat(p)) for i in range(5)) + g(16, 2, 0.9, A.coin(p)),
    "income": lambda p: B.jar_back(p) + "".join(g(x, y, 1.0, A.coin(p, 7.5)) for x, y in B.JAR_COINS[:6]) + B.jar_front(p, "#F5B800", "plus"),
    "others": lambda p: B.jar_back(p) + "".join(g(x, y, 1.0, A.coin(p, 7.5)) for x, y in B.JAR_COINS[:4]) + B.jar_front(p, "#9A7BE0", "dot"),
    "sports": lambda p: g(-6, 0, 1.05, A.bat(p)) + g(9, 0, 1.0, A.football(p)),
    "gift": lambda p: A.gift(p),
    "wallet": lambda p: B.wallet_back(p) + B.wallet_front(p),
    # the figures under the budget
    "coins": lambda p: g(-6, 0, 1.2, A.coin(p)) + g(8, -4, 1.0, A.coin(p)),
    "piggy": lambda p: A.piggy(p),
}


def svg(key):
    p = A.P("g" + key)
    art = ART[key](p)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-110 -150 220 180" width="880" height="720">{p.defs_svg()}{art}</svg>'


def render():
    from PIL import Image
    from playwright.sync_api import sync_playwright
    out = {}
    with sync_playwright() as pw:
        b = pw.chromium.launch()
        pg = b.new_page(viewport={"width": 880, "height": 720})
        for key in [k for k in ART if not ONLY or k in ONLY]:
            pg.set_content(f'<!doctype html><html><body style="margin:0;background:transparent">{svg(key)}</body></html>')
            im = Image.open(io.BytesIO(pg.screenshot(omit_background=True))).convert("RGBA")
            box = im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
            im = im.crop(box)
            side = round(max(im.size) * 1.12)
            sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
            sq.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
            out[key] = sq.resize((SIZE, SIZE), Image.LANCZOS)
        b.close()
    return out


def main():
    icons = render()
    os.makedirs(OUT, exist_ok=True)
    for key, im in icons.items():
        im.save(os.path.join(OUT, f"glossy_{key}.webp"), "WEBP", quality=90, alpha_quality=100, method=6)
    print("wrote", len(icons), "icons to", os.path.relpath(OUT))
    if "--preview" in sys.argv:
        from PIL import Image
        cols = 8
        rows = (len(icons) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * 170, rows * 170), (255, 241, 224, 255))
        for i, im in enumerate(icons.values()):
            sheet.alpha_composite(im, ((i % cols) * 170 + 13, (i // cols) * 170 + 13))
        sheet.save("/tmp/glossy-preview.png")
        print("preview /tmp/glossy-preview.png")


if __name__ == "__main__":
    main()
