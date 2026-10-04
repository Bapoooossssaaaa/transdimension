package dev.goober.transdimension.client.wings;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import dev.goober.transdimension.item.TransWings;
import dev.goober.transdimension.network.WingActionPayload;

/**
 * Per-player wing animation state on the client, eased over a few ticks so the wings unfold and fold smoothly, and
 * {@link #pose} turns it into angles for {@link TransWingsModel}.
 *
 * <ul>
 *     <li>standing or walking: folded down the back, breathing gently</li>
 *     <li>crouching on the ground: half open, swept back and trembling (charging a launch)</li>
 *     <li>in the air without gliding: raised and open</li>
 *     <li>gliding: spread wide, slow ripples running out to the tips; crouching while gliding (hovering) raises and
 *     cups them with a fast flutter</li>
 *     <li>flap or launch: one full wingbeat, down then up, the hand and feather tips trailing behind</li>
 * </ul>
 *
 * <p>Wingbeats always move the tips up and down in the world. Standing upright the wing's flat side faces backwards,
 * so the beat is a rotation within the plane of the back; gliding face down the flat side faces the sky, so the beat
 * is a rotation across it. The two blend as the player tips into a glide.
 */
public final class WingAnimations {
	private static final int FLAP_TICKS = 12;
	private static final int LAUNCH_TICKS = 16;
	/** Ticks in the air before the wings open fully (short hops only make them flutter a little). */
	private static final int AIRBORNE_TICKS = 8;
	private static final Map<Integer, State> STATES = new HashMap<>();

	private WingAnimations() {
	}

	private static final class State {
		float open;
		float previousOpen;
		float glide;
		float previousGlide;
		float hover;
		float previousHover;
		int airTicks;
		long flapStart = Long.MIN_VALUE;
		int flapLength = FLAP_TICKS;
		boolean seen;
	}

	/** Eases every winged player's state towards what they are doing; forgets players that left. */
	public static void tick(ClientLevel level) {
		STATES.values().forEach(state -> state.seen = false);
		for (Player player : level.players()) {
			if (!TransWings.isWearing(player)) {
				continue;
			}
			State state = STATES.computeIfAbsent(player.getId(), id -> new State());
			state.seen = true;
			state.previousOpen = state.open;
			state.previousGlide = state.glide;
			state.previousHover = state.hover;

			boolean gliding = player.isFallFlying();
			boolean grounded = player.onGround();
			state.airTicks = grounded || gliding ? 0 : state.airTicks + 1;
			float open;
			if (gliding) {
				open = 1.0F;
			} else if (grounded) {
				open = player.isCrouching() ? 0.45F : 0.0F;
			} else {
				open = state.airTicks > AIRBORNE_TICKS || player.getDeltaMovement().y < -0.4 ? 1.0F : 0.3F;
			}
			state.open += (open - state.open) * 0.2F;
			state.glide += ((gliding ? 1.0F : 0.0F) - state.glide) * 0.2F;
			state.hover += ((gliding && player.isShiftKeyDown() ? 1.0F : 0.0F) - state.hover) * 0.25F;
		}
		STATES.values().removeIf(state -> !state.seen);
	}

	/** Starts a wingbeat (or the bigger launch beat) on a player. */
	public static void flap(int entityId, int action, long gameTime) {
		State state = STATES.computeIfAbsent(entityId, id -> new State());
		state.flapStart = gameTime;
		state.flapLength = action == WingActionPayload.LAUNCH ? LAUNCH_TICKS : FLAP_TICKS;
	}

	/** The wing angles for this frame, or null when the entity wears no Trans Wings. */
	@Nullable
	public static WingPose pose(LivingEntity entity, float partialTick) {
		if (!TransWings.isWearing(entity)) {
			return null;
		}
		State state = STATES.get(entity.getId());
		float open = state == null ? 0.0F : Mth.lerp(partialTick, state.previousOpen, state.open);
		float glide = state == null ? 0.0F : Mth.lerp(partialTick, state.previousGlide, state.glide);
		float hover = state == null ? 0.0F : Mth.lerp(partialTick, state.previousHover, state.hover);
		float age = entity.tickCount + partialTick;

		WingPose base = WingPose.FOLDED.lerp(WingPose.AIRBORNE, open).lerp(WingPose.GLIDING, glide);
		float rootX = base.rootX();
		float rootY = base.rootY();
		float rootZ = base.rootZ();
		float handY = base.handY();
		float handZ = base.handZ();
		float fan = base.fan();
		float flex = 0.0F;

		// Breathing while folded; slow ripples while gliding, running out to the tips.
		float folded = 1.0F - open;
		rootZ += Mth.sin(age * 0.08F) * 0.03F * folded;
		rootX += Mth.sin(age * 0.08F + 1.0F) * 0.02F * folded;
		rootY += Mth.sin(age * 0.2F) * 0.04F * glide;
		handY += Mth.sin(age * 0.2F - 0.8F) * 0.05F * glide;
		flex += Mth.sin(age * 0.2F - 1.4F) * 0.06F * glide;
		// Charging a launch on the ground: half open, swept back and trembling.
		if (entity.onGround() && entity.isCrouching()) {
			rootY += 0.25F * open;
			rootZ += Mth.sin(age * 2.3F) * 0.03F;
		}
		// Hovering: tips raised and cupped forward, with a quick flutter.
		rootY += hover * (0.5F - 0.2F * Mth.sin(age * 0.9F));
		handY -= hover * 0.35F;

		if (state != null && entity.level() != null) {
			float t = (entity.level().getGameTime() - state.flapStart + partialTick) / state.flapLength;
			if (t >= 0.0F && t < 1.0F) {
				float strength = state.flapLength == LAUNCH_TICKS ? 1.2F : 1.0F;
				float lag = t - 0.1F < 0.0F ? t + 0.9F : t - 0.1F;
				float upright = 1.0F - glide;
				// Upright: a shallow downstroke and a big upstroke within the plane of the back.
				rootZ -= upright * strength * stroke(t, 0.55F, 0.85F);
				handZ -= upright * strength * stroke(lag, 0.3F, 0.4F);
				// Gliding: a strong downstroke and a lighter recovery across the plane.
				rootY -= glide * strength * stroke(t, 0.8F, 0.6F);
				handY -= glide * strength * stroke(lag, 0.45F, 0.35F);
				flex += glide * strength * stroke(lag, 0.3F, 0.25F);
				float s = Mth.sin(t * Mth.TWO_PI);
				// Feathers spread on the downstroke and close a little on the way back up.
				fan = Math.min(1.0F, fan + 0.15F * Math.max(0.0F, s)) - 0.18F * Math.max(0.0F, -s);
			}
		}
		return new WingPose(rootX, rootY, rootZ, handY, handZ, fan, flex);
	}

	/** One wingbeat over t in [0, 1): a downstroke (positive, peaking at t = 0.25) then an upstroke (negative). */
	private static float stroke(float t, float down, float up) {
		float s = Mth.sin(t * Mth.TWO_PI);
		return s * (s > 0.0F ? down : up);
	}
}
