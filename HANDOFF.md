# Trans Dimension: handoff notes

State of the mod for whoever picks it up next (person or AI). The README covers what the mod does for players; this file covers how it's built and what still needs checking.

## Status

- Target: Minecraft Java **26.2**, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25. Mod id `transdimension`, package `dev.goober.transdimension`.
- **Rounds 1 and 2 compiled and loaded.** The owner ran them on 26.2 next to Sodium, Iris and Xaero's maps. The first run hung on "Preparing for world creation" because the sky timeline repeated vanilla's time markers; commit 2e490ee fixed that.
- **Round 3 compiles and runs**: the owner's screenshots show Maddie (in her new skin) and the wings in game.
- **Round 4 builds: `gradlew build` passes; nothing has been play-tested yet.** The first round-4 builds reported six errors in `src/main` (`Player#displayClientMessage`, which 26.2 split into `sendOverlayMessage` and `sendSystemMessage`) and two in `src/client` (`RenderTypes.entityCutoutNoCull`, renamed `entityCutout` in 26.1), all fixed. javac checks every class's names before it stops, so that confirmed every other API in rounds 3 and 4. What it can't check is mixin targets and names looked up by reflection; the game checks those at startup. Round 4 rebuilt the wings and added the new plants, woods, flowers, biomes, the three creatures and the whole Fairy Realm endgame (see "Round 4 at a glance"). The cloud environment can't download Gradle, Fabric's maven, Mojang's game files or a JDK 25 (see "Building in the cloud"). Every API was checked by hand against Fabric API's 26.2 source, NeoForge's 26.2 source patches (which quote vanilla 26.2 code around each patch), the NeoForge 26.x porting primers and vanilla 26.2 data from misode/mcmeta. The names that couldn't be confirmed are listed under "Unverified APIs (round 4)" with what to try instead; round 3's table is kept below it.
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

