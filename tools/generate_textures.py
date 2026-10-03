#!/usr/bin/env python3
"""
Generates every texture of the Trans Dimension mod as original pixel art.

    pip install pillow
    python3 tools/generate_textures.py

Tweak the palette below and re-run to restyle the whole mod.
"""
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "transdimension")

# --------------------------------------------------------------------------------------------- palette
BLUE = (91, 206, 250)
PINK = (245, 169, 184)
WHITE = (255, 255, 255)
FLAG = [BLUE, PINK, WHITE, PINK, BLUE]
NAVY = (34, 44, 92)
OUTLINE = (52, 38, 74)
WOOD = (139, 97, 66)
WOOD_D = (101, 68, 45)
GREEN = (104, 178, 104)
GREEN_D = (66, 128, 76)
CLEAR = (0, 0, 0, 0)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c[:3])


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def save(img, rel):
    path = os.path.join(OUT, "textures", rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def save_mcmeta(rel, data):
    path = os.path.join(OUT, "textures", rel + ".mcmeta")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def new(w=16, h=16):
    return Image.new("RGBA", (w, h), CLEAR)


def band5(i, n):
    """Index 0..4 of the flag stripe for position i of n."""
    return min(4, i * 5 // n)


def from_ascii(rows, palette):
    img = new(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        assert len(row) == len(rows[0]), (row, len(row))
        for x, ch in enumerate(row):
            if ch in palette:
                img.putpixel((x, y), rgba(palette[ch]))
    return img


def outline(img, colour=OUTLINE):
    """Adds a 1px outline around all opaque pixels (4-neighbourhood)."""
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3]:
                    img.putpixel((x, y), rgba(colour))
                    break
    return img


def bevel(img, light=1.18, dark=0.8):
    """Lightens pixels with an empty pixel above/left and darkens those with one below/right."""
    w, h = img.size
    src = img.copy()

    def empty(x, y):
        return not (0 <= x < w and 0 <= y < h) or src.getpixel((x, y))[3] == 0 or src.getpixel((x, y))[:3] == OUTLINE

    for y in range(h):
        for x in range(w):
            p = src.getpixel((x, y))
            if p[3] == 0 or p[:3] == OUTLINE:
                continue
            if empty(x, y - 1) or empty(x - 1, y):
                img.putpixel((x, y), rgba(shade(p, light), p[3]))
            elif empty(x, y + 1) or empty(x + 1, y):
                img.putpixel((x, y), rgba(shade(p, dark), p[3]))
    return img


# ============================================================================================ blocks
def stone_pixel(rng, x, y, base=(206, 200, 218), tint=0.38):
    band = FLAG[[0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4][y]]
    c = mix(base, band, tint)
    return shade(c, 0.88 + rng.random() * 0.22)


def trans_stone():
    rng = random.Random(1)
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgba(stone_pixel(rng, x, y)))
    for _ in range(14):
        x, y = rng.randrange(16), rng.randrange(16)
        img.putpixel((x, y), rgba(shade(img.getpixel((x, y)), 0.72)))
    return img


def trans_cobblestone():
    rng = random.Random(2)
    seeds = [(rng.randrange(16), rng.randrange(16), rng.choice(FLAG)) for _ in range(10)]

    def nearest(x, y):
        best = None
        for i, (sx, sy, _) in enumerate(seeds):
            dx = min(abs(x - sx), 16 - abs(x - sx))
            dy = min(abs(y - sy), 16 - abs(y - sy))
            d = dx * dx + dy * dy
            if best is None or d < best[0]:
                best = (d, i)
        return best

    img = new()
    for y in range(16):
        for x in range(16):
            d, i = nearest(x, y)
            edge = any(nearest((x + dx) % 16, (y + dy) % 16)[1] != i for dx, dy in ((1, 0), (0, 1)))
            if edge:
                c = (120, 108, 140)
            else:
                c = shade(mix((200, 194, 214), seeds[i][2], 0.45), 1.08 - min(d, 16) * 0.015 + rng.random() * 0.06)
            img.putpixel((x, y), rgba(c))
    return img


def bricks(seed, row_colours, mortar=(150, 138, 168), tint=0.5):
    rng = random.Random(seed)
    img = new()
    for y in range(16):
        row = y // 4
        offset = 0 if row % 2 == 0 else 4
        for x in range(16):
            if y % 4 == 3 or (x + offset) % 8 == 7:
                c = shade(mortar, 0.92 + rng.random() * 0.1)
            else:
                c = mix((206, 198, 214), row_colours[row % len(row_colours)], tint)
                c = shade(c, 0.9 + rng.random() * 0.16)
                if y % 4 == 0 or (x + offset) % 8 == 0:
                    c = shade(c, 1.08)
            img.putpixel((x, y), rgba(c))
    return img


def trans_dirt(seed=3):
    rng = random.Random(seed)
    img = new()
    base = (156, 108, 128)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgba(shade(base, 0.82 + rng.random() * 0.3)))
    for colour, n in ((BLUE, 9), (PINK, 9), (WHITE, 4), ((112, 74, 92), 10)):
        for _ in range(n):
            x, y = rng.randrange(16), rng.randrange(16)
            img.putpixel((x, y), rgba(shade(mix(base, colour, 0.75), 0.9 + rng.random() * 0.15)))
    return img


