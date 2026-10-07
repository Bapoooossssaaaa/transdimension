package dev.goober.transdimension.client.entity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.client.GlowGeometry;
import dev.goober.transdimension.entity.CloudBoat;

/**
 * Draws the {@link CloudBoat}: vanilla's boat model in white (textures/entity/boat/cloud.png, tools/generate_textures.py
 * whitens the pale oak boat), placed the way vanilla places its boats, hanging in a sling of four short golden cords
 * from the cloud turtle's golden lead, which sags up to his hand and is striped in two golds like a lead.
 */
public class CloudBoatRenderer extends EntityRenderer<CloudBoat, CloudBoatRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(TransDimension.id("boat/cloud"), "main");
	private static final Identifier TEXTURE = TransDimension.id("textures/entity/boat/cloud.png");
	private static final int GOLD = 0xFFF2C14E;
	private static final int DARK_GOLD = 0xFFBE8726;
	/** Where the sling's cords meet, above the middle of the boat. */
	private static final double HOOK = 2.4;
	/** The lead's stripes. */
	private static final int SEGMENTS = 24;

	private final BoatModel model;

	public CloudBoatRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new BoatModel(context.bakeLayer(LAYER));
		this.shadowRadius = 0.8F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CloudBoat boat, State state, float partialTick) {
		super.extractRenderState(boat, state, partialTick);
		state.yRot = Mth.rotLerp(partialTick, boat.yRotO, boat.getYRot());
		Entity puller = boat.getPuller();
		if (puller == null) {
			state.hand = null;
			return;
		}
		// His right hand, reaching down as he sits on his Cloudy (CloudTurtleRenderer): a little in front of him and to his
		// right, about level with his seat.
		float yaw = puller instanceof LivingEntity living ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : puller.getYRot();
		double rad = yaw * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
		Vec3 right = new Vec3(-Math.cos(rad), 0.0, -Math.sin(rad));
		state.hand = puller.getPosition(partialTick).add(0.0, 0.3, 0.0).add(forward.scale(0.42)).add(right.scale(0.35))
				.subtract(boat.getPosition(partialTick));
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState camera) {
		// vanilla's boat placement (AbstractBoatRenderer)
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.375F, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
		nodeCollector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
				state.outlineColor, null);
		poseStack.popPose();

		if (state.hand != null) {
			// The sling and the lead, drawn from the boat's own position, lit by the world like the boat.
			GlowGeometry lines = new GlowGeometry(Vec3.ZERO, false).lit(state.lightCoords);
			Vec3 hook = new Vec3(0.0, HOOK, 0.0);
			double rad = state.yRot * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
			Vec3 right = new Vec3(-Math.cos(rad), 0.0, -Math.sin(rad));
			for (int corner = 0; corner < 4; corner++) {
				Vec3 at = forward.scale((corner & 1) == 0 ? 0.8 : -0.8).add(right.scale((corner & 2) == 0 ? 0.55 : -0.55)).add(0.0, 0.55, 0.0);
				lines.beam(at, hook, 0.02, 0.0, DARK_GOLD);
			}
			// The lead sags between the hook and his hand, more the further apart across they are, in alternating golds.
			Vec3 hand = state.hand;
			double slack = Math.min(0.9, 0.05 + 0.15 * Math.sqrt(hand.x * hand.x + hand.z * hand.z));
			Vec3 last = hook;
			for (int i = 1; i <= SEGMENTS; i++) {
				double t = i / (double) SEGMENTS;
				Vec3 at = hook.lerp(hand, t).subtract(0.0, slack * 4.0 * t * (1.0 - t), 0.0);
				lines.beam(last, at, 0.035, 0.0, (i & 1) == 0 ? GOLD : DARK_GOLD);
				last = at;
			}
			lines.submit(nodeCollector, poseStack);
		}
		super.submit(state, poseStack, nodeCollector, camera);
	}

	/** A boat's render state (for the model), plus where the turtle's hand is, relative to the boat (null: no line). */
	public static class State extends BoatRenderState {
		@Nullable
		public Vec3 hand;
	}
}
