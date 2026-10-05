package dev.goober.transdimension.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModItems;

/**
 * A thrown Trans Crystal Pearl, the Trans Realm's eye of ender. It rises and glides towards the nearest Fairy Sanctum
 * (twelve blocks at a time, or right to it once it's close), trailing pink, blue and white sparkles, then drops back as
 * a pearl four times in five and shatters the fifth. Throw it, follow it, and throw again until it sinks into the shrine.
 *
 * <p>It steers like vanilla's EyeOfEnder (whose 26.2 names aren't confirmed here), but it is a ThrowableItemProjectile
 * like the Trans Wand's bolt, so a hill or a tree in its way ends its flight early.
 */
public class CrystalEye extends ThrowableItemProjectile {
	private static final int LIFETIME = 80;
	private static final int[] SPARKLES = {0xF5A9B8, 0x5BCEFA, 0xFFFFFF};

	private double targetX;
	private double targetY;
	private double targetZ;
	/** False after a reload (the target isn't saved): such an eye just drops its pearl. */
	private boolean aimed;
	private boolean survives;

	public CrystalEye(EntityType<? extends ThrowableItemProjectile> type, Level level) {
		super(type, level);
	}

	public CrystalEye(Level level, LivingEntity thrower, ItemStack pearl) {
		super(ModEntities.CRYSTAL_EYE, thrower, level, pearl.copyWithCount(1));
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.TRANS_CRYSTAL_PEARL;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	/** Aims at a sanctum: straight there within twelve blocks, otherwise twelve blocks that way and eight up. */
	public void signalTo(BlockPos sanctum) {
		double dx = sanctum.getX() + 0.5 - this.getX();
		double dz = sanctum.getZ() + 0.5 - this.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance > 12.0) {
			this.targetX = this.getX() + dx / distance * 12.0;
			this.targetY = this.getY() + 8.0;
			this.targetZ = this.getZ() + dz / distance * 12.0;
		} else {
			this.targetX = sanctum.getX() + 0.5;
			this.targetY = sanctum.getY();
			this.targetZ = sanctum.getZ() + 0.5;
		}
		this.aimed = true;
		this.survives = this.random.nextInt(5) > 0;
	}

	@Override
	public void tick() {
		Level level = this.level();
		if (!level.isClientSide()) {
			if (!this.aimed || this.tickCount > LIFETIME) {
				this.land();
				return;
			}
			// Speed up gently towards the target, climbing or sinking to its height (vanilla's eye of ender does the same).
			Vec3 motion = this.getDeltaMovement();
			double dx = this.targetX - this.getX();
			double dz = this.targetZ - this.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);
			double angle = Math.atan2(dz, dx);
			double speed = Mth.lerp(0.0025, motion.horizontalDistance(), distance);
			double vy = motion.y;
			if (distance < 1.0) {
				speed *= 0.8;
				vy *= 0.8;
			}
			double up = this.getY() < this.targetY ? 1.0 : -1.0;
			this.setDeltaMovement(Math.cos(angle) * speed, vy + (up - vy) * 0.015, Math.sin(angle) * speed);
		}
		super.tick();
		if (level.isClientSide()) {
			Vec3 motion = this.getDeltaMovement();
			int colour = SPARKLES[this.random.nextInt(SPARKLES.length)];
			level.addParticle(new DustParticleOptions(colour, 0.9F), this.getX() - motion.x * 0.25 + this.random.nextGaussian() * 0.08,
					this.getY() - motion.y * 0.25 + 0.1, this.getZ() - motion.z * 0.25 + this.random.nextGaussian() * 0.08, 0.0, 0.0, 0.0);
			if (this.tickCount % 4 == 0) {
				level.addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
			}
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		// It passes through creatures: only the ground and trees stop it.
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (result.getType() == HitResult.Type.BLOCK && !this.level().isClientSide()) {
			this.land();
		}
	}

	/** The end of the flight: the pearl drops (four times in five) or shatters in a burst of sparkles. */
	private void land() {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.survives || !this.aimed) {
			this.spawnAtLocation(level, this.getItem());
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.2F);
		} else {
			for (int colour : SPARKLES) {
				level.sendParticles(new DustParticleOptions(colour, 1.3F), this.getX(), this.getY(), this.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.NEUTRAL, 1.0F, 1.1F);
		}
		this.discard();
	}

	@Override
	public void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.aimed = false;
	}
}
