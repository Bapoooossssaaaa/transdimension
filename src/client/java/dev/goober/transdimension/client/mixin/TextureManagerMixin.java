package dev.goober.transdimension.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.client.TransRecolor;

/**
 * Every entity texture is looked up through TextureManager when it is drawn. Inside the Trans Realm,
 * hand back the trans-recoloured version instead. {@code require = 0}: if this hook ever stops
 * matching, mobs just keep their normal look instead of the game crashing.
 */
@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {
	@Inject(
			method = "getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;",
			at = @At("HEAD"),
			cancellable = true,
			require = 0
	)
	private void transdimension$swapEntityTexture(Identifier id, CallbackInfoReturnable<AbstractTexture> cir) {
		AbstractTexture recoloured = TransRecolor.swap(id);
		if (recoloured != null) {
			cir.setReturnValue(recoloured);
		}
	}
}
