#!/usr/bin/env python3
"""
Sanity-checks the mod's resources without launching the game:

* every block/item registered in Java has its blockstate, item definition, name and (for blocks) loot table,
* every model, parent, texture, item model, loot entry, recipe ingredient and tag entry points at
  something that exists (in this mod, or in vanilla 26.2 when a vanilla registry dump is available),
* every JSON file parses.

    python3 tools/validate_resources.py [path/to/mcmeta-summary/registries/data.json]

The optional argument is misode/mcmeta's 26.2 "summary" registries file; with it, vanilla ids are
checked too. Exit code 1 if anything is wrong.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "transdimension")
DATA = os.path.join(RES, "data")
JAVA = os.path.join(ROOT, "src", "main", "java", "dev", "goober", "transdimension")
NS = "transdimension"

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:  # noqa: BLE001
        err(f"bad JSON {os.path.relpath(path, ROOT)}: {e}")
        return None


def walk_json(base):
    for root, _, files in os.walk(base):
        for f in files:
            if f.endswith(".json") or f.endswith(".mcmeta"):
                yield os.path.join(root, f)


# ------------------------------------------------------------------ vanilla registries (optional)
vanilla = {}
if len(sys.argv) > 1 and os.path.exists(sys.argv[1]):
    vanilla = json.load(open(sys.argv[1], encoding="utf-8"))


def vanilla_has(registry, ident):
    if not vanilla:
        return True
    ns, path = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    return ns == "minecraft" and path in set(vanilla.get(registry, []))


# ------------------------------------------------------------------ Java registrations
def java_names():
    blocks, items, no_item = set(), set(), set()
    src = open(os.path.join(JAVA, "registry", "ModBlocks.java"), encoding="utf-8").read()
    for m in re.finditer(r'\b(?:register|stairs|slab|wall|glass|pane|copy|ore|flower|leaves|plush|cushion)\("([a-z0-9_]+)"[,)]', src):
        blocks.add(m.group(1))
    for m in re.finditer(r'\b(?:registerWithoutItem|potted)\("([a-z0-9_]+)"[,)]', src):
        no_item.add(m.group(1))
        blocks.add(m.group(1))
    # Wood families registered through ModBlocks.woodFamily("<prefix>", ...).
    for m in re.finditer(r'\bwoodFamily\("([a-z]+)"', src):
        w = m.group(1)
        blocks.update({f"{w}_log", f"stripped_{w}_log", f"{w}_wood", f"stripped_{w}_wood", f"{w}_planks", f"{w}_stairs", f"{w}_slab",
                       f"{w}_fence", f"{w}_fence_gate", f"{w}_door", f"{w}_trapdoor", f"{w}_button", f"{w}_pressure_plate",
                       f"{w}_sapling", f"potted_{w}_sapling"})
        no_item.add(f"potted_{w}_sapling")
    # Coral colours registered through ModBlocks.coral("<colour>"): fans get their item from fanItem(), wall fans none.
    for m in re.finditer(r'\bcoral\("([a-z]+)"\)', src):
        c = m.group(1)
        for prefix in ("", "dead_"):
            blocks.update({f"{prefix}{c}_coral_block", f"{prefix}{c}_coral", f"{prefix}{c}_coral_fan", f"{prefix}{c}_coral_wall_fan"})
            no_item.add(f"{prefix}{c}_coral_wall_fan")
    items |= blocks - no_item
    src = open(os.path.join(JAVA, "registry", "ModItems.java"), encoding="utf-8").read()
    for m in re.finditer(r'register\("([a-z0-9_]+)"', src):
        items.add(m.group(1))
    return blocks, items, no_item


BLOCKS, ITEMS, NO_ITEM = java_names()


def is_ours(ident):
    return ident.startswith(NS + ":")


def path_of(ident):
    return ident.split(":", 1)[1]


def item_exists(ident):
    if ident.startswith("#"):
        return tag_exists("item", ident[1:])
    if is_ours(ident):
        return path_of(ident) in ITEMS
    return vanilla_has("item", ident)


def block_exists(ident):
    if ident.startswith("#"):
        return tag_exists("block", ident[1:])
    if is_ours(ident):
        return path_of(ident) in BLOCKS
    return vanilla_has("block", ident)


def tag_exists(kind, ident):
    ns, path = ident.split(":", 1)
    if os.path.exists(os.path.join(DATA, ns, "tags", kind, path + ".json")):
        return True
    if ns == "minecraft":
        return vanilla_has(f"tag/{kind}", ident)
    return False


# ------------------------------------------------------------------ assets
def model_exists(ident):
    ns, path = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    if ns == NS:
        return os.path.exists(os.path.join(ASSETS, "models", path + ".json"))
    return vanilla_has("model", f"minecraft:{path}") or path.startswith("builtin/")


def texture_exists(ident):
    if isinstance(ident, dict):
        ident = ident.get("sprite", "")
    if ident.startswith("#"):
        return True
    ns, path = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    if ns == NS:
        return os.path.exists(os.path.join(ASSETS, "textures", path + ".png"))
    return vanilla_has("texture", f"minecraft:{path}")


def check_models_in(obj, where):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == "model" and isinstance(v, str):
                if not model_exists(v):
                    err(f"{where}: missing model {v}")
            else:
                check_models_in(v, where)
    elif isinstance(obj, list):
        for v in obj:
            check_models_in(v, where)


for f in walk_json(os.path.join(ASSETS, "blockstates")):
    bs = load(f)
    if bs:
        check_models_in(bs, os.path.relpath(f, ROOT))

for f in walk_json(os.path.join(ASSETS, "models")):
    m = load(f)
    if not m:
        continue
    where = os.path.relpath(f, ROOT)
    if "parent" in m and not model_exists(m["parent"]):
        err(f"{where}: missing parent {m['parent']}")
    for key, tex in m.get("textures", {}).items():
        if not texture_exists(tex):
            err(f"{where}: missing texture {key}={tex}")

for f in walk_json(os.path.join(ASSETS, "items")):
    d = load(f)
    if d:
        check_models_in(d, os.path.relpath(f, ROOT))

for f in walk_json(os.path.join(ASSETS, "textures")):
    load(f)  # mcmeta must parse

lang = load(os.path.join(ASSETS, "lang", "en_us.json")) or {}
for b in BLOCKS:
    if not os.path.exists(os.path.join(ASSETS, "blockstates", b + ".json")):
        err(f"block {b}: no blockstate")
    if f"block.{NS}.{b}" not in lang:
        err(f"block {b}: no name in en_us.json")
for i in ITEMS:
    if not os.path.exists(os.path.join(ASSETS, "items", i + ".json")):
        err(f"item {i}: no item definition")
    if i not in BLOCKS and f"item.{NS}.{i}" not in lang:
        err(f"item {i}: no name in en_us.json")

# ------------------------------------------------------------------ data
# These have no loot table (Java says so): the cake, the plush marker, the unbreakable Fairy Realm blocks, pink lava, holy
# water and pink fire, and the unbreakable candle ritual blocks and Sky Portal.
for b in BLOCKS - {"trans_cake", "plush_spot", "fairy_portal_frame", "fairy_portal", "fairy_altar", "pink_lava", "holy_water", "pink_fire",
                   "ritual_pedestal", "ritual_crystal", "sky_portal"}:
    if not os.path.exists(os.path.join(DATA, NS, "loot_table", "blocks", b + ".json")):
        err(f"block {b}: no loot table")


def check_loot(obj, where):
    if isinstance(obj, dict):
        if obj.get("type") == "minecraft:item" and "name" in obj and not item_exists(obj["name"]):
            err(f"{where}: loot item {obj['name']} doesn't exist")
        if obj.get("type") == "minecraft:loot_table" and isinstance(obj.get("value"), str):
            pass
        if obj.get("function") == "minecraft:exploration_map" and "destination" in obj:
            # A plain structure tag id: with a '#', 26.2 rejects the whole loot table and its chests stay empty.
            dest = obj["destination"]
            ns, path = dest.split(":", 1) if ":" in dest else ("minecraft", dest)
            if dest.startswith("#"):
                err(f"{where}: exploration map destination {dest} must not start with '#'")
            elif not os.path.exists(os.path.join(DATA, ns, "tags", "worldgen", "structure", path + ".json")) \
                    and not (ns == "minecraft" and vanilla_has("tag/worldgen/structure", dest)):
                err(f"{where}: exploration map destination {dest} isn't a structure tag")
        for v in obj.values():
            check_loot(v, where)
    elif isinstance(obj, list):
        for v in obj:
            check_loot(v, where)


for f in walk_json(os.path.join(DATA, NS, "loot_table")):
    d = load(f)
    if d:
        check_loot(d, os.path.relpath(f, ROOT))


def ingredient_ok(value):
    if isinstance(value, list):
        return all(ingredient_ok(v) for v in value)
    return item_exists(value)


for f in walk_json(os.path.join(DATA, NS, "recipe")):
    r = load(f)
    if not r:
        continue
    where = os.path.relpath(f, ROOT)
    for v in r.get("key", {}).values():
        if not ingredient_ok(v):
            err(f"{where}: unknown ingredient {v}")
    for v in r.get("ingredients", []):
        if not ingredient_ok(v):
            err(f"{where}: unknown ingredient {v}")
    if "ingredient" in r and not ingredient_ok(r["ingredient"]):
        err(f"{where}: unknown ingredient {r['ingredient']}")
    res = r.get("result", {})
    if isinstance(res, dict) and "id" in res and not item_exists(res["id"]):
        err(f"{where}: unknown result {res['id']}")
    if "pattern" in r:
        widths = {len(row) for row in r["pattern"]}
        if len(widths) != 1:
            err(f"{where}: pattern rows have different widths {r['pattern']}")
        used = set("".join(r["pattern"])) - {" "}
        if used - set(r.get("key", {})):
            err(f"{where}: pattern uses undefined keys {used - set(r['key'])}")

for ns in os.listdir(DATA):
    for kind in ("block", "item"):
        base = os.path.join(DATA, ns, "tags", kind)
        for f in walk_json(base):
            t = load(f)
            if not t:
                continue
            for v in t.get("values", []):
                v = v["id"] if isinstance(v, dict) else v
                ok = block_exists(v) if kind == "block" else item_exists(v)
                if not ok:
                    err(f"{os.path.relpath(f, ROOT)}: unknown entry {v}")

# every JSON under data parses
for f in walk_json(DATA):
    load(f)

# ------------------------------------------------------------------ worldgen and other data packs
VANILLA_BLOCKS = {}
blocks_file = os.path.join(os.path.dirname(os.path.dirname(sys.argv[1])), "blocks", "data.json") if len(sys.argv) > 1 else ""
if blocks_file and os.path.exists(blocks_file):
    VANILLA_BLOCKS = json.load(open(blocks_file, encoding="utf-8"))

# Our blocks share their block-state properties with a vanilla "twin".
TWINS = {
    "trans_grass_block": "grass_block", "trans_leaves": "cherry_leaves", "trans_log": "cherry_log", "stripped_trans_log": "cherry_log",
    "trans_wood": "cherry_wood", "stripped_trans_wood": "cherry_wood", "trans_sapling": "cherry_sapling",
    "trans_crystal_cluster": "amethyst_cluster", "trans_lantern": "lantern", "trans_door": "cherry_door",
    "trans_trapdoor": "cherry_trapdoor", "trans_fence": "cherry_fence", "trans_fence_gate": "cherry_fence_gate",
    "trans_button": "cherry_button", "trans_stone_button": "stone_button", "trans_pressure_plate": "cherry_pressure_plate",
    "trans_stone_pressure_plate": "stone_pressure_plate", "trans_stained_glass_pane": "pink_stained_glass_pane",
    "trans_pink_stained_glass_pane": "pink_stained_glass_pane", "trans_blue_stained_glass_pane": "pink_stained_glass_pane",
    "trans_cake": "cake", "pride_oven": "smoker", "trans_chair": "smoker", "trans_table": "smoker",
    "trans_deepslate": "deepslate", "trans_redstone_ore": "redstone_ore", "trans_deepslate_redstone_ore": "redstone_ore",
    "trans_glass_pane": "glass_pane", "trans_petals": "pink_petals", "pride_peony": "peony",
    "pearl_leaves": "cherry_leaves", "sky_leaves": "cherry_leaves", "blush_leaves": "cherry_leaves", "twilight_leaves": "cherry_leaves",
    "trans_bed": "red_bed", "plush_spot": "smoker",
}
TWINS.update({
    "trans_short_grass": "short_grass", "tall_trans_grass": "tall_grass", "trans_fern": "fern", "large_trans_fern": "large_fern",
    "pastel_bush": "bush", "trans_firefly_bush": "firefly_bush", "short_sugar_grass": "short_dry_grass",
    "tall_sugar_grass": "tall_dry_grass", "trans_seagrass": "seagrass", "tall_trans_seagrass": "tall_seagrass",
    "trans_kelp": "kelp", "trans_kelp_plant": "kelp_plant", "trans_lily_pad": "lily_pad",
    "sky_delphinium": "peony", "blush_foxglove": "peony", "pearl_lupine": "peony",
})
# Wood family blocks behave like cherry's (logs and wood have an axis, and so on); saplings like the cherry sapling.
for b in list(BLOCKS):
    for suffix, twin in (("_log", "cherry_log"), ("_wood", "cherry_wood"), ("_planks", "cherry_planks"), ("_fence_gate", "cherry_fence_gate"),
                         ("_fence", "cherry_fence"), ("_door", "cherry_door"), ("_trapdoor", "cherry_trapdoor"), ("_button", "cherry_button"),
                         ("_pressure_plate", "cherry_pressure_plate"), ("_sapling", "cherry_sapling"), ("_leaves", "cherry_leaves"),
                         ("_hedge", "flowering_azalea_leaves")):
        if b.endswith(suffix) and not b.startswith("potted_"):
            TWINS.setdefault(b, twin)
            break
for b in list(BLOCKS):
    for suffix, twin in (("_coral_wall_fan", "tube_coral_wall_fan"), ("_coral_fan", "tube_coral_fan"), ("_coral_block", "tube_coral_block"),
                         ("_coral", "tube_coral")):
        if b.endswith(suffix):
            TWINS.setdefault(b, ("dead_" if b.startswith("dead_") else "") + twin)
            break
# Furniture-like blocks only have a horizontal "facing" (borrowed from the smoker); the bed adds a "heart" property.
FACING_ONLY = {"pride_oven", "trans_chair", "trans_table", "plush_spot", "fairy_portal_frame", "trans_stool", "trans_armchair",
               "light_blue_cushion", "pink_cushion", "white_cushion"}
TWINS.update({b: "smoker" for b in FACING_ONLY if b not in TWINS})
TWINS.update({"trans_lamp": "redstone_lamp", "trans_dirt_path": "dirt_path", "trans_bookshelf": "bookshelf",
              "trans_cactus": "cactus", "dry_sugar_bush": "dead_bush", "trans_sugar_cane": "sugar_cane"})
EXTRA_PROPS = {"trans_bed": {"heart": ["none", "left", "right"]}, "fairy_portal_frame": {"pearl": ["false", "true"]}}
TWINS["fairy_portal_frame"] = "smoker"
TWINS["trans_sea_pickle"] = "sea_pickle"
TWINS["pink_fire"] = "fire"
TWINS.update({"trans_cave_vines": "cave_vines", "trans_cave_vines_plant": "cave_vines_plant"})
# Round 9: pink sculk blocks are vanilla's sculk blocks; the ritual's blocks have their own properties.
TWINS.update({"pink_sculk_sensor": "sculk_sensor", "pink_sculk_shrieker": "sculk_shrieker", "pink_sculk_vein": "sculk_vein",
              "pink_sculk_catalyst": "sculk_catalyst", "sky_portal": "nether_portal", "ritual_pedestal": "stone",
              "ritual_crystal": "smoker"})
FACING_ONLY.add("ritual_crystal")
# Round 11: holy water is a liquid like water (and pink lava like lava).
TWINS.update({"holy_water": "water", "pink_lava": "lava"})
EXTRA_PROPS.update({"ritual_pedestal": {"candle": ["false", "true"]}, "ritual_crystal": {"awake": ["false", "true"]}})
for b in BLOCKS:
    if b.endswith("_plush"):
        TWINS[b] = "smoker"
        FACING_ONLY.add(b)
for b in BLOCKS:
    if b.endswith("_stairs"):
        TWINS.setdefault(b, "oak_stairs")
    elif b.endswith("_slab"):
        TWINS.setdefault(b, "oak_slab")
    elif b.endswith("_wall"):
        TWINS.setdefault(b, "cobblestone_wall")


def block_props(ident):
    ns, path = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    if ns == NS:
        twin = TWINS.get(path)
        if twin is None:
            return {}
        props = dict(VANILLA_BLOCKS.get(twin, [{}])[0])
        if path in FACING_ONLY:
            props = {"facing": props.get("facing", [])}
        props.update(EXTRA_PROPS.get(path, {}))
        return props
    return VANILLA_BLOCKS.get(path, [None])[0]


# Fluid states (a spring feature's "state") only have "falling".
FLUID_STATE_PROPS = {"falling": ["true", "false"]}


def check_states(obj, where):
    if isinstance(obj, dict):
        if obj.get("type") == "minecraft:spring_feature" and isinstance(obj.get("config", {}).get("state"), dict):
            fluid = obj["config"]["state"]
            for k, v in fluid.get("Properties", {}).items():
                if v not in FLUID_STATE_PROPS.get(k, []):
                    err(f"{where}: fluid {fluid.get('Name')} has no property {k}={v}")
            obj = {k: (dict(v, state=None) if k == "config" else v) for k, v in obj.items()}
        if "Name" in obj and isinstance(obj["Name"], str):
            ident = obj["Name"]
            if not block_exists(ident):
                err(f"{where}: unknown block {ident}")
            elif VANILLA_BLOCKS:
                props = block_props(ident)
                for k, v in obj.get("Properties", {}).items():
                    if props is not None and (k not in props or v not in props[k]):
                        err(f"{where}: {ident} has no property {k}={v}")
        for v in obj.values():
            check_states(v, where)
    elif isinstance(obj, list):
        for v in obj:
            check_states(v, where)


def placed_exists(ident):
    ns, path = ident.split(":", 1)
    if ns == NS:
        return os.path.exists(os.path.join(DATA, NS, "worldgen", "placed_feature", path + ".json"))
    return vanilla_has("worldgen/placed_feature", ident)


def configured_exists(ident):
    ns, path = ident.split(":", 1)
    if ns == NS:
        return os.path.exists(os.path.join(DATA, NS, "worldgen", "configured_feature", path + ".json"))
    return vanilla_has("worldgen/configured_feature", ident)


WG = os.path.join(DATA, NS, "worldgen")
for f in walk_json(DATA):
    rel = os.path.relpath(f, ROOT)
    if "/tags/" in f or "/loot_table/" in f or "/recipe/" in f:
        continue
    d = load(f)
    if d is not None:
        check_states(d, rel)

for f in walk_json(os.path.join(WG, "placed_feature")):
    d = load(f)
    if d and isinstance(d.get("feature"), str) and not configured_exists(d["feature"]):
        err(f"{os.path.relpath(f, ROOT)}: unknown configured feature {d['feature']}")

for f in walk_json(os.path.join(WG, "configured_feature")):
    d = load(f)
    if d and d.get("type") == "minecraft:random_selector":
        refs = [d["config"]["default"]] + [x["feature"] for x in d["config"]["features"]]
        for r in refs:
            if isinstance(r, str) and not placed_exists(r):
                err(f"{os.path.relpath(f, ROOT)}: unknown placed feature {r}")

# Our entity ids, from ModEntities (every TransDimension.id("...") there is an entity type).
ENTITIES = set(re.findall(r'TransDimension\.id\("([a-z0-9_]+)"\)',
                          open(os.path.join(JAVA, "registry", "ModEntities.java"), encoding="utf-8").read()))


def entity_exists(ident):
    return path_of(ident) in ENTITIES if is_ours(ident) else vanilla_has("entity_type", ident)


for f in walk_json(os.path.join(WG, "biome")):
    d = load(f)
    if not d:
        continue
    rel = os.path.relpath(f, ROOT)
    if len(d["features"]) != 11:
        err(f"{rel}: needs 11 feature steps")
    for step in d["features"]:
        for feat in step:
            if not placed_exists(feat):
                err(f"{rel}: unknown placed feature {feat}")
    for group in d["spawners"].values():
        for sp in group:
            t = sp["type"]
            if not entity_exists(t):
                err(f"{rel}: unknown entity {t}")
    for part in d["attributes"].get("minecraft:visual/ambient_particles", []):
        ptype = part["particle"]["type"]
        ours_ok = ptype.startswith(NS + ":") and os.path.exists(os.path.join(ASSETS, "particles", ptype.split(":", 1)[1] + ".json"))
        if not ours_ok and not vanilla_has("particle_type", ptype):
            err(f"{rel}: unknown particle {part['particle']['type']}")
    for m in d["attributes"].get("minecraft:audio/background_music", {}).values():
        if not vanilla_has("sound_event", m["sound"]):
            err(f"{rel}: unknown music {m['sound']}")

dim = load(os.path.join(DATA, NS, "dimension", "trans_realm.json"))
if dim:
    for e in dim["generator"]["biome_source"]["biomes"]:
        b = e["biome"]
        if not os.path.exists(os.path.join(WG, "biome", path_of(b) + ".json")):
            err(f"dimension: unknown biome {b}")

# ------------------------------------------------------------------ villages: structures, pools, templates
def ours_or_vanilla(ident, folder, registry, ext=".json"):
    ns, path = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    if ns == NS:
        return os.path.exists(os.path.join(DATA, NS, folder, path + ext))
    return vanilla_has(registry, f"minecraft:{path}")


def pool_exists(ident):
    return ident == "minecraft:empty" or ours_or_vanilla(ident, "worldgen/template_pool", "worldgen/template_pool")


def biome_tag_ok(tag):
    ns, path = tag.split(":", 1)
    return os.path.exists(os.path.join(DATA, ns, "tags", "worldgen", "biome", path + ".json")) \
        or (ns == "minecraft" and vanilla_has("tag/worldgen/biome", tag))


for f in walk_json(os.path.join(WG, "template_pool")):
    d = load(f)
    if not d:
        continue
    rel = os.path.relpath(f, ROOT)
    if not pool_exists(d["fallback"]):
        err(f"{rel}: unknown fallback pool {d['fallback']}")
    elements = [e["element"] for e in d["elements"]]
    for e in list(elements):
        if e["element_type"] == "minecraft:list_pool_element":
            elements += e["elements"]
    for el in elements:
        kind = el["element_type"]
        if kind == "minecraft:list_pool_element":
            continue
        if kind in ("minecraft:single_pool_element", "minecraft:legacy_single_pool_element"):
            if not ours_or_vanilla(el["location"], "structure", "structure", ".nbt"):
                err(f"{rel}: unknown structure template {el['location']}")
            p = el.get("processors")
            if isinstance(p, str) and not ours_or_vanilla(p, "worldgen/processor_list", "worldgen/processor_list"):
                err(f"{rel}: unknown processor list {p}")
        elif kind == "minecraft:feature_pool_element":
            if not placed_exists(el["feature"]):
                err(f"{rel}: unknown placed feature {el['feature']}")
        elif kind != "minecraft:empty_pool_element":
            err(f"{rel}: unexpected element type {kind}")


def check_rule_blocks(obj, where):
    if isinstance(obj, dict):
        if isinstance(obj.get("block"), str) and not block_exists(obj["block"]):
            err(f"{where}: unknown block {obj['block']}")
        for v in obj.values():
            check_rule_blocks(v, where)
    elif isinstance(obj, list):
        for v in obj:
            check_rule_blocks(v, where)


for f in walk_json(os.path.join(WG, "processor_list")):
    d = load(f)
    if d:
        check_rule_blocks(d, os.path.relpath(f, ROOT))

for f in walk_json(os.path.join(WG, "structure")):
    d = load(f)
    if not d:
        continue
    rel = os.path.relpath(f, ROOT)
    if d.get("type") == "minecraft:jigsaw" and not pool_exists(d["start_pool"]):
        err(f"{rel}: unknown start pool {d['start_pool']}")
    if isinstance(d.get("biomes"), str) and d["biomes"].startswith("#") and not biome_tag_ok(d["biomes"][1:]):
        err(f"{rel}: unknown biome tag {d['biomes']}")

for f in walk_json(os.path.join(WG, "structure_set")):
    d = load(f)
    for s in (d or {}).get("structures", []):
        if not ours_or_vanilla(s["structure"], "worldgen/structure", "worldgen/structure"):
            err(f"{os.path.relpath(f, ROOT)}: unknown structure {s['structure']}")

for ns in os.listdir(DATA):
    for f in walk_json(os.path.join(DATA, ns, "tags", "worldgen", "biome")):
        for v in (load(f) or {}).get("values", []):
            if not (biome_tag_ok(v[1:]) if v.startswith("#") else ours_or_vanilla(v, "worldgen/biome", "worldgen/biome")):
                err(f"{os.path.relpath(f, ROOT)}: unknown biome {v}")
    for f in walk_json(os.path.join(DATA, ns, "tags", "worldgen", "structure")):
        for v in (load(f) or {}).get("values", []):
            if not v.startswith("#") and not ours_or_vanilla(v, "worldgen/structure", "worldgen/structure"):
                err(f"{os.path.relpath(f, ROOT)}: unknown structure {v}")

try:
    import nbtlib
except ImportError:
    nbtlib = None
    warnings.append("nbtlib isn't installed (pip install nbtlib): structure templates weren't checked")

if nbtlib:
    for root, _, files in os.walk(os.path.join(DATA, NS, "structure")):
        for fn in files:
            path = os.path.join(root, fn)
            rel = os.path.relpath(path, ROOT)
            nbt = nbtlib.load(path)
            palette = [(str(e["Name"]), {str(k): str(v) for k, v in e.get("Properties", {}).items()})
                       for e in nbt["palette"]]
            for name, props in palette:
                check_states({"Name": name, "Properties": props}, rel)
            size = [int(v) for v in nbt["size"]]
            for b in nbt["blocks"]:
                pos = [int(v) for v in b["pos"]]
                if not all(0 <= c < s for c, s in zip(pos, size)):
                    err(f"{rel}: block at {pos} is outside the template size {size}")
                name = palette[int(b["state"])][0]
                data = b.get("nbt")
                if data is None:
                    continue
                if name == "minecraft:jigsaw":
                    if not pool_exists(str(data["pool"])):
                        err(f"{rel}: jigsaw at {pos} uses unknown pool {data['pool']}")
                    state = str(data["final_state"])
                    fname, _, rest = state.partition("[")
                    fprops = dict(kv.split("=", 1) for kv in rest.rstrip("]").split(",") if kv)
                    check_states({"Name": fname, "Properties": fprops}, f"{rel} jigsaw at {pos}")
                if "LootTable" in data and not ours_or_vanilla(str(data["LootTable"]), "loot_table", "loot_table"):
                    err(f"{rel}: unknown loot table {data['LootTable']}")
            for e in nbt["entities"]:
                if not entity_exists(str(e["nbt"]["id"])):
                    err(f"{rel}: unknown entity {e['nbt']['id']}")

# ------------------------------------------------------------------ sounds.json
for event, spec in (load(os.path.join(ASSETS, "sounds.json")) or {}).items():
    for s in spec["sounds"]:
        name = s if isinstance(s, str) else s["name"]
        is_event = isinstance(s, dict) and s.get("type") == "event"
        ok = vanilla_has("sound_event", name) if is_event else vanilla_has("sound", name)
        if not ok:
            err(f"sounds.json {event}: unknown {'sound event' if is_event else 'sound file'} {name}")

# ------------------------------------------------------------------ advancements
LANG = load(os.path.join(ASSETS, "lang", "en_us.json")) or {}
for f in walk_json(os.path.join(DATA, NS, "advancement")):
    d = load(f) or {}
    rel = os.path.relpath(f, ROOT)
    parent = d.get("parent")
    if parent and not ours_or_vanilla(parent, "advancement", "advancement"):
        err(f"{rel}: unknown parent {parent}")
    display = d.get("display", {})
    if display and not item_exists(display["icon"]["id"]):
        err(f"{rel}: unknown icon {display['icon']['id']}")
    for part in ("title", "description"):
        key = display.get(part, {}).get("translate")
        if key and key not in LANG:
            err(f"{rel}: no translation for {key}")
    for name, c in d.get("criteria", {}).items():
        if not vanilla_has("trigger_type", c["trigger"]):
            err(f"{rel}: unknown trigger {c['trigger']}")
        # 26.2 entity predicates are lists of loot conditions / "minecraft:" keyed maps, not the old objects.
        player = c.get("conditions", {}).get("player")
        if isinstance(player, dict) and any(":" not in k for k in player):
            err(f"{rel}: criterion {name} uses the pre-26.2 entity predicate format")

# ------------------------------------------------------------------ villager trades
for f in walk_json(os.path.join(DATA, NS, "villager_trade")):
    d = load(f) or {}
    for key in ("gives", "wants", "additional_wants"):
        v = d.get(key)
        if isinstance(v, dict) and "id" in v and not item_exists(v["id"]):
            err(f"{os.path.relpath(f, ROOT)}: unknown {key} item {v['id']}")
for f in walk_json(os.path.join(DATA, NS, "tags", "villager_trade")):
    for v in (load(f) or {}).get("values", []):
        if not v.startswith("#") and not ours_or_vanilla(v, "villager_trade", "villager_trade"):
            err(f"{os.path.relpath(f, ROOT)}: unknown villager trade {v}")
for f in walk_json(os.path.join(DATA, NS, "trade_set")):
    trades = (load(f) or {}).get("trades", "")
    if isinstance(trades, str) and trades.startswith("#"):
        ns, path = trades[1:].split(":", 1)
        if not os.path.exists(os.path.join(DATA, ns, "tags", "villager_trade", path + ".json")):
            err(f"{os.path.relpath(f, ROOT)}: unknown villager trade tag {trades}")

# A time marker may be defined only once per clock. Vanilla's own timelines own every minecraft: marker
# (wake_up_from_sleep, noon, ...), so a copied vanilla timeline must not keep them.
markers = {}
for f in walk_json(os.path.join(DATA, NS, "timeline")):
    d = load(f) or {}
    rel = os.path.relpath(f, ROOT)
    for marker in d.get("time_markers", {}):
        if marker.startswith("minecraft:"):
            err(f"{rel}: time marker {marker} is already defined by vanilla's timelines")
        key = (d.get("clock"), marker)
        if key in markers:
            err(f"{rel}: time marker {marker} is also defined by {markers[key]}")
        markers[key] = rel

tl = load(os.path.join(DATA, NS, "tags", "timeline", "in_trans_realm.json"))
if tl:
    for v in tl["values"]:
        if v.startswith("#"):
            continue
        ns, path = v.split(":", 1)
        if ns == NS and not os.path.exists(os.path.join(DATA, NS, "timeline", path + ".json")):
            err(f"timeline tag: unknown timeline {v}")
        if ns == "minecraft" and not vanilla_has("timeline", v):
            err(f"timeline tag: unknown timeline {v}")

# ------------------------------------------------------------------ entity tags and paintings
for ns in os.listdir(DATA):
    for f in walk_json(os.path.join(DATA, ns, "tags", "entity_type")):
        for v in (load(f) or {}).get("values", []):
            if not v.startswith("#") and not entity_exists(v):
                err(f"{os.path.relpath(f, ROOT)}: unknown entity {v}")

# paintings: a texture per variant, and the placeable tag only lists variants that exist
for f in walk_json(os.path.join(DATA, NS, "painting_variant")):
    d = load(f) or {}
    rel = os.path.relpath(f, ROOT)
    asset = d.get("asset_id", "")
    if is_ours(asset):
        tex = os.path.join(ASSETS, "textures", "painting", path_of(asset) + ".png")
        if not os.path.exists(tex):
            err(f"{rel}: no texture for {asset}")
        else:
            from PIL import Image
            w, h = Image.open(tex).size
            if (w, h) != (16 * d["width"], 16 * d["height"]):
                err(f"{rel}: texture is {w}x{h}, expected {16 * d['width']}x{16 * d['height']}")
    for part in ("title", "author"):
        key = d.get(part, {}).get("translate")
        if key and key not in lang:
            err(f"{rel}: no translation for {key}")
for f in walk_json(os.path.join(DATA, "minecraft", "tags", "painting_variant")):
    for v in (load(f) or {}).get("values", []):
        if is_ours(v) and not os.path.exists(os.path.join(DATA, NS, "painting_variant", path_of(v) + ".json")):
            err(f"{os.path.relpath(f, ROOT)}: unknown painting {v}")

for w in warnings:
    print("warning:", w)
for e in errors:
    print("ERROR:", e)
print(f"{len(BLOCKS)} blocks, {len(ITEMS)} items checked; {len(errors)} errors, {len(warnings)} warnings"
      + ("" if vanilla else " (vanilla ids not checked: pass the mcmeta summary registries file)"))
sys.exit(1 if errors else 0)
