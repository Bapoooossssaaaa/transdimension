package dev.goober.transdimension.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import dev.goober.transdimension.registry.ModBlocks;

/**
 * Vanilla fills caves below y -54 with lava straight from code ({@code createFluidPicker}), not from the noise settings.
 * In the Trans Realm (the only noise settings made of trans stone) that lava is pink. With {@code require = 0}, if the
 * method ever changes the deep lava just stays orange.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
	@Inject(method = "createFluidPicker", at = @At("RETURN"), cancellable = true, require = 0)
	private static void transdimension$pinkLava(NoiseGeneratorSettings settings, CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
		if (!settings.defaultBlock().is(ModBlocks.TRANS_STONE)) {
			return;
		}
		Aquifer.FluidPicker vanilla = cir.getReturnValue();
		BlockState pinkLava = ModBlocks.PINK_LAVA.defaultBlockState();
		cir.setReturnValue((x, y, z) -> {
			Aquifer.FluidStatus status = vanilla.computeFluid(x, y, z);
			return status.fluidType().is(Blocks.LAVA) ? new Aquifer.FluidStatus(status.fluidLevel(), pinkLava) : status;
		});
	}
}
