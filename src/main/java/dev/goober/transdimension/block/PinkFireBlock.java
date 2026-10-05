package dev.goober.transdimension.block;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fire as it burns in the Trans Realm and the Fairy Realm: pink. It is vanilla fire in every way (it spreads, burns out,
 * sets things alight) except its colour; {@code BaseFireBlockMixin} lights it instead of vanilla fire in the two realms.
 *
 * <p>A new fire block starts out knowing nothing about what burns, so it asks vanilla fire, which also knows everything
 * registered with Fabric's flammable block registry (the mod's own woods, wool and plants among them).
 */
public class PinkFireBlock extends FireBlock {
	public PinkFireBlock(Properties properties) {
		super(properties);
	}

	@Override
	public int getIgniteOdds(BlockState state) {
		return ((FireBlock) Blocks.FIRE).getIgniteOdds(state);
	}

	@Override
	public int getBurnOdds(BlockState state) {
		return ((FireBlock) Blocks.FIRE).getBurnOdds(state);
	}
}
