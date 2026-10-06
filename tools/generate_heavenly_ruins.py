#!/usr/bin/env python3
"""
Builds the Cloud Realm's heavenly ruins and their holy loot.

The ruins are templates that HeavenlyRuinFeature sets down on the realm's floating islands (only where an island is
roughly level under the whole ruin, centred on its chunk and turned a random way). Each is cloudcite trimmed in holy gold
and glowing gilded cloudcite, and each has holy water:

    spiral_tower   a round tower with two golden bands twisting up it, a broken crown, and a spiral stair rising out of
                   a ring of holy water round its central column to a landing at the top, where a chest of holy loot
                   waits beside a glowing holy gold pedestal (and another chest at the foot of the stair)
    broken_spire   four broken pillars round a pool of holy water, the spire that stood over it fallen across the floor
    sky_shrine     a ring of columns, some fallen, round a holy water fountain with a golden heart

Every template stands on a foundation FOUNDATION blocks deep (HeavenlyRuinFeature.FOUNDATION must agree), tapering
underneath like the rock of a floating island, with the floor at the top of it, level with the ground.

The giant beanstalk (BeanstalkFeature) has a chest of the same loot at its top.

Outputs (under src/main/resources/data/transdimension):
    structure/heavenly_ruin/{spiral_tower,broken_spire,sky_shrine}.nbt
    loot_table/chests/heavenly_ruin.json

    python3 tools/generate_heavenly_ruins.py      (needs: pip install nbtlib)
"""
import math
import os
import random

from generate_fairy_realm import DATA, NS, Template, chest, write

T = NS + ":"
LOOT = T + "chests/heavenly_ruin"
FOUNDATION = 4          # template layers 0..3 are foundation; the floor is layer 3, level with the ground

STAIRS = {"half": "bottom", "shape": "straight", "waterlogged": "false"}
BOTTOM_SLAB = {"type": "bottom", "waterlogged": "false"}
HOLY_WATER = ("holy_water", {"level": "0"})


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


def facing_towards(dx, dz):
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def foundation(t, cx, cz, radii, floor_at):
    """Layers 0..FOUNDATION-1: a disc of cloudcite tapering downwards (radius per layer), its top layer the floor,
    which floor_at(dx, dz, r) dresses (it returns a block name, or None for plain polished cloudcite)."""
    w, _, d = t.size
    for y, radius in enumerate(radii):
        for x in range(w):
            for z in range(d):
                dx, dz = x - cx, z - cz
                r = math.hypot(dx, dz)
                if r > radius:
                    continue
                if y < FOUNDATION - 1:
                    t.put((x, y, z), "cloudcite")
                else:
                    t.put((x, y, z), floor_at(dx, dz, r) or "polished_cloudcite")


