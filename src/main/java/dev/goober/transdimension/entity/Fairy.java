package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.registry.ModItems;

/**
 * A wild fairy: a little glowing cube with fluttering wings, like the light in a Fairy Jar, that drifts about the Trans
 * Realm and the Fairy Realm. Each one glows a single colour (blue, pink or white), and they're rare: there's never
 * more than one within {@link #SPACING} blocks.
 *
 * <p>Fairies are gentle and a bit curious: they bob about a few blocks above the ground and now and then come to hover
 * round a nearby player, but dart away from anyone sprinting at them. Use one with an empty glass bottle to catch it
 * (a {@code Bottled Fairy} saves you from death once, like a totem, if you're holding it); use it with anything else and
 * it leaves you a gift and vanishes in a burst of sparkles.
 */
public class Fairy extends PathfinderMob {
	public static final int BLUE = 0;
	public static final int PINK = 1;
	public static final int WHITE = 2;
	/** Glow colours by {@link #getColour() colour}. */
	public static final int[] COLOURS = {0x5BCEFA, 0xF5A9B8, 0xFFFFFF};
	/** Fairies keep at least this far apart, which keeps them rare. */
	public static final int SPACING = 48;
	private static final EntityDataAccessor<Integer> COLOUR = SynchedEntityData.defineId(Fairy.class, EntityDataSerializers.INT);

	private boolean colourRolled;
	@Nullable
	private Vec3 wanderTarget;

