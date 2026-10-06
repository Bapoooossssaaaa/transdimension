package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * The little fairy in a Fairy Jar (JarFairy): a wild fairy's light and wings at not much more than a third of a wild
 * one's size, so it can flit about inside the glass without its wings poking through. It's drawn cutout, with no halo
 * and no shadow (see FairyRenderer).
 */
public class JarFairyRenderer extends FairyRenderer {
	public JarFairyRenderer(EntityRendererProvider.Context context) {
		super(context, true);
	}

	@Override
	protected void scale(FairyRenderState state, PoseStack poseStack) {
		poseStack.scale(0.45F, 0.45F, 0.45F);
	}
}
