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


def simple_item(item, english):
    """A flat item sprite (textures/item/<item>.png) with its model, item definition and name."""
    model(item, {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}}, kind="item")
    item_def(item, f"{NS}:item/{item}")
    name(item, english, kind="item")


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
                {"count": 4.0, "function": "minecraft:set_count"},
                {"enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops", "function": "minecraft:apply_bonus"}],
             "name": rid(drop)},
            {"type": "minecraft:item", "functions": [{"count": 2.0, "function": "minecraft:set_count"},
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

# The nine village cat plushes (textures: PLUSHES in generate_textures.py; order matches ModBlocks.PLUSHES).
PLUSH_NAMES = {"silly_cat_plush": "Silly Cat Plush", "trans_cat_plush": "Trans Cat Plush",
               "midnight_cat_plush": "Midnight Cat Plush", "biscuit_cat_plush": "Biscuit Cat Plush",
               "patches_cat_plush": "Patches Cat Plush", "mochi_cat_plush": "Mochi Cat Plush",
               "pearl_cat_plush": "Pearl Cat Plush", "bubblegum_cat_plush": "Bubblegum Cat Plush",
               "bluebell_cat_plush": "Bluebell Cat Plush"}

# Paintings (textures: PAINTINGS in generate_textures.py): name -> (width, height, title, author).
PAINTINGS = {
    "silly_cat_portrait": (3, 3, "Portrait of a Silly Cat", "Maddie"),
    "big_lick": (3, 3, "The Big Lick", "Maddie"),
    "trans_heart": (2, 2, "Trans Heart", "Maddie"),
    "flag_of_the_realm": (3, 2, "Flag of the Realm", "Maddie"),
    "pastel_sunrise": (4, 2, "Pastel Sunrise", "Maddie"),
    "crystal_bloom": (1, 1, "Crystal Bloom", "The Silly Cat"),
    "the_egg_house": (2, 2, "The Egg House", "Maddie"),
    "plush_party": (2, 1, "Plush Party", "The Silly Cat"),
}

# Everything Maddie says (MaddieDialogueScreen picks the lines; "option.*" are the player's answers).
MADDIE_DIALOGUE = {
    "greeting": "Oh! A visitor! Hi hi, I'm Maddie! Welcome to the Egg House, way up here in the clouds. "
                "Watch your step, it's a long way down!",
    "greeting_again": "Hey, it's you again! How are the wings treating you? Not too many crash landings, I hope!",
    "who": "I'm Maddie! I live up here with my garden, a whole lot of pink and way too many eggs. I made this place so "
           "anyone who finds it has somewhere cozy to rest. You're valid, you know that? Just checking!",
    "place": "This is the Trans Realm! Pink skies, heart-shaped clouds and Silly Cats who lick you better. My house "
             "floats so I see the sunrise first. Down below, every village hides a little cat plush in one of its "
             "houses. Can you find all nine?",
    "gifts": "Actually... yes! I made these for travelers like you. A Trans Wand: it shoots sparkly hearts at anything "
             "mean. And Trans Wings! Crouch to charge, jump to launch, then tap jump to flap. Promise you'll fly safe?",
    "gifts_given": "I already gave you my wand and wings, silly! Crouch on the ground to charge, jump to launch, tap "
                   "jump while gliding to flap, and crouch in the air to hover down gently.",
    "thanks": "Yay! Come back and visit whenever you like. And remember: you are loved, just the way you are!",
    "option.who": "Who are you?",
    "option.place": "What is this place?",
    "option.gifts": "Do you have anything for me?",
    "option.accept": "I promise! Thank you, Maddie!",
    "option.back": "Maybe later.",
    "option.bye": "Bye, Maddie!",
}

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
    if tool:
        mine(block, tool)
    loot(block, loot_self(block))
    tag("block", "stairs", block)
    tag("item", "stairs", block)


def slab(block, english, full_block, textures, tool):
    bs = from_template("cherry_slab", "cherry_slab", block)
    double_ns, double_path = rid(full_block).split(":")
    bs["variants"]["type=double"]["model"] = f"{double_ns}:block/{double_path}"
    blockstate(block, bs)
    tex = {k: block_tex(v) for k, v in textures.items()}
    model(block, {"parent": "minecraft:block/slab", "textures": tex})
    model(f"{block}_top", {"parent": "minecraft:block/slab_top", "textures": tex})
    item_def(block, f"{NS}:block/{block}")
    name(block, english)
    if tool:
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


# Grass-coloured plants (trans grass and ferns) are tinted with the biome's grass colour; in the hand they're trans pink.
def grass_item_tint():
    return [{"type": "minecraft:constant", "value": argb_int(0xF5A9B8)}]


def cross_plant(block, english, potted=None, potted_english=None, tinted=False):
    blockstate(block, {"variants": {"": {"model": f"{NS}:block/{block}"}}})
    model(block, {"parent": "minecraft:block/tinted_cross" if tinted else "minecraft:block/cross", "textures": {"cross": block_tex(block)}})
    model(block, {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex(block)}}, kind="item")
    item_def(block, f"{NS}:item/{block}", tints=grass_item_tint() if tinted else None)
    name(block, english)
    loot(block, loot_self(block))
    if potted:
        blockstate(potted, {"variants": {"": {"model": f"{NS}:block/{potted}"}}})
        model(potted, {"parent": "minecraft:block/tinted_flower_pot_cross" if tinted else "minecraft:block/flower_pot_cross",
                       "textures": {"plant": block_tex(block)}})
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
    # The pink ancient city's gate frame. Like vanilla's reinforced deepslate it takes an age to break and drops nothing.
    blockstate("reinforced_trans_deepslate", {"variants": {"": {"model": f"{NS}:block/reinforced_trans_deepslate"}}})
    model("reinforced_trans_deepslate", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": block_tex("reinforced_trans_deepslate_top"), "side": block_tex("reinforced_trans_deepslate_side"),
        "bottom": block_tex("reinforced_trans_deepslate_bottom")}})
    item_def("reinforced_trans_deepslate", f"{NS}:block/reinforced_trans_deepslate")
    name("reinforced_trans_deepslate", "Reinforced Trans Deepslate")
    loot("reinforced_trans_deepslate", {"type": "minecraft:block", "random_sequence": f"{NS}:blocks/reinforced_trans_deepslate"})
    for immune in ("dragon_immune", "wither_immune", "features_cannot_replace"):
        tag("block", immune, "reinforced_trans_deepslate")

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
    # Pastel prism: the common, glowing crystal rock of geodes and crystal spikes (its clusters drop prism shards).
    cube("pastel_prism", "Pastel Prism")
    tag("block", "crystal_sound_blocks", "pastel_prism")
    blockstate("trans_crystal_cluster", from_template("amethyst_cluster", "amethyst_cluster", "trans_crystal_cluster"))
    model("trans_crystal_cluster", {"parent": "minecraft:block/cross", "textures": {"cross": block_tex("trans_crystal_cluster")}})
    model("trans_crystal_cluster", {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex("trans_crystal_cluster")},
                                    "display": {"head": {"translation": [0, 14, -5]}}}, kind="item")
    item_def("trans_crystal_cluster", f"{NS}:item/trans_crystal_cluster")
    name("trans_crystal_cluster", "Prism Cluster")
    mine("trans_crystal_cluster", "pickaxe")
    loot("trans_crystal_cluster", loot_cluster("trans_crystal_cluster", "prism_shard"))

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

    # Each kind of leaves drops the sapling of its own wood (trans leaves drop trans saplings).
    for leaves_id, english, sapling in (("trans_leaves", "Trans Leaves", "trans_sapling"), ("pearl_leaves", "Pearl Leaves", "pearl_sapling"),
                                        ("sky_leaves", "Sky Leaves", "sky_sapling"), ("blush_leaves", "Blush Leaves", "blush_sapling"),
                                        ("twilight_leaves", "Twilight Leaves", "twilight_sapling"),
                                        ("flowering_blush_leaves", "Flowering Blush Leaves", "blush_sapling")):
        blockstate(leaves_id, {"variants": {"": {"model": f"{NS}:block/{leaves_id}"}}})
        model(leaves_id, {"parent": "minecraft:block/leaves", "textures": {"all": block_tex(leaves_id)}})
        item_def(leaves_id, f"{NS}:block/{leaves_id}")
        name(leaves_id, english)
        mine(leaves_id, "hoe")
        loot(leaves_id, loot_leaves(leaves_id, sapling))
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

    # Flag stripes on the sides, plain trans blue on the top and bottom.
    blockstate("trans_wool", {"variants": {"": {"model": f"{NS}:block/trans_wool"}}})
    model("trans_wool", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "bottom": block_tex("trans_wool_top"), "side": block_tex("trans_wool"), "top": block_tex("trans_wool_top")}})
    item_def("trans_wool", f"{NS}:block/trans_wool")
    name("trans_wool", "Trans Pride Wool")
    loot("trans_wool", loot_self("trans_wool"))
    tag("block", "wool", "trans_wool")
    tag("item", "wool", "trans_wool")
    blockstate("trans_carpet", {"variants": {"": {"model": f"{NS}:block/trans_carpet"}}})
    model("trans_carpet", {"parent": "minecraft:block/carpet", "textures": {"wool": block_tex("trans_wool")}})
    item_def("trans_carpet", f"{NS}:block/trans_carpet")
    name("trans_carpet", "Trans Pride Carpet")
    loot("trans_carpet", loot_self("trans_carpet"))
    tag("block", "wool_carpets", "trans_carpet")
    tag("item", "wool_carpets", "trans_carpet")

    # The trans bed is built from block models, like every bed in 26.2. The foot has three tops: plain, and the left or
    # right half of the heart that two trans beds side by side share (TransBedBlock sets the "heart" property).
    head_textures = {"particle": block_tex("trans_planks"), "north": block_tex("trans_bed_head_north"),
                     "down": block_tex("trans_bed_down"), "up": block_tex("trans_bed_head_up"),
                     "east": block_tex("trans_bed_head_east"), "west": block_tex("trans_bed_head_west")}
    model("trans_bed_head", {"parent": "minecraft:block/template_bed_head", "textures": head_textures})
    foot_textures = {"particle": block_tex("trans_planks"), "south": block_tex("trans_bed_foot_south"),
                     "down": block_tex("trans_bed_down"), "east": block_tex("trans_bed_foot_east"),
                     "west": block_tex("trans_bed_foot_west")}
    for suffix in ("", "_left", "_right"):
        model(f"trans_bed_foot{suffix}", {"parent": "minecraft:block/template_bed_foot",
                                          "textures": {**foot_textures, "up": block_tex(f"trans_bed_foot_up{suffix}")}})
    bed_variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        turn = {"y": y} if y else {}
        bed_variants[f"facing={facing},part=head"] = {"model": f"{NS}:block/trans_bed_head", **turn}
        for heart, suffix in (("none", ""), ("left", "_left"), ("right", "_right")):
            bed_variants[f"facing={facing},heart={heart},part=foot"] = {"model": f"{NS}:block/trans_bed_foot{suffix}", **turn}
    blockstate("trans_bed", {"variants": bed_variants})
    identity = [0.0, 0.0, 0.0, 1.0]
    write(os.path.join(ASSETS, "items", "trans_bed.json"), {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": f"{NS}:block/trans_bed_head"},
        {"type": "minecraft:model", "model": f"{NS}:block/trans_bed_foot", "transformation": {
            "left_rotation": identity, "right_rotation": identity, "scale": [1.0, 1.0, 1.0], "translation": [0.0, 0.0, 1.0]}}]}})
    name("trans_bed", "Trans Bed")
    loot_like_vanilla("trans_bed", "pink_bed")
    tag("block", "beds", "trans_bed")
    tag("item", "beds", "trans_bed")

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

    # ---- cat plushes: one shared model, a texture each; the plush spot marker is invisible
    model("cat_plush", plush_model())
    for plush, english in PLUSH_NAMES.items():
        furniture_blockstate(plush)
        model(plush, {"parent": f"{NS}:block/cat_plush", "textures": {"cat": block_tex(plush)}})
        item_def(plush, f"{NS}:block/{plush}")
        name(plush, english)
        loot(plush, loot_self(plush))
        tag("block", "plushes", plush, ns=NS)
        tag("item", "plushes", plush, ns=NS)
    blockstate("plush_spot", {"variants": {"": {"model": "minecraft:block/air"}}})
    name("plush_spot", "Plush Spot")

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


# ============================================================================================ furniture, wool and paths (round 5)
FLAG_COLOURS = (("light_blue", "Light Blue"), ("pink", "Pink"), ("white", "White"))


def cushion_model():
    """A plump floor cushion (the shared parent; each colour fills in #top and #side)."""
    faces = {f: {"texture": "#side"} for f in ALL}
    faces["up"] = {"texture": "#top"}
    faces["down"]["cullface"] = "down"
    return {"parent": "minecraft:block/block", "textures": {"particle": "#side"},
            "elements": [{"from": [1, 0, 1], "to": [15, 5, 15], "faces": faces}]}


def stool_model():
    """Four legs joined by two rungs, a round-cornered seat and a trans flag pad."""
    log = {f: "#log" for f in ALL}
    legs = [element([x, 0, z], [x + 1.5, 9, z + 1.5], log) for x, z in ((3.5, 3.5), (11, 3.5), (3.5, 11), (11, 11))]
    rungs = [element([5, 3, 3.75], [11, 4, 4.75], log), element([5, 3, 11.25], [11, 4, 12.25], log)]
    seat = element([3, 9, 3], [13, 10, 13], {f: "#planks" for f in ALL})
    pad = element([3.5, 10, 3.5], [12.5, 11, 12.5], {f: "#cushion" for f in ALL})
    return {"parent": "minecraft:block/block", "textures": {
        "particle": block_tex("trans_planks"), "planks": block_tex("trans_planks"), "log": block_tex("stripped_trans_log"),
        "cushion": block_tex("trans_wool")}, "elements": legs + rungs + [seat, pad]}


def armchair_model():
    """An armchair in the flag's colours, its open side facing north: a light blue frame and arms, a pink seat cushion
    and a white cushion against the back. FurnitureBlock.ARMCHAIR_SHAPE matches it."""
    frame = {f: "#frame" for f in ALL}
    feet = [element([x, 0, z], [x + 2, 2, z + 2], {f: "#wood" for f in ALL}) for x, z in ((2, 2), (12, 2), (2, 12), (12, 12))]
    body = element([1, 2, 1], [15, 7, 15], frame)
    seat = element([3, 7, 1.5], [13, 10, 12], {f: "#seat" for f in ALL})
    arms = [element([1, 7, 1], [3, 13, 15], frame), element([13, 7, 1], [15, 13, 15], frame)]
    back = element([1, 7, 12], [15, 16, 15], frame)
    back_cushion = element([3, 10, 11], [13, 15, 12], {f: "#back" for f in ALL})
    return {"parent": "minecraft:block/block", "textures": {
        "particle": block_tex("trans_fabric_light_blue"), "frame": block_tex("trans_fabric_light_blue"),
        "seat": block_tex("trans_fabric_pink"), "back": block_tex("trans_fabric_white"), "wood": block_tex("stripped_trans_log")},
        "elements": feet + [body, seat] + arms + [back, back_cushion]}


def lamp_model(on):
    """A table lamp: a pearly foot and stem under a flag-striped shade (brighter when it's on)."""
    base = {f: "#base" for f in ALL}
    shade = {f: "#shade" for f in ALL}
    shade["up"] = shade["down"] = "#shade_top"
    suffix = "_on" if on else ""
    return {"parent": "minecraft:block/block", "textures": {
        "particle": block_tex("trans_lamp_shade" + suffix), "base": block_tex("trans_lamp_base"),
        "shade": block_tex("trans_lamp_shade" + suffix), "shade_top": block_tex("trans_lamp_shade_top" + suffix)},
        "elements": [element([5, 0, 5], [11, 2, 11], base), element([7, 2, 7], [9, 8, 9], base), element([3, 8, 3], [13, 15, 13], shade)]}


