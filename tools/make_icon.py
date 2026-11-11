"""Owl Tracker's launcher and Play icons, in the Mango & Cocoa look: the app's glossy owl on a ripe-mango ground.

    python3 tools/make_icon.py            # writes the app's icon layers and store/play-icon-512.png
    python3 tools/make_icon.py --preview  # also writes /tmp/owl-icons/preview.png (all variants, several masks)

Needs Playwright's Chromium (pip install playwright; playwright install chromium) to draw the glossy art.
Writes:
  app/src/main/res/mipmap-*/ic_launcher_fg.png   adaptive icon foreground (108 dp, the owl in the 66 dp safe zone)
  app/src/main/res/drawable/ic_launcher_bg.xml    adaptive icon background (a vector: mango gradient and sun glow)
  app/src/main/res/drawable/ic_launcher_mono.xml  themed-icon layer (Android 13+): the owl as one shape
  store/play-icon-512.png                         512 x 512, 32-bit PNG, full bleed (Play rounds the corners)
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
sys.path.insert(0, os.path.join(HERE, "scenes"))
import art5 as A  # noqa: E402

RES = os.path.join(ROOT, "app", "src", "main", "res")
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Mango: light at the top left, deep at the bottom right (as Home's header)
BG = ("#FFF1B3", "#FFC85C", "#FF9A3D")


def owl(p, scheme="cocoa"):
    """The app's owl, round and glossy. "cocoa" is the Mango & Cocoa one (warm brown, cream belly, mango beak
    and feet); "green" is the earlier one."""
    if scheme == "green":
        return A.owl(p)
    f1 = A.f1
    return (A.shadow(p, 20, op=0.22)
            + f'<ellipse cx="0" cy="-24" rx="20" ry="23" fill="{p.rad("#D9A06A", "#9A5528", "#5E2F12", 0.35, 0.25)}"></ellipse>'
            + '<path d="M-17,-40 L-12,-52 L-6,-44 Z M17,-40 L12,-52 L6,-44 Z" fill="#7A3B18"></path>'
            + f'<ellipse cx="0" cy="-16" rx="12" ry="12" fill="{p.rad("#FFF8EC", "#FFE7C2", "#F4C98E")}"></ellipse>'
            + '<path d="M-6,-19 q2,2 4,0 M2,-19 q2,2 4,0 M-4,-13 q2,2 4,0 M0,-13 q2,2 4,0" stroke="#E2B27A" stroke-width="1.1" fill="none" stroke-linecap="round"></path>'
            + "".join(f'<circle cx="{x}" cy="-33" r="8" fill="#FFFFFF"></circle><circle cx="{x}" cy="-33" r="8" fill="none" stroke="#FFC85C" stroke-width="1.4"></circle>'
                      f'<circle cx="{x + 1}" cy="-32.5" r="4" fill="#2A1708"></circle><circle cx="{x + 2.4}" cy="-34" r="1.3" fill="#FFFFFF"></circle>' for x in (-8, 8))
            + '<path d="M-2.8,-26 L0,-21.2 L2.8,-26 Z" fill="#FF9A1F"></path>'
            + '<path d="M-6,-1 l-2,-3 M-3,-1 l0,-3 M6,-1 l2,-3 M3,-1 l0,-3" stroke="#FF9A1F" stroke-width="1.8" stroke-linecap="round"></path>'
            + A.gloss(p, -10, -40, 5, 2.4, rot=-30, op=0.5))


def background_svg(p, variant):
    if variant.startswith("cocoa"):
        return (f'<rect width="108" height="108" fill="{p.lin("#6B3418", "#2A1708", 1, 1)}"></rect>'
                f'<circle cx="54" cy="50" r="30" fill="{p.rad("#FFE59A", "#FFC24D", "#F29A1F", 0.4, 0.35)}"></circle>')
    return (f'<rect width="108" height="108" fill="{p.lin(BG[0], BG[1], 1, 1, BG[2])}"></rect>'
            f'<circle cx="30" cy="22" r="46" fill="#FFFFFF" opacity="0.55" filter="{p.blur(10)}"></circle>')


SCALE = 1.0  # the owl is 40 x 52 units (y from -52 to 0), drawn about its feet: well inside the 66 dp circle


def foreground_svg(p, variant):
    scheme = "green" if variant.endswith("green") else "cocoa"
    o = f'<g transform="translate(54 {54 + 26 * SCALE:.1f}) scale({SCALE})">{owl(p, scheme)}</g>'
    if variant.startswith("coin"):
        coin = f'<g transform="translate(69 80) scale(0.9)">{A.coin(p, 8)}</g>'
        return f'<g transform="translate(-5 0)">{o}</g>' + coin
    return o


def svg(variant, layers, size, view="0 0 108 108"):
    p = A.P("i")
    body = ""
    if "bg" in layers:
        body += background_svg(p, variant)
    if "fg" in layers:
        body += foreground_svg(p, variant)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}" width="{size}" height="{size}">'
            f'{p.defs_svg()}{body}</svg>')


def render(jobs):
    """jobs: [(svg_text, size, path)] drawn with a transparent background."""
    from playwright.sync_api import sync_playwright
    with sync_playwright() as pw:
        b = pw.chromium.launch()
        pg = b.new_page()
        for text, size, path in jobs:
            pg.set_viewport_size({"width": size, "height": size})
            pg.set_content(f'<!doctype html><html><body style="margin:0;background:transparent">{text}</body></html>')
            pg.wait_for_timeout(50)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            pg.screenshot(path=path, omit_background=True, clip={"x": 0, "y": 0, "width": size, "height": size})
        b.close()


BG_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Made by tools/make_icon.py: the launcher icon's ground, ripe mango from the top left to the bottom right,
     with a soft sun in the corner (as Home's header). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0" android:startY="0"
                android:endX="108" android:endY="108"
                android:startColor="#FFFFF1B3"
                android:centerColor="#FFFFC85C"
                android:endColor="#FFFF9A3D" />
        </aapt:attr>
    </path>
    <path android:pathData="M30,22m-58,0a58,58 0,1 1,116 0a58,58 0,1 1,-116 0">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="30" android:centerY="22"
                android:gradientRadius="58"
                android:startColor="#B3FFFFFF"
                android:endColor="#00FFFFFF" />
        </aapt:attr>
    </path>
</vector>
"""

