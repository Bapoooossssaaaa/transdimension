package dev.goober.transdimension.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
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
 * y {@value #CATCH_Y} above the bottom) is caught: a white boat ({@link CloudBoat}) appears under them and holds them,
 * and the cloud turtle ({@link CloudTurtle}), sitting on his Cloudy, comes down out of the sky, hooks the boat on his
 * golden lead and flies it to the nearest land, where he sets it down. He waits for them to hop out (sneak) and flies off
 * with his boat.
 *
 * <p>He never flies through anything. If something hangs over the spot where the player was caught, the boat is first
 * swung out to the nearest open sky (a column with nothing above it, reachable sideways without hitting a block). From
 * there he rises, crosses at a height clear of every block along the way, and comes straight down onto the landing spot,
 * which has open sky above it. Land is the nearest top of a column within {@value #LAND_SEARCH} blocks (loaded ones only)
 * whose highest block is solid ground: not leaves, not a fluid. The way is a line with rounded corners ({@link Path}),
 * flown with eased speed (speeding up from rest, slowing to a stop), and the boat follows the Cloudy's exact path
 * {@value #TRAIL} ticks behind, so it swings along behind him on the lead rather than moving in lockstep.
 *
 * <p>The whole rescue is moved from here each tick (the boat, the Cloudy and the turtle have no minds of their own) and
 * lives in memory only: the turtle isn't saved, and the boat removes itself once no rescue holds it, so a rescue cut
 * short by a restart or a logout leaves nothing behind (and the player, if still falling, is simply caught again).
 */
public final class CloudRescue {
	/** How far above the bottom of the world a falling player is caught (wherever they are). */
	public static final int CATCH_Y = 24;
	/** How far a player falls into empty sky before they're caught, or a long fall slows to a drift. */
	private static final double FALL_BEFORE_CATCH = 10.0;
	private static final double FALL_BEFORE_DRIFT = 6.0;
	/** The lead's length: how far above the boat the Cloudy flies. */
	private static final double LINE = 5.0;
	/** How far round the catch to look for land, and for open sky. */
	private static final int LAND_SEARCH = 64;
	private static final int OPEN_SKY_SEARCH = 24;
	/** Room kept between the boat and the highest block under its way home. */
	private static final double CLEARANCE = 4.0;
	/** How far the corners of the way are rounded off. */
	private static final double CORNER = 3.0;
	/** Flying speed and acceleration, in blocks a tick (and a tick squared). */
	private static final double MAX_SPEED = 0.5;
	private static final double ACCEL = 0.02;
	/** How many ticks the boat trails behind the Cloudy along the same path. */
	private static final int TRAIL = 7;
	/** Ticks the Cloudy takes to come down out of the sky, and how far up it starts. */
	private static final int ARRIVE = 60;
	private static final double ARRIVE_HEIGHT = 30.0;
	private static final int MAX_WAIT = 20 * 30;
	/** Ticks a player can be out of the boat in mid-air before he gives up (and a new rescue catches them). */
	private static final int OVERBOARD = 20;

	private static final Map<UUID, Rescue> RESCUES = new HashMap<>();
	/** Column offsets round a spot, nearest first, out to {@link #LAND_SEARCH}. */
	@Nullable
	private static int[][] nearestFirst;

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
			// Being carried, nobody suffocates (should the boat brush a block).
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
		});
	}

	private static void tick(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			boolean free = !player.isSpectator() && !player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying()
					&& !player.onGround();
			if (free && player.getDeltaMovement().y < -0.3 && player.fallDistance > FALL_BEFORE_DRIFT) {
				// A long fall becomes a gentle drift down.
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
			}
			if (free && !RESCUES.containsKey(id) && (player.getY() < level.getMinY() + CATCH_Y
					|| player.fallDistance > FALL_BEFORE_CATCH && nothingBelow(level, player))) {
				RESCUES.put(id, new Rescue(level, player));
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
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ());
		return top <= level.getMinY() || top > feet.getY() + 1 && emptyUnder(level, feet);
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

	// ------------------------------------------------------------------------------------------------ finding the way

	/** The y just above the highest block (leaves and fluids too) in the column at x, z, or the bottom if it's empty. */
	private static int top(ServerLevel level, double x, double z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
	}

	private static int[][] nearestFirst() {
		if (nearestFirst == null) {
			List<int[]> offsets = new ArrayList<>();
			for (int dx = -LAND_SEARCH; dx <= LAND_SEARCH; dx++) {
				for (int dz = -LAND_SEARCH; dz <= LAND_SEARCH; dz++) {
					if (dx * dx + dz * dz <= LAND_SEARCH * LAND_SEARCH) {
						offsets.add(new int[] {dx, dz});
					}
				}
			}
			offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
			nearestFirst = offsets.toArray(new int[0][]);
		}
		return nearestFirst;
	}

	/**
	 * Where to set the boat down: the nearest column (by distance across) whose highest block is solid ground, not leaves
	 * or a fluid, with room to stand on it; or the arrival cloud if there's none within {@link #LAND_SEARCH} blocks.
	 */
	private static Vec3 nearestLand(ServerLevel level, Vec3 from) {
		int fx = Mth.floor(from.x);
		int fz = Mth.floor(from.z);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int[] offset : nearestFirst()) {
			int x = fx + offset[0];
			int z = fz + offset[1];
			if (!level.isLoaded(cursor.set(x, Mth.floor(from.y), z))) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (top <= level.getMinY() || top >= level.getMaxY() - 2) {
				continue;
			}
			BlockPos ground = new BlockPos(x, top - 1, z);
			BlockState state = level.getBlockState(ground);
			if (state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty() || !state.isFaceSturdy(level, ground, Direction.UP)) {
				continue;
			}
			if (open(level, ground.above()) && open(level, ground.above(2)) && openAbove(level, x + 0.5, z + 0.5, top + 1)) {
				return Vec3.atBottomCenterOf(ground.above());
			}
		}
		CloudRealm.ensureArrival(level);
		return CloudRealm.ARRIVAL;
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
	}

	/** True if no block in {@code box} has anything to bump into (unloaded ground counts as blocked). */
	private static boolean clear(ServerLevel level, AABB box) {
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX),
				Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			if (!level.isLoaded(pos) || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** The space a boat with a player sitting in it takes up, at {@code at}. */
	private static AABB boatSpace(Vec3 at) {
		return new AABB(at.x - 0.8, at.y - 0.1, at.z - 0.8, at.x + 0.8, at.y + 2.4, at.z + 0.8);
	}

	/**
	 * Where the boat can rise from: {@code from} itself if nothing hangs over it, or else the nearest spot at the same
	 * height with nothing over it that the boat can be swung to sideways without hitting a block.
	 */
	private static Vec3 openSky(ServerLevel level, Vec3 from) {
		if (openAbove(level, from.x, from.z, from.y)) {
			return from;
		}
		for (int[] offset : nearestFirst()) {
			if (offset[0] * offset[0] + offset[1] * offset[1] > OPEN_SKY_SEARCH * OPEN_SKY_SEARCH) {
				break;
			}
			Vec3 to = new Vec3(Mth.floor(from.x) + offset[0] + 0.5, from.y, Mth.floor(from.z) + offset[1] + 0.5);
			if (!level.isLoaded(BlockPos.containing(to)) || !openAbove(level, to.x, to.z, from.y)) {
				continue;
			}
			if (sweptClear(level, from, to)) {
				return to;
			}
		}
		return from;
	}

	/** Nothing at or above {@code y} in the column at x, z or the eight round it (unloaded ones count as blocked). */
	private static boolean openAbove(ServerLevel level, double x, double z, double y) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!level.isLoaded(BlockPos.containing(x + dx, y, z + dz)) || top(level, x + dx, z + dz) > y) {
					return false;
				}
			}
		}
		return true;
	}

	/** The boat's space is clear all along the straight line from {@code from} to {@code to}. */
	private static boolean sweptClear(ServerLevel level, Vec3 from, Vec3 to) {
		double distance = from.distanceTo(to);
		int steps = Math.max(1, Mth.ceil(distance * 2.0));
		for (int i = 0; i <= steps; i++) {
			if (!clear(level, boatSpace(from.lerp(to, i / (double) steps)))) {
				return false;
			}
		}
		return true;
	}

	/** The highest block (its top) in a strip two blocks either side of the line across from {@code from} to {@code to}. */
	private static double highestAlong(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 across = new Vec3(to.x - from.x, 0.0, to.z - from.z);
		double length = across.length();
		Vec3 side = length > 1.0E-3 ? new Vec3(-across.z / length, 0.0, across.x / length) : new Vec3(1.0, 0.0, 0.0);
		double highest = level.getMinY();
		int steps = Math.max(1, Mth.ceil(length));
		for (int i = 0; i <= steps; i++) {
			Vec3 at = from.lerp(to, i / (double) steps);
			for (int s = -2; s <= 2; s++) {
				double x = at.x + side.x * s;
				double z = at.z + side.z * s;
				if (level.isLoaded(BlockPos.containing(x, at.y, z))) {
					highest = Math.max(highest, top(level, x, z));
				}
			}
		}
		return highest;
	}

	/**
	 * The boat's way from {@code from} (with open sky above it) to {@code to} (also open above): straight up to a height
	 * clear of every block along the way, across, and straight down.
	 */
	private static List<Vec3> wayHome(ServerLevel level, Vec3 from, Vec3 to) {
		double cruise = Math.max(Math.max(from.y, to.y) + CLEARANCE, highestAlong(level, from, to) + CLEARANCE);
		cruise = Math.min(cruise, level.getMaxY() - LINE - 4.0);
		List<Vec3> way = new ArrayList<>();
		way.add(from);
		way.add(new Vec3(from.x, cruise, from.z));
		way.add(new Vec3(to.x, cruise, to.z));
		way.add(to);
		return way;
	}

	/** The way off after a rescue: up from {@code from} and away in {@code heading}'s direction, clear of every block. */
	private static List<Vec3> wayOff(ServerLevel level, Vec3 from, float heading) {
		double rad = heading * Mth.DEG_TO_RAD;
		Vec3 far = from.add(-Math.sin(rad) * 40.0, 0.0, Math.cos(rad) * 40.0);
		double height = Math.max(from.y + 18.0, highestAlong(level, from, far) + CLEARANCE);
		height = Math.min(height, level.getMaxY() - LINE - 4.0);
		List<Vec3> way = new ArrayList<>();
		way.add(from);
		way.add(new Vec3(from.x, height, from.z));
		way.add(new Vec3(far.x, height + 6.0, far.z));
		return way;
	}

	// ------------------------------------------------------------------------------------------------ paths

	/** A smooth way: the corners of a line of points, rounded off, measured so it can be followed by distance along it. */
	private static final class Path {
		final List<Vec3> points = new ArrayList<>();
		final double[] along;
		final double total;

		Path(List<Vec3> corners, Vec3 offset) {
			List<Vec3> raw = new ArrayList<>();
			for (Vec3 corner : corners) {
				Vec3 at = corner.add(offset);
				if (raw.isEmpty() || raw.get(raw.size() - 1).distanceToSqr(at) > 1.0E-4) {
					raw.add(at);
				}
			}
			for (int i = 0; i < raw.size(); i++) {
				Vec3 at = raw.get(i);
				if (i == 0 || i == raw.size() - 1) {
					this.points.add(at);
					continue;
				}
				// Round the corner: start bending a little before it, along a curve pulled towards it.
				Vec3 back = raw.get(i - 1).subtract(at);
				Vec3 ahead = raw.get(i + 1).subtract(at);
				double r = Math.min(CORNER, Math.min(back.length(), ahead.length()) * 0.45);
				Vec3 in = at.add(back.normalize().scale(r));
				Vec3 out = at.add(ahead.normalize().scale(r));
				for (int k = 0; k <= 6; k++) {
					double t = k / 6.0;
					double u = 1.0 - t;
					this.points.add(in.scale(u * u).add(at.scale(2.0 * u * t)).add(out.scale(t * t)));
				}
			}
			this.along = new double[this.points.size()];
			double length = 0.0;
			for (int i = 1; i < this.points.size(); i++) {
				length += this.points.get(i).distanceTo(this.points.get(i - 1));
				this.along[i] = length;
			}
			this.total = length;
		}

		Vec3 at(double s) {
			if (s <= 0.0 || this.points.size() == 1) {
				return this.points.get(0);
			}
			for (int i = 1; i < this.points.size(); i++) {
				if (s <= this.along[i]) {
					double span = this.along[i] - this.along[i - 1];
					return this.points.get(i - 1).lerp(this.points.get(i), span > 1.0E-6 ? (s - this.along[i - 1]) / span : 1.0);
				}
			}
			return this.points.get(this.points.size() - 1);
		}
	}

	// ------------------------------------------------------------------------------------------------ one rescue

	private enum Stage {
		/** The Cloudy comes down out of the sky above open sky by the boat. */
		ARRIVE,
		/** He swings the boat out from under whatever hangs over it, to below him. */
		HOOK,
		/** Up, across and down to land. */
		CARRY,
		/** Set down: he waits for the player to hop out. */
		LANDED,
		/** Off he flies with his boat. */
		LEAVE
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
		/** Where the player was caught, and where the boat can rise from (open sky). */
		final Vec3 caught;
		final Vec3 rise;
		final Vec3 landing;
		/** Where the Cloudy's path has got to, the last {@link #TRAIL} of them (the boat hangs from the oldest). */
		final Deque<Vec3> trail = new ArrayDeque<>();
		Stage stage = Stage.ARRIVE;
		int stageStart;
		@Nullable
		Path path;
		double along;
		double speed;
		Vec3 cloudAt;
		Vec3 boatAt;
		int age;
		int overboard;
		int landedAt;

		Rescue(ServerLevel level, ServerPlayer player) {
			this.level = level;
			this.player = player;
			this.caught = player.position().subtract(0.0, 0.1875, 0.0);
			this.boatAt = this.caught;
			this.rise = openSky(level, this.caught);
			this.landing = nearestLand(level, this.caught);
			this.boat = new CloudBoat(ModEntities.CLOUD_BOAT, level);
			this.boat.setPos(this.boatAt.x, this.boatAt.y, this.boatAt.z);
			this.boat.setYRot(player.getYRot());
			level.addFreshEntity(this.boat);
			player.resetFallDistance();
			player.setDeltaMovement(Vec3.ZERO);
			player.startRiding(this.boat);

			Vec3 sky = this.rise.add(0.0, LINE + ARRIVE_HEIGHT, 0.0);
			this.cloudAt = new Vec3(sky.x, Math.min(sky.y, level.getMaxY() - 4.0), sky.z);
			this.cloud = ModEntities.CLOUDY.create(level, EntitySpawnReason.EVENT);
			this.turtle = ModEntities.CLOUD_TURTLE.create(level, EntitySpawnReason.EVENT);
			if (this.cloud != null && this.turtle != null) {
				float yaw = player.getYRot() + 180.0F;
				this.cloud.snapTo(this.cloudAt.x, this.cloudAt.y, this.cloudAt.z, yaw, 0.0F);
				this.turtle.snapTo(this.cloudAt.x, this.cloudAt.y, this.cloudAt.z, yaw, 0.0F);
				level.addFreshEntity(this.cloud);
				level.addFreshEntity(this.turtle);
				this.turtle.startRiding(this.cloud);
				this.boat.setPuller(this.turtle);
			}

			player.sendOverlayMessage(Component.translatable("message.transdimension.rescue.caught"));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5F, 1.4F);
		}

		/** Runs one tick; true once the rescue is over. */
		boolean advance() {
			int t = this.age++;
			int k = t - this.stageStart;
			this.boat.claim();
			if (this.player.isRemoved() || !this.player.isAlive() || this.player.level() != this.level || this.boat.isRemoved()) {
				return true;
			}
			if (this.stage != Stage.LANDED && this.stage != Stage.LEAVE && !this.aboard() && !this.player.startRiding(this.boat)) {
				if (this.player.onGround()) {
					// They hopped out onto solid ground: he's done.
					this.startLeaving(t);
				} else if (++this.overboard > OVERBOARD) {
					// Out of the boat in mid-air: he lets go, and a new rescue will catch them.
					return true;
				}
			} else {
				this.overboard = 0;
			}
			switch (this.stage) {
				case ARRIVE -> {
					float f = Mth.clamp(k / (float) ARRIVE, 0.0F, 1.0F);
					float eased = 1.0F - (1.0F - f) * (1.0F - f) * (1.0F - f);
					Vec3 sky = this.cloudAt;
					Vec3 hover = this.rise.add(0.0, LINE, 0.0);
					this.moveCloud(new Vec3(hover.x, Mth.lerp(eased, sky.y, hover.y), hover.z), t, null);
					this.placeBoat(this.boatAt);
					if (k == ARRIVE - 10) {
						this.level.playSound(null, this.boatAt.x, this.boatAt.y, this.boatAt.z, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.2F,
								1.0F);
					}
					if (k >= ARRIVE) {
						this.cloudAt = hover;
						this.nextStage(this.rise.distanceToSqr(this.caught) > 0.01 ? Stage.HOOK : Stage.CARRY, t);
					}
				}
				case HOOK -> {
					// The boat swings out on the lead to below him, easing in and out.
					int duration = Math.max(20, Mth.ceil(this.caught.distanceTo(this.rise) / 0.12));
					float f = Mth.clamp(k / (float) duration, 0.0F, 1.0F);
					float eased = f * f * (3.0F - 2.0F * f);
					this.moveCloud(this.cloudAt, t, null);
					this.placeBoat(this.caught.lerp(this.rise, eased));
					if (k >= duration) {
						this.nextStage(Stage.CARRY, t);
					}
				}
				case CARRY -> {
					if (this.path == null) {
						this.follow(new Path(wayHome(this.level, this.rise, this.landing), new Vec3(0.0, LINE, 0.0)));
					}
					this.fly(true, t);
					if (this.along >= this.path.total && this.boatAt.distanceToSqr(this.landing) < 0.0025) {
						this.landedAt = t;
						this.nextStage(Stage.LANDED, t);
						this.player.sendOverlayMessage(Component.translatable("message.transdimension.rescue.landed"));
						this.level.playSound(null, this.boatAt.x, this.boatAt.y, this.boatAt.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5F,
								1.8F);
					}
				}
				case LANDED -> {
					// Home: he waits above, bobbing, for them to hop out (and gives them a nudge after a while).
					this.moveCloud(this.cloudAt, t, null);
					this.placeBoat(this.boatAt);
					boolean aboard = this.aboard();
					if (aboard && t - this.landedAt >= MAX_WAIT) {
						this.player.stopRiding();
						aboard = false;
					}
					if (aboard && (t - this.landedAt) % 60 == 59) {
						this.player.sendOverlayMessage(Component.translatable("message.transdimension.rescue.landed"));
					}
					if (!aboard) {
						this.startLeaving(t);
					}
				}
				case LEAVE -> {
					this.fly(false, t);
					return this.along >= this.path.total;
				}
			}
			return false;
		}

		private boolean aboard() {
			return this.player.getVehicle() == this.boat;
		}

		private void nextStage(Stage stage, int t) {
			this.stage = stage;
			this.stageStart = t;
		}

		private void startLeaving(int t) {
			if (this.stage == Stage.LEAVE) {
				return;
			}
			this.level.playSound(null, this.boatAt.x, this.boatAt.y, this.boatAt.z, SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 1.2F, 1.2F);
			float heading = this.cloud != null ? this.cloud.getYRot() : 0.0F;
			this.follow(new Path(wayOff(this.level, this.cloudAt.subtract(0.0, LINE, 0.0), heading), new Vec3(0.0, LINE, 0.0)));
			this.nextStage(Stage.LEAVE, t);
		}

		/** Starts along {@code path} from rest, with the boat hanging below where the Cloudy is now. */
		private void follow(Path path) {
			this.path = path;
			this.along = 0.0;
			this.speed = 0.0;
			this.trail.clear();
			for (int i = 0; i <= TRAIL; i++) {
				this.trail.addLast(this.boatAt.add(0.0, LINE, 0.0));
			}
		}

		/**
		 * One tick along the path: speeding up from rest to {@link #MAX_SPEED}, and (if {@code stop}) slowing so as to come to
		 * rest at its end. The boat hangs from where the Cloudy was {@link #TRAIL} ticks ago.
		 */
		private void fly(boolean stop, int t) {
			Path path = this.path;
			double remaining = path.total - this.along;
			double cap = stop ? Math.sqrt(2.0 * ACCEL * remaining) + 0.01 : MAX_SPEED;
			this.speed = Math.min(Math.min(MAX_SPEED, cap), this.speed + ACCEL);
			this.along = Math.min(path.total, this.along + this.speed);
			Vec3 before = this.cloudAt;
			this.cloudAt = path.at(this.along);
			this.trail.addLast(this.cloudAt);
			while (this.trail.size() > TRAIL + 1) {
				this.trail.removeFirst();
			}
			this.moveCloud(this.cloudAt, t, before);
			this.placeBoat(this.trail.peekFirst().subtract(0.0, LINE, 0.0));
		}

		/** The Cloudy goes to {@code at} with a gentle bob, turning to face the way it's going (from {@code before}). */
		private void moveCloud(Vec3 at, int t, @Nullable Vec3 before) {
			if (this.cloud == null) {
				return;
			}
			double bob = Math.sin(t * 0.12) * 0.12;
			this.cloud.setPos(at.x, at.y + bob, at.z);
			this.cloud.setDeltaMovement(Vec3.ZERO);
			this.cloud.resetFallDistance();
			Vec3 heading = before != null ? at.subtract(before) : Vec3.ZERO;
			if (heading.horizontalDistanceSqr() > 1.0E-4) {
				float yaw = (float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90.0F;
				this.cloud.setYRot(Mth.approachDegrees(this.cloud.getYRot(), yaw, 5.0F));
			}
			this.cloud.yBodyRot = this.cloud.getYRot();
			this.cloud.yHeadRot = this.cloud.getYRot();
			if (this.turtle != null) {
				// He sits facing where they're going, glancing down at the boat now and then.
				this.turtle.setYRot(this.cloud.getYRot());
				this.turtle.yBodyRot = this.cloud.getYRot();
				this.turtle.yHeadRot = this.cloud.getYRot() + (float) Math.sin(t * 0.03) * 25.0F;
				this.turtle.setXRot(18.0F + (float) Math.sin(t * 0.05) * 10.0F);
			}
		}

		/** The boat goes to {@code at}, turning gently to face the way it's moving. */
		private void placeBoat(Vec3 at) {
			Vec3 step = at.subtract(this.boatAt);
			this.boatAt = at;
			this.boat.setPos(at.x, at.y, at.z);
			this.boat.setDeltaMovement(step);
			if (step.horizontalDistanceSqr() > 1.0E-4) {
				float yaw = (float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90.0F;
				this.boat.setYRot(Mth.approachDegrees(this.boat.getYRot(), yaw, 4.0F));
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
