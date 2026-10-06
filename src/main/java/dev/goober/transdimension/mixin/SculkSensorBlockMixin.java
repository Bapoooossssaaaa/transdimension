package dev.goober.transdimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SculkSensorBlock;

import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModSounds;

/**
 * A pink sculk sensor (vanilla's sculk sensor block) chimes softly where vanilla's clicks: when it hears something
 * ({@code activate}) and when it settles again ({@code tick}). Vanilla's own sensors are untouched.
 */
@Mixin(SculkSensorBlock.class)
public abstract class SculkSensorBlockMixin {
	@ModifyExpressionValue(method = "activate", at = @At(value = "FIELD",
			target = "Lnet/minecraft/sounds/SoundEvents;SCULK_CLICKING:Lnet/minecraft/sounds/SoundEvent;"), require = 0)
	private SoundEvent transdimension$pinkClick(SoundEvent original) {
		return (Object) this == ModBlocks.PINK_SCULK_SENSOR ? ModSounds.PINK_SCULK_SENSOR_CLICKING : original;
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "FIELD",
			target = "Lnet/minecraft/sounds/SoundEvents;SCULK_CLICKING_STOP:Lnet/minecraft/sounds/SoundEvent;"), require = 0)
	private SoundEvent transdimension$pinkClickStop(SoundEvent original) {
		return (Object) this == ModBlocks.PINK_SCULK_SENSOR ? ModSounds.PINK_SCULK_SENSOR_CLICKING_STOP : original;
	}
}
