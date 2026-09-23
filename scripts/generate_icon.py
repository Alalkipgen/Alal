"""Regenerates the Alal launcher, splash and monochrome vectors.

The mark is a spiral notebook with coloured section tabs and a pencil laid
across the cover, drawn in the 108dp adaptive-icon viewport. Run from the
repository root:

    python3 scripts/generate_icon.py
"""

import math
import os

# ---------------------------------------------------------------- palette
SKY_TOP = "#FFDDEEFD"
SKY_BOTTOM = "#FFB6D5F7"

BACK_COVER = "#FFB8CFF2"
BACK_COVER_EDGE = "#FF9CBBE8"
COVER = "#FF2F6FE0"
COVER_SHEEN = "#FF5591F2"
SPINE = "#FF2B3596"
SPINE_SHEEN = "#FF3B41A8"
SPINE_EDGE = "#FF232C80"
PAGE = "#FFFFFFFF"
PAGE_SHADE = "#FFD9E4F5"
RING = "#FFDCE3EC"
RING_SHADE = "#FF9AA7BA"
TABS = ("#FFF9BE4B", "#FFF4705F", "#FF8CDCA9", "#FFC3A4F2")

WOOD = "#FFF7DBA9"
WOOD_DARK = "#FFE0BB84"
BODY = "#FFF2A11E"
BODY_LIGHT = "#FFFFC663"
BODY_DARK = "#FFD1820A"
FERRULE = "#FFDCE1E9"
FERRULE_DARK = "#FFB2BAC7"
ERASER = "#FFFA6F55"
ERASER_DARK = "#FFE05541"
GRAPHITE = "#FF32323C"
SHADOW = "#22101B33"


def f(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def poly(points, close=True):
    d = "M" + " L".join("%s,%s" % (f(x), f(y)) for x, y in points)
    return d + (" Z" if close else "")


def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    x2, y2 = x + w, y + h
    return (
        f"M{f(x + r)},{f(y)} L{f(x2 - r)},{f(y)} A{f(r)},{f(r)} 0 0 1 {f(x2)},{f(y + r)} "
        f"L{f(x2)},{f(y2 - r)} A{f(r)},{f(r)} 0 0 1 {f(x2 - r)},{f(y2)} "
        f"L{f(x + r)},{f(y2)} A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y2 - r)} "
        f"L{f(x)},{f(y + r)} A{f(r)},{f(r)} 0 0 1 {f(x + r)},{f(y)} Z"
    )


def ellipse(cx, cy, a, b):
    return (
        f"M{f(cx - a)},{f(cy)} A{f(a)},{f(b)} 0 1 0 {f(cx + a)},{f(cy)} "
        f"A{f(a)},{f(b)} 0 1 0 {f(cx - a)},{f(cy)} Z"
    )


def ring(cx, cy, rx, ry, thickness):
    """Open ring drawn as an even-odd ellipse pair."""
    return ellipse(cx, cy, rx, ry) + " " + ellipse(cx, cy, rx - thickness, ry - thickness)


def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)


# ---------------------------------------------------------------- notebook
BOOK_X, BOOK_Y, BOOK_W, BOOK_H, BOOK_R = 30.0, 28.0, 44.0, 52.0, 7.0
BOOK_X2, BOOK_Y2 = BOOK_X + BOOK_W, BOOK_Y + BOOK_H
SPINE_W = 12.0

back = rrect(BOOK_X + 2.0, BOOK_Y + 2.2, BOOK_W, BOOK_H, BOOK_R)
book = rrect(BOOK_X, BOOK_Y, BOOK_W, BOOK_H, BOOK_R)

# Cover sheen: the lighter blue wedge that fills the upper right of the cover.
sheen = (
    f"M{f(BOOK_X + SPINE_W)},{f(BOOK_Y)} L{f(BOOK_X2 - BOOK_R)},{f(BOOK_Y)} "
    f"A{f(BOOK_R)},{f(BOOK_R)} 0 0 1 {f(BOOK_X2)},{f(BOOK_Y + BOOK_R)} "
    f"L{f(BOOK_X2)},{f(BOOK_Y + BOOK_H * 0.60)} L{f(BOOK_X + SPINE_W)},{f(BOOK_Y2)} Z"
)

