package dev.goober.transdimension.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import dev.goober.transdimension.network.FairyCutscenePayload;

/**
 * The Fairy Realm's cutscene as each watching player sees it (the server runs it: FairyCutscene). Black letterbox bars
 * slide in over the HUD, each of Maddie's lines types itself out as a subtitle in the lower bar under her name in pink,
 * and the bars slide away again as the fight begins.
 */
public final class FairyCutsceneOverlay {
	private static final float BAR_SECONDS = 0.6F;
	private static final float CHARS_PER_SECOND = 30.0F;

	private static boolean active;
	/** When the bars last started moving in or out; -1 when there's nothing to draw. */
	private static long changedNanos = -1L;
	private static String speaker = "";
	private static String line = "";
	private static long lineNanos;

	private FairyCutsceneOverlay() {
	}

	public static void handle(FairyCutscenePayload payload) {
		long now = System.nanoTime();
		switch (payload.kind()) {
			case FairyCutscenePayload.START -> {
				active = true;
				changedNanos = now;
				line = "";
			}
			case FairyCutscenePayload.LINE -> {
				if (!active) {
					active = true;
					changedNanos = now;
				}
				speaker = Component.translatable(payload.speaker()).getString();
				line = Component.translatable(payload.line()).getString();
				lineNanos = now;
			}
			default -> {
				active = false;
				changedNanos = now;
			}
		}
	}

	/** Clears the scene (leaving the Fairy Realm mid-scene). */
	public static void reset() {
		active = false;
		changedNanos = -1L;
		line = "";
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (changedNanos < 0L) {
			return;
		}
		long now = System.nanoTime();
		float t = Mth.clamp((now - changedNanos) / 1.0e9F / BAR_SECONDS, 0.0F, 1.0F);
		float eased = t * t * (3.0F - 2.0F * t);
		float shown = active ? eased : 1.0F - eased;
		if (shown <= 0.0F) {
			changedNanos = -1L;
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int bar = Math.round(height * 0.13F * shown);
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
		if (active && shown >= 1.0F && !line.isEmpty()) {
			Font font = Minecraft.getInstance().font;
			int typed = Mth.clamp((int) ((now - lineNanos) / 1.0e9F * CHARS_PER_SECOND), 0, line.length());
			int y = height - bar + Math.max(2, (bar - 20) / 2);
			graphics.text(font, speaker, (width - font.width(speaker)) / 2, y, 0xFFF5A9B8, true);
			// Centred on the whole line, so the words don't shift as they type out.
			graphics.text(font, line.substring(0, typed), (width - font.width(line)) / 2, y + 11, 0xFFFFFFFF, true);
		}
	}
}
