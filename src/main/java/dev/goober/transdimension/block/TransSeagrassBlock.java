package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import dev.goober.transdimension.registry.ModBlocks;

/** Trans seagrass: bone meal grows it into tall trans seagrass, like vanilla seagrass but in the realm's colours. */
public class TransSeagrassBlock extends SeagrassBlock {
	public TransSeagrassBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
		BlockState lower = ModBlocks.TALL_TRANS_SEAGRASS.defaultBlockState();
		BlockState upper = lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
		BlockPos above = pos.above();
		if (level.getBlockState(above).is(Blocks.WATER)) {
			level.setBlock(pos, lower, Block.UPDATE_CLIENTS);
			level.setBlock(above, upper, Block.UPDATE_CLIENTS);
		}
	}
}
