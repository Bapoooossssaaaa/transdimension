package dev.goober.transdimension.world;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.block.FairyPortalFrameBlock;
import dev.goober.transdimension.entity.TransFairy;
import dev.goober.transdimension.registry.ModAttachments;
import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModParticles;

/**
 * The Fairy Realm and the way there.
 *
 * <p>Deep under the Trans Realm, Fairy Sanctums hide a ring of twelve Fairy Portal frames (like an end portal). Setting
 * a Trans Crystal Pearl into all twelve opens a shimmering portal. The first time anyone comes through, the arena island
 * is placed at the middle of the realm from the structure template {@link #ARENA_TEMPLATE} (made by
 * tools/generate_fairy_realm.py, whose ARENA_* numbers must match the constants here) and the Trans Fairy appears above
 * its altar. Beating her opens a portal home on the arena's north side; using a crystal pearl on the altar calls her back
 * for a rematch.
 */
public final class FairyRealm {
	public static final Identifier ARENA_TEMPLATE = TransDimension.id("fairy_realm/arena_island");
	/** The template's lowest corner. */
	public static final BlockPos ARENA_ORIGIN = new BlockPos(-36, 84, -36);
	/** The altar at the middle of the arena (it stands on the arena floor, y 120). */
	public static final BlockPos ALTAR = new BlockPos(0, 121, 0);
	/** Where travellers step off the portal: the path into the arena from the south, facing it. */
	public static final Vec3 ARRIVAL = new Vec3(0.5, 121.0, 30.5);
	/** The middle of the 3x3 portal home, inside a ring of frames on the arena's north side. */
	public static final BlockPos RETURN_PORTAL = new BlockPos(0, 121, -25);
	/** Where the Trans Fairy appears, high over the altar. */
	public static final Vec3 FAIRY_SPAWN = new Vec3(0.5, 127.0, 0.5);

	private FairyRealm() {
	}

