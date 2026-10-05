package dev.goober.transdimension.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.level.levelgen.Heightmap;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.entity.CrystalEye;
import dev.goober.transdimension.entity.Fairy;
import dev.goober.transdimension.entity.FairyCrystalSpike;
import dev.goober.transdimension.entity.Maddie;
import dev.goober.transdimension.entity.PastelSlime;
import dev.goober.transdimension.entity.Seat;
import dev.goober.transdimension.entity.SillyCat;
import dev.goober.transdimension.entity.TransEnderman;
import dev.goober.transdimension.entity.TransFairy;
import dev.goober.transdimension.entity.TransFish;
import dev.goober.transdimension.entity.TransMagicBolt;

/** Entity types. The realm's creatures spawn naturally through the biome JSON spawn lists. */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> SILLY_CAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("silly_cat"));

	public static final EntityType<SillyCat> SILLY_CAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, SILLY_CAT_KEY,
			FabricEntityType.Builder.createMob(SillyCat::new, MobCategory.CREATURE, mob -> mob
							.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules)
							.defaultAttributes(SillyCat::createAttributes))
					.sized(0.6F, 0.7F)
					.clientTrackingRange(8)
					.build(SILLY_CAT_KEY));

	public static final ResourceKey<EntityType<?>> TRANS_FISH_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("trans_fish"));
	public static final ResourceKey<EntityType<?>> TRANS_ENDERMAN_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("trans_enderman"));
	public static final ResourceKey<EntityType<?>> PASTEL_SLIME_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("pastel_slime"));

	/** Flag-striped schooling fish of the realm's seas and rivers; sized, spawned and bucketed like a cod. */
	public static final EntityType<TransFish> TRANS_FISH = Registry.register(BuiltInRegistries.ENTITY_TYPE, TRANS_FISH_KEY,
			FabricEntityType.Builder.createMob(TransFish::new, MobCategory.WATER_AMBIENT, mob -> mob
							.spawnPlacement(SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
									WaterAnimal::checkSurfaceWaterAnimalSpawnRules)
							.defaultAttributes(AbstractFish::createAttributes))
					.sized(0.5F, 0.3F)
					.eyeHeight(0.195F)
					.clientTrackingRange(4)
					.build(TRANS_FISH_KEY));
	/** The realm's white enderman; it replaces the vanilla enderman in the realm's spawn lists. */
	public static final EntityType<TransEnderman> TRANS_ENDERMAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, TRANS_ENDERMAN_KEY,
			FabricEntityType.Builder.createMob(TransEnderman::new, MobCategory.MONSTER, mob -> mob
							.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules)
							.defaultAttributes(EnderMan::createAttributes))
					.sized(0.6F, 2.9F)
					.eyeHeight(2.55F)
					.clientTrackingRange(8)
					.build(TRANS_ENDERMAN_KEY));
	/** Pink and blue pet slimes of the Gumdrop Glade. */
	public static final EntityType<PastelSlime> PASTEL_SLIME = Registry.register(BuiltInRegistries.ENTITY_TYPE, PASTEL_SLIME_KEY,
			FabricEntityType.Builder.createMob(PastelSlime::new, MobCategory.CREATURE, mob -> mob
							.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules)
							.defaultAttributes(PastelSlime::createAttributes))
					.sized(0.8F, 0.8F)
					.eyeHeight(0.5F)
					.clientTrackingRange(10)
					.build(PASTEL_SLIME_KEY));

	public static final ResourceKey<EntityType<?>> FAIRY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("fairy"));
	/** Rare wild fairies: glowing winged cubes that drift about the realms (ambient, so they come and go). */
	public static final EntityType<Fairy> FAIRY = Registry.register(BuiltInRegistries.ENTITY_TYPE, FAIRY_KEY,
			FabricEntityType.Builder.createMob(Fairy::new, MobCategory.AMBIENT, mob -> mob
							.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Fairy::checkFairySpawnRules)
							.defaultAttributes(Fairy::createAttributes))
					.sized(0.4F, 0.4F)
					.eyeHeight(0.2F)
					.clientTrackingRange(8)
					.build(FAIRY_KEY));

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

	public static final ResourceKey<EntityType<?>> MADDIE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("maddie"));
	public static final ResourceKey<EntityType<?>> TRANS_MAGIC_BOLT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("trans_magic_bolt"));

	/** Maddie of the Egg House; she is placed by the island's structure template and never spawns on her own. */
	public static final EntityType<Maddie> MADDIE = Registry.register(BuiltInRegistries.ENTITY_TYPE, MADDIE_KEY,
			FabricEntityType.Builder.createMob(Maddie::new, MobCategory.MISC, mob -> mob.defaultAttributes(Maddie::createAttributes))
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.clientTrackingRange(10)
					.build(MADDIE_KEY));
	/** The Trans Wand's spell. */
	public static final EntityType<TransMagicBolt> TRANS_MAGIC_BOLT = Registry.register(BuiltInRegistries.ENTITY_TYPE, TRANS_MAGIC_BOLT_KEY,
			EntityType.Builder.<TransMagicBolt>of(TransMagicBolt::new, MobCategory.MISC)
					.sized(0.3F, 0.3F)
					.clientTrackingRange(6)
					.updateInterval(5)
					.build(TRANS_MAGIC_BOLT_KEY));

	public static final ResourceKey<EntityType<?>> CRYSTAL_EYE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("crystal_eye"));
	/** A thrown Trans Crystal Pearl flying towards the nearest Fairy Sanctum, like an eye of ender. */
	public static final EntityType<CrystalEye> CRYSTAL_EYE = Registry.register(BuiltInRegistries.ENTITY_TYPE, CRYSTAL_EYE_KEY,
			EntityType.Builder.<CrystalEye>of(CrystalEye::new, MobCategory.MISC)
					.sized(0.25F, 0.25F)
					.clientTrackingRange(4)
					.updateInterval(4)
					.build(CRYSTAL_EYE_KEY));

	public static final ResourceKey<EntityType<?>> TRANS_FAIRY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("trans_fairy"));
	public static final ResourceKey<EntityType<?>> FAIRY_CRYSTAL_SPIKE_KEY = ResourceKey.create(Registries.ENTITY_TYPE,
			TransDimension.id("fairy_crystal_spike"));

	/** The boss of the Fairy Realm; she never spawns naturally (FairyRealm calls her). */
	public static final EntityType<TransFairy> TRANS_FAIRY = Registry.register(BuiltInRegistries.ENTITY_TYPE, TRANS_FAIRY_KEY,
			FabricEntityType.Builder.createMob(TransFairy::new, MobCategory.MONSTER, mob -> mob.defaultAttributes(TransFairy::createAttributes))
					.sized(0.9F, 2.4F)
					.eyeHeight(2.0F)
					.fireImmune()
					.clientTrackingRange(10)
					.build(TRANS_FAIRY_KEY));
	/** The Trans Fairy's ice crystals that burst out of the ground. */
	public static final EntityType<FairyCrystalSpike> FAIRY_CRYSTAL_SPIKE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			FAIRY_CRYSTAL_SPIKE_KEY,
			EntityType.Builder.<FairyCrystalSpike>of(FairyCrystalSpike::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.8F, 1.6F)
					.clientTrackingRange(6)
					.updateInterval(2)
					.build(FAIRY_CRYSTAL_SPIKE_KEY));

	public static final ResourceKey<EntityType<?>> SEAT_KEY = ResourceKey.create(Registries.ENTITY_TYPE, TransDimension.id("seat"));
	/** The invisible seat a player rides while sitting on a cushion, stool, chair or armchair. */
	public static final EntityType<Seat> SEAT = Registry.register(BuiltInRegistries.ENTITY_TYPE, SEAT_KEY,
			EntityType.Builder.<Seat>of(Seat::new, MobCategory.MISC)
					.noLootTable()
					.sized(0.001F, 0.001F)
					.clientTrackingRange(10)
					.build(SEAT_KEY));

	private ModEntities() {
	}

	public static void initialize() {
		// Static fields do the work; calling this forces class loading at the right time.
	}
}
