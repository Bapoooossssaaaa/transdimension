package dev.goober.transdimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;

import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModSounds;

/**
 * A pink sculk shrieker (vanilla's shrieker block entity on the pink block) sings a crystal note where vanilla's shrieks.
 * Vanilla's own shriekers are untouched.
 */
@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerBlockEntityMixin {
	@ModifyExpressionValue(method = "shriek", at = @At(value = "FIELD",
			target = "Lnet/minecraft/sounds/SoundEvents;SCULK_SHRIEKER_SHRIEK:Lnet/minecraft/sounds/SoundEvent;"), require = 0)
	private SoundEvent transdimension$pinkShriek(SoundEvent original) {
		return ((BlockEntity) (Object) this).getBlockState().is(ModBlocks.PINK_SCULK_SHRIEKER) ? ModSounds.PINK_SCULK_SHRIEKER_SHRIEK : original;
	}
}
