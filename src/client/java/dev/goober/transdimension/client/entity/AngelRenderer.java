package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.Angel;

/** Draws an {@link Angel} with {@link AngelModel}, see-through and full bright: angels shine white and gold in the dark they keep to. */
public class AngelRenderer extends MobRenderer<Angel, LivingEntityRenderState, AngelModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("angel"), "main");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/angel/angel.png");

	public AngelRenderer(EntityRendererProvider.Context context) {
		super(context, new AngelModel(context.bakeLayer(LAYER)), 0.4F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}

	@Override
	protected int getBlockLightLevel(Angel angel, BlockPos pos) {
		return 15;
	}
}
