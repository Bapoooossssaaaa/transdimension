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
# Deep slate: twilight indigo with mauve-pink light (it used to be plain blue-grey, which didn't look trans).
R_DEEPSLATE = [hexc(c) for c in ("161A33", "232A4C", "373862", "54477A", "7A5C8E", "A684AE")]
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
# Natural deepslate is layered: every four rows the slate leans faintly pink, then blue, like sediment in the flag's colours.
R_DEEPSLATE_PINK = mix_ramp(R_DEEPSLATE, [hexc(c) for c in ("2A1730", "40223F", "5A3155", "7A4670", "9E6690", "C493B4")], 0.4)
R_DEEPSLATE_BLUE = mix_ramp(R_DEEPSLATE, [hexc(c) for c in ("141C36", "1F2C52", "2D4170", "435C8E", "6683AE", "93AED0")], 0.4)


def layered(img, ramps, period=4):
    """Gradient-maps rows of an image with ramps in turn, `period` rows each (strata)."""
    lo, hi = lum_range(img)
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        t = (lum(px) - lo) / (hi - lo)
        out.putpixel((x, y), (*sample(ramps[(y // period) % len(ramps)], t), px[3]))
    return out


def trans_deepslate(name="deepslate", strata=False):
    """Twilight slate; natural and cobbled deepslate show faint pink and blue strata."""
    base = vblock(name)
    img = layered(base, [R_DEEPSLATE, R_DEEPSLATE_PINK, R_DEEPSLATE, R_DEEPSLATE_BLUE]) if strata else gradient_map(base, R_DEEPSLATE)
    return glints(img, base, R_DEEPSLATE, top=0.985, bottom=-1.0, chance=0.4, pink_mix=0.3)


def deepslate_bricks(name, seed):
    """Dark slate bricks or tiles; each one leans faintly pink or blue."""
    deep_mortar = [hexc(c) for c in ("0C0A16", "141123", "1D1930", "27223F", "332C4F", "413862")]
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
    """Pebbles in pink, blue and white, set in grey grit."""
    img = vblock("gravel")
    lo, hi = lum_range(img)
    norm = lambda px: (lum(px) - lo) / (hi - lo)
    pebble = lambda p, px: norm(px) > 0.42
    labels, count = components(img, pebble)
    rng = random.Random(31)
    # Most pebbles are pink, blue or white (it used to be mostly grey, which didn't look trans), but soft: the colours
    # are mixed well into grey and kept a little dark, as brighter pebbles looked garish.
    ramps = [mix_ramp(R_STONE, light_ramp(PINK, 0.5), 0.38), mix_ramp(R_STONE, light_ramp(BLUE, 0.5), 0.35),
             mix_ramp(R_STONE, R_PEARL, 0.35), R_STONE, mix_ramp(R_STONE, light_ramp(PINK, 0.5), 0.25)]
    # Vanilla's pebbles touch, so each one is split into 3x3 chunks, each its own colour.
    choice = {}
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        t = norm(px)
        if p in labels:
            key = (labels[p], p[0] // 3, p[1] // 3)
            if key not in choice:
                choice[key] = ramps[rng.randrange(len(ramps))]
            c = sample(choice[key], 0.04 + 0.66 * t)
        else:
            c = sample(R_MORTAR, t * 1.2)
        out.putpixel(p, (*c, px[3]))
    return out


# ============================================================================================ ores
def ore_on(base_new, base_vanilla, ore_vanilla, ramp):
    """Moves the ore of a vanilla ore texture onto a new background, so ores blend with the rock round them.

    Vanilla redraws the stone a little differently on every ore texture, so pixels close to the vanilla background
    count as background and take the new rock's own pixel. Clearly coloured pixels (the ore itself) keep their vanilla
    colours; the rest of the ore (outlines, shading, coal) is re-shaded with the new rock's palette. Deepslate's darkest
    pixels are faintly blue, so "coloured" means real colour (a spread of over 30 between channels), not saturation."""
    out = base_new.copy()
    lo, hi = lum_range(base_vanilla)
    for p in pixels(ore_vanilla):
        px = ore_vanilla.getpixel(p)
        bg = base_vanilla.getpixel(p)
        if max(abs(px[i] - bg[i]) for i in range(3)) < 24:
            continue
        if max(px[:3]) - min(px[:3]) > 30:
            out.putpixel(p, px)
        else:
            c = sample(ramp, (lum(px) - lo) / (hi - lo))
            out.putpixel(p, (*c, px[3]))
    return out


ORES = ("coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald")


def trans_ores():
    stone_new, deep_new = trans_stone(), trans_deepslate(strata=True)
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
# Trans crystals are a rare gem (cut like a diamond, in the flag's blue, pink and white); prisms are the common, softer
# lilac-pink crystal rock of geodes, spikes and clusters.
R_PRISM = [hexc(c) for c in ("5E5299", "8E7FD0", "BBAEEC", "EFBFDA", "FBE2EE", "FFFFFF")]


def trans_crystal_block():
    """A polished block of cut trans crystal."""
    return gradient_map(vblock("diamond_block"), R_TRANS)


def pastel_prism():
    return gradient_map(vblock("amethyst_block"), R_PRISM)


def trans_crystal_cluster():
    """The prism cluster (its id is older than its name)."""
    return gradient_map(vblock("amethyst_cluster"), R_PRISM, curve=lambda t: 0.1 + 0.9 * t)


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
    out = ore_on(trans_deepslate(strata=True), deep_v, ore, R_DEEPSLATE)
    labels, count = components(ore, gem)
    ramps = [[hexc("1F5288"), hexc("3EA5E6"), BLUE, hexc("E8F9FF")], [hexc("8E3E60"), hexc("E07D9C"), PINK, hexc("FFF0F4")]]
    lo, hi = lum_range(ore, gem)
    for p, label in labels.items():
        px = ore.getpixel(p)
        c = sample(ramps[label % 2], (lum(px) - lo) / (hi - lo))
        out.putpixel(p, (*c, px[3]))
    return out


def trans_crystal_item():
    """A cut gem: the diamond sprite with the flag's blue, pink and white."""
    return gradient_map(vitem("diamond"), R_TRANS, curve=lambda t: 0.04 + 0.96 * t)


# Crystal Alloy: an ingot of crystal and prism, blue at the edges and sides, pink on top, with white shine.
R_ALLOY = [hexc(c) for c in ("2C4F8F", "4F8FD0", "8FCBF2", "E7A6C6", "F7D3E2", "FFFFFF")]


def crystal_alloy():
    return gradient_map(vitem("iron_ingot"), R_ALLOY)


# Crystal Upgrade Smithing Template: vanilla's netherite upgrade template with a deep blue plate, and the diamond in
# its middle turned into a flag-striped crystal (blue at the bottom, pink, white on top).
R_TEMPLATE_PLATE = [hexc(c) for c in ("13254D", "1E4580", "2D69AA", "4A90CC")]
R_TEMPLATE_GEM = [hexc(c) for c in ("5BCEFA", "F5A9B8", "FFFFFF")]


def crystal_upgrade_smithing_template():
    base = vitem("netherite_upgrade_smithing_template")
    is_gem = lambda p, px: hsv(px)[0] > 0.3 and hsv(px)[1] > 0.3  # the cyan diamond; the plate is dark red
    out = gradient_map(base, R_TEMPLATE_PLATE, mask=lambda p, px: not is_gem(p, px))
    return gradient_map(base, R_TEMPLATE_GEM, mask=is_gem, out=out)


# ============================================================================================ crystal gear
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


def prism_shard_item():
    return gradient_map(vitem("amethyst_shard"), R_PRISM, curve=lambda t: 0.08 + 0.92 * t)


# ============================================================================================ trans vegetation
# The realm's own grass, ferns, bushes, seagrass, kelp, lily pads and corals (vanilla's would show their usual green,
# yellow and brown). Grass blades come in the flag's pink, blue and white; ferns are blue; bushes and kelp lilac;
# seagrass, lily pads and sugar grass pink. Corals come in blush pink, sky blue and pearl white.
V_PINK = [hexc(c) for c in ("8E3E64", "C2668B", "E58FAE", "F5A9B8", "FBD0DA", "FFF0F4")]
V_BLUE = [hexc(c) for c in ("1E4E86", "2F7DBE", "4FAFE6", "7FD0F8", "B5E6FC", "E6F8FF")]
V_WHITE = [hexc(c) for c in ("7E7A9A", "A6A3C0", "C9C7DD", "E3E2EF", "F4F3FA", "FFFFFF")]
V_LILAC = [hexc(c) for c in ("4E3E86", "7462B4", "9C8AD6", "C0B2EC", "E0D8F8", "F6F2FF")]
V_SUGAR = [hexc(c) for c in ("B8728C", "D896A8", "EDB9C6", "F8D9E1", "FFF1F5")]
GRASS_FAMILIES = [V_PINK, V_PINK, V_BLUE, V_WHITE]


def blades(img, families, seed=3, lo_t=0.18, hi_t=1.0):
    """Colours each blade of a grass texture with its own colour family (blades that touch are split into strips three
    pixels wide), shading from a darker base to light tips."""
    rng = random.Random(seed)
    w, h = img.size
    lo, hi = lum_range(img)
    labels, _ = components(img, lambda p, px: px[3] > 0, wrap=False)
    strips = {p: (label, p[0] // 3) for p, label in labels.items()}
    choice = {k: families[rng.randrange(len(families))] for k in sorted(set(strips.values()))}
    out = img.copy()
    for p, k in strips.items():
        px = img.getpixel(p)
        t = (lum(px) - lo) / (hi - lo)
        height = 1 - p[1] / (h - 1)
        out.putpixel(p, (*sample(choice[k], lo_t + (hi_t - lo_t) * (0.55 * t + 0.45 * height)), px[3]))
    return out


def recolour(img, ramp, lo_t=0.0, hi_t=1.0):
    return gradient_map(img, ramp, curve=lambda t: lo_t + (hi_t - lo_t) * t)


def animated(img, ramp, lo_t=0.0, hi_t=1.0):
    """Gradient-maps every frame of a vertical animation strip with one brightness range, so frames match."""
    w, h = img.size
    lo, hi = lum_range(img)
    out = img.copy()
    for y in range(0, h, w):
        frame = gradient_map(img.crop((0, y, w, y + w)), ramp, lo=lo, hi=hi, curve=lambda t: lo_t + (hi_t - lo_t) * t)
        out.paste(frame, (0, y))
    return out


# ============================================================================================ pink lava
# The Trans Realm's lava: vanilla's lava animation re-coloured from deep magenta through hot pink to a pale glow.
R_PINK_LAVA = [hexc(c) for c in ("6E0C40", "A4195B", "D8407F", "F26F9F", "FAA6C2", "FFE4EE")]


def pink_lava(name):
    """(image, mcmeta) for pink_lava_still / pink_lava_flow, from vanilla's lava_still / lava_flow."""
    with _zip(BLOCK_ZIP).open(f"base block textures/{name}.png.mcmeta") as f:
        meta = json.load(f)
    return animated(vblock(name), R_PINK_LAVA, 0.0, 1.0), meta


# The realms' fire: vanilla's two fire animations re-coloured from deep pink to a white-hot, faintly blue core.
R_PINK_FIRE = [hexc(c) for c in ("6E1440", "B0245F", "E8518E", "F58DB8", "FBD0E3", "E9F6FF")]


def pink_fire(name):
    """(image, mcmeta) for pink_fire_0 / pink_fire_1, from vanilla's fire_0 / fire_1 (same frames, same timing)."""
    with _zip(BLOCK_ZIP).open(f"base block textures/{name}.png.mcmeta") as f:
        meta = json.load(f)
    return gradient_map(vblock(name), R_PINK_FIRE), meta


def pink_lava_bucket():
    """The lava bucket with pink lava in it."""
    img = vitem("lava_bucket")
    hot = lambda p, px: px[3] > 0 and max(px[:3]) - min(px[:3]) > 60
    return gradient_map(img, R_PINK_LAVA, mask=hot, curve=lambda t: 0.2 + 0.8 * t)


# ============================================================================================ clay and sea pickles
R_CLAY = [hexc(c) for c in ("A28AB6", "B59FC8", "C8B5D8", "D9C9E5", "E8DCF0")]
# Sea pickles: pink bodies, light blue glowing tips.
R_PICKLE = [hexc(c) for c in ("7E2F52", "B95679", "E58FAE", "A9DDF6", "DDF4FD")]


def trans_clay():
    return gradient_map(vblock("clay"), R_CLAY)


def trans_sea_pickle(item=False):
    return gradient_map(vitem("sea_pickle") if item else vblock("sea_pickle"), R_PICKLE)


# ============================================================================================ trans cave vines and glow berries
# Lilac leaves (like trans kelp) on plum stems, under the realm's pink moss; the berries glow pink or blue.
R_VINE_STEM = [hexc(c) for c in ("3E1D36", "5E3053", "804A72", "9C6590")]
R_BERRIES = ([hexc(c) for c in ("A83E68", "E2729A", "F7B2C7", "FFEAF1")],
             [hexc(c) for c in ("2A6FB0", "4FB3EA", "9EDDFB", "EFFAFF")])


def vine_parts(img, plain=None):
    """Splits a vanilla cave vine texture into stems, leaves and berries. A berry is any pixel that differs from the
    plain (berry-less) texture; without one (the glow berries item) the bright orange and yellow pixels are berries."""
    stems, leaves, berries = set(), set(), set()
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3] == 0:
            continue
        h = hsv(px)[0]
        if (plain.getpixel(p) != px) if plain is not None else (h < 0.16 and max(px[:3]) >= 140):
            berries.add(p)
        elif h < 0.14:
            stems.add(p)
        else:
            leaves.add(p)
    return stems, leaves, berries


def trans_vines(img, plain=None, first_berry=0, berry_colour=None):
    """Recolours a cave vine texture: lilac leaves, plum stems, and each berry pink or blue in turn (or as
    `berry_colour(pos)` says: 0 pink, 1 blue)."""
    stems, leaves, berries = vine_parts(img, plain)
    out = gradient_map(img, V_LILAC, mask=lambda p, px: p in leaves, curve=lambda t: 0.06 + 0.66 * t)
    gradient_map(img, R_VINE_STEM, mask=lambda p, px: p in stems, out=out)
    labels, count = components(img, lambda p, px: p in berries, wrap=False)
    lo, hi = lum_range(img, lambda p, px: p in berries)
    for p, label in labels.items():
        px = img.getpixel(p)
        colour = berry_colour(p) if berry_colour else (label + first_berry) % 2
        out.putpixel(p, (*sample(R_BERRIES[colour], (lum(px) - lo) / (hi - lo)), px[3]))
    return out


def trans_cave_vines_textures():
    """The four cave vine textures (plain and with berries, tip and body)."""
    out = {}
    for part in ("cave_vines", "cave_vines_plant"):
        plain = vblock(part)
        out[f"trans_{part}"] = trans_vines(plain)
        out[f"trans_{part}_lit"] = trans_vines(vblock(f"{part}_lit"), plain, first_berry=1 if part == "cave_vines" else 0)
    return out


def trans_glow_berries():
    """The item: the big berry pink, the small one blue. They touch, so each pixel goes to the nearer berry's middle
    (scaled by the berry's size)."""
    big, small = ((5.0, 9.5), 4.0), ((10.0, 12.0), 2.4)
    nearer = lambda p, berry: math.hypot(p[0] - berry[0][0], p[1] - berry[0][1]) / berry[1]
    return trans_vines(vitem("glow_berries"), berry_colour=lambda p: 1 if nearer(p, small) < nearer(p, big) else 0)


# ============================================================================================ trans prismarine and sea lanterns
# Vanilla's prismarine drifts between four tints (one per animation frame); ours drifts through pink, lilac, blue and
# pearl, with vanilla's frame order and timing.
R_PRISMARINE_FRAMES = (
    [hexc(c) for c in ("5C2448", "8C3F6A", "C46A93", "E99BB7", "F8CBD9")],
    [hexc(c) for c in ("3D3270", "5E529C", "8A7CC6", "B6AAE6", "DCD5F6")],
    [hexc(c) for c in ("1D4472", "2E6CA3", "4FA2D6", "86CBEF", "C2E9FB")],
    [hexc(c) for c in ("575473", "7E7B9C", "A9A7C4", "D2D1E4", "F2F1F8")],
)
R_PRISMARINE_BRICKS = [hexc(c) for c in ("2B5C8E", "4C8FC2", "79BFE6", "A9DDF6", "D8F1FC")]
R_DARK_PRISMARINE = [hexc(c) for c in ("1A1636", "2D2752", "4A3A72", "7A4F8A", "B0679F")]
R_SEA_LANTERN = [hexc(c) for c in ("4C9BD6", "8CCFF2", "F2B4C6", "FBE0E8", "FFFFFF")]
R_PINK_OBSIDIAN = [hexc(c) for c in ("2A0B1E", "4A1535", "73214F", "A3326E", "D45A95", "F7A6C8")]


def trans_prismarine():
    """(image, mcmeta) from vanilla's four-frame prismarine, one flag colour per frame."""
    with _zip(BLOCK_ZIP).open("base block textures/prismarine.png.mcmeta") as f:
        meta = json.load(f)
    img = vblock("prismarine")
    lo, hi = lum_range(img)
    out = img.copy()
    for i, ramp in enumerate(R_PRISMARINE_FRAMES):
        out.paste(gradient_map(img.crop((0, 16 * i, 16, 16 * i + 16)), ramp, lo=lo, hi=hi), (0, 16 * i))
    return out, meta


def trans_sea_lantern():
    """(image, mcmeta): vanilla's shimmering sea lantern with blue edges, a pink glow and a white-hot heart."""
    with _zip(BLOCK_ZIP).open("base block textures/sea_lantern.png.mcmeta") as f:
        meta = json.load(f)
    return animated(vblock("sea_lantern"), R_SEA_LANTERN), meta


def trans_prismarine_textures():
    return {"trans_prismarine_bricks": recolour(vblock("prismarine_bricks"), R_PRISMARINE_BRICKS),
            "dark_trans_prismarine": recolour(vblock("dark_prismarine"), R_DARK_PRISMARINE)}


def pink_obsidian():
    return recolour(vblock("obsidian"), R_PINK_OBSIDIAN)


def pastel_bush():
    img = vblock("bush")
    out = recolour(img, V_LILAC, 0.15, 1.0)
    rng = random.Random(11)
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3] and lum(px) > 0.62 and rng.random() < 0.18:
            out.putpixel(p, (*sample(V_PINK, 0.85), 255))          # little blossoms on the brightest leaves
    return out


def trans_firefly_bush_emissive():
    """The fireflies glow pink and blue in turn."""
    img = vblock("firefly_bush_emissive")
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3]:
            family = V_PINK if (p[0] + p[1] % 16) % 2 == 0 else V_BLUE
            out.putpixel(p, (*sample(family, 0.55 + 0.45 * lum(px)), px[3]))
    return out


def trans_lily_pad():
    img = vblock("lily_pad")
    out = recolour(img, V_PINK, 0.15, 0.95)
    for p, c in {(9, 5): WHITE, (8, 6): WHITE, (10, 6): WHITE, (9, 7): WHITE, (9, 6): BLUE}.items():
        if img.getpixel(p)[3]:
            out.putpixel(p, (*c, 255))                               # a tiny white flower with a blue heart
    return out


CORAL_RAMPS = {
    "blush": [hexc(c) for c in ("6E2A50", "A8477A", "D56C9E", "F08EB8", "F9BCD4", "FFE6F0")],
    "sky": [hexc(c) for c in ("173E7A", "2463AE", "3E8FDA", "5BCEFA", "A6E6FC", "E6F8FF")],
    "pearl": [hexc(c) for c in ("6A6390", "938CB8", "BDB8DA", "DDDAEF", "F2F1FA", "FFFFFF")],
}
CORAL_SHAPES = {"blush": "brain", "sky": "tube", "pearl": "bubble"}
DEAD_CORAL = [hexc(c) for c in ("6B6670", "8B8690", "A8A3AD", "C2BDC6", "D8D4DB")]


def coral_texture(colour, part, dead=False):
    """part: "" (the coral), "_fan" or "_block". Dead coral is bleached grey with a hint of its old colour."""
    shape = CORAL_SHAPES[colour]
    if dead:
        tint = CORAL_RAMPS[colour][3]
        return recolour(vblock(f"dead_{shape}_coral{part}"), [mix(c, tint, 0.12) for c in DEAD_CORAL])
    return recolour(vblock(f"{shape}_coral{part}"), CORAL_RAMPS[colour])


def vegetation_textures():
    """(name, image, mcmeta or None) for every vegetation block texture."""
    out = [
        # Grass and ferns are light grey and tinted with the biome's grass colour (like vanilla), so each biome's
        # grass matches its grass blocks.
        ("trans_short_grass", grass_gray(vblock("short_grass"), low=0.55), None),
        ("tall_trans_grass_bottom", grass_gray(vblock("tall_grass_bottom"), low=0.55), None),
        ("tall_trans_grass_top", grass_gray(vblock("tall_grass_top"), low=0.55), None),
        ("trans_fern", grass_gray(vblock("fern"), low=0.5), None),
        ("large_trans_fern_bottom", grass_gray(vblock("large_fern_bottom"), low=0.5), None),
        ("large_trans_fern_top", grass_gray(vblock("large_fern_top"), low=0.5), None),
        ("pastel_bush", pastel_bush(), None),
        ("trans_firefly_bush", recolour(vblock("firefly_bush"), V_LILAC, 0.0, 0.85), None),
        ("trans_firefly_bush_emissive", trans_firefly_bush_emissive(), {"animation": {"frametime": 3}}),
        ("short_sugar_grass", recolour(vblock("short_dry_grass"), V_SUGAR), None),
        ("tall_sugar_grass", recolour(vblock("tall_dry_grass"), V_SUGAR), None),
        ("trans_seagrass", animated(vblock("seagrass"), V_PINK, 0.05, 0.95), {"animation": {"frametime": 2}}),
        ("tall_trans_seagrass_bottom", animated(vblock("tall_seagrass_bottom"), V_PINK, 0.05, 0.95), {"animation": {"frametime": 2}}),
        ("tall_trans_seagrass_top", animated(vblock("tall_seagrass_top"), V_PINK, 0.05, 0.95), {"animation": {"frametime": 2}}),
        ("trans_kelp", animated(vblock("kelp"), V_LILAC, 0.05, 1.0),
         {"animation": {"frametime": 2}, "texture": {"alpha_cutoff_bias": 0.1}}),
        ("trans_kelp_plant", animated(vblock("kelp_plant"), V_LILAC, 0.05, 1.0),
         {"animation": {"frametime": 2}, "texture": {"alpha_cutoff_bias": 0.1}}),
        ("trans_lily_pad", trans_lily_pad(), None),
    ]
    for colour in CORAL_RAMPS:
        for part in ("", "_fan", "_block"):
            out.append((f"{colour}_coral{part}", coral_texture(colour, part), None))
            out.append((f"dead_{colour}_coral{part}", coral_texture(colour, part, dead=True), None))
    return out


def vegetation_items():
    return {
        "trans_seagrass": gradient_map(vitem("seagrass"), V_PINK, curve=lambda t: 0.05 + 0.9 * t),
        "trans_kelp": gradient_map(vitem("kelp"), V_LILAC, curve=lambda t: 0.05 + 0.95 * t),
        "trans_firefly_bush": gradient_map(vitem("firefly_bush"), V_LILAC, curve=lambda t: 0.85 * t),
    }


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


def trans_wool_top():
    """The top and bottom of trans wool: the same fluffy wool, all in the flag's blue."""
    return flag_rows(vblock("white_wool"), lambda i: FLAG_RAMPS[0], curve=lambda t: 0.1 + 0.9 * t)


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


# ============================================================================================ furniture and paths
R_PATH = [hexc(c) for c in ("6A4258", "87586F", "A47389", "BE8FA4", "D6AEC0")]
FABRIC_INDEX = {"light_blue": 0, "pink": 1, "white": 2}


def trans_dirt_path_top():
    """Trodden trans dirt: a lighter, dustier pink than the dirt itself, like vanilla's path."""
    return gradient_map(vblock("dirt_path_top"), R_PATH)


def trans_dirt_path_side():
    """Vanilla's path side: the trodden band along the top in path colours, the dirt below matching trans dirt."""
    img = vblock("dirt_path_side")
    path = vblock("dirt_path_top")
    top = {path.getpixel(p)[:3] for p in pixels(path)}
    band = lambda p, px: p[1] <= 4 and px[:3] in top
    out = gradient_map(img, R_PATH, mask=band)
    lo, hi = lum_range(vblock("dirt"))
    return gradient_map(img, R_DIRT, mask=lambda p, px: not band(p, px), lo=lo, hi=hi, out=out)


def fabric(colour):
    """Soft upholstery in one of the flag's colours ('light_blue', 'pink' or 'white'), woven like wool."""
    return gradient_map(vblock("white_wool"), FLAG_RAMPS[FABRIC_INDEX[colour]], curve=lambda t: 0.15 + 0.85 * t)


def cushion_top(colour):
    """A tufted cushion seen from above (the model uses pixels 1-14): piping round the edge, creases running in from
    the corners and a button in the middle."""
    img = fabric(colour)
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))[:3]
        if 1 <= x <= 14 and 1 <= y <= 14:
            if x in (1, 14) or y in (1, 14):
                img.putpixel((x, y), (*mix(px, WHITE, 0.45), 255))
            elif (x == y or x + y == 15) and 3 <= x <= 12 and not 6 <= x <= 9:
                img.putpixel((x, y), (*shade(px, 0.76), 255))
            elif 7 <= x <= 8 and 7 <= y <= 8:
                img.putpixel((x, y), (*(mix(px, WHITE, 0.4) if (x, y) == (7, 7) else shade(px, 0.58)), 255))
            elif 6 <= x <= 9 and 6 <= y <= 9:
                img.putpixel((x, y), (*shade(px, 0.88), 255))
    return img


def cushion_side(colour):
    """The cushion's sides (the model shows rows 11-15): piping along the top, a shadow along the bottom."""
    img = fabric(colour)
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))[:3]
        if y == 11:
            img.putpixel((x, y), (*mix(px, WHITE, 0.35), 255))
        elif y == 15:
            img.putpixel((x, y), (*shade(px, 0.86), 255))
    return img


def trans_lamp_shade(on):
    """The lamp shade's sides (rows 1-7 show): the flag in five bands, and brighter when the lamp is on."""
    wool = vblock("white_wool")
    rows = [0, 0, 1, 2, 2, 2, 3, 4]
    rows = rows + rows
    ramps = [light_ramp(c, 0.86 if on else 0.68, 1.0) for c in (BLUE, PINK, (246, 246, 252), PINK, BLUE)]
    img = flag_rows(wool, lambda i: ramps[i], rows=rows, curve=lambda t: (0.35 if on else 0.1) + (0.65 if on else 0.9) * t)
    if on:
        for x in range(0, 16, 3):
            img.putpixel((x, 4 + x % 2), (*WHITE, 255))
    return img


def trans_lamp_shade_top(on):
    """The shade from above and below (pixels 3-12): a blue rim round the opening, dark inside, or glowing when on."""
    img = fabric("light_blue")
    for (x, y) in pixels(img):
        if 4 <= x <= 11 and 4 <= y <= 11:
            if on:
                c = mix(hexc("FFF4D6"), WHITE, 0.5) if 6 <= x <= 9 and 6 <= y <= 9 else hexc("FFE3EC")
            else:
                c = hexc("3A2A45") if 5 <= x <= 10 and 5 <= y <= 10 else hexc("5A4466")
            img.putpixel((x, y), (*c, 255))
    return img


def trans_lamp_base():
    """Pearly ceramic for the lamp's foot and stem."""
    return gradient_map(vblock("calcite"), R_PEARL[1:])


def trans_bookshelf():
    """Vanilla's bookshelf in trans planks, full of pink, blue, white and lavender books."""
    img = vblock("bookshelf")
    wood = lambda p, px: 0.06 <= hsv(px)[0] <= 0.13 and hsv(px)[2] > 0.38 and hsv(px)[1] < 0.7
    dark = lambda p, px: hsv(px)[2] <= 0.42 and not wood(p, px) and 0.04 <= hsv(px)[0] <= 0.15 and 0.3 <= hsv(px)[1] <= 0.7
    families = [
        (lambda h, s: s > 0.5 and (h < 0.08 or h > 0.9), R_PINK[1:5]),
        (lambda h, s: s > 0.4 and 0.55 <= h <= 0.67, R_BLUE[1:5]),
        (lambda h, s: s > 0.5 and 0.16 < h < 0.5, R_PEARL[1:]),
        (lambda h, s: s > 0.5 and 0.12 <= h <= 0.16, R_LAVENDER[1:5]),
        (lambda h, s: s < 0.08, mix_ramp(R_PEARL, R_PINK, 0.3)[2:]),
    ]
    out = gradient_map(img, R_WOOD_PINK, mask=wood)
    out = gradient_map(img, [hexc("2A1726"), hexc("3D2236"), hexc("4F2E46")], mask=dark, out=out)
    done = {p for p in pixels(img) if wood(p, img.getpixel(p)) or dark(p, img.getpixel(p))}
    for test, ramp in families:
        mask = lambda p, px, test=test: p not in done and test(hsv(px)[0], hsv(px)[1])
        out = gradient_map(img, ramp, mask=mask, out=out)
        done |= {p for p in pixels(img) if mask(p, img.getpixel(p))}
    missing = [p for p in pixels(img) if p not in done]
    if missing:
        raise SystemExit(f"trans_bookshelf: unclassified pixels {missing[:5]}")
    return out


def furniture_textures():
    textures = {"trans_dirt_path_top": trans_dirt_path_top(), "trans_dirt_path_side": trans_dirt_path_side(),
                "trans_lamp_shade": trans_lamp_shade(False), "trans_lamp_shade_on": trans_lamp_shade(True),
                "trans_lamp_shade_top": trans_lamp_shade_top(False), "trans_lamp_shade_top_on": trans_lamp_shade_top(True),
                "trans_lamp_base": trans_lamp_base(), "trans_bookshelf": trans_bookshelf()}
    for colour in FABRIC_INDEX:
        textures[f"trans_fabric_{colour}"] = fabric(colour)
        textures[f"{colour}_cushion_top"] = cushion_top(colour)
        textures[f"{colour}_cushion_side"] = cushion_side(colour)
    return textures


# ============================================================================================ desert and riverside plants
R_CACTUS = [hexc(c) for c in ("6F80AE", "98A7CF", "C4CEE9", "E6EBF7", "FFFFFF")]
R_SPINE = [hexc(c) for c in ("D9708F", "F5A9B8", "FBD3DC")]
R_DRY_BUSH = [hexc(c) for c in ("6B3A58", "9C5C7E", "C98AA6", "EDB9CB")]
CACTUS_META = {"texture": {"alpha_cutoff_bias": 0.1}}  # vanilla's cactus_side/top.png.mcmeta


def trans_cactus(face):
    """A white cactus with soft blue shading; vanilla's pale yellow spines turn pink (the bottom has none)."""
    img = vblock(f"cactus_{face}")
    spine = lambda p, px: face != "bottom" and hsv(px)[1] < 0.4 and hsv(px)[2] > 0.6
    out = gradient_map(img, R_CACTUS, mask=lambda p, px: not spine(p, px))
    return gradient_map(img, R_SPINE, mask=spine, out=out)


def dry_sugar_bush():
    """Vanilla's dead bush as dry candy-pink twigs."""
    return gradient_map(vblock("dead_bush"), R_DRY_BUSH)


def trans_sugar_cane():
    """Sugar cane whose four stalks come in the flag's colours (blue, pink, white, pink); leaves take their stalk's colour
    and the joints stay lighter."""
    img = vblock("sugar_cane")
    ramps = [R_BLUE[1:5], R_PINK[1:5], R_PEARL[1:], R_PINK[1:5]]
    lo, hi = lum_range(img)
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3] == 0:
            continue
        stalk = min(range(4), key=lambda i: abs(x - (1.5 + 4 * i)))
        c = sample(ramps[stalk], (lum(px) - lo) / (hi - lo))
        out.putpixel((x, y), (*c, px[3]))
    return out


