package dev.goober.transdimension.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;

/**
 * The trans enderman of the realm: snow white with soft blue and pink, glowing pink eyes, and light blue sparkles
 * instead of purple portal particles. It behaves like a vanilla enderman (it teleports and gets angry when stared at)
 * except that it leaves blocks alone, and it drops Trans Pearls, which make Trans Crystal Pearls.
 *
 * <p>The ambient sparkles are swapped in by {@code EnderManMixin}; the teleport burst is {@link #handleEntityEvent}.
 */
public class TransEnderman extends EnderMan {
	/** Light blue and white, for the sparkles. */
	public static final int[] SPARKLE_COLOURS = {0xA6E6FF, 0x7FD0F8, 0xE6F8FF};
	/** The entity event vanilla sends when something teleports (it would show purple portal particles). */
	private static final byte TELEPORT_EVENT = 46;

	public TransEnderman(EntityType<? extends EnderMan> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// Vanilla's enderman picks up and puts down blocks; the trans enderman doesn't (its hands are for waving).
		this.goalSelector.removeAllGoals(goal -> goal.getClass().getSimpleName().contains("Block"));
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id != TELEPORT_EVENT) {
			super.handleEntityEvent(id);
			return;
		}
		Level level = this.level();
		for (int i = 0; i < 48; i++) {
			int colour = SPARKLE_COLOURS[i % SPARKLE_COLOURS.length];
			level.addParticle(new DustParticleOptions(colour, 1.2F), this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6),
					0.0, 0.0, 0.0);
		}
		for (int i = 0; i < 8; i++) {
			level.addParticle(ParticleTypes.END_ROD, this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5),
					(this.random.nextDouble() - 0.5) * 0.1, this.random.nextDouble() * 0.1, (this.random.nextDouble() - 0.5) * 0.1);
		}
	}
}
