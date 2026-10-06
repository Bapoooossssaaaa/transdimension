package dev.goober.transdimension.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.goober.transdimension.block.RitualCrystalBlock;
import dev.goober.transdimension.block.RitualPedestalBlock;
import dev.goober.transdimension.block.SkyPortalBlock;
import dev.goober.transdimension.network.RitualPayload;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * The candle ritual that opens a pink ancient city's gate into a Sky Portal.
 *
 * <p>In front of the great gate of every pink ancient city (tools/generate_ancient_city.py) a circle of eight
 * {@link RitualPedestalBlock candle stands} rings the foot of a floating {@link RitualCrystalBlock}, which hangs level
 * with the middle of the gate and faces it. Ritual Candles come from the city's chests. When the last stand gets its
 * candle:
 * <ol>
 * <li>one after another round the circle, each candle shoots a beam of light up into the crystal,
 * <li>the crystal wakes with a flash and fires one great beam straight into the middle of the gate,
 * <li>a Sky Portal opens there behind a sheet of light and spreads out ring by ring until it touches the frame all round.
 * </ol>
 * The server changes the blocks and plays the sounds; every watching client draws the beams and the light itself from
 * one {@link RitualPayload} (RitualEffects), on the same timeline (the constants below).
 *
 * <p>The opening is the air in the gate's plane round the point {@link #GATE_DISTANCE} blocks in front of the crystal's
 * gem. A frame with a hole in it (more than {@link #MAX_PORTAL} blocks of air) can't hold a portal.
 */
public final class SculkRitual {
	/** How far round the crystal, and how far below it, the candle stands may be. */
	private static final int RING_REACH = 8;
	private static final int RING_DEPTH = 12;
	/** The middle of the gate is this many blocks in front of the crystal's gem, the way it faces. */
	public static final int GATE_DISTANCE = 6;
	/** How far from that point the opening may start (the first cities' crystals hung four blocks lower). */
	private static final int GATE_SEARCH = 5;
	private static final int MAX_PORTAL = 400;
	private static final double AUDIENCE = 96.0;

	// The timeline, in ticks from the moment the circle is complete. The clients' light show follows the same one.
	/** One candle beam starts every this many ticks, round the circle... */
	public static final int CANDLE_INTERVAL = 4;
	/** ...each taking this long to rise into the crystal. */
	public static final int CANDLE_GROW = 8;
	/** The crystal wakes... */
	public static final int AWAKEN = 44;
	/** ...fires at the gate... */
	public static final int FIRE = 56;
	/** ...its beam takes this long to cross... */
	public static final int FIRE_GROW = 5;
	/** ...and the portal starts in the middle of the gate, gaining a ring of blocks every {@link #RING_TICKS}. */
	public static final int OPEN = FIRE + FIRE_GROW;
	public static final int RING_TICKS = 2;
	/** Once the last ring is in, the light lingers this long, fading out over the last {@link #FADE} ticks. */
	public static final int LINGER = 40;
	public static final int FADE = 20;

	private static final List<Ritual> RUNNING = new ArrayList<>();

	private SculkRitual() {
	}

	public static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(SculkRitual::tick);
	}

	/**
	 * Where the crystal's beam leaves it: the middle of its floating gem. The gem hangs half a block above its block's
	 * middle and half a block to its clockwise side, so a crystal can sit exactly level with, and in line with, the
	 * middle of a gate that's an even number of blocks wide and tall (the ancient city's is 20 by 6).
	 */
	public static Vec3 beamOrigin(BlockPos crystal, Direction facing) {
		Direction side = facing.getClockWise();
		return Vec3.atCenterOf(crystal).add(side.getStepX() * 0.5, 0.5, side.getStepZ() * 0.5);
	}

	/** Where a crystal aims: the middle of its gate, if the gate is where the city puts it. */
	public static Vec3 aim(BlockPos crystal, Direction facing) {
		return beamOrigin(crystal, facing).add(facing.getStepX() * GATE_DISTANCE, 0.0, facing.getStepZ() * GATE_DISTANCE);
	}

	/** A candle was just set on a stand: if that completes its circle, the ritual begins. */
	public static void candleLit(ServerLevel level, BlockPos stand, Player player) {
		BlockPos crystal = findCrystal(level, stand);
		if (crystal != null) {
			check(level, crystal, player);
		}
	}

	/** A player used a stand or the crystal: tell them how far along the circle is (and begin, if it's complete). */
	public static void inspect(ServerLevel level, BlockPos pos, Player player) {
		BlockPos crystal = level.getBlockState(pos).is(ModBlocks.RITUAL_CRYSTAL) ? pos : findCrystal(level, pos);
		if (crystal != null) {
			check(level, crystal, player);
		}
	}

	private static void check(ServerLevel level, BlockPos crystal, Player player) {
		BlockState crystalState = level.getBlockState(crystal);
		if (crystalState.getValue(RitualCrystalBlock.AWAKE)) {
			player.sendOverlayMessage(Component.translatable("message.transdimension.ritual.open"));
			return;
		}
		if (isRunning(level, crystal)) {
			return;
		}
		List<BlockPos> stands = findStands(level, crystal);
		if (stands.isEmpty()) {
			return;
		}
		int lit = 0;
		for (BlockPos stand : stands) {
			if (level.getBlockState(stand).getValue(RitualPedestalBlock.CANDLE)) {
				lit++;
			}
		}
		if (lit < stands.size()) {
			player.sendOverlayMessage(Component.translatable("message.transdimension.ritual.candles", lit, stands.size()));
			return;
		}
		Direction facing = crystalState.getValue(RitualCrystalBlock.FACING);
		Set<BlockPos> opening = findOpening(level, aim(crystal, facing), facing);
		if (opening.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.transdimension.ritual.blocked"));
			return;
		}
		if (opening.size() > MAX_PORTAL) {
			player.sendOverlayMessage(Component.translatable("message.transdimension.ritual.broken"));
			return;
		}
		Gate gate = Gate.of(opening, facing);
		Vec3 origin = beamOrigin(crystal, facing);
		List<Vec3> candles = candleTips(stands, origin, facing);
		RUNNING.add(new Ritual(level, crystal, facing, candles, gate));

		RitualPayload payload = new RitualPayload(crystal, facing, origin, gate.centre, gate.halfAlong, gate.halfUp, gate.rings.size(), candles);
		for (ServerPlayer watcher : level.getPlayers(p -> p.distanceToSqr(origin) < AUDIENCE * AUDIENCE)) {
			ServerPlayNetworking.send(watcher, payload);
		}
		level.playSound(null, crystal, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 1.2F);
		tell(level, origin, "message.transdimension.ritual.begin");
	}

	private static boolean isRunning(ServerLevel level, BlockPos crystal) {
		for (Ritual ritual : RUNNING) {
			if (ritual.level == level && ritual.crystal.equals(crystal)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static BlockPos findCrystal(ServerLevel level, BlockPos around) {
		for (BlockPos pos : BlockPos.betweenClosed(around.offset(-RING_REACH, 0, -RING_REACH), around.offset(RING_REACH, RING_DEPTH, RING_REACH))) {
			if (level.getBlockState(pos).is(ModBlocks.RITUAL_CRYSTAL)) {
				return pos.immutable();
			}
		}
		return null;
	}

	private static List<BlockPos> findStands(ServerLevel level, BlockPos crystal) {
		List<BlockPos> stands = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(crystal.offset(-RING_REACH, -RING_DEPTH, -RING_REACH), crystal.offset(RING_REACH, 0, RING_REACH))) {
			if (level.getBlockState(pos).is(ModBlocks.RITUAL_PEDESTAL)) {
				stands.add(pos.immutable());
			}
		}
		return stands;
	}

	/** The flame of each stand's candle, going round the circle clockwise from the stand nearest the gate. */
	private static List<Vec3> candleTips(List<BlockPos> stands, Vec3 origin, Direction facing) {
		double front = Math.atan2(facing.getStepZ(), facing.getStepX());
		List<BlockPos> sorted = new ArrayList<>(stands);
		sorted.sort(Comparator.comparingDouble(stand -> {
			double angle = Math.atan2(stand.getZ() + 0.5 - origin.z, stand.getX() + 0.5 - origin.x) - front;
			return Mth.positiveModulo(angle, Math.PI * 2.0);
		}));
		List<Vec3> tips = new ArrayList<>(sorted.size());
		for (BlockPos stand : sorted) {
			tips.add(Vec3.atBottomCenterOf(stand).add(0.0, 1.15, 0.0));
		}
		return tips;
	}

	/**
	 * The air in the gate's plane joined to the air nearest the point the crystal aims at: empty if there's no air near
	 * that point, and more than {@link #MAX_PORTAL} blocks (where the search stops) if the frame has a hole in it.
	 */
	private static Set<BlockPos> findOpening(ServerLevel level, Vec3 aim, Direction facing) {
		Direction along = facing.getClockWise();
		BlockPos middle = BlockPos.containing(aim);
		Set<BlockPos> opening = new HashSet<>();
		BlockPos start = null;
		search:
		for (int r = 0; r <= GATE_SEARCH; r++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int da = -r; da <= r; da++) {
					if (Math.max(Math.abs(dy), Math.abs(da)) != r) {
						continue;
					}
					BlockPos pos = middle.above(dy).relative(along, da);
					if (level.getBlockState(pos).isAir()) {
						start = pos;
						break search;
					}
				}
			}
		}
		if (start == null) {
			return opening;
		}
		Direction[] plane = {Direction.UP, Direction.DOWN, along, along.getOpposite()};
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		opening.add(start);
		queue.add(start);
		while (!queue.isEmpty() && opening.size() <= MAX_PORTAL) {
			BlockPos pos = queue.poll();
			for (Direction direction : plane) {
				BlockPos next = pos.relative(direction);
				if (!opening.contains(next) && level.getBlockState(next).isAir()) {
					opening.add(next);
					queue.add(next);
				}
			}
		}
		return opening;
	}

	private static void tick(ServerLevel level) {
		if (RUNNING.isEmpty()) {
			return;
		}
		Iterator<Ritual> iterator = RUNNING.iterator();
		while (iterator.hasNext()) {
			Ritual ritual = iterator.next();
			if (ritual.level != level) {
				continue;
			}
			if (!level.isLoaded(ritual.crystal) || !level.getBlockState(ritual.crystal).is(ModBlocks.RITUAL_CRYSTAL) || ritual.advance()) {
				iterator.remove();
			}
		}
	}

	private static void tell(ServerLevel level, Vec3 at, String key) {
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(at) < AUDIENCE * AUDIENCE)) {
			player.sendOverlayMessage(Component.translatable(key));
		}
	}

	/** A gate's opening: its middle, half its width and height, and its blocks in rings out from the middle. */
	private record Gate(Vec3 centre, float halfAlong, float halfUp, List<List<BlockPos>> rings) {
		static Gate of(Set<BlockPos> opening, Direction facing) {
			Direction.Axis across = facing.getClockWise().getAxis();
			int minA = Integer.MAX_VALUE;
			int maxA = Integer.MIN_VALUE;
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			int plane = 0;
			for (BlockPos pos : opening) {
				int a = pos.get(across);
				minA = Math.min(minA, a);
				maxA = Math.max(maxA, a);
				minY = Math.min(minY, pos.getY());
				maxY = Math.max(maxY, pos.getY());
				plane = pos.get(facing.getAxis());
			}
			double ca = (minA + maxA + 1) / 2.0;
			double cy = (minY + maxY + 1) / 2.0;
			double cp = plane + 0.5;
			Vec3 centre = across == Direction.Axis.X ? new Vec3(ca, cy, cp) : new Vec3(cp, cy, ca);
			// Rings are squares round the middle: a block's ring is how many whole blocks out it is, either way.
			List<List<BlockPos>> rings = new ArrayList<>();
			for (BlockPos pos : opening) {
				double out = Math.max(Math.abs(pos.get(across) + 0.5 - ca), Math.abs(pos.getY() + 0.5 - cy));
				int ring = (int) Math.floor(out + 1.0E-6);
				while (rings.size() <= ring) {
					rings.add(new ArrayList<>());
				}
				rings.get(ring).add(pos);
			}
			return new Gate(centre, (maxA - minA + 1) / 2.0F, (maxY - minY + 1) / 2.0F, rings);
		}
	}

	/** One ritual in progress. */
	private static final class Ritual {
		final ServerLevel level;
		final BlockPos crystal;
		final Direction facing;
		final List<Vec3> candles;
		final Gate gate;
		final Vec3 origin;
		int age;

		Ritual(ServerLevel level, BlockPos crystal, Direction facing, List<Vec3> candles, Gate gate) {
			this.level = level;
			this.crystal = crystal;
			this.facing = facing;
			this.candles = candles;
			this.gate = gate;
			this.origin = beamOrigin(crystal, facing);
		}

		/** Runs one tick; true once the ritual is over. */
		boolean advance() {
			int t = this.age++;
			for (int i = 0; i < this.candles.size(); i++) {
				if (t == i * CANDLE_INTERVAL) {
					// Each candle flares with a chime, a note higher than the last.
					Vec3 tip = this.candles.get(i);
					this.level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.8F, 0.8F + i * 0.1F);
					this.level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 6, 0.05, 0.05, 0.05, 0.03);
				}
			}
			if (t == AWAKEN - 16) {
				this.level.playSound(null, this.crystal, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 1.0F);
			}
			if (t == AWAKEN) {
				BlockState state = this.level.getBlockState(this.crystal);
				this.level.setBlock(this.crystal, state.setValue(RitualCrystalBlock.AWAKE, true), Block.UPDATE_ALL);
				this.level.playSound(null, this.crystal, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.5F, 0.9F);
				this.level.sendParticles(ParticleTypes.END_ROD, this.origin.x, this.origin.y, this.origin.z, 40, 0.1, 0.1, 0.1, 0.12);
			}
			if (t == FIRE) {
				this.level.playSound(null, this.origin.x, this.origin.y, this.origin.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.5F, 1.6F);
				this.level.playSound(null, this.origin.x, this.origin.y, this.origin.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.5F, 1.5F);
			}
			if (t >= OPEN && (t - OPEN) % RING_TICKS == 0) {
				int ring = (t - OPEN) / RING_TICKS;
				Vec3 centre = this.gate.centre;
				if (ring == 0) {
					this.level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.5F, 1.4F);
				}
				if (ring >= this.gate.rings.size()) {
					this.level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.3F);
					tell(this.level, this.origin, "message.transdimension.ritual.opened");
					return true;
				}
				BlockState sheet = ModBlocks.SKY_PORTAL.defaultBlockState().setValue(SkyPortalBlock.AXIS, this.facing.getClockWise().getAxis());
				for (BlockPos pos : this.gate.rings.get(ring)) {
					if (this.level.getBlockState(pos).isAir()) {
						this.level.setBlock(pos, sheet, Block.UPDATE_CLIENTS);
						if (this.level.getRandom().nextInt(3) == 0) {
							this.level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
						}
					}
				}
			}
			return false;
		}
	}
}
