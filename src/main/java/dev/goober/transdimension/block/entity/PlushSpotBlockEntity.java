package dev.goober.transdimension.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.block.FurnitureBlock;
import dev.goober.transdimension.block.PlushSpotBlock;
import dev.goober.transdimension.registry.ModAttachments;
import dev.goober.transdimension.registry.ModBlockEntities;
import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.world.PlushLedger;

/**
 * Turns a {@link PlushSpotBlock} into a plush or into air on its first server tick.
 *
 * <p>The village is found from the structure piece the spot sits in, and the world's {@link PlushLedger} remembers
 * which villages already got their plush. The plush is one the world has handed out the fewest times, so the first
 * nine villages you find hold nine different cats.
 */
public class PlushSpotBlockEntity extends BlockEntity {
	public static final ResourceKey<Structure> TRANS_VILLAGE = ResourceKey.create(Registries.STRUCTURE, TransDimension.id("trans_village"));

	public PlushSpotBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PLUSH_SPOT, pos, state);
	}

	public static void resolve(Level level, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel) || !state.is(ModBlocks.PLUSH_SPOT)) {
			return;
		}
		BlockState result = Blocks.AIR.defaultBlockState();
		StructureStart village = villageAt(serverLevel, pos);
		if (village.isValid()) {
			long villageId = village.getChunkPos().pack();
			PlushLedger ledger = serverLevel.getAttachedOrElse(ModAttachments.PLUSH_LEDGER, PlushLedger.EMPTY);
			if (!ledger.villages().contains(villageId)) {
				int plush = ledger.leastGiven(ModBlocks.PLUSHES.size(), serverLevel.getRandom());
				serverLevel.setAttached(ModAttachments.PLUSH_LEDGER, ledger.withPlush(villageId, plush, ModBlocks.PLUSHES.size()));
				result = ModBlocks.PLUSHES.get(plush).defaultBlockState()
						.setValue(FurnitureBlock.FACING, state.getValue(PlushSpotBlock.FACING));
			}
		}
		serverLevel.setBlock(pos, result, Block.UPDATE_ALL);
	}

	private static StructureStart villageAt(ServerLevel level, BlockPos pos) {
		Holder<Structure> village = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(TRANS_VILLAGE);
		return level.structureManager().getStructureWithPieceAt(pos, HolderSet.direct(village));
	}
}
