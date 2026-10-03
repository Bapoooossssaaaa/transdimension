package dev.goober.transdimension.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the Silly Cat model needs to know each frame. */
public class SillyCatRenderState extends LivingEntityRenderState {
	/** 0 = tongue in, 1 = full lick. */
	public float lickProgress;
	/** Keeps the tip of the tongue out ("blep"). */
	public boolean blep;
}
