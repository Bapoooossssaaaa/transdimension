package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModEntities;

/**
 * The little fairy in a Fairy Jar: a tiny wild fairy (drawn at a fraction of a wild one's size) that drifts slowly about
 * inside its jar, stopping to hover a while at each spot, and keeps the one colour it was given (blue, pink or white, like
 * a wild fairy), now and then letting a mote of it slip up out of the jar. The jar makes one when it's placed, and its
 * random ticks give older jars theirs; if the jar is broken, the fairy goes with it. It can't be hurt, pushed or used, and
 * clicks go straight through it to the jar.
 */
public class JarFairy extends Fairy {
	/** How fast it wants to drift, in blocks a tick; with the air's drag it really moves at a bit under half that. */
	private static final double SPEED = 0.007;
	/** How sharply it turns towards where it's going (the share of the gap it closes each tick). */
	private static final double STEER = 0.1;

	@Nullable
	private Vec3 flitTarget;
	/** Ticks left to hover where it is before it drifts somewhere new. */
	private int hover;

	public JarFairy(EntityType<? extends JarFairy> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
		this.setInvulnerable(true);
		this.setPersistenceRequired();
	}

	/** Gives the jar at {@code jar} its fairy, unless it has one already. */
	public static void ensureIn(ServerLevel level, BlockPos jar) {
		if (!level.getEntitiesOfClass(JarFairy.class, new AABB(jar)).isEmpty()) {
			return;
		}
		JarFairy fairy = ModEntities.JAR_FAIRY.create(level, EntitySpawnReason.EVENT);
		if (fairy != null) {
			fairy.snapTo(jar.getX() + 0.5, jar.getY() + 0.3, jar.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(fairy);
		}
	}

	@Override
	protected boolean leavesTrail() {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// The jar is whatever block it's in (it never leaves it).
		BlockPos jar = this.blockPosition();
		if (!level.getBlockState(jar).is(ModBlocks.FAIRY_JAR)) {
			level.sendParticles(new DustParticleOptions(COLOURS[this.getColour()], 0.6F), this.getX(), this.getY() + 0.1, this.getZ(), 8,
					0.15, 0.15, 0.15, 0.0);
			this.discard();
			return;
		}
		// One fairy to a jar: a jar's random tick can make a second before the first has loaded with its chunk.
		if (this.tickCount % 40 == 0
				&& !level.getEntitiesOfClass(JarFairy.class, new AABB(jar), other -> other != this && other.getId() < this.getId()).isEmpty()) {
			this.discard();
			return;
		}
		if (this.hover > 0) {
			this.hover--;
			this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
			return;
		}
		if (this.flitTarget == null) {
			// Somewhere inside the glass, its wings clear of the glass and the lid.
			this.flitTarget = new Vec3(jar.getX() + 0.42 + this.random.nextDouble() * 0.16, jar.getY() + 0.12 + this.random.nextDouble() * 0.3,
					jar.getZ() + 0.42 + this.random.nextDouble() * 0.16);
		}
		Vec3 delta = this.flitTarget.subtract(this.position());
		if (delta.lengthSqr() < 0.0004) {
			// There: hover for one to four seconds before drifting on.
			this.flitTarget = null;
			this.hover = 20 + this.random.nextInt(60);
			return;
		}
		Vec3 wanted = delta.lengthSqr() > SPEED * SPEED ? delta.normalize().scale(SPEED) : delta;
		this.setDeltaMovement(this.getDeltaMovement().lerp(wanted, STEER));
		Vec3 motion = this.getDeltaMovement();
		if (motion.horizontalDistanceSqr() > 1.0E-7) {
			float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(Mth.rotLerp(0.12F, this.getYRot(), yaw));
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide() && this.random.nextInt(30) == 0) {
			// Now and then a mote of its light drifts up and out round the lid, in its own colour.
			this.level().addParticle(new DustParticleOptions(COLOURS[this.getColour()], 0.4F), this.getX(), this.getY() + 0.1, this.getZ(),
					0.0, 0.02, 0.0);
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	@Nullable
	protected SoundEvent getAmbientSound() {
		return null;
	}
}