def spiral_tower():
    w = d = 15
    h = 36
    cx = cz = 7
    t = Template((w, h, d))
    rng = random.Random(1101)
    floor_y = FOUNDATION - 1

    def floor(dx, dz, r):
        if 0.5 < r <= 2.5:
            return None              # under the holy water ring (filled below)
        if 5.3 < r <= 6.1:
            return "gilded_cloudcite"
        if r > 6.1:
            return "cloudcite_bricks"
        return None
    foundation(t, cx, cz, [4.2, 5.4, 6.4, 7.2], floor)

    # The wall: a ring a block thick, with a jagged, broken crown (one side fallen far lower).
    def crown(angle):
        top = 30 + round(2.5 * math.sin(3 * angle) + 1.5 * math.sin(5 * angle + 1.0))
        if angle_gap(angle, 2.4) < 0.5:
            top -= 7
        return top
    door = math.pi / 2              # the door faces south (+z)
    windows = [(9, 0.0), (9, math.pi), (17, math.pi / 2 + 0.6), (17, 3 * math.pi / 2 + 0.6), (25, 0.9), (25, math.pi + 0.9)]
    for x in range(w):
        for z in range(d):
            dx, dz = x - cx, z - cz
            r = math.hypot(dx, dz)
            if not 3.5 < r <= 4.6:
                continue
            angle = ring_angle(dx, dz)
            for y in range(floor_y + 1, crown(angle) + 1):
                if y <= floor_y + 3 and angle_gap(angle, door) < 0.3:
                    t.clear((x, y, z))                                   # the doorway
                    continue
                if any(wy <= y <= wy + 1 and angle_gap(angle, wa) < 0.25 for wy, wa in windows):
                    t.clear((x, y, z))                                   # arched windows
                    continue
                # two golden bands winding up the tower, one turn every sixteen blocks
                band = (y - floor_y) * math.tau / 16.0
                if angle_gap(angle, band) < 0.32:
                    block = "holy_gold_block"
                elif angle_gap(angle, band + math.pi) < 0.32:
                    block = "gilded_cloudcite"
                elif y == floor_y + 1:
                    block = "chiseled_cloudcite"                         # the base course
                elif y == floor_y + 4 and angle_gap(angle, door) < 0.45:
                    block = "gilded_cloudcite"                           # over the door
                else:
                    block = "cloudcite_bricks"
                t.put((x, y, z), block)

    # Inside: a ring of holy water round the central column, glowing gold underneath; the stair spirals up out of it.
    landing = floor_y + 24
    first_step = floor_y + 1
    for x in range(w):
        for z in range(d):
            dx, dz = x - cx, z - cz
            r = math.hypot(dx, dz)
            if r > 3.5:
                continue
            if r <= 0.5:
                for y in range(floor_y, landing + 1):
                    t.put((x, y, z), "chiseled_cloudcite")              # the central column
                t.put((x, landing + 1, z), "holy_gold_block")            # a glowing pedestal on the landing
                continue
            if r <= 2.5:
                t.put((x, floor_y - 1, z), "gilded_cloudcite")
                t.put((x, floor_y, z), *HOLY_WATER)
            for y in range(floor_y + 1, h):
                t.clear((x, y, z))
    step_angle = lambda y: (y - first_step) * math.tau / 12.0 + door + 0.6
    for y in range(first_step, landing):
        a = step_angle(y)
        for x in range(w):
            for z in range(d):
                dx, dz = x - cx, z - cz
                r = math.hypot(dx, dz)
                if 0.9 <= r <= 3.5 and angle_gap(ring_angle(dx, dz), a % math.tau) < 0.36:
                    t.put((x, y, z), "cloudcite_brick_stairs", {"facing": tangent_facing(a), **STAIRS})
    # The landing, open where the last steps come up through it.
    for x in range(w):
        for z in range(d):
            dx, dz = x - cx, z - cz
            r = math.hypot(dx, dz)
            if 0.5 < r <= 3.5:
                angle = ring_angle(dx, dz)
                if any(angle_gap(angle, step_angle(y) % math.tau) < 0.5 for y in (landing - 2, landing - 1)):
                    continue
                t.put((x, landing, z), "polished_cloudcite")
    # The holy loot: beside the pedestal on the landing, and at the foot of the stair, opposite the door.
    t.put((cx, landing + 1, cz - 2), *chest(LOOT, "south"))
    t.put((cx, floor_y + 1, cz - 3), *chest(LOOT, "south"))
    t.clear((cx, floor_y + 2, cz - 3))
    # A few fallen stones from the broken crown lie round the foot of the tower.
    for _ in range(9):
        a = rng.uniform(1.9, 2.9)
        r = rng.uniform(4.9, 6.9)
        x, z = round(cx + r * math.cos(a)), round(cz + r * math.sin(a))
        if t.get((x, floor_y + 1, z)) is None:
            if rng.random() < 0.5:
                t.put((x, floor_y + 1, z), "cloudcite_brick_slab", BOTTOM_SLAB)
            else:
                t.put((x, floor_y + 1, z), rng.choice(["cloudcite_bricks", "gilded_cloudcite"]))
    return t