def generate_furniture():
    # ---- the trans dirt path (shovel on trans grass or dirt; the camps' footpaths)
    blockstate("trans_dirt_path", {"variants": {"": [
        {"model": f"{NS}:block/trans_dirt_path", **({"y": y} if y else {})} for y in (0, 90, 180, 270)]}})
    model("trans_dirt_path", {"parent": "minecraft:block/dirt_path", "textures": {
        "particle": block_tex("trans_dirt"), "top": block_tex("trans_dirt_path_top"), "side": block_tex("trans_dirt_path_side"),
        "bottom": block_tex("trans_dirt")}})
    item_def("trans_dirt_path", f"{NS}:block/trans_dirt_path")
    name("trans_dirt_path", "Trans Dirt Path")
    mine("trans_dirt_path", "shovel")
    loot("trans_dirt_path", loot_self("trans_dirt_path", drop="trans_dirt"))

    # ---- wool stairs and slabs in the flag's colours (vanilla wool textures; the camps' tents)
    for colour, english in FLAG_COLOURS:
        tex = {k: f"minecraft:block/{colour}_wool" for k in ("bottom", "top", "side")}
        stairs(f"{colour}_wool_stairs", f"{english} Wool Stairs", None, tex, None)
        slab(f"{colour}_wool_slab", f"{english} Wool Slab", f"minecraft:{colour}_wool", tex, None)
        tag("block", "wool", f"{colour}_wool_stairs", f"{colour}_wool_slab")

    # ---- cushions: one shared model; they're seats (FurnitureBlock)
    model("cushion", cushion_model())
    for colour, english in FLAG_COLOURS:
        block = f"{colour}_cushion"
        furniture_blockstate(block)
        model(block, {"parent": f"{NS}:block/cushion", "textures": {
            "top": block_tex(f"{colour}_cushion_top"), "side": block_tex(f"{colour}_cushion_side")}})
        item_def(block, f"{NS}:block/{block}")
        name(block, f"{english} Cushion")
        loot(block, loot_self(block))
        tag("block", "cushions", block, ns=NS)
        tag("item", "cushions", block, ns=NS)

    # ---- stool, armchair, lamp and bookshelf
    for block, english, build, tool in (("trans_stool", "Trans Stool", stool_model, "axe"),
                                        ("trans_armchair", "Trans Armchair", armchair_model, None)):
        furniture_blockstate(block)
        model(block, build())
        item_def(block, f"{NS}:block/{block}")
        name(block, english)
        if tool:
            mine(block, tool)
        loot(block, loot_self(block))
    blockstate("trans_lamp", {"variants": {"lit=false": {"model": f"{NS}:block/trans_lamp"},
                                           "lit=true": {"model": f"{NS}:block/trans_lamp_on"}}})
    model("trans_lamp", lamp_model(False))
    model("trans_lamp_on", lamp_model(True))
    item_def("trans_lamp", f"{NS}:block/trans_lamp_on")
    name("trans_lamp", "Trans Lamp")
    loot("trans_lamp", loot_self("trans_lamp"))
    blockstate("trans_bookshelf", {"variants": {"": {"model": f"{NS}:block/trans_bookshelf"}}})
    model("trans_bookshelf", {"parent": "minecraft:block/cube_column", "textures": {
        "end": block_tex("trans_planks"), "side": block_tex("trans_bookshelf")}})
    item_def("trans_bookshelf", f"{NS}:block/trans_bookshelf")
    name("trans_bookshelf", "Trans Bookshelf")
    mine("trans_bookshelf", "axe")
    loot("trans_bookshelf", table("trans_bookshelf", [{"entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "conditions": [SILK], "name": rid("trans_bookshelf")},
        {"type": "minecraft:item", "functions": [{"add": False, "count": 3.0, "function": "minecraft:set_count"},
                                                 {"function": "minecraft:explosion_decay"}], "name": "minecraft:book"}]}],
        "rolls": 1.0}]))
    tag("block", "enchantment_power_provider", "trans_bookshelf")
    name("seat", "Seat", kind="entity")

    R = recipe
    for colour, _ in FLAG_COLOURS:
        wool = f"minecraft:{colour}_wool"
        R(f"{colour}_wool_stairs", shaped(f"{colour}_wool_stairs", ["#  ", "## ", "###"], {"#": wool}, 4, group="woolen_stairs"))
        R(f"{colour}_wool_slab", shaped(f"{colour}_wool_slab", ["###"], {"#": wool}, 6, group="woolen_slab"))
        R(f"{colour}_cushion", shaped(f"{colour}_cushion", ["###"], {"#": f"{colour}_wool_slab"}, 1, category="misc", group="cushion"))
    R("trans_stool", shaped("trans_stool", ["C", "F"], {"C": "trans_carpet", "F": "trans_fence"}, 1, category="misc"))
    R("trans_armchair", shaped("trans_armchair", ["B  ", "BPB", "T T"], {
        "B": "minecraft:light_blue_wool", "P": "minecraft:pink_wool", "T": "trans_planks"}, 1, category="misc"))
    R("trans_lamp", shaped("trans_lamp", ["W", "T", "S"], {"W": "trans_wool", "T": "minecraft:torch", "S": "trans_slab"}, 1,
                           category="misc"))
    R("trans_bookshelf", shaped("trans_bookshelf", ["###", "XXX", "###"], {"#": "trans_planks", "X": "minecraft:book"}, 1))


# ============================================================================================ trans vegetation
def plant_tags(block, edible=False):
    for t in ("replaceable", "replaceable_by_mushrooms", "replaceable_by_trees"):
        tag("block", t, block)
    if edible:
        tag("block", "edible_for_sheep", block)


def cross_model(block, texture=None, item_texture=None, item=True, tinted=False):
    """A plant drawn as two crossed planes. Most trans plants carry their own colours; tinted ones (grass) take the
    biome's grass colour."""
    model(block, {"parent": "minecraft:block/tinted_cross" if tinted else "minecraft:block/cross",
                  "textures": {"cross": block_tex(texture or block)}})
    if item:
        model(block, {"parent": "minecraft:item/generated", "textures": {"layer0": item_texture or block_tex(texture or block)}}, kind="item")
        item_def(block, f"{NS}:item/{block}", tints=grass_item_tint() if tinted else None)


def double_plant(block, english, vanilla, tinted=False):
    blockstate(block, {"variants": {"half=lower": {"model": f"{NS}:block/{block}_bottom"},
                                    "half=upper": {"model": f"{NS}:block/{block}_top"}}})
    for half in ("bottom", "top"):
        model(f"{block}_{half}", {"parent": "minecraft:block/tinted_cross" if tinted else "minecraft:block/cross",
                                  "textures": {"cross": block_tex(f"{block}_{half}")}})
    model(block, {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex(f"{block}_top")}}, kind="item")
    item_def(block, f"{NS}:item/{block}", tints=grass_item_tint() if tinted else None)
    name(block, english)
    return block


