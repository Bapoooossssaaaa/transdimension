# Trans Dimension 🏳️‍⚧️

A Fabric mod for **Minecraft Java 26.2** that adds the **Trans Realm**, a whole dimension in the colours of the trans flag.

**Say `Goober` in chat to go there. Say it again to come back.** You can also use `/goober`.

## What's inside

### The Trans Realm

Overworld-style hills, caves, rivers and oceans, built from trans grass, dirt, stone and sand, with thirteen biomes:

| Biome | What it's like |
| --- | --- |
| Trans Meadow | Rolling pink grassland with scattered trans trees, Pride Blossoms, flowers and little boulders. Villages grow here. |
| Blossom Forest | A dense forest of trans and cherry-style trees with carpets of Pride Blossoms and drifting petals. |
| Heartwood Grove | Big heartwood trees hung with lanterns, warm pink light and fireflies. |
| Sugar Dunes | Pale pink sand dunes, sandstone underneath, the odd crystal spike and camels. Villages grow here too. |
| Lavender Marsh | Purple-tinted wetland with pools, vine-draped marsh trees, lavender flowers and frogs. |
| Frosted Fields | Snowy pale-blue plains with frosted trees, ice spikes and frost flowers. Villages grow here too. |
| Crystal Grove | Blue grass, trans crystal spikes and clusters breaking through the surface, crystal-hung trees. |
| Pastel Peaks | Snow-capped mountains with lilac grass. |
| Trans Beach, Trans Ocean, Deep Trans Ocean | Blue water, dolphins and drowned. |
| Pastel River | Rivers of pink water. |
| Crystal Caves | Deep caves lined with trans crystal clusters and geodes. |

- **Water** is vanilla water recoloured per biome (blue seas, pink rivers, lavender crystal pools).
- **Sky.** Its own day/night cycle: trans blue skies, pink fog, tinted clouds, pink-and-blue sunrises and sunsets, and brighter stars at night.
- **Heart-shaped clouds.** These appear only inside the realm; the normal clouds come back when you leave.
- **Retextured mobs.** Every mob in the realm, passive and hostile, villagers included, is redrawn in the trans palette, and sheep grow trans-flag wool.

### The Silly Cat

A big-eyed, tongue-out goober that wanders most land biomes and trans villages. It trots up to you and gives you a big lick: that heals a heart and a half and leaves your screen covered in cat spit for a few seconds (the **Slobbered** effect). Pet one with an empty hand and it purrs. Some of them always keep the tip of their tongue out. They follow you if you hold fish, Sprinkle Cookies or Trans Donuts, and they never despawn.

### Trans villages

Built from Minecraft's own plains village pieces, rebuilt in trans blocks: striped plank roofs, white trans log frames, pink and blue cobblestone, flag-coloured windows, pink and blue beds, pastel brick roads. Inside are **Trans Tables and Trans Chairs**, trans flag rugs, lanterns and flowers. Every village centre is a fountain, a market or a **flag plaza** flying a giant trans flag, and lantern posts and trans-flag banners line the streets. Both butcher shops became **bakeries** with a Pride Oven, a cake on the counter and a chest of treats. The village cats are mostly Silly Cats.

Villages are spaced so they can never grow into each other.

### Blocks and items

- **Building sets:** trans stone, cobblestone, stone bricks (cracked and chiseled too) and sandstone (cut and chiseled too), each with stairs, slabs and walls where vanilla has them. There's a full **trans wood set**: logs, wood, stripped logs and wood, planks, stairs, slab, fence, fence gate, door, trapdoor, button, pressure plate, leaves and sapling.
- **Trans glass:** flag-striped, pink and blue stained glass, each with a pane.
- **Trans wool** and **trans carpet**, **Trans Lanterns**, **Trans Chairs** and **Trans Tables**.
- **Trans Crystal.** Crystal ore glows faintly underground and in Crystal Groves. Crystal blocks and clusters glow too. Crystals make a sword, pickaxe, axe, shovel, hoe and a full armor set, all slightly better than diamond.
- **The Trans Baker.** A villager profession whose job site is the Pride Oven. Bakers trade cookies, donuts, cupcakes, macarons, boba, cakes, crystals and lanterns.
- **Treats:**

  | Treat | Effect |
  | --- | --- |
  | Trans Donut | Absorption |
  | Sprinkle Cookie | Regeneration |
  | Pride Cupcake | Luck |
  | Trans Macaron | Night Vision |
  | Trans Boba Tea | Dolphin's Grace; returns the bottle |
  | Trans Cake | Placeable and eaten slice by slice, like vanilla cake |

