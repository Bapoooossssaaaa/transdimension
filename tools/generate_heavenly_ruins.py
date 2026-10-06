#!/usr/bin/env python3
"""
Builds the Cloud Realm's heavenly ruins and their holy loot.

The ruins are templates that HeavenlyRuinFeature sets down on the realm's floating islands (only where an island is
roughly level under the whole ruin, centred on its chunk and turned a random way). They're white cloudcite trimmed in
holy gold and glowing gilded cloudcite, each with holy water and a white-and-gold cloud chest of holy loot:

    spiral_tower   a round white tower ringed with gold, with a stair winding up round the outside to a lookout at the
                   top under a little golden dome (a cloud chest beneath it), and a pool of holy water inside at its foot
    temple         a small temple of white columns with golden capitals on a stepped platform, part of its roof fallen,
                   round a long pool of holy water, with an altar and a cloud chest at the far end
    sky_shrine     a ring of eight white columns under a stepped golden dome, round a fountain of holy water

Each template's bottom layer is its floor: it takes the place of the top of the ground (HeavenlyRuinFeature.FOUNDATION
must agree), and the feature shores it up with cloudcite wherever the ground dips under it. Inside each ruin the air is
set too, so a hummock of the island can't fill it.

The giant beanstalk (BeanstalkFeature) has a cloud chest of the same loot at its top.

Outputs (under src/main/resources/data/transdimension):
    structure/heavenly_ruin/{spiral_tower,temple,sky_shrine}.nbt
    loot_table/chests/heavenly_ruin.json

    python3 tools/generate_heavenly_ruins.py      (needs: pip install nbtlib)
"""
import math
import os

from nbtlib import Compound, String

from generate_fairy_realm import DATA, NS, Template, write

T = NS + ":"
LOOT = T + "chests/heavenly_ruin"
FOUNDATION = 1          # template layer 0 is the floor, level with the top of the ground

STAIRS = {"half": "bottom", "shape": "straight", "waterlogged": "false"}
BOTTOM_SLAB = {"type": "bottom", "waterlogged": "false"}
HOLY_WATER = ("holy_water", {"level": "0"})
WALL = {"up": "true", "north": "low", "east": "low", "south": "low", "west": "low", "waterlogged": "false"}


def cloud_chest(facing):
    """A cloud chest (a barrel underneath) full of holy loot, named like one a player places."""
    return (T + "cloud_chest", {"facing": facing, "open": "false"},
            Compound({"id": String("minecraft:barrel"), "LootTable": String(LOOT),
                      "CustomName": Compound({"translate": String("block.transdimension.cloud_chest")})}))


def ring_angle(dx, dz):
    return math.atan2(dz, dx) % math.tau


def angle_gap(a, b):
    d = abs(a - b) % math.tau
    return min(d, math.tau - d)


def tangent_facing(angle):
    """The way you walk up a stair winding anticlockwise (seen from above) at this angle round the middle."""
    dx, dz = -math.sin(angle), math.cos(angle)
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def clear_inside(t, cells, y_from, y_to):
    """Air in every free cell of `cells` (x, z) from y_from to y_to (what the ruin hasn't put there itself)."""
    for (x, z) in cells:
        for y in range(y_from, y_to + 1):
            if t.get((x, y, z)) is None:
                t.clear((x, y, z))


def disc(w, d, cx, cz, radius):
    return [(x, z) for x in range(w) for z in range(d) if math.hypot(x - cx, z - cz) <= radius]


