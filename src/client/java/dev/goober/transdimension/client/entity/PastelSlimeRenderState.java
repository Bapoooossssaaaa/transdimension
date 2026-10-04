package dev.goober.transdimension.client.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the pastel slime model needs each frame. */
public class PastelSlimeRenderState extends LivingEntityRenderState {
	/** {@code PastelSlime.PINK} or {@code PastelSlime.BLUE}. */
	public int variant;
	/** -0.5 squashed flat .. 0 a cube .. 1 stretched tall. */
	public float squish;
	/** Tamed slimes wear a bow. */
	public boolean tamed;
	public boolean sitting;
}
