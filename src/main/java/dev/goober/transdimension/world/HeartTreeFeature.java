package dev.goober.transdimension.world;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * A heart tree: a blushwood trunk whose crown is a big, puffy heart of blush leaves dotted with flowering ones. The heart
 * stands upright, facing along a random axis, with log branches reaching into both lobes. It grows in the Pride
 * Flower Fields and from a blush sapling planted near flowers.
 *
 * <p>Leaves get their real distance to the nearest log (like vanilla trees), so they decay normally when the tree is
 * cut down; any leaf further than six blocks from a log is simply left out.
 */
public class HeartTreeFeature extends Feature<NoneFeatureConfiguration> {
	/** The heart, top row first: 13 wide and 11 tall; its point sits on top of the trunk. */
	private static final String[] HEART = {
			"..###...###..",
			".#####.#####.",
			"#############",
			"#############",
			"#############",
			".###########.",
			"..#########..",
			"...#######...",
			"....#####....",
			".....###.....",
			"......#......",
	};
	private static final int HEART_WIDTH = HEART[0].length();
	private static final int HEART_HEIGHT = HEART.length;
	/** Like vanilla's trees: update clients, and skip shape updates (the leaf distances are already right). */
	private static final int PLACE_FLAGS = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;

	public HeartTreeFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		BlockState ground = level.getBlockState(origin.below());
		if (!(ground.is(BlockTags.DIRT) || ground.is(ModBlocks.TRANS_GRASS_BLOCK) || ground.is(ModBlocks.TRANS_MOSS_BLOCK))) {
			return false;
		}
		int trunk = 4 + random.nextInt(2);
		boolean alongX = random.nextBoolean();
		Direction across = alongX ? Direction.EAST : Direction.SOUTH;
		Direction depth = alongX ? Direction.SOUTH : Direction.EAST;

		// Plan the tree first and give up if anything solid is in the way.
		Set<BlockPos> logs = new HashSet<>();
		Map<BlockPos, Direction.Axis> logAxes = new HashMap<>();
		Set<BlockPos> leaves = new HashSet<>();
		int heartBase = trunk; // the heart's point rests on the top of the trunk
		for (int y = 0; y < trunk + 6; y++) {
			BlockPos pos = origin.above(y);
			logs.add(pos);
			logAxes.put(pos, Direction.Axis.Y);
		}
		// Branches climb diagonally into each lobe.
		for (int side = -1; side <= 1; side += 2) {
			for (int step = 1; step <= 3; step++) {
				BlockPos pos = origin.above(heartBase + 5 + step).relative(across, side * (step + 1));
				logs.add(pos);
				logAxes.put(pos, across.getAxis());
			}
		}
		for (int row = 0; row < HEART_HEIGHT; row++) {
			String line = HEART[HEART_HEIGHT - 1 - row]; // row 0 is the point at the bottom
			for (int col = 0; col < HEART_WIDTH; col++) {
				for (int layer = -2; layer <= 2; layer++) {
					if (!inHeart(row, col, Math.abs(layer)) || line.charAt(col) != '#') {
						continue;
					}
					BlockPos pos = origin.above(heartBase + row).relative(across, col - HEART_WIDTH / 2).relative(depth, layer);
					if (!logs.contains(pos)) {
						leaves.add(pos);
					}
				}
			}
		}
		for (BlockPos pos : logs) {
			if (!isFree(level, pos)) {
				return false;
			}
		}
		for (BlockPos pos : leaves) {
			if (!isFree(level, pos)) {
				return false;
			}
		}

		// Leaf distance to the nearest log, walking through leaves (at most 7, like vanilla).
		Map<BlockPos, Integer> distance = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos log : logs) {
			distance.put(log, 0);
			queue.add(log);
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int next = distance.get(pos) + 1;
			if (next > 6) {
				continue;
			}
			for (Direction direction : Direction.values()) {
				BlockPos neighbour = pos.relative(direction);
				if (leaves.contains(neighbour) && !distance.containsKey(neighbour)) {
					distance.put(neighbour, next);
					queue.add(neighbour);
				}
			}
		}

		BlockState log = ModBlocks.BLUSH_WOOD.log().defaultBlockState();
		for (BlockPos pos : logs) {
			level.setBlock(pos, log.setValue(RotatedPillarBlock.AXIS, logAxes.get(pos)), PLACE_FLAGS);
		}
		for (BlockPos pos : leaves) {
			Integer d = distance.get(pos);
			if (d == null) {
				continue;
			}
			Block block = random.nextFloat() < 0.3F ? ModBlocks.FLOWERING_BLUSH_LEAVES : ModBlocks.BLUSH_LEAVES;
			level.setBlock(pos, block.defaultBlockState()
					.setValue(BlockStateProperties.DISTANCE, d)
					.setValue(BlockStateProperties.PERSISTENT, false), PLACE_FLAGS);
		}
		return true;
	}

	/** The heart is five layers deep: the full shape in the middle, shrinking towards the front and back. */
	private static boolean inHeart(int row, int col, int layer) {
		if (layer == 0) {
			return true;
		}
		// A cell is kept in an outer layer only if its neighbours `layer` steps away are all inside the heart.
		for (int dy = -layer; dy <= layer; dy++) {
			for (int dx = -layer; dx <= layer; dx++) {
				if (Math.abs(dx) + Math.abs(dy) > layer) {
					continue;
				}
				int r = row + dy;
				int c = col + dx;
				if (r < 0 || r >= HEART_HEIGHT || c < 0 || c >= HEART_WIDTH || HEART[HEART_HEIGHT - 1 - r].charAt(c) != '#') {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean isFree(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(ModBlocks.BLUSH_WOOD.sapling());
	}
}
