# Trans Dimension: handoff notes

State of the mod for whoever picks it up next (person or AI). The README covers what the mod does for players; this file covers how it's built and what still needs checking.

## Status

- Target: Minecraft Java **26.2**, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25. Mod id `transdimension`, package `dev.goober.transdimension`.
- **The mod has never been compiled or run.** The cloud environment it was written in can't download Gradle, Fabric's maven, Mojang's game files or a JDK 25 (see "Building in the cloud" below). Everything was checked by hand against Fabric API's source, the fabric-docs reference mod for 26.2, the NeoForge 26.x porting primers and vanilla 26.2 data from misode/mcmeta.
- `python3 tools/validate_resources.py <mcmeta-summary>/registries/data.json` passes with 0 errors. That run covers resources and data, not Java.

## What changed in this round

| Area | Change |
| --- | --- |
| Textures | `tools/generate_textures.py` rebuilds every texture from Minecraft's own sprites (the two zips at the repo root plus `tools/vanilla_extra/entity`). It gradient-maps vanilla luminance onto trans palettes and adds flag stripes, Voronoi pink/blue patches and similar touches. It also draws Minecraft-style cookies and a donut. |
| Water | The custom Trans Water fluid is gone. Water is vanilla water, recoloured per biome through `water_color`. |
| Blocks | Full wood set; stone, cobblestone, stone brick and sandstone sets; flag, pink and blue stained glass and panes; trans wool and carpet; Trans Lantern; Trans Chair and Trans Table (`FurnitureBlock`); better crystal ore, block and cluster. |
| Biomes | 13 biomes (`tools/generate_worldgen.py`). Each has its own trees, flowers, particles, music, sky, fog, grass and water colours. |
| Sky | `data/transdimension/timeline/trans_day.json` is a full copy of vanilla's day timeline, re-tinted. The dimension type uses it through the `#transdimension:in_trans_realm` timeline tag. |
| Silly Cat | `entity/SillyCat` (PathfinderMob with a lick goal), `effect/SlobberedEffect`, client `SillyCatModel`, `SillyCatRenderer` and `SalivaOverlay`. Sounds are vanilla cat sounds re-pitched in `sounds.json`; there are no custom .ogg files. |
| Sheep | `TransRecolor.REPLACEMENTS` swaps vanilla sheep wool for trans flag wool inside the realm. |
| Intro | `TransIntroOverlay` is an 8.5 second cinematic (stars, waving flag, heart pop, typewriter title, letterbox). |
| Armor | Equipment textures are re-drawn from vanilla diamond armor in trans colours. |
| Villages | `tools/generate_villages.py` converts the vanilla plains village into `transdimension:trans_village`, a jigsaw structure with furniture, bakeries, Silly Cats, a flag plaza and banner and lantern posts. The old per-chunk village feature (and its overlapping houses) is gone. |
| Data fixes | The baker's level 5 trade no longer sells the removed water bucket; it sells Trans Lanterns. New advancements: Big Smooch and Home Sweet Home. |

## Generators

Run from the repository root (needs `pip install pillow nbtlib`), in this order:

1. `python3 tools/generate_textures.py`: textures, `.mcmeta` files, saliva overlay frames, heart clouds and the icon.
2. `python3 tools/generate_data.py`: blockstates, models, item definitions, loot tables, recipes, block and item tags, `sounds.json` and `en_us.json`. It wipes and rewrites `data/minecraft/tags/{block,item,...}` and our block and item tags. It leaves `data/minecraft/tags/worldgen` alone, because that belongs to the village script.
3. `python3 tools/generate_worldgen.py`: features, biomes (it wipes the biome folder), surface rules, noise settings, the dimension, the dimension type and the timeline. Placed features of all biomes are sorted by one global `FEATURE_RANK` list, because Minecraft crashes on inconsistent feature order. New features must be added there.
4. `python3 tools/generate_villages.py`: everything under `structure/village/trans`, `worldgen/template_pool/village/trans`, the processor lists, the structure, the structure set, the village biome tag and `#minecraft:village`.
5. `python3 tools/validate_resources.py /path/to/mcmeta-summary/registries/data.json`: cross-checks all of the above (blockstates, models, textures, loot, recipes, tags, block-state properties in worldgen and templates, template pools, jigsaw pools, structures, villager trades, advancements, sounds).

Hand-made files the scripts don't touch: the cake models and blockstate, `equipment/trans_crystal.json`, the chest loot tables `trans_bakery` and `trans_house`, the baker trades and trade sets, and the advancements.

