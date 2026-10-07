package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.goober.transdimension.registry.ModItems;
import dev.goober.transdimension.registry.ModParticles;
import dev.goober.transdimension.world.FairyRealm;

/**
 * The crystal altar at the middle of the Fairy Realm's arena. Throw a Trans Crystal (or a Crystal Pearl) onto it, or
 * use one on it, to call the Trans Fairy: the first time that plays the cutscene that brings her in (FairyCutscene),
 * later it calls her back for another fight (one fairy at a time). It can't be broken in survival.
 */
public class FairyAltarBlock extends Block {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(1.0, 0.0, 1.0, 15.0, 4.0, 15.0), Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0),
			Block.box(2.0, 12.0, 2.0, 14.0, 15.0, 14.0));

	public FairyAltarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (!stack.is(ModItems.TRANS_CRYSTAL) && !stack.is(ModItems.TRANS_CRYSTAL_PEARL)) {
			return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
		}
		if (level instanceof ServerLevel serverLevel && FairyRealm.offerAtAltar(serverLevel, player)) {
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(5) == 0) {
			level.addParticle(ModParticles.TRANS_SPARK, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0,
					pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.05, 0.0);
		}
	}
}
