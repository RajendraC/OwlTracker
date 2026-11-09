"""Builds the video for Google Play's SMS Permissions Declaration (1920x1080, 30 fps, about 55 s, no
sound) from the StoreScreenshots#smsDemo captures: a new user from the welcome screen through the
disclosure, Android's own permission prompt and the import, to a payment's original SMS.

    python3 tools/make_sms_demo_video.py CAPTURES_DIR OUT.mp4
    python3 tools/make_sms_demo_video.py CAPTURES_DIR OUT_DIR --preview 5.0,12.3

CAPTURES_DIR holds d01_welcome.png … d12_no_internet.png and taps.txt (the "sms-demo" pre-release
of the Store screenshots workflow, run with what = sms-demo). Upload the result to YouTube as
Unlisted and paste its link into the declaration form.
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_promo_video import (CAP_W, CAP_X, H, PHONE_CX, PHONE_TOP, SH, SW, W, Kit, caption_in, crossfade,  # noqa: E402
                              ease, ease_in, ease_out, frame_at, ramp, render, ripple, with_alpha)
from make_store_assets import (BRAND, BRAND_DEEP, MINT, WHITE, balanced, font, glow, glyph,  # noqa: E402
                               tidy_status_bar, vertical_gradient)

SHOTS = ["d01_welcome", "d02_disclosure", "d03_android_prompt", "d04_choose_senders", "d05_review", "d06_saved",
         "d07_budget", "d08_home", "d09_activity", "d10_payment", "d11_original_sms", "d12_no_internet"]

# (kicker, headline, line under it)
CAPTIONS = {
    "welcome": ("STEP 1 · OPEN THE APP", "A private expense tracker",
                "Owl Tracker builds your spending from the alerts your bank, card and UPI apps send by SMS."),
    "disclosure": ("STEP 2 · PROMINENT DISCLOSURE", "The app explains first",
                   "Before Android asks, a full screen says what is read, what is ignored, and that nothing leaves the phone."),
    "prompt": ("STEP 3 · ANDROID PERMISSION", "The user decides",
               "Only READ_SMS is requested (Android's prompt names the whole SMS group). Declining is fine: expenses can then be added by hand."),
    "senders": ("STEP 4 · ONLY MONEY ALERTS", "The user picks the senders",
                "Bank, card and UPI alerts only. OTPs, offers and personal messages are ignored."),
    "review": ("STEP 5 · REVIEW", "Nothing is saved unasked",
               "Each transaction found can be checked and unticked before it is imported."),
    "saved": ("STEP 6 · SAVED ON THE PHONE", "Encrypted, on the device",
              "Transactions are stored in an encrypted database on the phone. The SMS inbox is never changed."),
    "budget": ("STEP 7 · BUDGET", "Set a monthly budget", "Optional, and editable any time."),
    "home": ("THE CORE FEATURE", "Spending against the budget",
             "Every number here comes from the user's own transaction alerts."),
    "payment": ("EACH PAYMENT", "Traced back to its SMS",
                "Open any payment to see where it came from and change its category."),
    "original": ("EACH PAYMENT", "The original alert",
                 "Kept only on the phone, so the user can always check what was read."),
    "offline": ("PRIVACY", "No internet permission",
                "SMS content never leaves the phone. No accounts, no ads, no analytics."),
}


class DemoKit(Kit):
    def __init__(self, src):  # Kit.__init__ loads the promo's own screens, so it isn't called
        self.bg = Image.alpha_composite(vertical_gradient(W, H, BRAND, BRAND_DEEP).convert("RGBA"),
                                        glow((W, H), (PHONE_CX, 560), 470, (60, 170, 125), 85))
        self.full = {n: tidy_status_bar(Image.open(os.path.join(src, n + ".png"))) for n in SHOTS}
        self.small = {n: im.convert("RGB").resize((SW, SH), Image.LANCZOS) for n, im in self.full.items()}
        self.taps = {}
        path = os.path.join(src, "taps.txt")
        if os.path.exists(path):
            for line in open(path):
                parts = line.split()
                if len(parts) == 3:
                    self.taps[parts[0]] = (int(parts[1]), int(parts[2]))
        self._phone_body()
        self.captions = {k: self._caption3(*v) for k, v in CAPTIONS.items()}
        self.brand = self._brand_mark()
        self.title = self._card("How Owl Tracker uses SMS access", "Google Play declaration: SMS-based money management (READ_SMS)")
        self.outro = self._card("SMS-based money management", "READ_SMS only · no internet permission · data stays on the phone")

    def _caption3(self, kicker, head, sub):
        layer = Image.new("RGBA", (CAP_W + 40, 520), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        kf, hf, sf = font("Bold", 26), font("Bold", 62), font("Medium", 34)
        d.text((0, 0), kicker, font=kf, fill=MINT)
        y = kf.size + 22
        for ln in balanced(d, head, hf, CAP_W):
            d.text((0, y), ln, font=hf, fill=WHITE)
            y += hf.size + 10
        y += 16
        for ln in balanced(d, sub, sf, CAP_W - 30):
            d.text((0, y), ln, font=sf, fill=MINT)
            y += sf.size + 12
        return layer.crop((0, 0, layer.width, y + 10))

    def _card(self, line1, line2):
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        k = 4
        tile = Image.new("RGBA", (200 * k, 200 * k), (0, 0, 0, 0))
        td = ImageDraw.Draw(tile)
        td.rounded_rectangle([0, 0, 200 * k - 1, 200 * k - 1], radius=52 * k, fill=WHITE)
        glyph(td, 100 * k + 4 * k, 100 * k + 4 * k, 156 * k, color=BRAND)
        layer.alpha_composite(tile.resize((200, 200), Image.LANCZOS), ((W - 200) // 2, 250))
        tf, f1, f2 = font("Bold", 104), font("Medium", 46), font("Medium", 32)
        for text, f, y, c in (("Owl Tracker", tf, 486, WHITE), (line1, f1, 640, WHITE), (line2, f2, 716, MINT)):
            d.text(((W - d.textlength(text, font=f)) / 2, y), text, font=f, fill=c)
        return layer

    def tapped(self, shot, p):
        """The screen with a touch ripple where the next tap lands (if it was recorded)."""
        at = self.taps.get(shot)
        return ripple(self.small[shot], at[0], at[1], p) if at else self.small[shot]



def compose(kit, screen, caption=None, cap_alpha=1.0, cap_dy=0.0, old=None, old_alpha=0.0, phone_dy=0.0, brand_alpha=1.0, extra=None):
    frame = kit.bg.copy()
    if extra is not None:
        frame.alpha_composite(extra)
    if brand_alpha > 0:
        frame.alpha_composite(with_alpha(kit.brand, brand_alpha), (CAP_X, 84))
    for cap, a, dy in ((old, old_alpha, -16 * (1 - old_alpha)), (caption, cap_alpha, cap_dy)):
        if cap is not None and a > 0:
            layer = kit.captions[cap]
            frame.alpha_composite(with_alpha(layer, a), (CAP_X, int(560 - layer.height / 2 + dy)))
    if screen is not None:
        ph = kit.phone(screen)
        frame.alpha_composite(ph, (PHONE_CX - ph.width // 2, int(PHONE_TOP - kit.pad + phone_dy)))
    return frame


def step(kit, prev, shot, prev_cap, cap, dur, tap=True):
    """Crossfade in from the previous screen, hold, then show the tap that leads on."""
    def fn(t):
        ca, cdy = caption_in(t)
        if prev is not None and t < 0.5:
            screen = crossfade(kit.small[prev], kit.small[shot], ramp(t, 0.0, 0.5))
        elif tap:
            screen = kit.tapped(shot, ramp(t, dur - 0.75, dur - 0.1))
        else:
            screen = kit.small[shot]
        same = prev_cap == cap
        return compose(kit, screen, cap, 1.0 if same else ca, 0 if same else cdy,
                       old=None if same else prev_cap, old_alpha=1 - ease(ramp(t, 0, 0.25)))
    return fn


def scene_intro(kit, t):
    a = ease_out(ramp(t, 0.1, 0.9))
    frame = kit.bg.copy()
    frame.alpha_composite(with_alpha(kit.title, a), (0, int(26 * (1 - a))))
    return frame


def scene_welcome(kit, t, dur):
    out = 1 - ease(ramp(t, 0.0, 0.35))
    extra = with_alpha(kit.title, out) if out > 0 else None
    slide = ease_out(ramp(t, 0.2, 1.1))
    ca, cdy = caption_in(t, 0.6, 0.5)
    screen = kit.tapped("d01_welcome", ramp(t, dur - 0.75, dur - 0.1))
    return compose(kit, screen, "welcome", ca, cdy, phone_dy=(1 - slide) * 1100,
                   brand_alpha=ease(ramp(t, 0.6, 1.1)), extra=extra)


def scene_outro(kit, t):
    out = ease_in(ramp(t, 0.0, 0.7))
    fade = 1 - ease(ramp(t, 0.0, 0.4))
    a = ease_out(ramp(t, 0.5, 1.3))
    frame = compose(kit, kit.small["d12_no_internet"], "offline", fade, 0, phone_dy=out * 1150, brand_alpha=fade) if t < 0.75 else kit.bg.copy()
    frame.alpha_composite(with_alpha(kit.outro, a), (0, int(26 * (1 - a))))
    return frame


def scenes(kit):
    plan = [  # (scene name, previous shot, shot, previous caption, caption, seconds, tap at the end)
        ("03-disclosure", "d01_welcome", "d02_disclosure", "welcome", "disclosure", 7.0, True),
        ("04-prompt", "d02_disclosure", "d03_android_prompt", "disclosure", "prompt", 5.5, True),
        ("05-senders", "d03_android_prompt", "d04_choose_senders", "prompt", "senders", 5.5, True),
        ("06-review", "d04_choose_senders", "d05_review", "senders", "review", 4.5, True),
        ("07-saved", "d05_review", "d06_saved", "review", "saved", 5.0, True),
        ("08-budget", "d06_saved", "d07_budget", "saved", "budget", 3.5, True),
        ("09-home", "d07_budget", "d08_home", "budget", "home", 4.5, False),
        ("10-activity", "d08_home", "d09_activity", "home", "payment", 3.5, True),
        ("11-payment", "d09_activity", "d10_payment", "payment", "payment", 3.5, True),
        ("12-original", "d10_payment", "d11_original_sms", "payment", "original", 5.0, False),
        ("13-offline", "d11_original_sms", "d12_no_internet", "original", "offline", 5.0, False),
    ]
    out = [("01-intro", 3.5, lambda t: scene_intro(kit, t)),
           ("02-welcome", 4.5, lambda t: scene_welcome(kit, t, 4.5))]
    for name, prev, shot, pcap, cap, dur, tap in plan:
        out.append((name, dur, step(kit, prev, shot, pcap, cap, dur, tap)))
    out.append(("14-outro", 4.0, lambda t: scene_outro(kit, t)))
    return out


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    kit = DemoKit(sys.argv[1])
    sc = scenes(kit)
    if "--preview" in sys.argv:
        os.makedirs(sys.argv[2], exist_ok=True)
        for T in sys.argv[sys.argv.index("--preview") + 1].split(","):
            path = os.path.join(sys.argv[2], f"frame-{float(T):05.2f}.png")
            frame_at(sc, float(T)).convert("RGB").save(path)
            print("wrote", path)
        return
    out = sys.argv[2]
    render(sc, out, os.path.join(os.path.dirname(os.path.abspath(out)) or ".", "sms-demo-scenes"))
    print("total", sum(d for _, d, _ in sc), "s ->", out)


if __name__ == "__main__":
    main()
