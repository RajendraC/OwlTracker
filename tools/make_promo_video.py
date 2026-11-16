"""Builds the Google Play promo video (1920x1080, 30 fps, about 55 s, no sound) from the raw
emulator captures, using the same phone frame and Mango & Cocoa colours as the listing screenshots.
It leads with privacy (no internet access, no account, data only on the phone and under the user's
control), then the app: groups, six months, Paid by, budgets and reports.

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
    "internet": ("No internet access", "Owl Tracker has no internet permission, so your SMS and spending can't leave your phone."),
    "account": ("No account, no user ID", "No sign-up, no email, no password. Open it and start."),
    "phone": ("Stays on your phone, encrypted", "Stored only here, locked with a key in your phone's secure hardware."),
    "control": ("You're in full control", "Back up to your own file, delete by bank or by date, or erase it all."),
    "home": ("See where your money goes", "An expense tracker that fills itself in from your bank, card and UPI SMS."),
    "groups": ("Tap a group to go deeper", "Shopping into Fashion, Phones & Electronics and more, then each shop."),
    "six": ("Six months at a glance", "Every group's trend, against your budget."),
    "paid": ("See what each card paid for", "Spending by card and bank account, by its last 4 digits."),
    "budgets": ("Budgets by group", "Set one for Food, not for every snack. Warned before you overspend."),
    "reports": ("Reports you can tap into", "Trends, categories and shops over 1 to 12 months."),
}
PROMISES = ["No internet access", "No account or user ID", "Your data stays on your phone"]


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

    SCREENS = ["01_welcome", "02_disclosure", "05_import_done", "70_backup", "75b_settings_reset", "10_home", "13b_home_group",
               "15e_home_six_months", "15g_home_paid_by", "15i_home_paid_by_open", "20_budgets", "50_reports_top"]

    def __init__(self, src):
        self.bg = mango(W, H)
        self.full = {n: np.asarray(tidy_status_bar(Image.open(os.path.join(src, n + ".png")))) for n in self.SCREENS}
        self.small = {n: self.scale(a) for n, a in self.full.items()}
        self._phone_body()
        self.captions = {k: self._caption(*v) for k, v in CAPTIONS.items()}
        self.brand = self._brand_mark()
        self.title = self._title_card("The expense tracker that reads your bank SMS")
        self.outro = self._title_card("The private expense tracker.")
        self.badges = [self._badge(t) for t in PROMISES]

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
        layer = Image.new("RGBA", (CAP_W + 40, 460), (0, 0, 0, 0))
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

    def _title_card(self, line1):
        """The icon, the name and one line; the three promises go under it as badges."""
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        layer.alpha_composite(play_icon(180), ((W - 180) // 2, 170))
        tf, f1 = font("ExtraBold", 100), font("Bold", 46)
        for text, f, y in (("Owl Tracker", tf, 382), (line1, f1, 526)):
            d.text(((W - d.textlength(text, font=f)) / 2, y), text, font=f, fill=INK)
        return layer

    @staticmethod
    def _badge(text):
        """A cream pill with a cocoa tick and [text]."""
        f = font("ExtraBold", 38)
        tw = ImageDraw.Draw(Image.new("RGBA", (1, 1))).textlength(text, font=f)
        w, h, k = int(tw) + 130, 84, 3
        big = Image.new("RGBA", (w * k, h * k), (0, 0, 0, 0))
        d = ImageDraw.Draw(big)
        d.rounded_rectangle([0, 0, w * k - 1, h * k - 1], radius=h * k // 2, fill=(255, 248, 238, 245), outline=COCOA + (60,), width=2 * k)
        r = 26 * k
        cx, cy = 48 * k, h * k // 2
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=COCOA + (255,))
        d.line([(cx - 11 * k, cy + 1 * k), (cx - 3 * k, cy + 9 * k), (cx + 12 * k, cy - 9 * k)], fill=(255, 210, 110, 255), width=6 * k, joint="curve")
        im = big.resize((w, h), Image.LANCZOS)
        ImageDraw.Draw(im).text((88, (h - f.size) / 2 - 4), text, font=f, fill=INK)
        return im


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


def badges_layer(kit, t, starts, y0=650):
    """The three promises, side by side under the title, each rising in from its start time."""
    gap = 26
    total = sum(b.width for b in kit.badges) + gap * (len(kit.badges) - 1)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    x = (W - total) // 2
    for b, s0 in zip(kit.badges, starts):
        a = ease_out(ramp(t, s0, s0 + 0.45))
        if a > 0:
            layer.alpha_composite(with_alpha(b, a), (x, int(y0 + 30 * (1 - a))))
        x += b.width + gap
    return layer


def scene_intro(kit, t):
    a = ease_out(ramp(t, 0.1, 0.9))
    frame = kit.bg.copy()
    frame.alpha_composite(with_alpha(kit.title, a), (0, int(26 * (1 - a))))
    frame.alpha_composite(badges_layer(kit, t, [1.1, 1.6, 2.1]))
    return frame


def scene_internet(kit, t):
    """The welcome screen comes up; the camera moves in on "No internet permission"."""
    out = 1 - ease(ramp(t, 0.0, 0.35))
    extra = None
    if out > 0:
        extra = with_alpha(kit.title, out)
        extra.alpha_composite(with_alpha(badges_layer(kit, 9, [0, 0, 0]), out))
    slide = ease_out(ramp(t, 0.2, 1.1))
    ca, cdy = caption_in(t, 0.6, 0.5)
    z = zoom_at(t, 1.5, 4.2, 1.6)
    full = Image.fromarray(kit.full["01_welcome"]) if z > 1.001 else None
    return compose(kit, kit.small["01_welcome"], "internet", ca, cdy, phone_dy=(1 - slide) * 1100,
                   brand_alpha=ease(ramp(t, 0.6, 1.1)), extra=extra, zoom=z, focus=(540, 1040), full=full)


def scene_account(kit, t):
    """No sign-in: a tap on Get started goes straight in."""
    before = ripple(kit.small["01_welcome"], 540, 1639, ramp(t, 0.9, 1.4))
    p = ramp(t, 1.25, 1.75)
    ca, cdy = caption_in(t)
    return compose(kit, crossfade(before, kit.small["02_disclosure"], p), "account", ca, cdy, old="internet",
                   old_alpha=1 - ease(ramp(t, 0, 0.25)))


def switch(kit, t, prev_screen, screen, prev_cap, cap):
    p = ramp(t, 0.0, 0.5)
    ca, cdy = caption_in(t)
    return compose(kit, crossfade(prev_screen, screen, p), cap, ca, cdy, old=prev_cap, old_alpha=1 - ease(ramp(t, 0, 0.25)))


def scene_control(kit, t):
    """Backup & restore, then Settings' delete and reset."""
    s = kit.small
    screen = crossfade(s["70_backup"], s["75b_settings_reset"], ramp(t, 2.7, 3.2))
    if t < 0.5:
        screen = crossfade(s["05_import_done"], s["70_backup"], ramp(t, 0.0, 0.5))
    ca, cdy = caption_in(t)
    return compose(kit, screen, "control", ca, cdy, old="phone", old_alpha=1 - ease(ramp(t, 0, 0.25)))


