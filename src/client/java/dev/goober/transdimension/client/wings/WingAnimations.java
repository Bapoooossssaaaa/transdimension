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
 * Per-player wing animation state on the client: how far the wings are spread (eased over a few ticks, so they
 * unfold and fold smoothly) and when the last flap or launch happened. {@link #pose} turns that into angles.
 *
 * <ul>
 *     <li>standing: folded down the back, breathing gently</li>
 *     <li>crouching on the ground: half open and trembling (charging a launch)</li>
 *     <li>gliding: spread wide with slow waves rippling out to the tips</li>
 *     <li>flap or launch: one big downstroke and recovery, the outer feathers trailing behind</li>
 * </ul>
 */
public final class WingAnimations {
	private static final int FLAP_TICKS = 12;
	private static final int LAUNCH_TICKS = 16;
	private static final Map<Integer, State> STATES = new HashMap<>();

	private WingAnimations() {
	}

	private static final class State {
		float spread;
		float previousSpread;
		long flapStart = Long.MIN_VALUE;
		int flapLength = FLAP_TICKS;
		boolean seen;
	}

	/** Eases every player's spread towards what they are doing; forgets players that left. */
	public static void tick(ClientLevel level) {
		STATES.values().forEach(state -> state.seen = false);
		for (Player player : level.players()) {
			if (!TransWings.isWearing(player)) {
				continue;
			}
			State state = STATES.computeIfAbsent(player.getId(), id -> new State());
			state.seen = true;
			state.previousSpread = state.spread;
			float target = player.isFallFlying() ? 1.0F : (player.isCrouching() && player.onGround()) ? 0.45F : 0.0F;
			state.spread += (target - state.spread) * 0.22F;
		}
		STATES.values().removeIf(state -> !state.seen);
	}

	/** Starts a flap (or the bigger launch flap) animation on a player. */
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
		float spread = state == null ? 0.0F : Mth.lerp(partialTick, state.previousSpread, state.spread);
		float age = entity.tickCount + partialTick;

		float rootX = 0.0F;
		float rootY = Mth.lerp(spread, WingPose.FOLDED.rootY(), WingPose.SPREAD.rootY());
		float rootZ = Mth.lerp(spread, WingPose.FOLDED.rootZ(), WingPose.SPREAD.rootZ());
		float outerZ = Mth.lerp(spread, WingPose.FOLDED.outerZ(), WingPose.SPREAD.outerZ());

		// Breathing while folded; slow waves while gliding, travelling out to the tips.
		rootZ += Mth.sin(age * 0.09F) * 0.035F * (1.0F - spread);
		rootZ += Mth.sin(age * 0.22F) * 0.07F * spread;
		outerZ += Mth.sin(age * 0.22F - 0.7F) * 0.1F * spread;
		// Charging a launch: half open, swept back and trembling.
		if (spread > 0.2F && spread < 0.7F && !entity.isFallFlying()) {
			rootY += 0.3F;
			rootZ += Mth.sin(age * 2.3F) * 0.03F;
		}
		// A flap: down and back up again, the outer panel lagging behind for a whip-like stroke.
		if (state != null && entity.level() != null) {
			float t = (entity.level().getGameTime() - state.flapStart + partialTick) / state.flapLength;
			if (t >= 0.0F && t < 1.0F) {
				float strength = Math.max(spread, 0.65F) * (state.flapLength == LAUNCH_TICKS ? 1.25F : 1.0F);
				float stroke = Mth.sin(t * Mth.TWO_PI);
				rootZ -= stroke * 0.8F * strength;
				outerZ -= Mth.sin(t * Mth.TWO_PI - 0.6F) * 0.55F * strength;
				rootX += stroke * 0.12F * strength;
			}
		}
		return new WingPose(rootX, rootY, rootZ, outerZ);
	}
}
