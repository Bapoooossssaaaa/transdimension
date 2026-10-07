package dev.goober.transdimension.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import dev.goober.transdimension.network.FairyCutscenePayload;

/**
 * The Fairy Realm cutscene's subtitles as each watching player sees them (the server runs the scene: FairyCutscene; the
 * camera moves with {@link FairyCutsceneCamera}). There are no bars: each line types itself out low on the screen under
 * its speaker's name, Maddie's in pink and the Trans Fairy's in blue, and the last one fades away as the fight begins.
 * The rest of the HUD is hidden while the scene plays (TransDimensionClient).
 */
public final class FairyCutsceneOverlay {
	private static final float CHARS_PER_SECOND = 30.0F;
	private static final float FADE_SECONDS = 0.6F;
	private static final int MADDIE_PINK = 0xF5A9B8;
	private static final int FAIRY_BLUE = 0x5BCEFA;

	private static boolean active;
	/** When the scene ended (the last line fades from then), or -1. */
	private static long endedNanos = -1L;
	private static String speaker = "";
	private static int speakerColour = MADDIE_PINK;
	private static String line = "";
	private static long lineNanos;

	private FairyCutsceneOverlay() {
	}

	public static void handle(FairyCutscenePayload payload) {
		long now = System.nanoTime();
		switch (payload.kind()) {
			case FairyCutscenePayload.START -> {
				active = true;
				endedNanos = -1L;
				line = "";
				FairyCutsceneCamera.start();
			}
			case FairyCutscenePayload.LINE -> {
				active = true;
				endedNanos = -1L;
				speaker = Component.translatable(payload.speaker()).getString();
				speakerColour = payload.speaker().contains("trans_fairy") ? FAIRY_BLUE : MADDIE_PINK;
				line = Component.translatable(payload.line()).getString();
				lineNanos = now;
			}
			case FairyCutscenePayload.SHIELD -> FairyCutsceneCamera.shieldHit();
			default -> {
				active = false;
				endedNanos = now;
				FairyCutsceneCamera.stop();
			}
		}
	}

	/** Clears the scene (leaving the Fairy Realm mid-scene). */
	public static void reset() {
		active = false;
		endedNanos = -1L;
		line = "";
		FairyCutsceneCamera.stop();
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (line.isEmpty() || (!active && endedNanos < 0L)) {
			return;
		}
		long now = System.nanoTime();
		float alpha = active ? 1.0F : 1.0F - (now - endedNanos) / 1.0e9F / FADE_SECONDS;
		if (alpha <= 0.05F) {
			if (!active) {
				line = "";
				endedNanos = -1L;
			}
			return;
		}
		Font font = Minecraft.getInstance().font;
		int width = graphics.guiWidth();
		int y = graphics.guiHeight() - 46;
		int typed = Mth.clamp((int) ((now - lineNanos) / 1.0e9F * CHARS_PER_SECOND), 0, line.length());
		int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24;
		graphics.text(font, speaker, (width - font.width(speaker)) / 2, y, a | speakerColour, true);
		// Centred on the whole line, so the words don't shift as they type out.
		graphics.text(font, line.substring(0, typed), (width - font.width(line)) / 2, y + 12, a | 0xFFFFFF, true);
	}
}
