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
	private static final Identifier PINK = TransDimension.id("textures/entity/pastel_slime/pink.png");
	private static final Identifier BLUE = TransDimension.id("textures/entity/pastel_slime/blue.png");

	public PastelSlimeRenderer(EntityRendererProvider.Context context) {
		super(context, new PastelSlimeModel(context.bakeLayer(LAYER)), 0.45F);
		this.addLayer(new PastelSlimeJellyLayer(this, context));
	}

	static Identifier texture(int variant) {
		return variant == PastelSlime.BLUE ? BLUE : PINK;
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
