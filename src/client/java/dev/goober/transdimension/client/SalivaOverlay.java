package dev.goober.transdimension.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.registry.ModEffects;

/**
 * Cat spit all over your screen after a Silly Cat licks you (while you have the Slobbered effect).
 *
 * <p>The goo is a full-screen texture; it fades out by stepping through pre-faded frames
 * ({@code textures/gui/saliva/saliva_0..5.png}) over the last second and a half, and a few drips
 * slowly run down the glass the whole time.
 */
public final class SalivaOverlay {
	private static final int FRAMES = 6;
	private static final int FADE_TICKS = 30;
	private static final Identifier[] TEXTURES = new Identifier[FRAMES];

	/** Drips: x position (fraction of width), speed, width in pixels, start delay. */
	private static final float[][] DRIPS = {
			{0.08F, 0.55F, 3, 0}, {0.21F, 0.35F, 2, 8}, {0.33F, 0.7F, 4, 3}, {0.47F, 0.3F, 2, 14},
			{0.61F, 0.5F, 3, 5}, {0.74F, 0.65F, 2, 10}, {0.86F, 0.42F, 4, 2}, {0.94F, 0.6F, 2, 12},
	};

	static {
		for (int i = 0; i < FRAMES; i++) {
			TEXTURES[i] = TransDimension.id("textures/gui/saliva/saliva_" + i + ".png");
		}
	}

	private SalivaOverlay() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		MobEffectInstance slobber = player.getEffect(ModEffects.SLOBBERED);
		if (slobber == null) {
			return;
		}

		int remaining = slobber.getDuration();
		int frame = remaining >= FADE_TICKS ? 0 : Math.min(FRAMES - 1, 1 + (FADE_TICKS - remaining) * (FRAMES - 1) / FADE_TICKS);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURES[frame], 0, 0, 0.0F, 0.0F, width, height, width, height);

		// Drips run down the screen; they share the overlay's fade.
		int alpha = remaining >= FADE_TICKS ? 150 : 150 * remaining / FADE_TICKS;
		if (alpha <= 4) {
			return;
		}
		int elapsed = Math.max(0, 160 - remaining);
		for (float[] drip : DRIPS) {
			float t = Math.max(0.0F, elapsed - drip[3]);
			int x = Math.round(drip[0] * width);
			int w = Math.round(drip[2]);
			int length = Math.min(height, Math.round(8 + t * drip[1] * height / 100.0F));
			graphics.fill(x, 0, x + w, length, (alpha << 24) | 0xFFF3F8);
			graphics.fill(x, 0, x + 1, length, ((alpha / 2) << 24) | 0xFFFFFF);
			// A fat droplet at the end of each drip.
			graphics.fill(x - 1, length, x + w + 1, length + w + 1, (alpha << 24) | 0xFBE3EE);
		}
	}
}
