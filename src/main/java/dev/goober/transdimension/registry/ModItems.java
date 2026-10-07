package dev.goober.transdimension.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.registry.CompostableRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FlattenableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.fabricmc.fabric.api.registry.TillableBlockRegistry;

import dev.goober.transdimension.TransDimension;
import dev.goober.transdimension.item.TransCrystalPearlItem;
import dev.goober.transdimension.item.TransWandItem;
import dev.goober.transdimension.item.TransWings;

/** Crystals and crystal gear, bakery treats, creature drops, spawn eggs, Maddie's gifts, boats and the creative tab. */
public final class ModItems {
	// ---------------------------------------------------------------- crystal gear materials
	public static final TagKey<Block> INCORRECT_FOR_TRANS_TOOL = TagKey.create(Registries.BLOCK, TransDimension.id("incorrect_for_trans_tool"));
	public static final TagKey<Item> REPAIRS_TRANS_GEAR = TagKey.create(Registries.ITEM, TransDimension.id("repairs_trans_gear"));

	/** Slightly better than diamond: durability, speed, damage bonus, enchantability. */
	public static final ToolMaterial TRANS_TOOL_MATERIAL = new ToolMaterial(INCORRECT_FOR_TRANS_TOOL, 1800, 9.0F, 3.5F, 20, REPAIRS_TRANS_GEAR);

