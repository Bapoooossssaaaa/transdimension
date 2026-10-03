package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * Trans grass: spreads onto nearby trans dirt in the light, and turns back into
 * trans dirt when something solid or a fluid covers it.
 *
 * <p>It extends {@link SnowyBlock} (vanilla's 26.1 name for the old {@code SnowyDirtBlock}) so it gets the
 * {@code snowy} property and the frosted side texture whenever snow lies on top, just like vanilla grass.
 * The top and side overlay are tinted by the biome's grass colour on the client, which is how every
 * biome gets its own shade of trans grass.
 */
public class TransGrassBlock extends SnowyBlock {
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
					boolean snowy = level.getBlockState(target.above()).is(Blocks.SNOW);
					level.setBlockAndUpdate(target, this.defaultBlockState().setValue(BlockStateProperties.SNOWY, snowy));
				}
			}
		}
	}

	private static boolean isSmothered(ServerLevel level, BlockPos above) {
		BlockState stateAbove = level.getBlockState(above);
		if (stateAbove.is(Blocks.SNOW)) {
			return false; // a thin layer of snow lets the grass breathe, like vanilla
		}
		return stateAbove.canOcclude() || !level.getFluidState(above).isEmpty();
	}
}
