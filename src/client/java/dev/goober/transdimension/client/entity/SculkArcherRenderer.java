package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.SculkArcher;

/**
 * Draws a sculk archer like a sculk person (SculkPersonRenderer's player-shaped mesh and skin), with their bow in hand: a
 * humanoid mob renderer draws what a mob holds. While they aim ({@link SculkArcher#isAiming}) they hold it up with both
 * arms, as a skeleton does, following their head as it tilts up at the Trans Fairy.
 */
public class SculkArcherRenderer extends HumanoidMobRenderer<SculkArcher, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/sculk_person/sculk_person.png");

	public SculkArcherRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(SculkPersonRenderer.LAYER)), 0.5F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public void extractRenderState(SculkArcher archer, HumanoidRenderState state, float partialTick) {
		super.extractRenderState(archer, state, partialTick);
		if (archer.isAiming()) {
			state.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
		}
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}
