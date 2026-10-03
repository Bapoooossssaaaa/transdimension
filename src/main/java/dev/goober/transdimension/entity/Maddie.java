package dev.goober.transdimension.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.network.OpenMaddieDialoguePayload;
import dev.goober.transdimension.registry.ModAttachments;
import dev.goober.transdimension.registry.ModItems;

/**
 * Maddie, who lives in the Egg House on its floating island. Talk to her (use her with an empty hand, or with
 * anything) to open her dialogue; the first time, she gives you her Trans Wand and Trans Wings.
 *
 * <p>She can't be hurt (only by creative players and the void), never despawns, can't be pushed or leashed, and
 * potters around within a few blocks of where she first stood.
 */
public class Maddie extends PathfinderMob {
	private static final int HOME_RADIUS = 4;

	@Nullable
	private BlockPos home;

	public Maddie(EntityType<? extends Maddie> entityType, Level level) {
		super(entityType, level);
		this.setPersistenceRequired();
		this.setInvulnerable(true);
		this.setCustomName(Component.translatable("entity.transdimension.maddie"));
		this.setCustomNameVisible(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new GoHomeGoal(this));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F, 0.6F));
		this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.5, 0.004F));
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.home == null) {
			this.setHome(this.blockPosition());
		}
	}

	private void setHome(BlockPos pos) {
		this.home = pos.immutable();
		this.setHomeTo(this.home, HOME_RADIUS);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			this.getNavigation().stop();
			this.getLookControl().setLookAt(player, 30.0F, 30.0F);
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL,
					0.6F, 1.8F);
			ServerPlayNetworking.send(serverPlayer, new OpenMaddieDialoguePayload(this.getId(), hasGifted(serverPlayer)));
		}
		return InteractionResult.SUCCESS;
	}

	public static boolean hasGifted(Player player) {
		return player.getAttachedOrElse(ModAttachments.MADDIE_GIFTED, false);
	}

	/** Hands over the Trans Wand and Trans Wings, once per player. Called when the player accepts in the dialogue. */
	public void giveGifts(ServerPlayer player) {
		if (hasGifted(player) || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		player.setAttached(ModAttachments.MADDIE_GIFTED, true);
		for (ItemStack gift : new ItemStack[]{new ItemStack(ModItems.TRANS_WAND), new ItemStack(ModItems.TRANS_WINGS)}) {
			if (!player.addItem(gift)) {
				player.drop(gift, false);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
		level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 2.1, this.getZ(), 6, 0.4, 0.2, 0.4, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.7, 0.5, 0.05);
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

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		if (this.home != null) {
			valueOutput.putInt("home_x", this.home.getX());
			valueOutput.putInt("home_y", this.home.getY());
			valueOutput.putInt("home_z", this.home.getZ());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		valueInput.getInt("home_x").ifPresent(x -> this.setHome(new BlockPos(x, valueInput.getIntOr("home_y", 0),
				valueInput.getIntOr("home_z", 0))));
	}

	/** Walks back home if something carried Maddie away (a minecart, a water stream, a cheeky player). */
	static class GoHomeGoal extends Goal {
		private final Maddie maddie;

		GoHomeGoal(Maddie maddie) {
			this.maddie = maddie;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlockPos home = this.maddie.home;
			return home != null && home.distToCenterSqr(this.maddie.position()) > (HOME_RADIUS + 2) * (HOME_RADIUS + 2);
		}

		@Override
		public boolean canContinueToUse() {
			return !this.maddie.getNavigation().isDone();
		}

		@Override
		public void start() {
			BlockPos home = this.maddie.home;
			if (home != null) {
				this.maddie.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.7);
			}
		}
	}
}
