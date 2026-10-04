package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.TransEnderman;

public class TransEndermanRenderer extends MobRenderer<TransEnderman, TransEndermanRenderState, TransEndermanModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("trans_enderman"), "main");
	public static final ModelLayerLocation EYES_LAYER = new ModelLayerLocation(TransDimension.id("trans_enderman"), "eyes");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/trans_enderman/trans_enderman.png");

	public TransEndermanRenderer(EntityRendererProvider.Context context) {
		super(context, new TransEndermanModel(context.bakeLayer(LAYER)), 0.5F);
		this.addLayer(new TransEndermanEyesLayer(this, context));
	}

	@Override
	public TransEndermanRenderState createRenderState() {
		return new TransEndermanRenderState();
	}

	@Override
	public void extractRenderState(TransEnderman enderman, TransEndermanRenderState state, float partialTick) {
		super.extractRenderState(enderman, state, partialTick);
		state.creepy = enderman.isCreepy();
		state.attackAnim = enderman.getAttackAnim(partialTick);
	}

	@Override
	public Identifier getTextureLocation(TransEndermanRenderState state) {
		return TEXTURE;
	}
}
