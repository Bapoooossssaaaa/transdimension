package dev.goober.transdimension.client.wings;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;

/**
 * Draws Trans Wings on players who wear them, attached to the body so they lean and turn with it. The pose comes
 * from the render state ({@link WingPose#KEY}); players without wings have none and get nothing drawn.
 */
public class TransWingsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/trans_wings.png");

	private final TransWingsModel model;

	public TransWingsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
		super(parent);
		this.model = new TransWingsModel(context.bakeLayer(TransWingsModel.LAYER));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light, AvatarRenderState state, float limbAngle,
			float limbDistance) {
		if (state.getData(WingPose.KEY) == null || state.isInvisible) {
			return;
		}
		poseStack.pushPose();
		this.getParentModel().body.translateAndRotate(poseStack);
		nodeCollector.submitModel(this.model, state, poseStack, this.model.renderType(TEXTURE), light, OverlayTexture.NO_OVERLAY,
				state.outlineColor, null);
		poseStack.popPose();
	}
}
