# The little open-top car the owl drives in the Transport picture (Round 33/35). Used by make_owl_scenes.py.
# A better, smaller car for the Getting around / fuel scene: a rounded little convertible, the owl at the wheel.
CAR = '''<svg class="car" viewBox="0 0 300 130" width="220" height="95" style="position:absolute;left:5px;top:112px;overflow:visible">
<defs>
<linearGradient id="cb" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FF8A4C"/><stop offset=".55" stop-color="#F0602A"/><stop offset="1" stop-color="#C9441B"/></linearGradient>
<linearGradient id="gl" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#E6F4FF" stop-opacity=".9"/><stop offset="1" stop-color="#9CCBF2" stop-opacity=".55"/></linearGradient>
<radialGradient id="rim" cx="40%" cy="35%" r="70%"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#B8C2CC"/></radialGradient>
</defs>
<ellipse cx="150" cy="126" rx="138" ry="6" fill="#000" opacity=".12"/>
<g class="body">
<path d="M232 50 L250 8 Q252 4 256 6 L258 8 L246 50 Z" fill="url(#gl)" stroke="#5E6670" stroke-width="3" stroke-linejoin="round"/>
<path d="M14 80 C14 60 26 50 50 50 L238 50 C262 50 284 58 292 74 C296 82 296 92 294 100 C293 106 288 110 282 110 L20 110 C14 110 10 106 10 100 Z" fill="url(#cb)"/>
<path d="M50 50 L238 50 C252 50 266 53 276 58 L40 58 C42 53 46 50 50 50 Z" fill="#FFB27E" opacity=".7"/>
<path d="M30 66 L280 66" stroke="#FFFFFF" stroke-width="3" opacity=".35" stroke-linecap="round"/>
<path d="M150 56 L150 104" stroke="#B53E17" stroke-width="2.5" opacity=".7"/>
<rect x="160" y="70" width="20" height="5" rx="2.5" fill="#B53E17" opacity=".8"/>
<circle cx="34" cy="74" r="6" fill="#B53E17" opacity=".55"/><circle cx="34" cy="74" r="3" fill="#FFD08A"/>
<path d="M286 70 q8 2 8 12 q-6 2 -12 -2 Z" fill="#FFE68A" stroke="#E0A800" stroke-width="2"/>
<rect x="10" y="68" width="8" height="14" rx="3" fill="#E23B3B"/>
<path d="M8 104 L292 104" stroke="#D9DEE4" stroke-width="6" stroke-linecap="round"/>
<path d="M246 46 q12 -6 18 2 l-6 5 Z" fill="#5E6670"/>
</g>
<circle cx="70" cy="108" r="27" fill="#A9381A"/><circle cx="232" cy="108" r="27" fill="#A9381A"/>
<g class="wheel" style="transform-origin:70px 110px"><circle cx="70" cy="110" r="22" fill="#2B2B2E"/><circle cx="70" cy="110" r="22" fill="none" stroke="#45454A" stroke-width="4" stroke-dasharray="4 4"/><circle cx="70" cy="110" r="12" fill="url(#rim)"/><path d="M70 100 L70 120 M60 110 L80 110" stroke="#8C96A1" stroke-width="3"/><circle cx="70" cy="110" r="3" fill="#5E6670"/></g>
<g class="wheel" style="transform-origin:232px 110px"><circle cx="232" cy="110" r="22" fill="#2B2B2E"/><circle cx="232" cy="110" r="22" fill="none" stroke="#45454A" stroke-width="4" stroke-dasharray="4 4"/><circle cx="232" cy="110" r="12" fill="url(#rim)"/><path d="M232 100 L232 120 M222 110 L242 110" stroke="#8C96A1" stroke-width="3"/><circle cx="232" cy="110" r="3" fill="#5E6670"/></g>
</svg>'''
# steering wheel, held at the tip of the right wing (counter-rotated so it stays upright)
WHEEL_OLD = '<g transform="translate(14 150) rotate(25)"><path d="M40 30 L70 70" stroke="#3A3F46" stroke-width="10" stroke-linecap="round"/><ellipse cx="30" cy="18" rx="22" ry="44" fill="none" stroke="#2B2F35" stroke-width="12" transform="rotate(-20 30 18)"/></g>'
BACK = '''<div class="scenery"><span><img src="art/1f333.svg" alt=""></span><span><img src="art/26fd.svg" alt=""></span><span><img src="art/1f332.svg" alt=""></span><span><img src="art/1f3e0.svg" alt=""></span><span><img src="art/1f333.svg" alt=""></span><span><img src="art/26fd.svg" alt=""></span><span><img src="art/1f332.svg" alt=""></span><span><img src="art/1f3e0.svg" alt=""></span></div>
<div class="speed"><i></i><i></i><i></i></div>'''
CSS = '''
.k-go{overflow:hidden;border-radius:18px}
.k-go .ob{left:34px;top:18px;width:140px;height:147px;animation:bump2 .45s ease-in-out infinite alternate;animation-delay:-.12s}
.k-go .ob .owl{animation:none}
.k-go .car{animation:bump2 .45s ease-in-out infinite alternate}
.k-go .car .wheel{animation:spin .45s linear infinite}
@keyframes bump2{from{transform:translateY(0)}to{transform:translateY(-3px)}}
.k-go .road{top:212px;height:5px;background:repeating-linear-gradient(90deg,#C9B597 0 22px,transparent 22px 40px);animation:road .45s linear infinite}
@keyframes road{to{background-position:-40px 0}}
.k-go .ground{position:absolute;left:0;right:0;top:204px;bottom:0;background:#EADCC4}
.scenery{position:absolute;top:150px;left:0;white-space:nowrap;font-size:34px;opacity:.55;animation:scen 4s linear infinite}
.scenery span{display:inline-block;width:80px}
@keyframes scen{to{transform:translateX(-320px)}}
.speed i{position:absolute;left:-30px;height:3px;border-radius:2px;background:#C9B597;animation:zip .9s linear infinite}
.speed i:nth-child(1){top:150px;width:34px}.speed i:nth-child(2){top:172px;width:22px;animation-delay:-.3s}.speed i:nth-child(3){top:190px;width:28px;animation-delay:-.6s}
@keyframes zip{0%{transform:translateX(70px);opacity:0}30%{opacity:1}100%{transform:translateX(-10px);opacity:0}}
.k-go .puff{animation:puff .9s ease-out infinite}
'''

