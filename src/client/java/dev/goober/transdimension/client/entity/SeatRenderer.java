package dev.goober.transdimension.client.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

import dev.goober.transdimension.entity.Seat;

/** Draws nothing: a {@link Seat} is invisible, only the player sitting on it shows. */
public class SeatRenderer extends EntityRenderer<Seat, EntityRenderState> {
	public SeatRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}
}
