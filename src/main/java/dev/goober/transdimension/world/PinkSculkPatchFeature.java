package dev.goober.transdimension.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * A patch of pink sculk creeping over a cave's floor, walls and ceiling, fringed with pink veins, with a few sensors and
 * shriekers growing on it and now and then a catalyst in it. Vanilla's sculk patches spread through vanilla's sculk
 * spreader, which only ever places vanilla (blue) sculk, so the pink deep dark has its own.
 *
 * <p>{@link #spread} is also what a blooming pink catalyst uses to spread sculk where a creature died.
 */
public class PinkSculkPatchFeature extends Feature<NoneFeatureConfiguration> {
	private static final Map<Direction, BooleanProperty> FACES = new EnumMap<>(Map.of(
			Direction.NORTH, BlockStateProperties.NORTH, Direction.SOUTH, BlockStateProperties.SOUTH,
			Direction.EAST, BlockStateProperties.EAST, Direction.WEST, BlockStateProperties.WEST,
			Direction.UP, BlockStateProperties.UP, Direction.DOWN, BlockStateProperties.DOWN));

	public PinkSculkPatchFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		BlockPos origin = findAir(context.level(), context.origin());
		if (origin == null) {
			return false;
		}
		RandomSource random = context.random();
		return spread(context.level(), origin, random, 14 + random.nextInt(34), true) > 0;
	}

	/** An air block at {@code pos} or just above it (patches only grow in caves), or null. */
	@Nullable
	private static BlockPos findAir(LevelAccessor level, BlockPos pos) {
		for (int dy = 0; dy < 4; dy++) {
			BlockPos at = pos.above(dy);
			if (level.getBlockState(at).isAir()) {
				return at;
			}
		}
		return null;
	}

	/**
	 * Creeps pink sculk over the rock round {@code origin} (an air block): a wander through the air along the cave's
	 * surfaces, turning up to {@code budget} rock faces to sculk, then fringing the patch with veins. With
	 * {@code decorate}, a few sensors and shriekers grow on top (shriekers can't summon) and maybe one catalyst sits in it.
	 * Returns how many blocks turned to sculk.
	 */
	public static int spread(LevelAccessor level, BlockPos origin, RandomSource random, int budget, boolean decorate) {
		double reach = 3.0 + budget / 6.0;
		BlockState sculkState = ModBlocks.PINK_SCULK.defaultBlockState();
		List<BlockPos> open = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> sculk = new ArrayList<>();
		open.add(origin);
		seen.add(origin);
		int steps = 0;
		while (!open.isEmpty() && sculk.size() < budget && steps++ < budget * 12) {
			BlockPos air = open.remove(random.nextInt(open.size()));
			for (Direction direction : Direction.values()) {
				BlockPos next = air.relative(direction);
				BlockState state = level.getBlockState(next);
				if (state.is(BlockTags.SCULK_REPLACEABLE)) {
					if (random.nextFloat() < 0.8F) {
						level.setBlock(next, sculkState, Block.UPDATE_CLIENTS);
						sculk.add(next);
					}
				} else if (state.isAir() && next.closerThan(origin, reach) && touchesRock(level, next) && seen.add(next)) {
					open.add(next);
				}
			}
		}
		if (sculk.isEmpty()) {
			return 0;
		}
		if (decorate) {
			grow(level, random, sculk);
		}
		fringe(level, random, seen);
		return sculk.size();
	}

	/** Sensors and shriekers on top of the patch, and sometimes a catalyst in it. */
	private static void grow(LevelAccessor level, RandomSource random, List<BlockPos> sculk) {
		boolean catalyst = false;
		for (BlockPos pos : sculk) {
			BlockPos above = pos.above();
			if (!level.getBlockState(above).isAir()) {
				continue;
			}
			float roll = random.nextFloat();
			if (!catalyst && roll < 0.015F) {
				level.setBlock(pos, ModBlocks.PINK_SCULK_CATALYST.defaultBlockState(), Block.UPDATE_CLIENTS);
				catalyst = true;
			} else if (roll < 0.075F) {
				level.setBlock(above, ModBlocks.PINK_SCULK_SENSOR.defaultBlockState(), Block.UPDATE_CLIENTS);
			} else if (roll < 0.1F) {
				level.setBlock(above, ModBlocks.PINK_SCULK_SHRIEKER.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Veins over the bare rock next to the patch: on each face of rock that touches an air block bordering the sculk. */
	private static void fringe(LevelAccessor level, RandomSource random, Set<BlockPos> airAlongPatch) {
		BlockState plain = ModBlocks.PINK_SCULK_VEIN.defaultBlockState();
		for (BlockPos air : airAlongPatch) {
			if (random.nextFloat() > 0.45F || !level.getBlockState(air).isAir()) {
				continue;
			}
			boolean bordersSculk = false;
			BlockState vein = plain;
			for (Direction direction : Direction.values()) {
				BlockState neighbour = level.getBlockState(air.relative(direction));
				if (neighbour.is(ModBlocks.PINK_SCULK)) {
					bordersSculk = true;
				} else if (neighbour.is(BlockTags.SCULK_REPLACEABLE) && vein.hasProperty(FACES.get(direction))) {
					vein = vein.setValue(FACES.get(direction), true);
				}
			}
			if (bordersSculk && vein != plain) {
				level.setBlock(air, vein, Block.UPDATE_CLIENTS);
			}
		}
	}

	private static boolean touchesRock(LevelAccessor level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockState state = level.getBlockState(pos.relative(direction));
			if (!state.isAir() && state.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