def broken_spire():
    w = d = 13
    h = 22
    cx = cz = 6
    t = Template((w, h, d))
    rng = random.Random(1102)
    floor_y = FOUNDATION - 1

    def floor(dx, dz, r):
        if r <= 2.2:
            return "gilded_cloudcite"                                    # under the pool (water goes on top)
        if 4.6 < r <= 5.4:
            return "gilded_cloudcite"
        return None
    foundation(t, cx, cz, [3.6, 4.6, 5.6, 6.3], floor)
    # The pool of holy water, its bed glowing gold, with a low rim of slabs.
    for x in range(w):
        for z in range(d):
            dx, dz = x - cx, z - cz
            r = math.hypot(dx, dz)
            if r <= 2.2:
                t.put((x, floor_y - 1, z), "gilded_cloudcite")
                t.put((x, floor_y, z), *HOLY_WATER)
            elif r <= 3.1:
                t.put((x, floor_y + 1, z), "cloudcite_brick_slab", BOTTOM_SLAB)
    # Four broken pillars round the pool, gold-banded; a lintel still spans the two tallest.
    pillars = {(-3, -3): 15, (3, -3): 6, (-3, 3): 11, (3, 3): 3}
    for (px, pz), top in pillars.items():
        for y in range(floor_y + 1, floor_y + 1 + top):
            k = y - floor_y
            block = "chiseled_cloudcite" if k == 1 else ("gilded_cloudcite" if k % 4 == 0 else "cloudcite_bricks")
            t.put((cx + px, y, cz + pz), block)
    t.put((cx - 3, floor_y + 16, cz - 3), "holy_gold_block")
    for z in range(cz - 2, cz + 3):
        t.put((cx - 3, floor_y + 10, z), "polished_cloudcite")
    # The spire that stood over the pool, fallen: a line of its stones across the floor, its golden tip at the end.
    fallen = [(cx + 1 + i, cz + 2 + i // 2) for i in range(6)]
    for i, (x, z) in enumerate(fallen):
        if 0 <= x < w and 0 <= z < d:
            block = "holy_gold_block" if i == len(fallen) - 1 else ("gilded_cloudcite" if i % 3 == 1 else "cloudcite_bricks")
            t.put((x, floor_y + 1, z), block)
    # Rubble, and the chest by the pool.
    for _ in range(10):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(3.6, 5.8)
        x, z = round(cx + r * math.cos(a)), round(cz + r * math.sin(a))
        if t.get((x, floor_y + 1, z)) is None:
            if rng.random() < 0.6:
                t.put((x, floor_y + 1, z), "cloudcite_brick_slab", BOTTOM_SLAB)
            else:
                t.put((x, floor_y + 1, z), "cloudcite_brick_stairs", {"facing": rng.choice(["north", "south", "east", "west"]), **STAIRS})
    t.put((cx, floor_y + 1, cz - 4), *chest(LOOT, "south"))
    return t


def sky_shrine():
    w = d = 11
    h = 12
    cx = cz = 5
    t = Template((w, h, d))
    rng = random.Random(1103)
    floor_y = FOUNDATION - 1

    def floor(dx, dz, r):
        if r <= 1.5:
            return "gilded_cloudcite" if r > 0.5 else "chiseled_cloudcite"
        if r > 4.8:
            return "cloudcite_bricks"
        return None
    foundation(t, cx, cz, [3.0, 4.0, 5.0, 5.6], floor)
    # The fountain: a rim of polished cloudcite round a ring of holy water, a golden heart rising from its middle.
    for x in range(w):
        for z in range(d):
            dx, dz = x - cx, z - cz
            r = math.hypot(dx, dz)
            if 0.5 < r <= 1.5:
                t.put((x, floor_y + 1, z), *HOLY_WATER)
            elif 1.5 < r <= 2.3:
                t.put((x, floor_y + 1, z), "polished_cloudcite")
    t.put((cx, floor_y + 1, cz), "chiseled_cloudcite")
    t.put((cx, floor_y + 2, cz), "holy_gold_block")
    t.put((cx, floor_y + 3, cz), "cloudcite_brick_slab", BOTTOM_SLAB)
    # Eight columns in a ring, three of them broken off; a ring of slabs still rests on the rest.
    broken = {2: 2, 5: 1, 6: 3}
    tops = {}
    for k in range(8):
        a = k * math.tau / 8
        x, z = round(cx + 4 * math.cos(a)), round(cz + 4 * math.sin(a))
        top = broken.get(k, 5)
        tops[k] = top
        for i in range(top):
            y = floor_y + 1 + i
            block = "chiseled_cloudcite" if i in (0, 4) else ("gilded_cloudcite" if i == 2 else "cloudcite_bricks")
            t.put((x, y, z), block)
    for x in range(w):
        for z in range(d):
            dx, dz = x - cx, z - cz
            r = math.hypot(dx, dz)
            if 3.2 <= r <= 4.8:
                k = round(ring_angle(dx, dz) / (math.tau / 8)) % 8
                # the ring only rests where the column under that part of it still stands
                if tops[k] == 5 and tops[(k + 1) % 8] == 5 or tops[k] == 5 and tops[(k - 1) % 8] == 5:
                    t.put((x, floor_y + 6, z), "cloudcite_brick_slab", BOTTOM_SLAB)
    # The fallen columns' stones, and the chest.
    for _ in range(6):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(2.6, 4.6)
        x, z = round(cx + r * math.cos(a)), round(cz + r * math.sin(a))
        if t.get((x, floor_y + 1, z)) is None:
            t.put((x, floor_y + 1, z), rng.choice(["cloudcite_bricks", "chiseled_cloudcite", "gilded_cloudcite"]))
    t.put((cx, floor_y + 1, cz + 3), *chest(LOOT, "north"))
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
    counts = []
    for name, build in (("spiral_tower", spiral_tower), ("broken_spire", broken_spire), ("sky_shrine", sky_shrine)):
        counts.append(f"{name} {build().save('heavenly_ruin/' + name)}")
    write(os.path.join(DATA, NS, "loot_table", "chests", "heavenly_ruin.json"), loot_table())
    print("Heavenly ruins written:", ", ".join(counts), "blocks.")


if __name__ == "__main__":
    main()
