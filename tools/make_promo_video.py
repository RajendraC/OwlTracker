"""Builds the Google Play promo video (1920x1080, 30 fps, about 38 s, no sound) from the raw
emulator captures, using the same phone frame and colours as the listing screenshots.

    python3 tools/make_promo_video.py CAPTURES_DIR OUT.mp4            # full render
    python3 tools/make_promo_video.py CAPTURES_DIR OUT_DIR --preview 2.0,5.1,14.5

Each scene is encoded to its own mp4 (identical settings), then joined without re-encoding.
Upload the result to YouTube (unlisted, ads off) and paste its link into Play Console.
"""
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_store_assets import (BEZEL, BRAND, BRAND_DEEP, MINT, WHITE, balanced, font, glow, glyph,  # noqa: E402
                               rounded_mask, tidy_status_bar, vertical_gradient)

W, H, FPS = 1920, 1080, 30
SW = 430                        # phone screen width in the video
SH = round(2400 * SW / 1080)
PHONE_CX, PHONE_TOP = 1335, 50  # phone centre x and top of the phone body
CAP_X, CAP_W = 210, 700         # caption column

CAPTIONS = {
    "home": ("See where your money goes", "Every bank, card and UPI payment, sorted from your SMS."),
    "privacy": ("Private by design", "No internet permission. Nothing ever leaves your phone."),
    "reports": ("Reports you can tap into", "Trends, categories and merchants across 1 to 12 months."),
    "drill": ("Drill into any category", "Tap a slice, a merchant or a weekday to filter."),
    "budgets": ("Warned before you overspend", "Monthly budgets for everything, or one category at a time."),
    "activity": ("Every payment in one place", "Search, change categories or add cash spends."),
    "backup": ("Your data, your backups", "Save a file, add a password, restore it on a new phone."),
}


def ease(t):  # ease-in-out cubic, t in [0, 1]
    t = min(1.0, max(0.0, t))
    return 4 * t * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2


def ease_out(t):
    t = min(1.0, max(0.0, t))
    return 1 - (1 - t) ** 3


def ease_in(t):
    t = min(1.0, max(0.0, t))
    return t * t * t


def ramp(t, a, b):
    """0 before a, 1 after b, linear between."""
    return min(1.0, max(0.0, (t - a) / (b - a)))


