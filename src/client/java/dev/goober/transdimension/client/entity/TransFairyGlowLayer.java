package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;

/** The Trans Fairy's wings and wand star: see-through and full bright, so they glow even at night. */
public class TransFairyGlowLayer extends RenderLayer<TransFairyRenderState, TransFairyModel> {
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/trans_fairy/trans_fairy_glow.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	private final TransFairyModel glow;

	public TransFairyGlowLayer(RenderLayerParent<TransFairyRenderState, TransFairyModel> parent, EntityRendererProvider.Context context) {
		super(parent);
		this.glow = new TransFairyModel(context.bakeLayer(TransFairyRenderer.GLOW_LAYER));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light, TransFairyRenderState state, float yRot,
			float xRot) {
		if (state.isInvisible) {
			return;
		}
		nodeCollector.submitModel(this.glow, state, poseStack, RenderTypes.entityTranslucent(TEXTURE), FULL_BRIGHT,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor, null);
	}
}
