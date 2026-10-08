"""
Writes the pink ancient city: vanilla 26.2's ancient city rebuilt for the Trans Realm's pink deep dark.

  * It's built from the realm's own blocks: trans deepslate (bricks, tiles, cobbled, polished, chiseled, with their
    stairs, slabs and walls), a gate framed in pink reinforced trans deepslate, and pink wool and carpet everywhere
    vanilla's is gray or blue.
  * Everything sculk and soul is pink: pink sculk sensors, pink fire on the soul sand (it never goes out there: the
    realm's infiniburn tag includes soul sand), trans lanterns for soul lanterns, pink candles. The cities' sculk
    patches are pink (PinkSculkPatchFeature).
  * It is far less ruined: no ruined buildings or ruined walls, fewer empty plots, the cracks vanilla builds into the
    templates mended, and only a light touch of weathering (vanilla cracks 30% of the bricks and knocks 5% of the
    blocks out; this cracks 6% and knocks out 0.5%).
  * In front of the great gate at its centre, a dais with a circle of eight candle stands under a floating ritual
    crystal, level with the gate's middle and facing it: light the circle with Ritual Candles (in every chest of the
    city, including a double chest on the dais) and the gate fills with a Sky Portal to the Cloud Realm
    (SculkRitual.java). The opening is filled with air in the templates so it's always open.
  * A pink warden keeps watch by the gate, and two sculk people live by the dais.
  * Its chests hold vanilla's ancient city loot with pink sculk in place of vanilla's, sculk gems, and Ritual Candles.

Inputs, copied out of the vanilla 26.2 data (misode/mcmeta, branch 26.2-data):
    tools/vanilla_extra/structures/ancient_city/**.nbt      the city's 57 structure templates

Outputs (under src/main/resources/data/transdimension):
    structure/pink_ancient_city/**.nbt
    worldgen/template_pool/pink_ancient_city/**.json, worldgen/processor_list/pink_ancient_city_*.json
    worldgen/structure/pink_ancient_city.json, worldgen/structure_set/pink_ancient_cities.json
    tags/worldgen/biome/has_structure/pink_ancient_city.json, tags/block/pink_ancient_city_replaceable.json
    loot_table/chests/pink_ancient_city.json, loot_table/chests/pink_ancient_city_ice_box.json

    python3 tools/generate_ancient_city.py      (needs: pip install nbtlib)
"""
import gzip
import io
import json
import math
import os

import nbtlib
from nbtlib import Byte, Compound, Double, Float, Int, List, String

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
SRC = os.path.join(HERE, "vanilla_extra", "structures", "ancient_city")
VANILLA_DATA = os.path.join(HERE, "vanilla_extra", "templates", "ancient_city")
NS = "transdimension"
T = NS + ":"
DATA_VERSION = 4903         # Minecraft 26.2
CITY = "pink_ancient_city"

# Vanilla block -> ours. Block state properties carry over (the swapped blocks share them).
SWAP = {
    "minecraft:sculk_sensor": T + "pink_sculk_sensor",
    "minecraft:soul_fire": T + "pink_fire",
    "minecraft:soul_lantern": T + "trans_lantern",
    "minecraft:candle": "minecraft:pink_candle",
    # The city is built from the realm's own trans deepslate, and its gate frame is pink reinforced deepslate.
    "minecraft:reinforced_deepslate": T + "reinforced_trans_deepslate",
    # the hidden room's little garden
    "minecraft:grass_block": T + "trans_grass_block",
    "minecraft:dirt": T + "trans_dirt",
    # vanilla's dark oak beams, floors and fences, in the realm's own trans wood
    "minecraft:dark_oak_log": T + "trans_log",
    "minecraft:dark_oak_planks": T + "trans_planks",
    "minecraft:dark_oak_fence": T + "trans_fence",
}
# Every wool and carpet in the city is pink (vanilla's floors are gray wool, its banners and beds blue).
for colour in ("gray", "light_gray", "light_blue", "blue", "cyan", "magenta", "white", "black"):
    SWAP[f"minecraft:{colour}_wool"] = "minecraft:pink_wool"
    SWAP[f"minecraft:{colour}_carpet"] = "minecraft:pink_carpet"