def generate_vegetation():
    # ---- trans cave vines (TransCaveVinesBlock): vanilla's cave vines in lilac, with pink and blue berries; Trans Glow
    # Berries are their item, as glow berries are vanilla's
    for part in ("cave_vines", "cave_vines_plant"):
        block = f"trans_{part}"
        blockstate(block, from_template(part, part, block))
        for lit in ("", "_lit"):
            model(f"{block}{lit}", {"parent": "minecraft:block/cross", "textures": {"cross": block_tex(f"{block}{lit}")}})
        loot_like_vanilla(block, part, {"minecraft:glow_berries": "trans_glow_berries"})
        for t in ("cave_vines", "climbable", "bee_growables"):
            tag("block", t, block)
    name("trans_cave_vines", "Trans Cave Vines")
    name("trans_cave_vines_plant", "Trans Cave Vines Plant")
    simple_item("trans_glow_berries", "Trans Glow Berries")
    tag("item", "fox_food", "trans_glow_berries")

    # ---- grass, ferns, bushes
    blockstate("trans_short_grass", {"variants": {"": {"model": f"{NS}:block/trans_short_grass"}}})
    cross_model("trans_short_grass", tinted=True)
    name("trans_short_grass", "Trans Grass")
    loot_like_vanilla("trans_short_grass", "short_grass")
    plant_tags("trans_short_grass", edible=True)
    double_plant("tall_trans_grass", "Tall Trans Grass", "tall_grass", tinted=True)
    loot_like_vanilla("tall_trans_grass", "tall_grass", {"minecraft:short_grass": "trans_short_grass"})
    plant_tags("tall_trans_grass")
    cross_plant("trans_fern", "Trans Fern", "potted_trans_fern", "Potted Trans Fern", tinted=True)
    loot_like_vanilla("trans_fern", "fern")
    plant_tags("trans_fern", edible=True)
    double_plant("large_trans_fern", "Large Trans Fern", "large_fern", tinted=True)
    loot_like_vanilla("large_trans_fern", "large_fern", {"minecraft:fern": "trans_fern"})
    plant_tags("large_trans_fern")
    for block, english, vanilla, edible in (("pastel_bush", "Pastel Bush", "bush", False),
                                            ("short_sugar_grass", "Short Sugar Grass", "short_dry_grass", True),
                                            ("tall_sugar_grass", "Tall Sugar Grass", "tall_dry_grass", True)):
        blockstate(block, {"variants": {"": {"model": f"{NS}:block/{block}"}}})
        cross_model(block)
        name(block, english)
        loot_like_vanilla(block, vanilla)
        plant_tags(block, edible=edible)
    # ---- the Sugar Dunes' white cactus and dry sugar bushes, and trans sugar cane by the water
    blockstate("trans_cactus", {"variants": {"": {"model": f"{NS}:block/trans_cactus"}}})
    model("trans_cactus", {"parent": "minecraft:block/cactus", "textures": {
        "particle": block_tex("trans_cactus_side"), "bottom": block_tex("trans_cactus_bottom"), "top": block_tex("trans_cactus_top"),
        "side": block_tex("trans_cactus_side")}})
    item_def("trans_cactus", f"{NS}:block/trans_cactus")
    name("trans_cactus", "White Cactus")
    loot_like_vanilla("trans_cactus", "cactus")
    # Vanilla's pink cactus flowers may sit on it (and grow on it).
    tag("block", "support_override_cactus_flower", "trans_cactus")
    blockstate("dry_sugar_bush", {"variants": {"": {"model": f"{NS}:block/dry_sugar_bush"}}})
    cross_model("dry_sugar_bush")
    name("dry_sugar_bush", "Dry Sugar Bush")
    loot_like_vanilla("dry_sugar_bush", "dead_bush")
    plant_tags("dry_sugar_bush")
    blockstate("trans_sugar_cane", {"variants": {"": {"model": f"{NS}:block/trans_sugar_cane"}}})
    cross_model("trans_sugar_cane", item_texture=f"{NS}:item/trans_sugar_cane")
    name("trans_sugar_cane", "Trans Sugar Cane")
    loot_like_vanilla("trans_sugar_cane", "sugar_cane")
    R = recipe
    R("white_dye_from_trans_cactus", smelting("minecraft:white_dye", "trans_cactus", xp=1.0, category="misc"))
    R("sugar_from_trans_sugar_cane", shapeless("minecraft:sugar", ["trans_sugar_cane"], 1, category="misc", group="sugar"))
    R("paper_from_trans_sugar_cane", shaped("minecraft:paper", ["###"], {"#": "trans_sugar_cane"}, 3, category="misc"))

    # The firefly bush: a cross plus a glowing (light emission 15) cross of pink and blue fireflies, like vanilla's.
    blockstate("trans_firefly_bush", {"variants": {"": {"model": f"{NS}:block/trans_firefly_bush"}}})
    model("trans_firefly_bush", {"parent": "minecraft:block/cross_emissive", "textures": {
        "cross": block_tex("trans_firefly_bush"), "cross_emissive": block_tex("trans_firefly_bush_emissive")}})
    model("trans_firefly_bush", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/trans_firefly_bush"}}, kind="item")
    item_def("trans_firefly_bush", f"{NS}:item/trans_firefly_bush")
    name("trans_firefly_bush", "Trans Firefly Bush")
    loot_like_vanilla("trans_firefly_bush", "firefly_bush")
    for t in ("replaceable_by_mushrooms", "replaceable_by_trees"):
        tag("block", t, "trans_firefly_bush")

    # ---- seagrass, kelp, lily pads
    blockstate("trans_seagrass", {"variants": {"": {"model": f"{NS}:block/trans_seagrass"}}})
    model("trans_seagrass", {"parent": "minecraft:block/template_seagrass", "textures": {"texture": block_tex("trans_seagrass")}})
    model("trans_seagrass", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/trans_seagrass"}}, kind="item")
    item_def("trans_seagrass", f"{NS}:item/trans_seagrass")
    name("trans_seagrass", "Trans Seagrass")
    loot_like_vanilla("trans_seagrass", "seagrass")
    plant_tags("trans_seagrass")
    tag("item", "turtle_food", "trans_seagrass")
    blockstate("tall_trans_seagrass", {"variants": {"half=lower": {"model": f"{NS}:block/tall_trans_seagrass_bottom"},
                                                    "half=upper": {"model": f"{NS}:block/tall_trans_seagrass_top"}}})
    for half in ("bottom", "top"):
        model(f"tall_trans_seagrass_{half}", {"parent": "minecraft:block/template_seagrass",
                                              "textures": {"texture": block_tex(f"tall_trans_seagrass_{half}")}})
    name("tall_trans_seagrass", "Tall Trans Seagrass")
    loot_like_vanilla("tall_trans_seagrass", "tall_seagrass", {"minecraft:seagrass": "trans_seagrass"})
    plant_tags("tall_trans_seagrass")

    blockstate("trans_kelp", {"variants": {"": {"model": f"{NS}:block/trans_kelp"}}})
    cross_model("trans_kelp", item_texture=f"{NS}:item/trans_kelp")
    name("trans_kelp", "Trans Kelp")
    loot_like_vanilla("trans_kelp", "kelp")
    blockstate("trans_kelp_plant", {"variants": {"": {"model": f"{NS}:block/trans_kelp_plant"}}})
    cross_model("trans_kelp_plant", item=False)
    name("trans_kelp_plant", "Trans Kelp Plant")
    loot_like_vanilla("trans_kelp_plant", "kelp_plant", {"minecraft:kelp": "trans_kelp"})

    blockstate("trans_lily_pad", {"variants": {"": [{"model": f"{NS}:block/trans_lily_pad", **({"y": y} if y else {})}
                                                    for y in (0, 90, 180, 270)]}})
    model("trans_lily_pad", {"parent": "minecraft:block/lily_pad", "textures": {
        "particle": block_tex("trans_lily_pad"), "texture": block_tex("trans_lily_pad")}})
    model("trans_lily_pad", {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex("trans_lily_pad")}}, kind="item")
    item_def("trans_lily_pad", f"{NS}:item/trans_lily_pad")
    name("trans_lily_pad", "Trans Lily Pad")
    loot_like_vanilla("trans_lily_pad", "lily_pad")
    tag("block", "inside_step_sound_blocks", "trans_lily_pad")
    tag("block", "frog_prefer_jump_to", "trans_lily_pad")

    # ---- corals: blush pink, sky blue and pearl white, each with a dead twin. They are kept out of vanilla's coral
    # tags (vanilla's warm ocean reefs pick their blocks from those tags); the realm's reefs use our own feature.
    for colour, english in (("blush", "Blush"), ("sky", "Sky"), ("pearl", "Pearl")):
        for dead in (False, True):
            prefix = "dead_" if dead else ""
            label = f"Dead {english}" if dead else english
            block = f"{prefix}{colour}_coral_block"
            cube(block, f"{label} Coral Block", drop=None)
            if dead:
                loot_like_vanilla(block, "dead_tube_coral_block")
            else:
                loot_like_vanilla(block, "tube_coral_block", {"minecraft:dead_tube_coral_block": f"dead_{colour}_coral_block"})
            plant = f"{prefix}{colour}_coral"
            blockstate(plant, {"variants": {"": {"model": f"{NS}:block/{plant}"}}})
            cross_model(plant)
            name(plant, f"{label} Coral")
            loot_like_vanilla(plant, "dead_tube_coral" if dead else "tube_coral")
            fan = f"{prefix}{colour}_coral_fan"
            wall = f"{prefix}{colour}_coral_wall_fan"
            blockstate(fan, {"variants": {"": {"model": f"{NS}:block/{fan}"}}})
            model(fan, {"parent": "minecraft:block/coral_fan", "textures": {"fan": block_tex(fan)}})
            model(fan, {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex(fan)}}, kind="item")
            item_def(fan, f"{NS}:item/{fan}")
            name(fan, f"{label} Coral Fan")
            loot_like_vanilla(fan, "dead_tube_coral_fan" if dead else "tube_coral_fan")
            blockstate(wall, {"variants": {f"facing={f}": {"model": f"{NS}:block/{wall}", **({"y": y} if y else {})}
                                           for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
            model(wall, {"parent": "minecraft:block/coral_wall_fan", "textures": {"fan": block_tex(fan)}})
            name(wall, f"{label} Coral Wall Fan")
            # Wall fans drop the fan item (vanilla's borrow the fan's loot table; ours have their own).
            loot_like_vanilla(wall, "dead_tube_coral_fan" if dead else "tube_coral_fan",
                              {f"minecraft:{'dead_' if dead else ''}tube_coral_fan": fan})
            tag("block", "trans_corals", plant, fan, ns=NS)
            tag("block", "trans_wall_corals", wall, ns=NS)
        tag("block", "trans_coral_blocks", f"{colour}_coral_block", ns=NS)



# ============================================================================================ woods, flowers and hedges
# The themed forests' own woods (ModBlocks.WoodFamily): id prefix -> English name.
WOOD_FAMILIES = {"pearl": "Pearl", "sky": "Sky", "twilight": "Twilight", "blush": "Blush"}
# More small flowers (dye colour, dye count), tall flowers and floral hedges.
NEW_FLOWERS = {"blush_carnation": ("Blush Carnation", "pink_dye"), "pearl_snowdrop": ("Pearl Snowdrop", "white_dye"),
               "forget_me_not": ("Forget-Me-Not", "light_blue_dye"), "trans_rose": ("Trans Rose", "pink_dye"),
               "star_bloom": ("Starbloom", "white_dye"), "fairy_bell": ("Fairy Bells", "purple_dye")}
TALL_FLOWERS = {"sky_delphinium": ("Sky Delphinium", "light_blue_dye"), "blush_foxglove": ("Blush Foxglove", "pink_dye"),
                "pearl_lupine": ("Pearl Lupine", "white_dye")}
HEDGES = {"blossom_hedge": "Blossom Hedge", "bluebell_hedge": "Bluebell Hedge", "pearl_hedge": "Pearl Hedge"}


def wood_family(prefix, english):
    """Blockstates, models, loot, tags and names for one wood family; recipes are in wood_family_recipes()."""
    log(f"{prefix}_log", f"{english} Log", f"{prefix}_log", f"{prefix}_log_top")
    log(f"stripped_{prefix}_log", f"Stripped {english} Log", f"stripped_{prefix}_log", f"stripped_{prefix}_log_top")
    wood(f"{prefix}_wood", f"{english} Wood", f"{prefix}_log")
    wood(f"stripped_{prefix}_wood", f"Stripped {english} Wood", f"stripped_{prefix}_log")
    logs = [f"{prefix}_log", f"stripped_{prefix}_log", f"{prefix}_wood", f"stripped_{prefix}_wood"]
    tag("block", f"{prefix}_logs", *logs, ns=NS)
    tag("item", f"{prefix}_logs", *logs, ns=NS)
    tag("block", "logs_that_burn", f"#{NS}:{prefix}_logs")
    tag("item", "logs_that_burn", f"#{NS}:{prefix}_logs")
    tag("block", "overworld_natural_logs", f"{prefix}_log")
    planks = f"{prefix}_planks"
    cube(planks, f"{english} Planks", tool="axe")
    tag("block", "planks", planks)
    tag("item", "planks", planks)
    tex = {"bottom": planks, "top": planks, "side": planks}
    stairs(f"{prefix}_stairs", f"{english} Stairs", planks, tex, "axe")
    tag("block", "wooden_stairs", f"{prefix}_stairs")
    tag("item", "wooden_stairs", f"{prefix}_stairs")
    slab(f"{prefix}_slab", f"{english} Slab", planks, tex, "axe")
    tag("block", "wooden_slabs", f"{prefix}_slab")
    tag("item", "wooden_slabs", f"{prefix}_slab")
    fence(f"{prefix}_fence", f"{english} Fence", planks)
    fence_gate(f"{prefix}_fence_gate", f"{english} Fence Gate", planks)
    door(f"{prefix}_door", f"{english} Door", f"{prefix}_door_top", f"{prefix}_door_bottom")
    trapdoor(f"{prefix}_trapdoor", f"{english} Trapdoor", f"{prefix}_trapdoor")
    button(f"{prefix}_button", f"{english} Button", planks, "axe", wooden=True)
    pressure_plate(f"{prefix}_pressure_plate", f"{english} Pressure Plate", planks, "axe", wooden=True)
    cross_plant(f"{prefix}_sapling", f"{english} Sapling", f"potted_{prefix}_sapling", f"Potted {english} Sapling")
    tag("block", "saplings", f"{prefix}_sapling")
    tag("item", "saplings", f"{prefix}_sapling")


def wood_family_recipes(prefix):
    R = recipe
    planks = f"{prefix}_planks"
    R(planks, shapeless(planks, [f"#{NS}:{prefix}_logs"], 4, group="planks"))
    R(f"{prefix}_wood", shaped(f"{prefix}_wood", ["##", "##"], {"#": f"{prefix}_log"}, 3, group="bark"))
    R(f"stripped_{prefix}_wood", shaped(f"stripped_{prefix}_wood", ["##", "##"], {"#": f"stripped_{prefix}_log"}, 3, group="bark"))
    R(f"{prefix}_stairs", shaped(f"{prefix}_stairs", ["#  ", "## ", "###"], {"#": planks}, 4, group="wooden_stairs"))
    R(f"{prefix}_slab", shaped(f"{prefix}_slab", ["###"], {"#": planks}, 6, group="wooden_slab"))
    R(f"{prefix}_fence", shaped(f"{prefix}_fence", ["W#W", "W#W"], {"#": "minecraft:stick", "W": planks}, 3, category="misc", group="wooden_fence"))
    R(f"{prefix}_fence_gate", shaped(f"{prefix}_fence_gate", ["#W#", "#W#"], {"#": "minecraft:stick", "W": planks}, 1, category="redstone",
                                     group="wooden_fence_gate"))
    R(f"{prefix}_door", shaped(f"{prefix}_door", ["##", "##", "##"], {"#": planks}, 3, category="redstone", group="wooden_door"))
    R(f"{prefix}_trapdoor", shaped(f"{prefix}_trapdoor", ["###", "###"], {"#": planks}, 2, category="redstone", group="wooden_trapdoor"))
    R(f"{prefix}_button", shapeless(f"{prefix}_button", [planks], 1, category="redstone", group="wooden_button"))
    R(f"{prefix}_pressure_plate", shaped(f"{prefix}_pressure_plate", ["##"], {"#": planks}, 1, category="redstone", group="wooden_pressure_plate"))


def tall_flower(block, english):
    blockstate(block, from_template("peony", "peony", block))
    for half in ("top", "bottom"):
        model(f"{block}_{half}", {"parent": "minecraft:block/cross", "textures": {"cross": block_tex(f"{block}_{half}")}})
    model(block, {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex(f"{block}_top")}}, kind="item")
    item_def(block, f"{NS}:item/{block}")
    name(block, english)
    loot_like_vanilla(block, "peony", {"minecraft:peony": block})
    for t in ("flowers", "bee_attractive", "replaceable_by_trees"):
        tag("block", t, block)
    tag("item", "flowers", block)
    tag("item", "bee_food", block)


def generate_woods_and_flowers():
    for prefix, english in WOOD_FAMILIES.items():
        wood_family(prefix, english)
    for flower_id, (english, _) in NEW_FLOWERS.items():
        cross_plant(flower_id, english, f"potted_{flower_id}", f"Potted {english}")
        for t in ("small_flowers", "bee_attractive"):
            tag("block", t, flower_id)
        tag("item", "small_flowers", flower_id)
        tag("item", "bee_food", flower_id)
    # The Starbloom's blue heart glows: an emissive cross like vanilla's open eyeblossom (block, item and pot).
    model("star_bloom", {"parent": "minecraft:block/cross_emissive", "textures": {
        "cross": block_tex("star_bloom"), "cross_emissive": block_tex("star_bloom_emissive")}})
    model("star_bloom", {"parent": "minecraft:item/generated", "textures": {
        "layer0": block_tex("star_bloom"), "layer1": block_tex("star_bloom_emissive")}}, kind="item")
    model("potted_star_bloom", {"parent": "minecraft:block/flower_pot_cross_emissive", "textures": {
        "cross_emissive": block_tex("star_bloom_emissive"), "plant": block_tex("star_bloom")}})
    for block, (english, _) in TALL_FLOWERS.items():
        tall_flower(block, english)
    for block, english in HEDGES.items():
        blockstate(block, {"variants": {"": {"model": f"{NS}:block/{block}"}}})
        model(block, {"parent": "minecraft:block/leaves", "textures": {"all": block_tex(block)}})
        item_def(block, f"{NS}:block/{block}")
        name(block, english)
        mine(block, "hoe")
        loot(block, loot_self(block))
        tag("block", "hedges", block, ns=NS)
        tag("item", "hedges", block, ns=NS)


def generate_trans_prismarine():
    """Trans prismarine and sea lanterns for the mermaid ruins: vanilla's prismarine family in the flag's colours. Eight
    vanilla blocks around a prism shard make eight trans ones."""
    stone_family("trans_prismarine", "Trans Prismarine", "trans_prismarine", "Trans Prismarine")
    stone_family("trans_prismarine_bricks", "Trans Prismarine Bricks", "trans_prismarine_brick", "Trans Prismarine Brick",
                 wall_too=False)
    stone_family("dark_trans_prismarine", "Dark Trans Prismarine", "dark_trans_prismarine", "Dark Trans Prismarine",
                 wall_too=False)
    cube("trans_sea_lantern", "Trans Sea Lantern", tool=None, drop=None)
    loot_like_vanilla("trans_sea_lantern", "sea_lantern")
    for ours, vanilla in (("trans_prismarine", "prismarine"), ("trans_prismarine_bricks", "prismarine_bricks"),
                          ("dark_trans_prismarine", "dark_prismarine"), ("trans_sea_lantern", "sea_lantern")):
        recipe(ours, shaped(ours, ["###", "#S#", "###"], {"#": f"minecraft:{vanilla}", "S": "prism_shard"}, 8))
    stone_recipes("trans_prismarine", "trans_prismarine")
    stone_recipes("trans_prismarine_brick", "trans_prismarine_bricks", wall=False)
    stone_recipes("dark_trans_prismarine", "dark_trans_prismarine", wall=False)


def generate_woods_and_flowers_recipes():
    R = recipe
    for prefix in WOOD_FAMILIES:
        wood_family_recipes(prefix)
    for block, (_, dye) in {**NEW_FLOWERS, **TALL_FLOWERS}.items():
        count = 2 if block in TALL_FLOWERS else 1
        R(f"{dye}_from_{block}", shapeless(f"minecraft:{dye}", [block], count, category="misc", group=dye))
    # Hedges: leaves of the matching colour around a flower.
    for hedge, leaves, flower in (("blossom_hedge", "blush_leaves", "trans_rose"), ("bluebell_hedge", "sky_leaves", "forget_me_not"),
                                  ("pearl_hedge", "pearl_leaves", "pearl_snowdrop")):
        R(hedge, shaped(hedge, ["LLL", "LFL", "LLL"], {"L": leaves, "F": flower}, 8))


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


def plush_model():
    """The shared cat plush model (it looks north). UVs follow PLUSH_UV in generate_textures.py: 32x32 textures at one
    texel per model pixel, so a UV unit (1/16 of the texture) is two texels."""
    def uv(box):
        return [c / 2 for c in box]

    head_uv = {"up": (6, 0, 14, 6), "down": (14, 0, 22, 6), "west": (0, 6, 6, 12), "north": (6, 6, 14, 12),
               "east": (14, 6, 20, 12), "south": (20, 6, 28, 12)}
    body_uv = {"up": (5, 12, 11, 17), "down": (11, 12, 17, 17), "west": (0, 17, 5, 22), "north": (5, 17, 11, 22),
               "east": (11, 17, 16, 22), "south": (16, 17, 22, 22)}
    tail_uv = {"up": (22, 12, 24, 17), "down": (24, 12, 26, 17), "north": (26, 12, 28, 14), "south": (28, 12, 30, 14),
               "east": (22, 17, 27, 19), "west": (27, 17, 32, 19)}
    ear, ear_inner, muzzle, paw = (0, 22, 2, 24), (2, 22, 4, 24), (4, 22, 7, 24), (8, 22, 10, 24)

    def box(frm, to, faces):
        return {"from": frm, "to": to, "faces": {
            f: {"uv": uv(b), "texture": "#cat", **({"cullface": "down"} if f == "down" and frm[1] == 0 else {})}
            for f, b in faces.items()}}

    elements = [
        box([5, 0, 6], [11, 5, 11], body_uv),
        box([4, 4, 4], [12, 10, 10], head_uv),
        box([11, 0, 7], [13, 2, 12], tail_uv),
    ]
    for x in (4.5, 9.5):
        elements.append(box([x, 10, 6], [x + 2, 12, 8], {f: (ear_inner if f == "north" else ear) for f in ALL}))
    elements.append(box([6.5, 4.5, 3.5], [9.5, 6.5, 4], {f: muzzle for f in ALL if f != "south"}))
    for x in (5.5, 8.5):
        elements.append(box([x, 0, 4.5], [x + 2, 1.5, 6.5], {f: paw for f in ALL}))
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#cat"},
        "display": {
            "gui": {"rotation": [25, 200, 0], "translation": [0, 1.5, 0], "scale": [0.95, 0.95, 0.95]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 1.5, 0], "scale": [0.85, 0.85, 0.85]},
            "head": {"rotation": [0, 180, 0], "translation": [0, 9.5, 0], "scale": [1, 1, 1]},
        },
        "elements": elements,
    }


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
    R("trans_blue_stained_glass", shaped("trans_blue_stained_glass", ["###", "#X#", "###"], {"#": "trans_glass", "X": "transdimension:prism_shard"}, 8, group="stained_glass"))
    for glass_block in ("trans_glass", "trans_stained_glass", "trans_pink_stained_glass", "trans_blue_stained_glass"):
        R(f"{glass_block}_pane", shaped(f"{glass_block}_pane", ["###", "###"], {"#": glass_block}, 16, category="misc", group="stained_glass_pane"))
    R("trans_wool", shaped("trans_wool", ["L", "P", "W"], {"L": "minecraft:light_blue_wool", "P": "minecraft:pink_wool", "W": "minecraft:white_wool"}, 3, group="wool"))
    R("trans_carpet", shaped("trans_carpet", ["##"], {"#": "trans_wool"}, 3, category="misc", group="carpet"))
    R("trans_boat", shaped("trans_boat", ["# #", "###"], {"#": "trans_planks"}, category="misc", group="boat"))
    R("trans_chest_boat", shapeless("trans_chest_boat", ["minecraft:chest", "trans_boat"], category="misc", group="chest_boat"))
    R("trans_bed", shaped("trans_bed", ["###", "XXX"], {"#": "trans_wool", "X": "#minecraft:planks"}, category="misc", group="bed"))
    R("trans_lantern", shaped("trans_lantern", ["XSX", "X#X", "XXX"], {"#": "minecraft:torch", "X": "minecraft:iron_nugget",
                                                                     "S": "transdimension:prism_shard"}, 1, category="misc"))
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
    # Crystal gear is diamond gear upgraded with a Crystal Alloy (a crystal and four prism shards) and the Crystal
    # Upgrade template in a smithing table, like netherite. Enchantments carry over. The template is found in mermaid
    # ruins; it can also be made from scratch (dearer) or copied like vanilla's (two for one).
    R("crystal_alloy", shapeless("crystal_alloy", ["trans_crystal", "prism_shard", "prism_shard", "prism_shard", "prism_shard"],
                                 category="misc"))
    R("crystal_upgrade_smithing_template", shaped("crystal_upgrade_smithing_template", ["#X#", "#P#", "###"],
                                                  {"#": "minecraft:diamond", "X": "trans_crystal", "P": "dark_trans_prismarine"},
                                                  category="misc"))
    R("crystal_upgrade_smithing_template_copy", shaped("crystal_upgrade_smithing_template", ["#S#", "#P#", "###"],
                                                       {"#": "minecraft:diamond", "S": "crystal_upgrade_smithing_template",
                                                        "P": "trans_prismarine"}, 2, category="misc"))
    for piece in ("sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots"):
        R(f"trans_{piece}", {"type": "minecraft:smithing_transform", "template": rid("crystal_upgrade_smithing_template"),
                             "base": f"minecraft:diamond_{piece}", "addition": rid("crystal_alloy"),
                             "result": {"id": rid(f"trans_{piece}")}})
    for flower_id, dye, count in (("trans_tulip", "light_blue_dye", 1), ("pearl_daisy", "white_dye", 1), ("sky_bell", "light_blue_dye", 1),
                                  ("flag_lily", "white_dye", 1), ("lavender_puff", "purple_dye", 1), ("trans_orchid", "pink_dye", 1),
                                  ("heart_bloom", "pink_dye", 2), ("pride_peony", "pink_dye", 2), ("trans_petals", "pink_dye", 1)):
        R(f"{dye}_from_{flower_id}", shapeless(f"minecraft:{dye}", [flower_id], count, category="misc", group=dye))
    R("dried_kelp_from_smelting_trans_kelp", smelting("minecraft:dried_kelp", "trans_kelp", 0.1, category="food"))
    for plant, dye in (("trans_lily_pad", "pink_dye"), ("pastel_bush", "purple_dye")):
        R(f"{dye}_from_{plant}", shapeless(f"minecraft:{dye}", [plant], 1, category="misc", group=dye))
    R("trans_moss_carpet", shaped("trans_moss_carpet", ["##"], {"#": "trans_moss_block"}, 3, category="misc", group="carpet"))
    R("trans_sapling_from_blossoms", shapeless("trans_sapling", ["minecraft:cherry_sapling", "transdimension:pride_blossom", "transdimension:prism_shard"], 1, category="misc"))
    R("pastel_prism", shaped("pastel_prism", ["##", "##"], {"#": "prism_shard"}, 1))


# ============================================================================================ the realm's creatures
def entity_loot(entity, pools):
    write(os.path.join(DATA, NS, "loot_table", "entities", f"{entity}.json"),
          {"type": "minecraft:entity", "pools": pools, "random_sequence": f"{NS}:entities/{entity}"})


def counted(item, lo, hi, looting=1.0):
    """One loot entry of lo..hi of an item, plus up to `looting` more per level of Looting."""
    return {"type": "minecraft:item", "functions": [
        {"count": {"type": "minecraft:uniform", "max": float(hi), "min": float(lo)}, "function": "minecraft:set_count"},
        {"count": {"type": "minecraft:uniform", "max": looting, "min": 0.0}, "enchantment": "minecraft:looting",
         "function": "minecraft:enchanted_count_increase"}], "name": rid(item)}


