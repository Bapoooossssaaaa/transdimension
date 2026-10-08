package dev.goober.transdimension.client.herobrine;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.herobrine.HerobrineSightingPayload;

/**
 * Herobrine as this player sees him (the server picks when and where: Herobrine). He is drawn straight into the world
 * from his skin ({@link SkinFigure}), never added to the game's entities, so radars, map mods, F3, hitboxes and the
 * player list never know he's there. He stands still, facing the player, and his head turns to follow them; his eyes
 * glow.
 *
 * <p>He's gone the moment the player comes within the sighting's distance, or (if the host chose it) after they look
 * straight at him for a moment; one standing right behind the player is gone just after they turn and catch sight of him.
 * Otherwise he goes when his time is up. A cave sound can play where he stood. Leaving the world or the dimension ends a
 * sighting too.
 */
public final class HerobrineClient {
	private static final Identifier SKIN = TransDimension.id("textures/entity/herobrine/herobrine.png");
	private static final Identifier EYES = TransDimension.id("textures/entity/herobrine/herobrine_eyes.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	/** How far his head turns from his body to follow the player, and up or down. */
	private static final float HEAD_TURN = 70.0F;
	private static final float HEAD_TILT = 35.0F;
	/** Looking within about 7 degrees of him counts as staring; this many ticks of it and he's gone. */
	private static final double STARE_CONE = 0.9925;
	private static final int STARE_TICKS = 30;
	/** Turning to within about 45 degrees of him (behind) counts as seeing him; he's gone this many ticks later. */
	private static final double SEEN_CONE = 0.7;
	private static final int SEEN_TICKS = 6;

	@Nullable
	private static Sighting sighting;

	private static final class Sighting {
		final ClientLevel level;
		final Vec3 feet;
		final float yaw;
		final HerobrineSightingPayload rules;
		int age;
		int stared;
		int seen;

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

	private HerobrineClient() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(HerobrineClient::render);
		ClientTickEvents.END_CLIENT_TICK.register(HerobrineClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> sighting = null);
	}

	/** The server says he's standing somewhere for this player. */
	public static void show(HerobrineSightingPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			sighting = new Sighting(minecraft.level, payload);
		}
	}

	private static void tick(Minecraft minecraft) {
		Sighting s = sighting;
		if (s == null) {
			return;
		}
		if (minecraft.level != s.level || minecraft.player == null) {
			sighting = null;
			return;
		}
		s.age++;
		if (s.age > s.rules.lingerTicks()) {
			vanish(minecraft, s, false);
			return;
		}
		Vec3 eye = minecraft.player.getEyePosition();
		Vec3 toHim = s.head().subtract(eye);
		double facing = toHim.normalize().dot(minecraft.player.getViewVector(1.0F));
		if (s.rules.behind()) {
			// right behind: once they've turned and caught sight of him, he's gone
			if (facing > SEEN_CONE && ++s.seen >= SEEN_TICKS) {
				vanish(minecraft, s, true);
			}
			return;
		}
		if (toHim.length() < s.rules.vanishDistance()) {
			vanish(minecraft, s, true);
		} else if (s.rules.stare()) {
			s.stared = facing > STARE_CONE ? s.stared + 1 : Math.max(0, s.stared - 1);
			if (s.stared >= STARE_TICKS) {
				vanish(minecraft, s, true);
			}
		}
	}

	private static void vanish(Minecraft minecraft, Sighting s, boolean seen) {
		sighting = null;
		if (seen && s.rules.sounds() && minecraft.level != null) {
			Vec3 at = s.head();
			minecraft.level.playLocalSound(at.x, at.y, at.z, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.8F,
					0.8F + minecraft.level.getRandom().nextFloat() * 0.3F, false);
		}
	}

	private static void render(LevelRenderContext context) {
		Sighting s = sighting;
		Minecraft minecraft = Minecraft.getInstance();
		if (s == null || minecraft.level != s.level) {
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
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
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
		figure.box(rightLeg, right, up, front, 0, 16, 4, 12, 4, 0.0);
		figure.box(rightLeg, right, up, front, 0, 32, 4, 12, 4, 0.25);
		figure.box(leftLeg, right, up, front, 16, 48, 4, 12, 4, 0.0);
		figure.box(leftLeg, right, up, front, 0, 48, 4, 12, 4, 0.25);
		figure.box(body, right, up, front, 16, 16, 8, 12, 4, 0.0);
		figure.box(body, right, up, front, 16, 32, 8, 12, 4, 0.25);
		figure.box(rightArm, right, up, front, 40, 16, 4, 12, 4, 0.0);
		figure.box(rightArm, right, up, front, 40, 32, 4, 12, 4, 0.25);
		figure.box(leftArm, right, up, front, 32, 48, 4, 12, 4, 0.0);
		figure.box(leftArm, right, up, front, 48, 48, 4, 12, 4, 0.25);
		figure.box(headCentre, headRight, headUp, headFront, 0, 0, 8, 8, 8, 0.0);
		figure.box(headCentre, headRight, headUp, headFront, 32, 0, 8, 8, 8, 0.5);
		int light = minecraft.level.getBrightness(LightLayer.BLOCK, at) << 4 | minecraft.level.getBrightness(LightLayer.SKY, at) << 20;
		figure.submit(context, SKIN, light);

		// his white eyes glow, whatever the light
		SkinFigure eyes = new SkinFigure(camera);
		eyes.box(headCentre, headRight, headUp, headFront, 0, 0, 8, 8, 8, 0.05);
		eyes.submit(context, EYES, FULL_BRIGHT);
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