Inputs copied out of vanilla 26.2 live in `tools/vanilla_extra/`: entity textures, blockstate templates for the block families, the vanilla day timeline, the plains village templates (`structures/village/**.nbt`) and their pools.

## Conventions

- Tabs, Javadoc on classes and anything non-obvious, `snake_case` ids in the `transdimension:` namespace.
- Every block needs a blockstate, an item definition (unless registered without an item), a model, a name, a loot table, mining tags and a texture made by the texture script. The validator reports any that are missing.
- Hooks into game internals fail safe: mixins use `require = 0`, and reflection is wrapped in try/catch with a disable flag.

## Compile risks (check these first)

All of these were inferred from 26.2 data codecs, primers or older versions rather than read in 26.2 source:

| Where | API | If it doesn't compile |
| --- | --- | --- |
| `TransGrassBlock` | `extends SnowyBlock` (26.1 rename of `SnowyDirtBlock`), protected constructor `(Properties)` | Check the class name in 26.2; worst case extend `Block` and add the `SNOWY` property yourself. |
| `ModBlocks` trans_leaves | `new UntintedParticleLeavesBlock(float chance, ParticleOptions, Properties)` | It's the class cherry leaves use since 1.21.5; check the argument order in 26.2. |
| `ModBlocks` crystal cluster | `new AmethystClusterBlock(float height, float width, Properties)` | 1.21.5 swapped to (height, width); older order was (int, int). |
| `ModBlocks` trans_sand | `new ColoredFallingBlock(new ColorRGBA(0xFFF5A9B8), Properties)` | Gravel uses this class in 26.2. Plain `FallingBlock` subclasses need `codec()` and `getDustColor`. |
| `ModBlocks` sapling | `new TreeGrower(String, Optional, Optional, Optional)` | 4-argument form: mega tree, tree, flowers tree. |
| `ModBlocks` pride_blossom | `new FlowerBlock(Holder<MobEffect>, float seconds, Properties)` | The float is the suspicious-stew duration in seconds. |
| `ModItems` boba | `Consumables.defaultDrink()`, `Item.Properties#usingConvertsTo(Item)` | `Consumables.DEFAULT_DRINK` exists as a constant in 1.21.x. |
| `ModItems.initialize` | `HoeItem::onlyIfAirAbove`, `HoeItem.changeIntoState(BlockState)` | Used by vanilla's own tillables. |
| `SillyCat` | `ValueInput#getBooleanOr(String, boolean)`, `ValueInput#getInt(String)` returning `Optional<Integer>` | Use `valueInput.read("blep", Codec.BOOL).orElse(false)`. |
| `TransIntroOverlay` | `SimpleSoundInstance.forUI(SoundEvent, float pitch, float volume)` | `forUI(Holder<SoundEvent>, float)` also exists. |
| Overlays | `GuiGraphicsExtractor#guiWidth()` / `guiHeight()`, 10-argument `blit(RenderPipelines.GUI_TEXTURED, …)` | The saliva fade uses pre-faded frames so no colored blit is needed. |
| `TextureManagerMixin` | target `TextureManager#getTexture(Identifier)` | `require = 0`; it just stops recolouring mobs if it misses. |

## Things that would be nice next

- **Compile and play-test.** Look first at the Silly Cat model in game (the tongue position, the texture layout in `generate_textures.py` `silly_cat()`), the village spacing, and the sky colours at sunrise and sunset.
- Real sound effects for the Silly Cat (now vanilla cat sounds) and the Pride Oven.
- A Silly Cat that can be tamed or bred, and a spawn rule so it only appears on grass.
- Villages in more biomes (Pastel Peaks and the forests are left out on purpose: steep or crowded).
- Trampled farmland in villages turns back into vanilla dirt, not trans dirt.

## Building in the cloud

The Claude Code cloud environment used so far blocks these hosts, so Gradle can't run there: `maven.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`, `launchermeta.mojang.com`, and for JDK 25 `api.adoptium.net`, `download.java.net` and `cdn.azul.com`. Allowing them under the environment's network access settings (Custom, Allowed domains; see https://code.claude.com/docs/en/cloud-environments#network-access) should make `./gradlew build` possible there. Locally, any JDK 25 works.

## Licensing note

Textures and village templates are derived from Mojang's assets and are not MIT; the README says so. Keep the reference zips and `tools/vanilla_extra/` out of the built jar (they're outside `src/`, so they already are).
