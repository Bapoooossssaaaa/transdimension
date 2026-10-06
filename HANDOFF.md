# Trans Dimension: handoff notes

State of the mod for whoever picks it up next (person or AI). The README covers what the mod does for players; this file covers how it's built and what still needs checking.

## Status

- Target: Minecraft Java **26.2**, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25. Mod id `transdimension`, package `dev.goober.transdimension`.
- **Rounds 1 and 2 compiled and loaded.** The owner ran them on 26.2 next to Sodium, Iris and Xaero's maps. The first run hung on "Preparing for world creation" because the sky timeline repeated vanilla's time markers; commit 2e490ee fixed that.
- **Round 3 compiles and runs**: the owner's screenshots show Maddie (in her new skin) and the wings in game.
- **Round 4 builds: `gradlew build` passes; nothing has been play-tested yet.** The first round-4 builds reported six errors in `src/main` (`Player#displayClientMessage`, which 26.2 split into `sendOverlayMessage` and `sendSystemMessage`) and two in `src/client` (`RenderTypes.entityCutoutNoCull`, renamed `entityCutout` in 26.1), all fixed. javac checks every class's names before it stops, so that confirmed every other API in rounds 3 and 4. What it can't check is mixin targets and names looked up by reflection; the game checks those at startup. Round 4 rebuilt the wings and added the new plants, woods, flowers, biomes, the three creatures and the whole Fairy Realm endgame (see "Round 4 at a glance"). The cloud environment can't download Gradle, Fabric's maven, Mojang's game files or a JDK 25 (see "Building in the cloud"). Every API was checked by hand against Fabric API's 26.2 source, NeoForge's 26.2 source patches (which quote vanilla 26.2 code around each patch), the NeoForge 26.x porting primers and vanilla 26.2 data from misode/mcmeta. The names that couldn't be confirmed are listed under "Unverified APIs (round 4)" with what to try instead; round 3's table is kept below it.
- **Round 5 builds and runs**; the owner is play-testing it. Their first notes became round 6: pink fire in both realms, random pastel slime colours from spawn eggs and `/summon`, the Fairy Jar's fairy drawn cutout again (drawn translucent, it vanished behind the jar's glass), fairies with one pair of side wings instead of four, and recipe-book unlocks. **Round 6's first build failed only on `PinkFireBlock`** (vanilla's `FireBlock#getIgniteOdds`/`getBurnOdds` are private; fixed), so the rest of `src/main` compiles; the client source set hasn't been compiled yet. See "Unverified APIs (round 6)".
- **Round 9 is written. Its first build (the first of rounds 7 to 9) failed only on `AgeableMob#setAgeLocked`, which is protected (now fixed), so the rest of `src/main` compiles; the client hasn't been compiled yet.** Round 9: the Fairy Portal frame textures redone from vanilla's end portal frame (region by region, so the detail survives); the **pink deep dark** under the realm's mountains (pink sculk blocks: vanilla's sensor, shrieker and vein blocks with vanilla's block entities, a catalyst of our own, `PinkSculkPatchFeature`), **sculk gem ore**; the friendly **pink warden** (`entity/PinkWarden`, a `Warden` that only targets `Enemy` mobs); darkness turned into night vision in the realm (`world/PinkDeepDark`); **sculk people** (`entity/SculkPerson`, a `Villager` with the `sculk_person` profession and data-driven trades); pink sculk particles in the realm (`client/PinkSculkParticles`); the **pink ancient city** (`tools/generate_ancient_city.py`); the **candle ritual** that opens a **Sky Portal** in its gate (`world/SculkRitual`, `RitualPedestalBlock`, `RitualCrystalBlock`, `SkyPortalBlock`); and the **Cloud Realm** dimension (`world/CloudRealm`, `WoolCloudFeature`, `entity/Cloudy`, baby happy ghasts, white pig/cow/chicken variants). See "Unverified APIs (round 9)".
- **Round 8 is written, not compiled yet** (round 7 hasn't been built either): the Fairy Jar's light moved into the jar's block model (the block entity renderer never showed in the owner's game), the Fairy Realm intro is gone, the Trans Fairy is called by throwing a Trans Crystal onto the altar and arrives in a cutscene that kills Maddie (`world/FairyCutscene`), her health shows in the client's own trans bar, Crystal Pearls are thrown like eyes of ender (`entity/CrystalEye`, no more sanctum maps), chests hold diamonds instead of Trans Crystals, crystal gear is diamond gear upgraded with a Crystal Alloy and the Crystal Upgrade Smithing Template (a plain item with lore; found in mermaid ruin chests, crafted, or copied) in a smithing table, and pastel slimes follow by default, stay when petted and ignore falls. See "Unverified APIs (round 8)".
- **Round 7 is written, not compiled yet**: trans sea pickles on the reefs (the reef feature still placed vanilla's green ones), trans cave vines and Trans Glow Berries in place of vanilla's, softer trans gravel, the trans prismarine family and trans sea lanterns (built into the mermaid ruins), pink obsidian, and the Bottled Fairy's own rescue (only from bottling a fairy now). It also fixes two advancements that shared the id `bottled_magic` (the Fairy Jar's is now `fairy_in_a_jar`). See "Unverified APIs (round 7)".
- `python3 tools/validate_resources.py <mcmeta-summary>/registries/data.json` passes with 0 errors. That run covers resources and data, not Java.

## Round 3 at a glance

