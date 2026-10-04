package dev.goober.transdimension.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.network.WingActionPayload;
import dev.goober.transdimension.network.WingFlapPayload;
import dev.goober.transdimension.registry.ModItems;

/**
 * Server side of Maddie's Trans Wings. The wings are an elytra-like glider worn on the chest; on top of vanilla
 * gliding, the client lets you crouch to charge and jump to launch into the sky, flap (jump while gliding) to climb,
 * and crouch while gliding to hover. This class checks those actions, starts gliding after a launch and plays the
 * sounds and sparkles that everyone around sees. Falls and wall bumps don't hurt while you wear them (see
 * {@code RealmEvents}).
 */
public final class TransWings {
	/** Trans flag colours for the sparkles. */
	public static final int[] SPARKLE_COLOURS = {0x5BCEFA, 0xF5A9B8, 0xFFFFFF};
	/** Fewest ticks between two wing actions of one player (the client waits longer; this only stops spam). */
	private static final int MIN_ACTION_GAP = 4;

	private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();

	private TransWings() {
	}

	public static boolean isWearing(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.TRANS_WINGS);
	}

	public static void handleAction(ServerPlayer player, int action) {
		if (!isWearing(player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (action == WingActionPayload.GLIDE) {
			player.tryToStartFallFlying();
			return;
		}
		long now = level.getGameTime();
		Long last = LAST_ACTION.get(player.getUUID());
		if (last != null && now - last < MIN_ACTION_GAP) {
			return;
		}
		LAST_ACTION.put(player.getUUID(), now);

		double x = player.getX(), y = player.getY() + 1.0, z = player.getZ();
		if (action == WingActionPayload.LAUNCH) {
			level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.9F, 1.35F);
			level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
			for (int colour : SPARKLE_COLOURS) {
				level.sendParticles(new DustParticleOptions(colour, 1.4F), x, player.getY() + 0.2, z, 14, 0.6, 0.1, 0.6, 0.05);
			}
			level.sendParticles(ParticleTypes.END_ROD, x, player.getY() + 0.3, z, 10, 0.3, 0.1, 0.3, 0.12);
		} else if (action == WingActionPayload.FLAP) {
			level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.6F,
					1.55F + player.getRandom().nextFloat() * 0.25F);
			for (int colour : SPARKLE_COLOURS) {
				level.sendParticles(new DustParticleOptions(colour, 1.1F), x, y, z, 5, 0.9, 0.3, 0.9, 0.02);
			}
		} else {
			return;
		}
		WingFlapPayload animation = new WingFlapPayload(player.getId(), action);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			if (watcher != player) {
				ServerPlayNetworking.send(watcher, animation);
			}
		}
	}

	/** Forgets a player's cooldown when they leave. */
	public static void forget(UUID player) {
		LAST_ACTION.remove(player);
	}
}
