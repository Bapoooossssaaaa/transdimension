package dev.goober.transdimension.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The white boat the cloud turtle fishes falling players up in (CloudRescue). It's drawn with vanilla's boat model, but
 * it isn't a vanilla boat: nobody can steer it, because the server carries it along on the end of the turtle's line
 * (its {@link #getPuller() puller}, whom the client draws the line to). Like the turtle, it only exists while he works.
 */
public class CloudBoat extends Entity {
	private static final EntityDataAccessor<Integer> PULLER = SynchedEntityData.defineId(CloudBoat.class, EntityDataSerializers.INT);

	public CloudBoat(EntityType<? extends CloudBoat> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PULLER, -1);
	}

	/** Who holds the line this boat hangs from (the cloud turtle), or null. */
	@Nullable
	public Entity getPuller() {
		int id = this.entityData.get(PULLER);
		return id >= 0 ? this.level().getEntity(id) : null;
	}

	public void setPuller(@Nullable Entity puller) {
		this.entityData.set(PULLER, puller != null ? puller.getId() : -1);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
	}
}
