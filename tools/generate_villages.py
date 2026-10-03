#!/usr/bin/env python3
"""
Builds the Trans Village jigsaw structure from Mojang's plains village (26.2 templates).

Inputs, copied out of the vanilla 26.2 data:
    tools/vanilla_extra/structures/village/{plains,common}/**.nbt   structure templates
    tools/vanilla_extra/templates/worldgen/plains_pools/*.json      plains template pools

Every vanilla piece is rebuilt with trans blocks (planks, logs, cobblestone, glass, beds, wool...), and:
  * fence + pressure plate tables become Trans Tables (with a potted Pride Blossom or a lantern on top),
  * the stair "chairs" next to them become Trans Chairs,
  * the bigger rooms get a trans flag rug,
  * both butcher shops become bakeries (Pride Oven, Trans Cake, a bakery chest),
  * house chests use the trans house loot table, standing torches become Trans Lanterns,
  * village cats are (mostly) Silly Cats.
New pieces: a flag plaza town centre, lantern posts and trans flag banner posts.

Villages are placed by a random_spread structure set whose separation keeps the centres of two villages at
least 176 blocks apart, more than twice the 80 block radius a village may grow to, so villages can never run
into each other. Inside a village, jigsaw placement keeps pieces from overlapping.

Outputs (under src/main/resources/data):
    transdimension/structure/village/trans/**.nbt
    transdimension/worldgen/template_pool/village/trans/*.json
    transdimension/worldgen/processor_list/{street_trans,trans_weathering}.json
    transdimension/worldgen/placed_feature/village_flowers.json
    transdimension/worldgen/structure/trans_village.json
    transdimension/worldgen/structure_set/trans_villages.json
    transdimension/tags/worldgen/biome/has_structure/trans_village.json
    minecraft/tags/worldgen/structure/village.json

    python3 tools/generate_villages.py      (needs: pip install nbtlib)
"""
import copy
import gzip
import io
import json
import os
import shutil

import nbtlib
from nbtlib import Byte, Compound, Double, Float, Int, List, Short, String

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
SRC = os.path.join(HERE, "vanilla_extra", "structures", "village")
POOLS_SRC = os.path.join(HERE, "vanilla_extra", "templates", "worldgen", "plains_pools")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "transdimension"
T = NS + ":"
OUT_STRUCT = os.path.join(DATA, NS, "structure", "village", "trans")
WG = os.path.join(DATA, NS, "worldgen")
POOL = T + "village/trans/"

LANTERN = (T + "trans_lantern", {"hanging": "false", "waterlogged": "false"})
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}

# Plain block swaps; block-state properties carry over unchanged.
RENAME = {
    "minecraft:oak_door": T + "trans_door",
    "minecraft:oak_fence": T + "trans_fence",
    "minecraft:oak_fence_gate": T + "trans_fence_gate",
    "minecraft:oak_leaves": T + "trans_leaves",
    "minecraft:oak_log": T + "trans_log",
    "minecraft:oak_planks": T + "trans_planks",
    "minecraft:oak_pressure_plate": T + "trans_pressure_plate",
    "minecraft:oak_slab": T + "trans_slab",
    "minecraft:oak_stairs": T + "trans_stairs",
    "minecraft:oak_trapdoor": T + "trans_trapdoor",
    "minecraft:stripped_oak_log": T + "stripped_trans_log",
    "minecraft:stripped_oak_wood": T + "stripped_trans_wood",
    "minecraft:cobblestone": T + "trans_cobblestone",
    "minecraft:mossy_cobblestone": T + "trans_cobblestone",
    "minecraft:cobblestone_slab": T + "trans_cobblestone_slab",
    "minecraft:cobblestone_stairs": T + "trans_cobblestone_stairs",
    "minecraft:cobblestone_wall": T + "trans_cobblestone_wall",
    "minecraft:dirt": T + "trans_dirt",
    "minecraft:grass_block": T + "trans_grass_block",
    # Village roads become a pastel brick road (see street_trans for the cracked/grassy variation).
    "minecraft:dirt_path": T + "trans_stone_bricks",
    "minecraft:bricks": T + "trans_stone_bricks",
    "minecraft:smooth_stone": T + "trans_stone",
    "minecraft:smooth_stone_slab": T + "trans_stone_slab",
    "minecraft:white_bed": "minecraft:pink_bed",
    "minecraft:yellow_bed": "minecraft:light_blue_bed",
    "minecraft:white_carpet": T + "trans_carpet",
    "minecraft:green_carpet": "minecraft:pink_carpet",
    "minecraft:yellow_carpet": "minecraft:light_blue_carpet",
    "minecraft:white_wool": "minecraft:pink_wool",
    "minecraft:yellow_wool": "minecraft:light_blue_wool",
    "minecraft:white_terracotta": T + "cut_trans_sandstone",
    "minecraft:terracotta": "minecraft:light_blue_terracotta",
    "minecraft:white_stained_glass_pane": T + "trans_pink_stained_glass_pane",
    "minecraft:yellow_stained_glass_pane": T + "trans_blue_stained_glass_pane",
    "minecraft:dandelion": T + "pride_blossom",
    "minecraft:poppy": T + "pride_blossom",
    "minecraft:oxeye_daisy": T + "pride_blossom",
    "minecraft:potted_dandelion": T + "potted_pride_blossom",
}