ON_FIRE_OR_SMELTING = {"condition": "minecraft:any_of", "terms": [
    {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:flags": {"is_on_fire": True}}},
    {"condition": "minecraft:entity_properties", "entity": "direct_attacker", "predicate": {"minecraft:equipment": {
        "mainhand": {"predicates": {"minecraft:enchantments": [{"enchantments": "#minecraft:smelts_loot"}]}}}}}]}


def generate_creatures():
    """Trans fish, trans endermen and pastel slimes: their items, spawn eggs, names, loot, recipes and tags, and the
    slimes' gel blocks. (The entities are in ModEntities; textures come from generate_textures.py creature_textures().)"""
    # ---- items
    for item, english in (("trans_fish", "Raw Trans Fish"), ("cooked_trans_fish", "Cooked Trans Fish"),
                          ("trans_fish_bucket", "Bucket of Trans Fish"), ("trans_fish_spawn_egg", "Trans Fish Spawn Egg"),
                          ("trans_pearl", "Trans Pearl"), ("trans_crystal_pearl", "Trans Crystal Pearl"),
                          ("trans_enderman_spawn_egg", "Trans Enderman Spawn Egg"), ("pastel_gel", "Pastel Gel"),
                          ("gumdrop", "Gumdrop"), ("pastel_slime_spawn_egg", "Pastel Slime Spawn Egg"),
                          ("bottled_fairy", "Bottled Fairy"), ("fairy_spawn_egg", "Fairy Spawn Egg")):
        simple_item(item, english)
    NAMES.update({
        "entity.transdimension.trans_fish": "Trans Fish",
        "entity.transdimension.trans_enderman": "Trans Enderman",
        "entity.transdimension.pastel_slime": "Pastel Slime",
        "entity.transdimension.fairy": "Fairy",
        "item.transdimension.bottled_fairy.lore": "Hold it and it saves you from death once",
        "item.transdimension.bottled_fairy.lore2": "Use a glass bottle on a wild fairy to catch one",
        "item.transdimension.trans_crystal_pearl.lore": "Twelve of these awaken a Fairy Portal",
        "item.transdimension.gumdrop.lore": "Pastel slimes adore these: feed one a few to tame it",
    })

    # ---- gel blocks: vanilla's slime block model (a jelly cube around a firmer core) with our textures
    for block, english in (("pink_gel_block", "Pink Gel Block"), ("blue_gel_block", "Blue Gel Block")):
        blockstate(block, {"variants": {"": {"model": f"{NS}:block/{block}"}}})
        model(block, {"parent": "minecraft:block/slime_block", "textures": {"particle": block_tex(block), "texture": block_tex(block)}})
        item_def(block, f"{NS}:block/{block}")
        name(block, english)
        loot(block, loot_self(block))

    # ---- loot
    entity_loot("trans_fish", [
        {"entries": [{"type": "minecraft:item", "functions": [{"conditions": [ON_FIRE_OR_SMELTING], "function": "minecraft:furnace_smelt"}],
                      "name": rid("trans_fish")}], "rolls": 1.0},
        {"conditions": [{"chance": 0.05, "condition": "minecraft:random_chance"}],
         "entries": [{"type": "minecraft:item", "name": "minecraft:bone_meal"}], "rolls": 1.0}])
    entity_loot("trans_enderman", [{"entries": [counted("trans_pearl", 0, 1)], "rolls": 1.0}])
    entity_loot("pastel_slime", [{"entries": [counted("pastel_gel", 0, 2)], "rolls": 1.0}])
    # Trans clay (breaks into clay balls, smelts into pink terracotta) and trans sea pickles (vanilla's models, our texture).
    cube("trans_clay", "Trans Clay")
    mine("trans_clay", "shovel")
    loot_like_vanilla("trans_clay", "clay")
    recipe("pink_terracotta_from_trans_clay", smelting("minecraft:pink_terracotta", "trans_clay"))
    with open(os.path.join(TEMPLATES, "blockstates", "sea_pickle.json"), encoding="utf-8") as f:
        pickle_state = f.read()
    pickle_models = sorted(set(re.findall(r'"minecraft:block/([a-z_]+)"', pickle_state)))
    for m in pickle_models:
        pickle_state = pickle_state.replace(f'"minecraft:block/{m}"', f'"{NS}:block/trans_{m}"')
        model(f"trans_{m}", {"parent": f"minecraft:block/{m}", "textures": {"all": block_tex("trans_sea_pickle"),
                                                                            "particle": block_tex("trans_sea_pickle")}})
    blockstate("trans_sea_pickle", json.loads(pickle_state))
    model("trans_sea_pickle", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/trans_sea_pickle"}}, kind="item")
    item_def("trans_sea_pickle", f"{NS}:item/trans_sea_pickle")
    name("trans_sea_pickle", "Trans Sea Pickle")
    loot_like_vanilla("trans_sea_pickle", "sea_pickle")

    # Pink lava: the realm's lava (PinkLavaFluid). Its block only needs a particle texture; it counts as lava by tag.
    blockstate("pink_lava", {"variants": {"": {"model": f"{NS}:block/pink_lava"}}})
    model("pink_lava", {"textures": {"particle": block_tex("pink_lava_still")}})
    name("pink_lava", "Pink Lava")
    simple_item("pink_lava_bucket", "Pink Lava Bucket")
    tag("fluid", "lava", "pink_lava", "flowing_pink_lava")

    # Pink fire: the fire that burns in both realms (BaseFireBlockMixin). Vanilla fire's blockstate and model shapes,
    # with our two animated flame textures; it is fire by tag, so it burns, spreads and gets put out like fire.
    blockstate("pink_fire", from_template("fire", "fire", "pink_fire"))
    for shape in ("floor", "side", "side_alt", "up", "up_alt"):
        for frame in (0, 1):
            model(f"pink_fire_{shape}{frame}", {"parent": f"minecraft:block/template_fire_{shape}",
                                                 "textures": {"fire": block_tex(f"pink_fire_{frame}")}})
    name("pink_fire", "Pink Fire")
    for t in ("fire", "replaceable", "happy_ghast_avoids"):
        tag("block", t, "pink_fire")

    # Pink obsidian: what water makes of a pink lava source (LiquidBlockMixin). Tagged like obsidian.
    cube("pink_obsidian", "Pink Obsidian")
    for t in ("needs_diamond_tool", "dragon_immune"):
        tag("block", t, "pink_obsidian")

    # Trans dungeons (TransDungeonFeature): vanilla's dungeon loot with the realm's treasures mixed in.
    def entry(item, weight, lo=1, hi=1, enchant=False):
        e = {"type": "minecraft:item", "name": rid(item), "weight": weight}
        functions = []
        if hi > 1:
            functions.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}})
        if enchant:
            functions.append({"function": "minecraft:enchant_randomly"})
        if functions:
            e["functions"] = functions
        return e
    write(os.path.join(DATA, NS, "loot_table", "chests", "trans_dungeon.json"), {"type": "minecraft:chest", "pools": [
        {"rolls": {"type": "minecraft:uniform", "min": 1.0, "max": 3.0}, "entries": [
            entry("minecraft:saddle", 20), entry("minecraft:golden_apple", 15), entry("minecraft:enchanted_golden_apple", 2),
            entry("minecraft:name_tag", 20), entry("minecraft:music_disc_cat", 10), entry("minecraft:iron_horse_armor", 12),
            entry("minecraft:golden_horse_armor", 8), entry("minecraft:diamond_horse_armor", 4), entry("minecraft:book", 10, enchant=True),
            entry("minecraft:diamond", 6), entry("trans_pearl", 10, 1, 2)]},
        {"rolls": {"type": "minecraft:uniform", "min": 1.0, "max": 4.0}, "entries": [
            entry("minecraft:iron_ingot", 10, 1, 4), entry("minecraft:gold_ingot", 5, 1, 4), entry("minecraft:bread", 20),
            entry("gumdrop", 15, 2, 5), entry("prism_shard", 15, 2, 6), entry("minecraft:redstone", 15, 1, 4),
            entry("minecraft:coal", 15, 1, 4), entry("star_bloom", 5, 1, 2)]},
        {"rolls": 3.0, "entries": [entry("minecraft:bone", 10, 1, 8), entry("minecraft:gunpowder", 10, 1, 8),
                                   entry("minecraft:rotten_flesh", 10, 1, 8), entry("minecraft:string", 10, 1, 8)]},
        # One dungeon chest in four holds a map to the nearest pink ancient city (the candle ritual's city; its structure
        # tag is written by generate_ancient_city.py). The destination is the tag WITHOUT a '#'.
        {"rolls": 1.0, "entries": [{"type": "minecraft:empty", "weight": 3}, {
            "type": "minecraft:item", "name": "minecraft:map", "weight": 1, "functions": [
                {"function": "minecraft:exploration_map", "destination": f"{NS}:on_pink_ancient_city_maps",
                 "decoration": "minecraft:red_x", "zoom": 2, "search_radius": 100, "skip_existing_chunks": False},
                {"function": "minecraft:set_name", "name": {"translate": f"filled_map.{NS}.pink_ancient_city"},
                 "target": "item_name"}]}]}],
        "random_sequence": f"{NS}:chests/trans_dungeon"})

    # Fairies are much nicer met than fought (they give gifts), but they do leave some glitter behind.
    entity_loot("fairy", [{"entries": [counted("minecraft:glowstone_dust", 1, 2)], "rolls": 1.0},
                          {"conditions": [{"chance": 0.25, "condition": "minecraft:random_chance"}],
                           "entries": [{"type": "minecraft:item", "name": rid("trans_crystal")}], "rolls": 1.0}])

    # ---- recipes
    recipe("cooked_trans_fish", {"type": "minecraft:smelting", "category": "food", "cookingtime": 200, "experience": 0.35,
                                 "ingredient": rid("trans_fish"), "result": {"id": rid("cooked_trans_fish")}})
    recipe("cooked_trans_fish_from_smoking", {"type": "minecraft:smoking", "category": "food", "cookingtime": 100, "experience": 0.35,
                                              "ingredient": rid("trans_fish"), "result": {"id": rid("cooked_trans_fish")}})
    recipe("cooked_trans_fish_from_campfire_cooking", {"type": "minecraft:campfire_cooking", "category": "food", "cookingtime": 600,
                                                       "experience": 0.35, "ingredient": rid("trans_fish"),
                                                       "result": {"id": rid("cooked_trans_fish")}})
    recipe("trans_crystal_pearl", shapeless("trans_crystal_pearl", ["trans_pearl", "trans_crystal"], 1, category="misc"))
    recipe("gumdrop", shapeless("gumdrop", ["pastel_gel", "minecraft:sugar"], 3, category="misc"))
    for block, dye in (("pink_gel_block", "minecraft:pink_dye"), ("blue_gel_block", "minecraft:light_blue_dye")):
        recipe(block, shaped(block, ["GGG", "GDG", "GGG"], {"G": "pastel_gel", "D": dye}, 1, category="misc"))
        recipe(f"pastel_gel_from_{block}", shapeless("pastel_gel", [block], 8, category="misc", group="pastel_gel"))

    # ---- tags: the trans fish is a fish to everything that cares
    tag("item", "fishes", "trans_fish", "cooked_trans_fish")
    for food in ("cat_food", "ocelot_food"):
        tag("item", food, "trans_fish")
    tag("item", "wolf_food", "trans_fish", "cooked_trans_fish")
    tag("item", "nautilus_bucket_food", "trans_fish_bucket")
    for t in ("aquatic", "axolotl_hunt_targets", "can_breathe_under_water", "not_scary_for_pufferfish", "cannot_be_pushed_onto_boats"):
        tag("entity_type", t, "trans_fish")


# ============================================================================================ the Fairy Realm
def _faces(texture, uv_side=(0, 0, 16, 16), uv_top=(0, 0, 16, 16), cull=(), skip=()):
    faces = {}
    for face in ("down", "up", "north", "south", "west", "east"):
        if face in skip:
            continue
        f = {"uv": list(uv_top if face in ("up", "down") else uv_side), "texture": texture}
        if face in cull:
            f["cullface"] = face
        faces[face] = f
    return faces


def generate_fairy_realm_data():
    """The endgame: Fairy Portal frames (twelve make a portal), the portal itself, the arena's altar, the Fairy Jar
    trophy, the Trans Fairy and her crystals, the crystal pearl's messages and the endgame advancements' names.
    (Structures: generate_fairy_realm.py. Dimension and islands: generate_worldgen.py.)"""
    # ---- the portal frame: vanilla's end portal frame shape, a crystal pearl set in its socket when filled (lit like any
    # block: no full-bright faces, and light 1 like an end portal frame)
    frame_tex = {"particle": block_tex("fairy_portal_frame_side"), "bottom": block_tex("trans_stone"),
                 "top": block_tex("fairy_portal_frame_top"), "side": block_tex("fairy_portal_frame_side")}
    base = {"from": [0, 0, 0], "to": [16, 13, 16], "faces": {
        "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
        "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
        **{side: {"uv": [0, 3, 16, 16], "texture": "#side", "cullface": side} for side in ("north", "south", "west", "east")}}}
    pearl = {"from": [4, 13, 4], "to": [12, 16, 12], "faces": {
        "up": {"uv": [4, 4, 12, 12], "texture": "#pearl", "cullface": "up"},
        **{side: {"uv": [4, 0, 12, 3], "texture": "#pearl"} for side in ("north", "south", "west", "east")}}}
    model("fairy_portal_frame", {"parent": "minecraft:block/block", "textures": frame_tex, "elements": [base]})
    model("fairy_portal_frame_filled", {"parent": "minecraft:block/block",
                                        "textures": {**frame_tex, "pearl": block_tex("fairy_portal_frame_pearl")},
                                        "elements": [base, pearl]})
    rotation = {"south": 0, "west": 90, "north": 180, "east": 270}
    variants = {}
    for has_pearl in ("false", "true"):
        for facing, y in rotation.items():
            v = {"model": f"{NS}:block/fairy_portal_frame" + ("_filled" if has_pearl == "true" else "")}
            if y:
                v["y"] = y
            variants[f"facing={facing},pearl={has_pearl}"] = v
    blockstate("fairy_portal_frame", {"variants": variants})
    item_def("fairy_portal_frame", f"{NS}:block/fairy_portal_frame")
    name("fairy_portal_frame", "Fairy Portal Frame")

    # ---- the portal: a glowing, swirling sheet at the height of an end portal's surface
    model("fairy_portal", {"ambientocclusion": False, "textures": {"particle": block_tex("fairy_portal"), "portal": block_tex("fairy_portal")},
                           "elements": [{"from": [0, 11, 0], "to": [16, 12, 16], "light_emission": 15, "shade": False, "faces": {
                               "up": {"uv": [0, 0, 16, 16], "texture": "#portal"},
                               "down": {"uv": [0, 0, 16, 16], "texture": "#portal"}}}]})
    blockstate("fairy_portal", {"variants": {"": {"model": f"{NS}:block/fairy_portal"}}})
    name("fairy_portal", "Fairy Portal")

    # ---- the altar: a stone base, a prism pillar, a capstone, and a crystal growing out of the top (all lit by the world)
    crystal = lambda angle_from, angle_to: {"from": angle_from, "to": angle_to,
                                            "rotation": {"origin": [8, 20, 8], "axis": "y", "angle": 45, "rescale": True},
                                            "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#crystal"} for f in
                                                      (("north", "south") if angle_from[2] == angle_to[2] else ("west", "east"))}}
    model("fairy_altar", {"parent": "minecraft:block/block", "textures": {
        "particle": block_tex("chiseled_trans_stone_bricks"), "base": block_tex("trans_stone_bricks"), "pillar": block_tex("pastel_prism"),
        "cap": block_tex("chiseled_trans_stone_bricks"), "crystal": block_tex("trans_crystal_cluster")},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 4, 15], "faces": _faces("#base", (1, 12, 15, 16), (1, 1, 15, 15), cull=("down",))},
            {"from": [4, 4, 4], "to": [12, 12, 12], "faces": _faces("#pillar", (4, 4, 12, 12), (4, 4, 12, 12), skip=("up", "down"))},
            {"from": [2, 12, 2], "to": [14, 15, 14], "faces": _faces("#cap", (2, 0, 14, 3), (2, 2, 14, 14))},
            crystal([3, 15, 8], [13, 25, 8]), crystal([8, 15, 3], [8, 25, 13])]})
    blockstate("fairy_altar", {"variants": {"": {"model": f"{NS}:block/fairy_altar"}}})
    item_def("fairy_altar", f"{NS}:block/fairy_altar")
    name("fairy_altar", "Fairy Altar")

    # ---- the Fairy Jar: a trans glass jar with a flag-striped cloth lid. In the world its fairy is a tiny entity that
    # flies about inside it (JarFairy), so the block model is the empty jar; the item shows a little winged light in it
    # instead (glowing, its texture cycling pink, white and blue).
    jar = [
        {"from": [3, 0, 3], "to": [13, 11, 13], "faces": _faces("#glass", (3, 5, 13, 16), (3, 3, 13, 13), cull=("down",))},
        {"from": [4, 11, 4], "to": [12, 12, 12], "faces": _faces("#glass", (4, 4, 12, 5), (4, 4, 12, 12), skip=("down",))},
        {"from": [3.5, 12, 3.5], "to": [12.5, 13.5, 12.5], "faces": _faces("#lid", (0, 6, 16, 9), (4, 4, 12, 12))},
        {"from": [7, 13.5, 7], "to": [9, 14.5, 9], "faces": _faces("#lid", (7, 7, 9, 8), (7, 7, 9, 9), skip=("down",))},
    ]
    light = [
        {"from": [6, 3.5, 6], "to": [10, 7.5, 10], "light_emission": 15, "shade": False,
         "faces": _faces("#light", (0, 0, 6, 6), (0, 0, 6, 6))},
        {"from": [3, 6, 10.2], "to": [6.5, 9.5, 10.2], "light_emission": 15, "shade": False,
         "faces": {f: {"uv": [8, 0, 14, 6], "texture": "#light"} for f in ("north", "south")}},
        {"from": [9.5, 6, 10.2], "to": [13, 9.5, 10.2], "light_emission": 15, "shade": False,
         "faces": {f: {"uv": [8, 0, 14, 6], "texture": "#light"} for f in ("north", "south")}},
    ]
    jar_tex = {"particle": block_tex("fairy_jar_glass"), "glass": block_tex("fairy_jar_glass"), "lid": block_tex("fairy_jar_lid"),
               "light": block_tex("fairy_jar_light")}
    model("fairy_jar", {"parent": "minecraft:block/block", "textures": jar_tex, "elements": jar})
    model("fairy_jar_item", {"parent": "minecraft:block/block", "textures": jar_tex, "elements": light + jar})
    blockstate("fairy_jar", {"variants": {"": {"model": f"{NS}:block/fairy_jar"}}})
    item_def("fairy_jar", f"{NS}:block/fairy_jar_item")
    name("fairy_jar", "Fairy Jar")
    loot("fairy_jar", loot_self("fairy_jar"))

    # ---- the Trans Fairy and her crystals
    simple_item("trans_fairy_spawn_egg", "Trans Fairy Spawn Egg")
    entity_loot("trans_fairy", [
        {"entries": [{"type": "minecraft:item", "name": rid("fairy_jar")}], "rolls": 1.0},
        {"entries": [counted("trans_crystal", 3, 5)], "rolls": 1.0},
        {"entries": [counted("prism_shard", 6, 10, looting=2.0)], "rolls": 1.0},
        {"entries": [counted("trans_crystal_pearl", 1, 2)], "rolls": 1.0},
        {"entries": [counted("gumdrop", 3, 6)], "rolls": 1.0}])
    NAMES.update({
        "entity.transdimension.trans_fairy": "Trans Fairy",
        "entity.transdimension.fairy_crystal_spike": "Fairy Ice Crystal",
        "biome.transdimension.fairy_realm": "Fairy Realm",
        "item.transdimension.trans_crystal_pearl.lore2": "Throw it in the Trans Realm and follow it to a Fairy Sanctum",
        "item.transdimension.trans_crystal_pearl.quiet": "The pearl is quiet here. Throw it in the Trans Realm.",
        "item.transdimension.trans_crystal_pearl.nothing": "The pearl can't sense any Fairy Sanctum nearby.",
        "entity.transdimension.crystal_eye": "Trans Crystal Pearl",
        "message.transdimension.fairy_altar_hint": "✦ The arena is quiet. Throw a Trans Crystal onto the altar to call the Trans Fairy. ✦",
        "message.transdimension.cutscene.maddie_1": "Wait! What are you doing?!",
        "message.transdimension.cutscene.maddie_2": "You summoned her... You've done something horrible!",
        "message.transdimension.cutscene.maddie_3": "The Trans Fairy is an ancient, horrible being. Get away from her before she—",
        "message.transdimension.cutscene.fairy_1": "Hush now, little Maddie.",
        "message.transdimension.cutscene.fairy_2": "Ages I have slept beneath this altar... and you are the one who woke me.",
        "message.transdimension.cutscene.fairy_3": "Very well, child of the flag. Show me what your heart is worth!",
        "message.transdimension.pastel_slime.stay": "Your slime will wait here",
        "message.transdimension.pastel_slime.follow": "Your slime follows you again",
        "message.transdimension.fairy_already_here": "The Trans Fairy is already here!",
        "message.transdimension.fairy_summoned": "The Trans Fairy answers your call!",
        "message.transdimension.fairy_defeated": "✦ The Trans Fairy is beaten! A portal home has opened north of the arena. ✦",
        "message.transdimension.fairy_peaceful": "The world is peaceful, so the Trans Fairy can't hurt you here. A portal home is open north of the arena.",
    })


