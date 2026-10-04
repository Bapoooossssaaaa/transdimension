package dev.goober.transdimension.world;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RandomSource;

/**
 * Per-world record of the village plushes: the villages (by structure start chunk) that already got one, and how
 * many of each plush have been handed out. Saved with the level through a Fabric data attachment.
 *
 * @param villages  start chunks ({@code ChunkPos#pack}) of villages whose plush has been placed
 * @param handedOut how many times each plush (index into {@code ModBlocks.PLUSHES}) has been placed
 */
public record PlushLedger(List<Long> villages, List<Integer> handedOut) {
	public static final PlushLedger EMPTY = new PlushLedger(List.of(), List.of());

	public static final Codec<PlushLedger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.listOf().fieldOf("villages").forGetter(PlushLedger::villages),
			Codec.INT.listOf().fieldOf("handed_out").forGetter(PlushLedger::handedOut)
	).apply(instance, PlushLedger::new));

	/** A random plush among those handed out the fewest times. */
	public int leastGiven(int plushCount, RandomSource random) {
		int fewest = Integer.MAX_VALUE;
		List<Integer> candidates = new ArrayList<>();
		for (int i = 0; i < plushCount; i++) {
			int given = this.given(i);
			if (given < fewest) {
				fewest = given;
				candidates.clear();
			}
			if (given == fewest) {
				candidates.add(i);
			}
		}
		return candidates.get(random.nextInt(candidates.size()));
	}

	/** This ledger plus one more village, which got plush number {@code plush}. */
	public PlushLedger withPlush(long village, int plush, int plushCount) {
		List<Long> newVillages = new ArrayList<>(this.villages);
		newVillages.add(village);
		List<Integer> newCounts = new ArrayList<>();
		for (int i = 0; i < Math.max(plushCount, this.handedOut.size()); i++) {
			newCounts.add(this.given(i) + (i == plush ? 1 : 0));
		}
		return new PlushLedger(List.copyOf(newVillages), List.copyOf(newCounts));
	}

	private int given(int plush) {
		return plush < this.handedOut.size() ? this.handedOut.get(plush) : 0;
	}
}
