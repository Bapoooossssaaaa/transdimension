package dev.goober.transdimension.world;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.block.SkyPortalBlock;
import dev.goober.transdimension.entity.Cloudy;
import dev.goober.transdimension.registry.ModAttachments;
import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModEntities;

/**
 * The Cloud Realm: white floating islands and clouds of wool in a bright, white sky, with no monsters at all (dimension
 * {@code transdimension:cloud_realm}, made by tools/generate_worldgen.py). Its creatures are Cloudies you can ride, white
 * farm animals (data-driven pig, cow and chicken variants) and baby happy ghasts that never grow up.
 *
 * <p>The way in is the Sky Portal a candle ritual opens in a pink ancient city's gate ({@link SculkRitual}). Everyone
 * arrives on a little wool cloud at the middle of the realm ({@link #ISLAND}), built the first time it's needed, where a
 * Sky Portal leads back to the gate each traveller came through and a Cloudy is always waiting.
 */
public final class CloudRealm {
	/** The middle of the arrival cloud's top. */
	public static final BlockPos ISLAND = new BlockPos(0, 140, 0);
	/** Where travellers step out, facing the portal home. */
	public static final Vec3 ARRIVAL = new Vec3(0.5, 141.0, 2.5);
	/** The bottom middle of the portal home (a 3 wide, 4 tall sheet on the cloud's north side). */
	public static final BlockPos RETURN_PORTAL = new BlockPos(0, 141, -4);
	private static final int CLOUD_RADIUS = 7;
	private static final int GHASTLINGS_NEAR_PLAYER = 3;

	private CloudRealm() {
	}

