package dev.goober.transdimension.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.world.FairyRealm;

/**
 * The Trans Fairy, guardian of the Fairy Realm: a flying boss (320 health) who circles her arena on glittering wings
 * and fights with her star wand.
 *
 * <p>Between attacks she hovers in a slow orbit around her home (the arena altar), facing her target. Her attacks:
 * <ul>
 *   <li><b>Wand volley</b>: bursts of sparkling hearts (the Trans Wand's bolt), three per burst, five later on.</li>
 *   <li><b>Swoop</b>: she rises, then dives straight through where you stood, hitting anything in her way.</li>
 *   <li><b>Crystal spikes</b>: ice crystals burst out of the ground ({@link FairyCrystalSpike}) in a line towards you,
 *       later in a ring around you, and at the end scattered all about.</li>
 *   <li><b>Summon</b> (from two thirds health): trans endermen appear, angry at you.</li>
 *   <li><b>Starfall</b> (below one third): hearts rain down around you.</li>
 * </ul>
 * Crossing into a new phase sets off a burst of light. With no one to fight for half a minute she slowly heals. Beating
 * her opens the portal home (see {@link FairyRealm#onFairyDefeated}) and drops the Fairy Jar.
 *
 * <p>The current action is synced to clients ({@link #ACTION}) so the model can pose her: wand raised for a volley,
 * diving for a swoop, wand down for the spikes, arms up to summon.
 *
 * <p>The first time she's called she arrives in the middle of a cutscene ({@code FairyCutscene}): while {@link #INTRO}
 * is set she only hovers and watches, can't be hurt, and fires the one shot the scene asks for ({@link #castAt}). Her
 * health bar is the client's own trans bar ({@code TransFairyBossBar}), drawn from her synced health once the intro is
 * over, so she has no vanilla boss bar.
 */
public class TransFairy extends Monster {
	public static final int HOVER = 0;
	public static final int VOLLEY = 1;
	public static final int SWOOP = 2;
	public static final int SPIKES = 3;
	public static final int SUMMON = 4;
	public static final int STARFALL = 5;
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(TransFairy.class, EntityDataSerializers.INT);
	/** True during the cutscene that brings her in (not saved: after a reload she just fights). */
	private static final EntityDataAccessor<Boolean> INTRO = SynchedEntityData.defineId(TransFairy.class, EntityDataSerializers.BOOLEAN);
	private static final int[] SPARKLES = {0xF5A9B8, 0x5BCEFA, 0xFFFFFF};
	private static final int MAX_MINIONS = 4;

	/** The middle of her arena; she orbits it. Set when she's called, or to wherever she first appears. */
	@Nullable
	private BlockPos home;
	private int actionTicks;
	private int restTicks = 60;
	private int lastAction = HOVER;
	private int phase = 1;
	private int idleTicks;
	private float orbitAngle;
	@Nullable
	private Vec3 swoopTarget;
	private final Set<Integer> swoopHits = new HashSet<>();
	private final List<Entity> minions = new ArrayList<>();
	/** Client side: the tick the current action started, for the model's poses. */
	private int clientActionStart;
	/** What she watches during the cutscene. */
	@Nullable
	private Vec3 introLook;

