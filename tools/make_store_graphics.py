"""Draws the Google Play store icon (512x512) and feature graphic (1024x500) from the app's
launcher glyph (a lens over three rising bars, on the brand green). Run: python3 tools/make_store_graphics.py"""
from PIL import Image, ImageDraw, ImageFont
import os

BRAND = (15, 107, 79)
BRAND_DARK = (9, 74, 54)
WHITE = (255, 255, 255)
OUT = os.path.join(os.path.dirname(__file__), "..", "store")
FONT_DIR = "/usr/share/fonts/truetype/google-fonts"


def glyph(draw, cx, cy, size, color=WHITE):
    """The launcher glyph from ic_launcher_foreground.xml, drawn in a 24-unit box centred on (cx, cy)."""
    u = size / 24.0
    ox, oy = cx - 12 * u, cy - 12 * u
    w = max(2, round(2 * u))
    def p(x, y): return (ox + x * u, oy + y * u)
    def line(x1, y1, x2, y2):
        draw.line([p(x1, y1), p(x2, y2)], fill=color, width=w)
        r = w / 2
        for (x, y) in ((x1, y1), (x2, y2)):
            X, Y = p(x, y); draw.ellipse([X - r, Y - r, X + r, Y + r], fill=color)
    c = p(10.5, 10.5); r = 6.5 * u
    draw.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], outline=color, width=w)
    line(15.5, 15.5, 20, 20)
    line(8, 12.5, 8, 11)
    line(10.5, 12.5, 10.5, 8.5)
    line(13, 12.5, 13, 10)


def icon():
    S = 2048  # draw large, then downsample for smooth edges
    im = Image.new("RGB", (S, S), BRAND)
    glyph(ImageDraw.Draw(im), S / 2 + S * 0.02, S / 2 + S * 0.02, S * 0.72)
    im = im.resize((512, 512), Image.LANCZOS).convert("RGBA")  # Play asks for a 32-bit PNG
    im.save(os.path.join(OUT, "play-icon-512.png"), optimize=True)


def feature():
    W, H, k = 1024, 500, 2
    im = Image.new("RGB", (W * k, H * k), BRAND)
    d = ImageDraw.Draw(im)
    # soft diagonal band for depth
    d.polygon([(W * k * 0.58, 0), (W * k, 0), (W * k, H * k), (W * k * 0.42, H * k)], fill=BRAND_DARK)
    # icon tile
    tile = 220 * k; tx, ty = 90 * k, (H * k - tile) // 2
    d.rounded_rectangle([tx, ty, tx + tile, ty + tile], radius=56 * k, fill=WHITE)
    glyph(d, tx + tile / 2 + 4 * k, ty + tile / 2 + 4 * k, tile * 0.78, color=BRAND)
    title = ImageFont.truetype(f"{FONT_DIR}/Poppins-Bold.ttf", 74 * k)
    sub = ImageFont.truetype(f"{FONT_DIR}/Poppins-Medium.ttf", 30 * k)
    x = tx + tile + 56 * k
    d.text((x, 150 * k), "Owl Tracker", font=title, fill=WHITE)
    d.text((x, 262 * k), "Spending, sorted from your bank SMS", font=sub, fill=WHITE)
    d.text((x, 306 * k), "On your phone only. No internet.", font=sub, fill=(200, 236, 220))
    im = im.resize((W, H), Image.LANCZOS)
    im.save(os.path.join(OUT, "play-feature-graphic-1024x500.png"), optimize=True)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    icon(); feature()
    print("written to", os.path.abspath(OUT))