# Each house gets one kind of window glass, so a street mixes flag, pink and blue windows.
WINDOW_GLASS = [T + "trans_stained_glass_pane", T + "trans_pink_stained_glass_pane",
                T + "trans_stained_glass_pane", T + "trans_blue_stained_glass_pane"]

LOOT = {"minecraft:chests/village/village_plains_house": T + "chests/trans_house"}

# Jigsaw pools that point at our own pools; the remaining vanilla "common" pools (farm animals, iron
# golem, sheep) are used as they are.
POOL_MAP = {
    "minecraft:village/plains/town_centers": POOL + "town_centers",
    "minecraft:village/plains/streets": POOL + "streets",
    "minecraft:village/plains/houses": POOL + "houses",
    "minecraft:village/plains/terminators": POOL + "terminators",
    "minecraft:village/plains/decor": POOL + "decor",
    "minecraft:village/plains/trees": POOL + "trees",
    "minecraft:village/plains/villagers": POOL + "villagers",
    "minecraft:village/common/cats": POOL + "cats",
    "minecraft:village/common/well_bottoms": POOL + "well_bottoms",
}

PROCESSOR_MAP = {
    "minecraft:mossify_10_percent": T + "trans_weathering",
    "minecraft:mossify_20_percent": T + "trans_weathering",
    "minecraft:mossify_70_percent": T + "trans_weathering",
    "minecraft:street_plains": T + "street_trans",
}

# Rooms in these pieces get a rug; pens, farms, stables and plazas don't.
RUG_PIECES = ("house", "butcher_shop", "tool_smith", "cottage", "tannery", "cartographer", "library", "temple",
              "weaponsmith")
RUG_FLOORS = {T + "trans_planks", T + "trans_cobblestone", T + "trans_stone", T + "stripped_trans_log",
              T + "trans_log", T + "stripped_trans_wood"}


def chest(loot, facing):
    return ("minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
            Compound({"LootTable": String(loot), "id": String("minecraft:chest")}))


# Extra furniture, by vanilla template and position (all of these are air in the vanilla piece).
EXTRAS = {
    "plains/houses/plains_butcher_shop_1": {
        (7, 2, 2): (T + "trans_cake", {"bites": "0"}, None),       # on the counter
        (7, 2, 3): LANTERN + (None,),
        (8, 1, 2): chest(T + "chests/trans_bakery", "west"),       # behind the counter
    },
    "plains/houses/plains_butcher_shop_2": {
        (4, 2, 4): (T + "trans_cake", {"bites": "0"}, None),
        (7, 1, 4): chest(T + "chests/trans_bakery", "north"),
    },
}


# ============================================================================================ templates
def parse_state(text):
    if "[" not in text:
        return text, {}
    name, rest = text.split("[", 1)
    props = dict(kv.split("=", 1) for kv in rest.rstrip("]").split(",") if kv)
    return name, props


def state_text(name, props):
    return name + ("[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]" if props else "")


