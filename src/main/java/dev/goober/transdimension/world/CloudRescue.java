package dev.goober.transdimension.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import dev.goober.transdimension.entity.CloudBoat;
import dev.goober.transdimension.entity.CloudTurtle;
import dev.goober.transdimension.entity.Cloudy;
import dev.goober.transdimension.registry.ModEntities;

/**
 * The Cloud Realm's safety net. Nobody takes damage from the void there. A long fall turns into a gentle drift (slow
 * falling), and a player who falls off into empty sky (nothing under them all the way down) or below the islands (under
 * y {@value #CATCH_Y}) is caught: a white boat ({@link CloudBoat}) appears under them and holds them, then the
 * cloud turtle ({@link CloudTurtle}) rides down out of the sky on a Cloudy, hooks the boat on his line, and flies it back
 * up the way they fell and over to the last ground they stood on (or the arrival cloud), where he sets it down. He waits
 * for them to hop out (sneak) before flying off with his boat.
 *
 * <p>The whole rescue is moved from here each tick (the boat, the Cloudy and the turtle have no minds of their own) and
 * lives in memory only: the turtle and his Cloudy aren't saved, and the boat removes itself once no rescue holds it, so
 * a rescue cut short by a restart or a logout leaves nothing behind (and the player, if still falling, is simply caught
 * again).
 */
public final class CloudRescue {
	/** How far above the bottom of the world a falling player is caught (wherever they are). */
	public static final int CATCH_Y = 24;
	/** How far a player falls into empty sky before they're caught, or a long fall slows to a drift. */
	private static final double FALL_BEFORE_CATCH = 10.0;
	private static final double FALL_BEFORE_DRIFT = 6.0;
	/** The length of the turtle's line, from the Cloudy down to the boat. */
	private static final double LINE = 5.0;
	/** How high above the landing spot the boat flies in. */
	private static final double OVER = 3.0;
	/** Blocks a tick, carrying. */
	private static final double SPEED = 0.8;
	private static final int DESCEND = 50;
	private static final int MAX_WAIT = 20 * 30;
	private static final int LEAVE = 60;

	private static final Map<UUID, Rescue> RESCUES = new HashMap<>();
	private static final Map<UUID, Vec3> LAST_GROUND = new HashMap<>();