	public static void initialize() {
		FairyCutscene.initialize();
		// Arriving by any other means (commands, /goober round trips) still finds the arena.
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (destination.dimension().equals(TransDimension.FAIRY_REALM)) {
				ensureArena(destination);
				if (!FairyCutscene.running() && !fairyNearby(destination, ALTAR)) {
					player.sendSystemMessage(Component.translatable("message.transdimension.fairy_altar_hint"));
				}
				// On peaceful the fairy can't stay (no monsters), so the way home opens straight away.
				if (destination.getDifficulty() == Difficulty.PEACEFUL && !destination.getBlockState(RETURN_PORTAL).is(ModBlocks.FAIRY_PORTAL)) {
					fillPortal(destination, RETURN_PORTAL);
					player.sendSystemMessage(Component.translatable("message.transdimension.fairy_peaceful"));
				}
			}
		});
	}

	public static boolean isFairyRealm(net.minecraft.world.level.Level level) {
		return level.dimension().equals(TransDimension.FAIRY_REALM);
	}

	// ------------------------------------------------------------------------------------------------ the portals

	/** Where a Fairy Portal sends an entity: into the Fairy Realm, or (from inside it) back to the portal it came through. */
	@Nullable
	public static TeleportTransition portalDestination(ServerLevel from, Entity entity, BlockPos portal) {
		MinecraftServer server = from.getServer();
		if (isFairyRealm(from)) {
			GlobalPos back = entity.getAttached(ModAttachments.FAIRY_RETURN);
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
				spot = besideRing(target, back.pos());
			}
			return new TeleportTransition(target, Vec3.atBottomCenterOf(spot), Vec3.ZERO, entity.getYRot(), entity.getXRot(),
					TeleportTransition.DO_NOTHING);
		}
		ServerLevel fairy = server.getLevel(TransDimension.FAIRY_REALM);
		if (fairy == null) {
			return null;
		}
		ensureArena(fairy);
		entity.setAttached(ModAttachments.FAIRY_RETURN, GlobalPos.of(from.dimension(), portal));
		return new TeleportTransition(fairy, ARRIVAL, Vec3.ZERO, 180.0F, 0.0F, TeleportTransition.DO_NOTHING);
	}

	/** A safe spot to stand just outside the ring of frames around a portal (which sits within two blocks of {@code portal}). */
	private static BlockPos besideRing(ServerLevel level, BlockPos portal) {
		level.getChunk(portal.getX() >> 4, portal.getZ() >> 4);
		for (int distance = 3; distance <= 6; distance++) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos column = portal.relative(direction, distance);
				for (int dy = 2; dy >= -3; dy--) {
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
		return below.isFaceSturdy(level, feet.below(), Direction.UP) && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}

	/**
	 * After a pearl goes into a frame: if it completes a ring of twelve filled frames around a 3x3 hole (in any of the
	 * rings the frame could belong to), the hole fills with portal. Returns whether a portal opened.
	 */
	public static boolean tryOpenPortal(ServerLevel level, BlockPos frame) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos centre = frame.offset(dx, 0, dz);
				if (isCompleteRing(level, centre)) {
					fillPortal(level, centre);
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isCompleteRing(ServerLevel level, BlockPos centre) {
		for (BlockPos pos : ring(centre)) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(ModBlocks.FAIRY_PORTAL_FRAME) || !state.getValue(FairyPortalFrameBlock.PEARL)) {
				return false;
			}
		}
		return true;
	}

	/** The twelve frame positions around a 3x3 hole: three along each side, corners left out. */
	private static List<BlockPos> ring(BlockPos centre) {
		return List.of(centre.offset(-1, 0, -2), centre.offset(0, 0, -2), centre.offset(1, 0, -2),
				centre.offset(-1, 0, 2), centre.offset(0, 0, 2), centre.offset(1, 0, 2),
				centre.offset(-2, 0, -1), centre.offset(-2, 0, 0), centre.offset(-2, 0, 1),
				centre.offset(2, 0, -1), centre.offset(2, 0, 0), centre.offset(2, 0, 1));
	}

	private static void fillPortal(ServerLevel level, BlockPos centre) {
		BlockState portal = ModBlocks.FAIRY_PORTAL.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos pos = centre.offset(dx, 0, dz);
				BlockState old = level.getBlockState(pos);
				if (old.isAir() || old.canBeReplaced() || old.is(ModBlocks.FAIRY_PORTAL)) {
					level.setBlock(pos, portal, Block.UPDATE_CLIENTS);
				}
			}
		}
		level.playSound(null, centre, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.6F);
		level.playSound(null, centre, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5F, 1.2F);
		sparkle(level, Vec3.atCenterOf(centre), 60, 1.6);
	}

	// ------------------------------------------------------------------------------------------------ the arena

	/** Places the arena island the first time it's needed. The Trans Fairy waits to be called at the altar (FairyCutscene). */
	public static void ensureArena(ServerLevel level) {
		FairyRealmState state = level.getAttachedOrElse(ModAttachments.FAIRY_REALM_STATE, FairyRealmState.NEW);
		if (state.built()) {
			return;
		}
		StructureTemplate template = level.getStructureManager().get(ARENA_TEMPLATE).orElse(null);
		if (template == null) {
			TransDimension.LOGGER.error("The Fairy Realm's arena template {} is missing; building a plain platform instead", ARENA_TEMPLATE);
			buildFallbackPlatform(level);
		} else {
			// Generate every chunk the island covers first, so the template lands in finished chunks.
			BlockPos far = ARENA_ORIGIN.offset(template.getSize());
			for (int cx = ARENA_ORIGIN.getX() >> 4; cx <= far.getX() >> 4; cx++) {
				for (int cz = ARENA_ORIGIN.getZ() >> 4; cz <= far.getZ() >> 4; cz++) {
					level.getChunk(cx, cz);
				}
			}
			template.placeInWorld(level, ARENA_ORIGIN, ARENA_ORIGIN, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
		}
		level.setAttached(ModAttachments.FAIRY_REALM_STATE, state.withBuilt());
	}

	/** If the template ever goes missing, a pad of trans stone bricks still gives the fight somewhere to happen. */
	private static void buildFallbackPlatform(ServerLevel level) {
		BlockState floor = ModBlocks.TRANS_STONE_BRICKS.defaultBlockState();
		int y = ALTAR.getY() - 1;
		for (int dx = -16; dx <= 16; dx++) {
			for (int dz = -16; dz <= 34; dz++) {
				if (dx * dx + dz * dz <= 16 * 16 || (Math.abs(dx) <= 2 && dz > 0)) {
					level.setBlock(new BlockPos(dx, y, dz), floor, Block.UPDATE_CLIENTS);
				}
			}
		}
		level.setBlock(ALTAR, ModBlocks.FAIRY_ALTAR.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	@Nullable
	public static TransFairy spawnFairy(ServerLevel level, Vec3 at) {
		TransFairy fairy = ModEntities.TRANS_FAIRY.create(level, EntitySpawnReason.EVENT);
		if (fairy == null) {
			return null;
		}
		fairy.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);
		fairy.setHome(ALTAR.below());
		fairy.setPersistenceRequired();
		level.addFreshEntity(fairy);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.8F);
		sparkle(level, at, 80, 1.2);
		return fairy;
	}

	/** True while a Trans Fairy is anywhere near the arena. */
	public static boolean fairyNearby(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(TransFairy.class, new AABB(pos).inflate(64.0), TransFairy::isAlive).isEmpty();
	}

	/**
	 * A Trans Crystal or Crystal Pearl offered at the altar (used on it, or thrown onto it by {@code thrower} = null).
	 * The first time it starts the cutscene in which the Trans Fairy arrives; after that it calls her straight back for
	 * a rematch. Returns whether the offering was taken (not while she's here or the scene is playing).
	 */
	public static boolean offerAtAltar(ServerLevel level, @Nullable Player player) {
		if (FairyCutscene.running() || fairyNearby(level, ALTAR)) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.transdimension.fairy_already_here"));
			}
			return false;
		}
		if (!level.getAttachedOrElse(ModAttachments.FAIRY_REALM_STATE, FairyRealmState.NEW).maddieGone()) {
			FairyCutscene.start(level);
			return true;
		}
		spawnFairy(level, FAIRY_SPAWN);
		for (ServerPlayer watcher : level.players()) {
			if (watcher.distanceToSqr(Vec3.atCenterOf(ALTAR)) < 64.0 * 64.0) {
				watcher.sendOverlayMessage(Component.translatable("message.transdimension.fairy_summoned"));
			}
		}
		return true;
	}

	/** She's beaten: open the portal home and remember the victory. */
	public static void onFairyDefeated(ServerLevel level, TransFairy fairy) {
		if (!isFairyRealm(level)) {
			return;
		}
		FairyRealmState state = level.getAttachedOrElse(ModAttachments.FAIRY_REALM_STATE, FairyRealmState.NEW);
		level.setAttached(ModAttachments.FAIRY_REALM_STATE, state.withVictory());
		fillPortal(level, RETURN_PORTAL);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(fairy) < 96.0 * 96.0) {
				player.sendSystemMessage(Component.translatable("message.transdimension.fairy_defeated"));
			}
		}
	}

	/** A burst of trans sparks (our own particle, turning blue, pink and white), a few of them flung out fast. */
	public static void sparkle(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(ModParticles.TRANS_SPARK, at.x, at.y, at.z, count, spread, spread * 0.6, spread, 0.02);
		level.sendParticles(ModParticles.TRANS_SPARK, at.x, at.y, at.z, count / 4, spread * 0.5, spread * 0.5, spread * 0.5, 0.1);
	}
}
