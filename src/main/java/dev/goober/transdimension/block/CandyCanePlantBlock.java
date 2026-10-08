package dev.goober.transdimension.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A candy cane growing out of the ground in the Candy Cane Grove, hook and all. Breaking it gives the candy cane, which
 * is eaten as a snack or planted again (the item is this block's, like glow berries are cave vines'). It stands on dirt
 * and grass, like any plant, and on snow blocks.
 */
public class CandyCanePlantBlock extends VegetationBlock {
	public static final MapCodec<CandyCanePlantBlock> CODEC = simpleCodec(CandyCanePlantBlock::new);
	private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 14.0, 12.0);

	public CandyCanePlantBlock(Properties properties) {
		super(properties);
	}

	@Override
	public MapCodec<CandyCanePlantBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return super.mayPlaceOn(state, level, pos) || state.is(Blocks.SNOW_BLOCK);
	}
}
