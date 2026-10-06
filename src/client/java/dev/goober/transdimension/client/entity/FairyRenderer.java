package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.client.compat.ShaderCompat;
import dev.goober.transdimension.entity.Fairy;

/**
 * Wild fairies: the fairy light in one colour, full bright (a little less under a shader pack, see
 * {@link ShaderCompat#glowLight()}), a little bigger than the jar's so you can spot one.
 */
public class FairyRenderer extends MobRenderer<Fairy, FairyRenderState, FairyLightModel> {
	/** By colour: blue, pink, white (the same textures as the Fairy Jar's light). */
	private static final Identifier[] TEXTURES = {
			TransDimension.id("textures/entity/fairy_light/blue.png"),
			TransDimension.id("textures/entity/fairy_light/pink.png"),
			TransDimension.id("textures/entity/fairy_light/white.png")};

	public FairyRenderer(EntityRendererProvider.Context context) {
		this(context, false);
	}

	/**
	 * {@code inJar}: the Fairy Jar's little fairy (JarFairyRenderer), drawn cutout (a see-through model behind the jar's
	 * see-through glass vanishes) with no glowing halo and no shadow.
	 */
	protected FairyRenderer(EntityRendererProvider.Context context, boolean inJar) {
		super(context, new FairyLightModel(context.bakeLayer(FairyLightModel.LAYER), inJar), inJar ? 0.0F : 0.15F);
		if (!inJar) {
			this.addLayer(new FairyHaloLayer(this, context));
		}
	}

	static Identifier texture(int colour) {
		return TEXTURES[colour >= 0 && colour < TEXTURES.length ? colour : 0];
	}

	@Override
	public FairyRenderState createRenderState() {
		return new FairyRenderState();
	}

	@Override
	public void extractRenderState(Fairy fairy, FairyRenderState state, float partialTick) {
		super.extractRenderState(fairy, state, partialTick);
		state.colour = fairy.getColour();
	}

	@Override
	public Identifier getTextureLocation(FairyRenderState state) {
		return texture(state.colour);
	}

	@Override
	protected void scale(FairyRenderState state, PoseStack poseStack) {
		poseStack.scale(1.25F, 1.25F, 1.25F);
	}

	@Override
	protected int getBlockLightLevel(Fairy fairy, BlockPos pos) {
		return ShaderCompat.glowLight();
	}
}