DEEPSLATE = {
    "deepslate": "trans_deepslate",
    "cobbled_deepslate": "cobbled_trans_deepslate",
    "cobbled_deepslate_stairs": "cobbled_trans_deepslate_stairs",
    "cobbled_deepslate_slab": "cobbled_trans_deepslate_slab",
    "cobbled_deepslate_wall": "cobbled_trans_deepslate_wall",
    "polished_deepslate": "polished_trans_deepslate",
    "polished_deepslate_stairs": "polished_trans_deepslate_stairs",
    "polished_deepslate_slab": "polished_trans_deepslate_slab",
    "polished_deepslate_wall": "polished_trans_deepslate_wall",
    "deepslate_bricks": "trans_deepslate_bricks",
    "deepslate_brick_stairs": "trans_deepslate_brick_stairs",
    "deepslate_brick_slab": "trans_deepslate_brick_slab",
    "deepslate_brick_wall": "trans_deepslate_brick_wall",
    "deepslate_tiles": "trans_deepslate_tiles",
    "deepslate_tile_stairs": "trans_deepslate_tile_stairs",
    "deepslate_tile_slab": "trans_deepslate_tile_slab",
    "deepslate_tile_wall": "trans_deepslate_tile_wall",
    "chiseled_deepslate": "chiseled_trans_deepslate",
    # mended: vanilla builds cracks into the templates; the processors still crack 6% of the bricks for a little age
    "cracked_deepslate_bricks": "trans_deepslate_bricks",
    "cracked_deepslate_tiles": "trans_deepslate_tiles",
}
SWAP.update({"minecraft:" + k: T + v for k, v in DEEPSLATE.items()})
# Blocks the weathering may knock out (vanilla's #ancient_city_replaceable, in the city's own blocks).
REPLACEABLE = [T + DEEPSLATE[b] for b in ("deepslate", "deepslate_bricks", "deepslate_tiles", "deepslate_brick_slab", "deepslate_tile_slab",
                                          "deepslate_brick_stairs", "deepslate_tile_wall", "deepslate_brick_wall", "cobbled_deepslate")]
REPLACEABLE += [T + "cracked_trans_deepslate_bricks", T + "cracked_trans_deepslate_tiles", "minecraft:pink_wool"]
LOOT = {"minecraft:chests/ancient_city": T + "chests/" + CITY, "minecraft:chests/ancient_city_ice_box": T + "chests/" + CITY + "_ice_box"}

# ---- the ritual, in city_center template coordinates (the same in all three city centres)
# The gate: reinforced deepslate round an opening at x 13, y 18..23, z 11..30, raised on a base wall (x 11..15, up to
# y 16) over the plaza, whose floor is y 9. The opening's middle is the corner between blocks at y 20/21 and z 20/21:
# (13.5, 21.0, 21.0).
GATE_OPENING = [(13, y, z) for y in range(18, 24) for z in range(11, 31)]
# The crystal floats level with that middle, 6 blocks in front of it. Its model sits half a block up and half a block
# towards its clockwise side (SculkRitual.beamOrigin), so the block at (7, 20, 20) facing east shows it at
# (7.5, 21.0, 21.0) and its beam runs dead straight into the gate's middle.
CRYSTAL = (7, 20, 20)
RING_CENTRE = (7.5, 21.0)       # under the crystal, in x and z
DAIS_RADIUS = 4.0
DAIS_TOP = 9                    # the dais' floor; the stands stand on it at y 10
# Eight stands in an even ring round the crystal's foot (3.35 to 3.64 blocks out).
STANDS = [(10, 22), (8, 24), (6, 24), (4, 22), (4, 19), (6, 17), (8, 17), (10, 19)]
# A double chest at the back of the circle, facing the crystal and the gate.
RITUAL_CHEST = [((4, 20), "left"), ((4, 21), "right")]
WARDEN_SPOT = (16, 20)
SCULK_PEOPLE_SPOTS = [(5, 13), (5, 27)]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def vanilla(rel):
    with open(os.path.join(VANILLA_DATA, rel), encoding="utf-8") as f:
        return json.load(f)


# ============================================================================================ templates
def load(rel):
    """{pos: (name, props, nbt)}, size and entities of one vanilla template."""
    nbt = nbtlib.load(os.path.join(SRC, rel + ".nbt"))
    palette = nbt["palette"]
    blocks = {}
    for b in nbt["blocks"]:
        p = palette[int(b["state"])]
        props = {k: str(v) for k, v in p.get("Properties", {}).items()}
        blocks[tuple(int(c) for c in b["pos"])] = (str(p["Name"]), props, b.get("nbt"))
    return blocks, [int(c) for c in nbt["size"]], list(nbt.get("entities", []))


