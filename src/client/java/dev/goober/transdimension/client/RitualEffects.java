package dev.goober.transdimension.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.goober.transdimension.block.RitualCrystalBlock;
import dev.goober.transdimension.network.RitualPayload;
import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.world.SculkRitual;

/**
 * The candle ritual's light show (SculkRitual), drawn by each watching client from the one {@link RitualPayload} the
 * server sends when the circle is complete, on the timeline in SculkRitual's constants:
 * <ul>
 * <li>one after another round the circle, a beam of pink, blue or white light rises from each candle into the crystal,
 * <li>the crystal wakes in a flash and a great white and pink beam shoots from it into the middle of the gate,
 * <li>a sheet of light spreads out from the middle of the gate until it fills the frame, just ahead of the portal blocks
 * the server places ring by ring, then fades as the portal takes over.
 * </ul>
 * Once the gate is open, every awake crystal near the player keeps a steady, gently pulsing beam on its gate (the crystal
 * block reports itself from its display ticks: {@link RitualCrystalBlock#onAwakeCrystalShown}).
 *
 * <p>Everything is drawn as glowing, unlit boxes ({@link GlowGeometry}), so the light shows up bright in the darkest city.
 */
public final class RitualEffects {
	private static final int PINK = 0xF5A9B8;
	private static final int DEEP_PINK = 0xFF7EB3;
	private static final int BLUE = 0x5BCEFA;
	private static final int WHITE = 0xFFFFFF;
	private static final int[] CANDLE_COLOURS = {PINK, BLUE, WHITE};
	private static final double RANGE = 160.0;