	public Fairy(EntityType<? extends Fairy> entityType, Level level) {
		super(entityType, level);
		this.setNoGravity(true);
		this.xpReward = 3;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 4.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.FLYING_SPEED, 0.3);
	}

	/** Natural spawns: one try in fifteen succeeds, and only with no other fairy nearby. */
	public static boolean checkFairySpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		return random.nextInt(15) == 0 && Mob.checkMobSpawnRules(type, level, reason, pos, random)
				&& level.getEntitiesOfClass(Fairy.class, new AABB(pos).inflate(SPACING)).isEmpty();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(COLOUR, BLUE);
	}

	public int getColour() {
		return this.entityData.get(COLOUR);
	}

	public void setColour(int colour) {
		this.entityData.set(COLOUR, colour >= 0 && colour < COLOURS.length ? colour : BLUE);
		this.colourRolled = true;
	}

	// ------------------------------------------------------------------------------------------------ flight

	@Override
	public void travel(Vec3 input) {
		// Fairies fly by setting their own velocity (see customServerAiStep); here it's just applied, with air drag.
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(0.86));
	}

	@Override
	public void tick() {
		super.tick();
		this.fallDistance = 0;
		Level level = this.level();
		if (!level.isClientSide() && !this.colourRolled) {
			this.setColour(this.random.nextInt(COLOURS.length));
		}
		if (level.isClientSide() && this.random.nextInt(3) == 0) {
			// A trail of glitter in its colour.
			level.addParticle(new DustParticleOptions(COLOURS[this.getColour()], 0.55F), this.getRandomX(0.5), this.getY() + 0.1,
					this.getRandomZ(0.5), 0.0, -0.02, 0.0);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		Player near = level.getNearestPlayer(this, 7.0);
		if (near != null && near.isSprinting()) {
			// Startled: dart up and away.
			Vec3 away = this.position().subtract(near.position()).multiply(1.0, 0.0, 1.0);
			Vec3 escape = this.position().add(away.lengthSqr() > 1.0E-4 ? away.normalize().scale(6.0) : Vec3.ZERO).add(0.0, 2.0, 0.0);
			this.flyTowards(escape, 0.4, 0.25);
			this.wanderTarget = null;
		} else {
			if (this.wanderTarget == null || this.position().distanceToSqr(this.wanderTarget) < 0.8 || this.random.nextInt(90) == 0) {
				this.wanderTarget = this.pickWanderTarget(level, near);
			}
			if (this.wanderTarget != null) {
				// Bob gently on the way.
				Vec3 bob = new Vec3(0.0, Mth.sin(this.tickCount * 0.15F) * 0.4, 0.0);
				this.flyTowards(this.wanderTarget.add(bob), 0.12, 0.08);
			}
		}
		Vec3 motion = this.getDeltaMovement();
		if (motion.horizontalDistanceSqr() > 1.0E-4) {
			float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(Mth.rotLerp(0.3F, this.getYRot(), yaw));
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
		}
	}

	/** Somewhere to drift to: near itself, or now and then round a nearby player; always in the air, close to the ground. */
	@Nullable
	private Vec3 pickWanderTarget(ServerLevel level, @Nullable Player near) {
		Vec3 centre = near != null && this.random.nextInt(3) == 0 ? near.position().add(0.0, 1.4, 0.0) : this.position();
		for (int attempt = 0; attempt < 10; attempt++) {
			double x = centre.x + (this.random.nextDouble() - 0.5) * 10.0;
			double y = centre.y + (this.random.nextDouble() - 0.5) * 4.0;
			double z = centre.z + (this.random.nextDouble() - 0.5) * 10.0;
			BlockPos at = BlockPos.containing(x, y, z);
			if (!level.isEmptyBlock(at)) {
				continue;
			}
			// Stay within a few blocks of whatever is below.
			int gap = 0;
			while (gap < 6 && level.isEmptyBlock(at.below(gap + 1))) {
				gap++;
			}
			if (gap >= 6) {
				y -= 3.0;
				if (!level.isEmptyBlock(BlockPos.containing(x, y, z))) {
					continue;
				}
			}
			return new Vec3(x, y, z);
		}
		return null;
	}

	private void flyTowards(Vec3 point, double speed, double responsiveness) {
		Vec3 delta = point.subtract(this.position());
		Vec3 wanted = delta.lengthSqr() > speed * speed ? delta.normalize().scale(speed) : delta;
		this.setDeltaMovement(this.getDeltaMovement().lerp(wanted, responsiveness));
	}

	// ------------------------------------------------------------------------------------------------ meeting one

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (this.level() instanceof ServerLevel level) {
			if (stack.is(Items.GLASS_BOTTLE)) {
				stack.consume(1, player);
				ItemStack bottled = new ItemStack(ModItems.BOTTLED_FAIRY);
				if (!player.getInventory().add(bottled)) {
					this.spawnAtLocation(level, bottled);
				}
				this.vanish(level, SoundEvents.BOTTLE_FILL);
			} else {
				this.spawnAtLocation(level, this.rollGift());
				this.vanish(level, SoundEvents.AMETHYST_BLOCK_CHIME);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** A fairy's thank-you: usually crystals, pearls or sweets, sometimes something special. */
	private ItemStack rollGift() {
		int roll = this.random.nextInt(100);
		if (roll < 35) {
			return new ItemStack(ModItems.TRANS_CRYSTAL, 1 + this.random.nextInt(2));
		} else if (roll < 55) {
			return new ItemStack(ModItems.TRANS_PEARL, 1 + this.random.nextInt(2));
		} else if (roll < 70) {
			return new ItemStack(ModItems.GUMDROP, 3 + this.random.nextInt(3));
		} else if (roll < 85) {
			return new ItemStack(Items.GOLDEN_APPLE);
		} else if (roll < 95) {
			return new ItemStack(ModItems.BOTTLED_FAIRY);
		}
		return new ItemStack(ModItems.TRANS_CRYSTAL_PEARL);
	}

	private void vanish(ServerLevel level, SoundEvent sound) {
		int colour = COLOURS[this.getColour()];
		level.sendParticles(new DustParticleOptions(colour, 1.0F), this.getX(), this.getY() + 0.2, this.getZ(), 24, 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.2, this.getZ(), 10, 0.15, 0.15, 0.15, 0.08);
		this.playSound(sound, 1.0F, 1.4F);
		this.discard();
	}

	// ------------------------------------------------------------------------------------------------ odds and ends

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	@Nullable
	protected SoundEvent getAmbientSound() {
		return this.random.nextInt(3) == 0 ? SoundEvents.AMETHYST_BLOCK_CHIME : null;
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
		return 1.5F + (this.random.nextFloat() - 0.5F) * 0.2F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putInt("colour", this.getColour());
	}

	@Override
	public void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		valueInput.getInt("colour").ifPresent(this::setColour);
	}
}
