package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;

import dev.goober.transdimension.world.PinkDeepDark;

/**
 * The pink deep dark's warden: vanilla's warden in every way (it hears vibrations, sniffs, roars, smashes and fires sonic
 * booms) except that it's on your side. It only ever hunts monsters, so it guards you from whatever spawns in the dark;
 * players can't hurt it; it never digs back underground; and instead of darkness it gives the players round it night
 * vision. (Any darkness in the Trans Realm, a shrieker's too, turns into night vision: see {@link PinkDeepDark}.)
 *
 * <p>It's drawn by vanilla's warden renderer; in the realm TransRecolor swaps its textures for the pink ones in
 * {@code textures/entity/trans/warden/}. One guards the gate of every pink ancient city, and now and then one wanders
 * the pink deep dark on its own.
 */
public class PinkWarden extends Warden {
	/** Natural spawns keep at least this far from another pink warden. */
	public static final int SPACING = 48;
	private static final double NIGHT_VISION_RANGE = 20.0;

	public PinkWarden(EntityType<? extends Monster> entityType, Level level) {
		super(entityType, level);
		this.setPersistenceRequired();
	}

	/** Natural spawns: one try in four, and never near another pink warden. */
	public static boolean checkPinkWardenSpawnRules(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
			RandomSource random) {
		return random.nextInt(4) == 0 && Mob.checkMobSpawnRules(type, level, reason, pos, random)
				&& level.getEntitiesOfClass(PinkWarden.class, new AABB(pos).inflate(SPACING)).isEmpty();
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
		// A warden digs back underground after a minute with nothing to hunt; this one keeps its watch.
		this.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);
		if ((this.tickCount + this.getId()) % 100 == 0) {
			for (ServerPlayer player : level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(this) < NIGHT_VISION_RANGE * NIGHT_VISION_RANGE)) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 20, 0, true, false), this);
			}
		}
	}
}
