package dev.goober.transdimension.client.entity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.Kira;

/**
 * Draws Kira on Maddie's player-shaped model (MaddieRenderer's slim-armed mesh) in her own skin
 * (textures/entity/kira/kira.png, from tools/generate_textures.py {@code kira_skin()}), with a comically big head.
 */
public class KiraRenderer extends MobRenderer<Kira, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/kira/kira.png");
	/** How many times a player's size Kira's head is. It grows from her neck, so it still sits on her shoulders. */
	private static final float HEAD_SCALE = 2.0F;
	/** Lifts her name above her big head instead of leaving it inside (the head grows half a block taller). */
	private static final double NAME_TAG_LIFT = 0.55;

	public KiraRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(MaddieRenderer.LAYER)) {
			@Override
			public void setupAnim(HumanoidRenderState state) {
				super.setupAnim(state);
				// The hat layer is the head's child, so it grows with it.
				this.head.xScale = HEAD_SCALE;
				this.head.yScale = HEAD_SCALE;
				this.head.zScale = HEAD_SCALE;
			}
		}, 0.5F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public void extractRenderState(Kira kira, HumanoidRenderState state, float partialTick) {
		super.extractRenderState(kira, state, partialTick);
		if (state.nameTagAttachment != null) {
			state.nameTagAttachment = state.nameTagAttachment.add(0.0, NAME_TAG_LIFT, 0.0);
		}
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}
