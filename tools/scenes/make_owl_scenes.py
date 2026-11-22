"""Owl Tracker's category pictures with the app's own owl (Round 35, chosen 21 Nov 2026): for each kind of category the
owl acts it out (eats noodles, pushes a trolley, drives the car, holds the pump...) and its face shows the budget.

    python3 tools/scenes/make_owl_scenes.py

Writes app/src/main/assets/scenes/owl-scenes.js, which scene.html uses for the picture's right half (bright.js still
draws the card: its colours, name, amount, bar and chip). The props are Noto Emoji pictures by Google (Apache License
2.0, assets/scenes/art/LICENSE), as SVG files in assets/scenes/art, named by code point (from the noto-emoji
repository's 2D/svg folder, comments and Illustrator leftovers stripped).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
sys.path.insert(0, HERE)
import owl31 as O  # noqa: E402
import owl_car as CAR  # noqa: E402

O.NAVY, O.MID, O.LIGHT, O.PALE, O.BEAK, O.BELLY = "#5A2F14", "#A8612D", "#E2A86C", "#F5D9B4", "#FF9A1F", "#FFF4E2"
ART = os.path.join(ROOT, "app", "src", "main", "assets", "scenes", "art")


def code(e):
    return "_".join(format(ord(ch), "x") for ch in e if ch != "️")


def T(e, x, y, s, cls=""):
    """A prop inside the owl's drawing (owl units), centred on (x, y), s across."""
    assert os.path.exists(os.path.join(ART, code(e) + ".svg")), e
    return f'<image class="{cls}" href="art/{code(e)}.svg" x="{x - s / 2:g}" y="{y - s / 2:g}" width="{s}" height="{s}"/>'


def E(e, cls, x, y, s, extra=""):
    """A prop on the stage (240 x 240), its top left at (x, y), s across."""
    assert os.path.exists(os.path.join(ART, code(e) + ".svg")), e
    return f'<img class="fx {cls}" src="art/{code(e)}.svg" style="left:{x}px;top:{y}px;width:{s}px;height:{s}px;{extra}">'


def arm(angle, item=''):
    """The app owl's far wing as an arm, swung [angle] degrees clockwise from raised; [item] held at its tip, kept upright."""
    held = f'<g transform="translate(950 350) rotate({-angle})">{item}</g>' if item else ''
    return (f'<g class="arm" style="transform-origin:830px 640px;transform:rotate({angle}deg)">'
            f'<path d="M820 600 C900 560 950 470 930 380 C980 420 990 520 950 600 C920 650 870 680 820 690 Z" fill="{O.NAVY}"/>'
            f'<path d="M930 380 C960 440 960 520 925 580" stroke="{O.MID}" stroke-width="14" fill="none" stroke-linecap="round"/>{held}</g>')


SUN = lambda col='#1B1B1B', op='.92': (f'<g class="wear"><circle cx="520" cy="415" r="112" fill="{col}" opacity="{op}"/><circle cx="735" cy="415" r="112" fill="{col}" opacity="{op}"/>'
       f'<path d="M620 400 L640 400" stroke="{col}" stroke-width="18"/><path d="M450 350 l40 -18" stroke="#fff" stroke-width="14" opacity=".55" stroke-linecap="round"/></g>')
CAP = (f'<g class="wear" transform="translate(625 250)"><path d="M-190 0 L0 -80 L190 0 L0 80 Z" fill="#2A2A2A"/>'
       f'<path d="M-110 30 L-110 100 Q0 150 110 100 L110 30 L0 76 Z" fill="#3A3A3A"/><path d="M140 16 L150 140" stroke="#F2B705" stroke-width="10"/><circle cx="150" cy="148" r="18" fill="#F2B705"/></g>')
BAG = lambda col: (f'<g transform="translate(0 40)"><path d="M-34 0 q0 -50 34 -50 q34 0 34 50" stroke="{col}" stroke-width="12" fill="none"/>'
                   f'<rect x="-70" y="0" width="140" height="160" rx="20" fill="{col}"/><circle cx="0" cy="70" r="24" fill="#fff" opacity=".85"/></g>')
