package dev.goober.transdimension;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goober.transdimension.event.RealmEvents;
import dev.goober.transdimension.item.BottledFairy;
import dev.goober.transdimension.network.ModNetworking;
import dev.goober.transdimension.registry.ModAttachments;
import dev.goober.transdimension.registry.ModBlockEntities;
import dev.goober.transdimension.registry.ModBlocks;
import dev.goober.transdimension.registry.ModEffects;
import dev.goober.transdimension.registry.ModEntities;
import dev.goober.transdimension.registry.ModFeatures;
import dev.goober.transdimension.registry.ModFluids;
import dev.goober.transdimension.registry.ModItems;
import dev.goober.transdimension.registry.ModParticles;
import dev.goober.transdimension.registry.ModSounds;
import dev.goober.transdimension.registry.ModVillagers;
import dev.goober.transdimension.teleport.GooberTeleporter;
import dev.goober.transdimension.world.CloudRealm;
import dev.goober.transdimension.world.FairyRealm;
import dev.goober.transdimension.world.PinkDeepDark;
import dev.goober.transdimension.world.SculkRitual;

/**
 * Trans Dimension: say "Goober" in chat to visit the Trans Realm.
 */
public class TransDimension implements ModInitializer {
	public static final String MOD_ID = "transdimension";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** The level key of the Trans Realm (defined by data/transdimension/dimension/trans_realm.json). */
	public static final ResourceKey<Level> TRANS_REALM = ResourceKey.create(Registries.DIMENSION, id("trans_realm"));
	/** Floating islands above the clouds, where the Trans Fairy waits (data/transdimension/dimension/fairy_realm.json). */
	public static final ResourceKey<Level> FAIRY_REALM = ResourceKey.create(Registries.DIMENSION, id("fairy_realm"));
	/** White islands and wool clouds through the Sky Portal of a pink ancient city (data/transdimension/dimension/cloud_realm.json). */
	public static final ResourceKey<Level> CLOUD_REALM = ResourceKey.create(Registries.DIMENSION, id("cloud_realm"));

	@Override
	public void onInitialize() {
		// Order matters: sounds -> fluids (pink lava's block needs its fluid) -> blocks (the oven uses a sound) -> block entities (need their blocks) -> entities ->
		// items (the spawn egg and boats need their entity types, the creative tab needs every block) -> effects ->
		// villagers (the oven POI).
		ModSounds.initialize();
		ModParticles.initialize();
		ModFluids.initialize();
		ModBlocks.initialize();
		ModBlockEntities.initialize();
		ModFeatures.initialize();
		ModAttachments.initialize();
		ModEntities.initialize();
		ModItems.initialize();
		ModEffects.initialize();
		ModVillagers.initialize();
		ModNetworking.initialize();
		GooberTeleporter.initialize();
		RealmEvents.initialize();
		FairyRealm.initialize();
		BottledFairy.initialize();
		PinkDeepDark.initialize();
		SculkRitual.initialize();
		CloudRealm.initialize();

		LOGGER.info("Trans Dimension loaded. Say \"Goober\" in chat to visit the Trans Realm. You are valid!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