# ============================================================================================ the spiral tower
def spiral_tower():
    w = d = 13
    h = 34
    cx = cz = 6
    t = Template((w, h, d))
    top = 22                         # the lookout's floor
    door = math.pi / 2               # the door faces south (+z)

    # The floor: a white plaza ringed with gold, the tower's foot paved inside.
    for (x, z) in disc(w, d, cx, cz, 6.2):
        r = math.hypot(x - cx, z - cz)
        t.put((x, 0, z), "gilded_cloudcite" if 5.3 < r <= 6.2 else "polished_cloudcite")

    # The tower: a ring of white bricks, a chiseled foot, golden bands, arched windows and a door.
    windows = [(6, 0.0), (6, math.pi), (11, math.pi / 2 + 0.8), (11, 3 * math.pi / 2 + 0.8), (16, 0.6), (16, math.pi + 0.6)]
    for (x, z) in disc(w, d, cx, cz, 3.6):
        r = math.hypot(x - cx, z - cz)
        if r <= 2.6:
            continue
        angle = ring_angle(x - cx, z - cz)
        for y in range(1, top):
            if y <= 3 and angle_gap(angle, door) < 0.3:
                t.clear((x, y, z))
            elif any(wy <= y <= wy + 1 and angle_gap(angle, wa) < 0.25 for wy, wa in windows):
                t.clear((x, y, z))
            elif y == 1:
                t.put((x, y, z), "chiseled_cloudcite")
            elif y in (7, 14) or (y == 4 and angle_gap(angle, door) < 0.45):
                t.put((x, y, z), "gilded_cloudcite")
            else:
                t.put((x, y, z), "cloudcite_bricks")
    # Inside, at its foot: a round pool of holy water in a rim of polished cloudcite, gold under the water.
    inside = disc(w, d, cx, cz, 2.6)
    for (x, z) in inside:
        r = math.hypot(x - cx, z - cz)
        if r <= 1.5:
            t.put((x, 0, z), "gilded_cloudcite")
            t.put((x, 1, z), *HOLY_WATER)
        else:
            t.put((x, 1, z), "polished_cloudcite")
    t.put((cx, 2, cz - 2), *cloud_chest("south"))
    clear_inside(t, inside, 2, top - 1)

    # The stair, winding up round the outside, one step a block, one turn every twenty steps, to the top of the tower.
    steps = top - 1
    for i in range(steps):
        y = 1 + i
        a = (door + 0.7 + i * math.tau / 20.0) % math.tau
        for (x, z) in disc(w, d, cx, cz, 5.0):
            r = math.hypot(x - cx, z - cz)
            if r > 3.6 and angle_gap(ring_angle(x - cx, z - cz), a) < 0.2:
                t.put((x, y, z), "cloudcite_brick_stairs", {"facing": tangent_facing(a), **STAIRS})
                for above in (y + 1, y + 2):
                    if t.get((x, above, z)) is None:
                        t.clear((x, above, z))

    # The lookout on top of the tower: a white floor ringed with gold and a railing (open where the stair comes up), and
    # on it four golden-capped columns under a little stepped golden dome with a spire, sheltering a cloud chest.
    arrive = (door + 0.7 + (steps - 1) * math.tau / 20.0) % math.tau
    for (x, z) in disc(w, d, cx, cz, 3.6):
        r = math.hypot(x - cx, z - cz)
        t.put((x, top, z), "gilded_cloudcite" if 2.8 < r else "polished_cloudcite")
        if 2.8 < r and angle_gap(ring_angle(x - cx, z - cz), arrive) > 0.8:
            t.put((x, top + 1, z), "cloudcite_brick_wall", WALL)
    for (px, pz) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        t.put((cx + px, top + 1, cz + pz), "chiseled_cloudcite")
        t.put((cx + px, top + 2, cz + pz), "cloudcite")
        t.put((cx + px, top + 3, cz + pz), "gilded_cloudcite")
    for y, radius in ((top + 4, 2.9), (top + 5, 1.9), (top + 6, 1.0)):
        for (x, z) in disc(w, d, cx, cz, radius):
            t.put((x, y, z), "holy_gold_block")
    t.put((cx, top + 7, cz), "gilded_cloudcite")
    t.put((cx, top + 8, cz), "gilded_cloudcite")
    t.put((cx, top + 9, cz), "holy_gold_block")
    t.put((cx, top + 1, cz), *cloud_chest("south"))
    clear_inside(t, disc(w, d, cx, cz, 3.6), top + 1, top + 3)
    return t


