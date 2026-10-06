package dev.goober.transdimension.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.world.CloudRealm;
import dev.goober.transdimension.world.SculkRitual;

/**
 * The Sky Portal: a sheet of white and pink light standing in a gateway, like a nether portal. The candle ritual opens one
 * in a pink ancient city's gate ({@link SculkRitual}); it leads to the Cloud Realm, and the one waiting on the Cloud
 * Realm's arrival island leads back (see {@link CloudRealm#portalDestination}). {@link #AXIS} is the way the sheet runs.
 */
public class SkyPortalBlock extends Block implements Portal {
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private static final VoxelShape X_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
	private static final VoxelShape Z_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
	private static final int[] GLOW = {0xFFFFFF, 0xFFE3EE, 0xFFB3D1, 0xFF8EC0};

	public SkyPortalBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? X_SHAPE : Z_SHAPE;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier,
			boolean isPrecise) {
		if (entity.canUsePortal(false)) {
			entity.setAsInsidePortal(this, pos);
		}
	}

	@Override
	@Nullable
	public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
		return CloudRealm.portalDestination(level, entity, pos);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		boolean alongX = state.getValue(AXIS) == Direction.Axis.X;
		for (int i = 0; i < 2; i++) {
			double along = random.nextDouble();
			double across = 0.5 + (random.nextDouble() - 0.5) * 0.6;
			level.addParticle(new DustParticleOptions(GLOW[random.nextInt(GLOW.length)], 0.5F + random.nextFloat() * 0.5F),
					pos.getX() + (alongX ? along : across), pos.getY() + random.nextDouble(), pos.getZ() + (alongX ? across : along),
					0.0, 0.03, 0.0);
		}
		if (random.nextInt(8) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + random.nextDouble(), pos.getZ() + 0.5,
					(random.nextDouble() - 0.5) * 0.05, 0.02, (random.nextDouble() - 0.5) * 0.05);
		}
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
			return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
		}
		return state;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}
}
