package dev.goober.transdimension.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.goober.transdimension.entity.Maddie;
import dev.goober.transdimension.entity.SculkArcher;
import dev.goober.transdimension.entity.TransFairy;
import dev.goober.transdimension.world.FairyCutscene;
import dev.goober.transdimension.world.FairyRealm;

/**
 * The Fairy Realm cutscene's camera (the scene itself runs on the server: FairyCutscene), shot like a film: a list of
 * takes with hard cuts between them. Each take frames whoever is acting, looking at where they really are this frame (so
 * someone walking or falling stays in frame), and holds still or creeps slowly in. In order:
 * <ol>
 * <li>the player's view rises into a wide shot of the arena;</li>
 * <li>the door of light opening for Maddie (drawn here, {@link #renderDoor}), then backing away in front of her as she
 * walks out of it;</li>
 * <li>close on her first line, over her shoulder at the player for her second, pushing in on her third;</li>
 * <li>over her shoulder at the empty air above the altar, where the Trans Fairy appears, then up at the fairy as she
 * hushes Maddie;</li>
 * <li>low on Maddie as she calls her friends, then wide on the three doors as two more open;</li>
 * <li>low on the first archer walking out, over the second's shoulder as they draw and shoot, on the fairy as the arrows
 * stop on her shield (drawn here, {@link #renderShield}), side on as the second volley flies, close on her scorn;</li>
 * <li>over her shoulder as she strikes each archer down, close on Maddie's cry, low on the fairy raising her wand, on
 * Maddie as the bolt strikes her;</li>
 * <li>side on as the fairy turns to the player, and close on her last line, before the view cuts back to the player, who
 * now faces her.</li>
 * </ol>
 *
 * <p>The takes are timed to the server's timeline (FairyCutscene's constants), counted from the client's game time when
 * the scene's START packet came in. The camera itself is moved by CameraMixin; the view switches to third person
 * meanwhile (so the player is seen in the scene and no hand floats in front of the camera), and the HUD is hidden
 * (TransDimensionClient). Everything goes back the way it was when the scene ends.
 */
public final class FairyCutsceneCamera {
	private static final int WHITE = 0xFFFFFF;
	private static final int PINK = 0xF5A9B8;
	private static final int BLUE = 0x5BCEFA;
	/** The doors, standing on FairyCutscene.MADDIE_PORTAL and HELP_PORTALS and facing the altar (south). */
	private static final double DOOR_WIDTH = 1.5;
	private static final double DOOR_HEIGHT = 2.8;
	/** Ticks a door takes to open (top to bottom), and to close (bottom to top). */
	private static final int DOOR_OPENING = 20;
	/** Ticks the shield takes to fade when she lowers it, and how long it flares after each arrow. */
	private static final int SHIELD_FADE = 8;
	private static final int SHIELD_FLARE = 7;

	// Where everyone is meant to be: the takes are placed from these (where the actors really are is looked up each frame).
	private static final Vec3 ALTAR = Vec3.atBottomCenterOf(FairyRealm.ALTAR).add(0.0, 1.0, 0.0);
	private static final Vec3 PORTAL = FairyCutscene.MADDIE_PORTAL;
	private static final Vec3 DOOR = PORTAL.add(0.0, 1.4, 0.0);
	/** Where Maddie stops, having walked out of her door, and her eyes there. */
	private static final Vec3 STAND = PORTAL.add(0.0, 0.0, FairyCutscene.WALK_DISTANCE - FairyCutscene.WALK_FROM);
	private static final Vec3 MADDIE_EYE = STAND.add(0.0, 1.62, 0.0);
	private static final Vec3 FAIRY_EYE = FairyRealm.FAIRY_SPAWN.add(0.0, 2.0, 0.0);
	private static final Vec3 FAIRY_MIDDLE = FairyRealm.FAIRY_SPAWN.add(0.0, 1.2, 0.0);
	/** Each archer's eyes (west, then east) where they stop. */
	private static final List<Vec3> ARCHER_EYES = List.of(archerEye(0), archerEye(1));
	/** How far round the altar the actors are looked for. */
	private static final double STAGE = 24.0;

	/** Where the camera stands, or what it looks at, this frame, given where everyone is. */
	@FunctionalInterface
	private interface Place {
		Vec3 at(Cast cast);
	}

