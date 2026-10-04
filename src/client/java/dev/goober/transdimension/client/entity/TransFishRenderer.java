package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.TransFish;

public class TransFishRenderer extends MobRenderer<TransFish, LivingEntityRenderState, TransFishModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("trans_fish"), "main");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/trans_fish/trans_fish.png");

	public TransFishRenderer(EntityRendererProvider.Context context) {
		super(context, new TransFishModel(context.bakeLayer(LAYER)), 0.3F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
