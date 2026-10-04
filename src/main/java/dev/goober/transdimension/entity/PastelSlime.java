package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModItems;

/**
 * A pastel slime: a soft, friendly cube (pink or blue) with a sleepy little face, who lives in the Gumdrop Glade. It
 * never hurts anyone. It doesn't walk, it bounces: it sits still between hops, squashing when it lands and stretching
 * when it takes off, and every hop carries it towards wherever it wants to go.
 *
 * <p>Feed it Gumdrops to tame it (one in three tries); a tamed slime wears a bow, follows you around and sits when you
 * use it with an empty hand. Two tamed slimes fed Gumdrops have a baby, pink or blue like one of its parents.
 */
public class PastelSlime extends TamableAnimal {
	public static final int PINK = 0;
	public static final int BLUE = 1;
	/** Dust colours for the little splash when a slime lands, by variant. */
	private static final int[] SPLASH_COLOURS = {0xF5A9B8, 0x7FD0F8};
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(PastelSlime.class, EntityDataSerializers.INT);
	/** Air time of a hop, in ticks (vanilla jump strength); a hop's push is sized so it lands where it was heading. */
	private static final float HOP_AIR_TICKS = 7.0F;

	/** Ticks the slime rests on the ground before its next hop. */
	private int hopCooldown;
	/** The tick of the last hop: the slime may only slide along the ground on the tick it takes off. */
	private int lastHopTick = -1;
	private boolean variantRolled;

	// Squash and stretch, like vanilla slimes (both sides track it; the client draws it): -0.5 flat .. 1 tall.
	public float squish;
	public float oSquish;
	private float targetSquish;
	private boolean wasOnGround;