def trans_sugar_cane_item():
    """The sugar cane item: a bundle shaded from blue through pink to white."""
    return gradient_map(vitem("sugar_cane"), R_TRANS[1:])


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


# ============================================================================================ lush caves and forests
def trans_moss():
    """Soft pink moss with patches of trans blue."""
    img = vblock("moss_block")
    cells = voronoi_patches(16, 16, 5, random.Random(12))
    blue_cells = {1, 3}
    lo, hi = lum_range(img)
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        t = (lum(px) - lo) / (hi - lo)
        ramp = R_PETAL_BLUE if cells[p] in blue_cells else R_PETAL_PINK
        out.putpixel(p, (*sample(ramp, 0.2 + 0.7 * t), px[3]))
    return out


def leaves_from(name, ramp, sparkle=None, lo_t=0.1):
    """Vanilla leaves re-coloured onto `ramp` (keeping their holes), optionally with a few glowing specks."""
    img = vblock(name)
    out = gradient_map(img, ramp, curve=lambda t: lo_t + (1 - lo_t) * t)
    if sparkle:
        rng = random.Random(len(name))
        for p in pixels(img):
            if img.getpixel(p)[3] > 0 and rng.random() < 0.05:
                out.putpixel(p, (*sparkle, 255))
    return out


def pearl_leaves():
    return leaves_from("birch_leaves", [hexc("A8A3BE"), hexc("CFCBE0"), hexc("EAE8F3"), hexc("FBF7FB"), WHITE], sparkle=hexc("FCE2EA"))


def sky_leaves():
    return leaves_from("oak_leaves", R_PETAL_BLUE, lo_t=0.15)


def blush_leaves():
    return leaves_from("azalea_leaves", R_PETAL_PINK, lo_t=0.15)


def twilight_leaves():
    return leaves_from("dark_oak_leaves", [hexc("261C4A"), hexc("3B2E6E"), hexc("574694"), hexc("7A68B6"), hexc("A795D8")],
                       sparkle=hexc("F7B9CC"))


LEAVES = ("pearl_leaves", "sky_leaves", "blush_leaves", "twilight_leaves")


# ============================================================================================ woods, flowers and hedges
# The themed forests' own woods. Each family is a vanilla wood re-coloured: pearl (pale oak, pearly white), sky (oak,
# blue), twilight (dark oak, deep violet) and blush (cherry, rose).
WOOD_FAMILIES = {
    # prefix: (vanilla wood, bark ramp, wood ramp, sapling leaf ramp)
    "pearl": ("pale_oak", [hexc(c) for c in ("6E6A86", "9592AE", "B9B6CF", "D9D7E6", "F1F0F7", "FFFFFF")],
              [hexc(c) for c in ("A49CB8", "C2BBD3", "DCD6E8", "EEEAF5", "FBF9FE")], R_PETAL_WHITE),
    "sky": ("oak", [hexc(c) for c in ("14294D", "20406F", "2F5D96", "4B82B8", "73A8D6")],
            [hexc(c) for c in ("3C79B5", "5A9AD2", "7DBDEB", "A9DBF7", "D4F0FD")], R_PETAL_BLUE),
    "twilight": ("dark_oak", [hexc(c) for c in ("1A1233", "2A1F4D", "3D2F6B", "55448A", "7462A8")],
                 [hexc(c) for c in ("5A4A93", "7563B0", "9483CC", "B5A7E3", "D6CDF3")], R_PETAL_LAVENDER),
    "blush": ("cherry", [hexc(c) for c in ("3E1428", "5E2240", "833659", "A84E74", "C96D92")],
              [hexc(c) for c in ("B5476E", "D0688B", "E58CA8", "F2B2C6", "FBD9E4")], R_PETAL_PINK),
}


def wood_log_top(img, bark, wood):
    """The outer ring of a log end is bark, the rings inside are wood."""
    w, h = img.size
    ring = lambda p, px: min(p[0], p[1], w - 1 - p[0], h - 1 - p[1]) == 0
    out = gradient_map(img, bark, mask=ring)
    return gradient_map(img, wood, mask=lambda p, px: not ring(p, px), out=out)


def wood_door(img, bark, wood):
    """Doors and trapdoors: wooden parts in the planks' colour, dark iron and frames in the bark's."""
    dark = lambda p, px: hsv(px)[1] < 0.25 and lum(px) < 0.3
    out = gradient_map(img, wood, mask=lambda p, px: not dark(p, px))
    return gradient_map(img, bark, mask=dark, out=out)


