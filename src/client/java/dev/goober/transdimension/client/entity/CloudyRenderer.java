package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.Cloudy;

/** Draws a {@link Cloudy} with {@link CloudyModel}. */
public class CloudyRenderer extends MobRenderer<Cloudy, LivingEntityRenderState, CloudyModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("cloudy"), "main");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/cloudy/cloudy.png");

	public CloudyRenderer(EntityRendererProvider.Context context) {
		super(context, new CloudyModel(context.bakeLayer(LAYER)), 0.8F);
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