def convert(name, props, glass):
    """Returns the trans version of a vanilla block state (name, props) and whether its block entity stays."""
    if name == "minecraft:glass_pane":
        return glass, props, True
    if name == "minecraft:smoker":
        return T + "pride_oven", {"facing": props["facing"]}, False
    if name == "minecraft:torch":
        return LANTERN[0], dict(LANTERN[1]), True
    return RENAME.get(name, name), props, True


class Template:
    def __init__(self, rel):
        self.rel = rel
        self.file = nbtlib.load(os.path.join(SRC, rel + ".nbt"))
        palette = [(str(e["Name"]), {str(k): str(v) for k, v in e.get("Properties", {}).items()})
                   for e in self.file["palette"]]
        self.order = []
        self.blocks = {}
        for b in self.file["blocks"]:
            pos = tuple(int(v) for v in b["pos"])
            name, props = palette[int(b["state"])]
            self.order.append(pos)
            self.blocks[pos] = [name, dict(props), b.get("nbt")]
        self.size = [int(v) for v in self.file["size"]]

    def name_at(self, pos):
        b = self.blocks.get(pos)
        return b[0] if b else None

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
        self.file["palette"] = List[Compound](palette)
        self.file["blocks"] = List[Compound](blocks)
        self.file["size"] = List[Int]([Int(c) for c in self.size])
        path = os.path.join(OUT_STRUCT, out_rel + ".nbt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        # Gzip with a fixed timestamp so that rerunning the script doesn't change every file.
        raw = io.BytesIO()
        self.file.write(raw, "big")
        with open(path, "wb") as out, gzip.GzipFile(filename="", mode="wb", fileobj=out, mtime=0) as gz:
            gz.write(raw.getvalue())


def add_furniture(t):
    """Fence + pressure plate tables -> Trans Tables; the stair chairs around them -> Trans Chairs."""
    tables = [p for p, b in t.blocks.items()
              if b[0] == "minecraft:oak_fence" and t.name_at((p[0], p[1] + 1, p[2])) == "minecraft:oak_pressure_plate"]
    for x, y, z in tables:
        chairs = 0
        for d, (dx, dz) in DIRS.items():
            n = (x + dx, y, z + dz)
            b = t.blocks.get(n)
            # A vanilla chair is a bottom stair with its back turned to the table.
            if b and b[0] == "minecraft:oak_stairs" and b[1].get("half") == "bottom" \
                    and b[1].get("shape") == "straight" and b[1].get("facing") == d:
                t.put(n, T + "trans_chair", {"facing": OPPOSITE[d]})
                chairs += 1
        t.put((x, y, z), T + "trans_table", {"facing": "north"})
        # Dining tables get flowers, bedside tables and desks a lantern.
        if chairs:
            t.put((x, y + 1, z), T + "potted_pride_blossom")
        else:
            t.put((x, y + 1, z), *LANTERN)
    return len(tables)


def lay_rug(t):
    """Puts a trans flag rug (up to 3x3) on the biggest open patch of indoor floor."""
    sx, sy, sz = t.size
    jigsaw_columns = {(p[0], p[2]) for p, b in t.blocks.items() if b[0] == "minecraft:jigsaw"}

    def solid(pos):
        n = t.name_at(pos)
        return n is not None and n not in ("minecraft:air", "minecraft:structure_void")

    def enclosed(x, y, z):
        for dx, dz in DIRS.values():
            cx, cz = x, z
            while True:
                cx += dx
                cz += dz
                if not (0 <= cx < sx and 0 <= cz < sz) or t.name_at((cx, y, cz)) is None:
                    return False
                if solid((cx, y, cz)):
                    break
        return True

    def free(x, y, z):
        return (t.name_at((x, y, z)) == "minecraft:air" and t.name_at((x, y - 1, z)) in RUG_FLOORS
                and (x, z) not in jigsaw_columns
                and any(solid((x, yy, z)) for yy in range(y + 2, min(sy, y + 8)))
                and enclosed(x, y, z))

    best = None
    for y in range(1, sy):
        cells = [[free(x, y, z) for z in range(sz)] for x in range(sx)]
        for x0 in range(sx):
            for z0 in range(sz):
                for w in range(2, 4):
                    for h in range(2, 4):
                        if x0 + w > sx or z0 + h > sz:
                            continue
                        if all(cells[x][z] for x in range(x0, x0 + w) for z in range(z0, z0 + h)):
                            score = (w * h, -y)
                            if best is None or score > best[0]:
                                best = (score, x0, y, z0, w, h)
    if best is None:
        return 0
    _, x0, y, z0, w, h = best
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + h):
            t.put((x, y, z), T + "trans_carpet")
    return w * h


