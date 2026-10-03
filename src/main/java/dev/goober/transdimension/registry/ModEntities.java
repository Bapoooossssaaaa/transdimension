package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.level.levelgen.Heightmap;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.SillyCat;

/** Entity types. The Silly Cat spawns naturally through the biome JSON spawn lists. */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> SILLY_CAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("silly_cat"));

	public static final EntityType<SillyCat> SILLY_CAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, SILLY_CAT_KEY,
			FabricEntityType.Builder.createMob(SillyCat::new, MobCategory.CREATURE, mob -> mob
							.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules)
							.defaultAttributes(SillyCat::createAttributes))
					.sized(0.6F, 0.7F)
					.clientTrackingRange(8)
					.build(SILLY_CAT_KEY));

	public static final ResourceKey<EntityType<?>> TRANS_BOAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("trans_boat"));
	public static final ResourceKey<EntityType<?>> TRANS_CHEST_BOAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("trans_chest_boat"));

	/** Boats of trans planks, sized like vanilla's; breaking one gives back its item (looked up lazily, items come later). */
	public static final EntityType<Boat> TRANS_BOAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, TRANS_BOAT_KEY,
			EntityType.Builder.<Boat>of((type, level) -> new Boat(type, level, () -> ModItems.TRANS_BOAT), MobCategory.MISC)
					.noLootTable()
					.sized(1.375F, 0.5625F)
					.eyeHeight(0.5625F)
					.clientTrackingRange(10)
					.build(TRANS_BOAT_KEY));
	public static final EntityType<ChestBoat> TRANS_CHEST_BOAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, TRANS_CHEST_BOAT_KEY,
			EntityType.Builder.<ChestBoat>of((type, level) -> new ChestBoat(type, level, () -> ModItems.TRANS_CHEST_BOAT), MobCategory.MISC)
					.noLootTable()
					.sized(1.375F, 0.5625F)
					.eyeHeight(0.5625F)
					.clientTrackingRange(10)
					.build(TRANS_CHEST_BOAT_KEY));

	private ModEntities() {
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