spine = rrect(BOOK_X, BOOK_Y, SPINE_W + BOOK_R, BOOK_H, BOOK_R)
spine_fill = poly([
    (BOOK_X + BOOK_R, BOOK_Y),
    (BOOK_X + SPINE_W, BOOK_Y),
    (BOOK_X + SPINE_W, BOOK_Y2),
    (BOOK_X + BOOK_R, BOOK_Y2),
])
spine_sheen = poly([
    (BOOK_X + BOOK_R, BOOK_Y),
    (BOOK_X + SPINE_W, BOOK_Y),
    (BOOK_X + SPINE_W, BOOK_Y + BOOK_H * 0.42),
    (BOOK_X + BOOK_R, BOOK_Y + BOOK_H * 0.66),
])
spine_edge = rrect(BOOK_X + SPINE_W - 1.3, BOOK_Y, 1.3, BOOK_H, 0.65)

PAGE_H = 8.5
page = rrect(BOOK_X + 3.6, BOOK_Y2 - PAGE_H, BOOK_W - 7.2, PAGE_H, 3.4)
page_shade = rrect(BOOK_X + 3.6, BOOK_Y2 - PAGE_H, BOOK_W - 7.2, 3.2, 1.6)

TAB_TOPS = (33.8, 44.0, 54.2, 64.4)
tabs = [
    (rrect(BOOK_X2 - 6.0, top, 13.0, 9.2, 3.0), TABS[i])
    for i, top in enumerate(TAB_TOPS)
]

RING_CX = BOOK_X + 3.4
RING_YS = (34.5, 44.5, 54.5, 64.5, 74.0)
rings = [ring(RING_CX, y, 5.6, 3.7, 1.7) for y in RING_YS]
ring_shades = [rrect(RING_CX - 1.6, y - 1.1, 5.0, 2.2, 1.1) for y in RING_YS]

# ---------------------------------------------------------------- pencil
TIP = (45.5, 73.0)
END = (69.5, 38.5)
dx, dy = END[0] - TIP[0], END[1] - TIP[1]
L = math.hypot(dx, dy)
ux, uy = dx / L, dy / L
px, py = -uy, ux  # perpendicular, pointing to the lower-right side
HW = 4.5


def P(t, s):
    return (TIP[0] + ux * t + px * s, TIP[1] + uy * t + py * s)


def band(t0, t1, s0, s1):
    return poly([P(t0, s0), P(t1, s0), P(t1, s1), P(t0, s1)])


NIB = 5.6
WOOD_END = 11.5
FERRULE_A, FERRULE_B = L - 9.5, L - 4.8
NIB_HW = HW * NIB / WOOD_END

nib = poly([TIP, P(NIB, NIB_HW), P(NIB, -NIB_HW)])
cone = poly([P(NIB, NIB_HW), P(WOOD_END, HW), P(WOOD_END, -HW), P(NIB, -NIB_HW)])
cone_dark = poly([P(NIB, NIB_HW), P(WOOD_END, HW), P(WOOD_END, HW * 0.45), P(NIB, NIB_HW * 0.45)])

barrel = band(WOOD_END, FERRULE_A, -HW, HW)
barrel_light = band(WOOD_END, FERRULE_A, -HW, -HW * 0.3)
barrel_dark = band(WOOD_END, FERRULE_A, HW * 0.45, HW)

ferrule = band(FERRULE_A, FERRULE_B, -HW, HW)
ferrule_dark = band(FERRULE_A, FERRULE_B, HW * 0.45, HW)

eraser = poly([
    P(FERRULE_B, HW), P(L - 1.5, HW * 0.92), P(L, HW * 0.42),
    P(L, -HW * 0.42), P(L - 1.5, -HW * 0.92), P(FERRULE_B, -HW),
])
eraser_dark = poly([
    P(FERRULE_B, HW), P(L - 1.5, HW * 0.92), P(L, HW * 0.42),
    P(L, HW * 0.2), P(FERRULE_B, HW * 0.45),
])

pencil_outline = poly([
    TIP, P(NIB, NIB_HW), P(WOOD_END, HW), P(FERRULE_B, HW), P(L - 1.5, HW * 0.92),
    P(L, HW * 0.42), P(L, -HW * 0.42), P(L - 1.5, -HW * 0.92), P(FERRULE_B, -HW),
    P(WOOD_END, -HW), P(NIB, -NIB_HW),
])
GAP = 2.3
pencil_halo = poly([
    P(-GAP, 0), P(NIB, NIB_HW + GAP), P(WOOD_END, HW + GAP), P(L + GAP, HW + GAP),
    P(L + GAP, -HW - GAP), P(WOOD_END, -HW - GAP), P(NIB, -NIB_HW - GAP),
])