STEER = '''<svg class="steer" viewBox="-30 -30 60 60" width="54" height="54" style="position:absolute;left:77px;top:108px;overflow:visible">
<path d="M0 6 L0 40" stroke="#3A3F46" stroke-width="7" stroke-linecap="round"/>
<circle r="22" fill="none" stroke="#2B2F35" stroke-width="7"/><circle r="22" fill="none" stroke="#575E68" stroke-width="2" transform="translate(-1 -1)"/>
<path d="M-22 2 L-6 2 M6 2 L22 2 M0 8 L0 22" stroke="#2B2F35" stroke-width="6" stroke-linecap="round"/>
<circle r="8" fill="#3A3F46"/><circle r="4" fill="#FF9A1F"/></svg>'''
CSS += '''
.k-go .ob{top:36px}
.steer{transform-origin:50% 50%;animation:steer 1.8s ease-in-out infinite}
@keyframes steer{0%,100%{transform:rotate(-12deg)}50%{transform:rotate(12deg)}}
.scenery{top:118px;font-size:30px;opacity:.45}
'''
CSS += '''
.k-go .scenery,.k-go .speed,.k-go .ground{z-index:0}
.k-go .ob{z-index:2}.k-go .steer{z-index:3;left:77px;top:104px;width:54px;height:54px}.k-go .road{z-index:1}.k-go .car{z-index:4}.k-go .fx{z-index:5}
'''
