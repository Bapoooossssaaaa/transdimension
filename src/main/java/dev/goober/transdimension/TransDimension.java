package dev.goober.transdimension;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModEffects;
import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModItems;
import dev.goober.transdimension.registry.ModSounds;
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
		// Order matters: sounds -> blocks (the oven uses a sound) -> entities -> items (the spawn egg needs
		// the entity type, the creative tab needs every block) -> effects -> villagers (the oven POI).
		ModSounds.initialize();
		ModBlocks.initialize();
		ModEntities.initialize();
		ModItems.initialize();
		ModEffects.initialize();
		ModVillagers.initialize();
		GooberTeleporter.initialize();

		LOGGER.info("Trans Dimension loaded. Say \"Goober\" in chat to visit the Trans Realm. You are valid!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
