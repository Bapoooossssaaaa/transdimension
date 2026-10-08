package dev.goober.transdimension.herobrine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Client to server: the host saved the secret settings, and with {@code action} {@link #SHOW} wants him to appear
 * straight away, or with {@link #SOUND} a sound played at once, to test them. Only the world's host or an operator may
 * (Herobrine#mayConfigure).
 */
public record HerobrineSettingsPayload(HerobrineSettings settings, int action) implements CustomPacketPayload {
	public static final int SAVE = 0;
	public static final int SHOW = 1;
	public static final int SOUND = 2;

	public static final Type<HerobrineSettingsPayload> TYPE = new Type<>(TransDimension.id("herobrine_settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HerobrineSettingsPayload> CODEC = StreamCodec.composite(
			HerobrineSettings.STREAM_CODEC, HerobrineSettingsPayload::settings,
			ByteBufCodecs.VAR_INT, HerobrineSettingsPayload::action,
			HerobrineSettingsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
