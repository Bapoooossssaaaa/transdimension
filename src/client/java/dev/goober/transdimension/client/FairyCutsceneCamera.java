package dev.goober.transdimension.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.goober.transdimension.world.FairyCutscene;
import dev.goober.transdimension.world.FairyRealm;

/**
 * The Fairy Realm cutscene's camera (the scene itself runs on the server: FairyCutscene). While it plays, the view leaves
 * the player and glides along a path of shots through the scene: up from the player's eyes into a crane shot of the
 * altar, round to the door of trans-striped light that opens for Maddie (drawn here, {@link #renderDoor}), in front of
 * her as she walks out of it, slowly round her as she speaks, over her shoulder as the light
 * gathers over the altar, up at the Trans Fairy as she appears in a falling column of light and speaks, across to Maddie
 * as the bolt strikes her, back to the fairy, and at last back down to the player's own eyes, turned to face the fairy
 * as the fight begins.
 *
 * <p>The path is a smooth curve through key shots timed to the server's timeline (FairyCutscene's constants), counted
 * from the client's game time when the scene's START packet came in. The camera itself is moved by CameraMixin; the view
 * switches to third person meanwhile (so the player is seen in the scene and no hand floats in front of the camera), and
 * the HUD is hidden (TransDimensionClient). Everything goes back the way it was when the scene ends.
 */
public final class FairyCutsceneCamera {
	private static final int WHITE = 0xFFFFFF;
	private static final int PINK = 0xF5A9B8;
	private static final int DEEP_PINK = 0xFF7EB3;
	private static final int BLUE = 0x5BCEFA;
	/** Maddie's door, standing on FairyCutscene.MADDIE_PORTAL and facing the altar (south). */
	private static final double DOOR_WIDTH = 1.5;
	private static final double DOOR_HEIGHT = 2.8;
	/** Ticks the door takes to open, and to close. */
	private static final int DOOR_OPENING = 12;

	/** One key shot: where the camera is and what it looks at, {@code tick} ticks into the scene. */
	private record Key(int tick, Vec3 position, Vec3 look) {
	}

	/** Where the camera is and which way it faces this frame. */
	public record Shot(Vec3 position, float yaw, float pitch) {
	}

	private static boolean active;
	private static long startTick;
	private static List<Key> path = List.of();
	@Nullable
	private static CameraType savedCameraType;

