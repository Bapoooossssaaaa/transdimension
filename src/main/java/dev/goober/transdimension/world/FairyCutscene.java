package dev.goober.transdimension.world;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.Maddie;
import dev.goober.transdimension.entity.TransFairy;
import dev.goober.transdimension.network.FairyCutscenePayload;
import dev.goober.transdimension.registry.ModAttachments;
import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModItems;
import dev.goober.transdimension.registry.ModParticles;

/**
 * The ritual at the Fairy Altar and the cutscene that starts the first fight.
 *
 * <p>The Trans Fairy isn't waiting when you first arrive. Throw a Trans Crystal (or a Crystal Pearl) onto the altar, or
 * use one on it, and the scene plays: a portal opens north of the altar and Maddie steps through it, horrified that you
 * have summoned the Trans Fairy, an ancient and horrible being, and begging you to get away from her. Before she can
 * finish, light gathers over the altar and the Trans Fairy appears in a falling column of it, silences Maddie and strikes
 * her down with a bolt from her wand, then turns to you: you woke her, so now you'll show her what you're worth. Maddie
 * is gone from the realm from then on ({@link FairyRealmState#maddieGone}) and the fight begins.
 *
 * <p>Every later offering calls the fairy straight back for a rematch. The scene runs on the server (one at a time; it
 * isn't saved) and holds its audience still while it plays; each watching player's client moves the camera through it
 * (FairyCutsceneCamera, on the timeline below), hides the HUD and shows the subtitles (FairyCutsceneOverlay).
 */
public final class FairyCutscene {
	/** Maddie's portal: on the arena floor, north of the altar, facing anyone standing at the altar. */
	public static final Vec3 MADDIE_PORTAL = new Vec3(0.5, FairyRealm.ALTAR.getY(), -8.5);
	/** How far from the altar players see and hear the scene. */
	private static final double AUDIENCE = 96.0;
	private static final String MADDIE = "entity.transdimension.maddie";
	private static final String FAIRY = "entity.transdimension.trans_fairy";
	private static final int[] SPARKLES = {0xF5A9B8, 0x5BCEFA, 0xFFFFFF};
	/** Holds the audience still while the scene plays (no walking, no jumping). */
	private static final Identifier HOLD = TransDimension.id("cutscene_hold");

	// The timeline, in ticks. The clients' camera follows it too (FairyCutsceneCamera).
	/** A door of light in the flag's colours opens on the arena floor (drawn by the clients)... */
	public static final int PORTAL_OPENS = 30;
	/** ...Maddie steps out of it... */
	public static final int MADDIE_ARRIVES = 50;
	/** ...walking this many ticks, {@link #WALK_DISTANCE} blocks, before she stops... */
	public static final int WALK = 30;
	public static final double WALK_DISTANCE = 3.0;
	/** ...and it closes behind her. */
	public static final int PORTAL_CLOSES = 88;
	public static final int LINE_ONE = 86;
	public static final int LINE_TWO = 140;
	public static final int LINE_THREE = 200;
	/** Light gathers over the altar... */
	public static final int GATHER = 233;
	/** ...and the Trans Fairy appears in it. */
	public static final int FAIRY_APPEARS = 255;
	public static final int FAIRY_LINE_ONE = 271;
	public static final int WAND_RAISED = 295;
	public static final int SHOT = 305;
	public static final int MADDIE_FALLS = 320;
	public static final int FAIRY_LINE_TWO = 340;
	public static final int FAIRY_LINE_THREE = 405;
	public static final int FIGHT = 465;

	@Nullable
	private static Scene scene;

	private static final class Scene {
		int tick;
		@Nullable
		Maddie maddie;
		@Nullable
		TransFairy fairy;
	}

	private FairyCutscene() {
	}