def grass_colour(x, y, rng):
    k = (x + y) % 16
    band = 0 if k < 3 else 1 if k < 6 else 2 if k < 10 else 3 if k < 13 else 4
    c = FLAG[band]
    if band == 2:
        c = (246, 246, 252)
    return shade(c, 0.8 + rng.random() * 0.26)


def trans_grass_top():
    rng = random.Random(4)
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgba(grass_colour(x, y, rng)))
    for _ in range(18):
        x, y = rng.randrange(16), rng.randrange(16)
        img.putpixel((x, y), rgba(shade(img.getpixel((x, y)), 1.15)))
    return img


def trans_grass_side():
    rng = random.Random(5)
    img = trans_dirt(seed=6)
    depth = [rng.choice((2, 3, 3, 4, 4, 5)) for _ in range(16)]
    for x in range(16):
        stripe = FLAG[band5(x, 16)]
        if stripe == WHITE:
            stripe = (246, 246, 252)
        for y in range(depth[x]):
            img.putpixel((x, y), rgba(shade(stripe, (0.95 if y == 0 else 0.86) + rng.random() * 0.12)))
    return img


def trans_sand():
    rng = random.Random(7)
    img = new()
    base = (250, 234, 240)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgba(shade(base, 0.92 + rng.random() * 0.1)))
    for colour, n in ((PINK, 22), (BLUE, 14), ((255, 255, 255), 10)):
        for _ in range(n):
            x, y = rng.randrange(16), rng.randrange(16)
            img.putpixel((x, y), rgba(shade(mix(base, colour, 0.8), 0.95 + rng.random() * 0.08)))
    return img


GEM = ["..o..", ".oLo.", "oLBPo", ".oPo.", "..o.."]


def stamp_gem(img, gx, gy, pal):
    for dy, row in enumerate(GEM):
        for dx, ch in enumerate(row):
            if ch != ".":
                img.putpixel(((gx + dx) % 16, (gy + dy) % 16), rgba(pal[ch]))


def trans_crystal_ore():
    img = trans_stone()
    pal = {"o": (64, 52, 98), "L": WHITE, "B": BLUE, "P": PINK}
    for gx, gy in ((1, 1), (9, 2), (4, 8), (11, 10)):
        stamp_gem(img, gx, gy, pal)
    return img


def trans_crystal_block():
    img = new()
    rng = random.Random(8)
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            if d == 0:
                c = (58, 120, 178)
            elif d <= 2:
                c = BLUE
            elif d <= 4:
                c = PINK
            elif d <= 6:
                c = WHITE
            else:
                c = PINK
            c = shade(c, 0.95 + rng.random() * 0.08)
            if (x - y) in (-1, 0) and 2 < x < 13:
                c = mix(c, WHITE, 0.6)
            img.putpixel((x, y), rgba(c))
    return img


def trans_log_side():
    rng = random.Random(9)
    img = new()
    streaks = {2: PINK, 3: PINK, 7: BLUE, 11: PINK, 12: PINK, 14: BLUE}
    for x in range(16):
        for y in range(16):
            c = (238, 236, 246)
            if x in streaks:
                c = mix(c, streaks[x], 0.65)
            img.putpixel((x, y), rgba(shade(c, 0.9 + rng.random() * 0.12)))
    for _ in range(10):  # little bark marks
        x, y = rng.randrange(15), rng.randrange(16)
        for dx in range(rng.choice((1, 2, 2, 3))):
            if x + dx < 16:
                img.putpixel((x + dx, y), rgba((96, 92, 128)))
    return img