def convert(blocks):
    """Swaps vanilla's blocks for ours and points chests and jigsaws at our loot and pools."""
    out = {}
    for pos, (name, props, nbt) in blocks.items():
        name = SWAP.get(name, name)
        if name == T + "pink_fire":
            props = {}
        if nbt is not None:
            nbt = Compound(nbt)
            if "LootTable" in nbt and str(nbt["LootTable"]) in LOOT:
                nbt["LootTable"] = String(LOOT[str(nbt["LootTable"])])
            if name == "minecraft:jigsaw":
                if str(nbt.get("pool", "")).startswith("minecraft:ancient_city/"):
                    nbt["pool"] = String(T + CITY + "/" + str(nbt["pool"])[len("minecraft:ancient_city/"):])
                # what the jigsaw turns into once the city is placed
                final, bracket, props_text = str(nbt.get("final_state", "")).partition("[")
                if final in SWAP:
                    nbt["final_state"] = String(SWAP[final] + bracket + props_text)
        out[pos] = (name, props, nbt)
    return out


SOFT = ("minecraft:air", "minecraft:cave_air", "minecraft:pink_candle", T + "pink_fire", "minecraft:pink_carpet", "minecraft:redstone_wire",
        "minecraft:jigsaw")


def solid(blocks, pos):
    b = blocks.get(pos)
    return b is not None and b[0] not in SOFT


def standing_spot(blocks, x, z, height):
    """Feet position on the highest floor at x, z (below the gate's base top) with `height` blocks of room above."""
    for y in range(16, 0, -1):
        if solid(blocks, (x, y, z)) and not any(solid(blocks, (x, y + dy, z)) for dy in range(1, height + 1)):
            return (x, y + 1, z)
    return None


def entity(eid, feet, yaw):
    x, y, z = feet[0] + 0.5, float(feet[1]), feet[2] + 0.5
    nbt = Compound({
        "id": String(eid),
        "Pos": List[Double]([Double(x), Double(y), Double(z)]),
        "Motion": List[Double]([Double(0.0), Double(0.0), Double(0.0)]),
        "Rotation": List[Float]([Float(yaw), Float(0.0)]),
        "PersistenceRequired": Byte(1),
        "OnGround": Byte(1),
    })
    return Compound({"pos": List[Double]([Double(x), Double(y), Double(z)]),
                     "blockPos": List[Int]([Int(feet[0]), Int(feet[1]), Int(feet[2])]), "nbt": nbt})


def add_ritual(blocks, entities):
    """The dais, its candle stands, chest and crystal, the always-open gate, and the city's keepers."""
    cx, cz = RING_CENTRE
    for x in range(math.floor(cx - DAIS_RADIUS), math.ceil(cx + DAIS_RADIUS) + 1):
        for z in range(math.floor(cz - DAIS_RADIUS), math.ceil(cz + DAIS_RADIUS) + 1):
            if (x + 0.5 - cx) ** 2 + (z + 0.5 - cz) ** 2 > DAIS_RADIUS ** 2:
                continue
            # pink sculk right under the crystal, polished trans deepslate round it
            centre = abs(x + 0.5 - cx) <= 1.0 and abs(z + 0.5 - cz) <= 1.0
            blocks[(x, DAIS_TOP, z)] = (T + "pink_sculk" if centre else T + "polished_trans_deepslate", {}, None)
            for y in range(DAIS_TOP + 1, CRYSTAL[1] + 3):
                blocks[(x, y, z)] = ("minecraft:air", {}, None)
    for (x, z) in STANDS:
        blocks[(x, DAIS_TOP + 1, z)] = (T + "ritual_pedestal", {"candle": "false"}, None)
    for (x, z), half in RITUAL_CHEST:
        chest = Compound({"id": String("minecraft:chest"), "LootTable": String(T + "chests/" + CITY)})
        blocks[(x, DAIS_TOP + 1, z)] = ("minecraft:chest", {"facing": "east", "type": half, "waterlogged": "false"}, chest)
    blocks[CRYSTAL] = (T + "ritual_crystal", {"facing": "east", "awake": "false"}, None)
    for pos in GATE_OPENING:
        blocks[pos] = ("minecraft:air", {}, None)

    warden = standing_spot(blocks, WARDEN_SPOT[0], WARDEN_SPOT[1], 3)
    if warden:
        entities.append(entity(T + "pink_warden", warden, 90.0))
    for (x, z) in SCULK_PEOPLE_SPOTS:
        spot = standing_spot(blocks, x, z, 2)
        if spot:
            entities.append(entity(T + "sculk_person", spot, -90.0))


def save(blocks, size, entities, rel):
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
    path = os.path.join(DATA, NS, "structure", CITY, rel + ".nbt")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = io.BytesIO()
    nbtlib.File(root).write(raw, "big")
    with open(path, "wb") as out, gzip.GzipFile(filename="", mode="wb", fileobj=out, mtime=0) as gz:
        gz.write(raw.getvalue())


