package dev.goober.transdimension.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the Trans Fairy model needs each frame. */
public class TransFairyRenderState extends LivingEntityRenderState {
	/** {@code TransFairy.HOVER}, {@code VOLLEY}, {@code SWOOP}... */
	public int action;
	/** Ticks since the current action began. */
	public float actionTime;
}
