package dev.goober.transdimension.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import dev.goober.transdimension.block.RitualCrystalBlock;
import dev.goober.transdimension.block.RitualPedestalBlock;
import dev.goober.transdimension.block.SkyPortalBlock;
import dev.goober.transdimension.registry.ModBlocks;

/**
 * The candle ritual that opens a pink ancient city's gate into a Sky Portal.
 *
 * <p>In front of the great gate of every pink ancient city (tools/generate_ancient_city.py) stands a circle of eight
 * {@link RitualPedestalBlock candle stands} under a floating {@link RitualCrystalBlock}, which faces the gate. Ritual
 * Candles come from the city's chests. When the last stand gets its candle:
 * <ol>
 * <li>each candle shoots a beam of light up into the crystal ({@link #CANDLE_BEAMS} ticks),
 * <li>the crystal wakes and shoots one great beam into the middle of the gate ({@link #CRYSTAL_BEAM} ticks),
 * <li>a Sky Portal opens at the middle of the gate and spreads out ring by ring until it touches the frame all round.
 * </ol>
 * The opening is found by flood-filling the air in the gate's plane from its middle ({@link #GATE_DISTANCE} blocks the
 * way the crystal faces and {@link #GATE_RISE} up). A frame with a hole in it (more than {@link #MAX_PORTAL} blocks of
 * light) can't hold the portal, and it fades away again.
 */
public final class SculkRitual {
	/** How far round the crystal, and how far below it, the candle stands may be. */
	private static final int RING_REACH = 8;
	private static final int RING_DEPTH = 12;
	/** From the crystal to the middle of the gate: this many blocks the way it faces, and this many up. */
	private static final int GATE_DISTANCE = 6;
	private static final int GATE_RISE = 4;
	private static final int MAX_PORTAL = 400;
	private static final int CANDLE_BEAMS = 50;
	private static final int CRYSTAL_BEAM = 40;
	private static final double MESSAGE_RANGE = 48.0;
	private static final int[] PINKS = {0xFF5FA2, 0xFFB3D1, 0xFFFFFF};

	private static final List<Ritual> RUNNING = new ArrayList<>();

	private SculkRitual() {
	}

	public static void initialize() {
		ServerTickEvents.END_LEVEL_TICK.register(SculkRitual::tick);
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
		BlockPos start = findGateMiddle(level, crystal, facing);
		if (start == null) {
			player.sendOverlayMessage(Component.translatable("message.transdimension.ritual.blocked"));
			return;
		}
		RUNNING.add(new Ritual(level, crystal, facing, stands, start));
		level.playSound(null, crystal, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 1.2F);
		level.playSound(null, crystal, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.8F);
		tell(level, crystal, "message.transdimension.ritual.begin");
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

	/** The air block at the middle of the gate the crystal faces (or the nearest air in the gate's plane), or null. */
	@Nullable
	private static BlockPos findGateMiddle(ServerLevel level, BlockPos crystal, Direction facing) {
		BlockPos middle = crystal.relative(facing, GATE_DISTANCE).above(GATE_RISE);
		Direction along = facing.getClockWise();
		for (int r = 0; r <= 2; r++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int da = -r; da <= r; da++) {
					BlockPos pos = middle.above(dy).relative(along, da);
					if (level.getBlockState(pos).isAir()) {
						return pos;
					}
				}
			}
		}
		return null;
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

