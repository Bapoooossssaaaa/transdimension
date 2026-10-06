package dev.goober.transdimension.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.client.FairyCutsceneCamera;

/**
 * Moves the camera along the Fairy Realm cutscene's path while it plays ({@link FairyCutsceneCamera}). It runs after
 * vanilla has placed the camera for the frame ({@code Camera#update}, 26.x's {@code setup}) and simply puts it somewhere
 * else; outside the cutscene it does nothing.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "update", at = @At("TAIL"), require = 0)
	private void transdimension$cutsceneShot(CallbackInfo ci) {
		FairyCutsceneCamera.Shot shot = FairyCutsceneCamera.current();
		if (shot != null) {
			this.setRotation(shot.yaw(), shot.pitch());
			this.setPosition(shot.position());
		}
	}
}
