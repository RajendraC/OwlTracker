/*
 * Owl Tracker's category pictures: a small animated scene for the category opened in "Where it went".
 *
 * The scenes that depend on the amount (how far the trip got, how full the cup or the bowls are) are drawn here,
 * a line-for-line port of tools/scenes (the generator behind the design canvas). The ones that don't (the people
 * in Sports, Health and Entertainment, Travel's plane, train and coach, the four utilities) were drawn by that
 * generator into acts.js; here they are only strung together in the order of the person's own spending.
 *
 * The app calls renderScene(spec) once the page has loaded:
 *   { kind, color, p, spent, budget, label, over, order: [[act, chip text], ...], still }
 * p is spent / budget; label and over are ready-made texts ("Budget ₹8,000", "₹1,120 over").
 * Nothing here reaches the network: the page and its scripts come from the app's own files.
 */
(function () {
  'use strict';

  const SK = '#C68A5E', TYRE = '#2F3A35', HUB = '#C9D3CD', NAVY = '#2F3A55';
  const GOLD = '#F2B84B', GOLD_D = '#B8862B', GREY = '#DCE3DE', RED = '#C8423B';
  const X0 = 12, XB = 220, XE = 236, YG = 98;
  const HALO = 'paint-order: stroke; stroke: #FFFFFF; stroke-width: 3px; stroke-linejoin: round';

  // ---------------------------------------------------------------- helpers
  /** Python's round(): halves go to the even neighbour, so counts match the design canvas. */
  function pyRound(x) {
    const r = Math.round(x);
    return (Math.abs(x % 1) === 0.5 && r % 2 !== 0) ? r - 1 : r;
  }
  const f1 = (x) => x.toFixed(1);
  const f2 = (x) => x.toFixed(2);

  function mix(hex, other, t) {
    const a = [1, 3, 5].map((i) => parseInt(hex.substr(i, 2), 16));
    const b = [1, 3, 5].map((i) => parseInt(other.substr(i, 2), 16));
    return '#' + a.map((x, i) => pyRound(x + (b[i] - x) * t).toString(16).toUpperCase().padStart(2, '0')).join('');
  }

  function inr(n) {
    const s = String(pyRound(n));
    if (s.length <= 3) return '₹' + s;
    let head = s.slice(0, -3);
    const tail = s.slice(-3);
    const parts = [];
    while (head.length > 2) { parts.unshift(head.slice(-2)); head = head.slice(0, -2); }
    if (head) parts.unshift(head);
    return '₹' + parts.join(',') + ',' + tail;
  }

  /** One CSS animation per class, played once: the picture runs when the category opens, and again on a tap. */
  class Ctx {
    constructor(uid, dur) { this.uid = uid; this.dur = dur; this.n = 0; this.css = []; this.loop = false; }
    anim(frames, o) {
      o = o || {};
      this.n += 1;
      const name = this.uid + 'k' + this.n;
      this.css.push(rule(name, frames, o.dur || this.dur, o.timing || 'ease-out', o.delay || 0, o.count || '1', o.box, o.origin, o.extra));
      return name;
    }
    spin(t, turns) {
      return this.anim(`0%{transform:rotate(0)}${t === undefined ? 40 : t}%,100%{transform:rotate(${360 * (turns === undefined ? 3 : turns)}deg)}`,
        { box: 'fill-box', origin: 'center' });
    }
    appear(at) { return this.anim(`0%,${Math.max(at - 4, 0)}%{opacity:0}${at}%,100%{opacity:1}`); }
  }

  function rule(name, frames, dur, timing, delay, count, box, origin, extra) {
    const props = [];
    if (box) props.push('transform-box:' + box);
    if (origin) props.push('transform-origin:' + origin);
    if (extra) props.push(extra);
    props.push(`animation:${name} ${dur}s ${timing} ${delay}s ${count} both`);
    return '.' + name + '{' + props.join(';') + '}@keyframes ' + name + '{' + frames + '}';
  }

  /** Moves keyframes written over 0..end % of an act into the act's window [s, e] of the whole timeline. */
  function mapFrames(frames, s, e, end) {
    const out = [];
    frames.split('}').forEach((block) => {
      const at = block.indexOf('{');
      if (at < 0) return;
      const sel = block.slice(0, at), body = block.slice(at + 1);
      const parts = [];
      sel.split(',').forEach((part) => {
        const v = parseFloat(part.trim());
        if (v <= 0) parts.push('0%', f2(s) + '%');
        else if (v >= 100) parts.push(f2(Math.min(s + (e - s) * 100 / end, 100)) + '%', '100%');
        else parts.push(f2(Math.min(s + (e - s) * v / end, 99.99)) + '%');
      });
      out.push(parts.join(',') + '{' + body + '}');
    });
    return out.join('');
  }

  /** Runs a whole-scene drawing inside one act's window, as the canvas does for Food. */
  class Sub {
    constructor(ctx, s, e) { this.ctx = ctx; this.s = s; this.e = e; this.dur = ctx.dur; this.loop = false; }
    anim(frames, o) { return this.ctx.anim(mapFrames(frames, this.s, this.e, 100), o); }
    appear(at) { return this.anim(`0%,${Math.max(at - 4, 0)}%{opacity:0}${at}%,100%{opacity:1}`); }
    spin(t, turns) { return Ctx.prototype.spin.call(this, t, turns); }
  }

  function win(ctx, s, e, local, o) {
    o = Object.assign({ timing: 'ease-in-out' }, o || {});
    const fr = [`0%,${f2(s)}%{${local[0][1]}}`];
    local.slice(1, -1).forEach(([u, css]) => fr.push(`${f2(s + (e - s) * u)}%{${css}}`));
    const last = local[local.length - 1];
    fr.push(`${f2(s + (e - s) * last[0])}%,100%{${last[1]}}`);
    return ctx.anim(fr.join(''), o);
  }

  function windows(n, end) {
    const step = end / n;
    return Array.from({ length: n }, (_, i) => [i * step, (i + 1) * step]);
  }

  function actCls(ctx, i, n, s, e, scale) {
    if (n === 1) return '';
    const fd = 2.5;
    const a0 = scale ? 'transform:scale(.86);' : '', a1 = scale ? 'transform:scale(1);' : '';
    let fr;
    if (i === 0) fr = `0%,${f2(e - fd)}%{opacity:1;${a1}}${f2(e)}%,100%{opacity:0;${a0}}`;
    else if (i === n - 1) fr = `0%,${f2(s)}%{opacity:0;${a0}}${f2(s + fd)}%,100%{opacity:1;${a1}}`;
    else fr = `0%,${f2(s)}%{opacity:0;${a0}}${f2(s + fd)}%,${f2(e - fd)}%{opacity:1;${a1}}${f2(e)}%,100%{opacity:0;${a0}}`;
    return ctx.anim(fr, { timing: 'ease-in-out', box: scale ? 'fill-box' : null, origin: scale ? 'center' : null });
  }

  function chip(text, cls) {
    const w = 6.4 * text.length + 20;
    return `<g class="${cls}"><rect x="10" y="8" width="${w.toFixed(0)}" height="19" rx="9.5" fill="#FFFFFF" stroke="${GREY}" style="stroke-width: 1.2"></rect>` +
      `<text x="20" y="21.2" class="chip">${esc(text)}</text></g>`;
  }

  function esc(t) { return String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

  function progressBottom(ctx, c, p, label, over, end) {
    end = end === undefined ? 88 : end;
    const y = 111, x0 = 12, xb = 220;
    const xp = x0 + (xb - x0) * Math.min(p, 1);
    const t1 = p <= 1 ? end : end / p;
    const k = ctx.anim(`0%{stroke-dashoffset:100}${f1(t1)}%,100%{stroke-dashoffset:0}`, { timing: 'linear', extra: 'stroke-dasharray:100' });
    let out = `<path class="road" d="M${x0} ${y} H236" stroke="${GREY}" style="stroke-width: 5"></path>` +
      `<path class="road ${k}" d="M${x0} ${y} H${f1(xp)}" stroke="${c}" pathLength="100" style="stroke-width: 5"></path>`;
    if (p > 1) {
      const xo = Math.min(x0 + (xb - x0) * p, 236);
      const k2 = ctx.anim(`0%,${f1(t1)}%{stroke-dashoffset:100}${end}%,100%{stroke-dashoffset:0}`, { timing: 'linear', extra: 'stroke-dasharray:100' });
      out += `<path class="road ${k2}" d="M${xb} ${y} H${f1(xo)}" stroke="${RED}" pathLength="100" style="stroke-width: 5"></path>`;
      out += `<text x="236" y="21" class="lbl-red ${ctx.appear(end)}" style="${HALO}">${esc(over)}</text>`;
    }
    out += `<path d="M${xb} ${y} V${y - 15}" stroke="#46524C" class="w15"></path>` +
      `<path d="M${xb} ${y - 15} L${xb + 11} ${y - 12} L${xb} ${y - 9} Z" fill="${c}"></path>` +
      `<text x="${xb - 6}" y="${y - 4}" class="lbl lbl-end">${esc(label)}</text>`;
    return out;
  }

  function track(ctx, c, p, label, t, overText) {
    const y = YG, xb = XB;
    const out = [`<path class="road" d="M${X0} ${y} H${XE}" stroke="${GREY}"></path>`];
    const xp = X0 + (xb - X0) * Math.min(p, 1);
    const t1 = p <= 1 ? t : pyRound(t / p);
    const k = ctx.anim(`0%{stroke-dashoffset:100}${t1}%,100%{stroke-dashoffset:0}`, { extra: 'stroke-dasharray:100' });
    out.push(`<path class="road ${k}" d="M${X0} ${y} H${f1(xp)}" stroke="${c}" pathLength="100"></path>`);
    if (p > 1) {
      const xo = Math.min(X0 + (xb - X0) * p, XE);
      const k2 = ctx.anim(`0%,${t1}%{stroke-dashoffset:100}${t}%,100%{stroke-dashoffset:0}`, { extra: 'stroke-dasharray:100' });
      out.push(`<path class="road ${k2}" d="M${xb} ${y} H${f1(xo)}" stroke="${RED}" pathLength="100"></path>`);
      out.push(`<text x="${XE}" y="16" class="lbl-red ${ctx.appear(t + 4)}">${esc(overText)}</text>`);
    }
    out.push(`<path d="M${xb} ${y} V${y - 42}" stroke="#46524C" class="w2"></path>` +
      `<path d="M${xb} ${y - 42} L${xb + 18} ${y - 36} L${xb} ${y - 30} Z" fill="${c}"></path>`);
    out.push(`<text x="${xb - 7}" y="${y - 47}" class="lbl lbl-end">${esc(label)}</text>`);
    return out.join('');
  }

  /** Things in flight: legs are [u0, p0, u1, p1, ctrl, scale0, scale1]; a control point makes a projectile path. */
  function flight(ctx, s, e, legs, spin) {
    const pts = [];
    legs.forEach(([u0, p0, u1, p1, ctrl, s0, s1]) => {
      const k = Math.max(3, pyRound((u1 - u0) * 90));
      for (let i = 0; i <= k; i++) {
        if (i === 0 && pts.length && Math.abs(pts[pts.length - 1][0] - u0) < 1e-6) continue;
        const t = i / k;
        let x, y;
        if (!ctrl) { x = p0[0] + (p1[0] - p0[0]) * t; y = p0[1] + (p1[1] - p0[1]) * t; }
        else {
          x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * ctrl[0] + t * t * p1[0];
          y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * ctrl[1] + t * t * p1[1];
        }
        pts.push([u0 + (u1 - u0) * t, x, y, s0 + (s1 - s0) * t]);
      }
    });
    const frames = [];
    let ang = 0;
    pts.forEach(([u, x, y, k], i) => {
      if (i) ang += spin * Math.hypot(x - pts[i - 1][1], y - pts[i - 1][2]);
      frames.push([u, `opacity:1;transform:translate(${f1(x)}px,${f1(y)}px) rotate(${ang.toFixed(0)}deg) scale(${k.toFixed(2)})`]);
    });
    const first = frames[0][1].replace('opacity:1', 'opacity:0');
    const out = pts[0][0] > 0.003 ? [[0, first], [pts[0][0] - 0.002, first]].concat(frames) : [[0, frames[0][1]]].concat(frames.slice(1));
    return win(ctx, s, e, out, { timing: 'linear', box: 'view-box', origin: '0 0' });
  }

  // ---------------------------------------------------------------- the acts drawn in advance (acts.js)
  function exportedActs(ctx, group, order, c, p, label, over, opts) {
    const G = window.ACTS[group];
    const list = order.filter(([key]) => G.acts[key]);
    const n = list.length;
    const wins = windows(n, 88);
    let out = opts.ground ? '<path d="M12 96 H236" stroke="#E3E9E5" class="w2"></path>' : '';
    let chips = '';
    list.forEach(([key, text], i) => {
      const [s, e] = wins[i];
      const A = G.acts[key];
      A.anims.forEach((a) => {
        if (a.d > 0) ctx.css.push(rule(a.n, a.f, a.d, a.t, a.w + s / 100 * ctx.dur, a.c, a.b, a.o, a.x));
        else ctx.css.push(rule(a.n, mapFrames(a.f, s, e, G.end), ctx.dur, a.t, a.w, a.c, a.b, a.o, a.x));
      });
      out += `<g class="${actCls(ctx, i, n, s, e, !!opts.scale)}">${A.svg}</g>`;
      chips += chip(text, actCls(ctx, i, n, s, e, false));
    });
    return out + progressBottom(ctx, c, p, label, over) + chips;
  }

  /** Seconds for a scene made of n acts drawn in advance: each keeps its own length, the last 12% holds. */
  function actsDur(group, n) { return window.ACTS[group].len * n / 0.88; }

  // ---------------------------------------------------------------- Transport: one trip, changing vehicle
  function wheel(cx, cy, r, W) {
    return `<g class="${W}"><circle cx="${cx}" cy="${cy}" r="${r}" fill="${TYRE}"></circle>` +
      `<circle cx="${cx}" cy="${cy}" r="${f1(r * 0.42)}" fill="${HUB}"></circle>` +
      `<path d="M${cx} ${f1(cy - r + 1.4)} V${f1(cy - r * 0.42)}" stroke="${HUB}" class="w15"></path></g>`;
  }

  function car(c, W) {
    const D = mix(c, '#000000', 0.28), L = mix(c, '#FFFFFF', 0.72);
    return [`<ellipse cx="38" cy="40" rx="32" ry="2.2" fill="#16211C" class="sh"></ellipse>` +
      `<path d="M6 31 Q4 24 10 22 L20 13 Q24 9 30 9 H46 Q52 9 56 13 L63 21 Q70 22 70 29 V31 Q70 33 68 33 H8 Q6 33 6 31 Z" fill="${c}"></path>` +
      `<path d="M22 21 L27 15 Q29 13 32 13 H36.5 V21 Z" fill="${L}"></path>` +
      `<path d="M39.5 13 H46 Q50 13 53 16 L58 21 H39.5 Z" fill="${L}"></path>` +
      `<path d="M38 22.5 V31 M15 26 H62" stroke="${D}" class="w1"></path>` +
      `<rect x="64.5" y="24" width="4.5" height="3" rx="1" fill="${GOLD}"></rect>` +
      `<rect x="6" y="24" width="2.5" height="4" rx="1" fill="${RED}"></rect>` +
      wheel(18, 33, 6.5, W) + wheel(57, 33, 6.5, W), 70, 39.5];
  }

  function scooter(c, W) {
    const D = mix(c, '#000000', 0.28);
    return [`<ellipse cx="36" cy="40" rx="28" ry="2" fill="#16211C" class="sh"></ellipse>` +
      `<path d="M8 28 Q6 18 16 17 H34 L39 28 Z" fill="${c}"></path>` +
      `<path d="M14 16.5 H32" stroke="${TYRE}" class="w5"></path>` +
      `<path d="M30 29 H50" stroke="${D}" class="w4"></path>` +
      `<path d="M49 30 L55 9" stroke="${c}" class="w5"></path>` +
      `<path d="M46 31 Q54 24 62 31" stroke="${c}" class="w4"></path>` +
      `<path d="M52 9 H60" stroke="${TYRE}" class="w3"></path>` +
      `<circle cx="58.5" cy="13.5" r="2" fill="${GOLD}"></circle>` +
      `<path d="M24 15 L36 17 L38 27" stroke="#3B4A6B" class="w6"></path>` +
      `<path d="M36 27.5 H43" stroke="${TYRE}" class="w4"></path>` +
      `<path d="M23 15 L30 -3" stroke="${NAVY}" class="w10"></path>` +
      `<path d="M29 -1 L41 5 L51 9" stroke="${NAVY}" class="w5"></path>` +
      `<circle cx="52.5" cy="9" r="2.3" fill="${SK}"></circle>` +
      `<circle cx="32" cy="-10" r="6.8" fill="${GOLD}"></circle>` +
      `<path d="M34 -11.5 H39.2 Q39.6 -7 36 -6.4 H34 Z" fill="${TYRE}"></path>` +
      wheel(16, 33, 6.5, W) + wheel(56, 33, 6.5, W), 62, 39.5];
  }

  function bus(c, W) {
    const D = mix(c, '#000000', 0.28), L = mix(c, '#FFFFFF', 0.72);
    const wins = [8, 24, 40, 56].map((x) => `<rect x="${x}" y="8" width="13" height="13" rx="2" fill="${L}"></rect>`).join('');
    return [`<ellipse cx="50" cy="47.5" rx="46" ry="2" fill="#16211C" class="sh"></ellipse>` +
      `<rect x="2" y="2" width="96" height="38" rx="6" fill="${c}"></rect>` +
      `<rect x="2" y="27" width="96" height="4" fill="${D}"></rect>${wins}` +
      `<rect x="72" y="8" width="10" height="28" rx="1.5" fill="${L}"></rect>` +
      `<path d="M77 8 V36" stroke="${c}" class="w1"></path>` +
      `<path d="M86 7 H93 Q97 7 97 11 V23 H86 Z" fill="${L}"></path>` +
      `<rect x="84" y="3.2" width="11" height="2.6" rx="1" fill="${GOLD}"></rect>` +
      `<rect x="94" y="30" width="3" height="3" rx="1" fill="${GOLD}"></rect>` +
      wheel(20, 40, 7, W) + wheel(78, 40, 7, W), 99, 47];
  }

  function metro(c) {
    const wins = [7, 33, 45, 65, 91].map((x) => `<rect x="${x}" y="9" width="9" height="8" rx="1.5" fill="#3B4A57"></rect>`).join('');
    const doors = [20, 78].map((x) => `<rect x="${x}" y="8" width="9" height="20" rx="1" fill="#E7ECE8" stroke="#9AA6A0" class="w1"></rect>`).join('');
    const bog = [8, 38, 66, 98].map((x) => `<rect x="${x}" y="30" width="12" height="3" rx="1" fill="${TYRE}"></rect>`).join('');
    return [`<rect x="2" y="4" width="54" height="26" rx="4" fill="#FFFFFF" stroke="#9AA6A0" class="w1"></rect>` +
      `<path d="M60 4 H106 Q117 4 119.5 17 V30 H60 Z" fill="#FFFFFF" stroke="#9AA6A0" class="w1"></path>` +
      `<rect x="2.6" y="21" width="52.8" height="3.5" fill="${c}"></rect>` +
      `<rect x="60.6" y="21" width="58.6" height="3.5" fill="${c}"></rect>${wins}${doors}` +
      `<path d="M108 8 Q114.5 9 117 16 H108 Z" fill="#3B4A57"></path>` +
      `<circle cx="117" cy="26.5" r="1.5" fill="${GOLD}"></circle>` +
      `<path d="M56 25 H60" stroke="#9AA6A0" class="w2"></path>${bog}`, 120, 33];
  }

  const VEH = { car: [car, 0.62], scooter: [scooter, 0.62], bus: [bus, 0.5], metro: [metro, 0.48] };

  function placed(kind, c, W) {
    const [fn, s] = VEH[kind];
    const [body, front, gnd] = fn(c, W);
    return `<g transform="translate(${f1(-front * s)} ${f1(-gnd * s)}) scale(${s})">${body}</g>`;
  }

  function puffs(ctx, times) {
    const fr = ['0%{opacity:0;transform:scale(.4)}'];
    times.forEach((t) => fr.push(`${f1(t - 1.5)}%{opacity:0;transform:scale(.4)}${f1(t + 1)}%{opacity:.95;transform:scale(1)}${f1(t + 5)}%{opacity:0;transform:scale(1.5)}`));
    fr.push('100%{opacity:0;transform:scale(1.5)}');
    const k = ctx.anim(fr.join(''), { timing: 'ease-out', box: 'fill-box', origin: 'center' });
    return `<g class="${k}"><circle cx="-34" cy="-10" r="7" fill="#FFFFFF" stroke="${GREY}" class="w1"></circle>` +
      `<circle cx="-22" cy="-17" r="8" fill="#FFFFFF" stroke="${GREY}" class="w1"></circle>` +
      `<circle cx="-12" cy="-9" r="6" fill="#FFFFFF" stroke="${GREY}" class="w1"></circle></g>`;
  }

  function scTransport(ctx, c, p, label, over, modes) {
    modes = modes.filter(([k]) => VEH[k]);
    if (!modes.length) modes = [['car', 'Car'], ['scooter', 'Scooter'], ['bus', 'Bus'], ['metro', 'Metro']];
    const end = 84, n = modes.length, wins = windows(n, end);
    const W = ctx.anim(`0%{transform:rotate(0)}${end}%,100%{transform:rotate(2520deg)}`, { timing: 'cubic-bezier(.45,.05,.4,1)', box: 'fill-box', origin: 'center' });
    const start = 72;
    const target = Math.max(Math.min(12 + 208 * p, 236), start + 14);
    const mv = ctx.anim(`0%{transform:translate(${start}px,95px)}${end}%{transform:translate(${f1(target + 2)}px,95px)}` +
      `${end + 6}%,100%{transform:translate(${f1(target)}px,95px)}`, { timing: 'cubic-bezier(.45,.05,.4,1)' });
    let vehicles = '', chips = '';
    modes.forEach(([kind, text], i) => {
      const [s, e] = wins[i];
      vehicles += `<g class="${actCls(ctx, i, n, s, e, true)}">${placed(kind, c, W)}</g>`;
      chips += chip(text, actCls(ctx, i, n, s, e, false));
    });
    const mover = `<g class="${mv}">${vehicles}${puffs(ctx, wins.slice(1).map((w) => w[0]))}</g>`;
    return track(ctx, c, p, label, end, over) + mover + chips;
  }

  // ---------------------------------------------------------------- Fuel: the pump counts up, the gauge settles
  function scFuel(ctx, c, p, label, amount) {
    const L = mix(c, '#FFFFFF', 0.72), D = mix(c, '#000000', 0.28);
    const drop = '0%,6%{opacity:0;transform:translateY(-4px)}10%{opacity:1}30%{opacity:0;transform:translateY(14px)}100%{opacity:0;transform:translateY(14px)}';
    const d1 = ctx.anim(drop, { timing: 'ease-in' }), d2 = ctx.anim(drop, { timing: 'ease-in', delay: 0.3 });
    const kg = ctx.anim(`0%{stroke-dashoffset:100}40%,100%{stroke-dashoffset:${f1(100 - Math.min(p, 1) * 100)}}`, { extra: 'stroke-dasharray:100' });
    const ang = -90 + Math.min(p, 1) * 180;
    const kn = ctx.anim(`0%{transform:rotate(-90deg)}40%{transform:rotate(${f1(ang + 4)}deg)}46%,100%{transform:rotate(${f1(ang)}deg)}`,
      { box: 'view-box', origin: '186px 96px' });
    const svg = `<rect x="18" y="22" width="56" height="76" rx="7" fill="${c}"></rect>` +
      `<rect x="22" y="27" width="48" height="26" rx="4" fill="${L}"></rect>` +
      `<rect x="24" y="30" width="44" height="20" rx="3" fill="#16211C"></rect>` +
      `<rect x="28" y="60" width="36" height="22" rx="3" fill="${L}"></rect>` +
      `<path d="M34 66 H58 M34 72 H52" stroke="${D}" class="w2"></path>` +
      `<rect x="12" y="96" width="68" height="5" rx="2" fill="${D}"></rect>` +
      `<path d="M74 44 H86 Q94 44 94 54 V74" stroke="${TYRE}" class="w4"></path>` +
      `<path d="M90 73 H98 L102 80 H96 Z" fill="${TYRE}"></path>` +
      `<circle class="${d1}" cx="102" cy="86" r="2.8" fill="${c}"></circle>` +
      `<circle class="${d2}" cx="102" cy="86" r="2.8" fill="${c}"></circle>` +
      `<path class="gauge" d="M136 96 A50 50 0 0 1 236 96" stroke="${GREY}"></path>` +
      `<path class="gauge ${kg}" d="M136 96 A50 50 0 0 1 236 96" stroke="${c}" pathLength="100"></path>` +
      `<g class="${kn}"><path d="M186 96 V58" stroke="#16211C" class="w3"></path></g>` +
      `<circle cx="186" cy="96" r="5" fill="#16211C"></circle>` +
      `<text x="136" y="113" class="lbl lbl-mid">E</text><text x="236" y="113" class="lbl lbl-mid">F</text>` +
      `<text x="186" y="113" class="lbl lbl-mid">${esc(label)}</text>`;
    const digits = String(Math.trunc(amount)).split('').map(Number);
    let cols = '';
    digits.forEach((dgt, i) => {
      const k = ctx.anim(`0%{transform:translateY(0)}40%,100%{transform:translateY(-${(10 + dgt) * 14}px)}`, { timing: 'cubic-bezier(.2,.7,.2,1)' });
      cols += `<span class="dg"><span class="col ${k}">` + Array.from({ length: 20 }, (_, j) => `<span>${j % 10}</span>`).join('') + '</span></span>';
      if (digits.length - i === 4) cols += ',';
    });
    return [svg, `<div class="pump">₹${cols}</div>`];
  }

  // ---------------------------------------------------------------- Food: home tiffin, a café cup, a restaurant table
  /** Each food act's own over-budget note is left empty: the scene shows the amount over once, at the top. */
  function quietNote(ctx, p) {
    return p > 1 ? `<text x="236" y="21" class="lbl-red ${ctx.appear(70)}" style="${HALO}"></text>` : '';
  }

  function tiffin(ctx, c, p, label) {
    const x = 112, w = 52, h = 14, base = 94, full = 4;
    const n = Math.max(1, pyRound(p * full));
    const foods = [['#F6F1E6', '#E3DCC8'], ['#E9B949', '#C9962E'], ['#6E9F3B', '#4E7A26'], ['#D9A766', '#B97D3E'], ['#C8423B', '#9E2F28']];
    let tiers = '';
    for (let i = 0; i < n; i++) {
      const y = base - (i + 1) * h;
      const [topFood, edge] = i < full ? foods[i % foods.length] : ['#F3C6C2', RED];
      const at = 10 + i * (50 / n);
      const k = ctx.anim(`0%,${f1(at)}%{opacity:0;transform:translateY(-36px)}${f1(at + 4)}%{opacity:1;transform:translateY(1.4px)}${f1(at + 6)}%,100%{opacity:1;transform:translateY(0)}`,
        { timing: 'ease-in' });
      const steel = i < full ? '#D3DBD6' : '#F0D2CF';
      tiers += `<g class="${k}"><rect x="${x}" y="${y}" width="${w}" height="${h - 1}" rx="3" fill="${steel}"></rect>` +
        `<rect x="${x}" y="${y}" width="${w}" height="3.4" rx="1.7" fill="${topFood}"></rect>` +
        `<path d="M${x + 2} ${y + 3.4} H${x + w - 2}" stroke="${edge}" class="w1"></path>` +
        `<rect x="${x + 4}" y="${y + 6}" width="${w - 8}" height="2" rx="1" fill="#FFFFFF" style="fill-opacity: .7"></rect>` +
        `<path d="M${x - 3} ${y + 3} V${y + h - 1} M${x + w + 3} ${y + 3} V${y + h - 1}" stroke="#9AA6A0" class="w2"></path></g>`;
    }
    const top = base - Math.min(n, full) * h - Math.max(0, n - full) * h;
    const lidAt = 10 + 50 + 4;
    const lidK = ctx.anim(`0%,${f1(lidAt)}%{opacity:0;transform:translateY(-30px)}${f1(lidAt + 4)}%{opacity:1;transform:translateY(1px)}${f1(lidAt + 6)}%,100%{opacity:1;transform:translateY(0)}`,
      { timing: 'ease-in' });
    const lid = `<g class="${lidK}"><path d="M${x} ${top} Q${x} ${top - 8} ${x + w / 2} ${top - 9} Q${x + w} ${top - 8} ${x + w} ${top} Z" fill="#C9D3CD"></path>` +
      `<path d="M${x - 3} ${top + 2} V${top - 12} H${x + w + 3} V${top + 2}" stroke="${c}" class="w25"></path>` +
      `<path d="M${x + w / 2 - 8} ${top - 12} Q${x + w / 2} ${top - 22} ${x + w / 2 + 8} ${top - 12}" stroke="${c}" class="w3"></path></g>`;
    let steam = '';
    [-10, 0, 10].forEach((dx, i) => {
      const ks = ctx.anim('0%,30%{opacity:0;transform:translateY(4px)}50%{opacity:.85}80%,100%{opacity:0;transform:translateY(-10px)}', { delay: i * 0.35 });
      steam += `<path class="w15 ${ks}" d="M${x + w / 2 + dx} ${top - 24} q-3 -4 0 -8 q3 -4 0 -8" stroke="#9AA6A0"></path>`;
    });
    const lineY = base - full * h;
    const guide = `<path d="M${x - 10} ${lineY} H232" stroke="${c}" class="w15 dash"></path>` +
      `<text x="236" y="${lineY - 4}" class="lbl lbl-end" style="${HALO}">${esc(label)}</text>`;
    const shadow = `<ellipse cx="${x + w / 2}" cy="${base + 1.5}" rx="${w / 2 + 6}" ry="2.4" fill="#16211C" class="sh"></ellipse>`;
    const labels = ['Rice', 'Dal', 'Sabzi', 'Roti'].map((t, i) => `<text x="${x - 8}" y="${base - (i + 1) * h + 9}" class="lbl lbl-end">${t}</text>`).join('');
    return shadow + tiers + lid + steam + guide + labels +
      `<text x="14" y="114" class="lbl">${Math.min(n, full)} of ${full} tiers · one per quarter of the budget</text>` + quietNote(ctx, p);
  }

  function coffee(ctx, c, p, label) {
    const x0 = 92, x1 = 152, top = 40, bot = 92, mid = (x0 + x1) / 2;
    const level = bot - (bot - top - 4) * Math.min(p, 1);
    const cup = `<path d="M${x0} ${top} H${x1} L${x1 - 6} ${bot} Q${mid} ${bot + 5} ${x0 + 6} ${bot} Z" fill="#FFFFFF" stroke="${GREY}" class="w2"></path>` +
      `<path d="M${x1 - 1} 50 Q${x1 + 16} 50 ${x1 + 14} 64 Q${x1 + 12} 76 ${x1 - 4} 76" stroke="${GREY}" class="w4"></path>` +
      `<ellipse cx="${mid}" cy="${bot + 4}" rx="44" ry="5" fill="#E7ECE8"></ellipse>`;
    const k = ctx.anim(`0%{transform:translateY(${f1(bot - level)}px)}62%,100%{transform:translateY(0)}`, { timing: 'cubic-bezier(.3,.6,.3,1)' });
    const drink = `<g class="${k}"><path d="M${x0 + 1.5} ${level} H${x1 - 1.5} L${x1 - 6.5} ${bot - 0.5} Q${mid} ${bot + 4} ${x0 + 6.5} ${bot - 0.5} Z" fill="#6F4426"></path>` +
      `<path d="M${x0 + 1.5} ${level} H${x1 - 1.5} L${x1 - 2.4} ${level + 5} H${x0 + 2.4} Z" fill="#C8955F"></path></g>`;
    const foamK = ctx.anim('0%,62%{opacity:0;transform:scale(.4)}70%,100%{opacity:1;transform:scale(1)}', { box: 'fill-box', origin: 'center' });
    const heart = p <= 1 ? `<g class="${foamK}"><path d="M${mid} ${level + 3.6} q-6 -3 -5 -6 q1.4 -3 5 -0.6 q3.6 -2.4 5 0.6 q1 3 -5 6 Z" fill="#F7EBD8"></path></g>` : '';
    let steam = '';
    [-12, 0, 12].forEach((dx, i) => {
      const ks = ctx.anim('0%,40%{opacity:0;transform:translateY(4px)}60%{opacity:.8}90%,100%{opacity:0;transform:translateY(-10px)}', { delay: i * 0.3 });
      steam += `<path class="w15 ${ks}" d="M${mid + dx} ${top - 6} q-3 -4 0 -8 q3 -4 0 -8" stroke="#C9D3CD"></path>`;
    });
    let spill = '';
    if (p > 1) {
      const kd = ctx.anim('0%,62%{opacity:0;transform:scaleY(0)}74%,100%{opacity:1;transform:scaleY(1)}', { box: 'fill-box', origin: 'top' });
      spill = `<g class="${kd}"><path d="M${x1 - 1} ${top} q4 6 2 16 q-1 5 1 10" stroke="${RED}" class="w3"></path>` +
        `<ellipse cx="${x1 + 6}" cy="${bot + 6}" rx="10" ry="2" fill="${RED}" style="fill-opacity: .6"></ellipse></g>`;
    }
    const guide = `<path d="M${x0 - 6} ${top + 4} H232" stroke="${c}" class="w15 dash"></path>` +
      `<text x="236" y="${top}" class="lbl lbl-end" style="${HALO}">${esc(label)}</text>`;
    return cup + drink + heart + steam + spill + guide +
      `<text x="14" y="114" class="lbl">${pyRound(Math.min(p, 1) * 100)}% full · the brim is the budget</text>` + quietNote(ctx, p);
  }

  function table(ctx, c, p, label) {
    const full = 8, n = Math.max(1, pyRound(p * full));
    let tbl = '<path d="M34 60 H214 L236 96 H12 Z" fill="#F4E6D0"></path><path d="M12 96 H236 V100 H12 Z" fill="#D9BE95"></path>' +
      '<path d="M34 60 H214" stroke="#E3CFAE" class="w1"></path>';
    for (let x = 64; x < 214; x += 30) tbl += `<path d="M${x} 60 L${x - 12} 96" stroke="#EBDABD" style="stroke-width: .6"></path>`;
    const spots = [[66, 69], [110, 69], [154, 69], [198, 69], [50, 87], [100, 87], [150, 87], [200, 87], [122, 52], [166, 52]];
    const D = window.SHAPES.dishes;
    let dishes = '';
    for (let i = 0; i < n; i++) {
      const [x, y] = spots[Math.min(i, spots.length - 1)];
      const sc = y > 80 ? 1.75 : (y > 60 ? 1.45 : 1.3);
      const at = 10 + i * (54 / n);
      const k = ctx.anim(`0%,${f1(at)}%{opacity:0;transform:translate(${x + 40}px,${y - 6}px) scale(${sc})}${f1(at + 4)}%{opacity:1;transform:translate(${x - 1}px,${y}px) scale(${sc})}` +
        `${f1(at + 5.5)}%,100%{opacity:1;transform:translate(${x}px,${y}px) scale(${sc})}`, { timing: 'cubic-bezier(.2,.8,.2,1)', box: 'view-box', origin: '0 0' });
      const tag = i >= full ? `<circle cx="8" cy="-9" r="3.2" fill="${RED}"></circle>` : '';
      dishes += `<g class="${k}">${D[i % D.length]}${tag}</g>`;
    }
    const ksteam = ctx.anim('0%,40%{opacity:0;transform:translateY(3px)}60%{opacity:.8}90%,100%{opacity:0;transform:translateY(-8px)}');
    return tbl + dishes + `<path class="w1 ${ksteam}" d="M110 56 q-2 -3 0 -6 q2 -3 0 -6" stroke="#C9D3CD"></path>` +
      `<text x="236" y="36" class="lbl lbl-end">${esc(label)}</text><text x="236" y="48" class="lbl lbl-end">a full table</text>` +
      `<text x="14" y="114" class="lbl">${Math.min(n, full)} of ${full} dishes · one per eighth of the budget</text>` + quietNote(ctx, p);
  }

  const FOOD = { tiffin: tiffin, cafe: coffee, table: table };

  function scFood(ctx, c, p, label, over, order) {
    order = order.filter(([k]) => FOOD[k]);
    if (!order.length) order = [['table', 'Restaurant']];
    const n = order.length, wins = windows(n, 88);
    let out = '', chips = '';
    order.forEach(([kind, text], i) => {
      const [s, e] = wins[i];
      out += `<g class="${actCls(ctx, i, n, s, e, false)}">${FOOD[kind](new Sub(ctx, s, e), c, p, label)}</g>`;
      chips += chip(text, actCls(ctx, i, n, s, e, false));
    });
    if (p > 1) out += `<text x="236" y="21" class="lbl-red ${ctx.appear(30)}" style="${HALO}">${esc(over)}</text>`;
    return out + chips;
  }

  // ---------------------------------------------------------------- Groceries: four bowls side by side
  function scBowls(ctx, c, p, label, over) {
    const items = window.SHAPES.bowl;
    const names = ['Fruit', 'Vegetables', 'Bread', 'Others'];
    const xs = [35, 93, 151, 209];
    const rimY = 66, rx = 26, depth = 20, full = 12;
    const n = Math.max(1, pyRound(p * full));
    const inner = ['#F4D9D2', '#DCEBCF', '#F1E1C8', '#D7E4F2'];
    const band = ['#E07B6A', '#7FB04A', '#C98A48', '#3A86C9'];
    const seats = [[-9, rimY + 3], [9, rimY + 3], [0, rimY - 5], [-8, rimY - 13], [8, rimY - 13], [0, rimY - 21]];
    let backs = '', fronts = '', things = '';
    xs.forEach((x, b) => {
      backs += `<ellipse cx="${x}" cy="${rimY}" rx="${rx}" ry="5.4" fill="${inner[b]}"></ellipse>`;
      const body = `M${x - rx} ${rimY} Q${x - rx + 1} ${rimY + depth} ${x} ${rimY + depth} Q${x + rx - 1} ${rimY + depth} ${x + rx} ${rimY} ` +
        `Q${x} ${rimY + 5.6} ${x - rx} ${rimY} Z`;
      fronts += `<path d="M${x - 10} ${rimY + depth - 0.6} L${x - 8} ${rimY + depth + 3.4} H${x + 8} L${x + 10} ${rimY + depth - 0.6} Z" fill="#DCE3DE"></path>` +
        `<path d="${body}" fill="#FFFFFF" stroke="#C9D3CD" style="stroke-width: .8"></path>` +
        `<path d="M${x - rx + 1.6} ${rimY + 6} Q${x} ${rimY + 12.6} ${x + rx - 1.6} ${rimY + 6}" stroke="${band[b]}" class="w3"></path>` +
        `<path d="M${x - rx + 6} ${rimY + 13} Q${x - 6} ${rimY + 18} ${x + 4} ${rimY + 17.6}" stroke="#EEF2EF" class="w2"></path>` +
        `<text x="${x}" y="99" class="lbl lbl-mid">${names[b]}</text>`;
    });
    for (let i = 0; i < n; i++) {
      const b = i % 4, kIn = Math.floor(i / 4);
      const svg = items[names[b]][kIn % 3];
      const [dx, y] = seats[Math.min(kIn, seats.length - 1)];
      const x = xs[b] + dx;
      const at = 10 + i * (58 / n);
      const k = ctx.anim(`0%,${f1(at)}%{opacity:0;transform:translate(${x}px,${y - 34}px) rotate(-14deg) scale(1.2)}` +
        `${f1(at + 3)}%{opacity:1;transform:translate(${x}px,${f1(y + 1.8)}px) rotate(4deg) scale(1.26,1.12)}` +
        `${f1(at + 5)}%,100%{opacity:1;transform:translate(${x}px,${y}px) rotate(0deg) scale(1.2)}`, { timing: 'ease-in', box: 'view-box', origin: '0 0' });
      const tag = i >= full ? `<circle cx="7" cy="-13" r="3.2" fill="${RED}"></circle>` : '';
      things += `<g class="${k}">${svg}${tag}</g>`;
    }
    const appear = ctx.anim('0%{opacity:0;transform:translateY(8px)}7%,100%{opacity:1;transform:translateY(0)}', { timing: 'ease-out' });
    const line = p > 1 ? `${full} of ${full} · <tspan style="fill: #B3261E; font-weight: 700">${esc(over)}</tspan>`
      : `${n} of ${full} · full bowls = ${esc(label.replace('Budget ', ''))}`;
    return `<g class="${appear}">${backs}</g>${things}<g class="${appear}">${fronts}</g>` +
      `<text x="14" y="114" class="lbl">${line}</text>`;
  }

  // ---------------------------------------------------------------- Shopping: a trolley that fills, one thing at a time
  function trolley(c, W) {
    const tl = [8, -3], tr = [68, -5], br = [61, 21], bl = [15, 21];
    const at = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
    let wires = '';
    for (let i = 1; i < 12; i++) {
      const t = i / 12, a = at(tl, tr, t), b = at(bl, br, t);
      wires += `<path d="M${f1(a[0])} ${f1(a[1])} L${f1(b[0])} ${f1(b[1])}" stroke="#AAB4AE" style="stroke-width: .8"></path>`;
    }
    [0.33, 0.66].forEach((j) => {
      const a = at(tl, bl, j), b = at(tr, br, j);
      wires += `<path d="M${f1(a[0])} ${f1(a[1])} L${f1(b[0])} ${f1(b[1])}" stroke="#AAB4AE" style="stroke-width: .8"></path>`;
    });
    const back = `<path d="M${tl[0]} ${tl[1]} L${tr[0]} ${tr[1]} L${br[0]} ${br[1]} L${bl[0]} ${bl[1]} Z" fill="#FFFFFF" style="fill-opacity: .55"></path>`;
    const far = '<path d="M23 22 L25 30 M63 22 L63 30" stroke="#9AA6A0" class="w15"></path>' +
      '<circle cx="25" cy="30.6" r="3.2" fill="#46524C"></circle><circle cx="63" cy="30.6" r="3.2" fill="#46524C"></circle>';
    const wh = (x) => `<g class="${W}"><circle cx="${x}" cy="31" r="3.4" fill="${TYRE}"></circle><path d="M${x - 2.2} 31 H${x + 2.2}" stroke="#9AA6A0" class="w1"></path></g>`;
    const front = wires +
      `<path d="M${tl[0]} ${tl[1]} L${tr[0]} ${tr[1]} L${br[0]} ${br[1]} L${bl[0]} ${bl[1]} Z" stroke="#5B6770" class="w2" style="fill: none"></path>` +
      `<path d="M${tr[0] - 1} ${tr[1] - 0.6} L${tr[0] + 3} ${tr[1] + 3}" stroke="${c}" class="w3"></path>` +
      `<path d="M1.4 1.6 L${bl[0]} ${bl[1]} L17 26 H59 L${br[0]} ${br[1]} M17 26 L19 30 M59 26 L57 30" stroke="#5B6770" class="w15"></path>` +
      '<path d="M20 25.4 H56" stroke="#AAB4AE" class="w1"></path>' +
      `<path d="M0 0 L${tl[0]} ${tl[1]}" stroke="#5B6770" class="w2"></path>` +
      `<rect x="-2.6" y="-3.4" width="5.2" height="6.8" rx="2" fill="${c}"></rect>` +
      wh(19) + wh(57);
    return [far + back, front];
  }

  function scTrolley(ctx, c, p, label, over) {
    const K = 1.12, gx = 92, gyy = 94 - 34 * K, full = 8;
    const n = Math.max(1, pyRound(p * full));
    const rollEnd = 14.0;
    const lands = Array.from({ length: n }, (_, i) => 20 + i * (52 / n) + 4.2);
    const fr = [`0%{transform:translateX(-190px)}${f1(rollEnd - 2)}%{transform:translateX(3px)}${f1(rollEnd)}%{transform:translateX(0)}`];
    lands.forEach((t) => fr.push(`${f1(t - 0.2)}%{transform:translateX(0) translateY(0)}${f1(t + 0.8)}%{transform:translateX(0) translateY(1.8px)}` +
      `${f1(t + 2.6)}%{transform:translateX(0) translateY(0)}`));
    fr.push('100%{transform:translateX(0) translateY(0)}');
    const bodyK = ctx.anim(fr.join(''), { timing: 'ease-out' });
    const W = ctx.anim(`0%{transform:rotate(0deg)}${f1(rollEnd)}%,100%{transform:rotate(${(190 / (2 * Math.PI * 3.4) * 360).toFixed(0)}deg)}`,
      { timing: 'ease-out', box: 'fill-box', origin: 'center' });
    const [back, front] = trolley(c, W);
    const shapes = window.SHAPES.shop.map((s) => s.split('@C@').join(c));
    const slots = [[23, 14], [33, 14], [43, 14], [53, 14], [26, 3], [36, 3], [46, 3], [56, 3], [30, -9], [42, -9], [54, -9], [40, -19]];
    let items = '', sparks = '';
    for (let i = 0; i < n; i++) {
      const [lx, ly] = slots[Math.min(i, slots.length - 1)];
      const tx = gx + lx * K, ty = gyy + ly * K;
      const t0 = (20 + i * (52 / n)) / 100;
      const side = i % 2 === 0 ? -1 : 1;
      const start = [tx + side * 70, -14];
      const k = flight(ctx, 0, 100, [[t0, start, t0 + 0.042, [tx, ty], [(start[0] + tx) / 2, -26], 0.75, 0.75]], -side * 3);
      const tag = i >= full ? `<circle cx="9" cy="-9" r="4" fill="${RED}"></circle>` : '';
      items += `<g class="${k}">${shapes[i % shapes.length]}${tag}</g>`;
      const a = lands[i];
      const sk = ctx.anim(`0%,${f1(a - 0.5)}%{opacity:0;transform:translate(${f1(tx)}px,${f1(ty - 8)}px) scale(.3)}${f1(a + 0.8)}%{opacity:1;transform:translate(${f1(tx)}px,${f1(ty - 8)}px) scale(1)}` +
        `${f1(a + 4)}%,100%{opacity:0;transform:translate(${f1(tx)}px,${f1(ty - 12)}px) scale(1.3)}`, { box: 'view-box', origin: '0 0' });
      sparks += `<g class="${sk}"><path d="M0 -6 L1.2 -1.2 L6 0 L1.2 1.2 L0 6 L-1.2 1.2 L-6 0 L-1.2 -1.2 Z" fill="${GOLD}"></path></g>`;
    }
    const bx = gx - 2, by = gyy - 12;
    let counts = '';
    for (let i = 0; i < n; i++) {
      const a = lands[i], b = i + 1 < n ? lands[i + 1] : 101;
      const vis = `0%,${f1(a - 0.1)}%{opacity:0}${f1(a)}%{opacity:1}` + (b <= 100 ? `${f1(b - 0.1)}%{opacity:1}${f1(b)}%,100%{opacity:0}` : '100%{opacity:1}');
      counts += `<text x="${bx}" y="${f1(by + 3.6)}" class="lbl lbl-mid ${ctx.anim(vis, { timing: 'step-end' })}" style="fill: #FFFFFF; font-weight: 700; font-size: 10px">${i + 1}</text>`;
    }
    const pulse = ctx.anim(lands.map((t) => `${f1(t - 0.1)}%{transform:scale(1)}${f1(t + 0.9)}%{transform:scale(1.3)}${f1(t + 2.4)}%{transform:scale(1)}`).join('') +
      '100%{transform:scale(1)}', { box: 'fill-box', origin: 'center' });
    const badgeIn = ctx.anim(`0%,${f1(lands[0] - 0.2)}%{opacity:0}${f1(lands[0])}%,100%{opacity:1}`, { timing: 'linear' });
    const badge = `<g class="${badgeIn}"><g class="${pulse}"><circle cx="${bx}" cy="${by}" r="7" fill="${c}" stroke="#FFFFFF" style="stroke-width: 1.4"></circle></g>${counts}</g>`;
    const rim = gyy - 4 * K;
    const cart = `<g class="${bodyK}"><g transform="translate(${gx} ${f1(gyy)}) scale(${K})">${back}</g>${items}` +
      `<g transform="translate(${gx} ${f1(gyy)}) scale(${K})">${front}</g>${badge}</g>`;
    const shadow = `<ellipse cx="${f1(gx + 38 * K)}" cy="95" rx="${f1(34 * K)}" ry="2.4" fill="#16211C" class="sh"></ellipse>`;
    let out = '<path d="M12 95 H236" stroke="#E3E9E5" class="w2"></path>' + shadow + cart + sparks +
      `<path d="M${f1(gx + 8 * K)} ${f1(rim)} H232" stroke="${c}" class="w15 dash"></path>` +
      `<text x="236" y="${f1(rim - 5)}" class="lbl lbl-end">${esc(label)}</text>` +
      `<text x="14" y="112" class="lbl">${Math.min(n, full)} of ${full} items · one per eighth of the budget</text>`;
    if (p > 1) out += `<text x="236" y="21" class="lbl-red ${ctx.appear(74)}">${esc(over)}</text>`;
    return out;
  }

  // ---------------------------------------------------------------- the single pictures
  function scBattery(ctx, c, p, label, over) {
    const w = 150 * Math.min(p, 1), col = p > 1 ? RED : c;
    const k = ctx.anim('0%{transform:scaleX(0)}40%,100%{transform:scaleX(1)}', { box: 'fill-box', origin: '0% 50%' });
    const kb = ctx.anim('0%,100%{opacity:.55}50%{opacity:1}', { dur: 1.2, timing: 'ease-in-out', count: '3' });
    const cells = [78, 116, 154].map((x) => `<path d="M${x} 46 V86" stroke="#FFFFFF" class="w2"></path>`).join('');
    let out = `<rect x="36" y="38" width="164" height="56" rx="10" fill="#FFFFFF" stroke="${TYRE}" class="w3"></rect>` +
      `<rect x="200" y="56" width="9" height="20" rx="3" fill="${TYRE}"></rect>` +
      `<rect class="${k}" x="43" y="45" width="${f1(w)}" height="42" rx="6" fill="${col}"></rect>${cells}` +
      `<path class="w1 ${kb}" d="M122 50 L106 70 H117 L112 84 L130 62 H119 Z" fill="#FFFFFF" stroke="${TYRE}"></path>` +
      `<text x="200" y="28" class="lbl lbl-end">${esc(label)}</text>`;
    if (p > 1) out += `<text x="${XE}" y="16" class="lbl-red ${ctx.appear(44)}">${esc(over)}</text>`;
    return out;
  }

  function scBooks(ctx, c, p, label, over) {
    const full = 8, n = Math.max(1, pyRound(p * full));
    let out = `<path d="M80 34 H244" stroke="${c}" class="w15 dash"></path><text x="244" y="29" class="lbl lbl-end">${esc(label)}</text>` +
      '<path d="M60 98 H200" stroke="#C9D3CD" class="w3"></path>';
    const shades = [c, mix(c, '#000000', 0.25), mix(c, '#FFFFFF', 0.35), mix(c, '#FFFFFF', 0.15)];
    const widths = [58, 52, 62, 48, 56, 60, 50, 54, 58, 52];
    for (let i = 0; i < n; i++) {
      const y = 96.5 - (i + 1) * 8, w = widths[i % widths.length];
      const x = 135 - w / 2 + (i % 2 ? 3 : -2);
      const col = i >= full ? RED : shades[i % 4];
      const at = 3 + i * 4;
      const k = ctx.anim(`0%,${at}%{opacity:0;transform:translateY(-50px)}${at + 1}%{opacity:1}${at + 4}%{transform:translateY(1.5px)}${at + 5}%,100%{opacity:1;transform:translateY(0)}`,
        { timing: 'ease-in' });
      out += `<g class="${k}"><rect x="${f1(x)}" y="${f1(y)}" width="${w}" height="7.4" rx="1.5" fill="${col}"></rect>` +
        `<path d="M${f1(x + 4)} ${f1(y + 3.7)} H${f1(x + w - 4)}" stroke="#FFFFFF" class="w1"></path></g>`;
    }
    out += `<path d="M30 92 L46 70" stroke="${GOLD}" class="w5"></path><path d="M46 70 L49 66" stroke="${TYRE}" class="w3"></path>` +
      '<path d="M29 93.5 L30.5 91" stroke="#E8B560" class="w3"></path>';
    if (p > 1) out += `<text x="${XE}" y="114" class="lbl-red ${ctx.appear(44)}">${esc(over)}</text>`;
    return out;
  }

  function scKey(ctx, c, p, label) {
    const doorX = 210;
    const target = 12 + (doorX - 12 - 14) * Math.min(p, 1);
    const hop = [];
    for (let i = 0; i < 9; i++) {
      const u = i / 8, x = target * u, y = -6 * Math.abs(Math.sin(u * Math.PI * 4));
      hop.push(`${f1(u * 40)}%{transform:translate(${f1(x)}px,${f1(y)}px)}`);
    }
    hop.push(`100%{transform:translate(${f1(target)}px,0px)}`);
    const kk = ctx.anim(hop.join(''), { timing: 'linear' });
    const key = `<g class="${kk}"><g transform="translate(0 84)"><circle cx="6" cy="6" r="5.5" fill="none" stroke="${GOLD_D}" class="w3"></circle>` +
      `<path d="M11.5 6 H26 M21 6 V10 M25 6 V9" stroke="${GOLD_D}" class="w3"></path></g></g>`;
    let house = `<path d="M168 56 L204 26 L240 56 Z" fill="${c}"></path>` +
      `<rect x="176" y="54" width="56" height="44" fill="#FFFFFF" stroke="${c}" class="w2"></rect>` +
      `<rect x="183" y="62" width="14" height="12" rx="1" fill="${mix(c, '#FFFFFF', 0.7)}"></rect>`;
    if (p >= 1) {
      const kw = ctx.anim('0%,40%{opacity:0}46%,100%{opacity:1}');
      house += `<rect class="${kw}" x="183" y="62" width="14" height="12" rx="1" fill="${GOLD}"></rect>`;
      const kd = ctx.anim('0%,40%{transform:scaleX(1)}48%,100%{transform:scaleX(.35)}', { box: 'fill-box', origin: '0% 50%' });
      house += `<rect x="${doorX}" y="70" width="16" height="28" fill="#3B4A57"></rect><rect class="${kd}" x="${doorX}" y="70" width="16" height="28" fill="${mix(c, '#000000', 0.3)}"></rect>`;
    } else {
      house += `<rect x="${doorX}" y="70" width="16" height="28" fill="${mix(c, '#000000', 0.3)}"></rect>`;
    }
    const k = ctx.anim('0%{stroke-dashoffset:100}40%,100%{stroke-dashoffset:0}', { extra: 'stroke-dasharray:100' });
    return `<path class="road" d="M12 ${YG} H${doorX}" stroke="${GREY}"></path>` +
      `<path class="road ${k}" d="M12 ${YG} H${f1(12 + (doorX - 12) * Math.min(p, 1))}" stroke="${c}" pathLength="100"></path>` +
      house + key + `<text x="160" y="40" class="lbl lbl-end">${esc(label)}</text>`;
  }

  function scCoins(ctx, c, p, label, over) {
    const full = 10, n = Math.max(1, pyRound(p * full));
    let out = `<path d="M86 28 H244" stroke="${c}" class="w15 dash"></path><text x="244" y="23" class="lbl lbl-end">${esc(label)}</text>` +
      '<path d="M70 98 H180" stroke="#C9D3CD" class="w3"></path>';
    for (let i = 0; i < n; i++) {
      const y = 91 - i * 7, at = 3 + i * 3.4;
      const [col, edge] = i >= full ? [RED, '#8E2B26'] : [GOLD, GOLD_D];
      const k = ctx.anim(`0%,${f1(at)}%{opacity:0;transform:translateY(-40px)}${f1(at + 1)}%{opacity:1}${f1(at + 3)}%,100%{opacity:1;transform:translateY(0)}`, { timing: 'ease-in' });
      out += `<g class="${k}"><rect x="112" y="${y}" width="40" height="7" rx="3.5" fill="${col}" stroke="${edge}" class="w1"></rect></g>`;
    }
    const month = new Date().toLocaleString('en', { month: 'short' }).toUpperCase();
    let cal = `<rect x="22" y="46" width="44" height="50" rx="5" fill="#FFFFFF" stroke="${c}" class="w2"></rect>` +
      `<rect x="22" y="46" width="44" height="12" rx="5" fill="${c}"></rect><rect x="22" y="52" width="44" height="6" fill="${c}"></rect>` +
      `<text x="44" y="55.5" class="tiny" style="font-size: 7px">${month}</text>`;
    if (p >= 1) {
      const kt = ctx.anim('0%,40%{stroke-dashoffset:100}52%,100%{stroke-dashoffset:0}', { extra: 'stroke-dasharray:100' });
      cal += `<path class="w3 ${kt}" d="M33 76 L41 84 L56 68" stroke="${c}" pathLength="100"></path>`;
    }
    out = cal + out;
    if (p > 1) out += `<text x="${XE}" y="114" class="lbl-red ${ctx.appear(44)}">${esc(over)}</text>`;
    return out;
  }

  function scPlant(ctx, c, p, label, over) {
    const top = 76 - 56 * Math.min(p, 1.1);
    const k = ctx.anim('0%{stroke-dashoffset:100}40%,100%{stroke-dashoffset:0}', { extra: 'stroke-dasharray:100' });
    let leaves = '', y = 68, i = 0;
    while (y > top + 4) {
      const at = 4 + (76 - y) / (76 - top) * 36;
      const side = i % 2 === 0 ? -1 : 1;
      const kl = ctx.anim(`0%,${f1(at)}%{transform:scale(0)}${f1(at + 5)}%,100%{transform:scale(1)}`, { box: 'fill-box', origin: side < 0 ? '100% 100%' : '0% 100%' });
      const d = side < 0 ? `M120 ${y} Q106 ${y} 102 ${y - 12} Q116 ${y - 14} 120 ${y} Z` : `M120 ${y} Q134 ${y} 138 ${y - 12} Q124 ${y - 14} 120 ${y} Z`;
      leaves += `<path class="${kl}" d="${d}" fill="${mix(c, '#FFFFFF', 0.15)}"></path>`;
      y -= 11;
      i += 1;
    }
    let out = `<circle cx="34" cy="30" r="12" fill="${GOLD}" class="sh" style="fill-opacity: .55"></circle>` +
      `<path d="M90 20 H244" stroke="${c}" class="w15 dash"></path><text x="244" y="15" class="lbl lbl-end">${esc(label)}</text>` +
      `<path class="w3 ${k}" d="M120 78 V${f1(top)}" stroke="${mix(c, '#000000', 0.2)}" pathLength="100"></path>` + leaves +
      '<path d="M100 76 H140 L135 98 H105 Z" fill="#C9774A"></path><rect x="97" y="73" width="46" height="6" rx="2" fill="#B5643A"></rect>';
    if (p > 1) out += `<text x="${XE}" y="114" class="lbl-good ${ctx.appear(44)}">${esc(over)}</text>`;
    return out;
  }

  function scAtm(ctx, c, p, label, over) {
    const full = 8, n = Math.max(1, pyRound(p * full));
    let out = `<rect x="16" y="18" width="56" height="80" rx="6" fill="${c}"></rect>` +
      '<rect x="22" y="24" width="44" height="22" rx="3" fill="#7FD3AE"></rect>' +
      Array.from({ length: 6 }, (_, i) => `<rect x="${25 + (i % 3) * 13}" y="${52 + Math.floor(i / 3) * 7}" width="9" height="4.5" rx="1" fill="${mix(c, '#FFFFFF', 0.6)}"></rect>`).join('') +
      `<rect x="58" y="70" width="16" height="4" rx="1" fill="${TYRE}"></rect>` +
      `<path d="M140 ${f1(98 - 8 * 4.2)} H220" stroke="${c}" class="w15 dash"></path>` +
      `<text x="220" y="${f1(98 - 8 * 4.2 - 5)}" class="lbl lbl-end">${esc(label)}</text>` +
      '<path d="M120 98 H200" stroke="#C9D3CD" class="w3"></path>';
    for (let i = 0; i < n; i++) {
      const at = 4 + i * 4.2, ex = 142 + (i % 2) * 3, ey = 92 - i * 4.2, sx = 66, sy = 70;
      const k = ctx.anim(`0%,${f1(at)}%{opacity:0;transform:translate(${sx}px,${sy}px) rotate(-12deg)}${f1(at + 1)}%{opacity:1}` +
        `${f1(at + 5)}%,100%{opacity:1;transform:translate(${ex}px,${f1(ey)}px) rotate(0deg)}`, { timing: 'ease-out', box: 'view-box', origin: '0 0' });
      const col = i >= full ? '#F6D3D0' : '#A8D5B9';
      out += `<g class="${k}"><rect x="0" y="0" width="34" height="5" rx="1" fill="${col}" stroke="#4F8A6B" class="w1"></rect></g>`;
    }
    if (p > 1) out += `<text x="${XE}" y="16" class="lbl-red ${ctx.appear(44)}">${esc(over)}</text>`;
    return out;
  }

  function scTransfer(ctx, c, p, label) {
    const D = mix(c, '#000000', 0.3);
    const k = ctx.anim('0%,6%{opacity:0;transform:translate(60px,58px)}12%{opacity:1}36%{transform:translate(124px,26px)}' +
      '48%{opacity:1;transform:translate(182px,58px)}54%,100%{opacity:0;transform:translate(182px,58px)}', { timing: 'ease-in-out', box: 'view-box', origin: '0 0' });
    const kb = ctx.anim('0%,48%{transform:scale(1)}52%{transform:scale(1.06)}58%,100%{transform:scale(1)}', { box: 'fill-box', origin: 'center' });
    // the two accounts are drawn without bank names: the app doesn't know which two the money moved between
    return `<rect x="14" y="42" width="78" height="48" rx="8" fill="${D}"></rect><rect x="24" y="54" width="14" height="10" rx="2" fill="${GOLD}"></rect>` +
      '<path d="M24 80 H54" stroke="#FFFFFF" class="w2" style="stroke-opacity: .6"></path>' +
      `<g class="${kb}"><rect x="156" y="42" width="78" height="48" rx="8" fill="${c}"></rect><rect x="166" y="54" width="14" height="10" rx="2" fill="${GOLD}"></rect>` +
      '<path d="M166 80 H196" stroke="#FFFFFF" class="w2" style="stroke-opacity: .6"></path></g>' +
      '<path d="M92 60 Q124 18 156 60" stroke="#9AA6A0" class="w2 dots"></path>' +
      `<g class="${k}"><circle cx="0" cy="0" r="8" fill="${GOLD}" stroke="${GOLD_D}" class="w15"></circle>` +
      '<text x="0" y="3" class="tiny" style="font-size: 9px; fill: #6B4A12">₹</text></g>' +
      `<text x="124" y="110" class="lbl lbl-mid">${esc(label)}</text>`;
  }

  function scJar(ctx, c, p, label, over) {
    const h = Math.min(52 * p, 60);
    const kf = ctx.anim('0%{transform:scaleY(0)}40%,100%{transform:scaleY(1)}', { box: 'fill-box', origin: '50% 100%' });
    let coins = '';
    for (let i = 0; i < 3; i++) {
      const kc = ctx.anim(`0%,${4 + i * 8}%{opacity:0;transform:translateY(-30px)}${6 + i * 8}%{opacity:1}${14 + i * 8}%{opacity:1;transform:translateY(30px)}${16 + i * 8}%,100%{opacity:0;transform:translateY(30px)}`,
        { timing: 'ease-in' });
      coins += `<g class="${kc}"><rect x="${116 + (i - 1) * 8}" y="14" width="14" height="5" rx="2.5" fill="${GOLD}" stroke="${GOLD_D}" class="w1"></rect></g>`;
    }
    let out = coins + `<rect class="${kf}" x="102" y="${f1(96 - h)}" width="48" height="${f1(h)}" rx="4" fill="${mix(c, '#FFFFFF', 0.35)}"></rect>` +
      '<rect x="98" y="34" width="56" height="64" rx="10" fill="none" stroke="#9AA6A0" class="w2"></rect>' +
      `<rect x="102" y="26" width="48" height="9" rx="3" fill="${c}"></rect>` +
      '<path d="M106 44 V86" stroke="#FFFFFF" class="w2"></path>' +
      `<path d="M86 44 H244" stroke="${c}" class="w15 dash"></path><text x="244" y="39" class="lbl lbl-end">${esc(label)}</text>`;
    if (p > 1) out += `<text x="${XE}" y="114" class="lbl-red ${ctx.appear(44)}">${esc(over)}</text>`;
    return out;
  }

  function scPiggy(ctx, c, p, label, over) {
    let coins = '';
    for (let i = 0; i < 3; i++) {
      const kc = ctx.anim(`0%,${4 + i * 8}%{opacity:0;transform:translateY(-26px)}${6 + i * 8}%{opacity:1}${14 + i * 8}%{opacity:1;transform:translateY(14px)}${16 + i * 8}%,100%{opacity:0;transform:translateY(14px)}`,
        { timing: 'ease-in' });
      coins += `<g class="${kc}"><circle cx="108" cy="22" r="6" fill="${GOLD}" stroke="${GOLD_D}" class="w1"></circle></g>`;
    }
    const pig = '<ellipse cx="104" cy="72" rx="38" ry="25" fill="#F4A9BA"></ellipse>' +
      '<ellipse cx="142" cy="70" rx="7" ry="9" fill="#EE93A8"></ellipse><circle cx="141" cy="67" r="1.4" fill="#B5576C"></circle><circle cx="141" cy="73" r="1.4" fill="#B5576C"></circle>' +
      '<path d="M118 50 L126 40 L130 54 Z" fill="#EE93A8"></path><circle cx="126" cy="62" r="2.2" fill="#3B2B30"></circle>' +
      '<path d="M80 92 V100 M94 95 V100 M114 95 V100 M128 92 V100" stroke="#EE93A8" class="w6"></path>' +
      '<path d="M66 66 Q58 60 62 54" stroke="#EE93A8" class="w2"></path>' +
      '<rect x="98" y="47" width="20" height="4" rx="2" fill="#B5576C"></rect>';
    const level = 96 - 60 * Math.min(p, 1.25) / 1.25, lineY = 96 - 60 / 1.25;
    const k = ctx.anim('0%{transform:scaleY(0)}40%,100%{transform:scaleY(1)}', { box: 'fill-box', origin: '50% 100%' });
    const meter = `<rect x="196" y="30" width="14" height="66" rx="7" fill="${GREY}"></rect>` +
      `<rect class="${k}" x="196" y="${f1(level)}" width="14" height="${f1(96 - level)}" rx="7" fill="${c}"></rect>` +
      `<path d="M186 ${f1(lineY)} H220" stroke="${TYRE}" class="w15 dash"></path>` +
      `<text x="182" y="${f1(lineY + 3)}" class="lbl lbl-end">${esc(label)}</text>`;
    let out = coins + pig + meter;
    if (p > 1) out += `<text x="${XE}" y="16" class="lbl-good ${ctx.appear(44)}">${esc(over)}</text>`;
    return out;
  }

  // ---------------------------------------------------------------- the scene for a category
  const GROUP_OF = { travel: 'travel', utilities: 'utilities', sports: 'sports', health: 'health', fun: 'fun' };
  const DEFAULT_ORDER = {
    travel: [['flight', 'Flight'], ['train', 'Train'], ['coach', 'Coach']],
    utilities: [['power', 'Electricity'], ['water', 'Water'], ['broadband', 'Broadband'], ['gas', 'Gas']],
    sports: [['cricket', 'Cricket'], ['football', 'Football'], ['badminton', 'Badminton']],
    health: [['running', 'Running'], ['gym', 'Gym'], ['yoga', 'Yoga'], ['meditation', 'Meditation']],
    fun: [['movies', 'Movies'], ['park', 'Amusement park'], ['streaming', 'Streaming']],
  };

  /** Builds the scene for a spec: returns {svg, overlay, css, dur}. Exposed for checking pages and tests. */
  function build(spec) {
    const c = spec.color, p = Math.max(0, spec.p || 0), label = spec.label || '', over = spec.over || '';
    const kind = spec.kind;
    const order = (spec.order && spec.order.length) ? spec.order : (DEFAULT_ORDER[kind] || []);
    let dur = 4, svg = '', overlay = '';
    if (GROUP_OF[kind]) {
      const group = GROUP_OF[kind];
      const list = order.filter(([k]) => window.ACTS[group].acts[k]);
      const use = list.length ? list : DEFAULT_ORDER[kind];
      dur = actsDur(group, use.length);
      const ctx = new Ctx('q', dur);
      svg = exportedActs(ctx, group, use, c, p, label, over, { ground: kind === 'utilities', scale: kind === 'utilities' });
      return { svg, overlay, css: ctx.css.join('\n'), dur };
    }
    const DUR = { transport: 6.5, food: 2.7 * Math.max(1, order.length) / 0.88, groceries: 5, shopping: 6.5 };
    dur = DUR[kind] || 4;
    const ctx = new Ctx('q', dur);
    switch (kind) {
      case 'transport': svg = scTransport(ctx, c, p, label, over, order); break;
      case 'food': svg = scFood(ctx, c, p, label, over, order); break;
      case 'groceries': svg = scBowls(ctx, c, p, label, over); break;
      case 'shopping': svg = scTrolley(ctx, c, p, label, over); break;
      case 'fuel': [svg, overlay] = scFuel(ctx, c, p, `${pyRound(Math.min(p, 9.99) * 100)}%`, spec.spent || 0); break;
      case 'recharge': svg = scBattery(ctx, c, p, label, over); break;
      case 'education': svg = scBooks(ctx, c, p, label, over); break;
      case 'rent': svg = scKey(ctx, c, p, label); break;
      case 'emi': svg = scCoins(ctx, c, p, label, over); break;
      case 'investments': svg = scPlant(ctx, c, p, label, over); break;
      case 'cash': svg = scAtm(ctx, c, p, label, over); break;
      case 'transfers': svg = scTransfer(ctx, c, p, label); break;
      case 'income': svg = scPiggy(ctx, c, p, label, over); break;
      default: svg = scJar(ctx, c, p, label, over);
    }
    return { svg, overlay, css: ctx.css.join('\n'), dur };
  }

  let last = null;

  function fit() {
    const stage = document.getElementById('stage');
    if (stage) stage.style.transform = `scale(${window.innerWidth / 248})`;
  }

  /** Draws the scene into the page and starts it. With still, it shows the finished picture without moving. */
  function renderScene(spec) {
    last = spec;
    const s = build(spec);
    document.getElementById('anim').textContent = s.css;
    const stage = document.getElementById('stage');
    stage.innerHTML = `<svg viewBox="0 0 248 120" width="248" height="120" aria-hidden="true">${s.svg}</svg>${s.overlay}`;
    fit();
    if (spec.still) {
      document.getAnimations().forEach((a) => {
        const t = a.effect && a.effect.getComputedTiming();
        if (t && isFinite(t.endTime)) a.currentTime = t.endTime; else a.pause();
      });
    }
    return s.dur;
  }

  /** Plays the scene again from the start. */
  function replay() {
    if (!last) return;
    const stage = document.getElementById('stage');
    stage.innerHTML = '';
    void stage.offsetWidth; // let the old animations go before the new ones start
    renderScene(last);
  }

  window.addEventListener('resize', fit);
  window.SceneKit = { build, mix, inr, pyRound, mapFrames };
  window.renderScene = renderScene;
  window.replay = replay;
}());
