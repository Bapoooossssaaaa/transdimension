package dev.goober.transdimension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Every chest glows: chests and trapped chests give off a soft light, so they light up in their own glowy pink (the
 * mod's chest textures, assets/minecraft/textures/entity/chest/) and cast a glow round them. A chest that was already
 * placed before the mod starts glowing once something next to it changes and its light is worked out again.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ChestLightMixin {
	@Unique
	private static final int CHEST_GLOW = 4;

	@Shadow
	public abstract Block getBlock();

	@Inject(method = "getLightEmission", at = @At("RETURN"), cancellable = true, require = 0)
	private void transdimension$chestGlow(CallbackInfoReturnable<Integer> cir) {
		Block block = this.getBlock();
		if ((block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) && cir.getReturnValueI() < CHEST_GLOW) {
			cir.setReturnValue(CHEST_GLOW);
		}
	}
}