def trans_log_top():
    img = new()
    rng = random.Random(10)
    ring_colours = [PINK, WHITE, BLUE]
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r > 6.6 or min(x, y, 15 - x, 15 - y) == 0:
                c = shade((236, 234, 244), 0.9 + rng.random() * 0.1)
            elif r < 1.6:
                c = (226, 118, 150)
            else:
                c = ring_colours[int(r - 1.6) % 3]
                c = shade(mix(c, (230, 200, 190), 0.25), 0.92 + rng.random() * 0.08)
            img.putpixel((x, y), rgba(c))
    return img


def trans_planks():
    rng = random.Random(11)
    img = new()
    colours = [BLUE, PINK, WHITE, PINK]
    seams = [5, 11, 2, 9]
    for y in range(16):
        plank = y // 4
        for x in range(16):
            c = mix((214, 182, 168), colours[plank], 0.55)
            c = shade(c, 0.9 + rng.random() * 0.1)
            if y % 4 == 3 or x == seams[plank]:
                c = shade(c, 0.72)
            elif rng.random() < 0.12:
                c = shade(c, 0.86)
            img.putpixel((x, y), rgba(c))
    return img


def trans_leaves():
    rng = random.Random(12)
    img = new()
    for y in range(16):
        for x in range(16):
            roll = rng.random()
            if roll < 0.17:
                continue  # holes -> cutout rendering
            if roll < 0.32:
                c = PINK
            elif roll < 0.38:
                c = WHITE
            else:
                c = mix(BLUE, (70, 150, 210), rng.random() * 0.5)
            img.putpixel((x, y), rgba(shade(c, 0.85 + rng.random() * 0.2)))
    return img


def pride_blossom():
    rows = [
        "................",
        "......bBBb......",
        ".....bBBBBb.....",
        "..pPP.BBBB.PPp..",
        ".pPPPPbBBbPPPPp.",
        ".pPPPPWYYWPPPPp.",
        "..pPPPWYYWPPPp..",
        ".....bWWWWb.....",
        ".....bBBBBb.....",
        "......bBBb......",
        ".......Gg.......",
        "...gG..Gg.Gg....",
        "....gGGGgGG.....",
        ".......Gg.......",
        ".......Gg.......",
        ".......Gg.......",
    ]
    return from_ascii(rows, {"B": BLUE, "b": shade(BLUE, 0.8), "P": PINK, "p": shade(PINK, 0.82), "W": WHITE,
                             "Y": (255, 226, 150), "G": GREEN, "g": GREEN_D})


def cake_top():
    rng = random.Random(13)
    img = new()
    for y in range(16):
        for x in range(16):
            d = min(x, y, 15 - x, 15 - y)
            c = PINK if d <= 2 else WHITE
            img.putpixel((x, y), rgba(shade(c, 0.95 + rng.random() * 0.06)))
    heart = [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."]
    for dy, row in enumerate(heart):
        for dx, ch in enumerate(row):
            if ch == "#":
                img.putpixel((4 + dx, 5 + dy), rgba(shade(PINK, 0.92)))
    for _ in range(14):
        x, y = rng.randrange(3, 13), rng.randrange(3, 13)
        if img.getpixel((x, y))[:3] != shade(PINK, 0.92):
            img.putpixel((x, y), rgba(rng.choice((BLUE, shade(PINK, 0.85)))))
    return img


def cake_side(inner=False):
    rng = random.Random(14 if not inner else 15)
    img = new()
    layers = {8: PINK, 9: PINK, 10: WHITE, 11: BLUE, 12: BLUE, 13: WHITE, 14: PINK, 15: (240, 214, 190)}
    if inner:
        layers = {8: (252, 236, 240), 9: (250, 226, 232), 10: WHITE, 11: BLUE, 12: BLUE, 13: WHITE,
                  14: PINK, 15: (240, 214, 190)}
    for y in range(8, 16):
        for x in range(16):
            c = layers[y]
            if not inner and y == 10 and rng.random() < 0.45:
                c = PINK  # frosting drips
            img.putpixel((x, y), rgba(shade(c, 0.93 + rng.random() * 0.08)))
    return img


def cake_bottom():
    rng = random.Random(16)
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), rgba(shade((236, 210, 188), 0.92 + rng.random() * 0.08)))
    return img


def pride_oven_side():
    return bricks(17, [BLUE, PINK, WHITE, PINK], tint=0.55)


