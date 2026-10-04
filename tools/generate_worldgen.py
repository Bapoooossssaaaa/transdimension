#!/usr/bin/env python3
"""
Generates the Trans Realm's world: the dimension and its multi-noise biome layout, the dimension type,
the day/night timeline (pink sunrises, indigo starry nights), the noise settings' surface rules,
every biome, and the trees, flowers, crystals and rocks they place.

    python3 tools/generate_worldgen.py

Everything is vanilla 26.2 data-pack JSON. The village structure is made by tools/generate_villages.py.
"""
import copy
import itertools
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "transdimension"
WG = os.path.join(DATA, NS, "worldgen")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def cf(name, obj):
    write(os.path.join(WG, "configured_feature", f"{name}.json"), obj)


def pf(name, feature, placement):
    write(os.path.join(WG, "placed_feature", f"{name}.json"), {"feature": feature, "placement": placement})


def state(name, **props):
    s = {"Name": name if ":" in name else f"{NS}:{name}"}
    if props:
        s["Properties"] = {k: str(v).lower() for k, v in props.items()}
    return s


def simple(st):
    return {"type": "minecraft:simple_state_provider", "state": st}


LEAVES = state("trans_leaves", distance=7, persistent=False, waterlogged=False)
LOG = state("trans_log", axis="y")
# The themed forests' own woods (ModBlocks.WoodFamily).
PEARL_LOG = state("pearl_log", axis="y")
SKY_LOG = state("sky_log", axis="y")
TWILIGHT_LOG = state("twilight_log", axis="y")
BLUSH_LOG = state("blush_log", axis="y")


def leaves_of(name):
    return state(name, distance=7, persistent=False, waterlogged=False)
BELOW_TRUNK = {"type": "minecraft:rule_based_state_provider", "rules": [{
    "if_true": {"type": "minecraft:not", "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:cannot_replace_below_tree_trunk"}},
    "then": simple(state("trans_dirt"))}]}


def petals_provider():
    return {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("trans_petals", facing=f, flower_amount=n), "weight": 1}
        for n in (1, 2, 3, 4) for f in ("north", "east", "south", "west")]}


def tree(trunk_placer, foliage_placer, minimum_size, decorators=(), foliage=LEAVES, trunk=LOG, ignore_vines=True):
    return {"type": "minecraft:tree", "config": {
        "below_trunk_provider": BELOW_TRUNK,
        "decorators": list(decorators),
        "foliage_placer": foliage_placer,
        "foliage_provider": simple(foliage),
        "ignore_vines": ignore_vines,
        "minimum_size": minimum_size,
        "trunk_placer": trunk_placer,
        "trunk_provider": simple(trunk)}}


def straight(base, a, b):
    return {"type": "minecraft:straight_trunk_placer", "base_height": base, "height_rand_a": a, "height_rand_b": b}


SAPLING_CHECK = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": state("trans_sapling", stage=0)}}
BEEHIVE = {"type": "minecraft:beehive", "probability": 0.03}
PETALS = {"type": "minecraft:place_on_ground", "block_state_provider": petals_provider(), "height": 2, "radius": 4, "tries": 64}
VINES = {"type": "minecraft:leave_vine", "probability": 0.25}
HANGING_LANTERNS = {"type": "minecraft:attached_to_leaves",
                    "block_provider": simple(state("trans_lantern", hanging=True, waterlogged=False)),
                    "directions": ["down"], "exclusion_radius_xz": 3, "exclusion_radius_y": 1, "probability": 0.06,
                    "required_empty_blocks": 2}
HANGING_CRYSTALS = {"type": "minecraft:attached_to_leaves",
                    "block_provider": simple(state("trans_crystal_cluster", facing="down", waterlogged=False)),
                    "directions": ["down"], "exclusion_radius_xz": 2, "exclusion_radius_y": 0, "probability": 0.12,
                    "required_empty_blocks": 1}


def patch(count, xz=6, y=2):
    """Vanilla 26.2's way of making a patch: many tries spread around a point, only into air."""
    return [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:random_offset",
         "xz_spread": {"type": "minecraft:trapezoid", "max": xz, "min": -xz, "plateau": 0},
         "y_spread": {"type": "minecraft:trapezoid", "max": y, "min": -y, "plateau": 0}},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}},
    ]


def surface_trees(count):
    return [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, SAPLING_CHECK, {"type": "minecraft:biome"}]


def weighted_count(pairs):
    return {"type": "minecraft:weighted_list", "distribution": [{"data": d, "weight": w} for d, w in pairs]}