pencil_shadow = poly([(x + 1.3, y + 1.8) for x, y in [
    TIP, P(NIB, NIB_HW), P(WOOD_END, HW), P(L, HW * 0.42),
    P(L, -HW * 0.42), P(WOOD_END, -HW), P(NIB, -NIB_HW),
]])


# ---------------------------------------------------------------- emit
def path(d, color, even_odd=False):
    extra = ' android:fillType="evenOdd"' if even_odd else ""
    return f'    <path android:fillColor="{color}"{extra} android:pathData="{d}" />'


HEAD = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="108dp"\n    android:height="108dp"\n'
    '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
)


def vector(paths):
    return HEAD + "\n".join(paths) + "\n</vector>\n"


art = (
    [path(d, color) for d, color in tabs]
    + [
        path(back, BACK_COVER),
        path(rrect(BOOK_X + 2.0, BOOK_Y2 - 3.0, BOOK_W, BOOK_H * 0.1 + 3.0, BOOK_R), BACK_COVER_EDGE),
        path(book, COVER),
        path(sheen, COVER_SHEEN),
        path(page, PAGE),
        path(page_shade, PAGE_SHADE),
        path(spine, SPINE),
        path(spine_fill, SPINE),
        path(spine_sheen, SPINE_SHEEN),
        path(spine_edge, SPINE_EDGE),
    ]
    + [path(r, RING, even_odd=True) for r in rings]
    + [path(s, RING_SHADE) for s in ring_shades]
    + [
        path(pencil_shadow, SHADOW),
        path(barrel, BODY),
        path(barrel_light, BODY_LIGHT),
        path(barrel_dark, BODY_DARK),
        path(cone, WOOD),
        path(cone_dark, WOOD_DARK),
        path(nib, GRAPHITE),
        path(ferrule, FERRULE),
        path(ferrule_dark, FERRULE_DARK),
        path(eraser, ERASER),
        path(eraser_dark, ERASER_DARK),
    ]
)

background = (
    HEAD.replace(
        'xmlns:android="http://schemas.android.com/apk/res/android"',
        'xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"',
    )
    + '    <path android:pathData="M0,0h108v108h-108z">\n'
    + '        <aapt:attr name="android:fillColor">\n'
    + '            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="0" android:endY="108">\n'
    + f'                <item android:offset="0" android:color="{SKY_TOP}" />\n'
    + f'                <item android:offset="1" android:color="{SKY_BOTTOM}" />\n'
    + "            </gradient>\n        </aapt:attr>\n    </path>\n</vector>\n"
)

BLACK = "#FF000000"
mono_holes = (
    [book]
    + [ellipse(RING_CX + 2.8, y, 2.4, 1.9) for y in RING_YS]
    + [pencil_halo]
)
mono_paths = [
    path(" ".join(mono_holes), BLACK, even_odd=True),
    path(pencil_outline, BLACK),
]

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
open(os.path.join(RES, "ic_launcher_foreground.xml"), "w").write(vector(art))
open(os.path.join(RES, "ic_launcher_background.xml"), "w").write(background)
open(os.path.join(RES, "ic_launcher_monochrome.xml"), "w").write(vector(mono_paths))
open(os.path.join(RES, "ic_splash.xml"), "w").write(vector([path(circle(54, 54, 54), SKY_TOP)] + art))


def reach(points):
    return max(math.hypot(x - 54.0, y - 54.0) for x, y in points)


def rrect_reach(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    pts = [(x + r, y + r), (x + w - r, y + r), (x + r, y + h - r), (x + w - r, y + h - r)]
    return reach(pts) + r


print("book reach:     %.1f" % rrect_reach(BOOK_X, BOOK_Y, BOOK_W, BOOK_H, BOOK_R))
print("back reach:     %.1f" % rrect_reach(BOOK_X + 2.0, BOOK_Y + 2.2, BOOK_W, BOOK_H, BOOK_R))
print("tab reach:      %.1f" % max(rrect_reach(BOOK_X2 - 6.0, t, 13.0, 9.2, 3.0) for t in TAB_TOPS))
print("ring reach:     %.1f" % max(reach([(RING_CX - 5.6, y), (RING_CX + 5.6, y)]) for y in RING_YS))
print("pencil reach:   %.1f" % reach([TIP, P(L, HW * 0.42), P(L, -HW * 0.42), P(WOOD_END, HW), P(WOOD_END, -HW)]))
print("safe radius is 33 (66dp), mask radius is 36 (72dp)")
