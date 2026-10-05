package dev.goober.transdimension.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import dev.goober.transdimension.entity.TransFairy;

/**
 * The Trans Fairy's health bar, in place of a vanilla boss bar: the trans flag laid along the bar (blue, pink, white,
 * pink, blue, one stripe per row of pixels), a shimmer sweeping across it, sparkles twinkling around it, and her name
 * above it. The client finds the nearest Trans Fairy within 80 blocks (once her cutscene is over) and reads her synced
 * health, easing the bar down as she's hurt. Marks at two thirds and one third show where her fight gets harder.
 */
public final class TransFairyBossBar {
	private static final int WIDTH = 182;
	private static final int TOP = 14;
	private static final int[] FLAG = {0x5BCEFA, 0xF5A9B8, 0xFFFFFF, 0xF5A9B8, 0x5BCEFA};
	private static final int FRAME = 0xFF2A1630;

	/** The health the bar shows, easing towards her real health; -1 when no fairy is near. */
	private static float shown = -1.0F;

	private TransFairyBossBar() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		TransFairy fairy = null;
		double nearest = Double.MAX_VALUE;
		for (TransFairy candidate : minecraft.level.getEntitiesOfClass(TransFairy.class, minecraft.player.getBoundingBox().inflate(80.0),
				candidate -> candidate.isAlive() && !candidate.isIntro())) {
			double distance = candidate.distanceToSqr(minecraft.player);
			if (distance < nearest) {
				nearest = distance;
				fairy = candidate;
			}
		}
		if (fairy == null) {
			shown = -1.0F;
			return;
		}
		float health = Mth.clamp(fairy.getHealth() / fairy.getMaxHealth(), 0.0F, 1.0F);
		shown = shown < 0.0F ? health : shown + (health - shown) * 0.1F;
		float seconds = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000.0F;
		int width = graphics.guiWidth();
		int x = (width - WIDTH) / 2;

		Font font = minecraft.font;
		String name = "✦ " + fairy.getDisplayName().getString() + " ✦";
		graphics.text(font, name, (width - font.width(name)) / 2, TOP - 11, 0xFFFBD3DD, true);

		// The frame, then each stripe: dim where she's lost health, bright where she hasn't.
		graphics.fill(x - 1, TOP - 1, x + WIDTH + 1, TOP + 6, FRAME);
		int filled = Math.round(WIDTH * shown);
		for (int row = 0; row < FLAG.length; row++) {
			graphics.fill(x, TOP + row, x + WIDTH, TOP + row + 1, 0xFF000000 | dim(FLAG[row]));
			if (filled > 0) {
				graphics.fill(x, TOP + row, x + filled, TOP + row + 1, 0xFF000000 | FLAG[row]);
			}
		}
		// A soft shine that sweeps along the bright part.
		int sweep = x + Math.floorMod((int) (seconds * 80.0F), WIDTH + 40) - 20;
		for (int i = -3; i <= 3; i++) {
			int column = sweep + i;
			if (column >= x && column < x + filled) {
				graphics.fill(column, TOP, column + 1, TOP + 5, ((140 - Math.abs(i) * 35) << 24) | 0xFFFFFF);
			}
		}
		// Phase marks at two thirds and one third.
		for (int mark : new int[] {Math.round(WIDTH * 2.0F / 3.0F), Math.round(WIDTH / 3.0F)}) {
			graphics.fill(x + mark, TOP - 1, x + mark + 1, TOP + 6, FRAME);
		}
		// Sparkles twinkling just above and below the bar, each in a flag colour, fading in and out at its own pace.
		for (int i = 0; i < 8; i++) {
			float cycle = seconds * 0.9F + i * 0.37F;
			int seed = i * 31 + (int) cycle;
			float glow = Mth.sin((cycle - (int) cycle) * Mth.PI);
			int alpha = Math.round(glow * 230.0F);
			if (alpha < 16) {
				continue;
			}
			int px = x + Math.floorMod(seed * 73, WIDTH);
			int py = Math.floorMod(seed, 2) == 0 ? TOP - 3 : TOP + 8;
			int colour = (alpha << 24) | FLAG[Math.floorMod(seed, FLAG.length)];
			graphics.fill(px - 1, py, px + 2, py + 1, colour);
			graphics.fill(px, py - 1, px + 1, py + 2, colour);
		}
	}

	/** The stripe colour for lost health: much darker. */
	private static int dim(int rgb) {
		int r = (rgb >> 16 & 0xFF) * 35 / 100;
		int g = (rgb >> 8 & 0xFF) * 35 / 100;
		int b = (rgb & 0xFF) * 35 / 100;
		return r << 16 | g << 8 | b;
	}
}
