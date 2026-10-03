package dev.goober.transdimension.registry;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.registry.CompostableRegistry;

import dev.goober.transdimension.TransDimension;

/** Trans Crystal gear, bakery treats, the Trans Water bucket and the creative tab. */
public final class ModItems {
	// ---------------------------------------------------------------- materials
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

	// ---------------------------------------------------------------- crystal + gear
	public static final Item TRANS_CRYSTAL = register("trans_crystal", Item::new, new Item.Properties());

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

	// ---------------------------------------------------------------- misc
	public static final Item TRANS_WATER_BUCKET = register("trans_water_bucket",
			properties -> new BucketItem(ModFluids.TRANS_WATER, properties),
			new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));

	// ---------------------------------------------------------------- creative tab
	public static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY = ResourceKey.create(
			BuiltInRegistries.CREATIVE_MODE_TAB.key(), TransDimension.id("trans_dimension"));

	public static final CreativeModeTab CREATIVE_TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(TRANS_CRYSTAL))
			.title(Component.translatable("itemGroup.transdimension.trans_dimension"))
			.displayItems((parameters, output) -> {
				for (ItemLike entry : creativeTabContents()) {
					output.accept(entry);
				}
			})
			.build();

	private ModItems() {
	}

	private static List<ItemLike> creativeTabContents() {
		return List.of(
				ModBlocks.TRANS_GRASS_BLOCK, ModBlocks.TRANS_DIRT, ModBlocks.TRANS_STONE, ModBlocks.TRANS_COBBLESTONE,
				ModBlocks.TRANS_STONE_BRICKS, ModBlocks.TRANS_SAND, ModBlocks.TRANS_LOG, ModBlocks.TRANS_PLANKS,
				ModBlocks.TRANS_LEAVES, ModBlocks.PRIDE_BLOSSOM, ModBlocks.TRANS_CRYSTAL_ORE, ModBlocks.TRANS_CRYSTAL_BLOCK,
				ModBlocks.PRIDE_OVEN, TRANS_WATER_BUCKET,
				TRANS_CRYSTAL, TRANS_SWORD, TRANS_PICKAXE, TRANS_AXE, TRANS_SHOVEL, TRANS_HOE,
				TRANS_HELMET, TRANS_CHESTPLATE, TRANS_LEGGINGS, TRANS_BOOTS,
				TRANS_DONUT, TRANS_COOKIE, TRANS_CUPCAKE, TRANS_MACARON, TRANS_BOBA, ModBlocks.TRANS_CAKE
		);
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
		CompostableRegistry.INSTANCE.add(ModBlocks.TRANS_LEAVES, 0.3F);
	}
}
