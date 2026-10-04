package dev.goober.transdimension.fluid;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;

import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModFluids;
import dev.goober.transdimension.registry.ModItems;

/**
 * Pink lava, the Trans Realm's lava. It is vanilla lava in every way but colour: it extends {@link LavaFluid}, so it
 * burns, starts fires, flows slowly, turns water into stone and obsidian, and counts as lava through the
 * {@code minecraft:lava} fluid tag. The realm's lava springs and its deep lava pools are pink lava. Its look is the
 * client's FluidModel (TransDimensionClient).
 */
public abstract class PinkLavaFluid extends LavaFluid {
	@Override
	public Fluid getFlowing() {
		return ModFluids.FLOWING_PINK_LAVA;
	}

	@Override
	public Fluid getSource() {
		return ModFluids.PINK_LAVA;
	}

	@Override
	public Item getBucket() {
		return ModItems.PINK_LAVA_BUCKET;
	}

	@Override
	public BlockState createLegacyBlock(FluidState state) {
		return ModBlocks.PINK_LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
	}

	@Override
	public boolean isSame(Fluid fluid) {
		return fluid == ModFluids.PINK_LAVA || fluid == ModFluids.FLOWING_PINK_LAVA;
	}

	public static class Flowing extends PinkLavaFluid {
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

	public static class Source extends PinkLavaFluid {
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
