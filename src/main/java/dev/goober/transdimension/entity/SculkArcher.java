package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One of the two friends Maddie brings to the Fairy Realm's cutscene (FairyCutscene): a sculk person of the pink deep
 * dark with a bow. At her call each steps out of a door of light, looses arrows at the Trans Fairy (her shield stops
 * every one) and is struck down by her wand; their loot (loot_table/entities/sculk_archer) drops where they fall.
 *
 * <p>They only ever act in the cutscene, which moves and poses them: no AI of their own, unhurt until the fairy strikes,
 * and never saved (their type is {@code noSave}), so a scene cut short leaves nobody standing about. They're drawn like
 * sculk people, holding the bow up with both arms while they aim (SculkArcherRenderer, from {@link #isAiming}).
 */
public class SculkArcher extends PathfinderMob {
	/** How fast their arrows fly, in blocks a tick. */
	private static final float ARROW_SPEED = 1.4F;
	private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(SculkArcher.class, EntityDataSerializers.BOOLEAN);

	public SculkArcher(EntityType<? extends SculkArcher> entityType, Level level) {
		super(entityType, level);
		this.setNoAi(true);
		this.setInvulnerable(true);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(AIMING, false);
	}

	/** True while the bow is drawn and raised (the client poses both arms for it). */
	public boolean isAiming() {
		return this.entityData.get(AIMING);
	}

	public void setAiming(boolean aiming) {
		this.entityData.set(AIMING, aiming);
	}

	/**
	 * Looses an arrow at {@code target}. The cutscene's arrows fly dead straight and pass through everything (no gravity,
	 * {@code noPhysics}), so the only thing that stops them is the Trans Fairy's shield (FairyCutscene catches them there)
	 * and they can't hurt anyone on the way.
	 */
	@Nullable
	public AbstractArrow shootAt(ServerLevel level, Vec3 target) {
		AbstractArrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.EVENT);
		if (arrow == null) {
			return null;
		}
		Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(0.6));
		Vec3 aim = target.subtract(from);
		arrow.setOwner(this);
		arrow.noPhysics = true;
		arrow.setNoGravity(true);
		arrow.setCritArrow(true);
		arrow.setBaseDamage(0.0);
		arrow.setPos(from.x, from.y, from.z);
		arrow.shoot(aim.x, aim.y, aim.z, ARROW_SPEED, 0.0F);
		level.addFreshEntity(arrow);
		this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 0.9F + this.getRandom().nextFloat() * 0.3F);
		return arrow;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