	/**
	 * One take, from {@code start} until the next one cuts in: the camera eases from {@code from} to {@code to} (a slow
	 * push or drift, if anything) while it looks from {@code lookFrom} to {@code lookTo}.
	 */
	private record Take(int start, Place from, Place to, Place lookFrom, Place lookTo) {
		Take(int start, Place from, Place to, Place look) {
			this(start, from, to, look, look);
		}
	}

	/** Where the actors are this frame (or where they're meant to be, while one isn't there). */
	private record Cast(Vec3 player, Vec3 maddieEye, Vec3 maddieBody, Vec3 fairyEye, Vec3 fairyMiddle, Vec3 westEye,
			Vec3 westBody, Vec3 eastEye, Vec3 eastBody) {
	}

	/** Where the camera is and which way it faces this frame. */
	public record Shot(Vec3 position, float yaw, float pitch) {
	}

	private static boolean active;
	private static long startTick;
	/** When (scene time) the last arrow stopped on the fairy's shield. */
	private static double lastShieldHit = -100.0;
	private static List<Take> takes = List.of();
	@Nullable
	private static CameraType savedCameraType;

	private FairyCutsceneCamera() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(FairyCutsceneCamera::renderLight);
	}

	/** How far down a door has opened at {@code t}: 0 (just its top bar) to 1 (down to the floor), eased. */
	private static double doorDown(double t, double opens, double closes) {
		double k = t < closes ? (t - opens) / DOOR_OPENING : 1.0 - (t - closes) / DOOR_OPENING;
		double down = Mth.clamp((Mth.clamp(k, 0.0, 1.0) - 0.25) / 0.75, 0.0, 1.0);
		return down * down * (3.0 - 2.0 * down);
	}

	public static boolean isActive() {
		return active;
	}

	/** The scene starts: plan the takes from where the player stands and looks now. */
	public static void start() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		takes = plan(eye, eye.add(player.getLookAngle().scale(4.0)));
		startTick = minecraft.level.getGameTime();
		lastShieldHit = -100.0;
		if (!active) {
			savedCameraType = minecraft.options.getCameraType();
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		active = true;
	}

	/** The scene ends (or is cut short): the camera returns to the player, who now faces the fairy. */
	public static void stop() {
		if (!active) {
			return;
		}
		active = false;
		Minecraft minecraft = Minecraft.getInstance();
		if (savedCameraType != null) {
			minecraft.options.setCameraType(savedCameraType);
			savedCameraType = null;
		}
		LocalPlayer player = minecraft.player;
		if (player != null && minecraft.level != null) {
			Shot facing = aim(player.getEyePosition(), cast(minecraft).fairyEye());
			player.setYRot(facing.yaw());
			player.setXRot(facing.pitch());
			player.yRotO = facing.yaw();
			player.xRotO = facing.pitch();
			player.setYHeadRot(facing.yaw());
		}
	}

	/** An arrow stopped on the fairy's shield (FairyCutscenePayload.SHIELD): it flares. */
	public static void shieldHit() {
		Minecraft minecraft = Minecraft.getInstance();
		if (active && minecraft.level != null) {
			lastShieldHit = time(minecraft);
		}
	}

	/** This frame's shot, or null when no scene is playing (CameraMixin leaves the camera alone then). */
	@Nullable
	public static Shot current() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!active || minecraft.level == null || takes.isEmpty()) {
			return null;
		}
		double t = time(minecraft);
		int i = 0;
		while (i < takes.size() - 1 && takes.get(i + 1).start() <= t) {
			i++;
		}
		Take take = takes.get(i);
		double end = i < takes.size() - 1 ? takes.get(i + 1).start() : FairyCutscene.FIGHT;
		double u = Mth.clamp((t - take.start()) / Math.max(1.0, end - take.start()), 0.0, 1.0);
		u = u * u * (3.0 - 2.0 * u);
		Cast cast = cast(minecraft);
		Vec3 position = take.from().at(cast).lerp(take.to().at(cast), u);
		Vec3 look = take.lookFrom().at(cast).lerp(take.lookTo().at(cast), u);
		return aim(position, look);
	}

	private static double time(Minecraft minecraft) {
		return minecraft.level.getGameTime() - startTick + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	/** Where everyone is this frame: Maddie, the fairy and the archers if they're about, the player always. */
	private static Cast cast(Minecraft minecraft) {
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		AABB stage = new AABB(ALTAR, ALTAR).inflate(STAGE);
		Vec3 maddieEye = MADDIE_EYE;
		Vec3 maddieBody = STAND.add(0.0, 0.9, 0.0);
		for (Maddie maddie : minecraft.level.getEntitiesOfClass(Maddie.class, stage)) {
			maddieEye = maddie.getEyePosition(partialTick);
			maddieBody = maddie.getPosition(partialTick).add(0.0, maddie.getBbHeight() / 2.0, 0.0);
		}
		Vec3 fairyEye = FAIRY_EYE;
		Vec3 fairyMiddle = FAIRY_MIDDLE;
		for (TransFairy fairy : minecraft.level.getEntitiesOfClass(TransFairy.class, stage)) {
			fairyEye = fairy.getEyePosition(partialTick);
			fairyMiddle = fairy.getPosition(partialTick).add(0.0, fairy.getBbHeight() / 2.0, 0.0);
		}
		Vec3[] eyes = {ARCHER_EYES.get(0), ARCHER_EYES.get(1)};
		Vec3[] bodies = {eyes[0].add(0.0, -0.7, 0.0), eyes[1].add(0.0, -0.7, 0.0)};
		for (SculkArcher archer : minecraft.level.getEntitiesOfClass(SculkArcher.class, stage)) {
			int i = archer.getX() < PORTAL.x ? 0 : 1;
			eyes[i] = archer.getEyePosition(partialTick);
			bodies[i] = archer.getPosition(partialTick).add(0.0, archer.getBbHeight() / 2.0, 0.0);
		}
		LocalPlayer player = minecraft.player;
		Vec3 playerEye = player != null ? player.getEyePosition(partialTick) : ALTAR.add(0.0, 1.6, 3.0);
		return new Cast(playerEye, maddieEye, maddieBody, fairyEye, fairyMiddle, eyes[0], bodies[0], eyes[1], bodies[1]);
	}

	/**
	 * The takes, timed to FairyCutscene's timeline. {@code eye} and {@code gaze}: where the player's eyes are and what
	 * they look at as it starts (they're held still through the scene, so the takes that frame them can be placed now).
	 */
	private static List<Take> plan(Vec3 eye, Vec3 gaze) {
		Vec3 westEye = ARCHER_EYES.get(0);
		Vec3 eastEye = ARCHER_EYES.get(1);
		Vec3 westDoor = FairyCutscene.HELP_PORTALS.get(0);
		List<Take> list = new ArrayList<>();
		// The player's view rises into a wide shot of the arena, looking past the altar to where the door will open.
		list.add(new Take(0, cast -> eye, fixed(ALTAR.add(6.5, 5.0, 8.5)), cast -> gaze, fixed(ALTAR.add(0.0, 0.5, -6.5))));
		// The door opens; Maddie walks out of it towards us as we back away.
		list.add(new Take(FairyCutscene.PORTAL_OPENS, fixed(PORTAL.add(1.2, 1.5, 7.0)), fixed(PORTAL.add(1.0, 1.5, 6.2)), fixed(DOOR)));
		list.add(new Take(FairyCutscene.MADDIE_ARRIVES + 4, fixed(PORTAL.add(0.9, 1.5, 3.4)), fixed(STAND.add(0.9, 1.5, 2.6)),
				Cast::maddieEye));
		// Her lines: close from her right, over her shoulder at the player she's talking to, pushing in from her left.
		list.add(new Take(FairyCutscene.LINE_ONE - 2, fixed(around(MADDIE_EYE, ALTAR, 2.0, 0.8, 0.0)),
				fixed(around(MADDIE_EYE, ALTAR, 1.75, 0.7, 0.0)), Cast::maddieEye));
		Vec3 behindMaddie = around(MADDIE_EYE, eye, -1.2, -0.55, 0.25);
		list.add(new Take(FairyCutscene.LINE_TWO - 4, fixed(behindMaddie), fixed(behindMaddie), Cast::player));
		list.add(new Take(FairyCutscene.LINE_THREE - 4, fixed(around(MADDIE_EYE, ALTAR, 2.0, -0.9, 0.0)),
				fixed(around(MADDIE_EYE, ALTAR, 1.3, -0.55, 0.0)), Cast::maddieEye));
		// Over her shoulder at the empty air over the altar, where the fairy appears; then up at her as she hushes Maddie.
		list.add(new Take(FairyCutscene.LOOK_TO_ALTAR - 4, fixed(around(MADDIE_EYE, FAIRY_MIDDLE, -1.1, 0.6, 0.3)),
				fixed(around(MADDIE_EYE, FAIRY_MIDDLE, -0.85, 0.55, 0.3)), fixed(FAIRY_MIDDLE)));
		list.add(new Take(FairyCutscene.FAIRY_APPEARS + 11, fixed(ALTAR.add(2.2, 3.8, -4.8)), fixed(ALTAR.add(1.8, 4.0, -4.1)),
				Cast::fairyEye));
		// Low on Maddie as she calls her friends, then wide on the three doors as theirs open either side of hers.
		list.add(new Take(FairyCutscene.CALL_FOR_HELP - 5, fixed(around(MADDIE_EYE, ALTAR, 1.9, 0.7, -0.45)),
				fixed(around(MADDIE_EYE, ALTAR, 1.55, 0.55, -0.4)), Cast::maddieEye));
		list.add(new Take(FairyCutscene.HELP_OPENS, fixed(PORTAL.add(0.0, 2.6, 7.5)), fixed(PORTAL.add(0.0, 2.6, 6.9)),
				fixed(PORTAL.add(0.0, 1.6, 0.0))));
		// Low by the floor as the first archer walks out of their door.
		list.add(new Take(FairyCutscene.HELP_ARRIVES + 2, fixed(westDoor.add(1.7, 0.55, 3.2)), fixed(westDoor.add(1.55, 0.6, 3.4)),
				Cast::westEye));
		// Over the second archer's shoulder as they draw and loose at the fairy.
		list.add(new Take(FairyCutscene.DRAW, fixed(around(eastEye, FAIRY_MIDDLE, -1.0, -0.55, -0.3)),
				fixed(around(eastEye, FAIRY_MIDDLE, -0.9, -0.5, -0.3)), Cast::fairyMiddle));
		// The arrows stop on her shield; then side on, the first archer and the fairy both in frame, as the second volley flies.
		list.add(new Take(FairyCutscene.VOLLEY_ONE + 6, fixed(FAIRY_MIDDLE.add(-2.6, -0.9, -3.6)), fixed(FAIRY_MIDDLE.add(-2.3, -0.8, -3.2)),
				Cast::fairyMiddle));
		Vec3 crossfire = westEye.lerp(FAIRY_MIDDLE, 0.4);
		list.add(new Take(FairyCutscene.VOLLEY_TWO, fixed(ALTAR.add(-9.0, 2.8, -3.0)), fixed(ALTAR.add(-8.7, 2.9, -2.7)), fixed(crossfire)));
		// Close on the fairy's scorn.
		list.add(new Take(FairyCutscene.FAIRY_LINE_FOUR - 2, fixed(FAIRY_EYE.add(0.9, -0.5, -3.2)), fixed(FAIRY_EYE.add(0.7, -0.45, -2.7)),
				Cast::fairyEye));
		// Over her shoulder as she strikes each archer down.
		list.add(new Take(FairyCutscene.STRIKE_ONE - 4, fixed(around(FAIRY_EYE, westEye, -2.5, 0.7, 0.4)),
				fixed(around(FAIRY_EYE, westEye, -2.35, 0.65, 0.4)), Cast::westBody));
		list.add(new Take(FairyCutscene.STRIKE_TWO + 4, fixed(around(FAIRY_EYE, eastEye, -2.5, -0.7, 0.4)),
				fixed(around(FAIRY_EYE, eastEye, -2.35, -0.65, 0.4)), Cast::eastBody));
		// Close on Maddie's cry; low on the fairy as she raises her wand; on Maddie as the bolt strikes her, following her down.
		list.add(new Take(FairyCutscene.MADDIE_CRIES - 4, fixed(around(MADDIE_EYE, ALTAR, 1.9, -0.6, 0.0)),
				fixed(around(MADDIE_EYE, ALTAR, 1.4, -0.45, 0.0)), Cast::maddieEye));
		list.add(new Take(FairyCutscene.WAND_RAISED - 4, fixed(ALTAR.add(-2.6, 3.0, -6.2)), fixed(ALTAR.add(-2.3, 2.8, -5.8)),
				Cast::fairyEye));
		list.add(new Take(FairyCutscene.SHOT + 4, fixed(around(MADDIE_EYE, ALTAR, 1.0, -2.4, -0.4)),
				fixed(around(MADDIE_EYE, ALTAR, 0.9, -2.2, -0.5)), Cast::maddieBody));
		// Side on as she turns from Maddie to the player, then close on her from their side for her last line.
		list.add(new Take(FairyCutscene.FAIRY_LINE_TWO - 3, fixed(FAIRY_EYE.add(4.2, -0.5, 0.3)), fixed(FAIRY_EYE.add(3.8, -0.45, 0.4)),
				Cast::fairyEye));
		list.add(new Take(FairyCutscene.FAIRY_LINE_THREE - 2, fixed(around(FAIRY_EYE, eye, 3.8, 0.35, -0.6)),
				fixed(around(FAIRY_EYE, eye, 2.9, 0.25, -0.5)), Cast::fairyEye));
		return list;
	}

	private static Place fixed(Vec3 point) {
		return cast -> point;
	}

	/**
	 * A point near {@code from}, placed by someone there facing {@code toward}: {@code forward} blocks ahead (negative:
	 * behind), {@code right} to their right (negative: left) and {@code up} above. Facing is taken level; straight
	 * above or below, they face south.
	 */
	private static Vec3 around(Vec3 from, Vec3 toward, double forward, double right, double up) {
		Vec3 flat = new Vec3(toward.x - from.x, 0.0, toward.z - from.z);
		Vec3 ahead = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		return from.add(ahead.scale(forward)).add(-ahead.z * right, up, ahead.x * right);
	}

	private static Vec3 archerEye(int i) {
		return FairyCutscene.HELP_PORTALS.get(i).add(0.0, 1.62, FairyCutscene.HELP_WALK_DISTANCE - FairyCutscene.WALK_FROM);
	}

	/** A camera at {@code position} looking at {@code look}. */
	private static Shot aim(Vec3 position, Vec3 look) {
		Vec3 d = look.subtract(position);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG);
		return new Shot(position, yaw, pitch);
	}

	/** The scene's light: the doors and the fairy's shield. */
	private static void renderLight(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!active || minecraft.level == null) {
			return;
		}
		double t = time(minecraft);
		GlowGeometry g = new GlowGeometry(context.levelState().cameraRenderState.pos);
		renderDoor(g, t, FairyCutscene.MADDIE_PORTAL, FairyCutscene.PORTAL_OPENS, FairyCutscene.PORTAL_CLOSES);
		for (Vec3 door : FairyCutscene.HELP_PORTALS) {
			renderDoor(g, t, door, FairyCutscene.HELP_OPENS, FairyCutscene.HELP_CLOSES);
		}
		renderShield(g, t, minecraft);
		g.submit(context);
	}

	/**
	 * The Trans Fairy's shield while the archers shoot: a faint bubble round her in the flag's gradient (blue at the top and
	 * bottom, then pink, white round her middle) that flares as each arrow stops on it and fades as she lowers it.
	 */
	private static void renderShield(GlowGeometry g, double t, Minecraft minecraft) {
		double up = FairyCutscene.SHIELD_UP;
		double down = FairyCutscene.SHIELD_DOWN;
		if (t < up || t > down + SHIELD_FADE) {
			return;
		}
		double shown = Math.min(Mth.clamp((t - up) / 5.0, 0.0, 1.0), Mth.clamp((down + SHIELD_FADE - t) / SHIELD_FADE, 0.0, 1.0));
		double flare = Mth.clamp(1.0 - (t - lastShieldHit) / SHIELD_FLARE, 0.0, 1.0);
		flare *= flare;
		double alpha = shown * (0.08 + 0.3 * flare);
		double radius = FairyCutscene.SHIELD_RADIUS * (1.0 + 0.04 * flare);
		Vec3 middle = cast(minecraft).fairyMiddle();
		int rings = 9;
		int segments = 16;
		for (int i = 0; i < rings; i++) {
			double from = Math.PI * i / rings;
			double to = Math.PI * (i + 1) / rings;
			int colour = GlowGeometry.argb(alpha, gradient(Math.abs((i + 0.5) / rings * 2.0 - 1.0)));
			for (int j = 0; j < segments; j++) {
				double a = Math.PI * 2.0 * j / segments;
				double b = Math.PI * 2.0 * (j + 1) / segments;
				g.panel(onSphere(middle, radius, from, a), onSphere(middle, radius, from, b), onSphere(middle, radius, to, b),
						onSphere(middle, radius, to, a), colour);
			}
		}
	}

	/** A point on a sphere: {@code down} radians from its top, {@code round} radians round it. */
	private static Vec3 onSphere(Vec3 middle, double radius, double down, double round) {
		double out = Math.sin(down) * radius;
		return middle.add(Math.cos(round) * out, Math.cos(down) * radius, Math.sin(round) * out);
	}

	/** The flag's colours as a smooth gradient: white at 0, pink at 0.5, blue at 1. */
	private static int gradient(double across) {
		return across < 0.5 ? mix(WHITE, PINK, across / 0.5) : mix(PINK, BLUE, (across - 0.5) / 0.5);
	}

	/**
	 * A door like the time doors in the show (Maddie's, and her friends'): a doorway outlined by a thin bright frame with a
	 * soft glow round it, filled with a see-through haze of light that its walker comes out of. Here it's the trans flag as
	 * a smooth gradient: blue at the sides, through pink, to white down the middle. It opens from the top down (a bar of
	 * light draws out across the top, then the doorway comes down from it to the floor) and closes from the bottom up.
	 * Standing on {@code base}, open from {@code opens} to {@code closes}. The doors are the only effect as people
	 * arrive: no sparks, no extra particles.
	 */
	private static void renderDoor(GlowGeometry g, double t, Vec3 base, double opens, double closes) {
		if (t < opens || t > closes + DOOR_OPENING) {
			return;
		}
		// 0 to 1 as it opens (the first quarter the top bar drawing out, the rest the door coming down), 1 to 0 as it closes
		double k = t < closes ? (t - opens) / DOOR_OPENING : 1.0 - (t - closes) / DOOR_OPENING;
		double wide = Mth.clamp(Mth.clamp(k, 0.0, 1.0) / 0.25, 0.0, 1.0);
		wide = wide * wide * (3.0 - 2.0 * wide);
		double down = doorDown(t, opens, closes);
		double along = Math.max(0.02, DOOR_WIDTH / 2.0 * wide);
		double top = base.y + DOOR_HEIGHT;
		double bottom = top - DOOR_HEIGHT * down;
		double up = (top - bottom) / 2.0;
		Vec3 middle = new Vec3(base.x, (top + bottom) / 2.0, base.z);
		double shimmer = 0.5 + 0.5 * Math.sin(t * 0.35);
		if (up > 0.01) {
			// The haze: thin upright bands, their colour following the gradient across the doorway.
			int bands = 12;
			double band = along * 2.0 / bands;
			for (int i = 0; i < bands; i++) {
				int colour = gradient(Math.abs((i + 0.5) / bands * 2.0 - 1.0));
				Vec3 at = middle.add(-along + band * (i + 0.5), 0.0, 0.0);
				g.sheet(at, Direction.SOUTH, band / 2.0, up, 0.015, GlowGeometry.argb(0.32 + 0.06 * shimmer, colour));
			}
		}
		// The frame: a thin bright core, a pink glow round it and a fainter blue one; the top first, the sides coming down
		// with the doorway and the bottom edge following it down.
		double[][] glows = {{0.025, 0.85}, {0.06, 0.3}, {0.11, 0.12}};
		int[] glowColours = {WHITE, PINK, BLUE};
		Vec3 topLeft = new Vec3(middle.x - along, top, middle.z);
		Vec3 topRight = new Vec3(middle.x + along, top, middle.z);
		Vec3 bottomLeft = new Vec3(middle.x - along, bottom, middle.z);
		Vec3 bottomRight = new Vec3(middle.x + along, bottom, middle.z);
		for (int i = 0; i < glows.length; i++) {
			double half = glows[i][0] * (1.0 + 0.15 * shimmer);
			int colour = GlowGeometry.argb(glows[i][1], glowColours[i]);
			g.beam(topLeft, topRight, half, 0.0, colour);
			if (up > 0.01) {
				g.beam(topLeft, bottomLeft, half, 0.0, colour);
				g.beam(topRight, bottomRight, half, 0.0, colour);
				g.beam(bottomLeft, bottomRight, half, 0.0, colour);
			}
		}
	}

	/** {@code a} blended towards {@code b} by {@code f} (0 to 1), as 0xRRGGBB. */
	private static int mix(int a, int b, double f) {
		int r = (int) Math.round(Mth.lerp(f, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int gr = (int) Math.round(Mth.lerp(f, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = (int) Math.round(Mth.lerp(f, a & 0xFF, b & 0xFF));
		return r << 16 | gr << 8 | bl;
	}
}