def wood_sapling(img, bark, leaf):
    """Saplings: the stem in bark colours, everything else in the family's leaf colour."""
    stem = lambda p, px: hsv(px)[1] < 0.55 and lum(px) < 0.32 and not is_green(px)
    out = gradient_map(img, bark, mask=stem, curve=lambda t: 0.25 + 0.75 * t)
    return gradient_map(img, leaf, mask=lambda p, px: not stem(p, px), out=out, curve=lambda t: 0.1 + 0.9 * t)


def wood_family_textures(prefix):
    src, bark, wood, leaf = WOOD_FAMILIES[prefix]
    blocks = {
        f"{prefix}_log": gradient_map(vblock(f"{src}_log"), bark),
        f"{prefix}_log_top": wood_log_top(vblock(f"{src}_log_top"), bark, wood),
        f"stripped_{prefix}_log": gradient_map(vblock(f"stripped_{src}_log"), wood),
        f"stripped_{prefix}_log_top": gradient_map(vblock(f"stripped_{src}_log_top"), wood),
        f"{prefix}_planks": gradient_map(vblock(f"{src}_planks"), wood),
        f"{prefix}_door_top": wood_door(vblock(f"{src}_door_top"), bark, wood),
        f"{prefix}_door_bottom": wood_door(vblock(f"{src}_door_bottom"), bark, wood),
        f"{prefix}_trapdoor": wood_door(vblock(f"{src}_trapdoor"), bark, wood),
        f"{prefix}_sapling": wood_sapling(vblock(f"{src}_sapling"), bark, leaf),
    }
    items = {f"{prefix}_door": wood_door(vitem(f"{src}_door"), bark, wood)}
    return blocks, items


def flowering_blush_leaves():
    """Blush leaves with clusters of white and blue flowers (the flower pattern of vanilla's flowering azalea leaves)."""
    base = vblock("azalea_leaves")
    out = gradient_map(base, R_PETAL_PINK, curve=lambda t: 0.15 + 0.85 * t)
    flowers = vblock("flowering_azalea_leaves")
    blossom = lambda p, px: px[3] > 0 and (hsv(px)[0] > 0.75 or hsv(px)[0] < 0.08) and hsv(px)[1] > 0.15
    labels, _ = components(flowers, blossom, wrap=True)
    lo, hi = lum_range(flowers, blossom)
    for p, label in labels.items():
        px = flowers.getpixel(p)
        ramp = R_PETAL_WHITE if label % 3 else R_PETAL_BLUE
        out.putpixel(p, (*sample(ramp, 0.3 + 0.7 * (lum(px) - lo) / (hi - lo)), 255))
    return out


def hedge(leaf_ramp, flower_ramps, seed):
    """A floral hedge: vanilla's flowering azalea leaves, leaves and flowers each in their own colours."""
    img = vblock("flowering_azalea_leaves")
    blossom = lambda p, px: px[3] > 0 and (hsv(px)[0] > 0.75 or hsv(px)[0] < 0.08) and hsv(px)[1] > 0.15
    out = gradient_map(img, leaf_ramp, mask=lambda p, px: not blossom(p, px), curve=lambda t: 0.15 + 0.85 * t)
    labels, _ = components(img, blossom, wrap=True)
    lo, hi = lum_range(img, blossom)
    rng = random.Random(seed)
    ramps = {}
    for p, label in labels.items():
        ramp = ramps.setdefault(label, flower_ramps[rng.randrange(len(flower_ramps))])
        px = img.getpixel(p)
        out.putpixel(p, (*sample(ramp, 0.25 + 0.75 * (lum(px) - lo) / (hi - lo)), 255))
    return out


def star_bloom():
    """A white star flower; its blue heart is on the glowing layer (star_bloom_emissive)."""
    return flower(vblock("open_eyeblossom"), lambda p, i: R_PETAL_WHITE)


def star_bloom_emissive():
    img = vblock("open_eyeblossom_emissive")
    return gradient_map(img, [hexc("3E9BD8"), BLUE, hexc("BDEBFD"), WHITE])


def flower_textures():
    out = {
        "blush_carnation": flower(vblock("poppy"), lambda p, i: R_PETAL_PINK),
        "pearl_snowdrop": flower(vblock("white_tulip"), lambda p, i: mix_ramp(R_PETAL_WHITE, R_PETAL_BLUE, 0.12)),
        "forget_me_not": flower(vblock("azure_bluet"), lambda p, i: R_PETAL_BLUE),
        "trans_rose": flower(vblock("wither_rose"), lambda p, i: R_PETAL_PINK if 6 <= p[0] <= 9 and p[1] <= 7 else R_PETAL_BLUE),
        "star_bloom": star_bloom(), "star_bloom_emissive": star_bloom_emissive(),
        "fairy_bell": flower(vblock("orange_tulip"), lambda p, i: R_PETAL_LAVENDER),
        "flowering_blush_leaves": flowering_blush_leaves(),
        "blossom_hedge": hedge([hexc(c) for c in ("8E6E8E", "AA8AAE", "C6A9CC", "E0C9E6", "F4E8F7")], [R_PETAL_PINK, R_PETAL_WHITE], 21),
        "bluebell_hedge": hedge([hexc(c) for c in ("5A6F9E", "7389BB", "93A9D6", "B8CCEE", "DCE8FB")], [R_PETAL_BLUE, R_PETAL_WHITE], 22),
        "pearl_hedge": hedge([hexc(c) for c in ("8A879E", "A9A6BD", "C8C6D9", "E2E1EE", "F7F6FB")], [R_PETAL_PINK, R_PETAL_BLUE], 23),
    }
    for half in ("top", "bottom"):
        out[f"sky_delphinium_{half}"] = flower(vblock(f"lilac_{half}"), lambda p, i: R_PETAL_BLUE)
        out[f"blush_foxglove_{half}"] = flower(vblock(f"rose_bush_{half}"), lambda p, i: R_PETAL_PINK)
        out[f"pearl_lupine_{half}"] = flower(vblock(f"lilac_{half}"), lambda p, i: (R_PETAL_WHITE, R_PETAL_PINK, R_PETAL_BLUE)[i % 3])
    return out


FLOWER_CUTOUT = ("blush_carnation", "pearl_snowdrop", "forget_me_not", "trans_rose", "star_bloom", "star_bloom_emissive", "fairy_bell",
                 "sky_delphinium_top", "sky_delphinium_bottom", "blush_foxglove_top", "blush_foxglove_bottom", "pearl_lupine_top",
                 "pearl_lupine_bottom")



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


# ============================================================================================ trans bed
def bed_recolor(img, stripe_of=None):
    """White bed texture -> trans flag blanket (stripes chosen by `stripe_of(x, y)`) on a pink wooden frame.
    The blanket is the pale, unsaturated part of the vanilla white bed; the frame is the brown wood."""
    out = img.copy()
    lo, hi = lum_range(img)
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3] == 0:
            continue
        t = (lum(px) - lo) / (hi - lo)
        if hsv(px)[1] < 0.15 and stripe_of is not None:
            c = sample(FLAG_RAMPS[stripe_of(x, y)], 0.2 + 0.8 * t)
        elif hsv(px)[1] < 0.15:
            c = sample(R_PETAL_WHITE, 0.3 + 0.7 * t)     # pillow
        else:
            c = sample(R_WOOD_PINK, t * 1.2)
        out.putpixel((x, y), (*c, px[3]))
    return out


BED_STRIPES = stripes5(16)


def trans_bed_textures():
    """All faces of the trans bed. The top of the foot comes in three versions: plain, and the left or right half
    of a heart, shown when two trans beds stand side by side (TransBedBlock picks which)."""
    across = lambda x, y: BED_STRIPES[x]                   # stripes run along the bed
    out = {
        "trans_bed_foot_up": bed_recolor(vblock("white_bed_foot_up"), across),
        "trans_bed_foot_south": bed_recolor(vblock("white_bed_foot_south"), across),
        "trans_bed_foot_east": bed_recolor(vblock("white_bed_foot_east"), lambda x, y: 4),
        "trans_bed_foot_west": bed_recolor(vblock("white_bed_foot_west"), lambda x, y: 0),
        "trans_bed_head_east": bed_recolor(vblock("white_bed_head_east"), lambda x, y: 4),
        "trans_bed_head_west": bed_recolor(vblock("white_bed_head_west"), lambda x, y: 0),
        "trans_bed_head_north": bed_recolor(vblock("bed_head_north")),
        "trans_bed_down": bed_recolor(vblock("bed_down")),
    }
    # The head: a pearly pillow at the top (north) end, blanket below it.
    head = vblock("white_bed_head_up")
    pillow_rows = range(0, 7)
    out["trans_bed_head_up"] = bed_recolor(head, None)
    blanket = bed_recolor(head, across)
    for (x, y) in pixels(head):
        if y not in pillow_rows:
            out["trans_bed_head_up"].putpixel((x, y), blanket.getpixel((x, y)))
    # A heart across two foot tops, centred on the seam between the beds.
    pair = Image.new("RGBA", (32, 16))
    pair.paste(out["trans_bed_foot_up"], (0, 0))
    pair.paste(out["trans_bed_foot_up"], (16, 0))
    heart = HEARTS[11]
    ox, oy = 16 - 11 // 2 - 0, 3
    for dy, row in enumerate(heart):
        for dx, ch in enumerate(row):
            if ch != "#":
                continue
            x, y = ox + dx, oy + dy
            edge = any(not (0 <= dy + ny < 11 and 0 <= dx + nx < 11 and heart[dy + ny][dx + nx] == "#")
                       for nx, ny in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            c = hexc("B4406E") if edge else sample(R_PETAL_PINK, 0.85 - (dx + dy) / 30.0)
            pair.putpixel((x, y), (*c, 255))
    pair.putpixel((ox + 2, oy + 2), (*WHITE, 255))
    pair.putpixel((ox + 3, oy + 2), (*WHITE, 255))
    pair.putpixel((ox + 2, oy + 3), (*WHITE, 255))
    out["trans_bed_foot_up_left"] = pair.crop((0, 0, 16, 16))
    out["trans_bed_foot_up_right"] = pair.crop((16, 0, 32, 16))
    return out


# ============================================================================================ trans boat
def trans_boat(img):
    """Vanilla birch boat (or chest boat) -> trans boat. Seen from inside, the floor and walls read as the flag: blue
    rims, pink and white planks. The paddles have blue shafts and pink blades; a chest boat's chest turns pink with a
    navy frame and a pearly latch."""
    out = img.copy()
    lo, hi = lum_range(img.crop((0, 0, 128, 51)))
    chest = img.crop((0, 58, 48, 96)) if img.height > 64 else None
    clo, chi = lum_range(chest) if chest else (0.0, 1.0)
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3] == 0:
            continue
        t = (lum(px) - lo) / (hi - lo)
        if y >= 58:                                              # the chest
            if lum(px) < 0.2:
                c = sample(MOB_NAVY, 0.15 + lum(px) * 2)
            elif hsv(px)[1] < 0.15:
                c = sample(R_PEARL, 0.3 + 0.7 * lum(px))
            else:
                c = sample(R_WOOD_PINK, (lum(px) - clo) / (chi - clo))
        elif x >= 62:                                            # paddles
            blade = x < 78 and (7 <= y < 13 or 27 <= y < 33)
            c = sample(R_WOOD_PINK if blade else R_WOOD_BLUE, t)
        elif y < 3:                                              # thin edges of the floor
            c = sample(R_WOOD_BLUE, t)
        elif y < 19:                                             # floor planks: pink, white, pink
            c = sample(R_WOOD_WHITE if 8 <= y < 13 else R_WOOD_PINK, t)
        else:                                                    # walls: blue rim, pink board, white board
            row = (y - 19) % 8
            c = sample(R_WOOD_BLUE if row < 2 else R_WOOD_PINK if row < 5 else R_WOOD_WHITE, t)
        out.putpixel((x, y), (*c, px[3]))
    return out


def trans_boat_item(vanilla):
    """The birch boat sprites in trans wood: pink hull with a white stripe and a blue rim, navy outline. In the chest
    boat, the pixels that differ from the plain boat are the chest: pink with a navy frame, like the chest boat's."""
    img = vitem(vanilla)
    plain = vitem("birch_boat")
    lo, hi = lum_range(plain)
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3] == 0:
            continue
        t = (lum(px) - lo) / (hi - lo)
        if px != plain.getpixel((x, y)):                         # the chest
            c = sample(MOB_NAVY, 0.4 + t) if lum(px) < 0.3 else sample(R_PINK, 0.25 + 0.6 * t)
        elif t < 0.2:
            c = sample(MOB_NAVY, 0.45 + t)
        elif y + x // 4 <= 6:                                    # rim along the far edge
            c = sample(R_WOOD_BLUE, 0.2 + 0.8 * t)
        elif 9 <= y <= 10:
            c = sample(R_WOOD_WHITE, t)
        else:
            c = sample(R_WOOD_PINK, t)
        out.putpixel((x, y), (*c, px[3]))
    return out


# ============================================================================================ cat plushes
# One 32x32 texture per plush, for the shared block model "cat_plush" (generate_data.py), at one texel per model
# pixel. Texel regions (x0, y0, x1, y1) of each face; the face of the cat looks north.
PLUSH_UV = {
    "head": {"up": (6, 0, 14, 6), "down": (14, 0, 22, 6), "west": (0, 6, 6, 12), "north": (6, 6, 14, 12),
             "east": (14, 6, 20, 12), "south": (20, 6, 28, 12)},
    "body": {"up": (5, 12, 11, 17), "down": (11, 12, 17, 17), "west": (0, 17, 5, 22), "north": (5, 17, 11, 22),
             "east": (11, 17, 16, 22), "south": (16, 17, 22, 22)},
    "tail": {"up": (22, 12, 24, 17), "down": (24, 12, 26, 17), "north": (26, 12, 28, 14), "south": (28, 12, 30, 14),
             "east": (22, 17, 27, 19), "west": (27, 17, 32, 19)},
    "ear": (0, 22, 2, 24), "ear_inner": (2, 22, 4, 24), "muzzle": (4, 22, 7, 24), "paw": (8, 22, 10, 24),
}

# name: (English name, base, pattern colour, belly, eyes, nose, pattern)
PLUSHES = {
    "silly_cat_plush": ("Silly Cat Plush", "A29A93", "5E5650", "F4F1EE", "3B4A2E", "E8A0A8", "tabby"),
    "trans_cat_plush": ("Trans Cat Plush", "F5A9B8", "5BCEFA", "FFFFFF", "2C6FB0", "E06C8C", "flag"),
    "midnight_cat_plush": ("Midnight Cat Plush", "2E2A3A", "1C1926", "4A4459", "E8E04A", "F08FB0", "solid"),
    "biscuit_cat_plush": ("Biscuit Cat Plush", "E8A15A", "C06A2C", "FBEBD4", "4E8A3A", "E58C8C", "tabby"),
    "patches_cat_plush": ("Patches Cat Plush", "F7F3EE", "E58A3C", "F7F3EE", "6B5A2A", "E8A0A8", "calico"),
    "mochi_cat_plush": ("Mochi Cat Plush", "F1E5CF", "5A4032", "FBF6EC", "3E8FD8", "7A5A50", "points"),
    "pearl_cat_plush": ("Pearl Cat Plush", "F4F3FA", "D8D3EA", "FFFFFF", "4FA6E8", "F5A9B8", "fluffy"),
    "bubblegum_cat_plush": ("Bubblegum Cat Plush", "F7A8C8", "E37AA5", "FFF2F7", "2C6FB0", "D9577F", "tabby"),
    "bluebell_cat_plush": ("Bluebell Cat Plush", "8ED3F5", "5DB4E6", "F4FBFF", "E0607E", "F5A9B8", "solid"),
}


