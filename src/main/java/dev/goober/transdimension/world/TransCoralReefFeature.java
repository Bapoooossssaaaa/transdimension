package dev.goober.transdimension.world;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * Trans coral reefs for the realm's warm seas, shaped like vanilla's: a "tree" (a short trunk with branches climbing
 * out of it), a "mushroom" (a hollow, rounded cap) and a "fan" (a low spreading mound). The body of each reef is one
 * colour of coral block; the corals, coral fans and wall fans that grow on it are any trans colour, so reefs come out
 * pink, blue and white together. The odd sea pickle glows on top.
 */
public class TransCoralReefFeature extends Feature<NoneFeatureConfiguration> {
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	public TransCoralReefFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		List<ModBlocks.CoralSet> corals = ModBlocks.CORALS;
		BlockState body = corals.get(random.nextInt(corals.size())).block().defaultBlockState();
		return switch (random.nextInt(3)) {
			case 0 -> this.tree(level, random, origin, body);
			case 1 -> this.mushroom(level, random, origin, body);
			default -> this.mound(level, random, origin, body);
		};
	}

	/** A trunk of one to three blocks, then two to four branches that wander outwards and upwards. */
	private boolean tree(WorldGenLevel level, RandomSource random, BlockPos origin, BlockState body) {
		BlockPos.MutableBlockPos pos = origin.mutable();
		int trunk = random.nextInt(3) + 1;
		for (int i = 0; i < trunk; i++) {
			if (!this.placeCoral(level, random, pos, body)) {
				return true;
			}
			pos.move(Direction.UP);
		}
		BlockPos top = pos.immutable();
		Direction[] directions = HORIZONTAL.clone();
		for (int i = directions.length - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			Direction swap = directions[i];
			directions[i] = directions[j];
			directions[j] = swap;
		}
		int branches = random.nextInt(3) + 2;
		for (int b = 0; b < branches; b++) {
			Direction direction = directions[b];
			pos.set(top);
			pos.move(direction);
			int length = random.nextInt(5) + 2;
			int sinceTurn = 0;
			for (int i = 0; i < length && this.placeCoral(level, random, pos, body); i++) {
				sinceTurn++;
				pos.move(Direction.UP);
				if (i == 0 || sinceTurn >= 2 && random.nextFloat() < 0.25F) {
					pos.move(direction);
					sinceTurn = 0;
				}
			}
		}
		return true;
	}

	/** A hollow box with its edges and corners knocked off, sunk a little into the sea floor. */
	private boolean mushroom(WorldGenLevel level, RandomSource random, BlockPos origin, BlockState body) {
		int height = random.nextInt(3) + 3;
		int width = random.nextInt(3) + 3;
		int depth = random.nextInt(3) + 3;
		int sink = random.nextInt(3) + 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x <= width; x++) {
			for (int y = 0; y <= height; y++) {
				for (int z = 0; z <= depth; z++) {
					boolean edgeX = x == 0 || x == width;
					boolean edgeY = y == 0 || y == height;
					boolean edgeZ = z == 0 || z == depth;
					boolean onShell = edgeX || edgeY || edgeZ;
					boolean onRim = edgeX && edgeY || edgeY && edgeZ || edgeX && edgeZ;
					if (onShell && !onRim && random.nextFloat() >= 0.1F) {
						pos.set(origin.getX() + x, origin.getY() + y - sink, origin.getZ() + z);
						this.placeCoral(level, random, pos, body);
					}
				}
			}
		}
		return true;
	}

	/** A low, rounded mound that thins out towards its edge. */
	private boolean mound(WorldGenLevel level, RandomSource random, BlockPos origin, BlockState body) {
		int radius = random.nextInt(2) + 2;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				int distance = Math.abs(x) + Math.abs(z);
				if (distance > radius + 1 || random.nextFloat() < 0.15F * distance) {
					continue;
				}
				int height = radius + 1 - distance + random.nextInt(2);
				for (int y = 0; y < height; y++) {
					pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					if (!this.placeCoral(level, random, pos, body)) {
						break;
					}
				}
			}
		}
		return true;
	}

	/**
	 * Places one block of the reef if there is water there (or coral to replace) and water above, then decorates it:
	 * a coral or a coral fan on top, sometimes a sea pickle, and wall fans on the open sides.
	 */
	private boolean placeCoral(WorldGenLevel level, RandomSource random, BlockPos pos, BlockState body) {
		BlockPos above = pos.above();
		BlockState current = level.getBlockState(pos);
		if (!(current.is(Blocks.WATER) || isCoralGrowth(current)) || !level.getBlockState(above).is(Blocks.WATER)) {
			return false;
		}
		level.setBlock(pos, body, Block.UPDATE_ALL);
		List<ModBlocks.CoralSet> corals = ModBlocks.CORALS;
		if (random.nextFloat() < 0.25F) {
			ModBlocks.CoralSet growth = corals.get(random.nextInt(corals.size()));
			Block plant = random.nextBoolean() ? growth.plant() : growth.fan();
			level.setBlock(above, plant.defaultBlockState(), Block.UPDATE_CLIENTS);
		} else if (random.nextFloat() < 0.05F) {
			level.setBlock(above, ModBlocks.TRANS_SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, random.nextInt(4) + 1),
					Block.UPDATE_CLIENTS);
		}
		for (Direction direction : HORIZONTAL) {
			if (random.nextFloat() < 0.2F) {
				BlockPos side = pos.relative(direction);
				if (level.getBlockState(side).is(Blocks.WATER)) {
					Block wallFan = corals.get(random.nextInt(corals.size())).wallFan();
					level.setBlock(side, wallFan.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, direction),
							Block.UPDATE_CLIENTS);
				}
			}
		}
		return true;
	}

	private static boolean isCoralGrowth(BlockState state) {
		for (ModBlocks.CoralSet coral : ModBlocks.CORALS) {
			if (state.is(coral.plant()) || state.is(coral.fan()) || state.is(coral.wallFan())) {
				return true;
			}
		}
		return false;
	}
}
