package dev.goober.transdimension.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import dev.goober.transdimension.registry.ModEntities;

/**
 * An ice crystal that bursts out of the ground under the Trans Fairy's targets, like an evoker's fangs: frost swirls on
 * the spot while it builds up (your warning to move), then a cluster of icy pink and blue crystal shards shoots up,
 * hurting and chilling whatever stands there, and sinks back into the ground. The client's FairyCrystalSpikeRenderer
 * animates the shards from {@link #getEruption(float)}.
 */
public class FairyCrystalSpike extends Entity {
	/** Ticks the shards stay out once they erupt (rise, hold, sink). */
	public static final int LIFETIME = 22;
	private static final float DAMAGE = 7.0F;
	private static final byte ERUPT_EVENT = 4;
	private static final int[] FROST = {0xCFEAFB, 0xF9D3E0, 0xFFFFFF};

	private int warmup;
	private int lifeTicks = LIFETIME;
	private boolean erupted;
	private boolean clientErupted;
	@Nullable
	private UUID ownerId;

	public FairyCrystalSpike(EntityType<? extends FairyCrystalSpike> type, Level level) {
		super(type, level);
	}

	public FairyCrystalSpike(Level level, double x, double y, double z, float yRot, int warmup, @Nullable LivingEntity owner) {
		this(ModEntities.FAIRY_CRYSTAL_SPIKE, level);
		this.warmup = warmup;
		this.ownerId = owner != null ? owner.getUUID() : null;
		this.setYRot(yRot);
		this.setPos(x, y, z);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		Level level = this.level();
		if (level.isClientSide()) {
			if (this.clientErupted) {
				this.lifeTicks--;
			} else if (this.tickCount % 2 == 0) {
				// The warning: frost spiralling up from the spot.
				float angle = this.tickCount * 0.6F;
				int colour = FROST[this.tickCount / 2 % FROST.length];
				level.addParticle(new DustParticleOptions(colour, 0.9F), this.getX() + Mth.cos(angle) * 0.45, this.getY() + 0.05,
						this.getZ() + Mth.sin(angle) * 0.45, 0.0, 0.08, 0.0);
				level.addParticle(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.05, 0.0);
			}
			return;
		}
		if (--this.warmup > 0) {
			return;
		}
		if (!this.erupted) {
			this.erupted = true;
			this.level().broadcastEntityEvent(this, ERUPT_EVENT);
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.2F,
					0.7F + this.random.nextFloat() * 0.3F);
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.6F, 1.6F);
			if (level instanceof ServerLevel serverLevel) {
				this.hurtWhatStandsHere(serverLevel);
			}
		}
		if (--this.lifeTicks < 0) {
			this.discard();
		}
	}

	private void hurtWhatStandsHere(ServerLevel level) {
		Entity owner = this.ownerId != null ? level.getEntity(this.ownerId) : null;
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.3, 0.2, 0.3))) {
			if (victim == owner || victim instanceof TransFairy || !victim.isAlive() || victim.isInvulnerable()) {
				continue;
			}
			DamageSource source = this.damageSources().indirectMagic(this, owner);
			if (victim.hurtServer(level, source, DAMAGE)) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
			}
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id != ERUPT_EVENT) {
			super.handleEntityEvent(id);
			return;
		}
		this.clientErupted = true;
		Level level = this.level();
		for (int i = 0; i < 12; i++) {
			int colour = FROST[i % FROST.length];
			level.addParticle(new DustParticleOptions(colour, 1.2F), this.getRandomX(0.6), this.getY() + this.random.nextDouble() * 1.6,
					this.getRandomZ(0.6), 0.0, 0.05, 0.0);
		}
		level.addParticle(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.1, 0.0);
	}

	/** Client side: 0 while waiting, then 0..1 as the shards shoot up (with a little overshoot), 1 held, back to 0 as they sink. */
	public float getEruption(float partialTick) {
		if (!this.clientErupted) {
			return 0.0F;
		}
		float age = LIFETIME - this.lifeTicks + partialTick;
		if (age < 3.0F) {
			float t = age / 3.0F;
			return t * (1.25F - 0.25F * t);
		}
		if (age < 5.0F) {
			return 1.0F + 0.08F * (1.0F - (age - 3.0F) / 2.0F);
		}
		if (age < LIFETIME - 6.0F) {
			return 1.0F;
		}
		return Math.max(0.0F, (LIFETIME - age) / 6.0F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		this.warmup = valueInput.getIntOr("warmup", 0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		valueOutput.putInt("warmup", this.warmup);
	}
}