# ============================================================================================ features
def generate_features():
    # ---- trees
    cf("trans_tree", tree(straight(5, 2, 0), {"type": "minecraft:blob_foliage_placer", "height": 3, "offset": 0, "radius": 2},
                          {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1}))
    cf("trans_tree_bees", tree(straight(5, 2, 0), {"type": "minecraft:blob_foliage_placer", "height": 3, "offset": 0, "radius": 2},
                               {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1}, [BEEHIVE]))
    cherry_trunk = {"type": "minecraft:cherry_trunk_placer", "base_height": 7, "height_rand_a": 1, "height_rand_b": 0,
                    "branch_count": weighted_count([(1, 1), (2, 1), (3, 1)]),
                    "branch_end_offset_from_top": {"type": "minecraft:uniform", "max_inclusive": 0, "min_inclusive": -1},
                    "branch_horizontal_length": {"type": "minecraft:uniform", "max_inclusive": 4, "min_inclusive": 2},
                    "branch_start_offset_from_top": {"max_inclusive": -3, "min_inclusive": -4}}
    cherry_foliage = {"type": "minecraft:cherry_foliage_placer", "corner_hole_chance": 0.25, "hanging_leaves_chance": 0.16666667,
                      "hanging_leaves_extension_chance": 0.33333334, "height": 5, "offset": 0, "radius": 4,
                      "wide_bottom_layer_hole_chance": 0.25}
    cf("trans_cherry_tree", tree(cherry_trunk, cherry_foliage, {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2}, [PETALS]))
    cf("fancy_trans_tree", tree({"type": "minecraft:fancy_trunk_placer", "base_height": 3, "height_rand_a": 11, "height_rand_b": 0},
                                {"type": "minecraft:fancy_foliage_placer", "height": 4, "offset": 4, "radius": 2},
                                {"type": "minecraft:two_layers_feature_size", "limit": 0, "min_clipped_height": 4, "upper_size": 0}, [PETALS]))
    cf("heartwood_tree", tree({"type": "minecraft:dark_oak_trunk_placer", "base_height": 7, "height_rand_a": 3, "height_rand_b": 1},
                              {"type": "minecraft:dark_oak_foliage_placer", "offset": 0, "radius": 0},
                              {"type": "minecraft:three_layers_feature_size", "upper_size": 2}, [HANGING_LANTERNS, PETALS]))
    cf("frosted_trans_tree", tree(straight(5, 2, 1),
                                  {"type": "minecraft:spruce_foliage_placer",
                                   "offset": {"type": "minecraft:uniform", "max_inclusive": 2, "min_inclusive": 0},
                                   "radius": {"type": "minecraft:uniform", "max_inclusive": 3, "min_inclusive": 2},
                                   "trunk_height": {"type": "minecraft:uniform", "max_inclusive": 2, "min_inclusive": 1}},
                                  {"type": "minecraft:two_layers_feature_size", "limit": 2, "lower_size": 0, "upper_size": 2}))
    cf("marsh_trans_tree", tree(straight(5, 3, 0), {"type": "minecraft:blob_foliage_placer", "height": 3, "offset": 0, "radius": 3},
                                {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1}, [VINES], ignore_vines=False))
    cf("crystal_trans_tree", tree(straight(5, 2, 0), {"type": "minecraft:blob_foliage_placer", "height": 3, "offset": 0, "radius": 2},
                                  {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1}, [HANGING_CRYSTALS]))
    cf("trans_bush", tree(straight(1, 0, 0), {"type": "minecraft:bush_foliage_placer", "height": 2, "offset": 1, "radius": 2},
                          {"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "upper_size": 0}))
    cf("fallen_trans_log", {"type": "minecraft:fallen_tree", "config": {
        "log_decorators": [{"type": "minecraft:attached_to_logs", "block_provider": {"type": "minecraft:weighted_state_provider", "entries": [
            {"data": state(f"{NS}:pride_blossom"), "weight": 2}, {"data": state("minecraft:brown_mushroom"), "weight": 1}]},
            "directions": ["up"], "probability": 0.12}],
        "log_length": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 4},
        "stump_decorators": [],
        "trunk_provider": simple(LOG)}})

    checked = lambda name: pf(f"{name}_checked", f"{NS}:{name}", [SAPLING_CHECK])
    for name in ("trans_tree", "trans_tree_bees", "trans_cherry_tree", "fancy_trans_tree", "heartwood_tree",
                 "frosted_trans_tree", "marsh_trans_tree", "crystal_trans_tree", "trans_bush", "fallen_trans_log"):
        checked(name)

    def selector(name, default, options):
        cf(name, {"type": "minecraft:random_selector", "config": {
            "default": f"{NS}:{default}_checked",
            "features": [{"chance": c, "feature": f"{NS}:{f}_checked"} for f, c in options]}})

    selector("trees_trans_meadow", "trans_tree_bees", [("fancy_trans_tree", 0.3), ("trans_bush", 0.25)])
    selector("trees_blossom_forest", "trans_tree", [("trans_cherry_tree", 0.35), ("fancy_trans_tree", 0.2), ("fallen_trans_log", 0.03)])
    selector("trees_heartwood_grove", "heartwood_tree", [("trans_cherry_tree", 0.15), ("trans_bush", 0.1)])
    selector("trees_crystal_grove", "crystal_trans_tree", [("trans_bush", 0.3)])

    pf("trees_trans_meadow", f"{NS}:trees_trans_meadow", surface_trees(weighted_count([(0, 9), (1, 2), (2, 1)])))
    pf("trees_blossom_forest", f"{NS}:trees_blossom_forest", surface_trees(weighted_count([(8, 9), (10, 1)])))
    pf("trees_heartwood_grove", f"{NS}:trees_heartwood_grove", surface_trees(weighted_count([(6, 9), (8, 1)])))
    pf("trees_crystal_grove", f"{NS}:trees_crystal_grove", surface_trees(weighted_count([(1, 3), (2, 1)])))
    pf("trees_frosted_fields", f"{NS}:frosted_trans_tree", surface_trees(weighted_count([(0, 6), (1, 3), (2, 1)])))
    pf("trees_lavender_marsh", f"{NS}:marsh_trans_tree",
       [{"type": "minecraft:count", "count": weighted_count([(2, 9), (3, 1)])}, {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 2},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, SAPLING_CHECK, {"type": "minecraft:biome"}])
    pf("trans_bushes", f"{NS}:trans_bush", surface_trees(weighted_count([(0, 3), (1, 2), (2, 1)])))

    # ---- themed forest trees
    pearl, sky, blush, twilight = (leaves_of(n) for n in ("pearl_leaves", "sky_leaves", "blush_leaves", "twilight_leaves"))
    small_blob = {"type": "minecraft:blob_foliage_placer", "height": 3, "offset": 0, "radius": 2}
    two_layers = {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1}
    # Pearlwood: tall slender white trees (vanilla's tall birch shape).
    cf("pearlwood_tree", tree(straight(5, 2, 6), small_blob, two_layers, [BEEHIVE], foliage=pearl, trunk=PEARL_LOG))
    cf("pearlwood_tree_short", tree(straight(5, 2, 0), small_blob, two_layers, foliage=pearl, trunk=PEARL_LOG))
    # Bluebell Woods: oaks and big fancy oaks in sky-blue leaves.
    cf("bluebell_tree", tree(straight(4, 2, 0), small_blob, two_layers, foliage=sky, trunk=SKY_LOG))
    cf("fancy_bluebell_tree", tree({"type": "minecraft:fancy_trunk_placer", "base_height": 3, "height_rand_a": 11, "height_rand_b": 0},
                                   {"type": "minecraft:fancy_foliage_placer", "height": 4, "offset": 4, "radius": 2},
                                   {"type": "minecraft:two_layers_feature_size", "limit": 0, "min_clipped_height": 4, "upper_size": 0},
                                   foliage=sky, trunk=SKY_LOG))
    # Twilight Thicket: dark oak canopies of twilight leaves, hung with glowing crystals.
    cf("twilight_tree", tree({"type": "minecraft:dark_oak_trunk_placer", "base_height": 6, "height_rand_a": 2, "height_rand_b": 1},
                             {"type": "minecraft:dark_oak_foliage_placer", "offset": 0, "radius": 0},
                             {"type": "minecraft:three_layers_feature_size", "upper_size": 2}, [HANGING_CRYSTALS], foliage=twilight,
                             trunk=TWILIGHT_LOG))
    # A single twilight sapling grows this smaller tree (four of them in a square grow the big one).
    cf("twilight_tree_small", tree(straight(4, 2, 1), {"type": "minecraft:blob_foliage_placer", "height": 3, "offset": 0, "radius": 2},
                                   two_layers, [HANGING_CRYSTALS], foliage=twilight, trunk=TWILIGHT_LOG))
    # Candy Floss Grove: round, fluffy puffs of pink or blue leaves on pink trunks.
    puff = {"type": "minecraft:blob_foliage_placer", "height": 4, "offset": 0, "radius": 3}
    cf("candy_floss_tree_pink", tree(straight(4, 2, 0), puff, two_layers, [PETALS], foliage=blush, trunk=BLUSH_LOG))
    cf("candy_floss_tree_blue", tree(straight(4, 2, 0), puff, two_layers, [PETALS], foliage=sky, trunk=BLUSH_LOG))
    # The heart tree (HeartTreeFeature): a blushwood trunk under a big heart of blush and flowering leaves.
    cf("heart_tree", {"type": f"{NS}:heart_tree", "config": {}})
    for name in ("pearlwood_tree", "pearlwood_tree_short", "bluebell_tree", "fancy_bluebell_tree", "twilight_tree",
                 "twilight_tree_small", "candy_floss_tree_pink", "candy_floss_tree_blue", "heart_tree"):
        checked(name)
    selector("trees_pearlwood_forest", "pearlwood_tree", [("pearlwood_tree_short", 0.3)])
    selector("trees_bluebell_woods", "bluebell_tree", [("fancy_bluebell_tree", 0.25), ("trans_bush", 0.1)])
    selector("trees_twilight_thicket", "twilight_tree", [("trans_bush", 0.15)])
    selector("trees_candy_floss_grove", "candy_floss_tree_pink", [("candy_floss_tree_blue", 0.5)])
    pf("trees_pearlwood_forest", f"{NS}:trees_pearlwood_forest", surface_trees(weighted_count([(7, 9), (9, 1)])))
    pf("trees_bluebell_woods", f"{NS}:trees_bluebell_woods", surface_trees(weighted_count([(6, 9), (8, 1)])))
    pf("trees_twilight_thicket", f"{NS}:trees_twilight_thicket", surface_trees(weighted_count([(12, 9), (14, 1)])))
    pf("trees_candy_floss_grove", f"{NS}:trees_candy_floss_grove", surface_trees(weighted_count([(2, 6), (3, 3), (4, 1)])))

    # ---- pastel lush caves: vanilla's lush cave patches grown in trans moss
    cf("trans_moss_vegetation", {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("minecraft:flowering_azalea"), "weight": 4}, {"data": state("trans_moss_carpet"), "weight": 25},
        {"data": state("trans_short_grass"), "weight": 40}, {"data": state("tall_trans_grass", half="lower"), "weight": 6},
        {"data": state("pride_blossom"), "weight": 4}, {"data": state("sky_bell"), "weight": 3}, {"data": state("pearl_daisy"), "weight": 3},
        {"data": state("heart_bloom"), "weight": 1}]}}})

    def moss_patch(surface, depth, vegetation, chance, radius, edge=0.3):
        return {"type": "minecraft:vegetation_patch", "config": {
            "depth": depth, "extra_bottom_block_chance": 0.0, "extra_edge_column_chance": edge,
            "ground_state": simple(state("trans_moss_block")), "replaceable": f"#{NS}:trans_moss_replaceable",
            "surface": surface, "vegetation_chance": chance, "vegetation_feature": {"feature": vegetation, "placement": []},
            "vertical_range": 5, "xz_radius": radius}}

    cf("trans_moss_patch", moss_patch("floor", 1, f"{NS}:trans_moss_vegetation", 0.8,
                                      {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 4}))
    cf("trans_moss_patch_ceiling", moss_patch("ceiling", {"type": "minecraft:uniform", "max_inclusive": 2, "min_inclusive": 1},
                                              "minecraft:cave_vine_in_moss", 0.08, {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 4}))
    # What bone meal on trans moss does.
    cf("trans_moss_patch_bonemeal", moss_patch("floor", 1, f"{NS}:trans_moss_vegetation", 0.6,
                                               {"type": "minecraft:uniform", "max_inclusive": 2, "min_inclusive": 1}, edge=0.75))

    def cave_scan(count, direction, y_spread):
        return [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "max_inclusive": {"absolute": 256},
                                                              "min_inclusive": {"above_bottom": 0}}},
                {"type": "minecraft:environment_scan", "allowed_search_condition": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"},
                 "direction_of_search": direction, "max_steps": 12, "target_condition": {"type": "minecraft:solid"}},
                {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": y_spread}, {"type": "minecraft:biome"}]

    pf("trans_lush_caves_vegetation", f"{NS}:trans_moss_patch", cave_scan(125, "down", 1))
    cf("trans_glow_lichen", {"type": "minecraft:multiface_growth", "config": {
        "block": "minecraft:glow_lichen", "can_place_on_ceiling": True, "can_place_on_wall": True, "search_range": 20,
        "can_be_placed_on": [f"{NS}:{b}" for b in ("trans_stone", "trans_deepslate", "trans_granite", "trans_diorite", "trans_andesite",
                                                   "trans_cobblestone", "cobbled_trans_deepslate")]}})
    pf("trans_glow_lichen", f"{NS}:trans_glow_lichen", [
        {"type": "minecraft:count", "count": {"type": "minecraft:uniform", "min_inclusive": 104, "max_inclusive": 157}},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 0},
                                                       "max_inclusive": {"absolute": 256}}},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_relative_threshold_filter", "heightmap": "OCEAN_FLOOR_WG", "max_inclusive": -13},
        {"type": "minecraft:biome"}])
    cf("deep_cave_vegetation", {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("trans_short_grass"), "weight": 5}, {"data": state("trans_moss_carpet"), "weight": 3},
        {"data": state("star_bloom"), "weight": 2}, {"data": state("trans_fern"), "weight": 1}]}}})
    cf("deep_cave_moss", moss_patch("floor", 1, f"{NS}:deep_cave_vegetation", 0.55,
                                    {"type": "minecraft:uniform", "max_inclusive": 4, "min_inclusive": 2}))
    cf("deep_cave_ceiling_moss", moss_patch("ceiling", 1, "minecraft:cave_vine_in_moss", 0.12,
                                            {"type": "minecraft:uniform", "max_inclusive": 4, "min_inclusive": 2}))

    def deep_scan(count, direction, y_spread):
        """Like cave_scan, but only in the deepslate levels (below y 0)."""
        scan = cave_scan(count, direction, y_spread)
        scan[2] = {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 8},
                                                                 "max_inclusive": {"absolute": 0}}}
        return scan
    pf("deep_cave_moss", f"{NS}:deep_cave_moss", deep_scan(5, "down", 1))
    pf("deep_cave_ceiling_moss", f"{NS}:deep_cave_ceiling_moss", deep_scan(3, "up", -1))
    pf("trans_lush_caves_ceiling_vegetation", f"{NS}:trans_moss_patch_ceiling", cave_scan(125, "up", -1))

    # ---- flowers and ground cover
    cf("pride_blossom_patch", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("pride_blossom"))}})
    blossoms = [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]
    pf("pride_blossoms", f"{NS}:pride_blossom_patch", [{"type": "minecraft:rarity_filter", "chance": 2}] + blossoms + patch(32))
    pf("pride_blossoms_dense", f"{NS}:pride_blossom_patch", [{"type": "minecraft:count", "count": 3}] + blossoms + patch(48))
    # The realm grows its own flowers; no vanilla ones.
    cf("trans_flowers", {"type": "minecraft:simple_block", "config": {"to_place": {
        "type": "minecraft:noise_provider", "noise": {"amplitudes": [1.0], "firstOctave": 0}, "scale": 0.02, "seed": 7171,
        "states": [state("pride_blossom"), state("trans_tulip"), state("pearl_daisy"), state("sky_bell"), state("trans_rose"),
                   state("heart_bloom"), state("flag_lily"), state("blush_carnation"), state("trans_orchid"), state("forget_me_not"),
                   state("pride_blossom")]}}})
    pf("trans_flowers", f"{NS}:trans_flowers", [{"type": "minecraft:count", "count": 2}] + blossoms + patch(40))
    # Each biome grows its own flowers (one mixed bunch everywhere felt out of place); the Pride Flower Fields still have
    # every kind.
    def flower_mix(name, weights, count=2, size=32):
        cf(name, {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:weighted_state_provider",
                                                                             "entries": [{"data": state(f), "weight": w} for f, w in weights]}}})
        pf(name, f"{NS}:{name}", [{"type": "minecraft:count", "count": count}] + blossoms + patch(size))
    flower_mix("meadow_flowers", [("pride_blossom", 3), ("trans_tulip", 3), ("flag_lily", 2), ("trans_rose", 2)])
    flower_mix("forest_flowers", [("blush_carnation", 3), ("trans_orchid", 2), ("fairy_bell", 2), ("trans_tulip", 1)])
    flower_mix("heartwood_flowers", [("heart_bloom", 4), ("trans_rose", 3), ("blush_carnation", 2)])
    flower_mix("crystal_flowers", [("sky_bell", 2), ("pearl_daisy", 2)], count=1, size=16)
    flower_mix("candy_flowers", [("heart_bloom", 3), ("blush_carnation", 2), ("trans_tulip", 2), ("pride_blossom", 1)])
    cf("lavender_flowers", {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("lavender_puff"), "weight": 3}, {"data": state("trans_orchid"), "weight": 2},
        {"data": state("fairy_bell"), "weight": 2}, {"data": state("pride_blossom"), "weight": 1}]}}})
    pf("lavender_flowers", f"{NS}:lavender_flowers", [{"type": "minecraft:count", "count": 2}] + blossoms + patch(40))
    cf("frost_flowers", {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("flag_lily"), "weight": 2}, {"data": state("pearl_daisy"), "weight": 2},
        {"data": state("pearl_snowdrop"), "weight": 2}, {"data": state("sky_bell"), "weight": 1}]}}})
    pf("frost_flowers", f"{NS}:frost_flowers", [{"type": "minecraft:rarity_filter", "chance": 2}] + blossoms + patch(24))
    # Trans petals carpet meadows lightly and forests thickly (vanilla's wildflowers and cherry petals, re-coloured).
    cf("trans_petals", {"type": "minecraft:simple_block", "config": {"to_place": petals_provider()}})
    petal_spots = lambda below, above: [{"type": "minecraft:noise_threshold_count", "above_noise": above, "below_noise": below,
                                          "noise_level": -0.8}] + blossoms
    pf("trans_petals_meadow", f"{NS}:trans_petals", petal_spots(2, 5) + patch(8))
    pf("trans_petals_forest", f"{NS}:trans_petals", petal_spots(5, 10) + patch(48))
    # Bluebell Woods' carpets of Sky Bells and the Candy Floss Grove's Heart Blooms.
    cf("sky_bell_patch", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("sky_bell"))}})
    pf("sky_bell_carpets", f"{NS}:sky_bell_patch", [{"type": "minecraft:count", "count": 4}] + blossoms + patch(48))
    cf("heart_bloom_patch", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("heart_bloom"))}})
    pf("heart_blooms", f"{NS}:heart_bloom_patch", [{"type": "minecraft:count", "count": 2}] + blossoms + patch(24))
    # Tall Pride Peonies (simple_block places both halves of a double plant).
    cf("pride_peony_patch", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("pride_peony", half="lower"))}})
    pf("pride_peonies", f"{NS}:pride_peony_patch", [{"type": "minecraft:rarity_filter", "chance": 4}] + blossoms + patch(24, xz=5))

    # ---- flower fields, hedges, heart trees and starry flowers
    # Tall flowers of every kind, in noisy drifts.
    cf("tall_trans_flowers", {"type": "minecraft:simple_block", "config": {"to_place": {
        "type": "minecraft:noise_provider", "noise": {"amplitudes": [1.0], "firstOctave": 0}, "scale": 0.03, "seed": 2468,
        "states": [state("sky_delphinium", half="lower"), state("blush_foxglove", half="lower"), state("pearl_lupine", half="lower"),
                   state("pride_peony", half="lower")]}}})
    pf("tall_trans_flowers", f"{NS}:tall_trans_flowers", [{"type": "minecraft:rarity_filter", "chance": 3}] + blossoms + patch(32, xz=6))
    pf("tall_trans_flowers_dense", f"{NS}:tall_trans_flowers", [{"type": "minecraft:count", "count": 2}] + blossoms + patch(48, xz=7))
    # The flower fields' carpet: every small trans flower, each in its own noisy patch.
    cf("field_flowers", {"type": "minecraft:simple_block", "config": {"to_place": {
        "type": "minecraft:noise_provider", "noise": {"amplitudes": [1.0], "firstOctave": 0}, "scale": 0.05, "seed": 1357,
        "states": [state("trans_rose"), state("blush_carnation"), state("forget_me_not"), state("pearl_snowdrop"), state("trans_tulip"),
                   state("heart_bloom"), state("sky_bell"), state("fairy_bell"), state("pearl_daisy"), state("pride_blossom"),
                   state("flag_lily"), state("trans_orchid")]}}})
    pf("field_flowers", f"{NS}:field_flowers", [{"type": "minecraft:count", "count": 6}] + blossoms + patch(64, xz=7))
    # Starblooms glow in the dark; forget-me-nots carpet the bluebell woods.
    cf("star_bloom_patch", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("star_bloom"))}})
    pf("star_blooms", f"{NS}:star_bloom_patch", [{"type": "minecraft:count", "count": 2}] + blossoms + patch(24))
    pf("star_blooms_dense", f"{NS}:star_bloom_patch", [{"type": "minecraft:count", "count": 5}] + blossoms + patch(40))
    cf("forget_me_not_patch", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("forget_me_not"))}})
    pf("forget_me_nots", f"{NS}:forget_me_not_patch", [{"type": "minecraft:count", "count": 2}] + blossoms + patch(32))
    # Hedge bushes: a stub of wood under a round puff of flowering hedge.
    for hedge, log in (("blossom_hedge", "blush_log"), ("bluebell_hedge", "sky_log"), ("pearl_hedge", "pearl_log")):
        cf(f"{hedge}_bush", tree(straight(1, 0, 0), {"type": "minecraft:bush_foliage_placer", "height": 2, "offset": 1, "radius": 2},
                                 {"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "upper_size": 0},
                                 foliage=state(hedge), trunk=state(log, axis="y")))
        checked(f"{hedge}_bush")
    selector("hedge_bushes", "blossom_hedge_bush", [("bluebell_hedge_bush", 0.33), ("pearl_hedge_bush", 0.33)])
    pf("hedge_bushes", f"{NS}:hedge_bushes", surface_trees(weighted_count([(0, 2), (1, 2), (2, 1)])))
    # Heart trees: rare, and only on open ground.
    pf("heart_trees", f"{NS}:heart_tree", [{"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"},
                                            {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
                                            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, SAPLING_CHECK, {"type": "minecraft:biome"}])
    pf("heart_trees_rare", f"{NS}:heart_tree", [{"type": "minecraft:rarity_filter", "chance": 40}, {"type": "minecraft:in_square"},
                                                 {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
                                                 {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, SAPLING_CHECK, {"type": "minecraft:biome"}])

    # ---- trans vegetation: the realm's own grass, ferns, bushes, water plants and corals instead of vanilla's
    def spread(count, xz=7, y=3, extra=None):
        predicate = {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}
        if extra:
            predicate = {"type": "minecraft:all_of", "predicates": [predicate, extra]}
        return [{"type": "minecraft:count", "count": count}, {"type": "minecraft:random_offset",
                "xz_spread": {"type": "minecraft:trapezoid", "max": xz, "min": -xz, "plateau": 0},
                "y_spread": {"type": "minecraft:trapezoid", "max": y, "min": -y, "plateau": 0}},
                {"type": "minecraft:block_predicate_filter", "predicate": predicate}]

    def surface(heightmap="WORLD_SURFACE_WG"):
        return [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": heightmap}, {"type": "minecraft:biome"}]

    def weighted(*pairs):
        return {"type": "minecraft:weighted_state_provider", "entries": [{"data": st, "weight": w} for st, w in pairs]}

    cf("trans_grass", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("trans_short_grass"))}})
    cf("trans_grass_jungle", {"type": "minecraft:simple_block", "config": {"to_place": weighted(
        (state("trans_short_grass"), 3), (state("trans_fern"), 1))}})
    cf("trans_taiga_grass", {"type": "minecraft:simple_block", "config": {"to_place": weighted(
        (state("trans_short_grass"), 1), (state("trans_fern"), 4))}})
    cf("tall_trans_grass", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("tall_trans_grass", half="lower"))}})
    cf("large_trans_fern", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("large_trans_fern", half="lower"))}})
    cf("pastel_bush", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("pastel_bush"))}})
    cf("trans_firefly_bush", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("trans_firefly_bush"))}})
    cf("sugar_grass", {"type": "minecraft:simple_block", "config": {"to_place": weighted(
        (state("short_sugar_grass"), 1), (state("tall_sugar_grass"), 1))}})
    cf("trans_lily_pad", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("trans_lily_pad"))}})
    noisy = lambda above, below: [{"type": "minecraft:noise_threshold_count", "above_noise": above, "below_noise": below, "noise_level": -0.8}]
    pf("trans_grass_meadow", f"{NS}:trans_grass", noisy(10, 5) + surface() + spread(16))
    pf("trans_grass_forest", f"{NS}:trans_grass", [{"type": "minecraft:count", "count": 2}] + surface() + spread(32))
    pf("trans_grass_plain", f"{NS}:trans_grass", noisy(10, 5) + surface() + spread(32))
    pf("trans_grass_normal", f"{NS}:trans_grass", [{"type": "minecraft:count", "count": 5}] + surface() + spread(32))
    pf("trans_grass_jungle", f"{NS}:trans_grass_jungle", [{"type": "minecraft:count", "count": 25}] + surface() + spread(32))
    pf("trans_grass_taiga", f"{NS}:trans_taiga_grass", [{"type": "minecraft:count", "count": 7}] + surface() + spread(32))
    pf("tall_trans_grass", f"{NS}:tall_trans_grass", noisy(7, 0) + [{"type": "minecraft:rarity_filter", "chance": 32}]
       + surface("MOTION_BLOCKING") + spread(96))
    pf("large_trans_ferns", f"{NS}:large_trans_fern", [{"type": "minecraft:rarity_filter", "chance": 5}] + surface("MOTION_BLOCKING")
       + spread(96))
    pf("pastel_bushes", f"{NS}:pastel_bush", [{"type": "minecraft:rarity_filter", "chance": 4}] + surface("MOTION_BLOCKING") + spread(24, xz=5))
    water_nearby = {"type": "minecraft:any_of", "predicates": [
        {"type": "minecraft:matching_fluids", "fluids": ["minecraft:water", "minecraft:flowing_water"], "offset": off}
        for off in ([1, -1, 0], [-1, -1, 0], [0, -1, 1], [0, -1, -1])]}
    pf("trans_firefly_bushes_near_water", f"{NS}:trans_firefly_bush", [{"type": "minecraft:count", "count": 2}]
       + surface("MOTION_BLOCKING_NO_LEAVES") + [{"type": "minecraft:block_predicate_filter", "predicate": {
           "type": "minecraft:all_of", "predicates": [{"type": "minecraft:matching_block_tag", "tag": "minecraft:air"},
                                                      {"type": "minecraft:would_survive", "state": state("trans_firefly_bush")},
                                                      water_nearby]}}] + spread(20, xz=4))
    pf("trans_firefly_bushes_swamp", f"{NS}:trans_firefly_bush", [{"type": "minecraft:rarity_filter", "chance": 8}]
       + surface("MOTION_BLOCKING") + spread(20, xz=4))
    pf("sugar_grass", f"{NS}:sugar_grass", [{"type": "minecraft:rarity_filter", "chance": 3}] + surface("MOTION_BLOCKING") + spread(64))
    pf("trans_lily_pads", f"{NS}:trans_lily_pad", [{"type": "minecraft:count", "count": 4}] + surface() + spread(10))

    # Water plants: placed on the sea floor, only into water. Tall seagrass and kelp are columns of blocks.
    in_water = {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:water"}}
    sea_floor = [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"}]
    cf("trans_seagrass_short", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("trans_seagrass"))}})
    cf("trans_seagrass_tall", {"type": "minecraft:block_column", "config": {
        "allowed_placement": {"type": "minecraft:matching_blocks", "blocks": "minecraft:water"}, "direction": "up", "prioritize_tip": False,
        "layers": [{"height": 1, "provider": simple(state("tall_trans_seagrass", half="lower"))},
                   {"height": 1, "provider": simple(state("tall_trans_seagrass", half="upper"))}]}})
    cf("trans_seagrass", {"type": "minecraft:random_selector", "config": {
        "default": {"feature": f"{NS}:trans_seagrass_short", "placement": []},
        "features": [{"chance": 0.35, "feature": {"feature": f"{NS}:trans_seagrass_tall", "placement": []}}]}})
    cf("trans_kelp", {"type": "minecraft:block_column", "config": {
        "allowed_placement": {"type": "minecraft:matching_blocks", "blocks": "minecraft:water"}, "direction": "up", "prioritize_tip": True,
        "layers": [{"height": {"type": "minecraft:biased_to_bottom", "max_inclusive": 14, "min_inclusive": 2},
                    "provider": simple(state("trans_kelp_plant"))},
                   {"height": 1, "provider": {"type": "minecraft:randomized_int_state_provider", "property": "age",
                                              "source": simple(state("trans_kelp", age=0)),
                                              "values": {"type": "minecraft:uniform", "max_inclusive": 23, "min_inclusive": 20}}}]}})
    for name, count in (("trans_seagrass_warm", 80), ("trans_seagrass_deep", 48), ("trans_seagrass_river", 48), ("trans_seagrass_swamp", 64)):
        pf(name, f"{NS}:trans_seagrass", sea_floor + [{"type": "minecraft:count", "count": count}, in_water, {"type": "minecraft:biome"}])
    for name, ratio in (("trans_kelp_warm", 80), ("trans_kelp_cold", 120)):
        pf(name, f"{NS}:trans_kelp", [{"type": "minecraft:noise_based_count", "noise_factor": 80.0, "noise_to_count_ratio": ratio}]
           + sea_floor + [in_water, {"type": "minecraft:biome"}])
    cf("trans_coral_reef", {"type": f"{NS}:trans_coral_reef", "config": {}})
    pf("trans_coral_reefs", f"{NS}:trans_coral_reef", [{"type": "minecraft:noise_based_count", "noise_factor": 400.0,
                                                        "noise_to_count_ratio": 20}] + sea_floor + [{"type": "minecraft:biome"}])

    # ---- moonlit meadow trees, denser reefs, blooming caverns
    pf("trees_moonlit_meadow", f"{NS}:pearlwood_tree_short", surface_trees(weighted_count([(0, 5), (1, 2), (2, 1)])))
    pf("trans_coral_reefs_rare", f"{NS}:trans_coral_reef", [{"type": "minecraft:rarity_filter", "chance": 6}] + sea_floor
       + [{"type": "minecraft:biome"}])
    pf("trans_coral_reefs_dense", f"{NS}:trans_coral_reef", [{"type": "minecraft:noise_based_count", "noise_factor": 400.0,
                                                              "noise_to_count_ratio": 30}] + sea_floor + [{"type": "minecraft:biome"}])
    # Blooming Caverns: floors of trans moss thick with flowers (starblooms glow in the dark), ceilings of flowering
    # blush leaves hung with trans lanterns.
    cf("blooming_cave_flowers", {"type": "minecraft:simple_block", "config": {"to_place": {"type": "minecraft:weighted_state_provider", "entries": [
        {"data": state("star_bloom"), "weight": 6}, {"data": state("trans_short_grass"), "weight": 14}, {"data": state("trans_moss_carpet"), "weight": 8},
        {"data": state("fairy_bell"), "weight": 4}, {"data": state("forget_me_not"), "weight": 4}, {"data": state("trans_rose"), "weight": 3},
        {"data": state("blush_carnation"), "weight": 3}, {"data": state("pearl_snowdrop"), "weight": 3}, {"data": state("pride_blossom"), "weight": 3},
        {"data": state("sky_delphinium", half="lower"), "weight": 2}, {"data": state("blush_foxglove", half="lower"), "weight": 2},
        {"data": state("pastel_bush"), "weight": 2}]}}})
    cf("blooming_cave_floor", moss_patch("floor", 1, f"{NS}:blooming_cave_flowers", 0.85,
                                         {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 4}))
    cf("blooming_lantern", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("trans_lantern", hanging=True, waterlogged=False))}})
    cf("blooming_cave_ceiling", {"type": "minecraft:vegetation_patch", "config": {
        "depth": {"type": "minecraft:uniform", "max_inclusive": 2, "min_inclusive": 1}, "extra_bottom_block_chance": 0.0,
        "extra_edge_column_chance": 0.3,
        "ground_state": {"type": "minecraft:weighted_state_provider", "entries": [
            {"data": state("flowering_blush_leaves", distance=7, persistent=True, waterlogged=False), "weight": 3},
            {"data": state("blush_leaves", distance=7, persistent=True, waterlogged=False), "weight": 2},
            {"data": state("blossom_hedge"), "weight": 1}]},
        "replaceable": f"#{NS}:trans_moss_replaceable", "surface": "ceiling", "vegetation_chance": 0.04,
        "vegetation_feature": {"feature": f"{NS}:blooming_lantern", "placement": []}, "vertical_range": 5,
        "xz_radius": {"type": "minecraft:uniform", "max_inclusive": 6, "min_inclusive": 3}}})
    pf("blooming_cave_floor", f"{NS}:blooming_cave_floor", cave_scan(110, "down", 1))
    pf("blooming_cave_ceiling", f"{NS}:blooming_cave_ceiling", cave_scan(70, "up", -1))

    # ---- crystals
    cf("trans_crystal_spike", {"type": "minecraft:spike", "config": {
        "can_place_on": {"type": "minecraft:matching_blocks", "blocks": [f"{NS}:trans_grass_block", f"{NS}:trans_stone", f"{NS}:trans_sand", f"{NS}:trans_dirt", "minecraft:snow_block"]},
        "can_replace": {"type": "minecraft:matching_block_tag", "tag": "minecraft:ice_spike_replaceable"},
        "state": state("pastel_prism")}})
    pf("trans_crystal_spikes", f"{NS}:trans_crystal_spike", [{"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
                                                             {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}])
    pf("trans_crystal_spikes_rare", f"{NS}:trans_crystal_spike", [{"type": "minecraft:rarity_filter", "chance": 6}, {"type": "minecraft:in_square"},
                                                                  {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}])
    cf("frosted_ice_spike", {"type": "minecraft:spike", "config": {
        "can_place_on": {"type": "minecraft:matching_blocks", "blocks": ["minecraft:snow_block", f"{NS}:trans_grass_block"]},
        "can_replace": {"type": "minecraft:matching_block_tag", "tag": "minecraft:ice_spike_replaceable"},
        "state": state("minecraft:packed_ice")}})
    pf("frosted_ice_spikes", f"{NS}:frosted_ice_spike", [{"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"},
                                                         {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}])
    cf("trans_crystal_cluster", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("trans_crystal_cluster", facing="up", waterlogged=False))}})
    pf("trans_crystal_clusters_surface", f"{NS}:trans_crystal_cluster", [{"type": "minecraft:count", "count": 2}] + blossoms + patch(12, xz=4, y=1))
    pf("trans_crystal_clusters_cave_floor", f"{NS}:trans_crystal_cluster", [
        {"type": "minecraft:count", "count": 45}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "max_inclusive": {"absolute": 60}, "min_inclusive": {"above_bottom": 4}}},
        {"type": "minecraft:environment_scan", "allowed_search_condition": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"},
         "direction_of_search": "down", "max_steps": 12, "target_condition": {"type": "minecraft:solid"}},
        {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": 1}, {"type": "minecraft:biome"}])
    cf("trans_crystal_cluster_hanging", {"type": "minecraft:simple_block", "config": {"to_place": simple(state("trans_crystal_cluster", facing="down", waterlogged=False))}})
    pf("trans_crystal_clusters_cave_ceiling", f"{NS}:trans_crystal_cluster_hanging", [
        {"type": "minecraft:count", "count": 30}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "max_inclusive": {"absolute": 60}, "min_inclusive": {"above_bottom": 4}}},
        {"type": "minecraft:environment_scan", "allowed_search_condition": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"},
         "direction_of_search": "up", "max_steps": 12, "target_condition": {"type": "minecraft:solid"}},
        {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -1}, {"type": "minecraft:biome"}])
    cf("trans_crystal_geode", {"type": "minecraft:geode", "config": {
        "blocks": {
            "alternate_inner_layer_provider": simple(state("pastel_prism")),
            "cannot_replace": "#minecraft:features_cannot_replace",
            "filling_provider": simple(state("minecraft:air")),
            "inner_layer_provider": simple(state("pastel_prism")),
            "inner_placements": [state("trans_crystal_cluster", facing="up", waterlogged=False)],
            "invalid_blocks": "#minecraft:geode_invalid_blocks",
            "middle_layer_provider": simple(state("trans_stone_bricks")),
            "outer_layer_provider": simple(state("trans_cobblestone"))},
        "crack": {"generate_crack_chance": 0.95},
        "invalid_blocks_threshold": 1, "layers": {},
        "outer_wall_distance": {"type": "minecraft:uniform", "max_inclusive": 6, "min_inclusive": 4},
        "use_alternate_layer0_chance": 0.15}})
    geode = lambda chance, top: [{"type": "minecraft:rarity_filter", "chance": chance}, {"type": "minecraft:in_square"},
                                 {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "max_inclusive": {"absolute": top},
                                                                               "min_inclusive": {"above_bottom": 6}}}, {"type": "minecraft:biome"}]
    pf("trans_crystal_geode", f"{NS}:trans_crystal_geode", geode(28, 30))
    pf("trans_crystal_geode_common", f"{NS}:trans_crystal_geode", geode(10, 50))

    # ---- ores: trans crystals are rare, deep and mostly small, rarer than diamonds (vanilla's diamond placements,
    # scaled down). Crystal Groves and Pastel Peaks get a few small veins higher up. Geodes and spikes are pastel prism.
    def crystal_ore(name, size, discard):
        cf(name, {"type": "minecraft:ore", "config": {"discard_chance_on_air_exposure": discard, "size": size, "targets": [
            {"state": state("trans_crystal_ore"), "target": {"predicate_type": "minecraft:tag_match", "tag": f"{NS}:trans_stone_ore_replaceables"}},
            {"state": state("trans_deepslate_crystal_ore"),
             "target": {"predicate_type": "minecraft:tag_match", "tag": f"{NS}:trans_deepslate_ore_replaceables"}}]}})
    crystal_ore("ore_trans_crystal_small", 3, 0.5)
    crystal_ore("ore_trans_crystal_buried", 6, 1.0)
    crystal_ore("ore_trans_crystal_large", 9, 0.7)
    generate_vanilla_ores()
    deep = {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "max_inclusive": {"above_bottom": 80},
                                                         "min_inclusive": {"above_bottom": -80}}}
    pf("ore_trans_crystal", f"{NS}:ore_trans_crystal_small", [{"type": "minecraft:count", "count": 4}, {"type": "minecraft:in_square"}, deep, {"type": "minecraft:biome"}])
    pf("ore_trans_crystal_deep", f"{NS}:ore_trans_crystal_buried", [{"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"}, deep, {"type": "minecraft:biome"}])
    pf("ore_trans_crystal_large", f"{NS}:ore_trans_crystal_large", [{"type": "minecraft:rarity_filter", "chance": 14}, {"type": "minecraft:in_square"}, deep, {"type": "minecraft:biome"}])
    pf("ore_trans_crystal_extra", f"{NS}:ore_trans_crystal_small", [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "max_inclusive": {"absolute": 48}, "min_inclusive": {"absolute": -32}}},
        {"type": "minecraft:biome"}])

    # ---- rocks
    cf("trans_boulder", {"type": "minecraft:block_blob", "config": {
        "can_place_on": {"type": "minecraft:matching_block_tag", "tag": "minecraft:forest_rock_can_place_on"},
        "state": state("trans_cobblestone")}})
    pf("trans_boulders", f"{NS}:trans_boulder", [{"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"},
                                                 {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}])
    # Springs of water and pink lava in the realm's rock (vanilla's placements).
    spring_rock = [f"{NS}:{b}" for b in ("trans_stone", "trans_deepslate", "trans_granite", "trans_diorite", "trans_andesite", "trans_dirt")]
    cf("spring_trans_water", {"type": "minecraft:spring_feature", "config": {
        "state": {"Name": "minecraft:water", "Properties": {"falling": "true"}}, "valid_blocks": spring_rock}})
    pf("spring_trans_water", f"{NS}:spring_trans_water", [
        {"type": "minecraft:count", "count": 25}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 0},
                                                       "max_inclusive": {"absolute": 192}}},
        {"type": "minecraft:biome"}])
    cf("spring_pink_lava", {"type": "minecraft:spring_feature", "config": {
        "state": {"Name": f"{NS}:pink_lava", "Properties": {"falling": "true"}}, "valid_blocks": spring_rock}})
    pf("spring_pink_lava", f"{NS}:spring_pink_lava", [
        {"type": "minecraft:count", "count": 20}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:very_biased_to_bottom", "inner": 8,
                                                       "min_inclusive": {"above_bottom": 0}, "max_inclusive": {"below_top": 8}}},
        {"type": "minecraft:biome"}])
    # Trans dungeons (TransDungeonFeature): vanilla's monster room placements, made of trans stone.
    cf("trans_dungeon", {"type": f"{NS}:trans_dungeon", "config": {}})
    pf("trans_dungeon", f"{NS}:trans_dungeon", [
        {"type": "minecraft:count", "count": 10}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 0},
                                                       "max_inclusive": {"below_top": 0}}},
        {"type": "minecraft:biome"}])
    pf("trans_dungeon_deep", f"{NS}:trans_dungeon", [
        {"type": "minecraft:count", "count": 4}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 6},
                                                       "max_inclusive": {"absolute": -1}}},
        {"type": "minecraft:biome"}])
    # The Fairy Realm's floating islands (FairyIslandFeature keeps them away from the arena in the middle).
    cf("fairy_island", {"type": f"{NS}:fairy_island", "config": {}})
    pf("fairy_islands", f"{NS}:fairy_island", [
        {"type": "minecraft:rarity_filter", "chance": 4}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 70},
                                                       "max_inclusive": {"absolute": 170}}},
        {"type": "minecraft:biome"}])

    # Wobbly mounds of pink and blue gel in the Gumdrop Glade, where the pastel slimes live.
    for colour in ("pink", "blue"):
        cf(f"{colour}_gel_mound", {"type": "minecraft:block_blob", "config": {
            "can_place_on": {"type": "minecraft:matching_block_tag", "tag": "minecraft:forest_rock_can_place_on"},
            "state": state(f"{colour}_gel_block")}})
        pf(f"{colour}_gel_mounds", f"{NS}:{colour}_gel_mound", [
            {"type": "minecraft:rarity_filter", "chance": 4}, {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}])