| Area | Where |
| --- | --- |
| Grey stone, trans deepslate family, rose granite / pearl diorite / sky andesite, trans gravel, the eight vanilla ores in trans stone and deepslate, deepslate crystal ore | `ModBlocks`, `generate_textures.py` (`trans_stone`, `trans_deepslate`, `ORES`...), `generate_worldgen.py` (`VANILLA_ORES`, `ORE_SWAPS`, deepslate gradient in the surface rule) |
| Clear trans glass from smelting trans sand | `ModBlocks.TRANS_GLASS`, recipe in `generate_data.py` |
| Seven flowers, Pride Peony, Trans Petals, replacing vanilla flowers in the realm | `ModBlocks` (`flower()`), `generate_textures.py` (`FLOWERS`), worldgen flower features |
| Per-mob trans textures and trans axolotls | `generate_textures.py` `trans_mob_textures()` writes `textures/entity/trans/<vanilla path>`; `TransRecolor` prefers those over runtime recolouring |
| Pastel Lush Caves and four themed forests (Pearlwood, Bluebell, Twilight, Candy Floss) | `generate_worldgen.py` |
| Trans Bed with the double-bed heart | `block/TransBedBlock` (the `heart` property pairs beds along a row), three foot models |
| Trans boats | `ModEntities.TRANS_BOAT/TRANS_CHEST_BOAT` (vanilla `Boat`/`ChestBoat`), `BoatRenderer` with our layers in `TransDimensionClient` |
| Shearing realm sheep gives trans wool | `event/RealmEvents` (`LootTableEvents.MODIFY_DROPS`) |
| Nine cat plushes, one per village | `ModBlocks.PLUSHES`, `block/PlushSpotBlock`, `block/entity/PlushSpotBlockEntity`, `world/PlushLedger`, `registry/ModAttachments`; spots placed by `generate_villages.py` `place_plush_spot()` |
| Eight paintings | `generate_textures.py` `PAINTINGS`, `generate_data.py` `PAINTINGS` (variants + `#minecraft:placeable`) |
| Egg House island | `tools/generate_egg_house.py` |
| Maddie | `entity/Maddie`, `client/entity/MaddieRenderer`, `client/screen/MaddieDialogueScreen`, `network/*` |
| Trans Wand | `item/TransWandItem`, `entity/TransMagicBolt` |
| Trans Wings | `item/TransWings` (server), `client/wings/*`, `client/mixin/AvatarRendererMixin` |
| Flag-coloured clouds | `generate_worldgen.py` `generate_cloud_timeline()` |
| Advancement tab | `generate_data.py` `generate_advancements()` |

## Round 4 at a glance

| Area | Where |
| --- | --- |
| Quick fixes: trans sand smelts into trans glass (it left `#minecraft:smelts_to_glass`, which vanilla's glass recipe uses), blue tops on trans wool, Maddie's skin from `maddieskintexture.png`, the Egg House ten times rarer (152/76 chunk grid) | `generate_data.py`, `generate_textures.py` (`trans_wool_top`, `MADDIE_SKIN`), `generate_egg_house.py` |
| Trans Wings rebuilt: feathered bird wings with a clean fold, up-and-down wingbeats; capes hidden while wearing them | `client/wings/TransWingsModel`, `WingPose`, `WingAnimations`; texture `trans_wings_model_texture()`; `TransDimensionClient` (`ALLOW_CAPE_RENDER`) |
| Trans crystals: a rare deep ore, gear removed; prism clusters drop prism shards; Pastel Prism block | `ModItems`, `ModBlocks`, `generate_worldgen.py` crystal ores, `generate_data.py` |
| Vegetation: trans grass/ferns/bushes/firefly bushes/sugar grass, seagrass, kelp, lily pads, blush/sky/pearl corals | `ModBlocks` (`coral()`), `block/TransGrassPlantBlock`, `TransSeagrassBlock`, `TransKelpBlock`, `TransKelpPlantBlock`, `world/TransCoralReefFeature`, `generate_data.py` `generate_vegetation()` |
| Four wood families (pearl, sky, twilight, blush), heart trees, new flowers, tall flowers, hedges, flowering blush leaves | `ModBlocks.WoodFamily`/`woodFamily()`, `world/HeartTreeFeature`, `generate_data.py` `generate_woods_and_flowers()` |
| Five biomes: Pride Flower Fields, Moonlit Meadow, Gumdrop Glade, Pastel Reef, Blooming Caverns (a cave biome) | `generate_worldgen.py` (`land_biome`, `generate_biomes`) |
| Trans fish, trans enderman, pastel slime pets, gel blocks, gumdrops, trans pearls | `entity/TransFish`, `TransEnderman`, `PastelSlime`, `mixin/EnderManMixin`; `client/entity/*Fish*`, `*Enderman*`, `*PastelSlime*`; `generate_data.py` `generate_creatures()`; `generate_textures.py` `creature_textures()` |
| The endgame: Fairy Sanctums, the Fairy Portal, the Fairy Realm, the Trans Fairy, the Fairy Jar | see "How the Fairy Realm works" |

### How the Fairy Realm works

- **Sanctums.** `generate_fairy_realm.py` builds `structure/fairy_sanctum.nbt` (33x41x41: a shrine on top, a spiral staircase, a domed hall with the frames) and a one-piece jigsaw structure whose template y 33 lands on the first free block above the ground (`start_height` -33 with `project_start_to_heightmap`), `random_spread` 56/24 chunks, `terrain_adaptation: none`. The processor list `fairy_sanctum` fills about one frame in eight with a pearl. There are no maps to them: `item/TransCrystalPearlItem` finds the nearest one with `ChunkGenerator#findNearestMapStructure` and throws an `entity/CrystalEye` at it, a `ThrowableItemProjectile` that steers like vanilla's eye of ender (12 blocks at a time, rising 8) and drops back as a pearl four times in five.
- **The portal.** `block/FairyPortalFrameBlock` takes a pearl (`useItemOn`), then `FairyRealm.tryOpenPortal` looks for a complete ring of twelve filled frames around a 3x3 hole (end portal layout) and fills it with `block/FairyPortalBlock`, which implements vanilla's `Portal` (`entity.setAsInsidePortal`), so travel works like the end portal for every entity. `FairyRealm.portalDestination` sends you to the Fairy Realm, remembering the portal you used (`ModAttachments.FAIRY_RETURN`), or from there back beside that portal.
- **The realm.** `dimension/fairy_realm.json` is a flat world of air whose one biome (`fairy_realm`) only places `FairyIslandFeature` islands (none within 96 blocks of 0,0). The dimension type copies the Trans Realm's (same timelines, so the same sky) with clouds at y 72, below the islands. The client treats it as "in the realm" for the heart clouds and mob colours. It has no intro (the owner found it annoying), and coming back from it doesn't replay the Trans Realm's.
- **The arena.** The first arrival (or any player arriving by other means, through `ServerEntityLevelChangeEvents`) places `structure/fairy_realm/arena_island.nbt` at (-36, 84, -36); the Trans Fairy isn't there yet. `FairyRealmState` (a level attachment) remembers that it's built, whether she's been beaten, and whether Maddie is gone. `ARENA_*` in the script and the constants in `world/FairyRealm` must agree. On peaceful the portal home opens at once.
- **The boss.** `entity/TransFairy` is a `Monster` that flies by setting its own velocity (`travel` is overridden) and runs a small state machine in `customServerAiStep`: hover (orbit around her home), volley (`TransMagicBolt`s), swoop, spikes (`entity/FairyCrystalSpike`, like evoker fangs), summon (trans endermen) and starfall, with three phases by health. The synced `ACTION` drives the model's poses. On death `FairyRealm.onFairyDefeated` opens the portal home; her loot table drops the Fairy Jar. She has no vanilla boss bar: `client/TransFairyBossBar` (a HUD element after vanilla's boss bars) finds the nearest fairy and draws her synced health as the flag, with a shimmer and sparkles. While her synced `INTRO` flag is set she only hovers and watches, can't be hurt, and the bar stays hidden.
- **Calling her: the ritual and the cutscene.** A Trans Crystal or Crystal Pearl thrown onto the `fairy_altar` (an `ItemEntity` above it, checked every 5 ticks) or used on it calls her through `FairyRealm.offerAtAltar`. Until Maddie is gone that starts `world/FairyCutscene`, a server-side timeline (not saved): letterbox and subtitles go to watching players as `FairyCutscenePayload`s (`client/FairyCutsceneOverlay` draws them), a portal swirl opens 8.5 blocks north of the altar, a cutscene-only Maddie (`Maddie#actInCutscene`, no AI) says three lines, the fairy appears in intro mode and fires one `TransMagicBolt` at her (`TransFairy#castAt`; Maddie is left on one heart so the bolt kills her, with a fallback kill 15 ticks later), `FairyRealmState.maddieGone` is set, and the fight starts. From then on every Maddie discards herself on her next tick (any in the Fairy Realm that isn't the actor does too), and offerings call the fairy straight back.
- **Rendering.** `TransFairyModel` has two layer definitions with the same parts: the solid body and a glow layer (wings and wand star) that `TransFairyGlowLayer` draws translucent and full bright. The crystal spike is drawn full bright too. The Fairy Jar's light is part of the jar's block model (a glowing cube and two wing quads, `light_emission` 15, its texture cycling pink, white and blue): it used to be a block entity renderer, which never appeared in the owner's game through rounds 4 to 6 (cutout or translucent), for reasons not found.

### How the camps work

`generate_camps.py` reads 26.3's abandoned camp templates (the cherry grove version: 10 tents, 45 shared campsites and 4 cherry grove ones, fetched from misode/mcmeta's `26.3-data` branch into `tools/vanilla_extra/structures/abandoned_camp/`). 26.3 writes palettes as `id`/`properties` instead of `Name`/`Properties`; the script rewrites them in 26.2's format with data version 4903. Three things don't exist in 26.2: white wool stairs (our light blue, pink and white wool stairs, chosen by row so the tents are flag-striped), straw beds (trans beds) and cushion entities (our cushion blocks, one block above where the entity sat; their 16 colours fold into three). The loot tables are 26.3's with its camp and treasure maps swapped for trans items. A camp is a tent (the start pool) joined to one campsite; the tents' tree jigsaws grow `trans_cherry_tree_checked` or `trans_tree_bees_checked`. Jigsaw pieces are placed with a known shape, so fence and wall connections come from the templates and beds keep `heart=none`.

