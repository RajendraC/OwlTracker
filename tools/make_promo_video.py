"""Builds the Google Play promo video (1920x1080, 30 fps, about 40 s, no sound) from the raw
emulator captures, using the same phone frame and Mango & Cocoa colours as the listing screenshots.
The category pictures play on the phone: their frames are drawn from the app's own scene page
(app/src/main/assets/scenes) in Chromium and laid over the captures (needs Playwright).

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
from make_store_assets import (BEZEL, COCOA, COCOA_SOFT, INK, RIM, ROOT, SHADOW, balanced, font, mango,  # noqa: E402
                               play_icon, rounded_mask, tidy_status_bar)

W, H, FPS = 1920, 1080, 30
SW = 430                        # phone screen width in the video
SH = round(2400 * SW / 1080)
PHONE_CX, PHONE_TOP = 1335, 50  # phone centre x and top of the phone body
CAP_X, CAP_W = 210, 700         # caption column

CAPTIONS = {
    "home": ("See where your money goes", "Every bank, card and UPI payment, sorted from your SMS."),
    "pictures": ("Your month in pictures", "A bright picture for each category fills up with its budget."),
    "where": ("Tap in to see where it went", "Each category's shops, then every payment."),
    "privacy": ("Private by design", "No internet permission. Nothing ever leaves your phone."),
    "reports": ("Reports you can tap into", "Trends, categories and merchants across 1 to 12 months."),
    "budgets": ("Warned before you overspend", "Monthly budgets for everything, or one category at a time."),
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


ZMAX = 1.7  # the closest the camera comes to the phone


class PhoneBody:
    """A plain modern phone, [sw] px of screen wide, with room around it for its shadow."""

    def __init__(self, sw, pad):
        from PIL import ImageFilter
        sh = round(2400 * sw / 1080)
        bez = max(10, round(sw * 0.028))
        pw, ph = sw + 2 * bez, sh + 2 * bez
        r_out = round(pw * 0.105)
        im = Image.new("RGBA", (pw + 2 * pad, ph + 2 * pad), (0, 0, 0, 0))
        shadow = Image.new("RGBA", im.size, (0, 0, 0, 0))
        drop = round(pad * 0.3)
        ImageDraw.Draw(shadow).rounded_rectangle([pad, pad + drop, pad + pw, pad + ph + drop], radius=r_out, fill=SHADOW + (120,))
        im = Image.alpha_composite(im, shadow.filter(ImageFilter.GaussianBlur(pad * 0.43)))
        body = Image.new("RGBA", (pw, ph), BEZEL + (255,))
        body.putalpha(rounded_mask((pw, ph), r_out))
        im.alpha_composite(body, (pad, pad))
        rim = Image.new("RGBA", (pw * 4, ph * 4), (0, 0, 0, 0))
        ImageDraw.Draw(rim).rounded_rectangle([2, 2, pw * 4 - 3, ph * 4 - 3], radius=r_out * 4, outline=RIM + (255,), width=6)
        im.alpha_composite(rim.resize((pw, ph), Image.LANCZOS), (pad, pad))
        self.im, self.sw, self.sh, self.pad, self.bez = im, sw, sh, pad, bez
        self.mask = rounded_mask((sw, sh), r_out - bez)
        cam = max(6, round(sw * 0.026))
        hole = Image.new("RGBA", (cam * 8, cam * 8), (0, 0, 0, 0))
        ImageDraw.Draw(hole).ellipse([0, 0, cam * 8 - 1, cam * 8 - 1], fill=(10, 8, 6, 255))
        self.hole = hole.resize((cam * 2, cam * 2), Image.LANCZOS)
        self.hole_xy = (pad + bez + sw // 2 - cam, pad + bez + round(sh * 0.0155) - cam)

    def put(self, screen):
        """The phone showing [screen] (any size: it is fitted to the screen)."""
        im = self.im.copy()
        s = screen.convert("RGBA")
        if s.size != (self.sw, self.sh):
            s = s.resize((self.sw, self.sh), Image.LANCZOS)
        s.putalpha(self.mask)
        im.alpha_composite(s, (self.pad + self.bez, self.pad + self.bez))
        im.alpha_composite(self.hole, self.hole_xy)
        return im


class Kit:
    """Everything drawn once: background, phone body, scaled screens, caption and title layers."""

    def __init__(self, src):
        self.bg = mango(W, H)
        self.full = {}
        for n in ["10_home", "10b_home_pictures", "13_home_drill", "01_welcome", "50_reports_top", "51_reports_categories",
                  "20_budgets", "70_backup"]:
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
        self.pictures = Pictures(self.full)

    @staticmethod
    def scale(arr):
        return Image.fromarray(arr).resize((SW, SH), Image.LANCZOS)

    def _phone_body(self):
        self.body_small = PhoneBody(SW, 60)
        self.body_big = PhoneBody(round(SW * ZMAX), round(60 * ZMAX))
        self.pad, self.bez = self.body_small.pad, self.body_small.bez

    def phone(self, screen):
        return self.body_small.put(screen)

    def _caption(self, head, sub):
        layer = Image.new("RGBA", (CAP_W + 40, 420), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        hf, sf = font("ExtraBold", 66), font("SemiBold", 36)
        y = 0
        for ln in balanced(d, head, hf, CAP_W):
            d.text((0, y), ln, font=hf, fill=INK)
            y += hf.size + 10
        y += 18
        for ln in balanced(d, sub, sf, CAP_W - 40):
            d.text((0, y), ln, font=sf, fill=COCOA_SOFT)
            y += sf.size + 12
        return layer.crop((0, 0, layer.width, y + 10))

    def _brand_mark(self):
        layer = Image.new("RGBA", (420, 80), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        layer.alpha_composite(play_icon(64), (0, 6))
        d.text((84, 14), "Owl Tracker", font=font("ExtraBold", 38), fill=INK)
        return layer

    def _title_card(self, line1, line2):
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        layer.alpha_composite(play_icon(200), ((W - 200) // 2, 250))
        tf, f1, f2 = font("ExtraBold", 104), font("Bold", 44), font("SemiBold", 32)
        for text, f, y, c in (("Owl Tracker", tf, 486, INK), (line1, f1, 640, INK), (line2, f2, 712, COCOA_SOFT)):
            d.text(((W - d.textlength(text, font=f)) / 2, y), text, font=f, fill=c)
        return layer


class Pictures:
    """The category pictures, playing: frames drawn by the app's own scene page, at their place in each capture."""

    # The demo month: Shopping ₹10,144 of ₹8,000, Food & Dining ₹7,256 of ₹9,000 (as the app hands them over)
    SHOP = {"kind": "shopping", "name": "Shopping", "color": "#7552C4", "p": 10144 / 8000, "spent": 10144, "budget": 8000,
            "sub": "of ₹8,000 budget", "chip": "₹2,144 over", "over": True, "order": [], "still": False}
    FOOD = {"kind": "food", "name": "Food & Dining", "color": "#E65100", "p": 7256 / 9000, "spent": 7256, "budget": 9000,
            "sub": "of ₹9,000 budget", "chip": "81% used", "over": False, "order": [], "still": False}
    DP = 2.625  # the pixel_6 emulator's density
    LAYOUT = {
        # the pictures row: 16 dp in, 280 dp wide, 12 dp apart
        "10b_home_pictures": [(SHOP, round(16 * DP), round(280 * DP)), (FOOD, round((16 + 280 + 12) * DP), round(280 * DP))],
        # "Where it went" with Shopping open: 32 dp in on each side
        "13_home_drill": [(SHOP, round(32 * DP), 1080 - 2 * round(32 * DP))],
    }
    SECONDS = 6.0

    def __init__(self, full):
        self.full = full
        self.frames = {}   # capture -> list of (x, y, [RGBA frames])
        jobs = [(cap, spec, x, w) for cap, items in self.LAYOUT.items() for spec, x, w in items]
        drawn = self._draw([(spec, w) for _, spec, _, w in jobs])
        for (cap, spec, x, w), frames in zip(jobs, drawn):
            # pictures in one row share its height: found from the first, the one fully on screen
            y = self.frames[cap][0][1] if cap in self.frames else self._find(full[cap], frames[-1], x)
            print("placed", spec["name"], "in", cap, "at", (x, y), flush=True)
            self.frames.setdefault(cap, []).append((x, y, frames))
        self._cache = {}

    def _draw(self, items):
        import io
        from playwright.sync_api import sync_playwright
        page_url = "file://" + os.path.abspath(os.path.join(ROOT, "app", "src", "main", "assets", "scenes", "scene.html"))
        out = []
        with sync_playwright() as pw:
            b = pw.chromium.launch()
            for spec, w in items:
                h = round(w * 180 / 320)
                css_w = w / self.DP
                pg = b.new_page(viewport={"width": round(css_w), "height": round(css_w * 180 / 320) + 2}, device_scale_factor=self.DP)
                pg.goto(page_url)
                pg.evaluate("s => { window.__scene = s; window.renderScene(s); }", spec)
                pg.wait_for_function("document.querySelector('#stage svg') !== null")
                pg.evaluate("document.getAnimations().forEach(a => a.pause())")
                frames = []
                mask = rounded_mask((w, h), round(22 * self.DP))
                for i in range(round(self.SECONDS * FPS)):
                    pg.evaluate("t => document.getAnimations().forEach(a => { a.currentTime = t; })", i * 1000 / FPS)
                    im = Image.open(io.BytesIO(pg.locator("#stage").screenshot())).convert("RGBA").resize((w, h), Image.LANCZOS)
                    im.putalpha(mask)
                    frames.append(im)
                pg.close()
                out.append(frames)
                print("drew", spec["name"], "at", w, "px:", len(frames), "frames", flush=True)
            b.close()
        return out

    @staticmethod
    def _find(capture, last, x):
        """Where the picture sits in the capture: the height at which its last frame matches best."""
        k = 4
        cap = Image.fromarray(capture).convert("L")
        cap = cap.resize((cap.width // k, cap.height // k))
        pic = Image.alpha_composite(Image.new("RGBA", last.size, (255, 248, 238, 255)), last).convert("L")
        pic = pic.resize((pic.width // k, pic.height // k))
        c, p = np.asarray(cap, dtype=np.float32), np.asarray(pic, dtype=np.float32)
        x0 = x // k
        w = min(p.shape[1], c.shape[1] - x0)
        best = min(range(0, c.shape[0] - p.shape[0]), key=lambda y: float(np.abs(c[y:y + p.shape[0], x0:x0 + w] - p[:, :w]).mean()))
        return best * k

    def index(self, t):
        return min(round(self.SECONDS * FPS) - 1, max(0, int(t * FPS)))

    def full_screen(self, cap, t):
        """The full-size capture with its pictures at time t (seconds since they started)."""
        i = self.index(t)
        im = Image.fromarray(self.full[cap]).convert("RGBA")
        for x, y, frames in self.frames[cap]:
            f = frames[i]
            im.alpha_composite(f.crop((0, 0, min(f.width, im.width - x), f.height)), (x, y))
        im.paste(Image.fromarray(self.full[cap][:63]), (0, 0))  # the status bar stays on top
        return im.convert("RGB")

    def screen(self, cap, t):
        """The same, scaled for the phone."""
        key = (cap, self.index(t))
        if key not in self._cache:
            self._cache[key] = Kit.scale(np.asarray(self.full_screen(cap, t)))
        return self._cache[key]

    def centre(self, cap, n=0):
        """The middle of picture [n] in a capture, in capture px."""
        x, y, frames = self.frames[cap][n]
        return x + frames[0].width / 2, y + frames[0].height / 2


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
    ImageDraw.Draw(over).ellipse([x * k - r, y * k - r, x * k + r, y * k + r], fill=(255, 255, 255, a), outline=COCOA + (min(255, a + 40),), width=2)
    return Image.alpha_composite(screen.convert("RGBA"), over).convert("RGB")


def crossfade(a, b, p):
    return b if p >= 1 else a if p <= 0 else Image.blend(a, b, ease(p))


# ---------- scenes: each returns the frame at local time t ----------

def place(frame, layer, x, y):
    """alpha_composite that also takes layers hanging off the frame's edges."""
    cx, cy = max(0, -x), max(0, -y)
    w, h = min(layer.width, frame.width - x) - cx, min(layer.height, frame.height - y) - cy
    if w > 0 and h > 0:
        frame.alpha_composite(layer.crop((cx, cy, cx + w, cy + h)), (x + cx, y + cy))


def compose(kit, screen, caption=None, cap_alpha=1.0, cap_dy=0.0, old=None, old_alpha=0.0, phone_dy=0.0, brand_alpha=1.0, extra=None,
            zoom=1.0, focus=None, full=None):
    """One frame. With [zoom] above 1, the camera moves in on the phone: [focus] (x, y in capture px) glides towards the
    middle of the frame, and the phone is drawn from [full] (the full-size screen) so it stays sharp."""
    frame = kit.bg.copy()
    if extra is not None:
        frame.alpha_composite(extra)
    if brand_alpha > 0:
        frame.alpha_composite(with_alpha(kit.brand, brand_alpha), (CAP_X, 84))
    for cap, a, dy in ((old, old_alpha, -16 * (1 - old_alpha)), (caption, cap_alpha, cap_dy)):
        if cap is not None and a > 0:
            layer = kit.captions[cap]
            frame.alpha_composite(with_alpha(layer, a), (CAP_X, int(540 - layer.height / 2 + dy)))
    if screen is not None and zoom > 1.001 and focus is not None:
        big = kit.body_big
        ph = big.put(full if full is not None else screen)
        k = zoom / ZMAX
        ph = ph.resize((round(ph.width * k), round(ph.height * k)), Image.LANCZOS)
        u = (zoom - 1) / (ZMAX - 1)
        fx, fy = focus
        # where the focus sits at zoom 1, and where it is heading
        x1 = PHONE_CX - SW / 2 + fx * SW / 1080
        y1 = PHONE_TOP + kit.bez + fy * SW / 1080
        tx, ty = x1 + (PHONE_CX - x1) * u, y1 + (540 - y1) * u
        sw = SW * zoom
        left = tx - (big.pad + big.bez) * k - fx * sw / 1080
        top = ty - (big.pad + big.bez) * k - fy * sw / 1080
        place(frame, ph, round(left), round(top + phone_dy))
    elif screen is not None:
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


def zoom_at(t, t_in, t_out, z):
    """The camera's zoom: in from t_in (over 0.8 s), out again from t_out (over 0.7 s)."""
    return 1 + (z - 1) * ease(ramp(t, t_in, t_in + 0.8)) * (1 - ease(ramp(t, t_out, t_out + 0.7)))


def scene_pictures(kit, t):
    """The pictures row: the two cards play as the screen comes in, and the camera moves in on them."""
    pics = kit.pictures
    live = pics.screen("10b_home_pictures", t - 0.2)
    p = ramp(t, 0.0, 0.5)
    ca, cdy = caption_in(t)
    z = zoom_at(t, 0.9, 4.3, 1.55)
    cx, cy = pics.centre("10b_home_pictures")
    full = pics.full_screen("10b_home_pictures", t - 0.2) if z > 1.001 else None
    return compose(kit, crossfade(kit.small["10_home"], live, p), "pictures", ca, cdy, old="home",
                   old_alpha=1 - ease(ramp(t, 0, 0.25)), zoom=z, focus=(cx + 160, cy), full=full)


def scene_where(kit, t):
    """A tap on the Shopping picture opens it in "Where it went", where it plays again, big."""
    pics = kit.pictures
    before = ripple(pics.screen("10b_home_pictures", 5.0 + t), 300, 900, ramp(t, 0.0, 0.5))
    live = pics.screen("13_home_drill", t - 0.45)
    p = ramp(t, 0.35, 0.8)
    ca, cdy = caption_in(t)
    z = zoom_at(t, 1.0, 4.5, ZMAX)
    full = pics.full_screen("13_home_drill", t - 0.45) if z > 1.001 else None
    return compose(kit, crossfade(before, live, p), "where", ca, cdy, old="pictures", old_alpha=1 - ease(ramp(t, 0, 0.25)),
                   zoom=z, focus=pics.centre("13_home_drill"), full=full)


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
    where_end = kit.pictures.screen("13_home_drill", 5.5)
    return [
        ("01-intro", 3.2, lambda t: scene_intro(kit, t)),
        ("02-home", 4.4, lambda t: scene_home(kit, t)),
        ("03-pictures", 5.2, lambda t: scene_pictures(kit, t)),
        ("04-where", 5.4, lambda t: scene_where(kit, t)),
        ("05-privacy", 4.0, lambda t: switch(kit, t, where_end, s["01_welcome"], "where", "privacy")),
        ("06-reports", 5.4, lambda t: scene_reports(kit, t)),
        ("07-budgets", 4.0, lambda t: switch(kit, t, s["51_reports_categories"], s["20_budgets"], "reports", "budgets")),
        ("08-backup", 4.0, lambda t: switch(kit, t, s["20_budgets"], s["70_backup"], "budgets", "backup")),
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
