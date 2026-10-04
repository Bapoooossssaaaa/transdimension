package dev.goober.transdimension.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.state.BlockState;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * A path worn into trans grass or dirt (use a shovel on them, see ModItems), and the camps' footpaths. It behaves like
 * vanilla's dirt path, except that it turns back into trans dirt, not plain dirt, when something solid covers it.
 */
public class TransDirtPathBlock extends DirtPathBlock {
	public TransDirtPathBlock(Properties properties) {
		super(properties);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = this.defaultBlockState();
		return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state
				: Block.pushEntitiesUp(state, ModBlocks.TRANS_DIRT.defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// Vanilla schedules this tick once the block above stops the path from surviving.
		level.setBlockAndUpdate(pos, Block.pushEntitiesUp(state, ModBlocks.TRANS_DIRT.defaultBlockState(), level, pos));
	}
}
