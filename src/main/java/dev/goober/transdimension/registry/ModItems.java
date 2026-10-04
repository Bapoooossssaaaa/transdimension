package dev.goober.transdimension.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.registry.CompostableRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.fabricmc.fabric.api.registry.TillableBlockRegistry;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.item.TransCrystalPearlItem;
import dev.goober.transdimension.item.TransWandItem;
import dev.goober.transdimension.item.TransWings;

/** Crystals, bakery treats, creature drops, spawn eggs, Maddie's gifts, boats and the creative tab. */
public final class ModItems {
	// ---------------------------------------------------------------- crystals
	/** A rare gem from trans crystal ore, deep underground. It makes Trans Crystal Pearls, the keys to the Fairy Realm. */
	public static final Item TRANS_CRYSTAL = register("trans_crystal", Item::new, new Item.Properties());
	/** Common sparkly shards from prism clusters in the crystal caves and groves; decoration and crafting. */
	public static final Item PRISM_SHARD = register("prism_shard", Item::new, new Item.Properties());

	// ---------------------------------------------------------------- bakery treats
	public static final Item TRANS_DONUT = register("trans_donut", Item::new, new Item.Properties().food(
			food(6, 0.6F, false),
			withEffect(Consumables.defaultFood(), new MobEffectInstance(MobEffects.ABSORPTION, 20 * 30, 0))));
	public static final Item TRANS_COOKIE = register("trans_cookie", Item::new, new Item.Properties().food(
			food(2, 0.3F, false),
			withEffect(Consumables.defaultFood(), new MobEffectInstance(MobEffects.REGENERATION, 20 * 4, 0))));
	public static final Item TRANS_CUPCAKE = register("trans_cupcake", Item::new, new Item.Properties().food(
			food(5, 0.6F, false),
			withEffect(Consumables.defaultFood(), new MobEffectInstance(MobEffects.LUCK, 20 * 60, 0))));
	public static final Item TRANS_MACARON = register("trans_macaron", Item::new, new Item.Properties().food(
			food(3, 0.5F, true),
			withEffect(Consumables.defaultFood(), new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 45, 0))));
	public static final Item TRANS_BOBA = register("trans_boba", Item::new, new Item.Properties()
			.stacksTo(16)
			.usingConvertsTo(Items.GLASS_BOTTLE)
			.food(food(4, 0.4F, true),
					withEffect(Consumables.defaultDrink(), new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20 * 60, 0))));

	// ---------------------------------------------------------------- mobs
	public static final Item SILLY_CAT_SPAWN_EGG = register("silly_cat_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.SILLY_CAT));

	// ---------------------------------------------------------------- trans fish
	public static final Item TRANS_FISH = register("trans_fish", Item::new, new Item.Properties()
			.food(food(2, 0.2F, false), Consumables.defaultFood().build()));
	public static final Item COOKED_TRANS_FISH = register("cooked_trans_fish", Item::new, new Item.Properties()
			.food(food(6, 0.7F, false), Consumables.defaultFood().build()));
	public static final Item TRANS_FISH_BUCKET = register("trans_fish_bucket",
			properties -> new MobBucketItem(ModEntities.TRANS_FISH, Fluids.WATER, SoundEvents.BUCKET_EMPTY_FISH, properties),
			new Item.Properties().stacksTo(1).component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY));
	public static final Item TRANS_FISH_SPAWN_EGG = register("trans_fish_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.TRANS_FISH));

	// ---------------------------------------------------------------- trans enderman and the keys to the Fairy Realm
	/** Dropped by trans endermen; thrown, it teleports you like an ender pearl. */
	public static final Item TRANS_PEARL = register("trans_pearl", EnderpearlItem::new, new Item.Properties()
			.stacksTo(16)
			.useCooldown(1.0F));
	/** A trans pearl set with a trans crystal. Twelve of them in the frame of a Fairy Portal open the way to the Fairy Realm. */
	public static final Item TRANS_CRYSTAL_PEARL = register("trans_crystal_pearl", TransCrystalPearlItem::new, new Item.Properties()
			.stacksTo(16)
			.rarity(Rarity.RARE)
			.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
			.component(DataComponents.LORE, lore("item.transdimension.trans_crystal_pearl.lore", "item.transdimension.trans_crystal_pearl.lore2")));
	public static final Item TRANS_ENDERMAN_SPAWN_EGG = register("trans_enderman_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.TRANS_ENDERMAN));

	// ---------------------------------------------------------------- pastel slimes
	/** Wobbly jelly from pastel slimes and the gel mounds of the Gumdrop Glade. */
	public static final Item PASTEL_GEL = register("pastel_gel", Item::new, new Item.Properties());
	/** A chewy sweet; pastel slimes adore them (feed them to tame and breed slimes). */
	public static final Item GUMDROP = register("gumdrop", Item::new, new Item.Properties()
			.food(food(2, 0.3F, true), Consumables.defaultFood().consumeSeconds(0.8F).build()));
	public static final Item PASTEL_SLIME_SPAWN_EGG = register("pastel_slime_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.PASTEL_SLIME));

	// ---------------------------------------------------------------- the Fairy Realm
	public static final Item TRANS_FAIRY_SPAWN_EGG = register("trans_fairy_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.TRANS_FAIRY).rarity(Rarity.EPIC));

	// ---------------------------------------------------------------- boats
	public static final Item TRANS_BOAT = register("trans_boat",
			properties -> new BoatItem(ModEntities.TRANS_BOAT, properties), new Item.Properties().stacksTo(1));
	public static final Item TRANS_CHEST_BOAT = register("trans_chest_boat",
			properties -> new BoatItem(ModEntities.TRANS_CHEST_BOAT, properties), new Item.Properties().stacksTo(1));

	// ---------------------------------------------------------------- Maddie and her gifts
	public static final Item MADDIE_SPAWN_EGG = register("maddie_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.MADDIE));
	/** Shoots a sparkling heart of trans magic; see {@link TransWandItem}. */
	public static final Item TRANS_WAND = register("trans_wand", TransWandItem::new, new Item.Properties()
			.stacksTo(1)
			.rarity(Rarity.EPIC)
			.useCooldown(0.6F)
			.component(DataComponents.LORE, lore("item.transdimension.trans_wand.lore")));
	/** An elytra-like glider with extra moves; see {@link TransWings}. No durability: they are a gift. */
	public static final Item TRANS_WINGS = register("trans_wings", Item::new, new Item.Properties()
			.stacksTo(1)
			.rarity(Rarity.EPIC)
			.component(DataComponents.GLIDER, Unit.INSTANCE)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
					.setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
					.setDamageOnHurt(false)
					.build())
			.component(DataComponents.LORE, lore("item.transdimension.trans_wings.lore", "item.transdimension.trans_wings.lore2",
					"item.transdimension.trans_wings.lore3")));
	/** Only used to draw the wand's projectile. */
	public static final Item TRANS_MAGIC_BOLT = register("trans_magic_bolt", Item::new, new Item.Properties());

	// ---------------------------------------------------------------- creative tab
	public static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY = ResourceKey.create(
			BuiltInRegistries.CREATIVE_MODE_TAB.key(), TransDimension.id("trans_dimension"));

	/** Technical items that shouldn't show up in the creative tab. */
	private static final Set<String> HIDDEN_FROM_TAB = Set.of("trans_magic_bolt");

	public static final CreativeModeTab CREATIVE_TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(TRANS_CRYSTAL))
			.title(Component.translatable("itemGroup.transdimension.trans_dimension"))
			.displayItems((parameters, output) -> {
				// Everything the mod registers, in registration order (blocks first, by family, then items).
				for (Item item : BuiltInRegistries.ITEM) {
					Identifier id = BuiltInRegistries.ITEM.getKey(item);
					if (id.getNamespace().equals(TransDimension.MOD_ID) && !HIDDEN_FROM_TAB.contains(id.getPath())) {
						output.accept(item);
					}
				}
			})
			.build();

	private ModItems() {
	}

	private static ItemLore lore(String... keys) {
		List<Component> lines = new ArrayList<>();
		for (String key : keys) {
			lines.add(Component.translatable(key));
		}
		return new ItemLore(lines);
	}

	private static FoodProperties food(int nutrition, float saturation, boolean alwaysEdible) {
		FoodProperties.Builder builder = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation);
		if (alwaysEdible) {
			builder.alwaysEdible();
		}
		return builder.build();
	}

	private static Consumable withEffect(Consumable.Builder builder, MobEffectInstance effect) {
		return builder.onConsume(new ApplyStatusEffectsConsumeEffect(effect, 1.0F)).build();
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, TransDimension.id(name));
		Item item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CREATIVE_TAB_KEY, CREATIVE_TAB);

		CompostableRegistry.INSTANCE.add(ModBlocks.PRIDE_BLOSSOM, 0.65F);
		for (Block flower : List.of(ModBlocks.TRANS_TULIP, ModBlocks.PEARL_DAISY, ModBlocks.SKY_BELL, ModBlocks.FLAG_LILY,
				ModBlocks.LAVENDER_PUFF, ModBlocks.TRANS_ORCHID, ModBlocks.HEART_BLOOM, ModBlocks.PRIDE_PEONY)) {
			CompostableRegistry.INSTANCE.add(flower, 0.65F);
			FlammableBlockRegistry.getDefaultInstance().add(flower, 60, 100);
		}
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_PETALS, 0.3F);
		FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.TRANS_PETALS, 60, 100);
		for (Block leaves : List.of(ModBlocks.TRANS_LEAVES, ModBlocks.PEARL_LEAVES, ModBlocks.SKY_LEAVES, ModBlocks.BLUSH_LEAVES,
				ModBlocks.TWILIGHT_LEAVES)) {
			CompostableRegistry.INSTANCE.add(leaves, 0.3F);
			FlammableBlockRegistry.getDefaultInstance().add(leaves, 30, 60);
		}
		// Trans vegetation composts and burns like vanilla's.
		for (Block plant : List.of(ModBlocks.TRANS_SHORT_GRASS, ModBlocks.TRANS_SEAGRASS, ModBlocks.TRANS_KELP, ModBlocks.PASTEL_BUSH,
				ModBlocks.SHORT_SUGAR_GRASS, ModBlocks.TALL_SUGAR_GRASS)) {
			CompostableRegistry.INSTANCE.add(plant, 0.3F);
		}
		for (Block plant : List.of(ModBlocks.TALL_TRANS_GRASS, ModBlocks.TRANS_FIREFLY_BUSH)) {
			CompostableRegistry.INSTANCE.add(plant, 0.5F);
		}
		for (Block plant : List.of(ModBlocks.TRANS_FERN, ModBlocks.LARGE_TRANS_FERN, ModBlocks.TRANS_LILY_PAD)) {
			CompostableRegistry.INSTANCE.add(plant, 0.65F);
		}
		for (Block plant : List.of(ModBlocks.TRANS_SHORT_GRASS, ModBlocks.TALL_TRANS_GRASS, ModBlocks.TRANS_FERN, ModBlocks.LARGE_TRANS_FERN,
				ModBlocks.PASTEL_BUSH, ModBlocks.TRANS_FIREFLY_BUSH, ModBlocks.SHORT_SUGAR_GRASS, ModBlocks.TALL_SUGAR_GRASS)) {
			FlammableBlockRegistry.getDefaultInstance().add(plant, 60, 100);
		}
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_MOSS_BLOCK, 0.65F);
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_MOSS_CARPET, 0.3F);
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_SAPLING, 0.3F);
		CompostableRegistry.INSTANCE.add(GUMDROP, 0.5F);
		CompostableRegistry.INSTANCE.add(TRANS_COOKIE, 0.85F);
		CompostableRegistry.INSTANCE.add(TRANS_CUPCAKE, 0.85F);
		CompostableRegistry.INSTANCE.add(TRANS_DONUT, 0.85F);
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_CAKE, 1.0F);

		// Logs strip with an axe, like vanilla wood.
		StrippableBlockRegistry.register(ModBlocks.TRANS_LOG, ModBlocks.STRIPPED_TRANS_LOG);
		StrippableBlockRegistry.register(ModBlocks.TRANS_WOOD, ModBlocks.STRIPPED_TRANS_WOOD);
		// The themed forests' woods strip, burn and compost like any wood.
		for (ModBlocks.WoodFamily family : ModBlocks.WOOD_FAMILIES) {
			StrippableBlockRegistry.register(family.log(), family.strippedLog());
			StrippableBlockRegistry.register(family.wood(), family.strippedWood());
			for (Block block : List.of(family.planks(), family.stairs(), family.slab(), family.fence(), family.fenceGate())) {
				FlammableBlockRegistry.getDefaultInstance().add(block, 5, 20);
			}
			for (Block block : List.of(family.log(), family.strippedLog(), family.wood(), family.strippedWood())) {
				FlammableBlockRegistry.getDefaultInstance().add(block, 5, 5);
			}
			CompostableRegistry.INSTANCE.add(family.sapling(), 0.3F);
		}
		for (Block flower : List.of(ModBlocks.BLUSH_CARNATION, ModBlocks.PEARL_SNOWDROP, ModBlocks.FORGET_ME_NOT, ModBlocks.TRANS_ROSE,
				ModBlocks.STAR_BLOOM, ModBlocks.FAIRY_BELL, ModBlocks.SKY_DELPHINIUM, ModBlocks.BLUSH_FOXGLOVE, ModBlocks.PEARL_LUPINE)) {
			CompostableRegistry.INSTANCE.add(flower, 0.65F);
			FlammableBlockRegistry.getDefaultInstance().add(flower, 60, 100);
		}
		for (Block leafy : List.of(ModBlocks.FLOWERING_BLUSH_LEAVES, ModBlocks.BLOSSOM_HEDGE, ModBlocks.BLUEBELL_HEDGE, ModBlocks.PEARL_HEDGE)) {
			CompostableRegistry.INSTANCE.add(leafy, 0.3F);
			FlammableBlockRegistry.getDefaultInstance().add(leafy, 30, 60);
		}

		// Trans dirt and grass till into (vanilla) farmland, so you can farm in the realm.
		TillableBlockRegistry.register(ModBlocks.TRANS_DIRT, HoeItem::onlyIfAirAbove, HoeItem.changeIntoState(Blocks.FARMLAND.defaultBlockState()));
		TillableBlockRegistry.register(ModBlocks.TRANS_GRASS_BLOCK, HoeItem::onlyIfAirAbove, HoeItem.changeIntoState(Blocks.FARMLAND.defaultBlockState()));

		// Wood burns like wood, wool like wool.
		FlammableBlockRegistry flammable = FlammableBlockRegistry.getDefaultInstance();
		for (Block block : List.of(ModBlocks.TRANS_PLANKS, ModBlocks.TRANS_STAIRS, ModBlocks.TRANS_SLAB, ModBlocks.TRANS_FENCE,
				ModBlocks.TRANS_FENCE_GATE, ModBlocks.TRANS_CHAIR, ModBlocks.TRANS_TABLE)) {
			flammable.add(block, 5, 20);
		}
		for (Block block : List.of(ModBlocks.TRANS_LOG, ModBlocks.TRANS_WOOD, ModBlocks.STRIPPED_TRANS_LOG, ModBlocks.STRIPPED_TRANS_WOOD)) {
			flammable.add(block, 5, 5);
		}
		flammable.add(ModBlocks.TRANS_LEAVES, 30, 60);
		flammable.add(ModBlocks.TRANS_WOOL, 30, 60);
		flammable.add(ModBlocks.TRANS_CARPET, 60, 20);
		flammable.add(ModBlocks.PRIDE_BLOSSOM, 60, 100);

		FuelValueEvents.BUILD.register((builder, context) -> {
			builder.add(ModBlocks.TRANS_CHAIR, context.baseSmeltTime() * 3 / 2);
			builder.add(ModBlocks.TRANS_TABLE, context.baseSmeltTime() * 3 / 2);
		});
	}
}
