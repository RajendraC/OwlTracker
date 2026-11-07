"""Builds the Google Play listing images from raw emulator captures.

    python3 tools/make_store_assets.py CAPTURES_DIR

CAPTURES_DIR holds the PNGs saved by StoreScreenshots#capture (download the "store-screens"
artifact or pre-release of the "Store screenshots" workflow). Writes into store/:
  play-icon-512.png                 512x512, 32-bit PNG (Play adds the rounded corners)
  play-feature-graphic-1024x500.png 1024x500, 24-bit PNG, centre kept clear for the video play button
  phone/NN-name.png                 1080x1920 captioned phone screenshots, 24-bit PNG, no alpha
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

BRAND = (15, 107, 79)
BRAND_DARK = (9, 74, 54)
BRAND_DEEP = (7, 56, 41)
MINT = (205, 235, 221)
WHITE = (255, 255, 255)
BEZEL = (16, 21, 18)
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "store")
FONTS = "/usr/share/fonts/truetype/google-fonts"


def font(weight, size):
    return ImageFont.truetype(f"{FONTS}/Poppins-{weight}.ttf", size)


# Phone screenshots, in listing order: capture file, headline, line under it.
SCREENS = [
    ("10_home", "See where your money goes", "Every bank, card and UPI payment, sorted from your SMS"),
    ("01_welcome", "Private by design", "No internet permission. Nothing ever leaves your phone."),
    ("50_reports_top", "Reports you can tap into", "Trends, categories and merchants across 1 to 12 months"),
    ("20_budgets", "Warned before you overspend", "Monthly budgets for everything, or one category at a time"),
    ("30_activity", "Every payment in one place", "Search, change categories or add cash spends"),
    ("53_reports_filtered_top", "Drill into any category", "Tap a slice, a merchant or a weekday to filter"),
    ("03_import_filters", "You choose what's imported", "Pick the banks and how far back. Offers and OTPs are skipped."),
    ("70_backup", "Your data, your backups", "Save a file, add a password, restore it on a new phone"),
]


NAMES = ["home", "privacy", "reports", "budgets", "activity", "drill-down", "import", "backup"]


# ---------- drawing helpers ----------

def glyph(draw, cx, cy, size, color=WHITE):
    """The launcher glyph (a lens over three rising bars), drawn in a 24-unit box centred on (cx, cy)."""
    u = size / 24.0
    ox, oy = cx - 12 * u, cy - 12 * u
    w = max(2, round(2 * u))

    def p(x, y):
        return (ox + x * u, oy + y * u)

    def line(x1, y1, x2, y2):
        draw.line([p(x1, y1), p(x2, y2)], fill=color, width=w)
        r = w / 2
        for (x, y) in ((x1, y1), (x2, y2)):
            X, Y = p(x, y)
            draw.ellipse([X - r, Y - r, X + r, Y + r], fill=color)

    c = p(10.5, 10.5)
    r = 6.5 * u
    draw.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], outline=color, width=w)
    line(15.5, 15.5, 20, 20)
    line(8, 12.5, 8, 11)
    line(10.5, 12.5, 10.5, 8.5)
    line(13, 12.5, 13, 10)


def vertical_gradient(w, h, top, bottom):
    g = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        g.putpixel((0, y), tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return g.resize((w, h))


def glow(size, center, radius, color, alpha):
    """A soft round light, for depth behind the phone."""
    layer = Image.new("RGBA", size, color + (0,))
    d = ImageDraw.Draw(layer)
    x, y = center
    d.ellipse([x - radius, y - radius, x + radius, y + radius], fill=color + (alpha,))
    return layer.filter(ImageFilter.GaussianBlur(radius * 0.45))


def rounded_mask(size, radius, scale=4):
    w, h = size
    m = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * scale - 1, h * scale - 1], radius=radius * scale, fill=255)
    return m.resize(size, Image.LANCZOS)


STATUS_BAR = 63  # px on the 1080x2400 pixel_6 emulator (24 dp at 420 dpi)


def tidy_status_bar(screen, inset=40):
    """The emulator draws the clock and battery at the very edges of a square screen. Real phones
    keep them clear of the rounded corners, so move them inwards by [inset] px."""
    im = screen.convert("RGB").copy()
    if im.width != 1080:
        return im
    strip = im.crop((0, 0, im.width, STATUS_BAR))
    clean = Image.new("RGB", strip.size)
    for y in range(STATUS_BAR):  # each row's own background, taken from the empty middle
        clean.paste(strip.getpixel((im.width // 2, y)), (0, y, im.width, y + 1))
    half = im.width // 2 - 60
    clean.paste(strip.crop((0, 0, half, STATUS_BAR)), (inset, 0))
    clean.paste(strip.crop((im.width - half, 0, im.width, STATUS_BAR)), (im.width - half - inset, 0))
    im.paste(clean, (0, 0))
    return im


def phone(screen, width):
    """The screenshot in a plain modern phone body. Returns RGBA, with room around it for the shadow."""
    screen = tidy_status_bar(screen)
    sw = width
    sh = round(screen.height * sw / screen.width)
    scr = screen.convert("RGB").resize((sw, sh), Image.LANCZOS)
    bez = max(10, round(width * 0.028))
    pw, ph = sw + 2 * bez, sh + 2 * bez
    r_out = round(pw * 0.105)
    r_in = r_out - bez
    pad = round(width * 0.09)
    im = Image.new("RGBA", (pw + 2 * pad, ph + 2 * pad), (0, 0, 0, 0))

    shadow = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([pad, pad + round(pad * 0.35), pad + pw, pad + ph + round(pad * 0.35)], radius=r_out, fill=(0, 20, 12, 120))
    im = Image.alpha_composite(im, shadow.filter(ImageFilter.GaussianBlur(pad * 0.45)))

    body = Image.new("RGBA", (pw, ph), BEZEL + (255,))
    body.putalpha(rounded_mask((pw, ph), r_out))
    im.alpha_composite(body, (pad, pad))
    # thin lighter rim
    rim = Image.new("RGBA", (pw * 4, ph * 4), (0, 0, 0, 0))
    ImageDraw.Draw(rim).rounded_rectangle([2, 2, pw * 4 - 3, ph * 4 - 3], radius=r_out * 4, outline=(70, 82, 76, 255), width=6)
    im.alpha_composite(rim.resize((pw, ph), Image.LANCZOS), (pad, pad))

    s = scr.convert("RGBA")
    s.putalpha(rounded_mask((sw, sh), r_in))
    im.alpha_composite(s, (pad + bez, pad + bez))
    # front camera, centred in the status bar like a Pixel
    cam = max(6, round(sw * 0.026))
    cx, cy = pad + bez + sw // 2, pad + bez + round(sh * 0.0155)
    hole = Image.new("RGBA", (cam * 8, cam * 8), (0, 0, 0, 0))
    ImageDraw.Draw(hole).ellipse([0, 0, cam * 8 - 1, cam * 8 - 1], fill=(8, 10, 9, 255))
    im.alpha_composite(hole.resize((cam * 2, cam * 2), Image.LANCZOS), (cx - cam, cy - cam))
    return im, pad


def wrap(draw, text, fnt, max_w):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=fnt) <= max_w or not cur:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def balanced(draw, text, fnt, max_w):
    """Like wrap, but two-line text is split near the middle so no word is left on its own."""
    lines = wrap(draw, text, fnt, max_w)
    if len(lines) != 2:
        return lines
    words = text.split()
    best = None
    for i in range(1, len(words)):
        a, b = " ".join(words[:i]), " ".join(words[i:])
        wa, wb = draw.textlength(a, font=fnt), draw.textlength(b, font=fnt)
        if max(wa, wb) <= max_w and (best is None or max(wa, wb) < best[0]):
            best = (max(wa, wb), [a, b])
    return best[1] if best else lines


def fitted(draw, text, weight, size, min_size, max_w):
    """The largest font size, down to [min_size], at which [text] fits on one line."""
    while size > min_size and draw.textlength(text, font=font(weight, size)) > max_w:
        size -= 2
    return font(weight, size)


def centered(draw, y, lines, fnt, fill, W, gap):
    for ln in lines:
        w = draw.textlength(ln, font=fnt)
        draw.text(((W - w) / 2, y), ln, font=fnt, fill=fill)
        y += fnt.size + gap
    return y


# ---------- assets ----------

def store_screenshot(raw, headline, sub, dest, h_size):
    """One 1080x1920 listing image. Every image uses the same headline size and phone position."""
    W, H = 1080, 1920
    canvas = vertical_gradient(W, H, BRAND, BRAND_DEEP).convert("RGBA")
    canvas = Image.alpha_composite(canvas, glow((W, H), (W // 2, 1180), 520, (60, 170, 125), 90))
    d = ImageDraw.Draw(canvas)
    h_font, s_font = font("Bold", h_size), font("Medium", 35)
    y = centered(d, 100, [headline], h_font, WHITE, W, 6)
    lines = balanced(d, sub, s_font, 940)
    slot = 2 * (s_font.size + 10)               # room for two lines; one line sits in the middle
    centered(d, y + 22 + (slot - len(lines) * (s_font.size + 10)) // 2, lines, s_font, MINT, W, 10)
    top = y + 22 + slot + 34
    avail = H - 40 - top                        # whole phone visible, 40 px above the bottom edge
    width = int(avail / (2400 / 1080 + 2 * 0.028))
    ph, pad = phone(raw, width)
    canvas.alpha_composite(ph, ((W - ph.width) // 2, top - pad))
    canvas.convert("RGB").save(dest, optimize=True)  # 24-bit, no alpha channel


def headline_size():
    """The largest size at which every headline fits on one line."""
    d = ImageDraw.Draw(Image.new("RGB", (10, 10)))
    return min(fitted(d, head, "Bold", 70, 54, 980).size for _, head, _ in SCREENS)


def feature_graphic(home_raw, dest):
    W, H, k = 1024, 500, 2
    im = vertical_gradient(W * k, H * k, BRAND, BRAND_DARK).convert("RGBA")
    im = Image.alpha_composite(im, glow((W * k, H * k), (780 * k, 250 * k), 260 * k, (70, 180, 135), 110))
    d = ImageDraw.Draw(im)
    # icon tile and name, left
    tile = 92 * k
    tx, ty = 64 * k, 118 * k
    d.rounded_rectangle([tx, ty, tx + tile, ty + tile], radius=24 * k, fill=WHITE)
    glyph(d, tx + tile / 2 + 2 * k, ty + tile / 2 + 2 * k, tile * 0.78, color=BRAND)
    d.text((tx + tile + 22 * k, ty + 10 * k), "Owl Tracker", font=font("Bold", 46 * k), fill=WHITE)
    lines = ["Spending sorted from", "your bank & UPI SMS"]
    y = ty + tile + 34 * k
    for ln in lines:
        d.text((tx, y), ln, font=font("SemiBold", 31 * k) if os.path.exists(f"{FONTS}/Poppins-SemiBold.ttf") else font("Medium", 31 * k), fill=WHITE)
        y += 42 * k
    d.text((tx, y + 14 * k), "On your phone only. No internet.", font=font("Medium", 20 * k), fill=MINT)
    # phone, right, rising from the bottom edge
    ph, pad = phone(home_raw, 250 * k)
    px = 690 * k - pad
    py = 56 * k - pad
    im.alpha_composite(ph, (px, py))
    im = im.resize((W, H), Image.LANCZOS).convert("RGB")
    im.save(dest, optimize=True)


def icon(dest):
    S = 2048  # draw large, then downsample for smooth edges
    im = Image.new("RGB", (S, S), BRAND)
    glyph(ImageDraw.Draw(im), S / 2 + S * 0.02, S / 2 + S * 0.02, S * 0.72)
    im = im.resize((512, 512), Image.LANCZOS).convert("RGBA")  # Play asks for a 32-bit PNG
    im.save(dest, optimize=True)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    src = sys.argv[1]
    os.makedirs(os.path.join(OUT, "phone"), exist_ok=True)
    icon(os.path.join(OUT, "play-icon-512.png"))
    size = headline_size()
    for i, (name, head, sub) in enumerate(SCREENS, 1):
        raw = Image.open(os.path.join(src, name + ".png"))
        dest = os.path.join(OUT, "phone", f"{i:02d}-{NAMES[i - 1]}.png")
        store_screenshot(raw, head, sub, dest, size)
        print("wrote", os.path.relpath(dest, ROOT))
    feature_graphic(Image.open(os.path.join(src, "10_home.png")), os.path.join(OUT, "play-feature-graphic-1024x500.png"))
    print("wrote store/play-feature-graphic-1024x500.png and store/play-icon-512.png")


if __name__ == "__main__":
    main()
