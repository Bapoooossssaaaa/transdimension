package dev.goober.transdimension;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModFeatures;
import dev.goober.transdimension.registry.ModFluids;
import dev.goober.transdimension.registry.ModItems;
import dev.goober.transdimension.registry.ModVillagers;
import dev.goober.transdimension.teleport.GooberTeleporter;

/**
 * Trans Dimension: say "Goober" in chat to visit the Trans Realm.
 */
public class TransDimension implements ModInitializer {
	public static final String MOD_ID = "transdimension";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The level key of the Trans Realm (defined by data/transdimension/dimension/trans_realm.json). */
	public static final ResourceKey<Level> TRANS_REALM = ResourceKey.create(Registries.DIMENSION, id("trans_realm"));

	@Override
	public void onInitialize() {
		// Order matters: fluids -> blocks (the liquid block needs the fluid) -> items (the bucket needs the fluid).
		ModFluids.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		ModFeatures.initialize();
		ModVillagers.initialize();
		GooberTeleporter.initialize();

		LOGGER.info("Trans Dimension loaded. Say \"Goober\" in chat to visit the Trans Realm. You are valid!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