# kind: (arm angle, held item, things in front of the owl, wear, things on the stage)
SC = {
 'food': (150, T('🥢',0,0,170,'chop'), T('🍜',760,820,300,'bowl') + T('♨️',760,620,120,'steam'), '', ''),
 'groceries': (105, '', '', '', E('🛒','trolley',118,120,100) + E('🥕','drop1',160,40,30) + E('🍅','drop2',186,30,28)),
 'shopping': (110, '<g transform="translate(0 -110) scale(.8)">' + BAG('#E2559D') + '</g>', T('🛍️',470,860,230,'bag2'), '', ''),
 'transport': None,
 'fuel': (95, T('⛽',40,40,260), '', '', E('💧','fueldrop',212,70,22)),
 'bills': (25, T('💡',0,-40,200,'bulb'), T('🧾',600,780,230,'bill'), '', ''),
 'recharge': (120, T('📱',20,20,220,'phone'), '', '', E('📶','signal',190,14,30) + E('🔋','batt',14,40,30)),
 'entertainment': (150, '', T('🍿',700,800,280,'popcorn'), SUN('#D63B3B','.55'), E('🎬','clap',186,20,38) + E('✨','spark',20,30,26)),
 'sports': (60, T('🏏',30,-30,300,'bat'), '', '', ''),
 'health': (35, T('💊',0,-20,170,'pill'), T('🩺',620,780,250,'steth'), '', E('❤️','heart',16,44,30)),
 'travel': (150, T('🧳',40,60,260,'case'), '', SUN(), E('✈️','plane',-30,10,34)),
 'education': (120, T('✏️',20,0,170,'pencil'), T('📖',640,800,280,'book'), CAP, E('💡','idea',196,4,30)),
 'rent': (90, T('🔑',40,10,190,'key'), '', '', E('🏠','house',168,112,74) + E('✅','tick',206,84,24)),
 'emi': (75, T('🪙',30,20,130,'hold'), '', '', E('📅','cal',184,14,40) + E('🪙','coin1',196,110,24) + E('🐷','piggy',176,160,52)),
 'investments': (100, T('🫗',40,0,190,'pour'), '', '', E('💧','water',204,116,18) + E('🌱','sprout',190,140,52) + E('📈','chart',14,30,34)),
 'cash': (140, T('💵',20,20,200,'note1'), T('💵',620,800,240,'note2'), '', E('💸','fly',170,40,34)),
 'transfers': (60, T('💸',30,-10,200,'throw'), '', '', E('📩','env',186,30,38) + E('➡️','arrow',172,96,26)),
 'income': (10, T('🪙',0,-20,170,'hold'), T('💰',640,820,260,'sack'), '', E('🎉','party',190,10,34) + E('🪙','rain1',40,-20,24) + E('🪙','rain2',130,-30,24)),
 'others': (160, '', '', '', E('📦','box',168,140,60) + E('❔','q',196,12,30)),
 # Metro (1.9.13): the owl taps its metro card as a train glides past
 'metro': (95, T('💳',30,10,170,'tap'), '', '', E('🚇','train',0,150,60) + E('🎫','ticket',190,20,32)),
 # The Kids categories (1.9.12)
 'baby': (120, T('🍼',20,10,200,'bottle'), '', '', E('🧴','lotion',190,140,40) + E('🫧','bubble1',170,40,28) + E('🫧','bubble2',196,80,20)),
 'daycare': (130, T('🎒',20,20,190,'satchel'), '', '', E('🏡','home',160,104,70) + E('⏰','clock',190,20,34)),
 'kids_health': (40, T('🩹',0,-20,170,'plaster'), T('🌡️',610,790,220,'thermo'), '', E('💉','jab',184,120,40) + E('❤️','heart',16,44,30)),
 'toys': (60, T('🪀',20,-10,170,'yoyo'), T('🧸',640,800,280,'teddy'), '', E('🎈','balloon',190,0,46) + E('🪁','kite',10,10,40)),
 'kids_clothes': (55, T('👕',20,0,190,'tee'), '', '', E('👟','shoes',176,160,40) + E('🧢','cap',190,40,36)),
}
ARMS = {  # arm swings: (from, to, seconds)
 'food': (140,160,.6), 'groceries': (100,110,1.8), 'shopping': (104,116,.6), 'fuel': (92,100,1.4), 'bills': (20,32,1.6),
 'recharge': (115,124,1), 'sports': (20,110,1.2), 'health': (30,45,1.2), 'travel': (146,154,1.6), 'education': (115,128,.5),
 'rent': (86,96,1.2), 'emi': (70,82,1.4), 'investments': (96,108,1.4), 'cash': (130,148,.5), 'transfers': (40,100,1.4), 'income': (0,20,.5), 'others': (155,165,2),
 'metro': (88,100,.8),
 'baby': (112,128,1), 'daycare': (126,136,1.4), 'kids_health': (34,48,1.2), 'toys': (50,70,.5), 'kids_clothes': (45,65,.7),
}
CSS = '\n.st{position:relative;width:240px;height:240px}\n.ob{position:absolute;left:28px;top:36px;width:160px;height:168px}\n.road{position:absolute;left:0;right:0}\n.ob svg *{transform-box:view-box}\n.ob .owl{transform-origin:625px 968px}.ob .head{transform-origin:625px 540px}\n.fx{position:absolute;line-height:1;pointer-events:none}\n@keyframes hop{0%,100%{transform:translateY(0)}50%{transform:translateY(-8px)}}\n@keyframes bob{0%,100%{transform:translateY(0)}50%{transform:translateY(-3px)}}\n@keyframes shake{0%,40%,100%{transform:rotate(0)}55%{transform:rotate(-6deg)}70%{transform:rotate(6deg)}85%{transform:rotate(-4deg)}}\n@keyframes tilt{0%,100%{transform:rotate(0)}50%{transform:rotate(-7deg)}}\n.m-happy .owl{animation:hop .8s ease-in-out infinite}\n.m-worried .owl{animation:bob 1.6s ease-in-out infinite}.m-worried .head{animation:tilt 2s ease-in-out infinite}\n.m-sad .owl{animation:bob 2s ease-in-out infinite}.m-sad .head{animation:shake 1.8s ease-in-out infinite}\n.m-look .head{animation:tilt 3s ease-in-out infinite}\n.m-party .owl{animation:hop .5s ease-in-out infinite}\n.sweat{animation:sweat 1.3s ease-in infinite}@keyframes sweat{0%{opacity:0;transform:translateY(0)}20%{opacity:1}100%{opacity:0;transform:translateY(30px)}}\n.alarm{animation:flash .7s steps(1) infinite}@keyframes flash{50%{opacity:0}}\n.k-food .bowl{animation:bob 1.2s ease-in-out infinite}.k-food .chop{animation:chop .6s ease-in-out infinite alternate}\n.ob .chop{transform-box:fill-box;transform-origin:50% 90%}@keyframes chop{from{transform:rotate(-10deg)}to{transform:rotate(12deg) translateY(-6px)}}\n.steam{animation:steam 1.6s ease-out infinite}@keyframes steam{0%{opacity:0;transform:translateY(16px) scale(.6)}40%{opacity:1}100%{opacity:0;transform:translateY(-14px) scale(1.1)}}\n.k-groceries .trolley,.k-groceries .owl{animation:push 1.8s ease-in-out infinite}@keyframes push{50%{transform:translateX(14px)}}\n.drop1,.drop2{animation:drop 1.8s ease-in infinite}.drop2{animation-delay:.9s}@keyframes drop{0%{opacity:0;transform:translate(-8px,-20px) rotate(-40deg)}25%{opacity:1}70%{opacity:1;transform:translate(10px,80px) rotate(20deg)}100%{opacity:0;transform:translate(14px,86px)}}\n.k-shopping .owl{animation:walk .6s ease-in-out infinite alternate}@keyframes walk{from{transform:translateY(0) rotate(-3deg)}to{transform:translateY(-8px) rotate(3deg)}}\n.k-shopping .wingL{animation:swingL .6s ease-in-out infinite alternate}.k-shopping .wingR{animation:swingR .6s ease-in-out infinite alternate}\n@keyframes swingL{from{transform:translate(98px,236px) rotate(-4deg)}to{transform:translate(98px,236px) rotate(10deg)}}\n@keyframes swingR{from{transform:translate(302px,236px) rotate(4deg)}to{transform:translate(302px,236px) rotate(-10deg)}}\n.fueldrop{animation:drop2 1s ease-in infinite}@keyframes drop2{0%{opacity:0;transform:translateY(0)}20%{opacity:1}100%{opacity:0;transform:translateY(50px)}}\n.k-fuel .wingR{animation:fuelR 1.4s ease-in-out infinite}@keyframes fuelR{0%,100%{transform:translate(302px,236px) rotate(-50deg)}50%{transform:translate(302px,236px) rotate(-42deg)}}\n.bulb{animation:flicker 2s steps(1) infinite}@keyframes flicker{0%,100%{opacity:1;filter:drop-shadow(0 0 10px #FFD43B)}35%{opacity:.35;filter:none}40%{opacity:1}55%{opacity:.4}60%{opacity:1}}\n.bill{transform-origin:10% 10%;animation:flutter 1.4s ease-in-out infinite}@keyframes flutter{0%,100%{transform:rotate(-8deg)}50%{transform:rotate(8deg)}}\n.signal{animation:flash 1.2s steps(2) infinite}.batt{animation:bob 1s ease-in-out infinite}\n.k-recharge .wingR{animation:tap 1s ease-in-out infinite}@keyframes tap{0%,100%{transform:translate(302px,236px) rotate(-45deg)}50%{transform:translate(302px,236px) rotate(-38deg)}}\n.popcorn{animation:bob .9s ease-in-out infinite}.clap{transform-origin:20% 80%;animation:clap 1.2s ease-in-out infinite}@keyframes clap{0%,60%,100%{transform:rotate(0)}70%{transform:rotate(-18deg)}80%{transform:rotate(0)}}\n.spark{animation:twinkle 1.4s ease-in-out infinite}@keyframes twinkle{0%,100%{opacity:.2;transform:scale(.6)}50%{opacity:1;transform:scale(1.1)}}\n.k-sports .wingR{animation:swingBat 1.2s cubic-bezier(.5,0,.3,1) infinite}@keyframes swingBat{0%,100%{transform:translate(302px,236px) rotate(-30deg)}35%{transform:translate(302px,236px) rotate(-95deg)}50%{transform:translate(302px,236px) rotate(10deg)}}\n.ball{animation:ball 1.2s linear infinite}@keyframes ball{0%,40%{opacity:1;transform:translate(0,0)}50%{opacity:1;transform:translate(-10px,0)}100%{opacity:0;transform:translate(60px,-140px)}}\n.heart{animation:beat .8s ease-in-out infinite}@keyframes beat{0%,100%{transform:scale(1)}15%{transform:scale(1.25)}30%{transform:scale(1)}45%{transform:scale(1.18)}}\n.pill{animation:toss 1.2s cubic-bezier(.3,0,.5,1) infinite}@keyframes toss{0%,100%{transform:translateY(30px) rotate(0)}50%{transform:translateY(-20px) rotate(200deg)}}\n.steth{animation:bob 1.6s ease-in-out infinite}\n.case{animation:roll 1.6s ease-in-out infinite}@keyframes roll{50%{transform:translateX(12px) rotate(3deg)}}\n.plane{animation:fly 3s linear infinite}@keyframes fly{0%{transform:translate(0,30px)}100%{transform:translate(280px,-10px)}}\n.q{animation:pop 2.4s ease-in-out infinite}@keyframes pop{0%,15%{opacity:0;transform:scale(.3)}30%,85%{opacity:1;transform:scale(1)}100%{opacity:0}}\n.book{animation:bob 1.4s ease-in-out infinite}.pencil{animation:write .5s ease-in-out infinite alternate}@keyframes write{from{transform:rotate(-10deg) translateX(0)}to{transform:rotate(6deg) translateX(8px)}}\n.idea{animation:pop 2s ease-in-out infinite}\n.house{animation:bob 2s ease-in-out infinite}.tick{animation:pop 2s ease-in-out infinite}\n.k-rent .wingR{animation:turn 1.2s ease-in-out infinite}@keyframes turn{0%,100%{transform:translate(302px,236px) rotate(-55deg)}50%{transform:translate(302px,236px) rotate(-47deg)}}\n.coin1{animation:coin 1.4s ease-in infinite}@keyframes coin{0%{opacity:0;transform:translateY(-20px) rotateY(0)}20%{opacity:1}80%{opacity:1;transform:translateY(56px) rotateY(720deg)}100%{opacity:0;transform:translateY(60px) rotateY(720deg)}}\n.piggy{animation:bob 1.4s ease-in-out infinite}.cal{transform-origin:50% 0;animation:flutter 2s ease-in-out infinite}\n.sprout{transform-origin:50% 100%;animation:grow 2.4s ease-out infinite}@keyframes grow{0%{transform:scale(.4)}60%,100%{transform:scale(1.05)}}\n.water{animation:drop2 .9s ease-in infinite}.chart{animation:bob 1.6s ease-in-out infinite}\n.note1{animation:count .5s ease-in-out infinite alternate}.note2{animation:count .5s ease-in-out infinite alternate-reverse}@keyframes count{from{transform:rotate(-12deg)}to{transform:rotate(10deg) translateY(-6px)}}\n.fly{animation:fly2 2s ease-in-out infinite}@keyframes fly2{0%{opacity:0;transform:translate(-20px,30px)}30%{opacity:1}100%{opacity:0;transform:translate(60px,-30px)}}\n.k-transfers .wingR{animation:throw 1.4s ease-in-out infinite}@keyframes throw{0%,100%{transform:translate(302px,236px) rotate(-30deg)}40%{transform:translate(302px,236px) rotate(-110deg)}}\n.env{animation:fly3 1.4s ease-in-out infinite}@keyframes fly3{0%,40%{opacity:0;transform:translate(-60px,60px) scale(.5)}60%{opacity:1}100%{opacity:0;transform:translate(30px,-30px) scale(1)}}\n.arrow{animation:nudge .7s ease-in-out infinite alternate}@keyframes nudge{to{transform:translateX(10px)}}\n.rain1,.rain2{animation:rain 1.2s linear infinite}.rain2{animation-delay:.6s}@keyframes rain{0%{opacity:0;transform:translateY(0)}20%{opacity:1}100%{opacity:0;transform:translateY(150px) rotate(360deg)}}\n.sack{animation:bob .8s ease-in-out infinite}.party{animation:twinkle 1s ease-in-out infinite}\n.box{transform-origin:50% 100%;animation:wob 1.4s ease-in-out infinite}@keyframes wob{0%,100%{transform:rotate(-6deg)}50%{transform:rotate(6deg)}}\n\n.k-transport{overflow:hidden;border-radius:18px}\n.k-transport .ob{left:34px;top:18px;width:140px;height:147px;animation:bump2 .45s ease-in-out infinite alternate;animation-delay:-.12s}\n.k-transport .ob .owl{animation:none}\n.k-transport .car{animation:bump2 .45s ease-in-out infinite alternate}\n.k-transport .car .wheel{animation:spin .45s linear infinite}\n@keyframes bump2{from{transform:translateY(0)}to{transform:translateY(-3px)}}\n.k-transport .road{top:212px;height:5px;background:repeating-linear-gradient(90deg,#C9B597 0 22px,transparent 22px 40px);animation:road .45s linear infinite}\n@keyframes road{to{background-position:-40px 0}}\n.k-transport .ground{position:absolute;left:0;right:0;top:204px;bottom:0;background:#EADCC4}\n.scenery{position:absolute;top:150px;left:0;white-space:nowrap;font-size:34px;opacity:.55;animation:scen 4s linear infinite}\n.scenery span{display:inline-block;width:80px}\n@keyframes scen{to{transform:translateX(-320px)}}\n.speed i{position:absolute;left:-30px;height:3px;border-radius:2px;background:#C9B597;animation:zip .9s linear infinite}\n.speed i:nth-child(1){top:150px;width:34px}.speed i:nth-child(2){top:172px;width:22px;animation-delay:-.3s}.speed i:nth-child(3){top:190px;width:28px;animation-delay:-.6s}\n@keyframes zip{0%{transform:translateX(70px);opacity:0}30%{opacity:1}100%{transform:translateX(-10px);opacity:0}}\n.k-transport .puff{animation:puff .9s ease-out infinite}\n\n.k-transport .ob{top:36px}\n.steer{transform-origin:50% 50%;animation:steer 1.8s ease-in-out infinite}\n@keyframes steer{0%,100%{transform:rotate(-12deg)}50%{transform:rotate(12deg)}}\n.scenery{top:118px;font-size:30px;opacity:.45}\n\n.k-transport .scenery,.k-transport .speed,.k-transport .ground{z-index:0}\n.k-transport .ob{z-index:2}.k-transport .steer{z-index:3;left:77px;top:104px;width:54px;height:54px}.k-transport .road{z-index:1}.k-transport .car{z-index:4}.k-transport .fx{z-index:5}\n\n.k-transport .ob{left:34px;top:36px;width:140px;height:147px}\n.k-transport .ob .owl{animation:none}\n@media (prefers-reduced-motion: reduce){.st *{animation:none!important}}\n.k-food .arm{animation:arm-food 0.6s ease-in-out infinite alternate}@keyframes arm-food{from{transform:rotate(140deg)}to{transform:rotate(160deg)}}.k-groceries .arm{animation:arm-groceries 1.8s ease-in-out infinite alternate}@keyframes arm-groceries{from{transform:rotate(100deg)}to{transform:rotate(110deg)}}.k-shopping .arm{animation:arm-shopping 0.6s ease-in-out infinite alternate}@keyframes arm-shopping{from{transform:rotate(160deg)}to{transform:rotate(172deg)}}.k-fuel .arm{animation:arm-fuel 1.4s ease-in-out infinite alternate}@keyframes arm-fuel{from{transform:rotate(92deg)}to{transform:rotate(100deg)}}.k-bills .arm{animation:arm-bills 1.6s ease-in-out infinite alternate}@keyframes arm-bills{from{transform:rotate(20deg)}to{transform:rotate(32deg)}}.k-recharge .arm{animation:arm-recharge 1s ease-in-out infinite alternate}@keyframes arm-recharge{from{transform:rotate(115deg)}to{transform:rotate(124deg)}}.k-sports .arm{animation:arm-sports 1.2s ease-in-out infinite alternate}@keyframes arm-sports{from{transform:rotate(20deg)}to{transform:rotate(110deg)}}.k-health .arm{animation:arm-health 1.2s ease-in-out infinite alternate}@keyframes arm-health{from{transform:rotate(30deg)}to{transform:rotate(45deg)}}.k-travel .arm{animation:arm-travel 1.6s ease-in-out infinite alternate}@keyframes arm-travel{from{transform:rotate(146deg)}to{transform:rotate(154deg)}}.k-education .arm{animation:arm-education 0.5s ease-in-out infinite alternate}@keyframes arm-education{from{transform:rotate(115deg)}to{transform:rotate(128deg)}}.k-rent .arm{animation:arm-rent 1.2s ease-in-out infinite alternate}@keyframes arm-rent{from{transform:rotate(86deg)}to{transform:rotate(96deg)}}.k-emi .arm{animation:arm-emi 1.4s ease-in-out infinite alternate}@keyframes arm-emi{from{transform:rotate(70deg)}to{transform:rotate(82deg)}}.k-investments .arm{animation:arm-investments 1.4s ease-in-out infinite alternate}@keyframes arm-investments{from{transform:rotate(96deg)}to{transform:rotate(108deg)}}.k-cash .arm{animation:arm-cash 0.5s ease-in-out infinite alternate}@keyframes arm-cash{from{transform:rotate(130deg)}to{transform:rotate(148deg)}}.k-transfers .arm{animation:arm-transfers 1.4s ease-in-out infinite alternate}@keyframes arm-transfers{from{transform:rotate(40deg)}to{transform:rotate(100deg)}}.k-income .arm{animation:arm-income 0.5s ease-in-out infinite alternate}@keyframes arm-income{from{transform:rotate(0deg)}to{transform:rotate(20deg)}}.k-others .arm{animation:arm-others 2s ease-in-out infinite alternate}@keyframes arm-others{from{transform:rotate(155deg)}to{transform:rotate(165deg)}}\n.ob{left:-4px;top:18px;width:212px;height:216px}\n.k-transport .ob{left:22px;top:30px;width:176px;height:180px}\n.bowl{animation:none}\n'


