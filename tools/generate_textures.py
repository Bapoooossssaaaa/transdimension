#!/usr/bin/env python3
"""
Generates every texture of the Trans Dimension mod.

Most textures are *recoloured vanilla textures*: they are read from the two reference zips in the
repository root ("base block textures.zip" and "Base Sprite Images.zip") plus a few extra vanilla
entity textures in tools/vanilla_extra/, and gradient-mapped onto trans palettes. Mapping the
brightness of every pixel onto a colour ramp keeps Minecraft's own shading, texture and "feel"
while changing the colours, so the mod looks like it belongs in the game.

Textures with no vanilla counterpart (donut, cupcake, macaron, boba, the Silly Cat, the cat-spit
overlay...) are drawn here as pixel art in the same style.

    pip install pillow
    python3 tools/generate_textures.py

Re-running overwrites every PNG under src/main/resources/assets/transdimension/textures/, so edit
this script (not the PNGs) to change a texture.
"""
import colorsys
import json
import math
import os
import random
import zipfile

from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "transdimension")
BLOCK_ZIP = os.path.join(ROOT, "base block textures.zip")
ITEM_ZIP = os.path.join(ROOT, "Base Sprite Images.zip")
EXTRA = os.path.join(HERE, "vanilla_extra")


# ============================================================================================ palette
def hexc(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


BLUE = hexc("5BCEFA")
PINK = hexc("F5A9B8")
WHITE = (255, 255, 255)
FLAG = [BLUE, PINK, WHITE, PINK, BLUE]

# Colour ramps, dark -> light. A pixel's brightness picks its colour along the ramp.
R_BLUE = [hexc(c) for c in ("0F2A4D", "1F5288", "3487C2", "5BCEFA", "93DEFB", "CFF2FE")]
R_PINK = [hexc(c) for c in ("4D1A33", "87395A", "C26683", "F5A9B8", "F9C8D2", "FDE6EB")]
R_PEARL = [hexc(c) for c in ("4E4A66", "7D7A96", "A9A7C0", "D2D1E3", "EEEEF6", "FFFFFF")]
R_LAVENDER = [hexc(c) for c in ("2F2652", "4E4382", "7468B4", "A096D8", "C9C1EF", "ECE8FB")]
# Stone is a soft warm grey (it used to be lavender); trans colours show up as sparse glints instead.
R_STONE = [hexc(c) for c in ("4E484C", "6A6368", "888085", "A69EA3", "C3BCC0", "DDD7DA")]
R_MORTAR = [hexc(c) for c in ("3A3539", "524C50", "6C6569", "878084", "A29B9F", "BDB7BA")]
# Deep slate: cool blue-grey, darker than stone.
R_DEEPSLATE = [hexc(c) for c in ("15181F", "21262F", "2F3542", "404858", "535D6F", "687487")]
R_GRANITE = [hexc(c) for c in ("6A3846", "8E5165", "B07186", "CF94A7", "E6B8C6", "F6DCE4")]
R_DIORITE = [hexc(c) for c in ("8A8E99", "AAB0BB", "C8CED7", "E0E5EB", "F2F5F8", "FFFFFF")]
R_ANDESITE = [hexc(c) for c in ("3C4758", "536176", "6B7C93", "8697AE", "A2B3C8", "C0CEDF")]
R_DIRT = [hexc(c) for c in ("3E2235", "5C364D", "7B4C66", "996581", "B5819C")]
R_SAND = [hexc(c) for c in ("C27C92", "D896A8", "E8B2C0", "F4CBD5", "FCE3EA")]
R_SANDSTONE = [hexc(c) for c in ("9C5370", "BE7590", "D898AD", "EAB6C7", "F7D5E0")]
R_WOOD_PINK = [hexc(c) for c in ("6E3050", "9C5073", "C77596", "E59EB9", "F6C7D8")]
R_WOOD_BLUE = [hexc(c) for c in ("24476F", "37699B", "5596C6", "86C3E6", "BCE3F6")]
R_WOOD_WHITE = [hexc(c) for c in ("7F7A93", "A6A2BA", "C9C6D9", "E6E4EF", "FAF9FD")]
# Blue -> lavender -> pink -> white: crystals, crystal gear.
R_TRANS = [hexc(c) for c in ("17396B", "2C6FB0", "4FB3EA", "93D9F8", "C8B9EC", "F2A6BF", "F9CCD7", "FFFFFF")]
R_DOUGH = [hexc(c) for c in ("6B3A1E", "94572C", "B97A3F", "D69E58", "EDC27E")]
NAVY = hexc("1B2650")
CLEAR = (0, 0, 0, 0)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def mix_ramp(ramp_a, ramp_b, t):
    """Blends two ramps stop by stop (resampled to the longer one)."""
    n = max(len(ramp_a), len(ramp_b))
    return [mix(sample(ramp_a, i / (n - 1)), sample(ramp_b, i / (n - 1)), t) for i in range(n)]


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c[:3])


def sample(ramp, t):
    t = min(1.0, max(0.0, t))
    pos = t * (len(ramp) - 1)
    i = min(len(ramp) - 2, int(pos))
    return mix(ramp[i], ramp[i + 1], pos - i)


def lum(px):
    return (0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]) / 255.0


def hsv(px):
    return colorsys.rgb_to_hsv(px[0] / 255.0, px[1] / 255.0, px[2] / 255.0)


# ============================================================================================ I/O
_zip_cache = {}


def _zip(path):
    if path not in _zip_cache:
        _zip_cache[path] = zipfile.ZipFile(path)
    return _zip_cache[path]


def vblock(name):
    """A vanilla block texture (from 'base block textures.zip')."""
    with _zip(BLOCK_ZIP).open(f"base block textures/{name}.png") as f:
        return Image.open(f).convert("RGBA")


def vitem(name):
    """A vanilla item sprite (from 'Base Sprite Images.zip')."""
    with _zip(ITEM_ZIP).open(f"Base Sprite Images/{name}.png") as f:
        return Image.open(f).convert("RGBA")


def vextra(rel):
    return Image.open(os.path.join(EXTRA, rel)).convert("RGBA")


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


CUTOUT = {"texture": {"mipmap_strategy": "strict_cutout"}}
LEAVES_META = {"texture": {"mipmap_strategy": "dark_cutout"}}
GLASS_META = {"texture": {"mipmap_strategy": "mean"}}


# ============================================================================================ core tools
def pixels(img):
    w, h = img.size
    return [(x, y) for y in range(h) for x in range(w)]


def lum_range(img, mask=None, lo_pct=0.02, hi_pct=0.98):
    values = sorted(lum(img.getpixel(p)) for p in pixels(img)
                    if img.getpixel(p)[3] > 0 and (mask is None or mask(p, img.getpixel(p))))
    if not values:
        return 0.0, 1.0
    lo = values[int(lo_pct * (len(values) - 1))]
    hi = values[int(hi_pct * (len(values) - 1))]
    return lo, max(hi, lo + 1e-3)


def gradient_map(img, ramp, mask=None, lo=None, hi=None, curve=lambda t: t, out=None, frame_height=None):
    """
    Recolours `img` (or only the pixels selected by `mask(pos, px)`) by mapping each pixel's
    brightness, normalised over the selected pixels, onto `ramp`. Alpha is kept.
    """
    src = img
    dst = out if out is not None else img.copy()
    if lo is None or hi is None:
        lo2, hi2 = lum_range(src, mask)
        lo = lo2 if lo is None else lo
        hi = hi2 if hi is None else hi
    for p in pixels(src):
        px = src.getpixel(p)
        if px[3] == 0 or (mask is not None and not mask(p, px)):
            continue
        t = curve((lum(px) - lo) / (hi - lo))
        c = sample(ramp, t)
        dst.putpixel(p, (c[0], c[1], c[2], px[3]))
    return dst


def components(img, pred, wrap=True):
    """Labels 4-connected regions of pixels for which pred(pos, px) is true (wrapping at the edges,
    so tileable textures keep consistent regions). Returns {pos: label} and the label count."""
    w, h = img.size
    labels = {}
    count = 0
    for start in pixels(img):
        if start in labels:
            continue
        if not pred(start, img.getpixel(start)):
            continue
        stack = [start]
        labels[start] = count
        while stack:
            x, y = stack.pop()
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if wrap:
                    nx %= w
                    ny %= h
                elif not (0 <= nx < w and 0 <= ny < h):
                    continue
                q = (nx, ny)
                if q not in labels and pred(q, img.getpixel(q)):
                    labels[q] = count
                    stack.append(q)
        count += 1
    return labels, count


def stripes5(n):
    """Splits n rows into the five flag stripes as evenly as possible, widest in the middle."""
    base = [n // 5] * 5
    for i in (2, 1, 3, 0, 4)[: n - sum(base)]:
        base[i] += 1
    bands = []
    for i, size in enumerate(base):
        bands += [i] * size
    return bands


def flag_rows(img, ramp_for_stripe, rows=None, lo=None, hi=None, curve=lambda t: t):
    """Gradient-maps each row with the ramp of the flag stripe it falls in."""
    w, h = img.size
    bands = rows if rows is not None else stripes5(h)
    out = img.copy()
    if lo is None or hi is None:
        lo, hi = lum_range(img)
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3] == 0:
            continue
        t = curve((lum(px) - lo) / (hi - lo))
        c = sample(ramp_for_stripe(bands[y]), t)
        out.putpixel((x, y), (c[0], c[1], c[2], px[3]))
    return out


