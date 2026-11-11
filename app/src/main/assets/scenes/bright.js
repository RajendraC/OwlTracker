/*
 * Owl Tracker · the bright category pictures. A line-by-line port of tools/scenes/bright.py (its reference):
 * tools/scenes/check_bright.py runs both on the same specs and compares what they draw.
 *
 * The glossy objects are pre-drawn (window.BRIGHT_ART, from bright-art.js: name -> [defs, markup]); this file only
 * places them, times their animations, and writes the amount, the bar and the chip. Nothing here loads anything.
 */
(function () {
  'use strict';
  var ART = window.BRIGHT_ART;
  var W = 320, H = 180;
  var FONT = 'Plus Jakarta Sans, system-ui, sans-serif';
  var DARK = '#17251D', MUTED = '#4E5A53', RED = '#C8281C';

  var LOOK = {
    groceries: ['#E4F8C6', '#F8FDEE', '#2B6620', '#9BE06A', '#3E9E2E'],
    food: ['#FFDDBE', '#FFF6EC', '#A63A08', '#FFB04D', '#EE5F18'],
    shopping: ['#E9DFFF', '#F9F6FF', '#5A2EC0', '#B9A2FF', '#7552C4'],
    transport: ['#D3EAFF', '#F3F9FF', '#1A589E', '#86C6FF', '#2A6FB5'],
    travel: ['#CDF3EC', '#F1FCFA', '#0B6A62', '#6FE0CF', '#0F9488'],
    health: ['#FFDCE8', '#FFF5F8', '#A81E52', '#FF9DBC', '#E0346A'],
    entertainment: ['#F6DDF6', '#FDF5FD', '#8C2582', '#EBA6E6', '#B04FA5'],
    bills: ['#FFF0BA', '#FFFBEA', '#7A5600', '#FFD966', '#DDA000'],
    sports: ['#FFDDD4', '#FFF6F3', '#B42E24', '#FFA28E', '#E5483A'],
    fuel: ['#FFE6CC', '#FFF8EF', '#8A400A', '#FFC07A', '#E07A1A'],
    recharge: ['#FFE0EE', '#FFF6FA', '#A3195B', '#FF9CC8', '#D63B85'],
    education: ['#DDE6FF', '#F5F8FF', '#2C46A8', '#94AEFF', '#3F5FD9'],
    rent: ['#DCF4E6', '#F4FCF7', '#1F6B45', '#8EE0B0', '#2E9E62'],
    emi: ['#E3E7F2', '#F7F8FC', '#3B4A6B', '#AAB7D6', '#55678F'],
    investments: ['#D7F5E5', '#F2FCF6', '#11704A', '#7EDDB0', '#18A06A'],
    cash: ['#EAF6D5', '#F9FDF1', '#46660E', '#C2E57A', '#7FAE22'],
    transfers: ['#D8F1FA', '#F3FBFE', '#0F6383', '#86D4F0', '#1E9BC8'],
    income: ['#FFF1C9', '#FFFBEE', '#7A5600', '#FFD966', '#DDA000']
  };

  // Python's round(): halves go to the even neighbour
  function pyRound(x) {
    var r = Math.round(x);
    if (Math.abs(x % 1) === 0.5 && r % 2 !== 0) r -= 1;
    return r;
  }
  function f1(v) { return v.toFixed(1).replace(/\.?0+$/, ''); }
  function f2(v) { return v.toFixed(2); }
  function hex2(n) { var s = n.toString(16).toUpperCase(); return s.length < 2 ? '0' + s : s; }
  function mix(a, b, t) {
    var out = '#';
    for (var i = 1; i < 7; i += 2) {
      var x = parseInt(a.substr(i, 2), 16), y = parseInt(b.substr(i, 2), 16);
      out += hex2(pyRound(x + (y - x) * t));
    }
    return out;
  }
  function look(kind, color) {
    if (LOOK[kind]) return LOOK[kind];
    return [mix(color, '#FFFFFF', 0.82), mix(color, '#FFFFFF', 0.95), mix(color, '#000000', 0.4), mix(color, '#FFFFFF', 0.45), color];
  }
  function inr(n) {
    var s = String(pyRound(n));
    var neg = s.charAt(0) === '-';
    if (neg) s = s.substring(1);
    if (s.length > 3) {
      var head = s.substring(0, s.length - 3), tail = s.substring(s.length - 3), parts = [];
      while (head.length > 2) { parts.unshift(head.substring(head.length - 2)); head = head.substring(0, head.length - 2); }
      if (head) parts.unshift(head);
      s = parts.concat([tail]).join(',');
    }
    return (neg ? '-' : '') + '₹' + s;
  }
  function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function rep(n, fn) { var out = ''; for (var i = 0; i < n; i++) out += fn(i); return out; }

  var ENTRIES = {
    drop: ['translateY(-48px) scale(.92)', 0.42, 'translateY(0) scale(1.12,.86)'],
    rise: ['translateY(22px) scale(.85)', 0.38, 'translateY(-3px) scale(.96,1.06)'],
    pop: ['scale(0) rotate(-30deg)', 0.34, 'scale(1.18) rotate(4deg)'],
    drive: ['translateX(-70px)', 0.6, 'translateX(3px) rotate(-2deg)'],
    drive_l: ['translateX(70px)', 0.6, 'translateX(-3px) rotate(2deg)'],
    fade: ['translateY(6px)', 0.4, 'translateY(0)'],
    grow: ['scale(.6)', 0.4, 'scale(1.06)'],
    fly: ['translate(-120px,-70px) scale(.7)', 0.7, 'translateY(0) scale(1.1,.88)'],
    up: ['scaleY(0)', 0.9, 'scaleY(1.04)']
  };
  var IDLES = {
    bob: ['0%,100%{transform:translateY(0)}50%{transform:translateY(-2.2px)}', 2.4, '50% 100%'],
    sway: ['0%,100%{transform:rotate(-3deg)}50%{transform:rotate(3deg)}', 2.8, '50% 100%'],
    pulse: ['0%,100%{transform:scale(1)}12%{transform:scale(1.08)}24%{transform:scale(.98)}36%{transform:scale(1.05)}50%{transform:scale(1)}', 1.6, '50% 70%'],
    bounce: ['0%,100%{transform:translateY(0)}45%{transform:translateY(-7px)}55%{transform:translateY(-7px)}', 1.4, '50% 100%'],
    shake: ['0%,100%{transform:rotate(0)}25%{transform:rotate(-4deg)}75%{transform:rotate(4deg)}', 0.5, '50% 100%'],
    twinkle: ['0%,100%{opacity:0;transform:scale(.4) rotate(0)}50%{opacity:1;transform:scale(1) rotate(45deg)}', 1.9, '50% 50%'],
    steam: ['0%{opacity:0;transform:translateY(2px)}40%{opacity:.85}100%{opacity:0;transform:translateY(-7px)}', 2.2, '50% 100%'],
    drip: ['0%{opacity:0;transform:translateY(0)}20%{opacity:1}100%{opacity:0;transform:translateY(16px)}', 1.2, '50% 100%'],
    glow: ['0%,100%{opacity:.55}50%{opacity:1}', 1.8, '50% 50%']
  };

  function Scene(uid, loop, dur) {
    this.uid = uid; this.loop = loop; this.dur = dur; this.n = 0; this.css = [];
    this.defs = []; this.memo = {}; this.used = [];
  }
  Scene.prototype._def = function (key, make) {
    if (!(key in this.memo)) {
      this.n += 1;
      var i = this.uid + 'd' + this.n;
      this.defs.push(make(i));
      this.memo[key] = 'url(#' + i + ')';
    }
    return this.memo[key];
  };
  Scene.prototype.lin = function (c0, c1, x2, y2) {
    if (x2 === undefined) x2 = 0;
    if (y2 === undefined) y2 = 1;
    return this._def('l' + c0 + c1 + x2 + y2, function (i) {
      return '<linearGradient id="' + i + '" x1="0" y1="0" x2="' + x2 + '" y2="' + y2 + '"><stop offset="0" stop-color="' + c0 + '"></stop>' +
        '<stop offset="1" stop-color="' + c1 + '"></stop></linearGradient>';
    });
  };
  Scene.prototype.blur = function (sd) {
    return this._def('b' + sd, function (i) {
      return '<filter id="' + i + '" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="' + sd + '"></feGaussianBlur></filter>';
    });
  };
  Scene.prototype.art = function (name) {
    if (this.used.indexOf(name) < 0) this.used.push(name);
    return ART[name][1];
  };
  Scene.prototype.pc = function (t) { return Math.max(0, Math.min(100, t / this.dur * 100)); };
  Scene.prototype.key = function (frames, dur, delay, count, timing, origin) {
    this.n += 1;
    var name = this.uid + 'a' + this.n;
    var cnt = count || (this.loop ? 'infinite' : '1');
    this.css.push('.' + name + '{transform-box:fill-box;transform-origin:' + (origin || '50% 100%') + ';' +
      'animation:' + name + ' ' + (dur || this.dur) + 's ' + (timing || 'ease-out') + ' ' + (delay || 0) + 's ' + cnt + ' both}@keyframes ' + name + '{' + frames + '}');
    return name;
  };
  Scene.prototype.end = function (d) {
    if (this.loop) return f2(d + 0.01) + '%,92%{opacity:1;transform:none}97%,100%{opacity:0;transform:none}';
    return f2(d + 0.01) + '%,100%{opacity:1;transform:none}';
  };
  Scene.prototype.enter = function (t, kind, origin) {
    kind = kind || 'drop';
    var e = ENTRIES[kind], frm = e[0], span = e[1], land = e[2];
    var a = this.pc(t - span), b = this.pc(t), c = this.pc(t + 0.15), d = this.pc(t + 0.3);
    var easeIn = (kind === 'drop' || kind === 'fly') ? 'cubic-bezier(.55,0,1,.45)' : 'cubic-bezier(.2,.8,.3,1)';
    var mid = kind === 'fly' ? f2((a + b) / 2) + '%{transform:translate(-60px,-96px) scale(.9)}' : '';
    var squash = (kind === 'drop' || kind === 'rise' || kind === 'fly') ? 'translateY(-2px) scale(.97,1.04)' : 'none';
    var frames = '0%,' + f2(a) + '%{opacity:0;transform:' + frm + ';animation-timing-function:' + easeIn + '}' +
      f2(a + 0.05) + '%{opacity:1}' + mid +
      f2(b) + '%{transform:' + land + ';animation-timing-function:ease-out}' +
      f2(c) + '%{transform:' + squash + '}' +
      f2(d) + '%{transform:none}' + this.end(d);
    return this.key(frames, null, 0, null, null, origin);
  };
  Scene.prototype.idle = function (kind, delay) {
    var i = IDLES[kind];
    return this.key(i[0], i[1], delay, 'infinite', 'ease-in-out', i[2]);
  };

  function g(x, y, s, inner, cls, rot) {
    var tr = 'translate(' + f1(x) + ' ' + f1(y) + ')' + (s !== 1 ? ' scale(' + s + ')' : '') + (rot ? ' rotate(' + rot + ')' : '');
    return '<g transform="' + tr + '"><g class="' + (cls || '') + '">' + inner + '</g></g>';
  }
  function item(sc, x, y, s, inner, t, kind, idle, rot) {
    if (t === null) return g(x, y, s, inner, '', rot);
    var cls = sc.enter(t, kind || 'drop');
    if (idle) inner = '<g class="' + sc.idle(idle, t + 0.5) + '">' + inner + '</g>';
    return g(x, y, s, inner, cls, rot);
  }
  function times(k, start, total) {
    if (start === undefined) start = 0.8;
    if (total === undefined) total = 2.3;
    var step = Math.min(0.34, total / Math.max(k, 1)), out = [];
    for (var i = 0; i < k; i++) out.push(start + i * step);
    return out;
  }
  function count(f, n) { return f > 0 ? Math.max(1, Math.min(n, Math.ceil(f * n - 1e-9))) : 0; }
  function T(still, t) { return still ? null : t; }
  function sparkle(cx, cy, r, color) {
    var d = 'M' + f1(cx) + ',' + f1(cy - r) + ' Q' + f1(cx + r * 0.18) + ',' + f1(cy - r * 0.18) + ' ' + f1(cx + r) + ',' + f1(cy) + ' ' +
      'Q' + f1(cx + r * 0.18) + ',' + f1(cy + r * 0.18) + ' ' + f1(cx) + ',' + f1(cy + r) + ' Q' + f1(cx - r * 0.18) + ',' + f1(cy + r * 0.18) + ' ' + f1(cx - r) + ',' + f1(cy) + ' ' +
      'Q' + f1(cx - r * 0.18) + ',' + f1(cy - r * 0.18) + ' ' + f1(cx) + ',' + f1(cy - r) + ' Z';
    return '<path d="' + d + '" fill="' + color + '"></path>';
  }
  function slots(list, k, fn) { var out = ''; for (var i = 0; i < k; i++) out += fn(list[i], i); return out; }

  // ---------------------------------------------------------------- clusters
  function groceries(sc, f, still) {
    var s = [['lettuce', -10, -32, 1.2, null], ['baguette', -21, -30, 1.0, null], ['milk', 16, -31, 1.12, null],
      ['carrot', 6, -30, 1.05, 'sway'], ['broccoli', -2, -34, 1.05, null], ['apple', -13, -36, 1.0, null],
      ['orange', 13, -37, 1.0, null], ['tomato', 1, -37, 0.95, 'bob']];
    var k = count(f, 8), ts = times(k);
    var inner = slots(s, k, function (e, i) { return item(sc, e[1], e[2], e[3], sc.art(e[0]), T(still, ts[i]), 'rise', still ? null : e[4]); });
    var spill = '';
    if (f > 1) spill = item(sc, -48, 3, 0.8, sc.art('tomato'), T(still, 3.3), 'drop') + item(sc, -66, 3, 0.75, sc.art('orange'), T(still, 3.6), 'drop');
    var bag = sc.art('groc_back') + inner + sc.art('groc_front');
    return item(sc, 240, 164, 1.25, bag + spill, T(still, 0.35), 'grow');
  }

  function food(sc, f, still) {
    var s = [['coffee', -42, -11, 1.0], ['burger', -12, -11, 1.0], ['juice', 17, -11, 0.95], ['kulhad', 43, -11, 0.92],
      ['croissant', -32, -3, 1.0], ['donut', -1, -3, 1.0], ['cake', 28, -3, 0.92], ['macaron', 52, -3, 0.82]];
    var k = count(f, 8), ts = times(k);
    var parts = [sc.art('board')];
    for (var i = 0; i < k; i++) {
      var e = s[i], art = sc.art(e[0]);
      if (e[0] === 'coffee') art = art.split('class="__STEAM__"').join('class="' + (still ? '' : sc.idle('steam', ts[i] + 0.6)) + '"');
      parts.push(item(sc, e[1], e[2], e[3], art, T(still, ts[i]), 'drop'));
    }
    if (f > 1) parts.push(item(sc, 74, 8, 0.8, sc.art('donut'), T(still, 3.4), 'drop', null, -30));
    return item(sc, 230, 162, 1.16, parts.join(''), T(still, 0.3), 'fade');
  }

  function shopping(sc, f, still) {
    var s = [['bag_purple', -28, 0, 1.15], ['bag_pink', 2, 0, 1.15], ['bag_teal', 31, 0, 1.1], ['gift', -52, 10, 0.95],
      ['handbag', -18, 12, 1.0], ['perfume', 12, 12, 0.9], ['sneaker', 42, 12, 0.92], ['bag_yellow', 62, 6, 0.95]];
    var k = count(f, 8), ts = times(k);
    var parts = [sc.art('rug')];
    for (var i = 0; i < k; i++) parts.push(item(sc, s[i][1], s[i][2], s[i][3], sc.art(s[i][0]), T(still, ts[i]), 'drop', (still || i !== 1) ? null : 'bob'));
    if (f > 1) parts.push(item(sc, -76, 14, 0.8, sc.art('bag_orange'), T(still, 3.4), 'drop', null, -20));
    return g(238, 150, 1.14, parts.join(''));
  }

  var TRANSPORT = { bus: [196, 146, 0.86], metro: [272, 146, 0.78], auto: [176, 171, 1.05], taxi: [238, 171, 1.0], scooter: [294, 171, 1.0] };
  var TRANSPORT_PARTS = { car: ['taxi'], scooter: ['auto', 'scooter'], bus: ['bus'], metro: ['metro'] };
  function transportOrder(order) {
    var seq = [];
    (order || []).forEach(function (part) {
      (TRANSPORT_PARTS[part] || []).forEach(function (v) { if (seq.indexOf(v) < 0) seq.push(v); });
    });
    ['bus', 'metro', 'auto', 'taxi', 'scooter'].forEach(function (v) { if (seq.indexOf(v) < 0) seq.push(v); });
    return seq;
  }
  function transport(sc, f, still, spec) {
    var seq = transportOrder(spec.order), k = count(f, 5), ts = times(k, 0.8, 2.0), back = '', front = '';
    for (var i = 0; i < k; i++) {
      var n = seq[i], p = TRANSPORT[n];
      var el = item(sc, p[0], p[1], p[2], sc.art(n), T(still, ts[i]), 'drive', still ? null : 'bob');
      if (p[1] < 150) back += el; else front += el;
    }
    var extra = f > 1 ? item(sc, 296, 120, 0.6, sc.art('utility_flame'), T(still, 3.4), 'pop', still ? null : 'pulse') : '';
    return sc.art('road') + back + front + extra;
  }

  var STICKERS = ['plane', 'palm', 'heart', 'sun', 'globe', 'camera', 'mountain', 'star'];
  function travel(sc, f, still) {
    var spots = [[-16, -52, -12], [8, -56, 10], [19, -40, -8], [-19, -31, 8], [1, -35, -6], [18, -18, 12], [-14, -13, -10], [4, -14, 6]];
    var k = count(f, 8), ts = times(k, 1.0);
    var st = slots(spots, k, function (e, i) { return item(sc, e[0], e[1], 1.0, sc.art('sticker_' + STICKERS[i]), T(still, ts[i]), 'pop', null, e[2]); });
    var c = item(sc, 236, 164, 1.36, sc.art('suitcase') + st, T(still, 0.35), 'drop');
    var planeCls = still ? '' : sc.key(
      '0%{transform:translate(-36px,26px) rotate(-14deg);opacity:0}12%{opacity:1}50%{transform:translate(20px,-2px) rotate(-6deg)}' +
      '86%{opacity:1}100%{transform:translate(64px,-20px) rotate(-12deg);opacity:0}', 4.6, 0.6, 'infinite', 'ease-in-out', '50% 50%');
    var drift = still ? '' : sc.key('0%,100%{transform:translateX(0)}50%{transform:translateX(8px)}', 5, 0, 'infinite', 'ease-in-out', '50% 50%');
    var sky = '<g class="' + drift + '">' + g(176, 54, 0.9, sc.art('cloud')) + g(300, 36, 0.7, sc.art('cloud')) + '</g>' +
      g(196, 46, 1.05, sc.art('plane'), planeCls);
    var extra = f > 1 ? item(sc, 292, 168, 0.9, sc.art('sticker_heart'), T(still, 3.4), 'drop', null, 30) : '';
    return sky + c + extra;
  }

  function health(sc, f, still) {
    var s = [['capsule', 180, 170, 1.15], ['pill_bottle', 288, 150, 1.05], ['dumbbell', 184, 148, 1.0], ['apple85', 264, 173, 1.0],
      ['water_bottle', 306, 152, 0.98], ['avocado', 210, 175, 0.85], ['capsule_pink', 238, 176, 0.98], ['plaster', 297, 175, 0.98]];
    var k = count(f, 8), ts = times(k, 1.0);
    var hrt = item(sc, 240, 158, 1.42, sc.art('heart'), T(still, 0.4), 'pop', still ? null : 'pulse');
    var back = '', front = '';
    for (var i = 0; i < k; i++) if (s[i][2] < 160) back += item(sc, s[i][1], s[i][2], s[i][3], sc.art(s[i][0]), T(still, ts[i]), 'drop');
    for (var j = 0; j < k; j++) if (s[j][2] >= 160) front += item(sc, s[j][1], s[j][2], s[j][3], sc.art(s[j][0]), T(still, ts[j]), 'drop');
    return back + hrt + front;
  }

  var KERNELS = [[-18, -38], [-11, -38], [-4, -38], [3, -38], [10, -38], [17, -38], [-15, -44], [-8, -44], [-1, -44], [6, -44], [13, -44],
    [-11, -50], [-4, -50], [3, -50], [10, -50], [-7, -56], [0, -56], [7, -56], [-3, -61], [4, -61]];
  function entertainment(sc, f, still) {
    var n = KERNELS.length, k = f > 0 ? Math.max(1, Math.min(n, pyRound(f * n))) : 0, step = Math.min(0.12, 2.2 / Math.max(k, 1));
    var kern = slots(KERNELS, k, function (e, i) { return item(sc, e[0], e[1], 1.25, sc.art('kernel'), T(still, 1.0 + i * step), 'rise'); });
    var over = '';
    if (f > 1) over = slots([[-30, 0], [27, 0], [-38, 2], [35, 1]], 4, function (e, i) { return item(sc, e[0], e[1], 1.25, sc.art('kernel'), T(still, 3.3 + i * 0.12), 'drop'); });
    var bucket = sc.art('popcorn_back') + kern + sc.art('popcorn') + over;
    var deco = item(sc, 180, 170, 1.2, sc.art('ticket'), T(still, 0.5), 'drop', still ? null : 'sway') +
      item(sc, 296, 172, 1.1, sc.art('glasses3d'), T(still, 0.7), 'drop');
    var back = item(sc, 298, 140, 1.0, sc.art('clapper'), T(still, 0.6), 'drop');
    return back + item(sc, 238, 166, 1.25, bucket, T(still, 0.3), 'grow') + deco;
  }

  function bills(sc, f, still) {
    var ang = -120 + 240 * Math.min(f, 1.12);
    var gauge = sc.art('gauge');
    if (still) {
      gauge = gauge.split('class="__NEEDLE__"').join('transform="rotate(' + f1(ang) + ')"');
    } else {
      var frames = '0%,' + f2(sc.pc(0.8)) + '%{transform:rotate(-120deg)}' + f2(sc.pc(2.6)) + '%{transform:rotate(' + f1(ang + 8) + 'deg)}' +
        f2(sc.pc(2.9)) + '%{transform:rotate(' + f1(ang - 3) + 'deg)}' + f2(sc.pc(3.1)) + '%,100%{transform:rotate(' + f1(ang) + 'deg)}';
      gauge = gauge.split('class="__NEEDLE__"').join('class="' + sc.key(frames, null, 0, null, 'cubic-bezier(.3,.6,.3,1)') + '"');
    }
    var out = item(sc, 234, 170, 1.28, gauge, T(still, 0.3), 'grow');
    if (f > 1 && !still) out = '<g class="' + sc.idle('shake', 3.2) + '">' + out + '</g>';
    var icons = [['bulb', 176, 74], ['drop', 294, 68], ['flame', 168, 132], ['wifi', 302, 128]];
    var k = count(f, 4), ts = times(k, 1.0, 1.8);
    return out + slots(icons, k, function (e, i) { return item(sc, e[1], e[2], 1.0, sc.art('utility_' + e[0]), T(still, ts[i]), 'pop', still ? null : 'bob'); });
  }

  function sports(sc, f, still) {
    var s = [['bat', 198, 162, 1.4], ['football', 176, 172, 1.3], ['cricket_ball', 220, 173, 1.3], ['racket', 254, 162, 1.3],
      ['shuttle', 240, 174, 1.25], ['paddle', 284, 164, 1.25], ['pickleball', 298, 174, 1.3], ['trophy', 270, 174, 1.25]];
    var k = count(f, 8), ts = times(k), idles = { 1: 'bounce', 2: 'bob', 6: 'bounce' };
    var back = '', front = '';
    for (var i = 0; i < k; i++) if (s[i][2] < 166) back += item(sc, s[i][1], s[i][2], s[i][3], sc.art(s[i][0]), T(still, ts[i]), 'drop', still ? null : (idles[i] || null));
    for (var j = 0; j < k; j++) if (s[j][2] >= 166) front += item(sc, s[j][1], s[j][2], s[j][3], sc.art(s[j][0]), T(still, ts[j]), 'drop', still ? null : (idles[j] || null));
    var turf = item(sc, 236, 176, 1.0, sc.art('grass140'), T(still, 0.3), 'grow');
    return turf + back + front;
  }

  function fuel(sc, f, still) {
    var fc = Math.min(f, 1), h = 26 * fc;
    var liquid = h > 0 ? '<rect class="' + (still ? '' : sc.enter(1.8, 'up')) + '" x="-5" y="' + f1(-11.5 - h) + '" width="10" height="' + f1(h) + '" rx="3" fill="' + sc.lin('#FFD15A', '#FF8A00') + '"></rect>' : '';
    var pump = item(sc, 198, 168, 1.2, sc.art('pump') + liquid, T(still, 0.35), 'grow');
    var car = item(sc, 274, 171, 1.05, '<g transform="scale(-1 1)">' + sc.art('taxi_blue') + '</g>', T(still, 0.9), 'drive_l');
    var drops = still ? '' : rep(3, function (i) { return g(243, 152, 1.1, sc.art('drop_fuel'), sc.idle('drip', 1.4 + i * 0.4)); });
    var over = f > 1 ? item(sc, 262, 174, 1.0, '<ellipse cx="0" cy="0" rx="22" ry="3.4" fill="' + sc.lin('#FFD15A', '#FF8A00') + '" opacity="0.85"></ellipse>', T(still, 3.3), 'grow') : '';
    return over + pump + car + drops;
  }

  function recharge(sc, f, still) {
    var kinds = ['play', 'music', 'cloud', 'chat', 'tv', 'game', 'star', 'wifi'], spots = [];
    [-74, -56, -38, -20].forEach(function (y) { [-9.5, 9.5].forEach(function (x) { spots.push([x, y]); }); });
    var k = count(f, 8), ts = times(k, 1.0);
    var tiles = slots(spots, k, function (e, i) { return item(sc, e[0], e[1] + 7, 1.0, sc.art('app_' + kinds[i]), T(still, ts[i]), 'pop'); });
    var ph = item(sc, 238, 170, 1.15, sc.art('phone') + tiles, T(still, 0.35), 'grow');
    var extra = f > 1 ? item(sc, 296, 96, 1.6, sc.art('app_star'), T(still, 3.4), 'pop', still ? null : 'bob') : '';
    var deco = item(sc, 178, 168, 1.0, sc.art('utility_wifi'), T(still, 0.7), 'pop', still ? null : 'bob');
    return deco + ph + extra;
  }

  var BOOK_DX = [0, 2, -2, 3, -1, 2, -3, 1], BOOK_ROT = [0, -2, 2, -1, 2, -2, 1, -1];
  function education(sc, f, still) {
    var k = count(f, 8), ts = times(k);
    var books = rep(k, function (i) { return item(sc, BOOK_DX[i], -8.4 * i, 1.0, sc.art('book' + i), T(still, ts[i]), 'drop', null, BOOK_ROT[i]); });
    var cap = k === 8 ? item(sc, 0, -8.4 * 8, 1.0, sc.art('grad_cap'), T(still, ts.length ? ts[ts.length - 1] + 0.4 : 3.0), 'pop') : '';
    var stack = g(236, 170, 1.2, '<ellipse cx="0" cy="0" rx="28" ry="5" fill="#3A2412" opacity="0.16"></ellipse>' + books + cap);
    var deco = item(sc, 176, 170, 1.15, sc.art('pencil'), T(still, 0.5), 'drop') + item(sc, 296, 170, 0.85, sc.art('apple'), T(still, 0.65), 'drop');
    var extra = f > 1 ? item(sc, 296, 160, 0.9, sc.art('book0'), T(still, 3.4), 'drop', null, -14) : '';
    return stack + deco + extra;
  }

  var HOUSE_LIGHTS = [[-18, -32, 'win'], [18, -32, 'win'], [-18, -14, 'win'], [18, -14, 'win'], [0, -55, 'round'], [0, -27.5, 'lamp']];
  function rent(sc, f, still) {
    var k = count(f, 6), ts = times(k, 1.0, 1.8);
    var lights = slots(HOUSE_LIGHTS, k, function (e, i) {
      return item(sc, e[0], e[2] === 'win' ? e[1] + 5 : e[1], 1.0, sc.art('light_' + e[2]), T(still, ts[i]), 'fade', still ? null : 'glow');
    });
    var smoke = '';
    if (f > 1 && !still) smoke = rep(2, function (i) { return g(16.5 + i * 3, -74, 1.0, '<circle cx="0" cy="0" r="3.4" fill="#FFFFFF" opacity="0.9"></circle>', sc.idle('steam', 3.2 + i * 0.6)); });
    var house = item(sc, 240, 170, 1.3, sc.art('house') + lights + smoke, T(still, 0.35), 'grow');
    var turf = item(sc, 238, 174, 1.0, sc.art('grass160'), T(still, 0.3), 'grow');
    var tree = item(sc, 176, 170, 1.15, sc.art('tree'), T(still, 0.6), 'grow', still ? null : 'sway');
    return turf + tree + house;
  }

  function emi(sc, f, still) {
    var k = count(f, 8), ts = times(k);
    var coins = rep(k, function (i) { return item(sc, 0, -3.4 * i, 1.0, sc.art('coin_flat'), T(still, ts[i]), 'drop'); });
    var stack = g(274, 170, 1.3, '<ellipse cx="0" cy="0" rx="14" ry="3.2" fill="#3A2412" opacity="0.18"></ellipse>' + coins);
    var cal = item(sc, 198, 170, 1.15, sc.art('calendar'), T(still, 0.35), 'drop');
    var extra = f > 1 ? item(sc, 300, 170, 0.9, sc.art('coin'), T(still, 3.4), 'drop') : '';
    return cal + stack + extra;
  }

  var PLANT_LEAVES = [[-30, 1], [-38, -1], [-46, 1], [-54, -1], [-62, 1], [-70, -1], [-78, 1], [-86, -1]];
  function investments(sc, f, still) {
    var k = count(f, 8), ts = times(k, 1.1, 2.0);
    var top = k ? PLANT_LEAVES[k - 1][0] - 6 : -30;
    var stem = '<path class="' + (still ? '' : sc.enter(0.9, 'up')) + '" d="M0,-26 C3,' + f1((top - 26) / 2) + ' -3,' + f1((top - 26) / 2) + ' 0,' + f1(top) + '" ' +
      'stroke="#2E8A3A" stroke-width="2.8" stroke-linecap="round" fill="none"></path>';
    var leaves = slots(PLANT_LEAVES, k, function (e, i) { return item(sc, 0, e[0], 1.0, sc.art(e[1] > 0 ? 'leaf_r' : 'leaf_l'), T(still, ts[i]), 'pop'); });
    var fruit = k === 8 ? item(sc, 0, top + 2, 0.9, sc.art('coin'), T(still, ts[ts.length - 1] + 0.4), 'pop', still ? null : 'bob') : '';
    var plant = item(sc, 236, 170, 1.2, sc.art('pot') + stem + leaves + fruit, T(still, 0.35), 'grow');
    var deco = item(sc, 292, 172, 1.0, sc.art('coin') + g(12, 2, 0.85, sc.art('coin')), T(still, 0.6), 'drop');
    var extra = f > 1 ? item(sc, 182, 100, 1.1, sc.art('coin'), T(still, 3.4), 'pop', still ? null : 'bob') : '';
    return deco + plant + extra;
  }

  var NOTE_FAN = [[-16, -30, -22], [-8, -33, -12], [0, -35, -2], [8, -33, 8], [16, -30, 18], [-12, -31, -16], [4, -34, 4], [12, -31, 14]];
  function cash(sc, f, still) {
    var k = count(f, 8), ts = times(k);
    var notes = slots(NOTE_FAN, k, function (e, i) { return item(sc, e[0], e[1] + 16, 1.0, sc.art('note'), T(still, ts[i]), 'rise', null, e[2]); });
    var wallet = item(sc, 240, 170, 1.3, sc.art('wallet_back') + notes + sc.art('wallet_front'), T(still, 0.35), 'grow');
    var deco = item(sc, 180, 172, 1.0, sc.art('coin') + g(13, 1, 0.8, sc.art('coin')), T(still, 0.6), 'drop');
    var extra = f > 1 ? item(sc, 296, 96, 0.9, sc.art('note'), T(still, 3.4), 'pop', null, 24) : '';
    return deco + wallet + extra;
  }

  function transfers(sc, f, still) {
    var k = count(f, 8), ts = times(k, 1.1);
    var arc = '<path d="M200,80 Q238,38 276,80" stroke="#0F6383" stroke-width="2" stroke-dasharray="4 5" fill="none" opacity="0.35"></path>';
    var left = item(sc, 182, 110, 1.1, sc.art('avatar_a'), T(still, 0.35), 'pop');
    var right = item(sc, 292, 110, 1.1, sc.art('avatar_b'), T(still, 0.5), 'pop');
    var coins = rep(k, function (i) { return item(sc, 0, -3.4 * i, 1.0, sc.art('coin_flat'), T(still, ts[i]), 'fly'); });
    var stack = g(292, 172, 1.2, '<ellipse cx="0" cy="0" rx="14" ry="3.2" fill="#3A2412" opacity="0.18"></ellipse>' + coins);
    var extra = f > 1 ? item(sc, 252, 172, 0.9, sc.art('coin'), T(still, 3.4), 'drop') : '';
    return arc + left + right + stack + extra;
  }

  var JAR_COINS = [[-10, -3], [10, -3], [0, -12], [-10, -21], [10, -22], [0, -31], [-9, -40], [9, -41]];
  function jar(sc, f, still, spec, front) {
    var k = count(f, 8), ts = times(k);
    var coins = slots(JAR_COINS, k, function (e, i) { return item(sc, e[0], e[1], 1.0, sc.art('coin75'), T(still, ts[i]), 'drop'); });
    var fr = sc.art(front);
    if (front === 'jar_dot') fr = fr.split('#__TINT__').join(spec.color || '#7F8682');
    var spill = '';
    if (f > 1) spill = item(sc, -38, 0, 0.9, sc.art('coin'), T(still, 3.3), 'drop') + item(sc, 36, 0, 0.8, sc.art('coin'), T(still, 3.6), 'drop');
    return item(sc, 240, 170, 1.25, sc.art('jar_back') + coins + fr + spill, T(still, 0.35), 'grow');
  }
  function income(sc, f, still, spec) { return jar(sc, f, still, spec, 'jar_plus'); }
  function others(sc, f, still, spec) { return jar(sc, f, still, spec, 'jar_dot'); }

  var CLUSTER = {
    groceries: groceries, food: food, shopping: shopping, transport: transport, travel: travel, health: health,
    entertainment: entertainment, bills: bills, sports: sports, fuel: fuel, recharge: recharge, education: education,
    rent: rent, emi: emi, investments: investments, cash: cash, transfers: transfers, income: income
  };

  function scene(spec, uid, loop, dur, width) {
    var kind = CLUSTER[spec.kind] ? spec.kind : 'others';
    var lk = look(kind, spec.color || '#7F8682'), top = lk[0], bottom = lk[1], ink = lk[2], b0 = lk[3], b1 = lk[4];
    var sc = new Scene(uid || 's', !!loop, dur || 4.4);
    var f = spec.p, still = !!spec.still, over = !!spec.over;
    var bg = '<rect width="' + W + '" height="' + H + '" fill="' + sc.lin(top, bottom, 1, 1) + '"></rect>' +
      '<circle cx="250" cy="66" r="78" fill="#FFFFFF" opacity="0.5" filter="' + sc.blur(14) + '"></circle>' +
      '<circle cx="40" cy="168" r="46" fill="#FFFFFF" opacity="0.55" filter="' + sc.blur(12) + '"></circle>';
    for (var x = 150; x < 320; x += 14) for (var y = 12; y < 60; y += 14) bg += '<circle cx="' + x + '" cy="' + y + '" r="1.3" fill="' + ink + '" opacity="0.10"></circle>';
    var chip = spec.chip, chipW = 16 + chip.length * 6.3;
    var chipFill = over ? RED : '#FFFFFF', chipInk = over ? '#FFFFFF' : ink;
    var barW = 124, fillW = barW * Math.min(Math.max(f, 0), 1);
    var grow = still ? '' : sc.key('0%,' + f2(sc.pc(0.6)) + '%{transform:scaleX(0)}' + f2(sc.pc(3.0)) + '%{transform:scaleX(1)}' + sc.end(sc.pc(3.0)),
      null, 0, null, 'cubic-bezier(.3,.7,.3,1)', '0% 50%');
    var rise = still ? '' : sc.enter(0.45, 'fade');
    var barFill = over ? sc.lin('#FFA36B', '#E0341E', 1, 0) : sc.lin(b0, b1, 1, 0);
    var name = spec.name.toUpperCase();
    if (name.length > 22) name = name.substring(0, 21).replace(/\s+$/, '') + '…';
    var amount = inr(spec.spent);
    var amountSize = amount.length <= 8 ? 31 : (amount.length <= 10 ? 27 : 23);
    var text = '<g class="' + rise + '">' +
      '<text x="18" y="31" font-family="' + FONT + '" font-size="10.5" font-weight="800" letter-spacing="0.7" fill="' + ink + '">' + esc(name) + '</text>' +
      '<text x="17" y="66" font-family="' + FONT + '" font-size="' + amountSize + '" font-weight="800" letter-spacing="-0.6" fill="' + DARK + '">' + amount + '</text>' +
      '<text x="18" y="84" font-family="' + FONT + '" font-size="11.5" font-weight="600" fill="' + MUTED + '">' + esc(spec.sub) + '</text>' +
      '<rect x="18" y="95" width="' + barW + '" height="12" rx="6" fill="#FFFFFF" stroke="' + ink + '" stroke-opacity="0.14"></rect>' +
      (fillW > 0 ? '<rect class="' + grow + '" x="18" y="95" width="' + f1(fillW) + '" height="12" rx="6" fill="' + barFill + '"></rect>' : '') +
      '<rect x="22" y="97" width="' + f1(Math.max(fillW - 8, 0)) + '" height="3" rx="1.5" fill="#FFFFFF" opacity="0.45"></rect>' +
      '<rect x="18" y="116" width="' + f1(chipW) + '" height="22" rx="11" fill="' + chipFill + '" stroke="' + ink + '" stroke-opacity="' + (over ? 0 : 0.14) + '"></rect>' +
      '<text x="' + f1(18 + chipW / 2) + '" y="131" text-anchor="middle" font-family="' + FONT + '" font-size="11" font-weight="800" fill="' + chipInk + '">' + esc(chip) + '</text>' +
      '</g>';
    var cluster = (CLUSTER[kind] || others)(sc, f, still, spec);
    var sparkles = still ? '' : [[168, 34, 5, '#FFFFFF', 1.2], [306, 98, 4, '#FFFFFF', 2.0], [214, 22, 3.4, '#FFD43B', 2.6]].map(function (e) {
      return '<g class="' + sc.idle('twinkle', e[4]) + '">' + sparkle(e[0], e[1], e[2], e[3]) + '</g>';
    }).join('');
    var defs = sc.defs.join('') + sc.used.map(function (n) { return ART[n][0]; }).join('');
    var wattr = width ? ' width="' + width + '" height="' + pyRound(width * H / W) + '"' : ' width="100%" height="100%"';
    var svg = '<svg viewBox="0 0 ' + W + ' ' + H + '"' + wattr + ' preserveAspectRatio="xMidYMid slice" aria-hidden="true" style="display:block">' +
      '<defs>' + defs + '</defs>' + bg + cluster + sparkles + text + '</svg>';
    return [svg, sc.css.join('\n')];
  }

  window.BrightKit = { scene: scene, inr: inr, pyRound: pyRound, mix: mix };
})();
