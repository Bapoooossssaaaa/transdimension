package dev.goober.transdimension.client.herobrine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.herobrine.HerobrineSightingPayload;
import dev.goober.transdimension.herobrine.HerobrineSoundPayload;

/**
 * Herobrine as this player sees and hears him (the server picks when and where: Herobrine). He is drawn straight into
 * the world from his skin ({@link SkinFigure}), never added to the game's entities, so radars, map mods, F3, hitboxes and
 * the player list never know he's there. He stands still, facing the player, and his head turns to follow them.
 *
 * <p>What counts is whether he's on the player's screen (inside their field of view with nothing solid in the way), not
 * where the crosshair is. He only turns up while he's off their screen, so he's never suddenly there where they're
 * looking. Once he's been on screen for the sighting's time he's gone; once they've seen him for a moment and look away,
 * he's gone before they look back; and he's gone if they come close or walk towards him. One standing right behind them
 * is gone a moment after they turn and catch sight of him. Otherwise he goes when his time is up (waiting, if they're
 * looking, until they look away). A cave sound can play where he stood. Leaving the world or the dimension ends a sighting.
 *
 * <p>The sounds ({@link HerobrineSoundPayload}) are played here too, for this player only: footsteps coming up behind them
 * on whatever the ground is made of, a creeper hissing right behind them, a door opening and closing, or someone mining in
 * the rock nearby.
 */
