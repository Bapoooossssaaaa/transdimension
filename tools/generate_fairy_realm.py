#!/usr/bin/env python3
"""
Builds the endgame's two structures and their data.

The Fairy Sanctum hides under the Trans Realm. On the surface it shows as a moonlit shrine: a ring of pale pillars
crowned with prism crystals around a glowing column. A spiral staircase goes down past the column into a domed hall,
where a dais holds a ring of twelve Fairy Portal frames (like an end portal), between crystal-topped pillars, star
bloom planters and two chests. A few frames already hold a Trans Crystal Pearl (the processor list picks them at
random). Set pearls into all twelve to open the portal.

The arena island is the heart of the Fairy Realm, placed by FairyRealm.java the first time anyone arrives (its numbers
are in ARENA_* below and must match the constants there). A big floating island of trans grass, rock and crystal: in the
middle a round arena paved in the flag's colours (white, then pink, then blue, then pale stone) with eight pillars and
the Fairy Altar at its centre; around it trees of every trans wood, flowers, a pond spilling into a waterfall, little
islets floating alongside, a path to the arrival point on the south side and, to the north, the ring of frames that
becomes the portal home once the Trans Fairy is beaten.

Outputs (under src/main/resources/data/transdimension):
    structure/fairy_sanctum.nbt
    structure/fairy_realm/arena_island.nbt
    worldgen/structure/fairy_sanctum.json, worldgen/template_pool/fairy_sanctum.json,
    worldgen/structure_set/fairy_sanctums.json, worldgen/processor_list/fairy_sanctum.json
    tags/worldgen/biome/has_structure/fairy_sanctum.json, tags/worldgen/structure/fairy_sanctums.json
    loot_table/chests/fairy_sanctum.json

    python3 tools/generate_fairy_realm.py      (needs: pip install nbtlib)
"""
import gzip
import io
import json
import math
import os
import random

import nbtlib
from nbtlib import Compound, Int, List, String

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "transdimension"
T = NS + ":"
DATA_VERSION = 4903         # Minecraft 26.2

# The arena island (FairyRealm.java must agree): template corner, size, and where things are, in world coordinates.
ARENA_ORIGIN = (-36, 84, -36)
ARENA_SIZE = (73, 56, 73)
ARENA_FLOOR_Y = 120         # the arena paving (and the island's grass) is at this height
ALTAR = (0, 121, 0)
RETURN_PORTAL = (0, 121, -25)
ARRIVAL = (0, 121, 30)


