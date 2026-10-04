package dev.goober.transdimension.block;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.entity.Seat;

/**
 * Trans furniture (chairs, tables, cushions, stools, armchairs and cat plushes) for the village houses, the camps and
 * your own builds. Furniture with a seat height is a seat: use it with an empty hand to sit down ({@link Seat}).
 *
 * <p>The shape is given as boxes for a block facing north (pixel coordinates, like {@link Block#box});
 * the other three directions are rotated from it. Rotation and mirroring are implemented so the
 * furniture keeps its orientation when a village template is rotated during world generation.
 */
public class FurnitureBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** A chair whose open side faces north: four legs, a seat and a backrest along the south edge. */
	public static final double[][] CHAIR_SHAPE = {
			{3, 0, 3, 13, 8, 13},
			{3, 8, 11, 13, 16, 13},
	};
	/** A plump square floor cushion. */
	public static final double[][] CUSHION_SHAPE = {
			{1, 0, 1, 15, 5, 15},
	};
	/** A round-topped stool on four legs. */
	public static final double[][] STOOL_SHAPE = {
			{3, 0, 3, 13, 11, 13},
	};
	/** An armchair whose open side faces north: a padded seat between two arms, and a tall back along the south edge. */
	public static final double[][] ARMCHAIR_SHAPE = {
			{1, 0, 1, 15, 10, 15},
			{1, 10, 1, 3, 13, 15},
			{13, 10, 1, 15, 13, 15},
			{1, 10, 12, 15, 16, 15},
	};
	/** A round-ish pedestal table: a thick top, a central post and a foot. */
	public static final double[][] TABLE_SHAPE = {
			{0, 13, 0, 16, 16, 16},
			{6, 1, 6, 10, 13, 10},
			{3, 0, 3, 13, 1, 13},
	};

	/** A sitting cat plush looking north: the body and big head, plus the tail curled along its east side. */
	public static final double[][] PLUSH_SHAPE = {
			{4, 0, 3.5, 12, 12, 12},
			{12, 0, 7, 13, 2, 12},
	};

	private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
	/** How high the seat is, in pixels above the bottom of the block; 0 for furniture you can't sit on. */
	private final double seatHeight;

	public FurnitureBlock(double[][] northBoxes, Properties properties) {
		this(northBoxes, 0.0, properties);
	}

	/** Furniture you can sit on, with its seat {@code seatHeight} pixels above the bottom of the block. */
	public FurnitureBlock(double[][] northBoxes, double seatHeight, Properties properties) {
		super(properties);
		this.seatHeight = seatHeight;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			VoxelShape shape = Shapes.empty();
			for (double[] box : northBoxes) {
				shape = Shapes.or(shape, rotatedBox(box, direction));
			}
			this.shapes.put(direction, shape);
		}
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	private static VoxelShape rotatedBox(double[] b, Direction direction) {
		double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
		return switch (direction) {
			case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
			case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
			case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
			default -> Block.box(x1, y1, z1, x2, y2, z2);
		};
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shapes.get(state.getValue(FACING));
	}

	public boolean isSeat() {
		return this.seatHeight > 0.0;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return this.isSeat() ? Seat.sit(level, pos, this.seatHeight / 16.0, player) : InteractionResult.PASS;
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// Face the player who placed it, like a furnace or a stair.
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
		builder.add(FACING);
	}
}
