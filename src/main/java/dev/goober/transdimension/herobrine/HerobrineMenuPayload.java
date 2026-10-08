package dev.goober.transdimension.herobrine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/** Server to the host: open the secret settings screen, showing these settings. */
public record HerobrineMenuPayload(HerobrineSettings settings) implements CustomPacketPayload {
	public static final Type<HerobrineMenuPayload> TYPE = new Type<>(TransDimension.id("herobrine_menu"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HerobrineMenuPayload> CODEC =
			HerobrineSettings.STREAM_CODEC.map(HerobrineMenuPayload::new, HerobrineMenuPayload::settings);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
