package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.fluid.HolyWaterFluid;
import dev.goober.transdimension.fluid.PinkLavaFluid;

/** Fluids: the Trans Realm's pink lava (its block is in ModBlocks, its bucket in ModItems). Registered before blocks. */
public final class ModFluids {
	public static final FlowingFluid PINK_LAVA = Registry.register(BuiltInRegistries.FLUID, TransDimension.id("pink_lava"),
			new PinkLavaFluid.Source());
	public static final FlowingFluid FLOWING_PINK_LAVA = Registry.register(BuiltInRegistries.FLUID, TransDimension.id("flowing_pink_lava"),
			new PinkLavaFluid.Flowing());
	/** The heavenly ruins' golden holy water (see HolyWaterFluid). */
	public static final FlowingFluid HOLY_WATER = Registry.register(BuiltInRegistries.FLUID, TransDimension.id("holy_water"),
			new HolyWaterFluid.Source());
	public static final FlowingFluid FLOWING_HOLY_WATER = Registry.register(BuiltInRegistries.FLUID, TransDimension.id("flowing_holy_water"),
			new HolyWaterFluid.Flowing());

	private ModFluids() {
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