	public TransFairy(EntityType<? extends Monster> entityType, Level level) {
		super(entityType, level);
		this.setNoGravity(true);
		this.xpReward = 300;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 320.0)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.ATTACK_DAMAGE, 9.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ACTION, HOVER);
		builder.define(INTRO, false);
	}

	/** True while the cutscene that brings her in is still playing (her health bar stays hidden till it ends). */
	public boolean isIntro() {
		return this.entityData.get(INTRO);
	}

	public void setIntro(boolean intro) {
		this.entityData.set(INTRO, intro);
	}

	/** During the cutscene she turns to watch this point. */
	public void lookAtDuringIntro(Vec3 point) {
		this.introLook = point;
	}

	/** A single spell from her wand at {@code target} (the cutscene's shot at Maddie). */
	public void castAt(LivingEntity target) {
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.face(target.getEyePosition());
		Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(0.8));
		Vec3 aim = target.getBoundingBox().getCenter().subtract(from);
		TransMagicBolt bolt = new TransMagicBolt(level, this, ItemStack.EMPTY);
		bolt.setPos(from.x, from.y, from.z);
		bolt.shoot(aim.x, aim.y, aim.z, 1.1F, 0.0F);
		level.addFreshEntity(bolt);
		this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 1.0F);
		this.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.5F, 1.4F);
	}

	public int getAction() {
		return this.entityData.get(ACTION);
	}

	/** Client side: ticks (with the partial tick) since the current action began. */
	public float getActionTime(float partialTick) {
		return this.tickCount - this.clientActionStart + partialTick;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (ACTION.equals(accessor)) {
			this.clientActionStart = this.tickCount;
		}
	}

	public void setHome(BlockPos home) {
		this.home = home;
	}

	// ------------------------------------------------------------------------------------------------ flight

	@Override
	public void travel(Vec3 input) {
		// She flies by setting her own velocity (see customServerAiStep); here it's just applied, with air drag.
		this.move(MoverType.SELF, this.getDeltaMovement());
		this.setDeltaMovement(this.getDeltaMovement().scale(0.88));
	}

	@Override
	public void tick() {
		super.tick();
		this.fallDistance = 0;
		Level level = this.level();
		if (level.isClientSide() && this.tickCount % 2 == 0) {
			// Glitter falls from her wings.
			int colour = SPARKLES[this.random.nextInt(SPARKLES.length)];
			level.addParticle(new DustParticleOptions(colour, 0.8F), this.getRandomX(1.2), this.getY() + 1.2 + this.random.nextDouble() * 0.8,
					this.getRandomZ(1.2), 0.0, -0.03, 0.0);
		}
	}

	private void flyTowards(Vec3 point, double speed, double responsiveness) {
		Vec3 delta = point.subtract(this.position());
		Vec3 wanted = delta.lengthSqr() > speed * speed ? delta.normalize().scale(speed) : delta;
		this.setDeltaMovement(this.getDeltaMovement().lerp(wanted, responsiveness));
	}

	private void face(Vec3 point) {
		double dx = point.x - this.getX();
		double dz = point.z - this.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	// ------------------------------------------------------------------------------------------------ the fight

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.home == null) {
			this.home = this.blockPosition();
		}
		if (this.isIntro()) {
			// The cutscene: she hangs in the air over the altar, watching, until it hands her the fight.
			this.setTarget(null);
			this.setDeltaMovement(this.getDeltaMovement().scale(0.7));
			if (this.introLook != null) {
				this.face(this.introLook);
			}
			return;
		}
		this.minions.removeIf(minion -> !minion.isAlive());

		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || target instanceof Player player && (player.isCreative() || player.isSpectator()))) {
			this.setTarget(null);
			target = null;
		}
		if (target == null) {
			// Nobody to fight: drift back to the middle of the arena and, after a while, mend.
			if (++this.idleTicks > 600 && this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
				this.heal(4.0F);
			}
			if (this.getAction() != HOVER) {
				this.setAction(HOVER);
			}
		} else {
			this.idleTicks = 0;
		}
		this.updatePhase(level);

		this.actionTicks++;
		switch (this.getAction()) {
			case VOLLEY -> this.tickVolley(level, target);
			case SWOOP -> this.tickSwoop(level, target);
			case SPIKES -> this.tickSpikes(level, target);
			case SUMMON -> this.tickSummon(level, target);
			case STARFALL -> this.tickStarfall(level, target);
			default -> this.tickHover(target);
		}
	}

	private void setAction(int action) {
		this.entityData.set(ACTION, action);
		this.actionTicks = 0;
		if (action != HOVER) {
			this.lastAction = action;
		}
	}

	/** Back to hovering for a while: shorter rests as she gets desperate. */
	private void rest() {
		this.setAction(HOVER);
		this.restTicks = switch (this.phase) {
			case 1 -> 45 + this.random.nextInt(25);
			case 2 -> 32 + this.random.nextInt(20);
			default -> 22 + this.random.nextInt(16);
		};
	}

	private void updatePhase(ServerLevel level) {
		float health = this.getHealth() / this.getMaxHealth();
		int newPhase = health > 0.66F ? 1 : health > 0.33F ? 2 : 3;
		if (newPhase > this.phase) {
			this.phase = newPhase;
			// A burst of light that shoves everyone back a little, then she calls for help.
			FairyRealm.sparkle(level, this.position().add(0.0, 1.0, 0.0), 120, 2.5);
			this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.7F);
			this.playSound(SoundEvents.ALLAY_HURT, 2.0F, 0.6F);
			for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(8.0))) {
				Vec3 push = player.position().subtract(this.position()).normalize().scale(1.2);
				player.push(push.x, 0.5, push.z);
				player.hurtMarked = true;
			}
			this.setAction(SUMMON);
		}
	}

	private void tickHover(@Nullable LivingEntity target) {
		Vec3 centre = Vec3.atBottomCenterOf(this.home != null ? this.home : this.blockPosition());
		this.orbitAngle += 0.012F + 0.005F * this.phase;
		double radius = 8.0 + 1.5 * Mth.sin(this.tickCount * 0.02F);
		double height = 6.0 + Mth.sin(this.tickCount * 0.05F) * 0.8;
		Vec3 point = centre.add(Mth.cos(this.orbitAngle) * radius, height, Mth.sin(this.orbitAngle) * radius);
		this.flyTowards(point, 0.35, 0.12);
		if (target != null) {
			this.face(target.getEyePosition());
			if (this.actionTicks >= this.restTicks) {
				this.setAction(this.chooseAction());
			}
		} else {
			this.face(point.add(this.getDeltaMovement().scale(10.0)));
		}
	}

	private int chooseAction() {
		int volley = this.phase == 1 ? 45 : this.phase == 2 ? 30 : 20;
		int swoop = this.phase == 1 ? 30 : this.phase == 2 ? 25 : 20;
		int spikes = this.phase == 1 ? 25 : this.phase == 2 ? 30 : 25;
		int summon = this.phase >= 2 && this.minions.size() < 2 ? (this.phase == 2 ? 15 : 10) : 0;
		int starfall = this.phase == 3 ? 25 : 0;
		for (int attempt = 0; attempt < 2; attempt++) {
			int roll = this.random.nextInt(volley + swoop + spikes + summon + starfall);
			int action;
			if ((roll -= volley) < 0) {
				action = VOLLEY;
			} else if ((roll -= swoop) < 0) {
				action = SWOOP;
			} else if ((roll -= spikes) < 0) {
				action = SPIKES;
			} else if ((roll -= summon) < 0) {
				action = SUMMON;
			} else {
				action = STARFALL;
			}
			if (action != this.lastAction) {
				return action;
			}
		}
		return VOLLEY;
	}

	/** Wand raised, she fires three bursts of sparkling hearts. */
	private void tickVolley(ServerLevel level, @Nullable LivingEntity target) {
		this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
		if (target == null) {
			this.rest();
			return;
		}
		this.face(target.getEyePosition());
		if (this.actionTicks == 4) {
			this.playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.5F, 1.6F);
		}
		if (this.actionTicks == 14 || this.actionTicks == 22 || this.actionTicks == 30) {
			int bolts = this.phase == 3 ? 5 : 3;
			Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(0.8));
			Vec3 aim = target.getEyePosition().subtract(0.0, 0.3, 0.0).subtract(from);
			float spread = 11.0F;
			for (int i = 0; i < bolts; i++) {
				float yawOffset = (i - (bolts - 1) / 2.0F) * spread * Mth.DEG_TO_RAD;
				Vec3 dir = aim.yRot(yawOffset);
				TransMagicBolt bolt = new TransMagicBolt(level, this, ItemStack.EMPTY);
				bolt.setPos(from.x, from.y, from.z);
				bolt.shoot(dir.x, dir.y, dir.z, 1.1F, 1.5F);
				level.addFreshEntity(bolt);
			}
			this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 1.2F + this.random.nextFloat() * 0.4F);
			this.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.2F, 1.6F);
		}
		if (this.actionTicks >= 40) {
			this.rest();
		}
	}

	/** She climbs, then dives through where her target was standing. */
	private void tickSwoop(ServerLevel level, @Nullable LivingEntity target) {
		if (this.actionTicks < 14) {
			// Wind up: rise and glare.
			this.setDeltaMovement(this.getDeltaMovement().scale(0.7).add(0.0, 0.06, 0.0));
			if (target != null) {
				this.face(target.getEyePosition());
			}
			if (this.actionTicks == 1) {
				this.playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 1.5F);
			}
			return;
		}
		if (this.actionTicks == 14) {
			if (target == null) {
				this.rest();
				return;
			}
			this.swoopTarget = target.position().add(0.0, 0.8, 0.0);
			this.swoopHits.clear();
			this.playSound(SoundEvents.PHANTOM_FLAP, 2.0F, 1.4F);
		}
		if (this.swoopTarget != null && this.actionTicks < 38) {
			Vec3 toTarget = this.swoopTarget.subtract(this.position());
			if (toTarget.lengthSqr() < 1.5 || this.horizontalCollision) {
				this.swoopTarget = null; // overshot or hit something: pull up
			} else {
				this.face(this.swoopTarget);
				this.setDeltaMovement(toTarget.normalize().scale(1.05));
			}
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.8),
					entity -> entity != this && !(entity instanceof TransFairy) && !this.minions.contains(entity))) {
				if (this.swoopHits.add(hit.getId())) {
					hit.hurtServer(level, this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
					this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 1.3F);
				}
			}
			return;
		}
		// Recover: climb back up.
		this.setDeltaMovement(this.getDeltaMovement().scale(0.85).add(0.0, 0.05, 0.0));
		if (this.actionTicks >= 50) {
			this.rest();
		}
	}

	/** Wand pointed at the ground: ice crystals burst up under her target. */
	private void tickSpikes(ServerLevel level, @Nullable LivingEntity target) {
		this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
		if (target == null) {
			this.rest();
			return;
		}
		this.face(target.getEyePosition());
		if (this.actionTicks == 2) {
			this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.5F, 1.4F);
		}
		if (this.actionTicks == 14) {
			this.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.5F, 1.5F);
			Vec3 at = target.position();
			switch (this.phase) {
				case 1 -> this.spikeLine(level, at);
				case 2 -> {
					this.spikeRing(level, at, 2.6, 9);
					this.spawnSpike(level, at.x, at.z, at.y, 4);
				}
				default -> {
					this.spikeRing(level, at, 2.4, 9);
					for (int i = 0; i < 10; i++) {
						double angle = this.random.nextDouble() * Mth.TWO_PI;
						double distance = 3.5 + this.random.nextDouble() * 3.5;
						this.spawnSpike(level, at.x + Math.cos(angle) * distance, at.z + Math.sin(angle) * distance, at.y, 6 + this.random.nextInt(14));
					}
				}
			}
		}
		if (this.actionTicks >= 36) {
			this.rest();
		}
	}

	/** A line of spikes racing from under her towards (and past) the target. */
	private void spikeLine(ServerLevel level, Vec3 at) {
		Vec3 start = this.position();
		Vec3 dir = new Vec3(at.x - start.x, 0.0, at.z - start.z);
		if (dir.lengthSqr() < 1.0E-4) {
			dir = new Vec3(1.0, 0.0, 0.0);
		}
		dir = dir.normalize();
		double length = Math.min(18.0, Math.sqrt((at.x - start.x) * (at.x - start.x) + (at.z - start.z) * (at.z - start.z)) + 4.0);
		int count = (int) (length / 1.25);
		for (int i = 1; i <= count; i++) {
			double d = i * 1.25;
			this.spawnSpike(level, start.x + dir.x * d, start.z + dir.z * d, at.y, i);
		}
	}

	private void spikeRing(ServerLevel level, Vec3 at, double radius, int count) {
		double offset = this.random.nextDouble() * Mth.TWO_PI;
		for (int i = 0; i < count; i++) {
			double angle = offset + i * Mth.TWO_PI / count;
			this.spawnSpike(level, at.x + Math.cos(angle) * radius, at.z + Math.sin(angle) * radius, at.y, 2);
		}
	}

	/** One spike on the first solid floor at or below a little above {@code nearY}. */
	private void spawnSpike(ServerLevel level, double x, double z, double nearY, int warmup) {
		BlockPos column = BlockPos.containing(x, nearY + 2.0, z);
		for (int i = 0; i < 8; i++) {
			BlockPos below = column.below();
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getBlockState(column).getCollisionShape(level, column).isEmpty()) {
				level.addFreshEntity(new FairyCrystalSpike(level, x, column.getY(), z, this.random.nextFloat() * 360.0F, warmup, this));
				return;
			}
			column = below;
		}
	}

	/** Arms up: trans endermen appear around the arena, angry at her target. */
	private void tickSummon(ServerLevel level, @Nullable LivingEntity target) {
		this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
		if (this.actionTicks == 2) {
			this.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.8F, 1.3F);
		}
		if (this.actionTicks % 4 == 0 && this.actionTicks < 24) {
			FairyRealm.sparkle(level, this.position().add(0.0, 2.6, 0.0), 12, 0.4);
		}
		if (this.actionTicks == 24) {
			int count = Math.min(this.phase == 3 ? 3 : 2, MAX_MINIONS - this.minions.size());
			BlockPos centre = this.home != null ? this.home : this.blockPosition();
			for (int i = 0; i < count; i++) {
				double angle = this.random.nextDouble() * Mth.TWO_PI;
				double distance = 4.0 + this.random.nextDouble() * 5.0;
				BlockPos spot = BlockPos.containing(centre.getX() + 0.5 + Math.cos(angle) * distance, centre.getY() + 1,
						centre.getZ() + 0.5 + Math.sin(angle) * distance);
				TransEnderman minion = ModEntities.TRANS_ENDERMAN.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (minion == null) {
					continue;
				}
				minion.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, this.random.nextFloat() * 360.0F, 0.0F);
				if (target != null) {
					minion.setTarget(target);
				}
				level.addFreshEntity(minion);
				this.minions.add(minion);
				FairyRealm.sparkle(level, minion.position().add(0.0, 1.4, 0.0), 30, 0.6);
			}
			this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.5F, 1.4F);
		}
		if (this.actionTicks >= 40) {
			this.rest();
		}
	}

	/** Hearts rain down from the sky around her target. */
	private void tickStarfall(ServerLevel level, @Nullable LivingEntity target) {
		this.setDeltaMovement(this.getDeltaMovement().scale(0.8).add(0.0, 0.01, 0.0));
		if (target == null) {
			this.rest();
			return;
		}
		this.face(target.getEyePosition());
		if (this.actionTicks == 2) {
			this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 1.5F);
		}
		if (this.actionTicks >= 10 && this.actionTicks <= 46 && this.actionTicks % 3 == 0) {
			double angle = this.random.nextDouble() * Mth.TWO_PI;
			double distance = this.random.nextDouble() * 5.0;
			double x = target.getX() + Math.cos(angle) * distance;
			double z = target.getZ() + Math.sin(angle) * distance;
			TransMagicBolt star = new TransMagicBolt(level, this, ItemStack.EMPTY);
			star.setPos(x, target.getY() + 13.0, z);
			star.shoot(0.0, -1.0, 0.0, 0.9F, 0.0F);
			level.addFreshEntity(star);
		}
		if (this.actionTicks >= 56) {
			this.rest();
		}
	}

	// ------------------------------------------------------------------------------------------------ damage and death

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isIntro() || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.getEntity() == this
				|| this.minions.contains(source.getEntity())) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			for (Entity minion : this.minions) {
				FairyRealm.sparkle(level, minion.position().add(0.0, 1.4, 0.0), 20, 0.5);
				minion.discard();
			}
			this.minions.clear();
			FairyRealm.sparkle(level, this.position().add(0.0, 1.2, 0.0), 240, 2.0);
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.2, this.getZ(), 60, 0.2, 0.2, 0.2, 0.25);
			FairyRealm.onFairyDefeated(level, this);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	/**
	 * She's the story's boss, not a stray monster: Peaceful doesn't make her vanish (it did, the moment she arrived, so
	 * on Peaceful the cutscene played and no fairy ever came). On Peaceful her attacks can't hurt players anyway.
	 */
	@Override
	protected boolean shouldDespawnInPeaceful() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	// ------------------------------------------------------------------------------------------------ sounds and saving

	@Override
	@Nullable
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
	}

	@Override
	@Nullable
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.ALLAY_HURT;
	}

	@Override
	@Nullable
	protected SoundEvent getDeathSound() {
		return SoundEvents.ALLAY_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0F;
	}

	@Override
	public float getVoicePitch() {
		return 0.85F + (this.random.nextFloat() - 0.5F) * 0.1F;
	}

	@Override
	public void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		if (this.home != null) {
			valueOutput.putInt("home_x", this.home.getX());
			valueOutput.putInt("home_y", this.home.getY());
			valueOutput.putInt("home_z", this.home.getZ());
		}
		valueOutput.putInt("phase", this.phase);
	}

	@Override
	public void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		valueInput.getInt("home_x").ifPresent(x -> this.home = new BlockPos(x, valueInput.getIntOr("home_y", 0), valueInput.getIntOr("home_z", 0)));
		this.phase = valueInput.getIntOr("phase", 1);
	}
}
