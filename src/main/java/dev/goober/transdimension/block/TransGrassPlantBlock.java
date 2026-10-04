package dev.goober.transdimension.block;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Trans grass and the trans fern: short plants that bone meal grows into their two-block-tall versions (vanilla's
 * {@link TallGrassBlock} would grow vanilla tall grass or a vanilla large fern instead).
 */
public class TransGrassPlantBlock extends TallGrassBlock {
	private final Supplier<Block> tallVersion;

	public TransGrassPlantBlock(Supplier<Block> tallVersion, Properties properties) {
		super(properties);
		this.tallVersion = tallVersion;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
		BlockState tall = this.tallVersion.get().defaultBlockState();
		if (tall.canSurvive(level, pos) && level.isEmptyBlock(pos.above())) {
			DoublePlantBlock.placeAt(level, tall, pos, Block.UPDATE_CLIENTS);
		}
	}
}
