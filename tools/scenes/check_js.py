"""Checks the app's scenes.js against the Python generator it was ported from.

For each scene that depends on the amounts, both draw the same spec; the markup and CSS must match, numbers
within a tenth (Python and JavaScript round exact halves differently). Needs node.

    python3 tools/scenes/check_js.py
"""
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen2  # noqa: E402
import gen3  # noqa: E402
import gen4  # noqa: E402
from gen import CATS  # noqa: E402
from gen2 import Ctx, inr  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "scenes")

NODE = r"""
const vm = require('vm'), fs = require('fs');
const win = {addEventListener() {}};
const sandbox = {window: win, document: {}, console};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(process.argv[1] + '/acts.js', 'utf8'), sandbox);
vm.runInContext(fs.readFileSync(process.argv[1] + '/scenes.js', 'utf8'), sandbox);
const specs = JSON.parse(fs.readFileSync(0, 'utf8'));
console.log(JSON.stringify(specs.map((s) => win.SceneKit.build(s))));
"""


def py_scene(kind, c, p, spent, budget, label, over, order):
    """The same scene from the Python generator, with the uid and durations scenes.js uses."""
    if kind == "transport":
        ctx = Ctx("q", False, 6.5)
        svg, _ = gen3.sc_transport(ctx, c, p, label, over, order)
    elif kind == "food":
        ctx = Ctx("q", False, 2.7 * len(order) / 0.88)
        svg, _ = gen4.sc_food(ctx, c, p, label, over, order)
    elif kind == "groceries":
        ctx = Ctx("q", False, 5)
        svg, _ = gen4.build(ctx, "groceries", "groceries", spent, budget)
    elif kind == "shopping":
        ctx = Ctx("q", False, 6.5)
        svg, _ = gen4.build(ctx, "shopcart", "shopping", spent, budget)
    else:
        ctx = Ctx("q", False, 4)
        fn = {"fuel": gen2.sc_fuel, "recharge": gen2.sc_battery, "education": gen2.sc_books, "rent": gen2.sc_key,
              "emi": gen2.sc_coins, "investments": gen2.sc_plant, "cash": gen2.sc_atm, "others": gen2.sc_jar, "income": gen2.sc_piggy}[kind]
        if kind == "fuel":
            svg, _ = fn(ctx, c, p, f"{round(p * 100)}%", spent)
        elif kind == "rent":
            svg, _ = fn(ctx, c, p, label, over)
        else:
            svg, _ = fn(ctx, c, p, label, over)
    return svg, "\n".join(ctx.css)


NUM = re.compile(r"-?\d+(?:\.\d+)?")


def same(a, b, tol=0.101):  # a tenth can differ where Python rounds an exact half down and JavaScript up
    ta, tb = NUM.split(a), NUM.split(b)
    na, nb = [float(x) for x in NUM.findall(a)], [float(x) for x in NUM.findall(b)]
    if ta != tb or len(na) != len(nb):
        # show where the text parts first differ
        for i, (x, y) in enumerate(zip(ta, tb)):
            if x != y:
                return f"text differs at part {i}: {x[:80]!r} vs {y[:80]!r}"
        return f"different number of parts: {len(ta)} vs {len(tb)}"
    for i, (x, y) in enumerate(zip(na, nb)):
        if abs(x - y) > tol:
            return f"number {i} differs: {x} vs {y} (near {ta[i][-60:]!r})"
    return None


def main():
    cases = []
    for kind, cat, budget, spents, order in [
        ("transport", "transport", 8000, (6480, 9300, 1500), [("bus", "Bus · BMTC"), ("metro", "Metro"), ("scooter", "Scooter · Rapido"), ("car", "Car · Uber")]),
        ("food", "food", 12000, (9000, 13900, 2000), [("table", "Restaurant · Swiggy"), ("tiffin", "Tiffin service"), ("cafe", "Café Coffee Day")]),
        ("food", "food", 12000, (5000,), [("cafe", "Café")]),
        ("groceries", "groceries", 10000, (6600, 11600, 700), None),
        ("shopping", "shopping", 5000, (3750, 6120), None),
        ("fuel", "fuel", 5000, (4350, 6000), None),
        ("recharge", "recharge", 1000, (600, 1300), None),
        ("education", "education", 4000, (2000, 5200), None),
        ("rent", "rent", 22000, (22000, 11000), None),
        ("emi", "emi", 14700, (14700, 9000), None),
        ("investments", "investments", 10000, (8500, 12000), None),
        ("cash", "cash", 5000, (2000, 6000), None),
        ("others", "others", 3000, (900, 3600), None),
        ("income", "income", 65000, (71500, 50000), None),
    ]:
        c = CATS[cat][1]
        for spent in spents:
            p = spent / budget
            label = f"Budget {inr(budget)}"
            over = (inr(spent - budget) + " over") if spent > budget else ""
            cases.append({"kind": kind, "color": c, "p": p, "spent": spent, "budget": budget, "label": label, "over": over,
                          "order": [list(o) for o in order] if order else []})
    out = subprocess.run(["node", "-e", NODE, ASSETS], input=json.dumps(cases), capture_output=True, text=True, check=True)
    js = json.loads(out.stdout)
    bad = 0
    for case, got in zip(cases, js):
        order = [tuple(o) for o in case["order"]]
        svg, css = py_scene(case["kind"], case["color"], case["p"], case["spent"], case["budget"], case["label"], case["over"], order)
        for part, a, b in (("svg", svg, got["svg"]), ("css", css, got["css"])):
            why = same(a, b)
            if why:
                bad += 1
                print(f"{case['kind']} {case['spent']}/{case['budget']} {part}: {why}")
    print(f"{len(cases)} scenes compared, {bad} differences")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