# ============================================================================================ templates
class Template:
    def __init__(self, size):
        self.size = size
        self.blocks = {}

    def put(self, pos, name, props=None, nbt=None):
        x, y, z = pos
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            self.blocks[(x, y, z)] = (name if ":" in name else T + name, dict(props or {}), nbt)

    def get(self, pos):
        b = self.blocks.get(pos)
        return b[0] if b else None

    def clear(self, pos):
        self.put(pos, "minecraft:air")

    def save(self, rel):
        palette, index, entries = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, props, nbt = self.blocks[pos]
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = Compound({"Name": String(name)})
                if props:
                    entry["Properties"] = Compound({k: String(str(v)) for k, v in sorted(props.items())})
                palette.append(entry)
            b = Compound({"pos": List[Int]([Int(c) for c in pos]), "state": Int(index[key])})
            if nbt is not None:
                b["nbt"] = nbt
            entries.append(b)
        root = Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(c) for c in self.size]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](entries),
            "entities": List[Compound]([]),
        })
        path = os.path.join(DATA, NS, "structure", rel + ".nbt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = io.BytesIO()
        nbtlib.File(root).write(raw, "big")
        # A fixed gzip timestamp keeps reruns from changing the file.
        with open(path, "wb") as out, gzip.GzipFile(filename="", mode="wb", fileobj=out, mtime=0) as gz:
            gz.write(raw.getvalue())
        return len(entries)


def chest(loot, facing):
    return ("minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
            Compound({"LootTable": String(loot), "id": String("minecraft:chest")}))


def noise(x, z, seed, scale=6.0):
    """Smooth value noise in [-1, 1] (bilinear over a hashed lattice)."""
    def h(ix, iz):
        n = (ix * 374761393 + iz * 668265263 + seed * 2147483647) & 0xFFFFFFFF
        n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
        return ((n ^ (n >> 16)) & 0xFFFF) / 32767.5 - 1.0
    fx, fz = x / scale, z / scale
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = fx - ix, fz - iz
    tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
    a = h(ix, iz) + (h(ix + 1, iz) - h(ix, iz)) * tx
    b = h(ix, iz + 1) + (h(ix + 1, iz + 1) - h(ix, iz + 1)) * tx
    return a + (b - a) * tz


STAIR_FACING = {(0, -1): "north", (0, 1): "south", (1, 0): "east", (-1, 0): "west"}
LEAVES = {"trans": "trans_leaves", "pearl": "pearl_leaves", "sky": "sky_leaves", "blush": "blush_leaves", "twilight": "twilight_leaves"}
SMALL_FLOWERS = ["pride_blossom", "trans_tulip", "pearl_daisy", "sky_bell", "flag_lily", "lavender_puff", "trans_orchid", "heart_bloom",
                 "blush_carnation", "pearl_snowdrop", "forget_me_not", "trans_rose", "star_bloom", "fairy_bell"]
TALL_FLOWERS = ["pride_peony", "sky_delphinium", "blush_foxglove", "pearl_lupine"]


def leaves(kind):
    return LEAVES[kind], {"distance": "1", "persistent": "true", "waterlogged": "false"}


def tree(t, x, y, z, kind, rng, height=None, radius=None):
    """A round-crowned tree of one of the trans woods (persistent leaves, so it never decays)."""
    height = height or rng.randint(5, 7)
    radius = radius or rng.randint(3, 4)
    log = f"{kind}_log"
    for dy in range(height):
        t.put((x, y + dy, z), log, {"axis": "y"})
    # a couple of branches reaching into the crown
    for dx, dz in rng.sample([(1, 0), (-1, 0), (0, 1), (0, -1)], 2):
        t.put((x + dx, y + height - 2, z + dz), log, {"axis": "x" if dx else "z"})
    cy = y + height - 1
    leaf, props = leaves(kind)
    for dx in range(-radius, radius + 1):
        for dz in range(-radius, radius + 1):
            for dy in range(-2, radius):
                d = (dx * dx + dz * dz) / (radius * radius) + (dy * dy) / ((radius - 0.6) ** 2)
                if d > 1.0 + 0.15 * noise(x + dx, z + dz + dy * 7, 9, 2.0):
                    continue
                p = (x + dx, cy + dy, z + dz)
                if t.get(p) and not t.get(p).endswith("_leaves") and t.get(p) != "minecraft:air":
                    continue
                if kind == "blush" and rng.random() < 0.3:
                    t.put(p, "flowering_blush_leaves", props)
                else:
                    t.put(p, leaf, props)


def flowers_on(t, x, y, z, rng, density=0.45):
    """Grass, flowers and now and then a tall flower on the grass block at (x, y - 1, z)."""
    roll = rng.random()
    if roll > density:
        return
    if roll < density * 0.45:
        t.put((x, y, z), "trans_short_grass")
    elif roll < density * 0.85:
        t.put((x, y, z), rng.choice(SMALL_FLOWERS))
    else:
        flower = rng.choice(TALL_FLOWERS)
        t.put((x, y, z), flower, {"half": "lower"})
        t.put((x, y + 1, z), flower, {"half": "upper"})


# ============================================================================================ the Fairy Sanctum
SANCTUM_SIZE = (33, 41, 41)
SURFACE = 32                # template y that sits at the surface (the shrine's paving)
HALL_FLOOR = 4
HALL = (16, 14)             # centre of the domed hall
HALL_R = 11
SHAFT = (16, 33)            # centre of the spiral staircase (and of the shrine above it)


def sanctum():
    rng = random.Random(41)
    t = Template(SANCTUM_SIZE)
    hx, hz = HALL
    sx, sz = SHAFT
    bricks = ("trans_stone_bricks", "trans_stone_bricks", "trans_stone_bricks", "cracked_trans_stone_bricks", "trans_moss_block")

    def brick():
        return rng.choice(bricks)

    # ---- the domed hall: a cylinder of wall up to y+8, then a dome narrowing to an oculus of prism
    for x in range(hx - HALL_R - 1, hx + HALL_R + 2):
        for z in range(hz - HALL_R - 1, hz + HALL_R + 2):
            d = math.hypot(x - hx, z - hz)
            if d > HALL_R + 0.9:
                continue
            # floor and the ground beneath it
            for y in range(HALL_FLOOR - 2, HALL_FLOOR):
                t.put((x, y, z), "trans_stone")
            if d <= HALL_R - 0.1:
                ring = d
                if ring <= 6.0:
                    floor = "polished_trans_diorite" if ring <= 2.5 else "chiseled_trans_stone_bricks" if ring <= 3.5 else "polished_trans_granite"
                else:
                    floor = "polished_trans_andesite" if int(math.degrees(math.atan2(z - hz, x - hx)) // 30) % 2 == 0 else "trans_stone_bricks"
                t.put((x, HALL_FLOOR, z), floor)
            else:
                t.put((x, HALL_FLOOR, z), "trans_stone_bricks")
            # walls and dome
            top = HALL_FLOOR + 14
            for y in range(HALL_FLOOR + 1, top + 1):
                height = y - (HALL_FLOOR + 8)
                inner = HALL_R - 0.1 if height <= 0 else math.sqrt(max(0.0, (HALL_R - 0.1) ** 2 - (height * 1.75) ** 2))
                if d <= inner - 1.0:
                    t.clear((x, y, z))
                elif d <= inner + 0.9 or (height > 0 and d <= inner + 1.6):
                    t.put((x, y, z), brick())
    # oculus: a disc of glowing prism at the top of the dome
    for x in range(hx - 1, hx + 2):
        for z in range(hz - 1, hz + 2):
            t.put((x, HALL_FLOOR + 14, z), "pastel_prism")

    # ---- the portal dais: a raised round platform with steps, the twelve frames on it
    for x in range(hx - 5, hx + 6):
        for z in range(hz - 5, hz + 6):
            d = math.hypot(x - hx, z - hz)
            if d <= 4.6:
                t.put((x, HALL_FLOOR + 1, z), "chiseled_trans_stone_bricks" if d > 3.6 else "trans_stone_bricks")
            elif d <= 5.6:
                dx, dz = x - hx, z - hz
                facing = ("east" if dx < 0 else "west") if abs(dx) >= abs(dz) else ("south" if dz < 0 else "north")
                t.put((x, HALL_FLOOR + 1, z), "trans_stone_brick_stairs", {"facing": facing, "half": "bottom", "shape": "straight",
                                                                          "waterlogged": "false"})
    frame_y = HALL_FLOOR + 2
    for (dx, dz, facing) in [(-1, -2, "south"), (0, -2, "south"), (1, -2, "south"), (-1, 2, "north"), (0, 2, "north"), (1, 2, "north"),
                             (-2, -1, "east"), (-2, 0, "east"), (-2, 1, "east"), (2, -1, "west"), (2, 0, "west"), (2, 1, "west")]:
        t.put((hx + dx, frame_y, hz + dz), "fairy_portal_frame", {"facing": facing, "pearl": "false"})
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            t.clear((hx + dx, frame_y, hz + dz))

    # ---- eight crystal-topped pillars around the hall, star bloom planters between them
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        px, pz = round(hx + math.cos(a) * 8), round(hz + math.sin(a) * 8)
        for y in range(HALL_FLOOR + 1, HALL_FLOOR + 6):
            t.put((px, y, pz), "chiseled_trans_stone_bricks" if y in (HALL_FLOOR + 1, HALL_FLOOR + 5) else "trans_stone_bricks")
        t.put((px, HALL_FLOOR + 6, pz), "pastel_prism")
        t.put((px, HALL_FLOOR + 7, pz), "trans_crystal_cluster", {"facing": "up", "waterlogged": "false"})
        b = math.radians(i * 45)
        fx, fz = round(hx + math.cos(b) * 9.5), round(hz + math.sin(b) * 9.5)
        if i not in (2,):     # leave the south side (towards the corridor) open
            t.put((fx, HALL_FLOOR, fz), "trans_moss_block")
            t.put((fx, HALL_FLOOR + 1, fz), "star_bloom" if i % 2 else "fairy_bell")
    # seal the dome: whatever sits right above the hall's highest air is wall, so caves can't break through
    for x in range(hx - HALL_R - 1, hx + HALL_R + 2):
        for z in range(hz - HALL_R - 1, hz + HALL_R + 2):
            airs = [y for y in range(HALL_FLOOR + 1, HALL_FLOOR + 16) if t.get((x, y, z)) == "minecraft:air"]
            if airs and t.get((x, max(airs) + 1, z)) is None:
                t.put((x, max(airs) + 1, z), brick())
    # lanterns hanging from the dome
    for i in range(6):
        a = math.radians(i * 60)
        lx, lz = round(hx + math.cos(a) * 5), round(hz + math.sin(a) * 5)
        for yy in range(HALL_FLOOR + 7, HALL_FLOOR + 16):
            if t.get((lx, yy, lz)) not in ("minecraft:air", None):
                t.put((lx, yy - 1, lz), "trans_lantern", {"hanging": "true", "waterlogged": "false"})
                break
    # two chests in the west and east walls' alcoves
    for (cx, facing) in ((hx - HALL_R + 1, "east"), (hx + HALL_R - 1, "west")):
        for dz in (-1, 0, 1):
            for y in range(HALL_FLOOR + 1, HALL_FLOOR + 4):
                t.clear((cx, y, hz + dz))
        name, props, nbt = chest(T + "chests/fairy_sanctum", facing)
        t.put((cx, HALL_FLOOR + 1, hz), name, props, nbt)
        t.put((cx, HALL_FLOOR + 1, hz - 1), "trans_lantern", {"hanging": "false", "waterlogged": "false"})

    # ---- the corridor from the hall to the foot of the stairs
    for z in range(hz + HALL_R - 1, sz - 1):
        for x in range(sx - 2, sx + 3):
            for y in range(HALL_FLOOR, HALL_FLOOR + 6):
                edge = x in (sx - 2, sx + 2) or y in (HALL_FLOOR, HALL_FLOOR + 5)
                if edge:
                    t.put((x, y, z), brick() if y != HALL_FLOOR else "trans_stone_bricks")
                else:
                    t.clear((x, y, z))
        if z % 3 == 0:
            # glowing prism set into both walls
            t.put((sx + 2, HALL_FLOOR + 3, z), "pastel_prism")
            t.put((sx - 2, HALL_FLOOR + 3, z), "pastel_prism")

    # ---- the spiral staircase: a round shaft with a glowing central column, steps winding down around it
    ring = [(-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0)]
    bottom = HALL_FLOOR + 1      # the lowest air level in the shaft
    for y in range(HALL_FLOOR, SURFACE + 1):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                p = (sx + dx, y, sz + dz)
                if max(abs(dx), abs(dz)) == 2:
                    t.put(p, brick() if y < SURFACE else "trans_stone_bricks")
                elif (dx, dz) == (0, 0):
                    t.put(p, "pastel_prism" if y % 4 == 0 else "trans_stone_bricks")
                else:
                    t.clear(p)
    # the door from the shaft to the corridor (north side, at the bottom)
    for x in range(sx - 1, sx + 2):
        for y in range(bottom, bottom + 3):
            t.clear((x, y, sz - 2))
    # steps: step i is a stair block at y = floor + i, winding anticlockwise (seen from above) as it climbs. Step 0 is
    # the floor itself at the north cell, by the door; the last step is level with the shrine's paving.
    for i in range(1, SURFACE - HALL_FLOOR + 1):
        y = HALL_FLOOR + i
        cell = ring[(1 - i) % 8]
        above = ring[(1 - (i + 1)) % 8]
        facing = STAIR_FACING[(above[0] - cell[0], above[1] - cell[1])]
        p = (sx + cell[0], y, sz + cell[1])
        t.put(p, "trans_stone_brick_stairs", {"facing": facing, "half": "bottom", "shape": "straight", "waterlogged": "false"})
        if i > 1:
            t.put((p[0], y - 1, p[2]), "trans_stone_brick_slab", {"type": "top", "waterlogged": "false"})
    # the floor of the shaft
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if (dx, dz) != (0, 0):
                t.put((sx + dx, HALL_FLOOR, sz + dz), "polished_trans_diorite")

    # ---- the shrine on the surface: paving, eight pillars with prism crystals, the glowing column rising through it
    for x in range(sx - 7, sx + 8):
        for z in range(sz - 7, sz + 8):
            d = math.hypot(x - sx, z - sz)
            if d > 6.6:
                continue
            if max(abs(x - sx), abs(z - sz)) <= 1 and (x, z) != (sx, sz):
                continue      # the stair opening
            t.put((x, SURFACE, z), "chiseled_trans_stone_bricks" if 2.5 < d <= 3.5 else "polished_trans_diorite" if d <= 2.5
                  else "trans_stone_bricks")
            for y in range(SURFACE + 1, SURFACE + 6):
                if t.get((x, y, z)) is None:
                    t.clear((x, y, z))
    for i in range(8):
        a = math.radians(i * 45)
        px, pz = round(sx + math.cos(a) * 5.5), round(sz + math.sin(a) * 5.5)
        tall = 4 if i % 2 == 0 else 3
        for y in range(SURFACE + 1, SURFACE + 1 + tall):
            t.put((px, y, pz), "polished_trans_diorite" if y == SURFACE + 1 else "trans_stone_bricks")
        t.put((px, SURFACE + 1 + tall, pz), "pastel_prism")
        t.put((px, SURFACE + 2 + tall, pz), "trans_crystal_cluster", {"facing": "up", "waterlogged": "false"})
    for y in range(SURFACE + 1, SURFACE + 4):
        t.put((sx, y, sz), "pastel_prism" if y < SURFACE + 3 else "trans_crystal_block")
    t.put((sx, SURFACE + 4, sz), "trans_crystal_cluster", {"facing": "up", "waterlogged": "false"})
    # flowers around the shrine
    for x in range(sx - 8, sx + 9):
        for z in range(sz - 8, sz + 9):
            d = math.hypot(x - sx, z - sz)
            if 6.6 < d <= 8.2 and rng.random() < 0.5:
                t.put((x, SURFACE + 1, z), rng.choice(["star_bloom", "fairy_bell", "pearl_snowdrop", "forget_me_not", "trans_short_grass"]))
    return t


def sanctum_data():
    wg = os.path.join(DATA, NS, "worldgen")
    write(os.path.join(wg, "template_pool", "fairy_sanctum.json"), {
        "elements": [{"element": {"element_type": "minecraft:single_pool_element", "location": T + "fairy_sanctum",
                                  "processors": T + "fairy_sanctum", "projection": "rigid"}, "weight": 1}],
        "fallback": "minecraft:empty"})
    # The template's y 33 (one above the shrine's paving) lands at the first free block above the ground.
    write(os.path.join(wg, "structure", "fairy_sanctum.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/fairy_sanctum",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"absolute": -(SURFACE + 1)},
        "start_pool": T + "fairy_sanctum",
        "step": "underground_structures",
        "terrain_adaptation": "none",
        "use_expansion_hack": False,
    })
    # Rare but findable: at most one per 56x56 chunk cell (896 blocks), never closer than 24 chunks to the next.
    write(os.path.join(wg, "structure_set", "fairy_sanctums.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 71820623, "separation": 24, "spacing": 56},
        "structures": [{"structure": T + "fairy_sanctum", "weight": 1}]})
    # About one frame in eight already holds a pearl.
    rules = []
    for facing in ("north", "south", "east", "west"):
        empty = {"Name": T + "fairy_portal_frame", "Properties": {"facing": facing, "pearl": "false"}}
        full = {"Name": T + "fairy_portal_frame", "Properties": {"facing": facing, "pearl": "true"}}
        rules.append({"input_predicate": {"predicate_type": "minecraft:random_blockstate_match", "block_state": empty, "probability": 0.12},
                      "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": full})
    write(os.path.join(wg, "processor_list", "fairy_sanctum.json"), {"processors": [{"processor_type": "minecraft:rule", "rules": rules}]})
    write(os.path.join(DATA, NS, "tags", "worldgen", "biome", "has_structure", "fairy_sanctum.json"), {
        "values": [T + b for b in ("trans_meadow", "trans_forest", "heartwood_grove", "sugar_dunes", "lavender_marsh", "frosted_fields",
                                   "crystal_grove", "pastel_peaks", "pearlwood_forest", "bluebell_woods", "twilight_thicket",
                                   "candy_floss_grove", "pride_flower_fields", "moonlit_meadow", "gumdrop_glade")]})
    write(os.path.join(DATA, NS, "tags", "worldgen", "structure", "fairy_sanctums.json"), {"values": [T + "fairy_sanctum"]})

    def item(name, weight, lo=1, hi=1):
        e = {"type": "minecraft:item", "name": name, "weight": weight}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "add": False,
                               "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}}]
        return e

    write(os.path.join(DATA, NS, "loot_table", "chests", "fairy_sanctum.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3.0, "max": 6.0}, "entries": [
                item("minecraft:diamond", 8, 1, 3), item(T + "trans_crystal_pearl", 5, 1, 2), item(T + "trans_pearl", 8, 1, 4),
                item(T + "prism_shard", 10, 3, 8), item(T + "gumdrop", 6, 2, 6), item(T + "star_bloom", 4, 1, 3),
                item(T + "pastel_prism", 4, 1, 4), item("minecraft:experience_bottle", 5, 1, 4), item("minecraft:golden_apple", 2),
                {"type": "minecraft:item", "name": "minecraft:book", "weight": 4,
                 "functions": [{"function": "minecraft:enchant_randomly"}]}]},
        ],
        "random_sequence": T + "chests/fairy_sanctum"})


