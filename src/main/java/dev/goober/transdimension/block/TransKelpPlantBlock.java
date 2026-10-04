package dev.goober.transdimension.block;

import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.KelpPlantBlock;

import dev.goober.transdimension.registry.ModBlocks;

/** A trans kelp stem; it belongs to the trans kelp tip above it. */
public class TransKelpPlantBlock extends KelpPlantBlock {
	public TransKelpPlantBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected GrowingPlantHeadBlock getHeadBlock() {
		return (GrowingPlantHeadBlock) ModBlocks.TRANS_KELP;
	}
}