class Kit:
    """Everything drawn once: background, phone body, scaled screens, caption and title layers."""

    def __init__(self, src):
        self.bg = Image.alpha_composite(vertical_gradient(W, H, BRAND, BRAND_DEEP).convert("RGBA"),
                                        glow((W, H), (PHONE_CX, 560), 470, (60, 170, 125), 85))
        self.full = {}
        for n in ["10_home", "01_welcome", "50_reports_top", "51_reports_categories", "53_reports_filtered_top",
                  "20_budgets", "30_activity", "31_txn_detail", "70_backup"]:
            self.full[n] = np.asarray(tidy_status_bar(Image.open(os.path.join(src, n + ".png"))))
        self.small = {n: self.scale(a) for n, a in self.full.items()}
        self._phone_body()
        self.captions = {k: self._caption(*v) for k, v in CAPTIONS.items()}
        self.brand = self._brand_mark()
        self.title = self._title_card("Spending sorted from your bank & UPI SMS", "No internet permission. Nothing leaves your phone.")
        self.outro = self._title_card("Your spending, sorted. Privately.", "No sign-up. No internet. Just your phone.")
        # Reports: 51 is 50 scrolled down by exactly 1737 px (measured), so the two make one tall page.
        a, b = self.full["50_reports_top"], self.full["51_reports_categories"]
        self.d_scroll = 1737
        self.tall = np.concatenate([a[:2358], b[2358 - self.d_scroll:2358]], axis=0)
        self.bottom = a[2358:]

    @staticmethod
    def scale(arr):
        return Image.fromarray(arr).resize((SW, SH), Image.LANCZOS)

    def _phone_body(self):
        bez = max(10, round(SW * 0.028))
        pw, ph = SW + 2 * bez, SH + 2 * bez
        r_out = round(pw * 0.105)
        pad = 60
        im = Image.new("RGBA", (pw + 2 * pad, ph + 2 * pad), (0, 0, 0, 0))
        shadow = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle([pad, pad + 18, pad + pw, pad + ph + 18], radius=r_out, fill=(0, 20, 12, 130))
        from PIL import ImageFilter
        im = Image.alpha_composite(im, shadow.filter(ImageFilter.GaussianBlur(26)))
        body = Image.new("RGBA", (pw, ph), BEZEL + (255,))
        body.putalpha(rounded_mask((pw, ph), r_out))
        im.alpha_composite(body, (pad, pad))
        rim = Image.new("RGBA", (pw * 4, ph * 4), (0, 0, 0, 0))
        ImageDraw.Draw(rim).rounded_rectangle([2, 2, pw * 4 - 3, ph * 4 - 3], radius=r_out * 4, outline=(70, 82, 76, 255), width=6)
        im.alpha_composite(rim.resize((pw, ph), Image.LANCZOS), (pad, pad))
        self.body, self.pad, self.bez = im, pad, bez
        self.mask = rounded_mask((SW, SH), r_out - bez)
        cam = max(6, round(SW * 0.026))
        hole = Image.new("RGBA", (cam * 8, cam * 8), (0, 0, 0, 0))
        ImageDraw.Draw(hole).ellipse([0, 0, cam * 8 - 1, cam * 8 - 1], fill=(8, 10, 9, 255))
        self.hole = hole.resize((cam * 2, cam * 2), Image.LANCZOS)
        self.hole_xy = (pad + bez + SW // 2 - cam, pad + bez + round(SH * 0.0155) - cam)

    def phone(self, screen):
        im = self.body.copy()
        s = screen.convert("RGBA")
        s.putalpha(self.mask)
        im.alpha_composite(s, (self.pad + self.bez, self.pad + self.bez))
        im.alpha_composite(self.hole, self.hole_xy)
        return im

    def _caption(self, head, sub):
        layer = Image.new("RGBA", (CAP_W + 40, 420), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        hf, sf = font("Bold", 66), font("Medium", 36)
        y = 0
        for ln in balanced(d, head, hf, CAP_W):
            d.text((0, y), ln, font=hf, fill=WHITE)
            y += hf.size + 10
        y += 18
        for ln in balanced(d, sub, sf, CAP_W - 40):
            d.text((0, y), ln, font=sf, fill=MINT)
            y += sf.size + 12
        return layer.crop((0, 0, layer.width, y + 10))

    def _brand_mark(self):
        layer = Image.new("RGBA", (420, 80), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        d.rounded_rectangle([0, 6, 64, 70], radius=17, fill=WHITE)
        glyph(d, 33, 39, 50, color=BRAND)
        d.text((84, 12), "Owl Tracker", font=font("Bold", 38), fill=WHITE)
        return layer

    def _title_card(self, line1, line2):
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        k = 4
        tile = Image.new("RGBA", (200 * k, 200 * k), (0, 0, 0, 0))
        td = ImageDraw.Draw(tile)
        td.rounded_rectangle([0, 0, 200 * k - 1, 200 * k - 1], radius=52 * k, fill=WHITE)
        glyph(td, 100 * k + 4 * k, 100 * k + 4 * k, 156 * k, color=BRAND)
        layer.alpha_composite(tile.resize((200, 200), Image.LANCZOS), ((W - 200) // 2, 250))
        tf, f1, f2 = font("Bold", 104), font("Medium", 44), font("Medium", 32)
        for text, f, y, c in (("Owl Tracker", tf, 486, WHITE), (line1, f1, 640, WHITE), (line2, f2, 712, MINT)):
            d.text(((W - d.textlength(text, font=f)) / 2, y), text, font=f, fill=c)
        return layer


def with_alpha(layer, a):
    if a >= 0.999:
        return layer
    out = layer.copy()
    out.putalpha(out.getchannel("A").point(lambda v: int(v * a)))
    return out


def ripple(screen, x, y, p):
    """A touch ripple at full-resolution screen point (x, y), progress p in [0, 1]."""
    if p <= 0 or p >= 1:
        return screen
    k = SW / 1080
    r = 18 + 42 * ease_out(p)
    a = int(170 * (1 - p))
    over = Image.new("RGBA", screen.size, (0, 0, 0, 0))
    ImageDraw.Draw(over).ellipse([x * k - r, y * k - r, x * k + r, y * k + r], fill=(255, 255, 255, a), outline=(15, 107, 79, min(255, a + 40)), width=2)
    return Image.alpha_composite(screen.convert("RGBA"), over).convert("RGB")


def crossfade(a, b, p):
    return b if p >= 1 else a if p <= 0 else Image.blend(a, b, ease(p))


# ---------- scenes: each returns the frame at local time t ----------

def compose(kit, screen, caption=None, cap_alpha=1.0, cap_dy=0.0, old=None, old_alpha=0.0, phone_dy=0.0, brand_alpha=1.0, extra=None):
    frame = kit.bg.copy()
    if extra is not None:
        frame.alpha_composite(extra)
    if brand_alpha > 0:
        frame.alpha_composite(with_alpha(kit.brand, brand_alpha), (CAP_X, 84))
    for cap, a, dy in ((old, old_alpha, -16 * (1 - old_alpha)), (caption, cap_alpha, cap_dy)):
        if cap is not None and a > 0:
            layer = kit.captions[cap]
            frame.alpha_composite(with_alpha(layer, a), (CAP_X, int(540 - layer.height / 2 + dy)))
    if screen is not None:
        ph = kit.phone(screen)
        frame.alpha_composite(ph, (PHONE_CX - ph.width // 2, int(PHONE_TOP - kit.pad + phone_dy)))
    return frame


def caption_in(t, start=0.28, length=0.45):
    p = ramp(t, start, start + length)
    return ease_out(p), 22 * (1 - ease_out(p))


def scene_intro(kit, t):
    a = ease_out(ramp(t, 0.1, 0.9))
    frame = kit.bg.copy()
    layer = with_alpha(kit.title, a)
    frame.alpha_composite(layer, (0, int(26 * (1 - a))))
    return frame


def scene_home(kit, t):
    out = 1 - ease(ramp(t, 0.0, 0.35))
    extra = with_alpha(kit.title, out) if out > 0 else None
    slide = ease_out(ramp(t, 0.2, 1.1))
    ca, cdy = caption_in(t, 0.6, 0.5)
    return compose(kit, kit.small["10_home"], "home", ca, cdy, phone_dy=(1 - slide) * 1100,
                   brand_alpha=ease(ramp(t, 0.6, 1.1)), extra=extra)


def switch(kit, t, prev_screen, screen, prev_cap, cap):
    p = ramp(t, 0.0, 0.5)
    ca, cdy = caption_in(t)
    return compose(kit, crossfade(prev_screen, screen, p), cap, ca, cdy, old=prev_cap, old_alpha=1 - ease(ramp(t, 0, 0.25)))


def reports_at(kit, s):
    s = int(round(s))
    a = kit.full["50_reports_top"]
    frame = np.concatenate([a[:63], kit.tall[63 + s:63 + s + 2295], kit.bottom], axis=0)
    return Kit.scale(frame)


def scene_reports(kit, t):
    if t < 0.5:
        return switch(kit, t, kit.small["01_welcome"], kit.small["50_reports_top"], "privacy", "reports")
    s = kit.d_scroll * ease(ramp(t, 2.1, 3.7))
    screen = kit.small["50_reports_top"] if s < 0.5 else kit.small["51_reports_categories"] if s > kit.d_scroll - 0.5 else reports_at(kit, s)
    return compose(kit, screen, "reports")


def scene_drill(kit, t):
    before = ripple(kit.small["51_reports_categories"], 300, 1140, ramp(t, 0.0, 0.5))
    p = ramp(t, 0.35, 0.8)
    ca, cdy = caption_in(t)
    return compose(kit, crossfade(before, kit.small["53_reports_filtered_top"], p), "drill", ca, cdy, old="reports", old_alpha=1 - ease(ramp(t, 0, 0.25)))


def scene_activity(kit, t):
    if t < 0.5:
        return switch(kit, t, kit.small["20_budgets"], kit.small["30_activity"], "budgets", "activity")
    base_full = kit.full["30_activity"]
    if t < 2.2:
        return compose(kit, ripple(kit.small["30_activity"], 400, 700, ramp(t, 1.75, 2.2)), "activity")
    p = ease_out(ramp(t, 2.2, 2.75))
    if p >= 1:
        return compose(kit, kit.small["31_txn_detail"], "activity")
    dim = (base_full.astype(np.float32) * (1 - 0.32 * p)).astype(np.uint8)
    sheet_top = 362
    sheet = kit.full["31_txn_detail"][sheet_top:]
    y = int(sheet_top + (1 - p) * (2400 - sheet_top))
    frame = dim.copy()
    frame[y:] = sheet[:2400 - y]
    return compose(kit, Kit.scale(frame), "activity")


def scene_outro(kit, t):
    out = ease_in(ramp(t, 0.0, 0.7))
    fade = 1 - ease(ramp(t, 0.0, 0.4))
    a = ease_out(ramp(t, 0.5, 1.3))
    title = with_alpha(kit.outro, a)
    frame = compose(kit, kit.small["70_backup"], "backup", fade, 0, phone_dy=out * 1150, brand_alpha=fade) if t < 0.75 else kit.bg.copy()
    frame.alpha_composite(title, (0, int(26 * (1 - a))))
    return frame


def scenes(kit):
    s = kit.small
    return [
        ("01-intro", 3.2, lambda t: scene_intro(kit, t)),
        ("02-home", 4.6, lambda t: scene_home(kit, t)),
        ("03-privacy", 4.0, lambda t: switch(kit, t, s["10_home"], s["01_welcome"], "home", "privacy")),
        ("04-reports", 5.4, lambda t: scene_reports(kit, t)),
        ("05-drill", 3.8, lambda t: scene_drill(kit, t)),
        ("06-budgets", 4.0, lambda t: switch(kit, t, s["53_reports_filtered_top"], s["20_budgets"], "drill", "budgets")),
        ("07-activity", 5.0, lambda t: scene_activity(kit, t)),
        ("08-backup", 4.0, lambda t: switch(kit, t, s["31_txn_detail"], s["70_backup"], "activity", "backup")),
        ("09-outro", 4.0, lambda t: scene_outro(kit, t)),
    ]


def frame_at(sc, T):
    t0 = 0.0
    for name, dur, fn in sc:
        if T < t0 + dur:
            return fn(T - t0)
        t0 += dur
    return sc[-1][2](sc[-1][1] - 1 / FPS)


def render(sc, out, workdir):
    os.makedirs(workdir, exist_ok=True)
    parts = []
    for name, dur, fn in sc:
        tmp, final = os.path.join(workdir, name + ".part.mp4"), os.path.join(workdir, name + ".mp4")
        enc = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
                                "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-crf", "18", "-preset", "veryfast",
                                "-pix_fmt", "yuv420p", "-r", str(FPS), tmp], stdin=subprocess.PIPE)
        for i in range(round(dur * FPS)):
            enc.stdin.write(fn(i / FPS).convert("RGB").tobytes())
        enc.stdin.close()
        if enc.wait() != 0:
            sys.exit(f"ffmpeg failed on {name}")
        os.replace(tmp, final)
        parts.append(final)
        print("scene", name, "done", flush=True)
    lst = os.path.join(workdir, "parts.txt")
    with open(lst, "w") as f:
        f.writelines(f"file '{os.path.abspath(p)}'\n" for p in parts)
    tmp = out[:-4] + ".part.mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy",
                    "-movflags", "+faststart", tmp], check=True)
    os.replace(tmp, out)


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    kit = Kit(sys.argv[1])
    sc = scenes(kit)
    if "--preview" in sys.argv:
        os.makedirs(sys.argv[2], exist_ok=True)
        for T in sys.argv[sys.argv.index("--preview") + 1].split(","):
            path = os.path.join(sys.argv[2], f"frame-{float(T):05.2f}.png")
            frame_at(sc, float(T)).convert("RGB").save(path)
            print("wrote", path)
        return
    out = sys.argv[2]
    render(sc, out, os.path.join(os.path.dirname(os.path.abspath(out)) or ".", "promo-scenes"))
    print("total", sum(d for _, d, _ in sc), "s ->", out)


if __name__ == "__main__":
    main()
