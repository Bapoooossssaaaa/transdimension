package dev.goober.transdimension.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import dev.goober.transdimension.block.FurnitureBlock;
import dev.goober.transdimension.registry.ModEntities;

/**
 * The invisible seat a player rides while sitting on trans furniture (cushions, stools and chairs), like 26.3's
 * cushions. It sits right on the seat's surface, so the player's legs rest on it. Sneak to get up; the seat vanishes
 * as soon as nobody is on it or the furniture under it is gone.
 */
public class Seat extends Entity {
	public Seat(EntityType<? extends Seat> entityType, Level level) {
		super(entityType, level);
		this.noPhysics = true;
	}

	/**
	 * Sits the player down on the furniture at {@code pos}, whose seat is {@code height} blocks above the bottom of
	 * the block. Players who are sneaking or already riding something don't sit, and only one sits on each seat.
	 */
	public static InteractionResult sit(Level level, BlockPos pos, double height, Player player) {
		if (player.isShiftKeyDown() || player.isPassenger()) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			if (!serverLevel.getEntitiesOfClass(Seat.class, new AABB(pos)).isEmpty()) {
				return InteractionResult.PASS;
			}
			Seat seat = new Seat(ModEntities.SEAT, serverLevel);
			seat.setPos(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
			serverLevel.addFreshEntity(seat);
			player.startRiding(seat);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && (!this.isVehicle()
				|| !(this.level().getBlockState(this.blockPosition()).getBlock() instanceof FurnitureBlock furniture && furniture.isSeat()))) {
			this.ejectPassengers();
			this.discard();
		}
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
