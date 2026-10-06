package dev.goober.transdimension.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The cloud turtle: a goggled little turtle who rides a Cloudy about the Cloud Realm's sky and fishes falling players
 * out of it in a white boat (CloudRescue moves him; he has no mind of his own). He only exists while he's working, and
 * nothing can hurt him.
 */
public class CloudTurtle extends PathfinderMob {
	public CloudTurtle(EntityType<? extends CloudTurtle> entityType, Level level) {
		super(entityType, level);
		this.setNoGravity(true);
		this.setNoAi(true);
		this.setInvulnerable(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}
}
