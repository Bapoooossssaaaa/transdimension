package dev.goober.transdimension.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import dev.goober.transdimension.item.TransWings;
import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModItems;

/**
 * The Trans Wand's spell: a glowing heart that ignores gravity, trails pink, blue and white sparkles and bursts on
 * impact, dealing magic damage (6, three hearts). It fizzles out after three seconds.
 */
public class TransMagicBolt extends ThrowableItemProjectile {
	private static final float DAMAGE = 6.0F;
	private static final int LIFETIME = 60;

	public TransMagicBolt(EntityType<? extends ThrowableItemProjectile> type, Level level) {
		super(type, level);
	}

	/** Shot from a wand: shows the spell sprite rather than the wand that cast it. */
	public TransMagicBolt(Level level, LivingEntity owner, ItemStack wand) {
		super(ModEntities.TRANS_MAGIC_BOLT, owner, level, new ItemStack(ModItems.TRANS_MAGIC_BOLT));
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.TRANS_MAGIC_BOLT;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		super.tick();
		Level level = this.level();
		if (level.isClientSide()) {
			int colour = TransWings.SPARKLE_COLOURS[this.tickCount % TransWings.SPARKLE_COLOURS.length];
			level.addParticle(new DustParticleOptions(colour, 0.9F), this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
			if (this.tickCount % 3 == 0) {
				level.addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
			}
		} else if (this.tickCount > LIFETIME) {
			this.burst();
			this.discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		if (this.level() instanceof ServerLevel serverLevel) {
			Entity target = result.getEntity();
			Entity owner = this.getOwner();
			target.hurtServer(serverLevel, this.damageSources().indirectMagic(this, owner), DAMAGE);
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!this.level().isClientSide()) {
			this.burst();
			this.discard();
		}
	}

	private void burst() {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		for (int colour : TransWings.SPARKLE_COLOURS) {
			level.sendParticles(new DustParticleOptions(colour, 1.3F), this.getX(), this.getY(), this.getZ(), 8, 0.25, 0.25, 0.25, 0.05);
		}
		level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 6, 0.1, 0.1, 0.1, 0.08);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.5F);
	}
}
