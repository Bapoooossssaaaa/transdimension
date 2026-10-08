package dev.goober.transdimension.world;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.Maddie;
import dev.goober.transdimension.entity.SculkArcher;
import dev.goober.transdimension.entity.TransFairy;
import dev.goober.transdimension.network.FairyCutscenePayload;
import dev.goober.transdimension.registry.ModAttachments;
import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModItems;

/**
 * The ritual at the Fairy Altar and the cutscene that starts the first fight.
 *
 * <p>The Trans Fairy isn't waiting when you first arrive. Throw a Trans Crystal (or a Crystal Pearl) onto the altar, or
 * use one on it, and the scene plays: a door of light opens north of the altar and Maddie walks through it, horrified
 * that you have summoned the Trans Fairy, an ancient and horrible being, and begging you to get away from her. Before
 * she can finish, the fairy appears over the altar and hushes her. Maddie didn't come alone: at her call two more doors
 * open either side of hers and two sculk archers ({@link SculkArcher}) step out and loose arrows at the fairy, which stop
 * on a shield of light round her. She strikes the archers down one after the other (their loot drops where they fall).
 * Then she lifts Maddie off her feet, flies up close and touches her heart with the tip of her wand, and Maddie is
 * pruned: the flag's light spreads over her from the wand and she comes apart into it and drifts away (the clients draw
 * it: PruneEffect). The fairy floats back over the altar and turns to you: you woke her, so now you'll show her what
 * you're worth. Maddie is gone from the realm from then on ({@link FairyRealmState#maddieGone}) and the fight begins.
 *
 * <p>Every later offering calls the fairy straight back for a rematch. The scene runs on the server (one at a time; it
 * isn't saved) and holds its audience still while it plays; each watching player's client moves the camera through it
 * (FairyCutsceneCamera, on the timeline below), hides the HUD and shows the subtitles (FairyCutsceneOverlay).
 */
public final class FairyCutscene {
	/** Maddie's portal: on the arena floor, north of the altar, facing anyone standing at the altar. */
	public static final Vec3 MADDIE_PORTAL = new Vec3(0.5, FairyRealm.ALTAR.getY(), -8.5);
	/** Her friends' doors, five blocks either side of hers, facing the same way: the first archer's (west), the second's. */
	public static final List<Vec3> HELP_PORTALS = List.of(MADDIE_PORTAL.add(-5.0, 0.0, 0.0), MADDIE_PORTAL.add(5.0, 0.0, 0.0));
	/** The Trans Fairy's shield: a bubble this far out from her middle, where every arrow stops. */
	public static final double SHIELD_RADIUS = 2.2;
	/** How far from the altar players see and hear the scene. */
	private static final double AUDIENCE = 96.0;
	private static final String MADDIE = "entity.transdimension.maddie";
	private static final String FAIRY = "entity.transdimension.trans_fairy";
	/** Holds the audience still while the scene plays (no walking, no jumping). */
	private static final Identifier HOLD = TransDimension.id("cutscene_hold");

