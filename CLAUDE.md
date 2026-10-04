# Trans Dimension: quick handoff

Claude Code loads this file at the start of every session, so it stays short. For the details, read `HANDOFF.md` only when you need them (how the plushes, Egg House, wings and clouds work, and the table of unverified APIs).

## The project

Fabric mod for **Minecraft Java 26.2**: a trans-flag dimension (the Trans Realm). Mod id `transdimension`, package `dev.goober.transdimension`. Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Loom 1.18, Java 25. Minecraft 26.x is unobfuscated, so it uses Mojang's names and has no mappings. `README.md` lists every feature.

- `src/main`: common code. `registry/` (ModBlocks, ModItems, ModEntities, ModAttachments...), `block/`, `entity/` (Maddie, SillyCat, TransMagicBolt), `item/` (TransWings, TransWandItem), `network/`, `event/RealmEvents`, `world/PlushLedger`, `teleport/`.
- `src/client`: `TransDimensionClient`, `HeartClouds`, `TransRecolor`, `wings/`, `screen/MaddieDialogueScreen`, `entity/` renderers, `mixin/` (AvatarRendererMixin, TextureManagerMixin).
- `extras/BSL_Trans_Realm.txt`: a preset for BSL Shaders (not part of the jar).

## Generated files: edit the scripts, not the JSON

Most assets and data are written by Python scripts. Change the script, rerun it, and commit both. Run them from the repository root (`pip install pillow nbtlib`):

1. `tools/generate_textures.py`: all textures (palettes are at the top of the file)
2. `tools/generate_data.py`: blockstates, models, loot, recipes, tags, advancements, lang, paintings, Maddie's dialogue
3. `tools/generate_worldgen.py`: biomes, features, the dimension, sky and cloud timelines
4. `tools/generate_villages.py`: trans villages
5. `tools/generate_egg_house.py`: Maddie's floating island
6. `tools/validate_resources.py <mcmeta>/registries/data.json`: must report 0 errors

You only need to rerun the script you changed (plus the validator). The validator's vanilla id check needs misode/mcmeta's 26.2 summary branch; without it, run the script with no argument.

## Building

The cloud environment can't download Minecraft or Fabric, so **the owner builds locally on Windows** (`gradlew build`) and pastes the errors. When fixing compile errors:
- Read the error, fix only that, and check `HANDOFF.md` → "Unverified APIs" for what might be wrong next.
- 26.2 names learned the hard way: `ChunkPos#pack()` (not `toLong`), no `Entity#getTags` on players (use a Fabric attachment), `LivingEntity#knockback` takes a `DamageSource`, no `Options#hideGui`, `Blocks.WOOL.pink()` / `Blocks.BED.red()` for dyed blocks, `Screen#extractBackground(GuiGraphicsExtractor, ...)`, `minecraft.gui.setScreen`.
- Fabric API source, if a session has it, is the best reference. Never guess silently: say which names are unverified.

## Status (latest first)

- Recent work: the BSL preset; the clouds now use the flag's exact blue `#5BCEFA` and pink `#F5A9B8`.
- Round 3 (beds, boats, plushes, Maddie, wand, wings, Egg House, paintings, advancements) is written. The **main source set compiles**. The **client source set** got past one error (`hideGui`) and may still have more. Nothing in round 3 has been play-tested.
- Rounds 1 and 2 compiled and ran on 26.2 next to Sodium, Iris and Xaero's maps.

## Rules

- Tabs, Javadoc on classes and anything non-obvious, `snake_case` ids.
- Every new block needs a blockstate, item definition, model, lang entry, loot table, mining tags and a texture from the texture script. The validator catches missing ones.
- Mixins use `require = 0`, and reflection is wrapped in try/catch, so a game update disables a feature instead of crashing.
- Never commit the owner's original photos; only the 48x48 versions in `tools/art/` belong in the repo.
- Work happens on branch `claude/nifty-turing-t1v8vk` (not merged into `main` yet). Don't open a pull request unless asked.
- The owner is cost-conscious: keep changes focused, avoid rereading big files, and don't run all the generators when one is enough.