### How the mermaid ruins work

`generate_mermaid_ruins.py` builds three single-piece templates (court, arch, cottage) with the paving at template y 3 and foundation below it; the structure uses `project_start_to_heightmap: OCEAN_FLOOR_WG` with `start_height` -3, so the paving replaces the top block of the sea floor. Templates contain no air, so the sea stays in every gap, and all waterloggable blocks are saved un-waterlogged: jigsaw placement's default `liquid_settings` (`apply_waterlogging`) waterlogs them wherever the sea already was, so a ruin poking out of a shallow sea doesn't leak water. The cottage's room is filled with water blocks so a slope can't bury it. The processor list cracks bricks, wears diorite and prismarine and knocks the odd block out at random. Biomes: Trans Ocean, Deep Trans Ocean, Pastel Reef.

### How sitting works

`FurnitureBlock` takes an optional seat height (pixels). Using a seat with an empty hand calls `entity/Seat.sit`: it spawns an invisible `Seat` entity on the seat's surface and the player rides it (one per block). The seat discards itself as soon as nobody rides it or the block under it stops being a seat. Players sit with their hips 0.6 above their feet (the player's vehicle attachment), so the seat entity goes exactly at the surface.

### How the plushes work

Village houses carry an invisible `plush_spot` block (a corner of the room, facing in; `generate_villages.py` puts one in 22 of the 36 house pieces). It has a ticking block entity. On its first tick the server looks up the trans village whose piece it sits in, then checks the realm level's `PlushLedger` (a persistent Fabric attachment). If that village has no plush yet, the spot turns into one, choosing among the cats handed out least so far, and the village is recorded. Otherwise the spot turns into air. So every village gets exactly one plush, in whichever house loads first, and the first nine villages give nine different cats. The ledger stores indexes into `ModBlocks.PLUSHES`: only ever append to that list.

### How the Egg House is built

`generate_egg_house.py` reads the region files straight out of `Egg House!.zip` (a 1.16.5 world), copies the house, its garden, the trees and the flag, swaps materials (`RENAME`, `LEAVES`, beds become trans beds with their `heart` set), carves an island under it and writes `structure/egg_house_island.nbt`. The template keeps the save's data version (2586) so Minecraft's data fixers upgrade the item frames, the armour stand and the lectern books when it loads; block names are written in their current form. Chests and barrels get the `egg_house` loot table. Maddie stands on the ground floor. The structure is a one-piece jigsaw 45-60 blocks above the ground at its start, `random_spread` 48/24 chunks. The script also adds a rare treasure-map pool to `chests/trans_house`.

When the island is placed, the item frames may log "Hanging entity at invalid position" once: their saved block position points at the original world. Placement moves them to the right spot anyway.

### How the mobs work

