package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A table lamp with a trans flag shade. Use it to switch it on or off (with a soft chime); lit, it shines as brightly
 * as a lantern. It's placed lit. The light level comes from the block's properties in ModBlocks.
 */
public class TransLampBlock extends Block {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	/** A round foot, a thin stem and a wide shade. */
	private static final VoxelShape SHAPE = Shapes.or(Block.box(5, 0, 5, 11, 2, 11), Block.box(7, 2, 7, 9, 8, 9),
			Block.box(3, 8, 3, 13, 15, 13));

	public TransLampBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LIT, true));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide()) {
			boolean lit = !state.getValue(LIT);
			level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, lit ? 1.6F : 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}
}
