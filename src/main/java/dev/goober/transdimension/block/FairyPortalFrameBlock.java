package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.registry.ModItems;
import dev.goober.transdimension.registry.ModParticles;
import dev.goober.transdimension.world.FairyRealm;

/**
 * One of the twelve frames of a Fairy Portal, found in the Fairy Sanctums deep under the Trans Realm. Use a Trans
 * Crystal Pearl on it to set the pearl into its socket; when all twelve frames of a ring hold one, the portal opens
 * (see {@link FairyRealm#tryOpenPortal}). Like an end portal frame it can't be broken in survival.
 */
public class FairyPortalFrameBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty PEARL = BooleanProperty.create("pearl");
	private static final VoxelShape BASE = Block.box(0.0, 0.0, 0.0, 16.0, 13.0, 16.0);
	private static final VoxelShape WITH_PEARL = Shapes.or(BASE, Block.box(4.0, 13.0, 4.0, 12.0, 16.0, 12.0));

	public FairyPortalFrameBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PEARL, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(PEARL) ? WITH_PEARL : BASE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (!stack.is(ModItems.TRANS_CRYSTAL_PEARL) || state.getValue(PEARL)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		}
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.setBlock(pos, state.setValue(PEARL, true), Block.UPDATE_CLIENTS);
			stack.consume(1, player);
			serverLevel.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 1.3F);
			serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 1.0F + serverLevel.getRandom().nextFloat() * 0.4F);
			FairyRealm.sparkle(serverLevel, Vec3.atCenterOf(pos).add(0.0, 0.6, 0.0), 12, 0.25);
			FairyRealm.tryOpenPortal(serverLevel, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(PEARL) && random.nextInt(6) == 0) {
			level.addParticle(ModParticles.TRANS_SPARK, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.05,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.03, 0.0);
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
		builder.add(FACING, PEARL);
	}
}
