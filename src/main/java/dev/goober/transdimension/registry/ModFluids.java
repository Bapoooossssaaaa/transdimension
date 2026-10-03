package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.fluid.TransWaterFluid;

/** Trans Water: the fluid that fills every ocean, lake and aquifer of the Trans Realm. */
public final class ModFluids {
	public static final ResourceKey<Fluid> FLOWING_TRANS_WATER_ID = key("flowing_trans_water");
	public static final ResourceKey<Fluid> TRANS_WATER_ID = key("trans_water");

	public static final FlowingFluid FLOWING_TRANS_WATER = register(FLOWING_TRANS_WATER_ID, new TransWaterFluid.Flowing());
	public static final FlowingFluid TRANS_WATER = register(TRANS_WATER_ID, new TransWaterFluid.Source());

	private ModFluids() {
	}

	private static ResourceKey<Fluid> key(String name) {
		return ResourceKey.create(Registries.FLUID, TransDimension.id(name));
	}

	private static FlowingFluid register(ResourceKey<Fluid> key, FlowingFluid fluid) {
		return Registry.register(BuiltInRegistries.FLUID, key, fluid);
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
