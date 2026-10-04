package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.world.TransCoralReefFeature;

/** The mod's own world generation feature types; their configured and placed features are JSON (generate_worldgen.py). */
public final class ModFeatures {
	public static final Feature<NoneFeatureConfiguration> TRANS_CORAL_REEF = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("trans_coral_reef"), new TransCoralReefFeature(NoneFeatureConfiguration.CODEC));

	private ModFeatures() {
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
