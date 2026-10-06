package dev.goober.transdimension.fluid;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;

import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModFluids;
import dev.goober.transdimension.registry.ModItems;

/**
 * Holy water, from the fountains and pools of the Cloud Realm's heavenly ruins. It is vanilla water in every way but its
 * look and its blessing: it extends {@link WaterFluid} and counts as water through the {@code minecraft:water} fluid tag
 * (you swim in it, it puts out fire, it makes a new source between two), it can be carried in a bucket, and anything
 * bathing in it is Blessed (ModBlocks.HOLY_WATER, BlessedEffect). Its golden look is the client's FluidModel
 * (TransDimensionClient).
 */
public abstract class HolyWaterFluid extends WaterFluid {
	@Override
	public Fluid getFlowing() {
		return ModFluids.FLOWING_HOLY_WATER;
	}

	@Override
	public Fluid getSource() {
		return ModFluids.HOLY_WATER;
	}

	@Override
	public Item getBucket() {
		return ModItems.HOLY_WATER_BUCKET;
	}

	@Override
	public BlockState createLegacyBlock(FluidState state) {
		return ModBlocks.HOLY_WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
	}

	@Override
	public boolean isSame(Fluid fluid) {
		return fluid == ModFluids.HOLY_WATER || fluid == ModFluids.FLOWING_HOLY_WATER;
	}

	public static class Flowing extends HolyWaterFluid {
		@Override
		protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
			super.createFluidStateDefinition(builder);
			builder.add(LEVEL);
		}

		@Override
		public int getAmount(FluidState state) {
			return state.getValue(LEVEL);
		}

		@Override
		public boolean isSource(FluidState state) {
			return false;
		}
	}

	public static class Source extends HolyWaterFluid {
		@Override
		public int getAmount(FluidState state) {
			return 8;
		}

		@Override
		public boolean isSource(FluidState state) {
			return true;
		}
	}
}
