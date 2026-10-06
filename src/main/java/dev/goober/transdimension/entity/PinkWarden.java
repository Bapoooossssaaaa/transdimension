package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModSounds;
import dev.goober.transdimension.world.PinkDeepDark;

/**
 * The pink deep dark's warden: vanilla's warden in every way (it hears vibrations, sniffs, roars, smashes and fires sonic
 * booms) except that it's on your side. It only ever hunts monsters, so it guards you from whatever spawns in the dark;
 * players can't hurt it; it doesn't dig back underground; and instead of darkness it gives the players round it night
 * vision. (Any darkness in the Trans Realm, a shrieker's too, turns into night vision: see {@link PinkDeepDark}.)
 *
 * <p>It's drawn by vanilla's warden renderer; in the realm TransRecolor swaps its textures for the pink ones in
 * {@code textures/entity/trans/warden/}. One guards the gate of every pink ancient city and stays there for good. Now
 * and then a wild one wanders the pink deep dark, but never more than two at a time, never close to another, and they
 * leave again when nobody's near. Where two pink wardens meet, one of them digs away.
 */
public class PinkWarden extends Warden {
	/** Natural spawns keep at least this far from any other pink warden... */
	public static final int SPACING = 96;
	/** ...and only this many wild ones roam a level at once (the cities' guardians don't count). */
	private static final int MAX_WILD = 2;
	/** A warden that finds another this close by burrows away, unless it's the one that stays (see {@link #yieldsTo}). */
	private static final double CROWD = 40.0;
	private static final double NIGHT_VISION_RANGE = 20.0;

	/** Set when another pink warden already keeps watch nearby: this one digs back into the ground and leaves. */
	private boolean leaving;

	public PinkWarden(EntityType<? extends Monster> entityType, Level level) {
		super(entityType, level);
		// Silent, so the client never plays its thudding heartbeat or tendril clicks; everything it does say goes
		// through playSound, softly.
		this.setSilent(true);
	}

	/**
	 * Every sound a warden makes (its roar, sniffs, sonic boom, steps, heartbeat-quick anger, hurt and death) comes out
	 * as a gentle chime or a mossy footstep instead ({@link ModSounds#soften}), and never louder than a cat. It plays
	 * even though the warden is silent.
	 */
	@Override
	public void playSound(SoundEvent sound, float volume, float pitch) {
		SoundEvent soft = ModSounds.soften(sound);
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), soft != null ? soft : sound, this.getSoundSource(),
				Math.min(volume, 1.0F) * 0.8F, pitch);
	}

	/**
	 * Natural spawns: one try in eight, never within {@link #SPACING} blocks of another pink warden, and never while
	 * {@link #MAX_WILD} wild ones are already about.
	 */
	public static boolean checkPinkWardenSpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		if (random.nextInt(8) != 0 || !Mob.checkMobSpawnRules(type, level, reason, pos, random)
				|| !level.getEntitiesOfClass(PinkWarden.class, new AABB(pos).inflate(SPACING)).isEmpty()) {
			return false;
		}
		if (level instanceof ServerLevelAccessor accessor) {
			int wild = accessor.getLevel().getEntities(ModEntities.PINK_WARDEN, warden -> !warden.isPersistenceRequired()).size();
			return wild < MAX_WILD;
		}
		return true;
	}

	/**
	 * Wild pink wardens wander off when nobody's near, like other monsters; a city's guardian (it comes with its city)
	 * and one from a spawn egg or a command stay.
	 */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			this.setPersistenceRequired();
		}
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return true;
	}

	/**
	 * Whether this warden leaves the watch to {@code other}: a guardian outranks a wild warden; between two of a kind, the
	 * one with the lower UUID stays (so both agree, whichever checks first).
	 */
	private boolean yieldsTo(PinkWarden other) {
		if (other.isPersistenceRequired() != this.isPersistenceRequired()) {
			return other.isPersistenceRequired();
		}
		return other.getUUID().compareTo(this.getUUID()) < 0;
	}

	/** Hunts monsters only: never players, the realm's folk or other wardens. */
	@Override
	public boolean canTargetEntity(@Nullable Entity entity) {
		return entity instanceof Enemy && !(entity instanceof Warden) && super.canTargetEntity(entity);
	}

	/** Players can't hurt it (so it never turns on anyone by mistake). */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof Player) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if ((this.tickCount + this.getId()) % 100 == 0) {
			this.leaving = !level.getEntitiesOfClass(PinkWarden.class, this.getBoundingBox().inflate(CROWD),
					other -> other != this && this.yieldsTo(other)).isEmpty();
		}
		// A warden digs back underground after a minute with nothing to hunt; a pink one keeps its watch, unless another
		// already keeps it nearby (then it digs away, the way vanilla's wardens leave).
		if (this.leaving) {
			this.getBrain().eraseMemory(MemoryModuleType.DIG_COOLDOWN);
		} else {
			this.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);
		}
		if ((this.tickCount + this.getId()) % 100 == 0) {
			for (ServerPlayer player : level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(this) < NIGHT_VISION_RANGE * NIGHT_VISION_RANGE)) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 20, 0, true, false), this);
			}
		}
	}
}
