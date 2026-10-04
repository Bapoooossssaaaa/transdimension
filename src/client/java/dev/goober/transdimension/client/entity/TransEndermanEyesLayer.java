package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;

/** The trans enderman's pink eyes, drawn full bright so they glow in the dark. */
public class TransEndermanEyesLayer extends RenderLayer<TransEndermanRenderState, TransEndermanModel> {
	private static final Identifier EYES = TransDimension.id("textures/entity/trans_enderman/trans_enderman_eyes.png");
	/** Block and sky light both at 15. */
	private static final int FULL_BRIGHT = 0xF000F0;

	private final TransEndermanModel eyes;

	public TransEndermanEyesLayer(RenderLayerParent<TransEndermanRenderState, TransEndermanModel> parent, EntityRendererProvider.Context context) {
		super(parent);
		this.eyes = new TransEndermanModel(context.bakeLayer(TransEndermanRenderer.EYES_LAYER));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light, TransEndermanRenderState state, float yRot,
			float xRot) {
		if (state.isInvisible) {
			return;
		}
		nodeCollector.submitModel(this.eyes, state, poseStack, this.eyes.renderType(EYES), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				state.outlineColor, null);
	}
}