# ============================================================================================ other tags, names, sounds
def generate_misc():
    for b in ("trans_dirt",):
        tag("block", "dirt", b)
        tag("item", "dirt", b)
    tag("block", "grass_blocks", "trans_grass_block")
    tag("item", "grass_blocks", "trans_grass_block")
    tag("block", "sand", "trans_sand")
    tag("item", "sand", "trans_sand")
    # Trans sand is deliberately NOT in #minecraft:smelts_to_glass: vanilla's glass recipe uses that tag, and the
    # furnace would pick it over ours. Trans sand smelts into trans glass (see generate_recipes).
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

    # crystal tools and armor (upgraded from diamond with Crystal Alloy; their item models and equipment asset are hand-made)
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
    tag("item", "repairs_trans_gear", "crystal_alloy", ns=NS)
    tag("block", "incorrect_for_trans_tool", "#minecraft:incorrect_for_diamond_tool", ns=NS)
    # The Pride Oven is a job site villagers can claim.
    tag("point_of_interest_type", "acquirable_job_site", "transdimension:pride_oven")

    # boats: vanilla boat entities with trans textures (see TransDimensionClient)
    for item, english in (("trans_boat", "Trans Boat"), ("trans_chest_boat", "Trans Boat with Chest")):
        simple_item(item, english)
        NAMES[f"entity.{NS}.{item}"] = english
    tag("item", "boats", "trans_boat")
    tag("item", "chest_boats", "trans_chest_boat")
    tag("entity_type", "boat", "trans_boat")

    # paintings: data-driven variants; the placeable tag lets a placed painting pick them at random
    for painting, (pw, ph, title, author) in PAINTINGS.items():
        write(os.path.join(DATA, NS, "painting_variant", f"{painting}.json"), {
            "asset_id": rid(painting), "width": pw, "height": ph,
            "title": {"color": "yellow", "translate": f"painting.{NS}.{painting}.title"},
            "author": {"color": "gray", "translate": f"painting.{NS}.{painting}.author"}})
        NAMES[f"painting.{NS}.{painting}.title"] = title
        NAMES[f"painting.{NS}.{painting}.author"] = author
        tag("painting_variant", "placeable", painting)

    # crystals: the rare gem has its own sprite; prism shards drop from prism clusters
    simple_item("trans_crystal", "Trans Crystal")
    simple_item("crystal_alloy", "Crystal Alloy")
    NAMES["item.transdimension.crystal_alloy.lore"] = "Upgrades diamond gear, with a Crystal Upgrade template"
    simple_item("crystal_upgrade_smithing_template", "Crystal Upgrade Smithing Template")
    NAMES["item.transdimension.crystal_upgrade_smithing_template.lore"] = "Diamond gear + Crystal Alloy in a smithing table"
    NAMES["item.transdimension.crystal_upgrade_smithing_template.lore2"] = "Found in mermaid ruins"
    simple_item("prism_shard", "Prism Shard")

    # Maddie and her gifts
    simple_item("trans_wings", "Trans Wings")
    simple_item("trans_magic_bolt", "Trans Magic")
    simple_item("maddie_spawn_egg", "Maddie Spawn Egg")
    model("trans_wand", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/trans_wand"}}, kind="item")
    item_def("trans_wand", f"{NS}:item/trans_wand")
    name("trans_wand", "Trans Wand", kind="item")
    NAMES.update({
        "entity.transdimension.maddie": "Maddie",
        "entity.transdimension.trans_magic_bolt": "Trans Magic",
        "item.transdimension.trans_wand.lore": "Shoots sparkly hearts of trans magic",
        "filled_map.transdimension.egg_house": "Map to Maddie's Egg House",
        "filled_map.transdimension.pink_ancient_city": "Map to a Pink Ancient City",
        "item.transdimension.trans_wings.lore": "Crouch to charge, jump to launch",
        "item.transdimension.trans_wings.lore2": "Jump while gliding to flap",
        "item.transdimension.trans_wings.lore3": "Crouch while gliding to hover",
    })
    for key, line in MADDIE_DIALOGUE.items():
        NAMES[f"dialogue.transdimension.maddie.{key}"] = line

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
        "subtitles.transdimension.block.pink_sculk_sensor.clicking": "Pink Sculk Sensor chimes",
        "subtitles.transdimension.block.pink_sculk_sensor.clicking_stop": "Pink Sculk Sensor settles",
        "subtitles.transdimension.block.pink_sculk_shrieker.shriek": "Pink Sculk Shrieker sings",
        "subtitles.transdimension.block.pink_sculk_catalyst.bloom": "Pink Sculk Catalyst blooms",
        "subtitles.transdimension.entity.silly_cat.purr": "Silly Cat purrs",
        "subtitles.transdimension.entity.silly_cat.lick": "Silly Cat gives a big lick",
        "subtitles.transdimension.entity.silly_cat.hurt": "Silly Cat hurts",
        "subtitles.transdimension.entity.silly_cat.death": "Silly Cat dies",
        "subtitles.transdimension.block.pride_oven.crackle": "Pride Oven crackles",
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
    "pride_flower_fields": "Pride Flower Fields", "moonlit_meadow": "Moonlit Meadow", "gumdrop_glade": "Gumdrop Glade",
    "pastel_reef": "Pastel Reef", "blooming_caverns": "Blooming Caverns", "pink_deep_dark": "Pink Deep Dark",
    "candy_cane_grove": "Candy Cane Grove", "frosted_caves": "Frosted Caves",
}


# ============================================================================================ advancements
ADV_DIR = os.path.join(DATA, NS, "advancement")


def has(*items):
    """All of these items in the inventory at once."""
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": rid(i)} for i in items]}}


def has_any(items):
    return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [rid(i) for i in items]}]}}


def player_is(**predicate):
    """A location-trigger criterion on the player (keys are 26.2 entity sub-predicates without the namespace)."""
    return {"trigger": "minecraft:location", "conditions": {"player": [{
        "condition": "minecraft:entity_properties", "entity": "this",
        "predicate": {f"minecraft:{k}": v for k, v in predicate.items()}}]}}


def in_biome(biome):
    return player_is(location={"biomes": rid(biome)})


def interacted(entity):
    return {"trigger": "minecraft:player_interacted_with_entity", "conditions": {"entity": [{
        "condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": rid(entity)}}]}}


def ate(item):
    return {"trigger": "minecraft:consume_item", "conditions": {"item": {"items": rid(item)}}}


def advancement(name, parent, icon, title, description, criteria, frame="task", any_of=False, display=None):
    d = {}
    if parent:
        d["parent"] = rid(parent)
    d["criteria"] = criteria
    shown = {"icon": {"id": rid(icon)},
             "title": {"translate": f"advancements.{NS}.{name}.title"},
             "description": {"translate": f"advancements.{NS}.{name}.description"}}
    if frame != "task":
        shown["frame"] = frame
    shown.update(display or {})
    d["display"] = shown
    d["requirements"] = [list(criteria)] if any_of else [[k] for k in criteria]
    write(os.path.join(ADV_DIR, f"{name}.json"), d)
    NAMES[f"advancements.{NS}.{name}.title"] = title
    NAMES[f"advancements.{NS}.{name}.description"] = description


def generate_advancements():
    """The Trans Dimension advancement tab: exploring, Maddie and her gifts, Silly Cats, building, crystals, the bakery,
    flowers, and collecting all nine cat plushes."""
    if os.path.isdir(ADV_DIR):
        for f in os.listdir(ADV_DIR):
            if f.endswith(".json"):
                os.remove(os.path.join(ADV_DIR, f))
    A = advancement
    A("goober", None, "trans_crystal", "Trans Dimension", "Say \"Goober\" and step into the Trans Realm",
      {"entered_trans_realm": {"trigger": "minecraft:changed_dimension", "conditions": {"to": f"{NS}:trans_realm"}}},
      display={"background": f"{NS}:gui/advancements/backgrounds/trans"})

    # ---- exploring
    A("trans_village", "goober", "trans_door", "Home Sweet Home", "Find a Trans Village",
      {"found_trans_village": player_is(location={"structures": f"{NS}:trans_village"})})
    A("first_plush", "trans_village", "silly_cat_plush", "Plushie Pal", "Find the cat plush hidden in a village house",
      {"plush": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": f"#{NS}:plushes"}]}}})
    A("all_plushes", "first_plush", "trans_cat_plush", "Gotta Hug 'Em All", "Collect all nine cat plushes",
      {plush: has(plush) for plush in PLUSH_NAMES}, frame="challenge")
    A("crystal_caves", "goober", "trans_crystal_cluster", "Glitter Grotto", "Explore the Crystal Caves",
      {"crystal_caves": in_biome("crystal_caves")})
    A("pastel_lush_caves", "crystal_caves", "trans_moss_block", "Soft Spot", "Find the Pastel Lush Caves",
      {"pastel_lush_caves": in_biome("pastel_lush_caves")})
    A("blooming_caverns", "crystal_caves", "star_bloom", "Underground Garden", "Find the Blooming Caverns",
      {"blooming_caverns": in_biome("blooming_caverns")})
    A("frosted_caves", "crystal_caves", "minecraft:blue_ice", "Cold Feet", "Find the Frosted Caves, under the realm's coldest land",
      {"frosted_caves": in_biome("frosted_caves")})
    A("pastel_reef", "goober", "blush_coral_fan", "Reef Dreams", "Swim through the Pastel Reef",
      {"reef": in_biome("pastel_reef")})
    A("mermaid_ruin", "pastel_reef", "minecraft:heart_of_the_sea", "Mermaid Tales", "Find the ruins of a mermaid's home under the sea",
      {"ruin": player_is(location={"structures": f"{NS}:mermaid_ruin"})})
    A("pastel_peaks", "goober", "trans_stone", "Head in the Clouds", "Climb above Y 160 in the Pastel Peaks",
      {"peak": player_is(location={"biomes": rid("pastel_peaks"), "position": {"y": {"min": 160.0}}})})
    A("pastel_passport", "pastel_peaks", "minecraft:filled_map", "Pastel Passport", "Visit every biome of the Trans Realm",
      {biome: in_biome(biome) for biome in BIOMES}, frame="challenge")
    A("egg_house", "goober", "minecraft:egg", "Egg-cellent View", "Find Maddie's Egg House floating in the sky",
      {"egg_house": player_is(location={"structures": f"{NS}:egg_house_island"})}, frame="goal")

    # ---- Maddie and her gifts
    A("meet_maddie", "egg_house", "maddie_spawn_egg", "Hi Hi!", "Say hello to Maddie", {"talked": interacted("maddie")})
    A("trans_wand", "meet_maddie", "trans_wand", "Bibbidi-Bobbidi-Valid", "Receive the Trans Wand", {"wand": has("trans_wand")})
    A("magic_missile", "trans_wand", "trans_magic_bolt", "Magic Missile", "Defeat a mob with the Trans Wand's magic",
      {"zapped": {"trigger": "minecraft:player_killed_entity", "conditions": {
          "killing_blow": {"direct_entity": {"minecraft:entity_type": rid("trans_magic_bolt")}}}}})
    A("trans_wings", "meet_maddie", "trans_wings", "Earned Your Wings", "Receive the Trans Wings",
      {"wings": has("trans_wings")}, frame="goal")
    A("sky_dancer", "trans_wings", "minecraft:feather", "Sky Dancer", "Fly above Y 250 in the Trans Realm with your Trans Wings",
      {"high": player_is(location={"dimension": f"{NS}:trans_realm", "position": {"y": {"min": 250.0}}},
                         equipment={"chest": {"items": rid("trans_wings")}})}, frame="challenge")

    # ---- Silly Cats
    A("slobbered", "goober", "silly_cat_spawn_egg", "Big Smooch", "Get licked by a Silly Cat",
      {"slobbered": {"trigger": "minecraft:effects_changed", "conditions": {"effects": {f"{NS}:slobbered": {}}}}})
    A("pet_silly_cat", "slobbered", "trans_cookie", "Who's a Good Goober?", "Pet a Silly Cat",
      {"petted": interacted("silly_cat")})

    # ---- building
    A("trans_planks", "goober", "trans_planks", "Pastel Carpentry", "Make Trans Planks from a trans log",
      {"planks": has("trans_planks")})
    A("sweet_dreams", "trans_planks", "trans_bed", "Sweet Dreams", "Sleep in a Trans Bed",
      {"slept": {"trigger": "minecraft:slept_in_bed", "conditions": {"player": [{
          "condition": "minecraft:entity_properties", "entity": "this",
          "predicate": {"minecraft:location": {"block": {"blocks": rid("trans_bed")}}}}]}}})
    heart = lambda half: {"trigger": "minecraft:placed_block", "conditions": {"location": [{
        "condition": "minecraft:block_state_property", "block": rid("trans_bed"), "properties": {"heart": half}}]}}
    A("heart_to_heart", "sweet_dreams", "trans_bed", "Heart to Heart", "Push two Trans Beds together to make a heart",
      {"left": heart("left"), "right": heart("right")}, any_of=True)
    A("trans_glass", "trans_planks", "trans_glass", "Rose-Tinted Glasses", "Smelt Trans Sand into Trans Glass",
      {"glass": has("trans_glass")})
    A("trans_boat", "trans_planks", "trans_boat", "Smooth Sailing", "Build a Trans Boat", {"boat": has("trans_boat")})
    A("cozy_corner", "trans_planks", "trans_chair", "Cozy Corner", "Get a Trans Chair and a Trans Table",
      {"chair": has("trans_chair"), "table": has("trans_table")})
    A("flag_fluff", "goober", "trans_wool", "Flag Fluff", "Shear a trans flag sheep in the realm", {"wool": has("trans_wool")})

    # ---- crystals and gear
    A("trans_crystal", "goober", "trans_crystal", "Shiny!", "Find a rare Trans Crystal deep underground", {"crystal": has("trans_crystal")})
    A("crystal_clear", "trans_crystal", "trans_crystal_block", "Crystal Clear", "Collect nine Trans Crystals and make a Block of Trans Crystal",
      {"block": has("trans_crystal_block")}, frame="goal")
    A("armored_in_pride", "trans_crystal", "trans_chestplate", "Armored in Pride", "Wear a full set of Trans Crystal armor",
      {"armor": player_is(equipment={"head": {"items": rid("trans_helmet")}, "chest": {"items": rid("trans_chestplate")},
                                     "legs": {"items": rid("trans_leggings")}, "feet": {"items": rid("trans_boots")}})},
      frame="goal")
    A("deep_pastel", "trans_crystal", "trans_deepslate", "Deep Pastel", "Dig down to Trans Deepslate",
      {"deepslate": has("cobbled_trans_deepslate")})

    # ---- the bakery
    treats = ["trans_cookie", "trans_donut", "trans_cupcake", "trans_macaron", "trans_boba"]
    A("fresh_from_the_oven", "trans_village", "pride_oven", "Fresh From the Oven", "Buy a treat from a Trans Baker",
      {"bought": {"trigger": "minecraft:villager_trade", "conditions": {
          "item": {"items": [rid(t) for t in treats + ["trans_cake"]]}}}})
    A("sprinkle_sprinkle", "fresh_from_the_oven", "trans_donut", "Sprinkle Sprinkle", "Eat a Trans Donut",
      {"donut": ate("trans_donut")})
    A("sweet_tooth", "sprinkle_sprinkle", "trans_cupcake", "Sweet Tooth", "Eat every treat the Trans Bakers make",
      {t: ate(t) for t in treats}, frame="challenge")
    A("let_them_eat_cake", "fresh_from_the_oven", "trans_cake", "Let Them Eat Cake", "Get a Trans Cake",
      {"cake": has("trans_cake")})

    # ---- flowers
    flowers = ["pride_blossom", *FLOWERS, *NEW_FLOWERS, "pride_peony", *TALL_FLOWERS, "trans_petals"]
    A("petal_pusher", "goober", "trans_tulip", "Petal Pusher", "Pick a flower of the Trans Realm",
      {"flower": has_any(flowers)})
    A("pastel_bouquet", "petal_pusher", "pride_peony", "Pastel Bouquet", "Collect every flower of the Trans Realm",
      {f: has(f) for f in flowers}, frame="challenge")
    A("flower_fields", "petal_pusher", "trans_rose", "Stop and Smell the Flowers", "Wander through the Pride Flower Fields",
      {"fields": in_biome("pride_flower_fields")})
    A("heart_tree", "flower_fields", "flowering_blush_leaves", "Love Grows on Trees",
      "Shear some Flowering Blush Leaves from a heart tree", {"leaves": has("flowering_blush_leaves")}, frame="goal")
    A("star_gazer", "petal_pusher", "star_bloom", "Star Gazer", "Pick a glowing Starbloom", {"star": has("star_bloom")})

    # ---- creatures
    A("fishy_pride", "goober", "trans_fish_bucket", "Fishy Pride", "Catch a trans fish in a bucket",
      {"bucket": {"trigger": "minecraft:filled_bucket", "conditions": {"item": {"items": rid("trans_fish_bucket")}}}})
    A("squishy_sweetheart", "goober", "gumdrop", "Squishy Sweetheart", "Tame a Pastel Slime with Gumdrops or sugar",
      {"tamed": {"trigger": "minecraft:tame_animal", "conditions": {"entity": [{
          "condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": rid("pastel_slime")}}]}}})
    A("bottled_magic", "goober", "bottled_fairy", "Bottled Magic", "Catch a wild fairy in a glass bottle",
      {"bottle": has("bottled_fairy")})
    A("pearly_white", "goober", "trans_pearl", "Pearly White", "Get a Trans Pearl from a trans enderman",
      {"pearl": has("trans_pearl")})
    A("pearl_of_the_realm", "pearly_white", "trans_crystal_pearl", "Pearl of the Realm",
      "Set a Trans Crystal into a Trans Pearl", {"crystal_pearl": has("trans_crystal_pearl")})

    # ---- the endgame
    A("fairy_sanctum", "pearl_of_the_realm", "fairy_portal_frame", "Hidden Sanctum", "Find a Fairy Sanctum beneath its moonlit shrine",
      {"sanctum": player_is(location={"structures": f"{NS}:fairy_sanctum"})})
    A("fairy_realm", "fairy_sanctum", "trans_crystal_pearl", "Through the Opal Pool",
      "Set twelve Trans Crystal Pearls into a Fairy Portal and step through",
      {"entered": {"trigger": "minecraft:changed_dimension", "conditions": {"to": f"{NS}:fairy_realm"}}}, frame="goal")
    A("fairy_tale_ending", "fairy_realm", "trans_fairy_spawn_egg", "A Fairy Tale Ending", "Defeat the Trans Fairy",
      {"defeated": {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [{
          "condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": rid("trans_fairy")}}]}}},
      frame="challenge")
    A("fairy_in_a_jar", "fairy_tale_ending", "fairy_jar", "Fairy in a Jar", "Take home a Fairy Jar", {"jar": has("fairy_jar")})


