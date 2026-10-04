package dev.goober.transdimension.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the trans enderman model needs each frame. */
public class TransEndermanRenderState extends LivingEntityRenderState {
	/** Angry (stared at): the jaw drops open. */
	public boolean creepy;
	/** 0..1 through an arm swing when it hits something. */
	public float attackAnim;
}
