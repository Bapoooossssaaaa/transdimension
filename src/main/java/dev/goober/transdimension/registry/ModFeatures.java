package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.world.BeanstalkFeature;
import dev.goober.transdimension.world.CandyCaneFeature;
import dev.goober.transdimension.world.FairyIslandFeature;
import dev.goober.transdimension.world.HeartTreeFeature;
import dev.goober.transdimension.world.HeavenlyRuinFeature;
import dev.goober.transdimension.world.PinkLavaFeature;
import dev.goober.transdimension.world.PinkSculkPatchFeature;
import dev.goober.transdimension.world.TransCoralReefFeature;
import dev.goober.transdimension.world.TransDungeonFeature;
import dev.goober.transdimension.world.WoolCloudFeature;

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
	/** Patches of pink sculk over the pink deep dark's cave walls, with sensors, shriekers and catalysts. */
	public static final Feature<NoneFeatureConfiguration> PINK_SCULK_PATCH = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("pink_sculk_patch"), new PinkSculkPatchFeature(NoneFeatureConfiguration.CODEC));
	/** The last step of every realm chunk: any vanilla lava left by the cave generator turns pink. */
	public static final Feature<NoneFeatureConfiguration> PINK_LAVA = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("pink_lava"), new PinkLavaFeature(NoneFeatureConfiguration.CODEC));
	/** The Cloud Realm's clouds of white wool. */
	public static final Feature<NoneFeatureConfiguration> WOOL_CLOUD = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("wool_cloud"), new WoolCloudFeature(NoneFeatureConfiguration.CODEC));
	/** The Cloud Realm's heavenly ruins: spiral towers and shrines of cloudcite and holy gold, with holy water. */
	public static final Feature<NoneFeatureConfiguration> HEAVENLY_RUIN = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("heavenly_ruin"), new HeavenlyRuinFeature(NoneFeatureConfiguration.CODEC));
	/** A very rare giant beanstalk of green concrete, climbing from an island up to a cloud. */
	public static final Feature<NoneFeatureConfiguration> BEANSTALK = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("beanstalk"), new BeanstalkFeature(NoneFeatureConfiguration.CODEC));

	/** A giant candy cane in the Candy Cane Grove, hooked over at the top. */
	public static final Feature<NoneFeatureConfiguration> CANDY_CANE = Registry.register(BuiltInRegistries.FEATURE,
			TransDimension.id("candy_cane"), new CandyCaneFeature(NoneFeatureConfiguration.CODEC));

	private ModFeatures() {
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