def trans_piece(rel, glass):
    t = Template(rel)
    furniture = add_furniture(t)
    for pos, extra in EXTRAS.get(rel, {}).items():
        if t.name_at(pos) != "minecraft:air":
            raise SystemExit(f"{rel}: {pos} isn't air ({t.name_at(pos)})")
        t.put(pos, *extra)
    for pos in t.order:
        b = t.blocks[pos]
        name, props, keep_nbt = convert(b[0], b[1], glass)
        nbt = b[2] if keep_nbt else None
        if nbt is not None and name == "minecraft:jigsaw":
            nbt = copy.deepcopy(nbt)
            nbt["pool"] = String(POOL_MAP.get(str(nbt["pool"]), str(nbt["pool"])))
            fname, fprops, _ = convert(*parse_state(str(nbt["final_state"])), glass)
            nbt["final_state"] = String(state_text(fname, fprops))
        elif nbt is not None and "LootTable" in nbt:
            nbt = copy.deepcopy(nbt)
            nbt["LootTable"] = String(LOOT.get(str(nbt["LootTable"]), str(nbt["LootTable"])))
        t.blocks[pos] = [name, props, nbt]
    rug = lay_rug(t) if any(k in rel.split("/")[-1] for k in RUG_PIECES) else 0
    return t, furniture, rug


def out_name(rel):
    parts = rel.split("/")[1:]
    parts[-1] = parts[-1].replace("plains_", "trans_").replace("butcher_shop", "bakery")
    return "/".join(parts)


def jigsaw_nbt(final_state, pool="minecraft:empty"):
    return Compound({"joint": String("rollable"), "final_state": String(final_state), "name": String("minecraft:bottom"),
                     "pool": String(pool), "id": String("minecraft:jigsaw"), "target": String("minecraft:bottom")})


def new_post(fences, top):
    """A 1x1 decor post: `fences` fences (the first one is the jigsaw) topped by `top` (name, props, nbt)."""
    t = Template("plains/plains_lamp_1")
    t.order, t.blocks = [], {}
    t.put((0, 0, 0), "minecraft:jigsaw", {"orientation": "down_south"}, jigsaw_nbt(T + "trans_fence"))
    for y in range(1, fences):
        t.put((0, y, 0), T + "trans_fence")
    t.put((0, fences, 0), *top)
    t.size = [1, fences + 1, 1]
    t.file["entities"] = List[Compound]([])
    return t


def flag_plaza():
    """The fountain with its spout swapped for a tall flagpole flying a wool trans flag."""
    t, _, _ = trans_piece("plains/town_centers/plains_fountain_01", WINDOW_GLASS[0])
    for pos in [(4, 2, 3), (3, 2, 4), (5, 2, 4), (4, 2, 5), (4, 3, 3), (3, 3, 4), (5, 3, 4), (4, 3, 5)]:
        if t.name_at(pos) == "minecraft:water":
            t.put(pos, "minecraft:air")
    t.put((4, 2, 4), T + "chiseled_trans_stone_bricks")
    stripes = {11: "light_blue", 10: "pink", 9: "white", 8: "pink", 7: "light_blue"}
    for y in range(3, 12):
        east = "true" if y in stripes else "false"
        t.put((4, y, 4), T + "trans_fence", {"east": east, "north": "false", "south": "false", "west": "false",
                                             "waterlogged": "false"})
    t.put((4, 12, 4), *LANTERN)
    for y, colour in stripes.items():
        for x in range(5, 9):
            t.put((x, y, 4), f"minecraft:{colour}_wool")
    t.size = [t.size[0], 13, t.size[2]]
    return t


