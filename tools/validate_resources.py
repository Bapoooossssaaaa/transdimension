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
    for m in re.finditer(r'(?:register|stairs|slab|wall|glass|pane)\("([a-z0-9_]+)"', src):
        blocks.add(m.group(1))
    for m in re.finditer(r'registerWithoutItem\("([a-z0-9_]+)"', src):
        no_item.add(m.group(1))
        blocks.add(m.group(1))
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
for b in BLOCKS - {"trans_cake"}:
    if not os.path.exists(os.path.join(DATA, NS, "loot_table", "blocks", b + ".json")):
        err(f"block {b}: no loot table")


def check_loot(obj, where):
    if isinstance(obj, dict):
        if obj.get("type") == "minecraft:item" and "name" in obj and not item_exists(obj["name"]):
            err(f"{where}: loot item {obj['name']} doesn't exist")
        if obj.get("type") == "minecraft:loot_table" and isinstance(obj.get("value"), str):
            pass
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
}
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
        if path in ("pride_oven", "trans_chair", "trans_table"):
            props = {"facing": props.get("facing", [])}
        return props
    return VANILLA_BLOCKS.get(path, [None])[0]


def check_states(obj, where):
    if isinstance(obj, dict):
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
            if not (is_ours(t) and path_of(t) == "silly_cat") and not vanilla_has("entity_type", t):
                err(f"{rel}: unknown entity {t}")
    for part in d["attributes"].get("minecraft:visual/ambient_particles", []):
        if not vanilla_has("particle_type", part["particle"]["type"]):
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

for w in warnings:
    print("warning:", w)
for e in errors:
    print("ERROR:", e)
print(f"{len(BLOCKS)} blocks, {len(ITEMS)} items checked; {len(errors)} errors, {len(warnings)} warnings"
      + ("" if vanilla else " (vanilla ids not checked: pass the mcmeta summary registries file)"))
sys.exit(1 if errors else 0)
