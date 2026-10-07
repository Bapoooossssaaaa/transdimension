package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.TransFairy;

/**
 * Draws the Trans Fairy with {@link TransFairyModel} and her glow layer. She simply appears where she's summoned, in
 * the burst of sparkles FairyRealm#spawnFairy makes (round 14 went back to that from round 10's grow-in).
 */
public class TransFairyRenderer extends MobRenderer<TransFairy, TransFairyRenderState, TransFairyModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("trans_fairy"), "main");
	public static final ModelLayerLocation GLOW_LAYER = new ModelLayerLocation(TransDimension.id("trans_fairy"), "glow");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/trans_fairy/trans_fairy.png");

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
	}

	@Override
	public Identifier getTextureLocation(TransFairyRenderState state) {
		return TEXTURE;
	}
}
