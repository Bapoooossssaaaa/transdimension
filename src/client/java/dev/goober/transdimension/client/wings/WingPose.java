package dev.goober.transdimension.client.wings;

import net.minecraft.util.Mth;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * Angles (radians) of the right wing for one frame; the left wing mirrors them. Stored on a player's render state
 * under {@link #KEY} by {@code AvatarRendererMixin} when the player wears Trans Wings.
 *
 * <p>The wing's flat side lies in the plane of the back, so a z rotation moves it within that plane (raising and
 * lowering it while standing) and a y rotation swings it out of the plane (which is up and down in the world while
 * gliding face down).
 *
 * @param rootX pitch of the whole wing at the shoulder blade (positive tilts the lower part away from the back)
 * @param rootY swing out of the back's plane: positive moves the tip backwards, which is up while gliding
 * @param rootZ swing in the back's plane: positive raises the tip, negative folds it down along the back
 * @param handY swing of the hand (the outer half) out of the plane, against the arm
 * @param handZ bend of the hand against the arm: 0 is a straight wing, near pi folds it back along the arm
 * @param fan   0 = feathers folded together, 1 = fanned out into a full wing
 * @param flex  how far the feather tips bend away from the back (air pushing on them during a wingbeat)
 */
public record WingPose(float rootX, float rootY, float rootZ, float handY, float handZ, float fan, float flex) {
	public static final RenderStateDataKey<WingPose> KEY = RenderStateDataKey.create(() -> "transdimension:wing_pose");

	/** Resting on the back: the arm points down, the hand folds back up beside it, feathers hang to the hips. */
	public static final WingPose FOLDED = new WingPose(0.24F, 0.10F, -1.30F, 0.0F, 2.75F, 0.0F, 0.0F);
	/** In the air but not gliding: raised, open and swept back a little. */
	public static final WingPose AIRBORNE = new WingPose(0.10F, 0.30F, 0.45F, 0.0F, -0.50F, 0.9F, 0.0F);
	/** Gliding: straight out to the sides, slightly forward, tips a touch up, every feather fanned. */
	public static final WingPose GLIDING = new WingPose(0.0F, 0.12F, 0.10F, 0.0F, -0.18F, 1.0F, 0.0F);

	public WingPose lerp(WingPose other, float t) {
		return new WingPose(
				Mth.lerp(t, this.rootX, other.rootX),
				Mth.lerp(t, this.rootY, other.rootY),
				Mth.lerp(t, this.rootZ, other.rootZ),
				Mth.lerp(t, this.handY, other.handY),
				Mth.lerp(t, this.handZ, other.handZ),
				Mth.lerp(t, this.fan, other.fan),
				Mth.lerp(t, this.flex, other.flex));
	}
}