	private FairyCutsceneCamera() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(FairyCutsceneCamera::renderLight);
	}

	public static boolean isActive() {
		return active;
	}

	/** The scene starts: plan the shots from where the player stands and looks now. */
	public static void start() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		path = plan(eye, eye.add(player.getLookAngle().scale(4.0)));
		startTick = minecraft.level.getGameTime();
		if (!active) {
			savedCameraType = minecraft.options.getCameraType();
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		active = true;
	}

	/** The scene ends (or is cut short): the camera returns to the player, who now faces the fairy as the camera did. */
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
		if (player != null && !path.isEmpty()) {
			Key last = path.get(path.size() - 1);
			Shot facing = aim(last.position(), last.look());
			player.setYRot(facing.yaw());
			player.setXRot(facing.pitch());
			player.yRotO = facing.yaw();
			player.xRotO = facing.pitch();
			player.setYHeadRot(facing.yaw());
		}
	}

	/** This frame's shot, or null when no scene is playing (CameraMixin leaves the camera alone then). */
	@Nullable
	public static Shot current() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!active || minecraft.level == null || path.size() < 2) {
			return null;
		}
		double t = time(minecraft);
		Key last = path.get(path.size() - 1);
		if (t >= last.tick()) {
			return aim(last.position(), last.look());
		}
		int i = 0;
		while (i < path.size() - 2 && path.get(i + 1).tick() <= t) {
			i++;
		}
		Key a = path.get(i);
		Key b = path.get(i + 1);
		Key before = path.get(Math.max(0, i - 1));
		Key after = path.get(Math.min(path.size() - 1, i + 2));
		double u = Mth.clamp((t - a.tick()) / (double) (b.tick() - a.tick()), 0.0, 1.0);
		// ease each shot in and out a little, on top of the curve's own smoothness
		u = u * u * (3.0 - 2.0 * u) * 0.35 + u * 0.65;
		Vec3 position = catmullRom(before.position(), a.position(), b.position(), after.position(), u);
		Vec3 look = catmullRom(before.look(), a.look(), b.look(), after.look(), u);
		return aim(position, look);
	}

	private static double time(Minecraft minecraft) {
		return minecraft.level.getGameTime() - startTick + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	/** The shots, timed to FairyCutscene's timeline. */
	private static List<Key> plan(Vec3 eye, Vec3 gaze) {
		Vec3 altar = Vec3.atBottomCenterOf(FairyRealm.ALTAR).add(0.0, 1.0, 0.0);
		Vec3 portal = FairyCutscene.MADDIE_PORTAL;
		Vec3 door = portal.add(0.0, DOOR_HEIGHT / 2.0, 0.0);
		// where Maddie stops, having walked out of the door towards the altar
		Vec3 stand = portal.add(0.0, 0.0, FairyCutscene.WALK_DISTANCE - 0.7);
		Vec3 maddie = stand.add(0.0, 1.5, 0.0);
		Vec3 fairy = FairyRealm.FAIRY_SPAWN;
		List<Key> keys = new ArrayList<>();
		// the player's own view, rising into a crane shot of the glowing altar
		keys.add(new Key(0, eye, gaze));
		keys.add(new Key(26, altar.add(5.5, 4.5, 8.0), altar.add(0.0, 0.6, 0.0)));
		// round to face the door as it opens, and in as Maddie steps through it
		keys.add(new Key(FairyCutscene.PORTAL_OPENS + DOOR_OPENING, portal.add(2.4, 1.9, 7.0), door));
		keys.add(new Key(FairyCutscene.MADDIE_ARRIVES + 6, portal.add(1.3, 1.6, 4.4), door.add(0.0, 0.1, 0.0)));
		// backing away in front of her as she walks out, until she stops
		keys.add(new Key(FairyCutscene.MADDIE_ARRIVES + FairyCutscene.WALK, stand.add(1.6, 1.7, 3.8), maddie.add(0.0, -0.1, 0.0)));
		// slowly round her as she speaks, closer with every line, the door closing behind her
		keys.add(new Key(FairyCutscene.LINE_TWO - 20, stand.add(1.8, 1.65, 3.4), maddie));
		keys.add(new Key(FairyCutscene.LINE_THREE - 25, stand.add(-1.7, 1.6, 3.0), maddie));
		keys.add(new Key(FairyCutscene.LINE_THREE + 8, stand.add(-0.7, 1.7, 2.2), maddie.add(0.0, 0.05, 0.0)));
		// over her shoulder: the light gathers over the altar beyond her
		keys.add(new Key(FairyCutscene.GATHER - 2, stand.add(0.9, 2.1, -1.6), fairy.add(0.0, -1.0, 0.0)));
		// the fairy appears in a falling column of light
		keys.add(new Key(FairyCutscene.FAIRY_APPEARS - 4, altar.add(2.8, 2.5, 7.5), fairy.add(0.0, -0.5, 0.0)));
		keys.add(new Key(FairyCutscene.FAIRY_LINE_ONE - 4, altar.add(2.0, 3.6, 5.0), fairy));
		// a low hero shot as she speaks and raises her wand
		keys.add(new Key(FairyCutscene.SHOT - 8, altar.add(-2.6, 1.6, 3.4), fairy.add(0.0, 0.3, 0.0)));
		// across to Maddie as the bolt strikes, and down as she falls
		keys.add(new Key(FairyCutscene.SHOT + 6, altar.add(stand.subtract(altar).scale(0.5)).add(5.5, 3.0, 0.0), maddie.add(0.0, -0.3, 0.0)));
		keys.add(new Key(FairyCutscene.MADDIE_FALLS + 14, stand.add(2.5, 1.6, 2.6), stand.add(0.0, 0.4, 0.0)));
		// back to the fairy, closer as she speaks
		keys.add(new Key(FairyCutscene.FAIRY_LINE_TWO + 6, fairy.add(1.0, -1.5, 7.5), fairy));
		keys.add(new Key(FairyCutscene.FAIRY_LINE_THREE, fairy.add(0.5, -1.0, 5.0), fairy));
		keys.add(new Key(FairyCutscene.FAIRY_LINE_THREE + 32, fairy.add(-1.5, -0.8, 4.0), fairy));
		// and home to the player's eyes, facing her
		keys.add(new Key(FairyCutscene.FIGHT, eye, fairy));
		return keys;
	}

	/** A camera at {@code position} looking at {@code look}. */
	private static Shot aim(Vec3 position, Vec3 look) {
		Vec3 d = look.subtract(position);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG);
		return new Shot(position, yaw, pitch);
	}

	private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double u) {
		double u2 = u * u;
		double u3 = u2 * u;
		return new Vec3(spline(p0.x, p1.x, p2.x, p3.x, u, u2, u3), spline(p0.y, p1.y, p2.y, p3.y, u, u2, u3),
				spline(p0.z, p1.z, p2.z, p3.z, u, u2, u3));
	}

	private static double spline(double a, double b, double c, double d, double u, double u2, double u3) {
		return 0.5 * (2.0 * b + (c - a) * u + (2.0 * a - 5.0 * b + 4.0 * c - d) * u2 + (3.0 * b - a - 3.0 * c + d) * u3);
	}

	/** The scene's light: Maddie's door, and the column of light the Trans Fairy appears from. */
	private static void renderLight(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!active || minecraft.level == null) {
			return;
		}
		double t = time(minecraft);
		GlowGeometry g = new GlowGeometry(context.levelState().cameraRenderState.pos);
		renderDoor(g, t);
		renderColumn(g, t);
		g.submit(context);
	}

	/**
	 * Maddie's door: a tall doorway of light in the trans flag's stripes, the way the time doors open in a certain show.
	 * A thread of light splits the air from the middle up and down, the doorway swings open sideways from it, and later
	 * it narrows back to the thread and is gone. Its glowing frame is white at the core, then pink, then blue.
	 */
	private static void renderDoor(GlowGeometry g, double t) {
		double opens = FairyCutscene.PORTAL_OPENS;
		double closes = FairyCutscene.PORTAL_CLOSES;
		if (t < opens || t > closes + DOOR_OPENING) {
			return;
		}
		// 0 to 1 as it opens (the first third the thread growing, the rest the door widening), 1 to 0 as it closes
		double k = t < closes ? (t - opens) / DOOR_OPENING : 1.0 - (t - closes) / DOOR_OPENING;
		k = Mth.clamp(k, 0.0, 1.0);
		double tall = Math.min(1.0, k * 3.0);
		double wide = Mth.clamp((k - 0.33) / 0.67, 0.0, 1.0);
		wide = wide * wide * (3.0 - 2.0 * wide);
		Vec3 centre = FairyCutscene.MADDIE_PORTAL.add(0.0, DOOR_HEIGHT / 2.0, 0.0);
		double up = DOOR_HEIGHT / 2.0 * tall;
		double along = Math.max(0.02, DOOR_WIDTH / 2.0 * wide);
		double shimmer = 0.5 + 0.5 * Math.sin(t * 0.5);
		Direction facing = Direction.SOUTH;
		// the doorway: the flag's five stripes, side by side, shimmering
		int[] stripes = {BLUE, PINK, WHITE, PINK, BLUE};
		double band = along * 2.0 / stripes.length;
		for (int i = 0; i < stripes.length; i++) {
			Vec3 at = centre.add(-along + band * (i + 0.5), 0.0, 0.0);
			g.sheet(at, facing, band / 2.0, up, 0.02, GlowGeometry.argb((0.6 + 0.15 * shimmer) * wide, stripes[i]));
		}
		g.sheet(centre, facing, along, up, 0.04, GlowGeometry.argb(0.25 * wide, WHITE));
		// the frame: a white core with a pink and a blue glow round it
		double[][] glows = {{0.035, 0.95}, {0.09, 0.4}, {0.16, 0.18}};
		int[] glowColours = {WHITE, DEEP_PINK, BLUE};
		Vec3 left = centre.add(-along, 0.0, 0.0);
		Vec3 right = centre.add(along, 0.0, 0.0);
		for (int i = 0; i < glows.length; i++) {
			double half = glows[i][0] * (1.0 + 0.2 * shimmer);
			int colour = GlowGeometry.argb(glows[i][1], glowColours[i]);
			g.beam(left.add(0.0, -up, 0.0), left.add(0.0, up, 0.0), half, 0.0, colour);
			if (wide > 0.0) {
				g.beam(right.add(0.0, -up, 0.0), right.add(0.0, up, 0.0), half, 0.0, colour);
				g.beam(left.add(0.0, up, 0.0), right.add(0.0, up, 0.0), half, 0.0, colour);
				g.beam(left.add(0.0, -up, 0.0), right.add(0.0, -up, 0.0), half, 0.0, colour);
			}
		}
	}

	/** The column of light the Trans Fairy appears from: it falls from the sky onto the spot over the altar, then bursts. */
	private static void renderColumn(GlowGeometry g, double t) {
		double from = FairyCutscene.GATHER;
		double appear = FairyCutscene.FAIRY_APPEARS;
		if (t < from || t > appear + 40.0) {
			return;
		}
		Vec3 fairy = FairyRealm.FAIRY_SPAWN.add(0.0, 0.8, 0.0);
		Vec3 sky = fairy.add(0.0, 48.0, 0.0);
		double spin = t * 0.15;
		if (t < appear) {
			// it falls, widening as it comes
			double k = (t - from) / (appear - from);
			Vec3 end = sky.lerp(fairy, Math.min(1.0, k * 1.4));
			g.beam(sky, end, 0.08 + 0.12 * k, spin, GlowGeometry.argb(0.85, WHITE));
			g.beam(sky, end, 0.25 + 0.35 * k, -spin, GlowGeometry.argb(0.3, PINK));
			g.beam(sky, end, 0.5 + 0.5 * k, spin * 0.5, GlowGeometry.argb(0.12, BLUE));
			if (k > 0.7) {
				g.cube(fairy, 0.2 + (k - 0.7) * 2.0, GlowGeometry.argb(0.5, WHITE));
			}
		} else {
			// she's here: a flash, and the column thins away
			double k = (t - appear) / 40.0;
			double fade = 1.0 - k;
			g.beam(sky, fairy, 0.2 * fade, spin, GlowGeometry.argb(0.8 * fade, WHITE));
			g.beam(sky, fairy, 0.6 * fade, -spin, GlowGeometry.argb(0.25 * fade, PINK));
			double flash = Math.max(0.0, 1.0 - (t - appear) / 14.0);
			if (flash > 0.0) {
				g.cube(fairy, 0.5 + 3.5 * (1.0 - flash), GlowGeometry.argb(0.6 * flash, DEEP_PINK));
				g.cube(fairy, 0.4 + 2.0 * (1.0 - flash), GlowGeometry.argb(0.75 * flash, WHITE));
			}
		}
	}
}
