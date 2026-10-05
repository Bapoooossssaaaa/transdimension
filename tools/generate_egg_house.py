#!/usr/bin/env python3
"""
Builds the Egg House floating island from the owner's world save ("Egg House!.zip" in the repository root, a
Minecraft 1.16.5 world).

The house and its little garden are copied block for block, so every chest, bed, door, stair and trapdoor keeps the
exact facing it was built with, and the lecterns keep their notes, the item frames their eggs and the armour stand its
trans flag outfit. Minecraft materials are swapped for the mod's where the mod has one: trans planks, doors, trapdoors
and fences for the crimson ones, trans logs for the acacia frame, trans glass and stained glass panes, trans lanterns,
trans stone bricks, trans grass and dirt, the realm's flowers, and the two beds of the double bed become trans beds
(so they show the heart). The quartz roof, the concrete walls in the flag's colours and the warped wood stay.

Under the house the script carves a floating island: a rough, rounded disc of trans grass over trans dirt and stone,
tapering to a point with ores and a glowing trans crystal tip, with spore blossoms hanging from its underside.
Maddie lives inside (the dialogue and her gifts are in Java).

The template keeps the save's data version (2586, 1.16.5) so Minecraft upgrades the item frames, armour stand and
books itself when it loads the template; blocks are written with their current names.

Outputs (under src/main/resources/data):
    transdimension/structure/egg_house_island.nbt
    transdimension/worldgen/template_pool/egg_house_island.json
    transdimension/worldgen/structure/egg_house_island.json
    transdimension/worldgen/structure_set/egg_house_island.json
    transdimension/tags/worldgen/biome/has_structure/egg_house_island.json
    transdimension/tags/worldgen/structure/on_egg_house_maps.json
    transdimension/loot_table/chests/egg_house.json
    transdimension/loot_table/chests/trans_house.json   (adds rare maps to the island to village house chests)

    python3 tools/generate_egg_house.py      (needs: pip install nbtlib)
"""
import gzip
import io
import json
import math
import os
import random
import struct
import zipfile
import zlib

import nbtlib
from nbtlib import Byte, Compound, Double, Float, Int, List, String

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
WORLD_ZIP = os.path.join(ROOT, "Egg House!.zip")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "transdimension"
T = NS + ":"

# The part of the save that holds the house, its garden, the flag and the trees (inclusive block coordinates).
X0, X1 = -264, -232
Z0, Z1 = -181, -149
GROUND = 75                 # the house's ground floor level; the island's grass is at this height
TOP = 96                    # one above the flag
DEPTH = 18                  # how far the island hangs below its grass at the centre
DATA_VERSION = 2586         # Minecraft 1.16.5
ISLAND_CENTRE = (-247.5, -165.0)
ISLAND_RADII = (16.0, 15.5)

