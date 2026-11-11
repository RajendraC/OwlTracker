"""Checks the app's bright.js against bright.py, the Python reference it was ported from.

Both draw the same specs (every kind, under, at and over budget, still and moving, with odd names and amounts);
the markup and CSS must match, numbers within a tenth (Python and JavaScript round exact halves differently).
Needs node.

    python3 tools/scenes/check_bright.py
"""
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bright  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "scenes")

NODE = r"""
const vm = require('vm'), fs = require('fs');
const win = {};
const sandbox = {window: win, console};
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(process.argv[1] + '/bright-art.js', 'utf8'), sandbox);
vm.runInContext(fs.readFileSync(process.argv[1] + '/bright.js', 'utf8'), sandbox);
const jobs = JSON.parse(fs.readFileSync(0, 'utf8'));
console.log(JSON.stringify(jobs.map((j) => win.BrightKit.scene(j.spec, j.uid, j.loop, j.dur, j.width))));
"""

NUM = re.compile(r"-?\d+(?:\.\d+)?")


def same(a, b, tol=0.101):
    ta, tb = NUM.split(a), NUM.split(b)
    na, nb = [float(x) for x in NUM.findall(a)], [float(x) for x in NUM.findall(b)]
    if ta != tb or len(na) != len(nb):
        for i, (x, y) in enumerate(zip(ta, tb)):
            if x != y:
                return f"text differs at part {i}: {x[:90]!r} vs {y[:90]!r}"
        return f"different number of parts: {len(ta)} vs {len(tb)}"
    for i, (x, y) in enumerate(zip(na, nb)):
        if abs(x - y) > tol:
            return f"number {i} differs: {x} vs {y} (near {ta[i][-60:]!r})"
    return None


def specs():
    kinds = list(bright.CLUSTER) + ["others"]
    out = []
    for kind in kinds:
        for spent, budget in ((0, 12000), (1450, 12000), (9000, 12000), (12000, 12000), (13900, 12000), (40250, 9999)):
            p = spent / budget
            over = spent > budget
            for still in (False, True):
                spec = {"kind": kind, "name": "Pets & <vet> \"care\" and grooming" if kind == "others" else kind.title(),
                        "color": "#7552C4", "p": p, "spent": spent, "budget": budget, "sub": f"of {bright.inr(budget)} budget",
                        "chip": (bright.inr(spent - budget) + " over") if over else f"{round(p * 100)}% used", "over": over,
                        "order": ["scooter", "bus"] if kind == "transport" else [], "still": still}
                out.append({"spec": spec, "uid": "q", "loop": kind in ("food", "travel") and not still, "dur": 7.0 if kind == "food" else 4.4,
                            "width": 300 if kind == "sports" else None})
    return out


if __name__ == "__main__":
    jobs = specs()
    res = subprocess.run(["node", "-e", NODE, ASSETS], input=json.dumps(jobs), capture_output=True, text=True, check=True)
    js = json.loads(res.stdout)
    bad = 0
    for job, (svg, css) in zip(jobs, js):
        py_svg, py_css = bright.scene(job["spec"], job["uid"], job["loop"], job["dur"], job["width"])
        for what, a, b in (("svg", py_svg, svg), ("css", py_css, css)):
            d = same(a, b)
            if d:
                bad += 1
                s = job["spec"]
                print(f"{s['kind']} {s['spent']}/{s['budget']} still={s['still']} {what}: {d}")
    print(f"{len(jobs)} scenes, {bad} differences")
    sys.exit(1 if bad else 0)