def cat_plush(name):
    _, base, pattern, belly, eyes, nose, kind = PLUSHES[name]
    base, pattern, belly, eyes, nose = (hexc(c) for c in (base, pattern, belly, eyes, nose))
    rng = random.Random(name)
    img = new(32, 32)

    def fill(box, colour, jitter=0.06):
        x0, y0, x1, y1 = box
        for y in range(y0, y1):
            for x in range(x0, x1):
                img.putpixel((x, y), (*shade(colour, 1 + rng.uniform(-jitter, jitter)), 255))

    def put(x, y, colour):
        img.putpixel((x, y), (*colour, 255))

    for part in ("head", "body", "tail"):
        for box in PLUSH_UV[part].values():
            fill(box, base)
    for key in ("ear", "paw"):
        fill(PLUSH_UV[key], base)
    fill(PLUSH_UV["ear_inner"], mix(nose, (255, 255, 255), 0.35), 0.03)
    fill(PLUSH_UV["muzzle"], belly, 0.03)

    head, body, tail = PLUSH_UV["head"], PLUSH_UV["body"], PLUSH_UV["tail"]
    dark = shade(pattern, 1.0)
    if kind == "tabby":
        # Stripes run over the head front to back, around the body and tail; an "M" on the forehead.
        x0, y0, x1, y1 = head["up"]
        for x in (x0 + 1, x0 + 3, x0 + 4, x0 + 6):
            for y in range(y0, y1, 1):
                if (x + y) % 3 != 0:
                    put(x, y, dark)
        for side in ("west", "east", "south"):
            x0, y0, x1, y1 = head[side]
            for x in range(x0 + 1, x1, 2):
                for y in range(y0, y0 + 3):
                    put(x, y, dark)
        x0, y0, _, _ = head["north"]
        for dx in (1, 3, 4, 6):
            put(x0 + dx, y0, dark)
        for side in ("west", "east", "south", "up"):
            x0, y0, x1, y1 = body[side]
            for x in range(x0, x1):
                for y in range(y0, y1):
                    if (x - x0) % 3 == 1 and side != "up" or side == "up" and (y - y0) % 2 == 0 and x not in (x0, x1 - 1):
                        put(x, y, dark)
        for side in ("up", "east", "west"):
            x0, y0, x1, y1 = tail[side]
            long_axis_x = (x1 - x0) > (y1 - y0)
            for x in range(x0, x1):
                for y in range(y0, y1):
                    if ((x - x0) if long_axis_x else (y - y0)) % 2 == 1:
                        put(x, y, dark)
        x0, y0, x1, y1 = tail["south"]
        fill((x0, y0, x1, y1), dark, 0.03)
    elif kind == "flag":
        # Trans flag stripes around the body (top to bottom) and on the tail; blue ears and paws.
        stripes = [BLUE, PINK, WHITE, PINK, BLUE]
        for side in ("north", "south", "west", "east"):
            x0, y0, x1, y1 = body[side]
            for y in range(y0, y1):
                for x in range(x0, x1):
                    put(x, y, shade(stripes[(y - y0) * 5 // (y1 - y0)], 1 + rng.uniform(-0.04, 0.04)))
        for i, side in enumerate(("up", "east", "west")):
            x0, y0, x1, y1 = tail[side]
            for y in range(y0, y1):
                for x in range(x0, x1):
                    k = (y - y0) if (y1 - y0) > (x1 - x0) else (x - x0)
                    put(x, y, stripes[k % 5])
        fill(PLUSH_UV["ear"], BLUE, 0.04)
        fill(PLUSH_UV["paw"], WHITE, 0.03)
        fill(tail["south"], WHITE, 0.03)
    elif kind == "calico":
        # Orange and black patches on a white coat.
        black = hexc("2E2A30")
        for part, boxes in (("head", head), ("body", body), ("tail", tail)):
            for side, (x0, y0, x1, y1) in boxes.items():
                if side == "down" or part == "head" and side == "north":
                    continue
                for _ in range(2 if part != "tail" else 1):
                    colour = pattern if rng.random() < 0.6 else black
                    cx, cy = rng.randrange(x0, x1), rng.randrange(y0, y1)
                    r = rng.choice((1.2, 1.6, 2.2))
                    for y in range(y0, y1):
                        for x in range(x0, x1):
                            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                                put(x, y, shade(colour, 1 + rng.uniform(-0.05, 0.05)))
        fill(PLUSH_UV["ear"], pattern, 0.05)
    elif kind == "points":
        # Siamese: dark ears, paws, tail and a soft mask around the eyes.
        fill(PLUSH_UV["ear"], dark, 0.04)
        fill(PLUSH_UV["paw"], dark, 0.04)
        for box in tail.values():
            fill(box, dark, 0.05)
        x0, y0, x1, y1 = head["north"]
        for y in range(y0 + 1, y1):
            for x in range(x0 + 1, x1 - 1):
                d = abs(x - (x0 + x1 - 1) / 2) / 4 + abs(y - (y0 + 3)) / 4
                if d < 1:
                    put(x, y, mix(dark, base, d * 0.9))
    elif kind == "fluffy":
        # Soft lavender shading at the edges of every face, like long fur.
        for part in ("head", "body"):
            for side, (x0, y0, x1, y1) in PLUSH_UV[part].items():
                for y in range(y0, y1):
                    for x in range(x0, x1):
                        if x in (x0, x1 - 1) or y == y1 - 1:
                            put(x, y, shade(pattern, 1 + rng.uniform(-0.04, 0.04)))

    # Belly patch on the chest, and the face.
    x0, y0, x1, y1 = body["north"]
    for y in range(y0, y1):
        for x in range(x0 + 1, x1 - 1):
            if kind != "flag" and not (y == y0 and x in (x0 + 1, x1 - 2)):
                put(x, y, shade(belly, 1 + rng.uniform(-0.03, 0.03)))
    x0, y0, _, _ = head["north"]
    for ex in (1, 5):
        put(x0 + ex, y0 + 1, hexc("FFFFFF"))
        put(x0 + ex + 1, y0 + 1, shade(eyes, 0.55))
        put(x0 + ex, y0 + 2, eyes)
        put(x0 + ex + 1, y0 + 2, shade(eyes, 0.75))
    blush = mix(nose, base, 0.35)
    put(x0, y0 + 3, blush)
    put(x0 + 7, y0 + 3, blush)
    mx0, my0, _, _ = PLUSH_UV["muzzle"]
    put(mx0 + 1, my0, nose)
    mouth = shade(mix(belly, nose, 0.5), 0.6)
    put(mx0, my0 + 1, mouth)
    put(mx0 + 2, my0 + 1, mouth)
    if name == "silly_cat_plush":
        put(mx0 + 1, my0 + 1, hexc("E86A88"))       # the blep
    # Toe lines on the paws.
    px0, py0, px1, py1 = PLUSH_UV["paw"]
    for x in range(px0, px1):
        put(x, py1 - 1, shade(img.getpixel((x, py1 - 1))[:3], 0.8))
    return img


# ============================================================================================ paintings
# Paintings are 16 pixels per block with a one-pixel frame, like vanilla's. The two cat portraits come from the
# owner's photos of the Silly Cat, pixelated to 48x48 (tools/art/; the photos themselves aren't in the repository).
ART = os.path.join(HERE, "art")
FRAME = [hexc("3A1D2C"), hexc("4A2638"), hexc("2E1622"), hexc("55304A")]
BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def framed(img):
    out = img.copy()
    w, h = out.size
    rng = random.Random(w * 31 + h)
    for x in range(w):
        for y in range(h):
            if x in (0, w - 1) or y in (0, h - 1):
                out.putpixel((x, y), (*FRAME[rng.randrange(len(FRAME))], 255))
    return out


def dithered_gradient(w, h, stops, y0=0, y1=None):
    """Vertical gradient through `stops` (top to bottom), split into many close bands that are ordered-dithered
    into each other, so it reads as a soft painted sky rather than a checkerboard."""
    y1 = h if y1 is None else y1
    levels = max(len(stops), (y1 - y0) // 2)
    colours = [sample(stops, i / (levels - 1)) for i in range(levels)]
    img = new(w, h)
    for y in range(h):
        t = min(1.0, max(0.0, (y - y0) / max(1, y1 - y0 - 1)))
        pos = t * (levels - 1)
        i = min(levels - 2, int(pos))
        f = pos - i
        for x in range(w):
            c = colours[i + 1] if f * 16 > BAYER4[y % 4][x % 4] + 0.5 else colours[i]
            img.putpixel((x, y), (*c, 255))
    return img


def pixel_heart(scale=1):
    """The 11x11 pixel heart (HEARTS[11]), optionally scaled up: set of (x, y)."""
    return {(x * scale + i, y * scale + j) for y, row in enumerate(HEARTS[11]) for x, ch in enumerate(row) if ch == "#"
            for i in range(scale) for j in range(scale)}


def heart_mask(w, h):
    """A smooth heart filling a w x h box: set of (x, y)."""
    cells = set()
    for y in range(h):
        for x in range(w):
            u = (x + 0.5) / w * 2.6 - 1.3
            v = 1.25 - (y + 0.5) / h * 2.5
            if (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0:
                cells.add((x, y))
    return cells


def sparkle(img, x, y, colour, big=False):
    w, h = img.size
    pts = [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)] if big else [(0, 0)]
    for dx, dy in pts:
        if 0 < x + dx < w - 1 and 0 < y + dy < h - 1:
            img.putpixel((x + dx, y + dy), (*colour, 255))


def cloud(img, x, y, width, colour=WHITE, shadow=hexc("E6DDF0")):
    rows = [(1, width - 1), (0, width)]
    for dy, (a, b) in enumerate(rows):
        for dx in range(a, b):
            img.putpixel((x + dx, y + dy), (*(colour if dy == 0 else shadow), 255))
    for dx in range(width // 3, width // 3 + max(2, width // 3)):
        img.putpixel((x + dx, y - 1), (*colour, 255))


def photo_painting(name):
    img = Image.open(os.path.join(ART, name)).convert("RGB")
    img = img.quantize(colors=30, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGBA")
    out = img.copy()
    for p in pixels(img):
        r, g, b, a = img.getpixel(p)
        out.putpixel(p, (*mix((r, g, b), hexc("F7C9D4"), 0.08), 255))   # a faint pink glaze
    return framed(out)


def painting_trans_heart():
    w = h = 32
    img = dithered_gradient(w, h, [hexc("221B45"), hexc("3A2E6E"), hexc("6A4C93"), hexc("9A6FB0")])
    rng = random.Random(5)
    for _ in range(14):
        sparkle(img, rng.randrange(2, 30), rng.randrange(2, 30), rng.choice((WHITE, PINK, BLUE)), big=rng.random() < 0.25)
    cells = pixel_heart(2)
    ys = sorted({y for _, y in cells})
    stripe_of = {y: stripes5(len(ys))[i] for i, y in enumerate(ys)}
    ox, oy = 5, 5
    for (x, y) in cells:
        edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        base = FLAG[stripe_of[y]]
        c = shade(base, 0.62) if edge else shade(base, 1.0 - 0.14 * (x / 22))
        img.putpixel((ox + x, oy + y), (*c, 255))
    for dx, dy in ((4, 3), (5, 3), (3, 4), (3, 5), (4, 4)):
        img.putpixel((ox + dx, oy + dy), (*WHITE, 255))
    return framed(img)


def painting_trans_flag():
    w, h = 48, 32
    img = dithered_gradient(w, h, [hexc("8FD0F2"), hexc("BFE6F8"), hexc("F7D6E0"), hexc("FCE8EE")])
    for x, y, cw in ((30, 6, 9), (40, 11, 6), (4, 13, 7)):
        cloud(img, x, y, cw)
    rng = random.Random(8)
    for x in range(w):                                   # a pink meadow on a rolling hill
        top = 25 + round(1.6 * math.sin(x / 7.0) + 0.8 * math.sin(x / 3.1))
        for y in range(top, h):
            c = sample(R_PINK, 0.62 - (y - top) * 0.05 + rng.uniform(-0.04, 0.04))
            img.putpixel((x, y), (*c, 255))
        if rng.random() < 0.3:
            img.putpixel((x, top), (*rng.choice((WHITE, BLUE, hexc("FFF3A8"))), 255))
    for y in range(4, 28):                               # the pole
        img.putpixel((9, y), (*hexc("D8D4E4"), 255))
        img.putpixel((10, y), (*hexc("A9A4BC"), 255))
    for dx, dy in ((0, 0), (1, 0), (0, -1), (1, -1)):
        img.putpixel((9 + dx, 3 + dy), (*hexc("F4D35E"), 255))
    bands = stripes5(15)
    for x in range(11, 40):
        k = x - 11
        dy = round(1.8 * math.sin(k / 4.5))
        light = 1.0 + 0.12 * math.cos(k / 4.5)
        for y in range(15):
            c = shade(FLAG[bands[y]], light)
            img.putpixel((x, 5 + y + dy), (*c, 255))
    return framed(img)


def painting_realm_sunrise():
    w, h = 64, 32
    img = dithered_gradient(w, h, [hexc("4FB6EC"), hexc("8DD2F4"), hexc("F2B3C6"), hexc("FBD9E2"), hexc("FFF4EC")], 0, 22)
    sun = pixel_heart()                                  # a heart-shaped sun above the mountains
    for (x, y) in sun:
        edge = any((x + dx, y + dy) not in sun for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        img.putpixel((26 + x, 5 + y), (*(hexc("FFC7A0") if edge else hexc("FFEFB8")), 255))
    for x, y in ((28, 7), (29, 7), (28, 8)):
        img.putpixel((x, y), (*WHITE, 255))
    for x, y, cw in ((6, 6, 8), (44, 4, 10), (53, 10, 6), (14, 12, 5)):
        cloud(img, x, y, cw)
    rng = random.Random(21)
    for x in range(1, w - 1):                            # far lavender mountains
        top = 17 + round(2.5 * math.sin(x / 6.0) + 1.5 * math.sin(x / 2.7 + 1))
        for y in range(top, 23):
            img.putpixel((x, y), (*sample(R_LAVENDER, 0.55 + (y - top) * 0.02), 255))
    for x in range(1, w - 1):                            # near pink hills
        top = 22 + round(1.5 * math.sin(x / 9.0 + 2) + math.sin(x / 4.0))
        for y in range(top, h - 1):
            img.putpixel((x, y), (*sample(R_PINK, 0.7 - (y - top) * 0.045 + rng.uniform(-0.03, 0.03)), 255))
    for tx, crown in ((7, R_PINK), (15, R_BLUE), (47, R_PINK), (56, R_BLUE)):   # trans trees
        base = 23 + round(1.5 * math.sin(tx / 9.0 + 2) + math.sin(tx / 4.0))
        for y in range(base - 4, base):
            img.putpixel((tx, y), (*hexc("8E5A6E"), 255))
        for dy in range(-3, 2):
            for dx in range(-2, 3):
                if dx * dx + dy * dy <= 5:
                    img.putpixel((tx + dx, base - 6 + dy), (*sample(crown, 0.55 + 0.1 * dy - 0.05 * dx), 255))
    return framed(img)


def painting_crystal_bloom():
    img = dithered_gradient(16, 16, [hexc("2B2350"), hexc("4A3A7A")])
    pot = [(5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (6, 12), (7, 12), (8, 12), (9, 12), (6, 13), (7, 13),
           (8, 13), (9, 13)]
    for x, y in pot:
        img.putpixel((x, y), (*sample(R_PINK, 0.45 if y == 11 else 0.3 + 0.05 * x / 4), 255))
    crystals = [((7, 4), 6, R_BLUE), ((5, 7), 4, R_PINK), ((10, 6), 5, R_TRANS), ((9, 8), 3, R_PEARL)]
    for (cx, top), height, ramp in crystals:
        for y in range(top, 11):
            for dx in (0, 1):
                t = 0.85 - (y - top) / (11 - top) * 0.5 - dx * 0.15
                img.putpixel((cx + dx, y), (*sample(ramp, t), 255))
        img.putpixel((cx, top - 1), (*sample(ramp, 0.95), 255))
    sparkle(img, 3, 3, WHITE, big=True)
    sparkle(img, 12, 3, PINK)
    return framed(img)


def painting_floating_isle():
    w = h = 32
    img = dithered_gradient(w, h, [hexc("7CC8F0"), hexc("A9DCF6"), hexc("F4C4D2"), hexc("FBE2EA")])
    cloud(img, 3, 6, 7)
    cloud(img, 22, 23, 8)
    rng = random.Random(3)
    # The island: grass on top, then an upside-down cone of pink earth and stone.
    for x in range(5, 27):
        depth = round(10 * (1 - abs(x - 15.5) / 11.5) ** 0.8) + rng.randrange(0, 2)
        for y in range(18, 18 + max(1, depth)):
            t = (y - 18) / 10
            c = sample(R_PINK, 0.62 - 0.1 * (y == 18)) if y == 18 else sample(R_DIRT if t < 0.5 else R_STONE, 0.6 - t * 0.4)
            img.putpixel((x, y), (*c, 255))
    for x in range(6, 26):
        img.putpixel((x, 17), (*sample(R_PINK, 0.75 + rng.uniform(-0.05, 0.05)), 255))
    # The egg house: a white egg-shaped dome with a pink door and a round window.
    for y in range(7, 17):
        # An egg: narrower at the top, widest a little below the middle, cut flat where it sits on the grass.
        v = (y - 12.5) / 6.0
        half = 6.0 * math.sqrt(max(0.0, 1 - v * v)) * (0.78 + 0.22 * (y - 7) / 10)
        for x in range(round(16 - half), round(16 + half)):
            shade_t = 0.92 - 0.25 * (x - 16 + half) / (2 * half) ** 1 + 0.1 * (y < 10)
            img.putpixel((x, y), (*sample(R_PEARL, shade_t), 255))
    for y in range(13, 17):
        for x in (15, 16):
            img.putpixel((x, y), (*sample(R_PINK, 0.4 if y > 13 else 0.5), 255))
    for x, y in ((18, 10), (19, 10), (18, 11), (19, 11)):
        img.putpixel((x, y), (*sample(R_BLUE, 0.7), 255))
    for x, y in ((15, 6), (16, 6), (15, 5), (16, 5), (16, 4)):            # a little trans flag on top
        img.putpixel((x, y), (*hexc("A9A4BC"), 255))
    for x, colour in ((17, BLUE), (18, PINK)):
        img.putpixel((x, 4), (*colour, 255))
        img.putpixel((x, 5), (*(WHITE if colour == BLUE else colour), 255))
    for tx, crown in ((8, R_BLUE), (23, R_PINK)):                       # little trees
        for y in (15, 16):
            img.putpixel((tx, y), (*hexc("8E5A6E"), 255))
        for dx, dy in ((0, -1), (-1, -1), (1, -1), (0, -2), (-1, -2), (1, -2), (0, -3)):
            img.putpixel((tx + dx, 16 + dy - 1), (*sample(crown, 0.5 + 0.1 * dy * -1), 255))
    for y in range(18, 29):                                              # a thin waterfall off the edge
        if 6 <= y - 18 + 2:
            img.putpixel((24, y), (*sample(R_BLUE, 0.8 - (y % 2) * 0.1), 255))
    return framed(img)


def painting_plush_party():
    w, h = 32, 16
    img = new(w, h)
    for y in range(h):
        for x in range(w):
            c = hexc("FAD3DE") if (x // 2) % 2 == 0 else hexc("F6C2D2")
            img.putpixel((x, y), (*c, 255))
    for x in range(w):                                   # the shelf
        img.putpixel((x, 13), (*sample(R_WOOD_PINK, 0.75), 255))
        img.putpixel((x, 14), (*sample(R_WOOD_PINK, 0.45), 255))
    for i, (name, x0) in enumerate((("trans_cat_plush", 2), ("silly_cat_plush", 12), ("bluebell_cat_plush", 22))):
        tex = cat_plush(name)
        hx, hy, hx1, hy1 = PLUSH_UV["head"]["north"]
        bx, by, bx1, by1 = PLUSH_UV["body"]["north"]
        for dy in range(by1 - by):                        # body under the head
            for dx in range(bx1 - bx):
                img.putpixel((x0 + 1 + dx, 8 + dy), tex.getpixel((bx + dx, by + dy)))
        for dy in range(hy1 - hy):
            for dx in range(hx1 - hx):
                img.putpixel((x0 + dx, 3 + dy), tex.getpixel((hx + dx, hy + dy)))
        ex, ey, _, _ = PLUSH_UV["ear_inner"]
        for ear_x in (x0 + 1, x0 + 5):
            for dx in (0, 1):
                img.putpixel((ear_x + dx, 2), tex.getpixel((ex + dx, ey)))
                img.putpixel((ear_x + dx, 1), tex.getpixel((PLUSH_UV["ear"][0] + dx, PLUSH_UV["ear"][1])))
        mx, my, _, _ = PLUSH_UV["muzzle"]
        for dx in range(3):
            for dy in range(2):
                img.putpixel((x0 + 2 + dx + (dx > 0) * 0, 6 + dy), tex.getpixel((mx + dx, my + dy)))
    return framed(img)


def advancement_background():
    planks = trans_planks()
    out = planks.copy()
    for p in pixels(planks):
        r, g, b, a = planks.getpixel(p)
        out.putpixel(p, (*shade(mix((r, g, b), hexc("2A1B45"), 0.45), 0.8), 255))
    return out


# name: (blocks wide, blocks high, English title, author, texture maker)
PAINTINGS = {
    "silly_cat_portrait": (3, 3, "Portrait of a Silly Cat", "Maddie", lambda: photo_painting("silly_cat_painting.png")),
    "big_lick": (3, 3, "The Big Lick", "Maddie", lambda: photo_painting("big_lick_painting.png")),
    "trans_heart": (2, 2, "Trans Heart", "Maddie", painting_trans_heart),
    "flag_of_the_realm": (3, 2, "Flag of the Realm", "Maddie", painting_trans_flag),
    "pastel_sunrise": (4, 2, "Pastel Sunrise", "Maddie", painting_realm_sunrise),
    "crystal_bloom": (1, 1, "Crystal Bloom", "The Silly Cat", painting_crystal_bloom),
    "the_egg_house": (2, 2, "The Egg House", "Maddie", painting_floating_isle),
    "plush_party": (2, 1, "Plush Party", "The Silly Cat", painting_plush_party),
}


# ============================================================================================ Maddie and her gifts
# Maddie's skin uses the 64x64 player layout with slim (3 pixel) arms. Boxes are (u, v, width, height, depth); a box's
# faces sit at the usual spots: top (u+d, v), bottom (u+d+w, v), right (u, v+d), front (u+d, v+d), left (u+d+w, v+d),
# back (u+2d+w, v+d).
SKIN_BOXES = {
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
    "right_arm": (40, 16, 3, 12, 4), "right_sleeve": (40, 32, 3, 12, 4), "left_arm": (32, 48, 3, 12, 4),
    "left_sleeve": (48, 48, 3, 12, 4), "right_leg": (0, 16, 4, 12, 4), "right_pants": (0, 32, 4, 12, 4),
    "left_leg": (16, 48, 4, 12, 4), "left_pants": (0, 48, 4, 12, 4),
}


def skin_face(box, face):
    u, v, w, h, d = SKIN_BOXES[box]
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}[face]


# Maddie wears the owner's own skin (a 64x64 slim-arm player skin at the repository root). The spawn egg face below
# uses its colours: ash blonde hair, green eyes.
MADDIE_SKIN = os.path.join(ROOT, "maddieskintexture.png")
MADDIE = {
    "hair": hexc("D3C3BD"), "hair2": hexc("B9A7A4"), "hair3": hexc("A38A86"), "hair_light": hexc("EFD6CF"),
    "skin": hexc("FDE0D5"), "skin2": hexc("FAE5DD"), "white": hexc("FFFFFF"), "iris": hexc("87C588"), "iris2": hexc("63746D"),
    "lash": hexc("5E5C55"), "blush": hexc("F3C9C1"), "mouth": hexc("E596A8"),
}


def maddie_skin():
    """Maddie's skin, as the owner made it (64x64, slim arms, every outer layer used)."""
    img = Image.open(MADDIE_SKIN).convert("RGBA")
    assert img.size == (64, 64), "maddieskintexture.png must be a 64x64 skin"
    return img


def maddie_spawn_egg():
    """Spawn eggs are faces in 26.2: Maddie's, with her ash blonde hair, side-swept bangs and green eyes."""
    rows = [
        "................",
        "....HHHHHHHH....",
        "...HHhHHHHhHH...",
        "..HHhHHHHHHhHH..",
        "..HDHHHDDHHHDH..",
        "..HDssssssssDH..",
        "..HsLLssssLLsH..",
        "..HswIssssIwsH..",
        "..HswJssssJwsH..",
        "..HssssssssssH..",
        "..HbsssmmsssbH..",
        "..HHsssssssssH..",
        "..HHHssssssHHH..",
        "...HHH....HHH...",
        "....HD....DH....",
        "................",
    ]
    M = MADDIE
    key = {"H": M["hair"], "D": M["hair3"], "h": M["hair_light"], "s": M["skin"], "L": M["lash"], "w": M["white"],
           "I": M["iris"], "J": M["iris2"], "b": M["blush"], "m": M["mouth"]}
    img = new()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), (*key[ch], 255))
    return img


def pixel_sprite(rows, palette):
    img = new(len(rows[0]), len(rows))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), (*palette[ch], 255))
    return img


def trans_wand():
    """A white and pink striped wand with a sky blue star on top."""
    return pixel_sprite([
        "..........b.....",
        ".........bBb....",
        "......bbbBWBbbb.",
        ".......bBWWWBb..",
        "........bBWBb...",
        ".......bBbbbBb..",
        "......bb.....bb.",
        ".....op.........",
        "....opw.........",
        "...opw..........",
        "..ppw...........",
        ".opw............",
        "opp.............",
        "oo..............",
        "................",
        "................",
    ], {"b": hexc("2C6FB0"), "B": BLUE, "W": WHITE, "o": hexc("8E4A66"), "p": PINK, "w": WHITE})


def trans_wings_item():
    """A pair of feathered wings: blue tips, pink middles, white near the body."""
    return pixel_sprite([
        "................",
        "..bb........bb..",
        ".bBBb......bBBb.",
        ".bBPPb....bPPBb.",
        "bBBPPWb..bWPPBBb",
        "bBPPWWboobWWPPBb",
        "bBPPWWWooWWWPPBb",
        ".bBPPWWooWWPPBb.",
        ".bBPPWWooWWPPBb.",
        "..bBPPW..WPPBb..",
        "..bBBP....PBBb..",
        "...bBP....PBb...",
        "...bB......Bb...",
        "....b......b....",
        "................",
        "................",
    ], {"b": hexc("2C6FB0"), "B": BLUE, "P": PINK, "W": WHITE, "o": hexc("D9779A")})


def trans_magic_bolt():
    """The wand's spell: a glowing white heart in a pink and blue sparkle."""
    return pixel_sprite([
        "................",
        ".......B........",
        ".......B........",
        "....P..B..P.....",
        ".....PPWPP......",
        "....PWWWWWP.....",
        ".BBBPWWWWWPBBB..",
        "....PWWWWWP.....",
        ".....PWWWP......",
        "......PWP.......",
        ".....P.P.P......",
        "....P..B..P.....",
        ".......B........",
        ".......B........",
        "................",
        "................",
    ], {"B": BLUE, "P": PINK, "W": WHITE})


# Layout shared with TransWingsModel: bone boxes are 5x2x1 / 5x3x1, feathers 3xLx1 (a box of w x h x d at u,v covers
# u..u+2(w+d) and v..v+d+h; its two big faces are north at u+d and south at u+2d+w).
WING_ARM, WING_ARM_COVERTS, WING_HAND, WING_HAND_COVERTS = (0, 0), (14, 0), (28, 0), (42, 0)
WING_SECONDARIES = [((8 * i, 6), length) for i, length in enumerate((7, 8, 9, 10))]
WING_PRIMARIES = [((8 * i, 20), length) for i, length in enumerate((11, 12, 13, 14, 13))]
WING_WHITE, WING_PEARL = hexc("FBF9FE"), hexc("E9E5F2")
WING_PINK, WING_PINK_DEEP, WING_BLUE, WING_BLUE_DEEP = PINK, hexc("E58AA2"), BLUE, hexc("3FA9DE")


def wing_gradient(stops, t):
    t = max(0.0, min(1.0, t))
    for i in range(1, len(stops)):
        if t <= stops[i][0]:
            (a, ca), (b, cb) = stops[i - 1], stops[i]
            return mix(ca, cb, (t - a) / (b - a) if b > a else 0)
    return stops[-1][1]


def paint_wing_box(img, u, v, w, h, face_fn, edge_fn, end_fn):
    """Paints a w x h x 1 model box: face_fn for the big north/south faces, edge_fn for the thin sides and end_fn for
    the two ends. Each returns a colour or None (see-through)."""
    regions = {"north": (u + 1, v + 1, w, h), "south": (u + 2 + w, v + 1, w, h), "west": (u, v + 1, 1, h),
               "east": (u + 1 + w, v + 1, 1, h), "top": (u + 1, v, w, 1), "bottom": (u + 1 + w, v, w, 1)}
    for face, (x0, y0, fw, fh) in regions.items():
        for y in range(fh):
            for x in range(fw):
                fn = face_fn if face in ("north", "south") else edge_fn if face in ("west", "east") else end_fn
                c = fn(face, x, y)
                if c is not None:
                    img.putpixel((x0 + x, y0 + y), (*c, 255))


def wing_feather(img, u, v, length, stops):
    """A flight feather: a pale shaft, a lit and a shaded vane, a pointed tip. The underside (north, towards the body)
    is a little paler, like real wings."""
    def col(y, column, face):
        t = y / max(1, length - 1)
        c = wing_gradient(stops, t)
        if column == 1:
            c = mix(c, WING_WHITE, 0.5 * (1 - t) + 0.12)
        elif column == 2:
            c = shade(c, 0.9)
        return mix(c, WING_PEARL, 0.18) if face == "north" else c

    def face(f, x, y):
        if y == length - 1 and x != 1:
            return None
        c = col(y, x, f)
        return shade(c, 0.88) if y >= length - 2 and x != 1 else c

    paint_wing_box(img, u, v, 3, length, face,
                   lambda f, x, y: None if y >= length - 1 else shade(col(y, 0, "south"), 0.82),
                   lambda f, x, y: col(0, 1, "south") if f == "top" else shade(col(length - 1, 1, "south"), 0.85))


def wing_bone(img, u, v, w, h, tint):
    """The leading edge: a bone covered in tiny white feathers, blushing towards its far end (the north face's left
    edge is the box's min x, the far end of a right wing)."""
    def face(f, x, y):
        a = (w - 1 - x) / max(1, w - 1) if f == "north" else x / max(1, w - 1)
        c = mix(WING_WHITE, mix(WING_PEARL, tint, 0.3), a * 0.7)
        return shade(c, 0.9) if y == h - 1 else c

    paint_wing_box(img, u, v, w, h, face, lambda f, x, y: shade(WING_PEARL, 0.92),
                   lambda f, x, y: WING_WHITE if f == "top" else shade(WING_PEARL, 0.88))


def wing_coverts(img, u, v, w, h, stops):
    """Rows of small rounded covert feathers over the roots of the flight feathers, with a scalloped hem."""
    def face(f, x, y):
        if y == h - 1 and x % 2 == 1:
            return None
        c = wing_gradient(stops, y / max(1, h - 1))
        c = shade(c, 0.96) if x % 2 == 1 else c
        return mix(c, WING_PEARL, 0.15) if f == "north" else c

    paint_wing_box(img, u, v, w, h, face, lambda f, x, y: shade(wing_gradient(stops, y / max(1, h - 1)), 0.86),
                   lambda f, x, y: WING_WHITE)


def trans_wings_model_texture():
    """64x64 texture for TransWingsModel: white bones and coverts along the leading edge, secondaries white to pink,
    primaries white to pink to blue, so a spread wing reads white, pink and blue from the body to the tip."""
    img = new(64, 64)
    wing_bone(img, *WING_ARM, 5, 2, WING_PINK)
    wing_coverts(img, *WING_ARM_COVERTS, 5, 3, [(0.0, WING_WHITE), (0.6, mix(WING_WHITE, WING_PINK, 0.5)), (1.0, WING_PINK)])
    wing_bone(img, *WING_HAND, 5, 2, WING_BLUE)
    wing_coverts(img, *WING_HAND_COVERTS, 5, 2, [(0.0, WING_WHITE), (1.0, mix(WING_PINK, WING_BLUE, 0.35))])
    secondary = [(0.0, WING_WHITE), (0.3, WING_WHITE), (0.62, WING_PINK), (1.0, WING_PINK_DEEP)]
    primary = [(0.0, WING_WHITE), (0.2, WING_WHITE), (0.42, WING_PINK), (0.58, WING_PINK), (0.8, WING_BLUE),
               (1.0, WING_BLUE_DEEP)]
    for (u, v), length in WING_SECONDARIES:
        wing_feather(img, u, v, length, secondary)
    for (u, v), length in WING_PRIMARIES:
        wing_feather(img, u, v, length, primary)
    return img


# ============================================================================================ trans mobs
# Every vanilla mob gets a trans version of its texture, worn only inside the Trans Realm (TransRecolor swaps
# them in). Colours are mapped by family, not brightness, so mobs come out pink, blue AND white instead of
# mostly blue: warm colours (browns, reds, oranges) turn pink with their darkest tones blue, yellows and
# greens turn blue, blues turn pink, purples lavender, and greys white, lavender or navy depending on lightness.
ENTITY_ZIP = os.path.join(ROOT, "base entity textures.zip")
MOB_PINK = [hexc(c) for c in ("6E2945", "A84A6E", "D9779A", "F5A9B8", "FAD0DA", "FFF0F4")]
MOB_BLUE = [hexc(c) for c in ("173A63", "2A6DA8", "3E9BD8", "6CC6F2", "A6E1FA", "E3F7FF")]
MOB_WHITE = [hexc(c) for c in ("8E8AA6", "B3B0C8", "D2D0E2", "E8E7F2", "F6F5FB", "FFFFFF")]
MOB_NAVY = [hexc(c) for c in ("0E1230", "1B2350", "2D3872", "434F8F", "5E6AA8", "7C86BC")]
MOB_LAVENDER = [hexc(c) for c in ("3F3170", "65559E", "8E7EC8", "B5A8E3", "D9D0F5", "F3EFFE")]
MOB_TARGET = {"warm": MOB_PINK, "yellow": MOB_BLUE, "green": MOB_BLUE, "blue": MOB_PINK, "purple": MOB_LAVENDER,
              "white": MOB_WHITE, "navy": MOB_NAVY, "gray": MOB_LAVENDER}
# Not mobs, or textures the game tints at runtime (collars, fish patterns, dyed overlays): left alone.
MOB_SKIP_FOLDERS = {"banner", "beacon", "bell", "boat", "chest", "chest_boat", "conduit", "decorated_pot", "enchantment",
                    "end_crystal", "end_portal", "equipment", "experience", "fishing", "lead_knot", "minecart", "player",
                    "projectiles", "shield", "trident", "armorstand", "creeper"}
MOB_SKIP_WORDS = ("collar", "pattern", "overlay", "crackiness", "tropical", "sheep_wool")
AXOLOTL_COLOURS = {"lucy": (MOB_PINK, MOB_BLUE), "wild": (MOB_LAVENDER, MOB_PINK), "gold": (MOB_WHITE, MOB_PINK),
                   "cyan": (MOB_BLUE, MOB_PINK), "blue": (MOB_WHITE, MOB_BLUE)}


def colour_family(px):
    h, s, v = hsv(px)
    if s < 0.16 or v < 0.12:
        l = lum(px)
        return "white" if l > 0.62 else "navy" if l < 0.22 else "gray"
    if h < 0.11 or h >= 0.9:
        return "warm"
    if h < 0.19:
        return "yellow"
    if h < 0.47:
        return "green"
    return "blue" if h < 0.75 else "purple"


def trans_mob(img):
    families = {p: colour_family(img.getpixel(p)) for p in pixels(img) if img.getpixel(p)[3] > 0}
    span = {}
    for p, f in families.items():
        l = lum(img.getpixel(p))
        lo, hi = span.get(f, (9.0, -9.0))
        span[f] = (min(lo, l), max(hi, l))
    # The darkest 30% of the warm pixels (fur shadows, manes, dark patches) turn blue.
    warm = sorted(lum(img.getpixel(p)) for p, f in families.items() if f == "warm")
    warm_cut = warm[int(0.3 * (len(warm) - 1))] if len(warm) > 12 else -1.0
    out = img.copy()
    for p, f in families.items():
        px = img.getpixel(p)
        l = lum(px)
        lo, hi = span[f]
        norm = (l - lo) / (hi - lo) if hi > lo else 0.5
        ramp, t = MOB_TARGET[f], 0.55 * l + 0.45 * norm
        if f == "gray":
            t = l
        elif f == "warm" and l <= warm_cut:
            ramp, t = MOB_BLUE, 0.3 + 0.5 * norm
        out.putpixel(p, (*sample(ramp, t), px[3]))
    return out


def trans_axolotl(img, variant):
    main, accent = AXOLOTL_COLOURS[variant]
    lo, hi = lum_range(img)
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3] == 0:
            continue
        t = (lum(px) - lo) / (hi - lo)
        if lum(px) < 0.12:
            c = sample(MOB_NAVY, 0.3)
        elif t < 0.45:
            c = sample(accent, 0.25 + t)
        else:
            c = sample(main, 0.35 + 0.65 * t)
        out.putpixel(p, (*c, px[3]))
    return out


def trans_mob_textures():
    """Yields (path under textures/entity/, trans texture) for every vanilla mob texture in the reference zip."""
    zf = _zip(ENTITY_ZIP)
    for entry in sorted(zf.namelist()):
        if not entry.endswith(".png"):
            continue
        rel = entry.split("/", 1)[1]
        folder, filename = rel.split("/", 1)[0], rel.rsplit("/", 1)[-1]
        if folder in MOB_SKIP_FOLDERS or any(word in filename for word in MOB_SKIP_WORDS):
            continue
        with zf.open(entry) as f:
            img = Image.open(f).convert("RGBA")
        variant = filename[len("axolotl_"):-4].replace("_baby", "") if filename.startswith("axolotl_") else None
        yield rel, (trans_axolotl(img, variant) if variant in AXOLOTL_COLOURS else trans_mob(img))


# ============================================================================================ the realm's creatures (round 4)
# Trans fish, trans endermen and pastel slimes: their entity textures (layouts match the models in
# src/client/.../client/entity/), item sprites, spawn eggs and the slimes' gel blocks.
FISH_STRIPES = [[hexc(c) for c in ("2F86C4", "4FB3EA", "5BCEFA", "A6E6FC")],
                [hexc(c) for c in ("C2668B", "E58FAE", "F5A9B8", "FBD0DA")],
                [hexc(c) for c in ("B8B6CC", "DDDCEA", "F4F3FA", "FFFFFF")],
                [hexc(c) for c in ("C2668B", "E58FAE", "F5A9B8", "FBD0DA")],
                [hexc(c) for c in ("2F86C4", "4FB3EA", "5BCEFA", "A6E6FC")]]
FISH_EYE = hexc("1B2650")
GEL_PINK = [hexc(c) for c in ("B04E78", "D9759A", "F5A9B8", "FBD0DA", "FFF0F4")]
GEL_BLUE = [hexc(c) for c in ("2A6FB0", "3E9BD8", "6CC6F2", "A6E1FA", "E3F7FF")]
PEARL_WHITE = [hexc(c) for c in ("6E7FA8", "A9B8D8", "D7E2F2", "F2F5FB", "FFFFFF")]
CRYSTAL_PEARL = [hexc(c) for c in ("1F3F7A", "3A6FB8", "5BCEFA", "B9C6F2", "F5A9B8", "FCD6E0", "FFFFFF")]


def paint_face(img, u, v, w, h, colour_at):
    """Fills a w x h rectangle of a model texture; colour_at(x, y) returns an RGB, an RGBA or None (left clear)."""
    for y in range(h):
        for x in range(w):
            c = colour_at(x, y)
            if c is not None:
                img.putpixel((u + x, v + y), (*c, 255) if len(c) == 3 else c)


# Colourways of the trans fish, which wear vanilla's cod texture recoloured: (back, side rows top to bottom, belly,
# fins). Read round the fish (back, sides, belly) each is a run of the flag's colours.
FISH_COLOURWAYS = {
    "blue": (0, (0, 1, 2, 1), 0, 1),
    "pink": (1, (1, 2, 0, 2), 1, 0),
    "white": (2, (2, 1, 0, 1), 2, 1),
}


def trans_fish(colourway):
    """vanilla's cod texture in trans stripes, keeping the cod's own shading and scales (TransFishRenderer extends
    CodRenderer, so the fish keeps vanilla's model and animations). The layout is vanilla's: nose (0,0), body (0,0),
    head (11,0), fins from x 21."""
    cod = vextra("entity/fish/cod.png")
    back, rows, belly, fins = FISH_COLOURWAYS[colourway]
    ramps = [FISH_STRIPES[0], FISH_STRIPES[1], FISH_STRIPES[2]]
    opaque = [p for p in pixels(cod) if cod.getpixel(p)[3]]
    lo, hi = min(lum(cod.getpixel(p)) for p in opaque if lum(cod.getpixel(p)) > 0.2), max(lum(cod.getpixel(p)) for p in opaque)

    def part(x, y):
        """Which colour a pixel takes: an index into ramps, from where it sits on the cod's texture."""
        if x <= 5 and y <= 3:                         # nose: top/bottom faces on row 0, sides rows 1-3
            return (back if x <= 2 else belly) if y == 0 else rows[y - 1]
        if 7 <= x <= 10 and y <= 6:                   # body top (x 7-8) and bottom (x 9-10)
            return back if x <= 8 else belly
        if x <= 17 and 7 <= y <= 10:                  # body sides
            return rows[y - 7]
        if 11 <= x <= 20 and y <= 2:                  # head top (x 14-15) and bottom (x 16-17)
            return back if x <= 15 else belly
        if 11 <= x <= 20 and 3 <= y <= 6:             # head sides
            return rows[y - 3]
        return fins

    img = new(32, 32)
    for p in opaque:
        px = cod.getpixel(p)
        if lum(px) < 0.2:
            img.putpixel(p, (*FISH_EYE, 255))
            continue
        t = (lum(px) - lo) / (hi - lo)
        img.putpixel(p, (*sample(ramps[part(*p)], 0.1 + 0.85 * t), 255))
    return img


def diagonal_flag(img, pts, toast=0.0):
    """Recolours the given pixels of a diagonal fish sprite (head at the top right) into five flag stripes along its body."""
    across = [p[0] + p[1] for p in pts]
    lo, hi = min(across), max(across)
    llo, lhi = lum_range(img, mask=lambda p, px: p in pts)
    out = img.copy()
    for p in pts:
        px = img.getpixel(p)
        band = min(4, int((p[0] + p[1] - lo) / (hi - lo + 1) * 5))
        t = 0.15 + 0.85 * (lum(px) - llo) / (lhi - llo)
        c = sample(FISH_STRIPES[band], t)
        if toast:
            c = mix(c, sample([hexc("8A5A2B"), hexc("C98F4E"), hexc("E8C38C")], t), toast)
        out.putpixel(p, (*c, px[3]))
    return out


def _is_dark(px):
    return lum(px) < 0.12


def trans_fish_item(cooked=False):
    img = vitem("cooked_cod" if cooked else "cod")
    body = {p for p in pixels(img) if img.getpixel(p)[3] and not _is_dark(img.getpixel(p))}
    out = diagonal_flag(img, body, toast=0.38 if cooked else 0.0)
    for p in pixels(img):
        if img.getpixel(p)[3] and _is_dark(img.getpixel(p)):
            out.putpixel(p, (*(hexc("4A2A1A") if cooked else FISH_EYE), 255))
    return out


def trans_fish_bucket():
    img = vitem("cod_bucket")
    fish = {p for p in pixels(img) if img.getpixel(p)[3] and colour_family(img.getpixel(p)) == "warm"}
    out = diagonal_flag(img, fish)
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3] and colour_family(px) == "blue":
            out.putpixel(p, (*sample(R_BLUE, 0.35 + 0.6 * lum(px)), px[3]))
        elif px[3] and _is_dark(px) and p[1] < 8:
            out.putpixel(p, (*FISH_EYE, 255))
    return out


def trans_fish_spawn_egg():
    img = vitem("cod_spawn_egg")
    out = img.copy()
    fish = [p for p in pixels(img) if img.getpixel(p)[3] and colour_family(img.getpixel(p)) in ("warm", "yellow", "white", "gray")]
    ys = [p[1] for p in fish]
    lo, hi = min(ys), max(ys)
    llo, lhi = lum_range(img)
    for p in fish:
        px = img.getpixel(p)
        band = min(4, int((p[1] - lo) / (hi - lo + 1) * 5))
        out.putpixel(p, (*sample(FISH_STRIPES[band], 0.2 + 0.8 * (lum(px) - llo) / (lhi - llo)), px[3]))
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3] and colour_family(px) == "blue":
            out.putpixel(p, (*sample(R_BLUE, 0.3 + 0.6 * lum(px)), px[3]))
        elif px[3] and _is_dark(px):
            out.putpixel(p, (*FISH_EYE, 255))
    return out


# ---- the trans enderman: vanilla's enderman layout, snow white with soft pink and blue blended in, pink eyes
E_WHITE = hexc("F6F4FB")
E_SHADOW = hexc("E3E0F0")
E_PINK = hexc("F9D3E0")
E_BLUE = hexc("CFEAFB")
E_EYE = hexc("FF5FAE")
E_EYE_EDGE = hexc("FFA6D2")
E_EYES = {(9, 12): E_EYE, (14, 12): E_EYE, (8, 12): E_EYE_EDGE, (10, 12): E_EYE_EDGE, (13, 12): E_EYE_EDGE, (15, 12): E_EYE_EDGE}


def _enderman_tint(x, y):
    """(tint colour, strength) at a texel: limbs go white, pink, then blue towards the hands and feet; the body is pink at
    the chest fading to blue at the hips; the head is white with a faint blue crown."""
    if x >= 56:
        t = y / 31
        if t < 0.25:
            return E_PINK, 0.0
        if t < 0.55:
            return E_PINK, (t - 0.25) / 0.3 * 0.55
        return E_BLUE, min(1.0, (t - 0.55) / 0.35) * 0.85
    if x >= 32 and y >= 16:
        t = (y - 16) / 15
        return (E_PINK, 0.55 * (1 - t / 0.6)) if t < 0.6 else (E_BLUE, (t - 0.6) / 0.4 * 0.7)
    if y < 16:
        return E_BLUE, 0.35 if y < 8 else 0.0
    return E_PINK, 0.25


def trans_enderman():
    with _zip(ENTITY_ZIP).open("base entity textures/enderman/enderman.png") as f:
        src = Image.open(f).convert("RGBA")
    rng = random.Random(23)
    out = new(64, 32)
    for (x, y) in pixels(src):
        px = src.getpixel((x, y))
        if px[3] == 0:
            continue
        tint, strength = _enderman_tint(x, y)
        if px[:3] == (0, 0, 0):            # vanilla's dark pattern becomes a soft tinted line, never dark
            base = mix(mix(E_SHADOW, tint, 0.35 + 0.65 * strength), tint, 0.2)
        else:                               # the body colour: near white, gently tinted
            base = mix(E_WHITE, tint, strength * 0.75)
        out.putpixel((x, y), (*shade(base, 1.0 + (rng.random() - 0.5) * 0.04), 255))
    for p, c in E_EYES.items():
        out.putpixel(p, (*c, 255))
    return out


def trans_enderman_eyes():
    """Just the eyes, for the full-bright eyes model."""
    img = new(64, 32)
    for p, c in E_EYES.items():
        img.putpixel(p, (*c, 255))
    return img


# ---- pastel slimes: a sleepy-faced cube after the owner's reference creature (slit eyes and a little mouth on a lighter
# lower band), a clear jelly coat, and a bow for tamed slimes
SLIMES = {
    "pink": {"top": [hexc(c) for c in ("D9789A", "EB93AE", "F5A9B8", "F9BECB", "FCD3DC")],
             "band": [hexc(c) for c in ("F2B6C6", "F8C8D4", "FCDAE2", "FFE8EE", "FFF3F6")],
             "eye": hexc("5E2152"), "mouth": hexc("7A2E66"), "blush": hexc("F08FAE"),
             "bow": [hexc(c) for c in ("2F8FD0", "5BCEFA", "A6E6FC")]},
    "blue": {"top": [hexc(c) for c in ("4F8FD6", "68A8E8", "7FBDF2", "98CEF6", "B3DDF9")],
             "band": [hexc(c) for c in ("7FCFEF", "93DCF5", "A9E6F9", "C2EFFB", "DDF7FD")],
             "eye": hexc("2E2378"), "mouth": hexc("3C2F8F"), "blush": hexc("9FB6F2"),
             "bow": [hexc(c) for c in ("D9708F", "F5A9B8", "FCD3DC")]},
    "white": {"top": [hexc(c) for c in ("C6C0DA", "D8D4E7", "E8E6F1", "F4F3F9", "FFFFFF")],
              "band": [hexc(c) for c in ("EEEAF6", "F4F2F9", "F9F8FC", "FFFFFF", "FFFFFF")],
              "eye": hexc("4A3F6E"), "mouth": hexc("5E4A80"), "blush": hexc("F5B5C8"),
              "bow": [hexc(c) for c in ("D9708F", "F5A9B8", "FCD3DC")]},
    "lavender": {"top": [hexc(c) for c in ("9C84D4", "B09BE1", "C3B1EB", "D3C5F2", "E2D8F8")],
                 "band": [hexc(c) for c in ("D2C6F1", "DCD2F5", "E7DFF9", "F0EBFC", "F8F5FE")],
                 "eye": hexc("3A2470"), "mouth": hexc("4A2F86"), "blush": hexc("E7A6D6"),
                 "bow": [hexc(c) for c in ("2F8FD0", "5BCEFA", "A6E6FC")]},
    # The rare one: striped like the flag, its sleepy face on the white stripe.
    "trans": {"top": [hexc(c) for c in ("4F8FD6", "68A8E8", "7FBDF2", "98CEF6", "B3DDF9")],
              "band": [hexc(c) for c in ("EEEAF6", "F4F2F9", "F9F8FC", "FFFFFF", "FFFFFF")],
              "eye": hexc("2E2378"), "mouth": hexc("7A2E66"), "blush": hexc("F08FAE"),
              "bow": [hexc(c) for c in ("C9C4DA", "F4F3F9", "FFFFFF")],
              "stripes": ["blue", "blue", "pink", "pink", "pink", "white", "white", "pink", "pink", "pink", "blue", "blue"]},
}
SLIME_SIZE = 12
SLIME_BAND = 6      # first row of the lighter lower band (and of the eyes)


def _slime_body_pixel(colours, rng, y, side):
    if "stripes" in colours and side not in ("top", "bottom"):
        ramp = {"blue": SLIMES["blue"]["top"], "pink": SLIMES["pink"]["top"], "white": SLIMES["white"]["band"]}[colours["stripes"][y]]
        darker = {"front": 0.0, "side": -0.1, "back": -0.06}[side]
        return sample(ramp, 0.5 + 0.25 * rng.random() + darker)
    if side == "top":
        return sample(colours["top"], 0.62 + 0.25 * rng.random())
    if side == "bottom":
        return sample(colours["band"], 0.05 + 0.2 * rng.random())
    darker = {"front": 0.0, "side": -0.1, "back": -0.06}[side]
    if y >= SLIME_BAND:
        t = 0.75 - 0.5 * (y - SLIME_BAND) / (SLIME_SIZE - SLIME_BAND) + 0.15 * rng.random() + darker
        return sample(colours["band"], t)
    t = 0.38 + 0.25 * (1 - y / SLIME_BAND) + 0.22 * rng.random() + darker
    return sample(colours["top"], t)


def pastel_slime(kind):
    """64x64 for PastelSlimeModel: body cube 0,0 · jelly coat 0,24 · bow loops 48,0 · bow knot 48,8."""
    c = SLIMES[kind]
    rng = random.Random(7 if kind == "pink" else 9)
    img = new(64, 64)
    n = SLIME_SIZE
    for name, (u, v) in {"top": (12, 0), "bottom": (24, 0), "side_r": (0, 12), "front": (12, 12), "side_l": (24, 12),
                         "back": (36, 12)}.items():
        side = "side" if name.startswith("side") else name
        paint_face(img, u, v, n, n, lambda x, y: _slime_body_pixel(c, rng, y, side))
    # the face: sleepy slit eyes on the band's first row, a tiny mouth, a faint blush
    u, v = 12, 12
    for x in (1, 2, 3, 8, 9, 10):
        img.putpixel((u + x, v + SLIME_BAND), (*c["eye"], 255))
    for x in (5, 6):
        img.putpixel((u + x, v + SLIME_BAND + 2), (*c["mouth"], 255))
    for x in (1, 2, 9, 10):
        img.putpixel((u + x, v + SLIME_BAND + 1), (*mix(c["blush"], sample(c["band"], 0.6), 0.35), 255))
    # the jelly coat: nearly clear, a little thicker at the edges, with a glossy glint on every face but the bottom
    light = mix(sample(c["band"], 1.0), WHITE, 0.3)
    for name, (u, v) in {"top": (12, 24), "bottom": (24, 24), "side_r": (0, 36), "front": (12, 36), "side_l": (24, 36),
                         "back": (36, 36)}.items():
        paint_face(img, u, v, n, n, lambda x, y: (*light, 78 if x in (0, n - 1) or y in (0, n - 1) else 30))
        if name != "bottom":
            for (x, y) in ((1, 1), (2, 1), (3, 1), (1, 2), (1, 3)):
                img.putpixel((u + x, v + y), (255, 255, 255, 170))
    # the bow: loops (4x3x1) at (48,0), knot (2x2x2) at (48,8)
    bow = c["bow"]
    paint_face(img, 48, 0, 10, 4, lambda x, y: sample(bow, 0.3 + 0.6 * rng.random()))
    paint_face(img, 49, 1, 4, 3, lambda x, y: sample(bow, 0.45 + 0.4 * (x / 3) + (0.15 if y == 0 else 0)))
    paint_face(img, 48, 8, 8, 4, lambda x, y: WHITE if (x + y) % 4 else mix(WHITE, bow[1], 0.25))
    return img


def _egg_recolour(name, ramp_for):
    """Recolours a vanilla spawn egg: ramp_for(px, pos) picks (ramp, lo, hi) for each pixel; brightness is mapped within
    each group."""
    img = vitem(name)
    out = img.copy()
    groups = {}
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3]:
            groups.setdefault(ramp_for(px, p), []).append(p)
    for (ramp, lo_t, hi_t), pts in groups.items():
        values = [lum(img.getpixel(p)) for p in pts]
        lo, hi = min(values), max(values) + 1e-6
        for p in pts:
            px = img.getpixel(p)
            out.putpixel(p, (*sample(ramp, lo_t + (hi_t - lo_t) * (lum(px) - lo) / (hi - lo)), px[3]))
    return out