# ============================================================================================ the arena island
def arena():
    rng = random.Random(77)
    t = Template(ARENA_SIZE)
    ox, oy, oz = ARENA_ORIGIN
    top = ARENA_FLOOR_Y - oy                # template y of the grass / paving
    cx, cz = -ox, -oz                       # template x/z of the world origin (the arena's centre)

    def local(x, y, z):
        return x - ox, y - oy, z - oz

    # ---- the island's outline: a big lumpy disc, stretched a little to the south (the arrival path) and north
    def radius_at(angle):
        r = 29.0 + 2.5 * noise(math.cos(angle) * 20, math.sin(angle) * 20, 3, 9.0)
        r += 3.5 * max(0.0, math.sin(angle)) ** 6       # south lobe (+z)
        r += 2.0 * max(0.0, -math.sin(angle)) ** 8      # north lobe (-z)
        return r

    column_top = {}
    for x in range(ARENA_SIZE[0]):
        for z in range(ARENA_SIZE[2]):
            dx, dz = x - cx, z - cz
            d = math.hypot(dx, dz)
            r = radius_at(math.atan2(dz, dx))
            if d > r:
                continue
            f = 1.0 - d / r
            # gentle hills outside the arena, flat inside it
            hill = 0 if d < 17 else int(round(max(0.0, 1.6 * noise(x, z, 5, 7.0) + 0.6) * min(1.0, (d - 17) / 6.0)))
            surface = top + hill
            depth = 2 + int(30 * f ** 0.85 + 4 * noise(x, z, 11, 4.0) * f)
            bottom = surface - depth
            for y in range(max(0, bottom), surface + 1):
                if y == surface:
                    name = "trans_grass_block"
                    props = {"snowy": "false"}
                elif y >= surface - 3:
                    name, props = "trans_dirt", {}
                elif y <= bottom + 2:
                    name, props = ("trans_deepslate", {"axis": "y"}) if (x + z + y) % 3 else ("trans_granite", {})
                else:
                    n = noise(x * 1.3, z * 1.3 + y * 5, 21, 3.0)
                    name, props = ("trans_diorite", {}) if n > 0.55 else ("trans_granite", {}) if n < -0.55 else ("trans_stone", {})
                t.put((x, y, z), name, props)
            column_top[(x, z)] = surface
            # hanging crystals and spore blossoms under the island
            under = (x, max(0, bottom) - 1, z)
            roll = rng.random()
            if bottom > 0 and roll < 0.04:
                t.put(under, "trans_crystal_cluster", {"facing": "down", "waterlogged": "false"})
            elif bottom > 0 and roll < 0.065:
                t.put(under, "minecraft:spore_blossom")

    # ---- the arena: rings in the flag's colours, a rim of chiseled bricks, eight pillars and the altar
    for x in range(cx - 15, cx + 16):
        for z in range(cz - 15, cz + 16):
            d = math.hypot(x - cx, z - cz)
            if d > 14.5:
                continue
            if d <= 3.5:
                floor = "polished_trans_diorite"                 # white
            elif d <= 6.5:
                floor = "polished_trans_granite"                 # pink
            elif d <= 9.5:
                floor = "polished_trans_andesite"                # blue
            elif d <= 13.5:
                floor = "cracked_trans_stone_bricks" if rng.random() < 0.15 else "trans_stone_bricks"
            else:
                floor = "chiseled_trans_stone_bricks"
            if abs(d - 6.5) < 0.5 or abs(d - 3.5) < 0.5:
                floor = "chiseled_trans_stone_bricks"
            t.put((x, top, z), floor)
            for y in range(top + 1, top + 4):
                t.clear((x, y, z))
            column_top[(x, z)] = top
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        px, pz = round(cx + math.cos(a) * 12.5), round(cz + math.sin(a) * 12.5)
        broken = i in (2, 5)
        height = 4 if broken else 7
        for y in range(top + 1, top + 1 + height):
            name = "chiseled_trans_stone_bricks" if y == top + 1 or y == top + height else \
                "cracked_trans_stone_bricks" if broken and rng.random() < 0.5 else "trans_stone_bricks"
            t.put((px, y, pz), name)
        if broken:
            t.put((px + 1, top + 1, pz), "trans_stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
            t.put((px, top + 1 + height, pz), "trans_moss_carpet")
        else:
            t.put((px, top + 1 + height, pz), "pastel_prism")
            t.put((px, top + 2 + height, pz), "trans_crystal_cluster", {"facing": "up", "waterlogged": "false"})
            # a lantern hanging from a little arm on the inside of the pillar
            ax, az = round(cx + math.cos(a) * 11.5), round(cz + math.sin(a) * 11.5)
            t.put((ax, top + height, az), "trans_stone_brick_slab", {"type": "top", "waterlogged": "false"})
            t.put((ax, top + height - 1, az), "trans_lantern", {"hanging": "true", "waterlogged": "false"})
    ax, ay, az = local(*ALTAR)
    t.put((ax, ay, az), "fairy_altar")
    # star blooms in the gaps of the rim
    for i in range(16):
        a = math.radians(i * 22.5)
        fx, fz = round(cx + math.cos(a) * 15.5), round(cz + math.sin(a) * 15.5)
        if (fx, fz) in column_top and i % 4 != 0:
            t.put((fx, column_top[(fx, fz)] + 1, fz), "star_bloom")

    # ---- paths: south to the arrival point, north to the portal home
    def path(z0, z1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for x in range(cx - 1, cx + 2):
                if (x, z) not in column_top:
                    continue
                y = column_top[(x, z)]
                t.put((x, y, z), "trans_stone_bricks" if x == cx else "polished_trans_diorite")
                for yy in range(y + 1, y + 4):
                    t.clear((x, yy, z))
    _, _, rz = local(*RETURN_PORTAL)
    _, _, arrive_z = local(*ARRIVAL)
    path(cz + 15, arrive_z + 2)
    path(rz + 4, cz - 15)
    # lantern posts along the south path
    for z in range(cz + 18, arrive_z + 1, 5):
        for x in (cx - 2, cx + 2):
            if (x, z) in column_top:
                y = column_top[(x, z)]
                t.put((x, y + 1, z), "trans_stone_brick_wall", {"east": "none", "north": "none", "south": "none", "up": "true",
                                                               "waterlogged": "false", "west": "none"})
                t.put((x, y + 2, z), "trans_lantern", {"hanging": "false", "waterlogged": "false"})

    # ---- the portal home: a dais with twelve filled frames around an empty 3x3 (FairyRealm fills it on victory)
    px, py, pz = local(*RETURN_PORTAL)
    for x in range(px - 4, px + 5):
        for z in range(pz - 4, pz + 5):
            d = math.hypot(x - px, z - pz)
            if d <= 4.4:
                t.put((x, py - 1, z), "chiseled_trans_stone_bricks" if d > 3.4 else "polished_trans_diorite")
                for yy in range(py, py + 4):
                    t.clear((x, yy, z))
                column_top[(x, z)] = py - 1
    for (dx, dz, facing) in [(-1, -2, "south"), (0, -2, "south"), (1, -2, "south"), (-1, 2, "north"), (0, 2, "north"), (1, 2, "north"),
                             (-2, -1, "east"), (-2, 0, "east"), (-2, 1, "east"), (2, -1, "west"), (2, 0, "west"), (2, 1, "west")]:
        t.put((px + dx, py, pz + dz), "fairy_portal_frame", {"facing": facing, "pearl": "true"})
    for dx in (-3, 3):
        for dz in (-3, 3):
            t.put((px + dx, py, pz + dz), "pastel_prism")
            t.put((px + dx, py + 1, pz + dz), "trans_crystal_cluster", {"facing": "up", "waterlogged": "false"})

    # ---- the pond to the east, spilling over the edge in a waterfall
    pond = (cx + 22, cz + 6)
    for x in range(pond[0] - 5, pond[0] + 6):
        for z in range(pond[1] - 5, pond[1] + 6):
            if (x, z) not in column_top:
                continue
            d = math.hypot((x - pond[0]) * 1.0, (z - pond[1]) * 1.3) + 0.8 * noise(x, z, 31, 2.5)
            if d <= 4.2:
                y = top
                t.put((x, y - 2, z), "trans_dirt")
                t.put((x, y - 1, z), "minecraft:water", {"level": "0"})
                t.put((x, y, z), "minecraft:water", {"level": "0"})
                for yy in range(y + 1, y + 3):
                    t.clear((x, yy, z))
                column_top[(x, z)] = None
                if rng.random() < 0.1 and d < 3.5:
                    t.put((x, y + 1, z), "trans_lily_pad")
                elif rng.random() < 0.25:
                    t.put((x, y - 1, z), "trans_seagrass")
    # the outlet: a channel running east to the island's edge
    ex = pond[0] + 4
    while (ex, pond[1]) in column_top or t.get((ex, top, pond[1])) == "minecraft:water":
        t.put((ex, top, pond[1]), "minecraft:water", {"level": "0"})
        t.put((ex, top - 1, pond[1]), "trans_stone")
        for side in (-1, 1):
            if column_top.get((ex, pond[1] + side)) is not None:
                t.put((ex, top, pond[1] + side), "trans_stone_bricks" if rng.random() < 0.5 else "trans_moss_block")
        ex += 1
        if ex >= ARENA_SIZE[0]:
            break

    # ---- trees, bushes and flowers over the rest of the island
    kinds = ["pearl", "sky", "blush", "twilight", "trans"]
    spots = []
    for _ in range(400):
        x, z = rng.randrange(ARENA_SIZE[0]), rng.randrange(ARENA_SIZE[2])
        d = math.hypot(x - cx, z - cz)
        if d < 18 or column_top.get((x, z)) is None or abs(x - cx) <= 3 or math.hypot(x - pond[0], z - pond[1]) < 7:
            continue
        if math.hypot(x - px, z - pz) < 7:
            continue
        if any(math.hypot(x - sx2, z - sz2) < 6 for sx2, sz2 in spots):
            continue
        if d > radius_at(math.atan2(z - cz, x - cx)) - 4:
            continue
        spots.append((x, z))
        if len(spots) >= 16:
            break
    for i, (x, z) in enumerate(spots):
        tree(t, x, column_top[(x, z)] + 1, z, kinds[i % len(kinds)], rng)
    for (x, z), y in list(column_top.items()):
        if y is None or t.get((x, y + 1, z)) not in (None, "minecraft:air"):
            continue
        if t.get((x, y, z)) != T + "trans_grass_block":
            continue
        if rng.random() < 0.025:
            t.put((x, y + 1, z), rng.choice(["blossom_hedge", "bluebell_hedge", "pearl_hedge"]),
                  {"distance": "1", "persistent": "true", "waterlogged": "false"})
        else:
            flowers_on(t, x, y + 1, z, rng)

    # ---- little islets floating alongside
    for (ix, iy, iz, r, kind) in ((6, 30, 14, 4, "blush"), (64, 26, 58, 5, "sky"), (60, 44, 8, 3, "pearl"), (10, 46, 60, 3, "twilight")):
        for x in range(ix - r, ix + r + 1):
            for z in range(iz - r, iz + r + 1):
                d = math.hypot(x - ix, z - iz)
                if d > r + 0.3:
                    continue
                f = 1.0 - d / (r + 0.3)
                depth = 1 + int((r + 2) * f)
                for y in range(iy - depth, iy + 1):
                    t.put((x, y, z), "trans_grass_block" if y == iy else "trans_dirt" if y >= iy - 1 else "trans_stone",
                          {"snowy": "false"} if y == iy else {})
                if d < r - 0.5:
                    flowers_on(t, x, iy + 1, z, rng, density=0.5)
        tree(t, ix, iy + 1, iz, kind, rng, height=4, radius=2)
    return t


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main():
    n1 = sanctum().save("fairy_sanctum")
    sanctum_data()
    n2 = arena().save("fairy_realm/arena_island")
    print(f"Fairy Sanctum: {n1} blocks; arena island: {n2} blocks.")


if __name__ == "__main__":
    main()