# ============================================================================================ round 9
# The pink deep dark, the candle ritual and the Cloud Realm. (Textures: generate_textures.py round9_textures(); the
# biome and the Cloud Realm: generate_worldgen.py; the pink ancient cities: generate_ancient_city.py.)
SCULK_PERSON_TRADES = {
    # level: [(wants, count, gives, count, max uses, xp)] — sculk gems buy the deep's treasures
    1: [("sculk_gem", 4, "minecraft:echo_shard", 1, 12, 2), ("sculk_gem", 6, "minecraft:diamond", 2, 12, 2),
        ("sculk_gem", 2, "minecraft:experience_bottle", 4, 16, 1), ("minecraft:echo_shard", 3, "sculk_gem", 1, 16, 2)],
    2: [("sculk_gem", 8, "minecraft:netherite_scrap", 1, 8, 5), ("sculk_gem", 10, "minecraft:recovery_compass", 1, 4, 5),
        ("sculk_gem", 6, "minecraft:golden_apple", 2, 8, 5), ("sculk_gem", 5, "ritual_candle", 1, 8, 5)],
    3: [("sculk_gem", 14, "minecraft:shulker_shell", 2, 6, 10), ("sculk_gem", 18, "minecraft:totem_of_undying", 1, 3, 10),
        ("sculk_gem", 16, "minecraft:netherite_upgrade_smithing_template", 1, 3, 10),
        ("sculk_gem", 12, "crystal_upgrade_smithing_template", 1, 3, 10)],
    4: [("sculk_gem", 24, "minecraft:netherite_ingot", 1, 3, 15), ("sculk_gem", 20, "minecraft:enchanted_golden_apple", 1, 3, 15),
        ("sculk_gem", 16, "trans_crystal", 2, 4, 15), ("sculk_gem", 20, "minecraft:music_disc_5", 1, 2, 15)],
    5: [("sculk_gem", 40, "minecraft:elytra", 1, 2, 30), ("sculk_gem", 32, "minecraft:heavy_core", 1, 2, 30),
        ("sculk_gem", 48, "minecraft:nether_star", 1, 1, 30)],
}


def generate_sculk_trades():
    """The sculk people's trades: villager_trade/sculk_person/<level>/*, their tags and one trade set per level."""
    root = os.path.join(DATA, NS, "villager_trade", "sculk_person")
    if os.path.isdir(root):
        import shutil
        shutil.rmtree(root)
    for level, trades in SCULK_PERSON_TRADES.items():
        ids = []
        for wants, wcount, gives, gcount, uses, xp in trades:
            trade_name = f"{wants.split(':')[-1]}_{gives.split(':')[-1]}"
            trade = {"gives": {"id": rid(gives)}, "max_uses": float(uses), "reputation_discount": 0.05, "wants": {"id": rid(wants)},
                     "xp": float(xp)}
            if gcount > 1:
                trade["gives"]["count"] = gcount
            if wcount > 1:
                trade["wants"]["count"] = float(wcount)
            write(os.path.join(root, str(level), f"{trade_name}.json"), trade)
            ids.append(f"{NS}:sculk_person/{level}/{trade_name}")
        if level == 5:
            write(os.path.join(root, "5", "sculk_gem_swift_sneak_book.json"), {
                "additional_wants": {"id": "minecraft:book"},
                "given_item_modifiers": [{"function": "minecraft:set_enchantments", "enchantments": {"minecraft:swift_sneak": 3.0}}],
                "gives": {"id": "minecraft:enchanted_book"}, "max_uses": 3.0, "reputation_discount": 0.05,
                "wants": {"count": 24.0, "id": rid("sculk_gem")}, "xp": 30.0})
            ids.append(f"{NS}:sculk_person/5/sculk_gem_swift_sneak_book")
        write(os.path.join(DATA, NS, "tags", "villager_trade", "sculk_person", f"level_{level}.json"), {"replace": False, "values": ids})
        write(os.path.join(DATA, NS, "trade_set", "sculk_person", f"level_{level}.json"), {
            "amount": 3.0 if level < 5 else 2.0, "random_sequence": f"{NS}:trade_set/sculk_person/level_{level}",
            "trades": f"#{NS}:sculk_person/level_{level}"})


def faced_box(frm, to, texture, top=None, light=None):
    """A model element with every face from one texture (the up face from `top` if given), UVs following its size."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    faces = {
        "down": {"uv": [x0, z0, x1, z1], "texture": texture},
        "up": {"uv": [x0, z0, x1, z1], "texture": top or texture},
        "north": {"uv": [x0, 16 - y1, x1, 16 - y0], "texture": texture},
        "south": {"uv": [x0, 16 - y1, x1, 16 - y0], "texture": texture},
        "west": {"uv": [z0, 16 - y1, z1, 16 - y0], "texture": texture},
        "east": {"uv": [z0, 16 - y1, z1, 16 - y0], "texture": texture},
    }
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if light:
        e["light_emission"] = light
    return e


def generate_round9():
    # ---- pink sculk
    blockstate("pink_sculk", {"variants": {"": [
        {"model": f"{NS}:block/pink_sculk"}, {"model": f"{NS}:block/pink_sculk_mirrored"},
        {"model": f"{NS}:block/pink_sculk", "y": 180}, {"model": f"{NS}:block/pink_sculk_mirrored", "y": 180}]}})
    model("pink_sculk", {"parent": "minecraft:block/cube_all", "textures": {"all": block_tex("pink_sculk")}})
    model("pink_sculk_mirrored", {"parent": "minecraft:block/cube_mirrored_all", "textures": {"all": block_tex("pink_sculk")}})
    item_def("pink_sculk", f"{NS}:block/pink_sculk")

    blockstate("pink_sculk_vein", from_template("sculk_vein", "sculk_vein", "pink_sculk_vein"))
    model("pink_sculk_vein", {"ambientocclusion": False, "textures": {"particle": block_tex("pink_sculk_vein"), "sculk_vein": block_tex("pink_sculk_vein")},
                              "elements": [{"from": [0, 0, 0.1], "to": [16, 16, 0.1], "faces": {
                                  "north": {"uv": [16, 0, 0, 16], "texture": "#sculk_vein"}, "south": {"uv": [0, 0, 16, 16], "texture": "#sculk_vein"}}}]})
    model("pink_sculk_vein", {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex("pink_sculk_vein")}}, kind="item")
    item_def("pink_sculk_vein", f"{NS}:item/pink_sculk_vein")

    for bloom in ("", "_bloom"):
        model(f"pink_sculk_catalyst{bloom}", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "bottom": block_tex("pink_sculk_catalyst_bottom"), "side": block_tex(f"pink_sculk_catalyst_side{bloom}"),
            "top": block_tex(f"pink_sculk_catalyst_top{bloom}")}})
    blockstate("pink_sculk_catalyst", {"variants": {"bloom=false": {"model": f"{NS}:block/pink_sculk_catalyst"},
                                                    "bloom=true": {"model": f"{NS}:block/pink_sculk_catalyst_bloom"}}})
    item_def("pink_sculk_catalyst", f"{NS}:block/pink_sculk_catalyst")

    model("pink_sculk_sensor", {"parent": "minecraft:block/sculk_sensor", "textures": {
        "bottom": block_tex("pink_sculk_sensor_bottom"), "side": block_tex("pink_sculk_sensor_side"),
        "tendrils": block_tex("pink_sculk_sensor_tendril_inactive"), "top": block_tex("pink_sculk_sensor_top"),
        "particle": block_tex("pink_sculk_sensor_bottom")}})
    model("pink_sculk_sensor_active", {"parent": f"{NS}:block/pink_sculk_sensor", "textures": {
        "tendrils": block_tex("pink_sculk_sensor_tendril_active")}})
    blockstate("pink_sculk_sensor", {"variants": {
        "sculk_sensor_phase=inactive": {"model": f"{NS}:block/pink_sculk_sensor"},
        "sculk_sensor_phase=active": {"model": f"{NS}:block/pink_sculk_sensor_active"},
        "sculk_sensor_phase=cooldown": {"model": f"{NS}:block/pink_sculk_sensor_active"}}})
    item_def("pink_sculk_sensor", f"{NS}:block/pink_sculk_sensor")

    model("pink_sculk_shrieker", {"parent": "minecraft:block/template_sculk_shrieker", "textures": {
        "bottom": block_tex("pink_sculk_shrieker_bottom"), "side": block_tex("pink_sculk_shrieker_side"),
        "top": block_tex("pink_sculk_shrieker_top"), "inner_top": block_tex("pink_sculk_shrieker_inner_top"),
        "particle": block_tex("pink_sculk_shrieker_bottom")}})
    blockstate("pink_sculk_shrieker", {"variants": {"can_summon=false": {"model": f"{NS}:block/pink_sculk_shrieker"},
                                                    "can_summon=true": {"model": f"{NS}:block/pink_sculk_shrieker"}}})
    item_def("pink_sculk_shrieker", f"{NS}:block/pink_sculk_shrieker")

    for block, english, vanilla in (("pink_sculk", "Pink Sculk", "sculk"), ("pink_sculk_vein", "Pink Sculk Vein", "sculk_vein"),
                                    ("pink_sculk_catalyst", "Pink Sculk Catalyst", "sculk_catalyst"),
                                    ("pink_sculk_sensor", "Pink Sculk Sensor", "sculk_sensor"),
                                    ("pink_sculk_shrieker", "Pink Sculk Shrieker", "sculk_shrieker")):
        name(block, english)
        mine(block, "hoe")
        loot_like_vanilla(block, vanilla)

    # ---- sculk gems
    cube("sculk_gem_ore", "Sculk Gem Ore", drop=None)
    loot("sculk_gem_ore", loot_ore("sculk_gem_ore", "sculk_gem"))
    tag("block", "needs_iron_tool", "sculk_gem_ore")
    simple_item("sculk_gem", "Sculk Gem")
    NAMES["item.transdimension.sculk_gem.lore"] = "Sculk people trade for these"

    # ---- the candle ritual
    simple_item("ritual_candle", "Ritual Candle")
    NAMES["item.transdimension.ritual_candle.lore"] = "Set one on each candle stand of a ritual circle"
    NAMES["item.transdimension.ritual_candle.lore2"] = "Found in the chests of pink ancient cities"
    stand = [faced_box((2, 0, 2), (14, 3, 14), "#side", "#top"), faced_box((4, 3, 4), (12, 9, 12), "#side"),
             faced_box((3, 9, 3), (13, 11, 13), "#side", "#top")]
    candle = {"from": [6.5, 11, 6.5], "to": [9.5, 16, 9.5], "faces": {
        "up": {"uv": [0, 6, 3, 9], "texture": "#candle"},
        **{side: {"uv": [0, 0, 3, 5], "texture": "#candle"} for side in ("north", "south", "west", "east")}}}
    wick = {"from": [7.5, 16, 7.5], "to": [8.5, 17, 8.5], "faces": {
        side: {"uv": [1, 7, 2, 8], "texture": "#candle"} for side in ("up", "north", "south", "west", "east")}}
    stand_tex = {"particle": block_tex("ritual_pedestal_side"), "side": block_tex("ritual_pedestal_side"),
                 "top": block_tex("ritual_pedestal_top"), "candle": block_tex("ritual_candle")}
    model("ritual_pedestal", {"parent": "minecraft:block/block", "textures": stand_tex, "elements": stand})
    model("ritual_pedestal_lit", {"parent": "minecraft:block/block", "textures": stand_tex, "elements": stand + [candle, wick]})
    blockstate("ritual_pedestal", {"variants": {"candle=false": {"model": f"{NS}:block/ritual_pedestal"},
                                                "candle=true": {"model": f"{NS}:block/ritual_pedestal_lit"}}})
    item_def("ritual_pedestal", f"{NS}:block/ritual_pedestal")
    name("ritual_pedestal", "Ritual Candle Stand")

    def crystal_elements(shift=8):
        # In the world the gem floats half a block up and half a block east (its facing's clockwise side) of its block's
        # middle (SculkRitual.beamOrigin), so it can hang dead level with, and in line with, the middle of an even-sized
        # gate. The item shows it unshifted.
        def gem(frm, to, origin, axis, angle):
            e = faced_box(frm, to, "#crystal")
            e["from"] = [frm[0] + shift, frm[1] + shift, frm[2]]
            e["to"] = [to[0] + shift, to[1] + shift, to[2]]
            e["rotation"] = {"origin": [origin[0] + shift, origin[1] + shift, origin[2]], "axis": axis, "angle": angle}
            return e
        return [gem((5, 2, 5), (11, 14, 11), (8, 8, 8), "y", 45), gem((3, 0, 6), (6, 7, 9), (4.5, 0, 7.5), "z", 22.5),
                gem((10, 0, 7), (13, 6, 10), (11.5, 0, 8.5), "z", -22.5)]
    for awake in ("", "_awake"):
        model(f"ritual_crystal{awake}", {"parent": "minecraft:block/block", "textures": {
            "particle": block_tex(f"ritual_crystal{awake}"), "crystal": block_tex(f"ritual_crystal{awake}")}, "elements": crystal_elements()})
    rotation = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for awake in ("false", "true"):
        for facing, y in rotation.items():
            v = {"model": f"{NS}:block/ritual_crystal" + ("_awake" if awake == "true" else "")}
            if y:
                v["y"] = y
            variants[f"awake={awake},facing={facing}"] = v
    blockstate("ritual_crystal", {"variants": variants})
    model("ritual_crystal_item", {"parent": "minecraft:block/block", "textures": {
        "particle": block_tex("ritual_crystal"), "crystal": block_tex("ritual_crystal")}, "elements": crystal_elements(shift=0)})
    item_def("ritual_crystal", f"{NS}:block/ritual_crystal_item")
    name("ritual_crystal", "Ritual Crystal")

    portal_tex = {"particle": block_tex("sky_portal"), "portal": translucent("sky_portal")}
    model("sky_portal_ns", {"textures": portal_tex, "elements": [{"from": [0, 0, 6], "to": [16, 16, 10], "faces": {
        "north": {"uv": [0, 0, 16, 16], "texture": "#portal"}, "south": {"uv": [0, 0, 16, 16], "texture": "#portal"}}}]})
    model("sky_portal_ew", {"textures": portal_tex, "elements": [{"from": [6, 0, 0], "to": [10, 16, 16], "faces": {
        "east": {"uv": [0, 0, 16, 16], "texture": "#portal"}, "west": {"uv": [0, 0, 16, 16], "texture": "#portal"}}}]})
    blockstate("sky_portal", {"variants": {"axis=x": {"model": f"{NS}:block/sky_portal_ns"}, "axis=z": {"model": f"{NS}:block/sky_portal_ew"}}})
    name("sky_portal", "Sky Portal")

    NAMES.update({
        "message.transdimension.ritual.candles": "✦ %s of %s candles burn round the circle ✦",
        "message.transdimension.ritual.begin": "✦ The candles flare, and the crystal drinks in their light... ✦",
        "message.transdimension.ritual.opened": "✦ The gate opens onto the sky ✦",
        "message.transdimension.ritual.open": "✦ The gate already stands open ✦",
        "message.transdimension.ritual.blocked": "✦ Something fills the middle of the gate ✦",
        "message.transdimension.ritual.broken": "✦ The gate's frame is broken, and the light spills away ✦",
    })

    # ---- the Cloud Realm's ground
    blockstate("cloud_grass_block", {"variants": {"": [{"model": f"{NS}:block/cloud_grass_block", **({"y": y} if y else {})} for y in (0, 90, 180, 270)]}})
    model("cloud_grass_block", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "bottom": block_tex("cloud_soil"), "side": block_tex("cloud_grass_block_side"), "top": block_tex("cloud_grass_block_top"),
        "particle": block_tex("cloud_soil")}})
    item_def("cloud_grass_block", f"{NS}:block/cloud_grass_block")
    name("cloud_grass_block", "Cloud Grass Block")
    mine("cloud_grass_block", "shovel")
    loot("cloud_grass_block", loot_silk_or("cloud_grass_block", "cloud_soil"))
    cube("cloud_soil", "Cloud Soil", tool="shovel")
    for b in ("cloud_grass_block", "cloud_soil"):
        tag("block", "dirt", b)
        tag("item", "dirt", b)
    for t in ("animals_spawnable_on", "rabbits_spawnable_on", "wolves_spawnable_on", "valid_spawn", "grass_blocks"):
        tag("block", t, "cloud_grass_block")
    tag("item", "grass_blocks", "cloud_grass_block")
    tag("block", "sniffer_diggable_block", "cloud_soil", "cloud_grass_block")
    # Pink fire on the pink ancient cities' soul sand burns forever, like soul fire (the realm's dimension type uses this).
    tag("block", "infiniburn_trans_realm", "#minecraft:infiniburn_overworld", "minecraft:soul_sand", "minecraft:soul_soil", ns=NS)

    # ---- creatures
    for egg, english in (("pink_warden_spawn_egg", "Pink Warden Spawn Egg"), ("sculk_person_spawn_egg", "Sculk Person Spawn Egg"),
                         ("cloudy_spawn_egg", "Cloudy Spawn Egg")):
        simple_item(egg, english)
    NAMES.update({"entity.transdimension.pink_warden": "Pink Warden", "entity.transdimension.sculk_person": "Sculk Person",
                  "entity.transdimension.cloudy": "Cloudy", "entity.transdimension.villager.sculk_person": "Sculk Person",
                  "biome.transdimension.cloud_isles": "Cloud Isles"})
    entity_loot("pink_warden", [{"entries": [{"type": "minecraft:item", "name": rid("pink_sculk_catalyst")}], "rolls": 1.0}])
    entity_loot("sculk_person", [])
    entity_loot("cloudy", [{"entries": [counted("minecraft:white_wool", 1, 2)], "rolls": 1.0}])
    generate_sculk_trades()
    # The Cloud Realm's white farm animals: data-driven variants that spawn only in its biome.
    for kind in ("pig", "cow", "chicken"):
        write(os.path.join(DATA, NS, f"{kind}_variant", "cloud.json"), {
            "asset_id": f"{NS}:entity/{kind}/cloud_{kind}", "baby_asset_id": f"{NS}:entity/{kind}/cloud_{kind}_baby",
            "spawn_conditions": [{"condition": {"type": "minecraft:biome", "biomes": f"{NS}:cloud_isles"}, "priority": 2}]})

    # ---- pink sculk particles (their sprites; see ModParticles)
    for particle, frames in (("pink_sculk_soul", 11), ("pink_sonic_boom", 16), ("pink_shriek", 0), ("pink_vibration", 0)):
        textures = [f"{NS}:{particle}_{i}" for i in range(frames)] if frames else [f"{NS}:{particle}"]
        write(os.path.join(ASSETS, "particles", f"{particle}.json"), {"textures": textures})


def generate_round9_advancements():
    A = advancement
    A("pink_deep_dark", "crystal_caves", "pink_sculk", "Pink Silence", "Find the Pink Deep Dark, under the realm's mountains",
      {"pink_deep_dark": in_biome("pink_deep_dark")})
    A("sky_portal", "pink_deep_dark", "ritual_candle", "Ritual of Light",
      "Light the candle circle of a pink ancient city and step through its gate",
      {"entered": {"trigger": "minecraft:changed_dimension", "conditions": {"to": f"{NS}:cloud_realm"}}}, frame="goal")
    A("cloud_nine", "sky_portal", "cloudy_spawn_egg", "Cloud Nine", "Ride a Cloudy",
      {"ride": {"trigger": "minecraft:started_riding", "conditions": {"player": [{
          "condition": "minecraft:entity_properties", "entity": "this",
          "predicate": {"minecraft:vehicle": {"minecraft:entity_type": rid("cloudy")}}}]}}})
    A("sculk_trader", "pink_deep_dark", "sculk_gem", "Hushed Haggling", "Trade with a sculk person",
      {"trade": {"trigger": "minecraft:villager_trade", "conditions": {"villager": [{
          "condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": rid("sculk_person")}}]}}})


# ============================================================================================ the Cloud Realm, round 11
CLOUD_FLOWERS = {"cloud_puff": ("Cloud Puff", "white_dye"), "halo_lily": ("Halo Lily", "yellow_dye"),
                 "breezebell": ("Breezebell", "light_blue_dye"), "stardust_daisy": ("Stardust Daisy", "purple_dye")}
HOLY_GEAR = {"sword": ("Sword", "swords"), "pickaxe": ("Pickaxe", "pickaxes"), "axe": ("Axe", "axes"), "shovel": ("Shovel", "shovels"),
             "hoe": ("Hoe", "hoes"), "helmet": ("Helmet", "head_armor"), "chestplate": ("Chestplate", "chest_armor"),
             "leggings": ("Leggings", "leg_armor"), "boots": ("Boots", "foot_armor")}
ROUND11_PARTICLES = {"pink_flame": 5, "prism_spark": 8, "holy_spark": 6}


# ============================================================================================ round 14
def generate_round14():
    """The trans torch (ModBlocks.TRANS_TORCH and its wall twin): vanilla's torch models with our texture, one item that
    places either, and four from a prism shard, coal and a stick."""
    model("trans_torch", {"parent": "minecraft:block/template_torch", "textures": {"torch": block_tex("trans_torch")}})
    model("trans_wall_torch", {"parent": "minecraft:block/template_torch_wall", "textures": {"torch": block_tex("trans_torch")}})
    blockstate("trans_torch", {"variants": {"": {"model": f"{NS}:block/trans_torch"}}})
    wall = f"{NS}:block/trans_wall_torch"
    blockstate("trans_wall_torch", {"variants": {"facing=east": {"model": wall}, "facing=north": {"model": wall, "y": 270},
                                                 "facing=south": {"model": wall, "y": 90}, "facing=west": {"model": wall, "y": 180}}})
    model("trans_torch", {"parent": "minecraft:item/generated", "textures": {"layer0": block_tex("trans_torch")}}, kind="item")
    item_def("trans_torch", f"{NS}:item/trans_torch")
    name("trans_torch", "Trans Torch")
    name("trans_wall_torch", "Trans Wall Torch")
    loot("trans_torch", loot_self("trans_torch"))
    loot("trans_wall_torch", loot_self("trans_wall_torch", drop="trans_torch"))
    recipe("trans_torch", shaped("trans_torch", ["P", "C", "S"], {"P": "prism_shard", "C": "#minecraft:coals", "S": "minecraft:stick"},
                                 count=4, category="equipment"))

    # ---- Kira, in Maddie's home once Maddie is gone (entity/Kira), and her dialogue (KIRA_DIALOGUE).
    simple_item("kira_spawn_egg", "Kira Spawn Egg")
    NAMES["entity.transdimension.kira"] = "Kira"
    for key, line in KIRA_DIALOGUE.items():
        NAMES[f"dialogue.transdimension.kira.{key}"] = line


def generate_round15():
    """Maddie's two friends in the Fairy Realm's cutscene (entity/SculkArcher, world/FairyCutscene): their name, what drops
    where they fall, and the scene's new lines."""
    NAMES["entity.transdimension.sculk_archer"] = "Sculk Archer"
    entity_loot("sculk_archer", [
        {"entries": [{"type": "minecraft:item", "name": "minecraft:bow", "functions": [
            {"function": "minecraft:set_damage", "damage": {"type": "minecraft:uniform", "min": 0.3, "max": 0.8}}]}], "rolls": 1.0},
        {"entries": [counted("minecraft:arrow", 2, 6)], "rolls": 1.0},
        {"entries": [counted("sculk_gem", 1, 2)], "rolls": 1.0},
        {"conditions": [{"condition": "minecraft:random_chance", "chance": 0.25}],
         "entries": [{"type": "minecraft:item", "name": "minecraft:echo_shard"}], "rolls": 1.0},
    ])
    NAMES.update({
        "message.transdimension.cutscene.maddie_4": "I didn't come alone. NOW!",
        "message.transdimension.cutscene.fairy_4": "Arrows? How quaint.",
        "message.transdimension.cutscene.maddie_5": "No... no, no, NO!",
    })


