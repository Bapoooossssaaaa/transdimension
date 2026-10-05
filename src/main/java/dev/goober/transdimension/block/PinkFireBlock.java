package dev.goober.transdimension.block;

import net.minecraft.world.level.block.FireBlock;

/**
 * Fire as it burns in the Trans Realm and the Fairy Realm: pink. It is vanilla fire in every way (it spreads, burns out,
 * sets things alight) except its colour; {@code BaseFireBlockMixin} lights it instead of vanilla fire in the two realms.
 *
 * <p>A new fire block starts out knowing nothing about what burns: vanilla keeps that in each fire block's own private
 * tables, and Fabric gives each fire block its own registry. So {@code FireBlockMixin} has pink fire ask vanilla fire's
 * registry, which also holds everything registered with Fabric (the mod's own woods, wool and plants among them).
 */
public class PinkFireBlock extends FireBlock {
	public PinkFireBlock(Properties properties) {
		super(properties);
	}
}
