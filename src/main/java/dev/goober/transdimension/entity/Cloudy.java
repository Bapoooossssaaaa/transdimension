package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A Cloudy: a little smiling cloud that drifts about the Cloud Realm a few blocks above the islands. Use one to sit on
 * it, and it carries you wherever you look: hold forward to fly the way you're facing (look up to climb, down to sink),
 * back to drift backwards, and sneak to hop off.
 *
 * <p>It flies by setting its own velocity, like a wild fairy. When a player rides it, the riding player's game moves it
 * (vanilla's ridden-entity path: {@link #getRiddenInput} becomes the {@link #travel} input).
 */
public class Cloudy extends PathfinderMob {
	/** How fast a ridden cloud picks up speed, and the drag that caps it (top speed about 0.35 blocks a tick). */
	private static final float RIDDEN_ACCELERATION = 0.035F;
	private static final double DRAG = 0.9;

	@Nullable
	private Vec3 wanderTarget;

	public Cloudy(EntityType<? extends Cloudy> entityType, Level level) {
		super(entityType, level);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 14.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.FLYING_SPEED, 0.3);
	}

	/** Natural spawns: over the Cloud Realm's grass (anything animals spawn on). */
	public static boolean checkCloudySpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		return level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON) && Mob.checkMobSpawnRules(type, level, reason, pos, random);
	}

	// ------------------------------------------------------------------------------------------------ riding

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.isVehicle() && !player.isSecondaryUseActive()) {
			if (!this.level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		return this.getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
	}

	@Override
	protected void tickRidden(Player player, Vec3 travelVector) {
		super.tickRidden(player, travelVector);
		this.setRot(player.getYRot(), player.getXRot() * 0.5F);
		this.yRotO = this.getYRot();
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
	}

	/** Forward flies along where the rider looks (pitch included), backward drifts back slowly, strafing slides sideways. */
	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
		float strafe = player.xxa * 0.6F;
		float forward = player.zza;
		if (forward > 0.0F) {
			float pitch = player.getXRot() * Mth.DEG_TO_RAD;
			return new Vec3(strafe, -Mth.sin(pitch) * forward, Mth.cos(pitch) * forward);
		}
		return new Vec3(strafe, 0.0, forward * 0.4F);
	}

	@Override
	public void travel(Vec3 input) {
		if (this.getControllingPassenger() instanceof Player) {
			this.moveRelative(RIDDEN_ACCELERATION, input);
		}
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(DRAG));
	}

	@Override
	public void tick() {
		super.tick();
		this.fallDistance = 0;
		for (Entity passenger : this.getPassengers()) {
			passenger.resetFallDistance();
		}
		if (this.level().isClientSide() && this.random.nextInt(this.isVehicle() ? 2 : 8) == 0) {
			// Wisps of cloud trail behind it.
			this.level().addParticle(ParticleTypes.CLOUD, this.getRandomX(0.8), this.getY() + 0.2, this.getRandomZ(0.8), 0.0, -0.01, 0.0);
		}
	}

	// ------------------------------------------------------------------------------------------------ drifting about

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.isVehicle()) {
			this.wanderTarget = null;
			return;
		}
		if (this.wanderTarget == null || this.position().distanceToSqr(this.wanderTarget) < 1.0 || this.random.nextInt(160) == 0) {
			this.wanderTarget = this.pickWanderTarget(level);
		}
		if (this.wanderTarget != null) {
			Vec3 to = this.wanderTarget.subtract(this.position());
			double length = to.length();
			if (length > 1.0E-3) {
				this.setDeltaMovement(this.getDeltaMovement().add(to.scale(0.012 / length)));
				// Turn to face the way it drifts.
				float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
				this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 4.0F));
				this.yBodyRot = this.getYRot();
				this.yHeadRot = this.getYRot();
			}
		}
	}

	/** Somewhere within a dozen blocks, three to ten blocks over whatever is below it (and never into the void). */
	@Nullable
	private Vec3 pickWanderTarget(ServerLevel level) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double x = this.getX() + (this.random.nextDouble() - 0.5) * 24.0;
			double z = this.getZ() + (this.random.nextDouble() - 0.5) * 24.0;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			if (ground <= level.getMinY() + 1) {
				continue;
			}
			double y = ground + 3 + this.random.nextInt(8);
			if (y < this.getY() - 16.0) {
				// A deep drop below: stay up at about this height instead.
				y = this.getY() + (this.random.nextDouble() - 0.5) * 4.0;
			}
			return new Vec3(x, y, z);
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------------ odds and ends

	/** A Cloudy carrying the cloud turtle on a rescue (CloudRescue) is borrowed: it isn't saved with the world. */
	@Override
	public boolean shouldBeSaved() {
		return !(this.getFirstPassenger() instanceof CloudTurtle) && super.shouldBeSaved();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WOOL_PLACE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.WOOL_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WOOL_BREAK;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}
}
