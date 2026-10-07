package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.CloudTurtle;

/**
 * Draws the cloud turtle as a player-shaped figure in the owner's skin for him (textures/entity/cloud_turtle/
 * cloud_turtle.png, copied from tools/art/cloud_turtle_skin.png by tools/generate_textures.py), on the same model as
 * the sculk people. He always rides his Cloudy, so he sits on it: legs out in front (vanilla's riding pose, set here
 * because this isn't a humanoid mob renderer, which is what sets it), drawn {@link #SEAT_DROP} blocks lower so he sits on
 * the cloud rather than standing on its top, his right hand holding the golden lead down to the boat
 * (CloudBoatRenderer) and his left resting on his knee.
 */
public class CloudTurtleRenderer extends MobRenderer<CloudTurtle, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/cloud_turtle/cloud_turtle.png");
	/** How far below where he stands he's drawn, so his hips (not his feet) are on the Cloudy's top. */
	private static final float SEAT_DROP = 0.6F;

	public CloudTurtleRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(SculkPersonRenderer.LAYER)) {
			@Override
			public void setupAnim(HumanoidRenderState state) {
				super.setupAnim(state);
				// Sitting, legs forward and a little apart (vanilla's riding pose).
				this.rightLeg.xRot = -1.4137167F;
				this.rightLeg.yRot = 0.31415927F;
				this.rightLeg.zRot = 0.07853982F;
				this.leftLeg.xRot = -1.4137167F;
				this.leftLeg.yRot = -0.31415927F;
				this.leftLeg.zRot = -0.07853982F;
				// Holding the lead down to the boat, the other hand on his knee.
				this.rightArm.xRot = -0.6F;
				this.rightArm.yRot = 0.0F;
				this.rightArm.zRot = 0.1F;
				this.leftArm.xRot = -0.9F;
				this.leftArm.yRot = 0.1F;
				this.leftArm.zRot = -0.05F;
			}
		}, 0.0F);
	}

	@Override
	protected void scale(HumanoidRenderState state, PoseStack poseStack) {
		// The model is drawn upside down here, so down is +y.
		poseStack.translate(0.0F, SEAT_DROP, 0.0F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}
