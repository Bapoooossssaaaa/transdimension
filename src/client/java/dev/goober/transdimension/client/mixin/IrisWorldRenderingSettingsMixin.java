package dev.goober.transdimension.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.world.level.block.state.BlockState;

import dev.goober.transdimension.client.compat.ShaderCompat;

/**
 * Iris (optional): when Iris stores a shader pack's block id map (built from its block.properties, once per pack load,
 * in {@code IrisRenderingPipeline#beginLevelRendering}), our blocks get their vanilla twins' ids first
 * ({@link ShaderCompat#addTwins}). The map is an {@code Object2IntLinkedOpenHashMap} Iris made for this, so it can be
 * filled in place. {@code @Pseudo}: without Iris the target class doesn't exist and this mixin is skipped; with
 * {@code require = 0}, if Iris changes the method, our blocks just look as they did without it.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", remap = false)
public abstract class IrisWorldRenderingSettingsMixin {
	@Inject(method = "setBlockStateIds", at = @At("HEAD"), require = 0, remap = false)
	private void transdimension$addTwins(Object2IntMap<BlockState> ids, CallbackInfo ci) {
		if (ids != null) {
			ShaderCompat.addTwins(ids);
		}
	}
}