def pride_oven_front():
    img = pride_oven_side()
    rng = random.Random(18)
    for y in range(7, 15):
        for x in range(3, 13):
            if x in (3, 12) or y == 7:
                c = (70, 56, 92)
            else:
                heat = (y - 7) / 7.0
                c = mix((255, 220, 140), (255, 120, 150), heat)
                c = shade(c, 0.85 + rng.random() * 0.2)
            img.putpixel((x, y), rgba(c))
    heart = [".#.#.", "#####", ".###.", "..#.."]
    for dy, row in enumerate(heart):
        for dx, ch in enumerate(row):
            if ch == "#":
                img.putpixel((6 + dx, 1 + dy), rgba(WHITE))
    return img


def pride_oven_top():
    img = bricks(19, [WHITE, PINK, BLUE, PINK], tint=0.5)
    for y in range(6, 10):
        for x in range(6, 10):
            img.putpixel((x, y), rgba((70, 56, 92)))
    return img


# ------------------------------------------------------------------------------------- trans water
def flag_gradient(t):
    """Smooth gradient through the flag colours; t in [0, 1) wraps around."""
    t = t % 1.0
    stops = [BLUE, PINK, WHITE, PINK, BLUE]
    pos = t * 4
    i = int(pos)
    return mix(stops[i], stops[min(i + 1, 4)], pos - i)


def water_still(frames=32):
    img = Image.new("RGBA", (16, 16 * frames), CLEAR)
    for f in range(frames):
        phase = f / frames
        for y in range(16):
            for x in range(16):
                ripple = (0.06 * math.sin(2 * math.pi * (x / 16 + phase))
                          + 0.05 * math.sin(2 * math.pi * (y / 8 - 2 * phase)))
                c = flag_gradient((x + y) / 32 + phase + ripple)
                crest = 0.5 + 0.5 * math.sin(2 * math.pi * ((x - y) / 16 + 2 * phase))
                if crest > 0.94:
                    c = mix(c, WHITE, 0.5)
                img.putpixel((x, f * 16 + y), rgba(c, 200))
    return img


def water_flow(frames=32):
    img = Image.new("RGBA", (32, 32 * frames), CLEAR)
    for f in range(frames):
        phase = f / frames
        for y in range(32):
            for x in range(32):
                wobble = 0.06 * math.sin(2 * math.pi * (2 * x / 32 + phase))
                c = flag_gradient(y / 32 - phase + wobble)
                foam = 0.5 + 0.5 * math.sin(2 * math.pi * (y / 8 - 4 * phase + x / 32))
                if foam > 0.95:
                    c = mix(c, WHITE, 0.5)
                img.putpixel((x, f * 32 + y), rgba(c, 190))
    return img


# ============================================================================================= items
def tool(head_pixels, handle_pixels, head_order):
    """head_pixels: list of (x, y); head_order(x, y) -> 0..1 used to stripe the head with the flag."""
    img = new()
    for i, (x, y) in enumerate(handle_pixels):
        img.putpixel((x, y), rgba(WOOD if i % 2 == 0 else WOOD_D))
    values = [head_order(x, y) for x, y in head_pixels]
    lo, hi = min(values), max(values)
    for (x, y), v in zip(head_pixels, values):
        t = 0 if hi == lo else (v - lo) / (hi - lo)
        img.putpixel((x, y), rgba(FLAG[band5(int(t * 99.99), 100)]))
    bevel(img)
    return outline(img)


def line(x0, y0, x1, y1):
    pts = []
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        pts.append((round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)))
    return pts


def sword():
    blade = []
    for i in range(9):
        blade += [(5 + i, 9 - i), (6 + i, 9 - i)]
    blade.append((14, 0))
    guard = [(3, 8), (4, 9), (5, 10), (6, 11), (7, 12)]
    img = tool(blade, line(4, 11, 2, 13), lambda x, y: x - y)
    for x, y in guard:
        img.putpixel((x, y), rgba((170, 120, 190)))
    img.putpixel((1, 14), rgba(BLUE))
    return outline(img)


def pickaxe():
    head = set()
    for k in range(-60, 61):
        s = k / 10.0
        px = 10.5 + s * 0.707 - 0.06 * s * s * 0.707
        py = 4.5 + s * 0.707 + 0.06 * s * s * 0.707
        for ox, oy in ((0, 0), (0.5, -0.5)):
            x, y = round(px + ox), round(py + oy)
            if 1 <= x <= 14 and 1 <= y <= 14:
                head.add((x, y))
    head = sorted(head)
    return tool(head, line(2, 13, 9, 6), lambda x, y: x + y)


