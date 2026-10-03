package dev.goober.transdimension.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * "Slobbered": you just got a big lick from a Silly Cat. It does nothing by itself; the client
 * sees it on the player and covers the screen in cat spit until it wears off. Silly Cats also
 * won't lick a player who is still slobbered, which keeps the free healing in check.
 */
public class SlobberedEffect extends MobEffect {
	public SlobberedEffect() {
		super(MobEffectCategory.NEUTRAL, 0xF7A8C4);
	}
}