def silly_cat(blep):
    t = Template("common/animals/cat_tabby")
    src = t.file["entities"][0]
    cat = Compound({
        "id": String(T + "silly_cat"),
        "Pos": src["nbt"]["Pos"],
        "Motion": List[Double]([Double(0.0), Double(0.0), Double(0.0)]),
        "Rotation": src["nbt"]["Rotation"],
        "Health": Float(10.0),
        "PersistenceRequired": Byte(1),
        "OnGround": Byte(1),
        "Air": Short(300),
        "Fire": Short(-1),
        "blep": Byte(1 if blep else 0),
        "lick_cooldown": Int(200),
    })
    t.file["entities"] = List[Compound]([Compound({"pos": src["pos"], "blockPos": src["blockPos"], "nbt": cat})])
    return t


# ============================================================================================ pools etc.
def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def single(location, processors=None, projection="rigid"):
    return {"element_type": "minecraft:legacy_single_pool_element", "location": location,
            "processors": processors if processors is not None else {"processors": []}, "projection": projection}


def feature(placed):
    return {"element_type": "minecraft:feature_pool_element", "feature": placed, "projection": "rigid"}


EMPTY = {"element_type": "minecraft:empty_pool_element"}


def pool(name, elements, fallback="minecraft:empty"):
    write(os.path.join(WG, "template_pool", "village", "trans", name + ".json"),
          {"elements": [{"element": e, "weight": w} for e, w in elements], "fallback": fallback})


def converted_pool(name, weights=None):
    """Copies a vanilla plains pool, pointing at our pieces and processors and dropping the zombie village."""
    src = json.load(open(os.path.join(POOLS_SRC, name + ".json"), encoding="utf-8"))
    elements = []
    for e in src["elements"]:
        el = copy.deepcopy(e["element"])
        loc = el.get("location", "")
        if "/zombie/" in loc:
            continue
        if loc:
            short = loc.split(":", 1)[1].split("/", 1)[1]           # plains/houses/plains_small_house_1
            el["location"] = T + "village/trans/" + out_name(short)
        if isinstance(el.get("processors"), str):
            el["processors"] = PROCESSOR_MAP.get(el["processors"], el["processors"])
        if el.get("element_type") == "minecraft:feature_pool_element":
            el["feature"] = {"minecraft:oak": T + "trans_tree_checked",
                             "minecraft:flower_plain": T + "village_flowers"}.get(el["feature"], el["feature"])
        weight = (weights or {}).get(el.get("location"), e["weight"])
        elements.append((el, weight))
    fallback = POOL_MAP.get(src["fallback"], src["fallback"])
    return elements, fallback


def rule(input_block, output, probability=None, location=None):
    inp = {"block": input_block, "predicate_type": "minecraft:block_match"}
    if probability is not None:
        inp = {"block": input_block, "predicate_type": "minecraft:random_block_match", "probability": probability}
    loc = {"block": location, "predicate_type": "minecraft:block_match"} if location else {"predicate_type": "minecraft:always_true"}
    return {"input_predicate": inp, "location_predicate": loc, "output_state": output}


def processor_lists():
    water = {"Name": "minecraft:water", "Properties": {"level": "0"}}
    write(os.path.join(WG, "processor_list", "street_trans.json"), {"processors": [{
        "processor_type": "minecraft:rule", "rules": [
            rule(T + "trans_stone_bricks", {"Name": T + "trans_planks"}, location="minecraft:water"),
            rule(T + "trans_stone_bricks", {"Name": T + "cracked_trans_stone_bricks"}, probability=0.12),
            rule(T + "trans_stone_bricks", {"Name": T + "trans_grass_block", "Properties": {"snowy": "false"}},
                 probability=0.06),
            rule(T + "trans_grass_block", water, location="minecraft:water"),
            rule(T + "trans_dirt", water, location="minecraft:water"),
        ]}]})
    write(os.path.join(WG, "processor_list", "trans_weathering.json"), {"processors": [{
        "processor_type": "minecraft:rule", "rules": [
            rule(T + "trans_stone_bricks", {"Name": T + "cracked_trans_stone_bricks"}, probability=0.1),
        ]}]})