def convert_templates():
    count = 0
    for folder, _, files in os.walk(SRC):
        for f in sorted(files):
            if not f.endswith(".nbt"):
                continue
            rel = os.path.relpath(os.path.join(folder, f[:-4]), SRC).replace(os.sep, "/")
            blocks, size, entities = load(rel)
            blocks = convert(blocks)
            if rel.startswith("city_center/city_center_"):
                add_ritual(blocks, entities)
            save(blocks, size, entities, rel)
            count += 1
    return count


# ============================================================================================ pools and processors
RUINS = ("large_ruin_1", "tall_ruin_1", "tall_ruin_2", "tall_ruin_3", "tall_ruin_4", "medium_ruin_1", "medium_ruin_2",
         "small_ruin_1", "small_ruin_2")
MISSING = ("intact_horizontal_wall_stairs_5",)     # named by vanilla's pool, but absent from its data


def ours(location):
    return T + CITY + "/" + location[len("minecraft:ancient_city/"):]


def convert_element(el):
    t = el.get("element_type")
    if t == "minecraft:list_pool_element":
        return {**el, "elements": [convert_element(e) for e in el["elements"]]}
    if t == "minecraft:feature_pool_element":
        return {**el, "feature": T + "pink_sculk_patch_city"}
    if "location" in el:
        el = {**el, "location": ours(el["location"])}
        if isinstance(el.get("processors"), str):
            el["processors"] = T + CITY + "_" + el["processors"][len("minecraft:ancient_city_"):]
    return el


def keep(entry):
    el = entry["element"]
    location = el.get("location", "")
    name = location.rsplit("/", 1)[-1]
    if name in RUINS or name in MISSING or name.startswith("ruined_"):
        return False
    return True


def generate_pools():
    src = os.path.join(VANILLA_DATA, "template_pool")
    for folder, _, files in os.walk(src):
        for f in files:
            rel = os.path.relpath(os.path.join(folder, f), src).replace(os.sep, "/")
            pool = vanilla("template_pool/" + rel)
            entries = []
            for e in pool["elements"]:
                if not keep(e):
                    continue
                e = {**e, "element": convert_element(e["element"])}
                if e["element"].get("element_type") == "minecraft:empty_pool_element" and rel == "structures.json":
                    e["weight"] = 3     # more buildings, fewer empty plots
                entries.append(e)
            write(os.path.join(DATA, NS, "worldgen", "template_pool", CITY, rel), {**pool, "elements": entries})


def generate_processors():
    """Vanilla's weathering, much lighter and in the city's own blocks: 0.5% of blocks knocked out (was 5%), 6% of the
    bricks and tiles cracked (was 30%), wall slabs knocked out 5% of the time (was 30%), and lanterns kept."""
    cracked = {"minecraft:cracked_deepslate_bricks": T + "cracked_trans_deepslate_bricks",
               "minecraft:cracked_deepslate_tiles": T + "cracked_trans_deepslate_tiles"}
    for kind in ("generic", "start", "walls"):
        lists = vanilla(f"processor_list/ancient_city_{kind}_degradation.json")
        out = []
        for p in lists["processors"]:
            if p["processor_type"] == "minecraft:block_rot":
                p = {**p, "integrity": 0.995, "rottable_blocks": f"#{T}{CITY}_replaceable"}
            elif p["processor_type"] == "minecraft:rule":
                rules = []
                for rule in p["rules"]:
                    block = rule["input_predicate"].get("block")
                    if block == "minecraft:soul_lantern":
                        continue
                    probability = 0.05 if block == "minecraft:deepslate_tile_slab" else 0.06
                    output = rule["output_state"]["Name"]
                    rules.append({**rule,
                                  "input_predicate": {**rule["input_predicate"], "block": SWAP.get(block, block), "probability": probability},
                                  "output_state": {**rule["output_state"], "Name": cracked.get(output, SWAP.get(output, output))}})
                p = {**p, "rules": rules}
            out.append(p)
        write(os.path.join(DATA, NS, "worldgen", "processor_list", f"{CITY}_{kind}_degradation.json"), {"processors": out})
    write(os.path.join(DATA, NS, "tags", "block", f"{CITY}_replaceable.json"), {"values": REPLACEABLE})


# Exactly as rare as vanilla's ancient cities, on the owner's word: vanilla's start height (its floor at y -51) and
# vanilla's spacing (a try every 24 chunks, at least 8 apart), in the Pink Deep Dark, placed like vanilla's deep dark.
# Maps to them (city_map) are this mod's own addition, so the candle ritual's city can always be found.
START_HEIGHT = -27
SPACING = 24
SEPARATION = 8
MAP_TAG = "on_pink_ancient_city_maps"


