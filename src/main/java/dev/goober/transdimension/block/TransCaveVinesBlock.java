package dev.goober.transdimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModItems;

/**
 * The growing tip of a trans cave vine: vanilla's cave vines (they hang from cave ceilings, glow where they bear
 * berries and can be climbed) with lilac leaves and pink and blue berries. Picking the berries gives Trans Glow
 * Berries, which plant new vines. {@link TransCaveVinesPlantBlock} is the rest of the vine.
 *
 * <p>The overrides are public so they compile whether vanilla's are protected or public.
 */
public class TransCaveVinesBlock extends CaveVinesBlock {
	public TransCaveVinesBlock(Properties properties) {
		super(properties);
	}

	@Override
	public Block getBodyBlock() {
		return ModBlocks.TRANS_CAVE_VINES_PLANT;
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		return new ItemStack(ModItems.TRANS_GLOW_BERRIES);
	}

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return pickBerries(state, level, pos);
	}

	/**
	 * Vanilla's {@code CaveVines.use}, giving Trans Glow Berries: vanilla's own drops come from a fixed loot table
	 * ({@code minecraft:harvest/cave_vine}) that only knows glow berries.
	 */
	static InteractionResult pickBerries(BlockState state, Level level, BlockPos pos) {
		if (!state.getValue(BlockStateProperties.BERRIES)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			Block.popResource(serverLevel, pos, new ItemStack(ModItems.TRANS_GLOW_BERRIES));
			serverLevel.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F,
					0.8F + serverLevel.getRandom().nextFloat() * 0.4F);
			BlockState picked = state.setValue(BlockStateProperties.BERRIES, false);
			serverLevel.setBlock(pos, picked, Block.UPDATE_CLIENTS);
			serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(picked));
		}
		return InteractionResult.SUCCESS;
	}
}
