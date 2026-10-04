package dev.goober.transdimension.client.wings;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * Angles (radians) of the right wing for one frame; the left wing mirrors them. Stored on a player's render state
 * under {@link #KEY} by {@code AvatarRendererMixin} when the player wears Trans Wings.
 *
 * @param rootX  pitch of the whole wing at the shoulder
 * @param rootY  sweep: positive swings the tip back
 * @param rootZ  lift: positive raises the tip, negative folds it down along the back
 * @param outerZ bend of the outer feather panel against the inner one
 */
public record WingPose(float rootX, float rootY, float rootZ, float outerZ) {
	public static final RenderStateDataKey<WingPose> KEY = RenderStateDataKey.create(() -> "transdimension:wing_pose");
	public static final WingPose FOLDED = new WingPose(0.0F, 0.15F, -1.25F, -0.25F);
	public static final WingPose SPREAD = new WingPose(0.0F, 0.2F, 0.25F, 0.12F);
}
