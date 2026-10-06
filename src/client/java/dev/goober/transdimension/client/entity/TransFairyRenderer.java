package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.TransFairy;

public class TransFairyRenderer extends MobRenderer<TransFairy, TransFairyRenderState, TransFairyModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("trans_fairy"), "main");
	public static final ModelLayerLocation GLOW_LAYER = new ModelLayerLocation(TransDimension.id("trans_fairy"), "glow");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/trans_fairy/trans_fairy.png");
	/** In the cutscene she appears over this many ticks, growing out of the column of light (FairyCutsceneCamera). */
	private static final float APPEAR_TICKS = 24.0F;
	/** Half her height: she grows from her middle, not her feet. */
	private static final float HALF_HEIGHT = 1.2F;

	public TransFairyRenderer(EntityRendererProvider.Context context) {
		super(context, new TransFairyModel(context.bakeLayer(LAYER)), 0.6F);
		this.addLayer(new TransFairyGlowLayer(this, context));
	}

	@Override
	public TransFairyRenderState createRenderState() {
		return new TransFairyRenderState();
	}

	@Override
	public void extractRenderState(TransFairy fairy, TransFairyRenderState state, float partialTick) {
		super.extractRenderState(fairy, state, partialTick);
		state.action = fairy.getAction();
		state.actionTime = fairy.getActionTime(partialTick);
		state.appear = fairy.isIntro() ? Mth.clamp((fairy.tickCount + partialTick) / APPEAR_TICKS, 0.0F, 1.0F) : 1.0F;
	}

	/** Appearing in the cutscene: she grows out of a point of light with a little overshoot, turning as she comes. */
	@Override
	protected void scale(TransFairyRenderState state, PoseStack poseStack) {
		super.scale(state, poseStack);
		if (state.appear < 1.0F) {
			float t = state.appear - 1.0F;
			float grow = Math.max(0.0F, 1.0F + 2.70158F * t * t * t + 1.70158F * t * t);
			// the model is drawn upside down from here on (y down), so "up" to her middle is -y
			poseStack.translate(0.0F, -HALF_HEIGHT, 0.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees(-t * 540.0F));
			poseStack.scale(grow, grow, grow);
			poseStack.translate(0.0F, HALF_HEIGHT, 0.0F);
		}
	}

	@Override
	public Identifier getTextureLocation(TransFairyRenderState state) {
		return TEXTURE;
	}
}