def generate_structure():
    s = vanilla("structure/ancient_city.json")
    s["biomes"] = f"#{T}has_structure/{CITY}"
    s["start_pool"] = T + CITY + "/city_center"
    s["start_height"] = {"absolute": START_HEIGHT}
    # Monsters stay out of the city (as in vanilla), but fairies and sculk people may wander in.
    s["spawn_overrides"] = {k: v for k, v in s["spawn_overrides"].items() if k not in ("ambient", "creature")}
    write(os.path.join(DATA, NS, "worldgen", "structure", f"{CITY}.json"), s)
    write(os.path.join(DATA, NS, "worldgen", "structure_set", "pink_ancient_cities.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 20083233, "separation": SEPARATION, "spacing": SPACING},
        "structures": [{"structure": T + CITY, "weight": 1}]})
    write(os.path.join(DATA, NS, "tags", "worldgen", "biome", "has_structure", f"{CITY}.json"), {"values": [T + "pink_deep_dark"]})
    # Maps to the nearest city (generate_data.py puts them in trans dungeon chests; maps_in_village_chests below).
    write(os.path.join(DATA, NS, "tags", "worldgen", "structure", f"{MAP_TAG}.json"), {"values": [T + CITY]})


def city_map(weight):
    """A loot entry: a map to the nearest pink ancient city. Its destination is the structure tag WITHOUT a '#'
    (with one, 26.2 can't read the loot table at all)."""
    return {"type": "minecraft:item", "name": "minecraft:map", "weight": weight, "functions": [
        {"function": "minecraft:exploration_map", "destination": T + MAP_TAG, "decoration": "minecraft:red_x",
         "zoom": 2, "search_radius": 100, "skip_existing_chunks": False},
        {"function": "minecraft:set_name", "name": {"translate": f"filled_map.{NS}.{CITY}"}, "target": "item_name"}]}


def maps_in_village_chests():
    """One trans village house chest in twelve also holds a map to the nearest pink ancient city. generate_villages.py
    writes that chest's loot table; this (like generate_egg_house.py's Egg House map) adds its own pool to it, so run it
    again after generate_villages.py."""
    path = os.path.join(DATA, NS, "loot_table", "chests", "trans_house.json")
    with open(path, encoding="utf-8") as f:
        table = json.load(f)
    mine = {T + MAP_TAG, f"#{T}{MAP_TAG}"}
    table["pools"] = [p for p in table["pools"]
                      if not any(fn.get("destination") in mine for e in p["entries"] for fn in e.get("functions", []))]
    table["pools"].append({"rolls": 1.0, "entries": [{"type": "minecraft:empty", "weight": 11}, city_map(1)]})
    write(path, table)


# ============================================================================================ loot
def generate_loot():
    swaps = {"minecraft:sculk": T + "pink_sculk", "minecraft:sculk_sensor": T + "pink_sculk_sensor",
             "minecraft:sculk_catalyst": T + "pink_sculk_catalyst", "minecraft:candle": "minecraft:pink_candle"}
    text = json.dumps(vanilla("loot_table/chests/ancient_city.json"))
    for old, new in swaps.items():
        text = text.replace(f'"{old}"', f'"{new}"')
    table = json.loads(text)
    table["pools"][0]["entries"] += [
        {"type": "minecraft:item", "name": T + "pink_sculk_shrieker", "weight": 2},
        {"type": "minecraft:item", "name": T + "pink_sculk_vein", "weight": 3, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2.0, "max": 6.0}, "add": False}]},
        {"type": "minecraft:item", "name": T + "sculk_gem", "weight": 4, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 3.0}, "add": False}]},
    ]
    # Ritual Candles for the circle: every chest has one to three (the double chest by the dais alone has two to six).
    table["pools"].append({"rolls": 1.0, "entries": [
        {"type": "minecraft:item", "name": T + "ritual_candle", "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 3.0}, "add": False}]}]})
    table["random_sequence"] = T + "chests/" + CITY
    write(os.path.join(DATA, NS, "loot_table", "chests", f"{CITY}.json"), table)
    ice = vanilla("loot_table/chests/ancient_city_ice_box.json")
    ice["random_sequence"] = T + "chests/" + CITY + "_ice_box"
    write(os.path.join(DATA, NS, "loot_table", "chests", f"{CITY}_ice_box.json"), ice)


def main():
    n = convert_templates()
    generate_pools()
    generate_processors()
    generate_structure()
    maps_in_village_chests()
    generate_loot()
    print(f"Pink ancient city: {n} templates")


if __name__ == "__main__":
    main()
