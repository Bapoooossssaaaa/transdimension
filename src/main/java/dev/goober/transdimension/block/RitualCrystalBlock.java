package dev.goober.transdimension.block;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.registry.ModParticles;
import dev.goober.transdimension.world.SculkRitual;

/**
 * The great pink crystal that floats over the ritual circle in a pink ancient city. It {@link #FACING faces} the city's
 * gate: when the circle's candles are all lit, they beam their light up into it and it beams it on into the gate, where
 * the Sky Portal opens (see {@link SculkRitual}). Use it to see how many candles still need lighting. Unbreakable.
 *
 * <p>Its gem hangs half a block above the block's middle and half a block to its clockwise side
 * ({@link SculkRitual#beamOrigin}), so it can float exactly level with and in line with the middle of a gate that's an
 * even number of blocks across. Its model, outline and sparkles are all offset the same way.
 */
public class RitualCrystalBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Set once the ritual has opened the gate: the crystal glows brighter for good. */
	public static final BooleanProperty AWAKE = BooleanProperty.create("awake");
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
	static {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			Direction side = facing.getClockWise();
			double dx = side.getStepX() * 8.0;
			double dz = side.getStepZ() * 8.0;
			SHAPES.put(facing, Block.box(3.0 + dx, 8.0, 3.0 + dz, 13.0 + dx, 24.0, 13.0 + dz));
		}
	}

	public RitualCrystalBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(AWAKE, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel) {
			SculkRitual.inspect(serverLevel, pos, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// Now and then a mote of light drifts up round the gem (a little more often once it's awake).
		if (random.nextInt(state.getValue(AWAKE) ? 3 : 8) == 0) {
			Vec3 gem = SculkRitual.beamOrigin(pos, state.getValue(FACING));
			double angle = random.nextDouble() * Math.PI * 2.0;
			double radius = 0.5 + random.nextDouble() * 0.4;
			level.addParticle(ModParticles.PRISM_SPARK, gem.x + Math.cos(angle) * radius, gem.y - 0.3 + random.nextDouble() * 0.8,
					gem.z + Math.sin(angle) * radius, 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, AWAKE);
	}
}
