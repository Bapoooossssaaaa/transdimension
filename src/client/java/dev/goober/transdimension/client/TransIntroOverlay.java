package dev.goober.transdimension.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The "you just arrived" animation: the five stripes of the trans flag sweep across the screen,
 * a heart pulses above a welcome title, little hearts float up, a bell jingle plays,
 * then the stripes slide away to reveal the realm.
 */
public final class TransIntroOverlay {
	private static final float DURATION = 130.0F; // ticks
	private static final int[] FLAG = {0x5BCEFA, 0xF5A9B8, 0xFFFFFF, 0xF5A9B8, 0x5BCEFA};
	private static final String[] HEART = {
			".##...##.",
			"####.####",
			"#########",
			"#########",
			".#######.",
			"..#####..",
			"...###...",
			"....#....",
	};

	private static long startNanos = -1L;
	private static int ticks = -1;

	private TransIntroOverlay() {
	}

	public static void start() {
		startNanos = System.nanoTime();
		ticks = 0;
	}

	public static void tick(Minecraft client) {
		if (ticks < 0) {
			return;
		}
		ticks++;

		// A little rising bell arpeggio, then a sparkly chime.
		float[] pitches = {0.794F, 1.0F, 1.189F, 1.587F};
		int[] when = {3, 7, 11, 17};
		for (int i = 0; i < when.length; i++) {
			if (ticks == when[i]) {
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, pitches[i]));
			}
		}
		if (ticks == 24) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F));
		}

		if (ticks > DURATION + 5) {
			ticks = -1;
			startNanos = -1L;
		}
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (startNanos < 0L) {
			return;
		}
		float t = (System.nanoTime() - startNanos) / 50_000_000.0F; // elapsed time in ticks, smooth between ticks
		if (t > DURATION) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		// 1) Flag stripes slide in from alternating sides, then slide out.
		for (int i = 0; i < 5; i++) {
			int y0 = height * i / 5;
			int y1 = height * (i + 1) / 5;
			float in = ease(clamp01((t - i * 2.5F) / 14.0F));
			float out = ease(clamp01((t - (DURATION - 28.0F) - i * 2.0F) / 14.0F));
			boolean fromLeft = i % 2 == 0;
			int x0;
			int x1;
			if (out > 0.0F) {
				x0 = fromLeft ? Math.round(width * out) : 0;
				x1 = fromLeft ? width : Math.round(width * (1.0F - out));
			} else {
				x0 = fromLeft ? 0 : Math.round(width * (1.0F - in));
				x1 = fromLeft ? Math.round(width * in) : width;
			}
			if (x1 > x0) {
				graphics.fill(x0, y0, x1, y1, 0xF0000000 | FLAG[i]);
			}
		}

		// 2) Hearts, title and subtitle fade in once the flag is up.
		float contentAlpha = clamp01((t - 18.0F) / 10.0F) * (1.0F - clamp01((t - (DURATION - 34.0F)) / 8.0F));
		if (contentAlpha <= 0.02F) {
			return;
		}
		int alpha = Math.max(8, Math.round(contentAlpha * 255.0F));

		floatingHearts(graphics, t, width, height, alpha);

		float pulse = 1.0F + 0.08F * Mth.sin(t * 0.45F);
		int pixel = Math.max(2, height / 42);
		drawHeart(graphics, width / 2.0F, height * 0.3F, pixel * pulse, (alpha << 24) | 0xC9587A, 1.15F);
		drawHeart(graphics, width / 2.0F, height * 0.3F, pixel * pulse, (alpha << 24) | 0xFFFFFF, 1.0F);

		Font font = Minecraft.getInstance().font;
		drawScaledText(graphics, font, "Welcome to the Trans Realm", width / 2.0F, height * 0.5F - 9.0F, 2.5F, (alpha << 24) | 0x2B3A67);
		drawScaledText(graphics, font, "You are valid. You are loved. \u2764", width / 2.0F, height * 0.66F, 1.5F, (alpha << 24) | 0x6B2142);
		drawScaledText(graphics, font, "Say \"Goober\" again to go home", width / 2.0F, height * 0.88F, 1.0F, (alpha << 24) | 0x1E2A4A);
	}

	private static void drawScaledText(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float scale, int argb) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale);
		graphics.centeredText(font, text, 0, 0, argb);
		graphics.pose().popMatrix();
	}

	/** Draws the pixel heart centred on (cx, cy). The size multiplier makes a slightly bigger outline pass. */
	private static void drawHeart(GuiGraphicsExtractor graphics, float cx, float cy, float pixel, int argb, float size) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(pixel * size);
		graphics.pose().translate(-4.5F, -4.0F);
		for (int row = 0; row < HEART.length; row++) {
			String line = HEART[row];
			for (int col = 0; col < line.length(); col++) {
				if (line.charAt(col) == '#') {
					graphics.fill(col, row, col + 1, row + 1, argb);
				}
			}
		}
		graphics.pose().popMatrix();
	}

	private static void floatingHearts(GuiGraphicsExtractor graphics, float t, int width, int height, int alpha) {
		int[] colours = {0xFFFFFF, 0x5BCEFA, 0xF5A9B8};
		for (int k = 0; k < 14; k++) {
			float x = ((k * 73 + 19) % 100) / 100.0F * width;
			float travel = height + 30.0F;
			float y = height + 10.0F - ((t * (1.6F + (k % 3) * 0.5F) + k * 41.0F) % travel);
			float sway = Mth.sin(t * 0.12F + k) * 6.0F;
			int colour = colours[k % colours.length];
			drawHeart(graphics, x + sway, y, 1.5F + (k % 2), (alpha << 24) | colour, 1.0F);
		}
	}

	private static float clamp01(float value) {
		return Mth.clamp(value, 0.0F, 1.0F);
	}

	private static float ease(float x) {
		return x * x * (3.0F - 2.0F * x); // smoothstep
	}
}
