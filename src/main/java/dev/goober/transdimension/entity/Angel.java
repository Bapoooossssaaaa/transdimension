package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.registry.ModEffects;
import dev.goober.transdimension.registry.ModParticles;
import dev.goober.transdimension.world.CloudRealm;

/**
 * An angel of the Cloud Realm: a glowing figure in white and gold with a golden halo over its head and great white
 * feathered wings. Angels gather where the realm's light can't reach (under the islands, in the ruins' towers), and they
 * float there quietly, drifting a little and shedding motes of golden light.
 *
 * <p>They are kind: an angel blesses any hurt player who comes near (the Blessed effect, as from holy water), and anyone
 * who asks (use one), though it needs a while to gather its light again after each blessing. Hit one and it flies off.
 */
public class Angel extends PathfinderMob {
	/** Angels keep at least this far apart. */
	public static final int SPACING = 32;
	private static final int BLESSING_COOLDOWN = 20 * 40;
	private static final double BLESSING_RANGE = 6.0;

	@Nullable
	private Vec3 driftTarget;
	@Nullable
	private Vec3 home;
	private int blessingCooldown;
	private int fleeing;

	public Angel(EntityType<? extends Angel> entityType, Level level) {
		super(entityType, level);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.FLYING_SPEED, 0.3);
	}

	/** Natural spawns: only in the Cloud Realm, only in the dark (light 7 or less), one try in three, never near another. */
	public static boolean checkAngelSpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (!(level instanceof ServerLevelAccessor accessor) || !CloudRealm.isCloudRealm(accessor.getLevel())) {
			return false;
		}
		return random.nextInt(3) == 0 && level.getMaxLocalRawBrightness(pos) <= 7 && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())
				&& level.getEntitiesOfClass(Angel.class, new AABB(pos).inflate(SPACING)).isEmpty();
	}

	// ------------------------------------------------------------------------------------------------ floating

	@Override
	public void travel(Vec3 input) {
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(0.85));
	}

	@Override
	public void tick() {
		super.tick();
		this.fallDistance = 0;
		if (this.level().isClientSide() && this.random.nextInt(4) == 0) {
			this.level().addParticle(ModParticles.HOLY_SPARK, this.getRandomX(0.8), this.getY() + 0.4 + this.random.nextDouble() * 1.4,
					this.getRandomZ(0.8), 0.0, 0.01, 0.0);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.home == null) {
			this.home = this.position();
		}
		if (this.blessingCooldown > 0) {
			this.blessingCooldown--;
		}
		if (this.fleeing > 0) {
			this.fleeing--;
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.04, 0.0));
			return;
		}
		if (this.blessingCooldown == 0 && this.tickCount % 20 == 0) {
			for (ServerPlayer player : level.getPlayers(p -> !p.isSpectator() && p.getHealth() < p.getMaxHealth()
					&& p.distanceToSqr(this) < BLESSING_RANGE * BLESSING_RANGE)) {
				this.bless(level, player);
				break;
			}
		}
		// Drift about its home, now and then, rising and sinking a little.
		if (this.driftTarget == null || this.position().distanceToSqr(this.driftTarget) < 0.5 || this.random.nextInt(120) == 0) {
			Vec3 centre = this.home;
			Vec3 target = centre.add((this.random.nextDouble() - 0.5) * 8.0, (this.random.nextDouble() - 0.5) * 3.0,
					(this.random.nextDouble() - 0.5) * 8.0);
			this.driftTarget = level.isEmptyBlock(BlockPos.containing(target)) ? target : centre;
		}
		Vec3 to = this.driftTarget.subtract(this.position());
		double length = to.length();
		if (length > 1.0E-3) {
			Vec3 bob = new Vec3(0.0, Mth.sin(this.tickCount * 0.08F) * 0.01, 0.0);
			this.setDeltaMovement(this.getDeltaMovement().add(to.scale(0.006 / Math.max(length, 1.0))).add(bob));
			float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 3.0F));
			this.yBodyRot = this.getYRot();
		}
		Player watcher = level.getNearestPlayer(this, 10.0);
		if (watcher != null) {
			// It turns its head to whoever comes near.
			this.getLookControl().setLookAt(watcher, 30.0F, 30.0F);
		}
	}

	/** The blessing: the Blessed effect, a chime and a ring of golden light. */
	private void bless(ServerLevel level, Player player) {
		player.addEffect(new MobEffectInstance(ModEffects.BLESSED, 20 * 15, 0), this);
		level.sendParticles(ModParticles.HOLY_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.6F);
		this.blessingCooldown = BLESSING_COOLDOWN;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.level() instanceof ServerLevel level) {
			if (this.blessingCooldown == 0) {
				this.bless(level, player);
			} else {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.NEUTRAL, 0.6F, 0.8F);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			this.fleeing = 40;
		}
		return hurt;
	}

	// ------------------------------------------------------------------------------------------------ odds and ends

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.ALLAY_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ALLAY_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.8F;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}
}