def generate_round18():
    """The Holy Bow (item/HolyBowItem): holy gold like the rest of the holy gear (unbreakable), and every shot is two
    arrows. Its three drawn frames work like vanilla's bow; it takes the bow's enchantments. Found in the Cloud Realm's
    heavenly ruins and ruined churches (tools/generate_heavenly_ruins.py)."""
    def frame(suffix):
        return {"type": "minecraft:model", "model": f"{NS}:item/holy_bow{suffix}"}

    for suffix in ("", "_pulling_0", "_pulling_1", "_pulling_2"):
        model(f"holy_bow{suffix}", {"parent": "minecraft:item/bow", "textures": {"layer0": f"{NS}:item/holy_bow{suffix}"}}, kind="item")
    write(os.path.join(ASSETS, "items", "holy_bow.json"), {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item", "on_false": frame(""),
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                    "entries": [{"threshold": 0.65, "model": frame("_pulling_1")}, {"threshold": 0.9, "model": frame("_pulling_2")}],
                    "fallback": frame("_pulling_0")}}})
    name("holy_bow", "Holy Bow", kind="item")
    NAMES["item.transdimension.holy_bow.lore"] = "Every arrow flies twice"
    tag("item", "enchantable/bow", "holy_bow")


def generate_round17():
    """Candy canes from the Candy Cane Grove: the candy cane (ModBlocks.CANDY_CANE in the ground, a cross like a flower;
    ModItems.CANDY_CANE in the hand, a snack that plants itself back) and the candy cane block of the giant canes
    (CandyCaneFeature), which breaks into two to four candy canes unless mined with Silk Touch. Four candy canes make one."""
    blockstate("candy_cane", {"variants": {"": {"model": f"{NS}:block/candy_cane"}}})
    model("candy_cane", {"parent": "minecraft:block/cross", "textures": {"cross": block_tex("candy_cane")}})
    simple_item("candy_cane", "Candy Cane")
    name("candy_cane", "Candy Cane")
    loot("candy_cane", loot_self("candy_cane"))
    blockstate("candy_cane_block", from_template("cherry_log", "cherry_log", "candy_cane_block"))
    textures = {"end": block_tex("candy_cane_block_top"), "side": block_tex("candy_cane_block")}
    model("candy_cane_block", {"parent": "minecraft:block/cube_column", "textures": textures})
    for axis in "xyz":
        model(f"candy_cane_block_{axis}", {"parent": f"minecraft:block/cube_column_uv_locked_{axis}", "textures": textures})
    item_def("candy_cane_block", f"{NS}:block/candy_cane_block")
    name("candy_cane_block", "Candy Cane Block")
    mine("candy_cane_block", "pickaxe")
    loot("candy_cane_block", table("candy_cane_block", [{"entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "conditions": [SILK], "name": rid("candy_cane_block")},
        {"type": "minecraft:item", "name": rid("candy_cane"), "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2.0, "max": 4.0}},
            {"function": "minecraft:explosion_decay"}]}]}], "rolls": 1.0}]))
    recipe("candy_cane_block", shaped("candy_cane_block", ["##", "##"], {"#": "candy_cane"}))


# What Kira says once you have Maddie's gifts, picked at random (one per talk).
# Kira's dialogue (MaddieDialogueScreen with Kira talking): Maddie was her girlfriend. Some answers only come up after
# others (asking about Maddie leads on to "fairy", asking how she is leads on to "now").
KIRA_DIALOGUE = {
    "greeting": "Oh... hi. Sorry, I wasn't expecting anyone. I'm Kira. This is Maddie's house. Was. I'm looking after it "
                "now.",
    "greeting_again": "Hey, you came back. It's nice, having someone around. The house is so quiet without her.",
    "who": "I'm Kira. Maddie was my girlfriend. We met under the heart clouds; she talked about eggs for an hour and I "
           "fell for her before she'd even finished. She said my head was the perfect size for kissing.",
    "maddie": "Someone woke the Trans Fairy, and Maddie went through her door to warn them. She said she'd be home for "
              "dinner. She never came back. Nobody found anything of her. It's like she was just... erased.",
    "fairy": "If you ever face her... make her pay. No. Sorry. Maddie never wanted anyone hurt, not even her. Just be "
             "careful out there. I can't lose anyone else.",
    "okay": "Not really. Some mornings I still make two cups of cocoa. Her pillow still smells like strawberries. I keep "
            "waiting for her to float in through the window and laugh at me for crying. ...Thanks for asking.",
    "now": "Keep the house. Water her flowers; they still come up in trans colours every morning, like she's still here. "
           "She wanted the Egg House to be a safe place for anyone who needs one. So I'll keep it that way. For her.",
    "gifts": "She left these with me: her Trans Wand and her Trans Wings. She made them for travellers, and she always "
             "said the next one to find the house should have them. It's what she'd want. Will you look after them?",
    "gifts_given": "How are her wings treating you? Crouch to charge, jump to launch, tap jump to flap: she made me learn it "
                   "by heart. Fly far for her. And come back sometimes and tell me where they took you.",
    "thanks": "Thank you. She'd be so happy to see them fly again. Come and visit, okay? I don't like being alone up here.",
    "later": "That's okay. They'll be here when you're ready. She'd have said the same.",
    "option.who": "Who are you?",
    "option.maddie": "What happened to Maddie?",
    "option.fairy": "And the fairy?",
    "option.okay": "Are you okay?",
    "option.now": "What will you do now?",
    "option.gifts": "Did she leave anything?",
    "option.accept": "I'll look after them. I promise.",
    "option.back": "Not yet.",
    "option.bye": "Take care, Kira.",
}


# The Cloud Bible's pages, in order (ModItems.CLOUD_BIBLE_PAGES must match their number).
CLOUD_BIBLE = [
    "\u2601 The Cloud Bible \u2601\n\nThe words of the angels of the Cloud Realm, for anyone who climbs this high.",
    "In the beginning there was the sky, and the sky was soft.\n\nAnd the clouds said: let there be blue, and pink, and white. And it was good.",
    "Blessed are they who become who they are, for the sky has always known their name.",
    "Thou shalt not deadname.\n\nThou shalt share thy cloud candy.\n\nThou shalt pet the Cloudies, gently.",
    "When thou fallest, fear not: the turtle upon the cloud shall fish thee out of the sky, in a white boat, on a golden lead.",
    "Rest a while in the holy water, and be made whole. Leave the golden things for those who come after.",
    "Go now, and be valid.\n\nAmen. \u2726",
]


