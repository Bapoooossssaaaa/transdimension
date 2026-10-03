package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * Trans grass: spreads onto nearby trans dirt in the light, and turns back into
 * trans dirt when something solid or a fluid covers it.
 */
public class TransGrassBlock extends Block {
	public TransGrassBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos above = pos.above();
		if (isSmothered(level, above)) {
			level.setBlockAndUpdate(pos, ModBlocks.TRANS_DIRT.defaultBlockState());
			return;
		}

		if (level.getMaxLocalRawBrightness(above) >= 9) {
			for (int i = 0; i < 4; i++) {
				BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(5) - 3, random.nextInt(3) - 1);
				if (level.getBlockState(target).is(ModBlocks.TRANS_DIRT) && !isSmothered(level, target.above())) {
					level.setBlockAndUpdate(target, this.defaultBlockState());
				}
			}
		}
	}

	private static boolean isSmothered(ServerLevel level, BlockPos above) {
		return level.getBlockState(above).canOcclude() || !level.getFluidState(above).isEmpty();
	}
}
