package dev.goober.transdimension.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;

import dev.goober.transdimension.registry.ModSounds;

/**
 * The arrival cinematic, drawn on top of the whole HUD:
 *
 * <ol>
 *   <li>letterbox bars close in over a starry indigo night,</li>
 *   <li>the five stripes of the trans flag unfurl from the centre and wave in the wind,</li>
 *   <li>a big pixel heart pops in with an elastic bounce, sends out glowing rings and a burst of sparkles,</li>
 *   <li>the title types itself out, followed by the subtitle,</li>
 *   <li>then the stripes fly off in alternating directions, the bars open, and a soft flash reveals the realm.</li>
 * </ol>
 *
 * Timing uses {@link System#nanoTime} so motion is smooth at any frame rate; sounds are scheduled on client ticks.
 */
public final class TransIntroOverlay {
	private static final float DURATION = 170.0F; // ticks
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
	private static final String REALM_TITLE = "Welcome to the Trans Realm";
	private static final String REALM_SUBTITLE = "You are valid. You are loved. ❤";
	private static final String REALM_HINT = "Say \"Goober\" again to go home";
	private static final String FAIRY_TITLE = "The Fairy Realm";
	private static final String FAIRY_SUBTITLE = "The Trans Fairy awaits in her arena ✦";
	private static final String FAIRY_HINT = "Beat her to open the portal home";

	private static String title = REALM_TITLE;
	private static String subtitle = REALM_SUBTITLE;
	private static String hint = REALM_HINT;

	private static long startNanos = -1L;
	private static int ticks = -1;

	private TransIntroOverlay() {
	}

	public static void start() {
		start(false);
	}

	/** Plays the arrival cinematic, titled for the Trans Realm or the Fairy Realm. */
	public static void start(boolean fairyRealm) {
		title = fairyRealm ? FAIRY_TITLE : REALM_TITLE;
		subtitle = fairyRealm ? FAIRY_SUBTITLE : REALM_SUBTITLE;
		hint = fairyRealm ? FAIRY_HINT : REALM_HINT;
		startNanos = System.nanoTime();
		ticks = 0;
	}

	public static void tick(Minecraft client) {
		if (ticks < 0) {
			return;
		}
		ticks++;

		if (ticks == 2) {
			play(client, ModSounds.INTRO_WHOOSH, 0.7F);
		}
		// The flag unfurls to a rising bell arpeggio...
		float[] pitches = {0.707F, 0.794F, 0.891F, 1.0F, 1.189F};
		for (int i = 0; i < pitches.length; i++) {
			if (ticks == 22 + i * 5) {
				play(client, ModSounds.INTRO_BELL, pitches[i]);
			}
		}
		// ...the heart lands with a sparkly chord...
		if (ticks == 56) {
			play(client, ModSounds.INTRO_CHIME, 1.0F);
			play(client, ModSounds.INTRO_BELL, 1.335F);
			play(client, ModSounds.INTRO_BELL, 1.587F);
		}
		// ...and the realm is revealed with one last shimmer.
		if (ticks == 140) {
			play(client, ModSounds.INTRO_WHOOSH, 1.2F);
		}
		if (ticks == 156) {
			play(client, ModSounds.INTRO_CHIME, 1.26F);
		}

		if (ticks > DURATION + 5) {
			ticks = -1;
			startNanos = -1L;
		}
	}