def axe():
    # Blade sits on the upper-left side of the handle's top end, so it reads as an axe, not a mallet.
    head = []
    for y in range(16):
        for x in range(16):
            s, d = x + y, x - y
            if 9 <= s <= 14 and 2 <= d <= 10 and not (s <= 10 and (d <= 3 or d >= 9)):
                head.append((x, y))
    return tool(head, line(2, 13, 12, 3), lambda x, y: x + y)


def shovel():
    rows = {0: (12, 13), 1: (11, 14), 2: (10, 14), 3: (10, 14), 4: (10, 13), 5: (11, 12)}
    head = [(x, y) for y, (a, b) in rows.items() for x in range(a, b + 1)]
    return tool(head, line(2, 13, 9, 6), lambda x, y: x - y)


def hoe():
    head = [(7, 2), (8, 2), (9, 2), (10, 2), (11, 2), (12, 3), (7, 3), (8, 3), (11, 3), (12, 4)]
    return tool(head, line(2, 13, 11, 4), lambda x, y: x)


def banded(mask, highlight=True):
    """Fills the 'X' pixels of mask with flag stripes by row, then bevels and outlines."""
    img = new()
    ys = [y for y, row in enumerate(mask) if "X" in row]
    y0, y1 = min(ys), max(ys)
    for y, row in enumerate(mask):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch == "X":
                img.putpixel((x, y), rgba(FLAG[band5(y - y0, y1 - y0 + 1)]))
    if highlight:
        bevel(img)
    return outline(img)


HELMET = ["................", "................", "................", "....XXXXXXXX....", "...XXXXXXXXXX...",
          "...XXXXXXXXXX...", "...XXXXXXXXXX...", "...XXX....XXX...", "...XXX....XXX...", "...XX......XX...",
          "................", "................", "................", "................", "................",
          "................"]
CHESTPLATE = ["................", "................", "..XXX......XXX..", "..XXXX....XXXX..", "..XXXXXXXXXXXX..",
              "..XXXXXXXXXXXX..", "...XXXXXXXXXX...", "....XXXXXXXX....", "....XXXXXXXX....", "....XXXXXXXX....",
              "....XXXXXXXX....", "....XXXXXXXX....", "....XXXXXXXX....", "................", "................",
              "................"]
LEGGINGS = ["................", "................", "....XXXXXXXX....", "....XXXXXXXX....", "....XXXXXXXX....",
            "....XXX..XXX....", "....XXX..XXX....", "....XXX..XXX....", "....XXX..XXX....", "....XXX..XXX....",
            "....XXX..XXX....", "....XXX..XXX....", "................", "................", "................",
            "................"]
BOOTS = ["................", "................", "................", "................", "................",
         "...XXX....XXX...", "...XXX....XXX...", "...XXX....XXX...", "..XXXX....XXXX..", "..XXXX....XXXX..",
         "................", "................", "................", "................", "................",
         "................"]
CRYSTAL = ["................", "................", "......XXXX......", ".....XXXXXX.....", "....XXXXXXXX....",
           "...XXXXXXXXXX...", "...XXXXXXXXXX...", "....XXXXXXXX....", ".....XXXXXX.....", "......XXXX......",
           ".......XX.......", "................", "................", "................", "................",
           "................"]


def crystal_item():
    img = banded(CRYSTAL)
    for x, y in ((6, 3), (5, 4), (6, 4), (4, 5)):
        img.putpixel((x, y), rgba(WHITE))
    return img


def disk(cx, cy, r):
    return {(x, y) for y in range(16) for x in range(16) if (x - cx) ** 2 + (y - cy) ** 2 <= r * r}


def donut():
    rng = random.Random(20)
    img = new()
    ring = disk(7.5, 8, 6.3) - disk(7.5, 8, 2.2)
    for x, y in ring:
        frosted = y < 8.5 + 1.2 * math.sin(x * 1.3)
        c = PINK if frosted else (226, 172, 112)
        img.putpixel((x, y), rgba(shade(c, 0.95 + rng.random() * 0.08)))
    for _ in range(9):
        x, y = rng.choice(sorted(ring))
        if img.getpixel((x, y))[:3] != (226, 172, 112) and y < 9:
            img.putpixel((x, y), rgba(rng.choice((BLUE, WHITE))))
    bevel(img)
    return outline(img, (96, 54, 64))


