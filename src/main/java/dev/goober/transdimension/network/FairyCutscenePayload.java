package dev.goober.transdimension.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Server to client: a step of the Fairy Realm's cutscene (FairyCutscene). {@link #START} starts the scene on the client
 * (the moving camera, the hidden HUD), {@link #LINE} shows a subtitle ({@code speaker} and {@code line} are translation
 * keys), {@link #END} hands the camera back to the player as the fight begins.
 */
public record FairyCutscenePayload(int kind, String speaker, String line) implements CustomPacketPayload {
	public static final int START = 0;
	public static final int LINE = 1;
	public static final int END = 2;

	public static final Type<FairyCutscenePayload> TYPE = new Type<>(TransDimension.id("fairy_cutscene"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FairyCutscenePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, FairyCutscenePayload::kind,
			ByteBufCodecs.STRING_UTF8, FairyCutscenePayload::speaker,
			ByteBufCodecs.STRING_UTF8, FairyCutscenePayload::line,
			FairyCutscenePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