RENAME = {
    "minecraft:crimson_planks": T + "trans_planks",
    "minecraft:crimson_trapdoor": T + "trans_trapdoor",
    "minecraft:crimson_door": T + "trans_door",
    "minecraft:crimson_fence": T + "trans_fence",
    "minecraft:crimson_pressure_plate": T + "trans_pressure_plate",
    "minecraft:stripped_crimson_hyphae": T + "stripped_trans_wood",
    "minecraft:acacia_log": T + "trans_log",
    "minecraft:acacia_wood": T + "trans_wood",
    "minecraft:oak_log": T + "trans_log",
    "minecraft:petrified_oak_slab": T + "trans_slab",
    "minecraft:pink_stained_glass_pane": T + "trans_pink_stained_glass_pane",
    "minecraft:light_blue_stained_glass_pane": T + "trans_blue_stained_glass_pane",
    "minecraft:cyan_stained_glass_pane": T + "trans_blue_stained_glass_pane",
    "minecraft:white_stained_glass_pane": T + "trans_glass_pane",
    "minecraft:white_stained_glass": T + "trans_glass",
    "minecraft:pink_stained_glass": T + "trans_pink_stained_glass",
    "minecraft:light_blue_stained_glass": T + "trans_blue_stained_glass",
    "minecraft:soul_lantern": T + "trans_lantern",
    "minecraft:stone_brick_stairs": T + "trans_stone_brick_stairs",
    "minecraft:stone_brick_slab": T + "trans_stone_brick_slab",
    "minecraft:stone_brick_wall": T + "trans_stone_brick_wall",
    "minecraft:grass_block": T + "trans_grass_block",
    "minecraft:dirt": T + "trans_dirt",
    "minecraft:coarse_dirt": T + "trans_dirt",
    "minecraft:stone": T + "trans_stone",
    "minecraft:granite": T + "trans_granite",
    "minecraft:diorite": T + "trans_diorite",
    "minecraft:andesite": T + "trans_andesite",
    "minecraft:green_carpet": T + "trans_moss_carpet",
    "minecraft:allium": T + "lavender_puff",
    "minecraft:pink_tulip": T + "trans_tulip",
    "minecraft:blue_orchid": T + "sky_bell",
    "minecraft:cornflower": T + "sky_bell",
    "minecraft:white_tulip": T + "pearl_daisy",
    "minecraft:lily_of_the_valley": T + "pearl_daisy",
    "minecraft:oxeye_daisy": T + "pearl_daisy",
    "minecraft:peony": T + "pride_peony",
    "minecraft:lilac": T + "pride_peony",
    "minecraft:potted_allium": T + "potted_lavender_puff",
    "minecraft:potted_cornflower": T + "potted_sky_bell",
    "minecraft:potted_blue_orchid": T + "potted_sky_bell",
    "minecraft:potted_lily_of_the_valley": T + "potted_pearl_daisy",
    "minecraft:potted_pink_tulip": T + "potted_trans_tulip",
    # Renamed by Minecraft since 1.16.
    "minecraft:chain": "minecraft:iron_chain",
    "minecraft:grass_path": "minecraft:dirt_path",
    "minecraft:grass": "minecraft:short_grass",
    "minecraft:cave_air": "minecraft:air",
}
LEAVES = {"minecraft:oak_leaves": T + "trans_leaves", "minecraft:acacia_leaves": T + "sky_leaves"}
BEDS = ("minecraft:pink_bed", "minecraft:light_blue_bed")
CONTAINERS = ("minecraft:chest", "minecraft:barrel")
TERRAIN = {"minecraft:dirt", "minecraft:grass_block", "minecraft:stone", "minecraft:granite", "minecraft:diorite",
           "minecraft:andesite", "minecraft:coarse_dirt", "minecraft:gravel"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
STEP = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


# ============================================================================================ reading the 1.16 save
class World:
    """Just enough of the Anvil format to read block states, block entities and entities of a 1.16 world."""

    def __init__(self, path):
        self.zip = zipfile.ZipFile(path)
        self.regions = {}
        self.chunks = {}

    def _region(self, rx, rz):
        if (rx, rz) not in self.regions:
            name = f"Egg House!/region/r.{rx}.{rz}.mca"
            self.regions[(rx, rz)] = self.zip.read(name) if name in self.zip.namelist() else None
        return self.regions[(rx, rz)]

    def chunk(self, cx, cz):
        if (cx, cz) in self.chunks:
            return self.chunks[(cx, cz)]
        data = self._region(cx >> 5, cz >> 5)
        result = None
        if data:
            idx = 4 * ((cx & 31) + (cz & 31) * 32)
            loc = struct.unpack(">I", data[idx:idx + 4])[0]
            if loc >> 8:
                start = (loc >> 8) * 4096
                length = struct.unpack(">I", data[start:start + 4])[0]
                raw = data[start + 5:start + 4 + length]
                raw = zlib.decompress(raw) if data[start + 4] == 2 else gzip.decompress(raw)
                result = self._decode(nbtlib.File.parse(io.BytesIO(raw)))
        self.chunks[(cx, cz)] = result
        return result

    @staticmethod
    def _decode(nbt):
        level = nbt["Level"]
        sections = {}
        for sec in level.get("Sections", []):
            if "Palette" not in sec or "BlockStates" not in sec:
                continue
            palette = [(str(p["Name"]), {str(k): str(v) for k, v in p.get("Properties", {}).items()}) for p in sec["Palette"]]
            longs = [int(v) & 0xFFFFFFFFFFFFFFFF for v in sec["BlockStates"]]
            bits = max(4, math.ceil(math.log2(len(palette))))
            per_long = 64 // bits                       # 1.16 packs whole entries per long (no spanning)
            mask = (1 << bits) - 1
            idxs = [(longs[i // per_long] >> ((i % per_long) * bits)) & mask for i in range(4096)]
            sections[int(sec["Y"])] = (palette, idxs)
        tiles = {(int(t["x"]), int(t["y"]), int(t["z"])): t for t in level.get("TileEntities", [])}
        return {"sections": sections, "tiles": tiles, "entities": list(level.get("Entities", []))}

    def block(self, x, y, z):
        ch = self.chunk(x >> 4, z >> 4)
        sec = ch["sections"].get(y >> 4) if ch else None
        if sec is None:
            return "minecraft:air", {}
        palette, idxs = sec
        return palette[idxs[((y & 15) * 16 + (z & 15)) * 16 + (x & 15)]]

    def tile(self, x, y, z):
        ch = self.chunk(x >> 4, z >> 4)
        return ch["tiles"].get((x, y, z)) if ch else None

    def entities(self):
        out = []
        for cx in range(X0 >> 4, (X1 >> 4) + 1):
            for cz in range(Z0 >> 4, (Z1 >> 4) + 1):
                ch = self.chunk(cx, cz)
                out += ch["entities"] if ch else []
        return out


# ============================================================================================ the island
def island_mask():
    """Which columns the island covers: a slightly wobbly ellipse around the house."""
    rng = random.Random(1997)
    wobble = [rng.uniform(-0.09, 0.09) for _ in range(12)]
    cells = {}
    cx, cz = ISLAND_CENTRE
    rx, rz = ISLAND_RADII
    for z in range(Z0, Z1 + 1):
        for x in range(X0, X1 + 1):
            dx, dz = (x - cx) / rx, (z - cz) / rz
            angle = math.atan2(dz, dx) % (2 * math.pi)
            k = angle / (2 * math.pi) * 12
            w = wobble[int(k) % 12] * (1 - k % 1) + wobble[(int(k) + 1) % 12] * (k % 1)
            r = math.hypot(dx, dz) / (1 + w)
            if r <= 1.0:
                cells[(x, z)] = r
    return cells


def island_blocks(mask, house_columns):
    """Blocks of the island body below the ground floor: {(x, y, z): (name, props)}."""
    rng = random.Random(75)
    out = {}
    for (x, z), r in mask.items():
        depth = 1 + round((DEPTH - 1) * max(0.0, 1 - r ** 1.6) + rng.uniform(-1.2, 1.2))
        depth = max(depth, 4 if (x, z) in house_columns else 1)
        bottom = GROUND - depth
        for y in range(bottom, GROUND):
            below_top = GROUND - y
            if below_top <= 3 - (r > 0.85):
                name = T + "trans_dirt"
            elif y - bottom < 2 and r < 0.35:
                name = T + "trans_deepslate"
            else:
                roll = rng.random()
                name = (T + "trans_coal_ore" if roll < 0.03 else T + "trans_iron_ore" if roll < 0.05
                        else T + "trans_crystal_ore" if roll < 0.06 else T + "trans_granite" if roll < 0.12
                        else T + "trans_diorite" if roll < 0.18 else T + "trans_stone")
            out[(x, y, z)] = (name, {"axis": "y"} if name == T + "trans_deepslate" else {})
        # Things hanging from the underside: spore blossoms, and crystal clusters near the tip.
        if r < 0.3 and depth >= DEPTH - 2 and rng.random() < 0.5:
            out[(x, bottom - 1, z)] = (T + "trans_crystal_cluster", {"facing": "down", "waterlogged": "false"})
        elif 0.3 <= r < 0.9 and rng.random() < 0.06:
            out[(x, bottom - 1, z)] = ("minecraft:spore_blossom", {})
    return out


# ============================================================================================ the house
def convert(name, props):
    if name in LEAVES:
        return LEAVES[name], {"distance": props.get("distance", "1"), "persistent": "true", "waterlogged": "false"}
    if name in BEDS:
        return T + "trans_bed", dict(props)
    return RENAME.get(name, name), dict(props)


def bed_hearts(blocks):
    """Sets the trans beds' "heart" property the way TransBedBlock would (pairs along a row from the left end)."""
    for pos in sorted(blocks):
        name, props, _ = blocks[pos]
        if name != T + "trans_bed":
            continue
        props["heart"] = "none"
    feet = {p for p, b in blocks.items() if b[0] == T + "trans_bed" and b[1].get("part") == "foot"}

    def foot_facing(p, facing):
        return p in feet and blocks[p][1]["facing"] == facing

    # Settle each row from its counter-clockwise end, like the block's shape updates would.
    for _ in range(4):
        for p in sorted(feet):
            facing = blocks[p][1]["facing"]
            lx, lz = STEP[CW[CW[CW[facing]]]]       # counter-clockwise neighbour
            rx, rz = STEP[CW[facing]]               # clockwise neighbour
            left, right = (p[0] + lx, p[1], p[2] + lz), (p[0] + rx, p[1], p[2] + rz)
            if foot_facing(left, facing) and blocks[left][1]["heart"] == "left":
                blocks[p][1]["heart"] = "right"
            else:
                blocks[p][1]["heart"] = "left" if foot_facing(right, facing) else "none"


def container_nbt(name, loot):
    return Compound({"id": String(name), "LootTable": String(loot)})


def house_blocks(world, mask):
    """The house, garden, trees and flag: {(x, y, z): [name, props, nbt]} in world coordinates."""
    blocks = {}
    for y in range(GROUND, TOP):
        for z in range(Z0, Z1 + 1):
            for x in range(X0, X1 + 1):
                name, props = world.block(x, y, z)
                if name in ("minecraft:air", "minecraft:cave_air"):
                    continue
                if (x, z) not in mask:
                    continue                        # beyond the island's edge (the old hillside)
                if y == GROUND and name in TERRAIN | {"minecraft:grass", "minecraft:tall_grass"}:
                    continue                        # the island makes its own ground
                new_name, new_props = convert(name, props)
                nbt = None
                tile = world.tile(x, y, z)
                if name in CONTAINERS:
                    nbt = container_nbt(name, T + "chests/egg_house")
                elif name == "minecraft:lectern" and tile is not None and "Book" in tile:
                    nbt = Compound({"id": String("minecraft:lectern"), "Book": tile["Book"], "Page": Int(0)})
                elif name == "minecraft:bee_nest":
                    nbt = Compound({"id": String("minecraft:beehive")})
                blocks[(x, y, z)] = [new_name, new_props, nbt]
    bed_hearts(blocks)
    return blocks


def ground_layer(world, mask, house):
    """The island's top: the house's own floor where it has one, paths where the save had paths, else trans grass."""
    out = {}
    rng = random.Random(4)
    for (x, z), r in mask.items():
        if (x, GROUND, z) in house:
            continue
        below = world.block(x, GROUND, z)[0]
        if below == "minecraft:grass_path" or world.block(x, GROUND - 1, z)[0] == "minecraft:grass_path" and r < 0.95:
            out[(x, GROUND, z)] = ("minecraft:dirt_path", {})
        else:
            out[(x, GROUND, z)] = (T + "trans_grass_block", {"snowy": "false"})
            above = (x, GROUND + 1, z)
            if above not in house and r < 0.97 and rng.random() < 0.12:
                out[above] = (rng.choice([T + "trans_tulip", T + "pearl_daisy", T + "sky_bell", T + "heart_bloom",
                                          "minecraft:short_grass", "minecraft:short_grass"]), {})
    return out


# ============================================================================================ entities
def relative(pos, origin):
    return [pos[i] - origin[i] for i in range(3)]


def entity_entries(world, origin, mask):
    out = []
    for e in world.entities():
        eid = str(e["id"])
        if eid not in ("minecraft:item_frame", "minecraft:armor_stand"):
            continue
        p = [float(v) for v in e["Pos"]]
        if not (X0 <= p[0] < X1 + 1 and Z0 <= p[2] < Z1 + 1):
            continue
        nbt = Compound(e)
        for key in ("UUID", "Attributes", "Brain"):     # the armour stand was saved with a modded attribute
            nbt.pop(key, None)
        rel = relative(p, origin)
        block_pos = [math.floor(v) for v in rel]
        if eid == "minecraft:item_frame":
            # Hanging entities remember the block they hang on; keep it relative to the template too.
            tile = [int(e["TileX"]) - origin[0], int(e["TileY"]) - origin[1], int(e["TileZ"]) - origin[2]]
            nbt["TileX"], nbt["TileY"], nbt["TileZ"] = Int(tile[0]), Int(tile[1]), Int(tile[2])
            block_pos = tile
        out.append(Compound({"pos": List[Double]([Double(v) for v in rel]),
                             "blockPos": List[Int]([Int(v) for v in block_pos]), "nbt": nbt}))
    return out


def maddie(origin, pos, yaw):
    rel = relative(pos, origin)
    nbt = Compound({
        "id": String(T + "maddie"),
        "Pos": List[Double]([Double(v) for v in rel]),
        "Motion": List[Double]([Double(0.0), Double(0.0), Double(0.0)]),
        "Rotation": List[Float]([Float(yaw), Float(0.0)]),
        "PersistenceRequired": Byte(1),
        "OnGround": Byte(1),
        "Health": Float(20.0),
    })
    return Compound({"pos": List[Double]([Double(v) for v in rel]),
                     "blockPos": List[Int]([Int(math.floor(v)) for v in rel]), "nbt": nbt})


# ============================================================================================ output
def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def save_template(blocks, entities, size):
    palette, index, entries = [], {}, []
    for pos in sorted(blocks, key=lambda p: (p[1], p[2], p[0])):
        name, props, nbt = blocks[pos]
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
        "size": List[Int]([Int(c) for c in size]),
        "palette": List[Compound](palette),
        "blocks": List[Compound](entries),
        "entities": List[Compound](entities),
    })
    path = os.path.join(DATA, NS, "structure", "egg_house_island.nbt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = io.BytesIO()
    nbtlib.File(root).write(raw, "big")
    with open(path, "wb") as out, gzip.GzipFile(filename="", mode="wb", fileobj=out, mtime=0) as gz:
        gz.write(raw.getvalue())


def worldgen_files():
    wg = os.path.join(DATA, NS, "worldgen")
    write(os.path.join(wg, "template_pool", "egg_house_island.json"), {
        "elements": [{"element": {"element_type": "minecraft:single_pool_element", "location": T + "egg_house_island",
                                  "processors": {"processors": []}, "projection": "rigid"}, "weight": 1}],
        "fallback": "minecraft:empty"})
    # The island floats 45-60 blocks above the ground where it starts, so it never sinks into a hill.
    write(os.path.join(wg, "structure", "egg_house_island.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/egg_house_island",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 45}, "max_inclusive": {"absolute": 60}},
        "start_pool": T + "egg_house_island",
        "step": "surface_structures",
        "terrain_adaptation": "none",
        "use_expansion_hack": False,
    })
    # Very rare: at most one island per 152x152 chunk cell (2432 blocks), never closer than 76 chunks to the next.
    # That is ten times rarer than the first version's 48-chunk grid ((152 / 48)^2 = 10).
    write(os.path.join(wg, "structure_set", "egg_house_island.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 52011997, "separation": 76, "spacing": 152},
        "structures": [{"structure": T + "egg_house_island", "weight": 1}]})
    write(os.path.join(DATA, NS, "tags", "worldgen", "biome", "has_structure", "egg_house_island.json"), {
        "values": [T + b for b in ("trans_meadow", "trans_forest", "frosted_fields", "sugar_dunes", "lavender_marsh",
                                   "crystal_grove", "heartwood_grove", "pearlwood_forest", "bluebell_woods",
                                   "twilight_thicket", "candy_floss_grove", "trans_ocean", "trans_beach")]})
    write(os.path.join(DATA, NS, "tags", "worldgen", "structure", "on_egg_house_maps.json"), {
        "values": [T + "egg_house_island"]})


def loot_table():
    def item(name, weight, lo=1, hi=1):
        e = {"type": "minecraft:item", "name": name, "weight": weight}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "add": False,
                               "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}}]
        return e

    write(os.path.join(DATA, NS, "loot_table", "chests", "egg_house.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 2.0, "max": 5.0}, "entries": [
                item(T + "trans_cookie", 10, 2, 6), item(T + "trans_cupcake", 6, 1, 3), item(T + "trans_macaron", 6, 1, 4),
                item(T + "trans_donut", 5, 1, 2), item(T + "trans_boba", 3), item("minecraft:diamond", 4, 1, 2),
                item(T + "trans_wool", 5, 2, 6), item(T + "pride_blossom", 5, 1, 3), item(T + "heart_bloom", 4, 1, 3),
                item("minecraft:egg", 6, 1, 4), item("minecraft:feather", 4, 1, 5), item("minecraft:book", 3, 1, 2)]},
            {"rolls": 1.0, "entries": [{"type": "minecraft:empty", "weight": 4}, item(T + "trans_lantern", 1, 1, 2),
                                       item("minecraft:emerald", 1, 1, 3), item(T + "trans_bed", 1)]},
        ],
        "random_sequence": T + "chests/egg_house"})