def structure_files():
    write(os.path.join(WG, "placed_feature", "village_flowers.json"), {
        "feature": T + "trans_flowers",
        "placement": [
            {"type": "minecraft:count", "count": 48},
            {"type": "minecraft:random_offset",
             "xz_spread": {"type": "minecraft:trapezoid", "max": 6, "min": -6, "plateau": 0},
             "y_spread": {"type": "minecraft:trapezoid", "max": 2, "min": -2, "plateau": 0}},
            {"type": "minecraft:block_predicate_filter",
             "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}},
        ]})
    write(os.path.join(WG, "structure", "trans_village.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/trans_village",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "size": 6,
        "spawn_overrides": {},
        "start_height": {"absolute": 0},
        "start_pool": POOL + "town_centers",
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "use_expansion_hack": True,
    })
    # One village per 24x24 chunk cell, never closer than 11 chunks (176 blocks) to the next one.
    write(os.path.join(WG, "structure_set", "trans_villages.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 19472013, "separation": 10, "spacing": 24},
        "structures": [{"structure": T + "trans_village", "weight": 1}],
    })
    write(os.path.join(DATA, NS, "tags", "worldgen", "biome", "has_structure", "trans_village.json"), {
        "values": [T + "trans_meadow", T + "frosted_fields", T + "sugar_dunes"]})
    write(os.path.join(DATA, "minecraft", "tags", "worldgen", "structure", "village.json"), {
        "replace": False, "values": [T + "trans_village"]})


def main():
    if os.path.isdir(OUT_STRUCT):
        shutil.rmtree(OUT_STRUCT)
    old_pools = os.path.join(WG, "template_pool", "village", "trans")
    if os.path.isdir(old_pools):
        shutil.rmtree(old_pools)

    rels = []
    for root, _, files in os.walk(os.path.join(SRC, "plains")):
        for f in files:
            rels.append(os.path.relpath(os.path.join(root, f), SRC)[:-4])
    rels = sorted(r for r in rels if "/zombie/" not in r) + ["common/well_bottom"]

    furniture = rugs = 0
    for i, rel in enumerate(rels):
        t, f, r = trans_piece(rel, WINDOW_GLASS[i % len(WINDOW_GLASS)])
        furniture += f
        rugs += 1 if r else 0
        t.save(out_name(rel))
    flag_plaza().save("town_centers/trans_flag_plaza")
    new_post(3, LANTERN + (None,)).save("trans_lantern_post_1")
    banner = Compound({"id": String("minecraft:banner"), "patterns": List[Compound]([
        Compound({"color": String("light_blue"), "pattern": String("minecraft:stripe_top")}),
        Compound({"color": String("light_blue"), "pattern": String("minecraft:stripe_bottom")}),
        Compound({"color": String("white"), "pattern": String("minecraft:stripe_middle")}),
    ])})
    new_post(2, ("minecraft:pink_banner", {"rotation": "0"}, banner)).save("trans_banner_post_1")
    silly_cat(False).save("animals/silly_cat")
    silly_cat(True).save("animals/silly_cat_blep")

    elements, fallback = converted_pool("town_centers")
    pool("town_centers", elements + [(single(POOL + "town_centers/trans_flag_plaza", T + "trans_weathering"), 100)], fallback)
    elements, fallback = converted_pool("houses", {POOL + "houses/trans_bakery_1": 3, POOL + "houses/trans_bakery_2": 3})
    pool("houses", elements, fallback)
    for name in ("streets", "terminators", "villagers"):
        pool(name, *converted_pool(name))
    pool("decor", [
        (single(POOL + "trans_lamp_1"), 2),
        (single(POOL + "trans_lantern_post_1"), 2),
        (single(POOL + "trans_banner_post_1"), 1),
        (feature(T + "trans_tree_checked"), 1),
        (feature(T + "village_flowers"), 1),
        (feature("minecraft:pile_hay"), 1),
        (EMPTY, 2),
    ])
    pool("trees", [(feature(T + "trans_tree_checked"), 1)])
    pool("cats", [
        (single(POOL + "animals/silly_cat"), 4),
        (single(POOL + "animals/silly_cat_blep"), 2),
        (single("minecraft:village/common/animals/cat_tabby"), 1),
        (single("minecraft:village/common/animals/cat_calico"), 1),
        (EMPTY, 6),
    ])
    pool("well_bottoms", [(single(POOL + "well_bottom"), 1)])
    processor_lists()
    structure_files()
    print(f"Trans village: {len(rels) + 5} pieces, {furniture} tables furnished, {rugs} rugs.")


if __name__ == "__main__":
    main()
