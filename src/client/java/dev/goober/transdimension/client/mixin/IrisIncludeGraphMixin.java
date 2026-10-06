package dev.goober.transdimension.client.mixin;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.goober.transdimension.client.compat.ShaderCompat;

/**
 * Iris (optional): every shader pack file Iris loads goes through {@code IncludeGraph#readFile(Path)}; we let
 * {@link ShaderCompat#patchShaderFile} patch it in memory (only BSL's vanilla cloud program, to keep the game's cloud
 * colour). Nothing on disk changes. {@code @Pseudo}: without Iris the target class doesn't exist and this mixin is
 * skipped; with {@code require = 0}, if Iris changes the method, the pack is simply loaded as it is.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.include.IncludeGraph", remap = false)
public abstract class IrisIncludeGraphMixin {
	@Inject(method = "readFile", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
	private static void transdimension$patchShader(Path path, CallbackInfoReturnable<String> cir) {
		String patched = ShaderCompat.patchShaderFile(path, cir.getReturnValue());
		if (patched != null) {
			cir.setReturnValue(patched);
		}
	}
}
