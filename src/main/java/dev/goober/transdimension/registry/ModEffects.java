package dev.goober.transdimension.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.effect.BlessedEffect;
import dev.goober.transdimension.effect.SlobberedEffect;

/** Status effects. The icon lives at {@code textures/mob_effect/slobbered.png}. */
public final class ModEffects {
	public static final Holder<MobEffect> SLOBBERED = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT, TransDimension.id("slobbered"), new SlobberedEffect());
	/** Bathing in holy water: healing and golden absorption hearts. */
	public static final Holder<MobEffect> BLESSED = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT, TransDimension.id("blessed"), new BlessedEffect());

	private ModEffects() {
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
