#!/usr/bin/env python3
"""
Generates the JSON for every Trans Dimension block family: blockstates, block and item models,
item definitions, loot tables, recipes, tags, sounds.json and the English names.

    python3 tools/generate_data.py

The block list below mirrors registry/ModBlocks.java. Complex blockstates (stairs, doors, walls,
panes...) are copied from vanilla 26.2 templates in tools/vanilla_extra/templates/ with the names
swapped, so their format always matches the game. Hand-made files (the cake models, chest loot,
villager trades, worldgen...) are left alone unless listed here.
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "transdimension")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
TEMPLATES = os.path.join(HERE, "vanilla_extra", "templates")
NS = "transdimension"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rid(name):
    return name if ":" in name else f"{NS}:{name}"


def block_tex(name):
    return name if ":" in name else f"{NS}:block/{name}"


def blockstate(name, obj):
    write(os.path.join(ASSETS, "blockstates", f"{name}.json"), obj)


def model(name, obj, kind="block"):
    write(os.path.join(ASSETS, "models", kind, f"{name}.json"), obj)


def item_def(name, model_id, tints=None):
    m = {"type": "minecraft:model", "model": model_id}
    if tints:
        m["tints"] = tints
    write(os.path.join(ASSETS, "items", f"{name}.json"), {"model": m})


def loot(name, obj):
    write(os.path.join(DATA, NS, "loot_table", "blocks", f"{name}.json"), obj)


def recipe(name, obj):
    write(os.path.join(DATA, NS, "recipe", f"{name}.json"), obj)


def from_template(template, old_prefix, new_name):
    """Loads a vanilla blockstate and points its models at ours (keeping vanilla's rotations/uvlock)."""
    with open(os.path.join(TEMPLATES, "blockstates", f"{template}.json"), encoding="utf-8") as f:
        text = f.read()
    text = text.replace(f"minecraft:block/{old_prefix}", f"{NS}:block/{new_name}")
    return json.loads(text)


# ============================================================================================ loot helpers
SILK = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
SHEARS_OR_SILK = {"condition": "minecraft:any_of", "terms": [
    {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}, SILK]}


def table(name, pools):
    return {"type": "minecraft:block", "pools": pools, "random_sequence": f"{NS}:blocks/{name}"}


def loot_self(name, drop=None):
    return table(name, [{"conditions": [{"condition": "minecraft:survives_explosion"}],
                         "entries": [{"type": "minecraft:item", "name": rid(drop or name)}], "rolls": 1.0}])


def loot_silk_or(name, other):
    return table(name, [{"entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "conditions": [SILK], "name": rid(name)},
        {"type": "minecraft:item", "conditions": [{"condition": "minecraft:survives_explosion"}], "name": rid(other)}]}],
        "rolls": 1.0}])


def loot_silk_only(name):
    return table(name, [{"conditions": [SILK], "entries": [{"type": "minecraft:item", "name": rid(name)}], "rolls": 1.0}])


def loot_slab(name):
    return table(name, [{"entries": [{"type": "minecraft:item", "functions": [
        {"conditions": [{"block": rid(name), "condition": "minecraft:block_state_property", "properties": {"type": "double"}}],
         "count": 2.0, "function": "minecraft:set_count"}, {"function": "minecraft:explosion_decay"}], "name": rid(name)}],
        "rolls": 1.0}])


def loot_door(name):
    return table(name, [{"conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [
        {"type": "minecraft:item", "conditions": [{"block": rid(name), "condition": "minecraft:block_state_property",
                                                   "properties": {"half": "lower"}}], "name": rid(name)}], "rolls": 1.0}])


def loot_leaves(name, sapling):
    return table(name, [
        {"entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "conditions": [SHEARS_OR_SILK], "name": rid(name)},
            {"type": "minecraft:item", "conditions": [{"condition": "minecraft:survives_explosion"}, {
                "chances": [0.05, 0.0625, 0.083333336, 0.1], "condition": "minecraft:table_bonus",
                "enchantment": "minecraft:fortune"}], "name": rid(sapling)}]}], "rolls": 1.0},
        {"conditions": [{"condition": "minecraft:inverted", "term": SHEARS_OR_SILK}], "entries": [
            {"type": "minecraft:item", "conditions": [{"chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1],
                                                       "condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune"}],
             "functions": [{"count": {"type": "minecraft:uniform", "max": 2.0, "min": 1.0}, "function": "minecraft:set_count"},
                           {"function": "minecraft:explosion_decay"}], "name": "minecraft:stick"}], "rolls": 1.0}])


def loot_ore(name, drop):
    return table(name, [{"entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "conditions": [SILK], "name": rid(name)},
        {"type": "minecraft:item", "functions": [
            {"enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops", "function": "minecraft:apply_bonus"},
            {"function": "minecraft:explosion_decay"}], "name": rid(drop)}]}], "rolls": 1.0}])


def loot_cluster(name, drop):
    return table(name, [{"entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "conditions": [SILK], "name": rid(name)},
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "conditions": [{"condition": "minecraft:match_tool", "predicate": {
                "items": "#minecraft:cluster_max_harvestables"}}], "functions": [
                {"count": 2.0, "function": "minecraft:set_count"},
                {"enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops", "function": "minecraft:apply_bonus"}],
             "name": rid(drop)},
            {"type": "minecraft:item", "functions": [{"count": 1.0, "function": "minecraft:set_count"},
                                                     {"function": "minecraft:explosion_decay"}], "name": rid(drop)}]}]}],
        "rolls": 1.0}])


def loot_like_vanilla(block, vanilla, swaps=None):
    """Copies a vanilla 26.2 block loot table (tools/vanilla_extra/templates/loot/) with its block ids swapped for ours."""
    with open(os.path.join(TEMPLATES, "loot", f"{vanilla}.json"), encoding="utf-8") as f:
        text = f.read()
    swaps = dict(swaps or {})
    swaps.setdefault(f"minecraft:{vanilla}", rid(block))
    for old, new in swaps.items():
        text = text.replace(f'"{old}"', f'"{rid(new)}"')
    d = json.loads(text)
    d["random_sequence"] = f"{NS}:blocks/{block}"
    loot(block, d)