	private static final List<Show> SHOWS = new ArrayList<>();
	private static final Set<BlockPos> AWAKE = new HashSet<>();
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
				AWAKE.add(payload.crystal());
			}
		});
		RitualCrystalBlock.onAwakeCrystalShown = pos -> AWAKE.add(pos.immutable());
		LevelRenderEvents.COLLECT_SUBMITS.register(RitualEffects::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			SHOWS.clear();
			AWAKE.clear();
			lastLevel = level;
		}
		if (level == null || (SHOWS.isEmpty() && AWAKE.isEmpty())) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		double now = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		GlowGeometry geometry = new GlowGeometry(camera);

		Set<BlockPos> showing = new HashSet<>();
		Iterator<Show> shows = SHOWS.iterator();
		while (shows.hasNext()) {
			Show show = shows.next();
			double age = now - show.start;
			if (age > show.length()) {
				shows.remove();
				continue;
			}
			showing.add(show.ritual.crystal());
			if (show.ritual.origin().distanceToSqr(camera) < RANGE * RANGE) {
				drawShow(geometry, show.ritual, age, show.length(), now);
			}
		}
		Iterator<BlockPos> crystals = AWAKE.iterator();
		while (crystals.hasNext()) {
			BlockPos pos = crystals.next();
			if (showing.contains(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!state.is(ModBlocks.RITUAL_CRYSTAL) || !state.getValue(RitualCrystalBlock.AWAKE)) {
				crystals.remove();
				continue;
			}
			if (Vec3.atCenterOf(pos).distanceToSqr(camera) < RANGE * RANGE) {
				drawSteadyBeam(geometry, pos, state.getValue(RitualCrystalBlock.FACING), now);
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
			g.beam(tip, end, 0.03, spin, argb(0.9 * fade, WHITE));
			g.beam(tip, end, 0.085, -spin, argb(0.4 * fade, colour));
			if (grow < 1.0) {
				g.cube(end, 0.09, argb(0.9, WHITE));
				g.cube(end, 0.18, argb(0.35, colour));
			}
			// the flame flares as its beam leaves it
			double flare = 1.0 - Math.min(1.0, grow);
			if (flare > 0.0) {
				g.cube(tip, 0.1 + 0.25 * (1.0 - flare), argb(0.5 * flare, colour));
			}
		}

		// 2. The crystal: it gathers the light (a glow that swells as the candles feed it), then wakes in a flash.
		double fed = Mth.clamp(age / SculkRitual.AWAKEN, 0.0, 1.0);
		double pulse = 0.5 + 0.5 * Math.sin(now * 0.4);
		g.cube(origin, 0.3 + 0.25 * fed + 0.04 * pulse, argb((0.15 + 0.25 * fed) * fade, PINK));
		double sinceWake = age - SculkRitual.AWAKEN;
		if (sinceWake >= 0.0 && sinceWake < 16.0) {
			double k = sinceWake / 16.0;
			g.cube(origin, 0.3 + 2.6 * k, argb(0.55 * (1.0 - k), DEEP_PINK));
			g.cube(origin, 0.2 + 1.4 * k, argb(0.7 * (1.0 - k), WHITE));
		}

		// 3. The great beam into the middle of the gate.
		double fire = (age - SculkRitual.FIRE) / SculkRitual.FIRE_GROW;
		if (fire > 0.0) {
			Vec3 end = origin.lerp(ritual.centre(), Math.min(1.0, fire));
			double throb = 1.0 + 0.15 * Math.sin(now * 0.6);
			g.beam(origin, end, 0.07 * throb, spin, argb(0.95 * fade, WHITE));
			g.beam(origin, end, 0.2 * throb, -spin, argb(0.35 * fade, DEEP_PINK));
			g.beam(origin, end, 0.34 * throb, spin * 0.5, argb(0.12 * fade, PINK));
			if (fire < 1.0) {
				g.cube(end, 0.25, argb(0.9, WHITE));
			}
		}

		// 4. The sheet of light, spreading from the middle of the gate a ring at a time until it meets the frame.
		double open = age - SculkRitual.OPEN;
		if (open > 0.0) {
			double full = ritual.rings() * SculkRitual.RING_TICKS;
			double reach = 1.0 + open / SculkRitual.RING_TICKS;
			double along = Math.min(reach, ritual.halfAlong());
			double up = Math.min(reach, ritual.halfUp());
			double sheet = open < full ? 1.0 : Mth.clamp(1.0 - (open - full) / SculkRitual.FADE, 0.0, 1.0);
			// a flash as it touches the frame all round
			double flash = open >= full ? Math.max(0.0, 1.0 - (open - full) / 8.0) : 0.0;
			Direction facing = ritual.facing();
			Vec3 centre = ritual.centre();
			g.sheet(centre, facing, along, up, 0.05, argb(Math.min(1.0, 0.55 * sheet + 0.4 * flash), WHITE));
			g.sheet(centre, facing, along + 0.12, up + 0.12, 0.14, argb(0.3 * sheet + 0.3 * flash, PINK));
			// its leading edge shines brighter while it grows
			if (open < full) {
				g.sheet(centre, facing, along, up, 0.2, argb(0.18, DEEP_PINK));
			}
		}
	}

	/** An awake crystal's steady beam on its gate (the gate's middle is where SculkRitual says it is). */
	private static void drawSteadyBeam(GlowGeometry g, BlockPos crystal, Direction facing, double now) {
		Vec3 origin = SculkRitual.beamOrigin(crystal, facing);
		Vec3 target = SculkRitual.aim(crystal, facing);
		double pulse = 0.5 + 0.5 * Math.sin(now * 0.15 + crystal.hashCode());
		double spin = now * 0.08;
		g.beam(origin, target, 0.035 + 0.01 * pulse, spin, argb(0.6 + 0.25 * pulse, WHITE));
		g.beam(origin, target, 0.12 + 0.03 * pulse, -spin, argb(0.22 + 0.1 * pulse, DEEP_PINK));
		g.cube(origin, 0.42 + 0.05 * pulse, argb(0.18 + 0.1 * pulse, PINK));
	}

	private static int argb(double alpha, int rgb) {
		return GlowGeometry.argb(alpha, rgb);
	}
}
