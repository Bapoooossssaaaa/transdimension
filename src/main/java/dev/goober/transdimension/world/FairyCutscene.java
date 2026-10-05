package dev.goober.transdimension.world;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.Maddie;
import dev.goober.transdimension.entity.TransFairy;
import dev.goober.transdimension.network.FairyCutscenePayload;
import dev.goober.transdimension.registry.ModAttachments;
import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModItems;

/**
 * The ritual at the Fairy Altar and the cutscene that starts the first fight.
 *
 * <p>The Trans Fairy isn't waiting when you first arrive. Throw a Trans Crystal (or a Crystal Pearl) onto the altar, or
 * use one on it, and the scene plays: letterbox bars close in, a portal opens north of the altar and Maddie steps
 * through it, crying out that she has done something horrible and that the Trans Fairy is a horrible being. Before she
 * can finish, the Trans Fairy appears over the altar and strikes her down with a bolt from her wand. Maddie is gone from
 * the realm from then on ({@link FairyRealmState#maddieGone}), the bars open and the fight begins.
 *
 * <p>Every later offering calls the fairy straight back for a rematch. The scene runs on the server (one at a time;
 * it isn't saved), and its subtitles and bars are drawn by each watching player's client (FairyCutsceneOverlay).
 */
public final class FairyCutscene {
	/** Maddie's portal: on the arena floor, north of the altar, facing anyone standing at the altar. */
	private static final Vec3 MADDIE_PORTAL = new Vec3(0.5, FairyRealm.ALTAR.getY(), -8.5);
	/** How far from the altar players see and hear the scene. */
	private static final double AUDIENCE = 96.0;
	private static final String MADDIE = "entity.transdimension.maddie";
	private static final int[] SPARKLES = {0xF5A9B8, 0x5BCEFA, 0xFFFFFF};

	// The timeline, in ticks.
	private static final int PORTAL_OPENS = 30;
	private static final int MADDIE_ARRIVES = 55;
	private static final int PORTAL_CLOSES = 70;
	private static final int LINE_ONE = 70;
	private static final int LINE_TWO = 135;
	private static final int LINE_THREE = 205;
	private static final int FAIRY_APPEARS = 255;
	private static final int WAND_RAISED = 275;
	private static final int SHOT = 285;
	private static final int MADDIE_FALLS = 300;
	private static final int FIGHT = 345;

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
		Vec3 altar = altarTop();
		FairyRealm.sparkle(level, altar, 90, 0.8);
		level.playSound(null, altar.x, altar.y, altar.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.9F);
		level.playSound(null, altar.x, altar.y, altar.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.5F, 0.8F);
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
		if (t >= PORTAL_OPENS && t < PORTAL_CLOSES) {
			swirl(level, portal, t);
		}
		// Each step is its own check, so two that fall on the same tick both happen.
		if (t == PORTAL_OPENS) {
			level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.NEUTRAL, 0.6F, 1.6F);
		}
		if (t == MADDIE_ARRIVES) {
			s.maddie = bringMaddie(level, portal);
		}
		if (t == PORTAL_CLOSES) {
			FairyRealm.sparkle(level, portal.add(0.0, 1.2, 0.0), 40, 0.6);
		}
		if (t == LINE_ONE) {
			say(level, "message.transdimension.cutscene.maddie_1");
		}
		if (t == LINE_TWO) {
			say(level, "message.transdimension.cutscene.maddie_2");
		}
		if (t == LINE_THREE) {
			say(level, "message.transdimension.cutscene.maddie_3");
		}
		if (t == FAIRY_APPEARS) {
			s.fairy = FairyRealm.spawnFairy(level, FairyRealm.FAIRY_SPAWN);
			if (s.fairy != null) {
				s.fairy.setIntro(true);
				s.fairy.lookAtDuringIntro(portal.add(0.0, 1.6, 0.0));
			}
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
		if (t >= FIGHT) {
			if (s.fairy != null && s.fairy.isAlive()) {
				s.fairy.setIntro(false);
			} else {
				FairyRealm.spawnFairy(level, FairyRealm.FAIRY_SPAWN);
			}
			send(level, new FairyCutscenePayload(FairyCutscenePayload.END, "", ""));
			scene = null;
		}
	}

	/** Maddie steps out of her portal, facing whoever stands nearest the altar. */
	@Nullable
	private static Maddie bringMaddie(ServerLevel level, Vec3 at) {
		Maddie maddie = ModEntities.MADDIE.create(level, EntitySpawnReason.EVENT);
		if (maddie == null) {
			return null;
		}
		float yaw = 0.0F;
		ServerPlayer watcher = level.getNearestPlayer(at.x, at.y, at.z, AUDIENCE, false) instanceof ServerPlayer player ? player : null;
		if (watcher != null) {
			yaw = (float) (Mth.atan2(watcher.getZ() - at.z, watcher.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F;
		}
		maddie.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		maddie.setYHeadRot(yaw);
		maddie.yBodyRot = yaw;
		maddie.actInCutscene();
		level.addFreshEntity(maddie);
		FairyRealm.sparkle(level, at.add(0.0, 1.0, 0.0), 60, 0.5);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5F, 1.2F);
		return maddie;
	}

	/** The portal: a ring of portal light and flag-coloured sparkles, standing upright. */
	private static void swirl(ServerLevel level, Vec3 at, int t) {
		for (int i = 0; i < 6; i++) {
			float angle = (t * 0.35F + i * Mth.TWO_PI / 6.0F);
			double x = at.x + Mth.cos(angle) * 0.9;
			double y = at.y + 1.2 + Mth.sin(angle) * 1.4;
			level.sendParticles(ParticleTypes.PORTAL, x, y, at.z, 2, 0.05, 0.05, 0.05, 0.3);
			level.sendParticles(new DustParticleOptions(SPARKLES[i % SPARKLES.length], 1.2F), x, y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** A line of Maddie's, shown as a subtitle to everyone watching. */
	private static void say(ServerLevel level, String line) {
		send(level, new FairyCutscenePayload(FairyCutscenePayload.LINE, MADDIE, line));
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
