package dev.goober.transdimension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * Every new fire (flint and steel, fire charges, lava, lightning, fireballs, fire spreading) asks
 * {@link BaseFireBlock#getState} what to place. In the Trans Realm and the Fairy Realm the answer becomes pink fire
 * ({@link dev.goober.transdimension.block.PinkFireBlock}) instead of vanilla fire, keeping its shape on the blocks
 * around it; soul fire is left alone. With {@code require = 0}, if the method ever changes, fire just stays orange.
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {
	@Inject(method = "getState", at = @At("RETURN"), cancellable = true, require = 0)
	private static void transdimension$pinkFire(BlockGetter getter, BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
		BlockState fire = cir.getReturnValue();
		if (fire.is(Blocks.FIRE) && getter instanceof Level level
				&& (level.dimension() == TransDimension.TRANS_REALM || level.dimension() == TransDimension.FAIRY_REALM)) {
			cir.setReturnValue(ModBlocks.PINK_FIRE.withPropertiesOf(fire));
		}
	}
}
