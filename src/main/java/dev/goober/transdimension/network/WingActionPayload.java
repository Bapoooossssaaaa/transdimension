package dev.goober.transdimension.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Client to server: the player used their Trans Wings. Movement itself is client side (like all player movement);
 * the server checks the wings, starts gliding when asked and plays the sounds and sparkles everyone sees.
 *
 * @param action {@link #LAUNCH}, {@link #FLAP} or {@link #GLIDE}
 */
public record WingActionPayload(int action) implements CustomPacketPayload {
	public static final int LAUNCH = 0;
	public static final int FLAP = 1;
	public static final int GLIDE = 2;

	public static final Type<WingActionPayload> TYPE = new Type<>(TransDimension.id("wing_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WingActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, WingActionPayload::action,
			WingActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