def light_ramp(color, dark=0.62, light=1.0):
    """A ramp that keeps a colour pastel: from a slightly darkened version up to the colour itself."""
    return [shade(color, dark), shade(color, (dark + light) / 2), shade(color, light), mix(color, WHITE, 0.25)]


FLAG_RAMPS = [light_ramp(BLUE, 0.7), light_ramp(PINK, 0.72), light_ramp((246, 246, 252), 0.78),
              light_ramp(PINK, 0.72), light_ramp(BLUE, 0.7)]


def paste_heart(img, cx, cy, fill, outline=None, size=5):
    shapes = {
        5: [".#.#.", "#####", "#####", ".###.", "..#.."],
        7: [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."],
    }
    rows = shapes[size]
    ox = cx - len(rows[0]) // 2
    oy = cy - len(rows) // 2
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == "#":
                img.putpixel((ox + dx, oy + dy), (*fill, 255))
    if outline:
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != "#":
                    continue
                for ndx, ndy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = dx + ndx, dy + ndy
                    inside = 0 <= ny < len(rows) and 0 <= nx < len(row) and rows[ny][nx] == "#"
                    if not inside:
                        px, py = ox + nx, oy + ny
                        if 0 <= px < img.width and 0 <= py < img.height and img.getpixel((px, py))[3] > 0:
                            img.putpixel((px, py), (*outline, 255))
    return img


# ============================================================================================ terrain
def grass_gray(img, low=0.66):
    """Turns a vanilla grass texture into a light grayscale one; the game tints it with the biome's
    grass colour, so keeping it bright gives pastel colours instead of muddy ones."""
    lo, hi = lum_range(img)
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3] == 0:
            continue
        t = min(1.0, max(0.0, (lum(px) - lo) / (hi - lo)))
        g = round(255 * (low + (1.0 - low) * t))
        out.putpixel(p, (g, g, g, px[3]))
    return out


def trans_dirt():
    return gradient_map(vblock("dirt"), R_DIRT)


def trans_grass_block_top():
    return grass_gray(vblock("grass_block_top"))


def trans_grass_block_side_overlay():
    return grass_gray(vblock("grass_block_side_overlay"), low=0.68)


def trans_grass_block_side():
    """Dirt with the grass fringe pre-tinted trans pink (what you see with the overlay off or far away)."""
    side = trans_dirt()
    overlay = trans_grass_block_side_overlay()
    for p in pixels(overlay):
        px = overlay.getpixel(p)
        if px[3] > 0:
            g = px[0] / 255.0
            side.putpixel(p, (*shade(PINK, g), 255))
    return side


def trans_grass_block_snow():
    img = vblock("grass_block_snow")
    snow = lambda p, px: hsv(px)[1] < 0.18 and lum(px) > 0.55
    out = gradient_map(img, R_DIRT, mask=lambda p, px: not snow(p, px))
    return gradient_map(img, [hexc("B9CBE6"), hexc("DCE8F7"), hexc("F4F8FE"), WHITE], mask=snow, out=out)


def glints(img, src, ramp_out, top=0.97, bottom=0.03, chance=0.5, pink_mix=0.35, blue_mix=0.3, seed=5):
    """Sprinkles a few pink glints on the brightest pixels of `src` and blue ones on the darkest
    (each candidate pixel with probability `chance`), on top of the already recoloured `img`."""
    lo, hi = lum_range(src)
    rng = random.Random(seed)
    out = img.copy()
    for p in pixels(src):
        px = src.getpixel(p)
        if px[3] == 0:
            continue
        t = (lum(px) - lo) / (hi - lo)
        c = out.getpixel(p)
        if t >= top and rng.random() < chance:
            out.putpixel(p, (*mix(c[:3], PINK, pink_mix), c[3]))
        elif t <= bottom and rng.random() < chance:
            out.putpixel(p, (*mix(c[:3], BLUE, blue_mix), c[3]))
    return out


def trans_stone():
    """Warm grey stone with the odd pink and blue glint."""
    base = vblock("stone")
    return glints(gradient_map(base, R_STONE), base, R_STONE)


def trans_cobblestone():
    """Grey cobbles, a few of them blushing pink or blue, set in darker mortar."""
    img = vblock("cobblestone")
    lo, hi = lum_range(img)
    norm = lambda px: (lum(px) - lo) / (hi - lo)
    stone_px = lambda p, px: norm(px) > 0.3
    labels, count = components(img, stone_px)
    rng = random.Random(7)
    ramps = [mix_ramp(R_STONE, light_ramp(PINK, 0.55), 0.38), mix_ramp(R_STONE, light_ramp(BLUE, 0.55), 0.32),
             R_STONE, mix_ramp(R_STONE, R_PEARL, 0.35)]
    choice = [ramps[(i + rng.randrange(4)) % 4] for i in range(count)]
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        t = norm(px)
        if p in labels:
            c = sample(choice[labels[p]], 0.15 + 0.85 * t)
        else:
            c = sample(R_MORTAR, 0.1 + t * 1.2)
        out.putpixel(p, (*c, px[3]))
    return out


def bricks_from(img, seed=11, accent=None, base=None, mortar=None, tint=0.3):
    """Pale bricks with a whisper of pink or blue per brick, separated by grey mortar."""
    base = base or R_PEARL
    mortar = mortar or R_MORTAR
    lo, hi = lum_range(img)
    norm = lambda px: (lum(px) - lo) / (hi - lo)
    brick = lambda p, px: norm(px) > 0.28
    labels, count = components(img, brick)
    tints = [mix_ramp(base, light_ramp(PINK, 0.6), tint), mix_ramp(base, light_ramp(BLUE, 0.6), tint)]
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        t = norm(px)
        if p in labels:
            c = sample(tints[(labels[p] + seed) % 2], 0.12 + 0.88 * t)
        else:
            c = sample(mortar, 0.05 + t * 1.4)
        out.putpixel(p, (*c, px[3]))
    return out


# ============================================================================================ deepslate
def trans_deepslate(name="deepslate"):
    base = vblock(name)
    return glints(gradient_map(base, R_DEEPSLATE), base, R_DEEPSLATE, top=0.985, bottom=-1.0, chance=0.4, pink_mix=0.3)


def deepslate_bricks(name, seed):
    """Dark slate bricks or tiles; each one leans faintly pink or blue."""
    deep_mortar = [hexc(c) for c in ("0B0D12", "13161C", "1C2029", "272C37", "333A47", "414959")]
    return bricks_from(vblock(name), seed=seed, base=R_DEEPSLATE, mortar=deep_mortar, tint=0.16)


def chiseled_trans_deepslate():
    img = gradient_map(vblock("chiseled_deepslate"), R_DEEPSLATE)
    return paste_heart(img, 7, 7, shade(PINK, 0.8), outline=hexc("5C2A44"))


# ============================================================================================ granite, diorite, andesite, gravel
def trans_granite(name="granite"):
    """Rose granite: dusty pink with darker rose and pearly flecks."""
    return gradient_map(vblock(name), R_GRANITE)


def trans_diorite(name="diorite"):
    """Pearl diorite: white stone whose dark flecks are trans blue."""
    img = vblock(name)
    lo, hi = lum_range(img)
    fleck = lambda p, px: (lum(px) - lo) / (hi - lo) < 0.35
    out = gradient_map(img, R_DIORITE, mask=lambda p, px: not fleck(p, px))
    return gradient_map(img, [hexc("2F6FA8"), hexc("4FA6DD"), hexc("8FD4F7")], mask=fleck, out=out)


def trans_andesite(name="andesite"):
    """Sky andesite: soft blue-grey with a few pink grains."""
    base = vblock(name)
    return glints(gradient_map(base, R_ANDESITE), base, R_ANDESITE, top=0.95, bottom=-1.0, chance=0.35, pink_mix=0.4)


def trans_gravel():
    """Pebbles in grey, pink, blue and white."""
    img = vblock("gravel")
    lo, hi = lum_range(img)
    norm = lambda px: (lum(px) - lo) / (hi - lo)
    pebble = lambda p, px: norm(px) > 0.42
    labels, count = components(img, pebble)
    rng = random.Random(31)
    ramps = [R_STONE, mix_ramp(R_STONE, light_ramp(PINK, 0.5), 0.55), mix_ramp(R_STONE, light_ramp(BLUE, 0.5), 0.5),
             mix_ramp(R_STONE, R_PEARL, 0.6), R_STONE]
    choice = [ramps[rng.randrange(len(ramps))] for _ in range(count)]
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        t = norm(px)
        c = sample(choice[labels[p]], 0.1 + 0.9 * t) if p in labels else sample(R_MORTAR, t * 1.3)
        out.putpixel(p, (*c, px[3]))
    return out


# ============================================================================================ ores
def ore_on(base_new, base_vanilla, ore_vanilla, ramp):
    """Moves the ore of a vanilla ore texture onto a new background. Coloured ore pixels keep their
    vanilla colours (so ores stay recognisable); grey pixels that differ from the vanilla background
    (the ore's outlines and shading) are re-shaded with the new rock's palette."""
    out = base_new.copy()
    lo, hi = lum_range(base_vanilla)
    for p in pixels(ore_vanilla):
        px = ore_vanilla.getpixel(p)
        if px[:3] == base_vanilla.getpixel(p)[:3]:
            continue
        if hsv(px)[1] > 0.14:
            out.putpixel(p, px)
        else:
            c = sample(ramp, (lum(px) - lo) / (hi - lo))
            out.putpixel(p, (*c, px[3]))
    return out


