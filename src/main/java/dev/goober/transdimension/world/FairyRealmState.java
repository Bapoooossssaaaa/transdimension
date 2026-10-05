package dev.goober.transdimension.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What the Fairy Realm remembers (a Fabric data attachment on its level).
 *
 * @param built      the arena island has been placed
 * @param defeated   the Trans Fairy has been beaten at least once (the portal home is open)
 * @param victories  how many times she has been beaten
 * @param maddieGone the Trans Fairy has struck Maddie down in the arena (FairyCutscene): Maddie is gone from the realm
 */
public record FairyRealmState(boolean built, boolean defeated, int victories, boolean maddieGone) {
	public static final FairyRealmState NEW = new FairyRealmState(false, false, 0, false);

	public static final Codec<FairyRealmState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("built").forGetter(FairyRealmState::built),
			Codec.BOOL.fieldOf("defeated").forGetter(FairyRealmState::defeated),
			Codec.INT.fieldOf("victories").forGetter(FairyRealmState::victories),
			Codec.BOOL.optionalFieldOf("maddie_gone", false).forGetter(FairyRealmState::maddieGone)
	).apply(instance, FairyRealmState::new));

	public FairyRealmState withBuilt() {
		return new FairyRealmState(true, this.defeated, this.victories, this.maddieGone);
	}

	public FairyRealmState withVictory() {
		return new FairyRealmState(this.built, true, this.victories + 1, this.maddieGone);
	}

	public FairyRealmState withMaddieGone() {
		return new FairyRealmState(this.built, this.defeated, this.victories, true);
	}
}
