package dev.goober.transdimension.client.entity;

import net.minecraft.client.renderer.entity.CodRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.fish.Cod;

import dev.goober.transdimension.TransDimension;

/**
 * Trans fish are drawn exactly like vanilla cod (same model, tail wag, wiggle and flopping on land), wearing the cod
 * texture in trans stripes. Each fish picks one of three colourways from its UUID, so a school comes out mixed.
 */
public class TransFishRenderer extends CodRenderer {
	private static final Identifier[] TEXTURES = {
			TransDimension.id("textures/entity/trans_fish/blue.png"),
			TransDimension.id("textures/entity/trans_fish/pink.png"),
			TransDimension.id("textures/entity/trans_fish/white.png")};

	public TransFishRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Cod fish, LivingEntityRenderState state, float partialTick) {
		super.extractRenderState(fish, state, partialTick);
		if (state instanceof State fishState) {
			fishState.colourway = Math.floorMod(fish.getUUID().hashCode(), TEXTURES.length);
		}
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURES[state instanceof State fishState ? fishState.colourway : 0];
	}

	/** A cod's render state plus the colourway this fish wears. */
	public static class State extends LivingEntityRenderState {
		int colourway;
	}
}