ORES = ("coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald")


def trans_ores():
    stone_new, deep_new = trans_stone(), trans_deepslate()
    stone_v, deep_v = vblock("stone"), vblock("deepslate")
    out = {}
    for ore in ORES:
        out[f"trans_{ore}_ore"] = ore_on(stone_new, stone_v, vblock(f"{ore}_ore"), R_STONE)
        out[f"trans_deepslate_{ore}_ore"] = ore_on(deep_new, deep_v, vblock(f"deepslate_{ore}_ore"), R_DEEPSLATE)
    return out


def trans_stone_bricks():
    return bricks_from(vblock("stone_bricks"))


def cracked_trans_stone_bricks():
    return bricks_from(vblock("cracked_stone_bricks"))


def chiseled_trans_stone_bricks():
    img = gradient_map(vblock("chiseled_stone_bricks"), mix_ramp(R_PEARL, R_MORTAR, 0.3))
    # A little heart carved into the centre panel.
    return paste_heart(img, 7, 7, shade(PINK, 0.95), outline=hexc("9E4C6B"))


def trans_sand():
    return gradient_map(vblock("sand"), R_SAND)


def sandstone(name):
    return gradient_map(vblock(name), R_SANDSTONE)


def chiseled_trans_sandstone():
    img = sandstone("chiseled_sandstone")
    # Replace the creeper face with a heart.
    for y in range(4, 12):
        for x in range(4, 12):
            px = img.getpixel((x, y))
            img.putpixel((x, y), (*shade(px[:3], 1.04), 255))
    return paste_heart(img, 7, 7, hexc("F7B9C8"), outline=hexc("8E3E60"), size=7)


# ============================================================================================ trans wood
def trans_log():
    """Pearly birch-style bark whose dark knots are alternately trans pink and trans blue."""
    img = vblock("birch_log")
    dark = lambda p, px: lum(px) < 0.35
    labels, count = components(img, dark)
    out = gradient_map(img, mix_ramp(R_PEARL, R_PINK, 0.12), mask=lambda p, px: not dark(p, px), lo=0.45, hi=0.95)
    knot_ramps = [[hexc("3B1830"), hexc("8E3E60"), hexc("D97A97")], [hexc("10284A"), hexc("2F6FA8"), hexc("4FB3EA")]]
    for p, label in labels.items():
        px = img.getpixel(p)
        c = sample(knot_ramps[label % 2], lum(px) / 0.35)
        out.putpixel(p, (*c, px[3]))
    return out


def trans_log_top():
    img = vblock("birch_log_top")
    # The outer ring of bark stays pearly; the rings inside become pink wood.
    w, h = img.size
    bark = lambda p, px: min(p[0], p[1], w - 1 - p[0], h - 1 - p[1]) == 0
    out = gradient_map(img, R_PEARL, mask=bark)
    return gradient_map(img, R_WOOD_PINK, mask=lambda p, px: not bark(p, px), out=out)


def stripped_trans_log():
    return gradient_map(vblock("stripped_birch_log"), R_WOOD_PINK)


def stripped_trans_log_top():
    return gradient_map(vblock("stripped_birch_log_top"), R_WOOD_PINK)