	private CloudRescue() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !CloudRealm.isCloudRealm(player.level())) {
				return true;
			}
			if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
				return false;
			}
			// Carried home through an island, nobody suffocates.
			return !(source.is(DamageTypes.IN_WALL) && RESCUES.containsKey(player.getUUID()));
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (CloudRealm.isCloudRealm(level)) {
				tick(level);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Rescue rescue = RESCUES.remove(handler.player.getUUID());
			if (rescue != null) {
				rescue.finish();
			}
			LAST_GROUND.remove(handler.player.getUUID());
		});
	}

	private static void tick(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			if (player.onGround() && !player.isPassenger() && !player.isSpectator()) {
				LAST_GROUND.put(id, player.position());
			}
			boolean free = !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying()
					&& !player.onGround();
			if (free && player.getDeltaMovement().y < -0.3 && player.fallDistance > FALL_BEFORE_DRIFT) {
				// A long fall becomes a gentle drift down.
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
			}
			if (free && !RESCUES.containsKey(id) && (player.getY() < level.getMinY() + CATCH_Y
					|| player.fallDistance > FALL_BEFORE_CATCH && nothingBelow(level, player))) {
				RESCUES.put(id, new Rescue(level, player, landing(level, id)));
			}
		}
		Iterator<Rescue> iterator = RESCUES.values().iterator();
		while (iterator.hasNext()) {
			Rescue rescue = iterator.next();
			if (rescue.level == level && rescue.advance()) {
				rescue.finish();
				iterator.remove();
			}
		}
	}

	/** True if there's no block at all under the player, all the way down: they've fallen off into empty sky. */
	private static boolean nothingBelow(ServerLevel level, ServerPlayer player) {
		BlockPos feet = player.blockPosition();
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ()) <= level.getMinY()
				|| level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ()) > feet.getY() + 1
				&& emptyUnder(level, feet);
	}

	/** No block under {@code feet} down to the bottom of the world (for when an island hangs overhead). */
	private static boolean emptyUnder(ServerLevel level, BlockPos feet) {
		BlockPos.MutableBlockPos cursor = feet.mutable();
		for (int y = feet.getY() - 1; y >= level.getMinY(); y--) {
			if (!level.getBlockState(cursor.setY(y)).isAir()) {
				return false;
			}
		}
		return true;
	}

	/** Where to set a rescued player down: the last ground they stood on, if it's still there, or the arrival cloud. */
	private static Vec3 landing(ServerLevel level, UUID id) {
		Vec3 ground = LAST_GROUND.get(id);
		if (ground != null) {
			BlockPos feet = BlockPos.containing(ground);
			if (!level.getBlockState(feet.below()).isAir() && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
				return ground;
			}
		}
		CloudRealm.ensureArrival(level);
		return CloudRealm.ARRIVAL;
	}

	/** One rescue: catch, carry, set down, wait, leave. */
	private static final class Rescue {
		final ServerLevel level;
		final ServerPlayer player;
		final CloudBoat boat;
		@Nullable
		final Cloudy cloud;
		@Nullable
		final CloudTurtle turtle;
		/** The boat's way home: up the way the player fell, across to the landing spot, and down onto it. */
		final List<Vec3> route = new ArrayList<>();
		final Vec3 sky;
		Vec3 boatAt;
		int waypoint;
		int age;
		int landedAt = -1;
		int leftAt = -1;

		Rescue(ServerLevel level, ServerPlayer player, Vec3 landing) {
			this.level = level;
			this.player = player;
			this.boatAt = player.position().subtract(0.0, 0.1875, 0.0);
			this.boat = new CloudBoat(ModEntities.CLOUD_BOAT, level);
			this.boat.setPos(this.boatAt.x, this.boatAt.y, this.boatAt.z);
			level.addFreshEntity(this.boat);
			player.resetFallDistance();
			player.setDeltaMovement(Vec3.ZERO);
			player.startRiding(this.boat);

			this.sky = this.boatAt.add(0.0, LINE + 36.0, 0.0);
			this.cloud = ModEntities.CLOUDY.create(level, EntitySpawnReason.EVENT);
			this.turtle = ModEntities.CLOUD_TURTLE.create(level, EntitySpawnReason.EVENT);
			if (this.cloud != null && this.turtle != null) {
				this.cloud.snapTo(this.sky.x, this.sky.y, this.sky.z, 0.0F, 0.0F);
				this.turtle.snapTo(this.sky.x, this.sky.y, this.sky.z, 0.0F, 0.0F);
				level.addFreshEntity(this.cloud);
				level.addFreshEntity(this.turtle);
				this.turtle.startRiding(this.cloud);
				this.boat.setPuller(this.turtle);
			}

			double cruise = Math.max(landing.y + OVER, this.boatAt.y + 4.0);
			this.route.add(new Vec3(this.boatAt.x, cruise, this.boatAt.z));
			this.route.add(new Vec3(landing.x, cruise, landing.z));
			this.route.add(landing);

			player.sendOverlayMessage(Component.translatable("message.transdimension.rescue.caught"));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5F, 1.4F);
		}

		/** Runs one tick; true once the rescue is over. */
		boolean advance() {
			int t = this.age++;
			this.boat.claim();
			if (this.player.isRemoved() || !this.player.isAlive() || this.player.level() != this.level || this.boat.isRemoved()) {
				return true;
			}
			if (this.leftAt >= 0) {
				// Off he flies with his boat, up and away.
				int k = t - this.leftAt;
				this.boatAt = this.boatAt.add(0.15, 0.25 + k * 0.01, 0.15);
				this.carry(this.boatAt.add(0.15, 0.25, 0.15));
				return k >= LEAVE;
			}
			if (this.landedAt < 0) {
				// Until he's home, the player stays in the boat.
				if (!this.player.isPassenger() && !this.player.startRiding(this.boat)) {
					if (this.player.onGround()) {
						// They hopped out onto solid ground: he's done.
						this.landedAt = t;
						this.leftAt = t;
						return false;
					}
					// They can't get in just now (still sneaking, or just jumped out): the boat keeps under them, with the
					// turtle above it, until they can, and the way home starts again from there.
					this.boatAt = this.player.position().subtract(0.0, 0.1875, 0.0);
					this.carry(Vec3.ZERO);
					this.route.set(0, new Vec3(this.boatAt.x, Math.max(this.route.get(0).y, this.boatAt.y + 4.0), this.boatAt.z));
					this.waypoint = 0;
					return false;
				}
				if (t < DESCEND) {
					float k = Mth.clamp(t / (float) DESCEND, 0.0F, 1.0F);
					float eased = k * k * (3.0F - 2.0F * k);
					this.hold(this.sky.lerp(this.boatAt.add(0.0, LINE, 0.0), eased), this.boatAt.subtract(this.sky));
					if (t == DESCEND - 8) {
						this.level.playSound(null, this.boatAt.x, this.boatAt.y, this.boatAt.z, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.2F, 1.0F);
					}
					return false;
				}
				Vec3 target = this.route.get(this.waypoint);
				Vec3 to = target.subtract(this.boatAt);
				double distance = to.length();
				Vec3 step = distance > SPEED ? to.scale(SPEED / distance) : to;
				this.boatAt = this.boatAt.add(step);
				this.carry(step);
				if (distance <= SPEED) {
					this.waypoint++;
					if (this.waypoint >= this.route.size()) {
						this.landedAt = t;
						this.player.sendOverlayMessage(Component.translatable("message.transdimension.rescue.landed"));
						this.level.playSound(null, this.boatAt.x, this.boatAt.y, this.boatAt.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL,
								1.5F, 1.8F);
					}
				}
				return false;
			}
			// Home: he waits for them to hop out (and gives them a nudge after a while).
			this.carry(Vec3.ZERO);
			boolean aboard = this.player.getVehicle() == this.boat;
			if (aboard && t - this.landedAt >= MAX_WAIT) {
				this.player.stopRiding();
				aboard = false;
			}
			if (aboard && (t - this.landedAt) % 60 == 59) {
				this.player.sendOverlayMessage(Component.translatable("message.transdimension.rescue.landed"));
			}
			if (!aboard) {
				this.leftAt = t;
				this.level.playSound(null, this.boatAt.x, this.boatAt.y, this.boatAt.z, SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 1.2F, 1.2F);
			}
			return false;
		}

		/** The Cloudy waits at {@code at}, turned the way it came ({@code heading}); the boat stays where it is. */
		private void hold(Vec3 at, Vec3 heading) {
			this.boat.setPos(this.boatAt.x, this.boatAt.y, this.boatAt.z);
			this.boat.setDeltaMovement(Vec3.ZERO);
			this.placeCloud(at, heading);
		}

		/** The boat moves to {@link #boatAt} (having moved by {@code step}) and the Cloudy keeps the line above it. */
		private void carry(Vec3 step) {
			this.boat.setPos(this.boatAt.x, this.boatAt.y, this.boatAt.z);
			this.boat.setDeltaMovement(step);
			if (step.horizontalDistanceSqr() > 1.0E-4) {
				this.boat.setYRot((float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90.0F);
			}
			this.placeCloud(this.boatAt.add(0.0, LINE, 0.0), step);
		}

		private void placeCloud(Vec3 at, Vec3 heading) {
			if (this.cloud == null) {
				return;
			}
			this.cloud.setPos(at.x, at.y, at.z);
			this.cloud.setDeltaMovement(Vec3.ZERO);
			this.cloud.resetFallDistance();
			if (heading.horizontalDistanceSqr() > 1.0E-4) {
				float yaw = (float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90.0F;
				this.cloud.setYRot(Mth.approachDegrees(this.cloud.getYRot(), yaw, 8.0F));
			}
			this.cloud.yBodyRot = this.cloud.getYRot();
			this.cloud.yHeadRot = this.cloud.getYRot();
			if (this.turtle != null) {
				this.turtle.setYRot(this.cloud.getYRot());
				this.turtle.yBodyRot = this.cloud.getYRot();
				this.turtle.yHeadRot = this.cloud.getYRot();
			}
		}

		/** Everything goes in a puff of cloud. */
		void finish() {
			if (this.player.getVehicle() == this.boat) {
				this.player.stopRiding();
			}
			for (Entity entity : new Entity[] {this.turtle, this.cloud, this.boat}) {
				if (entity != null && !entity.isRemoved()) {
					this.level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.5, entity.getZ(), 12, 0.4, 0.3, 0.4, 0.02);
					entity.discard();
				}
			}
		}
	}
}
