package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import dev.goober.transdimension.registry.ModParticles;
import dev.goober.transdimension.world.PinkDeepDark;

/**
 * A pink sculk catalyst. Vanilla's catalyst spreads vanilla (blue) sculk through its block entity, so this one has no
 * block entity at all: when something dies within eight blocks, {@link PinkDeepDark} makes it {@link #bloom} and spreads
 * pink sculk where the creature fell.
 */
public class PinkSculkCatalystBlock extends Block {
	public static final BooleanProperty BLOOM = BlockStateProperties.BLOOM;
	private static final int[] GLOW = {0xFF5FA2, 0xFFB3D1, 0xFFE3EE};

	public PinkSculkCatalystBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(BLOOM, false));
	}

	/** Blooms for a moment (the bloom textures pulse) with a burst of pink souls, then settles back. */
	public static void bloom(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(BLOOM, true), Block.UPDATE_ALL);
		level.scheduleTick(pos, state.getBlock(), 8);
		level.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 2.0F, 0.6F + level.getRandom().nextFloat() * 0.4F);
		level.sendParticles(ModParticles.PINK_SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, 3, 0.2, 0.0, 0.2, 0.0);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(BLOOM)) {
			level.setBlock(pos, state.setValue(BLOOM, false), Block.UPDATE_ALL);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(state.getValue(BLOOM) ? 1 : 12) == 0) {
			level.addParticle(new DustParticleOptions(GLOW[random.nextInt(GLOW.length)], 0.6F), pos.getX() + 0.2 + random.nextDouble() * 0.6,
					pos.getY() + 1.02, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.02, 0.0);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BLOOM);
	}
}