def trans_planks():
    """Soft pink boards; the middle two are washed with white and blue so walls show gentle trans stripes."""
    img = vblock("birch_planks")
    board_ramps = [R_WOOD_PINK, mix_ramp(R_WOOD_PINK, R_WOOD_WHITE, 0.55), mix_ramp(R_WOOD_PINK, R_WOOD_BLUE, 0.45), R_WOOD_PINK]
    lo, hi = lum_range(img)
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        c = sample(board_ramps[y // 4], (lum(px) - lo) / (hi - lo))
        out.putpixel((x, y), (*c, px[3]))
    return out


def voronoi_patches(w, h, seeds, rng):
    """Splits a tileable w x h texture into patches around random seed points (wrapping at the edges)."""
    points = [(rng.uniform(0, w), rng.uniform(0, h)) for _ in range(seeds)]
    cells = {}
    for y in range(h):
        for x in range(w):
            best, best_d = 0, 1e9
            for i, (px, py) in enumerate(points):
                dx = min(abs(x - px), w - abs(x - px))
                dy = min(abs(y - py), h - abs(y - py))
                d = dx * dx + dy * dy
                if d < best_d:
                    best, best_d = i, d
            cells[(x, y)] = best
    return cells


def trans_leaves():
    """Cherry-blossom leaves in patches of trans pink and trans blue; the green bits become pearly white."""
    img = vblock("cherry_leaves")
    blossom = lambda p, px: px[3] > 0 and (hsv(px)[0] > 0.8 or hsv(px)[0] < 0.08) and hsv(px)[1] > 0.12
    rng = random.Random(9)
    cells = voronoi_patches(16, 16, 6, rng)
    colours = [0, 1, 0, 1, 0, 1]
    out = gradient_map(img, R_PEARL, mask=lambda p, px: not blossom(p, px), lo=0.2, hi=0.8,
                       curve=lambda t: 0.3 + 0.7 * t)
    pink = [hexc("B4557A"), hexc("E58AA4"), PINK, hexc("FCE2EA")]
    blue = [hexc("2F7BBB"), hexc("46A8E2"), BLUE, hexc("D6F4FE")]
    lo, hi = lum_range(img, blossom)
    for p in pixels(img):
        px = img.getpixel(p)
        if not blossom(p, px):
            continue
        ramp = pink if colours[cells[p]] == 0 else blue
        c = sample(ramp, (lum(px) - lo) / (hi - lo))
        out.putpixel(p, (*c, px[3]))
    return out


def trans_sapling():
    img = vblock("cherry_sapling")
    trunk = lambda p, px: hsv(px)[1] < 0.45 and lum(px) < 0.35
    blossom = lambda p, px: not trunk(p, px) and (hsv(px)[0] > 0.8 or hsv(px)[0] < 0.08)
    out = gradient_map(img, [hexc("6E6A86"), hexc("B9B6CB"), hexc("F4F3F9")], mask=trunk)
    labels, count = components(img, blossom, wrap=False)
    out = gradient_map(img, R_PEARL, mask=lambda p, px: not trunk(p, px) and not blossom(p, px), out=out)
    pink = [hexc("A84A6E"), hexc("E58AA4"), PINK, hexc("FCE2EA")]
    blue = [hexc("2D73B0"), hexc("46A8E2"), BLUE, hexc("D6F4FE")]
    lo, hi = lum_range(img, blossom)
    for p, label in labels.items():
        px = img.getpixel(p)
        c = sample(pink if label % 2 == 0 else blue, (lum(px) - lo) / (hi - lo))
        out.putpixel(p, (*c, px[3]))
    return out


def recolor_door(img):
    """Cherry door/trapdoor: the frame becomes pink wood, the panels white wood with blue trim."""
    lo, hi = lum_range(img)
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3] == 0:
            continue
        t = (lum(px) - lo) / (hi - lo)
        if t < 0.33:
            c = sample(R_WOOD_BLUE, t * 1.6)
        else:
            c = sample(R_WOOD_PINK, t)
        out.putpixel((x, y), (*c, px[3]))
    return out


def trans_door_top():
    return recolor_door(vblock("cherry_door_top"))


def trans_door_bottom():
    return recolor_door(vblock("cherry_door_bottom"))


def trans_trapdoor():
    return recolor_door(vblock("cherry_trapdoor"))


def trans_door_item():
    return recolor_door(vitem("cherry_door"))


# ============================================================================================ crystals
def trans_crystal_block():
    return gradient_map(vblock("amethyst_block"), R_TRANS)


def trans_crystal_cluster():
    return gradient_map(vblock("amethyst_cluster"), R_TRANS, curve=lambda t: 0.1 + 0.9 * t)


def trans_crystal_ore():
    """Trans stone with glittering gems that are alternately blue and pink."""
    img = vblock("diamond_ore")
    gem = lambda p, px: hsv(px)[1] > 0.25
    out = gradient_map(img, R_STONE, mask=lambda p, px: not gem(p, px))
    labels, count = components(img, gem)
    ramps = [[hexc("1F5288"), hexc("3EA5E6"), BLUE, hexc("E8F9FF")], [hexc("8E3E60"), hexc("E07D9C"), PINK, hexc("FFF0F4")]]
    lo, hi = lum_range(img, gem)
    for p, label in labels.items():
        px = img.getpixel(p)
        c = sample(ramps[label % 2], (lum(px) - lo) / (hi - lo))
        out.putpixel(p, (*c, px[3]))
    return out


def trans_deepslate_crystal_ore():
    """The crystal ore in deep slate: alternately blue and pink gems on dark slate."""
    ore = vblock("deepslate_diamond_ore")
    deep_v = vblock("deepslate")
    gem = lambda p, px: hsv(px)[1] > 0.25
    out = ore_on(trans_deepslate(), deep_v, ore, R_DEEPSLATE)
    labels, count = components(ore, gem)
    ramps = [[hexc("1F5288"), hexc("3EA5E6"), BLUE, hexc("E8F9FF")], [hexc("8E3E60"), hexc("E07D9C"), PINK, hexc("FFF0F4")]]
    lo, hi = lum_range(ore, gem)
    for p, label in labels.items():
        px = ore.getpixel(p)
        c = sample(ramps[label % 2], (lum(px) - lo) / (hi - lo))
        out.putpixel(p, (*c, px[3]))
    return out


def trans_crystal_item():
    return gradient_map(vitem("amethyst_shard"), R_TRANS, curve=lambda t: 0.08 + 0.92 * t)


# ============================================================================================ glass, wool, light
def tinted_glass(base, colour, alpha_boost=1.15):
    out = base.copy()
    lo, hi = lum_range(base)
    for p in pixels(base):
        px = base.getpixel(p)
        if px[3] == 0:
            continue
        t = (lum(px) - lo) / (hi - lo)
        c = mix(shade(colour, 0.82), mix(colour, WHITE, 0.55), t)
        out.putpixel(p, (*c, min(255, round(px[3] * alpha_boost))))
    return out


def trans_stained_glass():
    """Five translucent flag stripes in a frosted frame."""
    base = vblock("white_stained_glass")
    out = base.copy()
    bands = stripes5(16)
    lo, hi = lum_range(base)
    for (x, y) in pixels(base):
        px = base.getpixel((x, y))
        t = (lum(px) - lo) / (hi - lo)
        colour = FLAG[bands[y]] if FLAG[bands[y]] != WHITE else (240, 240, 250)
        c = mix(shade(colour, 0.85), mix(colour, WHITE, 0.5), t)
        out.putpixel((x, y), (*c, min(255, round(px[3] * 1.12))))
    return out


def trans_glass():
    """Clear glass (what trans sand smelts into): vanilla glass with a pink and blue frame."""
    base = vblock("glass")
    out = base.copy()
    for (x, y) in pixels(base):
        px = base.getpixel((x, y))
        if px[3] == 0:
            continue
        edge_x, edge_y = x in (0, 15), y in (0, 15)
        if edge_x and edge_y:
            c = WHITE
        elif edge_y:
            c = mix(BLUE, WHITE, 0.25)
        elif edge_x:
            c = mix(PINK, WHITE, 0.2)
        else:
            c = mix(px[:3], WHITE, 0.4)
        out.putpixel((x, y), (*c, px[3]))
    return out


def trans_glass_pane_top():
    base = vblock("glass_pane_top")
    out = base.copy()
    for (x, y) in pixels(base):
        px = base.getpixel((x, y))
        if px[3]:
            out.putpixel((x, y), (*mix(BLUE if (y // 4) % 2 == 0 else PINK, WHITE, 0.3), px[3]))
    return out


def trans_stained_glass_pane_top():
    return tinted_glass(vblock("white_stained_glass_pane_top"), PINK, 1.0)


def trans_pink_stained_glass():
    return tinted_glass(vblock("white_stained_glass"), PINK)


def trans_blue_stained_glass():
    return tinted_glass(vblock("white_stained_glass"), BLUE)


def trans_wool():
    """White wool's fluffy texture in five flag stripes."""
    return flag_rows(vblock("white_wool"), lambda i: FLAG_RAMPS[i], curve=lambda t: 0.1 + 0.9 * t)


def trans_lantern():
    """A navy metal lantern with a pink-and-white glow (three animated frames, like vanilla)."""
    img = vblock("lantern")
    glow = lambda p, px: hsv(px)[1] > 0.35 and hsv(px)[0] < 0.2 and lum(px) > 0.35
    out = gradient_map(img, [hexc("141A3A"), hexc("2B3F7A"), hexc("4E79B8"), hexc("8FC3EA")], mask=lambda p, px: not glow(p, px))
    return gradient_map(img, [hexc("E06A92"), hexc("F5A9B8"), hexc("FFE3EC"), WHITE], mask=glow, out=out)


def trans_lantern_item():
    img = vitem("lantern")
    glow = lambda p, px: hsv(px)[1] > 0.35 and hsv(px)[0] < 0.2 and lum(px) > 0.35
    out = gradient_map(img, [hexc("141A3A"), hexc("2B3F7A"), hexc("4E79B8"), hexc("8FC3EA")], mask=lambda p, px: not glow(p, px))
    return gradient_map(img, [hexc("E06A92"), hexc("F5A9B8"), hexc("FFE3EC"), WHITE], mask=glow, out=out)


# ============================================================================================ gear
def is_diamond(px):
    h, s, v = hsv(px)
    return px[3] > 0 and 0.38 < h < 0.6 and s > 0.15


def recolor_gear(img, ramp=R_TRANS, curve=lambda t: 0.06 + 0.94 * t):
    """Swaps the diamond parts of a vanilla diamond item/armor texture for crystal colours."""
    return gradient_map(img, ramp, mask=lambda p, px: is_diamond(px), curve=curve)


def trans_tool(name):
    return recolor_gear(vitem(f"diamond_{name}"))


def trans_armor_item(name):
    """Armor icons get the flag: rows of blue, pink and white crystal plates."""
    img = vitem(f"diamond_{name}")
    ys = [y for (x, y) in pixels(img) if is_diamond(img.getpixel((x, y)))]
    top, bottom = min(ys), max(ys)
    bands = stripes5(bottom - top + 1)
    lo, hi = lum_range(img, lambda p, px: is_diamond(px))
    ramps = [[hexc("17396B"), hexc("2C6FB0"), BLUE, hexc("D6F4FE")], [hexc("7A2F50"), hexc("D16F8F"), PINK, hexc("FFE8EE")],
             [hexc("6F6B88"), hexc("BDBBD2"), hexc("EEEEF6"), WHITE]]
    stripe_ramp = [ramps[0], ramps[1], ramps[2], ramps[1], ramps[0]]
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if not is_diamond(px):
            continue
        c = sample(stripe_ramp[bands[y - top]], (lum(px) - lo) / (hi - lo))
        out.putpixel((x, y), (*c, px[3]))
    return out


def trans_armor_layer(rel):
    """Worn armor: the same crystal recolour as the tools (blue shadows, pink mid-tones, white shine)."""
    return recolor_gear(vextra(rel), curve=lambda t: 0.08 + 0.9 * t)


# ============================================================================================ treats
def trans_cookie():
    """Vanilla's cookie, but the chocolate chips are pink and blue candy chips."""
    img = vitem("cookie")
    chip = lambda p, px: px[3] > 0 and lum(px) < 0.28
    labels, count = components(img, chip, wrap=False)
    out = img.copy()
    ramps = [[hexc("8E3E60"), hexc("E07D9C"), hexc("FBC6D3")], [hexc("1F5288"), hexc("3EA5E6"), hexc("C4EDFF")],
             [hexc("8E8AA8"), hexc("E6E4F2"), WHITE]]
    for p, label in labels.items():
        px = img.getpixel(p)
        c = sample(ramps[label % 3], lum(px) / 0.28)
        out.putpixel(p, (*c, px[3]))
    return out


def trans_cake_item():
    img = vitem("cake")
    berry = lambda p, px: px[3] > 0 and hsv(px)[1] > 0.45 and (hsv(px)[0] > 0.9 or hsv(px)[0] < 0.04)
    sponge = lambda p, px: px[3] > 0 and not berry(p, px) and hsv(px)[1] > 0.3 and 0.03 < hsv(px)[0] < 0.15
    out = gradient_map(img, R_WOOD_PINK, mask=sponge)
    labels, count = components(img, berry, wrap=False)
    for p, label in labels.items():
        px = img.getpixel(p)
        c = (BLUE, PINK)[label % 2]
        out.putpixel(p, (*shade(c, 0.75 + 0.5 * lum(px)), px[3]))
    return out


def sprinkle_frosting(img):
    """Red cherry pixels on vanilla cake textures become pink and blue sprinkles."""
    red = lambda p, px: px[3] > 0 and hsv(px)[1] > 0.45 and (hsv(px)[0] > 0.9 or hsv(px)[0] < 0.04)
    labels, count = components(img, red, wrap=False)
    out = img.copy()
    for p, label in labels.items():
        px = img.getpixel(p)
        out.putpixel(p, (*shade((BLUE, PINK)[label % 2], 0.8 + 0.4 * lum(px)), px[3]))
    return out


def cake_layers(img):
    """The brown sponge becomes a five-layer trans flag sponge; the frosting stays creamy white."""
    sponge = lambda p, px: px[3] > 0 and hsv(px)[1] > 0.25 and 0.0 < hsv(px)[0] < 0.15
    rows = sorted({y for (x, y) in pixels(img) if sponge((x, y), img.getpixel((x, y)))})
    out = img.copy()
    if not rows:
        return out
    bands = stripes5(len(rows))
    band_of = {y: bands[i] for i, y in enumerate(rows)}
    lo, hi = lum_range(img, sponge)
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if sponge((x, y), px):
            c = sample(FLAG_RAMPS[band_of[y]], 0.15 + 0.85 * (lum(px) - lo) / (hi - lo))
            out.putpixel((x, y), (*c, px[3]))
    return sprinkle_frosting(out)


def trans_cake_top():
    return sprinkle_frosting(vblock("cake_top"))


def trans_cake_side():
    return cake_layers(vblock("cake_side"))


def trans_cake_inner():
    return cake_layers(vblock("cake_inner"))


def trans_cake_bottom():
    return gradient_map(vblock("cake_bottom"), R_WOOD_PINK)


def from_ascii(rows, palette):
    img = new(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                c = palette[ch]
                img.putpixel((x, y), (*c, 255) if len(c) == 3 else c)
    return img


def trans_donut():
    """A frosted ring donut: golden dough, pink icing with blue and white sprinkles, a real hole."""
    img = new()
    cx, cy = 7.5, 7.6
    dough = R_DOUGH
    icing = [hexc("B44D72"), hexc("DE7896"), hexc("F2A0B5"), hexc("FAC6D3"), hexc("FFE6EE")]
    rng = random.Random(4)
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, (y - cy) * 1.08
            r = math.hypot(dx, dy)
            if r > 7.4 or r < 2.3:
                continue
            light = -(dx + dy) / 10.0  # light comes from the top left
            # Wavy edge where the icing ends, with a couple of drips over the dough.
            angle = math.atan2(dy, dx)
            edge = 5.9 + 0.55 * math.sin(angle * 5.0 + 0.6) + (0.7 if abs(angle - 1.2) < 0.18 or abs(angle - 2.4) < 0.15 else 0.0)
            on_icing = 3.15 < r < edge
            if on_icing:
                t = 0.62 + light * 0.55 - (0.25 if r > edge - 0.7 else 0.0) - (0.18 if r < 3.75 else 0.0)
                c = sample(icing, t)
            else:
                t = 0.55 + light * 0.5 - (0.35 if r > 6.8 else 0.0) - (0.3 if r < 2.9 else 0.0)
                c = sample(dough, t)
            img.putpixel((x, y), (*c, 255))
    # Sprinkles.
    for (x, y, colour) in ((5, 3, BLUE), (9, 3, WHITE), (11, 5, BLUE), (3, 6, WHITE), (12, 8, PINK), (4, 9, BLUE),
                           (10, 11, WHITE), (6, 12, BLUE), (8, 4, hexc("9BE3FC")), (12, 10, BLUE), (3, 8, hexc("FFE6EE"))):
        if img.getpixel((x, y))[3] and img.getpixel((x, y))[0] > 170:
            img.putpixel((x, y), (*colour, 255))
    # Darker outline, like vanilla food sprites.
    outline = img.copy()
    for (x, y) in pixels(img):
        if img.getpixel((x, y))[3] == 0:
            continue
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if not (0 <= nx < 16 and 0 <= ny < 16) or img.getpixel((nx, ny))[3] == 0:
                c = img.getpixel((x, y))
                outline.putpixel((x, y), (*shade(c[:3], 0.68), 255))
                break
    return outline


def trans_cupcake():
    rows = [
        "......wW........",
        ".....pWWp.......",
        "....pPPPPp......",
        "...pPPwPPPp.....",
        "...PpPPPPPPp....",
        "..pPPPPbPPPPp...",
        "..PPwPPPPPwPP...",
        ".pPPPPPPPPPPPp..",
        ".dpppppppppppd..",
        "..BwBwBwBwBwB...",
        "..bWbWbWbWbWb...",
        "..BwBwBwBwBwB...",
        "...bWbWbWbWb....",
        "...BwBwBwBwB....",
        "....bbbbbbb.....",
        "................",
    ]
    palette = {
        "w": hexc("FFF4F7"), "W": WHITE, "p": hexc("D97A97"), "P": hexc("F5A9B8"), "d": hexc("B4557A"),
        "B": hexc("5BCEFA"), "b": hexc("2F7BBB"),
    }
    img = from_ascii(rows, palette)
    # Shift the cupcake right by one so it sits centred.
    out = new()
    out.paste(img, (1, 0))
    return out


def trans_macaron():
    rows = [
        "................",
        "................",
        "....bbbbbbbb....",
        "..bBBBBBBBBBBb..",
        ".bBlllBBBBBBBBb.",
        ".bBBBBBBBBBBBBb.",
        ".dbbbbbbbbbbbbd.",
        ".wWWWWWWWWWWWWw.",
        ".cwwwwwwwwwwwwc.",
        ".pPPPPPPPPPPPPp.",
        ".pPPPPPPPPPPPPp.",
        "..pPPPPPPPPPPp..",
        "....pppppppp....",
        "................",
        "................",
        "................",
    ]
    palette = {
        "b": hexc("3487C2"), "B": hexc("5BCEFA"), "l": hexc("CFF2FE"), "d": hexc("1F5288"),
        "w": hexc("E8E6F2"), "W": WHITE, "c": hexc("B9B6CB"),
        "p": hexc("C26683"), "P": hexc("F5A9B8"),
    }
    return from_ascii(rows, palette)


def trans_boba():
    rows = [
        ".........S......",
        "........sS......",
        "....oooosSoo....",
        "...oLLLLsSLLo...",
        "...oggggsSggo...",
        "....gPPPsSPg....",
        "....gPpPsSPg....",
        "....gPPPPPPg....",
        "....gPPpPPPg....",
        "....gPPPPPpg....",
        ".....gPPPPg.....",
        ".....gkPkPg.....",
        ".....gKkKkg.....",
        ".....gkKkKg.....",
        "......gggg......",
        "................",
    ]
    palette = {
        "S": hexc("5BCEFA"), "s": hexc("2F7BBB"), "o": hexc("D2D1E3"), "L": WHITE, "g": hexc("A9A7C0"),
        "P": hexc("F7C3CF"), "p": hexc("EFA3B6"), "k": hexc("3A2633"), "K": hexc("5C4053"),
    }
    return from_ascii(rows, palette)


def pride_blossom():
    """An oxeye daisy whose petals spin pink and blue around a white heart."""
    img = vblock("oxeye_daisy")
    petal = lambda p, px: px[3] > 0 and hsv(px)[1] < 0.2 and lum(px) > 0.55
    centre = lambda p, px: px[3] > 0 and 0.03 < hsv(px)[0] < 0.2 and hsv(px)[1] > 0.4 and lum(px) > 0.35
    centre_px = [p for p in pixels(img) if centre(p, img.getpixel(p))]
    cx = sum(p[0] for p in centre_px) / len(centre_px)
    cy = sum(p[1] for p in centre_px) / len(centre_px)
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        if petal(p, px):
            angle = math.atan2(p[1] - cy, p[0] - cx)
            colour = PINK if math.sin(angle * 2.0) > 0 else BLUE
            out.putpixel(p, (*shade(colour, 0.8 + 0.25 * lum(px)), 255))
        elif centre(p, px):
            out.putpixel(p, (*mix(WHITE, PINK, 0.15 * (1 - lum(px))), 255))
    return gradient_map(img, R_STEM, mask=lambda p, px: is_green(px), out=out)


# ============================================================================================ trans flowers
# Soft mint for stems and leaves, so flowers sit nicely on pastel grass instead of looking like dark litter.
R_STEM = [hexc(c) for c in ("2C5A47", "3B7660", "4F937A", "6DB095", "93CBB3")]
R_PETAL_PINK = [hexc(c) for c in ("A8466C", "D26F91", "F09CB3", "F9C6D4", "FFE9F0")]
R_PETAL_BLUE = [hexc(c) for c in ("2A6DA8", "3E9BD8", "6CC6F2", "A6E1FA", "DDF5FE")]
R_PETAL_WHITE = [hexc(c) for c in ("A9A6C0", "CAC7DA", "E4E2EE", "F4F3F9", "FFFFFF")]
R_PETAL_LAVENDER = [hexc(c) for c in ("6C5AA6", "927FCC", "B9A8E6", "DACFF6", "F4EFFE")]


def is_green(px):
    h, s, v = hsv(px)
    return px[3] > 0 and 0.17 < h < 0.5 and s > 0.18


def flower(img, petal_ramp_at, stem_ramp=R_STEM):
    """Mint stems, and petals coloured by `petal_ramp_at(pos, label)` (label = which petal blob it is,
    numbered top to bottom)."""
    out = gradient_map(img, stem_ramp, mask=lambda p, px: is_green(px))
    petal = lambda p, px: px[3] > 0 and not is_green(px)
    labels, count = components(img, petal, wrap=False)
    # Number the blobs from the top of the texture down.
    tops = {}
    for p, label in labels.items():
        tops[label] = min(tops.get(label, 99), p[1])
    order = {label: i for i, label in enumerate(sorted(tops, key=lambda k: (tops[k], k)))}
    lo, hi = lum_range(img, petal)
    for p, label in labels.items():
        px = img.getpixel(p)
        c = sample(petal_ramp_at(p, order[label]), (lum(px) - lo) / (hi - lo))
        out.putpixel(p, (*c, px[3]))
    return out


def trans_tulip():
    """A blue tulip with a pink heart."""
    return flower(vblock("pink_tulip"), lambda p, i: R_PETAL_PINK if p[0] in (7, 8) else R_PETAL_BLUE)


def pearl_daisy():
    """A pearly white daisy with a pink centre."""
    img = vblock("oxeye_daisy")
    centre = lambda p, px: 0.03 < hsv(px)[0] < 0.2 and hsv(px)[1] > 0.4
    return flower(img, lambda p, i: R_PETAL_PINK if centre(p, img.getpixel(p)) else R_PETAL_WHITE)


def sky_bell():
    """Cornflower bells in trans blue."""
    return flower(vblock("cornflower"), lambda p, i: R_PETAL_BLUE)


def flag_lily():
    """Lily of the valley whose bells go pink, white, blue from top to bottom."""
    return flower(vblock("lily_of_the_valley"), lambda p, i: (R_PETAL_PINK, R_PETAL_WHITE, R_PETAL_BLUE)[i % 3])


def lavender_puff():
    """An allium puffball in lavender with pink highlights."""
    return flower(vblock("allium"), lambda p, i: mix_ramp(R_PETAL_LAVENDER, R_PETAL_PINK, 0.25))


def trans_orchid():
    """Blue orchid blooms in alternating pink and blue."""
    return flower(vblock("blue_orchid"), lambda p, i: (R_PETAL_PINK, R_PETAL_BLUE)[i % 2])


def heart_bloom():
    """A little pink heart in bloom on a mint stem."""
    base = vblock("pink_tulip")
    out = gradient_map(base, R_STEM, mask=lambda p, px: is_green(px))
    # Clear the tulip head and draw a heart in its place.
    for p in pixels(base):
        px = base.getpixel(p)
        if px[3] > 0 and not is_green(px):
            out.putpixel(p, CLEAR)
    heart = [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."]
    ox, oy = 4, 1
    for dy, row in enumerate(heart):
        for dx, ch in enumerate(row):
            if ch != "#":
                continue
            light = (dx + dy) / 10.0
            c = sample(R_PETAL_PINK, 0.85 - light * 0.6)
            out.putpixel((ox + dx, oy + dy), (*c, 255))
    out.putpixel((ox + 1, oy + 1), (*WHITE, 255))
    out.putpixel((ox + 2, oy + 1), (*R_PETAL_PINK[4], 255))
    return out


def pride_peony(half):
    """Peony blossoms in pink, blue and white."""
    return flower(vblock(f"peony_{half}"), lambda p, i: (R_PETAL_PINK, R_PETAL_BLUE, R_PETAL_WHITE)[i % 3],
                  stem_ramp=mix_ramp(R_STEM, [shade(c, 0.75) for c in R_STEM], 0.4))


def trans_petals():
    """Fallen petals in pink, blue and white clusters."""
    return flower(vblock("pink_petals"), lambda p, i: (R_PETAL_PINK, R_PETAL_BLUE, R_PETAL_WHITE)[i % 3])


def trans_petals_item():
    return flower(vitem("pink_petals"), lambda p, i: (R_PETAL_PINK, R_PETAL_BLUE, R_PETAL_WHITE)[i % 3])


def trans_petals_stem():
    return gradient_map(vblock("pink_petals_stem"), R_STEM)


FLOWERS = ("trans_tulip", "pearl_daisy", "sky_bell", "flag_lily", "lavender_puff", "trans_orchid", "heart_bloom")


# ============================================================================================ pride oven
def oven_recolor(img):
    """Smoker -> Pride Oven: pink wood, pearly stone and iron, and pink-and-white flames."""
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3] == 0:
            continue
        h, s, v = hsv(px)
        l = lum(px)
        if s > 0.45 and (h < 0.17 or h > 0.95) and v > 0.55:
            c = sample([hexc("D9467A"), hexc("F27FA2"), hexc("F9C6D4"), WHITE], (v - 0.55) / 0.45)   # flames
        elif s > 0.22 and 0.02 < h < 0.15:
            c = sample(R_WOOD_PINK, l * 1.6)                                                         # logs
        else:
            c = sample(mix_ramp(R_PEARL, R_LAVENDER, 0.35), l * 1.25)                               # stone, iron
        out.putpixel(p, (*c, px[3]))
    return out


def pride_oven_front():
    return oven_recolor(vblock("smoker_front_on"))


def pride_oven_side():
    return oven_recolor(vblock("smoker_side"))


def pride_oven_top():
    return oven_recolor(vblock("smoker_top"))


def pride_oven_bottom():
    return oven_recolor(vblock("smoker_bottom"))


# ============================================================================================ silly cat bits
def silly_cat_spawn_egg():
    """Spawn eggs are mob faces in 26.2, so this is the Silly Cat's face: big glossy eyes,
    a white muzzle, a pink nose and its tongue out."""
    rows = [
        "................",
        "................",
        "..oo........oo..",
        ".opgo......ogpo.",
        ".oppgoooooogppo.",
        ".ogggsgsgsgsggo.",
        ".ogGGGGGGGGGGgo.",
        ".oghkkGGGGhkkgo.",
        ".ogkkkGwwGkkkgo.",
        ".oGkkkwppwkkkGo.",
        ".oGGGwwwwwwGGGo.",
        ".ogGwwowwowwGgo.",
        "..ogGwwttwwGgo..",
        "...oggwtTwggo...",
        "....ooottooo....",
        "......oTTo......",
    ]
    assert all(len(r) == 16 for r in rows)
    palette = {
        "o": hexc("3A3940"), "g": hexc("8F8A91"), "G": hexc("B6B1B6"), "s": hexc("5D5860"), "w": hexc("F3F1EE"),
        "p": hexc("E99AB0"), "k": hexc("141118"), "h": WHITE, "t": hexc("F07A9A"), "T": hexc("C9506F"),
    }
    return from_ascii(rows, palette)


def slobbered_icon():
    """18x18 effect icon: a big glossy drop of spit with a heart in it."""
    rows = [
        "..................",
        "........ww........",
        ".......wWWw.......",
        ".......wWWw.......",
        "......wWLWWw......",
        "......wWLWWw......",
        ".....wWLLWWWw.....",
        "....wWWLWWWWWw....",
        "....wWWWWWWWWw....",
        "...wWWpWWWpWWWw...",
        "...wWpPpWpPpWWw...",
        "...wWpPPPPPpWWw...",
        "...wWWpPPPpWWWw...",
        "....wWWpPpWWWw....",
        "....wWWWpWWWWw....",
        ".....wwWWWWww.....",
        ".......wwww.......",
        "..................",
    ]
    palette = {"w": hexc("B98BB0"), "W": hexc("EADCF2"), "L": WHITE, "p": hexc("D97A97"), "P": hexc("F5A9B8")}
    return from_ascii(rows, palette)


def saliva_frames(width=320, height=180, frames=6):
    """Full-screen cat spit: gloopy blobs around the edges and a big smear across the middle, with
    glossy highlights. Frame 0 is full strength; later frames are progressively fainter (for fading)."""
    rng = random.Random(42)
    base = Image.new("RGBA", (width, height), CLEAR)
    alpha = [[0.0] * width for _ in range(height)]

    def blob(cx, cy, rx, ry, strength):
        for y in range(max(0, int(cy - ry - 2)), min(height, int(cy + ry + 3))):
            for x in range(max(0, int(cx - rx - 2)), min(width, int(cx + rx + 3))):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
                if d < 1.0:
                    alpha[y][x] = max(alpha[y][x], strength * (1.0 - d ** 2))

    # Goo collecting at the top edge with drips hanging down.
    for i in range(26):
        cx = rng.uniform(0, width)
        blob(cx, rng.uniform(-6, 10), rng.uniform(14, 34), rng.uniform(8, 20), 0.85)
        if rng.random() < 0.6:
            length = rng.uniform(20, 70)
            for k in range(int(length)):
                blob(cx + math.sin(k * 0.15) * 1.5, 10 + k, 2.4 + 2.0 * (1 - k / length), 2.0, 0.8)
            blob(cx, 10 + length, 4.5, 5.0, 0.85)
    # Splats around the sides and corners.
    for i in range(40):
        edge = rng.choice(("left", "right", "bottom", "corner"))
        if edge == "left":
            cx, cy = rng.uniform(-10, 40), rng.uniform(0, height)
        elif edge == "right":
            cx, cy = rng.uniform(width - 40, width + 10), rng.uniform(0, height)
        elif edge == "bottom":
            cx, cy = rng.uniform(0, width), rng.uniform(height - 30, height + 10)
        else:
            cx, cy = rng.choice((0, width)), rng.choice((0, height))
        blob(cx, cy, rng.uniform(10, 30), rng.uniform(10, 26), rng.uniform(0.6, 0.9))
    # The big lick smear across the middle of the screen.
    for k in range(60):
        t = k / 59.0
        cx = width * (0.18 + 0.64 * t)
        cy = height * (0.62 - 0.22 * math.sin(t * math.pi))
        blob(cx, cy, 16 + 10 * math.sin(t * math.pi), 9 + 5 * math.sin(t * math.pi), 0.42)

    for y in range(height):
        for x in range(width):
            a = alpha[y][x]
            if a <= 0.02:
                continue
            # Glossy: brighter towards the top-left of each blob (approximated by the alpha gradient).
            up = alpha[y - 1][x] if y > 0 else a
            left = alpha[y][x - 1] if x > 0 else a
            gloss = max(0.0, min(1.0, (a - up) * 6 + (a - left) * 6))
            c = mix(hexc("F7E3EE"), WHITE, gloss)
            base.putpixel((x, y), (*c, round(255 * min(0.82, a * 0.9))))
    # Shiny highlights.
    for i in range(55):
        x, y = rng.randrange(width), rng.randrange(height)
        if alpha[y][x] > 0.35:
            for dx in range(-1, 2):
                if 0 <= x + dx < width:
                    base.putpixel((x + dx, y), (255, 255, 255, 235))
    base = base.filter(ImageFilter.SMOOTH)

    result = []
    for i in range(frames):
        factor = 1.0 if i == 0 else (1.0 - i / frames) ** 1.3
        frame = base.copy()
        a = frame.getchannel("A").point(lambda v: round(v * factor))
        frame.putalpha(a)
        result.append(frame)
    return result


# ============================================================================================ entities
def box_faces(u, v, w, h, d):
    """UV rectangles (x0, y0, width, height) of a model box at texture offset (u, v), like vanilla's
    ModelPart.Cube: top, bottom, right (west), front (north), left (east), back (south)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def silly_cat():
    """The Silly Cat's 64x32 skin, matching SillyCatModel: a grey-brown tabby with darker stripes,
    a white muzzle, chest, belly and socks, big glossy black eyes, pink ears, nose and tongue."""
    img = new(64, 32)
    rng = random.Random(77)
    fur = [hexc("6A6158"), hexc("8A8178"), hexc("A39A92"), hexc("B9B0A7")]
    stripe = hexc("554D46")
    white = [hexc("D9D2CB"), hexc("EEE9E3"), hexc("FAF7F3")]
    pink = hexc("E99AB0")

    def put(x, y, c, jitter=6):
        j = rng.randint(-jitter, jitter)
        img.putpixel((x, y), (*[max(0, min(255, v + j)) for v in c], 255))

    def fill_face(rect, colour_at):
        x0, y0, w, h = rect
        for yy in range(h):
            for xx in range(w):
                put(x0 + xx, y0 + yy, colour_at(xx, yy, w, h))

    # ---- head (6x5x5 at 0,0)
    head = box_faces(0, 0, 6, 5, 5)
    fill_face(head["top"], lambda x, y, w, h: stripe if (x in (1, 4) and y >= 2) or (x in (2, 3) and y == 4) else fur[2])
    fill_face(head["bottom"], lambda x, y, w, h: white[1])
    fill_face(head["back"], lambda x, y, w, h: stripe if x in (1, 4) and y < 3 else fur[1])

    def cheek(front_at_right):
        def colour(x, y, w, h):
            near_front = (x == w - 1) if front_at_right else (x == 0)
            if y >= 3:
                return white[1] if near_front else white[0]
            return stripe if y == 1 and not near_front else fur[2]
        return colour

    fill_face(head["right"], cheek(True))
    fill_face(head["left"], cheek(False))
    face = [
        "GsGGsG",
        "hkGGhk",
        "kkGGkk",
        "GwwwwG",
        "wwwwww",
    ]
    face_palette = {"G": fur[2], "s": stripe, "h": WHITE, "k": hexc("141118"), "w": white[1]}
    fx, fy, _, _ = head["front"]
    for y, row in enumerate(face):
        for x, ch in enumerate(row):
            put(fx + x, fy + y, face_palette[ch], 0 if ch in "hk" else 5)

    # ---- muzzle (4x2x2 at 22,0)
    muzzle = box_faces(22, 0, 4, 2, 2)
    for name in ("top", "bottom", "right", "left", "back"):
        fill_face(muzzle[name], lambda x, y, w, h: white[2])
    mx, my, _, _ = muzzle["front"]
    for x, c in enumerate((white[2], hexc("E78AA3"), hexc("E78AA3"), white[2])):
        put(mx + x, my, c, 0)
    for x, c in enumerate((white[1], hexc("8B6B72"), hexc("8B6B72"), white[1])):
        put(mx + x, my + 1, c, 0)

    # ---- ears (2x2x1 at 34,0 and 40,0)
    for u, tip_left in ((34, True), (40, False)):
        ear = box_faces(u, 0, 2, 2, 1)
        for name in ("top", "bottom", "right", "left", "back"):
            fill_face(ear[name], lambda x, y, w, h: fur[1])
        ex, ey, _, _ = ear["front"]
        put(ex, ey, stripe if tip_left else fur[2])
        put(ex + 1, ey, fur[2] if tip_left else stripe)
        put(ex, ey + 1, pink, 2)
        put(ex + 1, ey + 1, pink, 2)

    # ---- tongue (2x1x5 at 46,0): lighter at the tip, with a darker groove down the middle of the top
    tongue = box_faces(46, 0, 2, 1, 5)
    for name, rect in tongue.items():
        if name == "top":
            fill_face(rect, lambda x, y, w, h: hexc("D9607F") if x == 0 and y > 0 else hexc("F48CA8") if y == 0 else hexc("F07A9A"))
        else:
            fill_face(rect, lambda x, y, w, h: hexc("E2688A"))

    # ---- body (5x4x9 at 0,10)
    body = box_faces(0, 10, 5, 4, 9)
    fill_face(body["top"], lambda x, y, w, h: stripe if (y % 3 == 1 and x in (0, 1, 3, 4)) or x == 2 and y % 3 != 2 else fur[2])
    fill_face(body["bottom"], lambda x, y, w, h: white[1] if y > 1 else white[0])
    fill_face(body["front"], lambda x, y, w, h: white[2] if 0 < x < w - 1 or y > 0 else white[1])
    fill_face(body["back"], lambda x, y, w, h: stripe if y == 0 and x in (1, 3) else fur[1])

    def flank(front_at_right):
        def colour(x, y, w, h):
            distance_to_front = (w - 1 - x) if front_at_right else x
            if y == h - 1:
                return white[0] if distance_to_front > 1 else white[1]
            if distance_to_front <= 1:
                return white[1] if y >= 1 else fur[2]
            return stripe if distance_to_front % 3 == 0 and y < h - 1 else fur[2] if y == 0 else fur[1]
        return colour

    fill_face(body["right"], flank(True))
    fill_face(body["left"], flank(False))

    # ---- legs (2x3x2 at 28,10): grey with white socks and pink toe beans
    leg = box_faces(28, 10, 2, 3, 2)
    for name in ("right", "front", "left", "back"):
        fill_face(leg[name], lambda x, y, w, h: white[2] if y == h - 1 else (stripe if y == 0 and x == 1 else fur[1]))
    fill_face(leg["top"], lambda x, y, w, h: fur[1])
    fill_face(leg["bottom"], lambda x, y, w, h: pink if (x + y) % 2 == 0 else white[1])

    # ---- tail (1x1x7 at 36,10): ringed, with a dark tip
    tail = box_faces(36, 10, 1, 1, 7)

    def ring(distance_from_tip):
        return stripe if distance_from_tip == 0 or distance_from_tip % 2 == 1 else fur[2]

    fill_face(tail["top"], lambda x, y, w, h: ring(y))
    fill_face(tail["bottom"], lambda x, y, w, h: ring(y))
    fill_face(tail["right"], lambda x, y, w, h: ring(x))
    fill_face(tail["left"], lambda x, y, w, h: ring(w - 1 - x))
    fill_face(tail["front"], lambda x, y, w, h: fur[2])
    fill_face(tail["back"], lambda x, y, w, h: stripe)
    return img


def sheep_stripe(x, y):
    """Which flag stripe a pixel of the adult sheep wool texture gets (see the UV notes in the docs)."""
    six = [0, 1, 2, 2, 3, 4]
    if x >= 28:  # body box (28,8) 8x16x6, rotated so its sides run along the sheep
        if y >= 14:
            if 28 <= x < 34:
                return six[x - 28]
            if 42 <= x < 48:
                return six[x - 42]
            return 0  # back and belly: the outer blue stripes
        return six[min(5, y - 8)]  # chest and rump: rows
    if y < 16:  # head box (0,0) 6x6x6
        return six[min(5, y - 6)] if y >= 6 else 0
    return six[y - 20] if 20 <= y < 26 else 0  # legs (0,16) 4x6x4


def trans_sheep_wool(rel, generic=False):
    img = vextra(rel)
    lo, hi = lum_range(img)
    out = img.copy()
    six = [0, 1, 2, 2, 3, 4]
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3] == 0:
            continue
        stripe = six[y % 6] if generic else sheep_stripe(x, y)
        t = (lum(px) - lo) / (hi - lo)
        c = sample(FLAG_RAMPS[stripe], 0.35 + 0.65 * t)
        out.putpixel((x, y), (*c, px[3]))
    return out


def trans_baker():
    """The Trans Baker's outfit: the butcher's apron and headband in trans colours, plus a chef hat."""
    img = vextra("entity/villager/profession/butcher.png")
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3] == 0:
            continue
        h, s, v = hsv(px)
        if s > 0.3 and (h < 0.05 or h > 0.9):  # red headband and stitching -> pink/blue
            colour = BLUE if (x // 2) % 2 == 0 else PINK
            out.putpixel((x, y), (*shade(colour, 0.75 + 0.35 * v), 255))
        elif s < 0.15:  # white apron -> keep, a touch of pink
            out.putpixel((x, y), (*mix(px[:3], PINK, 0.08), 255))
    # A puffy chef hat on the hat layer (box at 32,0 sized 8x10x8): top face and the rows above the band.
    hat_white = [hexc("E8E6F2"), hexc("F6F5FB"), WHITE]
    rng = random.Random(3)
    for x in range(40, 48):
        for y in range(0, 8):
            out.putpixel((x, y), (*hat_white[rng.randrange(3)], 255))
    for x in range(32, 64):
        for y in range(8, 10):
            out.putpixel((x, y), (*hat_white[rng.randrange(3)], 255))
    return out


# ============================================================================================ sky + icon
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
    """Mod icon: a rounded trans flag tile with a glossy heart and a little crystal sparkle."""
    size = 128
    img = Image.new("RGBA", (size, size), CLEAR)
    bands = stripes5(size)
    for y in range(size):
        for x in range(size):
            cxd = max(0, max(14 - x, x - 113))
            cyd = max(0, max(14 - y, y - 113))
            if cxd * cxd + cyd * cyd > 196:
                continue
            c = FLAG[bands[y]]
            img.putpixel((x, y), (*shade(c, 0.97 + 0.06 * ((x + y) % 7 == 0)), 255))
    heart = HEARTS[11]
    scale = 7
    ox, oy = 64 - 11 * scale // 2, 64 - 11 * scale // 2 + 4
    for dy, row in enumerate(heart):
        for dx, ch in enumerate(row):
            if ch != "#":
                continue
            for py in range(scale):
                for px in range(scale):
                    gx, gy = dx * scale + px, dy * scale + py
                    gloss = gx + gy < 26 and (gx - 12) ** 2 + (gy - 12) ** 2 < 60
                    colour = WHITE if not gloss else hexc("FFFFFF")
                    shadow = (dy * scale + py) > 9 * scale - 3 and heart[min(10, dy + 1)][dx] != "#"
                    img.putpixel((ox + gx, oy + gy), (*(hexc("F3E7EE") if shadow else colour), 255))
    for dy, row in enumerate(heart):  # outline
        for dx, ch in enumerate(row):
            if ch != "#":
                continue
            for ndx, ndy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = dx + ndx, dy + ndy
                if 0 <= ny < 11 and 0 <= nx < 11 and heart[ny][nx] == "#":
                    continue
                for i in range(scale):
                    if ndx:
                        px = ox + dx * scale + (scale - 1 if ndx > 0 else 0)
                        img.putpixel((px, oy + dy * scale + i), (*hexc("C9587A"), 255))
                    else:
                        py = oy + dy * scale + (scale - 1 if ndy > 0 else 0)
                        img.putpixel((ox + dx * scale + i, py), (*hexc("C9587A"), 255))
    # sparkle
    for (sx, sy, r) in ((101, 26, 6), (24, 98, 4)):
        for k in range(-r, r + 1):
            img.putpixel((sx + k, sy), (*WHITE, 255))
            img.putpixel((sx, sy + k), (*WHITE, 255))
    return img


# ============================================================================================ main
def main():
    blocks = {
        # terrain
        "trans_grass_block_top": trans_grass_block_top(), "trans_grass_block_side": trans_grass_block_side(),
        "trans_grass_block_side_overlay": trans_grass_block_side_overlay(), "trans_grass_block_snow": trans_grass_block_snow(),
        "trans_dirt": trans_dirt(), "trans_sand": trans_sand(), "trans_stone": trans_stone(),
        "trans_cobblestone": trans_cobblestone(), "trans_stone_bricks": trans_stone_bricks(),
        "cracked_trans_stone_bricks": cracked_trans_stone_bricks(), "chiseled_trans_stone_bricks": chiseled_trans_stone_bricks(),
        "trans_sandstone": sandstone("sandstone"), "trans_sandstone_top": sandstone("sandstone_top"),
        "trans_sandstone_bottom": sandstone("sandstone_bottom"), "cut_trans_sandstone": sandstone("cut_sandstone"),
        "chiseled_trans_sandstone": chiseled_trans_sandstone(),
        # deepslate
        "trans_deepslate": trans_deepslate(), "trans_deepslate_top": trans_deepslate("deepslate_top"),
        "cobbled_trans_deepslate": trans_deepslate("cobbled_deepslate"),
        "polished_trans_deepslate": trans_deepslate("polished_deepslate"),
        "trans_deepslate_bricks": deepslate_bricks("deepslate_bricks", 3),
        "cracked_trans_deepslate_bricks": deepslate_bricks("cracked_deepslate_bricks", 3),
        "trans_deepslate_tiles": deepslate_bricks("deepslate_tiles", 1),
        "cracked_trans_deepslate_tiles": deepslate_bricks("cracked_deepslate_tiles", 1),
        "chiseled_trans_deepslate": chiseled_trans_deepslate(),
        # granite, diorite, andesite, gravel
        "trans_granite": trans_granite(), "polished_trans_granite": trans_granite("polished_granite"),
        "trans_diorite": trans_diorite(), "polished_trans_diorite": trans_diorite("polished_diorite"),
        "trans_andesite": trans_andesite(), "polished_trans_andesite": trans_andesite("polished_andesite"),
        "trans_gravel": trans_gravel(),
        **trans_ores(),
        "trans_glass": trans_glass(), "trans_glass_pane_top": trans_glass_pane_top(),
        # crystals
        "trans_crystal_ore": trans_crystal_ore(), "trans_deepslate_crystal_ore": trans_deepslate_crystal_ore(),
        "trans_crystal_block": trans_crystal_block(),
        "trans_crystal_cluster": trans_crystal_cluster(),
        # wood
        "trans_log": trans_log(), "trans_log_top": trans_log_top(), "stripped_trans_log": stripped_trans_log(),
        "stripped_trans_log_top": stripped_trans_log_top(), "trans_planks": trans_planks(), "trans_leaves": trans_leaves(),
        "trans_sapling": trans_sapling(), "trans_door_top": trans_door_top(), "trans_door_bottom": trans_door_bottom(),
        "trans_trapdoor": trans_trapdoor(), "pride_blossom": pride_blossom(),
        # flowers
        **{name: globals()[name]() for name in FLOWERS},
        "pride_peony_top": pride_peony("top"), "pride_peony_bottom": pride_peony("bottom"),
        "trans_petals": trans_petals(), "trans_petals_stem": trans_petals_stem(),
        # glass, wool, light
        "trans_stained_glass": trans_stained_glass(), "trans_stained_glass_pane_top": trans_stained_glass_pane_top(),
        "trans_pink_stained_glass": trans_pink_stained_glass(),
        "trans_pink_stained_glass_pane_top": tinted_glass(vblock("white_stained_glass_pane_top"), PINK, 1.0),
        "trans_blue_stained_glass": trans_blue_stained_glass(),
        "trans_blue_stained_glass_pane_top": tinted_glass(vblock("white_stained_glass_pane_top"), BLUE, 1.0),
        "trans_wool": trans_wool(), "trans_lantern": trans_lantern(),
        # bakery
        "trans_cake_top": trans_cake_top(), "trans_cake_side": trans_cake_side(), "trans_cake_inner": trans_cake_inner(),
        "trans_cake_bottom": trans_cake_bottom(), "pride_oven_front": pride_oven_front(), "pride_oven_side": pride_oven_side(),
        "pride_oven_top": pride_oven_top(), "pride_oven_bottom": pride_oven_bottom(),
    }
    for name, img in blocks.items():
        save(img, f"block/{name}.png")
    for name in ("trans_crystal_cluster", "trans_sapling", "pride_blossom", *FLOWERS, "pride_peony_top", "pride_peony_bottom",
                 "trans_petals", "trans_petals_stem"):
        save_mcmeta(f"block/{name}.png", CUTOUT)
    save_mcmeta("block/trans_leaves.png", LEAVES_META)
    for name in ("trans_stained_glass", "trans_pink_stained_glass", "trans_blue_stained_glass", "trans_glass"):
        save_mcmeta(f"block/{name}.png", GLASS_META)
    save_mcmeta("block/trans_lantern.png", {"animation": {"frametime": 8}})
    save_mcmeta("block/pride_oven_front.png", {"animation": {"interpolate": False, "frametime": 4}})

    items = {
        "trans_crystal": trans_crystal_item(),
        "trans_sword": trans_tool("sword"), "trans_pickaxe": trans_tool("pickaxe"), "trans_axe": trans_tool("axe"),
        "trans_shovel": trans_tool("shovel"), "trans_hoe": trans_tool("hoe"),
        "trans_helmet": trans_armor_item("helmet"), "trans_chestplate": trans_armor_item("chestplate"),
        "trans_leggings": trans_armor_item("leggings"), "trans_boots": trans_armor_item("boots"),
        "trans_donut": trans_donut(), "trans_cookie": trans_cookie(), "trans_cupcake": trans_cupcake(),
        "trans_macaron": trans_macaron(), "trans_boba": trans_boba(), "trans_cake": trans_cake_item(),
        "silly_cat_spawn_egg": silly_cat_spawn_egg(), "trans_door": trans_door_item(), "trans_lantern": trans_lantern_item(),
        "trans_petals": trans_petals_item(),
    }
    for name, img in items.items():
        save(img, f"item/{name}.png")

    save(trans_armor_layer("entity/equipment/humanoid/diamond.png"), "entity/equipment/humanoid/trans_crystal.png")
    save(trans_armor_layer("entity/equipment/humanoid_leggings/diamond.png"), "entity/equipment/humanoid_leggings/trans_crystal.png")

    baker = trans_baker()
    for kind in ("villager", "zombie_villager"):
        save(baker, f"entity/{kind}/profession/trans_baker.png")
        save_mcmeta(f"entity/{kind}/profession/trans_baker.png", {"villager": {"hat": "full"}})

    save(silly_cat(), "entity/silly_cat/silly_cat.png")
    save(trans_sheep_wool("entity/sheep/sheep_wool.png"), "entity/sheep/trans_sheep_wool.png")
    save(trans_sheep_wool("entity/sheep/sheep_wool_undercoat.png"), "entity/sheep/trans_sheep_wool_undercoat.png")
    save(trans_sheep_wool("entity/sheep/sheep_wool_baby.png", generic=True), "entity/sheep/trans_sheep_wool_baby.png")

    save(slobbered_icon(), "mob_effect/slobbered.png")
    for i, frame in enumerate(saliva_frames()):
        save(frame, f"gui/saliva/saliva_{i}.png")
    save(heart_clouds(), "environment/heart_clouds.png")

    icon_path = os.path.join(OUT, "icon.png")
    icon().save(icon_path)
    print("Textures written to", os.path.normpath(os.path.join(OUT, "textures")))


if __name__ == "__main__":
    main()
