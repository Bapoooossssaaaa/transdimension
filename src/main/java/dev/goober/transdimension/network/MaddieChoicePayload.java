package dev.goober.transdimension.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Client to server: something the player chose in Maddie's dialogue that the server must act on.
 *
 * @param entityId Maddie's entity id
 * @param choice   {@link #ACCEPT_GIFTS} (more may follow)
 */
public record MaddieChoicePayload(int entityId, int choice) implements CustomPacketPayload {
	public static final int ACCEPT_GIFTS = 1;

	public static final Type<MaddieChoicePayload> TYPE = new Type<>(TransDimension.id("maddie_choice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MaddieChoicePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, MaddieChoicePayload::entityId,
			ByteBufCodecs.INT, MaddieChoicePayload::choice,
			MaddieChoicePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