def loot_pot(name, plant):
    return table(name, [
        {"conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [{"type": "minecraft:item", "name": "minecraft:flower_pot"}], "rolls": 1.0},
        {"conditions": [{"condition": "minecraft:survives_explosion"}], "entries": [{"type": "minecraft:item", "name": rid(plant)}], "rolls": 1.0}])


# ============================================================================================ recipe helpers
def shaped(result, pattern, key, count=1, category="building", group=None):
    r = {"type": "minecraft:crafting_shaped", "category": category}
    if group:
        r["group"] = group
    r["key"] = {k: rid(v) if not v.startswith("#") else v for k, v in key.items()}
    r["pattern"] = pattern
    r["result"] = {"count": count, "id": rid(result)}
    return r


def shapeless(result, ingredients, count=1, category="building", group=None):
    r = {"type": "minecraft:crafting_shapeless", "category": category}
    if group:
        r["group"] = group
    r["ingredients"] = [rid(i) if not i.startswith("#") else i for i in ingredients]
    r["result"] = {"count": count, "id": rid(result)}
    return r


def smelting(result, ingredient, xp=0.1, category="blocks"):
    return {"type": "minecraft:smelting", "category": category, "cookingtime": 200, "experience": xp,
            "ingredient": rid(ingredient), "result": {"id": rid(result)}}


def blasting(result, ingredient, xp=0.1, category="misc"):
    return {"type": "minecraft:blasting", "category": category, "cookingtime": 100, "experience": xp,
            "ingredient": rid(ingredient), "result": {"id": rid(result)}}


def stone_recipes(prefix, base, wall=True, cut_from=()):
    """Stairs, slab (and wall) from `base` by crafting and stonecutting, and stonecutting from the blocks in `cut_from`."""
    recipe(f"{prefix}_stairs", shaped(f"{prefix}_stairs", ["#  ", "## ", "###"], {"#": base}, 4))
    recipe(f"{prefix}_slab", shaped(f"{prefix}_slab", ["###"], {"#": base}, 6))
    parts = [("stairs", 1), ("slab", 2)]
    if wall:
        recipe(f"{prefix}_wall", shaped(f"{prefix}_wall", ["###", "###"], {"#": base}, 6, category="misc"))
        parts.append(("wall", 1))
    for source in [base, *cut_from]:
        for part, count in parts:
            recipe(f"{prefix}_{part}_from_{source}_stonecutting", stonecutting(f"{prefix}_{part}", source, count))


def stonecutting(result, ingredient, count=1):
    return {"type": "minecraft:stonecutting", "ingredient": rid(ingredient), "result": {"count": count, "id": rid(result)}}


# Small flowers that grow in the realm instead of vanilla's.
FLOWERS = {"trans_tulip": "Trans Tulip", "pearl_daisy": "Pearl Daisy", "sky_bell": "Sky Bell", "flag_lily": "Flag Lily",
           "lavender_puff": "Lavender Puff", "trans_orchid": "Trans Orchid", "heart_bloom": "Heart Bloom"}

# Vanilla ores re-made in trans rock: (name, English name, mining tier); ORE_SMELTING has what they smelt into.
ORE_INFO = [("coal", "Coal", "stone"), ("iron", "Iron", "stone"), ("copper", "Copper", "stone"), ("gold", "Gold", "iron"),
            ("redstone", "Redstone", "iron"), ("lapis", "Lapis Lazuli", "stone"), ("diamond", "Diamond", "iron"),
            ("emerald", "Emerald", "iron")]
ORE_SMELTING = {"coal": ("minecraft:coal", 0.1), "iron": ("minecraft:iron_ingot", 0.7), "copper": ("minecraft:copper_ingot", 0.7),
                "gold": ("minecraft:gold_ingot", 1.0), "redstone": ("minecraft:redstone", 0.7), "lapis": ("minecraft:lapis_lazuli", 0.2),
                "diamond": ("minecraft:diamond", 1.0), "emerald": ("minecraft:emerald", 1.0)}


# ============================================================================================ registry of generated things
NAMES = {}          # lang keys
TAGS = {}           # (registry, namespace, path) -> set of values
LOOT_DONE = set()


def tag(kind, path, *values, ns="minecraft"):
    TAGS.setdefault((kind, ns, path), set()).update(rid(v) if not v.startswith("#") else v for v in values)


def name(block, english, kind="block"):
    NAMES[f"{kind}.{NS}.{block}"] = english


def mine(block, tool):
    tag("block", f"mineable/{tool}", block)


# ============================================================================================ block families
def cube(block, english, texture=None, tool="pickaxe", drop="self"):
    blockstate(block, {"variants": {"": {"model": f"{NS}:block/{block}"}}})
    model(block, {"parent": "minecraft:block/cube_all", "textures": {"all": block_tex(texture or block)}})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    if tool:
        mine(block, tool)
    if drop == "self":
        loot(block, loot_self(block))


def column(block, english, side, end, tool="pickaxe"):
    blockstate(block, {"variants": {"": {"model": f"{NS}:block/{block}"}}})
    model(block, {"parent": "minecraft:block/cube_column", "textures": {"end": block_tex(end), "side": block_tex(side)}})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    mine(block, tool)
    loot(block, loot_self(block))


def log(block, english, side, end):
    blockstate(block, from_template("cherry_log", "cherry_log", block))
    textures = {"end": block_tex(end), "side": block_tex(side)}
    model(block, {"parent": "minecraft:block/cube_column", "textures": textures})
    for axis in "xyz":
        model(f"{block}_{axis}", {"parent": f"minecraft:block/cube_column_uv_locked_{axis}", "textures": textures})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    mine(block, "axe")
    loot(block, loot_self(block))


def mirrored_pillar(block, english, side, end, tool="pickaxe"):
    """A deepslate-style pillar whose sides are randomly mirrored and rotated (vanilla's deepslate blockstate)."""
    blockstate(block, from_template("deepslate", "deepslate", block))
    textures = {"end": block_tex(end), "side": block_tex(side)}
    model(block, {"parent": "minecraft:block/cube_column", "textures": textures})
    model(f"{block}_mirrored", {"parent": "minecraft:block/cube_column_mirrored", "textures": textures})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    mine(block, tool)


def stone_family(base, english, prefix, prefix_english, texture=None, wall_too=True, tool="pickaxe"):
    """A full block plus its stairs and slab (and wall), all sharing one texture."""
    cube(base, english, texture=texture or base, tool=tool)
    tex = {k: texture or base for k in ("bottom", "top", "side")}
    stairs(f"{prefix}_stairs", f"{prefix_english} Stairs", base, tex, tool)
    slab(f"{prefix}_slab", f"{prefix_english} Slab", base, tex, tool)
    if wall_too:
        wall(f"{prefix}_wall", f"{prefix_english} Wall", texture or base)


def wood(block, english, side):
    blockstate(block, from_template("cherry_wood", "cherry_wood", block))
    model(block, {"parent": "minecraft:block/cube_column", "textures": {"end": block_tex(side), "side": block_tex(side)}})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    mine(block, "axe")
    loot(block, loot_self(block))


def stairs(block, english, base, textures, tool):
    """textures: dict with bottom/top/side."""
    blockstate(block, from_template("cherry_stairs", "cherry_stairs", block))
    tex = {k: block_tex(v) for k, v in textures.items()}
    model(block, {"parent": "minecraft:block/stairs", "textures": tex})
    model(f"{block}_inner", {"parent": "minecraft:block/inner_stairs", "textures": tex})
    model(f"{block}_outer", {"parent": "minecraft:block/outer_stairs", "textures": tex})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    mine(block, tool)
    loot(block, loot_self(block))
    tag("block", "stairs", block)
    tag("item", "stairs", block)


def slab(block, english, full_block, textures, tool):
    bs = from_template("cherry_slab", "cherry_slab", block)
    bs["variants"]["type=double"]["model"] = f"{NS}:block/{full_block}"
    blockstate(block, bs)
    tex = {k: block_tex(v) for k, v in textures.items()}
    model(block, {"parent": "minecraft:block/slab", "textures": tex})
    model(f"{block}_top", {"parent": "minecraft:block/slab_top", "textures": tex})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    mine(block, tool)
    loot(block, loot_slab(block))
    tag("block", "slabs", block)
    tag("item", "slabs", block)


def wall(block, english, texture):
    blockstate(block, from_template("cobblestone_wall", "cobblestone_wall", block))
    tex = {"wall": block_tex(texture)}
    model(f"{block}_post", {"parent": "minecraft:block/template_wall_post", "textures": tex})
    model(f"{block}_side", {"parent": "minecraft:block/template_wall_side", "textures": tex})
    model(f"{block}_side_tall", {"parent": "minecraft:block/template_wall_side_tall", "textures": tex})
    model(f"{block}_inventory", {"parent": "minecraft:block/wall_inventory", "textures": tex})
    item_def(block, f"{NS}:block/{block}_inventory")
    name(block, english)
    mine(block, "pickaxe")
    loot(block, loot_self(block))
    tag("block", "walls", block)
    tag("item", "walls", block)


def fence(block, english, texture):
    blockstate(block, from_template("cherry_fence", "cherry_fence", block))
    tex = {"texture": block_tex(texture)}
    model(f"{block}_post", {"parent": "minecraft:block/fence_post", "textures": tex})
    model(f"{block}_side", {"parent": "minecraft:block/fence_side", "textures": tex})
    model(f"{block}_inventory", {"parent": "minecraft:block/fence_inventory", "textures": tex})
    item_def(block, f"{NS}:block/{block}_inventory")
    name(block, english)
    mine(block, "axe")
    loot(block, loot_self(block))
    tag("block", "wooden_fences", block)
    tag("item", "wooden_fences", block)


def fence_gate(block, english, texture):
    blockstate(block, from_template("cherry_fence_gate", "cherry_fence_gate", block))
    tex = {"texture": block_tex(texture)}
    for suffix, parent in (("", "template_fence_gate"), ("_open", "template_fence_gate_open"),
                           ("_wall", "template_fence_gate_wall"), ("_wall_open", "template_fence_gate_wall_open")):
        model(f"{block}{suffix}", {"parent": f"minecraft:block/{parent}", "textures": tex})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    mine(block, "axe")
    loot(block, loot_self(block))
    tag("block", "fence_gates", block)
    tag("item", "fence_gates", block)


def door(block, english, top, bottom):
    blockstate(block, from_template("cherry_door", "cherry_door", block))
    tex = {"bottom": block_tex(bottom), "top": block_tex(top)}
    for part in ("bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
                 "top_left", "top_left_open", "top_right", "top_right_open"):
        model(f"{block}_{part}", {"parent": f"minecraft:block/door_{part}", "textures": tex})
    model(block, {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{block}"}}, kind="item")
    item_def(block, f"{NS}:item/{block}")
    name(block, english)
    mine(block, "axe")
    loot(block, loot_door(block))
    tag("block", "wooden_doors", block)
    tag("item", "wooden_doors", block)


def trapdoor(block, english, texture):
    blockstate(block, from_template("cherry_trapdoor", "cherry_trapdoor", block))
    tex = {"texture": block_tex(texture)}
    for suffix in ("bottom", "top", "open"):
        model(f"{block}_{suffix}", {"parent": f"minecraft:block/template_orientable_trapdoor_{suffix}", "textures": tex})
    item_def(block, f"{NS}:block/{block}_bottom")
    name(block, english)
    mine(block, "axe")
    loot(block, loot_self(block))
    tag("block", "wooden_trapdoors", block)
    tag("item", "wooden_trapdoors", block)


def button(block, english, texture, tool, wooden):
    blockstate(block, from_template("cherry_button", "cherry_button", block))
    tex = {"texture": block_tex(texture)}
    model(block, {"parent": "minecraft:block/button", "textures": tex})
    model(f"{block}_pressed", {"parent": "minecraft:block/button_pressed", "textures": tex})
    model(f"{block}_inventory", {"parent": "minecraft:block/button_inventory", "textures": tex})
    item_def(block, f"{NS}:block/{block}_inventory")
    name(block, english)
    mine(block, tool)
    loot(block, loot_self(block))
    t = "wooden_buttons" if wooden else "stone_buttons"
    tag("block", t, block)
    tag("item", t, block)


def pressure_plate(block, english, texture, tool, wooden):
    blockstate(block, from_template("cherry_pressure_plate", "cherry_pressure_plate", block))
    tex = {"texture": block_tex(texture)}
    model(block, {"parent": "minecraft:block/pressure_plate_up", "textures": tex})
    model(f"{block}_down", {"parent": "minecraft:block/pressure_plate_down", "textures": tex})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    mine(block, tool)
    loot(block, loot_self(block))
    if wooden:
        tag("block", "wooden_pressure_plates", block)
        tag("item", "wooden_pressure_plates", block)
    else:
        tag("block", "stone_pressure_plates", block)


def translucent(sprite):
    return {"force_translucent": True, "sprite": block_tex(sprite)}


def glass(block, english):
    blockstate(block, {"variants": {"": {"model": f"{NS}:block/{block}"}}})
    model(block, {"parent": "minecraft:block/cube_all", "textures": {"all": translucent(block)}})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    loot(block, loot_silk_only(block))
    tag("block", "impermeable", block)


def pane(block, english, glass_block):
    blockstate(block, from_template("pink_stained_glass_pane", "pink_stained_glass_pane", block))
    edge = translucent(f"{glass_block}_pane_top")
    face = translucent(glass_block)
    model(f"{block}_post", {"parent": "minecraft:block/template_glass_pane_post", "textures": {"edge": edge, "pane": face}})
    model(f"{block}_side", {"parent": "minecraft:block/template_glass_pane_side", "textures": {"edge": edge, "pane": face}})
    model(f"{block}_side_alt", {"parent": "minecraft:block/template_glass_pane_side_alt", "textures": {"edge": edge, "pane": face}})
    model(f"{block}_noside", {"parent": "minecraft:block/template_glass_pane_noside", "textures": {"pane": face}})
    model(f"{block}_noside_alt", {"parent": "minecraft:block/template_glass_pane_noside_alt", "textures": {"pane": face}})
    model(block, {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex(glass_block)}}, kind="item")
    item_def(block, f"{NS}:item/{block}")
    name(block, english)
    loot(block, loot_silk_only(block))


def cross_plant(block, english, potted=None, potted_english=None):
    blockstate(block, {"variants": {"": {"model": f"{NS}:block/{block}"}}})
    model(block, {"parent": "minecraft:block/cross", "textures": {"cross": block_tex(block)}})
    model(block, {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex(block)}}, kind="item")
    item_def(block, f"{NS}:item/{block}")
    name(block, english)
    loot(block, loot_self(block))
    if potted:
        blockstate(potted, {"variants": {"": {"model": f"{NS}:block/{potted}"}}})
        model(potted, {"parent": "minecraft:block/flower_pot_cross", "textures": {"plant": block_tex(block)}})
        name(potted, potted_english)
        loot(potted, loot_pot(potted, block))
        tag("block", "flower_pots", potted)


# ============================================================================================ the blocks
def generate_blocks():
    # ---- terrain
    blockstate("trans_grass_block", from_template("grass_block", "grass_block", "trans_grass_block"))
    model("trans_grass_block", {
        "parent": "minecraft:block/block",
        "textures": {"particle": block_tex("trans_dirt"), "bottom": block_tex("trans_dirt"),
                     "top": block_tex("trans_grass_block_top"), "side": block_tex("trans_grass_block_side"),
                     "overlay": block_tex("trans_grass_block_side_overlay")},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up", "tintindex": 0},
                "north": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "north"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "south"},
                "west": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "west"},
                "east": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "east"}}},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#overlay", "tintindex": 0, "cullface": "north"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#overlay", "tintindex": 0, "cullface": "south"},
                "west": {"uv": [0, 0, 16, 16], "texture": "#overlay", "tintindex": 0, "cullface": "west"},
                "east": {"uv": [0, 0, 16, 16], "texture": "#overlay", "tintindex": 0, "cullface": "east"}}}]})
    model("trans_grass_block_snow", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "bottom": block_tex("trans_dirt"), "particle": block_tex("trans_dirt"),
        "side": block_tex("trans_grass_block_snow"), "top": block_tex("trans_grass_block_top")}})
    # Trans pink in the inventory; in the world the biome's grass colour tints it.
    item_def("trans_grass_block", f"{NS}:block/trans_grass_block", tints=[{"type": "minecraft:constant", "value": argb_int(0xF5A9B8)}])
    name("trans_grass_block", "Trans Grass Block")
    mine("trans_grass_block", "shovel")
    loot("trans_grass_block", loot_silk_or("trans_grass_block", "trans_dirt"))

    cube("trans_dirt", "Trans Dirt", tool="shovel")
    cube("trans_sand", "Trans Sand", tool="shovel")

    # ---- trans stone family
    cube("trans_stone", "Trans Stone", drop=None)
    loot("trans_stone", loot_silk_or("trans_stone", "trans_cobblestone"))
    stone_tex = {"bottom": "trans_stone", "top": "trans_stone", "side": "trans_stone"}
    stairs("trans_stone_stairs", "Trans Stone Stairs", "trans_stone", stone_tex, "pickaxe")
    slab("trans_stone_slab", "Trans Stone Slab", "trans_stone", stone_tex, "pickaxe")
    button("trans_stone_button", "Trans Stone Button", "trans_stone", "pickaxe", wooden=False)
    pressure_plate("trans_stone_pressure_plate", "Trans Stone Pressure Plate", "trans_stone", "pickaxe", wooden=False)

    cube("trans_cobblestone", "Trans Cobblestone")
    cobble_tex = {"bottom": "trans_cobblestone", "top": "trans_cobblestone", "side": "trans_cobblestone"}
    stairs("trans_cobblestone_stairs", "Trans Cobblestone Stairs", "trans_cobblestone", cobble_tex, "pickaxe")
    slab("trans_cobblestone_slab", "Trans Cobblestone Slab", "trans_cobblestone", cobble_tex, "pickaxe")
    wall("trans_cobblestone_wall", "Trans Cobblestone Wall", "trans_cobblestone")

    cube("trans_stone_bricks", "Trans Stone Bricks")
    cube("cracked_trans_stone_bricks", "Cracked Trans Stone Bricks")
    cube("chiseled_trans_stone_bricks", "Chiseled Trans Stone Bricks")
    brick_tex = {"bottom": "trans_stone_bricks", "top": "trans_stone_bricks", "side": "trans_stone_bricks"}
    stairs("trans_stone_brick_stairs", "Trans Stone Brick Stairs", "trans_stone_bricks", brick_tex, "pickaxe")
    slab("trans_stone_brick_slab", "Trans Stone Brick Slab", "trans_stone_bricks", brick_tex, "pickaxe")
    wall("trans_stone_brick_wall", "Trans Stone Brick Wall", "trans_stone_bricks")
    tag("block", "stone_bricks", "trans_stone_bricks", "cracked_trans_stone_bricks", "chiseled_trans_stone_bricks")
    tag("item", "stone_bricks", "trans_stone_bricks", "cracked_trans_stone_bricks", "chiseled_trans_stone_bricks")

    # ---- trans sandstone family
    blockstate("trans_sandstone", {"variants": {"": {"model": f"{NS}:block/trans_sandstone"}}})
    model("trans_sandstone", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "bottom": block_tex("trans_sandstone_bottom"), "side": block_tex("trans_sandstone"), "top": block_tex("trans_sandstone_top")}})
    item_def("trans_sandstone", f"{NS}:block/trans_sandstone")
    name("trans_sandstone", "Trans Sandstone")
    mine("trans_sandstone", "pickaxe")
    loot("trans_sandstone", loot_self("trans_sandstone"))
    column("cut_trans_sandstone", "Cut Trans Sandstone", "cut_trans_sandstone", "trans_sandstone_top")
    column("chiseled_trans_sandstone", "Chiseled Trans Sandstone", "chiseled_trans_sandstone", "trans_sandstone_top")
    sand_tex = {"bottom": "trans_sandstone_bottom", "top": "trans_sandstone_top", "side": "trans_sandstone"}
    stairs("trans_sandstone_stairs", "Trans Sandstone Stairs", "trans_sandstone", sand_tex, "pickaxe")
    slab("trans_sandstone_slab", "Trans Sandstone Slab", "trans_sandstone", sand_tex, "pickaxe")
    wall("trans_sandstone_wall", "Trans Sandstone Wall", "trans_sandstone")

    # ---- trans deepslate family
    mirrored_pillar("trans_deepslate", "Trans Deepslate", "trans_deepslate", "trans_deepslate_top")
    loot_like_vanilla("trans_deepslate", "deepslate", {"minecraft:cobbled_deepslate": "cobbled_trans_deepslate"})
    stone_family("cobbled_trans_deepslate", "Cobbled Trans Deepslate", "cobbled_trans_deepslate", "Cobbled Trans Deepslate")
    stone_family("polished_trans_deepslate", "Polished Trans Deepslate", "polished_trans_deepslate", "Polished Trans Deepslate")
    stone_family("trans_deepslate_bricks", "Trans Deepslate Bricks", "trans_deepslate_brick", "Trans Deepslate Brick")
    cube("cracked_trans_deepslate_bricks", "Cracked Trans Deepslate Bricks")
    stone_family("trans_deepslate_tiles", "Trans Deepslate Tiles", "trans_deepslate_tile", "Trans Deepslate Tile")
    cube("cracked_trans_deepslate_tiles", "Cracked Trans Deepslate Tiles")
    cube("chiseled_trans_deepslate", "Chiseled Trans Deepslate")

    # ---- rose granite, pearl diorite, sky andesite, gravel
    for stone, english in (("granite", "Rose Granite"), ("diorite", "Pearl Diorite"), ("andesite", "Sky Andesite")):
        stone_family(f"trans_{stone}", english, f"trans_{stone}", english)
        stone_family(f"polished_trans_{stone}", f"Polished {english}", f"polished_trans_{stone}", f"Polished {english}", wall_too=False)
    cube("trans_gravel", "Trans Gravel", tool="shovel", drop=None)
    loot_like_vanilla("trans_gravel", "gravel")

    # ---- vanilla ores, at home in trans stone and trans deepslate
    for ore, english, tier in ORE_INFO:
        for block, vanilla, label in ((f"trans_{ore}_ore", f"{ore}_ore", f"Trans {english} Ore"),
                                      (f"trans_deepslate_{ore}_ore", f"deepslate_{ore}_ore", f"Trans Deepslate {english} Ore")):
            cube(block, label, drop=None)
            loot_like_vanilla(block, vanilla)
            tag("block", f"{ore}_ores", block)
            tag("item", f"{ore}_ores", block)
            tag("block", f"needs_{tier}_tool", block)

    # ---- crystals
    cube("trans_crystal_ore", "Trans Crystal Ore", drop=None)
    loot("trans_crystal_ore", loot_ore("trans_crystal_ore", "trans_crystal"))
    cube("trans_deepslate_crystal_ore", "Trans Deepslate Crystal Ore", drop=None)
    loot("trans_deepslate_crystal_ore", loot_ore("trans_deepslate_crystal_ore", "trans_crystal"))
    tag("block", "needs_iron_tool", "trans_crystal_ore", "trans_deepslate_crystal_ore", "trans_crystal_block")
    cube("trans_crystal_block", "Block of Trans Crystal")
    tag("block", "crystal_sound_blocks", "trans_crystal_block")
    blockstate("trans_crystal_cluster", from_template("amethyst_cluster", "amethyst_cluster", "trans_crystal_cluster"))
    model("trans_crystal_cluster", {"parent": "minecraft:block/cross", "textures": {"cross": block_tex("trans_crystal_cluster")}})
    model("trans_crystal_cluster", {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex("trans_crystal_cluster")},
                                    "display": {"head": {"translation": [0, 14, -5]}}}, kind="item")
    item_def("trans_crystal_cluster", f"{NS}:item/trans_crystal_cluster")
    name("trans_crystal_cluster", "Trans Crystal Cluster")
    mine("trans_crystal_cluster", "pickaxe")
    loot("trans_crystal_cluster", loot_cluster("trans_crystal_cluster", "trans_crystal"))

    # ---- trans wood family
    log("trans_log", "Trans Log", "trans_log", "trans_log_top")
    log("stripped_trans_log", "Stripped Trans Log", "stripped_trans_log", "stripped_trans_log_top")
    wood("trans_wood", "Trans Wood", "trans_log")
    wood("stripped_trans_wood", "Stripped Trans Wood", "stripped_trans_log")
    tag("block", "logs_that_burn", "#transdimension:trans_logs")
    tag("item", "logs_that_burn", "#transdimension:trans_logs")
    tag("block", "trans_logs", "trans_log", "stripped_trans_log", "trans_wood", "stripped_trans_wood", ns=NS)
    tag("item", "trans_logs", "trans_log", "stripped_trans_log", "trans_wood", "stripped_trans_wood", ns=NS)
    tag("block", "overworld_natural_logs", "trans_log")
    cube("trans_planks", "Trans Planks", tool="axe")
    tag("block", "planks", "trans_planks")
    tag("item", "planks", "trans_planks")
    plank_tex = {"bottom": "trans_planks", "top": "trans_planks", "side": "trans_planks"}
    stairs("trans_stairs", "Trans Stairs", "trans_planks", plank_tex, "axe")
    tag("block", "wooden_stairs", "trans_stairs")
    tag("item", "wooden_stairs", "trans_stairs")
    slab("trans_slab", "Trans Slab", "trans_planks", plank_tex, "axe")
    tag("block", "wooden_slabs", "trans_slab")
    tag("item", "wooden_slabs", "trans_slab")
    fence("trans_fence", "Trans Fence", "trans_planks")
    fence_gate("trans_fence_gate", "Trans Fence Gate", "trans_planks")
    door("trans_door", "Trans Door", "trans_door_top", "trans_door_bottom")
    trapdoor("trans_trapdoor", "Trans Trapdoor", "trans_trapdoor")
    button("trans_button", "Trans Button", "trans_planks", "axe", wooden=True)
    pressure_plate("trans_pressure_plate", "Trans Pressure Plate", "trans_planks", "axe", wooden=True)

    for leaves_id, english in (("trans_leaves", "Trans Leaves"), ("pearl_leaves", "Pearl Leaves"), ("sky_leaves", "Sky Leaves"),
                               ("blush_leaves", "Blush Leaves"), ("twilight_leaves", "Twilight Leaves")):
        blockstate(leaves_id, {"variants": {"": {"model": f"{NS}:block/{leaves_id}"}}})
        model(leaves_id, {"parent": "minecraft:block/leaves", "textures": {"all": block_tex(leaves_id)}})
        item_def(leaves_id, f"{NS}:block/{leaves_id}")
        name(leaves_id, english)
        mine(leaves_id, "hoe")
        loot(leaves_id, loot_leaves(leaves_id, "trans_sapling"))
        tag("block", "leaves", leaves_id)
        tag("item", "leaves", leaves_id)

    cross_plant("trans_sapling", "Trans Sapling", "potted_trans_sapling", "Potted Trans Sapling")
    tag("block", "saplings", "trans_sapling")
    tag("item", "saplings", "trans_sapling")
    cross_plant("pride_blossom", "Pride Blossom", "potted_pride_blossom", "Potted Pride Blossom")
    tag("block", "small_flowers", "pride_blossom")
    tag("item", "small_flowers", "pride_blossom")
    for flower_id, english in FLOWERS.items():
        cross_plant(flower_id, english, f"potted_{flower_id}", f"Potted {english}")
        tag("block", "small_flowers", flower_id)
        tag("item", "small_flowers", flower_id)
        tag("block", "bee_attractive", flower_id)
        tag("item", "bee_food", flower_id)
    # A tall flower (two blocks high, like the peony).
    blockstate("pride_peony", from_template("peony", "peony", "pride_peony"))
    for half in ("top", "bottom"):
        model(f"pride_peony_{half}", {"parent": "minecraft:block/cross", "textures": {"cross": block_tex(f"pride_peony_{half}")}})
    model("pride_peony", {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex("pride_peony_top")}}, kind="item")
    item_def("pride_peony", f"{NS}:item/pride_peony")
    name("pride_peony", "Pride Peony")
    loot_like_vanilla("pride_peony", "peony")
    for t in ("flowers", "bee_attractive", "replaceable_by_trees"):
        tag("block", t, "pride_peony")
    tag("item", "flowers", "pride_peony")
    tag("item", "bee_food", "pride_peony")
    # Ground cover petals, like pink petals (one to four per block, facing any way).
    blockstate("trans_petals", from_template("pink_petals", "pink_petals", "trans_petals"))
    for n in (1, 2, 3, 4):
        model(f"trans_petals_{n}", {"parent": f"minecraft:block/flowerbed_{n}", "textures": {
            "flowerbed": block_tex("trans_petals"), "stem": block_tex("trans_petals_stem")}})
    model("trans_petals", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/trans_petals"}}, kind="item")
    item_def("trans_petals", f"{NS}:item/trans_petals")
    name("trans_petals", "Trans Petals")
    loot_like_vanilla("trans_petals", "pink_petals")
    for t in ("flowers", "inside_step_sound_blocks", "bee_attractive"):
        tag("block", t, "trans_petals")
    tag("item", "flowers", "trans_petals")

    # ---- pastel lush caves
    cube("trans_moss_block", "Trans Moss Block", tool="hoe")
    tag("block", "moss_blocks", "trans_moss_block")
    tag("item", "moss_blocks", "trans_moss_block")
    blockstate("trans_moss_carpet", {"variants": {"": {"model": f"{NS}:block/trans_moss_carpet"}}})
    model("trans_moss_carpet", {"parent": "minecraft:block/carpet", "textures": {"wool": block_tex("trans_moss_block")}})
    item_def("trans_moss_carpet", f"{NS}:block/trans_moss_carpet")
    name("trans_moss_carpet", "Trans Moss Carpet")
    mine("trans_moss_carpet", "hoe")
    loot("trans_moss_carpet", loot_self("trans_moss_carpet"))
    # What the realm's moss patches may grow over.
    tag("block", "trans_moss_replaceable", "#transdimension:trans_base_stone", "#minecraft:dirt", "#minecraft:cave_vines",
        "trans_grass_block", ns=NS)

    # ---- glass, wool and light
    glass("trans_glass", "Trans Glass")
    pane("trans_glass_pane", "Trans Glass Pane", "trans_glass")
    glass("trans_stained_glass", "Trans Pride Stained Glass")
    pane("trans_stained_glass_pane", "Trans Pride Stained Glass Pane", "trans_stained_glass")
    glass("trans_pink_stained_glass", "Trans Pink Stained Glass")
    pane("trans_pink_stained_glass_pane", "Trans Pink Stained Glass Pane", "trans_pink_stained_glass")
    glass("trans_blue_stained_glass", "Trans Blue Stained Glass")
    pane("trans_blue_stained_glass_pane", "Trans Blue Stained Glass Pane", "trans_blue_stained_glass")

    cube("trans_wool", "Trans Pride Wool", tool=None)
    tag("block", "wool", "trans_wool")
    tag("item", "wool", "trans_wool")
    blockstate("trans_carpet", {"variants": {"": {"model": f"{NS}:block/trans_carpet"}}})
    model("trans_carpet", {"parent": "minecraft:block/carpet", "textures": {"wool": block_tex("trans_wool")}})
    item_def("trans_carpet", f"{NS}:block/trans_carpet")
    name("trans_carpet", "Trans Pride Carpet")
    loot("trans_carpet", loot_self("trans_carpet"))
    tag("block", "wool_carpets", "trans_carpet")
    tag("item", "wool_carpets", "trans_carpet")

    blockstate("trans_lantern", from_template("lantern", "lantern", "trans_lantern"))
    model("trans_lantern", {"parent": "minecraft:block/template_lantern", "textures": {"lantern": block_tex("trans_lantern")}})
    model("trans_lantern_hanging", {"parent": "minecraft:block/template_hanging_lantern", "textures": {"lantern": block_tex("trans_lantern")}})
    model("trans_lantern", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/trans_lantern"}}, kind="item")
    item_def("trans_lantern", f"{NS}:item/trans_lantern")
    name("trans_lantern", "Trans Lantern")
    mine("trans_lantern", "pickaxe")
    loot("trans_lantern", loot_self("trans_lantern"))
    tag("block", "lanterns", "trans_lantern")
    tag("item", "lanterns", "trans_lantern")

    # ---- furniture (models with their own elements, facing like a furnace)
    furniture_blockstate("trans_chair")
    model("trans_chair", chair_model())
    item_def("trans_chair", f"{NS}:block/trans_chair")
    name("trans_chair", "Trans Chair")
    mine("trans_chair", "axe")
    loot("trans_chair", loot_self("trans_chair"))
    furniture_blockstate("trans_table")
    model("trans_table", table_model())
    item_def("trans_table", f"{NS}:block/trans_table")
    name("trans_table", "Trans Table")
    mine("trans_table", "axe")
    loot("trans_table", loot_self("trans_table"))

    # ---- bakery
    bs = from_template("smoker", "smoker", "pride_oven")
    bs = {"variants": {k.replace(",lit=false", ""): v for k, v in bs["variants"].items() if "lit=false" in k}}
    blockstate("pride_oven", bs)
    model("pride_oven", {"parent": "minecraft:block/orientable_with_bottom", "textures": {
        "bottom": block_tex("pride_oven_bottom"), "front": block_tex("pride_oven_front"),
        "side": block_tex("pride_oven_side"), "top": block_tex("pride_oven_top")}})
    item_def("pride_oven", f"{NS}:block/pride_oven")
    name("pride_oven", "Pride Oven")
    mine("pride_oven", "pickaxe")
    loot("pride_oven", loot_self("pride_oven"))
    name("trans_cake", "Trans Cake")


def furniture_blockstate(block):
    blockstate(block, {"variants": {
        "facing=north": {"model": f"{NS}:block/{block}"},
        "facing=east": {"model": f"{NS}:block/{block}", "y": 90},
        "facing=south": {"model": f"{NS}:block/{block}", "y": 180},
        "facing=west": {"model": f"{NS}:block/{block}", "y": 270}}})


def element(frm, to, textures, uv=None):
    faces = {}
    for face, tex in textures.items():
        faces[face] = {"texture": tex}
    return {"from": frm, "to": to, "faces": faces}


ALL = ("down", "up", "north", "south", "west", "east")


def chair_model():
    """A little wooden chair with a trans flag cushion; its open side faces north."""
    planks = "#planks"
    cushion = "#cushion"
    legs = [element([x, 0, z], [x + 2, 7, z + 2], {f: planks for f in ALL}) for x, z in ((3, 3), (11, 3), (3, 11), (11, 11))]
    seat = element([3, 7, 3], [13, 9, 13], {f: planks for f in ALL})
    pad = element([4, 9, 4], [12, 10, 12], {f: cushion for f in ALL})
    back_posts = [element([x, 9, 11], [x + 2, 17, 13], {f: planks for f in ALL}) for x in (3, 11)]
    back = element([5, 12, 11.5], [11, 16, 12.5], {f: cushion for f in ALL})
    return {"parent": "minecraft:block/block", "textures": {
        "particle": block_tex("trans_planks"), "planks": block_tex("trans_planks"), "cushion": block_tex("trans_wool")},
        "elements": legs + [seat, pad] + back_posts + [back]}


def table_model():
    """A pedestal table with a thick top."""
    planks = "#planks"
    top = element([0, 13, 0], [16, 16, 16], {f: planks for f in ALL})
    post = element([6, 1, 6], [10, 13, 10], {f: "#log" for f in ALL})
    foot = element([3, 0, 3], [13, 1, 13], {f: planks for f in ALL})
    return {"parent": "minecraft:block/block", "textures": {
        "particle": block_tex("trans_planks"), "planks": block_tex("trans_planks"), "log": block_tex("stripped_trans_log")},
        "elements": [top, post, foot]}


def argb_int(rgb):
    value = 0xFF000000 | rgb
    return value - (1 << 32) if value >= 1 << 31 else value


# ============================================================================================ recipes
def generate_recipes():
    R = recipe
    # wood
    R("trans_planks", shapeless("trans_planks", ["#transdimension:trans_logs"], 4, group="planks"))
    R("trans_wood", shaped("trans_wood", ["##", "##"], {"#": "trans_log"}, 3, group="bark"))
    R("stripped_trans_wood", shaped("stripped_trans_wood", ["##", "##"], {"#": "stripped_trans_log"}, 3, group="bark"))
    R("trans_stairs", shaped("trans_stairs", ["#  ", "## ", "###"], {"#": "trans_planks"}, 4, group="wooden_stairs"))
    R("trans_slab", shaped("trans_slab", ["###"], {"#": "trans_planks"}, 6, group="wooden_slab"))
    R("trans_fence", shaped("trans_fence", ["W#W", "W#W"], {"#": "minecraft:stick", "W": "trans_planks"}, 3, category="misc", group="wooden_fence"))
    R("trans_fence_gate", shaped("trans_fence_gate", ["#W#", "#W#"], {"#": "minecraft:stick", "W": "trans_planks"}, 1, category="redstone", group="wooden_fence_gate"))
    R("trans_door", shaped("trans_door", ["##", "##", "##"], {"#": "trans_planks"}, 3, category="redstone", group="wooden_door"))
    R("trans_trapdoor", shaped("trans_trapdoor", ["###", "###"], {"#": "trans_planks"}, 2, category="redstone", group="wooden_trapdoor"))
    R("trans_button", shapeless("trans_button", ["trans_planks"], 1, category="redstone", group="wooden_button"))
    R("trans_pressure_plate", shaped("trans_pressure_plate", ["##"], {"#": "trans_planks"}, 1, category="redstone", group="wooden_pressure_plate"))
    R("trans_chair", shaped("trans_chair", ["P  ", "PWP", "S S"], {"P": "trans_planks", "W": "trans_wool", "S": "minecraft:stick"}, 2, category="misc"))
    R("trans_table", shaped("trans_table", ["SSS", " L ", " P "], {"S": "trans_slab", "L": "stripped_trans_log", "P": "trans_planks"}, 1, category="misc"))
    # stone
    R("trans_stone_from_smelting", smelting("trans_stone", "trans_cobblestone"))
    R("trans_stone_bricks", shaped("trans_stone_bricks", ["##", "##"], {"#": "trans_stone"}, 4))
    R("cracked_trans_stone_bricks", smelting("cracked_trans_stone_bricks", "trans_stone_bricks"))
    R("chiseled_trans_stone_bricks", shaped("chiseled_trans_stone_bricks", ["#", "#"], {"#": "trans_stone_brick_slab"}, 1))
    for block, base in (("trans_stone", "trans_stone"), ("trans_cobblestone", "trans_cobblestone"),
                        ("trans_stone_brick", "trans_stone_bricks"), ("trans_sandstone", "trans_sandstone")):
        R(f"{block}_stairs", shaped(f"{block}_stairs", ["#  ", "## ", "###"], {"#": base}, 4))
        R(f"{block}_slab", shaped(f"{block}_slab", ["###"], {"#": base}, 6))
        R(f"{block}_stairs_from_{base}_stonecutting", stonecutting(f"{block}_stairs", base))
        R(f"{block}_slab_from_{base}_stonecutting", stonecutting(f"{block}_slab", base, 2))
    for block, base in (("trans_cobblestone", "trans_cobblestone"), ("trans_stone_brick", "trans_stone_bricks"),
                        ("trans_sandstone", "trans_sandstone")):
        R(f"{block}_wall", shaped(f"{block}_wall", ["###", "###"], {"#": base}, 6, category="misc"))
        R(f"{block}_wall_from_{base}_stonecutting", stonecutting(f"{block}_wall", base))
    R("trans_stone_bricks_from_trans_stone_stonecutting", stonecutting("trans_stone_bricks", "trans_stone"))
    R("chiseled_trans_stone_bricks_from_trans_stone_stonecutting", stonecutting("chiseled_trans_stone_bricks", "trans_stone"))
    R("trans_stone_brick_stairs_from_trans_stone_stonecutting", stonecutting("trans_stone_brick_stairs", "trans_stone"))
    R("trans_stone_brick_slab_from_trans_stone_stonecutting", stonecutting("trans_stone_brick_slab", "trans_stone", 2))
    R("trans_stone_brick_wall_from_trans_stone_stonecutting", stonecutting("trans_stone_brick_wall", "trans_stone"))
    R("trans_stone_button", shapeless("trans_stone_button", ["trans_stone"], 1, category="redstone"))
    R("trans_stone_pressure_plate", shaped("trans_stone_pressure_plate", ["##"], {"#": "trans_stone"}, 1, category="redstone"))
    # sandstone
    R("trans_sandstone", shaped("trans_sandstone", ["##", "##"], {"#": "trans_sand"}, 1))
    R("cut_trans_sandstone", shaped("cut_trans_sandstone", ["##", "##"], {"#": "trans_sandstone"}, 4))
    R("chiseled_trans_sandstone", shaped("chiseled_trans_sandstone", ["#", "#"], {"#": "trans_sandstone_slab"}, 1))
    R("cut_trans_sandstone_from_trans_sandstone_stonecutting", stonecutting("cut_trans_sandstone", "trans_sandstone"))
    R("chiseled_trans_sandstone_from_trans_sandstone_stonecutting", stonecutting("chiseled_trans_sandstone", "trans_sandstone"))
    # glass and wool: trans sand smelts into trans glass, which takes dye like vanilla glass
    R("trans_glass", smelting("trans_glass", "trans_sand"))
    R("trans_stained_glass", shaped("trans_stained_glass", ["PBW", "B#B", "WBP"],
                                    {"#": "trans_glass", "P": "minecraft:pink_dye", "B": "minecraft:light_blue_dye", "W": "minecraft:white_dye"}, 5, group="stained_glass"))
    R("trans_pink_stained_glass", shaped("trans_pink_stained_glass", ["###", "#X#", "###"], {"#": "trans_glass", "X": "transdimension:pride_blossom"}, 8, group="stained_glass"))
    R("trans_blue_stained_glass", shaped("trans_blue_stained_glass", ["###", "#X#", "###"], {"#": "trans_glass", "X": "transdimension:trans_crystal"}, 8, group="stained_glass"))
    for glass_block in ("trans_glass", "trans_stained_glass", "trans_pink_stained_glass", "trans_blue_stained_glass"):
        R(f"{glass_block}_pane", shaped(f"{glass_block}_pane", ["###", "###"], {"#": glass_block}, 16, category="misc", group="stained_glass_pane"))
    R("trans_wool", shaped("trans_wool", ["L", "P", "W"], {"L": "minecraft:light_blue_wool", "P": "minecraft:pink_wool", "W": "minecraft:white_wool"}, 3, group="wool"))
    R("trans_carpet", shaped("trans_carpet", ["##"], {"#": "trans_wool"}, 3, category="misc", group="carpet"))
    R("trans_lantern", shaped("trans_lantern", ["XXX", "X#X", "XXX"], {"#": "minecraft:torch", "X": "transdimension:trans_crystal"}, 2, category="misc"))
    # deepslate: cobbled -> polished -> bricks -> tiles, like vanilla
    R("trans_deepslate", smelting("trans_deepslate", "cobbled_trans_deepslate"))
    R("polished_trans_deepslate", shaped("polished_trans_deepslate", ["##", "##"], {"#": "cobbled_trans_deepslate"}, 4))
    R("trans_deepslate_bricks", shaped("trans_deepslate_bricks", ["##", "##"], {"#": "polished_trans_deepslate"}, 4))
    R("trans_deepslate_tiles", shaped("trans_deepslate_tiles", ["##", "##"], {"#": "trans_deepslate_bricks"}, 4))
    R("chiseled_trans_deepslate", shaped("chiseled_trans_deepslate", ["#", "#"], {"#": "cobbled_trans_deepslate_slab"}, 1))
    R("cracked_trans_deepslate_bricks", smelting("cracked_trans_deepslate_bricks", "trans_deepslate_bricks"))
    R("cracked_trans_deepslate_tiles", smelting("cracked_trans_deepslate_tiles", "trans_deepslate_tiles"))
    deepslate_chain = ["cobbled_trans_deepslate", "polished_trans_deepslate", "trans_deepslate_bricks", "trans_deepslate_tiles"]
    prefixes = {"cobbled_trans_deepslate": "cobbled_trans_deepslate", "polished_trans_deepslate": "polished_trans_deepslate",
                "trans_deepslate_bricks": "trans_deepslate_brick", "trans_deepslate_tiles": "trans_deepslate_tile"}
    for i, base in enumerate(deepslate_chain):
        stone_recipes(prefixes[base], base, wall=True, cut_from=deepslate_chain[:i])
        for earlier in deepslate_chain[:i]:
            R(f"{base}_from_{earlier}_stonecutting", stonecutting(base, earlier))
    R("chiseled_trans_deepslate_from_cobbled_trans_deepslate_stonecutting", stonecutting("chiseled_trans_deepslate", "cobbled_trans_deepslate"))
    # rose granite, pearl diorite, sky andesite: made from trans cobblestone and quartz, like their vanilla twins
    R("trans_diorite", shaped("trans_diorite", ["CQ", "QC"], {"C": "trans_cobblestone", "Q": "minecraft:quartz"}, 2))
    R("trans_andesite", shapeless("trans_andesite", ["trans_diorite", "trans_cobblestone"], 2))
    R("trans_granite", shapeless("trans_granite", ["trans_diorite", "minecraft:quartz"], 1))
    for stone in ("granite", "diorite", "andesite"):
        R(f"polished_trans_{stone}", shaped(f"polished_trans_{stone}", ["##", "##"], {"#": f"trans_{stone}"}, 4))
        R(f"polished_trans_{stone}_from_trans_{stone}_stonecutting", stonecutting(f"polished_trans_{stone}", f"trans_{stone}"))
        stone_recipes(f"trans_{stone}", f"trans_{stone}", wall=True)
        stone_recipes(f"polished_trans_{stone}", f"polished_trans_{stone}", wall=False, cut_from=[f"trans_{stone}"])
    # ores smelt and blast into their vanilla goods
    for ore, (result, xp) in ORE_SMELTING.items():
        for block in (f"trans_{ore}_ore", f"trans_deepslate_{ore}_ore"):
            R(f"{result.split(':')[1]}_from_smelting_{block}", {**smelting(result, block, xp, category="misc"), "group": result.split(":")[1]})
            R(f"{result.split(':')[1]}_from_blasting_{block}", {**blasting(result, block, xp), "group": result.split(":")[1]})
    R("trans_crystal_from_smelting_deepslate_ore", smelting("trans_crystal", "trans_deepslate_crystal_ore", 1.0, category="misc"))
    R("trans_crystal_from_blasting_deepslate_ore", blasting("trans_crystal", "trans_deepslate_crystal_ore", 1.0))
    # crystal
    R("trans_crystal_block", shaped("trans_crystal_block", ["###", "###", "###"], {"#": "trans_crystal"}, 1))
    R("trans_crystal_from_block", shapeless("trans_crystal", ["trans_crystal_block"], 9, category="misc"))
    for flower_id, dye, count in (("trans_tulip", "light_blue_dye", 1), ("pearl_daisy", "white_dye", 1), ("sky_bell", "light_blue_dye", 1),
                                  ("flag_lily", "white_dye", 1), ("lavender_puff", "purple_dye", 1), ("trans_orchid", "pink_dye", 1),
                                  ("heart_bloom", "pink_dye", 2), ("pride_peony", "pink_dye", 2), ("trans_petals", "pink_dye", 1)):
        R(f"{dye}_from_{flower_id}", shapeless(f"minecraft:{dye}", [flower_id], count, category="misc", group=dye))
    R("trans_moss_carpet", shaped("trans_moss_carpet", ["##"], {"#": "trans_moss_block"}, 3, category="misc", group="carpet"))
    R("trans_sapling_from_blossoms", shapeless("trans_sapling", ["minecraft:cherry_sapling", "transdimension:pride_blossom", "transdimension:trans_crystal"], 1, category="misc"))


# ============================================================================================ other tags, names, sounds
def generate_misc():
    for b in ("trans_dirt",):
        tag("block", "dirt", b)
        tag("item", "dirt", b)
    tag("block", "grass_blocks", "trans_grass_block")
    tag("item", "grass_blocks", "trans_grass_block")
    tag("block", "sand", "trans_sand")
    tag("item", "sand", "trans_sand")
    tag("block", "smelts_to_glass", "trans_sand")
    tag("item", "smelts_to_glass", "trans_sand")
    rocks = ("trans_stone", "trans_granite", "trans_diorite", "trans_andesite", "trans_deepslate")
    tag("block", "base_stone_overworld", *rocks)
    tag("block", "overworld_carver_replaceables", *rocks, "trans_dirt", "trans_grass_block", "trans_sand", "trans_sandstone", "trans_gravel")
    # What the realm's ore and rock-blob features may replace.
    tag("block", "trans_stone_ore_replaceables", "trans_stone", "trans_granite", "trans_diorite", "trans_andesite", ns=NS)
    tag("block", "trans_deepslate_ore_replaceables", "trans_deepslate", ns=NS)
    tag("block", "trans_base_stone", *rocks, ns=NS)
    tag("item", "stone_tool_materials", "trans_cobblestone", "cobbled_trans_deepslate")
    tag("item", "stone_crafting_materials", "trans_cobblestone", "cobbled_trans_deepslate")
    tag("block", "enderman_holdable", "trans_gravel")
    for t in ("animals_spawnable_on", "rabbits_spawnable_on", "wolves_spawnable_on", "foxes_spawnable_on",
              "frogs_spawnable_on", "parrots_spawnable_on", "valid_spawn"):
        tag("block", t, "trans_grass_block")
    tag("block", "rabbits_spawnable_on", "trans_sand")
    tag("block", "camels_spawnable_on", "trans_sand")
    tag("block", "goats_spawnable_on", "trans_stone")
    tag("block", "azalea_grows_on", "trans_sand")
    tag("block", "sniffer_diggable_block", "trans_dirt", "trans_grass_block")
    tag("block", "enderman_holdable", "trans_dirt", "trans_grass_block", "trans_sand", "pride_blossom")
    tag("block", "flowers", "pride_blossom")
    tag("item", "flowers", "pride_blossom")
    tag("block", "bee_attractive", "pride_blossom")

    # tools and armor
    tag("item", "swords", "trans_sword")
    tag("item", "pickaxes", "trans_pickaxe")
    tag("item", "axes", "trans_axe")
    tag("item", "shovels", "trans_shovel")
    tag("item", "hoes", "trans_hoe")
    tag("item", "head_armor", "trans_helmet")
    tag("item", "chest_armor", "trans_chestplate")
    tag("item", "leg_armor", "trans_leggings")
    tag("item", "foot_armor", "trans_boots")

    # the mod's own tags
    tag("item", "repairs_trans_gear", "trans_crystal", ns=NS)
    tag("block", "incorrect_for_trans_tool", "#minecraft:incorrect_for_diamond_tool", ns=NS)
    # The Pride Oven is a job site villagers can claim.
    tag("point_of_interest_type", "acquirable_job_site", "transdimension:pride_oven")

    # Names that aren't blocks.
    items = {
        "trans_crystal": "Trans Crystal", "trans_sword": "Trans Crystal Sword", "trans_pickaxe": "Trans Crystal Pickaxe",
        "trans_axe": "Trans Crystal Axe", "trans_shovel": "Trans Crystal Shovel", "trans_hoe": "Trans Crystal Hoe",
        "trans_helmet": "Trans Crystal Helmet", "trans_chestplate": "Trans Crystal Chestplate",
        "trans_leggings": "Trans Crystal Leggings", "trans_boots": "Trans Crystal Boots",
        "trans_donut": "Trans Donut", "trans_cookie": "Sprinkle Cookie", "trans_cupcake": "Pride Cupcake",
        "trans_macaron": "Trans Macaron", "trans_boba": "Trans Boba Tea", "silly_cat_spawn_egg": "Silly Cat Spawn Egg",
    }
    for k, v in items.items():
        name(k, v, kind="item")
    model("silly_cat_spawn_egg", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/silly_cat_spawn_egg"}}, kind="item")
    item_def("silly_cat_spawn_egg", f"{NS}:item/silly_cat_spawn_egg")
    NAMES.update({
        "itemGroup.transdimension.trans_dimension": "Trans Dimension",
        "entity.transdimension.silly_cat": "Silly Cat",
        "entity.transdimension.villager.trans_baker": "Trans Baker",
        "effect.transdimension.slobbered": "Slobbered",
        "subtitles.transdimension.entity.silly_cat.ambient": "Silly Cat meows",
        "subtitles.transdimension.entity.silly_cat.purr": "Silly Cat purrs",
        "subtitles.transdimension.entity.silly_cat.lick": "Silly Cat gives a big lick",
        "subtitles.transdimension.entity.silly_cat.hurt": "Silly Cat hurts",
        "subtitles.transdimension.entity.silly_cat.death": "Silly Cat dies",
        "subtitles.transdimension.block.pride_oven.crackle": "Pride Oven crackles",
        "advancements.transdimension.goober.title": "Goober!",
        "advancements.transdimension.goober.description": "Say the magic word and enter the Trans Realm",
        "advancements.transdimension.slobbered.title": "Big Smooch",
        "advancements.transdimension.slobbered.description": "Get licked by a Silly Cat",
        "advancements.transdimension.trans_village.title": "Home Sweet Home",
        "advancements.transdimension.trans_village.description": "Find a Trans Village",
        "message.transdimension.welcome": "❤ Welcome to the Trans Realm! Say \"Goober\" again to go home. ❤",
        "message.transdimension.realm_missing": "The Trans Realm didn't load. Check the server log for data pack errors.",
    })


BIOMES = {
    "trans_meadow": "Trans Meadow", "trans_forest": "Blossom Forest", "trans_beach": "Trans Beach",
    "trans_ocean": "Trans Ocean", "deep_trans_ocean": "Deep Trans Ocean", "trans_river": "Pastel River",
    "sugar_dunes": "Sugar Dunes", "lavender_marsh": "Lavender Marsh", "frosted_fields": "Frosted Fields",
    "crystal_grove": "Crystal Grove", "pastel_peaks": "Pastel Peaks", "heartwood_grove": "Heartwood Grove",
    "crystal_caves": "Crystal Caves", "pearlwood_forest": "Pearlwood Forest", "bluebell_woods": "Bluebell Woods",
    "twilight_thicket": "Twilight Thicket", "candy_floss_grove": "Candy Floss Grove", "pastel_lush_caves": "Pastel Lush Caves",
}


def generate_sounds():
    def event(name, volume=1.0, pitch=1.0):
        return {"name": name, "type": "event", "volume": volume, "pitch": pitch}

    sounds = {
        "entity.silly_cat.ambient": {"subtitle": "subtitles.transdimension.entity.silly_cat.ambient",
                                     "sounds": [event("minecraft:entity.cat.ambient", pitch=1.15), event("minecraft:entity.cat.purreow", pitch=1.1)]},
        "entity.silly_cat.purr": {"subtitle": "subtitles.transdimension.entity.silly_cat.purr",
                                  "sounds": [event("minecraft:entity.cat.purr")]},
        "entity.silly_cat.lick": {"subtitle": "subtitles.transdimension.entity.silly_cat.lick", "sounds": [
            {"name": "minecraft:random/drink", "volume": 0.7, "pitch": 1.6},
            {"name": "minecraft:mob/cat/eat1", "volume": 0.8, "pitch": 1.3},
            {"name": "minecraft:mob/cat/eat2", "volume": 0.8, "pitch": 1.3}]},
        "entity.silly_cat.hurt": {"subtitle": "subtitles.transdimension.entity.silly_cat.hurt",
                                  "sounds": [event("minecraft:entity.cat.hurt", pitch=1.1)]},
        "entity.silly_cat.death": {"subtitle": "subtitles.transdimension.entity.silly_cat.death",
                                   "sounds": [event("minecraft:entity.cat.death", pitch=1.1)]},
        "block.pride_oven.crackle": {"subtitle": "subtitles.transdimension.block.pride_oven.crackle",
                                     "sounds": [event("minecraft:block.smoker.smoke", volume=0.8, pitch=1.1)]},
        "ui.intro.bell": {"sounds": [{"name": "minecraft:note/bell"}]},
        "ui.intro.chime": {"sounds": [{"name": "minecraft:block/amethyst/shimmer", "volume": 0.9}]},
        "ui.intro.whoosh": {"sounds": [event("minecraft:entity.player.attack.sweep", volume=0.8, pitch=0.6)]},
    }
    write(os.path.join(ASSETS, "sounds.json"), sounds)


def write_tags():
    for (kind, ns, path), values in sorted(TAGS.items()):
        write(os.path.join(DATA, ns, "tags", kind, f"{path}.json"), {"replace": False, "values": sorted(values)})


def write_lang():
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path, encoding="utf-8") as f:
        lang = json.load(f)
    lang = {k: v for k, v in lang.items() if "trans_water" not in k and not k.startswith("biome.")}
    for biome, english in BIOMES.items():
        lang[f"biome.transdimension.{biome}"] = english
    lang.update(NAMES)
    ordered = dict(sorted(lang.items(), key=lambda kv: (kv[0].split(".")[0], kv[0])))
    write(path, ordered)


def remove_stale():
    stale = [
        os.path.join(ASSETS, "blockstates", "trans_water.json"),
        os.path.join(ASSETS, "models", "block", "trans_water.json"),
        os.path.join(ASSETS, "models", "block", "trans_log_horizontal.json"),
        os.path.join(ASSETS, "items", "trans_water_bucket.json"),
        os.path.join(ASSETS, "models", "item", "trans_water_bucket.json"),
        os.path.join(ASSETS, "models", "item", "pride_blossom.json"),
        os.path.join(DATA, "minecraft", "tags", "fluid", "water.json"),
        os.path.join(DATA, NS, "recipe", "glass_from_trans_sand.json"),
    ]
    for p in stale:
        if os.path.exists(p):
            os.remove(p)
    # Old tag files are regenerated from scratch (worldgen tags belong to generate_villages.py).
    for root, _, files in os.walk(os.path.join(DATA, "minecraft", "tags")):
        if os.sep + "worldgen" in root:
            continue
        for f in files:
            os.remove(os.path.join(root, f))
    for sub in ("block", "item"):
        d = os.path.join(DATA, NS, "tags", sub)
        if os.path.isdir(d):
            for f in os.listdir(d):
                if f.endswith(".json"):
                    os.remove(os.path.join(d, f))


def main():
    remove_stale()
    generate_blocks()
    generate_recipes()
    generate_misc()
    generate_sounds()
    write_tags()
    write_lang()
    print(f"Wrote {len(TAGS)} tags and {len(NAMES)} names.")


if __name__ == "__main__":
    main()