	// The timeline, in ticks. The clients' camera follows it too (FairyCutsceneCamera).
	/** A door of light in the flag's colours opens on the arena floor (drawn by the clients)... */
	public static final int PORTAL_OPENS = 30;
	/** ...Maddie steps out of it... */
	public static final int MADDIE_ARRIVES = 50;
	/** ...walking this many ticks, {@link #WALK_DISTANCE} blocks, before she stops... */
	public static final int WALK = 30;
	public static final double WALK_DISTANCE = 3.5;
	/**
	 * How far behind a door its walker starts: just behind its face of light, which hides them (FairyCutsceneCamera draws
	 * it bright, not see-through), so they step out of the front of the door rather than being seen walking up to it.
	 */
	public static final double WALK_FROM = 0.5;
	/** ...and it closes behind her. */
	public static final int PORTAL_CLOSES = 88;
	public static final int LINE_ONE = 86;
	public static final int LINE_TWO = 140;
	public static final int LINE_THREE = 200;
	/** The camera looks past Maddie to the altar... */
	public static final int LOOK_TO_ALTAR = 233;
	/** ...and the Trans Fairy appears over it. */
	public static final int FAIRY_APPEARS = 255;
	public static final int FAIRY_LINE_ONE = 271;
	/** Maddie calls her friends... */
	public static final int CALL_FOR_HELP = 305;
	/** ...their doors open... */
	public static final int HELP_OPENS = 318;
	/**
	 * ...and once they're fully open (FairyCutsceneCamera.DOOR_OPENING after), a sculk archer walks out of each,
	 * {@link #HELP_WALK_DISTANCE} blocks in {@link #HELP_WALK} ticks...
	 */
	public static final int HELP_ARRIVES = 338;
	public static final int HELP_WALK = 22;
	public static final double HELP_WALK_DISTANCE = 2.4;
	public static final int HELP_CLOSES = 366;
	/** ...and draws their bow. The fairy raises her shield, and two volleys (an arrow from each) stop on it. */
	public static final int DRAW = 360;
	public static final int SHIELD_UP = 364;
	public static final int VOLLEY_ONE = 368;
	public static final int VOLLEY_TWO = 388;
	public static final int FAIRY_LINE_FOUR = 406;
	public static final int SHIELD_DOWN = 418;
	/** She strikes the first archer down, then the second. */
	public static final int STRIKE_ONE = 426;
	public static final int STRIKE_TWO = 442;
	public static final int MADDIE_CRIES = 468;
	/** The fairy raises her wand and Maddie is lifted off her feet, {@link #LIFT_HEIGHT} blocks in {@link #LIFT_TIME} ticks... */
	public static final int LIFT = 492;
	public static final int LIFT_TIME = 30;
	public static final double LIFT_HEIGHT = 1.4;
	/** ...the fairy flies up close to her in {@link #APPROACH_TIME} ticks, to where her wand's tip just reaches ({@link #touchSpot})... */
	public static final int APPROACH = 508;
	public static final int APPROACH_TIME = 26;
	/** ...reaches out with it, taking this many ticks (the model's PRUNE pose)... */
	public static final int REACH = 6;
	/**
	 * ...and touches her heart with it. Maddie is pruned: the flag's light spreads over her from the wand and she comes
	 * apart into it (the clients draw it: PruneEffect). From {@link #PRUNED} only the light is left of her.
	 */
	public static final int TOUCH = 540;
	public static final int PRUNED = 550;
	/** The fairy floats back over the altar, in {@link #RETURN_TIME} ticks, and turns to the player. */
	public static final int RETURN = 570;
	public static final int RETURN_TIME = 26;
	public static final int FAIRY_LINE_TWO = 600;
	public static final int FAIRY_LINE_THREE = 665;
	public static final int FIGHT = 725;
	/** Where Maddie stands once she's walked out of her door. */
	public static final Vec3 STAND = MADDIE_PORTAL.add(0.0, 0.0, WALK_DISTANCE - WALK_FROM);
	/**
	 * The touch: where the tip of the fairy's wand is in her PRUNE pose (up from her feet, ahead of her and to her right,
	 * from TransFairyModel's arm and wand), and where on Maddie it touches (up from her feet, and in front of her middle).
	 */
	public static final double WAND_UP = 1.56;
	public static final double WAND_AHEAD = 1.15;
	public static final double WAND_RIGHT = 0.25;
	public static final double HEART = 1.25;
	public static final double HEART_AHEAD = 0.14;
	/** Ticks after a strike before someone it missed falls anyway. */
	private static final int FALLS_AFTER = 15;
	/** Ticks between the two archers' shots in a volley. */
	private static final int STAGGER = 4;

	@Nullable
	private static Scene scene;

