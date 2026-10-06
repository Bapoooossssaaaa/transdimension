package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.world.SculkRitual;

/**
 * The great pink crystal that floats over the ritual circle in a pink ancient city. It {@link #FACING faces} the city's
 * gate: when the circle's candles are all lit, they beam their light up into it and it beams it on into the gate, where
 * the Sky Portal opens (see {@link SculkRitual}). Use it to see how many candles still need lighting. Unbreakable.
 */
public class RitualCrystalBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Set once the ritual has opened the gate: the crystal glows brighter for good. */
	public static final BooleanProperty AWAKE = BooleanProperty.create("awake");
	private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
	private static final int[] GLOW = {0xFF5FA2, 0xFFB3D1, 0xFFFFFF};

	public RitualCrystalBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(AWAKE, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
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
		int motes = state.getValue(AWAKE) ? 3 : 1;
		for (int i = 0; i < motes; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double radius = 0.5 + random.nextDouble() * 0.4;
			level.addParticle(new DustParticleOptions(GLOW[random.nextInt(GLOW.length)], 0.7F), pos.getX() + 0.5 + Math.cos(angle) * radius,
					pos.getY() + 0.2 + random.nextDouble() * 0.8, pos.getZ() + 0.5 + Math.sin(angle) * radius, 0.0, 0.02, 0.0);
		}
		if (random.nextInt(state.getValue(AWAKE) ? 2 : 6) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + random.nextGaussian() * 0.3, pos.getY() + 0.5 + random.nextGaussian() * 0.3,
					pos.getZ() + 0.5 + random.nextGaussian() * 0.3, 0.0, 0.0, 0.0);
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
