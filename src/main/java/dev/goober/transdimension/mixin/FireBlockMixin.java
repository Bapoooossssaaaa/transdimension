package dev.goober.transdimension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * Lets pink fire age like vanilla fire. When fire spreads or its neighbours change, {@code FireBlock#getStateWithAge}
 * asks {@code BaseFireBlock#getState} for the new fire and then sets its age, but only if it is vanilla fire. In the
 * realms that answer is pink fire ({@link BaseFireBlockMixin}), so without this every new pink fire would start young
 * and spread harder than vanilla fire. The age comes from the method's only int argument (MixinExtras' {@code @Local},
 * which Fabric Loader ships), so the level argument's type doesn't matter. With {@code require = 0}, if the method
 * ever changes, pink fire just spreads a little more eagerly.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Inject(method = "getStateWithAge", at = @At("RETURN"), cancellable = true, require = 0)
	private void transdimension$pinkFireAge(CallbackInfoReturnable<BlockState> cir, @Local(argsOnly = true) int age) {
		BlockState fire = cir.getReturnValue();
		if (fire.is(ModBlocks.PINK_FIRE)) {
			cir.setReturnValue(fire.setValue(FireBlock.AGE, age));
		}
	}
}
