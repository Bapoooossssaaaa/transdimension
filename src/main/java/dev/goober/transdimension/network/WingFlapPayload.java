package dev.goober.transdimension.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Server to client: another player flapped or launched with their wings, so play the wing animation on them.
 *
 * @param entityId the flying player's entity id
 * @param action   a {@link WingActionPayload} action
 */
public record WingFlapPayload(int entityId, int action) implements CustomPacketPayload {
	public static final Type<WingFlapPayload> TYPE = new Type<>(TransDimension.id("wing_flap"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WingFlapPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, WingFlapPayload::entityId,
			ByteBufCodecs.INT, WingFlapPayload::action,
			WingFlapPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
