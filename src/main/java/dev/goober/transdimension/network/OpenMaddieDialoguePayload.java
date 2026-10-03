package dev.goober.transdimension.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Server to client: open Maddie's dialogue screen.
 *
 * @param entityId Maddie's entity id (sent back with the player's choice)
 * @param gifted   whether this player already got Maddie's wand and wings
 */
public record OpenMaddieDialoguePayload(int entityId, boolean gifted) implements CustomPacketPayload {
	public static final Type<OpenMaddieDialoguePayload> TYPE = new Type<>(TransDimension.id("open_maddie_dialogue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenMaddieDialoguePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, OpenMaddieDialoguePayload::entityId,
			ByteBufCodecs.BOOL, OpenMaddieDialoguePayload::gifted,
			OpenMaddieDialoguePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
