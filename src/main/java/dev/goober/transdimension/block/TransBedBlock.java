package dev.goober.transdimension.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The trans bed. Two of them side by side (same direction, feet next to each other) form a double bed
 * with one big heart across the blanket: each foot shows half of it.
 *
 * <p>The {@link #HEART} property picks the half. Pairs are made along a row from the counter-clockwise end
 * (the left end, seen from the foot of the bed): a bed becomes the right half when the bed on its left is a
 * left half, otherwise it becomes the left half when there is a bed on its right. So a row of four beds makes
 * two hearts and a row of three makes one heart plus a plain bed. Each bed only reads its two neighbours,
 * and a change ripples down the row through ordinary shape updates.
 */
public class TransBedBlock extends BedBlock {
	public static final EnumProperty<HeartHalf> HEART = EnumProperty.create("heart", HeartHalf.class);

	public TransBedBlock(Properties properties) {
		super(DyeColor.PINK, properties);
		this.registerDefaultState(this.defaultBlockState().setValue(HEART, HeartHalf.NONE));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(HEART);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state == null ? null : this.withHeart(state, context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		BlockState updated = super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
		return updated.is(this) ? this.withHeart(updated, level, pos) : updated;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		BlockState mirrored = super.mirror(state, mirror);
		return mirror == Mirror.NONE ? mirrored : mirrored.setValue(HEART, state.getValue(HEART).flipped());
	}

	private BlockState withHeart(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(PART) != BedPart.FOOT) {
			return state.setValue(HEART, HeartHalf.NONE);
		}
		Direction facing = state.getValue(FACING);
		BlockState left = level.getBlockState(pos.relative(facing.getCounterClockWise()));
		if (this.isFootFacing(left, facing) && left.getValue(HEART) == HeartHalf.LEFT) {
			return state.setValue(HEART, HeartHalf.RIGHT);
		}
		BlockState right = level.getBlockState(pos.relative(facing.getClockWise()));
		return state.setValue(HEART, this.isFootFacing(right, facing) ? HeartHalf.LEFT : HeartHalf.NONE);
	}

	private boolean isFootFacing(BlockState other, Direction facing) {
		return other.is(this) && other.getValue(FACING) == facing && other.getValue(PART) == BedPart.FOOT;
	}

	/** Which half of the double-bed heart a foot shows; the head part is always {@link #NONE}. */
	public enum HeartHalf implements StringRepresentable {
		NONE("none"),
		LEFT("left"),
		RIGHT("right");

		private final String name;

		HeartHalf(String name) {
			this.name = name;
		}

		HeartHalf flipped() {
			return switch (this) {
				case LEFT -> RIGHT;
				case RIGHT -> LEFT;
				case NONE -> NONE;
			};
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