def treasure_map_in_village_chests():
    """One in ten trans village house chests holds a map to the nearest Egg House island. (Fairy Sanctums have no maps:
    a thrown Trans Crystal Pearl leads the way, like an eye of ender.)

    An exploration map's destination is a structure tag written WITHOUT a '#' (vanilla's cartographer trades do the same);
    with one, 26.2 can't read the loot table at all and every chest using it comes up empty.
    """
    path = os.path.join(DATA, NS, "loot_table", "chests", "trans_house.json")
    with open(path, encoding="utf-8") as f:
        table = json.load(f)
    destination = f"{NS}:on_egg_house_maps"
    # Older runs also added sanctum maps, and some wrote '#' forms (which broke the whole table); drop all of those.
    maps = {destination, f"{NS}:fairy_sanctums", f"#{NS}:on_egg_house_maps", f"#{NS}:fairy_sanctums"}
    table["pools"] = [p for p in table["pools"]
                      if not any(fn.get("destination") in maps for e in p["entries"] for fn in e.get("functions", []))]
    table["pools"].append({"rolls": 1.0, "entries": [
        {"type": "minecraft:empty", "weight": 9},
        {"type": "minecraft:item", "name": "minecraft:map", "weight": 1, "functions": [
            {"function": "minecraft:exploration_map", "destination": destination, "decoration": "minecraft:red_x",
             "zoom": 2, "search_radius": 100, "skip_existing_chunks": False},
            {"function": "minecraft:set_name", "name": {"translate": "filled_map.transdimension.egg_house"},
             "target": "item_name"}]}]})
    write(path, table)


