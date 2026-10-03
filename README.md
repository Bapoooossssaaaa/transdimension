# Trans Dimension 🏳️‍⚧️

A Fabric mod for **Minecraft Java 26.2** that adds the **Trans Realm**, a whole dimension in the colours of the trans flag.

**Say `Goober` in chat to go there. Say it again to come back.** You can also use `/goober`.

## What's inside

- **The Trans Realm.** Overworld-style hills, caves and cliffs, built from trans grass, dirt, stone and sand. It has four biomes: Trans Meadow, Trans Forest, Trans Beach and Trans Ocean.
- **Trans Water oceans.** A custom fluid with animated pink/blue/white wave frames. It behaves like water: you can swim in it, drown in it, sail boats on it and fill buckets with it. It also has pink underwater fog.
- **Gradient sky.** Trans blue overhead, fading to trans pink at the horizon, with pink-tinted clouds and the dreamy cherry-grove music.
- **Heart-shaped clouds.** These appear only inside the realm; the normal clouds come back when you leave.
- **Retextured mobs.** Every mob in the realm, passive and hostile, villagers included, is redrawn in the trans palette while keeping its face and details.
- **Trans villages.** Each has a fountain of Trans Water, a giant trans flag, a bell and trans-flag roofs. There's a **bakery** with Pride Ovens, trans cakes and chests full of treats, plus cottages with beds and job sites.
- **The Trans Baker.** A villager profession whose job site is the Pride Oven. Bakers trade cookies, donuts, cupcakes, macarons, boba, cakes, and eventually crystals.
- **Trans Crystal.** Crystal ore glows faintly underground. It makes a sword, pickaxe, axe, shovel, hoe and a full armor set, all slightly better than diamond.
- **Treats:**

  | Treat | Effect |
  | --- | --- |
  | Trans Donut | Absorption |
  | Sprinkle Cookie | Regeneration |
  | Pride Cupcake | Luck |
  | Trans Macaron | Night Vision |
  | Trans Boba Tea | Dolphin's Grace; returns the bottle |
  | Trans Cake | Placeable and eaten slice by slice, like vanilla cake |

- **Trans trees and Pride Blossoms.** Trees have white logs with blue and pink leaves. Pride Blossoms are the sprinkle ingredient for every treat recipe.
- **Intro animation.** On arrival, the flag stripes sweep across the screen, a heart pulses over "Welcome to the Trans Realm", and a little bell jingle plays.
- **An advancement**, "Goober!", for entering the realm.

## Playing

1. Install **Fabric Loader 0.19.5+** for Minecraft 26.2 and **Fabric API**.
2. Put the built jar into your `mods` folder. It needs to be on both the client and any server.
3. Load any world, new or existing, and type **Goober** in chat.

Some tips:
- Villages spawn in **Trans Meadows**.
- Crystal ore appears in trans stone from deep underground up to y≈128.
- Everything is in the **Trans Dimension** creative tab.

## Building from source

You need **JDK 25**.

```bash
./gradlew build      # jar ends up in build/libs/
./gradlew runClient  # launches a dev client with the mod
```

The toolchain matches the official 26.2 template: Loom 1.18, Gradle 9.7.1, Fabric API 0.161.0+26.2. Minecraft 26.x is unobfuscated, so there is no mappings line.

## Customising

- **Textures.** Every texture is original pixel art made by `tools/generate_textures.py`. Edit the palette at the top and run `python3 tools/generate_textures.py` (needs Pillow) to restyle the whole mod.
- **Sky colours.** Edit the attributes in `data/transdimension/dimension_type/trans_realm.json` and the biome files.
- **Terrain.** Edit `data/transdimension/worldgen/`.
- **Baker trades.** Edit `data/transdimension/villager_trade/` and `data/transdimension/trade_set/`.

## Notes and troubleshooting

This was written against the real 26.2 APIs, checked against Fabric API 0.161.0's source, the Fabric docs reference mod, NeoForge's 26.2 code, and vanilla 26.2 data. It has not been compiled against Minecraft yet. If the first build reports an error, it will most likely be one of these few names that I couldn't check against code:

- `Consumables.defaultDrink()` and `Item.Properties#usingConvertsTo` (boba)
- `ColoredFallingBlock` / `ColorRGBA` (trans sand)
- `UntintedParticleLeavesBlock` (leaves)
- `SoundEvents.VILLAGER_WORK_BUTCHER` (baker work sound; replacing it with `null` is fine)

Two features hook into game internals and are built to fail safely:

- **Heart clouds** re-run the vanilla cloud loader with a redirected texture. If that ever stops matching, the log says "Could not swap in the heart clouds" and you get normal clouds.
- **Mob recolouring** hooks `TextureManager#getTexture` with `require = 0`. If it doesn't match, mobs keep their normal look.

## License

MIT. See `LICENSE`.
