package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModItems;

/** A trans cave vine's body; it belongs to the {@link TransCaveVinesBlock} tip below it. */
public class TransCaveVinesPlantBlock extends CaveVinesPlantBlock {
	public TransCaveVinesPlantBlock(Properties properties) {
		super(properties);
	}

	@Override
	public GrowingPlantHeadBlock getHeadBlock() {
		return (GrowingPlantHeadBlock) ModBlocks.TRANS_CAVE_VINES;
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(ModItems.TRANS_GLOW_BERRIES);
	}

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return TransCaveVinesBlock.pickBerries(state, level, pos);
	}
}
