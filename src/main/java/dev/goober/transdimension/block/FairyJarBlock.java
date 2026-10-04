package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.block.entity.FairyJarBlockEntity;

/**
 * The trophy for beating the Trans Fairy: a trans glass jar with a lid, and inside it a little cube of light with wings
 * that dances about, glowing pink, white and blue in turn. The jar itself is a block model; the dancing light is drawn
 * by the client's FairyJarRenderer, which is why the jar has a (data-less) block entity.
 */
public class FairyJarBlock extends Block implements EntityBlock {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(3.0, 0.0, 3.0, 13.0, 11.0, 13.0), Block.box(3.5, 11.0, 3.5, 12.5, 14.5, 12.5));
	/** The light's colours, in the order it cycles through them (FairyJarRenderer uses the same timing). */
	public static final int[] COLOURS = {0xF5A9B8, 0xFFFFFF, 0x5BCEFA};
	/** Ticks the light spends on each colour. */
	public static final int COLOUR_TICKS = 30;

	public FairyJarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FairyJarBlockEntity(pos, state);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// Now and then a sparkle in the light's current colour slips out around the lid.
		if (random.nextInt(4) == 0) {
			int colour = COLOURS[(int) (level.getGameTime() / COLOUR_TICKS % COLOURS.length)];
			level.addParticle(new DustParticleOptions(colour, 0.5F), pos.getX() + 0.35 + random.nextDouble() * 0.3, pos.getY() + 0.8,
					pos.getZ() + 0.35 + random.nextDouble() * 0.3, 0.0, 0.02, 0.0);
		}
	}
}
