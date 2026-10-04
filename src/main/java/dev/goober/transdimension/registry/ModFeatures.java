package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.world.FairyIslandFeature;
import dev.goober.transdimension.world.HeartTreeFeature;
import dev.goober.transdimension.world.TransCoralReefFeature;
import dev.goober.transdimension.world.TransDungeonFeature;

/** The mod's own world generation feature types; their configured and placed features are JSON (generate_worldgen.py). */
public final class ModFeatures {
	public static final Feature<NoneFeatureConfiguration> TRANS_CORAL_REEF = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("trans_coral_reef"), new TransCoralReefFeature(NoneFeatureConfiguration.CODEC));
	public static final Feature<NoneFeatureConfiguration> HEART_TREE = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("heart_tree"), new HeartTreeFeature(NoneFeatureConfiguration.CODEC));

	public static final Feature<NoneFeatureConfiguration> FAIRY_ISLAND = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("fairy_island"), new FairyIslandFeature(NoneFeatureConfiguration.CODEC));
	/** The realm's dungeons, in place of vanilla's cobblestone monster rooms. */
	public static final Feature<NoneFeatureConfiguration> TRANS_DUNGEON = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("trans_dungeon"), new TransDungeonFeature(NoneFeatureConfiguration.CODEC));

	private ModFeatures() {
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