# ============================================================================================ the temple
def temple():
    w, d = 11, 15
    h = 11
    t = Template((w, h, d))
    # The floor, and a platform a step up with steps all round it.
    for x in range(w):
        for z in range(d):
            edge = x in (0, w - 1) or z in (0, d - 1)
            t.put((x, 0, z), "cloudcite_bricks" if edge else "polished_cloudcite")
    for x in range(1, w - 1):
        for z in range(1, d - 1):
            if x in (1, w - 2) or z in (1, d - 2):
                if x in (1, w - 2) and z in (1, d - 2):
                    t.put((x, 1, z), "cloudcite_brick_slab", BOTTOM_SLAB)
                else:
                    facing = "north" if z == d - 2 else "south" if z == 1 else "east" if x == 1 else "west"
                    t.put((x, 1, z), "cloudcite_brick_stairs", {"facing": facing, **STAIRS})
            else:
                t.put((x, 1, z), "polished_cloudcite")
    # A long pool of holy water down the middle, golden underneath.
    for x in range(4, 7):
        for z in range(5, 10):
            t.put((x, 0, z), "gilded_cloudcite")
            t.put((x, 1, z), *HOLY_WATER)
    # Two rows of white columns with golden capitals; the one at the back corner has fallen.
    columns = [(2, z) for z in (2, 5, 9, 12)] + [(8, z) for z in (2, 5, 9, 12)]
    fallen = (8, 12)
    for (x, z) in columns:
        height = 2 if (x, z) == fallen else 6
        for i in range(height):
            y = 2 + i
            t.put((x, y, z), "chiseled_cloudcite" if i == 0 else ("gilded_cloudcite" if i == 5 else "cloudcite"))
    # The architrave and the roof, open over the fallen corner.
    broken = lambda x, z: x >= 6 and z >= 9
    for x in range(2, 9):
        for z in range(2, 13):
            if broken(x, z):
                continue
            if x in (2, 8) or z in (2, 12):
                t.put((x, 8, z), "gilded_cloudcite" if (x in (2, 8) and z in (2, 12)) else "cloudcite_bricks")
            t.put((x, 9, z), "cloudcite_brick_slab", BOTTOM_SLAB)
    # The fallen column's drums lie where it came down, under the gap in the roof.
    for (x, z) in ((7, 10), (8, 10), (7, 11)):
        t.put((x, 2, z), "cloudcite")
    # The altar at the far end: gold either side of a cloud chest.
    t.put((4, 2, 3), "holy_gold_block")
    t.put((6, 2, 3), "holy_gold_block")
    t.put((5, 2, 3), *cloud_chest("south"))
    clear_inside(t, [(x, z) for x in range(1, w - 1) for z in range(1, d - 1)], 2, h - 1)
    return t