	private static final class Scene {
		int tick;
		@Nullable
		Maddie maddie;
		@Nullable
		TransFairy fairy;
		/** The two archers, west then east (an entry stays null if one couldn't be made). */
		final SculkArcher[] archers = new SculkArcher[2];
		/** The arrows in flight, for the shield to stop. */
		final List<AbstractArrow> arrows = new ArrayList<>();
		/** Where Maddie stood when she was lifted, and where the fairy flew in from and back from. */
		@Nullable
		Vec3 liftFrom;
		@Nullable
		Vec3 approachFrom;
		@Nullable
		Vec3 returnFrom;
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
		// The scene lives in memory only: a world closed mid-scene mustn't carry it into the next one opened (in single
		// player the game keeps running between worlds), where it would block the altar or call her into an empty realm.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> scene = null);
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
		if (level.players().isEmpty()) {
			// Everyone left the realm (or logged out) mid-scene.
			endUnwatched(level, s, t);
			return;
		}
		Vec3 portal = MADDIE_PORTAL;
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
		if (t == FAIRY_APPEARS) {
			// She appears the way she always has: in a burst of sparkles, with a chime (FairyRealm#spawnFairy). The intro
			// only holds her still and unhurt over the altar until the fight.
			s.fairy = FairyRealm.spawnFairy(level, FairyRealm.FAIRY_SPAWN);
			if (s.fairy != null) {
				s.fairy.setIntro(true);
				s.fairy.lookAtDuringIntro(portal.add(0.0, 1.6, 0.0));
			}
		}
		if (t == FAIRY_LINE_ONE) {
			say(level, FAIRY, "message.transdimension.cutscene.fairy_1");
		}
		helpArrives(level, s, t);
		if (t == MADDIE_CRIES) {
			say(level, MADDIE, "message.transdimension.cutscene.maddie_5");
			if (s.fairy != null && s.maddie != null) {
				s.fairy.lookAtDuringIntro(s.maddie.getEyePosition());
			}
		}
		prune(level, s, t);
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
			clearHelp(s);
			hold(level, false);
			send(level, new FairyCutscenePayload(FairyCutscenePayload.END, "", ""));
			scene = null;
		}
	}

	/**
	 * The middle of the scene: Maddie calls her friends, the two sculk archers walk out of their doors and shoot at the
	 * fairy, her shield stops every arrow, and she strikes them down. Each step is its own check, like tick's.
	 */
	private static void helpArrives(ServerLevel level, Scene s, int t) {
		if (t == CALL_FOR_HELP) {
			say(level, MADDIE, "message.transdimension.cutscene.maddie_4");
			if (s.maddie != null) {
				// she turns to face the fairy as she calls
				face(s.maddie, fairyMiddle(s));
			}
		}
		for (int i = 0; i < HELP_PORTALS.size(); i++) {
			Vec3 door = HELP_PORTALS.get(i);
			if (t == HELP_OPENS) {
				level.playSound(null, door.x, door.y, door.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.0F, 1.9F);
				level.playSound(null, door.x, door.y, door.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.2F, 1.3F);
			}
			if (t == HELP_ARRIVES) {
				s.archers[i] = bringArcher(level, door);
			}
			SculkArcher archer = s.archers[i];
			if (archer != null && archer.isAlive() && t > HELP_ARRIVES && t <= HELP_ARRIVES + HELP_WALK) {
				archer.setPos(archer.getX(), archer.getY(), archer.getZ() + HELP_WALK_DISTANCE / HELP_WALK);
			}
			if (t == HELP_CLOSES) {
				level.playSound(null, door.x, door.y, door.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.8F, 1.9F);
			}
			if (archer == null || !archer.isAlive()) {
				continue;
			}
			if (t == DRAW) {
				archer.setAiming(true);
				face(archer, fairyMiddle(s));
			}
			// one arrow each per volley, the second archer a moment after the first
			if (t == VOLLEY_ONE + STAGGER * i || t == VOLLEY_TWO + STAGGER * i) {
				face(archer, fairyMiddle(s));
				AbstractArrow arrow = archer.shootAt(level, fairyMiddle(s));
				if (arrow != null) {
					s.arrows.add(arrow);
				}
			}
			int strike = i == 0 ? STRIKE_ONE : STRIKE_TWO;
			if (t == strike) {
				archer.setAiming(false);
				archer.setInvulnerable(false);
				if (s.fairy != null) {
					// she turns on them one at a time, and her bolt is what ends it (they're left with one heart)
					archer.setHealth(1.0F);
					s.fairy.lookAtDuringIntro(archer.getEyePosition());
					s.fairy.castAt(archer);
				}
			}
			if (t == strike + FALLS_AFTER) {
				archer.hurtServer(level, s.fairy != null ? archer.damageSources().indirectMagic(s.fairy, s.fairy)
						: archer.damageSources().magic(), 100.0F);
			}
		}
		if (t == SHIELD_UP && s.fairy != null) {
			s.fairy.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 1.6F);
		}
		if (t == FAIRY_LINE_FOUR) {
			say(level, FAIRY, "message.transdimension.cutscene.fairy_4");
		}
		if (t >= SHIELD_UP && t <= SHIELD_DOWN) {
			stopArrows(level, s, t == SHIELD_DOWN);
		}
	}

	/**
	 * The end of the scene for Maddie: the fairy lifts her off her feet, flies up close, reaches out and touches her heart
	 * with her wand, and Maddie is pruned (each client draws her coming apart into light: PruneEffect); then the fairy
	 * floats back over the altar. Each step is its own check, like tick's.
	 */
	private static void prune(ServerLevel level, Scene s, int t) {
		Maddie maddie = s.maddie;
		TransFairy fairy = s.fairy;
		if (t == LIFT && maddie != null && maddie.isAlive()) {
			s.liftFrom = maddie.position();
			maddie.setNoGravity(true);
			face(maddie, fairyMiddle(s));
			level.playSound(null, maddie.getX(), maddie.getY() + 1.0, maddie.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL,
					1.4F, 0.7F);
			if (fairy != null) {
				fairy.lookAtDuringIntro(maddie.getEyePosition());
				fairy.playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.5F, 1.6F);
			}
		}
		Vec3 from = s.liftFrom;
		if (maddie != null && from != null && t > LIFT && t <= LIFT + LIFT_TIME) {
			maddie.setPos(from.x, from.y + LIFT_HEIGHT * ease((double) (t - LIFT) / LIFT_TIME), from.z);
			if ((t - LIFT) % 5 == 0) {
				FairyRealm.sparkle(level, maddie.position().add(0.0, 0.9, 0.0), 3, 0.35);
			}
		}
		if (from != null && fairy != null) {
			Vec3 lifted = from.add(0.0, LIFT_HEIGHT, 0.0);
			Vec3 spot = touchSpot(lifted);
			if (t == APPROACH) {
				s.approachFrom = fairy.position();
				fairy.lookAtDuringIntro(lifted.add(0.0, HEART, 0.0));
				fairy.playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 1.4F);
			}
			Vec3 approachFrom = s.approachFrom;
			if (approachFrom != null && t > APPROACH && t <= APPROACH + APPROACH_TIME) {
				hold(fairy, approachFrom.lerp(spot, ease((double) (t - APPROACH) / APPROACH_TIME)));
				if (t == APPROACH + APPROACH_TIME) {
					// square on to her (not at her middle: the wand is in her right hand), so its tip meets her heart
					fairy.lookAtDuringIntro(spot.subtract(towardFairy(lifted).scale(4.0)));
				}
			}
			if (t == TOUCH - REACH) {
				fairy.reachOut(true);
			}
		}
		if (t == TOUCH && maddie != null && maddie.isAlive()) {
			send(level, new FairyCutscenePayload(FairyCutscenePayload.PRUNE, "", ""));
			Vec3 heart = maddie.position().add(0.0, HEART, 0.0);
			level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.NEUTRAL, 1.5F, 1.2F);
			level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.5F, 1.5F);
		}
		if (t == PRUNED) {
			if (maddie != null && !maddie.isRemoved()) {
				Vec3 heart = maddie.position().add(0.0, HEART, 0.0);
				level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.8F, 1.7F);
				level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 1.2F, 0.8F);
				maddie.discard();
			}
			FairyRealmState state = level.getAttachedOrElse(ModAttachments.FAIRY_REALM_STATE, FairyRealmState.NEW);
			level.setAttached(ModAttachments.FAIRY_REALM_STATE, state.withMaddieGone());
		}
		if (t == TOUCH + 24 && from != null) {
			Vec3 heart = from.add(0.0, LIFT_HEIGHT + HEART + 0.8, 0.0);
			level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.2F, 0.6F);
		}
		if (fairy != null && t == RETURN) {
			fairy.reachOut(false);
			s.returnFrom = fairy.position();
		}
		Vec3 returnFrom = s.returnFrom;
		if (fairy != null && returnFrom != null && t > RETURN && t <= RETURN + RETURN_TIME) {
			hold(fairy, returnFrom.lerp(FairyRealm.FAIRY_SPAWN, ease((double) (t - RETURN) / RETURN_TIME)));
		}
	}

	/**
	 * Where the fairy's feet go to touch Maddie (her feet at {@code maddieFeet}, facing the fairy over the altar) on the
	 * heart with the tip of her wand: in front of Maddie, a little to the fairy's left so the wand in her right hand lines
	 * up, and low enough for the wand to meet Maddie's heart.
	 */
	public static Vec3 touchSpot(Vec3 maddieFeet) {
		Vec3 ahead = towardFairy(maddieFeet);
		// the fairy faces back along `ahead`; her right hand is then this way
		Vec3 herRight = new Vec3(ahead.z, 0.0, -ahead.x);
		return maddieFeet.add(ahead.scale(HEART_AHEAD + WAND_AHEAD)).subtract(herRight.scale(WAND_RIGHT)).add(0.0, HEART - WAND_UP, 0.0);
	}

	/** Level, from Maddie (her feet at {@code maddieFeet}) towards where the fairy appears over the altar. */
	public static Vec3 towardFairy(Vec3 maddieFeet) {
		Vec3 toFairy = new Vec3(FairyRealm.FAIRY_SPAWN.x - maddieFeet.x, 0.0, FairyRealm.FAIRY_SPAWN.z - maddieFeet.z);
		return toFairy.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : toFairy.normalize();
	}

	/** Puts the fairy at {@code at}, still (the cutscene flies her by hand). */
	private static void hold(TransFairy fairy, Vec3 at) {
		fairy.setPos(at.x, at.y, at.z);
		fairy.setDeltaMovement(Vec3.ZERO);
	}

	/** 0 to 1, easing in and out. */
	private static double ease(double k) {
		double c = Mth.clamp(k, 0.0, 1.0);
		return c * c * (3.0 - 2.0 * c);
	}

	/** The fairy's shield: any arrow that reaches it stops there with a flash (all of them, once it's lowered). */
	private static void stopArrows(ServerLevel level, Scene s, boolean all) {
		Vec3 middle = fairyMiddle(s);
		for (AbstractArrow arrow : List.copyOf(s.arrows)) {
			Vec3 at = arrow.position();
			if (arrow.isRemoved()) {
				s.arrows.remove(arrow);
			} else if (all || at.distanceToSqr(middle) < SHIELD_RADIUS * SHIELD_RADIUS) {
				Vec3 out = at.subtract(middle);
				Vec3 hit = out.lengthSqr() < 1.0E-4 ? middle : middle.add(out.normalize().scale(SHIELD_RADIUS));
				arrow.discard();
				s.arrows.remove(arrow);
				if (!all) {
					level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 1.0F, 1.3F);
					level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 1.2F, 1.5F);
					FairyRealm.sparkle(level, hit, 8, 0.15);
					send(level, new FairyCutscenePayload(FairyCutscenePayload.SHIELD, "", ""));
				}
			}
		}
	}

	/** Where the fairy's middle is (or will be, if she hasn't come yet): what the archers shoot at and the shield's centre. */
	private static Vec3 fairyMiddle(Scene s) {
		TransFairy fairy = s.fairy;
		return fairy != null && fairy.isAlive() ? fairy.position().add(0.0, fairy.getBbHeight() / 2.0, 0.0)
				: FairyRealm.FAIRY_SPAWN.add(0.0, 1.2, 0.0);
	}

	/** An archer is put just behind their door's face, bow in hand, facing the altar (they step out of it: helpArrives). */
	@Nullable
	private static SculkArcher bringArcher(ServerLevel level, Vec3 door) {
		SculkArcher archer = ModEntities.SCULK_ARCHER.create(level, EntitySpawnReason.EVENT);
		if (archer == null) {
			return null;
		}
		archer.snapTo(door.x, door.y, door.z - WALK_FROM, 0.0F, 0.0F);
		archer.setYHeadRot(0.0F);
		archer.yBodyRot = 0.0F;
		archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		level.addFreshEntity(archer);
		level.playSound(null, door.x, door.y, door.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.2F, 0.9F);
		return archer;
	}

	/** Takes the archers (living or not) and their arrows off the stage. */
	private static void clearHelp(Scene s) {
		for (SculkArcher archer : s.archers) {
			if (archer != null && !archer.isRemoved()) {
				archer.discard();
			}
		}
		for (AbstractArrow arrow : s.arrows) {
			if (!arrow.isRemoved()) {
				arrow.discard();
			}
		}
		s.arrows.clear();
	}

	/** Maddie is put just behind her door's face, facing the altar (she steps out of it and walks on: see tick). */
	@Nullable
	private static Maddie bringMaddie(ServerLevel level, Vec3 door) {
		Maddie maddie = ModEntities.MADDIE.create(level, EntitySpawnReason.EVENT);
		if (maddie == null) {
			return null;
		}
		maddie.snapTo(door.x, door.y, door.z - WALK_FROM, 0.0F, 0.0F);
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
			face(maddie, watcher.getEyePosition());
		}
	}

	/** Turns an actor (who has no mind of their own to do it) to look at {@code point}: body, head and eyes. */
	private static void face(Mob actor, Vec3 point) {
		Vec3 d = point.subtract(actor.getEyePosition());
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		actor.setYRot(yaw);
		actor.setYHeadRot(yaw);
		actor.yBodyRot = yaw;
		actor.setXRot(pitch);
	}

	/**
	 * Nobody's left in the realm mid-scene. Before Maddie has fallen the scene is called off: she, her archers and the
	 * fairy go, the offering is given back on the altar, and throwing it again starts the scene over. After it, the story
	 * has moved on: the fairy is there for the fight (spawned now if she isn't).
	 */
	private static void endUnwatched(ServerLevel level, Scene s, int t) {
		clearHelp(s);
		if (t < PRUNED) {
			if (s.maddie != null && !s.maddie.isRemoved()) {
				s.maddie.discard();
			}
			if (s.fairy != null && !s.fairy.isRemoved()) {
				s.fairy.discard();
			}
			Vec3 altar = altarTop();
			level.addFreshEntity(new ItemEntity(level, altar.x, altar.y + 0.2, altar.z, new ItemStack(ModItems.TRANS_CRYSTAL)));
		} else if (s.fairy != null && s.fairy.isAlive()) {
			s.fairy.setIntro(false);
		} else if (!FairyRealm.fairyNearby(level, FairyRealm.ALTAR)) {
			FairyRealm.spawnFairy(level, FairyRealm.FAIRY_SPAWN);
		}
		hold(level, false);
		scene = null;
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
