package dev.goober.transdimension.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;

import dev.goober.transdimension.client.wings.WingAnimations;
import dev.goober.transdimension.client.wings.WingPose;

/**
 * Hands each player's Trans Wings pose to their render state, where {@code TransWingsLayer} picks it up (null for
 * players without wings, so stale poses never linger). {@code require = 0}: if this hook ever stops matching, the
 * wings simply aren't drawn instead of the game crashing.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("RETURN"),
			require = 0
	)
	private void transdimension$wingPose(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		WingPose pose = WingAnimations.pose(avatar, partialTick);
		state.setData(WingPose.KEY, pose);
	}
}