- **Pride Blossoms** are the sprinkle ingredient for every treat recipe and make pink dye.
- **Intro cinematic.** On arrival, a starry indigo screen unfurls a waving trans flag, a heart pops in with sparkles, "Welcome to the Trans Realm" types itself out, and a little bell jingle plays.
- **Advancements:** "Goober!" for entering the realm, "Big Smooch" for getting licked and "Home Sweet Home" for finding a trans village.

## Playing

1. Install **Fabric Loader 0.19.5+** for Minecraft 26.2 and **Fabric API**.
2. Put the built jar into your `mods` folder. It needs to be on both the client and any server.
3. Load any world, new or existing, and type **Goober** in chat.

Some tips:
- Villages spawn in **Trans Meadows**, **Frosted Fields** and **Sugar Dunes**. `/locate structure transdimension:trans_village` finds the nearest one.
- Crystal ore appears in trans stone from deep underground up to y≈128.
- Everything is in the **Trans Dimension** creative tab.

## Building from source

You need **JDK 25**.

```bash
./gradlew build      # jar ends up in build/libs/
./gradlew runClient  # launches a dev client with the mod
```

The toolchain matches the official 26.2 template: Loom 1.18, Gradle 9.7.1, Fabric API 0.161.0+26.2. Minecraft 26.x is unobfuscated, so there is no mappings line.

## Generated content

Most of the assets and data are written by scripts in `tools/` (Python 3, with `pip install pillow nbtlib`). Run them from the repository root, in this order:

| Script | What it writes |
| --- | --- |
| `generate_textures.py` | Every block, item, GUI and entity texture. It gradient-maps Minecraft's own textures (from `base block textures.zip`, `Base Sprite Images.zip` and `tools/vanilla_extra/`) onto trans palettes so everything keeps the vanilla feel. |
| `generate_data.py` | Blockstates, models, item definitions, loot tables, recipes, tags, `sounds.json` and the English names. |
| `generate_worldgen.py` | Features, biomes, surface rules, noise settings, the dimension and the sky timeline. |
| `generate_villages.py` | The trans village: structure templates (converted from the vanilla plains village), template pools, processors, the structure and its structure set. |
| `validate_resources.py` | Checks that everything above points at things that exist. Pass `mcmeta-summary/registries/data.json` from [misode/mcmeta](https://github.com/misode/mcmeta) (26.2 summary branch) to check vanilla ids too. |

To restyle the mod, edit the palettes at the top of `generate_textures.py`, the biome table in `generate_worldgen.py` or the block swaps in `generate_villages.py`, then rerun the scripts. Baker trades live in `data/transdimension/villager_trade/` and `data/transdimension/trade_set/`.

## Notes and troubleshooting

This was written against the real 26.2 APIs and checked against Fabric API 0.161.0's source, the Fabric docs reference mod, the 26.x porting primers and vanilla 26.2 data. **It has not been compiled yet.** If the first build fails, it will most likely be one of these names, which I couldn't check against code (`HANDOFF.md` lists the fixes to try):

- `SnowyBlock` (trans grass), `UntintedParticleLeavesBlock` (leaves), `AmethystClusterBlock(height, width, …)` (crystal cluster), `ColoredFallingBlock` / `ColorRGBA` (trans sand), `FlowerBlock(Holder<MobEffect>, float, …)` (Pride Blossom)
- `Consumables.defaultDrink()` and `Item.Properties#usingConvertsTo` (boba)
- `HoeItem::onlyIfAirAbove` (tilling trans dirt)
- `ValueInput#getBooleanOr` (Silly Cat save data)
- `SimpleSoundInstance.forUI(SoundEvent, float, float)` and `GuiGraphicsExtractor#guiWidth/guiHeight` (intro and spit overlay)

Two features hook into game internals and are built to fail safely:

- **Heart clouds** re-run the vanilla cloud loader with a redirected texture. If that ever stops matching, the log says "Could not swap in the heart clouds" and you get normal clouds.
- **Mob recolouring** hooks `TextureManager#getTexture` with `require = 0`. If it doesn't match, mobs keep their normal look.

## License

The code is MIT; see `LICENSE`. The textures and village structure templates are derived from Minecraft's own assets (© Mojang Studios / Microsoft), so they aren't covered by the MIT license; they're shared under Mojang's usage guidelines like any resource pack.