def trans_enderman_spawn_egg():
    """The enderman's egg in pearl white, its purple spots turned pink and blue."""
    pink, blue, pearl = tuple(GEL_PINK), tuple(GEL_BLUE), tuple(PEARL_WHITE)
    return _egg_recolour("enderman_spawn_egg", lambda px, p: (pink if (p[0] + p[1]) % 2 == 0 else blue, 0.4, 0.95)
                         if colour_family(px) in ("purple", "warm", "blue") else (pearl, 0.35, 1.0))


def pastel_slime_spawn_egg():
    pink, blue = tuple(GEL_PINK), tuple(GEL_BLUE)
    return _egg_recolour("slime_spawn_egg", lambda px, p: (pink, 0.25, 1.0) if lum(px) > 0.42 else (blue, 0.3, 0.85))


def fairy_spawn_egg():
    """The allay's egg in pearl white, its spots pink and blue."""
    pink, blue, pearl = tuple(GEL_PINK), tuple(GEL_BLUE), tuple(PEARL_WHITE)
    return _egg_recolour("allay_spawn_egg", lambda px, p: (pink if (p[0] + p[1]) % 2 == 0 else blue, 0.35, 0.95)
                         if lum(px) > 0.55 else (pearl, 0.4, 1.0))


def bottled_fairy_item():
    """A glass bottle with a little fairy glowing inside; its light fills the bottle with a soft pink-blue shimmer."""
    img = vitem("glass_bottle")
    out = img.copy()
    # The inside of the bottle: the clear pixels reachable from its middle, below the neck.
    inside, todo = set(), [(8, 11)]
    while todo:
        x, y = todo.pop()
        if (x, y) in inside or not (0 <= x < 16 and 7 <= y < 16) or img.getpixel((x, y))[3] != 0:
            continue
        inside.add((x, y))
        todo += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    for x, y in inside:
        out.putpixel((x, y), (*mix(mix(PINK, BLUE, (x - 5) / 6), WHITE, 0.35), 175))
    for (x, y), c in {(7, 10): WHITE, (8, 10): mix(PINK, WHITE, 0.4), (7, 11): mix(BLUE, WHITE, 0.4), (8, 11): PINK}.items():
        out.putpixel((x, y), (*c, 255))
    for (x, y) in ((5, 9), (6, 9), (6, 10), (9, 9), (10, 9), (9, 10)):
        if (x, y) in inside:
            out.putpixel((x, y), (255, 255, 255, 220))
    return out


