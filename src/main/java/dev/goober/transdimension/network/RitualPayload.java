package dev.goober.transdimension.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import dev.goober.transdimension.TransDimension;

/**
 * Server to client: a candle ritual has begun ({@code SculkRitual}). It carries everything the client needs to play the
 * whole light show on its own (RitualEffects): where each candle's beam starts, where the crystal's gem floats, the
 * middle and size of the gate's opening, and how many rings of portal it will take to fill it. The timing comes from
 * the constants in {@code SculkRitual}.
 *
 * @param crystal    the ritual crystal's block
 * @param facing     the way the crystal faces (towards the gate)
 * @param origin     the middle of the crystal's floating gem, where its beam starts
 * @param centre     the middle of the gate's opening, where the beam lands and the portal starts
 * @param halfAlong  half the opening's width (across the gate)
 * @param halfUp     half the opening's height
 * @param rings      how many rings of portal fill the opening
 * @param candles    the tip of each candle, in the order they light
 */
public record RitualPayload(BlockPos crystal, Direction facing, Vec3 origin, Vec3 centre, float halfAlong, float halfUp, int rings,
		List<Vec3> candles) implements CustomPacketPayload {
	public static final Type<RitualPayload> TYPE = new Type<>(TransDimension.id("ritual"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RitualPayload> CODEC = StreamCodec.of(RitualPayload::write, RitualPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, RitualPayload payload) {
		buf.writeBlockPos(payload.crystal);
		buf.writeVarInt(payload.facing.get3DDataValue());
		writeVec(buf, payload.origin);
		writeVec(buf, payload.centre);
		buf.writeFloat(payload.halfAlong);
		buf.writeFloat(payload.halfUp);
		buf.writeVarInt(payload.rings);
		buf.writeVarInt(payload.candles.size());
		for (Vec3 candle : payload.candles) {
			writeVec(buf, candle);
		}
	}

	private static RitualPayload read(RegistryFriendlyByteBuf buf) {
		BlockPos crystal = buf.readBlockPos();
		Direction facing = Direction.from3DDataValue(buf.readVarInt());
		Vec3 origin = readVec(buf);
		Vec3 centre = readVec(buf);
		float halfAlong = buf.readFloat();
		float halfUp = buf.readFloat();
		int rings = buf.readVarInt();
		int count = Math.min(buf.readVarInt(), 64);
		List<Vec3> candles = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			candles.add(readVec(buf));
		}
		return new RitualPayload(crystal, facing, origin, centre, halfAlong, halfUp, rings, candles);
	}

	private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 vec) {
		buf.writeDouble(vec.x);
		buf.writeDouble(vec.y);
		buf.writeDouble(vec.z);
	}

	private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