# ============================================================================================ vanilla ores
VANILLA_ORES = os.path.join(HERE, "vanilla_extra", "templates", "worldgen", "ores")
ORE_NAMES = ("coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald")
ORE_SWAPS = {
    **{f"minecraft:{o}_ore": f"{NS}:trans_{o}_ore" for o in ORE_NAMES},
    **{f"minecraft:deepslate_{o}_ore": f"{NS}:trans_deepslate_{o}_ore" for o in ORE_NAMES},
    "minecraft:dirt": f"{NS}:trans_dirt", "minecraft:gravel": f"{NS}:trans_gravel", "minecraft:granite": f"{NS}:trans_granite",
    "minecraft:diorite": f"{NS}:trans_diorite", "minecraft:andesite": f"{NS}:trans_andesite",
    "minecraft:stone_ore_replaceables": f"{NS}:trans_stone_ore_replaceables",
    "minecraft:deepslate_ore_replaceables": f"{NS}:trans_deepslate_ore_replaceables",
    "minecraft:base_stone_overworld": f"{NS}:trans_base_stone",
}


def swap_ids(obj):
    if isinstance(obj, dict):
        return {k: swap_ids(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [swap_ids(v) for v in obj]
    if isinstance(obj, str):
        return ORE_SWAPS.get(obj, obj)
    return obj


def generate_vanilla_ores():
    """Vanilla 26.2's overworld ores and rock blobs (same sizes, counts and heights), placing trans ores in trans rock.
    Each vanilla feature X becomes transdimension:trans_X."""
    for kind in ("configured", "placed"):
        folder = os.path.join(VANILLA_ORES, kind)
        for f in sorted(os.listdir(folder)):
            with open(os.path.join(folder, f), encoding="utf-8") as fh:
                d = swap_ids(json.load(fh))
            name = "trans_" + f[:-5]
            if kind == "configured":
                cf(name, d)
            else:
                pf(name, f"{NS}:trans_" + d["feature"].split(":", 1)[1], d["placement"])


# Rock blobs first, then ores, in vanilla's order.
TRANS_ORES = [f"{NS}:trans_{n}" for n in (
    "ore_dirt", "ore_gravel", "ore_granite_upper", "ore_granite_lower", "ore_diorite_upper", "ore_diorite_lower",
    "ore_andesite_upper", "ore_andesite_lower", "ore_coal_upper", "ore_coal_lower", "ore_iron_upper", "ore_iron_middle",
    "ore_iron_small", "ore_gold", "ore_gold_lower", "ore_redstone", "ore_redstone_lower", "ore_diamond", "ore_diamond_medium",
    "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried", "ore_copper")]


# ============================================================================================ biomes
def music(sound, underwater=False):
    m = {"default": {"max_delay": 24000, "min_delay": 12000, "sound": sound}}
    if underwater:
        m["underwater"] = {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.under_water"}
    return m


def spawn(entity, weight, lo, hi):
    return {"type": entity if ":" in entity else f"{NS}:{entity}", "maxCount": hi, "minCount": lo, "weight": weight}


MONSTERS = [spawn("minecraft:spider", 100, 4, 4), spawn("minecraft:zombie", 95, 4, 4), spawn("minecraft:zombie_villager", 5, 1, 1),
            spawn("minecraft:skeleton", 100, 4, 4), spawn("minecraft:creeper", 100, 4, 4), spawn("minecraft:slime", 100, 4, 4),
            spawn("trans_enderman", 10, 1, 4), spawn("minecraft:witch", 5, 1, 1)]
OCEAN_MONSTERS = [spawn("minecraft:drowned", 100, 1, 1)] + [m for m in MONSTERS if m["type"] != "minecraft:slime"]
BATS = [spawn("minecraft:bat", 10, 8, 8)]

# Trans dungeons (TransDungeonFeature) instead of vanilla's cobblestone monster rooms, placed the same way.
UNDERGROUND = [f"{NS}:trans_dungeon", f"{NS}:trans_dungeon_deep"]
ORES = TRANS_ORES + [f"{NS}:ore_trans_crystal", f"{NS}:ore_trans_crystal_deep", f"{NS}:ore_trans_crystal_large", "minecraft:disk_clay"]
# The realm's own springs: vanilla's only break out of vanilla stone, and the realm's lava is pink.
SPRINGS = [f"{NS}:spring_trans_water", f"{NS}:spring_pink_lava"]
# Every cave: glow lichen on trans rock (vanilla's only grows on vanilla stone), and deep down, now and then, a patch of
# moss with glowing Starblooms on the floor or glow berry vines hanging from the ceiling.
CAVE_DECOR = [f"{NS}:trans_glow_lichen", f"{NS}:deep_cave_moss", f"{NS}:deep_cave_ceiling_moss"]


def biome(name, *, temperature, downfall, grass, foliage, water, water_fog, sky, fog, music_sound,
          features, creatures=(), monsters=MONSTERS, water_creatures=(), water_ambient=(), underground_water=(),
          particles=None, precipitation=True, frozen=False, extra_attributes=None, underwater_music=False, axolotls=(), fairies=4):
    attributes = {
        "minecraft:audio/background_music": music(music_sound, underwater_music),
        "minecraft:visual/fog_color": fog,
        "minecraft:visual/sky_color": sky,
        "minecraft:visual/water_fog_color": water_fog,
    }
    if particles:
        attributes["minecraft:visual/ambient_particles"] = particles
    if extra_attributes:
        attributes.update(extra_attributes)
    steps = [[] for _ in range(11)]
    for step, items in features.items():
        steps[step] = list(items)
    b = {
        "attributes": attributes,
        "carvers": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"],
        "downfall": downfall,
        "effects": {"foliage_color": foliage, "grass_color": grass, "water_color": water},
        "features": steps,
        "has_precipitation": precipitation,
        "spawn_costs": {},
        "spawners": {
            # Wild fairies are rare however heavy their weight: one in fifteen spawn tries works, and never near another.
            "ambient": BATS + ([spawn("fairy", fairies, 1, 1)] if fairies else []), "axolotls": list(axolotls), "creature": list(creatures), "misc": [], "monster": list(monsters),
            "underground_water_creature": list(underground_water), "water_ambient": list(water_ambient),
            "water_creature": list(water_creatures)},
        "temperature": temperature,
    }
    if frozen:
        b["temperature_modifier"] = "frozen"
    write(os.path.join(WG, "biome", f"{name}.json"), b)


def particles(kind, probability):
    return [{"particle": {"type": kind}, "probability": probability}]


def land(*vegetation, top=(), ores=ORES, lakes=(), local=(), underground_decor=()):
    """Feature steps for a land biome: 1 lakes, 2 local modifications, 3 underground structures,
    6 ores, 7 underground decoration, 8 springs, 9 vegetation, 10 top layer."""
    return {1: lakes, 2: local, 3: UNDERGROUND, 6: ores, 7: list(underground_decor) + CAVE_DECOR, 8: SPRINGS, 9: vegetation, 10: top}


def generate_biomes():
    common_creatures = [spawn("minecraft:sheep", 12, 4, 4), spawn("minecraft:pig", 10, 4, 4), spawn("minecraft:chicken", 10, 4, 4),
                        spawn("minecraft:cow", 8, 4, 4)]

    biome("trans_meadow", temperature=0.7, downfall=0.6, grass="#f5a9b8", foliage="#f5a9b8", water="#5bcefa", water_fog="#1f6fa8",
          sky="#8ed8fa", fog="#f7c4cf", music_sound="minecraft:music.overworld.meadow",
          particles=particles("minecraft:cherry_leaves", 0.0008),
          features=land(f"{NS}:heart_trees_rare", f"{NS}:trees_trans_meadow", f"{NS}:pride_blossoms", f"{NS}:meadow_flowers",
                        f"{NS}:pride_peonies", f"{NS}:tall_trans_flowers",
                        f"{NS}:trans_petals_meadow", f"{NS}:pastel_bushes", f"{NS}:trans_grass_meadow", f"{NS}:tall_trans_grass", f"{NS}:trans_boulders"),
          creatures=common_creatures + [spawn("minecraft:rabbit", 6, 2, 3), spawn("minecraft:horse", 4, 2, 4), spawn("silly_cat", 8, 1, 2)])

    biome("trans_forest", temperature=0.6, downfall=0.8, grass="#f0a3c0", foliage="#f5a9b8", water="#6fcbf5", water_fog="#2a74a8",
          sky="#9cd3fa", fog="#f5b8d0", music_sound="minecraft:music.overworld.cherry_grove",
          particles=particles("minecraft:cherry_leaves", 0.004),
          features=land(f"{NS}:trees_blossom_forest", f"{NS}:pride_blossoms_dense", f"{NS}:forest_flowers", f"{NS}:pride_peonies",
                        
                        f"{NS}:trans_petals_forest", f"{NS}:pastel_bushes", f"{NS}:trans_grass_forest", "minecraft:brown_mushroom_normal"),
          creatures=[spawn("minecraft:sheep", 10, 4, 4), spawn("minecraft:pig", 8, 4, 4), spawn("minecraft:chicken", 8, 4, 4),
                     spawn("minecraft:wolf", 5, 2, 4), spawn("minecraft:fox", 4, 2, 4), spawn("minecraft:rabbit", 4, 2, 3),
                     spawn("silly_cat", 10, 1, 3)])

    biome("heartwood_grove", temperature=0.9, downfall=0.9, grass="#e890a8", foliage="#f08caa", water="#8aa8f5", water_fog="#36468f",
          sky="#b3c4fa", fog="#e6b0d8", music_sound="minecraft:music.overworld.jungle",
          particles=particles("minecraft:firefly", 0.002),
          features=land(f"{NS}:trees_heartwood_grove", f"{NS}:trans_bushes", f"{NS}:heartwood_flowers",
                        f"{NS}:large_trans_ferns",
                        f"{NS}:trans_grass_jungle", f"{NS}:trans_firefly_bushes_near_water", "minecraft:vines"),
          creatures=[spawn("minecraft:parrot", 8, 1, 2), spawn("minecraft:chicken", 6, 4, 4), spawn("minecraft:rabbit", 4, 2, 3),
                     spawn("minecraft:ocelot", 2, 1, 1), spawn("silly_cat", 12, 1, 3)])

    biome("sugar_dunes", fairies=0, temperature=2.0, downfall=0.0, precipitation=False, grass="#ffd6de", foliage="#ffd6de", water="#7fdbfa",
          water_fog="#2a8ec0", sky="#ffc7d6", fog="#ffe3ea", music_sound="minecraft:music.overworld.desert",
          particles=particles("minecraft:end_rod", 0.0006),
          extra_attributes={"minecraft:gameplay/snow_golem_melts": True},
          features=land("minecraft:patch_cactus_desert", "minecraft:patch_dead_bush_2", f"{NS}:sugar_grass",
                        f"{NS}:trans_crystal_spikes_rare", local=["minecraft:fossil_upper"]),
          creatures=[spawn("minecraft:rabbit", 4, 2, 3), spawn("minecraft:camel", 1, 1, 1)],
          monsters=[spawn("minecraft:spider", 100, 4, 4), spawn("minecraft:husk", 95, 4, 4), spawn("minecraft:skeleton", 100, 4, 4),
                    spawn("minecraft:creeper", 100, 4, 4), spawn("trans_enderman", 10, 1, 4), spawn("minecraft:witch", 5, 1, 1)])

    biome("lavender_marsh", temperature=0.8, downfall=0.9, grass="#b9a8e0", foliage="#a898d8", water="#7aa8e8", water_fog="#3a4a80",
          sky="#afa6f0", fog="#c8b8f0", music_sound="minecraft:music.overworld.swamp",
          particles=particles("minecraft:spore_blossom_air", 0.002),
          extra_attributes={"minecraft:visual/water_fog_end_distance": {"argument": 0.85, "modifier": "multiply"}},
          features=land(f"{NS}:trees_lavender_marsh", f"{NS}:lavender_flowers", f"{NS}:trans_lily_pads", f"{NS}:trans_seagrass_swamp",
                        "minecraft:patch_sugar_cane_swamp", "minecraft:brown_mushroom_swamp", "minecraft:red_mushroom_swamp",
                        f"{NS}:trans_firefly_bushes_swamp", f"{NS}:trans_grass_normal"),
          creatures=[spawn("minecraft:frog", 10, 2, 5), spawn("silly_cat", 4, 1, 2)],
          water_ambient=[spawn("trans_fish", 8, 2, 4), spawn("minecraft:tropical_fish", 4, 1, 3)])

    biome("frosted_fields", temperature=-0.3, downfall=0.5, grass="#d6eeff", foliage="#cfe8ff", water="#9be3fc", water_fog="#3d8fc0",
          sky="#cde8ff", fog="#eaf4ff", music_sound="minecraft:music.overworld.grove",
          particles=particles("minecraft:snowflake", 0.002),
          features=land(f"{NS}:trees_frosted_fields", f"{NS}:frost_flowers", f"{NS}:frosted_ice_spikes", f"{NS}:trans_grass_taiga",
                        top=["minecraft:freeze_top_layer"]),
          creatures=[spawn("minecraft:rabbit", 8, 2, 3), spawn("minecraft:fox", 6, 2, 4), spawn("minecraft:polar_bear", 1, 1, 2),
                     spawn("minecraft:wolf", 2, 2, 4), spawn("silly_cat", 3, 1, 1)],
          monsters=[m if m["type"] != "minecraft:skeleton" else spawn("minecraft:stray", 80, 4, 4) for m in MONSTERS]
          + [spawn("minecraft:skeleton", 20, 4, 4)])

    biome("crystal_grove", temperature=0.5, downfall=0.4, grass="#8ed8f8", foliage="#f5a9b8", water="#a88cf5", water_fog="#4a3a8f",
          sky="#9fb8ff", fog="#d9c8ff", music_sound="minecraft:music.overworld.flower_forest",
          particles=particles("minecraft:end_rod", 0.0025),
          features=land(f"{NS}:trees_crystal_grove", f"{NS}:trans_crystal_spikes", f"{NS}:trans_crystal_clusters_surface",
                        f"{NS}:crystal_flowers", f"{NS}:trans_grass_plain",
                        ores=ORES + [f"{NS}:ore_trans_crystal_extra"], local=[f"{NS}:trans_crystal_geode_common"]),
          creatures=[spawn("minecraft:rabbit", 6, 2, 3), spawn("minecraft:sheep", 6, 2, 4), spawn("silly_cat", 8, 1, 2)])

    biome("pastel_peaks", temperature=-0.5, downfall=0.7, grass="#e9ddf7", foliage="#e0d0f5", water="#9bc8fc", water_fog="#3d6fc0",
          sky="#a9c4ff", fog="#e8e0ff", music_sound="minecraft:music.overworld.jagged_peaks",
          particles=particles("minecraft:snowflake", 0.001),
          features=land(f"{NS}:trans_boulders", f"{NS}:trans_crystal_spikes_rare", f"{NS}:trans_grass_taiga",
                        ores=ORES + [f"{NS}:ore_trans_crystal_extra", f"{NS}:trans_ore_emerald"], top=["minecraft:freeze_top_layer"]),
          creatures=[spawn("minecraft:goat", 8, 1, 3), spawn("minecraft:rabbit", 3, 2, 3), spawn("silly_cat", 1, 1, 1)])

    biome("trans_beach", fairies=0, temperature=0.8, downfall=0.4, grass="#f5a9b8", foliage="#f5a9b8", water="#5bcefa", water_fog="#1f6fa8",
          sky="#7fd4fa", fog="#f7d0d8", music_sound="minecraft:music.overworld.meadow",
          features=land("minecraft:patch_sugar_cane"),
          creatures=[spawn("minecraft:turtle", 5, 2, 5), spawn("silly_cat", 2, 1, 1)])

    ocean_floor = {1: [], 2: [], 3: UNDERGROUND, 6: ORES + ["minecraft:disk_sand"], 7: CAVE_DECOR, 8: SPRINGS}
    biome("trans_ocean", fairies=0, temperature=0.5, downfall=0.5, grass="#f5a9b8", foliage="#f5a9b8", water="#5bcefa", water_fog="#1e7fb8",
          sky="#7fd4fa", fog="#f7d0d8", music_sound="minecraft:music.game", underwater_music=True,
          features={**ocean_floor, 9: [f"{NS}:trans_coral_reefs_rare", f"{NS}:trans_seagrass_warm", "minecraft:sea_pickle", f"{NS}:trans_kelp_warm"]},
          creatures=[], monsters=OCEAN_MONSTERS,
          water_creatures=[spawn("minecraft:squid", 4, 1, 4), spawn("minecraft:dolphin", 2, 1, 2)],
          water_ambient=[spawn("trans_fish", 20, 3, 6), spawn("minecraft:tropical_fish", 25, 8, 8), spawn("minecraft:cod", 6, 3, 6),
                         spawn("minecraft:pufferfish", 5, 1, 3)])
    biome("deep_trans_ocean", fairies=0, temperature=0.5, downfall=0.5, grass="#f5a9b8", foliage="#f5a9b8", water="#3e9fd8", water_fog="#0f3f78",
          sky="#7fd4fa", fog="#f7d0d8", music_sound="minecraft:music.game", underwater_music=True,
          features={**ocean_floor, 9: [f"{NS}:trans_seagrass_deep", f"{NS}:trans_kelp_cold"]},
          creatures=[], monsters=OCEAN_MONSTERS,
          water_creatures=[spawn("minecraft:squid", 6, 1, 4), spawn("minecraft:dolphin", 1, 1, 2)],
          water_ambient=[spawn("trans_fish", 12, 3, 6), spawn("minecraft:cod", 8, 3, 6), spawn("minecraft:salmon", 5, 1, 5)],
          underground_water=[spawn("minecraft:glow_squid", 10, 4, 6)])
    biome("trans_river", fairies=0, temperature=0.6, downfall=0.6, grass="#f5a9b8", foliage="#f5a9b8", water="#f5a9b8", water_fog="#c86a88",
          sky="#8ed8fa", fog="#f7c4cf", music_sound="minecraft:music.overworld.meadow",
          features={**ocean_floor, 9: [f"{NS}:trans_seagrass_river", "minecraft:patch_sugar_cane", f"{NS}:trans_lily_pads"]},
          creatures=[], water_creatures=[spawn("minecraft:squid", 2, 1, 4)],
          water_ambient=[spawn("trans_fish", 10, 2, 5), spawn("minecraft:salmon", 5, 1, 5)])

    # ---- themed forests
    biome("pearlwood_forest", temperature=0.3, downfall=0.6, grass="#eef0ff", foliage="#ffffff", water="#a6e1fa", water_fog="#4a8fc0",
          sky="#d8ecff", fog="#f4f2ff", music_sound="minecraft:music.overworld.forest",
          particles=particles("minecraft:white_ash", 0.004),
          features=land(f"{NS}:trees_pearlwood_forest", f"{NS}:frost_flowers", f"{NS}:trans_grass_forest"),
          creatures=[spawn("minecraft:rabbit", 6, 2, 3), spawn("minecraft:fox", 4, 2, 4), spawn("minecraft:wolf", 3, 2, 4),
                     spawn("minecraft:chicken", 6, 4, 4), spawn("silly_cat", 8, 1, 2)])
    biome("bluebell_woods", temperature=0.6, downfall=0.7, grass="#bfe3ff", foliage="#8fd0ff", water="#5bcefa", water_fog="#1f6fa8",
          sky="#9ad6ff", fog="#d6ecff", music_sound="minecraft:music.overworld.flower_forest",
          features=land(f"{NS}:trees_bluebell_woods", f"{NS}:sky_bell_carpets", f"{NS}:forget_me_nots",
                        f"{NS}:trans_grass_forest"),
          creatures=[spawn("minecraft:pig", 8, 4, 4), spawn("minecraft:rabbit", 6, 2, 3), spawn("minecraft:wolf", 4, 2, 4),
                     spawn("minecraft:sheep", 6, 4, 4), spawn("silly_cat", 8, 1, 2)])
    biome("twilight_thicket", temperature=0.7, downfall=0.9, grass="#8e7ab8", foliage="#7d6aa8", water="#6a7fd8", water_fog="#2a2f6e",
          sky="#6c6aa8", fog="#8b7cb8", music_sound="minecraft:music.overworld.old_growth_taiga",
          particles=particles("minecraft:firefly", 0.006),
          features=land(f"{NS}:trees_twilight_thicket", f"{NS}:trans_crystal_clusters_surface", f"{NS}:lavender_flowers",
                        f"{NS}:star_blooms",
                        f"{NS}:trans_grass_forest", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_swamp"),
          creatures=[spawn("minecraft:rabbit", 4, 2, 3), spawn("minecraft:fox", 3, 2, 3), spawn("silly_cat", 6, 1, 2)])
    biome("candy_floss_grove", temperature=0.8, downfall=0.6, grass="#ffc8dc", foliage="#ffc0d8", water="#9fe3ff", water_fog="#3a8fc8",
          sky="#bfe8ff", fog="#ffe0ee", music_sound="minecraft:music.overworld.cherry_grove",
          particles=particles("minecraft:cherry_leaves", 0.002),
          features=land(f"{NS}:heart_trees_rare", f"{NS}:trees_candy_floss_grove", f"{NS}:hedge_bushes", f"{NS}:heart_blooms",
                        f"{NS}:candy_flowers", f"{NS}:trans_petals_forest", f"{NS}:pastel_bushes", f"{NS}:trans_grass_plain"),
          creatures=common_creatures + [spawn("minecraft:rabbit", 6, 2, 3), spawn("silly_cat", 14, 1, 3)])

    # ---- round 4: flower fields, moonlit meadows, the slimes' glade, the reef and the blooming caverns
    biome("pride_flower_fields", fairies=10, temperature=0.7, downfall=0.7, grass="#f7b5cc", foliage="#f5a9b8", water="#7fd6fa", water_fog="#2a7fb5",
          sky="#9fd8ff", fog="#fbd0dc", music_sound="minecraft:music.overworld.flower_forest",
          particles=particles("minecraft:cherry_leaves", 0.003),
          features=land(f"{NS}:heart_trees", f"{NS}:hedge_bushes", f"{NS}:tall_trans_flowers_dense", f"{NS}:field_flowers",
                        f"{NS}:pride_blossoms_dense", f"{NS}:trans_petals_meadow", f"{NS}:pastel_bushes", f"{NS}:trans_grass_meadow"),
          creatures=common_creatures + [spawn("minecraft:rabbit", 6, 2, 3), spawn("minecraft:horse", 3, 2, 4), spawn("silly_cat", 10, 1, 3)])
    biome("moonlit_meadow", fairies=10, temperature=0.4, downfall=0.6, grass="#dfe6ff", foliage="#eef0ff", water="#8fc8f5", water_fog="#2a4f8f",
          sky="#7c90d8", fog="#c8d2f5", music_sound="minecraft:music.overworld.grove",
          particles=particles("minecraft:firefly", 0.004),
          features=land(f"{NS}:trees_moonlit_meadow", f"{NS}:star_blooms_dense", f"{NS}:forget_me_nots", f"{NS}:frost_flowers",
                        f"{NS}:trans_firefly_bushes_swamp", f"{NS}:trans_grass_plain"),
          creatures=[spawn("minecraft:rabbit", 8, 2, 3), spawn("minecraft:fox", 4, 2, 3), spawn("minecraft:sheep", 6, 2, 4),
                     spawn("silly_cat", 6, 1, 2)])
    biome("gumdrop_glade", temperature=0.8, downfall=0.5, grass="#ffc6e2", foliage="#ffb3d6", water="#a8e6ff", water_fog="#3a9fd0",
          sky="#bfe6ff", fog="#ffe0f0", music_sound="minecraft:music.overworld.cherry_grove",
          particles=particles("minecraft:cherry_leaves", 0.001),
          features=land(f"{NS}:trees_candy_floss_grove", f"{NS}:pastel_bushes", f"{NS}:heart_blooms", f"{NS}:trans_petals_meadow",
                        f"{NS}:trans_grass_plain", f"{NS}:pink_gel_mounds", f"{NS}:blue_gel_mounds"),
          creatures=[spawn("pastel_slime", 16, 2, 4), spawn("minecraft:rabbit", 4, 2, 3), spawn("minecraft:chicken", 4, 4, 4),
                     spawn("silly_cat", 4, 1, 2)])
    biome("pastel_reef", fairies=0, temperature=0.8, downfall=0.5, grass="#f5a9b8", foliage="#f5a9b8", water="#6fe0f2", water_fog="#1f9fb8",
          sky="#7fd4fa", fog="#f7d0d8", music_sound="minecraft:music.game", underwater_music=True,
          features={**ocean_floor, 9: [f"{NS}:trans_coral_reefs_dense", f"{NS}:trans_seagrass_warm", "minecraft:sea_pickle"]},
          creatures=[], monsters=OCEAN_MONSTERS,
          water_creatures=[spawn("minecraft:dolphin", 3, 1, 2)],
          water_ambient=[spawn("trans_fish", 25, 4, 8), spawn("minecraft:tropical_fish", 30, 8, 8), spawn("minecraft:pufferfish", 6, 1, 3)])
    biome("blooming_caverns", fairies=8, temperature=0.6, downfall=0.6, grass="#f5a9c0", foliage="#f5a9b8", water="#8fd8ff", water_fog="#2e7fb0",
          sky="#9fb8ff", fog="#f0c8e0", music_sound="minecraft:music.overworld.lush_caves",
          particles=particles("minecraft:spore_blossom_air", 0.004),
          features={3: UNDERGROUND, 6: ORES, 7: CAVE_DECOR, 8: SPRINGS,
                    9: [f"{NS}:blooming_cave_ceiling", f"{NS}:blooming_cave_floor", "minecraft:spore_blossom"]},
          creatures=[])

    # ---- the Fairy Realm: islands floating over a sea of clouds, no monsters but the fairy's
    biome("fairy_realm", fairies=12, temperature=0.7, downfall=0.5, grass="#f7b8d0", foliage="#f5a9b8", water="#8fdcff", water_fog="#3a9fd0",
          sky="#a8dcff", fog="#fbd6e6", music_sound="minecraft:music.overworld.cherry_grove",
          particles=particles("minecraft:cherry_leaves", 0.003),
          features={9: [f"{NS}:fairy_islands"]},
          creatures=[spawn("pastel_slime", 6, 1, 3), spawn("minecraft:rabbit", 3, 1, 2)], monsters=())

    # ---- pastel lush caves
    biome("pastel_lush_caves", temperature=0.5, downfall=0.5, grass="#f0b5c8", foliage="#f5a9b8", water="#7fd6ff", water_fog="#2e7fb0",
          sky="#9fb8ff", fog="#d9c8ff", music_sound="minecraft:music.overworld.lush_caves",
          particles=particles("minecraft:spore_blossom_air", 0.003),
          features={3: UNDERGROUND, 6: ORES + [f"{NS}:trans_ore_clay"], 7: CAVE_DECOR, 8: SPRINGS,
                    9: [f"{NS}:tall_trans_grass", f"{NS}:trans_lush_caves_ceiling_vegetation", "minecraft:cave_vines",
                        "minecraft:lush_caves_clay", f"{NS}:trans_lush_caves_vegetation", "minecraft:spore_blossom",
                        "minecraft:classic_vines_cave_feature"]},
          creatures=[], axolotls=[spawn("minecraft:axolotl", 10, 4, 6)],
          water_ambient=[spawn("minecraft:tropical_fish", 25, 8, 8)], underground_water=[spawn("minecraft:glow_squid", 10, 4, 6)])

    biome("crystal_caves", temperature=0.5, downfall=0.5, grass="#8ed8f8", foliage="#f5a9b8", water="#a88cf5", water_fog="#4a3a8f",
          sky="#9fb8ff", fog="#d9c8ff", music_sound="minecraft:music.overworld.lush_caves",
          particles=particles("minecraft:end_rod", 0.003),
          features={3: UNDERGROUND, 2: [f"{NS}:trans_crystal_geode_common"],
                    6: ORES + [f"{NS}:ore_trans_crystal_extra", f"{NS}:trans_ore_copper_large"],
                    7: CAVE_DECOR + [f"{NS}:trans_crystal_clusters_cave_floor", f"{NS}:trans_crystal_clusters_cave_ceiling"], 8: SPRINGS},
          creatures=[])


# ============================================================================================ feature order
# Minecraft requires features to appear in a consistent order across every biome of a dimension
# ("feature order cycle" crash otherwise), so every biome's lists are sorted by this one ranking.
FEATURE_RANK = [
    # structures & local modifications
    "minecraft:fossil_upper", f"{NS}:trans_crystal_geode_common", f"{NS}:trans_crystal_geode",
    f"{NS}:trans_dungeon", f"{NS}:trans_dungeon_deep",
    # ores
    *TRANS_ORES, f"{NS}:trans_ore_copper_large", f"{NS}:trans_ore_emerald", f"{NS}:trans_ore_clay",
    f"{NS}:ore_trans_crystal", f"{NS}:ore_trans_crystal_deep", f"{NS}:ore_trans_crystal_large", f"{NS}:ore_trans_crystal_extra",
    "minecraft:disk_clay", "minecraft:disk_sand",
    # underground decoration
    f"{NS}:trans_glow_lichen", f"{NS}:deep_cave_moss", f"{NS}:deep_cave_ceiling_moss",
    f"{NS}:trans_crystal_clusters_cave_floor", f"{NS}:trans_crystal_clusters_cave_ceiling",
    # springs
    f"{NS}:spring_trans_water", f"{NS}:spring_pink_lava",
    # vegetation: big things first, then flowers, grass and small decorations
    f"{NS}:trans_crystal_spikes", f"{NS}:frosted_ice_spikes", f"{NS}:trans_crystal_spikes_rare",
    f"{NS}:trees_trans_meadow", f"{NS}:trees_blossom_forest", f"{NS}:trees_heartwood_grove", f"{NS}:trees_crystal_grove",
    f"{NS}:trees_frosted_fields", f"{NS}:trees_lavender_marsh", f"{NS}:trans_bushes",
    f"{NS}:heart_trees", f"{NS}:heart_trees_rare",
    f"{NS}:trees_pearlwood_forest", f"{NS}:trees_bluebell_woods", f"{NS}:trees_twilight_thicket", f"{NS}:trees_candy_floss_grove",
    f"{NS}:trees_moonlit_meadow", f"{NS}:hedge_bushes",
    f"{NS}:tall_trans_grass", f"{NS}:trans_lush_caves_ceiling_vegetation", "minecraft:cave_vines", "minecraft:lush_caves_clay",
    f"{NS}:trans_lush_caves_vegetation", f"{NS}:blooming_cave_ceiling", f"{NS}:blooming_cave_floor", "minecraft:spore_blossom",
    "minecraft:classic_vines_cave_feature",
    f"{NS}:trans_coral_reefs", f"{NS}:trans_coral_reefs_dense", f"{NS}:trans_coral_reefs_rare", f"{NS}:trans_seagrass_warm", f"{NS}:trans_seagrass_deep", f"{NS}:trans_seagrass_river",
    f"{NS}:trans_seagrass_swamp", "minecraft:sea_pickle", f"{NS}:trans_kelp_warm", f"{NS}:trans_kelp_cold",
    f"{NS}:pride_blossoms_dense", f"{NS}:pride_blossoms", f"{NS}:trans_flowers", f"{NS}:meadow_flowers", f"{NS}:forest_flowers", f"{NS}:heartwood_flowers",
    f"{NS}:crystal_flowers", f"{NS}:candy_flowers", f"{NS}:lavender_flowers", f"{NS}:frost_flowers",
    f"{NS}:pride_peonies", f"{NS}:tall_trans_flowers_dense", f"{NS}:tall_trans_flowers", f"{NS}:field_flowers",
    f"{NS}:star_blooms_dense", f"{NS}:star_blooms", f"{NS}:forget_me_nots", f"{NS}:sky_bell_carpets", f"{NS}:heart_blooms", f"{NS}:trans_petals_forest", f"{NS}:trans_petals_meadow",
    f"{NS}:trans_crystal_clusters_surface",
    f"{NS}:trans_grass_meadow", f"{NS}:trans_grass_forest", f"{NS}:trans_grass_jungle", f"{NS}:trans_grass_plain",
    f"{NS}:trans_grass_normal", f"{NS}:trans_grass_taiga", f"{NS}:large_trans_ferns",
    f"{NS}:pastel_bushes", "minecraft:patch_cactus_desert", "minecraft:patch_dead_bush_2", f"{NS}:sugar_grass",
    f"{NS}:trans_lily_pads", "minecraft:patch_sugar_cane", "minecraft:patch_sugar_cane_swamp",
    "minecraft:brown_mushroom_normal", "minecraft:brown_mushroom_swamp", "minecraft:red_mushroom_swamp",
    f"{NS}:trans_firefly_bushes_near_water", f"{NS}:trans_firefly_bushes_swamp", "minecraft:vines", f"{NS}:trans_boulders",
    f"{NS}:pink_gel_mounds", f"{NS}:blue_gel_mounds", f"{NS}:fairy_islands",
    # top layer
    "minecraft:freeze_top_layer",
]


def sort_biome_features():
    folder = os.path.join(WG, "biome")
    rank = {f: i for i, f in enumerate(FEATURE_RANK)}
    for f in os.listdir(folder):
        path = os.path.join(folder, f)
        with open(path, encoding="utf-8") as fh:
            b = json.load(fh)
        for i, step in enumerate(b["features"]):
            missing = [x for x in step if x not in rank]
            if missing:
                raise SystemExit(f"{f}: add {missing} to FEATURE_RANK")
            b["features"][i] = sorted(step, key=lambda x: rank[x])
        write(path, b)
    # The same feature must also never be used in two different steps.
    seen = {}
    for f in os.listdir(folder):
        with open(os.path.join(folder, f), encoding="utf-8") as fh:
            b = json.load(fh)
        for i, step in enumerate(b["features"]):
            for x in step:
                if seen.setdefault(x, i) != i:
                    raise SystemExit(f"{x} is used in steps {seen[x]} and {i}")


# ============================================================================================ surface rules
def block(name, **props):
    return {"type": "minecraft:block", "result_state": state(name, **props)}


def cond(if_true, then_run):
    return {"type": "minecraft:condition", "if_true": if_true, "then_run": then_run}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def biome_is(*names):
    return {"type": "minecraft:biome", "biome_is": [f"{NS}:{n}" for n in names]}


def floor(add_surface_depth=False, secondary=0, offset=0):
    return {"type": "minecraft:stone_depth", "add_surface_depth": add_surface_depth, "offset": offset,
            "secondary_depth_range": secondary, "surface_type": "floor"}


def above_water(offset=-1, multiplier=0):
    return {"type": "minecraft:water", "add_stone_depth": False, "offset": offset, "surface_depth_multiplier": multiplier}


def y_above(y, multiplier=0, add_stone_depth=False):
    return {"type": "minecraft:y_above", "add_stone_depth": add_stone_depth, "anchor": {"absolute": y}, "surface_depth_multiplier": multiplier}


def noise(name, lo, hi=1.7976931348623157e+308):
    return {"type": "minecraft:noise_threshold", "max_threshold": hi, "min_threshold": lo, "noise": name}


def NOT(c):
    return {"type": "minecraft:not", "invert": c}


STEEP = {"type": "minecraft:steep"}
GRASS = block("trans_grass_block", snowy=False)
DIRT = block("trans_dirt")
SAND = block("trans_sand")
SANDSTONE = block("trans_sandstone")
STONE = block("trans_stone")


def surface_rule():
    sandy = ("sugar_dunes", "trans_beach")
    watery = ("trans_ocean", "deep_trans_ocean", "trans_river")
    top = seq(
        # Sandy biomes and water beds: sand all the way down to the stone.
        cond(biome_is(*sandy), SAND),
        cond(biome_is(*watery), SAND),
        # Lavender Marsh: puddles of water right at sea level, like a vanilla swamp.
        cond(biome_is("lavender_marsh"), cond(y_above(62), cond(NOT(y_above(63)), cond(noise("minecraft:surface_swamp", 0.0),
                                                                                    block("minecraft:water", level=0))))),
        # Pastel Peaks: snowy summits, bare stone on cliffs.
        cond(biome_is("pastel_peaks"), seq(cond(y_above(150, multiplier=1), block("minecraft:snow_block")),
                                           cond(STEEP, STONE))),
        # Frosted Fields: drifts of snow blocks (where ice spikes grow) between snowy grass.
        cond(biome_is("frosted_fields"), cond(noise("minecraft:surface", 0.35), block("minecraft:snow_block"))),
        # Crystal Grove: rocky patches of trans stone.
        cond(biome_is("crystal_grove"), cond(noise("minecraft:surface", 0.45), STONE)),
        # Everywhere else: grass above the water line, sand under water.
        cond(above_water(), GRASS),
        SAND,
    )
    under = seq(
        cond(biome_is(*sandy, *watery), SAND),
        cond(biome_is("pastel_peaks"), cond(STEEP, STONE)),
        DIRT,
    )
    # Sandstone under the sand of dunes and beaches.
    sandstone_layer = cond(biome_is(*sandy), cond(floor(add_surface_depth=True, secondary=6), SANDSTONE))
    return seq(
        cond({"type": "minecraft:vertical_gradient", "false_at_and_above": {"above_bottom": 5}, "random_name": "minecraft:bedrock_floor",
              "true_at_and_below": {"above_bottom": 0}}, block("minecraft:bedrock")),
        cond({"type": "minecraft:above_preliminary_surface"}, seq(
            cond(floor(), top),
            cond(floor(add_surface_depth=True), under),
            sandstone_layer,
        )),
        # Trans deepslate below y=0, blending into trans stone up to y=8, like vanilla deepslate.
        cond({"type": "minecraft:vertical_gradient", "false_at_and_above": {"absolute": 8}, "random_name": "minecraft:deepslate",
              "true_at_and_below": {"absolute": 0}}, block("trans_deepslate", axis="y")),
    )


def generate_noise_settings():
    path = os.path.join(WG, "noise_settings", "trans_realm.json")
    with open(path, encoding="utf-8") as f:
        settings = json.load(f)
    settings["default_block"] = state("trans_stone")
    settings["default_fluid"] = state("minecraft:water", level=0)
    settings["surface_rule"] = surface_rule()
    write(path, settings)


# ============================================================================================ biome layout
C_BANDS = [(-0.11, 0.3), (0.3, 0.55), (0.55, 1.0)]
T_BANDS = [(-1.0, -0.45), (-0.45, 0.2), (0.2, 0.55), (0.55, 1.0)]
H_BANDS = [(-1.0, -0.35), (-0.35, 0.1), (0.1, 0.3), (0.3, 1.0)]
E_BANDS = [(-1.0, -0.375), (-0.375, 0.45), (0.45, 1.0)]
W_BANDS = [(-1.0, -0.55), (-0.55, -0.05), (-0.05, 0.05), (0.05, 0.55), (0.55, 1.0)]


def land_biome(c, t, h, e, w):
    """Picks the biome for one inland cell of the climate grid (indices into the bands above)."""
    river_band = w == 2
    if river_band and c <= 1 and not (c == 1 and e == 0):
        return "trans_river"
    if c >= 1 and e == 0:
        return "pastel_peaks"
    if t == 0:
        return "pearlwood_forest" if h >= 2 else "frosted_fields"
    if t == 3:
        return "sugar_dunes" if h <= 1 else "heartwood_grove"
    if h == 3:
        return "twilight_thicket" if (t == 2 and e != 2) else "lavender_marsh"
    if h == 2:
        return "bluebell_woods" if (t == 1 and w <= 2) else "trans_forest"
    if w == 4:
        return "crystal_grove"
    if t == 2 and w <= 1:
        return "candy_floss_grove"
    if t == 1 and h == 1 and w <= 1:
        return "pride_flower_fields"
    if h == 0 and w >= 2:
        return "gumdrop_glade" if t == 2 else "moonlit_meadow"
    return "trans_meadow"


def merge_cells(cells):
    """Greedily merges grid cells into boxes (lists of index ranges) so the dimension file stays small."""
    remaining = set(cells)
    boxes = []
    dims = len(next(iter(cells)))
    while remaining:
        start = min(remaining)
        lo = list(start)
        hi = list(start)
        for axis in range(dims):
            while True:
                candidate = hi[axis] + 1
                trial_lo, trial_hi = lo[:], hi[:]
                trial_hi[axis] = candidate
                box = set(itertools.product(*[range(trial_lo[a], trial_hi[a] + 1) for a in range(dims)]))
                if box <= remaining:
                    hi = trial_hi
                else:
                    break
        box = set(itertools.product(*[range(lo[a], hi[a] + 1) for a in range(dims)]))
        remaining -= box
        boxes.append((lo, hi))
    return boxes


def entry(biome_name, temperature, humidity, continentalness, erosion, weirdness, depth=0.0):
    return {"biome": f"{NS}:{biome_name}", "parameters": {
        "temperature": list(temperature), "humidity": list(humidity), "continentalness": list(continentalness),
        "erosion": list(erosion), "weirdness": list(weirdness), "depth": depth, "offset": 0.0}}


def generate_dimension():
    full = (-1.0, 1.0)
    entries = [
        entry("deep_trans_ocean", full, full, (-1.2, -0.455), full, full),
        # Warm seas are the Pastel Reef, cooler ones the kelp-forested Trans Ocean.
        entry("trans_ocean", (-1.0, 0.2), full, (-0.455, -0.19), full, full),
        entry("pastel_reef", (0.2, 1.0), full, (-0.455, -0.19), full, full),
        entry("trans_beach", full, full, (-0.19, -0.11), full, full),
    ]
    by_biome = {}
    for c, t, h, e, w in itertools.product(range(len(C_BANDS)), range(len(T_BANDS)), range(len(H_BANDS)),
                                           range(len(E_BANDS)), range(len(W_BANDS))):
        by_biome.setdefault(land_biome(c, t, h, e, w), set()).add((c, t, h, e, w))
    bands = [C_BANDS, T_BANDS, H_BANDS, E_BANDS, W_BANDS]
    for name in sorted(by_biome):
        for lo, hi in merge_cells(by_biome[name]):
            rng = [(bands[a][lo[a]][0], bands[a][hi[a]][1]) for a in range(5)]
            entries.append(entry(name, rng[1], rng[2], rng[0], rng[3], rng[4]))
    # Cave biomes deep under the wetter, more inland parts of the realm: crystal caves (only a narrow, deep band, so they're
    # a find rather than every cave), and lush caves where it's wettest.
    entries.append(entry("crystal_caves", full, (0.45, 0.65), (0.0, 1.0), full, full, depth=[0.45, 0.9]))
    entries.append(entry("pastel_lush_caves", full, (0.65, 1.0), (0.0, 1.0), full, full, depth=[0.2, 0.9]))
    # Blooming caverns under the drier middle of the realm.
    entries.append(entry("blooming_caverns", full, (-0.1, 0.3), (0.0, 1.0), full, full, depth=[0.2, 0.9]))
    dimension = {"type": f"{NS}:trans_realm", "generator": {
        "type": "minecraft:noise", "settings": f"{NS}:trans_realm",
        "biome_source": {"type": "minecraft:multi_noise", "biomes": entries}}}
    write(os.path.join(DATA, NS, "dimension", "trans_realm.json"), dimension)
    return len(entries)


# ============================================================================================ dimension type + sky
def generate_dimension_type():
    path = os.path.join(DATA, NS, "dimension_type", "trans_realm.json")
    with open(path, encoding="utf-8") as f:
        dim = json.load(f)
    attrs = dim["attributes"]
    attrs["minecraft:visual/cloud_color"] = "#f0ffffff"     # white; trans_clouds tints it through the flag
    attrs["minecraft:visual/cloud_height"] = 172.33
    attrs["minecraft:visual/fog_color"] = "#f5a9b8"
    attrs["minecraft:visual/sky_color"] = "#5bcefa"
    attrs["minecraft:visual/water_fog_color"] = "#1f6fa8"
    dim["timelines"] = f"#{NS}:in_trans_realm"
    write(path, dim)
    # The Fairy Realm shares the realm's sky (same timelines), but its clouds drift far below the islands.
    fairy = json.loads(json.dumps(dim))
    fairy["attributes"]["minecraft:visual/cloud_height"] = 72.0
    write(os.path.join(DATA, NS, "dimension_type", "fairy_realm.json"), fairy)


def generate_fairy_dimension():
    """The Fairy Realm: an empty sky (a flat world of nothing) dotted with floating islands by its one biome's features.
    The arena island in the middle is placed by FairyRealm.java the first time anyone arrives."""
    write(os.path.join(DATA, NS, "dimension", "fairy_realm.json"), {
        "type": f"{NS}:fairy_realm",
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": f"{NS}:fairy_realm", "features": True, "lakes": False,
            "layers": [{"block": "minecraft:air", "height": 1}], "structure_overrides": []}}})


def argb(a, rgb):
    v = (a << 24) | rgb
    return v - (1 << 32) if v >= 1 << 31 else v


def lerp_rgb(c1, c2, t):
    r = round(((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * t)
    g = round(((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * t)
    b = round((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t)
    return (r << 16) | (g << 8) | b


def generate_timeline():
    """A copy of vanilla's day timeline (so the realm keeps the normal day/night rules) with a trans sky:
    pink-and-lavender sunrises and sunsets, a purple dusk, an indigo night with twice the stars,
    lilac moonlight and pinkish clouds."""
    vanilla_path = os.path.join(HERE, "vanilla_extra", "templates", "timeline_day.json")
    with open(vanilla_path, encoding="utf-8") as f:
        day = json.load(f)
    # Time markers (wake_up_from_sleep, noon, ...) may only be defined once per clock, and vanilla's day
    # timeline already defines them for minecraft:overworld, which this realm shares.
    day.pop("time_markers", None)
    tracks = day["tracks"]
    tracks["minecraft:visual/sky_color"]["keyframes"] = [
        {"ticks": 133, "value": "#ffffff"}, {"ticks": 11867, "value": "#ffffff"},
        {"ticks": 12700, "value": "#e8b4e0"}, {"ticks": 13670, "value": "#1c1648"},
        {"ticks": 22330, "value": "#1c1648"}, {"ticks": 23300, "value": "#f0c0dc"}]
    tracks["minecraft:visual/fog_color"]["keyframes"] = [
        {"ticks": 133, "value": "#ffffff"}, {"ticks": 11867, "value": "#ffffff"},
        {"ticks": 12500, "value": "#ffc4dc"}, {"ticks": 13670, "value": "#2a1a44"},
        {"ticks": 22330, "value": "#2a1a44"}, {"ticks": 23400, "value": "#ffd0e4"}]
    tracks["minecraft:visual/cloud_color"]["keyframes"] = [
        {"ticks": 133, "value": -1}, {"ticks": 11867, "value": -1},
        {"ticks": 12600, "value": argb(255, 0xFFC8E0)}, {"ticks": 13670, "value": argb(255, 0x3A2E5C)},
        {"ticks": 22330, "value": argb(255, 0x3A2E5C)}, {"ticks": 23300, "value": argb(255, 0xFFD6E8)}]
    tracks["minecraft:visual/sky_light_color"]["keyframes"] = [
        {"ticks": 730, "value": "#ffffff"}, {"ticks": 11270, "value": "#ffffff"},
        {"ticks": 13140, "value": "#9a7aff"}, {"ticks": 22860, "value": "#9a7aff"}]
    stars = tracks["minecraft:visual/star_brightness"]["keyframes"]
    for k in stars:
        k["value"] = round(min(1.0, k["value"] * 1.8), 4)
    # Sunrise/sunset glow: keep vanilla's timing and strength (the alpha), but colour it by strength:
    # the brightest glow is hot pink, fading through trans pink and lavender to trans blue.
    glow = tracks["minecraft:visual/sunrise_sunset_color"]["keyframes"]
    stops = [(0.0, 0x8ED8FA), (0.35, 0xB9A8F0), (0.7, 0xF5A9B8), (1.0, 0xF06C9A)]
    for k in glow:
        value = k["value"].lstrip("#")
        alpha = int(value[:2], 16)
        t = alpha / 255.0
        for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
            if t <= t1:
                rgb = lerp_rgb(c0, c1, (t - t0) / (t1 - t0))
                break
        k["value"] = "#%02x%06x" % (alpha, rgb)
    write(os.path.join(DATA, NS, "timeline", "trans_day.json"), day)
    generate_cloud_timeline()
    write(os.path.join(DATA, NS, "tags", "timeline", "in_trans_realm.json"), {"values": [
        "#minecraft:universal", "minecraft:moon", "minecraft:early_game", f"{NS}:trans_day", f"{NS}:trans_clouds"]})


CLOUD_PERIOD = 6000     # five minutes for the whole flag


def generate_cloud_timeline():
    """The realm's clouds drift through the trans flag in order: blue, pink, white, pink, blue. Each colour holds for
    about 38 seconds and blends into the next over another 38. (Since 1.21.6 the cloud renderer only takes shapes from its texture,
    so the colour has to come from the cloud_color attribute; the day timeline still dims it at dusk and night.)"""
    # Integers, like vanilla's own cloud_color keyframes (the multiply modifier's argument). The flag's exact blue and
    # pink: paler tints washed out to plain white under shader packs' bright lighting.
    blue, pink, white = argb(255, 0x5BCEFA), argb(255, 0xF5A9B8), argb(255, 0xFFFFFF)
    # Looping blue, pink, white, pink reads as the flag over and over: blue, pink, white, pink, blue, pink...
    step = CLOUD_PERIOD // 4
    keyframes = []
    for i, colour in enumerate((blue, pink, white, pink)):
        keyframes.append({"ticks": i * step, "value": colour})
        keyframes.append({"ticks": i * step + step // 2, "value": colour})
    write(os.path.join(DATA, NS, "timeline", "trans_clouds.json"), {
        "clock": "minecraft:overworld",
        "period_ticks": CLOUD_PERIOD,
        "tracks": {"minecraft:visual/cloud_color": {"keyframes": keyframes, "modifier": "multiply"}}})


def remove_stale():
    for name in ("pride_blossoms", "pride_blossoms_dense", "trans_cherry_trees", "trans_trees_dense", "trans_trees_sparse",
                 "trans_village", "ore_trans_crystal", "ore_trans_crystal_deep"):
        for kind in ("placed_feature", "configured_feature"):
            p = os.path.join(WG, kind, f"{name}.json")
            if os.path.exists(p):
                os.remove(p)
    folder = os.path.join(WG, "biome")
    for f in os.listdir(folder):
        os.remove(os.path.join(folder, f))


def main():
    remove_stale()
    generate_features()
    generate_biomes()
    sort_biome_features()
    generate_noise_settings()
    n = generate_dimension()
    generate_dimension_type()
    generate_fairy_dimension()
    generate_timeline()
    print(f"World generation written ({n} biome-source entries).")


if __name__ == "__main__":
    main()