def trans_pearl_item():
    """An ender pearl turned pearl white, with a soft pink sheen at the top and blue at the bottom."""
    img = vitem("ender_pearl")
    lo, hi = lum_range(img)
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if not px[3]:
            continue
        c = sample(PEARL_WHITE, 0.15 + 0.85 * (lum(px) - lo) / (hi - lo))
        c = mix(c, PINK, max(0.0, 0.35 - 0.03 * (x + y))) if x + y < 12 else mix(c, BLUE, min(0.35, 0.03 * (x + y - 12)))
        out.putpixel((x, y), (*c, px[3]))
    return out


def trans_crystal_pearl_item():
    """An eye of ender re-cut as a crystal: blue to lavender to pink, with a bright star where the pupil was."""
    img = vitem("ender_eye")
    lo, hi = lum_range(img)
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3]:
            out.putpixel((x, y), (*sample(CRYSTAL_PEARL, 0.1 + 0.8 * (lum(px) - lo) / (hi - lo)), px[3]))
    star = {(7, 7): WHITE, (8, 7): WHITE, (7, 8): WHITE, (8, 8): WHITE, (7, 6): hexc("FCE6EE"), (8, 9): hexc("E6F8FF"),
            (6, 8): hexc("FCE6EE"), (9, 7): hexc("E6F8FF")}
    for p, c in star.items():
        if out.getpixel(p)[3]:
            out.putpixel(p, (*c, 255))
    return out


