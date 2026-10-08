package dev.goober.transdimension.world;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * A giant candy cane standing in the Candy Cane Grove: a striped stem six to ten blocks tall that hooks over at the top
 * (two blocks across and one back down), the hook facing a random way. It only goes where there's solid ground under it
 * and room for all of it, so it never cuts into trees or hills.
 */
public class CandyCaneFeature extends Feature<NoneFeatureConfiguration> {
	public CandyCaneFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		BlockPos ground = origin.below();
		if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
			return false;
		}
		int top = 5 + random.nextInt(5);
		Direction hook = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos over = origin.above(top).relative(hook);
		BlockPos end = over.relative(hook);
		BlockPos tip = end.below();
		for (int y = 0; y <= top; y++) {
			if (!level.getBlockState(origin.above(y)).canBeReplaced()) {
				return false;
			}
		}
		for (BlockPos pos : new BlockPos[] {over, end, tip}) {
			if (!level.getBlockState(pos).canBeReplaced()) {
				return false;
			}
		}
		BlockState upright = ModBlocks.CANDY_CANE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		BlockState across = ModBlocks.CANDY_CANE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, hook.getAxis());
		for (int y = 0; y <= top; y++) {
			level.setBlock(origin.above(y), upright, Block.UPDATE_CLIENTS);
		}
		level.setBlock(over, across, Block.UPDATE_CLIENTS);
		level.setBlock(end, across, Block.UPDATE_CLIENTS);
		level.setBlock(tip, upright, Block.UPDATE_CLIENTS);
		return true;
	}
}