def main():
    world = World(WORLD_ZIP)
    mask = island_mask()
    house = house_blocks(world, mask)
    house_columns = {(x, z) for (x, _, z) in house}
    blocks = {}
    for pos, b in island_blocks(mask, house_columns).items():
        blocks[pos] = [b[0], dict(b[1]), None]
    for pos, b in ground_layer(world, mask, house).items():
        blocks[pos] = [b[0], dict(b[1]), None]
    blocks.update(house)

    min_y = min(p[1] for p in blocks)
    origin = (X0, min_y, Z0)
    size = (X1 - X0 + 1, TOP - min_y, Z1 - Z0 + 1)
    local = {tuple(relative(p, origin)): b for p, b in blocks.items()}
    entities = entity_entries(world, origin, mask)
    # Maddie waits in the open middle of the ground floor, facing the front door.
    entities.append(maddie(origin, (-246.5, GROUND + 1.0, -168.5), 0.0))
    save_template(local, entities, size)
    worldgen_files()
    loot_table()
    treasure_map_in_village_chests()
    beds = [(p, b[1]) for p, b in house.items() if b[0] == T + "trans_bed"]
    print(f"Egg House island: {len(blocks)} blocks, {len(entities)} entities, size {size}; beds {beds}")


if __name__ == "__main__":
    main()