def owl_svg(eyes):
    """The owl with [eyes], with UID for its ids and the kind's own parts marked for the page to fill in."""
    s = O.owl("UID", eyes, "folded", "folded", False, "<!--EXTRA-->")
    return s.replace('<path class="beak"', '<!--WEAR--><path class="beak"', 1)


def main():
    kinds = {}
    for k, v in SC.items():
        if v is None:
            continue
        ang, item, front, wear, stage = v
        kinds[k] = {"extra": arm(ang, item) + front, "wear": wear, "stage": stage}
    car = {
        "stage": '<div class="ground"></div>' + CAR.BACK + '<div class="road"></div>' + CAR.STEER + CAR.CAR + E("💧", "sweat", 150, 40, 16),
    }
    owls = {m: owl_svg(m) for m in ("happy", "worried", "sad", "look")}
    extras = {"worried": E("💧", "sweat", 150, 22, 18), "sad": E("💧", "sweat", 60, 90, 16) + E("❗", "alarm", 10, 24, 22)}
    css = CSS + CAR_CSS + KIDS_CSS
    out = ("// Made by tools/scenes/make_owl_scenes.py: the owl's half of each category picture. Don't edit by hand.\n"
           "window.OWL_SCENES = " + json.dumps({"owls": owls, "kinds": kinds, "car": car, "extras": extras, "css": css}, ensure_ascii=False, separators=(",", ":")) + ";\n")
    with open(os.path.join(ROOT, "app", "src", "main", "assets", "scenes", "owl-scenes.js"), "w", encoding="utf-8") as f:
        f.write(out)
    print(f"owl-scenes.js: {len(out) // 1024} KB, {len(kinds) + 1} kinds")


