package dev.goober.transdimension.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.KelpBlock;

import dev.goober.transdimension.registry.ModBlocks;

/** The growing tip of trans kelp; it grows trans kelp stems behind it (vanilla kelp would grow vanilla stems). */
public class TransKelpBlock extends KelpBlock {
	public TransKelpBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected Block getBodyBlock() {
		return ModBlocks.TRANS_KELP_PLANT;
	}
}