def cookie():
    rng = random.Random(21)
    img = new()
    for x, y in disk(7.5, 7.5, 6.2):
        img.putpixel((x, y), rgba(shade((226, 182, 122), 0.9 + rng.random() * 0.12)))
    for colour in (PINK, PINK, PINK, BLUE, BLUE, BLUE, WHITE, WHITE):
        x, y = rng.randrange(4, 12), rng.randrange(4, 12)
        img.putpixel((x, y), rgba(colour))
    bevel(img)
    return outline(img, (96, 60, 44))


def cupcake():
    rows = [
        "................",
        ".......PP.......",
        "......PWWP......",
        ".....PPWWPP.....",
        "....PWWPPWWP....",
        "...PPPWWWWPPP...",
        "..PWWPPPPPPWWP..",
        "..PPPPPPPPPPPP..",
        "...BWBWBWBWBW...",
        "...BWBWBWBWBW...",
        "....BWBWBWBW....",
        "....BWBWBWBW....",
        ".....BWBWBW.....",
        "................",
        "................",
        "................",
    ]
    img = from_ascii(rows, {"P": PINK, "W": WHITE, "B": BLUE})
    img.putpixel((7, 0), rgba((222, 76, 112)))
    img.putpixel((8, 0), rgba((222, 76, 112)))
    bevel(img)
    return outline(img, (96, 54, 74))


def macaron():
    img = new()
    shape = disk(7.5, 8.5, 6.4)
    for x, y in shape:
        if 3 <= y <= 14:
            img.putpixel((x, y), rgba(FLAG[band5(y - 3, 12)]))
    bevel(img)
    return outline(img, (60, 54, 96))


def boba():
    rng = random.Random(22)
    img = new()
    for y in range(4, 15):
        inset = (y - 4) // 4
        for x in range(4 + inset, 12 - inset):
            if y == 4:
                c = (250, 250, 255)
            elif y < 8:
                c = PINK
            elif y < 10:
                c = (250, 246, 248)
            else:
                c = BLUE
            img.putpixel((x, y), rgba(shade(c, 0.95 + rng.random() * 0.06)))
    for x, y in ((6, 13), (8, 13), (7, 12), (9, 12), (6, 11), (8, 11)):
        img.putpixel((x, y), rgba((58, 40, 54)))
    for i, (x, y) in enumerate(((10, 0), (10, 1), (9, 2), (9, 3))):
        img.putpixel((x, y), rgba(PINK if i % 2 == 0 else BLUE))
    bevel(img)
    return outline(img, (70, 58, 96))


def cake_item():
    img = new()
    for y in range(5, 14):
        for x in range(2, 14):
            if y <= 6:
                c = WHITE
            elif y == 7:
                c = PINK if x % 3 else WHITE
            else:
                c = FLAG[band5(y - 8, 6)]
            img.putpixel((x, y), rgba(c))
    for x, y in ((4, 5), (7, 6), (10, 5), (12, 6)):
        img.putpixel((x, y), rgba(BLUE))
    img.putpixel((7, 3), rgba((255, 200, 120)))
    img.putpixel((7, 4), rgba(PINK))
    bevel(img)
    return outline(img, (96, 54, 74))


def water_bucket():
    img = new()
    metal = (196, 200, 212)
    for y in range(4, 15):
        inset = (y - 4) // 5
        for x in range(2 + inset, 14 - inset):
            img.putpixel((x, y), rgba(shade(metal, 1.05 if x < 7 else 0.92)))
    for x in range(3, 13):
        img.putpixel((x, 4), rgba(FLAG[band5(x - 3, 10)]))
        img.putpixel((x, 5), rgba(shade(FLAG[band5(x - 3, 10)], 0.9)))
    for x, y in ((3, 3), (4, 2), (5, 1), (6, 1), (7, 1), (8, 1), (9, 1), (10, 1), (11, 2), (12, 3)):
        img.putpixel((x, y), rgba((120, 124, 140)))
    return outline(img, (60, 62, 84))


# ======================================================================================= entity textures
def paint_face(img, x0, y0, w, h, rows_from=0, rows_to=None, border=True, face_colour=None):
    """Paints one box face of a model texture with horizontal flag stripes."""
    rows_to = h if rows_to is None else rows_to
    for y in range(rows_from, rows_to):
        for x in range(w):
            c = face_colour or FLAG[band5(y, h)]
            if border and (x == 0 or x == w - 1 or y == rows_from or y == rows_to - 1):
                c = shade(c, 0.78)
            img.putpixel((x0 + x, y0 + y), rgba(c))