	public static final int ARMOR_BASE_DURABILITY = 35;
	public static final ResourceKey<EquipmentAsset> TRANS_EQUIPMENT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, TransDimension.id("trans_crystal"));
	public static final ArmorMaterial TRANS_ARMOR_MATERIAL = new ArmorMaterial(
			ARMOR_BASE_DURABILITY,
			Map.of(
					ArmorType.HELMET, 3,
					ArmorType.CHESTPLATE, 8,
					ArmorType.LEGGINGS, 6,
					ArmorType.BOOTS, 3
			),
			20,
			SoundEvents.ARMOR_EQUIP_DIAMOND,
			2.5F,
			0.05F,
			REPAIRS_TRANS_GEAR,
			TRANS_EQUIPMENT_ASSET
	);

	// ---------------------------------------------------------------- crystals
	/** A rare gem from trans crystal ore, deep underground. It makes Trans Crystal Pearls, the keys to the Fairy Realm. */
	public static final Item TRANS_CRYSTAL = register("trans_crystal", Item::new, new Item.Properties());
	/** Common sparkly shards from prism clusters in the crystal caves and groves; decoration and crafting. */
	public static final Item PRISM_SHARD = register("prism_shard", Item::new, new Item.Properties());
	/** A Trans Crystal fused with prism shards, like a netherite ingot: it upgrades diamond gear to crystal gear. */
	public static final Item CRYSTAL_ALLOY = register("crystal_alloy", Item::new, new Item.Properties()
			.component(DataComponents.LORE, lore("item.transdimension.crystal_alloy.lore")));
	/**
	 * The template for the crystal upgrade, like the netherite one: found in mermaid ruin chests, crafted, or copied.
	 * A plain item is enough, because the smithing table takes any item that a smithing recipe names as its template.
	 */
	public static final Item CRYSTAL_UPGRADE_SMITHING_TEMPLATE = register("crystal_upgrade_smithing_template", Item::new,
			new Item.Properties().rarity(Rarity.UNCOMMON).component(DataComponents.LORE, lore(
					"item.transdimension.crystal_upgrade_smithing_template.lore", "item.transdimension.crystal_upgrade_smithing_template.lore2")));

	// ---------------------------------------------------------------- crystal gear (diamond gear upgraded with Crystal Alloy and the template)
	public static final Item TRANS_SWORD = register("trans_sword", Item::new,
			new Item.Properties().sword(TRANS_TOOL_MATERIAL, 3.0F, -2.4F));
	public static final Item TRANS_PICKAXE = register("trans_pickaxe", Item::new,
			new Item.Properties().pickaxe(TRANS_TOOL_MATERIAL, 1.0F, -2.8F));
	public static final Item TRANS_AXE = register("trans_axe",
			properties -> new AxeItem(TRANS_TOOL_MATERIAL, 5.0F, -3.0F, properties), new Item.Properties());
	public static final Item TRANS_SHOVEL = register("trans_shovel",
			properties -> new ShovelItem(TRANS_TOOL_MATERIAL, 1.5F, -3.0F, properties), new Item.Properties());
	public static final Item TRANS_HOE = register("trans_hoe",
			properties -> new HoeItem(TRANS_TOOL_MATERIAL, -3.0F, 0.0F, properties), new Item.Properties());

	public static final Item TRANS_HELMET = register("trans_helmet", Item::new, new Item.Properties()
			.humanoidArmor(TRANS_ARMOR_MATERIAL, ArmorType.HELMET)
			.durability(ArmorType.HELMET.getDurability(ARMOR_BASE_DURABILITY)));
	public static final Item TRANS_CHESTPLATE = register("trans_chestplate", Item::new, new Item.Properties()
			.humanoidArmor(TRANS_ARMOR_MATERIAL, ArmorType.CHESTPLATE)
			.durability(ArmorType.CHESTPLATE.getDurability(ARMOR_BASE_DURABILITY)));
	public static final Item TRANS_LEGGINGS = register("trans_leggings", Item::new, new Item.Properties()
			.humanoidArmor(TRANS_ARMOR_MATERIAL, ArmorType.LEGGINGS)
			.durability(ArmorType.LEGGINGS.getDurability(ARMOR_BASE_DURABILITY)));
	public static final Item TRANS_BOOTS = register("trans_boots", Item::new, new Item.Properties()
			.humanoidArmor(TRANS_ARMOR_MATERIAL, ArmorType.BOOTS)
			.durability(ArmorType.BOOTS.getDurability(ARMOR_BASE_DURABILITY)));

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

	// ---------------------------------------------------------------- the realm's caves
	/** Pink and blue glow berries from trans cave vines: a snack like glow berries, and they plant new vines on a ceiling. */
	public static final Item TRANS_GLOW_BERRIES = register("trans_glow_berries",
			properties -> new BlockItem(ModBlocks.TRANS_CAVE_VINES, properties),
			new Item.Properties().food(food(2, 0.1F, false), Consumables.defaultFood().build()));

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
	/** A chewy sweet; pastel slimes adore them (feed them to tame and breed slimes; sugar tames them too, less often). */
	public static final Item GUMDROP = register("gumdrop", Item::new, new Item.Properties()
			.food(food(2, 0.3F, true), Consumables.defaultFood().consumeSeconds(0.8F).build())
			.component(DataComponents.LORE, lore("item.transdimension.gumdrop.lore")));
	public static final Item PASTEL_SLIME_SPAWN_EGG = register("pastel_slime_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.PASTEL_SLIME));

	// ---------------------------------------------------------------- the Fairy Realm
	public static final Item TRANS_FAIRY_SPAWN_EGG = register("trans_fairy_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.TRANS_FAIRY).rarity(Rarity.EPIC));

	// ---------------------------------------------------------------- pink lava
	public static final Item PINK_LAVA_BUCKET = register("pink_lava_bucket", properties -> new BucketItem(ModFluids.PINK_LAVA, properties),
			new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));

	// ---------------------------------------------------------------- wild fairies
	/**
	 * A fairy caught in a glass bottle (only by bottling one: fairies and chests never give them). Hold it and it saves you
	 * from death once; {@link dev.goober.transdimension.item.BottledFairy} does the rescue, in the flag's colours instead of
	 * the totem's, so the item has no death protection component.
	 */
	public static final Item BOTTLED_FAIRY = register("bottled_fairy", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
			.component(DataComponents.LORE, lore("item.transdimension.bottled_fairy.lore", "item.transdimension.bottled_fairy.lore2")));
	public static final Item FAIRY_SPAWN_EGG = register("fairy_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.FAIRY));

	// ---------------------------------------------------------------- the pink deep dark
	/** The pink deep dark's gem, mined from sculk gem ore: sculk people take it as money, like emeralds. */
	public static final Item SCULK_GEM = register("sculk_gem", Item::new, new Item.Properties()
			.component(DataComponents.LORE, lore("item.transdimension.sculk_gem.lore")));
	/** Found in pink ancient city chests: set one on every stand of the ritual circle to open the city's gate. */
	public static final Item RITUAL_CANDLE = register("ritual_candle", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON)
			.component(DataComponents.LORE, lore("item.transdimension.ritual_candle.lore", "item.transdimension.ritual_candle.lore2")));
	public static final Item PINK_WARDEN_SPAWN_EGG = register("pink_warden_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.PINK_WARDEN));
	public static final Item SCULK_PERSON_SPAWN_EGG = register("sculk_person_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.SCULK_PERSON));

	// ---------------------------------------------------------------- the Cloud Realm
	public static final Item CLOUDY_SPAWN_EGG = register("cloudy_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.CLOUDY));
	public static final Item ANGEL_SPAWN_EGG = register("angel_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.ANGEL));

	// ---------------------------------------------------------------- the Cloud Realm (round 11)
	/** Cotton candy spun from a cloud: eat it and you float up into the air, then drift gently back down. */
	public static final Item CLOUD_CANDY = register("cloud_candy", Item::new, new Item.Properties().food(food(3, 0.3F, true),
			Consumables.defaultFood()
					.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 * 3, 1), 1.0F))
					.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 15, 0), 1.0F))
					.build()).component(DataComponents.LORE, lore("item.transdimension.cloud_candy.lore")));
	/** A bucket of the heavenly ruins' holy water. */
	public static final Item HOLY_WATER_BUCKET = register("holy_water_bucket", properties -> new BucketItem(ModFluids.HOLY_WATER, properties),
			new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));
	/** How many pages the Cloud Bible has (lang keys book.transdimension.cloud_bible.1 to this). */
	public static final int CLOUD_BIBLE_PAGES = 7;
	/**
	 * The Cloud Bible, found in the Cloud Realm's ruined churches (open on the lectern, and in the cloud chest): a written
	 * book with a cover of its own, its pages the angels' verses. Being a {@link WrittenBookItem} with its own
	 * {@code WRITTEN_BOOK_CONTENT}, it opens like any written book wherever it came from, and it's in
	 * {@code #minecraft:lectern_books}, so it sits on a lectern. Its pages are translatable, so they read in each player's
	 * language.
	 */
	public static final Item CLOUD_BIBLE = register("cloud_bible", WrittenBookItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(DataComponents.WRITTEN_BOOK_CONTENT, cloudBible()));

	// ---------------------------------------------------------------- holy gold gear: the heavenly ruins' loot, never crafted
	public static final TagKey<Item> REPAIRS_HOLY_GEAR = TagKey.create(Registries.ITEM, TransDimension.id("repairs_holy_gear"));
	/** Holy gold mines like diamond, as quickly as gold, and takes enchantments like gold; and it never breaks. */
	public static final ToolMaterial HOLY_TOOL_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1561, 12.0F, 3.0F, 22,
			REPAIRS_HOLY_GEAR);
	public static final ResourceKey<EquipmentAsset> HOLY_EQUIPMENT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, TransDimension.id("holy_gold"));
	public static final ArmorMaterial HOLY_ARMOR_MATERIAL = new ArmorMaterial(
			ARMOR_BASE_DURABILITY,
			Map.of(
					ArmorType.HELMET, 3,
					ArmorType.CHESTPLATE, 8,
					ArmorType.LEGGINGS, 6,
					ArmorType.BOOTS, 3
			),
			25,
			SoundEvents.ARMOR_EQUIP_GOLD,
			2.0F,
			0.0F,
			REPAIRS_HOLY_GEAR,
			HOLY_EQUIPMENT_ASSET
	);

	public static final Item HOLY_GOLDEN_SWORD = register("holy_golden_sword", Item::new, holy().sword(HOLY_TOOL_MATERIAL, 3.0F, -2.4F));
	public static final Item HOLY_GOLDEN_PICKAXE = register("holy_golden_pickaxe", Item::new, holy().pickaxe(HOLY_TOOL_MATERIAL, 1.0F, -2.8F));
	public static final Item HOLY_GOLDEN_AXE = register("holy_golden_axe",
			properties -> new AxeItem(HOLY_TOOL_MATERIAL, 5.0F, -3.0F, properties), holy());
	public static final Item HOLY_GOLDEN_SHOVEL = register("holy_golden_shovel",
			properties -> new ShovelItem(HOLY_TOOL_MATERIAL, 1.5F, -3.0F, properties), holy());
	public static final Item HOLY_GOLDEN_HOE = register("holy_golden_hoe",
			properties -> new HoeItem(HOLY_TOOL_MATERIAL, -3.0F, 0.0F, properties), holy());
	public static final Item HOLY_GOLDEN_HELMET = register("holy_golden_helmet", Item::new, holy()
			.humanoidArmor(HOLY_ARMOR_MATERIAL, ArmorType.HELMET)
			.durability(ArmorType.HELMET.getDurability(ARMOR_BASE_DURABILITY)));
	public static final Item HOLY_GOLDEN_CHESTPLATE = register("holy_golden_chestplate", Item::new, holy()
			.humanoidArmor(HOLY_ARMOR_MATERIAL, ArmorType.CHESTPLATE)
			.durability(ArmorType.CHESTPLATE.getDurability(ARMOR_BASE_DURABILITY)));
	public static final Item HOLY_GOLDEN_LEGGINGS = register("holy_golden_leggings", Item::new, holy()
			.humanoidArmor(HOLY_ARMOR_MATERIAL, ArmorType.LEGGINGS)
			.durability(ArmorType.LEGGINGS.getDurability(ARMOR_BASE_DURABILITY)));
	public static final Item HOLY_GOLDEN_BOOTS = register("holy_golden_boots", Item::new, holy()
			.humanoidArmor(HOLY_ARMOR_MATERIAL, ArmorType.BOOTS)
			.durability(ArmorType.BOOTS.getDurability(ARMOR_BASE_DURABILITY)));

	// ---------------------------------------------------------------- boats
	public static final Item TRANS_BOAT = register("trans_boat",
			properties -> new BoatItem(ModEntities.TRANS_BOAT, properties), new Item.Properties().stacksTo(1));
	public static final Item TRANS_CHEST_BOAT = register("trans_chest_boat",
			properties -> new BoatItem(ModEntities.TRANS_CHEST_BOAT, properties), new Item.Properties().stacksTo(1));

	// ---------------------------------------------------------------- Maddie and her gifts
	public static final Item MADDIE_SPAWN_EGG = register("maddie_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.MADDIE));
	public static final Item KIRA_SPAWN_EGG = register("kira_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.KIRA));
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

	/**
	 * Holy gear's shared properties: epic, always shimmering with the enchantment glint, and unbreakable ("holy loot never
	 * breaks"), whatever it's made into.
	 */
	private static Item.Properties holy() {
		return new Item.Properties().rarity(Rarity.EPIC)
				.component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
				.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
				.component(DataComponents.LORE, lore("item.transdimension.holy_gear.lore"));
	}

	private static Consumable withEffect(Consumable.Builder builder, MobEffectInstance effect) {
		return builder.onConsume(new ApplyStatusEffectsConsumeEffect(effect, 1.0F)).build();
	}

	/** The Cloud Bible's text: its title, author and pages (translatable, see CLOUD_BIBLE_PAGES). */
	private static WrittenBookContent cloudBible() {
		List<Filterable<Component>> pages = new ArrayList<>();
		for (int i = 1; i <= CLOUD_BIBLE_PAGES; i++) {
			pages.add(Filterable.passThrough(Component.translatable("book.transdimension.cloud_bible." + i)));
		}
		return new WrittenBookContent(Filterable.passThrough("Cloud Bible"), "the Angels", 0, pages, true);
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
		// The desert and riverside plants compost and burn like vanilla's cactus, dead bush and sugar cane.
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_CACTUS, 0.5F);
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_SUGAR_CANE, 0.5F);
		CompostableRegistry.INSTANCE.add(ModBlocks.DRY_SUGAR_BUSH, 0.3F);
		FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.DRY_SUGAR_BUSH, 60, 100);
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_MOSS_BLOCK, 0.65F);
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_MOSS_CARPET, 0.3F);
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_SAPLING, 0.3F);
		CompostableRegistry.INSTANCE.add(GUMDROP, 0.5F);
		CompostableRegistry.INSTANCE.add(TRANS_COOKIE, 0.85F);
		CompostableRegistry.INSTANCE.add(TRANS_CUPCAKE, 0.85F);
		CompostableRegistry.INSTANCE.add(TRANS_DONUT, 0.85F);
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_CAKE, 1.0F);

		// Wool furniture burns like wool, the bookshelf like a bookshelf.
		for (Block woollen : List.of(ModBlocks.LIGHT_BLUE_WOOL_STAIRS, ModBlocks.PINK_WOOL_STAIRS, ModBlocks.WHITE_WOOL_STAIRS,
				ModBlocks.LIGHT_BLUE_WOOL_SLAB, ModBlocks.PINK_WOOL_SLAB, ModBlocks.WHITE_WOOL_SLAB, ModBlocks.LIGHT_BLUE_CUSHION,
				ModBlocks.PINK_CUSHION, ModBlocks.WHITE_CUSHION, ModBlocks.TRANS_ARMCHAIR)) {
			FlammableBlockRegistry.getDefaultInstance().add(woollen, 30, 60);
		}
		FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.TRANS_BOOKSHELF, 30, 20);
		// A shovel wears trans grass and dirt into a trans dirt path, like vanilla's.
		FlattenableBlockRegistry.register(ModBlocks.TRANS_GRASS_BLOCK, ModBlocks.TRANS_DIRT_PATH.defaultBlockState());
		FlattenableBlockRegistry.register(ModBlocks.TRANS_DIRT, ModBlocks.TRANS_DIRT_PATH.defaultBlockState());

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
