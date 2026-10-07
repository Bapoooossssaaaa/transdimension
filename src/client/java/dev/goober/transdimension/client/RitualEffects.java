package dev.goober.transdimension.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.goober.transdimension.network.RitualPayload;
import dev.goober.transdimension.registry.ModParticles;
import dev.goober.transdimension.world.SculkRitual;

/**
 * The candle ritual's light show (SculkRitual), drawn by each watching client from the one {@link RitualPayload} the
 * server sends when the circle is complete, on the timeline in SculkRitual's constants:
 * <ul>
 * <li>one after another round the circle, a beam of pink, blue or white light rises from each candle into the crystal,
 * <li>the crystal, glowing every colour in turn like a prism, wakes in a flash and a great white and pink beam shoots
 * from it into the middle of the gate,
 * <li>while the server builds the portal ring by ring from the middle of the gate out to the frame (the way it formed
 * in the first version: a couple of sparks per block, no sheet of light over it), and the beam fades soon after.
 * </ul>
 * Nothing stays lit afterwards: the crystal's beam fires during the ritual only. The light is drawn with
 * {@link GlowGeometry} (soft, not full bright, under a shader pack), and a few of our own particles ({@link #sparkle})
 * follow it.
 */
public final class RitualEffects {
	private static final int PINK = 0xF5A9B8;
	private static final int BLUE = 0x5BCEFA;
	private static final int WHITE = 0xFFFFFF;
	private static final int[] CANDLE_COLOURS = {PINK, BLUE, WHITE};
	private static final double RANGE = 160.0;

	private static final List<Show> SHOWS = new ArrayList<>();
	private static ClientLevel lastLevel;

	private RitualEffects() {
	}