def paint_box(img, u, v, w, h, d, rows_from=0, rows_to=None, top=True, bottom=True):
    """UV layout of a Minecraft model box at texture offset (u, v) with size w x h x d."""
    rows_to = h if rows_to is None else rows_to
    if top:
        paint_face(img, u + d, v, w, d, face_colour=BLUE)
    if bottom:
        paint_face(img, u + d + w, v, w, d, face_colour=BLUE)
    for fx, fw in ((u, d), (u + d, w), (u + d + w, d), (u + d + w + d, w)):
        paint_face(img, fx, v + d, fw, h, rows_from, rows_to)


def armor_layer_1():
    img = Image.new("RGBA", (64, 32), CLEAR)
    paint_box(img, 0, 0, 8, 8, 8)                               # helmet
    for x in range(10, 14):                                       # open visor on the front face
        for y in range(11, 16):
            img.putpixel((x, y), CLEAR)
    paint_box(img, 16, 16, 8, 12, 4)                             # chestplate
    heart = [".#.#.", "#####", ".###.", "..#.."]
    for dy, row in enumerate(heart):                             # heart on the chest
        for dx, ch in enumerate(row):
            if ch == "#":
                img.putpixel((22 + dx, 22 + dy), rgba((226, 98, 136)))
    paint_box(img, 40, 16, 4, 12, 4, rows_from=0, rows_to=7, bottom=False)   # shoulders / upper arms
    paint_box(img, 0, 16, 4, 12, 4, rows_from=6, rows_to=12, top=False)      # boots
    return img


def armor_layer_2():
    img = Image.new("RGBA", (64, 32), CLEAR)
    paint_box(img, 16, 16, 8, 12, 4, rows_from=6, rows_to=12, top=False)    # belt / hips
    paint_box(img, 0, 16, 4, 12, 4, rows_from=0, rows_to=9, bottom=False)   # leggings
    return img


def villager_profession():
    """Trans Baker overlay (64x64 villager layout): a white chef hat and a striped apron."""
    img = Image.new("RGBA", (64, 64), CLEAR)
    # chef hat on the hat layer (box at 32,0 sized 8x10x8): top face + upper rows of the sides
    for x in range(40, 48):
        for y in range(0, 8):
            img.putpixel((x, y), rgba(WHITE if (x + y) % 5 else (236, 236, 244)))
    for x in range(32, 64):
        for y in range(8, 12):
            img.putpixel((x, y), rgba(WHITE))
        img.putpixel((x, 12), rgba(PINK))
    # apron on the body front (22,26 8x12) and robe front (6,44 8x20)
    for x in range(22, 30):
        for y in range(28, 38):
            img.putpixel((x, y), rgba(WHITE))
    for x in range(6, 14):
        for y in range(44, 62):
            img.putpixel((x, y), rgba(FLAG[band5(y - 44, 18)]))
    for x, y in ((24, 30), (26, 30), (23, 31), (24, 31), (25, 31), (26, 31), (27, 31), (24, 32), (25, 32),
                 (26, 32), (25, 33)):
        img.putpixel((x, y), rgba((226, 98, 136)))
    return img


# ===================================================================================== sky + icon
HEARTS = {
    5: [".#.#.", "#####", "#####", ".###.", "..#.."],
    7: [".##.##.", "#######", "#######", "#######", ".#####.", "..###..", "...#..."],
    9: [".##...##.", "####.####", "#########", "#########", "#########", ".#######.", "..#####..", "...###...",
        "....#...."],
    11: ["..##...##..", ".####.####.", "###########", "###########", "###########", "###########",
         ".#########.", "..#######..", "...#####...", "....###....", ".....#....."],
}