	public PastelSlime(EntityType<? extends TamableAnimal> entityType, Level level) {
		super(entityType, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return TamableAnimal.createAnimalAttributes()
				.add(Attributes.MAX_HEALTH, 12.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.TEMPT_RANGE, 10.0);
	}

	@Override
	protected void registerGoals() {
		Ingredient gumdrop = Ingredient.of(ModItems.GUMDROP);
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(4, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(5, new TemptGoal(this, 1.1, gumdrop, false));
		this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.15, 8.0F, 2.5F));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANT, PINK);
	}

	public int getVariant() {
		return this.entityData.get(VARIANT);
	}

	public void setVariant(int variant) {
		this.entityData.set(VARIANT, variant == BLUE ? BLUE : PINK);
		this.variantRolled = true;
	}

	@Override
	public void tick() {
		super.tick();
		Level level = this.level();
		if (!level.isClientSide() && !this.variantRolled) {
			this.setVariant(this.random.nextBoolean() ? PINK : BLUE);
		}
		// Squash on landing, stretch on take-off, then ease back into a cube.
		this.oSquish = this.squish;
		this.squish += (this.targetSquish - this.squish) * 0.5F;
		boolean grounded = this.onGround();
		if (grounded && !this.wasOnGround) {
			this.targetSquish = -0.5F;
			if (level.isClientSide()) {
				int colour = SPLASH_COLOURS[this.getVariant()];
				float radius = this.getBbWidth() * 0.6F;
				for (int i = 0; i < 6; i++) {
					float angle = this.random.nextFloat() * Mth.TWO_PI;
					level.addParticle(new DustParticleOptions(colour, 0.9F), this.getX() + Mth.sin(angle) * radius, this.getY() + 0.05,
							this.getZ() + Mth.cos(angle) * radius, 0.0, 0.0, 0.0);
				}
			} else {
				this.playSound(SoundEvents.SLIME_SQUISH_SMALL, 0.35F, 1.4F + this.random.nextFloat() * 0.3F);
			}
		} else if (!grounded && this.wasOnGround) {
			this.targetSquish = 1.0F;
		}
		this.targetSquish *= 0.6F;
		this.wasOnGround = grounded;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide() || !this.onGround() || this.isInWater() || this.isOrderedToSit()) {
			return;
		}
		if (this.hopCooldown > 0) {
			this.hopCooldown--;
			return;
		}
		// Hop while going somewhere, and now and then just for joy.
		if (!this.getNavigation().isDone()) {
			this.getJumpControl().jump();
			this.hopCooldown = 3 + this.random.nextInt(4);
		} else if (this.random.nextInt(140) == 0) {
			this.getJumpControl().jump();
			this.hopCooldown = 20;
		}
	}

	@Override
	public void jumpFromGround() {
		super.jumpFromGround();
		this.lastHopTick = this.tickCount;
		this.playSound(SoundEvents.SLIME_JUMP_SMALL, 0.4F, 1.3F + this.random.nextFloat() * 0.3F);
		if (this.getNavigation().isDone()) {
			return;
		}
		// Push off towards where it's facing (the move control turns it towards the path), just far enough to land on
		// the path's end when that's close, so it doesn't bounce back and forth over its goal.
		float push = this.getSpeed() * 0.9F;
		Path path = this.getNavigation().getPath();
		Node end = path != null ? path.getEndNode() : null;
		if (end != null) {
			double dx = end.x + 0.5 - this.getX();
			double dz = end.z + 0.5 - this.getZ();
			push = Math.min(push, (float) Math.sqrt(dx * dx + dz * dz) / HOP_AIR_TICKS);
		}
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		Vec3 motion = this.getDeltaMovement();
		this.setDeltaMovement(-Mth.sin(yaw) * push, motion.y, Mth.cos(yaw) * push);
	}

	@Override
	public void travel(Vec3 input) {
		// No walking: between hops the slime sits still on the ground (it still swims and rides normally).
		if (this.onGround() && !this.isInWater() && this.tickCount != this.lastHopTick) {
			input = Vec3.ZERO;
		}
		super.travel(input);
	}

	/** 0 = cube, -0.5 squashed, 1 stretched; smooth between ticks. */
	public float getSquish(float partialTick) {
		return this.oSquish + (this.squish - this.oSquish) * partialTick;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!this.isTame() && stack.is(ModItems.GUMDROP)) {
			if (!this.level().isClientSide()) {
				stack.consume(1, player);
				if (this.random.nextInt(3) == 0) {
					this.tame(player);
					this.getNavigation().stop();
					this.setOrderedToSit(true);
					this.level().broadcastEntityEvent(this, (byte) 7); // hearts
				} else {
					this.level().broadcastEntityEvent(this, (byte) 6); // smoke
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (this.isTame() && this.isOwnedBy(player) && stack.isEmpty()) {
			if (!this.level().isClientSide()) {
				this.setOrderedToSit(!this.isOrderedToSit());
				this.getNavigation().stop();
				if (this.level() instanceof ServerLevel serverLevel) {
					serverLevel.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.9, this.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(ModItems.GUMDROP);
	}

	@Override
	@Nullable
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		PastelSlime baby = ModEntities.PASTEL_SLIME.create(level, EntitySpawnReason.BREEDING);
		if (baby != null) {
			int variant = partner instanceof PastelSlime other && this.random.nextBoolean() ? other.getVariant() : this.getVariant();
			baby.setVariant(variant);
			if (this.getOwner() instanceof Player owner) {
				baby.tame(owner);
			}
		}
		return baby;
	}

	@Override
	@Nullable
	protected SoundEvent getAmbientSound() {
		return this.random.nextInt(3) == 0 ? SoundEvents.SLIME_SQUISH_SMALL : null;
	}

	@Override
	@Nullable
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.SLIME_HURT_SMALL;
	}

	@Override
	@Nullable
	protected SoundEvent getDeathSound() {
		return SoundEvents.SLIME_DEATH_SMALL;
	}

	@Override
	protected float getSoundVolume() {
		return 0.6F;
	}

	@Override
	public float getVoicePitch() {
		return 1.4F + (this.random.nextFloat() - 0.5F) * 0.2F;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putInt("variant", this.getVariant());
	}

	@Override
	public void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.setVariant(valueInput.getIntOr("variant", PINK));
	}
}
