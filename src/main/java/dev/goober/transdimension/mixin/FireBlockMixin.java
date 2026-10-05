package dev.goober.transdimension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;

import dev.goober.transdimension.block.PinkFireBlock;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * Makes pink fire ({@link PinkFireBlock}) burn and age like vanilla fire.
 *
 * <p>What burns: {@code FireBlock#getIgniteOdds} and {@code getBurnOdds} are private and read the fire block's own
 * tables, which are empty for a new fire block. For pink fire they answer from vanilla fire's flammable block registry
 * instead (Fabric's {@link FlammableBlockRegistry#getDefaultInstance()}, which falls back to vanilla's tables), with
 * vanilla's rule that waterlogged blocks never burn. Fabric's own FireBlockMixin hooks the same two methods, which is
 * where their full descriptors come from ({@code getIgniteOdds} has an overload).
 *
 * <p>Age: when fire spreads or its neighbours change, {@code FireBlock#getStateWithAge} asks
 * {@code BaseFireBlock#getState} for the new fire and then sets its age, but only if it is vanilla fire. In the realms
 * that answer is pink fire ({@link BaseFireBlockMixin}), so without this every new pink fire would start young and
 * spread harder than vanilla fire. The age comes from the method's only int argument (MixinExtras' {@code @Local},
 * which Fabric Loader ships), so the level argument's type doesn't matter.
 *
 * <p>All three use {@code require = 0}: if a method ever changes, pink fire burns nothing or spreads a little more
 * eagerly, instead of crashing the game.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/block/state/BlockState;)I", at = @At("HEAD"), cancellable = true,
			require = 0)
	private void transdimension$pinkIgniteOdds(BlockState state, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof PinkFireBlock) {
			cir.setReturnValue(transdimension$waterlogged(state) ? 0
					: FlammableBlockRegistry.getDefaultInstance().get(state.getBlock()).getIgniteOdds());
		}
	}

	@Inject(method = "getBurnOdds(Lnet/minecraft/world/level/block/state/BlockState;)I", at = @At("HEAD"), cancellable = true,
			require = 0)
	private void transdimension$pinkBurnOdds(BlockState state, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof PinkFireBlock) {
			cir.setReturnValue(transdimension$waterlogged(state) ? 0
					: FlammableBlockRegistry.getDefaultInstance().get(state.getBlock()).getBurnOdds());
		}
	}

	@Inject(method = "getStateWithAge", at = @At("RETURN"), cancellable = true, require = 0)
	private void transdimension$pinkFireAge(CallbackInfoReturnable<BlockState> cir, @Local(argsOnly = true) int age) {
		BlockState fire = cir.getReturnValue();
		if (fire.is(ModBlocks.PINK_FIRE)) {
			cir.setReturnValue(fire.setValue(FireBlock.AGE, age));
		}
	}

	@Unique
	private static boolean transdimension$waterlogged(BlockState state) {
		return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
	}
}
