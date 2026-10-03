package dev.goober.transdimension.network;

import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.entity.Maddie;
import dev.goober.transdimension.item.TransWings;

/** Registers the mod's packets and handles the ones clients send. */
public final class ModNetworking {
	/** How close a player must stand to Maddie for their dialogue choices to count. */
	private static final double MADDIE_REACH = 8.0;

	private ModNetworking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(OpenMaddieDialoguePayload.TYPE, OpenMaddieDialoguePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WingFlapPayload.TYPE, WingFlapPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(MaddieChoicePayload.TYPE, MaddieChoicePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WingActionPayload.TYPE, WingActionPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(MaddieChoicePayload.TYPE, (payload, context) -> {
			Entity entity = context.player().level().getEntity(payload.entityId());
			if (entity instanceof Maddie maddie && maddie.closerThan(context.player(), MADDIE_REACH)
					&& payload.choice() == MaddieChoicePayload.ACCEPT_GIFTS) {
				maddie.giveGifts(context.player());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(WingActionPayload.TYPE,
				(payload, context) -> TransWings.handleAction(context.player(), payload.action()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TransWings.forget(handler.player.getUUID()));
	}
}
