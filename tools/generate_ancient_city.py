"""
Writes the pink ancient city: vanilla 26.2's ancient city rebuilt for the Trans Realm's pink deep dark.

  * Everything sculk and soul is pink: pink sculk sensors, pink fire on the soul sand (it never goes out there: the
    realm's infiniburn tag includes soul sand), trans lanterns for soul lanterns, pink candles, and pink and magenta
    wool and carpet where vanilla's is blue. The cities' sculk patches are pink (PinkSculkPatchFeature).
  * It is far less ruined: no ruined buildings or ruined walls, fewer empty plots, and only a light touch of
    weathering (vanilla cracks 30% of the bricks and knocks 5% of the blocks out; this cracks 6% and knocks out 0.5%).
  * In front of the great gate at its centre, a dais with a circle of eight candle stands under a floating ritual
    crystal that faces the gate: light the circle with Ritual Candles (found in the city's chests) and the gate fills
    with a Sky Portal to the Cloud Realm (SculkRitual.java; its GATE_DISTANCE/GATE_RISE must match CRYSTAL and the
    gate's opening here). The opening is filled with air in the templates so it's always open.
  * A pink warden keeps watch by the gate, and two sculk people live by the dais.
  * Its chests hold vanilla's ancient city loot with pink sculk in place of vanilla's, sculk gems, and Ritual Candles.

Inputs, copied out of the vanilla 26.2 data (misode/mcmeta, branch 26.2-data):
    tools/vanilla_extra/structures/ancient_city/**.nbt      the city's 57 structure templates

Outputs (under src/main/resources/data/transdimension):
    structure/pink_ancient_city/**.nbt
    worldgen/template_pool/pink_ancient_city/**.json, worldgen/processor_list/pink_ancient_city_*.json
    worldgen/structure/pink_ancient_city.json, worldgen/structure_set/pink_ancient_cities.json
    tags/worldgen/biome/has_structure/pink_ancient_city.json
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
    "minecraft:light_blue_wool": "minecraft:pink_wool",
    "minecraft:light_blue_carpet": "minecraft:pink_carpet",
    "minecraft:blue_wool": "minecraft:magenta_wool",
    "minecraft:blue_carpet": "minecraft:magenta_carpet",
    "minecraft:cyan_wool": "minecraft:pink_wool",
    "minecraft:cyan_carpet": "minecraft:pink_carpet",
}
LOOT = {"minecraft:chests/ancient_city": T + "chests/" + CITY, "minecraft:chests/ancient_city_ice_box": T + "chests/" + CITY + "_ice_box"}

# ---- the ritual, in city_center template coordinates (the same in all three city centres)
# The gate: reinforced deepslate round an opening at x 13, y 18..23, z 11..30. In front of it (towards -x) the plaza.
GATE_OPENING = [(13, y, z) for y in range(18, 24) for z in range(11, 31)]
DAIS_CENTRE = (7, 20)
DAIS_RADIUS = 3.6
DAIS_TOP = 9                # the dais' floor; the stands stand on it at y 10
STANDS = [(10, 20), (9, 22), (7, 23), (5, 22), (4, 20), (5, 18), (7, 17), (9, 18)]
# SculkRitual: the gate's middle is GATE_DISTANCE = 6 blocks the way the crystal faces and GATE_RISE = 4 up: (13, 20, 20).
CRYSTAL = (7, 16, 20)
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
            if name == "minecraft:jigsaw" and str(nbt.get("pool", "")).startswith("minecraft:ancient_city/"):
                nbt["pool"] = String(T + CITY + "/" + str(nbt["pool"])[len("minecraft:ancient_city/"):])
        out[pos] = (name, props, nbt)
    return out


SOFT = ("minecraft:air", "minecraft:cave_air", "minecraft:pink_candle", T + "pink_fire", "minecraft:pink_carpet", "minecraft:gray_carpet",
        "minecraft:magenta_carpet", "minecraft:redstone_wire", "minecraft:jigsaw")


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
    """The dais, its candle stands and crystal, the always-open gate, and the city's keepers."""
    cx, cz = DAIS_CENTRE
    r = math.ceil(DAIS_RADIUS)
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            if (x - cx) ** 2 + (z - cz) ** 2 > DAIS_RADIUS ** 2:
                continue
            centre = abs(x - cx) <= 1 and abs(z - cz) <= 1
            blocks[(x, DAIS_TOP, z)] = (T + "pink_sculk" if centre else "minecraft:polished_deepslate", {}, None)
            for y in range(DAIS_TOP + 1, CRYSTAL[1] + 1):
                blocks[(x, y, z)] = ("minecraft:air", {}, None)
    for (x, z) in STANDS:
        blocks[(x, DAIS_TOP + 1, z)] = (T + "ritual_pedestal", {"candle": "false"}, None)
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
    """Vanilla's weathering, much lighter: 0.5% of blocks knocked out (was 5%), 6% of bricks cracked (was 30%),
    wall slabs knocked out 5% of the time (was 30%), and lanterns kept."""
    for kind in ("generic", "start", "walls"):
        lists = vanilla(f"processor_list/ancient_city_{kind}_degradation.json")
        out = []
        for p in lists["processors"]:
            if p["processor_type"] == "minecraft:block_rot":
                p = {**p, "integrity": 0.995}
            elif p["processor_type"] == "minecraft:rule":
                rules = []
                for rule in p["rules"]:
                    block = rule["input_predicate"].get("block")
                    if block == "minecraft:soul_lantern":
                        continue
                    probability = 0.05 if block == "minecraft:deepslate_tile_slab" else 0.06
                    rules.append({**rule, "input_predicate": {**rule["input_predicate"], "probability": probability}})
                p = {**p, "rules": rules}
            out.append(p)
        write(os.path.join(DATA, NS, "worldgen", "processor_list", f"{CITY}_{kind}_degradation.json"), {"processors": out})


def generate_structure():
    s = vanilla("structure/ancient_city.json")
    s["biomes"] = f"#{T}has_structure/{CITY}"
    s["start_pool"] = T + CITY + "/city_center"
    # Monsters stay out of the city (as in vanilla), but fairies and sculk people may wander in.
    s["spawn_overrides"] = {k: v for k, v in s["spawn_overrides"].items() if k not in ("ambient", "creature")}
    write(os.path.join(DATA, NS, "worldgen", "structure", f"{CITY}.json"), s)
    write(os.path.join(DATA, NS, "worldgen", "structure_set", "pink_ancient_cities.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 20083233, "separation": 8, "spacing": 24},
        "structures": [{"structure": T + CITY, "weight": 1}]})
    write(os.path.join(DATA, NS, "tags", "worldgen", "biome", "has_structure", f"{CITY}.json"), {"values": [T + "pink_deep_dark"]})


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
    # Ritual Candles for the circle: most chests have one or two (a city has plenty of chests for its eight stands).
    table["pools"].append({"rolls": 1.0, "entries": [
        {"type": "minecraft:empty", "weight": 3},
        {"type": "minecraft:item", "name": T + "ritual_candle", "weight": 7, "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0}, "add": False}]}]})
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
    generate_loot()
    print(f"Pink ancient city: {n} templates")


if __name__ == "__main__":
    main()