KIDS_CSS = (
    ".bottle{transform-box:fill-box;transform-origin:50% 100%;animation:sway 1s ease-in-out infinite alternate}"
    "@keyframes sway{from{transform:rotate(-8deg)}to{transform:rotate(8deg)}}"
    ".bubble1,.bubble2{animation:bub 2.4s ease-out infinite}.bubble2{animation-delay:1.2s}"
    "@keyframes bub{0%{opacity:0;transform:translateY(30px) scale(.5)}30%{opacity:1}100%{opacity:0;transform:translateY(-30px) scale(1.2)}}"
    ".lotion,.home{animation:bob 1.8s ease-in-out infinite}"
    ".clock{transform-origin:50% 80%;animation:ring .5s ease-in-out infinite alternate}@keyframes ring{from{transform:rotate(-10deg)}to{transform:rotate(10deg)}}"
    ".jab{animation:bob 1.4s ease-in-out infinite}.thermo{transform-box:fill-box;transform-origin:50% 90%;animation:sway 1.2s ease-in-out infinite alternate}"
    ".yoyo{animation:yoyo .5s ease-in-out infinite alternate}@keyframes yoyo{from{transform:translateY(0)}to{transform:translateY(90px)}}"
    ".teddy{transform-box:fill-box;transform-origin:50% 100%;animation:sway 1.2s ease-in-out infinite alternate}"
    ".balloon{animation:float 2.4s ease-in-out infinite}@keyframes float{0%,100%{transform:translateY(0) rotate(-5deg)}50%{transform:translateY(-10px) rotate(5deg)}}"
    ".kite{animation:kite 3s ease-in-out infinite}@keyframes kite{0%,100%{transform:translate(0,0) rotate(-8deg)}50%{transform:translate(14px,-8px) rotate(10deg)}}"
    ".shoes{animation:bob 1s ease-in-out infinite}.cap{animation:kite 2.6s ease-in-out infinite}"
    ".train{animation:train 2.6s linear infinite}@keyframes train{0%{opacity:0;transform:translateX(-60px)}15%,85%{opacity:1}100%{opacity:0;transform:translateX(220px)}}"
    ".ticket{animation:bob 1.6s ease-in-out infinite}"
)

CAR_CSS = ".k-transport .ob{left:22px;top:30px;width:176px;height:180px}.scenery img{width:30px;height:30px}.st img.fx{display:block}"

if __name__ == "__main__":
    main()
