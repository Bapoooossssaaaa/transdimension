package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.registry.ModItems;
import dev.goober.transdimension.registry.ModParticles;
import dev.goober.transdimension.world.SculkRitual;

/**
 * One of the eight candle stands round the ritual circle in front of a pink ancient city's gate. Set a Ritual Candle on
 * it (they're found in the city's chests) and it lights; when every stand round the circle holds one, the ritual begins
 * (see {@link SculkRitual}). Like the gate itself it can't be broken in survival.
 */
public class RitualPedestalBlock extends Block {
	public static final BooleanProperty CANDLE = BooleanProperty.create("candle");
	private static final VoxelShape STAND = Shapes.or(Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0), Block.box(4.0, 3.0, 4.0, 12.0, 9.0, 12.0),
			Block.box(3.0, 9.0, 3.0, 13.0, 11.0, 13.0));
	private static final VoxelShape WITH_CANDLE = Shapes.or(STAND, Block.box(6.0, 11.0, 6.0, 10.0, 17.0, 10.0));

	public RitualPedestalBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CANDLE, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(CANDLE) ? WITH_CANDLE : STAND;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (!stack.is(ModItems.RITUAL_CANDLE) || state.getValue(CANDLE)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		}
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.setBlock(pos, state.setValue(CANDLE, true), Block.UPDATE_ALL);
			stack.consume(1, player);
			serverLevel.playSound(null, pos, SoundEvents.CANDLE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			serverLevel.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
			serverLevel.sendParticles(ModParticles.PINK_FLAME, pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, 6, 0.05, 0.05, 0.05, 0.01);
			SculkRitual.candleLit(serverLevel, pos, player);
		}
		return InteractionResult.SUCCESS;
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
		if (!state.getValue(CANDLE)) {
			return;
		}
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.12;
		double z = pos.getZ() + 0.5;
		level.addParticle(ModParticles.PINK_FLAME, x, y, z, 0.0, 0.0, 0.0);
		if (random.nextInt(4) == 0) {
			level.addParticle(new DustParticleOptions(random.nextBoolean() ? 0xFF5FA2 : 0xFFFFFF, 0.5F), x + random.nextGaussian() * 0.1, y + 0.1,
					z + random.nextGaussian() * 0.1, 0.0, 0.03, 0.0);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CANDLE);
	}
}
