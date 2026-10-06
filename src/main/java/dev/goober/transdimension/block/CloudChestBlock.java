package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Cloud Realm's chest: white wood with a golden latch and trim, found in its heavenly ruins and up the beanstalk (and
 * craftable). It's a barrel underneath (vanilla's barrel block entity, which ModBlocks lets it use), so it holds 27
 * stacks and its loot, and needs no block entity renderer of its own: its model is a plain chest-shaped block model,
 * whose lid lifts a little while it's open. It always faces whoever places it, like a chest. Its item carries the name
 * "Cloud Chest", which the block entity takes as its custom name, so that's what its screen says (the ruins' chests are
 * given the name in their templates).
 */
public class CloudChestBlock extends BarrelBlock {
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

	public CloudChestBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
