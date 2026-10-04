package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** A wild fairy's soft halo: a see-through, full-bright shell round its light. */
public class FairyHaloLayer extends RenderLayer<FairyRenderState, FairyLightModel> {
	private static final int FULL_BRIGHT = 0xF000F0;

	private final FairyLightModel halo;

	public FairyHaloLayer(RenderLayerParent<FairyRenderState, FairyLightModel> parent, EntityRendererProvider.Context context) {
		super(parent);
		this.halo = new FairyLightModel(context.bakeLayer(FairyLightModel.HALO_LAYER));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light, FairyRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		nodeCollector.submitModel(this.halo, state, poseStack, RenderTypes.entityTranslucent(FairyRenderer.texture(state.colour)), FULL_BRIGHT,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor, null);
	}
}
