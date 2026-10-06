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

import dev.goober.transdimension.block.FairyJarBlock;
import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModEntities;

/**
 * The little fairy in a Fairy Jar: a tiny wild fairy (drawn at a fraction of a wild one's size) that flits about inside
 * its jar, glowing pink, white and blue in turn. The jar makes one when it's placed, and its random ticks give older jars
 * theirs; if the jar is broken, the fairy goes with it. It can't be hurt, pushed or used, and clicks go straight through
 * it to the jar.
 */
public class JarFairy extends Fairy {
	/** The colours it glows in turn, as Fairy colours: pink, white, blue. */
	private static final int[] CYCLE = {PINK, WHITE, BLUE};

	@Nullable
	private Vec3 flitTarget;

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
		int colour = CYCLE[(int) (level.getGameTime() / FairyJarBlock.COLOUR_TICKS % CYCLE.length)];
		if (colour != this.getColour()) {
			this.setColour(colour);
		}
		if (this.flitTarget == null || this.position().distanceToSqr(this.flitTarget) < 0.0016 || this.random.nextInt(40) == 0) {
			// Somewhere inside the glass, its wings clear of the glass and the lid.
			this.flitTarget = new Vec3(jar.getX() + 0.42 + this.random.nextDouble() * 0.16, jar.getY() + 0.12 + this.random.nextDouble() * 0.3,
					jar.getZ() + 0.42 + this.random.nextDouble() * 0.16);
		}
		Vec3 delta = this.flitTarget.subtract(this.position());
		Vec3 wanted = delta.lengthSqr() > 0.0009 ? delta.normalize().scale(0.03) : delta;
		this.setDeltaMovement(this.getDeltaMovement().lerp(wanted, 0.25));
		Vec3 motion = this.getDeltaMovement();
		if (motion.horizontalDistanceSqr() > 1.0E-6) {
			float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(Mth.rotLerp(0.4F, this.getYRot(), yaw));
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
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
