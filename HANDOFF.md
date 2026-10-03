# Trans Dimension: handoff notes

State of the mod for whoever picks it up next (person or AI). The README covers what the mod does for players; this file covers how it's built and what still needs checking.

## Status

- Target: Minecraft Java **26.2**, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25. Mod id `transdimension`, package `dev.goober.transdimension`.
- **Rounds 1 and 2 compiled and loaded.** The owner ran them on 26.2 next to Sodium, Iris and Xaero's maps. The first run hung on "Preparing for world creation" because the sky timeline repeated vanilla's time markers; commit 2e490ee fixed that.
- **Round 3 hasn't been compiled.** The cloud environment can't download Gradle, Fabric's maven, Mojang's game files or a JDK 25 (see "Building in the cloud"). Every API was checked by hand against Fabric API's source, the fabric-docs reference mod for 26.2, the NeoForge 26.x porting primers and vanilla 26.2 data from misode/mcmeta. The names that couldn't be confirmed are listed under "Unverified APIs" with what to try instead.
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

### How the plushes work

Village houses carry an invisible `plush_spot` block (a corner of the room, facing in; `generate_villages.py` puts one in 22 of the 36 house pieces). It has a ticking block entity. On its first tick the server looks up the trans village whose piece it sits in, then checks the realm level's `PlushLedger` (a persistent Fabric attachment). If that village has no plush yet, the spot turns into one, choosing among the cats handed out least so far, and the village is recorded. Otherwise the spot turns into air. So every village gets exactly one plush, in whichever house loads first, and the first nine villages give nine different cats. The ledger stores indexes into `ModBlocks.PLUSHES`: only ever append to that list.

### How the Egg House is built

`generate_egg_house.py` reads the region files straight out of `Egg House!.zip` (a 1.16.5 world), copies the house, its garden, the trees and the flag, swaps materials (`RENAME`, `LEAVES`, beds become trans beds with their `heart` set), carves an island under it and writes `structure/egg_house_island.nbt`. The template keeps the save's data version (2586) so Minecraft's data fixers upgrade the item frames, the armour stand and the lectern books when it loads; block names are written in their current form. Chests and barrels get the `egg_house` loot table. Maddie stands on the ground floor. The structure is a one-piece jigsaw 45-60 blocks above the ground at its start, `random_spread` 48/24 chunks. The script also adds a rare treasure-map pool to `chests/trans_house`.

When the island is placed, the item frames may log "Hanging entity at invalid position" once: their saved block position points at the original world. Placement moves them to the right spot anyway.

### How the wings work

Player movement is client side, so `WingsController` (client) reads the jump and sneak keys and changes the player's velocity: charge and launch, flap, softer glide, hover. It sends a `WingActionPayload` for each action. The server (`TransWings.handleAction`) checks the wings are worn, starts the glide after a launch (`tryToStartFallFlying`), plays sounds and particles, and relays a `WingFlapPayload` to players tracking the flyer so their client plays the flap animation. `RealmEvents` cancels fall and fly-into-wall damage for wearers.

The wings are an equippable chest item with the `glider` component and no equipment asset, so vanilla draws nothing. `AvatarRendererMixin` puts a `WingPose` on each player's render state (from `WingAnimations`, which eases the spread and times flaps per entity id), and `TransWingsLayer` (added to every `AvatarRenderer` through `LivingEntityRenderLayerRegistrationCallback`) draws `TransWingsModel` on the body.

### Clouds

Since 1.21.6 the cloud renderer only takes shapes from `clouds.png`; colours come from the `minecraft:visual/cloud_color` attribute. So the hearts keep coming from `HeartClouds` and the colours from the `trans_clouds` timeline, which multiplies the realm's white base colour through blue, pink, white and pink over five minutes. The day timeline still dims it at night.

## Generators

Run from the repository root (needs `pip install pillow nbtlib`), in this order:

1. `python3 tools/generate_textures.py`: textures, `.mcmeta` files, saliva frames, heart clouds, paintings, the advancement background and the icon.
2. `python3 tools/generate_data.py`: blockstates, models, item definitions, loot tables, recipes, block/item/entity/painting tags, painting variants, advancements (it wipes the advancement folder), `sounds.json` and `en_us.json`. It wipes and rewrites `data/minecraft/tags/` except `worldgen`, plus our block and item tags.
3. `python3 tools/generate_worldgen.py`: features, biomes (it wipes the biome folder), surface rules, noise settings, the dimension, the dimension type and the timelines. Placed features of all biomes are sorted by one global `FEATURE_RANK` list, because Minecraft crashes on inconsistent feature order. New features must be added there.
4. `python3 tools/generate_villages.py`: everything under `structure/village/trans`, `worldgen/template_pool/village/trans`, the processor lists, the village structure and structure set, the village biome tag and `#minecraft:village`.
5. `python3 tools/generate_egg_house.py`: the island template, its pool, structure, structure set, biome tag, map tag, `chests/egg_house` and the map pool in `chests/trans_house`.
6. `python3 tools/validate_resources.py /path/to/mcmeta-summary/registries/data.json`: cross-checks all of the above.

Hand-made files the scripts don't touch: the cake models and blockstate, `equipment/trans_crystal.json`, `chests/trans_bakery`, most of `chests/trans_house`, and the baker trades and trade sets.

## Conventions

- Tabs, Javadoc on classes and anything non-obvious, `snake_case` ids in the `transdimension:` namespace.
- Every block needs a blockstate, an item definition (unless registered without an item), a model, a name, a loot table, mining tags and a texture made by the texture script. The validator reports any that are missing.
- Hooks into game internals fail safe: mixins use `require = 0`, and reflection is wrapped in try/catch with a disable flag.
- The owner's photos are not in the repository; only the 48x48 pixelated versions in `tools/art/` are.

## Unverified APIs (round 3)

These come from primers, older versions or the shape of 26.2 data, not from 26.2 source. If the build fails, look here first.

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

- **Compile and play-test round 3.** Look first at: the wings in flight (launch height, flap strength, how the hover feels), the wing model's poses and texture orientation in third person, Maddie's dialogue box layout at small GUI scales, the plush spot turning into a plush when a village loads, the Egg House island in the sky, and the trans bed heart.
- Real sound effects for the Silly Cat, the Pride Oven and the wings (now vanilla sounds re-pitched).
- A Silly Cat that can be tamed or bred.
- Villages in more biomes (Pastel Peaks and the forests are left out on purpose: steep or crowded).
- Trampled farmland in villages turns back into vanilla dirt, not trans dirt.

## Building in the cloud

The Claude Code cloud environment used so far blocks these hosts, so Gradle can't run there: `maven.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`, `launchermeta.mojang.com`, and for JDK 25 `api.adoptium.net`, `download.java.net` and `cdn.azul.com`. Allowing them under the environment's network access settings (Custom, Allowed domains; see https://code.claude.com/docs/en/cloud-environments#network-access) should make `./gradlew build` possible there. Locally, any JDK 25 works.

## Licensing note

Textures and village templates are derived from Mojang's assets and are not MIT; the README says so. Keep the reference zips and `tools/vanilla_extra/` out of the built jar (they're outside `src/`, so they already are).