	public static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (FairyRealm.isFairyRealm(level)) {
				tick(level);
			}
		});
		// Leaving the realm mid-scene lets that player go at once.
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> release(player));
	}

	public static boolean running() {
		return scene != null;
	}

	/** True once the Trans Fairy has struck Maddie down: from then on no Maddie stays anywhere. */
	public static boolean maddieGone(ServerLevel anyLevel) {
		ServerLevel realm = anyLevel.getServer().getLevel(TransDimension.FAIRY_REALM);
		return realm != null && realm.getAttachedOrElse(ModAttachments.FAIRY_REALM_STATE, FairyRealmState.NEW).maddieGone();
	}

	/** Starts the scene (FairyRealm#offerAtAltar decides when). */
	static void start(ServerLevel level) {
		scene = new Scene();
		send(level, new FairyCutscenePayload(FairyCutscenePayload.START, "", ""));
		hold(level, true);
		Vec3 altar = altarTop();
		FairyRealm.sparkle(level, altar, 90, 0.8);
		level.playSound(null, altar.x, altar.y, altar.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.9F);
		level.playSound(null, altar.x, altar.y, altar.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.5F, 0.8F);
	}

	/**
	 * Holds everyone watching still (or lets everyone go, wherever they've got to): movement speed and jump strength drop
	 * to nothing. The modifier is transient, so it never outlives a session.
	 */
	private static void hold(ServerLevel level, boolean still) {
		Vec3 altar = altarTop();
		for (ServerPlayer player : still ? level.players() : level.getServer().getPlayerList().getPlayers()) {
			release(player);
			if (!still || player.position().distanceToSqr(altar) >= AUDIENCE * AUDIENCE) {
				continue;
			}
			for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
				AttributeInstance instance = player.getAttribute(attribute);
				if (instance != null) {
					instance.addTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
		}
	}

	private static void release(ServerPlayer player) {
		for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance != null) {
				instance.removeModifier(HOLD);
			}
		}
	}

	private static void tick(ServerLevel level) {
		Scene s = scene;
		if (s == null) {
			if (level.getGameTime() % 5 == 0) {
				takeThrownOffering(level);
			}
			return;
		}
		int t = ++s.tick;
		Vec3 portal = MADDIE_PORTAL;
		if (t >= PORTAL_OPENS && t < PORTAL_CLOSES + 12 && t % 2 == 0) {
			doorSparkles(level, portal);
		}
		// Each step is its own check, so two that fall on the same tick both happen.
		if (t == PORTAL_OPENS) {
			level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.2F, 1.8F);
			level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.5F, 1.2F);
		}
		if (t == MADDIE_ARRIVES) {
			s.maddie = bringMaddie(level, portal);
		}
		if (s.maddie != null && t > MADDIE_ARRIVES && t <= MADDIE_ARRIVES + WALK) {
			// She walks out through the door towards the altar (the client sees her walk from the steps she takes).
			Maddie maddie = s.maddie;
			maddie.setPos(maddie.getX(), maddie.getY(), maddie.getZ() + WALK_DISTANCE / WALK);
			if (t == MADDIE_ARRIVES + WALK) {
				faceWatcher(level, maddie);
			}
		}
		if (t == PORTAL_CLOSES) {
			level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.0F, 1.8F);
		}
		if (t == LINE_ONE) {
			say(level, MADDIE, "message.transdimension.cutscene.maddie_1");
		}
		if (t == LINE_TWO) {
			say(level, MADDIE, "message.transdimension.cutscene.maddie_2");
		}
		if (t == LINE_THREE) {
			say(level, MADDIE, "message.transdimension.cutscene.maddie_3");
		}
		if (t >= GATHER && t < FAIRY_APPEARS) {
			gather(level, t);
		}
		if (t == GATHER) {
			Vec3 at = FairyRealm.FAIRY_SPAWN;
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.7F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.6F);
		}
		if (t == FAIRY_APPEARS) {
			s.fairy = FairyRealm.spawnFairy(level, FairyRealm.FAIRY_SPAWN);
			if (s.fairy != null) {
				s.fairy.setIntro(true);
				s.fairy.lookAtDuringIntro(portal.add(0.0, 1.6, 0.0));
			}
			Vec3 at = FairyRealm.FAIRY_SPAWN;
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.5F, 1.4F);
			level.sendParticles(ModParticles.PRISM_SPARK, at.x, at.y + 0.8, at.z, 60, 0.2, 0.2, 0.2, 0.25);
		}
		if (t == FAIRY_LINE_ONE) {
			say(level, FAIRY, "message.transdimension.cutscene.fairy_1");
		}
		if (t == WAND_RAISED && s.fairy != null) {
			s.fairy.playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.5F, 1.6F);
		}
		if (t == SHOT && s.fairy != null && s.maddie != null && s.maddie.isAlive()) {
			// The bolt is what cuts her off; she's left with one heart so it's the bolt that ends it.
			s.maddie.setInvulnerable(false);
			s.maddie.setHealth(1.0F);
			s.fairy.castAt(s.maddie);
		}
		if (t == MADDIE_FALLS) {
			if (s.maddie != null && s.maddie.isAlive()) {
				s.maddie.setInvulnerable(false);
				s.maddie.hurtServer(level, s.fairy != null ? s.maddie.damageSources().indirectMagic(s.fairy, s.fairy)
						: s.maddie.damageSources().magic(), 100.0F);
			}
			FairyRealmState state = level.getAttachedOrElse(ModAttachments.FAIRY_REALM_STATE, FairyRealmState.NEW);
			level.setAttached(ModAttachments.FAIRY_REALM_STATE, state.withMaddieGone());
		}
		if (t == FAIRY_LINE_TWO) {
			if (s.fairy != null) {
				// she turns from Maddie to whoever woke her
				ServerPlayer nearest = level.getNearestPlayer(s.fairy, AUDIENCE) instanceof ServerPlayer player ? player : null;
				if (nearest != null) {
					s.fairy.lookAtDuringIntro(nearest.getEyePosition());
				}
			}
			say(level, FAIRY, "message.transdimension.cutscene.fairy_2");
		}
		if (t == FAIRY_LINE_THREE) {
			say(level, FAIRY, "message.transdimension.cutscene.fairy_3");
			if (s.fairy != null) {
				s.fairy.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 1.3F);
			}
		}
		if (t >= FIGHT) {
			if (s.fairy != null && s.fairy.isAlive()) {
				s.fairy.setIntro(false);
			} else {
				FairyRealm.spawnFairy(level, FairyRealm.FAIRY_SPAWN);
			}
			hold(level, false);
			send(level, new FairyCutscenePayload(FairyCutscenePayload.END, "", ""));
			scene = null;
		}
	}

	/** Maddie steps into the doorway from behind it, facing the altar (she walks the rest of the way: see tick). */
	@Nullable
	private static Maddie bringMaddie(ServerLevel level, Vec3 door) {
		Maddie maddie = ModEntities.MADDIE.create(level, EntitySpawnReason.EVENT);
		if (maddie == null) {
			return null;
		}
		maddie.snapTo(door.x, door.y, door.z - 0.7, 0.0F, 0.0F);
		maddie.setYHeadRot(0.0F);
		maddie.yBodyRot = 0.0F;
		maddie.actInCutscene();
		level.addFreshEntity(maddie);
		level.playSound(null, door.x, door.y, door.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5F, 1.2F);
		return maddie;
	}

	/** Turns Maddie to whoever stands nearest. */
	private static void faceWatcher(ServerLevel level, Maddie maddie) {
		ServerPlayer watcher = level.getNearestPlayer(maddie.getX(), maddie.getY(), maddie.getZ(), AUDIENCE, false) instanceof ServerPlayer player
				? player : null;
		if (watcher != null) {
			float yaw = (float) (Mth.atan2(watcher.getZ() - maddie.getZ(), watcher.getX() - maddie.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			maddie.setYRot(yaw);
			maddie.setYHeadRot(yaw);
			maddie.yBodyRot = yaw;
		}
	}

	/** Prismatic sparks drifting off the door's edges while it stands open (the door itself is drawn by each client). */
	private static void doorSparkles(ServerLevel level, Vec3 door) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 2; i++) {
			boolean side = random.nextBoolean();
			double x = side ? door.x + (random.nextBoolean() ? -0.75 : 0.75) : door.x + (random.nextDouble() - 0.5) * 1.5;
			double y = side ? door.y + random.nextDouble() * 2.8 : door.y + (random.nextBoolean() ? 0.0 : 2.8);
			level.sendParticles(ModParticles.PRISM_SPARK, x, y, door.z, 1, 0.02, 0.02, 0.02, 0.01);
		}
	}

	/** Light gathers over the altar where the Trans Fairy is about to appear: sparkles spiralling in from all round. */
	private static void gather(ServerLevel level, int t) {
		Vec3 at = FairyRealm.FAIRY_SPAWN.add(0.0, 0.8, 0.0);
		double closeness = (t - GATHER) / (double) (FAIRY_APPEARS - GATHER);
		for (int i = 0; i < 4; i++) {
			float angle = t * 0.5F + i * Mth.TWO_PI / 4.0F;
			double radius = 4.5 * (1.0 - closeness) + 0.4;
			double x = at.x + Mth.cos(angle) * radius;
			double z = at.z + Mth.sin(angle) * radius;
			double y = at.y + Mth.sin(t * 0.3F + i) * 0.6;
			level.sendParticles(new DustParticleOptions(SPARKLES[i % SPARKLES.length], 1.4F), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ModParticles.PRISM_SPARK, x, y, z, 0, at.x - x, at.y - y, at.z - z, 0.08);
		}
	}

	/** A line of the scene, shown as a subtitle to everyone watching under the speaker's name. */
	private static void say(ServerLevel level, String speaker, String line) {
		send(level, new FairyCutscenePayload(FairyCutscenePayload.LINE, speaker, line));
	}

	private static void send(ServerLevel level, FairyCutscenePayload payload) {
		Vec3 altar = altarTop();
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(altar) < AUDIENCE * AUDIENCE) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** A Trans Crystal (or Crystal Pearl) thrown onto the altar is an offering: it's taken and the fairy is called. */
	private static void takeThrownOffering(ServerLevel level) {
		Vec3 altar = altarTop();
		AABB onTop = new AABB(altar.x - 1.2, altar.y - 1.0, altar.z - 1.2, altar.x + 1.2, altar.y + 1.6, altar.z + 1.2);
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, onTop,
				item -> item.getItem().is(ModItems.TRANS_CRYSTAL) || item.getItem().is(ModItems.TRANS_CRYSTAL_PEARL));
		if (items.isEmpty() || !FairyRealm.offerAtAltar(level, null)) {
			return;
		}
		ItemEntity offering = items.get(0);
		ItemStack rest = offering.getItem().copy();
		rest.shrink(1);
		if (rest.isEmpty()) {
			offering.discard();
		} else {
			offering.setItem(rest);
		}
	}

	private static Vec3 altarTop() {
		return Vec3.atBottomCenterOf(FairyRealm.ALTAR).add(0.0, 1.0, 0.0);
	}
}
