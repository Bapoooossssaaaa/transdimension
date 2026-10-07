package dev.goober.transdimension.client.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BaseCoralFanBlock;
import net.minecraft.world.level.block.BaseCoralPlantBlock;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.FireflyBushBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.ShortDryGrassBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallDryGrassBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import dev.goober.transdimension.TransDimension;

/**
 * Makes the mod work with shader packs under Iris, without depending on Iris: two optional mixins call in here, and
 * only when Iris is installed (IrisWorldRenderingSettingsMixin, IrisIncludeGraphMixin). Written against Iris 1.11.4
 * and BSL 10.1.8, read from their files.
 *
 * <p><b>Block ids.</b> A shader pack only knows vanilla blocks. Its block.properties gives vanilla blocks (or block
 * states) numbers, and its shaders use them to wave plants and leaves, make lava, fire and lamps glow, give water waves
 * and reflections, treat glass as glass and so on. None of our blocks were listed, so they all got nothing: still leaves,
 * dull lava, flat holy water. Just before Iris stores a pack's block id map, {@link #addTwins} gives each of our block
 * states the id of its vanilla twin (our leaves get oak leaves', our flowers a poppy's, pink lava lava's, holy water
 * water's), with the properties matched up, so a tall flower's top half gets a sunflower's top half's id and a lit lamp
 * a lit lamp's. A block the pack lists itself keeps the pack's id. Our blocks glow under a pack exactly where their
 * vanilla twins do (lava, fire, lamps, lanterns, sea lanterns, glow berries, amethyst clusters, the Sky Portal), and
 * blocks whose twins don't glow (crystal and prism blocks, like amethyst blocks; holy gold, like gold) get plain ids. This
 * works with any pack that maps vanilla blocks, not just BSL.
 *
 * <p><b>Clouds.</b> With BSL's clouds set to Vanilla, Iris draws the game's clouds, shapes and colour, but BSL's cloud
 * shader only keeps their alpha and lights them with its own sunlight colour, so the realm's heart clouds came out
 * plain. {@link #patchShaderFile} adds one line to that shader as Iris reads it, tinting the clouds with the game's cloud
 * colour (its hue only; BSL still does the light and shade). The Overworld's clouds are white, so they don't change.
 *
 * <p><b>Glowing mobs.</b> Packs such as BSL light a full-bright entity at nearly twice its block light, which blows pale
 * glowing mobs (fairies, angels) out to flat white. {@link #glowLight()} is the light level they're drawn at: full bright
 * without shaders, a little less with a pack on (asked through Iris's public API, by reflection).
 */
public final class ShaderCompat {
	/** The block light glowing mobs get while a shader pack is in use. */
	private static final int SHADER_GLOW_LIGHT = 13;
	/** Iris's {@code IrisApi.getInstance().isShaderPackInUse()}, or null without Iris. */
	@Nullable
	private static final MethodHandle SHADERS_IN_USE = findShadersInUse();

	/** Twins by our block's id, for blocks whose class doesn't say what they are. Vanilla ids. */
	private static final Map<String, String> TWINS_BY_ID = Map.ofEntries(
			Map.entry("pink_lava", "lava"),
			Map.entry("holy_water", "water"),
			// The Sky Portal is see-through, like a nether portal, which packs draw glowing (BSL adds depth).
			Map.entry("sky_portal", "nether_portal"),
			Map.entry("fairy_jar", "glass"),
			Map.entry("trans_sea_lantern", "sea_lantern"),
			// a floating crystal, glowing like an amethyst cluster (which packs draw glowing too)
			Map.entry("ritual_crystal", "amethyst_cluster"),
			Map.entry("trans_lamp", "redstone_lamp"),
			Map.entry("pink_sculk_catalyst", "sculk_catalyst"));
	/** The cloud program's line the tint goes after, in BSL (program/gbuffers_clouds.glsl). */
	private static final String BSL_CLOUD_LINE = "albedo.rgb = pow(albedo.rgb,vec3(2.2));";
	private static final String CLOUD_TINT = "\talbedo.rgb *= pow(color.rgb / max(max(color.r, color.g), max(color.b, 0.001)), vec3(2.2));"
			+ " // Trans Dimension: the game's cloud colour";

	private ShaderCompat() {
	}

	/** Whether Iris is drawing with a shader pack right now (always false without Iris). */
	public static boolean shadersInUse() {
		if (SHADERS_IN_USE == null) {
			return false;
		}
		try {
			return (boolean) SHADERS_IN_USE.invoke();
		} catch (Throwable e) {
			return false;
		}
	}

	/** The block light a glowing mob is drawn at: 15 without shaders, {@link #SHADER_GLOW_LIGHT} with a pack on. */
	public static int glowLight() {
		return shadersInUse() ? SHADER_GLOW_LIGHT : 15;
	}

	@Nullable
	private static MethodHandle findShadersInUse() {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			Method inUse = api.getMethod("isShaderPackInUse");
			return MethodHandles.publicLookup().unreflect(inUse).bindTo(instance);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}