def generate_round11():
    # ---- cloudcite: the realm's own stone (in place of calcite) and what's made from it
    stone_family("cloudcite", "Cloudcite", "cloudcite", "Cloudcite")
    stone_family("polished_cloudcite", "Polished Cloudcite", "polished_cloudcite", "Polished Cloudcite", wall_too=False)
    stone_family("cloudcite_bricks", "Cloudcite Bricks", "cloudcite_brick", "Cloudcite Brick")
    cube("chiseled_cloudcite", "Chiseled Cloudcite")
    cube("gilded_cloudcite", "Gilded Cloudcite")
    R = recipe
    stone_recipes("cloudcite", "cloudcite")
    stone_recipes("polished_cloudcite", "polished_cloudcite", wall=False, cut_from=("cloudcite",))
    stone_recipes("cloudcite_brick", "cloudcite_bricks", cut_from=("cloudcite", "polished_cloudcite"))
    R("polished_cloudcite", shaped("polished_cloudcite", ["##", "##"], {"#": "cloudcite"}, 4))
    R("cloudcite_bricks", shaped("cloudcite_bricks", ["##", "##"], {"#": "polished_cloudcite"}, 4))
    R("chiseled_cloudcite", shaped("chiseled_cloudcite", ["#", "#"], {"#": "cloudcite_brick_slab"}))
    R("gilded_cloudcite", shaped("gilded_cloudcite", ["###", "#G#", "###"], {"#": "cloudcite_bricks", "G": "minecraft:gold_ingot"}, 8))
    for result, count in (("polished_cloudcite", 1), ("cloudcite_bricks", 1), ("chiseled_cloudcite", 1)):
        R(f"{result}_from_cloudcite_stonecutting", stonecutting(result, "cloudcite", count))

    # ---- holy gold: far brighter than gold (the heavenly ruins' trim; never crafted)
    cube("holy_gold_block", "Block of Holy Gold")
    tag("block", "needs_iron_tool", "holy_gold_block")
    tag("block", "beacon_base_blocks", "holy_gold_block")

    # ---- cloud leaves: the cloud trees' puffs (no sapling: the trees only grow where the realm made them)
    blockstate("cloud_leaves", {"variants": {"": {"model": f"{NS}:block/cloud_leaves"}}})
    model("cloud_leaves", {"parent": "minecraft:block/leaves", "textures": {"all": block_tex("cloud_leaves")}})
    item_def("cloud_leaves", f"{NS}:block/cloud_leaves")
    name("cloud_leaves", "Cloud Leaves")
    mine("cloud_leaves", "hoe")
    loot("cloud_leaves", table("cloud_leaves", [
        {"entries": [{"type": "minecraft:item", "conditions": [SHEARS_OR_SILK], "name": rid("cloud_leaves")}], "rolls": 1.0},
        {"conditions": [{"condition": "minecraft:inverted", "term": SHEARS_OR_SILK}], "entries": [
            {"type": "minecraft:item", "conditions": [{"chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1],
                                                       "condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune"}],
             "functions": [{"count": {"type": "minecraft:uniform", "max": 2.0, "min": 1.0}, "function": "minecraft:set_count"},
                           {"function": "minecraft:explosion_decay"}], "name": "minecraft:stick"}], "rolls": 1.0}]))
    for kind in ("block", "item"):
        tag(kind, "leaves", "cloud_leaves")

    # ---- the realm's flowers (they grow on its islands' grass, not on the clouds)
    for flower_id, (english, dye) in CLOUD_FLOWERS.items():
        cross_plant(flower_id, english, f"potted_{flower_id}", f"Potted {english}")
        for t in ("small_flowers", "bee_attractive"):
            tag("block", t, flower_id)
        tag("item", "small_flowers", flower_id)
        tag("item", "bee_food", flower_id)
        R(f"{dye}_from_{flower_id}", shapeless(f"minecraft:{dye}", [flower_id], 1, category="misc", group=dye))

    # ---- holy water: golden water from the heavenly ruins' fountains (HolyWaterFluid); it counts as water by tag, so
    # it's swum in, drowned in and put out like water. Bathing in it Blesses you.
    blockstate("holy_water", {"variants": {"": {"model": f"{NS}:block/holy_water"}}})
    model("holy_water", {"textures": {"particle": block_tex("holy_water_still")}})
    name("holy_water", "Holy Water")
    simple_item("holy_water_bucket", "Bucket of Holy Water")
    tag("fluid", "water", "holy_water", "flowing_holy_water")
    NAMES["effect.transdimension.blessed"] = "Blessed"

    # ---- the Cloud Bible (ModItems.CLOUD_BIBLE, a written book of its own; ruined churches): its name, its pages, and a
    # place on lecterns
    simple_item("cloud_bible", "Cloud Bible")
    for i, page in enumerate(CLOUD_BIBLE, start=1):
        NAMES[f"book.transdimension.cloud_bible.{i}"] = page
    tag("item", "lectern_books", "cloud_bible")

    # ---- cloud candy: sugar spun round a stick with a tuft of cloud
    simple_item("cloud_candy", "Cloud Candy")
    NAMES["item.transdimension.cloud_candy.lore"] = "Float up on a sugar cloud, then drift gently down"
    R("cloud_candy", shapeless("cloud_candy", ["minecraft:sugar", "minecraft:sugar", "minecraft:white_wool", "minecraft:stick"], 2,
                               category="misc"))

    # ---- holy gold gear: only ever found in the heavenly ruins (and up the beanstalk); it never breaks
    for piece, (english, kind_tag) in HOLY_GEAR.items():
        item = f"holy_golden_{piece}"
        armor = kind_tag.endswith("_armor")
        model(item, {"parent": "minecraft:item/generated" if armor else "minecraft:item/handheld",
                     "textures": {"layer0": f"{NS}:item/{item}"}}, kind="item")
        item_def(item, f"{NS}:item/{item}")
        name(item, f"Holy Golden {english}", kind="item")
        tag("item", kind_tag, item)
        if armor:
            tag("item", "trimmable_armor", item)
    write(os.path.join(ASSETS, "equipment", "holy_gold.json"), {"layers": {
        "humanoid": [{"texture": f"{NS}:holy_gold"}], "humanoid_leggings": [{"texture": f"{NS}:holy_gold"}]}})
    tag("item", "repairs_holy_gear", "holy_gold_block", ns=NS)
    NAMES["item.transdimension.holy_gear.lore"] = "Holy loot: it never breaks"

    # ---- angels, the cloud turtle and his boat, and the Fairy Jar's little fairy
    simple_item("angel_spawn_egg", "Angel Spawn Egg")
    NAMES.update({"entity.transdimension.angel": "Angel", "entity.transdimension.cloud_turtle": "Cloud Turtle",
                  "entity.transdimension.cloud_boat": "Cloud Boat", "entity.transdimension.jar_fairy": "Jar Fairy",
                  "message.transdimension.rescue.caught": "☁ Hold on tight! The cloud turtle has you ☁",
                  "message.transdimension.rescue.landed": "☁ Home safe! Sneak to hop out of the boat ☁"})
    entity_loot("angel", [{"entries": [counted("minecraft:feather", 1, 3)], "rolls": 1.0},
                          {"entries": [counted("minecraft:glowstone_dust", 0, 2)], "rolls": 1.0}])

    # ---- the mod's own particles: the ritual candles' pink flames, prismatic stars and holy motes
    for particle, frames in ROUND11_PARTICLES.items():
        write(os.path.join(ASSETS, "particles", f"{particle}.json"), {"textures": [f"{NS}:{particle}_{i}" for i in range(frames)]})


# ============================================================================================ round 12
def generate_round12():
    """The Cloud Realm's cloud chest: a chest-shaped barrel (CloudChestBlock) in white wood with golden trim and latch;
    its lid lifts a little while it's open."""
    def faces(texture, uv):
        return {side: {"uv": uv, "texture": texture} for side in ("north", "south", "east", "west")}

    def elements(lift):
        return [
            {"from": [1, 0, 1], "to": [15, 10, 15], "faces": {**faces("#body", [1, 6, 15, 16]),
                                                              "down": {"uv": [1, 1, 15, 15], "texture": "#top", "cullface": "down"}}},
            {"from": [1, 10 + lift, 1], "to": [15, 14 + lift, 15], "faces": {**faces("#lid", [1, 2, 15, 6]),
                                                                            "up": {"uv": [1, 1, 15, 15], "texture": "#top"},
                                                                            "down": {"uv": [1, 1, 15, 15], "texture": "#top"}}},
            {"from": [7, 8 + lift, 0], "to": [9, 12 + lift, 1], "faces": {
                side: {"uv": [7, 6, 9, 10], "texture": "#latch"} for side in ("north", "south", "east", "west", "up", "down")}},
        ]
    textures = {"particle": block_tex("cloud_chest_body"), "body": block_tex("cloud_chest_body"), "lid": block_tex("cloud_chest_lid"),
                "top": block_tex("cloud_chest_top"), "latch": block_tex("holy_gold_block")}
    model("cloud_chest", {"parent": "minecraft:block/block", "textures": textures, "elements": elements(0)})
    model("cloud_chest_open", {"parent": "minecraft:block/block", "textures": textures, "elements": elements(2)})
    rotation = {"north": 0, "east": 90, "south": 180, "west": 270, "up": 0, "down": 0}
    variants = {}
    for facing, y in rotation.items():
        for open_ in ("false", "true"):
            v = {"model": f"{NS}:block/cloud_chest" + ("_open" if open_ == "true" else "")}
            if y:
                v["y"] = y
            variants[f"facing={facing},open={open_}"] = v
    blockstate("cloud_chest", {"variants": variants})
    item_def("cloud_chest", f"{NS}:block/cloud_chest")
    name("cloud_chest", "Cloud Chest")
    mine("cloud_chest", "axe")
    loot("cloud_chest", loot_self("cloud_chest"))
    recipe("cloud_chest", shaped("cloud_chest", ["PPP", "PGP", "PPP"], {"P": "minecraft:pale_oak_planks", "G": "minecraft:gold_ingot"},
                                 category="misc"))

    # The trans spark (ModParticles.TRANS_SPARK): the Fairy Realm's own sparkle, eight frames (trans_spark_frames()).
    write(os.path.join(ASSETS, "particles", "trans_spark.json"), {"textures": [f"{NS}:trans_spark_{i}" for i in range(8)]})


def generate_round11_advancements():
    A = advancement
    A("holy_loot", "sky_portal", "holy_golden_sword", "Holy Loot", "Find a piece of holy gold gear in a heavenly ruin",
      {"holy": has_any([f"holy_golden_{piece}" for piece in HOLY_GEAR] + ["holy_bow"])})
    A("blessed", "sky_portal", "holy_water_bucket", "Bless You!", "Be blessed by an angel, or by bathing in holy water",
      {"blessed": {"trigger": "minecraft:effects_changed", "conditions": {"effects": {f"{NS}:blessed": {}}}}})
    A("lucky_catch", "sky_portal", "cloud_candy", "Lucky Catch", "Fall off the Cloud Realm and get fished out by the cloud turtle",
      {"caught": {"trigger": "minecraft:started_riding", "conditions": {"player": [{
          "condition": "minecraft:entity_properties", "entity": "this",
          "predicate": {"minecraft:vehicle": {"minecraft:entity_type": rid("cloud_boat")}}}]}}})


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
        # Pink sculk sounds soft and sparkly instead of creepy: moss underfoot, chimes where vanilla's sculk clicks and
        # shrieks (ModSounds.PINK_SCULK and the sculk sensor and shrieker mixins).
        "block.pink_sculk.break": {"subtitle": "subtitles.block.generic.break", "sounds": [
            event("minecraft:block.moss.break", pitch=1.15), event("minecraft:block.moss.break", pitch=1.15),
            event("minecraft:block.amethyst_cluster.break", volume=0.6, pitch=1.4)]},
        "block.pink_sculk.step": {"subtitle": "subtitles.block.generic.footsteps", "sounds": [event("minecraft:block.moss.step", pitch=1.15)]},
        "block.pink_sculk.place": {"subtitle": "subtitles.block.generic.place", "sounds": [event("minecraft:block.moss.place", pitch=1.15)]},
        "block.pink_sculk.hit": {"subtitle": "subtitles.block.generic.hit", "sounds": [event("minecraft:block.moss.hit", pitch=1.15)]},
        "block.pink_sculk.fall": {"subtitle": "subtitles.block.generic.fall", "sounds": [event("minecraft:block.moss.fall", pitch=1.15)]},
        "block.pink_sculk_sensor.clicking": {"subtitle": "subtitles.transdimension.block.pink_sculk_sensor.clicking", "sounds": [
            event("minecraft:block.amethyst_block.chime", volume=0.9, pitch=1.4)]},
        "block.pink_sculk_sensor.clicking_stop": {"subtitle": "subtitles.transdimension.block.pink_sculk_sensor.clicking_stop", "sounds": [
            event("minecraft:block.amethyst_cluster.step", volume=0.6, pitch=1.6)]},
        "block.pink_sculk_shrieker.shriek": {"subtitle": "subtitles.transdimension.block.pink_sculk_shrieker.shriek", "sounds": [
            event("minecraft:block.amethyst_block.resonate", pitch=1.3), event("minecraft:entity.allay.ambient_without_item", pitch=1.1)]},
        "block.pink_sculk_catalyst.bloom": {"subtitle": "subtitles.transdimension.block.pink_sculk_catalyst.bloom", "sounds": [
            event("minecraft:block.amethyst_block.resonate", volume=0.8, pitch=1.6)]},
    }
    # The pink warden's voice (PinkWarden#playSound, ModSounds.soften): chimes, sniffles and moss, nothing scary.
    warden = {
        "ambient": [event("minecraft:entity.allay.ambient_without_item", volume=0.6, pitch=0.7),
                    event("minecraft:block.amethyst_block.resonate", volume=0.4, pitch=0.8)],
        "hurt": [event("minecraft:entity.allay.hurt", pitch=0.7)],
        "death": [event("minecraft:entity.allay.death", pitch=0.7)],
        "step": [event("minecraft:block.moss.step", pitch=0.8)],
        "roar": [event("minecraft:block.amethyst_block.resonate", pitch=0.7), event("minecraft:entity.allay.item_thrown", pitch=0.7)],
        "sniff": [event("minecraft:entity.sniffer.sniffing", volume=0.6, pitch=1.3)],
        "sonic_charge": [event("minecraft:block.beacon.power_select", volume=0.6, pitch=1.6)],
        "sonic_boom": [event("minecraft:entity.firework_rocket.twinkle", pitch=1.2), event("minecraft:block.amethyst_block.break", pitch=0.9)],
        "attack": [event("minecraft:entity.slime.attack", pitch=1.3)],
        "dig": [event("minecraft:block.moss.break", pitch=0.8)],
        "emerge": [event("minecraft:block.moss.place", pitch=0.8), event("minecraft:block.amethyst_block.chime", pitch=0.9)],
        "listening": [event("minecraft:block.amethyst_block.chime", volume=0.6, pitch=1.2)],
    }
    for what, entries in warden.items():
        sounds[f"entity.pink_warden.{what}"] = {"subtitle": f"subtitles.transdimension.entity.pink_warden.{what}", "sounds": entries}
        NAMES[f"subtitles.transdimension.entity.pink_warden.{what}"] = {
            "ambient": "Pink warden hums", "hurt": "Pink warden hurts", "death": "Pink warden fades", "step": "Pink warden pads",
            "roar": "Pink warden sings out", "sniff": "Pink warden sniffles", "sonic_charge": "Pink warden glows",
            "sonic_boom": "Pink warden sparkles", "attack": "Pink warden bonks", "dig": "Pink warden burrows",
            "emerge": "Pink warden pops up", "listening": "Pink warden listens"}[what]
    write(os.path.join(ASSETS, "sounds.json"), sounds)


def generate_recipe_unlocks():
    """Unlocks for the recipe book, one hidden advancement per recipe as vanilla does it (advancement/recipes/). A recipe
    shows up in the book once the player picks up one of its ingredients; without these the book never shows the mod's
    recipes, though they still craft. The realm's own ingredients do the unlocking when a recipe has any (as vanilla's
    fences unlock with planks, not sticks). This runs after every recipe is written, so it covers all of them."""
    out = os.path.join(ADV_DIR, "recipes")
    if os.path.isdir(out):
        for f in os.listdir(out):
            os.remove(os.path.join(out, f))
    recipe_dir = os.path.join(DATA, NS, "recipe")
    for f in sorted(os.listdir(recipe_dir)):
        if not f.endswith(".json"):
            continue
        with open(os.path.join(recipe_dir, f), encoding="utf-8") as fh:
            r = json.load(fh)
        if "key" in r:
            ingredients = [r["key"][c] for row in r["pattern"] for c in row if c != " "]
        elif "ingredients" in r or "ingredient" in r:
            ingredients = r.get("ingredients") or [r["ingredient"]]
        else:
            ingredients = [r[part] for part in ("template", "base", "addition") if part in r]
        unique = []
        for i in ingredients:
            if i not in unique:
                unique.append(i)
        ours = [i for i in unique if (i if isinstance(i, str) else i[0]).lstrip("#").startswith(f"{NS}:")]
        criteria = {}
        for i in ours or unique:
            path = (i if isinstance(i, str) else i[0]).lstrip("#").split(":")[-1].replace("/", "_")
            key, n = f"has_{path}", 2
            while key in criteria:
                key, n = f"has_{path}_{n}", n + 1
            criteria[key] = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": i}]}}
        recipe_id = f"{NS}:{f[:-5]}"
        criteria["has_the_recipe"] = {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": recipe_id}}
        write(os.path.join(out, f), {"parent": "minecraft:recipes/root", "criteria": criteria,
                                     "requirements": [["has_the_recipe", *[k for k in criteria if k != "has_the_recipe"]]],
                                     "rewards": {"recipes": [recipe_id]}})


def write_tags():
    for (kind, ns, path), values in sorted(TAGS.items()):
        write(os.path.join(DATA, ns, "tags", kind, f"{path}.json"), {"replace": False, "values": sorted(values)})


def write_lang():
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path, encoding="utf-8") as f:
        lang = json.load(f)
    # Keys for things that are gone: trans water, the Crystal Pearl's old compass directions, Fairy Sanctum maps.
    stale = ("item.transdimension.trans_crystal_pearl.tug", "item.transdimension.trans_crystal_pearl.here",
             "filled_map.transdimension.fairy_sanctum")
    lang = {k: v for k, v in lang.items() if "trans_water" not in k and not k.startswith("biome.")
            and not k.startswith("direction.transdimension.") and k not in stale}
    for biome, english in BIOMES.items():
        lang[f"biome.transdimension.{biome}"] = english
    lang.update(NAMES)
    ordered = dict(sorted(lang.items(), key=lambda kv: (kv[0].split(".")[0], kv[0])))
    write(path, ordered)


# Our block and item tags that other generators write (never wiped here): without the first, the pink ancient city's
# processor lists fail to load and no world can be created.
OTHER_GENERATORS_TAGS = {"block/pink_ancient_city_replaceable.json"}     # generate_ancient_city.py


def remove_stale():
    stale = [
        os.path.join(ASSETS, "blockstates", "trans_water.json"),
        os.path.join(ASSETS, "models", "block", "trans_water.json"),
        os.path.join(ASSETS, "models", "block", "trans_log_horizontal.json"),
        os.path.join(ASSETS, "items", "trans_water_bucket.json"),
        os.path.join(ASSETS, "models", "item", "trans_water_bucket.json"),
        os.path.join(ASSETS, "models", "item", "pride_blossom.json"),
        os.path.join(DATA, NS, "recipe", "glass_from_trans_sand.json"),
        os.path.join(ASSETS, "models", "item", "fairy_jar.json"),
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
                if f.endswith(".json") and f"{sub}/{f}" not in OTHER_GENERATORS_TAGS:
                    os.remove(os.path.join(d, f))


def main():
    remove_stale()
    generate_blocks()
    generate_vegetation()
    generate_woods_and_flowers()
    generate_recipes()
    generate_woods_and_flowers_recipes()
    generate_trans_prismarine()
    generate_creatures()
    generate_fairy_realm_data()
    generate_furniture()
    generate_misc()
    generate_round9()
    generate_round11()
    generate_round12()
    generate_round14()
    generate_round15()
    generate_round17()
    generate_round18()
    generate_sounds()
    generate_advancements()
    generate_round9_advancements()
    generate_round11_advancements()
    generate_recipe_unlocks()
    write_tags()
    write_lang()
    print(f"Wrote {len(TAGS)} tags and {len(NAMES)} names.")


if __name__ == "__main__":
    main()
