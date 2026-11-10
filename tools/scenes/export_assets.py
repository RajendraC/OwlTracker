"""Exports the category pictures that don't depend on the amounts for the app.

The people (Sports, Health, Entertainment), Travel's plane, train and coach, and the four utilities play the same
whatever was spent, so they are drawn here once, by the same generator as the design canvas, and saved with their
animations as data. The app's scenes.js (which draws the pictures that do depend on the amounts) strings them
together in the order of the person's own spending.

    python3 tools/scenes/export_assets.py

writes app/src/main/assets/scenes/acts.js and scene.html.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen3  # noqa: E402
import gen4  # noqa: E402
from gen import CATS  # noqa: E402
from gen2 import BASE_CSS, Ctx  # noqa: E402
from gen3 import EXTRA_CSS  # noqa: E402

OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "scenes")
# An act is drawn over 0..AUTHOR_END % of its own timeline, leaving room for the trailing echoes of the people.
AUTHOR_END = 91


class Rec(Ctx):
    """Records each animation as data instead of CSS, so the app can move it into the act's place in the timeline."""

    def __init__(self, uid, dur):
        super().__init__(uid, False, dur)
        self.rec = []

    def anim(self, frames, dur=None, timing="ease-out", delay=0, count=None, box=None, origin=None, extra=""):
        name = super().anim(frames, dur, timing, delay, count, box, origin, extra)
        # d = 0: runs over the act (its keyframes are % of the act); d > 0: repeats on its own clock from the act's start
        self.rec.append({"n": name, "f": frames, "d": dur or 0, "t": timing, "w": delay, "c": count or "1",
                         "b": box or "", "o": origin or "", "x": extra or ""})
        return name


GROUPS = {
    # group: (category colour, seconds per act, [(act id, function)])
    "sports": ("sports", 2.49, [("cricket", gen4.act_cover_drive), ("football", gen4.act_penalty), ("badminton", gen4.act_jump_smash),
                                ("pickleball", gen4.act_rally), ("chess", gen4.act_chess), ("running", gen4.act_block_start)]),
    "health": ("health", 1.8, [("running", gen4.act_running2), ("gym", gen4.on_floor(gen4.act_gym)), ("yoga", gen4.act_yoga),
                               ("meditation", gen4.on_floor(gen4.act_meditate))]),
    "fun": ("entertainment", 1.85, [("movies", gen4.act_movies), ("park", gen4.act_park), ("concert", gen4.act_concert),
                                    ("bowling", gen4.act_bowling), ("streaming", gen4.act_streaming)]),
    "travel": ("travel", 1.91, [("flight", gen4.act_flight), ("train", gen4.act_train), ("coach", gen4.act_coach)]),
    "utilities": ("bills", 1.43, [("power", gen3.act_power), ("water", gen3.act_water), ("broadband", gen3.act_broadband), ("gas", gen3.act_gas)]),
}


def export():
    acts = {}
    for group, (cat, seconds, items) in GROUPS.items():
        c = CATS[cat][1]
        out = {"len": seconds, "end": AUTHOR_END, "acts": {}}
        for i, (key, fn) in enumerate(items):
            ctx = Rec(f"{group[0]}{i}", seconds)
            svg = fn(ctx, c, 0, AUTHOR_END)
            out["acts"][key] = {"svg": svg, "anims": ctx.rec}
        acts[group] = out
    # the drawings scenes.js places by amount: groceries, things in the trolley, dishes ("@C@" is the category colour)
    import gen_food
    shapes = {
        "bowl": {k: v for k, v in gen4.BOWL_ITEMS.items()},
        "shop": gen4.shop_shapes("@C@"),
        "dishes": [svg for _, svg in gen_food.DISHES],
    }
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "acts.js"), "w") as f:
        f.write("// Made by tools/scenes/export_assets.py: do not edit by hand.\nwindow.ACTS = ")
        json.dump(acts, f, ensure_ascii=False, separators=(",", ":"))
        f.write(";\nwindow.SHAPES = ")
        json.dump(shapes, f, ensure_ascii=False, separators=(",", ":"))
        f.write(";\n")
    css = (BASE_CSS + EXTRA_CSS + gen4.GHOST_CSS).replace("body{margin:0}\n", "").replace("a{color:#0F6B4F}a:hover{color:#0B4D38}\n", "")
    page = f"""<!doctype html>
<!-- Made by tools/scenes/export_assets.py: do not edit by hand. -->
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html{{overflow:hidden}}
html,body{{margin:0;padding:0;background:transparent;-webkit-user-select:none;user-select:none}}
#stage{{position:relative;width:248px;height:120px;transform-origin:0 0}}
#stage svg{{display:block}}
{css}</style>
<style id="anim"></style>
</head><body><div id="stage"></div>
<script src="acts.js"></script>
<script src="scenes.js"></script>
</body></html>
"""
    with open(os.path.join(OUT, "scene.html"), "w") as f:
        f.write(page)
    for name in ("acts.js", "scene.html"):
        print(name, os.path.getsize(os.path.join(OUT, name)) // 1024, "KB")


if __name__ == "__main__":
    export()
