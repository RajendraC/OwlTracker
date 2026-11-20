"""Owl Tracker's launcher and Play icons with the Round 31 owl (the flat owl from the user's samples, in the app's
browns), standing on the mango ground beside a rupee coin. Replaces tools/make_icon.py's owl (chosen 20 Nov 2026).

    python3 tools/make_icon_r31.py

Writes the same files as make_icon.py: res/mipmap-*/ic_launcher_fg.png, res/drawable/ic_launcher_bg.xml (unchanged
mango), res/drawable/ic_launcher_mono.xml (the owl as one shape), res/drawable-nodpi/owl_mascot.png (Home's header
owl) and store/play-icon-512.png.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "scenes"))
import make_icon as M  # noqa: E402  (render(), BG_XML, BG)
import owl31  # noqa: E402

owl31.NAVY, owl31.MID, owl31.LIGHT, owl31.PALE, owl31.BEAK, owl31.BELLY = "#5A2F14", "#A8612D", "#E2A86C", "#F5D9B4", "#FF9A1F", "#FFF4E2"

K = 56 / 772          # the owl, 772 units tall, as 56 dp
TX, TY = 50 - 625 * K, 24 - 196 * K


def owl_g():
    """The owl's parts, without its own <svg>, in the 108 dp icon box."""
    inner = owl31.owl("i", "open", "folded", "folded", False)
    body = inner.split(">", 1)[1].rsplit("</svg>", 1)[0]
    return f'<g transform="translate({TX:.3f} {TY:.3f}) scale({K:.5f})">{body}</g>'


COIN = ('<g transform="translate(73 71)"><circle r="10.5" fill="#E09A12"/><circle r="9" fill="#FFC93C"/>'
        '<circle r="6.6" fill="none" stroke="#E5A417" stroke-width="1"/>'
        '<text y="3.6" text-anchor="middle" font-family="sans-serif" font-size="10" font-weight="800" fill="#B9790A">₹</text></g>')


def svg(layers, size, view="0 0 108 108"):
    bg = ('<defs><linearGradient id="bgg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#FFF1B3"/>'
          '<stop offset=".5" stop-color="#FFC85C"/><stop offset="1" stop-color="#FF9A3D"/></linearGradient></defs>'
          '<rect width="108" height="108" fill="url(#bgg)"/>') if "bg" in layers else ""
    fg = ('<ellipse cx="52" cy="81" rx="20" ry="3" fill="#7A3B18" opacity=".18"/>' + owl_g() + COIN) if "fg" in layers else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}" width="{size}" height="{size}">{bg}{fg}</svg>'


def mono_xml():
    eyes = "M416,415a104,104 0,1 0,208 0a104,104 0,1 0,-208 0z M631,415a104,104 0,1 0,208 0a104,104 0,1 0,-208 0z"
    sil = ("M330 196 C340 270 400 318 500 296 C470 262 420 226 330 196 Z M920 196 C910 270 850 318 750 296 C780 262 830 226 920 196 Z "
           "M480 262 C540 222 710 222 772 258 C840 280 880 330 888 400 C896 470 870 520 836 540 C760 562 690 536 625 548 C560 536 490 562 412 540 C376 520 350 470 360 400 C368 330 410 280 480 262 Z")
    body = "M412 532 C340 590 318 700 342 800 C356 880 370 940 382 966 L600 940 C700 930 790 900 840 860 C886 800 900 720 884 650 C870 590 846 556 836 538 C760 560 690 540 625 548 C560 540 490 560 412 532 Z"
    pupils = "M473,415a52,52 0,1 0,104 0a52,52 0,1 0,-104 0z M688,415a52,52 0,1 0,104 0a52,52 0,1 0,-104 0z M625,430L660,486L625,545L592,486z"
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Made by tools/make_icon_r31.py: the Round 31 owl and its coin as plain shapes, for Android 13+ themed icons. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group android:translateX="{TX:.3f}" android:translateY="{TY:.3f}" android:scaleX="{K:.5f}" android:scaleY="{K:.5f}">
        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{sil} {eyes}" />
        <path android:fillColor="#FFFFFFFF" android:pathData="{body}" />
        <path android:fillColor="#FFFFFFFF" android:pathData="{pupils}" />
    </group>
    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="M73,60.5a10.5,10.5 0,1 0,0.01 0z M73,64a7,7 0,1 1,-0.01 0z" />
</vector>
"""


def main():
    res = M.RES
    jobs = []
    for d, k in M.DENSITIES.items():
        size = round(108 * k)
        jobs.append((svg(("fg",), size), size, os.path.join(res, f"mipmap-{d}", "ic_launcher_fg.png")))
    jobs.append((svg(("bg", "fg"), 512, view="14 14 80 80"), 512, os.path.join(ROOT, "store", "play-icon-512.png")))
    # Home's header owl, on its own
    inner = owl31.owl("h", "open", "folded", "folded", False)
    mascot = inner.replace('viewBox="120 20 1040 1060" width="100%" height="100%"', 'xmlns="http://www.w3.org/2000/svg" viewBox="300 160 650 830" width="192" height="192"')
    jobs.append((mascot, 192, os.path.join(res, "drawable-nodpi", "owl_mascot.png")))
    M.render(jobs)
    with open(os.path.join(res, "drawable", "ic_launcher_mono.xml"), "w") as f:
        f.write(mono_xml())
    from PIL import Image
    im = Image.open(os.path.join(ROOT, "store", "play-icon-512.png")).convert("RGBA")
    flat = Image.new("RGBA", im.size, M.BG[1])
    flat.alpha_composite(im)
    flat.save(os.path.join(ROOT, "store", "play-icon-512.png"), optimize=True)
    print("wrote the launcher layers, owl_mascot.png and store/play-icon-512.png")


if __name__ == "__main__":
    main()