	/**
	 * Gives our block states their vanilla twins' ids in a shader pack's block id map (Iris's, from block.properties),
	 * before Iris stores it. Never throws: a failure only leaves our blocks without ids, as before.
	 */
	public static void addTwins(Object2IntMap<BlockState> ids) {
		try {
			Map<String, Block> vanilla = new HashMap<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(block);
				if ("minecraft".equals(id.getNamespace())) {
					vanilla.put(id.getPath(), block);
				}
			}
			int added = 0;
			for (Block block : BuiltInRegistries.BLOCK) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(block);
				if (!TransDimension.MOD_ID.equals(id.getNamespace())) {
					continue;
				}
				String twinName = twinName(id.getPath(), block);
				Block twin = twinName == null ? null : vanilla.get(twinName);
				for (BlockState state : block.getStateDefinition().getPossibleStates()) {
					if (ids.containsKey(state)) {
						continue;
					}
					BlockState twinState = twin == null ? null : twinState(state, twin);
					if (twinState != null && ids.containsKey(twinState)) {
						ids.put(state, ids.getInt(twinState));
						added++;
					}
				}
			}
			TransDimension.LOGGER.info("Shader pack block ids: gave {} block states of ours their vanilla twins' ids", added);
		} catch (RuntimeException | LinkageError e) {
			TransDimension.LOGGER.warn("Couldn't give our blocks shader pack ids; they'll look plain under shaders", e);
		}
	}

	/** The vanilla block (by id) a block of ours should look like to a shader pack, or null for none. */
	@Nullable
	private static String twinName(String path, Block block) {
		String byId = TWINS_BY_ID.get(path);
		if (byId != null) {
			return byId;
		}
		// Most specific classes first: a tall flower is a double plant, a firefly bush and a dry grass are bushes.
		if (block instanceof BaseFireBlock) {
			return "fire";
		}
		if (block instanceof LeavesBlock) {
			return "oak_leaves";
		}
		if (block instanceof SaplingBlock) {
			return "oak_sapling";
		}
		if (block instanceof TallFlowerBlock) {
			return "sunflower";
		}
		if (block instanceof TallSeagrassBlock) {
			return "tall_seagrass";
		}
		if (block instanceof DoublePlantBlock) {
			return "tall_grass";
		}
		if (block instanceof FlowerBlock) {
			return "poppy";
		}
		if (block instanceof FlowerBedBlock) {
			return "pink_petals";
		}
		if (block instanceof FireflyBushBlock) {
			return "firefly_bush";
		}
		if (block instanceof ShortDryGrassBlock) {
			return "short_dry_grass";
		}
		if (block instanceof TallDryGrassBlock) {
			return "tall_dry_grass";
		}
		if (block instanceof DryVegetationBlock) {
			return "dead_bush";
		}
		if (block instanceof BushBlock) {
			return "bush";
		}
		if (block instanceof TallGrassBlock) {
			return "short_grass";
		}
		if (block instanceof SeagrassBlock) {
			return "seagrass";
		}
		if (block instanceof KelpBlock) {
			return "kelp";
		}
		if (block instanceof KelpPlantBlock) {
			return "kelp_plant";
		}
		if (block instanceof LilyPadBlock) {
			return "lily_pad";
		}
		if (block instanceof CaveVinesBlock) {
			return "cave_vines";
		}
		if (block instanceof CaveVinesPlantBlock) {
			return "cave_vines_plant";
		}
		if (block instanceof SugarCaneBlock) {
			return "sugar_cane";
		}
		if (block instanceof BaseCoralWallFanBlock) {
			return "tube_coral_wall_fan";
		}
		if (block instanceof BaseCoralFanBlock) {
			return "tube_coral_fan";
		}
		if (block instanceof BaseCoralPlantBlock) {
			return "tube_coral";
		}
		if (block instanceof SeaPickleBlock) {
			return "sea_pickle";
		}
		if (block instanceof AmethystClusterBlock) {
			return "amethyst_cluster";
		}
		if (block instanceof LanternBlock) {
			return "lantern";
		}
		if (block instanceof WallTorchBlock) {
			return "wall_torch";
		}
		if (block instanceof TorchBlock) {
			return "torch";
		}
		if (block instanceof SlimeBlock) {
			return "slime_block";
		}
		if (block instanceof SculkSensorBlock) {
			return "sculk_sensor";
		}
		if (block instanceof SculkShriekerBlock) {
			return "sculk_shrieker";
		}
		if (block instanceof RedStoneOreBlock) {
			return "redstone_ore";
		}
		if (path.endsWith("_ore")) {
			return "diamond_ore";
		}
		if (path.contains("glass")) {
			return block instanceof IronBarsBlock ? "glass_pane" : "glass";
		}
		return null;
	}

	/** The twin's state with as many of ours's property values as it shares (by property name and value name). */
	private static BlockState twinState(BlockState ours, Block twin) {
		BlockState state = twin.defaultBlockState();
		for (Property<?> property : ours.getBlock().getStateDefinition().getProperties()) {
			Property<?> theirs = twin.getStateDefinition().getProperty(property.getName());
			if (theirs != null) {
				state = with(state, theirs, valueName(ours, property));
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}

	/**
	 * Patches a shader pack file as Iris reads it, or returns null to leave it alone. Only BSL's vanilla cloud program is
	 * patched (see the class comment), and only if it still reads the way it did in BSL 10.1.8.
	 */
	@Nullable
	public static String patchShaderFile(@Nullable Path path, @Nullable String source) {
		try {
			if (path == null || source == null || path.getFileName() == null
					|| !"gbuffers_clouds.glsl".equals(path.getFileName().toString())) {
				return null;
			}
			int at = source.indexOf(BSL_CLOUD_LINE);
			if (at < 0 || !source.contains("CLOUD_BRIGHTNESS") || !source.contains("varying vec4 color;") || source.contains("Trans Dimension")) {
				return null;
			}
			int end = at + BSL_CLOUD_LINE.length();
			TransDimension.LOGGER.info("Shader pack: tinting the pack's vanilla clouds with the game's cloud colour ({})", path);
			return source.substring(0, end) + "\n" + CLOUD_TINT + source.substring(end);
		} catch (RuntimeException e) {
			TransDimension.LOGGER.warn("Couldn't patch the shader pack's clouds", e);
			return null;
		}
	}
}