- **Trans fish** extend vanilla's `Cod` (schooling, flopping, bucketing) with their own model and bucket item.
- **Trans endermen** extend `EnderMan` without the block-carrying goals. `EnderManMixin` swaps the portal particles in `aiStep` for light blue dust (`require = 0`); `handleEntityEvent(46)` makes the teleport burst. Their glowing eyes are a second, slightly bigger head drawn full bright (`TransEndermanModel.createEyesLayer`).
- **Pastel slimes** are `TamableAnimal`s that only move by hopping: `travel` is ignored on the ground except on the tick they jump, and `jumpFromGround` pushes them towards where they're going. `squish` drives the squash-and-stretch in the model; a second model draws the translucent jelly coat. The **Bottled Fairy** saves its holder through Fabric's `ServerLivingEntityEvents.ALLOW_DEATH`, which fires just before vanilla's totem check (Fabric redirects the second `isDeadOrDying()` in `hurtServer`), so the item has no death protection component and none of the totem's green particles. `BottledFairy` checks the hands in vanilla's order (a totem in an earlier hand wins), swaps the bottle for an empty one, heals and buffs the holder, sends the sparkles and sounds from the server, and sends `FairyRescuePayload` so the saved player's client pops the Bottled Fairy up on screen like a totem. A slime keeps only a colour it was saved with: spawn eggs and `/summon` load it from data with no colour, so it rolls one on its first tick like a wild slime.

### How the wings work

Player movement is client side, so `WingsController` (client) reads the jump and sneak keys and changes the player's velocity: charge and launch, flap, softer glide, hover. It sends a `WingActionPayload` for each action. The server (`TransWings.handleAction`) checks the wings are worn, starts the glide after a launch (`tryToStartFallFlying`), plays sounds and particles, and relays a `WingFlapPayload` to players tracking the flyer so their client plays the flap animation. `RealmEvents` cancels fall and fly-into-wall damage for wearers.

The wings are an equippable chest item with the `glider` component and no equipment asset, so vanilla draws nothing. `AvatarRendererMixin` puts a `WingPose` on each player's render state (from `WingAnimations`, which eases the spread and times flaps per entity id), and `TransWingsLayer` (added to every `AvatarRenderer` through `LivingEntityRenderLayerRegistrationCallback`) draws `TransWingsModel` on the body. Since round 4 each wing is an arm bone, a hand bone and overlapping feather planes; folded, the feathers lie flat along the back without crossing the body, and a flap swings the whole wing up and down. Players wearing them don't show their cape.

### Clouds

Since 1.21.6 the cloud renderer only takes shapes from `clouds.png`; colours come from the `minecraft:visual/cloud_color` attribute. So the hearts keep coming from `HeartClouds` and the colours from the `trans_clouds` timeline, which multiplies the realm's white base colour through the flag's exact blue (`#5BCEFA`), pink (`#F5A9B8`), white and pink over five minutes. Paler tints washed out under shader packs. `extras/BSL_Trans_Realm.txt` is a BSL preset that turns off BSL's own clouds so the hearts show; its option names are from memory, not checked against BSL's source. The day timeline still dims it at night.

### How the pink deep dark and the Cloud Realm work

