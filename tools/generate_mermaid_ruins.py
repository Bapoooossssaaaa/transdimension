#!/usr/bin/env python3
"""
Builds the mermaid ruins: sunken remains of the homes of the Trans Realm's mermaids, on the floor of its oceans and reefs.

Three ruins, picked at random for each spot:
  * the Mermaid Court: a round courtyard paved in rings of the flag's colours (blue and pink coral, pearl diorite) around
    a fountain holding a glowing pearl, ringed by broken pillars, with a coral-crowned throne and a treasure chest on the
    north side, a vanity with a mirror, a candle and a pearl to the east, and a clam-shell bed to the west;
  * the Mermaid Arch: a collapsing gateway over a flag-striped path, with a little mermaid statue at its end and a chest
    half-buried by the path;
  * the Mermaid Cottage: a small prismarine dome (its roof caved in on one side) over a coral flag rug, with a clam-shell
    bed, a vanity, a chest and a sea-lantern chandelier, in a coral garden.
Kelp, seagrass, coral and sea pickles grow over all of them, and a processor list weathers them a little differently
every time (cracked bricks, worn diorite and prismarine, the odd missing block).

Every template keeps waterloggable blocks un-waterlogged: jigsaw placement waterlogs them wherever there is water, so a
ruin that pokes above a shallow sea doesn't leak water. Templates hold no air, so the sea stays in every gap; the
cottage's interior is filled with water blocks so that a slope can't fill it with sand.

Outputs (under src/main/resources/data/transdimension):
    structure/mermaid_ruin/{court,arch,cottage}.nbt
    worldgen/template_pool/mermaid_ruins.json, worldgen/processor_list/mermaid_weathering.json
    worldgen/structure/mermaid_ruin.json, worldgen/structure_set/mermaid_ruins.json
    tags/worldgen/biome/has_structure/mermaid_ruin.json
    loot_table/chests/mermaid_ruin.json

    python3 tools/generate_mermaid_ruins.py      (needs: pip install nbtlib)
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
LOOT = T + "chests/mermaid_ruin"
# Template y of the paving. y 0-2 below it is foundation; the structure's start_height puts the paving in the sea floor.
FLOOR = 3

PEARL = T + "polished_trans_diorite"
WORN_PEARL = T + "trans_diorite"
BRICK = T + "trans_stone_bricks"
CHISELED = T + "chiseled_trans_stone_bricks"
FOUNDATION = T + "trans_sandstone"
SAND = T + "trans_sand"
PRISMARINE = "minecraft:prismarine_bricks"
DARK = "minecraft:dark_prismarine"
LANTERN = "minecraft:sea_lantern"
CORALS = ("blush", "sky", "pearl")


# ============================================================================================ templates
class Template:
    def __init__(self, size, seed):
        self.size = size
        self.blocks = {}
        self.rng = random.Random(seed)

    def put(self, pos, name, props=None, nbt=None):
        x, y, z = pos
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            self.blocks[(x, y, z)] = (name if ":" in name else T + name, {k: str(v).lower() for k, v in (props or {}).items()}, nbt)

    def get(self, pos):
        b = self.blocks.get(pos)
        return b[0] if b else None

    def free(self, pos):
        return pos not in self.blocks or self.blocks[pos][0] == "minecraft:water"

    def save(self, rel):
        palette, index, entries = [], {}, []
        for pos in sorted(self.blocks, key=lambda p: (p[1], p[2], p[0])):
            name, props, nbt = self.blocks[pos]
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = Compound({"Name": String(name)})
                if props:
                    entry["Properties"] = Compound({k: String(v) for k, v in sorted(props.items())})
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

    # ---- the ruins' recurring bits
    def chest(self, pos, facing):
        self.put(pos, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": False},
                 Compound({"LootTable": String(LOOT), "id": String("minecraft:chest")}))

    def pickle(self, pos, count=None):
        self.put(pos, "trans_sea_pickle", {"pickles": count or self.rng.randint(1, 4), "waterlogged": False})

    def coral(self, pos, colour=None, fan=None):
        colour = colour or self.rng.choice(CORALS)
        fan = self.rng.random() < 0.5 if fan is None else fan
        self.put(pos, f"{colour}_coral_fan" if fan else f"{colour}_coral", {"waterlogged": False})

    def wall_fan(self, pos, facing, colour=None):
        """A coral fan on the side of a block; it points `facing`, so the block it hangs on is the other way."""
        self.put(pos, f"{colour or self.rng.choice(CORALS)}_coral_wall_fan", {"facing": facing, "waterlogged": False})

    def kelp(self, x, z, base_y, height):
        """A kelp column on its own sand block (so it never floats over a dip in the sea floor)."""
        self.put((x, base_y - 2, z), FOUNDATION)
        self.put((x, base_y - 1, z), SAND)
        for y in range(base_y, base_y + height - 1):
            self.put((x, y, z), "trans_kelp_plant")
        self.put((x, base_y + height - 1, z), "trans_kelp", {"age": self.rng.randint(0, 22)})

    def seagrass(self, pos):
        self.put(pos, "trans_seagrass")

    def table(self, x, z0, z1, mirror_x, decor):
        """A vanity along the z axis: a top-slab table at (x, z0..z1), a pane mirror in a pearl frame behind it at
        mirror_x, and decor (block name, props) set on the table."""
        y = FLOOR + 1
        for z in range(z0, z1 + 1):
            self.put((x, y, z), "minecraft:prismarine_brick_slab", {"type": "top", "waterlogged": False})
            for dy in (1, 2):
                # The panes join each other and the frame's walls at both ends.
                self.put((mirror_x, y + dy, z), "minecraft:light_blue_stained_glass_pane", {
                    "north": True, "south": True, "east": False, "west": False, "waterlogged": False})
            self.put((mirror_x, y + 3, z), "polished_trans_diorite_slab", {"type": "bottom", "waterlogged": False})
        for z in (z0 - 1, z1 + 1):
            for dy in (0, 1, 2):
                self.put((mirror_x, y + dy, z), "trans_diorite_wall", {
                    "up": True, "north": "low" if z == z1 + 1 else "none", "south": "low" if z == z0 - 1 else "none",
                    "east": "none", "west": "none", "waterlogged": False})
        for z, (name, props) in zip(range(z0, z1 + 1), decor):
            self.put((x, y + 1, z), name, props)

    def clam_bed(self, x0, x1, z0, z1, head_x):
        """A clam-shell bed: a pearl top-slab mattress with a white coral headboard, a coral pillow and a pearl."""
        y = FLOOR + 1
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.put((x, y, z), "polished_trans_diorite_slab", {"type": "top", "waterlogged": False})
        for z in range(z0, z1 + 1):
            self.put((head_x, y, z), "pearl_coral_block")
            self.put((head_x, y + 1, z), "pearl_coral_block")
        self.put((head_x, y + 2, (z0 + z1) // 2), "pearl_coral_block")
        near = x0 if abs(x0 - head_x) < abs(x1 - head_x) else x1
        self.coral((near, y + 1, (z0 + z1) // 2), "blush", fan=True)
        self.pickle((x0 + x1 - near, y + 1, z1), 2)

    def scatter(self, cells, chance):
        """Coral, seagrass and sea pickles on free cells on top of the paving."""
        for (x, z) in cells:
            pos = (x, FLOOR + 1, z)
            if not self.free(pos) or self.rng.random() > chance:
                continue
            roll = self.rng.random()
            if roll < 0.45:
                self.coral(pos)
            elif roll < 0.8:
                self.seagrass(pos)
            else:
                self.pickle(pos)


def ring_dist(x, z, cx, cz):
    return math.hypot(x - cx, z - cz)


# ============================================================================================ the Mermaid Court
def court():
    t = Template((17, 13, 17), seed=1701)
    c = 8
    cells = []
    for x in range(17):
        for z in range(17):
            r = ring_dist(x, z, c, c)
            if r >= 7.6:
                continue
            for y in range(FLOOR):
                t.put((x, y, z), FOUNDATION)
            # Rings in the flag's colours from the middle out: blue, pink, white, pink, blue, then a prismarine rim.
            pave = (LANTERN if r < 1.5 else "sky_coral_block" if r < 2.5 else "blush_coral_block" if r < 3.5 else PEARL if r < 5.0
                    else "blush_coral_block" if r < 6.0 else "sky_coral_block" if r < 7.0 else PRISMARINE)
            t.put((x, FLOOR, z), pave)
            if 1.5 <= r < 7.0:
                cells.append((x, z))

    # The fountain: a pearl of light on a pedestal, sea pickles round its foot.
    t.put((c, FLOOR + 1, c), DARK)
    t.put((c, FLOOR + 2, c), "minecraft:prismarine_wall", {"up": True, "north": "none", "south": "none", "east": "none", "west": "none",
                                                          "waterlogged": False})
    t.put((c, FLOOR + 3, c), LANTERN)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        t.pickle((c + dx, FLOOR + 1, c + dz), 3)

    # Eight pillars round the rim, broken off at different heights.
    for k, height in enumerate((7, 3, 6, 2, 5, 7, 4, 6)):
        a = k * math.pi / 4
        x, z = round(c + 7 * math.cos(a)), round(c + 7 * math.sin(a))
        for i in range(height):
            y = FLOOR + 1 + i
            block = CHISELED if i == 0 or i == height - 1 and height >= 6 else PRISMARINE if i % 3 == 2 else BRICK
            t.put((x, y, z), block)
        top = (x, FLOOR + 1 + height, z)
        if height >= 6:
            t.pickle(top, 2)
        elif t.rng.random() < 0.6:
            t.coral(top)
        # A fan or two on the pillar's sides.
        for facing, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
            pos = (x + dx, FLOOR + 1 + t.rng.randint(1, max(1, height - 1)), z + dz)
            if t.rng.random() < 0.3 and t.free(pos) and ring_dist(pos[0], pos[2], c, c) < 7.6:
                t.wall_fan(pos, facing)

    # The throne on the north side, facing the fountain: a stair seat, prismarine arms, a tall back crowned with coral.
    t.put((c, FLOOR + 1, 3), "minecraft:prismarine_brick_stairs", {"facing": "north", "half": "bottom", "shape": "straight",
                                                                  "waterlogged": False})
    for x in (c - 1, c + 1):
        t.put((x, FLOOR + 1, 3), "minecraft:prismarine_wall", {"up": True, "north": "none", "south": "none", "east": "none",
                                                              "west": "none", "waterlogged": False})
    for y in range(FLOOR + 1, FLOOR + 4):
        t.put((c, y, 2), DARK)
    t.coral((c, FLOOR + 4, 2), "blush", fan=True)
    t.wall_fan((c - 1, FLOOR + 3, 2), "west", "sky")
    t.wall_fan((c + 1, FLOOR + 3, 2), "east", "sky")
    t.chest((c + 2, FLOOR + 1, 3), "south")

    # A vanity to the east (leaning on the east pillar) and a clam-shell bed to the west.
    t.table(13, 7, 9, 14, [("minecraft:pink_candle", {"candles": 2, "lit": False, "waterlogged": False}),
                           (T + "trans_sea_pickle", {"pickles": 1, "waterlogged": False}),
                           (T + "blush_coral", {"waterlogged": False})])
    t.clam_bed(3, 4, 7, 9, 2)

    # A fallen pillar lying across the paving in the south-west.
    for pos, block in (((4, FLOOR + 1, 11), BRICK), ((5, FLOOR + 1, 12), BRICK), ((6, FLOOR + 1, 12), CHISELED)):
        t.put(pos, block)
    t.put((5, FLOOR + 1, 11), "trans_stone_brick_stairs", {"facing": "east", "half": "bottom", "shape": "straight", "waterlogged": False})

    t.scatter(cells, 0.22)
    for x, z, h in ((1, 1, 6), (15, 2, 8), (1, 15, 5), (16, 14, 7), (0, 6, 4)):
        t.kelp(x, z, FLOOR + 1, h)
    return t


# ============================================================================================ the Mermaid Arch
def arch():
    t = Template((15, 13, 7), seed=2602)
    cells = []
    for x in range(15):
        for z in range(1, 6):
            for y in range(FLOOR):
                t.put((x, y, z), FOUNDATION)
            # The path is the flag in three stripes (blue, white, pink), between prismarine kerbs.
            t.put((x, FLOOR, z), {1: PRISMARINE, 2: "sky_coral_block", 3: PEARL, 4: "blush_coral_block", 5: PRISMARINE}[z])
            if z in (1, 5):
                cells.append((x, z))

    # The gateway: two legs astride the path joined by an arch, its south leg crumbling.
    for x in (6, 7, 8):
        for z, top in ((1, 10), (5, 7)):
            for y in range(FLOOR + 1, top):
                block = CHISELED if y == FLOOR + 1 else PRISMARINE if y == FLOOR + 3 else BRICK
                t.put((x, y, z), block)
        # The arch over the path (missing over its south end, where the leg gave way).
        for z in (1, 2, 3):
            t.put((x, 10, z), BRICK if x == 7 else ("minecraft:pink_glazed_terracotta" if (x + z) % 2 else
                                                   "minecraft:light_blue_glazed_terracotta"), {} if x == 7 else {"facing": "north"})
        t.put((x, 9, 2), "trans_stone_brick_stairs", {"facing": "north", "half": "top", "shape": "straight", "waterlogged": False})
    t.put((7, 11, 2), LANTERN)
    t.put((7, 11, 1), CHISELED)
    t.pickle((7, 12, 2), 3)
    t.wall_fan((5, 8, 1), "west", "blush")
    t.wall_fan((9, 8, 1), "east", "sky")
    # Fallen blocks from the south leg.
    for pos, block in (((9, FLOOR + 1, 6), BRICK), ((10, FLOOR + 1, 5), PRISMARINE), ((11, FLOOR + 1, 6), CHISELED),
                       ((9, FLOOR + 2, 6), BRICK)):
        t.put(pos, block)
        t.put((pos[0], 0, pos[2]), FOUNDATION)
        for y in range(1, FLOOR + (1 if pos[1] == FLOOR + 1 else 0)):
            t.put((pos[0], y, pos[2]), FOUNDATION)

    # A little mermaid statue where the path ends: a white body on a dark tail, coral hair and fins.
    t.put((13, FLOOR + 1, 3), CHISELED)
    t.put((13, FLOOR + 2, 3), DARK)
    t.put((13, FLOOR + 3, 3), PEARL)
    t.put((13, FLOOR + 4, 3), "pearl_coral_block")
    t.coral((13, FLOOR + 5, 3), "blush", fan=True)
    t.wall_fan((12, FLOOR + 2, 3), "west", "sky")
    t.wall_fan((13, FLOOR + 3, 2), "north", "blush")
    t.wall_fan((13, FLOOR + 3, 4), "south", "blush")

    # A chest half-sunk beside the path, and the reef taking the ruin back.
    t.chest((1, FLOOR, 1), "south")
    t.scatter(cells, 0.35)
    for x, z, h in ((0, 0, 5), (4, 6, 7), (12, 0, 6), (14, 6, 4)):
        t.kelp(x, z, FLOOR + 1, h)
    return t


# ============================================================================================ the Mermaid Cottage
def cottage():
    t = Template((13, 11, 13), seed=3803)
    c = 6
    garden = []
    for x in range(13):
        for z in range(13):
            r = ring_dist(x, z, c, c)
            if r > 6.4:
                continue
            for y in range(FLOOR):
                t.put((x, y, z), FOUNDATION)
            if r > 5.4:
                t.put((x, FLOOR, z), SAND)
                garden.append((x, z))
            elif r >= 4.6:
                t.put((x, FLOOR, z), DARK)
            else:
                # A coral rug in the flag's stripes across the middle of the pearl floor.
                rug = {4: "sky_coral_block", 5: "blush_coral_block", 6: "pearl_coral_block", 7: "blush_coral_block", 8: "sky_coral_block"}
                t.put((x, FLOOR, z), rug[z] if 4 <= x <= 8 and z in rug else PEARL)

    # Walls with a window on three sides and a door to the south; the dome roof has fallen in to the north-east.
    caved = lambda x, z: x >= 7 and z <= 5
    for x in range(13):
        for z in range(13):
            r = ring_dist(x, z, c, c)
            if 4.6 <= r <= 5.4:
                for y in range(FLOOR + 1, FLOOR + 4):
                    door = x == c and z > c and y <= FLOOR + 2
                    window = y == FLOOR + 2 and (x, z) in ((c, 1), (1, c), (11, c))
                    if door:
                        continue
                    if window:
                        ns = x == c
                        t.put((x, y, z), "minecraft:light_blue_stained_glass_pane", {
                            "east": ns, "west": ns, "north": not ns, "south": not ns, "waterlogged": False})
                    elif not (caved(x, z) and y == FLOOR + 3):
                        t.put((x, y, z), PRISMARINE)
            if 3.6 <= r < 4.6 and not caved(x, z):
                t.put((x, FLOOR + 4, z), "minecraft:prismarine")
            if 2.5 <= r < 3.6 and not caved(x, z):
                t.put((x, FLOOR + 5, z), DARK)
            if r < 2.5 and not caved(x, z):
                t.put((x, FLOOR + 6, z), LANTERN if (x, z) == (c, c) else PRISMARINE)
    # The fallen roof lies outside, in the garden.
    for pos, block in (((11, FLOOR + 1, 2), "minecraft:prismarine"), ((10, FLOOR + 1, 1), DARK), ((12, FLOOR + 1, 4), PRISMARINE)):
        t.put(pos, block)

    # Keep the inside open even where the sea floor rises: water in every empty cell of the room.
    for x in range(13):
        for z in range(13):
            if ring_dist(x, z, c, c) < 4.6:
                for y in range(FLOOR + 1, FLOOR + 4):
                    t.put((x, y, z), "minecraft:water", {"level": 0})

    t.clam_bed(3, 4, 5, 7, 2)
    t.table(9, 5, 7, 10, [("minecraft:light_blue_candle", {"candles": 3, "lit": False, "waterlogged": False}),
                          (T + "trans_sea_pickle", {"pickles": 2, "waterlogged": False}),
                          (T + "pearl_coral_fan", {"waterlogged": False})])
    t.chest((c, FLOOR + 1, 2), "south")
    t.pickle((c, FLOOR + 1, 9), 4)
    t.coral((4, FLOOR + 1, 9), "sky")

    # A coral garden all round, with kelp.
    for (x, z) in garden:
        pos = (x, FLOOR + 1, z)
        if t.free(pos) and t.rng.random() < 0.55:
            t.coral(pos) if t.rng.random() < 0.7 else t.pickle(pos)
    for x, z, h in ((1, 2, 6), (11, 10, 7), (2, 11, 5)):
        t.kelp(x, z, FLOOR + 1, h)
    return t


# ============================================================================================ data
def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def ruin_data():
    wg = os.path.join(DATA, NS, "worldgen")
    write(os.path.join(wg, "template_pool", "mermaid_ruins.json"), {
        "elements": [{"element": {"element_type": "minecraft:single_pool_element", "location": T + f"mermaid_ruin/{name}",
                                  "processors": T + "mermaid_weathering", "projection": "rigid"}, "weight": weight}
                     for name, weight in (("court", 1), ("arch", 2), ("cottage", 2))],
        "fallback": "minecraft:empty"})
    # The template's paving (y 3) replaces the top block of the sea floor.
    write(os.path.join(wg, "structure", "mermaid_ruin.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/mermaid_ruin",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "OCEAN_FLOOR_WG",
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"absolute": -FLOOR},
        "start_pool": T + "mermaid_ruins",
        "step": "surface_structures",
        "terrain_adaptation": "none",
        "use_expansion_hack": False,
    })
    # About as common as vanilla's ocean ruins: one per 22x22 chunk cell, never closer than 7 chunks to the next.
    write(os.path.join(wg, "structure_set", "mermaid_ruins.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 31415926, "separation": 7, "spacing": 22},
        "structures": [{"structure": T + "mermaid_ruin", "weight": 1}]})
    write(os.path.join(DATA, NS, "tags", "worldgen", "biome", "has_structure", "mermaid_ruin.json"), {
        "values": [T + b for b in ("trans_ocean", "deep_trans_ocean", "pastel_reef")]})

    def worn(block, into, chance):
        return {"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": block, "probability": chance},
                "location_predicate": {"predicate_type": "minecraft:always_true"}, "output_state": {"Name": into}}

    write(os.path.join(wg, "processor_list", "mermaid_weathering.json"), {"processors": [{"processor_type": "minecraft:rule", "rules": [
        worn(BRICK, T + "cracked_trans_stone_bricks", 0.3),
        worn(PRISMARINE, "minecraft:prismarine", 0.25),
        worn(PEARL, WORN_PEARL, 0.2),
        worn(BRICK, "minecraft:water", 0.04),
        worn(PRISMARINE, "minecraft:water", 0.04),
    ]}]})

    def item(name, weight, lo=1, hi=1, functions=()):
        e = {"type": "minecraft:item", "name": name if ":" in name else T + name, "weight": weight}
        fns = list(functions)
        if hi > 1:
            fns.insert(0, {"function": "minecraft:set_count", "add": False,
                           "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}})
        if fns:
            e["functions"] = fns
        return e

    write(os.path.join(DATA, NS, "loot_table", "chests", "mermaid_ruin.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3.0, "max": 6.0}, "entries": [
                item("trans_pearl", 10, 1, 3), item("minecraft:prismarine_shard", 10, 2, 6), item("minecraft:prismarine_crystals", 8, 1, 4),
                item("minecraft:nautilus_shell", 6, 1, 2), item("minecraft:gold_nugget", 8, 2, 8), item("trans_sea_pickle", 6, 1, 4),
                item("cooked_trans_fish", 6, 1, 3), item("minecraft:dried_kelp", 5, 2, 6), item("minecraft:emerald", 4, 1, 2),
                item("minecraft:gold_ingot", 3, 1, 2)]},
            {"rolls": 1.0, "entries": [
                {"type": "minecraft:empty", "weight": 4}, item("trans_crystal", 5, 1, 2), item("minecraft:golden_apple", 2),
                item("minecraft:heart_of_the_sea", 1),
                item("minecraft:book", 4, functions=[{"function": "minecraft:enchant_randomly", "options": [
                    "minecraft:aqua_affinity", "minecraft:depth_strider", "minecraft:respiration", "minecraft:luck_of_the_sea",
                    "minecraft:lure"]}])]},
        ],
        "random_sequence": LOOT})


def main():
    counts = {name: build().save(f"mermaid_ruin/{name}") for name, build in (("court", court), ("arch", arch), ("cottage", cottage))}
    ruin_data()
    print("Mermaid ruins:", ", ".join(f"{name} {n} blocks" for name, n in counts.items()))


if __name__ == "__main__":
    main()
