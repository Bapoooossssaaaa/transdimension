package dev.goober.transdimension.herobrine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Client to server: the host saved the secret settings ({@code showNow}: and wants to see him straight away, to test
 * them). Only the world's host or an operator may (Herobrine#mayConfigure).
 */
public record HerobrineSettingsPayload(HerobrineSettings settings, boolean showNow) implements CustomPacketPayload {
	public static final Type<HerobrineSettingsPayload> TYPE = new Type<>(TransDimension.id("herobrine_settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HerobrineSettingsPayload> CODEC = StreamCodec.composite(
			HerobrineSettings.STREAM_CODEC, HerobrineSettingsPayload::settings,
			ByteBufCodecs.BOOL, HerobrineSettingsPayload::showNow,
			HerobrineSettingsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