	public static void initialize() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (isCloudRealm(destination)) {
				ensureArrival(destination);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (isCloudRealm(level) && level.getGameTime() % 300 == 0) {
				tendGhastlings(level);
			}
		});
	}

	public static boolean isCloudRealm(Level level) {
		return level.dimension().equals(TransDimension.CLOUD_REALM);
	}

	// ------------------------------------------------------------------------------------------------ the portals

	/** Where a Sky Portal sends an entity: up to the Cloud Realm, or (from inside it) back to the gate it came through. */
	@Nullable
	public static TeleportTransition portalDestination(ServerLevel from, Entity entity, BlockPos portal) {
		MinecraftServer server = from.getServer();
		if (isCloudRealm(from)) {
			GlobalPos back = entity.getAttached(ModAttachments.CLOUD_RETURN);
			ServerLevel target = back != null ? server.getLevel(back.dimension()) : null;
			BlockPos spot;
			if (target == null) {
				target = server.getLevel(TransDimension.TRANS_REALM);
				if (target == null) {
					target = server.overworld();
				}
				BlockPos spawn = target.getRespawnData().pos();
				target.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
				spot = new BlockPos(spawn.getX(), target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()),
						spawn.getZ());
			} else {
				spot = besideGate(target, back.pos());
			}
			return new TeleportTransition(target, Vec3.atBottomCenterOf(spot), Vec3.ZERO, entity.getYRot(), entity.getXRot(),
					TeleportTransition.DO_NOTHING);
		}
		ServerLevel clouds = server.getLevel(TransDimension.CLOUD_REALM);
		if (clouds == null) {
			return null;
		}
		ensureArrival(clouds);
		entity.setAttached(ModAttachments.CLOUD_RETURN, GlobalPos.of(from.dimension(), portal));
		return new TeleportTransition(clouds, ARRIVAL, Vec3.ZERO, 180.0F, 0.0F, TeleportTransition.DO_NOTHING);
	}

	/** A safe spot to stand next to a gate's portal: in front of it or behind it, on whatever ledge or floor is there. */
	private static BlockPos besideGate(ServerLevel level, BlockPos portal) {
		level.getChunk(portal.getX() >> 4, portal.getZ() >> 4);
		for (int distance = 1; distance <= 6; distance++) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos column = portal.relative(direction, distance);
				for (int dy = 1; dy >= -10; dy--) {
					BlockPos feet = column.above(dy);
					if (standable(level, feet)) {
						return feet;
					}
				}
			}
		}
		return portal.above(2);
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockState below = level.getBlockState(feet.below());
		return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && !below.isAir()
				&& below.getFluidState().isEmpty() && !below.is(ModBlocks.SKY_PORTAL);
	}

	// ------------------------------------------------------------------------------------------------ the arrival cloud

	/** Builds the arrival cloud and its portal home if they aren't there yet, and makes sure a Cloudy waits by it. */
	public static void ensureArrival(ServerLevel level) {
		if (!level.getBlockState(RETURN_PORTAL).is(ModBlocks.SKY_PORTAL)) {
			buildArrival(level);
		}
		if (level.getEntitiesOfClass(Cloudy.class, new AABB(ISLAND).inflate(32.0)).isEmpty()) {
			Cloudy cloudy = ModEntities.CLOUDY.create(level, EntitySpawnReason.EVENT);
			if (cloudy != null) {
				cloudy.snapTo(ISLAND.getX() + 3.5, ISLAND.getY() + 1.5, ISLAND.getZ() + 3.5, 0.0F, 0.0F);
				cloudy.setPersistenceRequired();
				level.addFreshEntity(cloudy);
			}
		}
	}

	private static void buildArrival(ServerLevel level) {
		for (int cx = -1; cx <= 0; cx++) {
			for (int cz = -1; cz <= 0; cz++) {
				level.getChunk(cx, cz);
			}
		}
		BlockState wool = Blocks.WOOL.white().defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		int top = ISLAND.getY();
		for (int dx = -CLOUD_RADIUS - 1; dx <= CLOUD_RADIUS + 1; dx++) {
			for (int dz = -CLOUD_RADIUS - 1; dz <= CLOUD_RADIUS + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				// a puffy cloud, level on top to stand on and round underneath (five deep in the middle), with clear sky
				// above it
				double edge = CLOUD_RADIUS + 0.5;
				if (d <= edge) {
					int depth = (int) Math.round(Math.sqrt(1.0 - d * d / (edge * edge)) * 4.0);
					for (int layer = 0; layer <= depth; layer++) {
						level.setBlock(new BlockPos(dx, top - layer, dz), wool, Block.UPDATE_ALL);
					}
				}
				if (d <= CLOUD_RADIUS + 0.5) {
					for (int dy = 1; dy <= 7; dy++) {
						level.setBlock(new BlockPos(dx, top + dy, dz), air, Block.UPDATE_ALL);
					}
				}
			}
		}
		// The portal home: a frame of chiseled cloudcite round a 3 x 4 sheet of light, with glowing froglights at its feet.
		BlockState frame = ModBlocks.CHISELED_CLOUDCITE.defaultBlockState();
		BlockState sheet = ModBlocks.SKY_PORTAL.defaultBlockState().setValue(SkyPortalBlock.AXIS, Direction.Axis.X);
		int z = RETURN_PORTAL.getZ();
		int bottom = RETURN_PORTAL.getY();
		for (int dx = -2; dx <= 2; dx++) {
			level.setBlock(new BlockPos(dx, bottom - 1, z), frame, Block.UPDATE_ALL);
			level.setBlock(new BlockPos(dx, bottom + 4, z), frame, Block.UPDATE_ALL);
		}
		for (int dy = 0; dy < 4; dy++) {
			level.setBlock(new BlockPos(-2, bottom + dy, z), frame, Block.UPDATE_ALL);
			level.setBlock(new BlockPos(2, bottom + dy, z), frame, Block.UPDATE_ALL);
			for (int dx = -1; dx <= 1; dx++) {
				level.setBlock(new BlockPos(dx, bottom + dy, z), sheet, Block.UPDATE_CLIENTS);
			}
		}
		for (int side : new int[] {-3, 3}) {
			level.setBlock(new BlockPos(side, bottom, z), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	// ------------------------------------------------------------------------------------------------ baby happy ghasts

	/**
	 * Now and then a baby happy ghast drifts in near each player, up to a few at a time. They never grow up: every 15
	 * seconds each baby near a player is made young again ({@code setBaby(true)} restarts its growing). Vanilla's own age
	 * lock, {@code AgeableMob#setAgeLocked} (the golden dandelion's), is protected.
	 */
	private static void tendGhastlings(ServerLevel level) {
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			AABB around = player.getBoundingBox().inflate(80.0);
			var nearby = level.getEntities(EntityTypes.HAPPY_GHAST, around, ghast -> true);
			for (var ghast : nearby) {
				if (ghast.isBaby()) {
					ghast.setBaby(true);
				}
			}
			if (nearby.size() >= GHASTLINGS_NEAR_PLAYER || random.nextFloat() > 0.35F) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 24.0 + random.nextDouble() * 32.0;
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;
			double y = Mth.clamp(player.getY() + random.nextInt(24) - 8, level.getMinY() + 40, level.getMaxY() - 30);
			BlockPos pos = BlockPos.containing(x, y, z);
			if (!level.isLoaded(pos) || !level.noCollision(new AABB(pos).inflate(2.0))) {
				continue;
			}
			var ghastling = EntityTypes.HAPPY_GHAST.create(level, EntitySpawnReason.NATURAL);
			if (ghastling == null) {
				continue;
			}
			ghastling.setBaby(true);
			ghastling.snapTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(ghastling);
		}
	}
}
