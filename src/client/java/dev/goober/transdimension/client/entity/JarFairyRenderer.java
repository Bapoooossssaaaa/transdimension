package dev.goober.transdimension.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;

import dev.goober.transdimension.block.FairyJarBlock;
import dev.goober.transdimension.entity.Fairy;

/**
 * The little fairy in a Fairy Jar (JarFairy): a wild fairy's light and wings at not much more than a third of a wild
 * one's size, so it can drift about inside the glass without its wings poking through. It's drawn cutout, with no halo
 * and no shadow, its wings beating slowly (see FairyRenderer and FairyLightModel), and lit at the jar's own light level
 * rather than full bright: shader packs such as BSL light a full-bright entity at almost twice its block light, which
 * blew the fairy out to a flat white blob.
 */
public class JarFairyRenderer extends FairyRenderer {
	public JarFairyRenderer(EntityRendererProvider.Context context) {
		super(context, true);
	}

	@Override
	protected void scale(FairyRenderState state, PoseStack poseStack) {
		poseStack.scale(0.45F, 0.45F, 0.45F);
	}

	@Override
	protected int getBlockLightLevel(Fairy fairy, BlockPos pos) {
		return FairyJarBlock.LIGHT;
	}
}
