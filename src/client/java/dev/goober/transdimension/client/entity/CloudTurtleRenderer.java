package dev.goober.transdimension.client.entity;

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
 * the sculk people. He's always riding his Cloudy, so he sits; his right arm reaches down to hold the boat's line.
 */
public class CloudTurtleRenderer extends MobRenderer<CloudTurtle, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/cloud_turtle/cloud_turtle.png");

	public CloudTurtleRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(SculkPersonRenderer.LAYER)) {
			@Override
			public void setupAnim(HumanoidRenderState state) {
				super.setupAnim(state);
				// holding the line down to the boat
				this.rightArm.xRot = -0.6F;
				this.rightArm.zRot = 0.1F;
			}
		}, 0.0F);
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