# ============================================================================================ the sky shrine
def sky_shrine():
    w = d = 11
    h = 13
    cx = cz = 5
    t = Template((w, h, d))
    # The floor: white, ringed with bricks, a golden ring set into it round the fountain.
    for (x, z) in disc(w, d, cx, cz, 5.4):
        r = math.hypot(x - cx, z - cz)
        t.put((x, 0, z), "cloudcite_bricks" if r > 4.6 else ("gilded_cloudcite" if 2.4 < r <= 3.0 else "polished_cloudcite"))
    # The fountain: a rim of polished cloudcite round a ring of holy water, and a golden heart rising from its middle.
    for (x, z) in disc(w, d, cx, cz, 2.3):
        r = math.hypot(x - cx, z - cz)
        if 0.5 < r <= 1.5:
            t.put((x, 0, z), "gilded_cloudcite")
            t.put((x, 1, z), *HOLY_WATER)
        elif r > 1.5:
            t.put((x, 1, z), "polished_cloudcite")
    t.put((cx, 1, cz), "chiseled_cloudcite")
    t.put((cx, 2, cz), "holy_gold_block")
    t.put((cx, 3, cz), "gilded_cloudcite")
    # Eight white columns with golden capitals, a ring of bricks on them, and a stepped golden dome with a spire.
    for k in range(8):
        a = k * math.tau / 8
        x, z = round(cx + 4 * math.cos(a)), round(cz + 4 * math.sin(a))
        for i, block in enumerate(("chiseled_cloudcite", "cloudcite", "cloudcite", "cloudcite", "gilded_cloudcite")):
            t.put((x, 1 + i, z), block)
    for (x, z) in disc(w, d, cx, cz, 4.8):
        if math.hypot(x - cx, z - cz) > 3.2:
            t.put((x, 6, z), "cloudcite_bricks")
    for y, radius in ((7, 4.4), (8, 3.4), (9, 2.4), (10, 1.2)):
        for (x, z) in disc(w, d, cx, cz, radius):
            t.put((x, y, z), "holy_gold_block")
    t.put((cx, 11, cz), "gilded_cloudcite")
    t.put((cx, 12, cz), "holy_gold_block")
    t.put((cx, 1, cz + 3), *cloud_chest("north"))
    clear_inside(t, disc(w, d, cx, cz, 4.6), 1, 6)
    return t


# ============================================================================================ holy loot
def item(name, weight, lo=1, hi=1, functions=()):
    e = {"type": "minecraft:item", "name": name if ":" in name else T + name, "weight": weight}
    fs = list(functions)
    if hi > 1:
        fs.insert(0, {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if fs:
        e["functions"] = fs
    return e


def loot_table():
    """Holy loot: three chests in four hold a piece of holy gold gear (now and then enchanted, as if from the table),
    with cloud candy, holy water, golden apples, gold and the realm's flowers."""
    enchanted = {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": 15, "max": 30},
                 "options": "#minecraft:in_enchanting_table",
                 "conditions": [{"condition": "minecraft:random_chance", "chance": 0.4}]}
    gear = [item(f"holy_golden_{piece}", weight, functions=[enchanted]) for piece, weight in (
        ("sword", 10), ("pickaxe", 10), ("axe", 8), ("shovel", 8), ("hoe", 6), ("helmet", 8), ("chestplate", 6),
        ("leggings", 6), ("boots", 8))]
    return {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": 1.0, "entries": gear + [{"type": "minecraft:empty", "weight": 23}]},
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
                item("cloud_candy", 20, 1, 4), item("holy_water_bucket", 6), item("minecraft:golden_apple", 8),
                item("minecraft:enchanted_golden_apple", 1), item("minecraft:gold_ingot", 15, 2, 6),
                item("minecraft:gold_nugget", 10, 4, 12), item("holy_gold_block", 3), item("minecraft:glowstone_dust", 10, 2, 6),
                item("minecraft:feather", 10, 2, 5), item("minecraft:white_wool", 8, 2, 6), item("minecraft:diamond", 4, 1, 2),
                item("minecraft:experience_bottle", 6, 1, 3), item("cloud_puff", 5, 1, 3), item("halo_lily", 5, 1, 3),
                item("breezebell", 5, 1, 3), item("stardust_daisy", 5, 1, 3)]},
        ],
        "random_sequence": LOOT,
    }


def main():
    folder = os.path.join(DATA, NS, "structure", "heavenly_ruin")
    if os.path.isdir(folder):
        for f in os.listdir(folder):
            os.remove(os.path.join(folder, f))
    counts = []
    for name, build in (("spiral_tower", spiral_tower), ("temple", temple), ("sky_shrine", sky_shrine)):
        counts.append(f"{name} {build().save('heavenly_ruin/' + name)}")
    write(os.path.join(DATA, NS, "loot_table", "chests", "heavenly_ruin.json"), loot_table())
    print("Heavenly ruins written:", ", ".join(counts), "blocks.")


if __name__ == "__main__":
    main()
