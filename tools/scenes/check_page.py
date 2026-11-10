"""Renders the app's own scene page (app/src/main/assets/scenes) in Chromium, the engine behind Android's WebView,
for every kind of category, frozen at a few moments, into one contact sheet.

    python3 tools/scenes/check_page.py [out.png] [chromium path]
"""
import json
import os
import sys

from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gen import CATS  # noqa: E402
from gen2 import inr  # noqa: E402

PAGE = "file://" + os.path.abspath(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "scenes", "scene.html"))

SCENES = [
    ("transport", "transport", 6480, 8000, [["bus", "Bus · BMTC"], ["metro", "Metro"], ["scooter", "Scooter · Rapido"], ["car", "Car · Uber"]]),
    ("travel", "travel", 4530, 6000, [["train", "Train · IRCTC"], ["flight", "Flight · IndiGo"], ["coach", "Coach · redBus"]]),
    ("fuel", "fuel", 4350, 5000, []),
    ("food", "food", 11240, 12000, [["table", "Restaurant · Swiggy"], ["tiffin", "Tiffin service"], ["cafe", "Café Coffee Day"]]),
    ("groceries", "groceries", 8460, 10000, []),
    ("shopping", "shopping", 6120, 5000, []),
    ("utilities", "bills", 5300, 6000, [["power", "Electricity · BESCOM"], ["broadband", "Broadband · Airtel"]]),
    ("recharge", "recharge", 600, 1000, []),
    ("fun", "entertainment", 3389, 4000, [["park", "Amusement park · Wonderla"], ["movies", "Movies · PVR INOX"]]),
    ("sports", "sports", 1600, 2500, [["badminton", "Badminton · Playo"], ["cricket", "Cricket"]]),
    ("health", "health", 2140, 3000, [["yoga", "Yoga"], ["gym", "Gym · cult.fit"]]),
    ("education", "education", 2000, 4000, []),
    ("rent", "rent", 22000, 22000, []),
    ("emi", "emi", 14700, 14700, []),
    ("cash", "cash", 2000, 5000, []),
    ("others", "others", 900, 3000, []),
]
TIMES = (0.3, 0.6, 0.97)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/scenes_page.png"
    chrome = sys.argv[2] if len(sys.argv) > 2 else "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
    shots = []
    with sync_playwright() as pw:
        b = pw.chromium.launch(executable_path=chrome)
        pg = b.new_page(viewport={"width": 360, "height": 174})
        errors = []
        pg.on("pageerror", lambda e: errors.append(str(e)))
        pg.goto(PAGE)
        for kind, cat, spent, budget, order in SCENES:
            spec = {"kind": kind, "color": CATS[cat][1], "p": spent / budget, "spent": spent, "budget": budget,
                    "label": f"Budget {inr(budget)}", "over": (inr(spent - budget) + " over") if spent > budget else "", "order": order}
            dur = pg.evaluate(f"renderScene({json.dumps(spec)})")
            for t in TIMES:
                pg.evaluate(f"document.getAnimations().forEach(a => {{ a.pause(); a.currentTime = {t * dur * 1000}; }})")
                path = f"/tmp/scene_{kind}_{t}.png"
                pg.screenshot(path=path)
                shots.append((kind, t, path))
        b.close()
    if errors:
        print("page errors:", errors)
    from PIL import Image, ImageDraw
    cols = len(TIMES)
    w, h = 360, 174
    sheet = Image.new("RGB", (cols * (w + 8), len(SCENES) * (h + 20)), "white")
    d = ImageDraw.Draw(sheet)
    for i, (kind, t, path) in enumerate(shots):
        r, col = divmod(i, cols)
        x, y = col * (w + 8), r * (h + 20)
        d.text((x + 2, y + 2), f"{kind} {t}", fill="black")
        tile = Image.open(path).convert("RGB")
        bg = Image.new("RGB", tile.size, "#F2F5F3")
        sheet.paste(tile, (x, y + 16))
    sheet.save(out)
    print("saved", out, len(shots), "frames", "errors:", len(errors))


if __name__ == "__main__":
    main()