def scene_paid(kit, t):
    """Paid by, then the first card opened into what it was used for."""
    s = kit.small
    if t < 0.5:
        screen = crossfade(s["15e_home_six_months"], s["15g_home_paid_by"], ramp(t, 0.0, 0.5))
    else:
        screen = crossfade(ripple(s["15g_home_paid_by"], PAID_ROW[0], PAID_ROW[1], ramp(t, 2.1, 2.6)), s["15i_home_paid_by_open"], ramp(t, 2.45, 2.95))
    ca, cdy = caption_in(t)
    return compose(kit, screen, "paid", ca, cdy, old="six", old_alpha=1 - ease(ramp(t, 0, 0.25)))


PAID_ROW = (540, 1110)  # the first card's row in 15g_home_paid_by


def zoom_at(t, t_in, t_out, z):
    """The camera's zoom: in from t_in (over 0.8 s), out again from t_out (over 0.7 s)."""
    return 1 + (z - 1) * ease(ramp(t, t_in, t_in + 0.8)) * (1 - ease(ramp(t, t_out, t_out + 0.7)))


def scene_outro(kit, t):
    out = ease_in(ramp(t, 0.0, 0.7))
    fade = 1 - ease(ramp(t, 0.0, 0.4))
    a = ease_out(ramp(t, 0.5, 1.3))
    frame = compose(kit, kit.small["50_reports_top"], "reports", fade, 0, phone_dy=out * 1150, brand_alpha=fade) if t < 0.75 else kit.bg.copy()
    frame.alpha_composite(with_alpha(kit.outro, a), (0, int(26 * (1 - a))))
    frame.alpha_composite(badges_layer(kit, t, [1.3, 1.7, 2.1]))
    return frame


def scenes(kit):
    s = kit.small
    return [
        ("01-intro", 4.2, lambda t: scene_intro(kit, t)),
        ("02-internet", 5.4, lambda t: scene_internet(kit, t)),
        ("03-account", 4.6, lambda t: scene_account(kit, t)),
        ("04-phone", 4.4, lambda t: switch(kit, t, s["02_disclosure"], s["05_import_done"], "account", "phone")),
        ("05-control", 5.6, lambda t: scene_control(kit, t)),
        ("06-home", 4.2, lambda t: switch(kit, t, s["75b_settings_reset"], s["10_home"], "control", "home")),
        ("07-groups", 4.4, lambda t: switch(kit, t, s["10_home"], s["13b_home_group"], "home", "groups")),
        ("08-six", 4.2, lambda t: switch(kit, t, s["13b_home_group"], s["15e_home_six_months"], "groups", "six")),
        ("09-paid", 5.4, lambda t: scene_paid(kit, t)),
        ("10-budgets", 4.0, lambda t: switch(kit, t, s["15i_home_paid_by_open"], s["20_budgets"], "paid", "budgets")),
        ("11-reports", 4.0, lambda t: switch(kit, t, s["20_budgets"], s["50_reports_top"], "budgets", "reports")),
        ("12-outro", 5.0, lambda t: scene_outro(kit, t)),
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
