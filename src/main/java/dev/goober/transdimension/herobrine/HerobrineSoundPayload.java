package dev.goober.transdimension.herobrine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Server to one player: play a sound near you, with nothing there. Only that player's client plays it (HerobrineClient
 * picks the spot, behind them, and the block underfoot), so nobody else hears a thing.
 *
 * @param kind {@link #FOOTSTEPS} coming up behind them on whatever the ground is made of, a {@link #CREEPER} hissing right
 *             behind them, a {@link #DOOR} opening and closing, or someone {@link #MINING} somewhere in the rock nearby
 */
public record HerobrineSoundPayload(int kind) implements CustomPacketPayload {
	public static final int FOOTSTEPS = 0;
	public static final int CREEPER = 1;
	public static final int DOOR = 2;
	public static final int MINING = 3;

	public static final Type<HerobrineSoundPayload> TYPE = new Type<>(TransDimension.id("herobrine_sound"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HerobrineSoundPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HerobrineSoundPayload::kind,
			HerobrineSoundPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
