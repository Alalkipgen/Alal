import math

BLUE   = "#1C32A1"
DEEP   = "#16277F"
WHITE  = "#FFFFFF"
PAPER  = "#F7F9FF"
ORANGE = "#FD6818"
ORANGE_D = "#E4530B"
NAVY   = "#1C32A1"

def f(v): return ("%.2f" % v).rstrip("0").rstrip(".")
def pt(p): return "%s,%s" % (f(p[0]), f(p[1]))

def poly(points, close=True):
    d = "M" + pt(points[0]) + " " + " ".join("L" + pt(p) for p in points[1:])
    return d + (" Z" if close else "")

def rrect(x, y, w, h, r):
    """rounded rectangle path"""
    r = min(r, w/2, h/2)
    x2, y2 = x + w, y + h
    return (f"M{f(x+r)},{f(y)} L{f(x2-r)},{f(y)} A{f(r)},{f(r)} 0 0 1 {f(x2)},{f(y+r)} "
            f"L{f(x2)},{f(y2-r)} A{f(r)},{f(r)} 0 0 1 {f(x2-r)},{f(y2)} "
            f"L{f(x+r)},{f(y2)} A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y2-r)} "
            f"L{f(x)},{f(y+r)} A{f(r)},{f(r)} 0 0 1 {f(x+r)},{f(y)} Z")

def circle(cx, cy, r):
    return (f"M{f(cx)},{f(cy)} m{f(-r)},0 a{f(r)},{f(r)} 0 1,0 {f(2*r)},0 "
            f"a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0 Z")

# ---------------------------------------------------------------- document
DOC_X, DOC_Y, DOC_W, DOC_H, DOC_R = 33.0, 29.0, 40.0, 50.0, 6.0
doc = rrect(DOC_X, DOC_Y, DOC_W, DOC_H, DOC_R)

# ruled lines (navy) -------------------------------------------------------
LINES = [  # (x_start, x_end, y_center, thickness)
    (40.0, 66.0, 39.0, 4.0),
    (40.0, 66.0, 48.0, 3.4),
    (40.0, 66.0, 57.0, 3.4),
    (40.0, 57.0, 66.0, 3.4),
]
lines = [rrect(a, y - t/2, b - a, t, t/2) for (a, b, y, t) in LINES]

# ---------------------------------------------------------------- pencil
TIP = (41.5, 76.0)
END = (76.8, 36.6)
dx, dy = END[0]-TIP[0], END[1]-TIP[1]
L = math.hypot(dx, dy)
ux, uy = dx/L, dy/L
px, py = -uy, ux            # perpendicular
HW = 5.6                    # half width
def P(t, s):                # point at distance t along axis, s across
    return (TIP[0] + ux*t + px*s, TIP[1] + uy*t + py*s)

NIB = 8.5                   # graphite nib length
WOOD = 14.0                 # end of the wooden cone
BAND_A, BAND_B = 32.0, 37.0 # metal ferrule band

nib  = poly([TIP, P(NIB, HW*NIB/WOOD), P(NIB, -HW*NIB/WOOD)])
wood = poly([P(NIB, HW*NIB/WOOD), P(WOOD, HW), P(WOOD, -HW), P(NIB, -HW*NIB/WOOD)])
body = poly([P(WOOD, HW), P(L, HW), P(L, -HW), P(WOOD, -HW)])
# rounded-ish end cap on the eraser side
cap  = poly([P(L, HW), P(L+2.2, HW*0.55), P(L+2.2, -HW*0.55), P(L, -HW)])
band = poly([P(BAND_A, HW), P(BAND_B, HW), P(BAND_B, -HW), P(BAND_A, -HW)])
# darker lower facet of the pencil for a bit of depth
facet = poly([P(WOOD, -HW), P(L, -HW), P(L, -HW*0.25), P(WOOD, -HW*0.25)])

def path(d, color, eo=False):
    extra = ' android:fillType="evenOdd"' if eo else ''
    return f'    <path android:fillColor="{color}"{extra} android:pathData="{d}" />'

HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n')

def vector(paths):
    return HEAD + "\n".join(paths) + "\n</vector>\n"

art = [
    path(doc, WHITE),
] + [path(l, NAVY) for l in lines] + [
    path(facet, ORANGE_D),
    path(body, ORANGE),
    path(facet, ORANGE_D),
    path(cap, ORANGE_D),
    path(band, "#FFC14D"),
    path(wood, "#FFD9A0"),
    path(nib, "#22252E"),
]
# rebuild in correct paint order
art = [path(doc, WHITE)] + [path(l, NAVY) for l in lines] + [
    path(body, ORANGE),
    path(facet, ORANGE_D),
    path(cap, ORANGE_D),
    path(band, "#FFC14D"),
    path(wood, "#FFD9A0"),
    path(nib, "#22252E"),
]

foreground = vector(art)
background = vector([path("M0,0h108v108h-108z", BLUE)])
splash = vector([path(circle(54, 54, 54), BLUE)] + art)
mono = vector([path(doc + " " + " ".join(lines), "#000000", eo=True),
               path(body, "#000000"), path(cap, "#000000"),
               path(wood, "#000000"), path(nib, "#000000")])

open("/data/work/alal/res/drawable/ic_launcher_foreground.xml", "w").write(foreground)
open("/data/work/alal/res/drawable/ic_launcher_background.xml", "w").write(background)
open("/data/work/alal/res/drawable/ic_splash.xml", "w").write(splash)
open("/data/work/alal/res/drawable/ic_launcher_monochrome.xml", "w").write(mono)

# ------------------------------------------------------------------ preview
def svg_of(paths_colors, bg):
    s = ['<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108">']
    s.append(f'<rect width="108" height="108" rx="24" fill="{bg}"/>')
    for d, c, eo in paths_colors:
        r = ' fill-rule="evenodd"' if eo else ''
        s.append(f'<path d="{d}" fill="{c}"{r}/>')
    s.append('</svg>')
    return "\n".join(s)

pc = [(doc, WHITE, False)] + [(l, NAVY, False) for l in lines] + [
    (body, ORANGE, False), (facet, ORANGE_D, False), (cap, ORANGE_D, False),
    (band, "#FFC14D", False), (wood, "#FFD9A0", False), (nib, "#22252E", False)]
open("/tmp/alal_preview.svg", "w").write(svg_of(pc, BLUE))

# safe-circle check
import itertools
print("max radius of doc corners:", max(math.hypot(x-54, y-54) for x in (DOC_X, DOC_X+DOC_W) for y in (DOC_Y, DOC_Y+DOC_H)))
for name, p in [("tip", TIP), ("cap1", P(L+2.2, HW*0.55)), ("cap2", P(L+2.2, -HW*0.55)),
                ("end+", P(L, HW)), ("end-", P(L, -HW))]:
    print(name, f(p[0]), f(p[1]), "r=%.1f" % math.hypot(p[0]-54, p[1]-54))