# The owl as one shape for themed icons: body, ears and feet, with the eyes cut out, and the coin beside it.
# Same place and size as the foreground of the "coin" icon.
MONO_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Made by tools/make_icon.py: the owl and its coin as plain shapes, for Android 13+ themed icons. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:translateX="49" android:translateY="80">
        <path
            android:fillColor="#FFFFFFFF"
            android:fillType="evenOdd"
            android:pathData="M0,-47a20,23 0,1 0,0.01 0z M-8,-41a8,8 0,1 0,0.01 0z M8,-41a8,8 0,1 0,0.01 0z" />
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M-17,-40L-12,-52L-6,-44z M17,-40L12,-52L6,-44z M-7,-36.5a4,4 0,1 0,0.01 0z M9,-36.5a4,4 0,1 0,0.01 0z" />
        <path
            android:fillColor="#00000000"
            android:strokeColor="#FFFFFFFF"
            android:strokeWidth="1.8"
            android:strokeLineCap="round"
            android:pathData="M-6,-1l-2,-3M-3,-1l0,-3M6,-1l2,-3M3,-1l0,-3" />
    </group>
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="M69,65.6a7.2,7.2 0,1 0,0.01 0z M69,68a4.8,4.8 0,1 1,-0.01 0z" />
</vector>
"""

VARIANT = "coin"  # the brown owl with a rupee coin, on mango (chosen 11 Nov 2026)


def write_app(variant=VARIANT):
    jobs = []
    for d, k in DENSITIES.items():
        size = round(108 * k)
        jobs.append((svg(variant, ("fg",), size), size, os.path.join(RES, f"mipmap-{d}", "ic_launcher_fg.png")))
    # Play shows the whole square (and rounds its corners): the middle 80 dp, a little roomier than a launcher
    jobs.append((svg(variant, ("bg", "fg"), 512, view="14 14 80 80"), 512, os.path.join(ROOT, "store", "play-icon-512.png")))
    # Home's header owl, on its own (192 px, shown at 48 dp)
    p = A.P("m")
    art = owl(p)
    mascot = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-30 -54 60 60" width="192" height="192">'
              f'{p.defs_svg()}<g transform="scale(0.95)">{art}</g></svg>')
    jobs.append((mascot, 192, os.path.join(RES, "drawable-nodpi", "owl_mascot.png")))
    render(jobs)
    with open(os.path.join(RES, "drawable", "ic_launcher_bg.xml"), "w") as f:
        f.write(BG_XML)
    with open(os.path.join(RES, "drawable", "ic_launcher_mono.xml"), "w") as f:
        f.write(MONO_XML)
    # Play wants an opaque 32-bit PNG
    from PIL import Image
    im = Image.open(os.path.join(ROOT, "store", "play-icon-512.png")).convert("RGBA")
    flat = Image.new("RGBA", im.size, BG[1])
    flat.alpha_composite(im)
    flat.save(os.path.join(ROOT, "store", "play-icon-512.png"), optimize=True)
    print("wrote the launcher layers in res/ and store/play-icon-512.png")


def preview():
    """Every variant under a circle, squircle and rounded-square mask, and in a monochrome themed version."""
    from PIL import Image, ImageDraw
    out_dir = "/tmp/owl-icons"
    variants = ["mango", "coin", "cocoa", "mango-green"]
    jobs = [(svg(v, ("bg", "fg"), 432), 432, f"{out_dir}/{v}.png") for v in variants]
    render(jobs)
    S, pad = 220, 30
    masks = []
    for kind in ("circle", "squircle", "rounded"):
        m = Image.new("L", (S * 4, S * 4), 0)
        dr = ImageDraw.Draw(m)
        if kind == "circle":
            dr.ellipse([0, 0, S * 4 - 1, S * 4 - 1], fill=255)
        elif kind == "squircle":
            dr.rounded_rectangle([0, 0, S * 4 - 1, S * 4 - 1], radius=int(S * 4 * 0.36), fill=255)
        else:
            dr.rounded_rectangle([0, 0, S * 4 - 1, S * 4 - 1], radius=int(S * 4 * 0.18), fill=255)
        masks.append(m.resize((S, S), Image.LANCZOS))
    W = pad + 4 * (S + pad)
    H = pad + len(variants) * (S + pad)
    sheet = Image.new("RGB", (W, H), "#3B3B44")
    for r, v in enumerate(variants):
        art = Image.open(f"{out_dir}/{v}.png").convert("RGBA")
        # The launcher shows the middle 72 of 108 dp
        crop = art.crop((72, 72, 360, 360)).resize((S, S), Image.LANCZOS)
        for c, m in enumerate(masks):
            tile = Image.new("RGBA", (S, S), (0, 0, 0, 0))
            tile.paste(crop, (0, 0), m)
            sheet.paste(tile, (pad + c * (S + pad), pad + r * (S + pad)), tile)
        full = art.resize((S, S), Image.LANCZOS)
        sheet.paste(full, (pad + 3 * (S + pad), pad + r * (S + pad)), full)
    sheet.save(f"{out_dir}/preview.png")
    print("wrote", f"{out_dir}/preview.png")


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview()
    else:
        write_app()
