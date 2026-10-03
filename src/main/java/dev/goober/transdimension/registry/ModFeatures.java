package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.world.TransVillageFeature;

/** Custom world generation features. The village is placed by data/transdimension/worldgen/placed_feature/trans_village.json. */
public final class ModFeatures {
	public static final Feature<NoneFeatureConfiguration> TRANS_VILLAGE = Registry.register(
			BuiltInRegistries.FEATURE, TransDimension.id("trans_village"), new TransVillageFeature(NoneFeatureConfiguration.CODEC));

	private ModFeatures() {
	}

	public static void initialize() {
	}
}
