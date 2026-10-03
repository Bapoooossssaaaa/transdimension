package dev.goober.transdimension.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import dev.goober.transdimension.registry.ModEffects;
import dev.goober.transdimension.registry.ModItems;
import dev.goober.transdimension.registry.ModSounds;

/**
 * The Silly Cat: a big-eyed, tongue-out goober who wanders the Trans Realm, trots up to players
 * and gives them a big lick. A lick heals a little and leaves the player {@link ModEffects#SLOBBERED}
 * (which smears cat spit over their screen for a few seconds).
 *
 * <p>Licks are announced to clients through the synced {@link #LICK_TICKS} counter, which drives the
 * tongue animation in the renderer. Right-click a Silly Cat with an empty hand to pet it.
 */
public class SillyCat extends PathfinderMob {
	/** Ticks left in the current lick animation (counts down from {@link #LICK_DURATION}). */
	private static final EntityDataAccessor<Integer> LICK_TICKS = SynchedEntityData.defineId(SillyCat.class, EntityDataSerializers.INT);
	/** Some cats keep the tip of their tongue out all the time ("blep"). */
	private static final EntityDataAccessor<Boolean> BLEP = SynchedEntityData.defineId(SillyCat.class, EntityDataSerializers.BOOLEAN);

	public static final int LICK_DURATION = 16;
	private static final float LICK_HEAL = 3.0F;
	private static final int SLOBBER_TICKS = 20 * 8;

	/** Ticks until this cat may lick someone again. */
	private int lickCooldown;
	private boolean blepRolled;

	public SillyCat(EntityType<? extends SillyCat> entityType, Level level) {
		super(entityType, level);
		this.lickCooldown = 100 + this.random.nextInt(200);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 10.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.TEMPT_RANGE, 10.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.5));
		this.goalSelector.addGoal(2, new LickPlayerGoal(this));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1,
				Ingredient.of(Items.COD, Items.SALMON, ModItems.TRANS_COOKIE, ModItems.TRANS_DONUT), false));
		this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(LICK_TICKS, 0);
		builder.define(BLEP, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		if (!this.blepRolled) {
			this.blepRolled = true;
			this.entityData.set(BLEP, this.random.nextInt(3) == 0);
		}
		if (this.lickCooldown > 0) {
			this.lickCooldown--;
		}
		int lickTicks = this.entityData.get(LICK_TICKS);
		if (lickTicks > 0) {
			this.entityData.set(LICK_TICKS, lickTicks - 1);
		}
	}

	/** 0 when the tongue is in, 1 at full stretch; smooth between ticks on the client. */
	public float getLickProgress(float partialTick) {
		int ticks = this.entityData.get(LICK_TICKS);
		if (ticks <= 0) {
			return 0.0F;
		}
		float elapsed = (LICK_DURATION - ticks) + partialTick;
		return (float) Math.sin(Math.PI * Math.min(1.0F, Math.max(0.0F, elapsed / LICK_DURATION)));
	}

	public boolean isBlepping() {
		return this.entityData.get(BLEP);
	}

	boolean canLick(Player player) {
		return this.lickCooldown <= 0 && player.isAlive() && !player.isSpectator() && !player.hasEffect(ModEffects.SLOBBERED);
	}

	/** The big moment: heal, slobber, purr, hearts. Server side only. */
	void lick(Player player) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		this.entityData.set(LICK_TICKS, LICK_DURATION);
		this.lickCooldown = 20 * 20 + this.random.nextInt(20 * 20);

		player.heal(LICK_HEAL);
		player.addEffect(new MobEffectInstance(ModEffects.SLOBBERED, SLOBBER_TICKS, 0, false, false, true));

		this.playSound(ModSounds.SILLY_CAT_LICK, 1.0F, 0.9F + this.random.nextFloat() * 0.3F);
		this.playSound(ModSounds.SILLY_CAT_PURR, 0.8F, 1.0F);
		serverLevel.sendParticles(ParticleTypes.HEART, player.getX(), player.getEyeY() + 0.3, player.getZ(), 5, 0.4, 0.3, 0.4, 0.05);
		serverLevel.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getEyeY() - 0.2, player.getZ(), 18, 0.25, 0.2, 0.25, 0.1);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player.getItemInHand(hand).isEmpty()) {
			// Petting! Purrs, hearts and a happy little shuffle.
			if (this.level() instanceof ServerLevel serverLevel) {
				this.playSound(ModSounds.SILLY_CAT_PURR, 1.0F, 1.0F);
				serverLevel.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.8, this.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
				this.lickCooldown = Math.min(this.lickCooldown, 40);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false; // they are pets at heart; never despawn
	}

	@Override
	@Nullable
	protected SoundEvent getAmbientSound() {
		return this.random.nextInt(4) == 0 ? ModSounds.SILLY_CAT_PURR : ModSounds.SILLY_CAT_AMBIENT;
	}

	@Override
	@Nullable
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return ModSounds.SILLY_CAT_HURT;
	}

	@Override
	@Nullable
	protected SoundEvent getDeathSound() {
		return ModSounds.SILLY_CAT_DEATH;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putInt("lick_cooldown", this.lickCooldown);
		valueOutput.putBoolean("blep", this.isBlepping());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.lickCooldown = valueInput.getInt("lick_cooldown").orElse(200);
		this.entityData.set(BLEP, valueInput.getBooleanOr("blep", false));
		this.blepRolled = true;
	}

	/**
	 * Finds a nearby player who hasn't been licked lately, trots over and licks them.
	 */
	static class LickPlayerGoal extends Goal {
		private static final double SEARCH_RANGE = 12.0;
		private static final double LICK_RANGE_SQR = 2.2 * 2.2;

		private final SillyCat cat;
		@Nullable
		private Player target;
		private int timeout;

		LickPlayerGoal(SillyCat cat) {
			this.cat = cat;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (this.cat.lickCooldown > 0) {
				return false;
			}
			Player nearest = this.cat.level().getNearestPlayer(this.cat, SEARCH_RANGE);
			if (nearest == null || !this.cat.canLick(nearest)) {
				return false;
			}
			this.target = nearest;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return this.target != null && this.timeout > 0 && this.cat.canLick(this.target)
					&& this.cat.distanceToSqr(this.target) < SEARCH_RANGE * SEARCH_RANGE * 1.5;
		}

		@Override
		public void start() {
			this.timeout = 20 * 10;
		}

		@Override
		public void stop() {
			this.target = null;
			this.cat.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Player player = this.target;
			if (player == null) {
				return;
			}
			this.timeout--;
			this.cat.getLookControl().setLookAt(player, 30.0F, 30.0F);
			if (this.cat.distanceToSqr(player) <= LICK_RANGE_SQR) {
				this.cat.getNavigation().stop();
				this.cat.lick(player);
				this.target = null;
			} else if (this.timeout % 10 == 0) {
				this.cat.getNavigation().moveTo(player, 1.15);
			}
		}
	}
}