	/** One ritual's light show, started at {@code start} (the client level's game time when its packet came in). */
	private record Show(RitualPayload ritual, long start) {
		int length() {
			return SculkRitual.OPEN + this.ritual.rings() * SculkRitual.RING_TICKS + SculkRitual.LINGER;
		}
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(RitualPayload.TYPE, (payload, context) -> {
			if (context.client().level != null) {
				SHOWS.add(new Show(payload, context.client().level.getGameTime()));
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(RitualEffects::render);
		ClientTickEvents.END_CLIENT_TICK.register(RitualEffects::sparkle);
	}

	/**
	 * The light show's sparkles, a few of our own particles following the light: drifting up each candle's beam into the
	 * crystal, a small burst as the crystal wakes, and the odd one along the great beam.
	 */
	private static void sparkle(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || SHOWS.isEmpty() || minecraft.isPaused()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Show show : SHOWS) {
			double age = level.getGameTime() - show.start;
			RitualPayload ritual = show.ritual;
			Vec3 origin = ritual.origin();
			List<Vec3> candles = ritual.candles();
			for (int i = 0; i < candles.size(); i++) {
				double grow = (age - i * SculkRitual.CANDLE_INTERVAL) / SculkRitual.CANDLE_GROW;
				if (grow <= 0.0 || age > SculkRitual.FIRE) {
					continue;
				}
				if (random.nextInt(3) != 0) {
					continue;
				}
				Vec3 tip = candles.get(i);
				Vec3 head = tip.lerp(origin, Math.min(1.0, grow));
				Vec3 at = tip.lerp(head, random.nextDouble());
				Vec3 flow = origin.subtract(tip).normalize().scale(0.06);
				level.addParticle(ModParticles.PRISM_SPARK, at.x, at.y, at.z, flow.x, flow.y, flow.z);
			}
			double sinceWake = age - SculkRitual.AWAKEN;
			if (sinceWake >= 0.0 && sinceWake < 2.0) {
				for (int n = 0; n < 6; n++) {
					level.addParticle(ModParticles.PRISM_SPARK, origin.x, origin.y, origin.z, random.nextGaussian() * 0.2, random.nextGaussian() * 0.2,
							random.nextGaussian() * 0.2);
				}
			}
			double fire = (age - SculkRitual.FIRE) / SculkRitual.FIRE_GROW;
			if (fire > 0.0 && age < show.length() - SculkRitual.FADE) {
				Vec3 end = origin.lerp(ritual.centre(), Math.min(1.0, fire));
				Vec3 flow = ritual.centre().subtract(origin).normalize().scale(0.25);
				if (random.nextInt(3) == 0) {
					Vec3 at = origin.lerp(end, random.nextDouble());
					level.addParticle(ModParticles.PRISM_SPARK, at.x, at.y, at.z, flow.x, flow.y, flow.z);
				}
			}
		}
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			SHOWS.clear();
			lastLevel = level;
		}
		if (level == null || SHOWS.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		double now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		GlowGeometry geometry = new GlowGeometry(camera);

		Iterator<Show> shows = SHOWS.iterator();
		while (shows.hasNext()) {
			Show show = shows.next();
			double age = now - show.start;
			if (age > show.length()) {
				shows.remove();
				continue;
			}
			if (show.ritual.origin().distanceToSqr(camera) < RANGE * RANGE) {
				drawShow(geometry, show.ritual, age, show.length(), now);
			}
		}
		geometry.submit(context);
	}

	private static void drawShow(GlowGeometry g, RitualPayload ritual, double age, int length, double now) {
		double fade = Mth.clamp((length - age) / SculkRitual.FADE, 0.0, 1.0);
		Vec3 origin = ritual.origin();
		double spin = now * 0.12;

		// 1. A beam from each candle up into the crystal, one after another round the circle, each with a bright head as it rises.
		List<Vec3> candles = ritual.candles();
		for (int i = 0; i < candles.size(); i++) {
			double grow = (age - i * SculkRitual.CANDLE_INTERVAL) / SculkRitual.CANDLE_GROW;
			if (grow <= 0.0) {
				continue;
			}
			Vec3 tip = candles.get(i);
			Vec3 end = tip.lerp(origin, Math.min(1.0, grow));
			int colour = CANDLE_COLOURS[i % CANDLE_COLOURS.length];
			g.beam(tip, end, 0.025, spin, argb(0.6 * fade, WHITE));
			g.beam(tip, end, 0.07, -spin, argb(0.25 * fade, colour));
			if (grow < 1.0) {
				g.cube(end, 0.07, argb(0.6, WHITE));
			}
		}

		// 2. The crystal: it gathers the light (a prismatic glow that swells as the candles feed it), then wakes in a flash.
		double fed = Mth.clamp(age / SculkRitual.AWAKEN, 0.0, 1.0);
		double pulse = 0.5 + 0.5 * Math.sin(now * 0.4);
		g.cube(origin, 0.25 + 0.15 * fed + 0.03 * pulse, argb((0.08 + 0.15 * fed) * fade, prism(now * 0.015)));
		double sinceWake = age - SculkRitual.AWAKEN;
		if (sinceWake >= 0.0 && sinceWake < 10.0) {
			double k = sinceWake / 10.0;
			g.cube(origin, 0.25 + 0.9 * k, argb(0.4 * (1.0 - k), WHITE));
		}

		// 3. The great beam into the middle of the gate.
		double fire = (age - SculkRitual.FIRE) / SculkRitual.FIRE_GROW;
		if (fire > 0.0) {
			Vec3 end = origin.lerp(ritual.centre(), Math.min(1.0, fire));
			double throb = 1.0 + 0.1 * Math.sin(now * 0.6);
			g.beam(origin, end, 0.05 * throb, spin, argb(0.7 * fade, WHITE));
			g.beam(origin, end, 0.15 * throb, -spin, argb(0.22 * fade, PINK));
		}
	}

	/** A soft rainbow colour, {@code phase} of the way round the colour wheel (it wraps every 1). */
	private static int prism(double phase) {
		return Mth.hsvToRgb((float) (phase - Math.floor(phase)), 0.45F, 1.0F);
	}

	private static int argb(double alpha, int rgb) {
		return GlowGeometry.argb(alpha, rgb);
	}
}
