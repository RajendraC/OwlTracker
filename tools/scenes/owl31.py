# The owl from your samples, drawn in parts so each part can move: tufts, head, eyes (lids), beak, body, belly,
# near wing, far wing (for waving and flying), feet, branch.
NAVY, MID, LIGHT, PALE, BEAK, BELLY = '#0E2A57', '#5B76A6', '#93A6C6', '#C9D3E6', '#F6A81E', '#FFFFFF'
def owl(u, eyes='open', far='folded', near='folded', branch=True, extra=''):
    # eyes: open | happy (^^) | shut (sleep) | wink | sad (half lids) | worried | look (pupils to one side)
    def eye(cx, cy, kind, side):
        s = f'<circle cx="{cx}" cy="{cy}" r="104" fill="#FFF"/>'
        if kind in ('happy', 'shut'):
            d = f'M{cx-48} {cy+12} Q{cx} {cy-38} {cx+48} {cy+12}' if kind == 'happy' else f'M{cx-48} {cy} Q{cx} {cy+36} {cx+48} {cy}'
            return s + f'<path d="{d}" stroke="{NAVY}" stroke-width="18" fill="none" stroke-linecap="round"/>'
        if kind == 'wink' and side == 'R':
            return s + f'<path d="M{cx-46} {cy+8} Q{cx} {cy-30} {cx+46} {cy+8}" stroke="{NAVY}" stroke-width="18" fill="none" stroke-linecap="round"/>'
        dx = {'look': 26}.get(kind, 0); dy = {'sad': 18, 'worried': 6}.get(kind, 0)
        s += f'<g class="pupil"><circle cx="{cx+dx}" cy="{cy+dy}" r="52" fill="{NAVY}"/><circle cx="{cx+dx+16}" cy="{cy+dy-18}" r="16" fill="#FFF"/></g>'
        if kind == 'sad':
            s += f'<path d="M{cx-110} {cy-110} L{cx+110} {cy-110} L{cx+110} {cy+(-6 if side=="L" else 30)} L{cx-110} {cy+(30 if side=="L" else -6)} Z" fill="{NAVY}" clip-path="url(#e{side}{u})"/>'
        if kind == 'worried':
            s += f'<path d="M{cx-110} {cy-110} L{cx+110} {cy-110} L{cx+110} {cy+(-40 if side=="L" else -20)} L{cx-110} {cy+(-20 if side=="L" else -40)} Z" fill="{NAVY}" clip-path="url(#e{side}{u})"/>'
        return s
    farwing = {
      'folded': '',
      'wave':  f'<g class="farwing"><path d="M820 600 C900 560 950 470 930 380 C980 420 990 520 950 600 C920 650 870 680 820 690 Z" fill="{NAVY}"/><path d="M930 380 C960 440 960 520 925 580" stroke="{MID}" stroke-width="14" fill="none" stroke-linecap="round"/></g>',
      'chin':  f'<g class="farwing"><path d="M850 700 C800 690 730 650 690 580 C680 556 700 540 726 548 C780 566 840 610 880 660 C896 690 876 708 850 700 Z" fill="{NAVY}"/><path d="M712 560 C760 574 820 618 862 670" stroke="{MID}" stroke-width="14" fill="none" stroke-linecap="round"/><path d="M700 552 l-8 -20 M716 548 l-4 -22 M732 552 l2 -22" stroke="{NAVY}" stroke-width="12" stroke-linecap="round"/></g>',
      'spread':f'<g class="farwing"><path d="M820 600 C900 520 990 470 1080 330 C1090 420 1070 500 1030 560 C1060 560 1080 550 1100 540 C1070 610 1010 650 950 670 C970 680 990 680 1010 676 C960 720 890 730 830 710 Z" fill="{NAVY}"/><path d="M1080 330 C1060 420 1010 500 940 560 M1100 540 C1040 600 970 640 900 660" stroke="{MID}" stroke-width="14" fill="none" stroke-linecap="round"/></g>',
    }[far]
    nearwing = {
      'folded': (f'<g class="nearwing"><path d="M345 700 C380 600 470 555 560 565 C630 575 650 650 620 730 C590 810 470 910 385 965 C365 880 330 790 345 700 Z" fill="url(#wg{u})"/>'
                 f'<path d="M378 958 C385 830 440 730 530 650 C480 760 440 860 400 955 Z" fill="{NAVY}"/>'
                 f'<path d="M560 565 C640 590 662 700 615 790 C565 875 470 935 392 966 C480 900 575 822 606 740 C636 660 620 600 560 565 Z" fill="{NAVY}"/></g>'),
      'spread': (f'<g class="nearwing"><path d="M440 600 C360 520 270 470 180 330 C170 420 190 500 230 560 C200 560 180 550 160 540 C190 610 250 650 310 670 C290 680 270 680 250 676 C300 720 370 730 430 710 Z" fill="url(#wg{u})"/>'
                 f'<path d="M180 330 C200 420 250 500 320 560 M160 540 C220 600 290 640 360 660" stroke="{NAVY}" stroke-width="14" fill="none" stroke-linecap="round"/></g>'),
    }[near]
    br = (f'<path class="branch" d="M226 1040 C420 960 600 935 760 935 C880 935 960 965 1025 1005 C930 975 840 960 760 962 C600 965 420 985 226 1040 Z" fill="{NAVY}"/>') if branch else ''
    return f'''<svg viewBox="120 20 1040 1060" width="100%" height="100%" aria-hidden="true" style="overflow:visible">
<defs><linearGradient id="wg{u}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{LIGHT}"/><stop offset="1" stop-color="{MID}"/></linearGradient>
<clipPath id="eL{u}"><circle cx="520" cy="415" r="104"/></clipPath><clipPath id="eR{u}"><circle cx="735" cy="415" r="104"/></clipPath></defs>
{br}
<g class="owl">
<g class="body">
<path d="M412 532 C340 590 318 700 342 800 C356 880 370 940 382 966 L600 940 C700 930 790 900 840 860 C886 800 900 720 884 650 C870 590 846 556 836 538 C760 560 690 540 625 548 C560 540 490 560 412 532 Z" fill="{NAVY}"/>
<path d="M520 560 C640 528 762 545 805 612 C842 690 826 800 764 862 C704 912 622 932 560 942 C602 862 640 760 622 662 C612 612 572 578 520 560 Z" fill="{BELLY}"/>
<path d="M812 650 C832 732 812 828 742 884 C700 912 640 928 590 936 C684 890 766 820 812 650 Z" fill="{PALE}"/>
<g class="feet" fill="{NAVY}"><path d="M528 936 C530 912 560 908 572 922 C584 908 610 912 612 936 L612 968 C604 952 594 950 590 966 C584 950 566 948 560 966 C556 950 540 950 528 968 Z"/><path d="M640 932 C642 908 672 904 684 918 C696 904 722 908 724 932 L724 964 C716 948 706 946 702 962 C696 946 678 944 672 962 C668 946 652 946 640 964 Z"/></g>
{nearwing}
</g>
{farwing}
<g class="head">
<path class="tuftL" d="M330 196 C340 270 400 318 500 296 C470 262 420 226 330 196 Z" fill="{NAVY}"/>
<path class="tuftR" d="M920 196 C910 270 850 318 750 296 C780 262 830 226 920 196 Z" fill="{NAVY}"/>
<path d="M480 262 C540 222 710 222 772 258 C840 280 880 330 888 400 C896 470 870 520 836 540 C760 562 690 536 625 548 C560 536 490 562 412 540 C376 520 350 470 360 400 C368 330 410 280 480 262 Z" fill="{NAVY}"/>
<g class="eyes">{eye(520, 415, eyes, 'L')}{eye(735, 415, eyes, 'R')}</g>
<path d="M440 300 C520 290 590 320 625 420 C660 320 730 290 812 300 C760 270 690 268 625 300 C560 268 492 270 440 300 Z" fill="{NAVY}"/>
<path class="beak" d="M625 430 L660 486 L625 545 L592 486 Z" fill="{BEAK}"/>
</g>
{extra}
</g></svg>'''