def heart_clouds():
    """256x256 cloud map: every opaque pixel becomes one cloud cell in the sky."""
    rng = random.Random(23)
    img = Image.new("RGBA", (256, 256), CLEAR)
    occupied = []
    attempts = 0
    while len(occupied) < 70 and attempts < 4000:
        attempts += 1
        size = rng.choice((5, 7, 7, 9, 9, 11))
        x, y = rng.randrange(2, 254 - size), rng.randrange(2, 254 - size)
        if any(abs(x - ox) < (size + os_) // 2 + 6 and abs(y - oy) < (size + os_) // 2 + 6 for ox, oy, os_ in occupied):
            continue
        occupied.append((x, y, size))
        for dy, row in enumerate(HEARTS[size]):
            for dx, ch in enumerate(row):
                if ch == "#":
                    img.putpixel((x + dx, y + dy), (255, 255, 255, 255))
    return img


def icon():
    img = Image.new("RGBA", (128, 128), CLEAR)
    for y in range(128):
        for x in range(128):
            cxd = max(0, max(12 - x, x - 115))
            cyd = max(0, max(12 - y, y - 115))
            if cxd * cxd + cyd * cyd > 144:
                continue
            img.putpixel((x, y), rgba(FLAG[band5(y, 128)]))
    heart = HEARTS[11]
    scale = 7
    ox, oy = 64 - 11 * scale // 2, 64 - 11 * scale // 2 + 4
    for dy, row in enumerate(heart):
        for dx, ch in enumerate(row):
            if ch != "#":
                continue
            for py in range(scale):
                for px in range(scale):
                    img.putpixel((ox + dx * scale + px, oy + dy * scale + py), rgba(WHITE))
    for dy, row in enumerate(heart):  # outline
        for dx, ch in enumerate(row):
            if ch != "#":
                continue
            for ndx, ndy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = dx + ndx, dy + ndy
                inside = 0 <= ny < 11 and 0 <= nx < 11 and heart[ny][nx] == "#"
                if inside:
                    continue
                for i in range(scale):
                    if ndx:
                        px = ox + dx * scale + (scale - 1 if ndx > 0 else 0)
                        img.putpixel((px, oy + dy * scale + i), rgba((214, 96, 132)))
                    else:
                        py = oy + dy * scale + (scale - 1 if ndy > 0 else 0)
                        img.putpixel((ox + dx * scale + i, py), rgba((214, 96, 132)))
    return img


def main():
    blocks = {
        "trans_stone": trans_stone(), "trans_cobblestone": trans_cobblestone(),
        "trans_stone_bricks": bricks(24, [BLUE, PINK, WHITE, PINK]),
        "trans_dirt": trans_dirt(), "trans_grass_block_top": trans_grass_top(),
        "trans_grass_block_side": trans_grass_side(), "trans_sand": trans_sand(),
        "trans_crystal_ore": trans_crystal_ore(), "trans_crystal_block": trans_crystal_block(),
        "trans_log": trans_log_side(), "trans_log_top": trans_log_top(), "trans_planks": trans_planks(),
        "trans_leaves": trans_leaves(), "pride_blossom": pride_blossom(),
        "trans_cake_top": cake_top(), "trans_cake_side": cake_side(), "trans_cake_inner": cake_side(True),
        "trans_cake_bottom": cake_bottom(), "pride_oven_front": pride_oven_front(),
        "pride_oven_side": pride_oven_side(), "pride_oven_top": pride_oven_top(),
    }
    for name, img in blocks.items():
        save(img, f"block/{name}.png")

    save(water_still(), "block/trans_water_still.png")
    save_mcmeta("block/trans_water_still.png", {"animation": {"frametime": 2}})
    save(water_flow(), "block/trans_water_flow.png")
    save_mcmeta("block/trans_water_flow.png", {"animation": {"frametime": 2}})

    items = {
        "trans_crystal": crystal_item(), "trans_sword": sword(), "trans_pickaxe": pickaxe(), "trans_axe": axe(),
        "trans_shovel": shovel(), "trans_hoe": hoe(), "trans_helmet": banded(HELMET),
        "trans_chestplate": banded(CHESTPLATE), "trans_leggings": banded(LEGGINGS), "trans_boots": banded(BOOTS),
        "trans_donut": donut(), "trans_cookie": cookie(), "trans_cupcake": cupcake(), "trans_macaron": macaron(),
        "trans_boba": boba(), "trans_cake": cake_item(), "trans_water_bucket": water_bucket(),
    }
    for name, img in items.items():
        save(img, f"item/{name}.png")

    save(armor_layer_1(), "entity/equipment/humanoid/trans_crystal.png")
    save(armor_layer_2(), "entity/equipment/humanoid_leggings/trans_crystal.png")
    baker = villager_profession()
    save(baker, "entity/villager/profession/trans_baker.png")
    save_mcmeta("entity/villager/profession/trans_baker.png", {"villager": {"hat": "full"}})
    save(baker, "entity/zombie_villager/profession/trans_baker.png")
    save_mcmeta("entity/zombie_villager/profession/trans_baker.png", {"villager": {"hat": "full"}})
    save(heart_clouds(), "environment/heart_clouds.png")

    icon_path = os.path.join(OUT, "icon.png")
    icon().save(icon_path)
    print("Textures written to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
