#!/usr/bin/env python3
"""
Builds the Trans Camp: Minecraft 26.3's abandoned camp (its cherry grove version), brought back to 26.2 and transified.

Inputs, copied out of the vanilla 26.3 data (misode/mcmeta, branch 26.3-data):
    tools/vanilla_extra/structures/abandoned_camp/{tent,camp}/**.nbt   the camp's structure templates

26.3 saves templates in a newer format (palette entries use "id"/"properties" instead of "Name"/"Properties"); the
templates are rewritten in 26.2's format with 26.2's data version. Blocks 26.2 doesn't have are swapped:
  * the tents' white wool stairs become our wool stairs in the flag's colours, by row: light blue along the bottom,
    then pink, then white up to the ridge (so a tent's cross-section reads blue, pink, white, pink, blue),
  * straw beds become trans beds,
  * the cushions round the campfires (entities in 26.3) become our cushion blocks: pink, light blue or white.
Everything else is transified like the trans village: cherry wood becomes trans wood, oak becomes sky wood, dirt paths
become trans dirt paths, and grass, dirt, stone, cobblestone and gravel their trans versions. The camp's chests and
barrels get trans loot tables (written here too) with 26.3's items swapped for 26.2 ones and a few trans treats.

A camp is a tent (the start piece) with one campsite joined to it; trees grow where the tents ask for them.

Outputs (under src/main/resources/data/transdimension):
    structure/trans_camp/{tent,camp}/*.nbt
    worldgen/template_pool/trans_camp/*.json
    worldgen/structure/trans_camp.json
    worldgen/structure_set/trans_camps.json
    tags/worldgen/biome/has_structure/trans_camp.json
    loot_table/chests/trans_camp_{common,secret,barrel}.json

    python3 tools/generate_camps.py      (needs: pip install nbtlib)
"""
import gzip
import io
import json
import os
import shutil

import nbtlib
from nbtlib import Compound, Int, List, String

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
SRC = os.path.join(HERE, "vanilla_extra", "structures", "abandoned_camp")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "transdimension"
T = NS + ":"
OUT_STRUCT = os.path.join(DATA, NS, "structure", "trans_camp")
WG = os.path.join(DATA, NS, "worldgen")
POOL = T + "trans_camp/"
DATA_VERSION = 4903  # 26.2

# Plain block swaps; block-state properties carry over unchanged.
RENAME = {
    "minecraft:cherry_log": T + "trans_log",
    "minecraft:cherry_leaves": T + "trans_leaves",
    "minecraft:cherry_fence": T + "trans_fence",
    "minecraft:cherry_sapling": T + "trans_sapling",
    "minecraft:oak_fence": T + "sky_fence",
    "minecraft:oak_planks": T + "sky_planks",
    "minecraft:grass_block": T + "trans_grass_block",
    "minecraft:dirt": T + "trans_dirt",
    "minecraft:dirt_path": T + "trans_dirt_path",
    "minecraft:short_grass": T + "trans_short_grass",
    "minecraft:tall_grass": T + "tall_trans_grass",
    "minecraft:pink_petals": T + "trans_petals",
    "minecraft:poppy": T + "pride_blossom",
    "minecraft:seagrass": T + "trans_seagrass",
    "minecraft:stone": T + "trans_stone",
    "minecraft:gravel": T + "trans_gravel",
    "minecraft:cobblestone": T + "trans_cobblestone",
    "minecraft:mossy_cobblestone": T + "trans_cobblestone",
    "minecraft:cobblestone_wall": T + "trans_cobblestone_wall",
    "minecraft:mossy_cobblestone_wall": T + "trans_cobblestone_wall",
}
# Tent canvas by row, counted up from the lowest row of wool stairs in the piece.
CANVAS = [T + "light_blue_wool_stairs", T + "pink_wool_stairs", T + "white_wool_stairs"]
# 26.3's sixteen cushion colours, folded into the flag's three.
CUSHIONS = {
    **{c: T + "pink_cushion" for c in ("pink", "magenta", "red", "purple", "orange", "brown")},
    **{c: T + "light_blue_cushion" for c in ("light_blue", "blue", "cyan", "green", "lime")},
    **{c: T + "white_cushion" for c in ("white", "light_gray", "gray", "black", "yellow")},
}
# A cushion entity's yaw (degrees) as the direction it faces.
YAW_FACING = {0: "south", 90: "west", 180: "north", 270: "east"}