def pastel_gel_item():
    """A slime ball swirled pink and blue."""
    img = vitem("slime_ball")
    lo, hi = lum_range(img)
    out = img.copy()
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if px[3]:
            ramp = GEL_PINK if (x - 8) * 0.8 + (y - 8) < 0.5 + 1.5 * ((x + y) % 3 == 0) else GEL_BLUE
            out.putpixel((x, y), (*sample(ramp, 0.1 + 0.9 * (lum(px) - lo) / (hi - lo)), px[3]))
    return out


def gumdrop_item():
    """Two sugared gumdrops, a pink one in front of a blue one."""
    rows = [
        "................",
        "................",
        "................",
        "......bbbb......",
        ".....bBBBbb.....",
        "....bBwBBBbb....",
        "....bBBBsBbb....",
        "...bbBsBBBBbb...",
        "...bbpppppbbb...",
        "..bbpPPwPPpbb...",
        "..bpPwPPPsPpb...",
        "..bpPPPsPPPpd...",
        "..ppPsPPPPPPp...",
        "..pPPPPPPsPPp...",
        "..dpppppppppd...",
        "................",
    ]
    palette = {"b": hexc("3E9BD8"), "B": hexc("7FD0F8"), "p": hexc("D9759A"), "P": hexc("F5A9B8"),
               "w": WHITE, "s": hexc("FFF0F6"), "d": hexc("A84A6E")}
    return from_ascii(rows, palette)


def gel_block(ramp):
    """Vanilla's slime block (a jelly cube around a firmer core) in pastel pink or blue."""
    img = vblock("slime_block")
    lo, hi = lum_range(img)
    out = img.copy()
    for p in pixels(img):
        px = img.getpixel(p)
        if px[3]:
            out.putpixel(p, (*sample(ramp, 0.05 + 0.95 * (lum(px) - lo) / (hi - lo)), px[3]))
    return out


def creature_textures():
    """(path under textures/, image) for everything above."""
    yield "entity/trans_fish/blue.png", trans_fish("blue")
    yield "entity/trans_fish/pink.png", trans_fish("pink")
    yield "entity/trans_fish/white.png", trans_fish("white")
    yield "entity/trans_enderman/trans_enderman.png", trans_enderman()
    yield "entity/trans_enderman/trans_enderman_eyes.png", trans_enderman_eyes()
    for kind in SLIMES:
        yield f"entity/pastel_slime/{kind}.png", pastel_slime(kind)
    items = {"trans_fish": trans_fish_item(), "cooked_trans_fish": trans_fish_item(cooked=True),
             "trans_fish_bucket": trans_fish_bucket(), "trans_fish_spawn_egg": trans_fish_spawn_egg(),
             "trans_pearl": trans_pearl_item(), "trans_crystal_pearl": trans_crystal_pearl_item(),
             "trans_enderman_spawn_egg": trans_enderman_spawn_egg(), "pastel_gel": pastel_gel_item(), "gumdrop": gumdrop_item(),
             "pastel_slime_spawn_egg": pastel_slime_spawn_egg(), "fairy_spawn_egg": fairy_spawn_egg(),
             "bottled_fairy": bottled_fairy_item()}
    for name, img in items.items():
        yield f"item/{name}.png", img
    yield "block/pink_gel_block.png", gel_block(GEL_PINK)
    yield "block/blue_gel_block.png", gel_block(GEL_BLUE)


# ============================================================================================ the Fairy Realm (round 4 endgame)
# The Trans Fairy (layouts match TransFairyModel), her ice crystals, the Fairy Jar's light, the portal frame and the
# shimmering portal, the jar, and the fairy's spawn egg.
FAIRY_SKIN = [hexc(c) for c in ("E3B4A6", "F2CDBF", "FBE0D6", "FEEDE6", "FFF6F2")]
FAIRY_HAIR = [hexc(c) for c in ("B04E78", "D06A94", "E888AE", "F4A8C4", "FAC8DA")]
FAIRY_HAIR_TIP = [hexc(c) for c in ("3E8FD0", "5BB6EC", "86D2F7")]
DRESS_BLUE = [hexc(c) for c in ("3F8FD0", "5BB8EE", "7FD0F8", "A6E2FC", "D2F2FE")]
DRESS_PINK = [hexc(c) for c in ("D46F93", "E890AC", "F5A9B8", "F9C4CF", "FDE2E8")]
DRESS_WHITE = [hexc(c) for c in ("C9C6DC", "E0DEEC", "F2F1F8", "FAF9FD", "FFFFFF")]
FAIRY_EYE = hexc("3C8FD8")
FAIRY_EYE_DARK = hexc("1F3F7A")
FAIRY_LASH = hexc("5A2A4A")


def _paint(img, u, v, w, h, fn):
    for y in range(h):
        for x in range(w):
            c = fn(x, y)
            if c is not None:
                img.putpixel((u + x, v + y), (*c, 255) if len(c) == 3 else c)


def fill_box(img, u, v, w, h, d, fn):
    for face, (fu, fv, fw, fh) in box_faces(u, v, w, h, d).items():
        _paint(img, fu, fv, fw, fh, lambda x, y, face=face, fw=fw, fh=fh: fn(face, x, y, fw, fh))


def trans_fairy():
    """128x128 for TransFairyModel's solid layer: head 0,0 (face on the front) · hair shell 32,0 · back hair 64,0 ·
    side locks 88,0 · tiara 96,0 and 96,4 · arms 40,16 · puff sleeves 48,16 · wand 64,16 · bodice 0,32 · collar 20,32 ·
    skirt tiers 0,44 / 0,53 / 0,64 · legs 0,76 · shoes 8,76."""
    rng = random.Random(5)
    img = new(128, 128)
    # head 8x8x8 at (0,0): skin, face on the front
    fill_box(img, 0, 0, 8, 8, 8, lambda f, x, y, w, h: sample(FAIRY_SKIN, 0.55 + 0.15 * (f == "front") - 0.1 * (f in ("left", "right")) + 0.05 * rng.random()))
    fu, fv = 8, 8
    face = {
        # eyes: big, two pixels wide, blue with a white sparkle, lashes above
        (1, 3): FAIRY_LASH, (2, 3): FAIRY_LASH, (5, 3): FAIRY_LASH, (6, 3): FAIRY_LASH,
        (1, 4): WHITE, (2, 4): FAIRY_EYE, (5, 4): FAIRY_EYE, (6, 4): WHITE,
        (1, 5): FAIRY_EYE_DARK, (2, 5): FAIRY_EYE_DARK, (5, 5): FAIRY_EYE_DARK, (6, 5): FAIRY_EYE_DARK,
        (0, 6): hexc("F7A8BC"), (7, 6): hexc("F7A8BC"),
        (3, 6): hexc("E07A98"), (4, 6): hexc("E07A98"),
    }
    for (x, y), c in face.items():
        img.putpixel((fu + x, fv + y), (*c, 255))
    # hair shell 8x8x8 at (32,0): fringe on the front top rows, full on top/back/sides, open face
    def hair_px(f, x, y, w, h):
        t = 0.45 + 0.35 * rng.random()
        c = sample(FAIRY_HAIR, t)
        if f == "front":
            fringe = [3, 3, 2, 1, 1, 2, 3, 3]          # fringe depth per column (rows from the top)
            if y >= fringe[x]:
                return None if not (x in (0, 7) and y < 7) else c   # side strands frame the face
            return c
        if f in ("left", "right") and y >= 6:
            return sample(FAIRY_HAIR_TIP, 0.5 + 0.4 * rng.random()) if rng.random() < 0.5 else c
        return c
    fill_box(img, 32, 0, 8, 8, 8, hair_px)
    # long back hair 9x13x2 at (64,0): pink fading to blue tips
    fill_box(img, 64, 0, 9, 13, 2, lambda f, x, y, w, h: sample(FAIRY_HAIR_TIP, 0.3 + 0.5 * rng.random()) if y >= h - 4 and f != "top"
             else sample(FAIRY_HAIR, 0.35 + 0.45 * rng.random() - 0.2 * (x % 3 == 0)))
    # side locks 2x9x2 at (88,0)
    fill_box(img, 88, 0, 2, 9, 2, lambda f, x, y, w, h: sample(FAIRY_HAIR_TIP, 0.6) if y >= h - 3 else sample(FAIRY_HAIR, 0.5 + 0.3 * rng.random()))
    # tiara: band 5x1x1 at (96,0) silver-blue; points 1x2x1 at (96,4) crystal pink
    fill_box(img, 96, 0, 5, 1, 1, lambda f, x, y, w, h: hexc("DCEBFA") if x % 2 else hexc("A6D6F5"))
    fill_box(img, 96, 4, 1, 2, 1, lambda f, x, y, w, h: hexc("FBD0DA") if y == 0 else hexc("F5A9B8"))
    # bodice 6x8x4 at (0,32): flag blue with white lace lacing down the front
    def bodice(f, x, y, w, h):
        c = sample(DRESS_BLUE, 0.55 + 0.2 * rng.random() - 0.1 * (f in ("left", "right")))
        if f == "front" and x in (2, 3) and y % 2 == 1:
            return sample(DRESS_WHITE, 0.9)
        if f == "front" and y == h - 1:
            return sample(DRESS_PINK, 0.7)
        return c
    fill_box(img, 0, 32, 6, 8, 4, bodice)
    # collar band 7x3x5 at (20,32): white lace with pink dots
    fill_box(img, 20, 32, 7, 3, 5, lambda f, x, y, w, h: sample(DRESS_PINK, 0.8) if (x + y) % 3 == 0 else sample(DRESS_WHITE, 0.85))
    # skirt tiers: pink (9x3x6 at 0,44), white (11x3x8 at 0,53), pink with a blue hem (13x3x10 at 0,64)
    def tier(ramp, hem=None):
        def fn(f, x, y, w, h):
            if f in ("top", "bottom"):
                return sample(ramp, 0.45)
            if hem and y == h - 1:
                return sample(hem, 0.55 + 0.25 * (x % 2))
            c = sample(ramp, 0.5 + 0.25 * rng.random())
            if y == 0 and x % 2 == 0:                    # a scalloped frill at the top of each tier
                c = sample(ramp, 0.85)
            if rng.random() < 0.06:                       # glitter
                c = WHITE
            return c
        return fn
    fill_box(img, 0, 44, 9, 3, 6, tier(DRESS_PINK))
    fill_box(img, 0, 53, 11, 3, 8, tier(DRESS_WHITE))
    fill_box(img, 0, 64, 13, 3, 10, tier(DRESS_PINK, DRESS_BLUE))
    # arms 2x10x2 at (40,16): skin with white gloves at the hand
    fill_box(img, 40, 16, 2, 10, 2, lambda f, x, y, w, h: sample(DRESS_WHITE, 0.85) if y >= h - 3 else sample(FAIRY_SKIN, 0.55 + 0.1 * rng.random()))
    # puff sleeves 3x3x3 at (48,16)
    fill_box(img, 48, 16, 3, 3, 3, lambda f, x, y, w, h: sample(DRESS_PINK, 0.6 + 0.25 * ((x + y) % 2)))
    # wand stick 1x1x9 at (64,16): pearl white with a pink ribbon wrap
    fill_box(img, 64, 16, 1, 1, 9, lambda f, x, y, w, h: sample(DRESS_PINK, 0.7) if (x + y) % 4 == 0 else hexc("F4F2FA"))
    # legs 2x9x2 at (0,76): white stockings; shoes 2x1x3 at (8,76): pink
    fill_box(img, 0, 76, 2, 9, 2, lambda f, x, y, w, h: sample(DRESS_WHITE, 0.75 + 0.1 * (y % 2)))
    fill_box(img, 8, 76, 2, 1, 3, lambda f, x, y, w, h: sample(DRESS_PINK, 0.45))
    return img


def trans_fairy_glow():
    """64x64 for TransFairyModel's glow layer (drawn translucent and full bright): upper wings (15x17 planes) 0,0 ·
    lower wings (10x13) 0,20 · the wand's star 0,48 and 14,48. White at the hinge, pink, then blue at the rim,
    with three veins and a scatter of glitter."""
    img = new(64, 64)
    rng = random.Random(8)

    def wing(u, v, w, h, cy):
        """One wing plane, drawn on both faces (north at u, south at u + w, mirrored so the hinge is at x = 0 / w - 1)."""
        cells = {}
        for y in range(h):
            for x in range(w):
                nx, ny = x / (w - 1), (y - cy) / (h - 1 - cy if y > cy else cy)
                r = nx * nx + ny * ny * (0.85 + 0.3 * nx)
                if r > 1.0:
                    continue
                cells[(x, y)] = r
        for (x, y), r in cells.items():
            t = min(1.0, math.sqrt(x * x + (y - cy) * (y - cy)) / (w * 0.95))
            # white at the hinge, trans pink in the middle, trans blue at the rim
            if t < 0.45:
                c = mix(WHITE, PINK, t / 0.45)
            else:
                c = mix(PINK, BLUE, min(1.0, (t - 0.45) / 0.45))
            a = 105 + int(60 * t)
            edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            # three veins fanning out from the hinge
            vein = False
            for angle in (-0.75, -0.15, 0.45):
                vx, vy = math.cos(angle), math.sin(angle)
                d = abs(x * vy - (y - cy) * vx)
                if d < 0.55 and x * vx + (y - cy) * vy > 0:
                    vein = True
            if edge:
                c, a = mix(c, BLUE, 0.55) if t > 0.45 else mix(c, PINK, 0.6), 235
            elif vein:
                c, a = mix(c, WHITE, 0.55), 200
            elif rng.random() < 0.025:
                c, a = WHITE, 255
            for side, px in ((0, u + x), (1, u + 2 * w - 1 - x)):
                img.putpixel((px, v + y), (*c, a))

    wing(0, 0, 15, 17, cy=9)
    wing(0, 20, 10, 13, cy=3)
    # star: arm cubes 5x1x1 and 1x5x1 at (0,48), centre 3x3x1.2 at (14,48)
    _paint(img, 0, 48, 14, 8, lambda x, y: (255, 236, 246, 255) if (x + y) % 3 else (166, 230, 252, 255))
    _paint(img, 14, 48, 10, 6, lambda x, y: (255, 255, 255, 255) if (x + y) % 2 else (245, 169, 184, 255))
    return img


PEARL_STONE = [hexc(c) for c in ("8E89A8", "B3AFC9", "D2CFE2", "E9E7F2", "F7F6FB")]
SOCKET = [hexc(c) for c in ("120E2C", "1E1846", "2D2560", "3E3478")]
OPAL = [hexc(c) for c in ("F5A9B8", "C9B8F2", "8ED8FA", "5BCEFA", "B8F0F5", "FFFFFF", "F9C6E6")]


def frame_recolour(img):
    """End stone -> pearl stone, the green trim -> pink and blue, the dark socket -> deep indigo."""
    out = img.copy()
    lo, hi = lum_range(img)
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if not px[3]:
            continue
        h, s, v = hsv(px)
        t = (lum(px) - lo) / (hi - lo)
        if s > 0.25 and 0.12 < h < 0.2:          # end stone (yellow)
            c = sample(PEARL_STONE, 0.25 + 0.75 * t)
        elif lum(px) < 0.16:                       # the dark socket
            c = sample(SOCKET, t * 3)
        else:                                       # green trim: pink on the outer ring, blue inside
            ring = min(x, y, 15 - x, 15 - y)
            c = sample([hexc("D46F93"), hexc("F5A9B8"), hexc("FBD0DA")] if ring % 2 == 0 else [hexc("2F86C4"), hexc("5BCEFA"), hexc("A6E6FC")],
                       0.2 + 0.8 * t)
        out.putpixel((x, y), (*c, px[3]))
    return out


def fairy_frame_top():
    return frame_recolour(vblock("end_portal_frame_top"))


def fairy_frame_side():
    return frame_recolour(vblock("end_portal_frame_side"))


