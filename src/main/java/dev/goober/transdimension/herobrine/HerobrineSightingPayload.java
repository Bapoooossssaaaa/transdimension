package dev.goober.transdimension.herobrine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.goober.transdimension.TransDimension;

/**
 * Server to one player: Herobrine stands here, for you alone, until you come too close, look at him too long (if
 * {@code stare}) or {@code lingerTicks} pass. Nothing else in the game is told he's there.
 *
 * @param x, y, z        where his feet are
 * @param yaw            which way his body faces (towards the player)
 * @param lingerTicks    how long he stays at most
 * @param vanishDistance how close the player may come (a far-off sighting)
 * @param behind         he's right behind the player: he goes the moment they turn to look
 * @param stare          he goes when looked at for a moment
 * @param sounds         a cave sound plays where he stood as he goes
 */
public record HerobrineSightingPayload(double x, double y, double z, float yaw, int lingerTicks, int vanishDistance, boolean behind,
		boolean stare, boolean sounds) implements CustomPacketPayload {
	public static final Type<HerobrineSightingPayload> TYPE = new Type<>(TransDimension.id("herobrine_sighting"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HerobrineSightingPayload> CODEC = StreamCodec.of(
			(buf, sighting) -> {
				buf.writeDouble(sighting.x());
				buf.writeDouble(sighting.y());
				buf.writeDouble(sighting.z());
				buf.writeFloat(sighting.yaw());
				buf.writeVarInt(sighting.lingerTicks());
				buf.writeVarInt(sighting.vanishDistance());
				buf.writeBoolean(sighting.behind());
				buf.writeBoolean(sighting.stare());
				buf.writeBoolean(sighting.sounds());
			},
			buf -> new HerobrineSightingPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt(),
					buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
