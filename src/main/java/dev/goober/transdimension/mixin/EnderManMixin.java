package dev.goober.transdimension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.monster.EnderMan;

import dev.goober.transdimension.entity.TransEnderman;

/**
 * Endermen trail purple portal particles. Trans endermen trail light blue and white sparkles instead (vanilla endermen
 * are untouched). {@code require = 0}: if this ever stops matching, trans endermen just trail portal particles.
 */
@Mixin(EnderMan.class)
public abstract class EnderManMixin {
	@ModifyArg(
			method = "aiStep()V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"),
			index = 0,
			require = 0
	)
	private ParticleOptions transdimension$sparkles(ParticleOptions original) {
		if ((Object) this instanceof TransEnderman enderman) {
			int[] colours = TransEnderman.SPARKLE_COLOURS;
			return new DustParticleOptions(colours[enderman.getRandom().nextInt(colours.length)], 0.8F);
		}
		return original;
	}
}