def fairy_frame_pearl():
    """The socket with a crystal pearl in it (the frame model shows the middle 8x8 and a 3-pixel rim of it)."""
    img = new(16, 16)
    rng = random.Random(4)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            t = max(0.0, 1.0 - d / 5.5)
            c = sample([hexc("2C6FB0"), hexc("5BCEFA"), hexc("C9B8F2"), hexc("F5A9B8"), hexc("FFFFFF")], 0.15 + 0.85 * t)
            img.putpixel((x, y), (*c, 255))
    for (x, y) in ((6, 5), (7, 5), (6, 6)):
        img.putpixel((x, y), (255, 255, 255, 255))
    return img


def fairy_portal_frames(n=32):
    """An opal pool: broad, soft bands of pink, lilac, blue and white drifting through each other (tileable), with stars
    twinkling in and out at different spots over the loop."""
    frames = []
    rng = random.Random(12)
    tau = math.tau
    stars = [(rng.randrange(16), rng.randrange(16), rng.randrange(n), 3 + rng.randrange(4)) for _ in range(14)]
    for k in range(n):
        p = k / n * tau
        img = new(16, 16)
        for y in range(16):
            for x in range(16):
                u, v = x / 16 * tau, y / 16 * tau
                f = math.sin(u + p) + math.sin(v - p) * 0.9 + math.sin(u + v + p) * 0.6 + math.cos(u - v - 2 * p) * 0.5
                t = (f / 6.0 + 0.5 + k / n) % 1.0
                c = sample(OPAL + [OPAL[0]], t)
                img.putpixel((x, y), (*c, 255))
        for (sx, sy, born, life) in stars:
            age = (k - born) % n
            if age < life:
                glow = 1.0 - abs(age - life / 2) / (life / 2)
                base = img.getpixel((sx, sy))
                img.putpixel((sx, sy), (*mix(base[:3], WHITE, 0.4 + 0.6 * glow), 255))
                if glow > 0.6:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        qx, qy = (sx + dx) % 16, (sy + dy) % 16
                        b2 = img.getpixel((qx, qy))
                        img.putpixel((qx, qy), (*mix(b2[:3], WHITE, 0.45 * glow), 255))
        frames.append(img)
    strip = new(16, 16 * n)
    for i, f in enumerate(frames):
        strip.paste(f, (0, 16 * i))
    return strip


def fairy_jar_glass():
    """Clear trans glass: barely tinted, with a white glint and a soft pink/blue edge."""
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            c = mix(PINK, BLUE, x / 15)
            img.putpixel((x, y), (*mix(c, WHITE, 0.55), 150 if edge else 38))
    for (x, y) in ((2, 2), (3, 2), (2, 3), (2, 4), (12, 11), (12, 12), (11, 12)):
        img.putpixel((x, y), (255, 255, 255, 200))
    return img


def fairy_jar_lid():
    """A cloth cap tied over the jar: trans stripes with a white rim."""
    img = new(16, 16)
    bands = stripes5(16)
    for y in range(16):
        for x in range(16):
            c = FLAG[bands[y]]
            c = shade(c, 0.92 + 0.08 * ((x + y) % 2))
            img.putpixel((x, y), (*c, 255))
    return img


def fairy_light(colour):
    """32x32 for the fairy light (FairyLightModel: wild fairies and the Fairy Jar): the glowing cube (0,0), the side
    wings (0,8; 5x5, both faces; shaped the same at root and tip, so either way round looks right) and the halo shell
    (0,20; mostly see-through). Wings are see-through with a bright rim, like a fairy's."""
    img = new(32, 32)
    core = hexc(colour)
    rim = mix(core, WHITE, 0.25)
    glow = mix(core, WHITE, 0.75)

    def cube_faces(v, paint):
        for name, (u0, v0) in {"top": (4, v), "bottom": (8, v), "west": (0, v + 4), "north": (4, v + 4), "east": (8, v + 4),
                               "south": (12, v + 4)}.items():
            for y in range(4):
                for x in range(4):
                    img.putpixel((u0 + x, v0 + y), paint(x, y))

    def body(x, y):
        inner = 1 <= x <= 2 and 1 <= y <= 2
        if (x, y) == (1, 1):
            return (255, 255, 255, 255)
        return (*(glow if inner else rim), 255)
    cube_faces(0, body)

    def wing(u, v, shape):
        cells = {(x, y) for y, row in enumerate(shape) for x, ch in enumerate(row) if ch == "X"}
        w = len(shape[0])
        for face_u in (u, u + w):
            for (x, y) in cells:
                edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                c = (255, 255, 255, 235) if edge else (*mix(core, WHITE, 0.45 + 0.1 * ((x + y) % 2)), 150)
                img.putpixel((face_u + x, v + y), c)
    wing(0, 8, [".XXX.", "XXXXX", "XXXXX", ".XXX.", "..X.."])

    def halo(x, y):
        edge = x in (0, 3) or y in (0, 3)
        return (*mix(core, WHITE, 0.3), 60 if edge else 35)
    cube_faces(20, halo)
    return img


def fairy_crystal_spike():
    """32x32 for the ice crystal shards: deep blue at the base to white tips, a pink glow inside, bright facet edges."""
    img = new(32, 32)
    ICE = [hexc(c) for c in ("2C6FB0", "5BB6EC", "A6E2FC", "E6F8FF", "FFFFFF")]

    def shard(u, v, w, h, d):
        for face, (fu, fv, fw, fh) in {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "a": (u, v + d, d, h),
                                       "b": (u + d, v + d, w, h), "c": (u + d + w, v + d, d, h), "e": (u + 2 * d + w, v + d, w, h)}.items():
            for y in range(fh):
                for x in range(fw):
                    if face in ("top", "bottom"):
                        c, a = sample(ICE, 0.9), 230
                    else:
                        t = 1.0 - y / max(1, fh - 1)          # 1 at the top
                        c = sample(ICE, 0.15 + 0.85 * t)
                        if fw > 1 and x == fw // 2:
                            c = mix(c, PINK, 0.35)           # the pink glow down the middle
                        a = 185 + int(50 * (x == 0 or x == fw - 1))
                        if x == 0:
                            c = mix(c, WHITE, 0.5)           # a bright facet edge
                    img.putpixel((fu + x, fv + y), (*c, a))

    shard(0, 0, 3, 20, 3)       # centre column
    shard(12, 0, 2, 3, 2)       # centre tip
    shard(12, 5, 1, 2, 1)       # centre point
    shard(20, 0, 2, 13, 2)      # side shards
    shard(28, 0, 1, 2, 1)       # side tips
    return img


def trans_fairy_spawn_egg():
    """The allay's egg (already a fairy) in trans colours."""
    img = vitem("allay_spawn_egg")
    out = img.copy()
    lo, hi = lum_range(img)
    for (x, y) in pixels(img):
        px = img.getpixel((x, y))
        if not px[3]:
            continue
        t = (lum(px) - lo) / (hi - lo)
        ramp = [hexc("C2668B"), hexc("F5A9B8"), hexc("FBD0DA"), hexc("FFFFFF")] if y < 8 or (x + y) % 5 == 0 else \
            [hexc("2C6FB0"), hexc("5BCEFA"), hexc("A6E6FC"), hexc("FFFFFF")]
        out.putpixel((x, y), (*sample(ramp, 0.1 + 0.9 * t), px[3]))
    return out


def fairy_jar_light():
    """The Fairy Jar's light, part of the jar's own model: three 16x16 frames in which the core (0,0, 6x6) glows pink,
    then white, then blue (FairyJarBlock.COLOURS, COLOUR_TICKS apart, the game blending between them), brightest in the
    middle, with a see-through rounded wing (8,0, 6x6) tinted to match."""
    strip = new(16, 48)
    for i, colour in enumerate((PINK, (244, 240, 255), BLUE)):
        for y in range(6):
            for x in range(6):
                glow = max(0.0, 0.8 - 0.28 * math.hypot(x - 2.5, y - 2.5))
                strip.putpixel((x, 16 * i + y), (*mix(colour, WHITE, glow), 255))
                if (x - 2.5) ** 2 + (y - 2.5) ** 2 <= 9.5:
                    strip.putpixel((8 + x, 16 * i + y), (*mix(WHITE, colour, 0.25 + 0.2 * x / 5), 210))
    return strip


def fairy_realm_textures():
    """(path under textures/, image, mcmeta or None) for everything above."""
    yield "entity/trans_fairy/trans_fairy.png", trans_fairy(), None
    yield "entity/trans_fairy/trans_fairy_glow.png", trans_fairy_glow(), None
    yield "entity/trans_fairy/fairy_crystal_spike.png", fairy_crystal_spike(), None
    for name, colour in (("pink", "F5A9B8"), ("white", "FFFFFF"), ("blue", "5BCEFA")):
        yield f"entity/fairy_light/{name}.png", fairy_light(colour), None
    yield "block/fairy_portal_frame_top.png", fairy_frame_top(), None
    yield "block/fairy_portal_frame_side.png", fairy_frame_side(), None
    yield "block/fairy_portal_frame_pearl.png", fairy_frame_pearl(), None
    yield "block/fairy_portal.png", fairy_portal_frames(), {"animation": {"frametime": 2, "interpolate": True}}
    yield "block/fairy_jar_glass.png", fairy_jar_glass(), None
    yield "block/fairy_jar_lid.png", fairy_jar_lid(), None
    yield "block/fairy_jar_light.png", fairy_jar_light(), {"animation": {"frametime": 30, "interpolate": True}}
    yield "item/trans_fairy_spawn_egg.png", trans_fairy_spawn_egg(), None


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
        "trans_deepslate": trans_deepslate(strata=True), "trans_deepslate_top": trans_deepslate("deepslate_top"),
        "cobbled_trans_deepslate": trans_deepslate("cobbled_deepslate", strata=True),
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
        "trans_crystal_block": trans_crystal_block(), "pastel_prism": pastel_prism(),
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
        # lush caves and forests
        "trans_moss_block": trans_moss(), **{name: globals()[name]() for name in LEAVES},
        **trans_bed_textures(),
        # glass, wool, light
        "trans_stained_glass": trans_stained_glass(), "trans_stained_glass_pane_top": trans_stained_glass_pane_top(),
        "trans_pink_stained_glass": trans_pink_stained_glass(),
        "trans_pink_stained_glass_pane_top": tinted_glass(vblock("white_stained_glass_pane_top"), PINK, 1.0),
        "trans_blue_stained_glass": trans_blue_stained_glass(),
        "trans_blue_stained_glass_pane_top": tinted_glass(vblock("white_stained_glass_pane_top"), BLUE, 1.0),
        "trans_wool": trans_wool(), "trans_wool_top": trans_wool_top(), "trans_lantern": trans_lantern(),
        # bakery
        "trans_cake_top": trans_cake_top(), "trans_cake_side": trans_cake_side(), "trans_cake_inner": trans_cake_inner(),
        "trans_cake_bottom": trans_cake_bottom(), "pride_oven_front": pride_oven_front(), "pride_oven_side": pride_oven_side(),
        "pride_oven_top": pride_oven_top(), "pride_oven_bottom": pride_oven_bottom(),
        "trans_clay": trans_clay(), "trans_sea_pickle": trans_sea_pickle(),
        **trans_cave_vines_textures(), **trans_prismarine_textures(), "pink_obsidian": pink_obsidian(),
    }
    blocks.update({name: cat_plush(name) for name in PLUSHES})
    blocks.update(furniture_textures())
    blocks.update({f"trans_cactus_{face}": trans_cactus(face) for face in ("side", "top", "bottom")})
    blocks.update({"dry_sugar_bush": dry_sugar_bush(), "trans_sugar_cane": trans_sugar_cane()})
    for name, img in blocks.items():
        save(img, f"block/{name}.png")
    for face in ("side", "top"):
        save_mcmeta(f"block/trans_cactus_{face}.png", CACTUS_META)
    save(trans_sugar_cane_item(), "item/trans_sugar_cane.png")
    for prefix in WOOD_FAMILIES:
        family_blocks, family_items = wood_family_textures(prefix)
        for name, img in family_blocks.items():
            save(img, f"block/{name}.png")
        save_mcmeta(f"block/{prefix}_sapling.png", CUTOUT)
        for name, img in family_items.items():
            save(img, f"item/{name}.png")
    for name, img in flower_textures().items():
        save(img, f"block/{name}.png")
    for name in FLOWER_CUTOUT:
        save_mcmeta(f"block/{name}.png", CUTOUT)
    for name in ("flowering_blush_leaves", "blossom_hedge", "bluebell_hedge", "pearl_hedge"):
        save_mcmeta(f"block/{name}.png", LEAVES_META)
    for name, img, meta in vegetation_textures():
        save(img, f"block/{name}.png")
        if meta:
            save_mcmeta(f"block/{name}.png", meta)
    for name in ("trans_crystal_cluster", "trans_sapling", "pride_blossom", *FLOWERS, "pride_peony_top", "pride_peony_bottom",
                 "trans_petals", "trans_petals_stem"):
        save_mcmeta(f"block/{name}.png", CUTOUT)
    for name in ("trans_leaves", *LEAVES):
        save_mcmeta(f"block/{name}.png", LEAVES_META)
    for name in ("trans_stained_glass", "trans_pink_stained_glass", "trans_blue_stained_glass", "trans_glass"):
        save_mcmeta(f"block/{name}.png", GLASS_META)
    save_mcmeta("block/trans_lantern.png", {"animation": {"frametime": 8}})
    save_mcmeta("block/pride_oven_front.png", {"animation": {"interpolate": False, "frametime": 4}})
    for vanilla, ours in (("lava_still", "pink_lava_still"), ("lava_flow", "pink_lava_flow")):
        img, meta = pink_lava(vanilla)
        save(img, f"block/{ours}.png")
        save_mcmeta(f"block/{ours}.png", meta)
    for name, (img, meta) in (("trans_prismarine", trans_prismarine()), ("trans_sea_lantern", trans_sea_lantern())):
        save(img, f"block/{name}.png")
        save_mcmeta(f"block/{name}.png", meta)
    for frame in ("0", "1"):
        img, meta = pink_fire(f"fire_{frame}")
        save(img, f"block/pink_fire_{frame}.png")
        save_mcmeta(f"block/pink_fire_{frame}.png", meta)

    items = {
        "trans_crystal": trans_crystal_item(), "prism_shard": prism_shard_item(), "pink_lava_bucket": pink_lava_bucket(),
        "crystal_alloy": crystal_alloy(), "crystal_upgrade_smithing_template": crystal_upgrade_smithing_template(),
        "trans_sea_pickle": trans_sea_pickle(item=True), "trans_glow_berries": trans_glow_berries(),
        "trans_sword": trans_tool("sword"), "trans_pickaxe": trans_tool("pickaxe"), "trans_axe": trans_tool("axe"),
        "trans_shovel": trans_tool("shovel"), "trans_hoe": trans_tool("hoe"),
        "trans_helmet": trans_armor_item("helmet"), "trans_chestplate": trans_armor_item("chestplate"),
        "trans_leggings": trans_armor_item("leggings"), "trans_boots": trans_armor_item("boots"),
        "trans_donut": trans_donut(), "trans_cookie": trans_cookie(), "trans_cupcake": trans_cupcake(),
        "trans_macaron": trans_macaron(), "trans_boba": trans_boba(), "trans_cake": trans_cake_item(),
        "silly_cat_spawn_egg": silly_cat_spawn_egg(), "trans_door": trans_door_item(), "trans_lantern": trans_lantern_item(),
        "trans_petals": trans_petals_item(),
        "trans_boat": trans_boat_item("birch_boat"), "trans_chest_boat": trans_boat_item("birch_chest_boat"),
        "trans_wand": trans_wand(), "trans_wings": trans_wings_item(), "trans_magic_bolt": trans_magic_bolt(),
        "maddie_spawn_egg": maddie_spawn_egg(),
    }
    items.update(vegetation_items())
    for name, img in items.items():
        save(img, f"item/{name}.png")

    save(trans_armor_layer("entity/equipment/humanoid/diamond.png"), "entity/equipment/humanoid/trans_crystal.png")
    save(trans_armor_layer("entity/equipment/humanoid_leggings/diamond.png"), "entity/equipment/humanoid_leggings/trans_crystal.png")


    baker = trans_baker()
    for kind in ("villager", "zombie_villager"):
        save(baker, f"entity/{kind}/profession/trans_baker.png")
        save_mcmeta(f"entity/{kind}/profession/trans_baker.png", {"villager": {"hat": "full"}})

    save(silly_cat(), "entity/silly_cat/silly_cat.png")
    for rel, img in creature_textures():
        save(img, rel)
    for rel, img, meta in fairy_realm_textures():
        save(img, rel)
        if meta:
            save_mcmeta(rel, meta)
    save(maddie_skin(), "entity/maddie/maddie.png")
    save(trans_wings_model_texture(), "entity/trans_wings.png")
    for kind in ("boat", "chest_boat"):
        with _zip(ENTITY_ZIP).open(f"base entity textures/{kind}/birch.png") as f:
            save(trans_boat(Image.open(f).convert("RGBA")), f"entity/{kind}/trans.png")
    save(trans_sheep_wool("entity/sheep/sheep_wool.png"), "entity/sheep/trans_sheep_wool.png")
    save(trans_sheep_wool("entity/sheep/sheep_wool_undercoat.png"), "entity/sheep/trans_sheep_wool_undercoat.png")
    save(trans_sheep_wool("entity/sheep/sheep_wool_baby.png", generic=True), "entity/sheep/trans_sheep_wool_baby.png")

    trans_mobs = os.path.join(OUT, "textures", "entity", "trans")
    if os.path.isdir(trans_mobs):
        import shutil
        shutil.rmtree(trans_mobs)
    for rel, img in trans_mob_textures():
        save(img, f"entity/trans/{rel}")

    save(slobbered_icon(), "mob_effect/slobbered.png")
    for i, frame in enumerate(saliva_frames()):
        save(frame, f"gui/saliva/saliva_{i}.png")
    save(heart_clouds(), "environment/heart_clouds.png")
    # The advancement tab's background: trans planks, dimmed so the icons stand out.
    save(advancement_background(), "gui/advancements/backgrounds/trans.png")
    for name, (pw, ph, _, _, make) in PAINTINGS.items():
        img = make()
        assert img.size == (16 * pw, 16 * ph), name
        save(img, f"painting/{name}.png")

    icon_path = os.path.join(OUT, "icon.png")
    icon().save(icon_path)
    print("Textures written to", os.path.normpath(os.path.join(OUT, "textures")))


if __name__ == "__main__":
    main()
