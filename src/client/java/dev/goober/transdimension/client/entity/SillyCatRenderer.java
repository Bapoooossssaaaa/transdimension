package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.SillyCat;

public class SillyCatRenderer extends MobRenderer<SillyCat, SillyCatRenderState, SillyCatModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("silly_cat"), "main");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/silly_cat/silly_cat.png");

	public SillyCatRenderer(EntityRendererProvider.Context context) {
		super(context, new SillyCatModel(context.bakeLayer(LAYER)), 0.35F);
	}

	@Override
	public SillyCatRenderState createRenderState() {
		return new SillyCatRenderState();
	}

	@Override
	public void extractRenderState(SillyCat cat, SillyCatRenderState state, float partialTick) {
		super.extractRenderState(cat, state, partialTick);
		state.lickProgress = cat.getLickProgress(partialTick);
		state.blep = cat.isBlepping();
	}

	@Override
	public Identifier getTextureLocation(SillyCatRenderState state) {
		return TEXTURE;
	}
}
