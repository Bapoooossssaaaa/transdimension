package dev.goober.transdimension.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** A wild fairy's render state: which colour it glows ({@code Fairy.BLUE}, {@code PINK} or {@code WHITE}). */
public class FairyRenderState extends LivingEntityRenderState {
	public int colour;
}
