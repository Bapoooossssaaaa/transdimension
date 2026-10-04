package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.PastelSlime;

public class PastelSlimeRenderer extends MobRenderer<PastelSlime, PastelSlimeRenderState, PastelSlimeModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("pastel_slime"), "main");
	public static final ModelLayerLocation JELLY_LAYER = new ModelLayerLocation(TransDimension.id("pastel_slime"), "jelly");
	/** By variant: pink, blue, white, lavender and the striped trans one (see {@link PastelSlime#VARIANTS}). */
	private static final Identifier[] TEXTURES = {
			TransDimension.id("textures/entity/pastel_slime/pink.png"),
			TransDimension.id("textures/entity/pastel_slime/blue.png"),
			TransDimension.id("textures/entity/pastel_slime/white.png"),
			TransDimension.id("textures/entity/pastel_slime/lavender.png"),
			TransDimension.id("textures/entity/pastel_slime/trans.png")};

	public PastelSlimeRenderer(EntityRendererProvider.Context context) {
		super(context, new PastelSlimeModel(context.bakeLayer(LAYER)), 0.45F);
		this.addLayer(new PastelSlimeJellyLayer(this, context));
	}

	static Identifier texture(int variant) {
		return TEXTURES[variant >= 0 && variant < TEXTURES.length ? variant : PastelSlime.PINK];
	}

	@Override
	public PastelSlimeRenderState createRenderState() {
		return new PastelSlimeRenderState();
	}

	@Override
	public void extractRenderState(PastelSlime slime, PastelSlimeRenderState state, float partialTick) {
		super.extractRenderState(slime, state, partialTick);
		state.variant = slime.getVariant();
		state.squish = slime.getSquish(partialTick);
		state.tamed = slime.isTame();
		state.sitting = slime.isInSittingPose();
	}

	@Override
	public Identifier getTextureLocation(PastelSlimeRenderState state) {
		return texture(state.variant);
	}
}
