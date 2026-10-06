package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.entity.JarFairy;

/**
 * The trophy for beating the Trans Fairy: a trans glass jar with a lid, and inside it a tiny fairy that drifts slowly
 * about, glowing in its own colour. The fairy is a little entity of its own ({@link JarFairy}), made when the jar is placed
 * (and by the jar's random ticks, for jars from before it could fly), and it sheds the jar's sparkles itself; the jar's
 * item still shows a little light.
 */
public class FairyJarBlock extends Block {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(3.0, 0.0, 3.0, 13.0, 11.0, 13.0), Block.box(3.5, 11.0, 3.5, 12.5, 14.5, 12.5));
	/** The jar's light level. Its fairy is lit at the same level (JarFairyRenderer), not full bright, so shaders don't blow it out. */
	public static final int LIGHT = 12;

	public FairyJarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
			JarFairy.ensureIn(serverLevel, pos);
		}
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		JarFairy.ensureIn(level, pos);
	}
}