	private static void play(Minecraft client, net.minecraft.sounds.SoundEvent sound, float pitch) {
		client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.7F));
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

		// 1) Backdrop: indigo night, fading in fast and out at the end.
		float backdrop = clamp01(t / 8.0F) * (1.0F - clamp01((t - 128.0F) / 22.0F));
		if (backdrop > 0.01F) {
			int a = Math.round(backdrop * 235.0F);
			graphics.fillGradient(0, 0, width, height, (a << 24) | 0x0B1030, (a << 24) | 0x2A1846);
			stars(graphics, t, width, height, backdrop);
		}

		// 2) Waving flag stripes in a band across the middle of the screen.
		flag(graphics, t, width, height);

		// 3) The heart, its rings and sparkles.
		float heartIn = clamp01((t - 50.0F) / 14.0F);
		float heartOut = clamp01((t - 130.0F) / 10.0F);
		if (heartIn > 0.0F && heartOut < 1.0F) {
			float cx = width / 2.0F;
			float cy = height * 0.36F;
			float pixel = Math.max(2.0F, height / 40.0F);
			float pop = easeOutBack(heartIn) * (1.0F - easeInBack(heartOut));
			float beat = 1.0F + 0.06F * Mth.sin(t * 0.5F) * heartIn;
			int alpha = Math.round(255 * clamp01(heartIn * 3.0F) * (1.0F - heartOut));

			for (int ring = 0; ring < 3; ring++) {
				float phase = ((t - 56.0F) / 30.0F + ring / 3.0F) % 1.0F;
				if (t >= 56.0F) {
					int ringAlpha = Math.round(90 * (1.0F - phase) * (1.0F - heartOut));
					drawHeart(graphics, cx, cy, pixel * (1.0F + phase * 1.6F), (ringAlpha << 24) | 0xF5A9B8);
				}
			}
			sparkles(graphics, t - 56.0F, cx, cy, pixel, 1.0F - heartOut);
			drawHeart(graphics, cx, cy + pixel * 0.3F, pixel * pop * beat * 1.12F, (alpha << 24) | 0xB04A70);
			drawHeart(graphics, cx, cy, pixel * pop * beat, (alpha << 24) | 0xFFFFFF);
			drawHeart(graphics, cx - pixel * 0.9F, cy - pixel * 0.9F, pixel * pop * beat * 0.28F, (alpha << 24) | 0xFBE0EA);
		}

		// 4) Text: the title types itself out, then the subtitle and hint fade in.
		Font font = Minecraft.getInstance().font;
		float textOut = 1.0F - clamp01((t - 126.0F) / 10.0F);
		int typed = Mth.clamp((int) ((t - 66.0F) * 0.75F), 0, title.length());
		if (typed > 0 && textOut > 0.0F) {
			int a = Math.round(255 * textOut);
			drawText(graphics, font, title.substring(0, typed), width / 2.0F, height * 0.56F, 2.4F, (a << 24) | 0xFFFFFF, (a << 24) | 0x2B3A67);
		}
		float subtitleIn = clamp01((t - 104.0F) / 10.0F) * textOut;
		if (subtitleIn > 0.02F) {
			int a = Math.max(8, Math.round(255 * subtitleIn));
			drawText(graphics, font, subtitle, width / 2.0F, height * 0.70F, 1.5F, (a << 24) | 0xFBD3DD, (a << 24) | 0x5A2440);
			drawText(graphics, font, hint, width / 2.0F, height * 0.80F, 1.0F, (a << 24) | 0xD6F4FE, (a << 24) | 0x173055);
		}

		// 5) Letterbox bars close at the start and open at the end.
		float bars = easeInOut(clamp01(t / 12.0F)) * (1.0F - easeInOut(clamp01((t - 140.0F) / 16.0F)));
		int barHeight = Math.round(height * 0.11F * bars);
		if (barHeight > 0) {
			graphics.fill(0, 0, width, barHeight, 0xFF000000);
			graphics.fill(0, height - barHeight, width, height, 0xFF000000);
		}

		// 6) A soft white flash as the realm is revealed.
		float flash = Math.max(0.0F, 1.0F - Math.abs(t - 150.0F) / 12.0F);
		if (flash > 0.0F) {
			graphics.fill(0, 0, width, height, (Math.round(flash * 120.0F) << 24) | 0xFFF4F8);
		}
	}

	private static void flag(GuiGraphicsExtractor graphics, float t, int width, int height) {
		float bandTop = height * 0.18F;
		float bandHeight = height * 0.64F;
		int slices = 40;
		for (int i = 0; i < 5; i++) {
			float unfurl = easeOutCubic(clamp01((t - 18.0F - i * 3.0F) / 18.0F));
			if (unfurl <= 0.0F) {
				continue;
			}
			// At the end each stripe flies off, alternating left and right.
			float leave = easeInCubic(clamp01((t - 124.0F - i * 2.5F) / 14.0F));
			float direction = i % 2 == 0 ? -1.0F : 1.0F;
			float halfWidth = width * 0.5F * unfurl;
			float shift = direction * leave * width * 1.05F;
			int alpha = Math.round(230 * clamp01(unfurl * 2.0F));
			// Behind the heart and text the flag dims so they stay readable.
			float dim = clamp01((t - 46.0F) / 14.0F) * 0.45F;
			int colour = mix(FLAG[i], 0x101030, dim);

			float y0 = bandTop + bandHeight * i / 5.0F;
			float y1 = bandTop + bandHeight * (i + 1) / 5.0F;
			for (int s = 0; s < slices; s++) {
				float sx0 = width / 2.0F - halfWidth + 2.0F * halfWidth * s / slices + shift;
				float sx1 = width / 2.0F - halfWidth + 2.0F * halfWidth * (s + 1) / slices + shift;
				float wave = Mth.sin(t * 0.18F + s * 0.35F) * height * 0.012F * unfurl;
				graphics.fill(Math.round(sx0), Math.round(y0 + wave), Math.round(sx1) + 1, Math.round(y1 + wave), (alpha << 24) | colour);
			}
		}
	}

	private static void stars(GuiGraphicsExtractor graphics, float t, int width, int height, float strength) {
		for (int k = 0; k < 70; k++) {
			float x = hash(k * 2 + 1) * width;
			float y = hash(k * 2 + 7) * height;
			float twinkle = 0.55F + 0.45F * Mth.sin(t * (0.15F + hash(k) * 0.25F) + k);
			int a = Math.round(220 * twinkle * strength * clamp01((t - k * 0.15F) / 10.0F));
			int colour = k % 3 == 0 ? 0xF5A9B8 : k % 3 == 1 ? 0x5BCEFA : 0xFFFFFF;
			int size = k % 7 == 0 ? 2 : 1;
			graphics.fill(Math.round(x), Math.round(y), Math.round(x) + size, Math.round(y) + size, (a << 24) | colour);
		}
	}

	private static void sparkles(GuiGraphicsExtractor graphics, float age, float cx, float cy, float pixel, float fade) {
		if (age <= 0.0F || age > 50.0F) {
			return;
		}
		for (int k = 0; k < 28; k++) {
			float angle = hash(k + 31) * (float) (Math.PI * 2.0);
			float speed = (1.2F + hash(k + 77) * 2.2F) * pixel * 0.35F;
			float distance = speed * age * (1.0F - age / 110.0F);
			float x = cx + Mth.cos(angle) * distance;
			float y = cy + Mth.sin(angle) * distance + age * age * 0.004F * pixel;
			int a = Math.round(255 * clamp01(1.0F - age / 50.0F) * fade);
			int colour = FLAG[k % 5];
			int r = Math.max(1, Math.round(pixel * 0.25F));
			int ix = Math.round(x);
			int iy = Math.round(y);
			graphics.fill(ix - r, iy, ix + r + 1, iy + 1, (a << 24) | colour);
			graphics.fill(ix, iy - r, ix + 1, iy + r + 1, (a << 24) | colour);
		}
	}

	/** Centred, scaled text with a soft offset shadow in its own colour. */
	private static void drawText(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, float scale, int argb, int shadowArgb) {
		int textWidth = font.width(text);
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale);
		graphics.text(font, text, -textWidth / 2 + 1, 1, shadowArgb, false);
		graphics.text(font, text, -textWidth / 2, 0, argb, false);
		graphics.pose().popMatrix();
	}

	/** Draws the pixel heart centred on (cx, cy), each heart pixel being {@code pixel} GUI units wide. */
	private static void drawHeart(GuiGraphicsExtractor graphics, float cx, float cy, float pixel, int argb) {
		if (pixel <= 0.05F || (argb >>> 24) == 0) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(pixel);
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

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** A cheap deterministic 0..1 hash, so stars and sparkles stay put from frame to frame. */
	private static float hash(int n) {
		int x = n * 0x45D9F3B;
		x = ((x >>> 16) ^ x) * 0x45D9F3B;
		x = (x >>> 16) ^ x;
		return (x & 0xFFFF) / 65535.0F;
	}

	private static float clamp01(float value) {
		return Mth.clamp(value, 0.0F, 1.0F);
	}

	private static float easeInOut(float x) {
		return x * x * (3.0F - 2.0F * x);
	}

	private static float easeOutCubic(float x) {
		float inv = 1.0F - x;
		return 1.0F - inv * inv * inv;
	}

	private static float easeInCubic(float x) {
		return x * x * x;
	}

	private static float easeOutBack(float x) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float k = x - 1.0F;
		return 1.0F + c3 * k * k * k + c1 * k * k;
	}

	private static float easeInBack(float x) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		return c3 * x * x * x - c1 * x * x;
	}
}