	private static void tell(ServerLevel level, BlockPos at, String key) {
		Vec3 centre = Vec3.atCenterOf(at);
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(centre) < MESSAGE_RANGE * MESSAGE_RANGE)) {
			player.sendOverlayMessage(Component.translatable(key));
		}
	}

	/** A line of glittering dust from one point to another, with a spark racing along it. */
	private static void beam(ServerLevel level, Vec3 from, Vec3 to, int age, float size) {
		Vec3 delta = to.subtract(from);
		int steps = Math.max(2, (int) (delta.length() * 2.5));
		for (int i = 0; i <= steps; i++) {
			Vec3 point = from.add(delta.scale(i / (double) steps));
			level.sendParticles(new DustParticleOptions(PINKS[(i + age) % PINKS.length], size), point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
		// count 0: the offset becomes the spark's velocity
		Vec3 velocity = delta.normalize();
		level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 0, velocity.x, velocity.y, velocity.z, 0.45);
	}

	/** One ritual in progress. */
	private static final class Ritual {
		final ServerLevel level;
		final BlockPos crystal;
		final Direction facing;
		final List<BlockPos> stands;
		final BlockPos start;
		final Direction.Axis axis;
		final Direction[] plane;
		final Set<BlockPos> seen = new HashSet<>();
		final List<BlockPos> portal = new ArrayList<>();
		List<BlockPos> frontier = new ArrayList<>();
		int age;

		Ritual(ServerLevel level, BlockPos crystal, Direction facing, List<BlockPos> stands, BlockPos start) {
			this.level = level;
			this.crystal = crystal;
			this.facing = facing;
			this.stands = stands;
			this.start = start;
			this.axis = facing.getClockWise().getAxis();
			this.plane = new Direction[] {Direction.UP, Direction.DOWN, facing.getClockWise(), facing.getCounterClockWise()};
		}

		/** Runs one tick; true once the ritual is over. */
		boolean advance() {
			int t = this.age++;
			Vec3 heart = Vec3.atCenterOf(this.crystal);
			if (t < CANDLE_BEAMS) {
				if (t % 3 == 0) {
					for (BlockPos stand : this.stands) {
						beam(this.level, Vec3.atCenterOf(stand).add(0.0, 0.7, 0.0), heart, t, 0.8F);
					}
				}
				if (t == CANDLE_BEAMS - 15) {
					this.level.playSound(null, this.crystal, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 1.4F);
				}
				return false;
			}
			if (t < CANDLE_BEAMS + CRYSTAL_BEAM) {
				if (t == CANDLE_BEAMS) {
					BlockState state = this.level.getBlockState(this.crystal);
					this.level.setBlock(this.crystal, state.setValue(RitualCrystalBlock.AWAKE, true), Block.UPDATE_ALL);
					this.level.playSound(null, this.crystal, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.5F, 0.8F);
				}
				beam(this.level, heart, Vec3.atCenterOf(this.start), t, 1.4F);
				return false;
			}
			if (t == CANDLE_BEAMS + CRYSTAL_BEAM) {
				this.frontier.add(this.start);
				this.seen.add(this.start);
				this.level.playSound(null, this.start, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1.5F, 1.4F);
			}
			if (t % 2 != 0) {
				return false;
			}
			// The portal grows one ring of blocks every other tick until the frame stops it everywhere.
			BlockState sheet = ModBlocks.SKY_PORTAL.defaultBlockState().setValue(SkyPortalBlock.AXIS, this.axis);
			List<BlockPos> next = new ArrayList<>();
			for (BlockPos pos : this.frontier) {
				if (!this.level.getBlockState(pos).isAir()) {
					continue;
				}
				this.level.setBlock(pos, sheet, Block.UPDATE_CLIENTS);
				this.portal.add(pos);
				if (this.portal.size() > MAX_PORTAL) {
					this.collapse();
					return true;
				}
				for (Direction direction : this.plane) {
					BlockPos neighbour = pos.relative(direction);
					if (this.seen.add(neighbour) && this.level.getBlockState(neighbour).isAir()) {
						next.add(neighbour);
					}
				}
				if (this.level.getRandom().nextInt(4) == 0) {
					this.level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
				}
			}
			this.frontier = next;
			if (next.isEmpty()) {
				this.level.playSound(null, this.start, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.3F);
				tell(this.level, this.crystal, "message.transdimension.ritual.opened");
				return true;
			}
			return false;
		}

		/** The frame has a hole: the light spills out and fades. */
		private void collapse() {
			for (BlockPos pos : this.portal) {
				this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			BlockState state = this.level.getBlockState(this.crystal);
			if (state.is(ModBlocks.RITUAL_CRYSTAL)) {
				this.level.setBlock(this.crystal, state.setValue(RitualCrystalBlock.AWAKE, false), Block.UPDATE_ALL);
			}
			tell(this.level, this.crystal, "message.transdimension.ritual.broken");
		}
	}
}
