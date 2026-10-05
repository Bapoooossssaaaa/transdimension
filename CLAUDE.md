# Trans Dimension: quick handoff

Claude Code loads this file at the start of every session, so it stays short. For the details, read `HANDOFF.md` only when you need them (how the plushes, Egg House, wings, clouds, mobs and Fairy Realm work, and the tables of unverified APIs).

## The project

Fabric mod for **Minecraft Java 26.2**: a trans-flag dimension (the Trans Realm). Mod id `transdimension`, package `dev.goober.transdimension`. Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Loom 1.18, Java 25. Minecraft 26.x is unobfuscated, so it uses Mojang's names and has no mappings. `README.md` lists every feature.

- `src/main`: common code. `registry/` (ModBlocks, ModItems, ModEntities, ModAttachments...), `block/` (incl. the fairy portal, altar and jar), `entity/` (Maddie, SillyCat, TransMagicBolt, TransFish, TransEnderman, PastelSlime, TransFairy, FairyCrystalSpike, Fairy, Seat), `item/` (TransWings, TransWandItem, TransCrystalPearlItem), `network/`, `event/RealmEvents`, `world/` (PlushLedger, FairyRealm, FairyRealmState, FairyIslandFeature, HeartTreeFeature, TransCoralReefFeature), `teleport/`, `mixin/EnderManMixin`.
- `src/client`: `TransDimensionClient`, `HeartClouds`, `TransRecolor`, `TransIntroOverlay`, `wings/`, `screen/MaddieDialogueScreen`, `entity/` models and renderers, `block/FairyJarRenderer`, `mixin/` (AvatarRendererMixin, TextureManagerMixin).
- `extras/BSL_Trans_Realm.txt`: a preset for BSL Shaders (not part of the jar).

## Generated files: edit the scripts, not the JSON

Most assets and data are written by Python scripts. Change the script, rerun it, and commit both. Run them from the repository root (`pip install pillow nbtlib`):

1. `tools/generate_textures.py`: all textures (palettes are at the top of the file)
2. `tools/generate_data.py`: blockstates, models, loot, recipes, tags, advancements, lang, paintings, Maddie's dialogue
3. `tools/generate_worldgen.py`: biomes, features, the dimension, sky and cloud timelines
4. `tools/generate_villages.py`: trans villages
5. `tools/generate_egg_house.py`: Maddie's floating island
6. `tools/generate_fairy_realm.py`: the Fairy Sanctum and the Fairy Realm's arena island (its `ARENA_*` numbers must match `FairyRealm.java`)
7. `tools/generate_camps.py`: trans camps (26.3's abandoned camp, converted from `tools/vanilla_extra/structures/abandoned_camp/`)
8. `tools/generate_mermaid_ruins.py`: the mermaid ruins under the realm's seas
9. `tools/validate_resources.py <mcmeta>/registries/data.json`: must report 0 errors

You only need to rerun the script you changed (plus the validator). The validator's vanilla id check needs misode/mcmeta's 26.2 summary branch; without it, run the script with no argument.

## Building

The cloud environment can't download Minecraft or Fabric, so **the owner builds locally on Windows** (`gradlew build`) and pastes the errors. When fixing compile errors:
- Read the error, fix only that, and check `HANDOFF.md` → "Unverified APIs" for what might be wrong next.
- 26.2 names learned the hard way: `ChunkPos#pack()` (not `toLong`), no `Entity#getTags` on players (use a Fabric attachment), `LivingEntity#knockback` takes a `DamageSource`, no `Options#hideGui`, `Blocks.WOOL.pink()` / `Blocks.BED.red()` / `Blocks.DYED_CANDLE.lightBlue()` for dyed blocks (they're `ColorCollection`s), `EntityTypes.ZOMBIE` (vanilla's entity types left `EntityType`), `Screen#extractBackground(GuiGraphicsExtractor, ...)`, `minecraft.gui.setScreen`, `Player#sendOverlayMessage(Component)` / `sendSystemMessage(Component)` (no `displayClientMessage`), `RenderTypes.entityCutout` (was `entityCutoutNoCull`; the culled one is `entityCutoutCull`).
- Read from source for round 4 and confirmed by the compiler: `new ServerBossEvent(UUID, Component, color, overlay)`, `Animal#mobInteract` is public, `Block#entityInside(..., InsideBlockEffectApplier, boolean)`, `hurtMarked` (renamed only in 26.3), `ServerLevel#getStructureManager`, `RenderTypes` in `client.renderer.rendertype`.
- Data formats learned the hard way: an `exploration_map` loot function's `destination` is a plain structure tag id (`transdimension:fairy_sanctums`, no `#`). With a `#`, the whole loot table fails to load and its chests come up empty (that emptied the Egg House and village chests in round 4). The validator checks this now.
- Fabric API source, if a session has it, is the best reference. Never guess silently: say which names are unverified.

## Status (latest first)

- **Round 5 is written; its first build failed only on `EntityType.SKELETON` (now `EntityTypes`, fixed), so all of `src/main` compiles. The client hasn't been compiled yet.** Round 5 adds wild fairies and the Bottled Fairy, trans dungeons, pink lava, deepslate strata, the crystal gear back, biome-tinted grass, clay, sea pickles and gravel, trans camps (26.3's abandoned camp), mermaid ruins, white cacti and trans sugar cane, furniture you can sit on, wool stairs and slabs, and trans dirt paths. HANDOFF's "Unverified APIs (round 5)" table lists what to check when the build fails.
- **Round 4 builds**: `gradlew build` passes (the last errors were `displayClientMessage` and `entityCutoutNoCull`). Play-testing is next. Round 4 added: rebuilt wings, trans vegetation, four woods, new flowers and biomes, crystals as a rare gem, trans fish, trans endermen, pastel slime pets, and the Fairy Realm endgame (sanctums, portal, the Trans Fairy boss, the fairy jar). Mixin targets and reflection are only checked when the game starts.
- Recent work: the BSL preset; the clouds now use the flag's exact blue `#5BCEFA` and pink `#F5A9B8`.
- Round 3 (beds, boats, plushes, Maddie, wand, wings, Egg House, paintings, advancements) is written. It builds along with round 4. Nothing in round 3 has been play-tested.
- Rounds 1 and 2 compiled and ran on 26.2 next to Sodium, Iris and Xaero's maps.

## Rules

- Tabs, Javadoc on classes and anything non-obvious, `snake_case` ids.
- Every new block needs a blockstate, item definition, model, lang entry, loot table, mining tags and a texture from the texture script. The validator catches missing ones.
- Mixins use `require = 0`, and reflection is wrapped in try/catch, so a game update disables a feature instead of crashing.
- Never commit the owner's original photos; only the 48x48 versions in `tools/art/` belong in the repo.
- Work happens on branch `claude/pensive-thompson-uqkpwk` (round 4, built on `claude/nifty-turing-t1v8vk`; neither is merged into `main` yet). Don't open a pull request unless asked.
- The owner is cost-conscious: keep changes focused, avoid rereading big files, and don't run all the generators when one is enough.