LOOT = {
    "minecraft:chests/abandoned_camp_common_chest": T + "chests/trans_camp_common",
    "minecraft:chests/abandoned_camp_secret_chest": T + "chests/trans_camp_secret",
    "minecraft:barrels/abandoned_camp_barrel": T + "chests/trans_camp_barrel",
}
POOL_MAP = {
    "minecraft:abandoned_camp/camp/cherry_grove": POOL + "campsites",
    "minecraft:abandoned_camp/trees/cherry": POOL + "trees",
    "minecraft:abandoned_camp/trees/cherry_bees": POOL + "trees_bees",
}

# Biomes with room for a camp: the woods, meadows and flower fields (not the beaches, oceans, dunes or caves).
BIOMES = ["trans_forest", "trans_meadow", "pearlwood_forest", "bluebell_woods", "heartwood_grove", "twilight_thicket",
          "candy_floss_grove", "pride_flower_fields", "moonlit_meadow", "gumdrop_glade", "crystal_grove", "lavender_marsh",
          "frosted_fields"]


def state_name(name, props):
    return name + ("[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]" if props else "")


class Template:
    """A 26.3 template: blocks by position as [name, props, nbt], and its entities."""

    def __init__(self, rel):
        self.file = nbtlib.load(os.path.join(SRC, rel + ".nbt"))
        palette = [(str(e["id"]), {str(k): str(v) for k, v in e.get("properties", {}).items()}) for e in self.file["palette"]]
        self.order = []
        self.blocks = {}
        for b in self.file["blocks"]:
            pos = tuple(int(v) for v in b["pos"])
            name, props = palette[int(b["state"])]
            self.order.append(pos)
            self.blocks[pos] = [name, dict(props), b.get("nbt")]
        self.entities = list(self.file["entities"])
        self.size = [int(v) for v in self.file["size"]]

    def put(self, pos, name, props=None, nbt=None):
        if pos not in self.blocks:
            self.order.append(pos)
        self.blocks[pos] = [name, dict(props or {}), nbt]

    def save(self, out_rel):
        palette, index, blocks = [], {}, []
        for pos in self.order:
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
            blocks.append(b)
        out = Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(c) for c in self.size]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](blocks),
            "entities": List[Compound]([]),
        })
        path = os.path.join(OUT_STRUCT, out_rel + ".nbt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        # Gzip with a fixed timestamp so that rerunning the script doesn't change every file.
        raw = io.BytesIO()
        nbtlib.File(out).write(raw, "big")
        with open(path, "wb") as f, gzip.GzipFile(filename="", mode="wb", fileobj=f, mtime=0) as gz:
            gz.write(raw.getvalue())


def transify(t):
    """Swaps every block for its trans (and 26.2) version; returns how many cushions were placed."""
    canvas_rows = [pos[1] for pos in t.order if t.blocks[pos][0] == "minecraft:white_wool_stairs"]
    lowest = min(canvas_rows) if canvas_rows else 0
    for pos in t.order:
        name, props, nbt = t.blocks[pos]
        if name == "minecraft:white_wool_stairs":
            name = CANVAS[min(pos[1] - lowest, len(CANVAS) - 1)]
        elif name == "minecraft:straw_bed":
            name, props = T + "trans_bed", {**props, "heart": "none"}
        else:
            name = RENAME.get(name, name)
        if nbt is not None:
            nbt = Compound(nbt)
            if str(nbt.get("id")) == "minecraft:jigsaw":
                nbt["pool"] = String(POOL_MAP.get(str(nbt["pool"]), str(nbt["pool"])))
                final = str(nbt["final_state"])
                nbt["final_state"] = String(RENAME.get(final, final))
            elif "LootTable" in nbt:
                nbt["LootTable"] = String(LOOT[str(nbt["LootTable"])])
        t.blocks[pos] = [name, props, nbt]

    # 26.3's cushions are entities sitting on a block; ours are blocks standing on it.
    cushions = 0
    for e in t.entities:
        data = e["nbt"]
        if str(data["id"]) != "minecraft:cushion":
            raise SystemExit(f"unexpected entity {data['id']}")
        x, y, z = (float(v) for v in e["pos"])
        pos = (int(x), round(y), int(z))
        below = t.blocks.get((pos[0], pos[1] - 1, pos[2]), ["minecraft:air"])[0]
        here = t.blocks.get(pos, ["minecraft:air"])[0]
        if below == "minecraft:air" or here not in ("minecraft:air", T + "trans_short_grass"):
            raise SystemExit(f"no room for a cushion at {pos} ({here} over {below})")
        yaw = round(float(data["Rotation"][0])) % 360
        t.put(pos, CUSHIONS[str(data["color"])], {"facing": YAW_FACING[yaw]})
        cushions += 1
    return cushions


# ============================================================================================ pools, structure, loot
def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def single(location):
    return {"element_type": "minecraft:legacy_single_pool_element", "location": location, "processors": {"processors": []},
            "projection": "rigid"}


def feature(placed):
    return {"element_type": "minecraft:feature_pool_element", "feature": placed, "projection": "rigid"}


def pool(name, elements):
    write(os.path.join(WG, "template_pool", "trans_camp", name + ".json"),
          {"elements": [{"element": e, "weight": 1} for e in elements], "fallback": "minecraft:empty"})


def item(name, lo=1, hi=None, weight=None, functions=()):
    entry = {"type": "minecraft:item", "name": name if ":" in name else T + name}
    count = lo if hi is None else {"type": "minecraft:uniform", "min": lo, "max": hi}
    fns = ([] if count == 1 else [{"function": "minecraft:set_count", "count": count, "add": False}]) + list(functions)
    if fns:
        entry["functions"] = fns
    if weight:
        entry["weight"] = weight
    return entry


def potion(kind):
    return item("minecraft:potion", functions=[{"function": "minecraft:set_potion", "id": f"minecraft:{kind}"}])


def chest_table(name, pools):
    write(os.path.join(DATA, NS, "loot_table", "chests", name + ".json"), {
        "type": "minecraft:chest",
        "pools": [{"rolls": rolls, "entries": entries} for rolls, entries in pools],
        "random_sequence": f"{NS}:chests/{name}"})


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min": lo, "max": hi}


def loot_tables():
    cushions = [item("light_blue_cushion"), item("pink_cushion"), item("white_cushion")]
    # 26.3's common chest, with flag-coloured candles, and cushions or gumdrops instead of the camp maps.
    chest_table("trans_camp_common", [
        (uniform(4, 6), [
            item("minecraft:arrow", 4), item("minecraft:map"), item("minecraft:bone", 2, 4), item("minecraft:cobweb"),
            item("minecraft:compass"), item("minecraft:gunpowder", 2, 4), item("minecraft:fishing_rod"),
            item("minecraft:flint_and_steel"), item("minecraft:glass_bottle", 1, 4), item("minecraft:lead", 1, 3),
            item("minecraft:leather", 1, 4), item("minecraft:bundle"), item("minecraft:rabbit_hide", 1, 4),
            item("minecraft:saddle"), item("minecraft:pink_candle", 1, 3), item("minecraft:light_blue_candle", 1, 3),
            item("gumdrop", 2, 5)]),
        (2, [item("minecraft:bow"), item("minecraft:bucket"), item("minecraft:copper_axe"), item("minecraft:copper_boots"),
             item("minecraft:copper_chestplate"), item("minecraft:copper_leggings"), item("minecraft:copper_spear"),
             item("minecraft:copper_sword"), item("minecraft:spyglass"), item("minecraft:shears")]),
        (1, cushions + [item("trans_wool", 1, 3)]),
    ])
    # 26.3's secret chest: its treasure maps (26.3 items) become trans treasures.
    chest_table("trans_camp_secret", [
        (2, [item("minecraft:diamond"), potion("healing"), potion("leaping"), potion("night_vision"), potion("swiftness"),
             item("trans_crystal")]),
        (uniform(4, 6), [item("minecraft:map"), item("minecraft:copper_ingot", 1, 2), item("minecraft:gold_ingot", 1, 2),
                         item("minecraft:iron_ingot"), item("trans_pearl", 1, 2)]),
        (uniform(0, 1), [item("minecraft:iron_axe"), item("minecraft:iron_boots"), item("minecraft:iron_leggings"),
                         item("minecraft:iron_spear")]),
        # (No Bottled Fairy: those only come from catching a fairy in a bottle.)
        (1, [item("trans_crystal", 1, 2, weight=4), item("minecraft:golden_apple", weight=3)]),
    ])
    # 26.3's barrel: cushions instead of its cushions, hay instead of its straw beds.
    chest_table("trans_camp_barrel", [
        (uniform(4, 8), [
            item("minecraft:arrow", 1, 3), item("minecraft:bone", 2, 4), item("minecraft:bowl", 1, 2), item("minecraft:bread", 1, 3),
            item("minecraft:coal", 2, 4), item("minecraft:cobweb"), item("minecraft:glass_bottle", 1, 3),
            item("minecraft:leather", 1, 3), item("minecraft:rabbit_hide", 1, 4), item("minecraft:string", 1, 2),
            item("minecraft:wheat", 1, 4), item("minecraft:pink_candle", 1, 3), item("minecraft:light_blue_candle", 1, 3),
            *(dict(c, functions=[{"function": "minecraft:set_count", "count": uniform(1, 2), "add": False}]) for c in cushions),
            item("minecraft:hay_block", 1, 2), item("gumdrop", 1, 3)]),
        (1, [item("minecraft:bundle"), item("minecraft:wooden_axe"), item("minecraft:fishing_rod")]),
    ])


def structure_files():
    write(os.path.join(WG, "structure", "trans_camp.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/trans_camp",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 2,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "start_pool": POOL + "tents",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": True,
    })
    # Like 26.3's abandoned camps: one per 34x34 chunk cell, never closer than 8 chunks to the next.
    write(os.path.join(WG, "structure_set", "trans_camps.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 26032026, "separation": 8, "spacing": 34},
        "structures": [{"structure": T + "trans_camp", "weight": 1}],
    })
    write(os.path.join(DATA, NS, "tags", "worldgen", "biome", "has_structure", "trans_camp.json"),
          {"values": [T + b for b in BIOMES]})


def main():
    for old in (OUT_STRUCT, os.path.join(WG, "template_pool", "trans_camp")):
        if os.path.isdir(old):
            shutil.rmtree(old)
    tents, campsites, cushions = [], [], 0
    for folder, out, names in (("tent/cherry_grove", "tent", tents), ("camp/default", "camp", campsites),
                               ("camp/cherry_grove", "camp", campsites)):
        for fn in sorted(os.listdir(os.path.join(SRC, folder)), key=lambda f: (len(f), f)):
            t = Template(f"{folder}/{fn[:-4]}")
            cushions += transify(t)
            short = fn[:-4].replace("campsite_default_", "").replace("campsite_cherry_grove_", "grove_").replace("tent_cherry_grove_", "tent_")
            t.save(f"{out}/{short}")
            names.append(f"{POOL}{out}/{short}")
    pool("tents", [single(n) for n in tents])
    pool("campsites", [single(n) for n in campsites])
    pool("trees", [feature(T + "trans_cherry_tree_checked")])
    pool("trees_bees", [feature(T + "trans_tree_bees_checked")])
    structure_files()
    loot_tables()
    print(f"Trans camp: {len(tents)} tents, {len(campsites)} campsites, {cushions} cushions.")


if __name__ == "__main__":
    main()