public final class HerobrineClient {
	private static final Identifier SKIN = TransDimension.id("textures/entity/herobrine/herobrine.png");
	private static final Identifier EYES = TransDimension.id("textures/entity/herobrine/herobrine_eyes.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	/** How far his head turns from his body to follow the player, and up or down. */
	private static final float HEAD_TURN = 70.0F;
	private static final float HEAD_TILT = 35.0F;
	/** Points down his middle (heights above his feet), checked against the player's screen; his shoulders too. */
	private static final double[] POINTS = {1.75, 1.35, 0.9, 0.35};
	private static final double SHOULDER = 0.3;
	/** A little past the edges of the screen still counts, so he never turns up right at its edge. */
	private static final double SCREEN_MARGIN = 1.05;
	/** One right behind the player is gone after this many ticks on screen. */
	private static final int GLIMPSE = 4;
	/** Seen this long they've noticed him; then this long looking away and he's gone. */
	private static final int NOTICED = 10;
	private static final int LOOKED_AWAY = 20;
	/** When his time is up he waits for them to look away, but never more than this much longer. */
	private static final int OVERSTAY = 200;
	/** Footsteps: a little louder than a real player's (0.15 of the block's volume), from this far behind, this far apart. */
	private static final float STEP_VOLUME = 0.2F;
	private static final double STEPS_FROM = 6.5;
	private static final double STRIDE = 0.9;

	@Nullable
	private static Sighting sighting;
	/** Sounds still to come, and the client's own tick count they're timed by. */
	private static final List<Planned> PLANNED = new ArrayList<>();
	private static int clock;

	private static final class Sighting {
		final ClientLevel level;
		final Vec3 feet;
		final float yaw;
		final HerobrineSightingPayload rules;
		/** He's there (drawn) once he's been off their screen. */
		boolean here;
		int age;
		/** Ticks he's been on their screen, ticks since he last was, and how far off they were the first time. */
		int seen;
		int unseen;
		double firstSeenFrom;

		Sighting(ClientLevel level, HerobrineSightingPayload rules) {
			this.level = level;
			this.feet = new Vec3(rules.x(), rules.y(), rules.z());
			this.yaw = rules.yaw();
			this.rules = rules;
		}

		Vec3 head() {
			return this.feet.add(0.0, 28.0 * SkinFigure.PIXEL, 0.0);
		}
	}

	/** A sound to play on a later tick (the client's tick count). */
	private record Planned(int at, Consumer<Minecraft> play) {
	}

	private HerobrineClient() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(HerobrineClient::render);
		ClientTickEvents.END_CLIENT_TICK.register(HerobrineClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			sighting = null;
			PLANNED.clear();
		});
	}

	/** The server says he's standing somewhere for this player. */
	public static void show(HerobrineSightingPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			sighting = new Sighting(minecraft.level, payload);
		}
	}

	private static void tick(Minecraft minecraft) {
		clock++;
		playDue(minecraft);
		Sighting s = sighting;
		if (s == null) {
			return;
		}
		LocalPlayer player = minecraft.player;
		if (minecraft.level != s.level || player == null) {
			sighting = null;
			return;
		}
		s.age++;
		boolean visible = onScreen(minecraft, player, s);
		if (!s.here) {
			// he only turns up where they aren't looking
			if (!visible) {
				s.here = true;
			} else if (s.age > s.rules.lingerTicks()) {
				sighting = null;
			}
			return;
		}
		double distance = player.getEyePosition().distanceTo(s.head());
		if (visible) {
			if (s.seen++ == 0) {
				s.firstSeenFrom = distance;
			}
			s.unseen = 0;
		} else if (s.seen > 0) {
			s.unseen++;
		}
		boolean timeUp = s.age > s.rules.lingerTicks() && (!visible || s.age > s.rules.lingerTicks() + OVERSTAY);
		if (s.rules.behind()) {
			// right behind them: gone once they've turned and caught sight of him
			if (s.seen >= GLIMPSE || timeUp) {
				vanish(minecraft, s);
			}
			return;
		}
		boolean closer = distance < s.rules.vanishDistance()
				|| s.seen > 0 && distance < s.firstSeenFrom - Math.max(4.0, s.firstSeenFrom * 0.05);
		boolean seenEnough = s.rules.seenTicks() > 0 && s.seen >= s.rules.seenTicks();
		boolean lookedAway = s.seen >= NOTICED && s.unseen >= LOOKED_AWAY;
		if (closer || seenEnough || lookedAway || timeUp) {
			vanish(minecraft, s);
		}
	}

	/**
	 * Whether any part of him is on the player's screen: inside their field of view (from their field of view setting and
	 * the window's shape) with nothing solid in the way.
	 */
	private static boolean onScreen(Minecraft minecraft, LocalPlayer player, Sighting s) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 right = look.cross(UP);
		right = right.lengthSqr() < 1.0E-6 ? rightOf(player.getYRot()) : right.normalize();
		Vec3 up = right.cross(look);
		double tall = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2.0) * SCREEN_MARGIN;
		double wide = tall * minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
		Vec3 shoulder = rightOf(s.yaw).scale(SHOULDER);
		for (int i = 0; i <= POINTS.length + 1; i++) {
			Vec3 point = i < POINTS.length ? s.feet.add(0.0, POINTS[i], 0.0)
					: s.feet.add(0.0, POINTS[1], 0.0).add(i == POINTS.length ? shoulder : shoulder.scale(-1.0));
			Vec3 to = point.subtract(eye);
			double ahead = to.dot(look);
			if (ahead > 0.05 && Math.abs(to.dot(right)) <= wide * ahead && Math.abs(to.dot(up)) <= tall * ahead
					&& s.level.clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType()
							== HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	private static void vanish(Minecraft minecraft, Sighting s) {
		sighting = null;
		if (s.seen > 0 && s.rules.sounds() && minecraft.level != null) {
			Vec3 at = s.head();
			minecraft.level.playLocalSound(at.x, at.y, at.z, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8F,
					0.8F + minecraft.level.getRandom().nextFloat() * 0.3F, false);
		}
	}

	/** The server says to play a sound near this player, with nothing there. */
	public static void prank(HerobrineSoundPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		// somewhere behind them, a little to one side or the other
		Vec3 back = facing(player.getYRot() + 180.0F + (random.nextFloat() - 0.5F) * 60.0F, 0.0F);
		switch (payload.kind()) {
			case HerobrineSoundPayload.CREEPER -> {
				Vec3 at = player.position().add(back.scale(1.5)).add(0.0, 0.8, 0.0);
				level.playLocalSound(at.x, at.y, at.z, SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 0.5F, false);
			}
			case HerobrineSoundPayload.DOOR -> {
				Vec3 at = player.position().add(back.scale(6.0 + random.nextDouble() * 4.0)).add(0.0, 1.0, 0.0);
				float pitch = 0.9F + random.nextFloat() * 0.1F;
				plan(2, game -> sound(game, at, SoundEvents.WOODEN_DOOR_OPEN, 1.0F, pitch));
				plan(32 + random.nextInt(20), game -> sound(game, at, SoundEvents.WOODEN_DOOR_CLOSE, 1.0F, pitch));
			}
			case HerobrineSoundPayload.MINING -> {
				if (!mining(level, player, random)) {
					footsteps(random, back);
				}
			}
			default -> footsteps(random, back);
		}
	}

	/** Footsteps coming up from behind, each on whatever the ground is made of where it falls. */
	private static void footsteps(RandomSource random, Vec3 back) {
		int steps = 4 + random.nextInt(3);
		int when = 4;
		for (int i = 0; i < steps; i++) {
			double distance = STEPS_FROM - i * STRIDE;
			plan(when, minecraft -> step(minecraft, back, distance));
			when += 7 + random.nextInt(3);
		}
	}

	/** One footstep this far along {@code way} from wherever the player is now, on the ground about level with them. */
	private static void step(Minecraft minecraft, Vec3 way, double distance) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return;
		}
		Vec3 at = player.position().add(way.scale(distance));
		BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos();
		for (int dy = 0; dy >= -4; dy--) {
			ground.set(Mth.floor(at.x), Mth.floor(at.y) + dy, Mth.floor(at.z));
			BlockPos feet = ground.above();
			if (!level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
					&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
				// snow, carpet-thin things and the like make their own sound on top of the block
				BlockState inside = level.getBlockState(feet);
				SoundType type = (inside.is(BlockTags.INSIDE_STEP_SOUND_BLOCKS) ? inside : level.getBlockState(ground)).getSoundType();
				level.playLocalSound(at.x, feet.getY(), at.z, type.getStepSound(), SoundSource.PLAYERS, type.getVolume() * STEP_VOLUME,
						type.getPitch(), false);
				return;
			}
		}
	}

	/**
	 * Someone mining a few blocks of whatever rock is around, 8 to 14 blocks off through the walls (as a pickaxe sounds:
	 * a hit every 4 ticks, then the block breaking). False if there's no rock about.
	 */
	private static boolean mining(ClientLevel level, LocalPlayer player, RandomSource random) {
		BlockPos rock = null;
		for (int tries = 0; tries < 24 && rock == null; tries++) {
			Vec3 way = facing(random.nextFloat() * 360.0F, (random.nextFloat() - 0.5F) * 60.0F);
			BlockPos pos = BlockPos.containing(player.getEyePosition().add(way.scale(8.0 + random.nextDouble() * 6.0)));
			if (level.getBlockState(pos).is(BlockTags.MINEABLE_WITH_PICKAXE)) {
				rock = pos;
			}
		}
		if (rock == null) {
			return false;
		}
		SoundType type = level.getBlockState(rock).getSoundType();
		Vec3 at = Vec3.atCenterOf(rock);
		int when = 2;
		for (int block = 2 + random.nextInt(2); block > 0; block--) {
			for (int hit = 3 + random.nextInt(3); hit > 0; hit--) {
				plan(when, minecraft -> sound(minecraft, at, type.getHitSound(), 0.5F, type.getPitch() * 0.5F));
				when += 4;
			}
			plan(when, minecraft -> sound(minecraft, at, type.getBreakSound(), 1.0F, type.getPitch() * 0.8F));
			when += 14 + random.nextInt(16);
		}
		return true;
	}

	private static void plan(int ticks, Consumer<Minecraft> play) {
		PLANNED.add(new Planned(clock + ticks, play));
	}

	private static void playDue(Minecraft minecraft) {
		Iterator<Planned> planned = PLANNED.iterator();
		while (planned.hasNext()) {
			Planned next = planned.next();
			if (next.at() <= clock) {
				planned.remove();
				next.play().accept(minecraft);
			}
		}
	}

	private static void sound(Minecraft minecraft, Vec3 at, SoundEvent event, float volume, float pitch) {
		if (minecraft.level != null) {
			minecraft.level.playLocalSound(at.x, at.y, at.z, event, SoundSource.BLOCKS, volume, pitch, false);
		}
	}

	private static void render(LevelRenderContext context) {
		Sighting s = sighting;
		Minecraft minecraft = Minecraft.getInstance();
		if (s == null || !s.here || minecraft.level != s.level) {
			return;
		}
		BlockPos at = BlockPos.containing(s.head());
		if (!minecraft.level.isLoaded(at)) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		// His body faces the player as he appeared; his head follows them, as far as a neck turns.
		Vec3 front = facing(s.yaw, 0.0F);
		Vec3 right = rightOf(s.yaw);
		Vec3 neck = s.feet.add(0.0, 24.0 * SkinFigure.PIXEL, 0.0);
		Vec3 toCamera = camera.subtract(neck.add(0.0, 4.0 * SkinFigure.PIXEL, 0.0));
		float towards = (float) (Mth.atan2(toCamera.z, toCamera.x) * Mth.RAD_TO_DEG) - 90.0F;
		float headYaw = s.yaw + Mth.clamp(Mth.wrapDegrees(towards - s.yaw), -HEAD_TURN, HEAD_TURN);
		float headPitch = Mth.clamp((float) -(Mth.atan2(toCamera.y, Math.sqrt(toCamera.x * toCamera.x + toCamera.z * toCamera.z))
				* Mth.RAD_TO_DEG), -HEAD_TILT, HEAD_TILT);
		Vec3 headFront = facing(headYaw, headPitch);
		Vec3 headRight = rightOf(headYaw);
		Vec3 headUp = headRight.cross(headFront);
		Vec3 headCentre = neck.add(headUp.scale(4.0 * SkinFigure.PIXEL));

		SkinFigure figure = new SkinFigure(camera);
		// legs, body and arms (classic arms, four pixels wide), each with its outer layer
		Vec3 rightLeg = s.feet.add(right.scale(1.9 * SkinFigure.PIXEL)).add(0.0, 6.0 * SkinFigure.PIXEL, 0.0);
		Vec3 leftLeg = s.feet.add(right.scale(-1.9 * SkinFigure.PIXEL)).add(0.0, 6.0 * SkinFigure.PIXEL, 0.0);
		Vec3 body = s.feet.add(0.0, 18.0 * SkinFigure.PIXEL, 0.0);
		Vec3 rightArm = body.add(right.scale(6.0 * SkinFigure.PIXEL));
		Vec3 leftArm = body.add(right.scale(-6.0 * SkinFigure.PIXEL));
		figure.box(rightLeg, right, UP, front, 0, 16, 4, 12, 4, 0.0);
		figure.box(rightLeg, right, UP, front, 0, 32, 4, 12, 4, 0.25);
		figure.box(leftLeg, right, UP, front, 16, 48, 4, 12, 4, 0.0);
		figure.box(leftLeg, right, UP, front, 0, 48, 4, 12, 4, 0.25);
		figure.box(body, right, UP, front, 16, 16, 8, 12, 4, 0.0);
		figure.box(body, right, UP, front, 16, 32, 8, 12, 4, 0.25);
		figure.box(rightArm, right, UP, front, 40, 16, 4, 12, 4, 0.0);
		figure.box(rightArm, right, UP, front, 40, 32, 4, 12, 4, 0.25);
		figure.box(leftArm, right, UP, front, 32, 48, 4, 12, 4, 0.0);
		figure.box(leftArm, right, UP, front, 48, 48, 4, 12, 4, 0.25);
		figure.box(headCentre, headRight, headUp, headFront, 0, 0, 8, 8, 8, 0.0);
		figure.box(headCentre, headRight, headUp, headFront, 32, 0, 8, 8, 8, 0.5);
		int light = minecraft.level.getBrightness(LightLayer.BLOCK, at) << 4 | minecraft.level.getBrightness(LightLayer.SKY, at) << 20;
		figure.submit(context, SKIN, light);

		if (s.rules.eyes()) {
			// his white eyes glow, whatever the light
			SkinFigure eyes = new SkinFigure(camera);
			eyes.box(headCentre, headRight, headUp, headFront, 0, 0, 8, 8, 8, 0.05);
			eyes.submit(context, EYES, FULL_BRIGHT);
		}
	}

	/** The way something with this yaw and pitch (in degrees, as the game counts them) looks. */
	private static Vec3 facing(float yaw, float pitch) {
		float y = yaw * Mth.DEG_TO_RAD;
		float p = pitch * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
	}

	/** Its right hand, level. */
	private static Vec3 rightOf(float yaw) {
		float y = yaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(y), 0.0, -Mth.sin(y));
	}
}
