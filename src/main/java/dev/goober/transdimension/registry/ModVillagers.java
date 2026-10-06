package dev.goober.transdimension.registry;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;

import dev.goober.transdimension.TransDimension;

/**
 * The Trans Baker: a villager profession whose job site is the Pride Oven.
 * Its trades are pure data: data/transdimension/trade_set/trans_baker/level_N.json.
 *
 * <p>Also the profession of the pink deep dark's sculk people ({@link dev.goober.transdimension.entity.SculkPerson}), who
 * have no job site at all. Their trades (made by tools/generate_data.py) are in trade_set/sculk_person/.
 */
public final class ModVillagers {
	public static final ResourceKey<PoiType> PRIDE_OVEN_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, TransDimension.id("pride_oven"));
	public static final ResourceKey<VillagerProfession> TRANS_BAKER = ResourceKey.create(Registries.VILLAGER_PROFESSION, TransDimension.id("trans_baker"));
	public static final ResourceKey<VillagerProfession> SCULK_PERSON = ResourceKey.create(Registries.VILLAGER_PROFESSION, TransDimension.id("sculk_person"));

	private ModVillagers() {
	}

	public static void initialize() {
		// Every facing of the oven counts as the job site.
		PoiHelper.register(PRIDE_OVEN_POI.identifier(), 1, 1, ModBlocks.PRIDE_OVEN);

		Int2ObjectOpenHashMap<ResourceKey<TradeSet>> tradeSets = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 5; level++) {
			tradeSets.put(level, ResourceKey.create(Registries.TRADE_SET, TransDimension.id("trans_baker/level_" + level)));
		}

		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, TRANS_BAKER, new VillagerProfession(
				Component.translatable("entity.transdimension.villager.trans_baker"),
				poi -> poi.is(PRIDE_OVEN_POI),
				poi -> poi.is(PRIDE_OVEN_POI),
				ImmutableSet.of(),
				ImmutableSet.of(),
				ModSounds.PRIDE_OVEN_CRACKLE,
				tradeSets
		));

		Int2ObjectOpenHashMap<ResourceKey<TradeSet>> sculkTrades = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 5; level++) {
			sculkTrades.put(level, ResourceKey.create(Registries.TRADE_SET, TransDimension.id("sculk_person/level_" + level)));
		}
		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, SCULK_PERSON, new VillagerProfession(
				Component.translatable("entity.transdimension.villager.sculk_person"),
				poi -> false,
				poi -> false,
				ImmutableSet.of(),
				ImmutableSet.of(),
				null,
				sculkTrades
		));
	}
}
