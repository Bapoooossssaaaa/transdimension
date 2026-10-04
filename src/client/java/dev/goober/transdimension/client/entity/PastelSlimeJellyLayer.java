package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** The pastel slime's clear jelly coat, drawn translucent over the body (like a vanilla slime's outer layer). */
public class PastelSlimeJellyLayer extends RenderLayer<PastelSlimeRenderState, PastelSlimeModel> {
	private final PastelSlimeModel jelly;

	public PastelSlimeJellyLayer(RenderLayerParent<PastelSlimeRenderState, PastelSlimeModel> parent, EntityRendererProvider.Context context) {
		super(parent);
		this.jelly = new PastelSlimeModel(context.bakeLayer(PastelSlimeRenderer.JELLY_LAYER));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light, PastelSlimeRenderState state, float yRot,
			float xRot) {
		if (state.isInvisible) {
			return;
		}
		nodeCollector.submitModel(this.jelly, state, poseStack, RenderTypes.entityTranslucent(PastelSlimeRenderer.texture(state.variant)),
				light, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor, null);
	}
}