- **Pink sculk.** `pink_sculk_sensor`, `pink_sculk_shrieker` and `pink_sculk_vein` are vanilla's block classes registered under our ids; the sensor and shrieker share vanilla's block entity types (`ModBlocks.initialize` calls Fabric's `addValidBlock`), so they hear and shriek like vanilla's. Shriekers are only ever placed with `can_summon=false` (worldgen and players alike), so they never summon a vanilla warden or give darkness. Vanilla's catalyst would spread vanilla sculk through its sculk spreader, so `PinkSculkCatalystBlock` has no block entity: `PinkDeepDark` blooms the nearest one within 8 blocks of any non-player death in the realm and spreads pink sculk there with `PinkSculkPatchFeature.spread`, which is also the biome's (and the cities') patch feature. Textures are vanilla's sculk gradient-mapped (`pink_sculk()` in the texture script); the warden's TransRecolor textures use the same mapping, so every warden in the realm is pink. `PinkSculkParticles` replaces vanilla's shriek, vibration and sonic boom providers with ones that use the pink twin particle types' sprites while the player is in the Trans Realm.
- **The pink warden** extends `Warden`: `canTargetEntity` only accepts `Enemy` mobs (and never other wardens), player damage is ignored, `DIG_COOLDOWN` is refreshed every tick so it never digs away, and every 5 seconds it gives players within 20 blocks night vision. `PinkDeepDark` also swaps any darkness on a player in the Trans Realm for night vision (checked every other tick), which covers shriekers too. One is placed by each city centre template; others spawn as rare monsters in the biome.
- **Sculk people** are `Villager`s of their own entity type, drawn as humanoids. They take the `sculk_person` profession (no job site) on their first tick with 1 xp, so `ResetProfession` never clears it, and reset their offers' uses every 12000 ticks since they can't restock at a job site. Trades: `generate_data.py` `SCULK_PERSON_TRADES`.
- **The ritual.** Positions in `generate_ancient_city.py` (`STANDS`, `CRYSTAL`, `GATE_OPENING`) must agree with `SculkRitual` (`GATE_DISTANCE` 6 and `GATE_RISE` 4 from the crystal to the gate's middle, stands within 8 blocks round and 12 below the crystal). The portal fills the air in the gate's plane by breadth-first search from the middle, one ring every other tick; more than 400 blocks means a broken frame and it collapses. Active rituals live in memory only (a restart mid-ritual just leaves the candles lit; use the crystal to start it again).
- **The Cloud Realm** is vanilla's floating-islands noise (`tools/vanilla_extra/templates/worldgen/noise_settings_floating_islands.json`) in calcite under cloud grass and soil, with one biome (`cloud_isles`), fixed time and no monster spawns. `CloudRealm` builds the arrival cloud and its portal home at (0, 140, 0) whenever the portal block there is missing, keeps a Cloudy there, records each traveller's gate in the `cloud_return` attachment, and every 15 seconds makes each baby happy ghast near a player young again (`setBaby(true)`; vanilla's `setAgeLocked` is protected) and may float a new one in, up to three nearby. White pigs, cows and chickens are data-driven variants whose spawn condition is the Cloud Isles biome.
- **Cloudies** fly by setting their own velocity (like wild fairies). Ridden, the rider's client moves them through vanilla's ridden-entity path: `getRiddenInput` turns forward input into a move along the rider's look (pitch included).

## Generators

Run from the repository root (needs `pip install pillow nbtlib`), in this order:

1. `python3 tools/generate_textures.py`: textures, `.mcmeta` files, saliva frames, heart clouds, paintings, the advancement background and the icon.
2. `python3 tools/generate_data.py`: blockstates, models, item definitions, loot tables, recipes, block/item/entity/painting tags, painting variants, advancements (it wipes the advancement folder), `sounds.json` and `en_us.json`. It wipes and rewrites `data/minecraft/tags/` except `worldgen`, plus our block and item tags.
3. `python3 tools/generate_worldgen.py`: features, biomes (it wipes the biome folder), surface rules, noise settings, the dimension, the dimension type and the timelines. Placed features of all biomes are sorted by one global `FEATURE_RANK` list, because Minecraft crashes on inconsistent feature order. New features must be added there.
4. `python3 tools/generate_villages.py`: everything under `structure/village/trans`, `worldgen/template_pool/village/trans`, the processor lists, the village structure and structure set, the village biome tag and `#minecraft:village`.
5. `python3 tools/generate_egg_house.py`: the island template, its pool, structure, structure set, biome tag, map tag, `chests/egg_house` and the map pools (Egg House and Fairy Sanctum maps) in `chests/trans_house`.
6. `python3 tools/generate_fairy_realm.py`: the Fairy Sanctum and arena island templates, the sanctum's structure, pool, structure set, processor list, tags and `chests/fairy_sanctum`.
7. `python3 tools/generate_camps.py`: the trans camp (see "How the camps work"): `structure/trans_camp`, `worldgen/template_pool/trans_camp`, the structure, structure set, biome tag and `chests/trans_camp_{common,secret,barrel}`.
8. `python3 tools/generate_mermaid_ruins.py`: the three mermaid ruins (`structure/mermaid_ruin/`), their pool, the `mermaid_weathering` processor list, the structure, structure set, biome tag and `chests/mermaid_ruin`.
9. `python3 tools/generate_ancient_city.py`: the pink ancient city (see "How the pink deep dark and the Cloud Realm work"): `structure/pink_ancient_city/`, `worldgen/template_pool/pink_ancient_city/`, three `pink_ancient_city_*_degradation` processor lists, the structure, structure set, biome tag and `chests/pink_ancient_city(_ice_box)`. Its vanilla inputs (templates, pools, processors, loot) are in `tools/vanilla_extra/`.
10. `python3 tools/validate_resources.py /path/to/mcmeta-summary/registries/data.json`: cross-checks all of the above.

Hand-made files the scripts don't touch: the crystal tools' and armor's item definitions, item models and recipes, `equipment/trans_crystal.json`, the cake models and blockstate, `chests/trans_bakery`, most of `chests/trans_house`, and the baker trades and trade sets.

## Conventions

- Tabs, Javadoc on classes and anything non-obvious, `snake_case` ids in the `transdimension:` namespace.
- Every block needs a blockstate, an item definition (unless registered without an item), a model, a name, a loot table, mining tags and a texture made by the texture script. The validator reports any that are missing.
- Hooks into game internals fail safe: mixins use `require = 0`, and reflection is wrapped in try/catch with a disable flag.
- The owner's photos are not in the repository; only the 48x48 pixelated versions in `tools/art/` are.

## Unverified APIs (round 4)

The compiler has checked every round-4 API (see the status above). Only the mixin's injection point is left; the game checks it at startup.

| Where | API | If it doesn't work |
| --- | --- | --- |
| `EnderManMixin` | the `Level#addParticle` call in `EnderMan#aiStep` | The mixin is `require = 0`: worst case the sparkles are purple. |

## Unverified APIs (round 5)

Written after round 4 compiled. The first round-5 build failed only on `EntityType.SKELETON`/`ZOMBIE`/`SPIDER` in `TransDungeonFeature` (26.2 moved vanilla's entity types to `EntityTypes`; fixed). Round 5 has since built and run, so every row below compiles. Whether `NoiseBasedChunkGeneratorMixin` applies only shows in game: the deep lava below y -54 should be pink. Mixins: `NoiseBasedChunkGeneratorMixin` joins `EnderManMixin` in `transdimension.mixins.json`.

| Where | API | If it doesn't compile |
| --- | --- | --- |
| `TransFishRenderer` | extends vanilla's `CodRenderer(Context)` (`net.minecraft.client.renderer.entity`), overriding `createRenderState`, `extractRenderState(Cod, LivingEntityRenderState, float)` and `getTextureLocation` | Copy CodRenderer: a `MobRenderer` with `new CodModel(context.bakeLayer(ModelLayers.COD))` (`client.model.animal.fish`) and its `setupRotations` flop. |
| `PastelSlime` | `TemptGoal(mob, speed, Predicate<ItemStack>, boolean)` with a lambda | Pass `Ingredient.of(ModItems.GUMDROP)` as before (sugar then only tames, without tempting). |
| `Fairy` | `PathfinderMob`, `Mob.createMobAttributes()`, `Mob.checkMobSpawnRules`, `EntityGetter#getNearestPlayer(Entity, double)`, `Mth.rotLerp`, `Inventory#add`, `spawnAtLocation(ServerLevel, ItemStack)` (confirmed by NeoForge's Fox patch), `getSoundVolume()` | |
| `ModItems.BOTTLED_FAIRY` | `DataComponents.DEATH_PROTECTION` (confirmed) with `DeathProtection.TOTEM_OF_UNDYING` | Build the totem's effects by hand: `new DeathProtection(List.of(...))` as vanilla's totem does. |
| `FairyRenderer` | `LivingEntityRenderer#scale(S, PoseStack)`, `EntityRenderer#getBlockLightLevel(T, BlockPos)` | Drop either override: the fairy is just smaller, or lit by the world instead of glowing. |
| `TransDungeonFeature` | `RandomizableContainer.setBlockEntityLootTable(level, random, pos, key)`, `SpawnerBlockEntity#setEntityId(EntityType, RandomSource)`, `BlockTags.FEATURES_CANNOT_REPLACE`, `getMinY()` (confirmed) | It is vanilla's `MonsterRoomFeature` with other blocks: copy whatever that class does in 26.2. |
| `PinkLavaFluid`, `ModFluids` | extends `LavaFluid`, overriding `getFlowing`/`getSource`/`getBucket`/`createLegacyBlock`/`isSame`; inner `Flowing`/`Source` exactly like Fabric's 26.2 test fluids (`fabric-rendering-fluids-v1` testmod); `new LiquidBlock(fluid, properties) {}`; `BucketItem(Fluid, Properties)`, `Properties#craftRemainder` | If `LavaFluid` can't be extended, extend `FlowingFluid` like the Fabric test fluids (pink lava then won't start fires). |
| client fluid model | `FluidRenderingRegistry.register(still, flowing, new FluidModel.Unbaked(new Material(id), new Material(id), null, null))` (from Fabric's 26.2 test mod) | |
| `ModBlocks.TRANS_SEA_PICKLE` | `SeaPickleBlock::new` (a public `(Properties)` constructor) | Subclass it: `properties -> new SeaPickleBlock(properties) {}`. |
| `NoiseBasedChunkGeneratorMixin` | `@Inject` at RETURN of the static `createFluidPicker(NoiseGeneratorSettings)`; `Aquifer.FluidStatus` is a record (1.21.2 primer) | `require = 0`: worst case the deep lava (below y -54) stays orange. |
| `TransDungeonFeature`, `ModBlocks` wool stairs | 26.2 keeps dyed blocks in `ColorCollection`s: `Blocks.DYED_CANDLE.pink()`/`.lightBlue()` (the field name comes from Fabric's `BlockItemIds.DYED_CANDLE`), `Blocks.WOOL.lightBlue()` | `.pick(DyeColor.PINK)` works on any collection; if `DYED_CANDLE` is wrong, find the candle collection in `Blocks`. |
| `Seat`, `SeatRenderer` | a plain `Entity` like `FairyCrystalSpike`; `player.startRiding(Entity)` (final since 1.21.9), `isVehicle()`, `ejectPassengers()`, `EntityType.Builder.sized(0.001F, 0.001F)`; the renderer is `EntityRenderer<Seat, EntityRenderState>` returning `new EntityRenderState()` | Register vanilla's `NoopRenderer::new` instead of `SeatRenderer`. |
| `FurnitureBlock`, `TransLampBlock` | `useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)` (confirmed by NeoForge's NoteBlock patch), `Block.UPDATE_ALL`, `MapColor.SNOW` | |
| `ModBlocks` desert plants | `new CactusBlock(p) {}`, `new SugarCaneBlock(p) {}`, `new DryVegetationBlock(p) {}` (anonymous subclasses, so a protected constructor is fine; `DryVegetationBlock` is 1.21.5's rename of `DeadBushBlock`). NeoForge's 26.2 patches show both `canSurvive`s check `is(this)` plus the `supports_cactus`/`supports_sugar_cane` tags, so our own cacti and canes stack. | Register them with `::new` if the anonymous classes cause trouble. |
| `TransDirtPathBlock` | extends `DirtPathBlock` (protected constructor), overriding `getStateForPlacement` and `tick(BlockState, ServerLevel, BlockPos, RandomSource)`; `Block.pushEntitiesUp(old, new, level, pos)` | Extend `Block` and copy vanilla's `DirtPathBlock` (shape, `canSurvive`, `updateShape`). `FlattenableBlockRegistry.register(Block, BlockState)` is confirmed in Fabric's source. |

## Unverified APIs (round 9)

Written after round 8, before rounds 7 to 9 were built. Fabric's `addValidBlock`, `FabricParticleTypes.simple()`, `ParticleProviderRegistry` (and its `PendingParticleProvider`), and `ServerLivingEntityEvents.AFTER_DEATH` were read from Fabric API's 26.2 source; `BlockEntityTypes`, `EntityTypes`, trade sets, age locking (`setAgeLocked`) and the particle classes' 1.21.9 rewrite come from the NeoForge primers.

| Where | API | If it doesn't compile |
| --- | --- | --- |
| `ModBlocks` | `SculkVeinBlock::new`, `SculkSensorBlock::new`, `SculkShriekerBlock::new` (Properties constructors), `BlockEntityTypes.SCULK_SENSOR/SCULK_SHRIEKER` | Subclass them; the block entity types moved to `BlockEntityTypes` in 26.2 |
| `PinkWarden` | `Warden(EntityType<? extends Monster>, Level)`, `Warden.createAttributes()`, a public `canTargetEntity(Entity)`, `getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, long)`, `ServerLevel#getPlayers(Predicate)` | |
| `SculkPerson` | `Villager(EntityType<? extends Villager>, Level)`, `Villager.createAttributes()`, `getVillagerData().profession()`, `VillagerData#withProfession(Holder)`, `setVillagerData`, `get/setVillagerXp`, `isTrading`, `getOffers`, `MerchantOffer#resetUses`, `getBreedOffspring` returning a subtype | `withProfession` might take a registry lookup and key instead |
| `ModVillagers` | `VillagerProfession` with a `null` work sound | Pass any `SoundEvent` |
| `Cloudy` | `getControllingPassenger`, `tickRidden(Player, Vec3)`, `getRiddenInput(Player, Vec3)`, `Player.xxa/zza`, `moveRelative`, `setRot`, `Entity#resetFallDistance`, `Mth.approachDegrees`, `Mth.RAD_TO_DEG`, `Level#getMinY` | |
| `CloudRealm` | `EntityTypes.HAPPY_GHAST`, `AgeableMob#setBaby`, `Level#getEntities(EntityTypeTest, AABB, Predicate)`, `noCollision(AABB)`, `getMaxY`, `Blocks.WOOL.white()`, `Blocks.PEARLESCENT_FROGLIGHT` (all compiled in the first round-9 build). `AgeableMob#setAgeLocked` is protected, so the ghastlings are kept young with `setBaby(true)` every 15 seconds instead | |
| `PinkSculkParticles` | `SoulParticle.EmissiveProvider`, `ShriekParticle.Provider`, `VibrationSignalParticle.Provider`, `SonicBoomParticle.Provider` (each built from a `SpriteSet`) | Drop the one that fails; that effect stays cyan |
| `TransDimensionClient` | `net.minecraft.client.renderer.entity.WardenRenderer` | |
| sounds | `SCULK_CATALYST_BLOOM`, `CANDLE_PLACE`, `FLINTANDSTEEL_USE`, `BEACON_ACTIVATE`, `BEACON_POWER_SELECT`, `PORTAL_TRIGGER`, `END_PORTAL_SPAWN`, `WOOL_PLACE/HIT/BREAK` | |
| data | the Cloud Realm's `has_fixed_time` dimension type with only `#minecraft:universal` timelines (no day timeline: is its sun at noon?), the `cloud_nine` advancement's `minecraft:vehicle` entity predicate | |

## Unverified APIs (round 8)

Written after round 7, before either was built. `FairyCutscene` registers on Fabric's `ServerTickEvents.END_LEVEL_TICK` (that's its name in this Fabric API; there's no `END_WORLD_TICK`). The HUD elements use the same calls as `TransIntroOverlay`, and the payloads the same codecs as the others.

| Where | API | If it doesn't compile |
| --- | --- | --- |
| `CrystalEye` | extends `ThrowableItemProjectile` with the `(EntityType, LivingEntity, Level, ItemStack)` constructor (as `TransMagicBolt`), `onHit`/`onHitEntity`, `HitResult.Type.BLOCK`, `spawnAtLocation(ServerLevel, ItemStack)`, a public `readAdditionalSaveData(ValueInput)` override | Drop the `readAdditionalSaveData` override (an eye saved mid-flight then just flies at 0,0 for four seconds). |
| `FairyCutscene` | `EntityGetter#getNearestPlayer(x, y, z, distance, boolean)`, `Entity#setYHeadRot`, `LivingEntity.yBodyRot`, `Mob#setNoAi`, `DamageSources#indirectMagic`/`magic`, `ItemEntity#getItem`/`setItem` | |
| `TransFairy` | `EntityDataSerializers.BOOLEAN`, `getLookAngle`, `AABB#getCenter` | |
| `PastelSlime` | a `hurtServer(ServerLevel, DamageSource, float)` override (as `TransFairy`'s) to ignore falls | |
| smithing recipes | `smithing_transform` with `template` `transdimension:crystal_upgrade_smithing_template`, a plain `Item` (vanilla's `SmithingTemplateItem` constructor isn't checked). The table takes any item a recipe names as a template, but the slot shows no ghost icons and the tooltip is the item's lore. | If the template slot refuses it, check that the recipes loaded (a bad recipe drops silently) before switching to `SmithingTemplateItem`. |

## Unverified APIs (round 7)

Written after round 6's first build. `LiquidBlockMixin` joins the mixin list. The cave vines follow trans kelp's pattern (overriding `getBodyBlock`/`getHeadBlock`, which compiles); their other overrides are declared public, which compiles whether vanilla's are protected or public.

| Where | API | If it doesn't compile |
| --- | --- | --- |
| `TransCaveVinesBlock`, `TransCaveVinesPlantBlock` | extend `CaveVinesBlock`/`CaveVinesPlantBlock` (`(Properties)` constructors); `getCloneItemStack(LevelReader, BlockPos, BlockState, boolean)` (NeoForge's BlockBehaviour patch); `useWithoutItem` (confirmed); `Block.popResource(Level, BlockPos, ItemStack)`, `GameEvent.Context.of(BlockState)` (both in NeoForge's patch context); `SoundEvents.CAVE_VINES_PICK_BERRIES` (the sound `block.cave_vines.pick_berries` is in 26.2's registry) | Drop `getCloneItemStack` (pick block then gives nothing) or the sound line. |
| `ModItems.TRANS_GLOW_BERRIES` | `new BlockItem(block, properties)` with the food helper (Fabric links block items to their blocks) | |
| `BottledFairy` | `ServerLivingEntityEvents.ALLOW_DEATH` (Fabric source), `ItemStack#has(DataComponents.DEATH_PROTECTION)`, `LivingEntity#removeAllEffects`, `MobEffects.FIRE_RESISTANCE` | |
| `FairyRescuePayload` | `StreamCodec.unit(INSTANCE)` (Fabric uses it the same way) | |
| `TransDimensionClient` | `minecraft.gameRenderer.displayItemActivation(ItemStack)`, the call vanilla makes for a totem | Delete that receiver: the rescue still works, just without the pop-up. |
| `LiquidBlockMixin` | MixinExtras `@ModifyExpressionValue` on the `Blocks.OBSIDIAN` read in `LiquidBlock#shouldSpreadLiquid` (NeoForge's patch shows the method) | `require = 0`: worst case pink lava makes ordinary obsidian. |

## Unverified APIs (round 6)

Written after round 5 ran. `BaseFireBlockMixin` and `FireBlockMixin` join the mixin list. Pink fire is fire by tag (`minecraft:fire`), so it hurts, gets punched out and keeps mobs away like fire; its blockstate and models copy vanilla fire's. Burning mobs and the first-person fire overlay still use vanilla's orange flames: `ScreenEffectRenderer` takes `this.sprites.get(ModelBakery.FIRE_1)` (NeoForge's 26.2 patch), so a client mixin could swap that in the realms later. The recipe-book unlocks (`advancement/recipes/`, one per recipe, written by `generate_data.py` from the recipe folder) copy the format of vanilla 26.2's own recipe advancements. A lesson from the jar: an entity model drawn translucent inside a translucent block (the jar's glass) is hidden by it, so draw it cutout.

| Where | API | If it doesn't compile |
| --- | --- | --- |
| `PinkFireBlock` | extends `FireBlock` (public `(Properties)` constructor; compiles). Vanilla's `getIgniteOdds`/`getBurnOdds(BlockState)` are **private** (the first round-6 build said so; NeoForge's patch shows them public only through its access transformer), so `FireBlockMixin` answers them for pink fire. | |
| `BaseFireBlockMixin` | `@Inject` at RETURN of the static `BaseFireBlock.getState(BlockGetter, BlockPos)`; `Block#withPropertiesOf` | `require = 0`: if the target is wrong, fire just stays orange. |
| `FireBlockMixin` | (compiles; its targets are checked at startup) `@Inject` at HEAD of `getIgniteOdds`/`getBurnOdds(Lnet/minecraft/world/level/block/state/BlockState;)I`, the same targets as Fabric's own `FireBlockMixin`, answering from `FlammableBlockRegistry.getDefaultInstance().get(block)`; `@Inject` at RETURN of the private `getStateWithAge` (NeoForge's patch shows `this.getStateWithAge(level, pos, newAge)`), reading the age with MixinExtras' `@Local(argsOnly = true) int` | `require = 0` on all three: if a target is wrong, pink fire burns nothing (odds) or spreads a little harder (age). |
| `PastelSlime` | `ValueInput#getInt(String)` returning an `Optional` (already used by `Fairy` and `Maddie`) | |

## Unverified APIs (round 3)

These come from primers, older versions or the shape of 26.2 data, not from 26.2 source. The compiler has since confirmed the Java names; what still matters here is mixin targets, reflected names and data formats, which only show up when the game runs.

| Where | API | If it doesn't compile |
| --- | --- | --- |
| `TransBedBlock` | `BedBlock(DyeColor, Properties)`; overriding `getStateForPlacement`, `updateShape(..., ScheduledTickAccess, ...)`, `mirror` | Match the 26.2 signatures; the heart logic only needs a hook after placement and after neighbour changes. |
| `ModEntities` boats | `new Boat(EntityType, Level, Supplier<Item>)`, `new ChestBoat(...)` in `net.minecraft.world.entity.vehicle.boat` | Look at how vanilla `EntityTypes` builds `CHERRY_BOAT`. |
| `ModItems` | `new BoatItem(EntityType, Properties)`; `Equippable.builder(...).setEquipSound(...).setDamageOnHurt(false)`; `new ItemLore(List<Component>)`; `Properties#useCooldown(float)` | Compare with vanilla's elytra and boat items in `Items`. |
| `TransDimensionClient` | `new BoatRenderer(Context, ModelLayerLocation)`, `BoatModel::createBoatModel`/`createChestBoatModel` (`net.minecraft.client.model.object.boat`), `new ThrownItemRenderer<>(context, 1.25F, true)` | Use `ThrownItemRenderer::new` if the 3-argument constructor is gone. |
| `PlushSpotBlockEntity` | `StructureManager#getStructureWithPieceAt(BlockPos, HolderSet<Structure>)` | `LocationPredicate` uses the same lookup for its `structures` check. |
| `Maddie` | `Mob#setHomeTo(BlockPos, int)` (1.21.6 rename of `restrictTo`), `LookAtPlayerGoal(..., float, float)`, `WaterAvoidingRandomStrollGoal(..., double, float)`, `canBeLeashed()`, `ValueInput#getIntOr` | All are small; remove the stroll goal if needed. |
| `TransMagicBolt` | `Entity#hurtServer`, `damageSources().indirectMagic`, `new DustParticleOptions(int rgb, float scale)` | |
| `TransWings`, `WingsController` | `Player#tryToStartFallFlying()`, `KeyMapping#isDown()`, `Mth.TWO_PI`, `Mth.DEG_TO_RAD` | |
| `MaddieRenderer` | `MobRenderer` with `HumanoidModel<HumanoidRenderState>`; `CubeDeformation(float)`, `PartPose.ZERO` | |
| `MaddieDialogueScreen` | `Screen#rebuildWidgets()`, `Screen#tick()`, `SoundEvents.NOTE_BLOCK_BIT` | `extractBackground` and the 12-argument `blit` are confirmed by Fabric's test mods. |
| `TransWingsLayer` | `SubmitNodeCollector#submitModel(model, state, poseStack, renderType, light, overlay, outlineColor, null)`, `Model#renderType(Identifier)`, `EntityRenderState#isInvisible` | The `submit` signature and the mixin target are confirmed by Fabric's render layer test mod. |
| `ModBlocks` | `TallFlowerBlock::new`, `FlowerBedBlock::new`, `BonemealableFeaturePlacerBlock(ResourceKey, Properties)`, `RedStoneOreBlock::new`, `TransparentBlock::new`, `IronBarsBlock::new`, `Blocks.PINK_PETALS` | 26.2 groups dyed blocks (`Blocks.WOOL.pink()`, `Blocks.BED.red()`); none of these are dyed, but check if one is missing. |

Earlier rounds' uncertain names (`SnowyBlock`, `UntintedParticleLeavesBlock`, `AmethystClusterBlock(height, width, ...)`, `ColoredFallingBlock`, `TreeGrower`, `FlowerBlock(Holder<MobEffect>, float, ...)`, `Consumables.defaultDrink()`, `HoeItem::onlyIfAirAbove`, `ValueInput#getBooleanOr`, `SimpleSoundInstance.forUI(SoundEvent, float, float)`, `GuiGraphicsExtractor#guiWidth`, the `TextureManager#getTexture` mixin) compiled in round 2.

## Things that would be nice next

- **Compile and play-test round 4.** Look first at: the Trans Fairy fight (her orbit height, how hard the volleys and spikes hit, whether swoops feel fair), the arena island and the portal home appearing after the fight, the Fairy Sanctum's spiral staircase and how often sanctums turn up, the crystal pearl's tug, the pastel slimes' hopping, the trans enderman's eyes, the fairy jar's light, and the new wings in flight.
- Boss music for the fairy fight (vanilla only plays boss music in the End).
- Real sound effects for the Silly Cat, the Pride Oven and the wings (now vanilla sounds re-pitched).
- A Silly Cat that can be tamed or bred.
- Villages in more biomes (Pastel Peaks and the forests are left out on purpose: steep or crowded).
- Trampled farmland in villages turns back into vanilla dirt, not trans dirt.
- 26.3 has abandoned camps for 18 biomes; only the cherry grove version is ported. Others (meadow, flower forest, snowy taiga...) would only need their templates copied into `tools/vanilla_extra/structures/abandoned_camp/` and a folder entry in `generate_camps.py`.

## Building in the cloud

The Claude Code cloud environment used so far blocks these hosts, so Gradle can't run there: `maven.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`, `launchermeta.mojang.com`, and for JDK 25 `api.adoptium.net`, `download.java.net` and `cdn.azul.com`. Allowing them under the environment's network access settings (Custom, Allowed domains; see https://code.claude.com/docs/en/cloud-environments#network-access) should make `./gradlew build` possible there. Locally, any JDK 25 works.

## Licensing note

Textures and village templates are derived from Mojang's assets and are not MIT; the README says so. Keep the reference zips and `tools/vanilla_extra/` out of the built jar (they're outside `src/`, so they already are).
