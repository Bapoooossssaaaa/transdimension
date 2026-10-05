package dev.goober.transdimension.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Server to client: a Bottled Fairy just saved you. The client shows the Bottled Fairy popping up on screen, the way
 * vanilla shows a totem (see {@link dev.goober.transdimension.item.BottledFairy}).
 */
public record FairyRescuePayload() implements CustomPacketPayload {
	public static final FairyRescuePayload INSTANCE = new FairyRescuePayload();
	public static final Type<FairyRescuePayload> TYPE = new Type<>(TransDimension.id("fairy_rescue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FairyRescuePayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