- **Sanctums.** `generate_fairy_realm.py` builds `structure/fairy_sanctum.nbt` (33x41x41: a shrine on top, a spiral staircase, a domed hall with the frames) and a one-piece jigsaw structure whose template y 33 lands on the first free block above the ground (`start_height` -33 with `project_start_to_heightmap`), `random_spread` 56/24 chunks, `terrain_adaptation: none`. The processor list `fairy_sanctum` fills about one frame in eight with a pearl. Maps: `#transdimension:fairy_sanctums` (Maddie's chest always, village house chests one in twelve, both written by `generate_egg_house.py`). `item/TransCrystalPearlItem` finds the nearest one with `ChunkGenerator#findNearestMapStructure` and points the way.
- **The portal.** `block/FairyPortalFrameBlock` takes a pearl (`useItemOn`), then `FairyRealm.tryOpenPortal` looks for a complete ring of twelve filled frames around a 3x3 hole (end portal layout) and fills it with `block/FairyPortalBlock`, which implements vanilla's `Portal` (`entity.setAsInsidePortal`), so travel works like the end portal for every entity. `FairyRealm.portalDestination` sends you to the Fairy Realm, remembering the portal you used (`ModAttachments.FAIRY_RETURN`), or from there back beside that portal.
- **The realm.** `dimension/fairy_realm.json` is a flat world of air whose one biome (`fairy_realm`) only places `FairyIslandFeature` islands (none within 96 blocks of 0,0). The dimension type copies the Trans Realm's (same timelines, so the same sky) with clouds at y 72, below the islands. The client treats it as "in the realm" for the heart clouds and mob colours, and plays the intro with a Fairy Realm title.
- **The arena.** The first arrival (or any player arriving by other means, through `ServerEntityLevelChangeEvents`) places `structure/fairy_realm/arena_island.nbt` at (-36, 84, -36) and calls the Trans Fairy; `FairyRealmState` (a level attachment) remembers that it's built and whether she's been beaten. `ARENA_*` in the script and the constants in `world/FairyRealm` must agree. On peaceful the portal home opens at once.
- **The boss.** `entity/TransFairy` is a `Monster` that flies by setting its own velocity (`travel` is overridden) and runs a small state machine in `customServerAiStep`: hover (orbit around her home), volley (`TransMagicBolt`s), swoop, spikes (`entity/FairyCrystalSpike`, like evoker fangs), summon (trans endermen) and starfall, with three phases by health. The synced `ACTION` drives the model's poses. On death `FairyRealm.onFairyDefeated` opens the portal home; her loot table drops the Fairy Jar. The `fairy_altar` re-summons her for a crystal pearl.
- **Rendering.** `TransFairyModel` has two layer definitions with the same parts: the solid body and a glow layer (wings and wand star) that `TransFairyGlowLayer` draws translucent and full bright. The crystal spike and the jar's light (a block entity renderer, `client/block/FairyJarRenderer`) are drawn full bright too.

### How the plushes work

Village houses carry an invisible `plush_spot` block (a corner of the room, facing in; `generate_villages.py` puts one in 22 of the 36 house pieces). It has a ticking block entity. On its first tick the server looks up the trans village whose piece it sits in, then checks the realm level's `PlushLedger` (a persistent Fabric attachment). If that village has no plush yet, the spot turns into one, choosing among the cats handed out least so far, and the village is recorded. Otherwise the spot turns into air. So every village gets exactly one plush, in whichever house loads first, and the first nine villages give nine different cats. The ledger stores indexes into `ModBlocks.PLUSHES`: only ever append to that list.

### How the Egg House is built

`generate_egg_house.py` reads the region files straight out of `Egg House!.zip` (a 1.16.5 world), copies the house, its garden, the trees and the flag, swaps materials (`RENAME`, `LEAVES`, beds become trans beds with their `heart` set), carves an island under it and writes `structure/egg_house_island.nbt`. The template keeps the save's data version (2586) so Minecraft's data fixers upgrade the item frames, the armour stand and the lectern books when it loads; block names are written in their current form. Chests and barrels get the `egg_house` loot table. Maddie stands on the ground floor. The structure is a one-piece jigsaw 45-60 blocks above the ground at its start, `random_spread` 48/24 chunks. The script also adds a rare treasure-map pool to `chests/trans_house`.

When the island is placed, the item frames may log "Hanging entity at invalid position" once: their saved block position points at the original world. Placement moves them to the right spot anyway.

### How the mobs work

- **Trans fish** extend vanilla's `Cod` (schooling, flopping, bucketing) with their own model and bucket item.
- **Trans endermen** extend `EnderMan` without the block-carrying goals. `EnderManMixin` swaps the portal particles in `aiStep` for light blue dust (`require = 0`); `handleEntityEvent(46)` makes the teleport burst. Their glowing eyes are a second, slightly bigger head drawn full bright (`TransEndermanModel.createEyesLayer`).
- **Pastel slimes** are `TamableAnimal`s that only move by hopping: `travel` is ignored on the ground except on the tick they jump, and `jumpFromGround` pushes them towards where they're going. `squish` drives the squash-and-stretch in the model; a second model draws the translucent jelly coat.

### How the wings work

Player movement is client side, so `WingsController` (client) reads the jump and sneak keys and changes the player's velocity: charge and launch, flap, softer glide, hover. It sends a `WingActionPayload` for each action. The server (`TransWings.handleAction`) checks the wings are worn, starts the glide after a launch (`tryToStartFallFlying`), plays sounds and particles, and relays a `WingFlapPayload` to players tracking the flyer so their client plays the flap animation. `RealmEvents` cancels fall and fly-into-wall damage for wearers.

The wings are an equippable chest item with the `glider` component and no equipment asset, so vanilla draws nothing. `AvatarRendererMixin` puts a `WingPose` on each player's render state (from `WingAnimations`, which eases the spread and times flaps per entity id), and `TransWingsLayer` (added to every `AvatarRenderer` through `LivingEntityRenderLayerRegistrationCallback`) draws `TransWingsModel` on the body. Since round 4 each wing is an arm bone, a hand bone and overlapping feather planes; folded, the feathers lie flat along the back without crossing the body, and a flap swings the whole wing up and down. Players wearing them don't show their cape.

### Clouds

Since 1.21.6 the cloud renderer only takes shapes from `clouds.png`; colours come from the `minecraft:visual/cloud_color` attribute. So the hearts keep coming from `HeartClouds` and the colours from the `trans_clouds` timeline, which multiplies the realm's white base colour through the flag's exact blue (`#5BCEFA`), pink (`#F5A9B8`), white and pink over five minutes. Paler tints washed out under shader packs. `extras/BSL_Trans_Realm.txt` is a BSL preset that turns off BSL's own clouds so the hearts show; its option names are from memory, not checked against BSL's source. The day timeline still dims it at night.

## Generators

Run from the repository root (needs `pip install pillow nbtlib`), in this order:

1. `python3 tools/generate_textures.py`: textures, `.mcmeta` files, saliva frames, heart clouds, paintings, the advancement background and the icon.
2. `python3 tools/generate_data.py`: blockstates, models, item definitions, loot tables, recipes, block/item/entity/painting tags, painting variants, advancements (it wipes the advancement folder), `sounds.json` and `en_us.json`. It wipes and rewrites `data/minecraft/tags/` except `worldgen`, plus our block and item tags.
3. `python3 tools/generate_worldgen.py`: features, biomes (it wipes the biome folder), surface rules, noise settings, the dimension, the dimension type and the timelines. Placed features of all biomes are sorted by one global `FEATURE_RANK` list, because Minecraft crashes on inconsistent feature order. New features must be added there.
4. `python3 tools/generate_villages.py`: everything under `structure/village/trans`, `worldgen/template_pool/village/trans`, the processor lists, the village structure and structure set, the village biome tag and `#minecraft:village`.
5. `python3 tools/generate_egg_house.py`: the island template, its pool, structure, structure set, biome tag, map tag, `chests/egg_house` and the map pools (Egg House and Fairy Sanctum maps) in `chests/trans_house`.
6. `python3 tools/generate_fairy_realm.py`: the Fairy Sanctum and arena island templates, the sanctum's structure, pool, structure set, processor list, tags and `chests/fairy_sanctum`.
7. `python3 tools/validate_resources.py /path/to/mcmeta-summary/registries/data.json`: cross-checks all of the above.

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

## Building in the cloud

The Claude Code cloud environment used so far blocks these hosts, so Gradle can't run there: `maven.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`, `launchermeta.mojang.com`, and for JDK 25 `api.adoptium.net`, `download.java.net` and `cdn.azul.com`. Allowing them under the environment's network access settings (Custom, Allowed domains; see https://code.claude.com/docs/en/cloud-environments#network-access) should make `./gradlew build` possible there. Locally, any JDK 25 works.

## Licensing note

Textures and village templates are derived from Mojang's assets and are not MIT; the README says so. Keep the reference zips and `tools/vanilla_extra/` out of the built jar (they're outside `src/`, so they already are).
